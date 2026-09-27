package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.player.Progression;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
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

    private static final Map<String, DeferredItem<? extends Item>> REGISTERED = new LinkedHashMap<>();

    private ModItems() {}

    private static String named(String legacy) {
        ModernNames.alias(ITEMS, legacy);
        return ModernNames.id(legacy);
    }

    private static DeferredItem<Item> simple(String name) {
        DeferredItem<Item> item = ITEMS.registerSimpleItem(named(name));
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
            // Products are usable, so they get their own item class rather than a plain one.
            REGISTERED.put("product_" + drug,
                    ITEMS.registerItem(named("product_" + drug), props -> new ProductItem(props, drug)));
            // Essence is product boiled down: the same item, hitting harder.
            REGISTERED.put("essence_" + drug,
                    ITEMS.registerItem(named("essence_" + drug), props -> new ProductItem(props, drug, "essence_", ProductItem.ESSENCE_DOSE)));
            // Parcels open back into product, so they have behaviour too.
            REGISTERED.put("package_" + drug,
                    ITEMS.registerItem(named("package_" + drug), props -> new ParcelItem(props, drug)));
        }
        simple("fertilizer");
        REGISTERED.put("remedy", ITEMS.registerItem(named("remedy"), RemedyItem::new));
        REGISTERED.put("journal", ITEMS.registerItem(named("journal"), JournalItem::new, p -> p.stacksTo(1)));
        // Money, as items. Named after the art prompts so the drawings drop straight in.
        REGISTERED.put("coin_penny", ITEMS.registerItem(named("coin_penny"), p -> new CoinItem(p, Coin.PENNY)));
        REGISTERED.put("coin_shilling", ITEMS.registerItem(named("coin_shilling"), p -> new CoinItem(p, Coin.SHILLING)));
        REGISTERED.put("coin_sovereign", ITEMS.registerItem(named("coin_sovereign"), p -> new CoinItem(p, Coin.SOVEREIGN)));

        // Drawn but not yet wired into any system. They register so the art can be looked
        // at, handed out and built against; every one is a plain item until the rule that
        // uses it lands in sim. Order follows docs/ART-PROMPTS.md, which is the tab order.
        simple("charcoal_screen");
        simple("filler");
        simple("solvent_spirit");
        simple("sealing_wax");
        simple("seal_stamp");
        simple("deed");
        simple("charter");
        simple("contract");
        simple("writ_of_pardon");
        simple("line_stabiliser");
        simple("grafting_knife");
        simple("seed_pouch");
        simple("dynamo_coil");
        simple("pump_valve");
        simple("battery_glass");
        simple("arc_carbon");
        simple("lamp_oil");
        simple("evidence_sack");
        simple("bounty_poster");
        simple("carved_mask");
        simple("lockpicks");
        simple("constable_whistle");
        simple("hollowcap");
        simple("mirror_glass");
        simple("informants_ledger");
        simple("shadow_silt");
        simple("product_nightvein");
        simple("product_tidecap");
    }

    /**
     * Registers the item that places a station, gated on the tier that unlocks it, and lists it
     * in the creative tab with the rest.
     */
    static void blockItem(String name, DeferredBlock<? extends Block> block, Progression.Tier tier) {
        REGISTERED.put(name, ITEMS.registerItem(named(name),
                props -> new StationBlockItem(block.get(), tier, props),
                Item.Properties::useBlockDescriptionPrefix));
    }

    /** Registration order, which is also the order they appear in the creative tab. */
    public static Map<String, DeferredItem<? extends Item>> all() {
        Map<String, DeferredItem<? extends Item>> modern = new LinkedHashMap<>();
        REGISTERED.forEach((name, item) -> modern.put(ModernNames.id(name), item));
        return Collections.unmodifiableMap(modern);
    }

    public static DeferredItem<? extends Item> get(String name) {
        DeferredItem<? extends Item> item = REGISTERED.get(name.equals("product_flatline") ? "hollowcap" : ModernNames.legacy(name));
        if (item == null) throw new IllegalArgumentException("No such item: " + name);
        return item;
    }

    /**
     * Which substance a stack is a stage of, or null if it is not one of ours. The prefix is
     * the stage: {@code "seed_"}, {@code "raw_"}, {@code "dried_"}, {@code "product_"} or
     * {@code "package_"}. Stages that only crops have simply do not match for the other two.
     */
    public static String drugOf(String stage, net.minecraft.world.item.ItemStack stack) {
        for (String drug : SUBSTANCES) {
            DeferredItem<? extends Item> item = REGISTERED.get(stage + drug);
            if (item != null && stack.is(item.get())) return drug;
        }
        return null;
    }
}
