package dev.lucas.slumdrugs.world;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.loot.Lootable;
import org.bukkit.util.BoundingBox;
import java.util.*;
import java.util.function.Predicate;

/** Finds roofed, enclosed rooms from beds/workstations, without assuming village templates. */
public final class HouseSurvey {
    public record Cell(int x,int z) {}
    public record House(World world,int minX,int maxX,int y,int minZ,int maxZ,List<Cell> interior) {
        public Location centre() { return new Location(world,(minX+maxX+1)/2.0,y,(minZ+maxZ+1)/2.0); }
        public boolean overlaps(House other) { return minX<=other.maxX && maxX>=other.minX && minZ<=other.maxZ && maxZ>=other.minZ; }
    }
    public static List<Cell> flood(Cell seed,Predicate<Cell> room,int limit) {
        var seen=new LinkedHashSet<Cell>(); var queue=new ArrayDeque<Cell>(); queue.add(seed);
        while(!queue.isEmpty()) {
            Cell c=queue.removeFirst();
            if(seen.contains(c) || !room.test(c)) continue;
            if(Math.abs(c.x-seed.x)>12 || Math.abs(c.z-seed.z)>12 || seen.size()>=limit) return List.of();
            seen.add(c); queue.add(new Cell(c.x+1,c.z)); queue.add(new Cell(c.x-1,c.z));
            queue.add(new Cell(c.x,c.z+1)); queue.add(new Cell(c.x,c.z-1));
        }
        return List.copyOf(seen);
    }
    public static List<House> find(World w,BoundingBox bounds,TakeoverSafety safety) {
        var seeds=new ArrayList<Block>();
        // Beds are ordinary blocks in 26.3, not tile entities. Read a bounded
        // snapshot volume so bed-only houses remain discoverable on this version.
        int low=Math.max(w.getMinHeight(),(int)Math.floor(bounds.getMinY()));
        int high=Math.min(w.getMaxHeight()-8,(int)Math.ceil(bounds.getMaxY()));
        if(high-low>96) return List.of();
        var indicators=EnumSet.of(Material.BREWING_STAND,Material.FURNACE,Material.BLAST_FURNACE,
                Material.SMOKER,Material.FLETCHING_TABLE,Material.CARTOGRAPHY_TABLE,
                Material.SMITHING_TABLE,Material.GRINDSTONE,Material.STONECUTTER);
        for(Material m:Material.values())if(m.name().endsWith("_BED") || m.name().endsWith("_DOOR"))indicators.add(m);
        for(int cx=((int)Math.floor(bounds.getMinX()))>>4;cx<=((int)Math.floor(bounds.getMaxX()))>>4;cx++)
            for(int cz=((int)Math.floor(bounds.getMinZ()))>>4;cz<=((int)Math.floor(bounds.getMaxZ()))>>4;cz++) {
                if(!w.isChunkLoaded(cx,cz)) continue;
                var snapshot=w.getChunkAt(cx,cz).getChunkSnapshot(false,false,false);
                for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                    int wx=(cx<<4)+x,wz=(cz<<4)+z;
                    if(wx<bounds.getMinX() || wx>bounds.getMaxX() || wz<bounds.getMinZ() || wz>bounds.getMaxZ())continue;
                    for(int y=low;y<=high;y++)if(indicators.contains(snapshot.getBlockType(x,y,z))) {
                        seeds.add(w.getBlockAt(wx,y,wz));
                        if(seeds.size()>1024)return List.of();
                    }
                }
            }
        seeds.sort(java.util.Comparator.comparingInt(Block::getX).thenComparingInt(Block::getZ));
        var houses=new ArrayList<House>();
        for(Block seed:seeds) {
            int y=seed.getY();
            List<Cell> room=List.of();
            for(int dx=-1;dx<=1 && room.isEmpty();dx++) for(int dz=-1;dz<=1 && room.isEmpty();dz++)
                room=flood(new Cell(seed.getX()+dx,seed.getZ()+dz),c->inside(w,c.x,y,c.z),144);
            if(room.size()<4) continue;
            int minX=room.stream().mapToInt(Cell::x).min().orElseThrow()-1,maxX=room.stream().mapToInt(Cell::x).max().orElseThrow()+1;
            int minZ=room.stream().mapToInt(Cell::z).min().orElseThrow()-1,maxZ=room.stream().mapToInt(Cell::z).max().orElseThrow()+1;
            House h=new House(w,minX,maxX,y,minZ,maxZ,room);
            if(houses.stream().anyMatch(h::overlaps) || !safe(h,safety)) continue;
            houses.add(h);
        }
        return houses;
    }
    private static boolean inside(World w,int x,int y,int z) {
        if(!w.isChunkLoaded(x>>4,z>>4) || y+7>=w.getMaxHeight() || y-1<w.getMinHeight()) return false;
        Block b=w.getBlockAt(x,y,z);
        boolean furnishing=b.getType().name().endsWith("_BED") || b.getState() instanceof InventoryHolder
                || Set.of(Material.BREWING_STAND,Material.CAULDRON,Material.COMPOSTER).contains(b.getType());
        if((!b.isPassable() && !furnishing) || b.getType().name().endsWith("_DOOR") || b.isLiquid()
                || !w.getBlockAt(x,y-1,z).getType().isSolid() || !w.getBlockAt(x,y+1,z).isPassable()) return false;
        for(int dy=2;dy<=6;dy++) if(w.getBlockAt(x,y+dy,z).getType().isSolid()) return true;
        return false;
    }
    private static boolean safe(House h,TakeoverSafety safety) {
        boolean door=false;
        for(int x=h.minX;x<=h.maxX;x++) for(int z=h.minZ;z<=h.maxZ;z++) {
            if(!h.world.isChunkLoaded(x>>4,z>>4)) return false;
            for(int y=h.y-1;y<=h.y+6;y++) {
                Block b=h.world.getBlockAt(x,y,z);
                if(safety.edited(b) || safety.usedBed(b)) return false;
                if(b.getType().name().endsWith("_DOOR")) door=true;
                if(b.getState() instanceof InventoryHolder inventory) {
                    if(b.getState() instanceof TileState tile && !tile.getPersistentDataContainer().isEmpty()) return false;
                    if(b.getState() instanceof Lootable loot && loot.getLootTable()!=null) return false;
                    if(!inventory.getInventory().isEmpty()) return false;
                } else if(b.getState() instanceof TileState tile) {
                    if(!(tile.getBlockData() instanceof org.bukkit.block.data.type.Bed) || !tile.getPersistentDataContainer().isEmpty()) return false;
                } else if(!TakeoverSafety.palette(b.getType())) return false;
            }
        }
        // Existing respawn beds are another signal of use predating this plugin installation.
        for(var p:Bukkit.getOnlinePlayers()) {
            Location bed=p.getRespawnLocation();
            if(bed!=null && bed.getWorld().equals(h.world) && bed.getBlockX()>=h.minX && bed.getBlockX()<=h.maxX
                    && bed.getBlockZ()>=h.minZ && bed.getBlockZ()<=h.maxZ) return false;
        }
        return door;
    }
}
