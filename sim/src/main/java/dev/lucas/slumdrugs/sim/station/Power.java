package dev.lucas.slumdrugs.sim.station;

/**
 * Electricity, MOD-GDD.md §5.16: small, honest, and replaceable.
 *
 * <p>The unit is the platform's own (FE), so any other mod's generator, cable or battery can
 * feed these machines, and these generators can feed anything that takes FE. What power buys is
 * speed and nothing else: every station works without it, a powered one works half again as
 * fast.
 *
 * <p>There are no cables. Blocks that touch share power: a generator hands it to whatever it
 * touches, a battery fills what it touches, and stations standing side by side pass it along
 * the row. Everything here counts in FE per second, because the blocks exchange once a second.
 */
public final class Power {

    /** What a block is to the flow. */
    public enum Role {
        /** Makes power and hands it out; never takes any in. Generators and the town meter. */
        SOURCE,
        /** Holds power: takes it from sources and other stores, gives it to stores and stations. */
        STORE,
        /** Uses power. Takes it from anything; gives it only to other stations, to pass it along a row. */
        STATION
    }

    /** How much faster a powered station works. */
    public static final double POWERED_SPEED = 1.5;

    /** FE a station uses in a second of powered work: a grow light, a fan. */
    public static final int STATION_DRAW = 400;

    /** A station holds this much, ten seconds of work, so a short gap in supply does not show. */
    public static final int STATION_BUFFER = 4_000;

    /** FE that can cross between two touching blocks in a second. */
    public static final int CONTACT_RATE = 2_000;

    /** What a generator makes in a second while it has fuel: two stations' worth. */
    public static final int GENERATOR_OUTPUT = 800;
    public static final int GENERATOR_BUFFER = 20_000;
    /** Fuel a generator will hold, in FE: a few cans, not a warehouse. */
    public static final long GENERATOR_TANK = 2_000_000;

    /** FE in a lump of coal or charcoal: what a furnace gets from one, turned into electricity. */
    public static final long FE_PER_COAL = 64_000;
    /** A can of generator fuel: five coals' worth in one slot, the reason to make it. */
    public static final long FE_PER_FUEL_CAN = 5 * FE_PER_COAL;

    public static final int BATTERY_BUFFER = 500_000;

    /** What a town connection supplies in a second: four stations. */
    public static final int METER_OUTPUT = 1_600;
    public static final int METER_BUFFER = 8_000;

    /**
     * FE a penny buys on a prepaid meter. Ten minutes of one grow light — a whole crop — costs
     * twelve dollars, a fraction of what the crop sells for, which is what makes the grid
     * tempting and the attention it brings the price.
     */
    public static final long FE_PER_PENNY = 20_000;

    /**
     * FE drawn through a meter that the utility notices, per point of suspicion. A grow op shows
     * up on the bill: one light is lost in a household's use and the Watch forgets it faster
     * than it builds, a room of six outruns the forgetting and climbs a dozen points an hour.
     */
    public static final long FE_PER_SUSPICION = 200_000;

    private Power() {}

    /** Whether a block in this role may hand power to one in that. */
    public static boolean gives(Role from, Role to) {
        return switch (from) {
            case SOURCE -> true;
            case STORE -> to != Role.SOURCE;
            case STATION -> to == Role.STATION;
        };
    }

    /**
     * FE to move from a to b this second: positive a to b, negative b to a, never more than
     * {@link #CONTACT_RATE}. Two of a kind even out how full they are; a source or a store
     * facing something it may feed fills it as far as it can.
     */
    public static long flow(Role a, long amountA, long capA, Role b, long amountB, long capB) {
        if (capA <= 0 || capB <= 0) return 0;
        boolean aGives = gives(a, b), bGives = gives(b, a);
        if (aGives && bGives) {
            // Same footing: level them. Fill fraction, not amount, or a battery would drain into
            // a grow tent until both held the same number of FE.
            double fraction = (double) (amountA + amountB) / (capA + capB);
            long move = (long) (amountA - fraction * capA);
            return clamp(move, CONTACT_RATE);
        }
        if (aGives) return Math.min(CONTACT_RATE, Math.min(amountA, capB - amountB));
        if (bGives) return -Math.min(CONTACT_RATE, Math.min(amountB, capA - amountA));
        return 0;
    }

