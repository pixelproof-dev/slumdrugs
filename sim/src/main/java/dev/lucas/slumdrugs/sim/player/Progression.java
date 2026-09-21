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

    /**
     * Coin earned, in shillings, that opens the Workshop. Standing 15 is the other half, once
     * standing exists. The design said sixty; the week-of-play simulation showed sixty is
     * earned by the same twenty units that open the Backroom, so the two tiers opened at the
     * same moment. Twelve sovereigns puts the Workshop a few days behind the Backroom for a
     * careful grower with two frames, and a day behind for one with four.
     */
    public static final int WORKSHOP_COIN = 240;

    /** The two gates, so a server can set its own. */
    public record Settings(int backroomUnits, int workshopCoin) {
        public Settings {
            backroomUnits = Math.max(0, backroomUnits);
            workshopCoin = Math.max(0, workshopCoin);
        }
        public static Settings defaults() { return new Settings(BACKROOM_UNITS, WORKSHOP_COIN); }
    }

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

    public Tier tier() { return tier(Settings.defaults()); }

    public Tier tier(Settings s) {
        if (unitsSold < s.backroomUnits()) return Tier.HAND_TO_MOUTH;
        if (coinEarned < s.workshopCoin()) return Tier.BACKROOM;
        return Tier.WORKSHOP;
    }

    public boolean reached(Tier tier) { return reached(tier, Settings.defaults()); }
    public boolean reached(Tier tier, Settings s) { return tier(s).ordinal() >= tier.ordinal(); }

    /**
     * What stands between the player and the next tier: units still to sell, coin still to
     * earn, or nothing because the ladder ends here for now. Either count is zero when met.
     */
    public record Gate(Tier next, int unitsNeeded, int coinNeeded, boolean reachable) {}

    public Gate gate() { return gate(Settings.defaults()); }

    public Gate gate(Settings s) {
        Tier now = tier(s);
        return switch (now) {
            case HAND_TO_MOUTH -> new Gate(Tier.BACKROOM, s.backroomUnits() - unitsSold, 0, true);
            case BACKROOM -> new Gate(Tier.WORKSHOP, 0, s.workshopCoin() - coinEarned, true);
            default -> new Gate(now.next(), 0, 0, false);
        };
    }
}
