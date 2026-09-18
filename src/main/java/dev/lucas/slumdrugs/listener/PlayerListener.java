package dev.lucas.slumdrugs.listener;

import dev.lucas.slumdrugs.Msg;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.drug.Items;
import dev.lucas.slumdrugs.player.PlayerData;
import io.papermc.paper.event.player.PlayerDeepSleepEvent;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Using substances, sleeping, joining and leaving. */
public final class PlayerListener implements Listener {

    private final SlumDrugsPlugin plugin;

    public PlayerListener(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerData pd = plugin.players().get(event.getPlayer());
        pd.name = event.getPlayer().getName();
        if (plugin.district().exists() && pd.totalSales == 0) {
            Msg.send(event.getPlayer(), "<gray>New here? Try</gray> <white>/drugs help</white><gray>, or</gray> "
                    + "<white>/drugs district</white> <gray>to find the slums.</gray>");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.condition().onQuit(event.getPlayer());
        plugin.effects().clear(event.getPlayer().getUniqueId());
        plugin.players().unload(event.getPlayer().getUniqueId());
    }

    /** Right-clicking a product uses it; right-clicking a remedy drinks it. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        // Furniture interactions take precedence over consuming the item in hand.
        if (event.getClickedBlock() != null
                && plugin.stations().at(event.getClickedBlock().getLocation()) != null) return;
        ItemStack item = event.getItem();
        if (item == null) return;

        Items items = plugin.items();
        String kind = items.kind(item);
        if (kind == null) return;

        if (Items.REMEDY.equals(kind)) {
            event.setCancelled(true);
            plugin.condition().drinkRemedy(event.getPlayer());
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
            return;
        }

        if (Items.PRODUCT.equals(kind)) {
            event.setCancelled(true);
            Drug drug = plugin.drugs().get(items.drugId(item));
            if (drug == null) return;
            plugin.condition().use(event.getPlayer(), drug, items.quality(item));
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onDeepSleep(PlayerDeepSleepEvent event) {
        plugin.condition().onDeepSleep(event.getPlayer());
    }

    @EventHandler
    public void onBedLeave(PlayerBedLeaveEvent event) {
        long time = event.getPlayer().getWorld().getTime();
        plugin.condition().onWake(event.getPlayer(), time < 1000 || time > 23000);
    }

    /** Hallucinations cannot hurt anyone and cannot be hurt. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (plugin.effects().isHallucination(event.getEntity()) || plugin.effects().isHallucination(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (plugin.effects().isHallucination(event.getEntity())) event.setCancelled(true);
    }
}
