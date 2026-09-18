package dev.lucas.slumdrugs.farm;

import org.bukkit.Location;

import java.util.UUID;

/** One planted crop. Growth and quality accumulate separately so a neglected plot still finishes, just badly. */
public final class Plot {

    public final Location location;
    public final String drug;
    public final UUID planter;
    public final String planterName;
    public final long plantedAt;

    /** 0..1 growth progress. */
    public double progress;
    /** Running average of growing conditions, 0..1. Becomes the harvest quality. */
    public double conditionSum;
    public int conditionSamples;
    /** 0..1 plant health. Drops when conditions are bad; visible as wilting. */
    public double health = 1.0;
    public int fertilizer;
    public long lastWarned;
    public int lastStage = -1;

    public Plot(Location location, String drug, UUID planter, String planterName) {
        this.location = location;
        this.drug = drug;
        this.planter = planter;
        this.planterName = planterName;
        this.plantedAt = System.currentTimeMillis();
    }

    public double conditionAverage() {
        return conditionSamples == 0 ? 0.5 : conditionSum / conditionSamples;
    }

    public boolean ready() {
        return progress >= 1.0;
    }

    public static String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    public String key() {
        return key(location);
    }
}
