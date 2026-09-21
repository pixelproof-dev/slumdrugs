package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lucas.slumdrugs.sim.drug.Strain;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item data. The plugin stored this in a persistent-data container on a vanilla material;
 * here it is a real component, which means it survives, syncs and shows up in tooltips.
 */
public final class ModComponents {

    public static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SlumDrugsMod.ID);

    /** 0-100. On a seed it is the line's worth; on goods it is what the batch turned out at. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> QUALITY =
            TYPES.register("quality", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 100))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Who grew it. Batches are traceable, which is what makes a seized parcel evidence. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> GROWER =
            TYPES.register("grower", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** Whose wax seal a parcel carries. Distinct from the grower: the seal names the hand that pressed it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SEAL =
            TYPES.register("seal", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    private static final Codec<Strain> STRAIN_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 100).fieldOf("potency").forGetter(Strain::potency),
            Codec.intRange(0, 100).fieldOf("vigour").forGetter(Strain::vigour),
            Codec.intRange(0, 100).fieldOf("hardiness").forGetter(Strain::hardiness),
            Codec.intRange(0, 100).fieldOf("subtlety").forGetter(Strain::subtlety)
    ).apply(instance, Strain::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, Strain> STRAIN_STREAM = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Strain::potency,
            ByteBufCodecs.VAR_INT, Strain::vigour,
            ByteBufCodecs.VAR_INT, Strain::hardiness,
            ByteBufCodecs.VAR_INT, Strain::subtlety,
            Strain::new);

    /** The line a seed belongs to, carried down the whole chain. Absent means the average line. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Strain>> STRAIN =
            TYPES.register("strain", () -> DataComponentType.<Strain>builder()
                    .persistent(STRAIN_CODEC)
                    .networkSynchronized(STRAIN_STREAM)
                    .build());

    /** Present and true on coin the counting house has stamped. Absent on loose coin. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> STAMPED =
            TYPES.register("stamped", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /** 0-1, how much of a batch is filler. Absent on anything honest. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> CUT =
            TYPES.register("cut", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.floatRange(0, 1))
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    private ModComponents() {}

    /** The line a stack belongs to, or the average line for anything unbred. */
    public static Strain strainOf(net.minecraft.world.item.ItemStack stack) {
        return stack.getOrDefault(STRAIN.get(), Strain.AVERAGE);
    }

    /** Marks a stack with a line; the average line leaves no mark, so plain goods stay plain. */
    public static net.minecraft.world.item.ItemStack withStrain(net.minecraft.world.item.ItemStack stack, Strain strain) {
        if (strain == null || strain.isAverage()) stack.remove(STRAIN.get());
        else stack.set(STRAIN.get(), strain);
        return stack;
    }

    /** Carries the line from one stage of the chain to the next. */
    public static net.minecraft.world.item.ItemStack inherit(net.minecraft.world.item.ItemStack from, net.minecraft.world.item.ItemStack to) {
        Strain strain = from.get(STRAIN.get());
        if (strain != null) to.set(STRAIN.get(), strain);
        return to;
    }

    /** Share of a stack that is filler, zero for honest goods. */
    public static double cutOf(net.minecraft.world.item.ItemStack stack) {
        return stack.getOrDefault(CUT.get(), 0f);
    }

    /** Marks a stack as cut; a cut of zero removes the mark, so honest goods stay honest. */
    public static net.minecraft.world.item.ItemStack withCut(net.minecraft.world.item.ItemStack stack, double cut) {
        if (cut <= 0) stack.remove(CUT.get());
        else stack.set(CUT.get(), (float) Math.min(1, cut));
        return stack;
    }

    /** Quality carried by a stack, or the neutral default for plain vanilla-made items. */
    public static int qualityOf(net.minecraft.world.item.ItemStack stack) {
        return stack.getOrDefault(QUALITY.get(), 50);
    }

    public static net.minecraft.world.item.ItemStack withQuality(
            net.minecraft.world.item.ItemStack stack, int quality, String grower) {
        stack.set(QUALITY.get(), Math.max(0, Math.min(100, quality)));
        if (grower != null) stack.set(GROWER.get(), grower);
        return stack;
    }
}
