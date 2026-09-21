package dev.lucas.slumdrugs.sim.drug;

/**
 * Raw harvest into dried material. Drying changes nothing about volume — a bundle comes down
 * as heavy as it went up — and adds a little quality when it is taken at the right moment.
 * Left hanging long past done, it slowly goes to dust. That is the whole tension of the loft:
 * the player is asked to come back for it, and rewarded a little for doing so on time.
 *
 * <p>Pure functions of elapsed time. The platform layer keeps the clock and the bundles.
 */
public final class Drying {

    /** Seconds a bundle needs on the rail before it is dried. */
    public static final int SECONDS = 90;

    /** Seconds after done during which a bundle keeps its full worth. */
    public static final int GRACE_SECONDS = 180;

    /** Quality a well-timed drying adds. */
    public static final int BONUS = 3;

    /** The most quality over-drying can cost. Neglect is a loss, never a total one. */
    public static final int MAX_LOSS = 20;

    private Drying() {}

    /** 0-1, how far along a bundle is. Stays at 1 once done, however long it hangs. */
    public static double progress(double secondsHung, int dryingSeconds) {
        if (dryingSeconds <= 0) return 1;
        return Math.max(0, Math.min(1, secondsHung / dryingSeconds));
    }

    public static boolean ready(double secondsHung, int dryingSeconds) {
        return progress(secondsHung, dryingSeconds) >= 1;
    }

    /**
     * What a bundle is worth when it comes down. Before it is ready this is simply the input
     * quality, because a caller should not be taking it down at all; the platform layer
     * refuses. Past the grace window it loses a point for every third of a drying time.
     */
    public static int quality(int inputQuality, double secondsHung, int dryingSeconds) {
        int input = Math.max(0, Math.min(100, inputQuality));
        if (!ready(secondsHung, dryingSeconds)) return input;
        double over = secondsHung - dryingSeconds - GRACE_SECONDS;
        if (over <= 0) return Quality.clamp(input + BONUS);
        int loss = (int) Math.min(MAX_LOSS, over / (Math.max(1, dryingSeconds) / 3.0));
        return Quality.clamp(input + BONUS - loss);
    }
}
