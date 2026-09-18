package dev.lucas.slumdrugs.world;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.npc.CustomerProfile;
import dev.lucas.slumdrugs.npc.NpcManager;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Places the district's residents, shopkeepers, guards and buyers once the buildings exist. */
public final class DistrictPopulator {

    private static final String[] RESIDENT_NAMES = {
            "Ardo", "Mila", "Fen", "Ruth", "Bex", "Onno", "Tilde", "Garr", "Saskia", "Pell"};

    private final SlumDrugsPlugin plugin;
    private final District district;
    private final NpcManager npcs;

    public DistrictPopulator(SlumDrugsPlugin plugin, District district, NpcManager npcs) {
        this.plugin = plugin;
        this.district = district;
        this.npcs = npcs;
    }

    /** Clears existing NPCs and spawns a full cast. Returns how many were placed. */
    public int populate() {
        if (!district.exists()) return 0;
        if(district.takeoverId()!=null) return plugin.takeover().populate();
        npcs.despawnAll();
        Random r = new Random(district.seed());
        int spawned = 0;

        Location clinic = district.anchor("clinic");
        if (clinic != null && npcs.spawn("medic", "medic", clinic, "<aqua>Medic</aqua>") != null) spawned++;

        Location warehouse = district.anchor("warehouse");
        if (warehouse != null && npcs.spawn("fixer", "vosk", warehouse, "<red>Vosk</red> <gray>(fixer)</gray>") != null) spawned++;

        Location market = district.anchor("market");
        if (market != null && npcs.spawn("trader", "trader", market, "<green>Seed Trader</green>") != null) spawned++;

        // Guards patrol the market and the main street.
        int guards = plugin.getConfig().getInt("district.guards", 3);
        for (int i = 0; i < guards; i++) {
            Location at = jitter(market != null ? market : district.origin(), r, 8);
            if (npcs.spawn("guard", "guard" + i, at, "<gold>District Guard</gold>") != null) spawned++;
        }

        // Residents stand outside the apartments and workshops.
        List<Location> homes = new ArrayList<>();
        for (String key : List.of("apartments_a", "apartments_b", "workshop_a", "workshop_b")) {
            Location l = district.anchor(key);
            if (l != null) homes.add(l);
        }
        for (int i = 0; i < Math.min(RESIDENT_NAMES.length, homes.size() * 2); i++) {
            Location at = jitter(homes.get(i % homes.size()), r, 3);
            String name = RESIDENT_NAMES[i % RESIDENT_NAMES.length];
            if (npcs.spawn("resident", name, at, "<white>" + name + "</white>") != null) spawned++;
        }

        // Customers wait at their usual haunts.
        List<Location> haunts = new ArrayList<>();
        for (String key : List.of("tavern", "alley", "market", "outpost")) {
            Location l = district.anchor(key);
            if (l != null) haunts.add(l);
        }
        if (haunts.isEmpty()) haunts.add(district.origin());
        int i = 0;
        int max = plugin.getConfig().getInt("market.max-customers-per-district", 6);
        for (CustomerProfile profile : npcs.profiles().values()) {
            if (i >= max) break;
            Location at = jitter(haunts.get(i % haunts.size()), r, 4);
            LivingEntity e = npcs.spawn("customer", profile.id, at, "<yellow>" + profile.name + "</yellow>");
            if (e != null) spawned++;
            i++;
        }
        return spawned;
    }

    private Location jitter(Location base, Random r, int radius) {
        if (base == null) return district.origin();
        Location l = base.clone().add(r.nextInt(radius * 2 + 1) - radius, 0, r.nextInt(radius * 2 + 1) - radius);
        l.setY(l.getWorld().getHighestBlockYAt(l) + 1);
        return l;
    }
}
