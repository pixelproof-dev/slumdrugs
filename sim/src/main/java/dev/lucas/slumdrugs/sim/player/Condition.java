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

    /** Until when a remedy draught holds withdrawal off. Zero when none is working. */
    public long soothedUntil;

    /** The most tolerance this person can carry. Comes down for good with every clean streak. */
    public double toleranceCeiling = 100;

    /** The last use a clean streak was rewarded from, so one streak pays once. */
    public long streakRewardedAt;

    /** How far the ceiling can fall. */
    public static final double CEILING_FLOOR = 50;

    /** What one clean streak takes off the ceiling. */
    public static final double CEILING_STEP = 10;

    /** How long a treatment holds withdrawal off. */
    public static final long TREATMENT_MILLIS = 10 * 60000L;

    /** Dependence a remedy takes off. Small on purpose: it is help, not a cure. */
    public static final double REMEDY_DEPENDENCE = 4;

    /** Tolerance a remedy takes off. */
    public static final double REMEDY_TOLERANCE = 2;

    /** How long one draught holds withdrawal off. */
    public static final long REMEDY_MILLIS = 5 * 60000L;

    public Condition() {}

    public Condition(double intoxication, double tolerance, double dependence) {
        this.intoxication = clamp(intoxication);
        this.tolerance = clamp(tolerance);
        this.dependence = clamp(dependence);
    }

    /** Full restore, for whatever the platform layer persists with. */
    public static Condition of(double intoxication, double tolerance, double dependence,
                               long lastUse, long lastSleep) {
        return of(intoxication, tolerance, dependence, lastUse, lastSleep, 0);
    }

    public static Condition of(double intoxication, double tolerance, double dependence,
                               long lastUse, long lastSleep, long soothedUntil) {
        return of(intoxication, tolerance, dependence, lastUse, lastSleep, soothedUntil, 100, 0);
    }

    public static Condition of(double intoxication, double tolerance, double dependence,
                               long lastUse, long lastSleep, long soothedUntil,
                               double toleranceCeiling, long streakRewardedAt) {
        Condition c = new Condition(intoxication, tolerance, dependence);
        c.lastUse = lastUse;
        c.lastSleep = lastSleep;
        c.soothedUntil = Math.max(0, soothedUntil);
        c.toleranceCeiling = Math.max(CEILING_FLOOR, Math.min(100, toleranceCeiling));
        c.streakRewardedAt = Math.max(0, streakRewardedAt);
        c.tolerance = Math.min(c.tolerance, c.toleranceCeiling);
        return c;
    }

    /**
     * A stay at the infirmary: a real cut to dependence, some tolerance with it, and a long
     * hold on withdrawal. Refused while a draught or a treatment is still working.
     */
    public boolean treat(long now, double dependenceOff, long millis) {
        if (soothed(now)) return false;
        dependence = clamp(dependence - Math.max(0, dependenceOff));
        tolerance = clamp(tolerance - Math.max(0, dependenceOff) / 2);
        soothedUntil = now + Math.max(0, millis);
        return true;
    }

    /**
     * A clean streak: this long since the last use, with a use to be clean from, lowers the
     * tolerance ceiling for good, once per streak. Returns whether it just did.
     */
    public boolean cleanStreak(long now, long streakMillis) {
        if (lastUse <= 0 || streakRewardedAt == lastUse) return false;
        if (now - lastUse < Math.max(1, streakMillis)) return false;
        streakRewardedAt = lastUse;
        toleranceCeiling = Math.max(CEILING_FLOOR, toleranceCeiling - CEILING_STEP);
        tolerance = Math.min(tolerance, toleranceCeiling);
        return true;
    }

    /** Whether a draught is holding withdrawal off right now. */
    public boolean soothed(long now) { return now < soothedUntil; }

    /**
     * A remedy draught: a little dependence and tolerance off, and withdrawal held at bay for
     * a while. Refused while the last one is still working, so it cannot be chained into a
     * cure; the way out is still to stop.
     */
    public boolean remedy(long now) { return remedy(now, REMEDY_DEPENDENCE, REMEDY_TOLERANCE, REMEDY_MILLIS); }

    public boolean remedy(long now, double dependenceOff, double toleranceOff, long millis) {
        if (soothed(now)) return false;
        dependence = clamp(dependence - Math.max(0, dependenceOff));
        tolerance = clamp(tolerance - Math.max(0, toleranceOff));
        soothedUntil = now + Math.max(0, millis);
        return true;
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
        tolerance = Math.min(toleranceCeiling, clamp(tolerance + Math.max(0, toleranceGain)));
        dependence = clamp(dependence + Math.max(0, dependenceGain));
        lastUse = Math.max(lastUse, now);
        return intoxication - before;
    }

    /** True when this dose pushes the player past what their body will take. */
    public boolean wouldOverdose(double dose, int quality) {
        return wouldOverdose(dose, quality, 0);
    }

    /** The same, for a dose that is partly filler: the line comes down to meet it. */
    public boolean wouldOverdose(double dose, int quality, double cutRatio) {
        return intoxication + effectiveDose(dose, quality)
                > dev.lucas.slumdrugs.sim.drug.Cutting.overdoseThreshold(OVERDOSE_THRESHOLD, cutRatio);
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

    /** Withdrawal is the harder state, and needs real dependence behind it. A draught holds it off. */
    public boolean withdrawing(long now, Settings settings) {
        return dependence >= WITHDRAWAL_MIN
                && intoxication < 5
                && !soothed(now)
                && now - lastUse >= settings.withdrawalDelayMillis();
    }

    /** 0-3, how rough the withdrawal is. Deliberately shallow: this is a game, not a lesson. */
    public int withdrawalSeverity(long now, Settings settings) {
        if (!withdrawing(now, settings)) return 0;
        if (dependence >= 85) return 3;
        if (dependence >= 65) return 2;
        return 1;
    }

    /**
     * Minutes until withdrawal would start if the player stays clean, or -1 when their
     * dependence is too low for it to start at all. Zero once it has started.
     */
    public double minutesUntilWithdrawal(long now, Settings settings) {
        if (dependence < WITHDRAWAL_MIN) return -1;
        return Math.max(0, (settings.withdrawalDelayMillis() - (now - lastUse)) / 60000.0);
    }

    /**
     * Minutes of withdrawal left at the plain decay rate, which is the honest estimate for a
     * player who does not sleep: dependence has to fall back under the threshold.
     */
    public double withdrawalMinutesLeft(Settings settings) {
        if (dependence < WITHDRAWAL_MIN || settings.dependenceDecayPerMinute() <= 0) return 0;
        return (dependence - WITHDRAWAL_MIN) / settings.dependenceDecayPerMinute();
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
