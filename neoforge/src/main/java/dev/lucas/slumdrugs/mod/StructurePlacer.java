package dev.lucas.slumdrugs.mod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.lucas.slumdrugs.sim.npc.Npc;
import dev.lucas.slumdrugs.sim.world.StructureFit;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * <p>Where the piece actually goes is arithmetic, so it lives in {@link StructureFit} and is
 * verified without a game. This class is the adapter: it reads the terrain, writes, and spawns.
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
     * The buildings of the quarter, all converted from downloaded WorldEdit files with
     * {@code tools/cut_structure.py} and given their people with {@code tools/add_markers.py}.
     *
     * <p>The number is the ground offset: the layer of the piece that sits at ground level,
     * read off the bottom of each file rather than guessed. Three of them were cut with earth
     * underneath and three were not, which is why they differ.
     */
    public static final Piece PRECINCT = piece("precinct", 3);
    public static final Piece CORNER_SHOP = piece("corner_shop", 2);
    public static final Piece WAREHOUSE = piece("warehouse", 2);
    public static final Piece APARTMENT_BLOCK = piece("apartment_block", 1);
    public static final Piece APARTMENT_TWO = piece("apartment_two", 1);
    public static final Piece STRIP_MALL = piece("strip_mall", 1);

    /** Every piece by its short name, in the order the command lists them. */
    public static final Map<String, Piece> PIECES = new LinkedHashMap<>();

    static {
        PIECES.put("corner_shop", CORNER_SHOP);
        PIECES.put("apartment_block", APARTMENT_BLOCK);
        PIECES.put("apartment_two", APARTMENT_TWO);
        PIECES.put("warehouse", WAREHOUSE);
        PIECES.put("strip_mall", STRIP_MALL);
        PIECES.put("precinct", PRECINCT);
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
        // An unloaded chunk has no surface to read: the heightmap would answer with the bottom
        // of the world and the piece would be refused for a cellar it does not have.
        if (!level.isLoaded(where)) return new Result.Refused("That chunk is not loaded");

        // WORLD_SURFACE, not the _WG one: that heightmap exists only while a chunk is being
        // generated, and asking a loaded chunk for it logs an error and answers from nothing.
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

        StructureFit.Fit fit = StructureFit.centredOn(
                columnX, columnZ, firstFree,
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
                .setIgnoreEntities(false)
                // A piece marks "leave the terrain alone here" with structure_void, and nothing
                // in placeInWorld skips it: only JigsawReplacementProcessor does, and that runs
                // on the jigsaw path, not this one. Without this the game writes structure_void
                // into the world as a real block — invisible, but it has replaced whatever was
                // there, so a generated piece leaves holes in the ground around its footing.
                .addProcessor(new BlockIgnoreProcessor(List.of(Blocks.STRUCTURE_VOID)));

        boolean placed = template.placeInWorld(level, writeFrom, writeFrom, settings,
                level.getRandom(), Block.UPDATE_CLIENTS);
        if (!placed) return new Result.Refused("The game refused the placement");

        int people = people(level, template.getBoundingBox(settings, writeFrom));
        return new Result.Placed(
                new BlockPos(ok.cornerX(), ok.cornerY(), ok.cornerZ()),
                new Vec3i(ok.footprint().x(), ok.footprint().y(), ok.footprint().z()),
                people);
    }

    /** A piece's own size, or null when its file is missing. The placer needs it to pick a plot. */
    public static Vec3i sizeOf(ServerLevel level, Piece piece) {
        return level.getServer().getStructureTemplateManager().get(piece.id())
                .map(StructureTemplate::getSize).orElse(null);
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

    private static StructureFit.Turn turn(Rotation rotation) {
        return switch (rotation) {
            case NONE -> StructureFit.Turn.NONE;
            case CLOCKWISE_90 -> StructureFit.Turn.CW_90;
            case CLOCKWISE_180 -> StructureFit.Turn.CW_180;
            case COUNTERCLOCKWISE_90 -> StructureFit.Turn.CCW_90;
        };
    }
}
