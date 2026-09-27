package dev.lucas.slumdrugs.mod;

import com.mojang.logging.LogUtils;
import dev.lucas.slumdrugs.sim.world.PlotSearch;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gives every settlement a trader's house.
 *
 * <p>The jigsaw route was tried first and measured: the piece is 17 × 17 × 23, larger in every
 * direction than anything vanilla puts in a village, and taller than the sixteen blocks that
 * qualify for the expansion hack. Across twelve villages it was drawn zero times out of a
 * hundred and sixty-seven, because its box never fits beside a sixteen-wide street tile. So the
 * house is placed from code instead, which is what {@code docs/HANDOFF-WORLDGEN.md} names as
 * the option that also keeps the placement journalled and reversible.
 *
 * <p>It runs when a player is standing in a settlement rather than at generation time. That is
 * cheaper, it survives worlds that already exist, and it means the ground around the village is
 * loaded and can actually be surveyed.
 *
 * <p>Every piece in {@link StructurePlacer#PIECES} gets a plot of its own, each avoiding the
 * settlement and whatever the mod has already put beside it. A piece that finds nowhere flat
 * enough is skipped and tried again next time somebody stands there, which is what a 52 by 61
 * hall will do beside most villages.
 *
 * <p><b>No longer automatic.</b> Since the setting moved to the present day, the buildings are
 * glass and concrete, and MOD-GDD.md §1 keeps them out of sight of any thatched village: towns
 * come from {@link Towns}, rare and far from villages, and this runs only when asked for with
 * {@code /slum structure village}. The tick hook that used to fire whenever a player stood in a
 * village is gone; the plot search, the levelling and the survey stay, because the commands use
 * them and the measurements that sized the lean limit came from them.
 *
 * <p><b>Not finished:</b> the design's hard rule is that every block changed is journalled with
 * a restore path. {@link SettlementRecords} records where a building went, not what was there
 * before, so a placement cannot yet be undone.
 */
public final class VillageTraderHouse {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * How far outside the settlement's own box to look, and how finely. Three rings, because a
     * village on broken ground can have nothing flat enough close by: of six villages measured,
     * five were served by the first two rings and the sixth needed to go further out.
     */
    private static final int[] RINGS = {12, 26, 40};
    private static final int RING_STEP = 6;

    /** Breathing room between the settlement's outermost piece and ours. */
    private static final int VILLAGE_MARGIN = 4;

    /**
     * How far a plot may lean before it is refused, which has to grow with the building.
     *
     * <p>A flat limit does not work: measured over twenty villages, a seventeen-wide house found
     * ground leaning two nearly every time, while a hall fifty-two by sixty-one found nothing
     * under five anywhere — a footprint that wide simply crosses more terrain. So the allowance
     * follows the longer side, and the plot is levelled to meet it.
     *
     * <p>It is the levelling that sets the ceiling. Every block of lean is a block cut off the
     * high side and filled on the low one, so ten is about as far as this can go before a
     * building sits in an obvious quarry: 17 wide gives 5, 33 gives 7, 61 gives 10.
     */
    private static int leanLimit(Vec3i size) {
        return Math.min(10, 3 + Math.max(size.getX(), size.getZ()) / 8);
    }

    /**
     * How deep under the plot to fill when levelling, before giving up on a hole.
     *
     * <p>One deeper than the lean the plot was accepted with: the low corner needs that much
     * earth under it, and the extra block is the difference between a floor and a floor with
     * daylight under its edge.
     */
    private static int fillDepth(Vec3i size) {
        return leanLimit(size) + 1;
    }

    private VillageTraderHouse() {}

    /**
     * Gives the settlement at {@code pos} its house if it has none yet, and says what happened.
     * Public because {@code /slum structure village} runs exactly this: a dedicated server has
     * no player to stand in a village, so the tick hook alone cannot be tested.
     */
    public static List<String> considerAround(ServerLevel level, BlockPos pos) {
        Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<Structure, LongSet> here = level.structureManager().getAllStructuresAt(pos);
        List<String> outcomes = new ArrayList<>();
        for (Map.Entry<Structure, LongSet> entry : here.entrySet()) {
            if (!isSettlement(structures, entry.getKey())) continue;
            level.structureManager().fillStartsForStructure(
                    entry.getKey(), entry.getValue(), start -> outcomes.add(giveHouse(level, start)));
        }
        return outcomes;
    }

    /**
     * Where a piece of this size would stand beside that settlement, if anywhere.
     *
     * <p>Pulled out of the placing so it can be asked without answering: see
     * {@link #surveyAround}.
     */
    private static Optional<PlotSearch.Plot> plotFor(ServerLevel level, BoundingBox box,
                                                     int aimX, int aimZ,
                                                     List<SettlementRecords.Placed> taken,
                                                     Vec3i size) {
        Map<Long, Integer> surveyed = new HashMap<>();
        PlotSearch.Ground ground = (x, z) -> surveyed.computeIfAbsent(
                (long) x << 32 | (z & 0xFFFFFFFFL), key -> survey(level, box, taken, x, z));

        int reach = Math.max(size.getX(), size.getZ()) / 2;
        List<PlotSearch.Spot> candidates = new ArrayList<>();
        for (int outset : RINGS)
            candidates.addAll(PlotSearch.ring(box.minX(), box.minZ(), box.maxX(), box.maxZ(),
                    outset + reach, RING_STEP));

        return PlotSearch.flattestAmong(ground, candidates, size.getX(), size.getZ(),
                leanLimit(size), aimX, aimZ);
    }

    /**
     * Asks where each piece would go beside the settlement at {@code pos}, and changes nothing.
     *
     * <p>Here so that a change to the rings or to the lean limit can be measured rather than
     * guessed at. Placing a building levels the ground it stands on and rules that ground out
     * for the next one, so two settings can only be compared honestly on terrain that neither
     * of them has touched — which means asking without building.
     */
    public static List<String> surveyAround(ServerLevel level, BlockPos pos) {
        Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Structure, LongSet> entry : level.structureManager().getAllStructuresAt(pos).entrySet()) {
            if (!isSettlement(structures, entry.getKey())) continue;
            level.structureManager().fillStartsForStructure(entry.getKey(), entry.getValue(), start -> {
                if (!start.isValid()) return;
                BoundingBox box = start.getBoundingBox();
                int aimX = (box.minX() + box.maxX()) / 2;
                int aimZ = (box.minZ() + box.maxZ()) / 2;
                for (Map.Entry<String, StructurePlacer.Piece> piece : StructurePlacer.PIECES.entrySet()) {
                    Vec3i size = StructurePlacer.sizeOf(level, piece.getValue());
                    if (size == null) {
                        lines.add(piece.getKey() + ": no structure file");
                        continue;
                    }
                    Optional<PlotSearch.Plot> plot =
                            plotFor(level, box, aimX, aimZ, List.of(), size);
                    lines.add(String.format("survey %s %dx%d at %s: %s",
                            piece.getKey(), size.getX(), size.getZ(), start.getChunkPos(),
                            plot.map(p -> "plot leaning " + p.spread()).orElse("none")));
                }
            });
        }
        return lines;
    }

    private static boolean isSettlement(Registry<Structure> structures, Structure structure) {
        for (Holder<Structure> holder : structures.getTagOrEmpty(StructureTags.VILLAGE))
            if (holder.value() == structure) return true;
        return false;
    }

    private static String giveHouse(ServerLevel level, StructureStart start) {
        if (!start.isValid()) return "That settlement never finished generating";
        long village = start.getChunkPos().pack();
        SettlementRecords records = level.getDataStorage().computeIfAbsent(SettlementRecords.TYPE);

        BoundingBox box = start.getBoundingBox();
        int aimX = (box.minX() + box.maxX()) / 2;
        int aimZ = (box.minZ() + box.maxZ()) / 2;
        List<String> said = new ArrayList<>();
        int built = 0;

        for (Map.Entry<String, StructurePlacer.Piece> entry : StructurePlacer.PIECES.entrySet()) {
            String name = entry.getKey();
            if (records.has(village, name)) continue;

            Vec3i size = StructurePlacer.sizeOf(level, entry.getValue());
            if (size == null) {
                said.add("no structure file for " + name);
                continue;
            }

            // Survey afresh for each building: what is unusable grows as each one lands.
            Optional<PlotSearch.Plot> found =
                    plotFor(level, box, aimX, aimZ, records.of(village), size);
            if (found.isEmpty()) {
                // Worth saying out loud: a piece this size beside a village on broken ground is
                // the case that decides whether the rings and the levelling are set wide enough.
                LOG.info("no plot for {} ({}x{}) beside the settlement at {}",
                        name, size.getX(), size.getZ(), start.getChunkPos());
                said.add(name + ": no plot flat enough");
                continue;
            }

            PlotSearch.Plot plot = found.get();
            level(level, plot, size);
            StructurePlacer.Result result = StructurePlacer.placeAt(
                    level, plot.centreX(), plot.centreZ(), plot.firstFreeY(),
                    entry.getValue(), facing(plot.centreX(), plot.centreZ(), aimX, aimZ));

            if (result instanceof StructurePlacer.Result.Placed placed) {
                records.remember(village, name, placed.origin(), placed.size());
                LOG.info("{} for the settlement at {} placed at {}, ground level {}, plot leans {}",
                        name, start.getChunkPos(), placed.origin().toShortString(),
                        plot.firstFreeY(), plot.spread());
                said.add(name + " at " + placed.origin().toShortString());
                built++;
            } else if (result instanceof StructurePlacer.Result.Refused refused) {
                said.add(name + ": " + refused.reason());
            }
        }

        if (!said.isEmpty()) return String.join("; ", said);
        if (records.of(village).size() >= StructurePlacer.PIECES.size())
            return "That settlement already has everything";
        return "No plot flat enough beside that settlement yet";
    }

    /**
     * Cuts the plot flat at the level the piece will rest on, so a building larger than any
     * patch of level ground near a village can still stand on one.
     *
     * <p>Only what the piece is about to cover is touched, and only up to the level the search
     * already agreed on: everything above comes away, everything below is filled with the earth
     * that was already there. Plants and snow go with the cut, which is what makes a plot rather
     * than a scar. A player's own work is never in reach, because the search refuses every column
     * inside the settlement and inside anything the mod has already built.
     */
    private static void level(ServerLevel world, PlotSearch.Plot plot, Vec3i size) {
        int minX = plot.centreX() - size.getX() / 2;
        int minZ = plot.centreZ() - size.getZ() / 2;
        BlockState fill = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int ground = plot.firstFreeY() - 1;          // the last solid level under the building

        for (int x = minX; x < minX + size.getX(); x++) {
            for (int z = minZ; z < minZ + size.getZ(); z++) {
                for (int y = plot.firstFreeY(); y <= plot.firstFreeY() + leanLimit(size); y++) {
                    pos.set(x, y, z);
                    if (!world.getBlockState(pos).isAir())
                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                for (int y = ground; y > ground - fillDepth(size); y--) {
                    pos.set(x, y, z);
                    if (!world.getBlockState(pos).isAir()) break;
                    world.setBlock(pos, fill, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The terrain as the search wants it, with everything it may not build on ruled out. */
    private static int survey(ServerLevel level, BoundingBox village,
                              List<SettlementRecords.Placed> taken, int x, int z) {
        if (PlotSearch.within(x, z, village.minX(), village.minZ(), village.maxX(), village.maxZ(),
                VILLAGE_MARGIN))
            return PlotSearch.UNUSABLE;
        for (SettlementRecords.Placed p : taken)
            if (p.covers(x, z, VILLAGE_MARGIN)) return PlotSearch.UNUSABLE;
        if (!level.hasChunkAt(x, z)) return PlotSearch.UNUSABLE;
        int firstFree = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (!level.getFluidState(new BlockPos(x, firstFree - 1, z)).isEmpty()) return PlotSearch.UNUSABLE;
        return firstFree;
    }

    /** Turn the house so its front — the garden path on the piece's south face — faces the village. */
    private static Rotation facing(int fromX, int fromZ, int toX, int toZ) {
        int dx = toX - fromX, dz = toZ - fromZ;
        Direction wanted = Math.abs(dx) > Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        for (Rotation rotation : Rotation.values())
            if (rotation.rotate(Direction.SOUTH) == wanted) return rotation;
        return Rotation.NONE;
    }
}
