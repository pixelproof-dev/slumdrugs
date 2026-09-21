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

    /** Every number the Watch runs on, so a server can set its own. Defaults are the design's. */
    public record Settings(double noticedAt, double watchedAt, double huntedAt, double raidAt,
                           double perLooseUnit, double perSealedUnit, double decayPerMinute,
                           long raidWarningMillis, double afterRaid) {
        public Settings {
            if (!(noticedAt <= watchedAt && watchedAt <= huntedAt && huntedAt <= raidAt))
                throw new IllegalArgumentException("Suspicion thresholds must climb");
            if (perLooseUnit < 0 || perSealedUnit < 0 || decayPerMinute < 0 || raidWarningMillis < 0)
                throw new IllegalArgumentException("Suspicion rates must not be negative");
        }

        public static Settings defaults() {
            return new Settings(NOTICED_AT, WATCHED_AT, HUNTED_AT, RAID_AT, PER_LOOSE_UNIT, PER_SEALED_UNIT,
                    DECAY_PER_MINUTE, RAID_WARNING_MILLIS, AFTER_RAID);
        }

        public Level levelOf(double value) {
            if (value >= raidAt) return Level.RAID;
            if (value >= huntedAt) return Level.HUNTED;
            if (value >= watchedAt) return Level.WATCHED;
            if (value >= noticedAt) return Level.NOTICED;
            return Level.CLEAR;
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

    /** Constables paid off so far. Each one is a liability the design's inspections will reach. */
    public int bribes;

    public Suspicion() {}

    public Suspicion(double value, long raidAt) { this(value, raidAt, 0); }

    public Suspicion(double value, long raidAt, int bribes) {
        this.value = clamp(value);
        this.raidAt = Math.max(0, raidAt);
        this.bribes = Math.max(0, bribes);
    }

    /**
     * Coin into a constable's hand: a point a shilling off, up to the cap, and one more name
     * in the book. Returns what it took off.
     */
    public double bribed(long pence, double perShilling, double cap) {
        double off = Math.min(Math.max(0, cap), Math.max(0, pence) / 12.0 * Math.max(0, perShilling));
        double before = value;
        value = clamp(value - off);
        bribes++;
        return before - value;
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    public Level level() { return Level.of(value); }
    public Level level(Settings s) { return s.levelOf(value); }

    /** A sale has been seen. */
    public void sold(int units, boolean sealed) { sold(units, sealed, Settings.defaults()); }

    public void sold(int units, boolean sealed, Settings s) {
        value = clamp(value + Math.max(0, units) * (sealed ? s.perSealedUnit() : s.perLooseUnit()));
    }

    /** Quiet time. */
    public void decay(double minutes) { decay(minutes, Settings.defaults()); }

    public void decay(double minutes, Settings s) {
        value = clamp(value - Math.max(0, minutes) * s.decayPerMinute());
    }

    /**
     * Whether the bell should ring now: suspicion has reached the top and no raid is called
     * yet. The caller records the raid with {@link #callRaid}.
     */
    public boolean shouldCallRaid() { return shouldCallRaid(Settings.defaults()); }
    public boolean shouldCallRaid(Settings s) { return raidAt == 0 && value >= s.raidAt(); }

    public void callRaid(long now) { callRaid(now, Settings.defaults()); }
    public void callRaid(long now, Settings s) { raidAt = now + s.raidWarningMillis(); }

    /** A called raid lapses if the player has laid low enough for the Watch to lose interest. */
    public boolean raidLapsed() { return raidLapsed(Settings.defaults()); }
    public boolean raidLapsed(Settings s) { return raidAt != 0 && value < s.huntedAt(); }

    public boolean raidDue(long now) { return raidAt != 0 && now >= raidAt; }

    /** Milliseconds until the raid lands, or -1 with none called. */
    public long untilRaid(long now) { return raidAt == 0 ? -1 : Math.max(0, raidAt - now); }

    /** The Watch has been and gone. */
    public void raided() { raided(Settings.defaults()); }

    public void raided(Settings s) {
        value = Math.min(value, s.afterRaid());
        raidAt = 0;
    }

    public void cancelRaid() { raidAt = 0; }
}
