package dev.lucas.slumdrugs.sim.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Where the streets and the plots of a town go.
 *
 * <p>Copying a city out of a downloaded map was measured and rejected: one 48 by 48 tile of the
 * Newisle map came to 88,127 cells, so its dense core would have been some forty of those,
 * twelve megabytes in the jar, four million blocks to place, and identical in every world. A
 * town laid out from the buildings we have costs a few hundred kilobytes and is different every
 * time.
 *
 * <p>The layout is arithmetic over rectangles, so it lives here and is checked without a game —
 * the block-laying half is in the mod. It is deterministic: the same buildings and the same seed
 * give the same town, which is what lets a world be regenerated from its seed.
 *
 * <p>The grid follows the buildings rather than the other way round. A fixed cell size would
 * have to be as large as the largest piece — the precinct is 43 by 33 — and every cottage would
 * then sit in the middle of a car park. So each column is as wide as the widest building in it,
 * each row as deep as the deepest, and the streets run between.
 */
public final class CityPlan {

    /** A rectangle on the ground, both corners inclusive, in town-local coordinates. */
    public record Rect(int minX, int minZ, int maxX, int maxZ) {
        public int width() {
            return maxX - minX + 1;
        }

        public int depth() {
            return maxZ - minZ + 1;
        }

        public boolean overlaps(Rect other) {
            return minX <= other.maxX && maxX >= other.minX
                    && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    /** Which way a building's front points, and therefore which street it is addressed from. */
    public enum Face { NORTH, EAST, SOUTH, WEST }

    /**
     * @param area    the ground the building occupies, exactly its footprint
     * @param faces   the street it fronts onto
     * @param piece   which building goes here, an index into the list handed to {@link #of}
     */
    public record Lot(Rect area, Face faces, int piece) {}

    /** A footprint. Height does not matter to a layout. */
    public record Size(int width, int depth) {}

    /**
     * @param spanX   the town's extent west to east, from 0 to spanX-1
     * @param spanZ   the town's extent north to south
     * @param streets the roadway, as rectangles that may be laid in any order
     * @param lots    where the buildings go
     *
     *                The two spans are kept apart rather than squared off to the larger. A town
     *                forced square ends in a band of roadway as wide as the difference, which
     *                looks like a runway and was visible the first time one was drawn.
     */
    public record Plan(int spanX, int spanZ, List<Rect> streets, List<Lot> lots) {}

    private CityPlan() {}

    /**
     * Lays out a town for these buildings.
     *
     * @param pieces      the footprints to place, in registration order; the plan refers to them
     *                    by index so the caller keeps hold of what each one actually is
     * @param streetWidth blocks between neighbouring rows and columns, and around the outside
     * @param seed        shuffles which building lands where, so two towns differ
     */
    public static Plan of(List<Size> pieces, int streetWidth, long seed) {
        if (pieces.isEmpty()) throw new IllegalArgumentException("a town needs buildings");
        if (streetWidth < 1) throw new IllegalArgumentException("a street needs width");

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) order.add(i);
        Collections.shuffle(order, new Random(seed));

        int cols = (int) Math.ceil(Math.sqrt(pieces.size()));
        int rows = (pieces.size() + cols - 1) / cols;

        // A column is as wide as its widest building, a row as deep as its deepest.
        int[] colWidth = new int[cols];
        int[] rowDepth = new int[rows];
        for (int n = 0; n < order.size(); n++) {
            Size size = pieces.get(order.get(n));
            colWidth[n % cols] = Math.max(colWidth[n % cols], size.width());
            rowDepth[n / cols] = Math.max(rowDepth[n / cols], size.depth());
        }

        int[] colX = new int[cols];
        int x = streetWidth;
        for (int c = 0; c < cols; c++) {
            colX[c] = x;
            x += colWidth[c] + streetWidth;
        }
        int[] rowZ = new int[rows];
        int z = streetWidth;
        for (int r = 0; r < rows; r++) {
            rowZ[r] = z;
            z += rowDepth[r] + streetWidth;
        }
        int spanX = x, spanZ = z;

        List<Rect> streets = new ArrayList<>();
        // Every gap between columns, full depth, and the same across. Laid as whole strips so
        // the crossings are covered twice rather than left as holes, which is what happens if
        // you lay only the gaps between and forget the junctions.
        for (int c = 0; c <= cols; c++) {
            int from = c == 0 ? 0 : colX[c - 1] + colWidth[c - 1];
            streets.add(new Rect(from, 0, from + streetWidth - 1, spanZ - 1));
        }
        for (int r = 0; r <= rows; r++) {
            int from = r == 0 ? 0 : rowZ[r - 1] + rowDepth[r - 1];
            streets.add(new Rect(0, from, spanX - 1, from + streetWidth - 1));
        }

        List<Lot> lots = new ArrayList<>();
        for (int n = 0; n < order.size(); n++) {
            int piece = order.get(n);
            Size size = pieces.get(piece);
            int c = n % cols, r = n / cols;
            Face faces = frontage(r, rows);
            // Centred across the street, so the slack in a narrow building shows as gap on both
            // sides rather than down one -- but pushed hard against the street it fronts onto.
            // Centring it there too would leave a shallow building set back from the road with
            // its door opening onto nothing, which is what the frontage check caught.
            int lx = colX[c] + (colWidth[c] - size.width()) / 2;
            int lz = faces == Face.SOUTH
                    ? rowZ[r] + rowDepth[r] - size.depth()
                    : rowZ[r];
            lots.add(new Lot(new Rect(lx, lz, lx + size.width() - 1, lz + size.depth() - 1),
                    faces, piece));
        }
        return new Plan(spanX, spanZ, streets, lots);
    }

    /**
     * Which street a building in this row fronts onto.
     *
     * <p>The near half of the town faces south and the far half north, so the two rows on either
     * side of a street look at each other across it. A row of buildings all facing the same way
     * reads as a film set.
     */
    private static Face frontage(int row, int rows) {
        return row < (rows + 1) / 2 ? Face.SOUTH : Face.NORTH;
    }

    /** Whether a column is roadway. Used by the builder to lay tarmac and by the checks. */
    public static boolean isStreet(Plan plan, int x, int z) {
        for (Rect street : plan.streets()) if (street.contains(x, z)) return true;
        return false;
    }
}
