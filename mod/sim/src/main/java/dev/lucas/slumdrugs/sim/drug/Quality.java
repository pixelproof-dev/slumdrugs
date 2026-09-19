package dev.lucas.slumdrugs.sim.drug;

/** Quality grades and the diminishing-returns curves that depend on them. */
public final class Quality {

    public enum Grade {
        POOR("Poor", 0),
        STANDARD("Standard", 35),
        GOOD("Good", 60),
        PREMIUM("Premium", 80);

        public final String label;
        public final int min;

        Grade(String label, int min) {
            this.label = label;
            this.min = min;
        }

        public static Grade of(int quality) {
            Grade g = POOR;
            for (Grade c : values()) if (quality >= c.min) g = c;
            return g;
        }

        public static Grade parse(String s, Grade fallback) {
            if (s == null) return fallback;
            for (Grade g : values()) if (g.name().equalsIgnoreCase(s) || g.label.equalsIgnoreCase(s)) return g;
            return fallback;
        }
    }

    private Quality() {}

    public static int clamp(double q) {
        return (int) Math.round(Math.max(0, Math.min(100, q)));
    }

    /** Price multiplier: 0.5 at q=0, ~1.0 at q=50, 1.2 at q=100 (square-root curve, diminishing returns). */
    public static double priceFactor(int quality) {
        return 0.5 + 0.7 * Math.sqrt(quality / 100.0);
    }

    /** Effect-duration multiplier: 0.7 at q=0 up to 1.2 at q=100. */
    public static double durationFactor(int quality) {
        return 0.7 + 0.5 * Math.sqrt(quality / 100.0);
    }
}
