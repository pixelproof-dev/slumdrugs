package dev.lucas.slumdrugs;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** All persistent-data keys used by the plugin, created once at startup. */
public final class Keys {

    // Item keys
    public final NamespacedKey kind;      // seed / raw / dried / product / package / fertilizer / remedy / station
    public final NamespacedKey drug;      // drug id
    public final NamespacedKey quality;   // 0..100
    public final NamespacedKey grower;    // player name
    public final NamespacedKey batch;     // short batch id
    public final NamespacedKey units;     // units inside a package
    public final NamespacedKey station;   // station type id

    // Entity keys
    public final NamespacedKey npcType;   // customer / resident / guard / medic / fixer / trader / thug / officer / hallucination
    public final NamespacedKey npcId;     // customer profile id or resident name
    public final NamespacedKey spawnedAt; // epoch millis
    public final NamespacedKey owner;     // player uuid (hallucinations)

    // Block keys (tile states)
    public final NamespacedKey stationBlock;
    public final NamespacedKey dropBox;
    public final NamespacedKey takeoverId;
    public final NamespacedKey reusedVillager;

    public Keys(Plugin plugin) {
        kind = new NamespacedKey(plugin, "kind");
        drug = new NamespacedKey(plugin, "drug");
        quality = new NamespacedKey(plugin, "quality");
        grower = new NamespacedKey(plugin, "grower");
        batch = new NamespacedKey(plugin, "batch");
        units = new NamespacedKey(plugin, "units");
        station = new NamespacedKey(plugin, "station");
        npcType = new NamespacedKey(plugin, "npc_type");
        npcId = new NamespacedKey(plugin, "npc_id");
        spawnedAt = new NamespacedKey(plugin, "spawned_at");
        owner = new NamespacedKey(plugin, "owner");
        stationBlock = new NamespacedKey(plugin, "station_block");
        dropBox = new NamespacedKey(plugin, "drop_box");
        takeoverId = new NamespacedKey(plugin,"takeover_id");
        reusedVillager = new NamespacedKey(plugin,"takeover_reused");
    }
}
