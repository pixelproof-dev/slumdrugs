package dev.lucas.slumdrugs.mod;

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
     * the cellar floor up to and including the ground floor.
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
        Optional<StructureTemplate> found =
                level.getServer().getStructureTemplateManager().get(piece.id());
        if (found.isEmpty()) return new Result.Missing(piece.id());

        StructureTemplate template = found.get();
        Vec3i size = template.getSize(rotation);
        if (size.getY() <= piece.groundOffset())
            return new Result.Refused("The piece is shorter than its own ground offset");

        // The floor block sits one below the first air block, so the origin drops by the offset.
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, where.getX(), where.getZ());
        BlockPos origin = new BlockPos(
                where.getX() - size.getX() / 2,
                surface - piece.groundOffset(),
                where.getZ() - size.getZ() / 2);

        if (origin.getY() < level.getMinY())
            return new Result.Refused("The cellar would reach below the world");

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);

        boolean placed = template.placeInWorld(level, origin, origin, settings,
                level.getRandom(), Block.UPDATE_CLIENTS);
        return placed ? new Result.Placed(origin, size) : new Result.Refused("The game refused the placement");
    }
}
