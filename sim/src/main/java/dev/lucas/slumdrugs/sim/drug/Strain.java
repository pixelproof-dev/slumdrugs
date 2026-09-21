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
public record Strain(int potency, int vigour, int hardiness, int subtlety, int stable, String name, int anchor) {

    /** Every seed nobody has bred. */
    public static final Strain AVERAGE = new Strain(50, 50, 50, 50);

    /** Generations a line must hold within tolerance before it can be named. */
    public static final int STABLE_GENERATIONS = 5;

    /** How far a harvest's seed may stray from its parent and still count as the same line. */
    public static final int TOLERANCE = 5;

    /** What a name is worth on the street: regulars pay a tenth more for a line they know. */
    public static final double NAMED_REPUTATION = 1.1;

    public Strain(int potency, int vigour, int hardiness, int subtlety) {
        this(potency, vigour, hardiness, subtlety, 0, "", 0);
    }

    public Strain(int potency, int vigour, int hardiness, int subtlety, int stable, String name) {
        this(potency, vigour, hardiness, subtlety, stable, name, 0);
    }

    /** How far a cross can land from the parents' mean, either way. */
    public static final int CROSS_SPREAD = 10;

    /** How far a harvest's seed drifts from the plant it came off, either way. */
    public static final int DRIFT = 3;

    public Strain {
        potency = clamp(potency);
        vigour = clamp(vigour);
        hardiness = clamp(hardiness);
        subtlety = clamp(subtlety);
        stable = Math.max(0, stable);
        name = name == null ? "" : name.trim();
        anchor = Math.max(0, anchor);
    }

    /**
     * The traits a line is measured against: where it started, packed into one int so the
     * seed carries it cheaply. Zero means the line is its own anchor.
     */
    public static int pack(int p, int v, int h, int s) { return 1 + ((p << 21) | (v << 14) | (h << 7) | s); }

    /** The anchor's traits, as a strain with nothing else on it. */
    public Strain origin() {
        if (anchor == 0) return new Strain(potency, vigour, hardiness, subtlety);
        int packed = anchor - 1;
        return new Strain((packed >> 21) & 127, (packed >> 14) & 127, (packed >> 7) & 127, packed & 127);
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

    /**
     * The seed a harvest returns: the same line, drifted a little. A generation that stays
     * within tolerance of where the line started counts toward stability; one that wanders
     * past it is a new line, and loses the count and the name with it. Drift is a random walk,
     * so holding a line means choosing the seed that stayed closest, and crossing a strayed
     * seed back toward the line at the grafting bench.
     */
    public Strain drift(double[] rolls) {
        if (rolls == null || rolls.length < 4) throw new IllegalArgumentException("a drift needs four rolls");
        Strain child = new Strain(
                potency + spread(rolls[0], DRIFT),
                vigour + spread(rolls[1], DRIFT),
                hardiness + spread(rolls[2], DRIFT),
                subtlety + spread(rolls[3], DRIFT));
        Strain from = origin();
        if (child.sameLine(from))
            return new Strain(child.potency, child.vigour, child.hardiness, child.subtlety, stable + 1, name,
                    pack(from.potency, from.vigour, from.hardiness, from.subtlety));
        // Strayed: a new line, its own anchor, with no name and no history.
        return new Strain(child.potency, child.vigour, child.hardiness, child.subtlety);
    }

    /** Whether two seeds are the same line: every trait within tolerance. */
    public boolean sameLine(Strain other) {
        if (other == null) return false;
        return Math.abs(potency - other.potency) <= TOLERANCE
                && Math.abs(vigour - other.vigour) <= TOLERANCE
                && Math.abs(hardiness - other.hardiness) <= TOLERANCE
                && Math.abs(subtlety - other.subtlety) <= TOLERANCE;
    }

    public boolean named() { return !name.isEmpty(); }

    /** A line can be named once it has held for enough generations, and only once. */
    public boolean canName() { return !named() && stable >= STABLE_GENERATIONS; }

    public Strain withName(String newName) {
        return new Strain(potency, vigour, hardiness, subtlety, stable, newName, anchor);
    }

    /** What the street pays for a name it knows. */
    public double reputationFactor() { return named() ? NAMED_REPUTATION : 1.0; }

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
