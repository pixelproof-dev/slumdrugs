package dev.lucas.slumdrugs.sim.world;

/**
 * Where a saved building goes.
 *
 * <p>Three numbers decide it and none of them needs a game: the piece's own size, how far up
 * from its origin the ground floor sits, and the height of the terrain in the column it is
 * centred on. The fourth thing that decides it is rotation, and that is the one that is easy
 * to get wrong — a template is not written from the lowest corner of where it ends up, but
 * from the corner that rotation maps the template's own origin onto. Getting that wrong puts
 * a rotated building a full width away from where the command says it is.
 *
 * <p>All of it is arithmetic, so all of it is checked in {@code SimChecks} rather than by
 * placing houses and walking around them.
 */
public final class StructureFit {

    /** Quarter turns clockwise. The platform layer maps its own rotation type onto this. */
    public enum Turn {
        NONE, CW_90, CW_180, CCW_90;

        /** True where the turn swaps the X and Z extents of the footprint. */
        public boolean swapsAxes() {
            return this == CW_90 || this == CCW_90;
        }
    }

    /** A piece's dimensions. Unrotated wherever it comes from a template. */
    public record Size(int x, int y, int z) {
        public Size {
            if (x < 1 || y < 1 || z < 1) throw new IllegalArgumentException("A piece has no zero dimension");
        }
    }

    /** The result of fitting a piece to a column, so a caller can say something useful. */
    public sealed interface Fit {

        /**
         * @param cornerX  lowest X of the box the piece will occupy
         * @param cornerY  lowest Y — the piece's origin, which for a piece with a cellar is the
         *                 cellar floor
         * @param cornerZ  lowest Z of the box
         * @param zeroX    X the template is written from; equal to {@code cornerX} only when the
         *                 piece is not turned
         * @param zeroZ    Z the template is written from
         * @param footprint the piece's size after the turn
         */
        record Ok(int cornerX, int cornerY, int cornerZ, int zeroX, int zeroZ, Size footprint) implements Fit {}

        record Refused(String reason) implements Fit {}
    }

    private StructureFit() {}

    /** The piece's size after {@code turn}. */
    public static Size rotated(Size size, Turn turn) {
        return turn.swapsAxes() ? new Size(size.z(), size.y(), size.x()) : size;
    }

    /**
     * Fits {@code size} onto the column at {@code columnX/columnZ}, centred, sunk so its ground
     * floor meets the terrain.
     *
     * @param firstFreeY  the first free Y above the terrain in that column — what a heightmap
     *                    lookup returns, one above the topmost solid block
     * @param groundOffset how far up from the piece's origin its ground surface sits
     */
    public static Fit centredOn(int columnX, int columnZ, int firstFreeY,
                                Size size, int groundOffset, Turn turn,
                                int worldMinY, int worldMaxY) {
        if (groundOffset < 0) return new Fit.Refused("Ground offset cannot be negative");
        if (groundOffset >= size.y()) return new Fit.Refused("The piece is shorter than its own ground offset");

        Size footprint = rotated(size, turn);
        int cornerX = columnX - footprint.x() / 2;
        int cornerZ = columnZ - footprint.z() / 2;
        int cornerY = firstFreeY - groundOffset;

        if (cornerY < worldMinY) return new Fit.Refused("The cellar would reach below the world");
        if (cornerY + size.y() - 1 > worldMaxY) return new Fit.Refused("The roof would reach above the world");

        // Mirrors the game's own corner transform with the pivot left at zero. The offsets are
        // in the piece's *unrotated* extents, which is the part that reads wrong and is right.
        int zeroX = switch (turn) {
            case NONE, CCW_90 -> cornerX;
            case CW_90 -> cornerX + size.z() - 1;
            case CW_180 -> cornerX + size.x() - 1;
        };
        int zeroZ = switch (turn) {
            case NONE, CW_90 -> cornerZ;
            case CCW_90 -> cornerZ + size.x() - 1;
            case CW_180 -> cornerZ + size.z() - 1;
        };
        return new Fit.Ok(cornerX, cornerY, cornerZ, zeroX, zeroZ, footprint);
    }

    /**
     * Where a template block at {@code (x, z)} inside the piece ends up, given the position the
     * template is written from. This is the game's transform, restated so the fit above can be
     * checked against it without a game.
     */
    public static int turnedX(int zeroX, int x, int z, Turn turn) {
        return switch (turn) {
            case NONE -> zeroX + x;
            case CW_90 -> zeroX - z;
            case CW_180 -> zeroX - x;
            case CCW_90 -> zeroX + z;
        };
    }

    /** The Z counterpart of {@link #turnedX}. */
    public static int turnedZ(int zeroZ, int x, int z, Turn turn) {
        return switch (turn) {
            case NONE -> zeroZ + z;
            case CW_90 -> zeroZ + x;
            case CW_180 -> zeroZ - z;
            case CCW_90 -> zeroZ - x;
        };
    }
}
