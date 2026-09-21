package dev.lucas.slumdrugs.sim.npc;

import dev.lucas.slumdrugs.sim.player.Progression;

/**
 * Turf without war: a corner of the quarter is held by nobody, by a crew, or by a player.
 * A player takes a free corner for a sovereign and takes a crew's corner only when the crew
 * likes them enough to allow it, and it still costs them some of that liking. On their own
 * corner a player's regulars pay a little more and the street looks the other way a little;
 * a crew that gave a corner up behaves, on that corner, as if it liked the player less than
 * it does. The war stages of the design, where corners are taken by force, are not here.
 *
 * <p>The platform decides what a corner is (a chunk) and who is standing on it; the rules here
 * only judge a claim and say what holding a corner is worth.
 */
public final class Turf {

    /** Holders that are players carry this prefix and their id; a crew is its id alone. */
    public static final String PLAYER_PREFIX = "player:";

    /** Standing with a crew at and above which they let a player take a corner of theirs. */
    public static final double CLAIM_STANDING = 40;

    /** What taking a crew's corner costs with them, even when they allowed it. */
    public static final double CLAIM_COST = 15;

    /** How much less a crew likes a player, on a corner that player took from them. */
    public static final double RESENTMENT = 20;

    /** What a corner costs, in pence: a sovereign, stamped. */
    public static final long CLAIM_PENCE = 240;

    /** What regulars pay on a player's own corner, over the usual. */
    public static final double PRICE_BONUS = 1.15;

    /** How much of a sale on a player's own corner the street notices. */
    public static final double SUSPICION_FACTOR = 0.85;

    public enum Verdict {
        /** Take it. */
        OK,
        /** The player holds it already. */
        YOURS_ALREADY,
        /** Another player holds it; there is no taking it from them here. */
        ANOTHER_PLAYER,
        /** A crew holds it and does not like the player enough to let it go. */
        CREW_REFUSES,
        /** The player holds as many as their tier allows. */
        TOO_MANY,
        /** The player's tier holds no corners at all. */
        TIER_TOO_LOW
    }

    private Turf() {}

    public static boolean isPlayer(String holder) {
        return holder != null && holder.startsWith(PLAYER_PREFIX);
    }

    public static boolean isCrew(String holder) {
        return holder != null && !holder.isEmpty() && !isPlayer(holder);
    }

    /** Corners a tier may hold: none until the Backroom, one there, three at the Workshop, five beyond. */
    public static int maxHeld(Progression.Tier tier) {
        if (tier == null) return 0;
        return switch (tier) {
            case HAND_TO_MOUTH -> 0;
            case BACKROOM -> 1;
            case WORKSHOP -> 3;
            default -> 5;
        };
    }

    /**
     * Judges a claim.
     *
     * @param holder               who holds the corner now: empty, a crew id, or a player id
     * @param you                  the claiming player's id, with the prefix
     * @param standingWithHolder   the player's standing with the holding crew; ignored otherwise
     * @param held                 corners the player holds already
     * @param tier                 the player's tier
     */
    public static Verdict claim(String holder, String you, double standingWithHolder, int held, Progression.Tier tier) {
        if (holder == null) holder = "";
        if (maxHeld(tier) == 0) return Verdict.TIER_TOO_LOW;
        if (holder.equals(you)) return Verdict.YOURS_ALREADY;
        if (held >= maxHeld(tier)) return Verdict.TOO_MANY;
        if (isPlayer(holder)) return Verdict.ANOTHER_PLAYER;
        if (isCrew(holder) && standingWithHolder < CLAIM_STANDING) return Verdict.CREW_REFUSES;
        return Verdict.OK;
    }

    /** The standing a crew behaves on: their real standing, less the resentment on a corner they lost to this player. */
    public static double standingOn(double standing, boolean tookItFromThem) {
        return Standing.clamp(tookItFromThem ? standing - RESENTMENT : standing);
    }

    /** How deep in a crew's turf a member stands: all the way on their own corner, not at all elsewhere. */
    public static double depth(String holder, String crew) {
        return crew != null && !crew.isBlank() && crew.equals(holder) ? 1 : 0;
    }

    public static double priceFactor(boolean ownCorner) { return ownCorner ? PRICE_BONUS : 1; }

    public static double suspicionFactor(boolean ownCorner) { return ownCorner ? SUSPICION_FACTOR : 1; }
}
