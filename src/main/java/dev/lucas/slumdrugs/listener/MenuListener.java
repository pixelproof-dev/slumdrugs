package dev.lucas.slumdrugs.listener;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.ui.Menu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Routes clicks in the plugin's menus. Storage crates behave like normal containers. */
public final class MenuListener implements Listener {

    private final SlumDrugsPlugin plugin;

    public MenuListener(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof Menu menu)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (menu.type == Menu.Type.STORAGE_CRATE) {
            boolean inCrate = event.getClickedInventory() == top;
            ItemStack candidate = null;
            if (event.isShiftClick() && !inCrate) candidate = event.getCurrentItem();
            else if (inCrate) {
                candidate = event.getCursor();
                if (event.getHotbarButton() >= 0)
                    candidate = player.getInventory().getItem(event.getHotbarButton());
                else if (event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND)
                    candidate = player.getInventory().getItemInOffHand();
            }
            if (candidate != null && !candidate.getType().isAir() && !plugin.items().isPluginItem(candidate)) {
                event.setCancelled(true);
                dev.lucas.slumdrugs.Msg.bar(player, "<gray>The crate only takes goods from this trade.</gray>");
            }
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) return;
        Runnable action = menu.actions.get(event.getSlot());
        if (action != null) {
            // Opening/closing an inventory during its click event is unsafe. Consume once,
            // then run after Bukkit has finished processing this transaction.
            menu.actions.clear();
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != top) return;
                if (menu.block != null && plugin.stations().at(menu.block) == null) {
                    player.closeInventory();
                    return;
                }
                action.run();
            });
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof Menu menu)) return;
        if (menu.type == Menu.Type.STORAGE_CRATE) {
            boolean intoCrate = event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize());
            if (intoCrate && !plugin.items().isPluginItem(event.getOldCursor())) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Menu menu)) return;
        if (menu.type == Menu.Type.STORAGE_CRATE && menu.block != null) {
            plugin.stations().saveCrate(menu.block, event.getInventory());
        }
    }
}
