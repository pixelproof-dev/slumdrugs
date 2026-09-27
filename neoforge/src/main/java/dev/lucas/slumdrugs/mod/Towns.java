package dev.lucas.slumdrugs.mod;

import com.mojang.logging.LogUtils;
import dev.lucas.slumdrugs.sim.world.TownSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Towns appearing in the world on their own, as rare as a stronghold (MOD-GDD.md §1).
 *
 * <p>Where a town may go is {@link TownSites}' arithmetic. Whether one does is decided here, by a
 * single test, {@link #isTownSite}: the ground is a flat biome and no village is close. The
 * burner phone asks the same test before it points anywhere, so the phone and the town cannot
 * disagree — a phone that led somewhere and found open grass would be worse than no phone.
 *
 * <p>A town is built when a player comes within {@link #WAKE_RANGE} of its site, and built in
 * steps: the chunks are loaded a few per tick, then the ground levelled a thousand columns per
 * tick, then the buildings one per tick. Generating a town in one go held the dev server for
 * seconds, which is tolerable for a command and not for somebody walking across a map.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Towns {

    private static final Logger LOG = LogUtils.getLogger();

    /** The biomes a town may stand in. Data-driven, so a server can widen or narrow it. */
    public static final TagKey<Biome> TOWN_BIOMES =
            TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, "town_site"));

    /** Five seconds between looks. The look is a distance sum until a site is actually close. */
    private static final int EVERY_TICKS = 100;

    /**
     * How close a player has to come for a town to be built. Far enough that it is finished before
     * they arrive, near enough that it is built where somebody will see it rather than wherever a
     * flying player happens to pass the edge of a region.
     */
    private static final int WAKE_RANGE = 320;

    /**
     * How far a town keeps from a village. MOD-GDD.md §1 says the two should never share a
     * horizon: a glass tower beside a thatched village is the cost the setting pays, and distance
     * is how it is paid.
     */
    static final int VILLAGE_CLEARANCE = 384;

    /** Half the side of the square a town needs, for sampling the ground under all of it. */
    private static final int SITE_SPAN = 64;

    /** Work per tick while a town is going up: columns levelled, or a quarter of a chunk loaded. */
    private static final int STEP_BUDGET = 1024;

    private record Running(ServerLevel level, int regionX, int regionZ, CityBuilder.Job job) {}

    private static final List<Running> RUNNING = new ArrayList<>();
    private static int ticks;

    private Towns() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Iterator<Running> it = RUNNING.iterator(); it.hasNext(); ) {
            Running r = it.next();
            if (r.job().step(STEP_BUDGET)) {
                records(r.level()).record(r.regionX(), r.regionZ(), TownRecords.Verdict.BUILT);
                it.remove();
            }
        }
        if (++ticks % EVERY_TICKS != 0) return;
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        for (ServerPlayer player : overworld.players())
            wakeNear(overworld, player.getBlockX(), player.getBlockZ());
    }

    /**
     * Builds any town whose site is within range of this column and has not been looked at.
     * Returns how many were started, which the tick ignores and the wake command reports.
     */
    static int wakeNear(ServerLevel level, int x, int z) {
        TownRecords records = records(level);
        int started = 0;
        for (TownSites.Site site : TownSites.nearest(x, z, 1, level.getSeed())) {
            if (site.distanceSquared(x, z) > (long) WAKE_RANGE * WAKE_RANGE) break;
            if (records.verdict(site.regionX(), site.regionZ()) != null) continue;
            if (running(site)) continue;
            if (!isTownSite(level, site.x(), site.z())) {
                records.record(site.regionX(), site.regionZ(), TownRecords.Verdict.REFUSED);
                continue;
            }
            start(level, site, true);
            started++;
        }
        return started;
    }

    /**
     * Starts building the town at a site. Recorded as started before the first block is laid; see
     * {@link TownRecords} for why that is the safe order.
     */
    static CityBuilder.Job start(ServerLevel level, TownSites.Site site, boolean forceChunks) {
        long seed = level.getSeed() ^ TownRecords.key(site.regionX(), site.regionZ());
        // The plan is laid out to find its size, and the corner put so the town is centred on
        // the site. The job lays the same plan out again from the same seed.
        CityBuilder.Job probe = new CityBuilder.Job(level, BlockPos.ZERO, seed, false);
        BlockPos corner = new BlockPos(site.x() - probe.spanX() / 2, level.getSeaLevel(),
                site.z() - probe.spanZ() / 2);
        CityBuilder.Job job = new CityBuilder.Job(level, corner, seed, forceChunks);
        records(level).record(site.regionX(), site.regionZ(), TownRecords.Verdict.STARTED);
        RUNNING.add(new Running(level, site.regionX(), site.regionZ(), job));
        LOG.info("town started at site {}, {} (region {}, {})", site.x(), site.z(),
                site.regionX(), site.regionZ());
        return job;
    }

    private static boolean running(TownSites.Site site) {
        for (Running r : RUNNING)
            if (r.regionX() == site.regionX() && r.regionZ() == site.regionZ()) return true;
        return false;
    }

    /**
     * The one test for whether a town stands at a column. Used by the builder and the phone alike.
     *
     * <p>The biome is read from the noise, which answers for chunks nobody has generated — that
     * is what lets a phone point a kilometre away without loading the kilometre. The village
     * search is the one {@code /locate} uses, and is the expensive half, so it runs second.
     */
    public static boolean isTownSite(ServerLevel level, int x, int z) {
        // The whole footprint, not just its centre: a site whose middle is plains can have a
        // river down one side, and rivers and oceans are biomes of their own, so a grid of
        // samples across the square catches them before a stream runs into the cleared ground.
        int y = QuartPos.fromBlock(level.getSeaLevel());
        for (int dx = -SITE_SPAN; dx <= SITE_SPAN; dx += SITE_SPAN)
            for (int dz = -SITE_SPAN; dz <= SITE_SPAN; dz += SITE_SPAN)
                if (!level.getUncachedNoiseBiome(QuartPos.fromBlock(x + dx), y,
                        QuartPos.fromBlock(z + dz)).is(TOWN_BIOMES)) return false;
        BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE,
                new BlockPos(x, level.getSeaLevel(), z), VILLAGE_CLEARANCE / 16 + 1, false);
        if (village == null) return true;
        long dx = village.getX() - x, dz = village.getZ() - z;
        return dx * dx + dz * dz > (long) VILLAGE_CLEARANCE * VILLAGE_CLEARANCE;
    }

    /**
     * The nearest town to a column: built, being built, or waiting at a site that passes the test.
     *
     * <p>Sites that fail are recorded as refused on the way, so a second call — or a second
     * player's phone — does not repeat the village search for them.
     *
     * @param radius how many regions out to look; each is {@link TownSites#REGION} blocks
     */
    public static Optional<TownSites.Site> nearest(ServerLevel level, int x, int z, int radius) {
        TownRecords records = records(level);
        for (TownSites.Site site : TownSites.nearest(x, z, radius, level.getSeed())) {
            TownRecords.Verdict verdict = records.verdict(site.regionX(), site.regionZ());
            if (verdict == TownRecords.Verdict.REFUSED) continue;
            if (verdict != null || isTownSite(level, site.x(), site.z())) return Optional.of(site);
            records.record(site.regionX(), site.regionZ(), TownRecords.Verdict.REFUSED);
        }
        return Optional.empty();
    }

    static TownRecords records(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TownRecords.TYPE);
    }
}
