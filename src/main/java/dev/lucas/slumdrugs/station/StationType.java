package dev.lucas.slumdrugs.station;

import org.bukkit.Material;

/** Processing furniture. Each type is backed by a vanilla tile-entity block so we can tag it. */
public enum StationType {
    DRYING_RACK("drying_rack", "Drying Rack", Material.SMOKER, "Dries raw harvest into cured material over time."),
    PROCESSING_BENCH("processing_bench", "Processing Bench", Material.BARREL, "Turns cured material and ingredients into product."),
    PACKAGING_STATION("packaging_station", "Packaging Station", Material.CRAFTER, "Seals product into labelled packages."),
    STORAGE_CRATE("storage_crate", "Storage Crate", Material.CHEST, "Blast-proof crate that only holds goods."),
    GROWBOX("growbox", "Growbox", Material.DISPENSER, "Plant seeds, add water and fertilizer, then harvest.");

    public final String id;
    public final String label;
    public final Material block;
    public final String description;

    StationType(String id, String label, Material block, String description) {
        this.id = id;
        this.label = label;
        this.block = block;
        this.description = description;
    }

    public static StationType byId(String id) {
        if (id == null) return null;
        for (StationType t : values()) if (t.id.equalsIgnoreCase(id) || t.name().equalsIgnoreCase(id)) return t;
        return null;
    }
}
