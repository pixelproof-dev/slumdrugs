package dev.lucas.slumdrugs.mod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.lucas.slumdrugs.sim.npc.Npc;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Places a saved building so that its ground floor lands on the terrain, cellar and all, and
 * then brings its people: a jigsaw block in the piece whose target is
 * {@code slumdrugs:npc/<role>} or {@code slumdrugs:npc/<role>/<crew>} becomes a villager of
 * ours standing where it was, and the jigsaw becomes its final state. That works for a piece
 * written by {@code tools/build_structures.py} and for one saved from a structure block with
 * a jigsaw placed by hand, alike.
 *
 * <p>The problem the offset solves: a structure block saves upward from its own position, so a
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

    private static Piece piece(String name, int groundOffset) {
        return new Piece(Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, name), groundOffset);
    }

    /**
     * The trader's house: timber frame over a stone footing, brick chimney, nine blocks from
     * the cellar floor up to and including the ground floor. Hand-built, no markers yet.
     */
    public static final Piece TRADER_HOUSE = piece("trader_house", 9);

    /** A resident's house, generated: footing at 0, the floor layer at 1, a resident inside and a regular at the door. */
    public static final Piece RESIDENT_HOUSE = piece("resident_house", 2);

    /** Every piece by its short name, in the order the command lists them. */
    public static final Map<String, Piece> PIECES = new LinkedHashMap<>();

    static {
        PIECES.put("trader_house", TRADER_HOUSE);
        PIECES.put("resident_house", RESIDENT_HOUSE);
    }

    private StructurePlacer() {}

    /** The result of an attempt, so a caller can say something useful rather than just fail. */
    public sealed interface Result {
        record Placed(BlockPos origin, Vec3i size, int people) implements Result {}
        record Missing(Identifier id) implements Result {}
        record Refused(String reason) implements Result {}
    }

    /**
     * Places {@code piece} centred on {@code where}, sunk so its ground floor meets the surface,
     * and spawns whoever its markers call for.
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

        // An unloaded chunk has no surface to read: the heightmap would answer with the bottom
        // of the world and the piece would be refused for a cellar it does not have.
        if (!level.isLoaded(where)) return new Result.Refused("That chunk is not loaded");

        // The floor block sits one below the first air block, so the origin drops by the offset.
        // WORLD_SURFACE, not the _WG one: that heightmap exists only while a chunk is being
        // generated, and asking a loaded chunk for it logs an error and answers from nothing.
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, where.getX(), where.getZ());
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
        if (!placed) return new Result.Refused("The game refused the placement");

        int people = people(level, template.getBoundingBox(settings, origin));
        return new Result.Placed(origin, size, people);
    }

    /** Turns every marker in the box into the person it names. Returns how many. */
    static int people(ServerLevel level, BoundingBox box) {
        int spawned = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (!(level.getBlockEntity(pos) instanceof JigsawBlockEntity jigsaw)) continue;
            Identifier target = jigsaw.getTarget();
            if (!target.getNamespace().equals(SlumDrugsMod.ID) || !target.getPath().startsWith("npc/")) continue;

            String[] parts = target.getPath().substring("npc/".length()).split("/", 2);
            Npc.Role role;
            try {
                role = Npc.Role.valueOf(parts[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                continue;
            }
            String crew = parts.length > 1 ? parts[1] : "";
            BlockState after = finalState(level, jigsaw.getFinalState());
            BlockPos here = pos.immutable();
            level.setBlock(here, after, Block.UPDATE_CLIENTS);
            if (Npcs.spawn(level, here, role, crew, Npcs.randomName(level)) != null) spawned++;
        }
        return spawned;
    }

    /** The block a marker leaves behind, as the jigsaw's final state names it; air if that does not parse. */
    private static BlockState finalState(ServerLevel level, String state) {
        if (state == null || state.isBlank()) return Blocks.AIR.defaultBlockState();
        try {
            return BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), state, true).blockState();
        } catch (CommandSyntaxException bad) {
            return Blocks.AIR.defaultBlockState();
        }
    }
}
