package dev.lucas.slumdrugs.world;

import dev.lucas.slumdrugs.Keys;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The rundown district: market, apartments, workshops, tavern, clinic, a gang warehouse,
 * an abandoned greenhouse and hidden basements. Condition rises when the player invests in
 * the place and falls when the gangs take over, which changes how the streets look.
 */
public final class District {

    public record Lot(String id, int cellX, int cellZ, String kind) {}

    private final SlumDrugsPlugin plugin;
    private final Keys keys;
    private final File file;

    private World world;
    private Location origin;
    private long seed;
    private int condition = 25;
    private double drift;
    private int lastRefreshCondition = 25;
    private final Map<String, Location> anchors = new LinkedHashMap<>();
    private final List<Location> dropBoxes = new ArrayList<>();
    private final Map<String, String> dropBoxNames = new LinkedHashMap<>();
    private String takeoverId;
    private org.bukkit.util.BoundingBox villageBounds;
    private final Map<String,Location> convertedVillages=new LinkedHashMap<>();
    private final java.util.Set<String> restoredVillages=new java.util.HashSet<>();

    public District(SlumDrugsPlugin plugin, Keys keys) {
        this.plugin = plugin;
        this.keys = keys;
        this.file = new File(plugin.getDataFolder(), "district.yml");
    }

    public boolean exists() { return world != null && origin != null; }

    public Location origin() { return origin; }

    public long seed() { return seed; }

    public int condition() { return condition; }
    public String takeoverId(){return takeoverId;}
    public boolean restored(String id){return restoredVillages.contains(id);}
    public boolean alreadyConverted(String id,Location centre,int distance) {
        if(convertedVillages.containsKey(id)) return true;
        for(Location l:convertedVillages.values()) if(l.getWorld().equals(centre.getWorld())
                && Math.pow(l.getX()-centre.getX(),2)+Math.pow(l.getZ()-centre.getZ(),2)<(double)distance*distance) return true;
        return false;
    }
    public void remember(String id,Location centre){convertedVillages.put(id,centre.clone());save();}
    public void adopt(String id,Location centre,org.bukkit.util.BoundingBox bounds,long villageSeed,
                      Map<String,Location> places,Map<String,Location> boxes) {
        world=centre.getWorld();origin=centre.clone();seed=villageSeed;takeoverId=id;villageBounds=bounds.clone();
        condition=plugin.getConfig().getInt("district.condition-start",25);drift=0;lastRefreshCondition=condition;
        anchors.clear();anchors.putAll(places);dropBoxes.clear();dropBoxNames.clear();
        for(var e:boxes.entrySet()){dropBoxes.add(e.getValue());dropBoxNames.put(key(e.getValue()),e.getKey());}
        remember(id,centre);
    }
    public void restoredTakeover(String id) {
        restoredVillages.add(id);
        if(id.equals(takeoverId)){world=null;origin=null;takeoverId=null;villageBounds=null;anchors.clear();dropBoxes.clear();dropBoxNames.clear();}
        save();
    }

    public Location anchor(String id) { return anchors.get(id); }

    public Map<String, Location> anchors() { return anchors; }

    public List<Location> dropBoxes() { return dropBoxes; }

    public String dropBoxName(Location l) { return dropBoxNames.get(key(l)); }

    /** True if the location sits inside the district footprint. */
    public boolean contains(Location l) {
        if (!exists() || !l.getWorld().equals(world)) return false;
        if(villageBounds!=null) return l.getX()>=villageBounds.getMinX() && l.getX()<=villageBounds.getMaxX()
                && l.getZ()>=villageBounds.getMinZ() && l.getZ()<=villageBounds.getMaxZ();
        int size = plugin.getConfig().getInt("district.cell-size", 9) * (plugin.getConfig().getInt("district.radius-cells", 3) * 2 + 1);
        return Math.abs(l.getBlockX() - origin.getBlockX()) <= size / 2
                && Math.abs(l.getBlockZ() - origin.getBlockZ()) <= size / 2;
    }

    public void adjustCondition(int delta) {
        condition = Math.max(0, Math.min(100, condition + delta));
    }

