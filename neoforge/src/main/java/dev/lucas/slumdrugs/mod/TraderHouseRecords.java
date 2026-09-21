package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Which settlements already have a trader's house, and where it went.
 *
 * <p>Two jobs. It stops a village being built on twice, which matters because the placer runs
 * whenever a player is standing in one. And it is the first half of the journal the design
 * demands: a placement that is not written down cannot be undone, and this at least records
 * that something was put somewhere.
 *
 * <p>It is only the first half. A restore needs the blocks that were there before, and those
 * are not kept yet — see the note on {@link VillageTraderHouse}.
 */
public final class TraderHouseRecords extends SavedData {

    /** @param village the packed chunk position of the settlement's structure start */
    public record Placed(long village, int x, int y, int z) {
        public BlockPos origin() {
            return new BlockPos(x, y, z);
        }
    }

    private static final Codec<Placed> PLACED_CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    Codec.LONG.fieldOf("village").forGetter(Placed::village),
                    Codec.INT.fieldOf("x").forGetter(Placed::x),
                    Codec.INT.fieldOf("y").forGetter(Placed::y),
                    Codec.INT.fieldOf("z").forGetter(Placed::z)
            ).apply(i, Placed::new));

    public static final Codec<TraderHouseRecords> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    PLACED_CODEC.listOf().optionalFieldOf("houses", List.of())
                            .forGetter(r -> List.copyOf(r.houses))
            ).apply(i, TraderHouseRecords::new));

    public static final SavedDataType<TraderHouseRecords> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, "trader_houses"),
            TraderHouseRecords::new, CODEC);

    private final List<Placed> houses = new ArrayList<>();

    public TraderHouseRecords() {}

    private TraderHouseRecords(List<Placed> loaded) {
        this.houses.addAll(loaded);
    }

    public boolean has(long village) {
        for (Placed placed : houses) if (placed.village() == village) return true;
        return false;
    }

    public void remember(long village, BlockPos origin) {
        houses.add(new Placed(village, origin.getX(), origin.getY(), origin.getZ()));
        setDirty();
    }

    public List<Placed> all() {
        return List.copyOf(houses);
    }
}
