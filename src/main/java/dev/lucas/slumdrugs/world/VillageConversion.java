package dev.lucas.slumdrugs.world;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.type.Bed;
import java.util.*;

/** Builds a complete journal in memory before any block is modified. */
public final class VillageConversion {
    public final Map<String,Location> anchors=new LinkedHashMap<>();
    public final Map<String,Location> boxes=new LinkedHashMap<>();
    public final List<String> summary=new ArrayList<>();
    private final TakeoverSnapshot journal;
    private final TakeoverSafety safety;
    public VillageConversion(TakeoverSnapshot journal,TakeoverSafety safety) {this.journal=journal;this.safety=safety;}
    private Block block(HouseSurvey.House h,int x,int y,int z){return h.world().getBlockAt(x,y,z);}
    private boolean empty(Block b){return b.getType().isAir() && journal.planned(b).isAir() && safety.changeable(b);}

    public boolean convert(List<HouseSurvey.House> houses,org.bukkit.util.BoundingBox village) {
        if(houses.isEmpty()) return false;
        HouseSurvey.House den=houses.get(0);
        anchors.put("tavern",standing(den));anchors.put("market",outside(den));
        anchors.put("apartments_a",outside(den));
        wear(den,false);
        basement(den,"basement_tavern");
        if(!barrel(den,"tavern")) return false;
        summary.add("den at "+coords(den.centre()));
        if(houses.size()>=2) {
            var clinic=houses.get(1);anchors.put("clinic",standing(clinic));medical(clinic);
            anchors.put("apartments_b",outside(clinic));summary.add("clinic at "+coords(clinic.centre()));
        } else {
            // An existing cleric's position is used later by the populator when available.
            Location medic=den.world().getNearbyEntities(den.centre(),96,32,96).stream()
                    .filter(e->e instanceof org.bukkit.entity.Villager v && v.getProfession()==org.bukkit.entity.Villager.Profession.CLERIC)
                    .map(org.bukkit.entity.Entity::getLocation).findFirst().orElse(outside(den));
            anchors.put("clinic",medic); summary.add("one house: den inside, fixer outside, medic at cleric or outside den");
        }
        HouseSurvey.House stash=houses.size()>=3?houses.get(2):den;
        anchors.put("warehouse",houses.size()>=3?standing(stash):outside(den));
        if(houses.size()>=3) {wear(stash,true);basement(stash,"basement_warehouse");summary.add("stash house at "+coords(stash.centre()));}
        else {anchors.put("basement_warehouse",anchors.get("basement_tavern"));summary.add("stash and den share a building");}
        if(!barrel(stash,"warehouse")) return false;
        anchors.values().removeIf(Objects::isNull);
        Location gap=houses.size()>=2?midpoint(outside(den),outside(houses.get(1))):outside(den);
        Location alley=findOutdoor(gap,6);
        anchors.put("alley",alley==null?outside(den):alley);
        Location edge=new Location(den.world(),village.getMaxX()-2,den.y(),(village.getMinZ()+village.getMaxZ())/2);
        Location outpost=findOutdoor(edge,8);
        if(outpost==null) return false;
        journal.set(outpost.getBlock(),Material.BARREL);boxes.put("outpost",outpost);
        anchors.put("outpost",outpost.clone().add(1,0,0));
        anchors.put("tavern",standing(den));
        if(houses.size()>=2) anchors.put("clinic",standing(houses.get(1)));
        if(houses.size()>=3) anchors.put("warehouse",standing(stash));
        return boxes.size()==3;
    }
    private void wear(HouseSurvey.House h,boolean stash) {
        int replaced=0;
        for(int x=h.minX();x<=h.maxX();x++) for(int z=h.minZ();z<=h.maxZ();z++) {
            if(x!=h.minX() && x!=h.maxX() && z!=h.minZ() && z!=h.maxZ()) continue;
            for(int y=h.y();y<=h.y()+3;y++) {
                Block b=block(h,x,y,z);Material m=b.getType();
                if(m==Material.COBBLESTONE && (x+y+z)%3==0) journal.set(b,Material.MOSSY_COBBLESTONE);
                if(m==Material.STONE_BRICKS && (x+y+z)%3==0) journal.set(b,Material.CRACKED_STONE_BRICKS);
                if((m==Material.GLASS_PANE || m.name().endsWith("_STAINED_GLASS_PANE")) && replaced<2) {
                    if(stash) journal.set(b,Material.IRON_BARS);
                    else {
                        TrapDoor trap=(TrapDoor)Material.SPRUCE_TRAPDOOR.createBlockData();trap.setOpen(true);
                        trap.setFacing(x==h.minX()?BlockFace.EAST:x==h.maxX()?BlockFace.WEST:z==h.minZ()?BlockFace.SOUTH:BlockFace.NORTH);
                        journal.set(b,trap);
                    }
                    replaced++;
                }
            }
        }
        int webs=0;
        for(var cell:h.interior()) {
            Block head=block(h,cell.x(),h.y()+2,cell.z());
            if(empty(head) && (cell.x()==h.minX()+1 || cell.x()==h.maxX()-1)
                    && (cell.z()==h.minZ()+1 || cell.z()==h.maxZ()-1) && webs++<2) journal.set(head,Material.COBWEB);
            Block b=block(h,cell.x(),h.y()+1,cell.z());
            if(b.getType()==Material.TORCH) journal.set(b,Material.SOUL_TORCH);
            if(b.getType()==Material.LANTERN) journal.set(b,Material.SOUL_LANTERN);
            if(b.getType()==Material.WALL_TORCH) {
                var data=(org.bukkit.block.data.Directional)Material.SOUL_WALL_TORCH.createBlockData();
                data.setFacing(((org.bukkit.block.data.Directional)b.getBlockData()).getFacing());journal.set(b,data);
            }
        }
    }
    private boolean barrel(HouseSurvey.House h,String name) {
        for(var cell:h.interior()) {
            Block b=block(h,cell.x(),h.y(),cell.z());
            if(empty(b) && b.getRelative(BlockFace.UP).getType().isAir()
                    && (cell.x()==h.minX()+1 || cell.z()==h.minZ()+1)) {
                journal.set(b,Material.BARREL);boxes.put(name,b.getLocation());return true;
            }
        }
        return false;
    }
    private void medical(HouseSurvey.House h) {
        var materials=new ArrayDeque<>(List.of(Material.BREWING_STAND,Material.CAULDRON,Material.SEA_LANTERN));
        for(var c:h.interior()) {
            Block b=block(h,c.x(),h.y(),c.z());
            if(!materials.isEmpty() && empty(b) && (c.x()==h.minX()+1 || c.z()==h.minZ()+1)) journal.set(b,materials.remove());
        }
        // Keep existing beds. Add one only where both halves fit in unoccupied interior cells.
        if(h.interior().stream().anyMatch(c->block(h,c.x(),h.y(),c.z()).getType().name().endsWith("_BED"))) return;
        for(var c:h.interior()) {
            Block foot=block(h,c.x(),h.y(),c.z()),head=foot.getRelative(BlockFace.EAST);
            if(!h.interior().contains(new HouseSurvey.Cell(c.x()+1,c.z())) || !empty(foot) || !empty(head)) continue;
            Bed f=(Bed)Material.WHITE_BED.createBlockData();f.setFacing(BlockFace.EAST);f.setPart(Bed.Part.FOOT);
            Bed hd=(Bed)f.clone();hd.setPart(Bed.Part.HEAD);journal.set(foot,f);journal.set(head,hd);return;
        }
    }
    private void basement(HouseSurvey.House h,String anchor) {
        // Require enough enclosed ground and a clear stairwell; never excavate through unknown blocks.
        if(h.maxX()-h.minX()<4 || h.maxZ()-h.minZ()<4 || h.y()-5<=h.world().getMinHeight()) {
            summary.add("basement skipped: building too small");return;
        }
        int a=h.minX()+1,b=h.minZ()+1,y=h.y();
        if(!empty(block(h,a,y,b)) || !block(h,a,y+1,b).getType().isAir()) {summary.add("basement skipped: entrance occupied");return;}
        for(int x=a-1;x<=a+3;x++) for(int z=b-1;z<=b+3;z++) for(int dy=y-5;dy<=y-1;dy++) {
            Block cell=block(h,x,dy,z);
            if(!h.world().isChunkLoaded(x>>4,z>>4) || !safety.changeable(cell) || cell.isLiquid()
                    || (dy<y-1 && cell.getType().isAir())) {summary.add("basement skipped: unsafe underground volume");return;}
        }
        for(int x=a-1;x<=a+3;x++) for(int z=b-1;z<=b+3;z++) for(int dy=y-5;dy<=y-1;dy++) {
            boolean shell=x==a-1||x==a+3||z==b-1||z==b+3||dy==y-5||dy==y-1;
            journal.set(block(h,x,dy,z),shell?Material.COBBLESTONE:Material.AIR);
        }
        for(int dy=y-4;dy<=y-1;dy++) {Ladder ladder=(Ladder)Material.LADDER.createBlockData();ladder.setFacing(BlockFace.EAST);journal.set(block(h,a,dy,b),ladder);}
        TrapDoor door=(TrapDoor)Material.SPRUCE_TRAPDOOR.createBlockData();door.setFacing(BlockFace.EAST);door.setOpen(true);journal.set(block(h,a,y,b),door);
        journal.set(block(h,a+2,y-1,b+2),Material.SEA_LANTERN);
        for(int x=a+1;x<=a+2;x++) {var soil=(org.bukkit.block.data.type.Farmland)Material.FARMLAND.createBlockData();soil.setMoisture(7);journal.set(block(h,x,y-4,b+2),soil);journal.set(block(h,x,y-3,b+2),Material.WHEAT);}
        
        anchors.put(anchor,new Location(h.world(),a+1.5,y-4,b+1.5));
        anchors.putIfAbsent("greenhouse",anchors.get(anchor));
        summary.add("ladder grow room under "+coords(h.centre()));
    }
    private Location standing(HouseSurvey.House h) {
        for(var c:h.interior()) {Block b=block(h,c.x(),h.y(),c.z());if(empty(b) && b.getRelative(BlockFace.UP).getType().isAir()) return b.getLocation().add(.5,0,.5);}
        return outside(h);
    }
    private Location outside(HouseSurvey.House h) {
        Location location=findOutdoor(new Location(h.world(),h.minX()-2,h.y(),h.minZ()-2),6);
        return location==null?h.centre():location.clone().add(.5,0,.5);
    }
    private Location findOutdoor(Location location,int radius) {
        for(int r=0;r<=radius;r++) for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++) {
            int x=location.getBlockX()+dx,z=location.getBlockZ()+dz;
            if(!location.getWorld().isChunkLoaded(x>>4,z>>4)) continue;
            Location at=surface(new Location(location.getWorld(),x,location.getY(),z));Block b=at.getBlock();
            if(empty(b) && b.getRelative(BlockFace.UP).getType().isAir() && TakeoverSafety.palette(b.getRelative(BlockFace.DOWN).getType())
                    && b.getRelative(BlockFace.DOWN).getType().isSolid()
                    && Math.abs(at.getY()-location.getY())<=3
                    && !safety.edited(b.getRelative(BlockFace.DOWN))) return at;
        }
        return null;
    }
    private Location surface(Location l) {
        if(!l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)) return l;
        l.setY(l.getWorld().getHighestBlockYAt(l)+1);return l;
    }
    private Location midpoint(Location a,Location b){return new Location(a.getWorld(),(a.getX()+b.getX())/2,a.getY(),(a.getZ()+b.getZ())/2);}
    private String coords(Location l){return l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ();}
}