    /**
     * Small pushes from player actions. Honest work lifts the district, gang work and open
     * dealing drag it down. The streets are redressed once the change is big enough to see.
     */
    public void nudge(double delta) {
        if (!exists()) return;
        drift += delta;
        int whole = (int) drift;
        if (whole == 0) return;
        drift -= whole;
        int before = condition;
        adjustCondition(whole);
        if (Math.abs(condition - lastRefreshCondition) >= 5) {
            lastRefreshCondition = condition;
            refresh();
            announce(before);
        }
    }

    private void announce(int before) {
        String text = condition > before
                ? "<green>The district looks a little better kept than it did.</green>"
                : "<gray>The district looks rougher than it did.</gray>";
        for (org.bukkit.entity.Player p : world.getPlayers()) {
            if (contains(p.getLocation())) p.sendMessage(dev.lucas.slumdrugs.Msg.mm(text));
        }
    }

    // ------------------------------------------------------------------ generation

    /** Builds the whole district at the given centre. Existing terrain in the footprint is replaced. */
    public void generate(Location centre) {
        if(takeoverId!=null) throw new IllegalStateException("Restore the village takeover before building a new district");
        villageBounds=null;
        this.world = centre.getWorld();
        this.seed = world.getSeed() ^ (centre.getBlockX() * 341873128712L) ^ (centre.getBlockZ() * 132897987541L);
        this.condition = plugin.getConfig().getInt("district.condition-start", 25);
        anchors.clear();
        dropBoxes.clear();
        dropBoxNames.clear();

        int cell = plugin.getConfig().getInt("district.cell-size", 9);
        int radius = plugin.getConfig().getInt("district.radius-cells", 3);
        int groundY = world.getHighestBlockYAt(centre) - 1;
        this.origin = new Location(world, centre.getBlockX(), groundY, centre.getBlockZ());
        Random r = new Random(seed);

        clearAndPave(cell, radius, r);

        List<Lot> lots = List.of(
                new Lot("market", 0, 0, "market"),
                new Lot("tavern", -1, 0, "tavern"),
                new Lot("clinic", 1, 0, "clinic"),
                new Lot("apartments_a", 0, -1, "apartments"),
                new Lot("apartments_b", 0, 1, "apartments"),
                new Lot("workshop_a", -1, -1, "workshop"),
                new Lot("workshop_b", 1, -1, "workshop"),
                new Lot("warehouse", 1, 1, "warehouse"),
                new Lot("greenhouse", -1, 1, "greenhouse"));

        for (Lot lot : lots) build(lot, cell, r);

        // Outer ring of shacks and empty shopfronts.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) continue;
                if (r.nextDouble() < 0.45) build(new Lot("shack_" + x + "_" + z, x, z, "shack"), cell, r);
            }
        }

        alley(cell, r);
        outpost(r);
        save();
    }

    private void clearAndPave(int cell, int radius, Random r) {
        int half = cell * radius + cell / 2;
        int y = origin.getBlockY();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int x = origin.getBlockX() + dx;
                int z = origin.getBlockZ() + dz;
                for (int dy = 1; dy <= 20; dy++) {
                    world.getBlockAt(x, y + dy, z).setType(Material.AIR, false);
                }
                for (int dy = 0; dy > -3; dy--) {
                    world.getBlockAt(x, y + dy, z).setType(Material.DIRT, false);
                }
                Material surface = switch (r.nextInt(8)) {
                    case 0, 1 -> Material.COBBLESTONE;
                    case 2 -> Material.MOSSY_COBBLESTONE;
                    case 3 -> Material.GRAVEL;
                    case 4 -> Material.ANDESITE;
                    case 5 -> Material.DIRT_PATH;
                    case 6 -> Material.COARSE_DIRT;
                    default -> Material.STONE;
                };
                world.getBlockAt(x, y, z).setType(surface, false);
            }
        }
        // Puddles and litter in the streets.
        for (int i = 0; i < 40; i++) {
            int x = origin.getBlockX() + r.nextInt(half * 2) - half;
            int z = origin.getBlockZ() + r.nextInt(half * 2) - half;
            Block b = world.getBlockAt(x, y, z);
            if (b.getType() == Material.AIR) continue;
            if (r.nextBoolean()) b.setType(Material.WATER, false);
            else world.getBlockAt(x, y + 1, z).setType(r.nextBoolean() ? Material.DEAD_BUSH : Material.SHORT_GRASS, false);
        }
    }

    private void build(Lot lot, int cell, Random r) {
        int size = cell - 2;
        int bx = origin.getBlockX() + lot.cellX() * cell - size / 2;
        int bz = origin.getBlockZ() + lot.cellZ() * cell - size / 2;
        int y = origin.getBlockY() + 1;
        Location door;

        switch (lot.kind()) {
            case "market" -> {
                door = market(bx, y, bz, size, r);
                anchors.put("market", door);
            }
            case "tavern" -> {
                door = house(bx, y, bz, size, 5, Material.DARK_OAK_PLANKS, Material.DARK_OAK_STAIRS, r, true);
                anchors.put("tavern", door);
                tavernInterior(bx, y, bz, size, r);
                basementFarm(bx, y, bz, size, r, "tavern");
                dropBox(new Location(world, bx + 1, y, bz + 1), "tavern");
            }
            case "clinic" -> {
                door = house(bx, y, bz, size, 5, Material.WHITE_CONCRETE, Material.SMOOTH_STONE_SLAB, r, false);
                anchors.put("clinic", door);
                clinicInterior(bx, y, bz, size);
            }
            case "apartments" -> {
                door = house(bx, y, bz, size, 9, Material.BRICKS, Material.BRICK_STAIRS, r, true);
                anchors.put(lot.id(), door);
                apartmentInterior(bx, y, bz, size, r);
            }
            case "workshop" -> {
                door = house(bx, y, bz, size, 5, Material.SPRUCE_PLANKS, Material.SPRUCE_STAIRS, r, true);
                anchors.put(lot.id(), door);
                workshopInterior(bx, y, bz, size, r);
            }
            case "warehouse" -> {
                door = warehouse(bx, y, bz, size, r);
                anchors.put("warehouse", door);
                basementFarm(bx, y, bz, size, r, "warehouse");
            }
            case "greenhouse" -> {
                door = greenhouse(bx, y, bz, size, r);
                anchors.put("greenhouse", door);
            }
            default -> {
                door = house(bx, y, bz, size, 4, Material.SPRUCE_PLANKS, Material.SPRUCE_SLAB, r, true);
                anchors.put(lot.id(), door);
            }
        }
    }

    // ------------------------------------------------------------------ building blocks

    /** Generic building shell. Returns the doorway location. */
    private Location house(int bx, int y, int bz, int size, int height, Material wall, Material trim, Random r, boolean worn) {
        int x2 = bx + size - 1;
        int z2 = bz + size - 1;

        for (int x = bx; x <= x2; x++) {
            for (int z = bz; z <= z2; z++) {
                world.getBlockAt(x, y - 1, z).setType(Material.STONE_BRICKS, false);
                world.getBlockAt(x, y, z).setType(r.nextInt(6) == 0 ? Material.SPRUCE_PLANKS : Material.OAK_PLANKS, false);
            }
        }
        for (int h = 1; h < height; h++) {
            for (int x = bx; x <= x2; x++) {
                for (int z = bz; z <= z2; z++) {
                    boolean edge = x == bx || x == x2 || z == bz || z == z2;
                    if (!edge) {
                        world.getBlockAt(x, y + h, z).setType(Material.AIR, false);
                        continue;
                    }
                    Material m = wall;
                    if (worn && r.nextInt(7) == 0) m = weathered(wall);
                    world.getBlockAt(x, y + h, z).setType(m, false);
                }
            }
        }
        // Floors above the first for taller blocks.
        for (int h = 4; h < height - 1; h += 4) {
            for (int x = bx + 1; x < x2; x++) {
                for (int z = bz + 1; z < z2; z++) {
                    world.getBlockAt(x, y + h, z).setType(Material.OAK_PLANKS, false);
                }
            }
        }
        // Roof.
        for (int x = bx; x <= x2; x++) {
            for (int z = bz; z <= z2; z++) {
                world.getBlockAt(x, y + height, z).setType(trim, false);
            }
        }
        // Windows.
        for (int h = 2; h < height; h += 3) {
            for (int x = bx + 2; x < x2; x += 3) {
                Block b = world.getBlockAt(x, y + h, bz);
                b.setType(worn && r.nextInt(3) == 0 ? Material.OAK_TRAPDOOR : Material.GLASS_PANE, false);
                Block b2 = world.getBlockAt(x, y + h, z2);
                b2.setType(worn && r.nextInt(3) == 0 ? Material.DARK_OAK_TRAPDOOR : Material.GLASS_PANE, false);
            }
        }
        // Doorway facing the centre.
        int doorX = bx + size / 2;
        int doorZ = origin.getBlockZ() > bz ? z2 : bz;
        world.getBlockAt(doorX, y + 1, doorZ).setType(Material.AIR, false);
        world.getBlockAt(doorX, y + 2, doorZ).setType(Material.AIR, false);
        world.getBlockAt(doorX, y + 3, doorZ).setType(Material.DARK_OAK_SLAB, false);
        lantern(doorX + 1, y + 3, doorZ);

        if (worn) grime(bx, y, bz, size, height, r);
        return new Location(world, doorX + 0.5, y + 1, doorZ + (doorZ == bz ? 1.5 : -0.5));
    }

    private Material weathered(Material wall) {
        return switch (wall) {
            case BRICKS -> Material.TERRACOTTA;
            case DARK_OAK_PLANKS -> Material.STRIPPED_DARK_OAK_LOG;
            case SPRUCE_PLANKS -> Material.STRIPPED_SPRUCE_LOG;
            case WHITE_CONCRETE -> Material.LIGHT_GRAY_CONCRETE;
            default -> Material.CRACKED_STONE_BRICKS;
        };
    }

    /** Cobwebs, cracks and rubbish scaled to how bad the district is doing. */
    private void grime(int bx, int y, int bz, int size, int height, Random r) {
        int amount = (int) ((100 - condition) / 100.0 * size);
        for (int i = 0; i < amount; i++) {
            int x = bx + 1 + r.nextInt(Math.max(1, size - 2));
            int z = bz + 1 + r.nextInt(Math.max(1, size - 2));
            int h = 1 + r.nextInt(Math.max(1, height - 1));
            Block b = world.getBlockAt(x, y + h, z);
            if (b.getType().isAir() && r.nextInt(3) == 0) b.setType(Material.COBWEB, false);
        }
    }

    private void lantern(int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        if (b.getType().isAir()) b.setType(Material.LANTERN, false);
    }

    private Location market(int bx, int y, int bz, int size, Random r) {
        int x2 = bx + size - 1;
        int z2 = bz + size - 1;
        for (int x = bx; x <= x2; x++) {
            for (int z = bz; z <= z2; z++) {
                world.getBlockAt(x, y - 1, z).setType(Material.STONE_BRICKS, false);
            }
        }
        // Four stalls with awnings.
        int[][] spots = {{bx + 1, bz + 1}, {x2 - 1, bz + 1}, {bx + 1, z2 - 1}, {x2 - 1, z2 - 1}};
        for (int[] spot : spots) {
            int x = spot[0];
            int z = spot[1];
            world.getBlockAt(x, y, z).setType(Material.OAK_FENCE, false);
            world.getBlockAt(x, y + 1, z).setType(Material.OAK_FENCE, false);
            world.getBlockAt(x, y + 2, z).setType(r.nextBoolean() ? Material.RED_CARPET : Material.WHITE_CARPET, false);
            world.getBlockAt(x + 1, y, z).setType(Material.BARREL, false);
            world.getBlockAt(x, y, z + 1).setType(Material.CRAFTING_TABLE, false);
        }
        world.getBlockAt(bx + size / 2, y, bz + size / 2).setType(Material.BELL, false);
        lantern(bx + size / 2, y + 3, bz + size / 2);
        return new Location(world, bx + size / 2.0, y + 1, bz + size / 2.0);
    }

    private Location warehouse(int bx, int y, int bz, int size, Random r) {
        Location door = house(bx, y, bz, size, 6, Material.DEEPSLATE_BRICKS, Material.COBBLED_DEEPSLATE_SLAB, r, true);
        int x2 = bx + size - 1;
        int z2 = bz + size - 1;
        for (int x = bx + 1; x < x2; x++) {
            for (int z = bz + 1; z < z2; z++) {
                if (r.nextInt(5) == 0) world.getBlockAt(x, y + 1, z).setType(Material.BARREL, false);
                else if (r.nextInt(9) == 0) world.getBlockAt(x, y + 1, z).setType(Material.HAY_BLOCK, false);
            }
        }
        for (int h = 2; h <= 4; h += 2) {
            for (int x = bx; x <= x2; x += 2) world.getBlockAt(x, y + h, bz).setType(Material.IRON_BARS, false);
        }
        world.getBlockAt(bx + 1, y + 1, bz + 1).setType(Material.IRON_CHAIN, false);
        return door;
    }

    private Location greenhouse(int bx, int y, int bz, int size, Random r) {
        int x2 = bx + size - 1;
        int z2 = bz + size - 1;
        for (int x = bx; x <= x2; x++) {
            for (int z = bz; z <= z2; z++) {
                world.getBlockAt(x, y - 1, z).setType(Material.COARSE_DIRT, false);
                boolean edge = x == bx || x == x2 || z == bz || z == z2;
                if (edge) {
                    for (int h = 1; h <= 3; h++) {
                        Material m = r.nextInt(4) == 0 ? Material.AIR : Material.GLASS_PANE;
                        world.getBlockAt(x, y + h, z).setType(m, false);
                    }
                } else {
                    world.getBlockAt(x, y, z).setType(r.nextInt(3) == 0 ? Material.FARMLAND : Material.COARSE_DIRT, false);
                    if (r.nextInt(4) == 0) world.getBlockAt(x, y + 1, z).setType(Material.DEAD_BUSH, false);
                }
            }
        }
        for (int x = bx; x <= x2; x++) {
            for (int z = bz; z <= z2; z++) {
                if (r.nextInt(3) == 0) continue;
                world.getBlockAt(x, y + 4, z).setType(Material.GLASS, false);
            }
        }
        world.getBlockAt(bx + size / 2, y + 1, bz).setType(Material.AIR, false);
        world.getBlockAt(bx + size / 2, y + 2, bz).setType(Material.AIR, false);
        return new Location(world, bx + size / 2.0, y + 1, bz + 1.5);
    }

    /** A hidden grow room under a building, reached by a ladder shaft. */
    private void basementFarm(int bx, int y, int bz, int size, Random r, String label) {
        // Keep the whole room inside the world, even when the district sits near bedrock.
        int floor = Math.max(world.getMinHeight() + 2, y - 6);
        if (floor + 5 >= y) return;
        int x2 = bx + size - 2;
        int z2 = bz + size - 2;
        for (int x = bx + 1; x <= x2; x++) {
            for (int z = bz + 1; z <= z2; z++) {
                for (int h = 0; h <= 4; h++) {
                    world.getBlockAt(x, floor + h, z).setType(Material.AIR, false);
                }
                world.getBlockAt(x, floor - 1, z).setType(Material.COBBLED_DEEPSLATE, false);
                world.getBlockAt(x, floor + 5, z).setType(Material.DEEPSLATE_BRICKS, false);
            }
        }
        for (int x = bx + 2; x < x2; x++) {
            for (int z = bz + 2; z < z2; z++) {
                if ((x + z) % 2 == 0) {
                    world.getBlockAt(x, floor, z).setType(Material.FARMLAND, false);
                } else if (r.nextInt(5) == 0) {
                    world.getBlockAt(x, floor, z).setType(Material.WATER, false);
                }
            }
        }
        for (int x = bx + 2; x < x2; x += 3) {
            for (int z = bz + 2; z < z2; z += 3) {
                world.getBlockAt(x, floor + 4, z).setType(Material.GLOWSTONE, false);
            }
        }
        // Ladder shaft from the ground floor.
        int sx = bx + 1;
        int sz = bz + 1;
        for (int h = floor; h < y + 1; h++) {
            world.getBlockAt(sx, h, sz).setType(Material.AIR, false);
            world.getBlockAt(sx, h, sz).setType(Material.LADDER, false);
        }
        world.getBlockAt(sx, y + 1, sz).setType(Material.AIR, false);
        anchors.put("basement_" + label, new Location(world, sx + 0.5, floor + 1, sz + 0.5));
    }

    private void tavernInterior(int bx, int y, int bz, int size, Random r) {
        int x2 = bx + size - 2;
        for (int x = bx + 2; x < x2; x++) {
            world.getBlockAt(x, y + 1, bz + 2).setType(Material.DARK_OAK_SLAB, false);
        }
        world.getBlockAt(bx + 2, y + 1, bz + 4).setType(Material.BREWING_STAND, false);
        world.getBlockAt(bx + 3, y + 1, bz + 4).setType(Material.BARREL, false);
        for (int i = 0; i < 3; i++) {
            int x = bx + 2 + r.nextInt(Math.max(1, size - 4));
            int z = bz + 4 + r.nextInt(2);
            world.getBlockAt(x, y + 1, z).setType(Material.SPRUCE_FENCE, false);
        }
        world.getBlockAt(bx + size / 2, y + 3, bz + size / 2).setType(Material.LANTERN, false);
    }

    private void clinicInterior(int bx, int y, int bz, int size) {
        world.getBlockAt(bx + 2, y + 1, bz + 2).setType(Material.WHITE_BED, false);
        world.getBlockAt(bx + 2, y + 1, bz + 4).setType(Material.WHITE_BED, false);
        world.getBlockAt(bx + 4, y + 1, bz + 2).setType(Material.BREWING_STAND, false);
        world.getBlockAt(bx + 4, y + 1, bz + 3).setType(Material.CAULDRON, false);
        world.getBlockAt(bx + size / 2, y + 3, bz + size / 2).setType(Material.SEA_LANTERN, false);
    }

    private void apartmentInterior(int bx, int y, int bz, int size, Random r) {
        for (int floor = 1; floor < 9; floor += 4) {
            world.getBlockAt(bx + 2, y + floor, bz + 2).setType(Material.RED_BED, false);
            world.getBlockAt(bx + size - 3, y + floor, bz + size - 3).setType(Material.CRAFTING_TABLE, false);
            if (r.nextBoolean()) world.getBlockAt(bx + 3, y + floor, bz + size - 3).setType(Material.FURNACE, false);
            world.getBlockAt(bx + size / 2, y + floor + 2, bz + size / 2).setType(Material.LANTERN, false);
        }
        // Ladder between floors.
        for (int h = 1; h < 9; h++) world.getBlockAt(bx + 1, y + h, bz + size - 2).setType(Material.LADDER, false);
    }

    private void workshopInterior(int bx, int y, int bz, int size, Random r) {
        Material[] kit = {Material.SMITHING_TABLE, Material.GRINDSTONE, Material.STONECUTTER,
                Material.LOOM, Material.FURNACE, Material.BARREL, Material.CRAFTING_TABLE};
        for (int i = 0; i < 4; i++) {
            int x = bx + 2 + r.nextInt(Math.max(1, size - 4));
            int z = bz + 2 + r.nextInt(Math.max(1, size - 4));
            world.getBlockAt(x, y + 1, z).setType(kit[r.nextInt(kit.length)], false);
        }
        world.getBlockAt(bx + size / 2, y + 3, bz + size / 2).setType(Material.LANTERN, false);
    }

    /** A dark back alley between two lots, with a drop box at the end. */
    private void alley(int cell, Random r) {
        int y = origin.getBlockY() + 1;
        int ax = origin.getBlockX() - cell / 2;
        int az = origin.getBlockZ() + cell + 2;
        for (int i = 0; i < cell; i++) {
            for (int h = 1; h <= 4; h++) {
                world.getBlockAt(ax - 1, y + h, az + i).setType(Material.MOSSY_COBBLESTONE, false);
                world.getBlockAt(ax + 1, y + h, az + i).setType(Material.CRACKED_STONE_BRICKS, false);
            }
            world.getBlockAt(ax, y - 1, az + i).setType(Material.COBBLESTONE, false);
            if (r.nextInt(4) == 0) world.getBlockAt(ax, y + 4, az + i).setType(Material.COBWEB, false);
        }
        Location box = new Location(world, ax, y, az + cell - 1);
        dropBox(box, "alley");
        anchors.put("alley", new Location(world, ax + 0.5, y, az + 1.5));
    }

    /** An outlying settlement used as a delivery destination. */
    private void outpost(Random r) {
        int distance = plugin.getConfig().getInt("district.outpost-distance", 120);
        double angle = r.nextDouble() * Math.PI * 2;
        int x = origin.getBlockX() + (int) (Math.cos(angle) * distance);
        int z = origin.getBlockZ() + (int) (Math.sin(angle) * distance);
        int y = world.getHighestBlockYAt(x, z);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.COBBLESTONE, false);
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                if (edge) {
                    for (int h = 1; h <= 3; h++) {
                        world.getBlockAt(x + dx, y + h, z + dz).setType(Material.SPRUCE_PLANKS, false);
                    }
                }
                world.getBlockAt(x + dx, y + 4, z + dz).setType(Material.SPRUCE_SLAB, false);
            }
        }
        world.getBlockAt(x, y + 1, z - 3).setType(Material.AIR, false);
        world.getBlockAt(x, y + 2, z - 3).setType(Material.AIR, false);
        Location box = new Location(world, x, y + 1, z);
        dropBox(box, "outpost");
        anchors.put("outpost", new Location(world, x + 0.5, y + 1, z + 1.5));
    }

    /** Places a tagged barrel that accepts deliveries. */
    private void dropBox(Location l, String name) {
        Block b = l.getBlock();
        b.setType(Material.BARREL, false);
        if (b.getState() instanceof TileState state) {
            state.getPersistentDataContainer().set(keys.dropBox, PersistentDataType.STRING, name);
            state.update();
        }
        dropBoxes.add(l);
        dropBoxNames.put(key(l), name);
        world.getBlockAt(l.getBlockX(), l.getBlockY() + 1, l.getBlockZ()).setType(Material.SOUL_LANTERN, false);
    }

    /** Applies the current condition to the streets: repairs or decay. */
    public int refresh() {
        if (!exists()) return 0;
        // Never make unjournaled street edits in a converted village.
        if(takeoverId!=null) return 0;
        int cell = plugin.getConfig().getInt("district.cell-size", 9);
        int radius = plugin.getConfig().getInt("district.radius-cells", 3);
        int half = cell * radius + cell / 2;
        int y = origin.getBlockY();
        Random r = new Random(seed + condition);
        int changed = 0;
        boolean good = condition >= 60;

        for (int i = 0; i < 120; i++) {
            int x = origin.getBlockX() + r.nextInt(half * 2) - half;
            int z = origin.getBlockZ() + r.nextInt(half * 2) - half;
            Block ground = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            if (good) {
                if (ground.getType() == Material.GRAVEL || ground.getType() == Material.COARSE_DIRT) {
                    ground.setType(Material.STONE_BRICKS, false);
                    changed++;
                }
                if (above.getType() == Material.COBWEB || above.getType() == Material.DEAD_BUSH) {
                    above.setType(Material.AIR, false);
                    changed++;
                }
                if (above.getType().isAir() && r.nextInt(12) == 0) {
                    above.setType(Material.LANTERN, false);
                    changed++;
                }
            } else {
                if (ground.getType() == Material.STONE_BRICKS && r.nextInt(3) == 0) {
                    ground.setType(Material.CRACKED_STONE_BRICKS, false);
                    changed++;
                }
                if (above.getType().isAir() && r.nextInt(10) == 0) {
                    above.setType(r.nextBoolean() ? Material.COBWEB : Material.DEAD_BUSH, false);
                    changed++;
                }
                if (above.getType() == Material.LANTERN && r.nextInt(4) == 0) {
                    above.setType(Material.AIR, false);
                    changed++;
                }
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------ persistence

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        convertedVillages.clear();restoredVillages.clear();
        var history=y.getConfigurationSection("converted-villages");
        if(history!=null) for(String id:history.getKeys(false)) {
            var s=history.getConfigurationSection(id);World w=Bukkit.getWorld(s.getString("world",""));
            if(w!=null) convertedVillages.put(id,new Location(w,s.getInt("x"),s.getInt("y"),s.getInt("z")));
        }
        restoredVillages.addAll(y.getStringList("restored-villages"));
        takeoverId=y.getString("takeover-id");
        if(y.isConfigurationSection("village-bounds")) villageBounds=new org.bukkit.util.BoundingBox(
                y.getDouble("village-bounds.min-x"),y.getDouble("village-bounds.min-y"),y.getDouble("village-bounds.min-z"),
                y.getDouble("village-bounds.max-x"),y.getDouble("village-bounds.max-y"),y.getDouble("village-bounds.max-z"));
        world = Bukkit.getWorld(y.getString("world", ""));
        if (world == null) return;
        origin = new Location(world, y.getInt("x"), y.getInt("y"), y.getInt("z"));
        seed = y.getLong("seed");
        condition = y.getInt("condition", 25);
        ConfigurationSection a = y.getConfigurationSection("anchors");
        if (a != null) {
            for (String k : a.getKeys(false)) {
                ConfigurationSection s = a.getConfigurationSection(k);
                if (s == null) continue;
                anchors.put(k, new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z")));
            }
        }
        ConfigurationSection d = y.getConfigurationSection("drop-boxes");
        if (d != null) {
            for (String k : d.getKeys(false)) {
                ConfigurationSection s = d.getConfigurationSection(k);
                if (s == null) continue;
                Location l = new Location(world, s.getInt("x"), s.getInt("y"), s.getInt("z"));
                dropBoxes.add(l);
                dropBoxNames.put(key(l), s.getString("name", "drop"));
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for(var entry:convertedVillages.entrySet()) {
            String k="converted-villages."+entry.getKey();Location l=entry.getValue();
            y.set(k+".world",l.getWorld().getName());y.set(k+".x",l.getBlockX());y.set(k+".y",l.getBlockY());y.set(k+".z",l.getBlockZ());
        }
        y.set("restored-villages",new ArrayList<>(restoredVillages));y.set("takeover-id",takeoverId);
        if(villageBounds!=null) {
            y.set("village-bounds.min-x",villageBounds.getMinX());y.set("village-bounds.min-y",villageBounds.getMinY());y.set("village-bounds.min-z",villageBounds.getMinZ());
            y.set("village-bounds.max-x",villageBounds.getMaxX());y.set("village-bounds.max-y",villageBounds.getMaxY());y.set("village-bounds.max-z",villageBounds.getMaxZ());
        }
        if(exists()) {
        y.set("world", world.getName());
        y.set("x", origin.getBlockX());
        y.set("y", origin.getBlockY());
        y.set("z", origin.getBlockZ());
        y.set("seed", seed);
        y.set("condition", condition);
        for (Map.Entry<String, Location> e : anchors.entrySet()) {
            y.set("anchors." + e.getKey() + ".x", e.getValue().getX());
            y.set("anchors." + e.getKey() + ".y", e.getValue().getY());
            y.set("anchors." + e.getKey() + ".z", e.getValue().getZ());
        }
        int i = 0;
        for (Location l : dropBoxes) {
            y.set("drop-boxes.d" + i + ".x", l.getBlockX());
            y.set("drop-boxes.d" + i + ".y", l.getBlockY());
            y.set("drop-boxes.d" + i + ".z", l.getBlockZ());
            y.set("drop-boxes.d" + i + ".name", dropBoxNames.getOrDefault(key(l), "drop"));
            i++;
        }
        }
        try {
            TakeoverSnapshot.atomic(file.toPath(),y.saveToString());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save district.yml: " + ex.getMessage());
        }
    }

    private static String key(Location l) {
        return l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }
}
