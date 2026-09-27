package dev.lucas.slumdrugs.mod;

import com.mojang.logging.LogUtils;
import dev.lucas.slumdrugs.sim.world.CityPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays a town in the world from a {@link CityPlan}.
 *
 * <p>The plan half is arithmetic and lives in sim, where it is checked without a game. This half
 * is everything that needs a level: cutting the ground flat, laying the roadway, and handing
 * each plot to {@link StructurePlacer}, which already knows how to put a building down and turn
 * its markers into people.
 *
 * <p>Why a town is built rather than copied: one 48 by 48 tile of the Newisle city map came to
 * 88,127 cells and 287 KB, so its dense core would have been some forty of those — twelve
 * megabytes shipped in the jar, four million blocks laid on placement, and the same town in
 * every world. This costs the buildings we already have and is laid out differently each time.
 */
public final class CityBuilder {

    private static final Logger LOG = LogUtils.getLogger();

    /** Blocks between rows and columns: two lanes and a pavement either side. */
    private static final int STREET_WIDTH = 7;

    /** How far above the ground line the levelling clears, and how far below it fills. */
    private static final int CLEAR_ABOVE = 12;
    private static final int FILL_BELOW = 8;

    public record Built(BlockPos origin, int spanX, int spanZ, int buildings, int people) {}

    private CityBuilder() {}

    /**
     * Builds a town with its north-west corner at {@code corner}, on the ground there.
     *
     * <p>Everything inside the town's square is levelled to one height. That is a blunt thing to
     * do to a landscape and it is why a town belongs somewhere flat: a street that follows the
     * contours is a different and much larger piece of work, and a town on a hillside with a
     * flat street through it looks worse than either.
     */
    public static Built build(ServerLevel level, BlockPos corner, long seed) {
        List<CityPlan.Size> sizes = new ArrayList<>();
        Map<Integer, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, StructurePlacer.Piece> entry : StructurePlacer.PIECES.entrySet()) {
            Vec3i size = StructurePlacer.sizeOf(level, entry.getValue());
            if (size == null) continue;
            names.put(sizes.size(), entry.getKey());
            sizes.add(new CityPlan.Size(size.getX(), size.getZ()));
        }
        if (sizes.isEmpty()) return new Built(corner, 0, 0, 0, 0);

        CityPlan.Plan plan = CityPlan.of(sizes, STREET_WIDTH, seed);
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                corner.getX(), corner.getZ());

        flatten(level, corner, plan, ground);
        pave(level, corner, plan, ground);

        int buildings = 0, people = 0;
        for (CityPlan.Lot lot : plan.lots()) {
            String name = names.get(lot.piece());
            StructurePlacer.Piece piece = StructurePlacer.PIECES.get(name);
            // placeAt wants the middle of the footprint; the plan speaks in corners.
            int midX = corner.getX() + (lot.area().minX() + lot.area().maxX()) / 2;
            int midZ = corner.getZ() + (lot.area().minZ() + lot.area().maxZ()) / 2;
            StructurePlacer.Result result =
                    StructurePlacer.placeAt(level, midX, midZ, ground, piece, turn(piece.front(), lot.faces()));
            if (result instanceof StructurePlacer.Result.Placed placed) {
                buildings++;
                people += placed.people();
            } else {
                LOG.warn("the town could not place {} on its plot: {}", name, result);
            }
        }
        LOG.info("town of {} buildings and {} people at {}, {} by {}",
                buildings, people, corner.toShortString(), plan.spanX(), plan.spanZ());
        return new Built(corner, plan.spanX(), plan.spanZ(), buildings, people);
    }

    /**
     * Cuts the town's square to one level.
     *
     * <p>Done before anything is laid, and over the whole square rather than per building,
     * because a street has to meet its neighbours: levelling each plot on its own leaves steps
     * in the road where two of them disagreed about the ground.
     */
    private static void flatten(ServerLevel level, BlockPos corner, CityPlan.Plan plan, int ground) {
        BlockState fill = Blocks.DIRT.defaultBlockState();
        BlockState turf = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < plan.spanX(); dx++) {
            for (int dz = 0; dz < plan.spanZ(); dz++) {
                int x = corner.getX() + dx, z = corner.getZ() + dz;
                for (int y = ground; y < ground + CLEAR_ABOVE; y++) {
                    at.set(x, y, z);
                    if (!level.getBlockState(at).isAir()) level.setBlock(at, air, Block.UPDATE_CLIENTS);
                }
                for (int y = ground - 1; y > ground - 1 - FILL_BELOW; y--) {
                    at.set(x, y, z);
                    if (!level.getBlockState(at).isAir()) break;
                    level.setBlock(at, fill, Block.UPDATE_CLIENTS);
                }
                // The surface of a plot is turf, not the bare earth the filling leaves. The
                // roadway is laid over this a moment later, so streets are not exempted here.
                at.set(x, ground - 1, z);
                level.setBlock(at, turf, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Lays the roadway: tarmac, a kerb along each edge, and a dashed line down the middle.
     *
     * <p>The line is what makes it read as a road rather than a grey floor, and it is only drawn
     * on strips wide enough to have a middle — the outer streets are laid over each other at the
     * junctions, so a line drawn on every strip would cross itself at every corner.
     */
    private static void pave(ServerLevel level, BlockPos corner, CityPlan.Plan plan, int ground) {
        // 26.3 folded the sixteen dyed variants of a block into one ColorCollection, so there
        // is no Blocks.GRAY_CONCRETE any more -- it is picked by colour instead.
        BlockState tarmac = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
        BlockState kerb = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
        BlockState line = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int y = ground - 1;

        for (CityPlan.Rect street : plan.streets()) {
            boolean acrossX = street.width() > street.depth();
            for (int x = street.minX(); x <= street.maxX(); x++) {
                for (int z = street.minZ(); z <= street.maxZ(); z++) {
                    at.set(corner.getX() + x, y, corner.getZ() + z);
                    boolean edge = acrossX ? (z == street.minZ() || z == street.maxZ())
                                           : (x == street.minX() || x == street.maxX());
                    boolean middle = acrossX ? z == (street.minZ() + street.maxZ()) / 2
                                             : x == (street.minX() + street.maxX()) / 2;
                    boolean dash = (acrossX ? x : z) % 4 < 2;
                    level.setBlock(at, edge ? kerb : middle && dash ? line : tarmac,
                            Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** How far to turn a building so the front it was drawn with ends up on its street. */
    private static Rotation turn(CityPlan.Face front, CityPlan.Face street) {
        return switch (CityPlan.quarterTurns(front, street)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
