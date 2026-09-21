package dev.lucas.slumdrugs.sim.drug;

/**
 * Turning a harvest into goods. Every method trades volume for strength: the press keeps what
 * it is given, the centrifuge concentrates, the still concentrates hard. What is lost is not
 * destroyed — spent mash comes back as compost, which feeds {@link Cultivation}.
 *
 * <p>Nothing here describes a real process. The verbs are press, separate and distil, the
 * inputs are invented plants, and the numbers exist to make a game loop, not a recipe.
 */
public final class Refining {

    public enum Method {
        /** Dried material into product, one for one. No concentration, no reagent needed. */
        PRESS(1.00, 0, 0, 20),
        /** The centrifuge: spins a batch apart. Three quarters comes through, stronger. */
        SEPARATE(0.75, 10, 15, 40),
        /** The still: half the volume, and the strongest thing a player can make. */
        DISTIL(0.50, 20, 20, 60);

        /** Share of the input units that survives as product. */
        public final double outputRatio;
        /** Quality added by the method itself. */
        public final int qualityGain;
        /** Extra quality available from a perfect reagent. */
        public final int reagentGain;
        /** Seconds per batch, hand-driven. */
        public final int seconds;

        Method(double outputRatio, int qualityGain, int reagentGain, int seconds) {
            this.outputRatio = outputRatio;
            this.qualityGain = qualityGain;
            this.reagentGain = reagentGain;
            this.seconds = seconds;
        }

        /** Power halves the time. It buys throughput, never a better product. */
        public int seconds(boolean powered) { return powered ? Math.max(1, seconds / 2) : seconds; }

        /** Strokes of a hand press that add up to one unpowered run. */
        public int strokes() { return Math.max(1, (seconds + HAND_STROKE_SECONDS - 1) / HAND_STROKE_SECONDS); }
    }

    /** Work one pull on a hand press is worth, in seconds of the method's run time. */
    public static final int HAND_STROKE_SECONDS = 4;

    /** What goes in: a stack of one substance at one quality. */
    public record Batch(int inputQuality, int units) {
        public Batch {
            inputQuality = Math.max(0, Math.min(100, inputQuality));
            if (units < 1) throw new IllegalArgumentException("A batch needs at least one unit");
        }
    }

    /** What comes out, plus the spent mash worth composting. */
    public record Result(int quality, int units, int wasteUnits, int compostQuality) {}

    private Refining() {}

    /**
     * @param reagentQuality 0-100; PRESS ignores it, the other methods lean on it
     * @param skill          0-1, the operator's hand. Worth a few points, never decisive.
     */
    public static Result run(Method method, Batch batch, int reagentQuality, double skill) {
        if (method == null) throw new IllegalArgumentException("method is required");
        int reagent = Math.max(0, Math.min(100, reagentQuality));
        double hand = Math.max(0, Math.min(1, skill));

        int quality = Quality.clamp(batch.inputQuality()
                + method.qualityGain
                + method.reagentGain * reagent / 100.0
                + hand * 5);

        int units = Math.max(1, (int) Math.round(batch.units() * method.outputRatio));
        int waste = Math.max(0, batch.units() - units);
        return new Result(quality, units, waste, compostQuality(batch.inputQuality()));
    }

    /**
     * Spent mash makes compost a little worse than the crop it came from — good enough that
     * a grower who refines their own harvest slowly improves their ground for free.
     */
    public static int compostQuality(int batchQuality) {
        // Floor, not round: at low quality a rounded 0.8 lands back on the input, which would
        // make compost as good as the crop it came from and break the loop's direction.
        return (int) (Math.max(0, Math.min(100, batchQuality)) * 0.8);
    }

    /** Whether a reagent is required at all. The press runs dry; the others do not. */
    public static boolean needsReagent(Method method) { return method.reagentGain > 0; }
}
