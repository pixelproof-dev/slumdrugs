package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What became of each town site that has been looked at.
 *
 * <p>Two jobs. It stops a site being tested twice: the test asks the structure system where the
 * nearest village is, which is not free, and the answer never changes. And it stops a town being
 * built twice, which would put a second set of people into the same buildings.
 *
 * <p>A site is recorded as {@link Verdict#STARTED} the moment building begins, not when it ends.
 * If the server stops half way the town stays half built rather than being laid again over itself
 * with its people spawned a second time. A half-built town is visible and can be finished by hand;
 * doubled people are neither.
 */
public final class TownRecords extends SavedData {

    public enum Verdict {
        /** Tested and unsuitable: wrong biome, or a village too close. Never tested again. */
        REFUSED,
        /** Building has begun; see the class note on why this is final. */
        STARTED,
        /** Finished. */
        BUILT
    }

    private record Entry(long region, int verdict) {}

    private static final Codec<Entry> ENTRY = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("region").forGetter(Entry::region),
            Codec.INT.fieldOf("verdict").forGetter(Entry::verdict)
    ).apply(i, Entry::new));

    public static final Codec<TownRecords> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRY.listOf().optionalFieldOf("sites", List.of()).forGetter(TownRecords::entries)
    ).apply(i, TownRecords::new));

    public static final SavedDataType<TownRecords> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, "towns"), TownRecords::new, CODEC);

    private final Map<Long, Verdict> sites = new HashMap<>();

    public TownRecords() {}

    private TownRecords(List<Entry> loaded) {
        for (Entry e : loaded) {
            Verdict[] all = Verdict.values();
            if (e.verdict() >= 0 && e.verdict() < all.length) sites.put(e.region(), all[e.verdict()]);
        }
    }

    private List<Entry> entries() {
        return sites.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue().ordinal())).toList();
    }

    public static long key(int regionX, int regionZ) {
        return (long) regionX << 32 | (regionZ & 0xFFFFFFFFL);
    }

    public Verdict verdict(int regionX, int regionZ) {
        return sites.get(key(regionX, regionZ));
    }

    public void record(int regionX, int regionZ, Verdict verdict) {
        sites.put(key(regionX, regionZ), verdict);
        setDirty();
    }
}
