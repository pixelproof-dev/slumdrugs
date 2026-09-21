package dev.lucas.slumdrugs.sim.player;

/**
 * How far a player has climbed. Two counters the game already produces — units sold and coin
 * earned — decide the tier, and the tier decides which stations they may set up.
 *
 * <p>The ladder is the design document's, with two gaps made explicit rather than hidden.
 * Tier 0's pots and hand-drying do not exist yet, so the forcing frame and the loft are open
 * from the start; and standing, turf and influence are not modelled yet, so tiers above
 * Workshop cannot be reached. Both are gates to tighten once those systems land, and the
 * numbers here are the only place that needs to change.
 */
public final class Progression {

    public enum Tier {
        HAND_TO_MOUTH("Hand to mouth"),
        BACKROOM("Backroom"),
        WORKSHOP("Workshop"),
        APOTHECARY("Apothecary"),
        GUILD("Guild"),
        KINGPIN("Kingpin");

        public final String label;

        Tier(String label) { this.label = label; }

        public Tier next() { return ordinal() + 1 < values().length ? values()[ordinal() + 1] : this; }
    }

    /** Units sold that open the Backroom. */
    public static final int BACKROOM_UNITS = 20;

    /** Coin earned that opens the Workshop. Standing 15 is the other half, once standing exists. */
    public static final int WORKSHOP_COIN = 60;

    /** The highest tier the counters can reach today. */
    public static final Tier REACHABLE = Tier.WORKSHOP;

    public int unitsSold;

    /** In shillings. */
    public int coinEarned;

    /** Whether the starting purse has been handed over. */
    public boolean started;

    public Progression() {}

    public Progression(int unitsSold, int coinEarned) {
        this(unitsSold, coinEarned, false);
    }

    public Progression(int unitsSold, int coinEarned, boolean started) {
        this.unitsSold = Math.max(0, unitsSold);
        this.coinEarned = Math.max(0, coinEarned);
        this.started = started;
    }

    /** Records a sale. Negative amounts are ignored; a refund is not a sale undone. */
    public void sold(int units, int coin) {
        unitsSold += Math.max(0, units);
        coinEarned += Math.max(0, coin);
    }

    public Tier tier() {
        if (unitsSold < BACKROOM_UNITS) return Tier.HAND_TO_MOUTH;
        if (coinEarned < WORKSHOP_COIN) return Tier.BACKROOM;
        return Tier.WORKSHOP;
    }

    public boolean reached(Tier tier) { return tier().ordinal() >= tier.ordinal(); }

    /**
     * What stands between the player and the next tier: units still to sell, coin still to
     * earn, or nothing because the ladder ends here for now. Either count is zero when met.
     */
    public record Gate(Tier next, int unitsNeeded, int coinNeeded, boolean reachable) {}

    public Gate gate() {
        Tier now = tier();
        return switch (now) {
            case HAND_TO_MOUTH -> new Gate(Tier.BACKROOM, BACKROOM_UNITS - unitsSold, 0, true);
            case BACKROOM -> new Gate(Tier.WORKSHOP, 0, WORKSHOP_COIN - coinEarned, true);
            default -> new Gate(now.next(), 0, 0, false);
        };
    }
}
