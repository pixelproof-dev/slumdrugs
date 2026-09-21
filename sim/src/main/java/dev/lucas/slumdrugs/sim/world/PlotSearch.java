package dev.lucas.slumdrugs.sim.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Picking the ground a building goes on.
 *
 * <p>A settlement placer has to answer one question before it may touch anything: which square
 * of ground is flat enough to build on and free of everything already there. That question is
 * arithmetic over a grid of heights, so it lives here and is checked without a game.
 *
 * <p>The search is deterministic. Two runs over the same terrain pick the same plot, which is
 * what lets a world be regenerated from its seed and lets a placement be reasoned about after
 * the fact. Ties are broken by distance from the point the caller aimed at, then by coordinate.
 */
public final class PlotSearch {

    /** The height of a column that may not be built on — inside a village, under water, a cliff. */
    public static final int UNUSABLE = Integer.MIN_VALUE;

    /** The terrain, as first-free levels: one above the topmost solid block, what a heightmap gives. */
    @FunctionalInterface
    public interface Ground {
        int firstFreeAt(int x, int z);
    }

    /**
     * @param firstFreeY the level to build from: the highest column under the plot, so the
     *                   building rests on the ground rather than having terrain through its floor
     * @param spread     blocks between the lowest and highest column under the plot
     */
    public record Plot(int centreX, int centreZ, int firstFreeY, int spread) {}

    /** A candidate centre. */
    public record Spot(int x, int z) {}

    private PlotSearch() {}

    /**
     * The flattest plot among candidate centres the caller chose.
     *
     * <p>This is the form a settlement placer wants. A village is nearly two hundred blocks
     * across, so testing every column around one is millions of terrain lookups on the server
     * thread; a ring of a hundred or so candidates answers the same question for a thousandth
     * of the work, and the caller is the one that knows where it is worth looking.
     */
    public static Optional<Plot> flattestAmong(Ground ground, Iterable<Spot> candidates,
                                               int width, int depth, int maxSpread,
                                               int aimX, int aimZ) {
        if (width < 1 || depth < 1) throw new IllegalArgumentException("A plot has no zero side");
        if (maxSpread < 0) throw new IllegalArgumentException("Spread cannot be negative");

        Plot best = null;
        long bestDistance = Long.MAX_VALUE;

        for (Spot spot : candidates) {
            int minX = spot.x() - width / 2;
            int minZ = spot.z() - depth / 2;
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            boolean usable = true;

            scan:
            for (int x = minX; x < minX + width; x++) {
                for (int z = minZ; z < minZ + depth; z++) {
                    int h = ground.firstFreeAt(x, z);
                    if (h == UNUSABLE) { usable = false; break scan; }
                    if (h < low) low = h;
                    if (h > high) high = h;
                }
            }
            if (!usable) continue;

            int spread = high - low;
            if (spread > maxSpread) continue;

            long dx = spot.x() - aimX, dz = spot.z() - aimZ;
            long distance = dx * dx + dz * dz;
            if (best == null
                    || spread < best.spread()
                    || (spread == best.spread() && distance < bestDistance)) {
                best = new Plot(spot.x(), spot.z(), high, spread);
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The flattest plot whose footprint is entirely usable.
     *
     * @param width,depth the footprint to fit, already rotated
     * @param maxSpread   how uneven the ground may be before the plot is refused
     * @param aimX,aimZ   what to stay near when several plots are equally flat
     */
    public static Optional<Plot> flattest(Ground ground,
                                          int fromX, int fromZ, int toX, int toZ,
                                          int width, int depth, int maxSpread,
                                          int aimX, int aimZ) {
        List<Spot> candidates = new ArrayList<>();
        for (int cx = fromX; cx <= toX; cx++)
            for (int cz = fromZ; cz <= toZ; cz++)
                candidates.add(new Spot(cx, cz));
        return flattestAmong(ground, candidates, width, depth, maxSpread, aimX, aimZ);
    }

    /**
     * Candidate centres on a rectangular ring {@code outset} blocks outside a box, every
     * {@code step} blocks. Deterministic in order, so a tie between two equally flat plots
     * resolves the same way on every run.
     */
    public static List<Spot> ring(int minX, int minZ, int maxX, int maxZ, int outset, int step) {
        if (step < 1) throw new IllegalArgumentException("A ring needs a positive step");
        int x0 = minX - outset, x1 = maxX + outset;
        int z0 = minZ - outset, z1 = maxZ + outset;
        List<Spot> spots = new ArrayList<>();
        for (int x = x0; x <= x1; x += step) { spots.add(new Spot(x, z0)); spots.add(new Spot(x, z1)); }
        for (int z = z0 + step; z < z1; z += step) { spots.add(new Spot(x0, z)); spots.add(new Spot(x1, z)); }
        return spots;
    }

    /**
     * Whether a column lies inside {@code box}, grown by {@code margin} — the test that keeps a
     * placement off a settlement's own buildings instead of on top of them.
     */
    public static boolean within(int x, int z, int minX, int minZ, int maxX, int maxZ, int margin) {
        return x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin;
    }
}
