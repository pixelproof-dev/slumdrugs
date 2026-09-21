package dev.lucas.slumdrugs.sim.economy;

/**
 * Money as it is carried: pence, shillings of twelve, sovereigns of twenty shillings. Every
 * price in the rules is in pence; this class splits a sum into the coins that make it and
 * takes the counting house's cut for stamping loose coin. Nothing here is a real currency.
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
    public static double baseUnitPence(int basePoints) {
        return Math.max(0, basePoints) * PENCE_PER_BASE_POINT;
    }

    /** What the counting house keeps of a loose sum, rounded in its favour. */
    public static long stampFee(long pence) {
        return (long) Math.ceil(Math.max(0, pence) * STAMP_CUT);
    }

    /** What comes back stamped. Never nothing for a sum that was something. */
    public static long stamped(long pence) {
        if (pence <= 0) return 0;
        return Math.max(1, pence - stampFee(pence));
    }

    /** "3 sov 4s 6d", dropping the parts that are zero, "0d" for nothing. */
    public static String format(long pence) {
        Split s = split(pence);
        StringBuilder out = new StringBuilder();
        if (s.sovereigns() > 0) out.append(s.sovereigns()).append(" sov");
        if (s.shillings() > 0) out.append(out.isEmpty() ? "" : " ").append(s.shillings()).append('s');
        if (s.pennies() > 0 || out.isEmpty()) out.append(out.isEmpty() ? "" : " ").append(s.pennies()).append('d');
        return out.toString();
    }
}
