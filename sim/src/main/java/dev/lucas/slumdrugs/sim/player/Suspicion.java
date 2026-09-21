package dev.lucas.slumdrugs.sim.player;

/**
 * How much the Watch has noticed. Sales raise it, time lowers it, and past the top of the
 * scale a raid is called — announced ahead of time, always, because the design's rule is that
 * the Watch never surprises anyone and never breaks anything. "Heat" was the plugin's name;
 * in the fiction it is suspicion.
 *
 * <p>A sealed parcel is evidence with a name on it, so it draws more suspicion per unit than
 * a quiet street sale. That is the trade the seal makes: worth more, and traceable.
 */
public final class Suspicion {

    public enum Level {
        CLEAR, NOTICED, WATCHED, HUNTED, RAID;

        public static Level of(double value) {
            if (value >= RAID_AT) return RAID;
            if (value >= HUNTED_AT) return HUNTED;
            if (value >= WATCHED_AT) return WATCHED;
            if (value >= NOTICED_AT) return NOTICED;
            return CLEAR;
        }
    }

    public static final double NOTICED_AT = 20;
    public static final double WATCHED_AT = 40;
    public static final double HUNTED_AT = 60;
    public static final double RAID_AT = 80;

    /** Suspicion per unit sold loose on the street. */
    public static final double PER_LOOSE_UNIT = 1.0;

    /** Suspicion per unit sold under seal. The seal is the evidence. */
    public static final double PER_SEALED_UNIT = 1.5;

    /** Points that fade per quiet minute. */
    public static final double DECAY_PER_MINUTE = 0.5;

    /** Warning the bell gives before the Watch arrives, in milliseconds. */
    public static final long RAID_WARNING_MILLIS = 2 * 60000L;

    /** Where suspicion lands after a raid: watched, not forgotten. */
    public static final double AFTER_RAID = 30;

    public double value;

    /** When the announced raid lands, or zero when none is called. */
    public long raidAt;

    public Suspicion() {}

    public Suspicion(double value, long raidAt) {
        this.value = clamp(value);
        this.raidAt = Math.max(0, raidAt);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    public Level level() { return Level.of(value); }

    /** A sale has been seen. */
    public void sold(int units, boolean sealed) {
        value = clamp(value + Math.max(0, units) * (sealed ? PER_SEALED_UNIT : PER_LOOSE_UNIT));
    }

    /** Quiet time. */
    public void decay(double minutes) {
        value = clamp(value - Math.max(0, minutes) * DECAY_PER_MINUTE);
    }

    /**
     * Whether the bell should ring now: suspicion has reached the top and no raid is called
     * yet. The caller records the raid with {@link #callRaid}.
     */
    public boolean shouldCallRaid() { return raidAt == 0 && value >= RAID_AT; }

    public void callRaid(long now) { raidAt = now + RAID_WARNING_MILLIS; }

    /** A called raid lapses if the player has laid low enough for the Watch to lose interest. */
    public boolean raidLapsed() { return raidAt != 0 && value < HUNTED_AT; }

    public boolean raidDue(long now) { return raidAt != 0 && now >= raidAt; }

    /** Milliseconds until the raid lands, or -1 with none called. */
    public long untilRaid(long now) { return raidAt == 0 ? -1 : Math.max(0, raidAt - now); }

    /** The Watch has been and gone. */
    public void raided() {
        value = Math.min(value, AFTER_RAID);
        raidAt = 0;
    }

    public void cancelRaid() { raidAt = 0; }
}
