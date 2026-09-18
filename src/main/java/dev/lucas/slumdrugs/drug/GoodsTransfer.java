package dev.lucas.slumdrugs.drug;

import dev.lucas.slumdrugs.station.StationManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Must be called on the server thread; preflight never changes the inventory. */
public final class GoodsTransfer {
    private GoodsTransfer() {}

    public static int count(Player player, Items items, Drug drug, int minQuality) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (matches(items, item, drug, minQuality)) total += items.units(item);
        }
        return total;
    }

    public static boolean matches(Items items, ItemStack item, Drug drug, int minQuality) {
        return item != null && !item.getType().isAir() && drug.id.equals(items.drugId(item))
                && (items.is(item, Items.PRODUCT) || items.is(item, Items.PACKAGE))
                && items.quality(item) >= minQuality;
    }

    public static void takeFromSlot(Player player, Items items, Drug drug, int slot, int units) {
        ItemStack item = player.getInventory().getItem(slot);
        if (!matches(items, item, drug, 0) || units < 1 || items.units(item) < units)
            throw new IllegalArgumentException("Not enough matching goods in slot");
        int per = items.is(item, Items.PACKAGE) ? items.units(item) / item.getAmount() : 1;
        UnitTransfer plan = UnitTransfer.plan(item.getAmount(), per, units);
        ItemStack change = plan.looseChange() == 0 ? null : items.product(drug, items.quality(item),
                items.grower(item), items.batch(item), plan.looseChange());
        if (plan.remainingPackages() == 0) player.getInventory().setItem(slot, null);
        else {
            ItemStack remainder = item.clone();
            remainder.setAmount(plan.remainingPackages());
            player.getInventory().setItem(slot, remainder);
        }
        if (change != null) StationManager.give(player, change);
    }

    public static boolean takeExactly(Player player, Items items, Drug drug, int minQuality, int wanted) {
        if (wanted < 1 || count(player, items, drug, minQuality) < wanted) return false;
        int remaining = wanted;
        int slots = player.getInventory().getContents().length;
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!matches(items, item, drug, minQuality)) continue;
            int take = Math.min(remaining, items.units(item));
            takeFromSlot(player, items, drug, slot, take);
            remaining -= take;
        }
        return true;
    }
}
