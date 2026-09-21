package dev.lucas.slumdrugs.sim.npc;

/**
 * What a regular thinks of the player, 0-100. Good goods at a fair price raise it, goods
 * below their floor and cut goods drop it. High loyalty is a standing order; none at all is
 * a customer lost, and one who talks. This is where cutting costs what the design says.
 */
public final class Loyalty {

    public static final double START = 50;

    /** Loyalty at and above which a regular takes a double hand at a premium. */
    public static final double STANDING_ORDER = 80;

    /** Loyalty at and below which a regular will not buy, and tells the Watch you asked. */
    public static final double LOST = 5;

    public static final double GOOD_SALE = 3;
    public static final double PREMIUM_BONUS = 2;
    public static final double BELOW_FLOOR = -5;
    public static final double CUT_GOODS = -15;

    /** Quality a regular insists on: somewhere between poor-but-honest and good. */
    public static final int FLOOR_MIN = 20;
    public static final int FLOOR_MAX = 60;

    private Loyalty() {}

    public static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    /** A regular's quality floor from a fixed seed, so it is theirs for life. */
    public static int floor(int seed) {
        return FLOOR_MIN + Math.floorMod(seed, FLOOR_MAX - FLOOR_MIN + 1);
    }

    /** Where loyalty lands after a sale of {@code quality} goods, cut or not, to a regular with {@code floor}. */
    public static double afterSale(double loyalty, int quality, int floor, boolean cut) {
        double delta;
        if (cut) delta = CUT_GOODS;
        else if (quality < floor) delta = BELOW_FLOOR;
        else delta = GOOD_SALE + (quality >= 80 ? PREMIUM_BONUS : 0);
        return clamp(loyalty + delta);
    }

    /** 0.85 at no loyalty up to 1.15 at full: a regular pays their friends better. */
    public static double priceFactor(double loyalty) {
        return 0.85 + 0.3 * clamp(loyalty) / 100.0;
    }

    /** A standing order takes twice the hand. */
    public static int hand(double loyalty, int hand) {
        return clamp(loyalty) >= STANDING_ORDER ? hand * 2 : hand;
    }

    public static boolean lost(double loyalty) { return clamp(loyalty) <= LOST; }

    /** Left alone, a regular drifts back toward indifference, a point at a time. */
    public static double settle(double loyalty) {
        double v = clamp(loyalty);
        if (v > START) return Math.max(START, v - 1);
        if (v < START) return Math.min(START, v + 1);
        return v;
    }
}
