package dev.lucas.slumdrugs.world;

import dev.lucas.slumdrugs.*;
import org.bukkit.*;
import org.bukkit.block.TileState;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import java.io.*;
import java.util.*;

/** Conservative, single-district takeover of already loaded generated villages. */
public final class VillageTakeover implements Listener {
    private final SlumDrugsPlugin plugin;
    private final TakeoverSafety safety;
    private BukkitTask task;
    
    private final Map<String,Long> retries=new HashMap<>();
    private boolean recoveryBlocked;
    private int nextPlayer;
    public VillageTakeover(SlumDrugsPlugin plugin) {this.plugin=plugin;this.safety=new TakeoverSafety(plugin);}
    public TakeoverSafety safety(){return safety;}
    public void start() {
        recover();
        task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if(recoveryBlocked || plugin.district().exists() || !plugin.getConfig().getBoolean("village-takeover.enabled",true)
                    || !plugin.getConfig().getBoolean("village-takeover.automatic",true)) return;
            var players=new ArrayList<>(Bukkit.getOnlinePlayers());
            if(!players.isEmpty()) takeover(players.get(Math.floorMod(nextPlayer++,players.size())),false);
        },100L,100L);
    }
    public void stop(){if(task!=null)task.cancel();}
    private record Village(String id,BoundingBox bounds,Location centre){}
    private List<Village> nearby(Player p) {
        int radius=Math.clamp(plugin.getConfig().getInt("village-takeover.search-radius",96),16,256);
        Map<String,Village> found=new TreeMap<>();
        int cx=p.getLocation().getBlockX()>>4,cz=p.getLocation().getBlockZ()>>4,r=(radius+15)/16;
        for(int x=cx-r;x<=cx+r;x++) for(int z=cz-r;z<=cz+r;z++) {
            if(!p.getWorld().isChunkLoaded(x,z))continue;
            for(var structure:p.getWorld().getChunkAt(x,z).getStructures()) {
                var kind=structure.getStructure();
                if(!Set.of(org.bukkit.generator.structure.Structure.VILLAGE_PLAINS,
                        org.bukkit.generator.structure.Structure.VILLAGE_DESERT,
                        org.bukkit.generator.structure.Structure.VILLAGE_SAVANNA,
                        org.bukkit.generator.structure.Structure.VILLAGE_SNOWY,
                        org.bukkit.generator.structure.Structure.VILLAGE_TAIGA).contains(kind))continue;
                BoundingBox b=structure.getBoundingBox();
                if(b.getWidthX()>256 || b.getWidthZ()>256)continue;
                double dx=Math.max(b.getMinX()-p.getX(),Math.max(0,p.getX()-b.getMaxX()));
                double dz=Math.max(b.getMinZ()-p.getZ(),Math.max(0,p.getZ()-b.getMaxZ()));
                if(dx*dx+dz*dz>radius*radius)continue;
                String id=UUID.nameUUIDFromBytes((p.getWorld().getUID()+":"+(int)b.getMinX()+":"+(int)b.getMinZ()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                found.putIfAbsent(id,new Village(id,b,new Location(p.getWorld(),b.getCenterX(),p.getY(),b.getCenterZ())));
            }
        }
        return found.values().stream().sorted(Comparator.comparingDouble(v->v.centre.distanceSquared(p.getLocation()))).toList();
    }
    private boolean loaded(World w,BoundingBox b) {
        for(int x=((int)Math.floor(b.getMinX())-16)>>4;x<=((int)Math.ceil(b.getMaxX())+16)>>4;x++)
            for(int z=((int)Math.floor(b.getMinZ())-16)>>4;z<=((int)Math.ceil(b.getMaxZ())+16)>>4;z++)
                if(!w.isChunkLoaded(x,z))return false;
        return true;
    }
    public void takeover(Player p,boolean manual) {
        String reason="No fully loaded, unused village with safe houses nearby.";
        if(recoveryBlocked)reason="Snapshot recovery needs attention; check the server log.";
        else if(!plugin.getConfig().getBoolean("village-takeover.enabled",true))reason="Village takeover is disabled.";
        else if(!plugin.getConfig().getBoolean("village-takeover.snapshot",true))reason="Enable village-takeover.snapshot before converting a village.";
        else if(plugin.district().exists())reason="A district already exists. Restore its takeover before converting another village.";
        else for(Village village:nearby(p)) {
            if(plugin.district().alreadyConverted(village.id,village.centre,Math.max(0,plugin.getConfig().getInt("village-takeover.min-distance-between",512))))continue;
            if(!manual && retries.getOrDefault(village.id,0L)>System.currentTimeMillis())continue;
            if(retries.size()>2048)retries.clear();
            retries.put(village.id,System.currentTimeMillis()+60000);
            if(!loaded(p.getWorld(),village.bounds))continue;
            var houses=HouseSurvey.find(p.getWorld(),village.bounds,safety);
            if(houses.isEmpty())continue;
            houses=houses.subList(0,Math.min(houses.size(),Math.clamp(plugin.getConfig().getInt("village-takeover.houses",3),1,3)));
            TakeoverSnapshot snapshot=new TakeoverSnapshot(plugin,village.id,p.getWorld(),safety);
            VillageConversion plan=new VillageConversion(snapshot,safety);
            if(!plan.convert(houses,village.bounds))continue;
            try {
                snapshot.persist("prepared"); snapshot.apply();
                // Include the edge outpost and outside anchors in district security checks.
                BoundingBox footprint=village.bounds.clone();
                for(Location at:plan.anchors.values())footprint.union(at.toVector());
                for(Location at:plan.boxes.values())footprint.union(at.toVector());
                
                long seed=p.getWorld().getSeed()^((long)(int)village.bounds.getMinX()*341873128712L)^((long)(int)village.bounds.getMinZ()*132897987541L);
                plugin.district().adopt(village.id,village.centre,footprint,seed,plan.anchors,plan.boxes);
                for(var e:plan.boxes.entrySet())if(e.getValue().getBlock().getState() instanceof TileState tile){
                    tile.getPersistentDataContainer().set(plugin.keys().dropBox,PersistentDataType.STRING,e.getKey());tile.update();
                }
                for(var change:snapshot.changes.values()) if(Bukkit.createBlockData(change.after()).getMaterial()==Material.WHEAT) {
                    var crop=plugin.drugs().grown().stream().filter(d->d.crop.block==Material.WHEAT).findFirst().orElse(null);
                    plugin.farms().adoptVillageCrop(new Location(snapshot.world,change.x(),change.y(),change.z()),crop);
                }
                plugin.farms().save();
                plugin.npcs().buildProfiles(seed);int count=plugin.populator().populate();
                plugin.getLogger().info("Village takeover "+village.id+" in "+p.getWorld().getName()+": "+String.join("; ",plan.summary)+"; "+count+" NPC roles assigned; "+snapshot.changes.size()+" blocks journaled.");
                Msg.send(p,"<gray>A few doors have changed hands. The village has new regulars.</gray>");return;
            } catch(Exception failure) {
                plugin.getLogger().severe("Takeover failed: "+failure);
                try {if(!snapshot.status().equals("prepared"))snapshot.restore(true);removeCrops(snapshot);plugin.district().restoredTakeover(village.id);cleanupLoaded(village.id);}
                catch(Exception rollback){recoveryBlocked=true;plugin.getLogger().severe("Takeover rollback failed: "+rollback);}
                reason="The conversion failed; check the server log.";break;
            }
        }
        if(manual)Msg.send(p,"<gray>"+reason+"</gray>");
    }
    public void restore(org.bukkit.command.CommandSender sender) {
        String id=plugin.district().takeoverId();
        if(id==null){Msg.send(sender,"<gray>No active village takeover to restore.</gray>");return;}
        try {
            TakeoverSnapshot snapshot=TakeoverSnapshot.read(plugin,id,safety);snapshot.restore(false);removeCrops(snapshot);
            plugin.district().restoredTakeover(id);cleanupLoaded(id);
            Msg.send(sender,"<gray>The village blocks are back as they were. Its original villagers remain.</gray>");
        }catch(Exception ex){Msg.send(sender,"<red>Restore stopped: "+ex.getMessage()+"</red>");}
    }
    private void recover() {
        File[] files=plugin.getDataFolder().listFiles((dir,name)->name.startsWith("takeover-")&&name.endsWith(".yml"));
        if(files==null)return;
        for(File file:files)try {
            String id=file.getName().substring(9,file.getName().length()-4);
            TakeoverSnapshot s=TakeoverSnapshot.read(plugin,id,safety);
            if(s.status().equals("applying")||s.status().equals("restoring")||s.status().equals("applied")&&!id.equals(plugin.district().takeoverId())) {
                s.restore(false);removeCrops(s);plugin.district().restoredTakeover(id);cleanupLoaded(id);
                plugin.getLogger().warning("Recovered unfinished takeover "+id);
            } else if(s.status().equals("restored")){plugin.district().restoredTakeover(id);cleanupLoaded(id);}
        }catch(Exception ex){recoveryBlocked=true;plugin.getLogger().severe("Cannot recover "+file.getName()+": "+ex.getMessage());}
    }
    private void removeCrops(TakeoverSnapshot snapshot) {
        for(var c:snapshot.changes.values()) if(Bukkit.createBlockData(c.after()).getMaterial()==Material.WHEAT)
            plugin.farms().remove(new Location(snapshot.world,c.x(),c.y(),c.z()));
        plugin.farms().save();
    }
    private void cleanupLoaded(String id){for(World w:Bukkit.getWorlds())for(Entity e:w.getEntities())if(id.equals(e.getPersistentDataContainer().get(plugin.keys().takeoverId,PersistentDataType.STRING)))cleanup(e);}
    private void cleanup(Entity e) {
        var data=e.getPersistentDataContainer();
        if(data.has(plugin.keys().reusedVillager,PersistentDataType.BYTE)) {
            data.remove(plugin.keys().npcType);data.remove(plugin.keys().npcId);data.remove(plugin.keys().reusedVillager);data.remove(plugin.keys().takeoverId);
        }else e.remove();
    }
    @EventHandler public void loaded(EntitiesLoadEvent event) {
        for(Entity e:event.getEntities()) {
            String id=e.getPersistentDataContainer().get(plugin.keys().takeoverId,PersistentDataType.STRING);
            if(id!=null && plugin.district().restored(id))cleanup(e);
        }
    }
    public int populate() {
        if(recoveryBlocked || plugin.district().takeoverId()==null)return 0;
        // Avoid respawning a role whose entity is temporarily in an unloaded chunk.
        File ledger=new File(plugin.getDataFolder(),"takeover-npcs-"+plugin.district().takeoverId()+".properties");
        Properties roles=new Properties();
        try {if(ledger.isFile())try(var in=new FileInputStream(ledger)){roles.load(in);}}
        catch(IOException ex){plugin.getLogger().warning("Cannot read NPC ledger: "+ex);return 0;}
        int count=0;
        count+=assign("medic","medic","clinic",roles,ledger);
        count+=assign("fixer","vosk","warehouse",roles,ledger);
        count+=assign("trader","trader","market",roles,ledger);
        for(int i=0;i<2;i++)count+=assign("resident","resident"+i,"apartments_a",roles,ledger);
        int max=Math.clamp(plugin.getConfig().getInt("market.max-customers-per-district",6),0,32),i=0;
        for(var profile:plugin.npcs().profiles().values()){if(i++>=max)break;count+=assign("customer",profile.id,i%2==0?"alley":"tavern",roles,ledger);}
        for(Entity e:plugin.district().origin().getWorld().getEntities())if(e instanceof IronGolem && plugin.district().contains(e.getLocation()) && plugin.npcs().typeOf(e)==null){tag(e,"guard","guard-"+e.getUniqueId(),true);count++;}
        return count;
    }
    private int assign(String type,String id,String anchor,Properties roles,File ledger) {
        String key=type+":"+id;if(roles.containsKey(key))return 0;
        Location at=plugin.district().anchor(anchor);if(at==null || !at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))return 0;
        for(Entity e:at.getWorld().getEntities())if(plugin.district().takeoverId().equals(e.getPersistentDataContainer().get(plugin.keys().takeoverId,PersistentDataType.STRING))
                && id.equals(plugin.npcs().idOf(e))){roles.setProperty(key,e.getUniqueId().toString());saveRoles(roles,ledger);return 0;}
        Entity entity=null;
        if(!type.equals("fixer") && plugin.getConfig().getBoolean("village-takeover.reuse-villagers",true))
            entity=at.getWorld().getEntities().stream().filter(e->e instanceof Villager v && v.isAdult() && plugin.district().contains(e.getLocation()) && plugin.npcs().typeOf(e)==null)
                    .min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(at)+(type.equals("medic")&&((Villager)e).getProfession()!=Villager.Profession.CLERIC?100000:0))).orElse(null);
        boolean reused=entity!=null;
        if(entity==null)entity=plugin.npcs().spawn(type,id,at,"<gray>"+type+"</gray>");
        if(entity==null)return 0;
        tag(entity,type,id,reused);roles.setProperty(key,entity.getUniqueId().toString());saveRoles(roles,ledger);return 1;
    }
    private void saveRoles(Properties roles,File file){try {var out=new StringWriter();roles.store(out,"Assigned village roles; prevents duplicates after chunk unload");TakeoverSnapshot.atomic(file.toPath(),out.toString());}catch(IOException ex){throw new java.io.UncheckedIOException(ex);}}
    private void tag(Entity e,String type,String id,boolean reused){var d=e.getPersistentDataContainer();d.set(plugin.keys().npcType,PersistentDataType.STRING,type);d.set(plugin.keys().npcId,PersistentDataType.STRING,id);d.set(plugin.keys().takeoverId,PersistentDataType.STRING,plugin.district().takeoverId());if(reused)d.set(plugin.keys().reusedVillager,PersistentDataType.BYTE,(byte)1);}
}
