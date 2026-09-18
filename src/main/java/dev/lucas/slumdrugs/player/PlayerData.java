package dev.lucas.slumdrugs.player;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Everything the plugin remembers about one player. Saved to players/<uuid>.yml. */
public final class PlayerData {

    public static final String[] FACTIONS = {"residents", "traders", "gangs", "guards"};

    /** Per-drug state. All 0..100 except timestamps (epoch millis). */
    public static final class Condition {
        public double intoxPeak;
        public long intoxStart;
        public long intoxEnd;
        public double tolerance;
        public double dependence;
        public long lastUse;
        public int uses;

        public double intoxication(long now) {
            if (now >= intoxEnd || intoxEnd <= intoxStart) return 0;
            double remaining = (double) (intoxEnd - now) / (double) (intoxEnd - intoxStart);
            return intoxPeak * Math.max(0, Math.min(1, remaining));
        }
    }

    /** A bulk order from a trusted customer. */
    public static final class Contract {
        public String customerId;
        public String customerName;
        public String drug;
        public int units;
        public int minQuality;
        public double reward;
        public long deadline;
    }

    /** A courier job: bring a package to a drop box. */
    public static final class Delivery {
        public String drug;
        public int units;
        public String dropBox;
        public double reward;
        public long deadline;
        public boolean intercepted;
    }

    /** A legal errand offered by a resident. */
    public static final class LegalJob {
        public String residentName;
        public Material material;
        public int amount;
        public double pay;
    }

    public final UUID uuid;
    public String name = "";
    public double balance;
    public double heat;
    public final Map<String, Integer> reputation = new HashMap<>();
    public final Map<String, Condition> conditions = new HashMap<>();
    public final Map<String, Integer> trust = new HashMap<>();
    public long lastFullSleep;
    public long lastRecoveryAt = System.currentTimeMillis();
    public long withdrawalSuppressedUntil;
    public long lastCravingPrompt;
    public long raidWarnedAt;
    public long raidAt;
    public long lastInspection;
    public long lastRivalOffer;
    public int totalSales;
    public Contract contract;
    public Delivery delivery;
    public LegalJob job;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        for (String f : FACTIONS) reputation.put(f, 0);
    }

    public Condition condition(String drug) {
        return conditions.computeIfAbsent(drug, k -> new Condition());
    }

    public int rep(String faction) {
        return reputation.getOrDefault(faction, 0);
    }

    public void addRep(String faction, int delta) {
        reputation.put(faction, Math.max(-100, Math.min(100, rep(faction) + delta)));
    }

    public int trust(String customerId) {
        return trust.getOrDefault(customerId, 0);
    }

    public void addTrust(String customerId, int delta) {
        trust.put(customerId, Math.max(-100, Math.min(100, trust(customerId) + delta)));
    }

    public boolean restedRecently(long now) {
        return now - lastFullSleep < 10 * 60_000L;
    }
}
