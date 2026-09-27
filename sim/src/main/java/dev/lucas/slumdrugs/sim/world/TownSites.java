package dev.lucas.slumdrugs.sim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Where a town may stand, before anyone has looked at the ground.
 *
 * <p>The world is cut into square regions and each region gets exactly one candidate site, at a
 * position drawn from the world seed — the same scheme vanilla uses to spread strongholds and
 * monuments, and for the same reason: a town has to be rare and has to be in the same place every
 * time the world is loaded, including in chunks nobody has generated yet. Whether a candidate
 * actually becomes a town depends on the ground (biome, a village nearby), which is the mod's
 * business, not this class's.
 *
 * <p>This is also what lets the burner phone point somewhere without generating chunks: it asks
 * for the candidates nearest the player in order, and the mod tries each against the same test
 * the builder will use. The phone and the town can therefore never disagree about where a town
 * is.
 */
public final class TownSites {

    /** Blocks per region side. One town at most per region, so this is what makes them rare. */
    public static final int REGION = 1024;

    /**
     * Blocks kept clear of a region's edge. A site on the very border of two regions would sit a
     * few blocks from its neighbour's; the margin keeps two towns at least twice this apart.
     */
    public static final int MARGIN = 192;

    /** A candidate: the region it belongs to and the column its town would be centred on. */
    public record Site(int regionX, int regionZ, int x, int z) {
        public long distanceSquared(int fromX, int fromZ) {
            long dx = x - fromX, dz = z - fromZ;
            return dx * dx + dz * dz;
        }
    }

    /** The eight points a phone names, clockwise from north. */
    public enum Bearing { NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST }

    private TownSites() {}

    /**
     * Which way to walk to cover a distance of {@code dx} east and {@code dz} south.
     *
     * <p>Minecraft's north is negative z, which is the trap here: the angle is measured from
     * north towards east, so it is the arc tangent of east over <em>minus</em> south. Getting that
     * sign wrong sends every player to the town's mirror image.
     */
    public static Bearing bearing(long dx, long dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        return Bearing.values()[(int) Math.floorMod(Math.round(degrees / 45.0), 8L)];
    }

    public static int regionOf(int block) {
        return Math.floorDiv(block, REGION);
    }

    /** The one candidate in a region. Deterministic in the seed and the region. */
    public static Site site(int regionX, int regionZ, long seed) {
        // Mixed the way vanilla mixes structure salts: distinct regions must not share a stream,
        // and neighbouring regions must not produce neighbouring offsets.
        long mixed = seed ^ (regionX * 341873128712L) ^ (regionZ * 132897987541L) ^ 0x5EED_70E5L;
        Random random = new Random(mixed);
        int span = REGION - 2 * MARGIN;
        int x = regionX * REGION + MARGIN + random.nextInt(span);
        int z = regionZ * REGION + MARGIN + random.nextInt(span);
        return new Site(regionX, regionZ, x, z);
    }

    /**
     * Every candidate within {@code radius} regions of a column, nearest first.
     *
     * <p>Ties are broken by region coordinates, so the order is total and the same on every run.
     */
    public static List<Site> nearest(int x, int z, int radius, long seed) {
        int rx = regionOf(x), rz = regionOf(z);
        List<Site> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
                out.add(site(rx + dx, rz + dz, seed));
        out.sort(Comparator.<Site>comparingLong(s -> s.distanceSquared(x, z))
                .thenComparingInt(Site::regionX).thenComparingInt(Site::regionZ));
        return out;
    }
}
