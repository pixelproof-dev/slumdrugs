package dev.lucas.slumdrugs.world;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Bed;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Chunk-persistent edit history. Old blocks are handled using conservative palette heuristics. */
public final class TakeoverSafety implements Listener {
    private final NamespacedKey edits, beds;
    public TakeoverSafety(SlumDrugsPlugin plugin) {
        edits=new NamespacedKey(plugin,"takeover_player_edits"); beds=new NamespacedKey(plugin,"takeover_used_beds");
    }
    private String pos(Block b) { return (b.getX()&15)+","+b.getY()+","+(b.getZ()&15); }
    private void mark(Block b,NamespacedKey key) {
        var pdc=b.getChunk().getPersistentDataContainer();
        var values=new LinkedHashSet<>(List.of(pdc.getOrDefault(key,PersistentDataType.STRING,"").split(";")));
        values.remove(""); values.add(pos(b)); pdc.set(key,PersistentDataType.STRING,String.join(";",values));
    }
    private boolean marked(Block b,NamespacedKey key) {
        String value=b.getChunk().getPersistentDataContainer().getOrDefault(key,PersistentDataType.STRING,"");
        return (";"+value+";").contains(";"+pos(b)+";");
    }
    public boolean edited(Block b) { return marked(b,edits); }
    public boolean usedBed(Block b) { return marked(b,beds); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void place(BlockPlaceEvent e) {
        mark(e.getBlock(),edits);
        if(e instanceof BlockMultiPlaceEvent multi) for(var state:multi.getReplacedBlockStates()) mark(state.getBlock(),edits);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e) { mark(e.getBlock(),edits); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void bed(PlayerBedEnterEvent e) {
        if(e.getBedEnterResult()!=PlayerBedEnterEvent.BedEnterResult.OK) return;
        Block b=e.getBed(); mark(b,beds);
        if(b.getBlockData() instanceof Bed data)
            mark(b.getRelative(data.getPart()==Bed.Part.FOOT?data.getFacing():data.getFacing().getOppositeFace()),beds);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void piston(BlockPistonExtendEvent e) { for(Block b:e.getBlocks()) if(edited(b)) { mark(b,edits); mark(b.getRelative(e.getDirection()),edits); } }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void piston(BlockPistonRetractEvent e) { for(Block b:e.getBlocks()) if(edited(b)) { mark(b,edits); mark(b.getRelative(e.getDirection()),edits); mark(b.getRelative(e.getDirection().getOppositeFace()),edits); } }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void bucket(org.bukkit.event.player.PlayerBucketEmptyEvent e) { mark(e.getBlock(),edits); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void bucketFill(org.bukkit.event.player.PlayerBucketFillEvent e) { mark(e.getBlock(),edits); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void entityChange(org.bukkit.event.entity.EntityChangeBlockEvent e) {
        if(e.getEntity() instanceof org.bukkit.entity.Player)mark(e.getBlock(),edits);
    }

    public static boolean palette(Material material) {
        String n=material.name();
        if(material==Material.AIR || material==Material.CAVE_AIR || material==Material.VOID_AIR) return true;
        if(Set.of("DIRT","GRASS_BLOCK","DIRT_PATH","COARSE_DIRT","ROOTED_DIRT","STONE","GRAVEL","SAND",
                "SANDSTONE","SMOOTH_SANDSTONE","CUT_SANDSTONE","COBBLESTONE","MOSSY_COBBLESTONE","STONE_BRICKS",
                "CRACKED_STONE_BRICKS","MOSSY_STONE_BRICKS","GLASS","GLASS_PANE","SNOW","SNOW_BLOCK","ICE",
                "PACKED_ICE","TORCH","WALL_TORCH","LANTERN","SOUL_LANTERN","TERRACOTTA","WHITE_TERRACOTTA",
                "YELLOW_TERRACOTTA","ORANGE_TERRACOTTA","RED_TERRACOTTA","BROWN_TERRACOTTA","WHITE_WOOL",
                "SHORT_GRASS","TALL_GRASS","FERN","DEAD_BUSH","DANDELION","POPPY","COBWEB","IRON_BARS",
                "FARMLAND","WHEAT","BEETROOTS","CARROTS","POTATOES","COMPOSTER","HAY_BLOCK","CAULDRON",
                "WATER_CAULDRON","FLETCHING_TABLE","CARTOGRAPHY_TABLE","SMITHING_TABLE","GRINDSTONE","STONECUTTER").contains(n)) return true;
        for(String wood:List.of("OAK","SPRUCE","ACACIA")) {
            if(n.equals("STRIPPED_"+wood+"_LOG") || n.equals("STRIPPED_"+wood+"_WOOD")) return true;
            for(String suffix:List.of("PLANKS","LOG","WOOD","STAIRS","SLAB","FENCE","FENCE_GATE","DOOR","TRAPDOOR"))
                if(n.equals(wood+"_"+suffix)) return true;
        }
        return n.endsWith("_BED") || n.endsWith("_CARPET") || n.endsWith("_STAINED_GLASS_PANE")
                || Set.of("COBBLESTONE_STAIRS","COBBLESTONE_SLAB","COBBLESTONE_WALL","MOSSY_COBBLESTONE_STAIRS",
                "MOSSY_COBBLESTONE_SLAB","SANDSTONE_STAIRS","SANDSTONE_SLAB","SMOOTH_SANDSTONE_STAIRS",
                "SMOOTH_SANDSTONE_SLAB","STONE_BRICK_STAIRS","STONE_BRICK_SLAB").contains(n);
    }
    public boolean changeable(Block block) {
        return !edited(block) && !(block.getState() instanceof TileState) && palette(block.getType());
    }
}
