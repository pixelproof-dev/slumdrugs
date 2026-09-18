package dev.lucas.slumdrugs.world;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.InventoryHolder;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Write-ahead journal. Only ordinary block data may be replaced; old tile entities are never touched. */
public final class TakeoverSnapshot {
    public record Change(int x,int y,int z,String before,String after) {}
    public final String id;
    public final World world;
    public final LinkedHashMap<String,Change> changes=new LinkedHashMap<>();
    private final File file;
    private String status="prepared";
    private final TakeoverSafety safety;
    public TakeoverSnapshot(SlumDrugsPlugin plugin,String id,World world,TakeoverSafety safety) {
        this.id=id;this.world=world;this.safety=safety;
        this.file=new File(plugin.getDataFolder(),"takeover-"+id+".yml");
    }
    public boolean set(Block block,org.bukkit.block.data.BlockData after) {
        if(!world.equals(block.getWorld()) || !safety.changeable(block)) return false;
        String key=block.getX()+","+block.getY()+","+block.getZ();
        Change previous=changes.get(key);
        String before=previous==null?block.getBlockData().getAsString():previous.before;
        changes.put(key,new Change(block.getX(),block.getY(),block.getZ(),before,after.getAsString())); return true;
    }
    public boolean set(Block b,Material material) {return set(b,material.createBlockData());}
    public Material planned(Block b) {
        Change c=changes.get(b.getX()+","+b.getY()+","+b.getZ());
        return c==null?b.getType():Bukkit.createBlockData(c.after).getMaterial();
    }
    public void persist(String phase) throws IOException {
        status=phase; var y=new YamlConfiguration();
        y.set("id",id);y.set("world",world.getUID().toString());y.set("status",status);
        int n=0;
        for(Change c:changes.values()) {
            String key="blocks.b"+n++; y.set(key+".x",c.x);y.set(key+".y",c.y);y.set(key+".z",c.z);
            y.set(key+".before",c.before);y.set(key+".after",c.after);
        }
        atomic(file.toPath(),y.saveToString());
    }
    public static void atomic(Path file,String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temp,text,java.nio.charset.StandardCharsets.UTF_8);
        try {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
    public void apply() throws IOException {
        if(changes.isEmpty()) throw new IOException("No safe changes planned");
        for(Change c:changes.values()) {
            Block b=world.getBlockAt(c.x,c.y,c.z);
            if(!safety.changeable(b) || !b.getBlockData().getAsString().equals(c.before)) throw new IOException("World changed during planning");
        }
        persist("applying");
        for(Change c:changes.values()) world.getBlockAt(c.x,c.y,c.z).setBlockData(Bukkit.createBlockData(c.after),false);
        persist("applied");
    }
    public static TakeoverSnapshot read(SlumDrugsPlugin plugin,String id,TakeoverSafety safety) throws IOException {
        File file=new File(plugin.getDataFolder(),"takeover-"+id+".yml");
        if(!file.isFile()) throw new IOException("Snapshot is missing: "+file.getName());
        YamlConfiguration y=new YamlConfiguration();
        try {y.load(file);} catch(Exception e){throw new IOException("Cannot read snapshot",e);}
        World world=Bukkit.getWorld(UUID.fromString(y.getString("world","")));
        if(world==null) throw new IOException("Snapshot world is not loaded");
        var snapshot=new TakeoverSnapshot(plugin,id,world,safety);snapshot.status=y.getString("status","prepared");
        var blocks=y.getConfigurationSection("blocks");
        if(blocks==null) throw new IOException("Snapshot has no block records");
        for(String key:blocks.getKeys(false)) {
            var s=blocks.getConfigurationSection(key);
            Change c=new Change(s.getInt("x"),s.getInt("y"),s.getInt("z"),s.getString("before"),s.getString("after"));
            Bukkit.createBlockData(c.before);Bukkit.createBlockData(c.after);
            snapshot.changes.put(c.x+","+c.y+","+c.z,c);
        }
        return snapshot;
    }
    public String status(){return status;}
    /** A partial apply may contain original and converted blocks; unrelated edits are conflicts. */
    public static boolean restoreCompatible(String before,String after,String current,boolean naturalGrowth) {
        if(current.equals(before) || current.equals(after))return true;
        return naturalGrowth && current.split("\\[",2)[0].equals(after.split("\\[",2)[0]);
    }
    public void restore(boolean rollback) throws IOException {
        // Preflight the whole restore before changing a single block.
        if(!rollback) for(Change c:changes.values()) {
            Block b=world.getBlockAt(c.x,c.y,c.z);
            if(b.getState() instanceof InventoryHolder inventory && !inventory.getInventory().isEmpty())
                throw new IOException("Empty the container at "+c.x+", "+c.y+", "+c.z+" before restoring");
            String current=b.getBlockData().getAsString();
            boolean crop=(b.getBlockData() instanceof org.bukkit.block.data.Ageable || b.getBlockData() instanceof org.bukkit.block.data.type.Farmland)
                    && b.getType()==Bukkit.createBlockData(c.after).getMaterial();
            if(!restoreCompatible(c.before,c.after,current,crop))
                throw new IOException("Block changed since takeover at "+c.x+", "+c.y+", "+c.z+"; restore it to its takeover state first");
        }
        persist("restoring");
        var reverse=new ArrayList<>(changes.values());Collections.reverse(reverse);
        for(Change c:reverse) world.getBlockAt(c.x,c.y,c.z).setBlockData(Bukkit.createBlockData(c.before),false);
        persist("restored");
    }
}
