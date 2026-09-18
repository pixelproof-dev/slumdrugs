package dev.lucas.slumdrugs.listener;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.drug.Items;
import dev.lucas.slumdrugs.farm.Plot;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Planting, fertilizing, harvesting and protecting plots from vanilla growth and explosions. */
public final class FarmListener implements Listener {

    private final SlumDrugsPlugin plugin;

    public FarmListener(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (plugin.stations().at(clicked.getLocation()) != null) return;
        ItemStack item = event.getItem();
        Items items = plugin.items();

        if (items.is(item, Items.SEED)) {
            Drug drug = plugin.drugs().get(items.drugId(item));
            if (drug == null || !drug.isGrown()) return;
            event.setCancelled(true);
            if (plugin.farms().plant(event.getPlayer(), clicked, drug)
                    && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
            return;
        }

        if (items.is(item, Items.FERTILIZER)) {
            if (plugin.farms().fertilize(event.getPlayer(), clicked)) {
                event.setCancelled(true);
                if (event.getPlayer().getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
            }
            return;
        }

        // Empty hand on a plot: quick condition check.
        if ((item == null || item.getType().isAir())) {
            Plot plot = plugin.farms().at(clicked.getLocation());
            if (plot != null) {
                Drug drug = plugin.drugs().get(plot.drug);
                if (drug != null) {
                    event.setCancelled(true);
                    plugin.farms().report(event.getPlayer(), clicked, drug);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Plot plot = plugin.farms().at(event.getBlock().getLocation());
        if (plot == null) return;
        event.setDropItems(false);
        List<ItemStack> drops = plugin.farms().harvest(plot, event.getPlayer());
        for (ItemStack drop : drops) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), drop);
        }
    }

    /** Our crops grow on the plugin's schedule, not the vanilla random tick. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (plugin.farms().at(event.getBlock().getLocation()) != null) event.setCancelled(true);
    }

    /** Plots survive explosions; the blocks may go, but the registration is cleaned up properly. */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> {
            if (plugin.farms().at(b.getLocation()) != null) return true;
            return plugin.stations().at(b.getLocation()) != null;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.farms().at(b.getLocation()) != null || plugin.stations().at(b.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.farms().at(b.getLocation()) != null || plugin.stations().at(b.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.farms().at(b.getLocation()) != null
                || plugin.stations().at(b.getLocation()) != null);
    }

    /** Stops mobs trampling farmland under a plot. */
    @EventHandler(ignoreCancelled = true)
    public void onTrample(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;
        Block above = event.getBlock().getRelative(0, 1, 0);
        if (plugin.farms().at(above.getLocation()) != null) event.setCancelled(true);
    }
}
