package dev.lucas.slumdrugs.mod;

import com.mojang.logging.LogUtils;
import dev.lucas.slumdrugs.sim.world.PlotSearch;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * <p><b>Not finished:</b> the design's hard rule is that every block changed is journalled with
 * a restore path. {@link TraderHouseRecords} records where a house went, not what was there
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
     * A plot may lean by this much before it is refused. The piece carries nine layers of its own
     * earth under the ground floor, so a few blocks of lean become a slightly taller plinth on the
     * low side rather than a house with terrain through its floor.
     */
    private static final int MAX_SPREAD = 3;

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
        TraderHouseRecords records = level.getDataStorage().computeIfAbsent(TraderHouseRecords.TYPE);
        if (records.has(village)) return "That settlement already has its trader house";

        BoundingBox box = start.getBoundingBox();
        int aimX = (box.minX() + box.maxX()) / 2;
        int aimZ = (box.minZ() + box.maxZ()) / 2;

        // One terrain lookup per column, however many candidate plots share it.
        Map<Long, Integer> surveyed = new HashMap<>();
        PlotSearch.Ground ground = (x, z) -> surveyed.computeIfAbsent(
                (long) x << 32 | (z & 0xFFFFFFFFL), key -> survey(level, box, x, z));

        List<PlotSearch.Spot> candidates = new ArrayList<>();
        for (int outset : RINGS)
            candidates.addAll(PlotSearch.ring(box.minX(), box.minZ(), box.maxX(), box.maxZ(), outset, RING_STEP));

        Optional<PlotSearch.Plot> found = PlotSearch.flattestAmong(
                ground, candidates, 17, 17, MAX_SPREAD, aimX, aimZ);
        // Nothing suitable is loaded yet, or the ground around is all too steep. Saying nothing
        // and looking again in five seconds is right for the tick hook and useless for a command,
        // so the reason goes back either way.
        if (found.isEmpty())
            return "No plot flat enough within " + RINGS[RINGS.length - 1] + " blocks of that settlement";

        PlotSearch.Plot plot = found.get();
        StructurePlacer.Result result = StructurePlacer.placeAt(
                level, plot.centreX(), plot.centreZ(), plot.firstFreeY(),
                StructurePlacer.TRADER_HOUSE, facing(plot.centreX(), plot.centreZ(), aimX, aimZ));

        if (result instanceof StructurePlacer.Result.Placed placed) {
            records.remember(village, placed.origin());
            LOG.info("Trader house for the settlement at {} placed at {}, ground level {}, plot leans {}",
                    start.getChunkPos(), placed.origin().toShortString(), plot.firstFreeY(), plot.spread());
            return "Placed the trader house at " + placed.origin().toShortString()
                    + " (plot leans " + plot.spread() + ")";
        }
        if (result instanceof StructurePlacer.Result.Missing missing) {
            LOG.error("No structure file for {} — no settlement will get a trader house", missing.id());
            return "No structure file for " + missing.id();
        }
        return ((StructurePlacer.Result.Refused) result).reason();
    }

    /** The terrain as the search wants it, with everything it may not build on ruled out. */
    private static int survey(ServerLevel level, BoundingBox village, int x, int z) {
        if (PlotSearch.within(x, z, village.minX(), village.minZ(), village.maxX(), village.maxZ(),
                VILLAGE_MARGIN))
            return PlotSearch.UNUSABLE;
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
