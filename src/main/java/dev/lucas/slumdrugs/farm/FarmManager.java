package dev.lucas.slumdrugs.farm;

import dev.lucas.slumdrugs.Msg;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.drug.Items;
import dev.lucas.slumdrugs.drug.Quality;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Growing as a profession. Each plot is scored every tick on light, temperature, biome,
 * moisture and shelter. The score drives growth speed and, averaged over the plant's life,
 * the quality of the harvest.
 */
public final class FarmManager {

    /** Blocks that act as grow lamps when placed within 4 blocks of a plot. */
    private static final List<Material> LAMPS = List.of(
            Material.GLOWSTONE, Material.SEA_LANTERN, Material.SHROOMLIGHT,
            Material.OCHRE_FROGLIGHT, Material.LANTERN, Material.SOUL_LANTERN);

    private final SlumDrugsPlugin plugin;
    private final Map<String, Plot> plots = new LinkedHashMap<>();
    private final File file;
    private BukkitTask task;

    public FarmManager(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plots.yml");
    }

    public void start() {
        int seconds = Math.max(1, plugin.getConfig().getInt("farming.tick-seconds", 5));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 60L, seconds * 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public int plotCount() { return plots.size(); }

    public Plot at(Location l) { return plots.get(Plot.key(l)); }

    public List<Plot> near(Location l, double radius) {
        List<Plot> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Plot p : plots.values()) {
            if (p.location.getWorld().equals(l.getWorld()) && p.location.distanceSquared(l) <= r2) out.add(p);
        }
        return out;
    }

    /** Registers an already journaled village crop without editing the world. */
    public void adoptVillageCrop(Location location, Drug drug) {
        if (drug == null || !drug.isGrown() || location.getBlock().getType() != drug.crop.block || at(location) != null) return;
        Plot plot = new Plot(location, drug.id, new UUID(0, 0), "Village");
        plots.put(plot.key(), plot);
    }

    // ------------------------------------------------------------------ planting

    /** Attempts to plant on the block the player clicked. Returns true if a plot was created. */
    public boolean plant(Player player, Block clicked, Drug drug) {
        if (!drug.isGrown()) return false;
        Block soil = clicked;
        Block above = soil.getRelative(0, 1, 0);
        if (soil.getType() != drug.crop.soil) {
            Msg.send(player, "<red>" + drug.name + " only grows on " + Items.pretty(drug.crop.soil) + ".</red>");
            return false;
        }
        if (!above.getType().isAir()) {
            Msg.send(player, "<red>There is no room for the plant.</red>");
            return false;
        }
        if (plots.containsKey(Plot.key(above.getLocation()))) return false;

        BlockData data = Bukkit.createBlockData(drug.crop.block);
        if (data instanceof Ageable age) age.setAge(0);
        above.setBlockData(data, false);

        Plot plot = new Plot(above.getLocation(), drug.id, player.getUniqueId(), player.getName());
        plots.put(plot.key(), plot);

        player.playSound(above.getLocation(), Sound.ITEM_CROP_PLANT, 0.8f, 1.0f);
        double score = score(plot, drug);
        Msg.send(player, "Planted " + drug.colored() + "<gray>. Conditions here: </gray>" + describe(score));
        return true;
    }

