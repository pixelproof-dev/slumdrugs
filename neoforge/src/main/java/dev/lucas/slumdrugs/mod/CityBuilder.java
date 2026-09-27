package dev.lucas.slumdrugs.mod;

import com.mojang.logging.LogUtils;
import dev.lucas.slumdrugs.sim.world.CityPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
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
 *
 * <p>The work is a {@link Job} taken in steps. A town levelled in one go holds the server for
 * seconds, which a command can get away with and world generation cannot: it happens while
 * somebody is walking, on whatever machine hosts the server. {@link Towns} runs a job a few
 * chunks per tick; {@code /slum structure town} runs one to the end at once.
 */
public final class CityBuilder {

    private static final Logger LOG = LogUtils.getLogger();

    /** Blocks between rows and columns: two lanes and a pavement either side. */
    private static final int STREET_WIDTH = 7;

    /**
     * How far above the ground line the levelling may cut, and how far below it fills.
     *
     * <p>The cut goes to the top of the terrain in each column, not a fixed height: the ground
     * line is the median of the square, so a hill in one corner can stand well above it, and a
     * fixed twelve-block cut left its top sitting on the road -- invisible in the log, obvious
     * the moment a generated town was drawn from above. The cap is only there so a town that
     * clips a mountain does not dig a canyon into it.
     */
    private static final int MAX_CUT = 40;
    private static final int FILL_BELOW = 8;

    public record Built(BlockPos origin, int spanX, int spanZ, int buildings, int people) {}

    private CityBuilder() {}

    /** Builds a whole town at once, north-west corner at {@code corner}. For the command. */
    public static Built build(ServerLevel level, BlockPos corner, long seed) {
        Job job = new Job(level, corner, seed, false);
        while (!job.done()) job.step(Integer.MAX_VALUE);
        return job.result();
    }

    /**
     * What goes on the plots a grid would otherwise leave as bare grass. A town has more than one
     * shop, and the corner shop is the one a player needs most often -- it sells the seed.
     */
    private static final String FILLER = "corner_shop";

    /** Lays out a town for the buildings that exist, or null if there are none. */
    static CityPlan.Plan plan(ServerLevel level, long seed, Map<Integer, String> namesOut) {
        List<CityPlan.Size> sizes = new ArrayList<>();
        for (Map.Entry<String, StructurePlacer.Piece> entry : StructurePlacer.PIECES.entrySet()) {
            Vec3i size = StructurePlacer.sizeOf(level, entry.getValue());
            if (size == null) continue;
            namesOut.put(sizes.size(), entry.getKey());
            sizes.add(new CityPlan.Size(size.getX(), size.getZ()));
        }
        if (sizes.isEmpty()) return null;
        // Pad to a full grid with more shops. CityPlan.plots is checked to keep the grid's shape
        // when padded to, so this fills the gaps rather than moving them somewhere else.
        StructurePlacer.Piece filler = StructurePlacer.PIECES.get(FILLER);
        Vec3i fillerSize = filler == null ? null : StructurePlacer.sizeOf(level, filler);
        if (fillerSize != null) {
            for (int n = sizes.size(), full = CityPlan.plots(n); n < full; n++) {
                namesOut.put(sizes.size(), FILLER);
                sizes.add(new CityPlan.Size(fillerSize.getX(), fillerSize.getZ()));
            }
        }
        return CityPlan.of(sizes, STREET_WIDTH, seed);
    }

    /**
     * One town being built, a piece at a time.
     *
     * <p>Phases run in order: load the chunks, measure the ground, cut it flat, lay the road, put
     * up the buildings one by one, let the chunks go. {@link #step} does as much as the budget
     * allows and returns; the budget is in columns while levelling and in chunks while loading,
     * which are the two phases that cost anything.
     */
    public static final class Job {
        private enum Phase { LOAD, MEASURE, FLATTEN, PAVE, BUILD, RELEASE, DONE }

        private final ServerLevel level;
        private final BlockPos corner;
        private final boolean forceChunks;
        private final Map<Integer, String> names = new LinkedHashMap<>();
        private final CityPlan.Plan plan;
        private final List<long[]> chunks = new ArrayList<>();

        private Phase phase = Phase.LOAD;
        private int cursor;
        private int ground;
        private int buildings, people;

        /**
         * @param forceChunks hold the town's chunks loaded for the length of the job. World
         *                    generation needs this, because nobody may be standing close enough to
         *                    keep the far side of the town loaded between ticks; the command runs
         *                    in one call and does not.
         */
        public Job(ServerLevel level, BlockPos corner, long seed, boolean forceChunks) {
            this.level = level;
            this.corner = corner;
            this.forceChunks = forceChunks;
            this.plan = plan(level, seed, names);
            if (plan == null) {
                phase = Phase.DONE;
                return;
            }
            for (int cx = corner.getX() >> 4; cx <= (corner.getX() + plan.spanX() - 1) >> 4; cx++)
                for (int cz = corner.getZ() >> 4; cz <= (corner.getZ() + plan.spanZ() - 1) >> 4; cz++)
                    chunks.add(new long[] {cx, cz});
            if (!forceChunks) phase = Phase.MEASURE;
        }

        public boolean done() {
            return phase == Phase.DONE;
        }

        public Built result() {
            return plan == null ? new Built(corner, 0, 0, 0, 0)
                    : new Built(corner, plan.spanX(), plan.spanZ(), buildings, people);
        }

