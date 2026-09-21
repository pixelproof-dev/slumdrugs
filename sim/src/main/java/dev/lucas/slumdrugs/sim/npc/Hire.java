package dev.lucas.slumdrugs.sim.npc;

import dev.lucas.slumdrugs.sim.player.Progression;

/**
 * A hired hand: one resident who works a station for the player, for a sovereign to start
 * and a wage a day out of the nearest crate. The design's first crew hire, without a crew:
 * no orders, no loyalty, no muscle. They press what is under the plate and take down what is
 * dry, and the day the crate has no wage in it they go back to being a resident.
 */
public final class Hire {

    /** What it costs to take someone on: a sovereign, stamped. */
    public static final long HIRE_PENCE = 240;

    /** A day's wage: two shillings, any coin. */
    public static final long WAGE_PENCE = 24;

    /** How far from the hand a crate or a station counts. */
    public static final int REACH = 6;

    private Hire() {}

    /** Only a Workshop grower has work enough for a hand. */
    public static boolean canHire(Progression.Tier tier) {
        return tier != null && tier.ordinal() >= Progression.Tier.WORKSHOP.ordinal();
    }

    /** Whether a wage is owed: a new day has begun since the last one paid. */
    public static boolean wageDue(long paidDay, long day) {
        return day != paidDay;
    }

    /**
     * Which coins the hand takes for a wage, smallest first: the counts to remove from each
     * denomination, or null when the coins on offer do not add up to the wage. A hand keeps
     * the change when a coin is too big to split, which is why small coin is worth leaving.
     *
     * @param values  pence per coin, one entry per stack on offer
     * @param counts  coins in each stack
     */
    public static int[] take(long wage, long[] values, int[] counts) {
        if (values == null || counts == null || values.length != counts.length) throw new IllegalArgumentException("coins need values and counts");
        int[] taken = new int[values.length];
        if (wage <= 0) return taken;
        long total = 0;
        for (int i = 0; i < values.length; i++) total += Math.max(0, values[i]) * Math.max(0, counts[i]);
        if (total < wage) return null;

        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Long.compare(values[a], values[b]));

        long left = wage;
        for (int i : order) {
            if (left <= 0) break;
            if (values[i] <= 0 || counts[i] <= 0) continue;
            int want = (int) Math.min(counts[i], (left + values[i] - 1) / values[i]);
            taken[i] = want;
            left -= want * values[i];
        }
        return left <= 0 ? taken : null;
    }
}