    /** Feeds a plot with fertilizer. Returns true if it was accepted. */
    public boolean fertilize(Player player, Block block) {
        Plot plot = at(block.getLocation());
        if (plot == null) return false;
        int max = plugin.getConfig().getInt("farming.fertilizer-max-charges", 3);
        if (plot.fertilizer >= max) {
            Msg.send(player, "<gray>This plant has had enough fertilizer.</gray>");
            return true;
        }
        plot.fertilizer++;
        plot.health = Math.min(1.0, plot.health + 0.25);
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.6, 0.5), 12, 0.3, 0.3, 0.3, 0.01);
        player.playSound(block.getLocation(), Sound.ITEM_BONE_MEAL_USE, 0.8f, 1.1f);
        Msg.send(player, "<green>Fertilized.</green> <gray>" + (max - plot.fertilizer) + " more doses will be accepted.</gray>");
        return true;
    }

    // ------------------------------------------------------------------ harvest

    /**
     * Harvests a plot. Returns the drops, or an empty list if the plant was not ready.
     * The caller is responsible for clearing the block.
     */
    public List<ItemStack> harvest(Plot plot, Player harvester) {
        Drug drug = plugin.drugs().get(plot.drug);
        plots.remove(plot.key());
        if (drug == null) return List.of();

        if (!plot.ready()) {
            if (harvester != null) Msg.send(harvester, "<gray>You pull it up early. The harvest is ruined.</gray>");
            return List.of();
        }

        int quality = Quality.clamp(plot.conditionAverage() * 100 * (0.6 + 0.4 * plot.health));
        int yield = drug.crop.yield;
        if (plot.fertilizer > 0) yield += ThreadLocalRandom.current().nextInt(plot.fertilizer + 1);
        if (plot.health < 0.4) yield = Math.max(1, yield - 1);

        String grower = plot.planterName == null ? "unknown" : plot.planterName;
        List<ItemStack> drops = new ArrayList<>();
        drops.add(plugin.items().raw(drug, quality, grower, Items.newBatchId(), yield));
        if (ThreadLocalRandom.current().nextDouble() < 0.5) drops.add(plugin.items().seed(drug, 1));

        if (harvester != null) {
            Quality.Grade g = Quality.Grade.of(quality);
            Msg.send(harvester, "Harvested <white>" + yield + "x</white> " + drug.colored()
                    + " <gray>at</gray> " + g.colored() + " <dark_gray>(" + quality + ")</dark_gray>");
            harvester.playSound(plot.location, Sound.BLOCK_CROP_BREAK, 0.8f, 1.0f);
        }
        return drops;
    }

    /** Removes a plot without dropping anything, e.g. when the block is destroyed by other means. */
    public void remove(Location l) {
        plots.remove(Plot.key(l));
    }

    public int removeNear(Location l, double radius) {
        int n = 0;
        for (Plot p : near(l, radius)) {
            plots.remove(p.key());
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        int seconds = Math.max(1, plugin.getConfig().getInt("farming.tick-seconds", 5));
        double fertBoost = plugin.getConfig().getDouble("farming.fertilizer-boost", 0.35);
        List<Plot> dead = new ArrayList<>();

        for (Plot plot : new ArrayList<>(plots.values())) {
            Location l = plot.location;
            World w = l.getWorld();
            if (w == null || !w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;

            Drug drug = plugin.drugs().get(plot.drug);
            Block block = l.getBlock();
            if (drug == null || block.getType() != drug.crop.block) {
                dead.add(plot);
                continue;
            }

            double score = score(plot, drug);
            plot.conditionSum += score;
            plot.conditionSamples++;

            if (score < 0.35) plot.health = Math.max(0, plot.health - 0.012);
            else if (score > 0.7) plot.health = Math.min(1.0, plot.health + 0.006);

            if (plot.health <= 0) {
                wither(plot, block);
                dead.add(plot);
                continue;
            }

            if (!plot.ready()) {
                double totalSeconds = drug.crop.growthMinutes * 60.0;
                double rate = (seconds / totalSeconds) * (0.25 + 1.25 * score);
                if (plot.fertilizer > 0) rate *= 1.0 + fertBoost;
                plot.progress = Math.min(1.0, plot.progress + rate);
                updateStage(plot, block, drug);
            }

            signs(plot, block, score);
        }

        for (Plot p : dead) plots.remove(p.key());
    }

    private void updateStage(Plot plot, Block block, Drug drug) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable age)) return;
        int stage = (int) Math.round(plot.progress * age.getMaximumAge());
        if (stage == plot.lastStage) return;
        plot.lastStage = stage;
        age.setAge(Math.min(age.getMaximumAge(), stage));
        block.setBlockData(age, false);
    }

    /** Visible signs of poor health, so a struggling plot can be spotted without a menu. */
    private void signs(Plot plot, Block block, double score) {
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        if (plot.health < 0.5) {
            block.getWorld().spawnParticle(Particle.SMOKE, at, 2, 0.2, 0.2, 0.2, 0.0);
        } else if (plot.ready()) {
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 1, 0.3, 0.3, 0.3, 0.0);
        } else if (score > 0.75 && ThreadLocalRandom.current().nextInt(4) == 0) {
            block.getWorld().spawnParticle(Particle.COMPOSTER, at, 1, 0.2, 0.2, 0.2, 0.0);
        }

        if (plot.health < 0.35 && System.currentTimeMillis() - plot.lastWarned > 120_000L) {
            plot.lastWarned = System.currentTimeMillis();
            Player owner = Bukkit.getPlayer(plot.planter);
            if (owner != null && owner.getWorld().equals(block.getWorld())
                    && owner.getLocation().distanceSquared(block.getLocation()) < 4096) {
                Msg.send(owner, "<gold>One of your plants is wilting.</gold> <gray>Check light, water and climate.</gray>");
            }
        }
    }

    private void wither(Plot plot, Block block) {
        block.setType(Material.DEAD_BUSH, false);
        block.getWorld().spawnParticle(Particle.SMOKE, block.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.01);
        Player owner = Bukkit.getPlayer(plot.planter);
        if (owner != null) Msg.send(owner, "<red>A plant died.</red> <gray>Conditions were wrong for too long.</gray>");
    }

    // ------------------------------------------------------------------ conditions

    /** Composite growing score, 0..1. Also used by /drugs soil. */
    public double score(Plot plot, Drug drug) {
        Block block = plot.location.getBlock();
        Block soil = block.getRelative(0, -1, 0);
        boolean sheltered = sheltered(block);

        double light = lightScore(block, drug);
        double temp = tempScore(block, drug, sheltered);
        double biome = biomeScore(block, drug);
        double water = moistureScore(soil, drug);

        double score = light * 0.3 + temp * 0.25 + biome * 0.2 + water * 0.25;
        if (sheltered) score = Math.min(1.0, score + 0.05);
        return Math.max(0, Math.min(1, score));
    }

    private double lightScore(Block block, Drug drug) {
        int light = block.getLightLevel();
        if (lampNear(block)) light = Math.max(light, 14);
        if (light >= drug.crop.lightMin) return 1.0;
        return Math.max(0, light / (double) Math.max(1, drug.crop.lightMin));
    }

    private boolean lampNear(Block block) {
        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -4; z <= 4; z++) {
                    if (LAMPS.contains(block.getRelative(x, y, z).getType())) return true;
                }
            }
        }
        return false;
    }

    private double tempScore(Block block, Drug drug, boolean sheltered) {
        double t = block.getWorld().getTemperature(block.getX(), block.getY(), block.getZ());
        double min = drug.crop.tempMin;
        double max = drug.crop.tempMax;
        if (sheltered) {
            // A greenhouse pulls the effective temperature toward the plant's ideal range.
            double ideal = (min + max) / 2.0;
            t = t + (ideal - t) * 0.6;
        }
        if (t >= min && t <= max) return 1.0;
        double distance = t < min ? min - t : t - max;
        return Math.max(0, 1.0 - distance * 1.2);
    }

    private double biomeScore(Block block, Drug drug) {
        if (drug.crop.preferredBiomes.isEmpty()) return 1.0;
        String key = block.getBiome().getKey().getKey().toLowerCase(Locale.ROOT);
        return drug.crop.preferredBiomes.contains(key) ? 1.0 : 0.55;
    }

    private double moistureScore(Block soil, Drug drug) {
        if (soil.getBlockData() instanceof Farmland farmland) {
            return Math.max(0.2, farmland.getMoisture() / (double) farmland.getMaximumMoisture());
        }
        if (soil.getType() == drug.crop.soil) return waterNear(soil) ? 1.0 : 0.8;
        return 0.4;
    }

    private boolean waterNear(Block block) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = -1; y <= 1; y++) {
                    if (block.getRelative(x, y, z).getType() == Material.WATER) return true;
                }
            }
        }
        return false;
    }

    /** True if something solid or glass covers the plot: greenhouse or underground farm. */
    private boolean sheltered(Block block) {
        int height = plugin.getConfig().getInt("farming.greenhouse-scan-height", 8);
        for (int y = 1; y <= height; y++) {
            Material m = block.getRelative(0, y, 0).getType();
            if (m.isAir()) continue;
            return m.isSolid() || m.name().contains("GLASS") || m.name().contains("LEAVES");
        }
        return false;
    }

    /** Human-readable report used by the soil-check command. */
    public void report(Player player, Block block, Drug drug) {
        Plot plot = at(block.getLocation());
        Plot probe = plot != null ? plot : new Plot(block.getLocation(), drug.id, player.getUniqueId(), player.getName());
        Block soil = block.getRelative(0, -1, 0);
        boolean sheltered = sheltered(block);

        Msg.raw(player, "<gold><bold>Soil check</bold></gold> <gray>for</gray> " + drug.colored());
        Msg.raw(player, " <gray>Light      </gray> " + Msg.gauge(lightScore(probe, drug), 1, "yellow")
                + (lampNear(block) ? " <dark_gray>(grow lamp)</dark_gray>" : ""));
        Msg.raw(player, " <gray>Temperature</gray> " + Msg.gauge(tempScore(block, drug, sheltered), 1, "gold")
                + (sheltered ? " <dark_gray>(sheltered)</dark_gray>" : ""));
        Msg.raw(player, " <gray>Biome      </gray> " + Msg.gauge(biomeScore(block, drug), 1, "green")
                + " <dark_gray>(" + block.getBiome().getKey().getKey() + ")</dark_gray>");
        Msg.raw(player, " <gray>Moisture   </gray> " + Msg.gauge(moistureScore(soil, drug), 1, "aqua"));
        double total = score(probe, drug);
        Msg.raw(player, " <gray>Overall    </gray> " + describe(total));
        if (plot != null) {
            Msg.raw(player, " <gray>Growth     </gray> " + Msg.gauge(plot.progress, 1, "white")
                    + "  <gray>Health</gray> " + Msg.gauge(plot.health, 1, "red"));
            Msg.raw(player, " <gray>Expected grade</gray> " + Quality.Grade.of(Quality.clamp(plot.conditionAverage() * 100)).colored());
        }
    }

    private double lightScore(Plot plot, Drug drug) {
        return lightScore(plot.location.getBlock(), drug);
    }

    private static String describe(double score) {
        String word = score > 0.85 ? "<green>excellent</green>"
                : score > 0.65 ? "<green>good</green>"
                : score > 0.45 ? "<yellow>workable</yellow>"
                : score > 0.25 ? "<gold>poor</gold>" : "<red>hostile</red>";
        return Msg.gauge(score, 1, "green") + " " + word;
    }

    // ------------------------------------------------------------------ persistence

    public void load() {
        plots.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            World w = Bukkit.getWorld(s.getString("world", ""));
            if (w == null) continue;
            Location l = new Location(w, s.getInt("x"), s.getInt("y"), s.getInt("z"));
            UUID planter;
            try {
                planter = UUID.fromString(s.getString("planter", ""));
            } catch (IllegalArgumentException ex) {
                planter = new UUID(0, 0);
            }
            Plot p = new Plot(l, s.getString("drug", ""), planter, s.getString("planter-name", "unknown"));
            p.progress = s.getDouble("progress");
            p.conditionSum = s.getDouble("condition-sum");
            p.conditionSamples = s.getInt("condition-samples");
            p.health = s.getDouble("health", 1.0);
            p.fertilizer = s.getInt("fertilizer");
            plots.put(p.key(), p);
        }
        plugin.getLogger().info("Loaded " + plots.size() + " plots.");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        Map<String, Integer> counter = new HashMap<>();
        for (Plot p : plots.values()) {
            String key = p.key().replace(':', '_');
            counter.merge(key, 1, Integer::sum);
            y.set(key + ".world", p.location.getWorld().getName());
            y.set(key + ".x", p.location.getBlockX());
            y.set(key + ".y", p.location.getBlockY());
            y.set(key + ".z", p.location.getBlockZ());
            y.set(key + ".drug", p.drug);
            y.set(key + ".planter", p.planter.toString());
            y.set(key + ".planter-name", p.planterName);
            y.set(key + ".progress", p.progress);
            y.set(key + ".condition-sum", p.conditionSum);
            y.set(key + ".condition-samples", p.conditionSamples);
            y.set(key + ".health", p.health);
            y.set(key + ".fertilizer", p.fertilizer);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save plots.yml: " + ex.getMessage());
        }
    }
}
