package dev.lucas.slumdrugs.sim.drug;

/**
 * What a planting is worth. Three inputs the player controls — the seed they kept, the ground
 * they prepared, the compost they spent — decide both how good the harvest is and how much of
 * it there is.
 *
 * <p>Two deliberate shapes. Seed quality dominates, so keeping your best harvest for seed is
 * the strongest long game available. Everything else has diminishing returns, so a player
 * cannot buy their way past a bad line with compost alone.
 */
public final class Cultivation {

    /** What the frame is standing in. Bare ground works and is visibly the worst choice. */
    public enum Soil {
        BARE(-10, 1.00),
        TILLED(0, 1.00),
        COMPOSTED(8, 1.15),
        RICH(15, 1.30);

        /** Added to the quality score. */
        public final int qualityBonus;
        /** Multiplies the harvest count. */
        public final double yieldFactor;

        Soil(int qualityBonus, double yieldFactor) {
            this.qualityBonus = qualityBonus;
            this.yieldFactor = yieldFactor;
        }
    }

    /** The most compost a single frame will take. Beyond this it is wasted, and the game says so. */
    public static final int MAX_CHARGES = 3;

    /**
     * Where a substance is comfortable: warmth and damp both 0-1, each with a band it likes.
     * Inside the band it grows as well as it can; outside, fit falls off with distance, and it
     * is a poor frame rather than a dead one at the far end.
     */
    public record Band(double warmthLow, double warmthHigh, double dampLow, double dampHigh) {
        public Band {
            warmthLow = clampUnit(warmthLow);
            warmthHigh = Math.max(warmthLow, clampUnit(warmthHigh));
            dampLow = clampUnit(dampLow);
            dampHigh = Math.max(dampLow, clampUnit(dampHigh));
        }

        /** Likes everything: the fit of a substance nobody has tuned yet. */
        public static Band any() { return new Band(0, 1, 0, 1); }
    }

    /** How far outside a band before fit reaches its floor. */
    public static final double BAND_FALLOFF = 0.5;

    /** The worst climate fit. Light alone can already take a frame to 0.6; this stacks on it. */
    public static final double CLIMATE_FLOOR = 0.4;

    private static double clampUnit(double v) { return Math.max(0, Math.min(1, v)); }

    private static double distanceOutside(double v, double low, double high) {
        v = clampUnit(v);
        return v < low ? low - v : v > high ? v - high : 0;
    }

    /** 0-1 climate fit: one inside the band on both axes, falling to the floor as either strays. */
    public static double climateFit(double warmth, double damp, Band band) {
        return climateFit(warmth, damp, band, CLIMATE_FLOOR);
    }

    /** The same with a line's own floor: a hardy strain minds a bad climate less. */
    public static double climateFit(double warmth, double damp, Band band, double floor) {
        if (band == null) return 1;
        double bottom = Math.max(CLIMATE_FLOOR, Math.min(1, floor));
        double outside = distanceOutside(warmth, band.warmthLow(), band.warmthHigh())
                + distanceOutside(damp, band.dampLow(), band.dampHigh());
        double fit = 1 - outside / BAND_FALLOFF * (1 - bottom);
        return Math.max(bottom, Math.min(1, fit));
    }

    /** Neutral quality: an average seed in tilled ground with nothing added returns this. */
    public static final int BASELINE = 20;

    /**
     * @param seedQuality        0-100, carried by the seed from the plant it came off
     * @param soil               what the frame sits in
     * @param fertiliserCharges  0-3 doses of compost applied
     * @param fertiliserQuality  0-100, how good that compost was
     * @param environmentFit     0-1, how close warmth, light and damp were to the substance's band
     */
    public record Inputs(int seedQuality, Soil soil, int fertiliserCharges, int fertiliserQuality,
                         double environmentFit) {
        public Inputs {
            if (soil == null) throw new IllegalArgumentException("soil is required");
            seedQuality = clampPercent(seedQuality);
            fertiliserQuality = clampPercent(fertiliserQuality);
            fertiliserCharges = Math.max(0, Math.min(MAX_CHARGES, fertiliserCharges));
            environmentFit = Math.max(0, Math.min(1, environmentFit));
        }

        /** An average seed, tilled ground, nothing added, perfect conditions. */
        public static Inputs neutral() { return new Inputs(50, Soil.TILLED, 0, 0, 1); }
    }

    /** A finished harvest: what came off the plant, and what to keep for next time. */
    public record Harvest(int quality, int units, int seeds, int seedQuality) {}

    private Cultivation() {}

    private static int clampPercent(int v) { return Math.max(0, Math.min(100, v)); }

    /** 0-100. Seed quality is 60% of the ceiling; the rest is preparation. */
    public static int quality(Inputs in) {
        double seed = in.seedQuality() * 0.6;
        double compost = in.fertiliserQuality() / 100.0 * 10.0 * Math.sqrt(in.fertiliserCharges());
        double environment = (in.environmentFit() - 1) * 25.0;
        return Quality.clamp(BASELINE + seed + in.soil().qualityBonus + compost + environment);
    }

    /**
     * How many units come off a plant whose base yield is {@code baseYield}. Soil multiplies,
     * compost adds, and a good crop is slightly more generous than a poor one — never less
     * than a single unit, so a bad planting is a loss of time rather than of the seed.
     */
    public static int units(int baseYield, Inputs in, int quality) {
        if (baseYield < 1) throw new IllegalArgumentException("baseYield must be at least 1");
        double compost = 1 + 0.12 * in.fertiliserCharges() * in.fertiliserQuality() / 100.0;
        double grade = 0.8 + 0.4 * clampPercent(quality) / 100.0;
        return Math.max(1, (int) Math.round(baseYield * in.soil().yieldFactor * compost * grade));
    }

    /**
     * Seeds recovered from a harvest. Quality drifts around the crop it came off, so a line
     * improves slowly when the grower selects for it and decays when they do not.
     *
     * @param roll 0-1 from the caller's own randomness, so the rule stays deterministic here
     */
    public static int seedQuality(int harvestQuality, double roll) {
        double drift = (Math.max(0, Math.min(1, roll)) - 0.5) * 12.0;
        return Quality.clamp(clampPercent(harvestQuality) - 2 + drift);
    }

    /** One planting resolved end to end. */
    public static Harvest harvest(int baseYield, Inputs in, double seedRoll) {
        int quality = quality(in);
        int units = units(baseYield, in, quality);
        int seeds = 1 + (units >= baseYield * 2 ? 1 : 0);
        return new Harvest(quality, units, seeds, seedQuality(quality, seedRoll));
    }
}
