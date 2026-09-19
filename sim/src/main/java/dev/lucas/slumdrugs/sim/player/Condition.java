package dev.lucas.slumdrugs.sim.player;

/**
 * What using a substance does to a person over time: they feel it less each time, they come to
 * want it, and they get it back by stopping. Tolerance and dependence are the two dials, and
 * the second one is the only one with teeth.
 *
 * <p>Every value is 0-100 and every rule here is invented. Recovery is always available and
 * always works: {@link Recovery} runs whether the player is online or not, and there is no
 * state a player can reach that they cannot walk back from.
 */
public final class Condition {

    /** Above this, the next dose is dangerous. */
    public static final double OVERDOSE_THRESHOLD = 95;

    /** Dependence below this does nothing at all. */
    public static final double CRAVING_MIN = 20;

    /** Dependence above this can bring on withdrawal once the delay has passed. */
    public static final double WITHDRAWAL_MIN = 40;

    public double intoxication;
    public double tolerance;
    public double dependence;

    /** Millisecond timestamps, from the world clock rather than the wall clock. */
    public long lastUse;
    public long lastSleep;

    public Condition() {}

    public Condition(double intoxication, double tolerance, double dependence) {
        this.intoxication = clamp(intoxication);
        this.tolerance = clamp(tolerance);
        this.dependence = clamp(dependence);
    }

    /** Full restore, for whatever the platform layer persists with. */
    public static Condition of(double intoxication, double tolerance, double dependence,
                               long lastUse, long lastSleep) {
        Condition c = new Condition(intoxication, tolerance, dependence);
        c.lastUse = lastUse;
        c.lastSleep = lastSleep;
        return c;
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    /**
     * How much of a dose actually lands. A fresh user feels all of it; a hardened one feels
     * half. Tolerance never blocks a dose entirely, or the loop would stall.
     */
    public double effectiveDose(double dose, int quality) {
        double grade = 0.7 + 0.005 * Math.max(0, Math.min(100, quality));  // 0.7 to 1.2
        double blunting = 1.0 - tolerance / 200.0;                          // 1.0 down to 0.5
        return Math.max(0, dose) * grade * blunting;
    }

    /** What a dose does. Returns the intoxication actually gained. */
    public double use(double dose, int quality, double toleranceGain, double dependenceGain, long now) {
        double landed = effectiveDose(dose, quality);
        double before = intoxication;
        intoxication = clamp(intoxication + landed);
        tolerance = clamp(tolerance + Math.max(0, toleranceGain));
        dependence = clamp(dependence + Math.max(0, dependenceGain));
        lastUse = Math.max(lastUse, now);
        return intoxication - before;
    }

    /** True when this dose pushes the player past what their body will take. */
    public boolean wouldOverdose(double dose, int quality) {
        return intoxication + effectiveDose(dose, quality) > OVERDOSE_THRESHOLD;
    }

    /**
     * Moves the whole condition forward to {@code now}. Intoxication burns off quickly,
     * tolerance and dependence slowly, and dependence only once the delay since the last use
     * has passed — so recovery starts when someone stops, not while they are still using.
     */
    public void advance(long from, long now, Settings settings) {
        if (now <= from) return;
        double minutes = (now - from) / 60000.0;
        intoxication = Math.max(0, intoxication - minutes * settings.intoxicationDecayPerMinute());
        tolerance = Recovery.tolerance(tolerance, from, now, settings.toleranceDecayPerMinute());
        dependence = Recovery.dependence(dependence, from, now, lastUse,
                settings.dependenceDelayMillis(), lastSleep,
                settings.dependenceDecayPerMinute(), settings.restMultiplier());
    }

    /** Craving starts once someone is dependent and has been without for a while. */
    public boolean craving(long now, Settings settings) {
        return dependence >= CRAVING_MIN
                && intoxication < 5
                && now - lastUse >= settings.cravingDelayMillis();
    }

    /** Withdrawal is the harder state, and needs real dependence behind it. */
    public boolean withdrawing(long now, Settings settings) {
        return dependence >= WITHDRAWAL_MIN
                && intoxication < 5
                && now - lastUse >= settings.withdrawalDelayMillis();
    }

    /** 0-3, how rough the withdrawal is. Deliberately shallow: this is a game, not a lesson. */
    public int withdrawalSeverity(long now, Settings settings) {
        if (!withdrawing(now, settings)) return 0;
        if (dependence >= 85) return 3;
        if (dependence >= 65) return 2;
        return 1;
    }

    /** A full night's sleep speeds recovery for a while. */
    public void slept(long now) { lastSleep = now; }

    /** Tuning, defaulted to the plugin's shipped values so behaviour carries over. */
    public record Settings(double intoxicationDecayPerMinute, double toleranceDecayPerMinute,
                           double dependenceDecayPerMinute, long dependenceDelayMillis,
                           long cravingDelayMillis, long withdrawalDelayMillis, double restMultiplier) {
        public Settings {
            if (intoxicationDecayPerMinute <= 0) throw new IllegalArgumentException("Intoxication must wear off");
            if (toleranceDecayPerMinute < 0 || dependenceDecayPerMinute < 0)
                throw new IllegalArgumentException("Decay rates must not be negative");
            if (restMultiplier < 1) throw new IllegalArgumentException("Rest must not slow recovery");
        }

        public static Settings defaults() {
            return new Settings(4.0, 0.15, 0.08, 20 * 60000L, 15 * 60000L, 30 * 60000L, 2.5);
        }
    }
}
