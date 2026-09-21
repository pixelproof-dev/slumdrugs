package dev.lucas.slumdrugs.sim.drug;

/**
 * A line of one substance: four traits, each 0-100, carried on the seed and passed down with
 * drift. Potency is what the product is worth and how hard it hits; vigour how fast and how
 * much it grows; hardiness how far outside its comfort it will still grow; subtlety how
 * little a sale is noticed. Crossing two seeds gives offspring near the parents' mean with a
 * spread, so a line can be bred toward something and drifts away from it when it is not.
 *
 * <p>Invented plants with invented traits; the numbers exist to give breeding a direction.
 */
public record Strain(int potency, int vigour, int hardiness, int subtlety) {

    /** Every seed nobody has bred. */
    public static final Strain AVERAGE = new Strain(50, 50, 50, 50);

    /** How far a cross can land from the parents' mean, either way. */
    public static final int CROSS_SPREAD = 10;

    /** How far a harvest's seed drifts from the plant it came off, either way. */
    public static final int DRIFT = 3;

    public Strain {
        potency = clamp(potency);
        vigour = clamp(vigour);
        hardiness = clamp(hardiness);
        subtlety = clamp(subtlety);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    private static int spread(double roll, int by) {
        return (int) Math.round((Math.max(0, Math.min(1, roll)) - 0.5) * 2 * by);
    }

    /**
     * Offspring of two parents: the mean of each trait, moved by up to the spread either way.
     *
     * @param rolls four values 0-1 from the caller's randomness, one per trait
     */
    public static Strain cross(Strain a, Strain b, double[] rolls) {
        if (a == null) a = AVERAGE;
        if (b == null) b = AVERAGE;
        if (rolls == null || rolls.length < 4) throw new IllegalArgumentException("a cross needs four rolls");
        return new Strain(
                (a.potency + b.potency) / 2 + spread(rolls[0], CROSS_SPREAD),
                (a.vigour + b.vigour) / 2 + spread(rolls[1], CROSS_SPREAD),
                (a.hardiness + b.hardiness) / 2 + spread(rolls[2], CROSS_SPREAD),
                (a.subtlety + b.subtlety) / 2 + spread(rolls[3], CROSS_SPREAD));
    }

    /** The seed a harvest returns: the same line, drifted a little. */
    public Strain drift(double[] rolls) {
        if (rolls == null || rolls.length < 4) throw new IllegalArgumentException("a drift needs four rolls");
        return new Strain(
                potency + spread(rolls[0], DRIFT),
                vigour + spread(rolls[1], DRIFT),
                hardiness + spread(rolls[2], DRIFT),
                subtlety + spread(rolls[3], DRIFT));
    }

    /** 0.7 at no potency, 1.3 at full: multiplies dose and price. */
    public double potencyFactor() { return 0.7 + 0.6 * potency / 100.0; }

    /** 0.8 to 1.2: multiplies growth speed and yield. */
    public double vigourFactor() { return 0.8 + 0.4 * vigour / 100.0; }

    /** The worst a bad climate can do to this line: the usual floor up to twice it. */
    public double climateFloor() {
        return Cultivation.CLIMATE_FLOOR + (Math.min(1, Cultivation.CLIMATE_FLOOR * 2) - Cultivation.CLIMATE_FLOOR) * hardiness / 100.0;
    }

    /** 1.4 at no subtlety, 0.6 at full: multiplies the suspicion a sale draws. */
    public double subtletyFactor() { return 1.4 - 0.8 * subtlety / 100.0; }

    public boolean isAverage() { return equals(AVERAGE); }
}
