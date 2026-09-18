package dev.lucas.slumdrugs.ui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Marker holder so click events can tell which of our menus is open. */
public final class Menu implements InventoryHolder {

    public enum Type {
        DRYING_RACK,
        PROCESSING_BENCH,
        PACKAGING_STATION,
        STORAGE_CRATE,
        CUSTOMER,
        CLINIC,
        FIXER,
        TRADER
    }

    public final Type type;
    public final Location block;
    public final String context;
    public final Map<Integer, Runnable> actions = new HashMap<>();
    private Inventory inventory;

    public Menu(Type type, Location block, String context) {
        this.type = type;
        this.block = block;
        this.context = context;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
