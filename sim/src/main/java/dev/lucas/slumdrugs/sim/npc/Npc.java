package dev.lucas.slumdrugs.sim.npc;

/**
 * The people of the quarter. Crew members are villagers who went bad rather than a hostile mob
 * faction, which is what makes them buyable, poachable and unsettling — see the design note in
 * docs/MOD-GDD.md §5.9.
 *
 * <p>Aggression is readable from behaviour rather than a bar: what an NPC does is decided by
 * which band they are in, and the bands are wide enough to be legible.
 */
public final class Npc {

    public enum Role {
        /** Buys from the player on a schedule. */
        CUSTOMER(false),
        /** Lives here. Sees things, and might mention them. */
        RESIDENT(false),
        /** Sells seed and supplies. */
        TRADER(false),
        /** Treats the dependent, for a price. */
        HEALER(false),
        /** Sells what the shops will not. */
        BROKER(false),
        /** The Watch. Not a crew, but the thing crews watch for. */
        CONSTABLE(true),
        /** Crew muscle. */
        BRUISER(true),
        /** Crew leadership: their mood spreads to the people around them. */
        LIEUTENANT(true),
        /** A hired hand: works a station for the player who pays them, and quits when they stop. */
        HAND(false);

        /** Whether this role carries aggression at all. A healer never turns on you. */
        public final boolean hostileCapable;

        Role(boolean hostileCapable) { this.hostileCapable = hostileCapable; }
    }

    /** What an NPC does at a given aggression. Ordered, and each band is visible in-world. */
    public enum Stance {
        /** Trades, talks, takes a bribe. Hands empty. */
        CALM,
        /** Follows, warns you off, reports you. Turns to watch. */
        WARY,
        /** Demands tribute, shoves, blocks doorways. Hand on a cosh. */
        DEMANDING,
        /** Attacks on sight. */
        HOSTILE
    }

    public static final double MAX = 100;

    private Npc() {}

    public static Stance stance(Role role, double aggression) {
        if (role == null || !role.hostileCapable) return Stance.CALM;
        if (aggression >= 75) return Stance.HOSTILE;
        if (aggression >= 50) return Stance.DEMANDING;
        if (aggression >= 25) return Stance.WARY;
        return Stance.CALM;
    }

    public static double clamp(double aggression) { return Math.max(0, Math.min(MAX, aggression)); }

    /**
     * Where an NPC's mood settles, given the standing between the player and their crew and how
     * deep into that crew's turf the player is. Standing is -100..100; turf depth is 0..1.
     *
     * <p>A crew that likes you stays calm on their own ground; a crew that hates you does not
     * calm down just because you left it.
     */
    public static double restingAggression(Role role, double standing, double turfDepth) {
        if (role == null || !role.hostileCapable) return 0;
        double hostility = (-Math.max(-100, Math.min(100, standing)) + 100) / 2.0;  // 0..100
        double depth = Math.max(0, Math.min(1, turfDepth));
        return clamp(hostility * (0.6 + 0.4 * depth));
    }

    /**
     * Moves aggression toward its resting point, and never instantly: a crew that just caught
     * you takes a while to settle, which is what makes leaving and coming back a real tactic.
     *
     * @param minutes elapsed world minutes
     */
    public static double settle(double aggression, double resting, double minutes) {
        double rate = 2.0 * Math.max(0, minutes);
        double current = clamp(aggression);
        double target = clamp(resting);
        if (current > target) return Math.max(target, current - rate);
        return Math.min(target, current + rate);
    }

    /** A provocation the player caused: a hit runner, an undercut price, a refused demand. */
    public static double provoke(double aggression, double amount) {
        return clamp(aggression + Math.max(0, amount));
    }

    /** Tribute, a drink, time spent not being a problem. */
    public static double appease(double aggression, double amount) {
        return clamp(aggression - Math.max(0, amount));
    }

    /**
     * A lieutenant's mood spreads to the crew around them, but only part of the way — the crew
     * takes its lead from the boss without becoming a copy of him.
     */
    public static double spread(double memberAggression, double lieutenantAggression) {
        return clamp(memberAggression + (clamp(lieutenantAggression) - clamp(memberAggression)) * 0.35);
    }
}
