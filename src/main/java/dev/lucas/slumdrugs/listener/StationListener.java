package dev.lucas.slumdrugs.listener;

import dev.lucas.slumdrugs.Keys;
import dev.lucas.slumdrugs.Msg;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.drug.Items;
import dev.lucas.slumdrugs.player.PlayerData;
import dev.lucas.slumdrugs.station.Station;
import dev.lucas.slumdrugs.station.StationType;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Placing, using and breaking processing furniture, plus delivery drop boxes. */
public final class StationListener implements Listener {

    private final SlumDrugsPlugin plugin;
    private final Keys keys;

    public StationListener(SlumDrugsPlugin plugin, Keys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        StationType type = plugin.items().stationType(event.getItemInHand());
        if (type == null) return;
        Block block = event.getBlockPlaced();
        var location=block.getLocation(); var owner=event.getPlayer().getUniqueId();
        float yaw=event.getPlayer().getYaw()+180;
        org.bukkit.Bukkit.getScheduler().runTask(plugin,()->{
            if(event.isCancelled() || block.getType()!=type.block) return;
            plugin.stations().register(location,type,owner);
            plugin.stations().placed(location,yaw);
        });
        Msg.send(event.getPlayer(), "<gold>" + type.label + "</gold> <gray>placed. Right-click to use. Sneak + left-click to pick up.</gray>");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Station station = plugin.stations().at(event.getBlock().getLocation());
        if (station == null) return;
        event.setDropItems(false);
        List<ItemStack> drops = plugin.stations().unregister(event.getBlock().getLocation());
        for (ItemStack drop : drops) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), drop);
        }
        plugin.stations().save();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Station station = plugin.stations().at(block.getLocation());
        if (station != null) {
            if(event.getPlayer().getGameMode()==org.bukkit.GameMode.SPECTATOR
                    || event.getPlayer().getGameMode()==org.bukkit.GameMode.ADVENTURE) return;
            event.setCancelled(true);
            if(event.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
                if(!event.getPlayer().isSneaking()) return;
                // Fire the standard event so land-protection plugins can veto dismantling.
                BlockBreakEvent breaking=new BlockBreakEvent(block,event.getPlayer());
                org.bukkit.Bukkit.getPluginManager().callEvent(breaking);
                if(!breaking.isCancelled()) block.setType(org.bukkit.Material.AIR,false);
                return;
            }
            if(!event.getAction().isRightClick()) return;
            ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
            plugin.stations().interact(event.getPlayer(), station, hand);
            return;
        }

        if(!event.getAction().isRightClick()) return;

        // Drop boxes complete delivery runs.
        if (block.getState() instanceof TileState state) {
            String box = state.getPersistentDataContainer().get(keys.dropBox, PersistentDataType.STRING);
            if (box != null) {
                event.setCancelled(true);
                deliver(event, box);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        plugin.stations().chunk(event.getChunk(),true);
    }

    @EventHandler(ignoreCancelled=true)
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        plugin.stations().chunk(event.getChunk(),false);
    }

    private void deliver(PlayerInteractEvent event, String box) {
        var player = event.getPlayer();
        PlayerData pd = plugin.players().get(player);
        if (pd.delivery == null) {
            Msg.send(player, "<gray>A locked drop box. Vosk at the warehouse hands out the runs.</gray>");
            return;
        }
        if (!pd.delivery.dropBox.equals(box)) {
            Msg.send(player, "<gray>Wrong box. Yours is the " + pd.delivery.dropBox + " one.</gray>");
            return;
        }

        Drug drug = plugin.drugs().get(pd.delivery.drug);
        if (drug == null) return;
        if (System.currentTimeMillis() >= pd.delivery.deadline) {
            pd.delivery = null;
            Msg.send(player, "<red>This delivery has expired. Your goods have not been taken.</red>");
            return;
        }
        Items items = plugin.items();
        int needed = pd.delivery.units;
        int found = dev.lucas.slumdrugs.drug.GoodsTransfer.count(player, items, drug, 0);
        if (!dev.lucas.slumdrugs.drug.GoodsTransfer.takeExactly(player, items, drug, 0, needed)) {
            Msg.send(player, "<red>Not enough to fill the drop.</red> <gray>You need " + needed
                    + " units of " + drug.name + ", you had " + found + ".</gray>");
            return;
        }

        double reward = pd.delivery.reward;
        if (pd.delivery.intercepted) {
            reward *= 0.7;
            Msg.send(player, "<gray>You took the long way. The cut is smaller, but you made it.</gray>");
        }
        plugin.economy().deposit(player, reward);
        pd.addRep("gangs", 6);
        pd.addRep("guards", -2);
        plugin.heat().add(player, 4);
        plugin.district().nudge(-1.5);
        pd.delivery = null;
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.8f, 0.9f);
        Msg.send(player, "<green><bold>Delivery complete.</bold></green> <gray>You earn <green>"
                + Msg.money(reward) + "</green>.</gray>");
    }
}