        public int spanX() {
            return plan == null ? 0 : plan.spanX();
        }

        public int spanZ() {
            return plan == null ? 0 : plan.spanZ();
        }

        /** Does up to {@code budget} units of work. Returns true once the town is finished. */
        public boolean step(int budget) {
            switch (phase) {
                case LOAD -> {
                    // Forcing a chunk loads it on the spot, generating it if it is new, so this is
                    // the expensive part of the whole job and is paced in chunks, not columns.
                    int loads = Math.max(1, budget / 256);
                    for (int n = 0; n < loads && cursor < chunks.size(); n++, cursor++)
                        level.setChunkForced((int) chunks.get(cursor)[0], (int) chunks.get(cursor)[1], true);
                    if (cursor >= chunks.size()) advance(Phase.MEASURE);
                }
                case MEASURE -> {
                    ground = medianGround();
                    advance(Phase.FLATTEN);
                }
                case FLATTEN -> {
                    int columns = plan.spanX() * plan.spanZ();
                    for (int n = 0; n < budget && cursor < columns; n++, cursor++)
                        flattenColumn(cursor % plan.spanX(), cursor / plan.spanX());
                    if (cursor >= columns) advance(Phase.PAVE);
                }
                case PAVE -> {
                    pave();
                    advance(Phase.BUILD);
                }
                case BUILD -> {
                    // One building a step: placing one is a structure placement plus its people,
                    // which is the size of work the rest of the job is paced to.
                    if (cursor < plan.lots().size()) placeLot(plan.lots().get(cursor++));
                    if (cursor >= plan.lots().size()) advance(forceChunks ? Phase.RELEASE : Phase.DONE);
                }
                case RELEASE -> {
                    for (long[] c : chunks) level.setChunkForced((int) c[0], (int) c[1], false);
                    LOG.info("town of {} buildings and {} people at {}, {} by {}, ground {}",
                            buildings, people, corner.toShortString(), plan.spanX(), plan.spanZ(), ground);
                    advance(Phase.DONE);
                }
                case DONE -> { }
            }
            return done();
        }

        private void advance(Phase next) {
            phase = next;
            cursor = 0;
        }

        /**
         * The level to build at: the middle height of the ground under the town.
         *
         * <p>Taken as the median over a coarse grid rather than read at one corner. A corner that
         * happens to sit in a dip or on a hummock would otherwise decide the level for everything,
         * and the whole town would be cut into a hill or stood on a plinth of dirt.
         */
        private int medianGround() {
            List<Integer> heights = new ArrayList<>();
            for (int dx = 0; dx < plan.spanX(); dx += 8)
                for (int dz = 0; dz < plan.spanZ(); dz += 8)
                    heights.add(level.getHeight(Heightmap.Types.WORLD_SURFACE,
                            corner.getX() + dx, corner.getZ() + dz));
            int[] sorted = heights.stream().mapToInt(Integer::intValue).toArray();
            Arrays.sort(sorted);
            return sorted[sorted.length / 2];
        }

        /**
         * Cuts one column to the town's level.
         *
         * <p>The whole square is levelled rather than each plot on its own, because a street has
         * to meet its neighbours: levelling plot by plot leaves steps in the road wherever two of
         * them disagreed about the ground.
         */
        private void flattenColumn(int dx, int dz) {
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            int x = corner.getX() + dx, z = corner.getZ() + dz;
            BlockState air = Blocks.AIR.defaultBlockState();
            int top = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), ground + MAX_CUT);
            for (int y = ground; y <= top; y++) {
                at.set(x, y, z);
                if (!level.getBlockState(at).isAir()) level.setBlock(at, air, Block.UPDATE_CLIENTS);
            }
            // Water counts as a hole. Leaving it meant a pond under a plot stayed a pond, and a
            // stream along the edge ran straight into the cleared square.
            BlockState fill = Blocks.DIRT.defaultBlockState();
            for (int y = ground - 1; y > ground - 1 - FILL_BELOW; y--) {
                at.set(x, y, z);
                BlockState here = level.getBlockState(at);
                if (!here.isAir() && here.getFluidState().isEmpty()) break;
                level.setBlock(at, fill, Block.UPDATE_CLIENTS);
            }
            // The surface of a plot is turf, not the bare earth the filling leaves. The roadway
            // goes over this afterwards, so streets are not exempted here.
            at.set(x, ground - 1, z);
            level.setBlock(at, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        /**
         * Lays the roadway: tarmac, a kerb along each edge, and a dashed line down the middle.
         *
         * <p>The line is what makes it read as a road rather than a grey floor.
         */
        private void pave() {
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
                        level.setBlock(at, edge ? kerb : middle && dash ? line : tarmac, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        private void placeLot(CityPlan.Lot lot) {
            String name = names.get(lot.piece());
            StructurePlacer.Piece piece = StructurePlacer.PIECES.get(name);
            // placeAt wants the middle of the footprint; the plan speaks in corners.
            int midX = corner.getX() + (lot.area().minX() + lot.area().maxX()) / 2;
            int midZ = corner.getZ() + (lot.area().minZ() + lot.area().maxZ()) / 2;
            StructurePlacer.Result result = StructurePlacer.placeAt(
                    level, midX, midZ, ground, piece, turn(piece.front(), lot.faces()));
            if (result instanceof StructurePlacer.Result.Placed placed) {
                buildings++;
                people += placed.people();
            } else {
                LOG.warn("the town could not place {} on its plot: {}", name, result);
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
