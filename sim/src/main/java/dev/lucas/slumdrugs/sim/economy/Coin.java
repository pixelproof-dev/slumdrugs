package dev.lucas.slumdrugs.sim.economy;

/**
 * Money as it is carried: pence, shillings of twelve, sovereigns of twenty shillings. Every
 * price in the rules is in pence; this class splits a sum into the coins that make it and
 * takes the counting house's cut for stamping loose coin. Nothing here is a real currency.
 *
 * <p>Since the setting moved to the present day the coins are cash notes, rolls and bricks and a
 * sum is shown in dollars (see {@link #format}), but the arithmetic below is unchanged.
 */
public final class Coin {

    public static final int PENNY = 1;
    public static final int SHILLING = 12;
    public static final int SOVEREIGN = 20 * SHILLING;

    /** Pence a Standard unit at the plugin's base price of ten is worth: about two shillings. */
    public static final double PENCE_PER_BASE_POINT = 2.4;

    /** The counting house's share for stamping loose coin. */
    public static final double STAMP_CUT = 0.10;

    /** What a new player starts with. */
    public static final int STARTING_PURSE = 10 * SHILLING;

    /** A sum broken into coins, largest first. */
    public record Split(int sovereigns, int shillings, int pennies) {
        public long pence() { return (long) sovereigns * SOVEREIGN + (long) shillings * SHILLING + pennies; }
    }

    private Coin() {}

    public static Split split(long pence) {
        long left = Math.max(0, pence);
        int sovereigns = (int) Math.min(Integer.MAX_VALUE, left / SOVEREIGN);
        left -= (long) sovereigns * SOVEREIGN;
        int shillings = (int) (left / SHILLING);
        left -= (long) shillings * SHILLING;
        return new Split(sovereigns, shillings, (int) left);
    }

    /** Pence for a unit priced at a base of {@code basePoints}, before quality and demand. */
    public static double baseUnitPence(int basePoints) { return baseUnitPence(basePoints, PENCE_PER_BASE_POINT); }

    public static double baseUnitPence(int basePoints, double pencePerPoint) {
        return Math.max(0, basePoints) * Math.max(0, pencePerPoint);
    }

    /** What the counting house keeps of a loose sum, rounded in its favour. */
    public static long stampFee(long pence) { return stampFee(pence, STAMP_CUT); }

    public static long stampFee(long pence, double cut) {
        return (long) Math.ceil(Math.max(0, pence) * Math.max(0, Math.min(1, cut)));
    }

    /** What comes back stamped. Never nothing for a sum that was something. */
    public static long stamped(long pence) { return stamped(pence, STAMP_CUT); }

    public static long stamped(long pence, double cut) {
        if (pence <= 0) return 0;
        return Math.max(1, pence - stampFee(pence, cut));
    }

    /**
     * A sum as a player reads it: dollars, grouped by thousands, "$0" for nothing.
     *
     * <p>The rules still count in pence, shillings of twelve and sovereigns of twenty shillings,
     * and nothing about that changed with the setting: every price, cut and wage is balanced in
     * those units and checked in them. Only what a player sees moved to the present day, at one
     * dollar to the penny -- so the starting purse of ten shillings reads $120, a day's wage $24,
     * a sovereign $240, and the notes, rolls and bricks the coins became are those same sums.
     */
    public static String format(long pence) {
        return String.format(java.util.Locale.ROOT, "$%,d", Math.max(0, pence));
    }
}
