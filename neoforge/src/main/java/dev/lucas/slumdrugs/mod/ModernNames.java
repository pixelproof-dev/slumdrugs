package dev.lucas.slumdrugs.mod;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Public modern IDs; old simulation/save keys remain stable behind this boundary. */
public final class ModernNames {
    private static final Map<String, String> IDS = Map.ofEntries(
            Map.entry("coin_penny", "cash_note"),
            Map.entry("coin_shilling", "cash_roll"),
            Map.entry("coin_sovereign", "cash_brick"),
            Map.entry("forcing_frame", "grow_tent"),
            Map.entry("drying_loft", "drying_rack"),
            Map.entry("grafting_bench", "cloning_bench"),
            Map.entry("sealing_press", "vacuum_sealer"),
            Map.entry("counting_house", "cash_counter"),
            Map.entry("charcoal_screen", "carbon_filter"),
            Map.entry("solvent_spirit", "solvent"),
            Map.entry("sealing_wax", "seal_film"),
            Map.entry("seal_stamp", "label_gun"),
            Map.entry("grafting_knife", "clone_cutter"),
            Map.entry("dynamo_coil", "coil_pack"),
            Map.entry("battery_glass", "battery_cell"),
            Map.entry("arc_carbon", "carbon_rod"),
            Map.entry("lamp_oil", "generator_fuel"),
            Map.entry("charter", "business_licence"),
            Map.entry("deed", "property_deed"),
            Map.entry("writ_of_pardon", "forged_id"),
            Map.entry("evidence_sack", "evidence_bag"),
            Map.entry("bounty_poster", "wanted_notice"),
            Map.entry("carved_mask", "ski_mask"),
            Map.entry("constable_whistle", "police_scanner"),
            Map.entry("informants_ledger", "informant_file"),
            Map.entry("journal", "notebook"),
            Map.entry("hollowcap", "product_flatline"),
            Map.entry("seed_sunleaf", "seed_daybreak"),
            Map.entry("raw_sunleaf", "raw_daybreak"),
            Map.entry("dried_sunleaf", "dried_daybreak"),
            Map.entry("product_sunleaf", "product_daybreak"),
            Map.entry("essence_sunleaf", "essence_daybreak"),
            Map.entry("package_sunleaf", "package_daybreak"),
            Map.entry("seed_frostroot", "seed_coldsnap"),
            Map.entry("raw_frostroot", "raw_coldsnap"),
            Map.entry("dried_frostroot", "dried_coldsnap"),
            Map.entry("product_frostroot", "product_coldsnap"),
            Map.entry("essence_frostroot", "essence_coldsnap"),
            Map.entry("package_frostroot", "package_coldsnap"),
            Map.entry("seed_emberbloom", "seed_redline"),
            Map.entry("raw_emberbloom", "raw_redline"),
            Map.entry("dried_emberbloom", "dried_redline"),
            Map.entry("product_emberbloom", "product_redline"),
            Map.entry("essence_emberbloom", "essence_redline"),
            Map.entry("package_emberbloom", "package_redline"),
            Map.entry("seed_glowcap", "seed_neon"),
            Map.entry("raw_glowcap", "raw_neon"),
            Map.entry("dried_glowcap", "dried_neon"),
            Map.entry("product_glowcap", "product_neon"),
            Map.entry("essence_glowcap", "essence_neon"),
            Map.entry("package_glowcap", "package_neon"),
            Map.entry("seed_sparkshard", "seed_voltage"),
            Map.entry("raw_sparkshard", "raw_voltage"),
            Map.entry("dried_sparkshard", "dried_voltage"),
            Map.entry("product_sparkshard", "product_voltage"),
            Map.entry("essence_sparkshard", "essence_voltage"),
            Map.entry("package_sparkshard", "package_voltage"),
            Map.entry("seed_nightvein", "seed_blackout"),
            Map.entry("raw_nightvein", "raw_blackout"),
            Map.entry("dried_nightvein", "dried_blackout"),
            Map.entry("product_nightvein", "product_blackout"),
            Map.entry("essence_nightvein", "essence_blackout"),
            Map.entry("package_nightvein", "package_blackout"),
            Map.entry("seed_tidecap", "seed_riptide"),
            Map.entry("raw_tidecap", "raw_riptide"),
            Map.entry("dried_tidecap", "dried_riptide"),
            Map.entry("product_tidecap", "product_riptide"),
            Map.entry("essence_tidecap", "essence_riptide"),
            Map.entry("package_tidecap", "package_riptide"),
            Map.entry("seed_hollowcap", "seed_flatline"),
            Map.entry("raw_hollowcap", "raw_flatline"),
            Map.entry("dried_hollowcap", "dried_flatline"),
            Map.entry("product_hollowcap", "product_flatline"),
            Map.entry("essence_hollowcap", "essence_flatline"),
            Map.entry("package_hollowcap", "package_flatline"));

    public static String id(String legacy) { return IDS.getOrDefault(legacy, legacy); }

    public static String legacy(String modern) {
        for (var entry : IDS.entrySet()) if (entry.getValue().equals(modern)) return entry.getKey();
        return modern;
    }

    /** NeoForge applies these before registration; old saved item/block IDs resolve to the new entry. */
    public static void alias(DeferredRegister<?> register, String legacy) {
        String modern = id(legacy);
        if (!legacy.equals(modern)) register.addAlias(
                Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, legacy),
                Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, modern));
    }

    private ModernNames() {}
}
