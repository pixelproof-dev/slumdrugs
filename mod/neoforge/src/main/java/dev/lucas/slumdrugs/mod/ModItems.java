package dev.lucas.slumdrugs.mod;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every item the mod registers. These are real registry entries, not vanilla materials
 * wearing a custom-model-data mask the way the plugin had to do it.
 */
public final class ModItems {

    /** Substances with a full grow chain: seed, raw harvest, dried. */
    public static final List<String> CROPS = List.of("sunleaf", "frostroot", "emberbloom");

    /** Every substance, including the two that are refined rather than grown. */
    public static final List<String> SUBSTANCES =
            List.of("sunleaf", "frostroot", "emberbloom", "glowcap", "sparkshard");

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SlumDrugsMod.ID);

    private static final Map<String, DeferredItem<Item>> REGISTERED = new LinkedHashMap<>();

    private ModItems() {}

    private static DeferredItem<Item> simple(String name) {
        DeferredItem<Item> item = ITEMS.registerSimpleItem(name);
        REGISTERED.put(name, item);
        return item;
    }

    static {
        for (String drug : CROPS) {
            simple("seed_" + drug);
            simple("raw_" + drug);
            simple("dried_" + drug);
        }
        for (String drug : SUBSTANCES) {
            simple("product_" + drug);
            simple("package_" + drug);
        }
        simple("fertilizer");
        simple("remedy");
    }

    /** Registration order, which is also the order they appear in the creative tab. */
    public static Map<String, DeferredItem<Item>> all() {
        return Collections.unmodifiableMap(REGISTERED);
    }

    public static DeferredItem<Item> get(String name) {
        DeferredItem<Item> item = REGISTERED.get(name);
        if (item == null) throw new IllegalArgumentException("No such item: " + name);
        return item;
    }
}
