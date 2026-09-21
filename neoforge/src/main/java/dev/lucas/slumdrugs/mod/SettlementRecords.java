package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * What the mod has built beside which settlement, and where.
 *
 * <p>Three jobs. It stops a settlement being built on twice, which matters because the placer
 * runs whenever a player stands in one. It gives the plot search the boxes of what is already
 * there, so the second building does not land on the first. And it is the first half of the
 * journal the design demands: a placement that is not written down cannot be undone.
 *
 * <p>It is only the first half. A restore needs the blocks that stood there before, and those
 * are not kept yet — see the note on {@link VillageTraderHouse}.
 */
public final class SettlementRecords extends SavedData {

    /**
     * @param village the packed chunk position of the settlement's structure start
     * @param piece   which building, by its short name
     */
    public record Placed(long village, String piece, int x, int y, int z, int sx, int sy, int sz) {
        public BlockPos origin() {
            return new BlockPos(x, y, z);
        }

        /** Whether a column falls inside this building's footprint, grown by {@code margin}. */
        public boolean covers(int cx, int cz, int margin) {
            return cx >= x - margin && cx <= x + sx - 1 + margin
                    && cz >= z - margin && cz <= z + sz - 1 + margin;
        }
    }

    private static final Codec<Placed> PLACED_CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    Codec.LONG.fieldOf("village").forGetter(Placed::village),
                    Codec.STRING.fieldOf("piece").forGetter(Placed::piece),
                    Codec.INT.fieldOf("x").forGetter(Placed::x),
                    Codec.INT.fieldOf("y").forGetter(Placed::y),
                    Codec.INT.fieldOf("z").forGetter(Placed::z),
                    Codec.INT.fieldOf("sx").forGetter(Placed::sx),
                    Codec.INT.fieldOf("sy").forGetter(Placed::sy),
                    Codec.INT.fieldOf("sz").forGetter(Placed::sz)
            ).apply(i, Placed::new));

    public static final Codec<SettlementRecords> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    PLACED_CODEC.listOf().optionalFieldOf("buildings", List.of())
                            .forGetter(r -> List.copyOf(r.buildings))
            ).apply(i, SettlementRecords::new));

    public static final SavedDataType<SettlementRecords> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, "settlements"),
            SettlementRecords::new, CODEC);

    private final List<Placed> buildings = new ArrayList<>();

    public SettlementRecords() {}

    private SettlementRecords(List<Placed> loaded) {
        this.buildings.addAll(loaded);
    }

    public boolean has(long village, String piece) {
        for (Placed p : buildings)
            if (p.village() == village && p.piece().equals(piece)) return true;
        return false;
    }

    /** Everything already standing beside this settlement, so a new plot can avoid it. */
    public List<Placed> of(long village) {
        List<Placed> here = new ArrayList<>();
        for (Placed p : buildings) if (p.village() == village) here.add(p);
        return here;
    }

    public void remember(long village, String piece, BlockPos origin, Vec3i size) {
        buildings.add(new Placed(village, piece, origin.getX(), origin.getY(), origin.getZ(),
                size.getX(), size.getY(), size.getZ()));
        setDirty();
    }

    public List<Placed> all() {
        return List.copyOf(buildings);
    }
}