    private static long clamp(long v, long limit) {
        return Math.max(-limit, Math.min(limit, v));
    }

    /**
     * A generator's second: what it makes into its buffer and what that burns. Makes nothing
     * into a full buffer, so fuel is not burnt to heat the room.
     *
     * @return {@code {made, fuelLeft}}
     */
    public static long[] generate(long stored, long capacity, long fuel) {
        long made = Math.max(0, Math.min(GENERATOR_OUTPUT, Math.min(capacity - stored, fuel)));
        return new long[]{made, fuel - made};
    }

    /** Fuel a lump adds, or 0 if it is not fuel. */
    public static long fuelValue(String item) {
        return switch (item) {
            case "minecraft:coal", "minecraft:charcoal" -> FE_PER_COAL;
            case "minecraft:coal_block" -> 9 * FE_PER_COAL;
            case "slumdrugs:generator_fuel" -> FE_PER_FUEL_CAN;
            default -> 0;
        };
    }

    /** Lumps of fuel a generator can take now without overfilling its tank. */
    public static int fuelFits(long fuel, long perLump, int offered) {
        if (perLump <= 0) return 0;
        long room = Math.max(0, GENERATOR_TANK - fuel);
        return (int) Math.min(offered, room / perLump);
    }

    /** What one cable network carries in a second, all of it together. */
    public static final int NETWORK_RATE = 20_000;

    /** What a cable holds: enough for another mod's generator to push into. */
    public static final int CABLE_BUFFER = 2_000;

    /**
     * One second of a cable network: every block on it by role, and what each gains or loses.
     *
     * <p>Stations are served first, the emptiest first, from sources and then from stores.
     * Stores are filled from sources with what is left. Stores never feed stores — that would
     * only shuffle charge round the network — stations never give and sources never take, and
     * no more than {@link #NETWORK_RATE} moves in all. A cable is the plain version of touching:
     * the same roles, over a distance.
     *
     * @return the change for each block, summing to zero
     */
    public static long[] route(Role[] roles, long[] amounts, long[] capacities) {
        int n = roles.length;
        long[] delta = new long[n];
        long[] budget = {NETWORK_RATE};
        java.util.List<Integer> sources = new java.util.ArrayList<>(), stores = new java.util.ArrayList<>(),
                stations = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (capacities[i] <= 0) continue;
            switch (roles[i]) {
                case SOURCE -> sources.add(i);
                case STORE -> stores.add(i);
                case STATION -> stations.add(i);
            }
        }
        java.util.Comparator<Integer> emptiest = java.util.Comparator.comparingDouble(i -> (double) amounts[i] / capacities[i]);
        stations.sort(emptiest);
        stores.sort(emptiest);
        for (int s : stations) {
            long need = capacities[s] - amounts[s];
            need -= take(sources, s, need, amounts, delta, budget);
            take(stores, s, need, amounts, delta, budget);
        }
        for (int s : stores) take(sources, s, capacities[s] - amounts[s] - delta[s], amounts, delta, budget);
        return delta;
    }

    /** Moves up to {@code need} into {@code to} from the givers in order. Returns what moved. */
    private static long take(java.util.List<Integer> givers, int to, long need, long[] amounts, long[] delta, long[] budget) {
        long moved = 0;
        for (int g : givers) {
            if (need <= 0 || budget[0] <= 0) break;
            if (g == to) continue;
            long x = Math.min(Math.min(need, budget[0]), amounts[g] + delta[g]);
            if (x <= 0) continue;
            delta[g] -= x;
            delta[to] += x;
            need -= x;
            budget[0] -= x;
            moved += x;
        }
        return moved;
    }

    /** A second of a station's work: whether it runs powered, and what it draws for it. */
    public static boolean powered(long stored, boolean working) {
        return working && stored >= STATION_DRAW;
    }
}
