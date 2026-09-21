package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.world.StructureFit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Places a saved building so that its ground floor lands on the terrain, cellar and all.
 *
 * <p>The problem this solves: a structure block saves upward from its own position, so a
 * building with a cellar has its origin on the cellar floor. Dropping that at the surface —
 * which is what heightmap projection in the jigsaw JSON does — leaves the cellar above ground
 * and the house hanging over it. The fix is a single number the {@code .nbt} cannot carry:
 * how far up from the origin the ground floor sits.
 *
 * <p>The arithmetic lives in {@link StructureFit} and is verified without a game. This class
 * is the adapter: it reads the terrain, refuses what it should not build on, and writes.
 */
public final class StructurePlacer {

    /** One building and what a placer needs to know about it. */
    public record Piece(Identifier id, int groundOffset) {
        public Piece {
            if (groundOffset < 0) throw new IllegalArgumentException("Ground offset cannot be negative");
        }
    }

    /**
     * The trader's house: timber frame over a stone footing, brick chimney, nine blocks from
     * the cellar floor up to the first free level above its ground surface.
     */
    public static final Piece TRADER_HOUSE =
            new Piece(Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, "trader_house"), 9);

    private StructurePlacer() {}

    /** The result of an attempt, so a caller can say something useful rather than just fail. */
    public sealed interface Result {
        record Placed(BlockPos origin, Vec3i size) implements Result {}
        record Missing(Identifier id) implements Result {}
        record Refused(String reason) implements Result {}
    }

    /**
     * Places {@code piece} centred on {@code where}, sunk so its ground floor meets the surface.
     *
     * @param where  the column to build in; only its X and Z are used
     * @param rotation which way the front faces
     */
    public static Result place(ServerLevel level, BlockPos where, Piece piece, Rotation rotation) {
        // getHeight answers minY for a chunk that is not loaded, which reads back as a cellar
        // below the world rather than as the real reason. Say the real reason.
        if (!level.hasChunkAt(where.getX(), where.getZ()))
            return new Result.Refused("That column is not loaded — stand nearer, or force-load it");

        // WORLD_SURFACE, not WORLD_SURFACE_WG: the _WG maps are worldgen-only, so on a live
        // chunk 26.3 primes one on demand, logs an error, and then never updates it again.
        int firstFree = level.getHeight(Heightmap.Types.WORLD_SURFACE, where.getX(), where.getZ());
        return placeAt(level, where.getX(), where.getZ(), firstFree, piece, rotation);
    }

    /**
     * Places {@code piece} centred on a column at a level the caller has already decided.
     *
     * <p>A settlement placer surveys a whole plot before it builds and knows the level the piece
     * should rest on — the highest column under the footprint, so nothing of the terrain comes
     * up through the floor. Sampling the middle column again here would throw that away.
     */
    public static Result placeAt(ServerLevel level, int columnX, int columnZ, int firstFree,
                                 Piece piece, Rotation rotation) {
        Optional<StructureTemplate> found =
                level.getServer().getStructureTemplateManager().get(piece.id());
        if (found.isEmpty()) return new Result.Missing(piece.id());

        StructureTemplate template = found.get();
        Vec3i raw = template.getSize();
        BlockPos where = new BlockPos(columnX, firstFree, columnZ);

        StructureFit.Fit fit = StructureFit.centredOn(
                where.getX(), where.getZ(), firstFree,
                new StructureFit.Size(raw.getX(), raw.getY(), raw.getZ()),
                piece.groundOffset(), turn(rotation),
                level.getMinY(), level.getMaxY());

        if (fit instanceof StructureFit.Fit.Refused refused)
            return new Result.Refused(refused.reason());

        StructureFit.Fit.Ok ok = (StructureFit.Fit.Ok) fit;

        // A turned template is written from the corner the turn maps its origin onto, which is
        // not the lowest corner of the result; passing the latter moves the piece a full width.
        BlockPos writeFrom = new BlockPos(ok.zeroX(), ok.cornerY(), ok.zeroZ());

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);

        boolean placed = template.placeInWorld(level, writeFrom, writeFrom, settings,
                level.getRandom(), Block.UPDATE_CLIENTS);
        if (!placed) return new Result.Refused("The game refused the placement");

        return new Result.Placed(
                new BlockPos(ok.cornerX(), ok.cornerY(), ok.cornerZ()),
                new Vec3i(ok.footprint().x(), ok.footprint().y(), ok.footprint().z()));
    }

    private static StructureFit.Turn turn(Rotation rotation) {
        return switch (rotation) {
            case NONE -> StructureFit.Turn.NONE;
            case CLOCKWISE_90 -> StructureFit.Turn.CW_90;
            case CLOCKWISE_180 -> StructureFit.Turn.CW_180;
            case COUNTERCLOCKWISE_90 -> StructureFit.Turn.CCW_90;
        };
    }
}
