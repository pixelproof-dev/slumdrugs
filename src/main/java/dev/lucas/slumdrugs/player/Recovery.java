package dev.lucas.slumdrugs.player;

/** Wall-clock recovery shared by online ticks and loading a saved player. */
public final class Recovery {
    private Recovery() {}
    public static double tolerance(double value, long from, long to, double perMinute) {
        return Math.max(0, value - Math.max(0, to - from) / 60000.0 * Math.max(0, perMinute));
    }
    public static double dependence(double value, long from, long to, long lastUse,
                                    long delay, long lastSleep, double perMinute, double restMultiplier) {
        long start = Math.max(from, lastUse + Math.max(0, delay));
        if (to <= start) return value;
        long rested = lastSleep <= 0 ? 0 : Math.max(0,
                Math.min(to, lastSleep + 600000L) - Math.max(start, lastSleep));
        double elapsed = to - start + rested * (Math.max(1, restMultiplier) - 1);
        return Math.max(0, value - elapsed / 60000.0 * Math.max(0, perMinute));
    }
}
