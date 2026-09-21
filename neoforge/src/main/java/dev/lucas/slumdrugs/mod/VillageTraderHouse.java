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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
 * <p><b>Not finished:</b> the design's hard rule is that every block changed is journalled with
 * a restore path. {@link SettlementRecords} records where a building went, not what was there
 * before, so a placement cannot yet be undone. That has to land before this ships.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class VillageTraderHouse {

    private static final Logger LOG = LogUtils.getLogger();

    /** Five seconds. The work only happens once per settlement; this is the cost of looking. */
    private static final int EVERY_TICKS = 100;

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
     * A plot may lean by this much before it is refused. A seventeen-wide house rides a lean of
     * three on its own foundation, but a hall fifty-two by sixty-one will not find three blocks
     * of flat ground anywhere near a village, so the plot is levelled first and the limit is what
     * the levelling may reasonably cut through.
     */
    private static final int MAX_SPREAD = 5;

    /** How deep under the plot to fill when levelling, before giving up on a hole. */
    private static final int FILL_DEPTH = 6;

    /**
     * Which pieces a village gets on its own. The three converted from downloaded builds are
     * 32 by 33 and larger, and measured against real terrain they never find a plot: a village
     * simply has no patch of ground that size and that level beside it. They stay available to
     * /slum structure place, where a person picks the spot.
     */
    private static final java.util.Set<String> BESIDE_A_VILLAGE =
            java.util.Set.of("trader_house", "resident_house");

    private static int ticks;

    private VillageTraderHouse() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % EVERY_TICKS != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels())
            for (ServerPlayer player : level.players())
                considerAround(level, player.blockPosition());
    }

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
            if (!BESIDE_A_VILLAGE.contains(name)) continue;
            if (records.has(village, name)) continue;

            Vec3i size = StructurePlacer.sizeOf(level, entry.getValue());
            if (size == null) {
                said.add("no structure file for " + name);
                continue;
            }

            // Survey afresh for each building: what is unusable grows as each one lands.
            List<SettlementRecords.Placed> taken = records.of(village);
            Map<Long, Integer> surveyed = new HashMap<>();
            PlotSearch.Ground ground = (x, z) -> surveyed.computeIfAbsent(
                    (long) x << 32 | (z & 0xFFFFFFFFL), key -> survey(level, box, taken, x, z));

            int reach = Math.max(size.getX(), size.getZ()) / 2;
            List<PlotSearch.Spot> candidates = new ArrayList<>();
            for (int outset : RINGS)
                candidates.addAll(PlotSearch.ring(box.minX(), box.minZ(), box.maxX(), box.maxZ(),
                        outset + reach, RING_STEP));

            Optional<PlotSearch.Plot> found = PlotSearch.flattestAmong(
                    ground, candidates, size.getX(), size.getZ(), MAX_SPREAD, aimX, aimZ);
            if (found.isEmpty()) continue;

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
                for (int y = plot.firstFreeY(); y <= plot.firstFreeY() + MAX_SPREAD; y++) {
                    pos.set(x, y, z);
                    if (!world.getBlockState(pos).isAir())
                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                for (int y = ground; y > ground - FILL_DEPTH; y--) {
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
