package dev.lucas.slumdrugs.mod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SlumDrugsMod.ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForcingFrameBlockEntity>> FORCING_FRAME =
            TYPES.register("grow_tent", () -> new BlockEntityType<>(
                    ForcingFrameBlockEntity::new, ModBlocks.FORCING_FRAME.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE =
            TYPES.register("centrifuge", () -> new BlockEntityType<>(
                    CentrifugeBlockEntity::new, ModBlocks.CENTRIFUGE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DryingLoftBlockEntity>> DRYING_LOFT =
            TYPES.register("drying_rack", () -> new BlockEntityType<>(
                    DryingLoftBlockEntity::new, ModBlocks.DRYING_LOFT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PressingBenchBlockEntity>> PRESSING_BENCH =
            TYPES.register("pressing_bench", () -> new BlockEntityType<>(
                    PressingBenchBlockEntity::new, ModBlocks.PRESSING_BENCH.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SealingPressBlockEntity>> SEALING_PRESS =
            TYPES.register("vacuum_sealer", () -> new BlockEntityType<>(
                    SealingPressBlockEntity::new, ModBlocks.SEALING_PRESS.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageCrateBlockEntity>> STORAGE_CRATE =
            TYPES.register("storage_crate", () -> new BlockEntityType<>(
                    StorageCrateBlockEntity::new, ModBlocks.STORAGE_CRATE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StillBlockEntity>> STILL =
            TYPES.register("still", () -> new BlockEntityType<>(
                    StillBlockEntity::new, ModBlocks.STILL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CuttingBenchBlockEntity>> CUTTING_BENCH =
            TYPES.register("cutting_bench", () -> new BlockEntityType<>(
                    CuttingBenchBlockEntity::new, ModBlocks.CUTTING_BENCH.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GraftingBenchBlockEntity>> GRAFTING_BENCH =
            TYPES.register("cloning_bench", () -> new BlockEntityType<>(
                    GraftingBenchBlockEntity::new, ModBlocks.GRAFTING_BENCH.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorBlock.Entity>> GENERATOR =
            TYPES.register("generator", () -> new BlockEntityType<>(
                    GeneratorBlock.Entity::new, ModBlocks.GENERATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBankBlock.Entity>> BATTERY_BANK =
            TYPES.register("battery_bank", () -> new BlockEntityType<>(
                    BatteryBankBlock.Entity::new, ModBlocks.BATTERY_BANK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerMeterBlock.Entity>> POWER_METER =
            TYPES.register("power_meter", () -> new BlockEntityType<>(
                    PowerMeterBlock.Entity::new, ModBlocks.POWER_METER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlock.Entity>> CABLE =
            TYPES.register("power_cable", () -> new BlockEntityType<>(
                    CableBlock.Entity::new, ModBlocks.CABLE.get()));

    static {
        ModernNames.alias(TYPES, "forcing_frame");
        ModernNames.alias(TYPES, "drying_loft");
        ModernNames.alias(TYPES, "grafting_bench");
        ModernNames.alias(TYPES, "sealing_press");
    }

    private ModBlockEntities() {}
}
