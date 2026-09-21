package dev.lucas.slumdrugs.sim.drug;

/**
 * Stretching product with filler. More units, worse ones, and a batch that is more dangerous
 * to the people who use it. Always the profitable choice and always the one that costs the
 * seller their regulars: that is the design's moral pressure valve, and the numbers here
 * exist to make that trade real rather than to describe anything.
 */
public final class Cutting {

    /** The most filler a batch will take: equal parts. */
    public static final double MAX_FILLER_RATIO = 1.0;

    /** Quality lost on top of plain dilution: a cut batch is worse than the sum of its parts. */
    public static final int HANDLING_LOSS = 5;

    /** How much sooner a fully cut dose tips into overdose: the threshold falls by this share. */
    public static final double OVERDOSE_PENALTY = 0.4;

    /** A cut batch: the units it makes, what they are worth, and how much of them is filler. */
    public record Result(int units, int quality, double cutRatio) {}

    private Cutting() {}

    /** The most filler that can go into {@code units} of product. */
    public static int maxFiller(int units) {
        return (int) Math.floor(Math.max(0, units) * MAX_FILLER_RATIO);
    }

    /**
     * @param cutBefore 0-1, how much of the input was already filler; cutting cut goods stacks
     */
    public static Result cut(int quality, int units, int filler, double cutBefore) {
        if (units < 1) throw new IllegalArgumentException("A batch needs at least one unit");
        int added = Math.max(0, Math.min(maxFiller(units), filler));
        double before = Math.max(0, Math.min(1, cutBefore));
        int total = units + added;
        double pureUnits = units * (1 - before);
        double ratio = 1 - pureUnits / total;
        double diluted = Math.max(0, Math.min(100, quality)) * units / (double) total;
        int outQuality = added == 0 ? Quality.clamp(diluted) : Quality.clamp(diluted - HANDLING_LOSS);
        return new Result(total, outQuality, ratio);
    }

    /** The overdose threshold for a dose that is partly filler. */
    public static double overdoseThreshold(double threshold, double cutRatio) {
        double ratio = Math.max(0, Math.min(1, cutRatio));
        return threshold * (1 - OVERDOSE_PENALTY * ratio);
    }
}
