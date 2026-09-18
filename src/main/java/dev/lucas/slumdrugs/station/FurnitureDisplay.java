package dev.lucas.slumdrugs.station;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import org.bukkit.*;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** One non-persistent display per loaded station; the saved station record is authoritative. */
public final class FurnitureDisplay {
    private final SlumDrugsPlugin plugin;
    private final Map<String,ItemDisplay> displays = new HashMap<>();
    private final Map<String,String> shown = new HashMap<>();
    public FurnitureDisplay(SlumDrugsPlugin plugin) { this.plugin=plugin; }
    public void sync(Station station) {
        Location l=station.location;
        if(!l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)) return;
        var block=l.getBlock();
        if(block.getType()!=Material.BARRIER) {
            if(block.getType()!=station.type.block) { remove(station); return; }
            // Do not discard items accidentally left in a legacy vanilla container.
            if(block.getState() instanceof InventoryHolder holder && !holder.getInventory().isEmpty()) return;
            block.setType(Material.BARRIER,false);
        }
        ItemDisplay display=displays.get(station.key());
        if(display==null || !display.isValid()) {
            var centre=l.clone().add(.5,.5,.5); centre.setYaw(station.yaw); centre.setPitch(0);
            display=l.getWorld().spawn(centre,ItemDisplay.class,e->{
                e.setPersistent(false); e.setInvulnerable(true); e.setGravity(false);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setDisplayWidth(2); e.setDisplayHeight(2); e.setViewRange(1);
            });
            displays.put(station.key(),display); shown.remove(station.key());
        }
        String model=station.type==StationType.GROWBOX
                ? "growbox_"+station.growbox.stage()+(station.growbox.lamp?"_on":"_off") : station.type.id;
        String signature=model+":"+station.yaw;
        if(!signature.equals(shown.get(station.key()))) {
            ItemStack item=new ItemStack(Material.PAPER);
            item.editMeta(meta->meta.setItemModel(new NamespacedKey("slumdrugs","furniture/"+model)));
            display.setItemStack(item); display.setRotation(station.yaw,0);
            display.setBrightness(station.type==StationType.GROWBOX && station.growbox.lamp
                    ? new org.bukkit.entity.Display.Brightness(12,12) : null);
            shown.put(station.key(),signature);
        }
    }
    public void remove(Station station) {
        ItemDisplay display=displays.remove(station.key());
        if(display!=null) display.remove(); shown.remove(station.key());
    }
    public void clear() { for(var display:displays.values()) display.remove(); displays.clear(); shown.clear(); }
}
