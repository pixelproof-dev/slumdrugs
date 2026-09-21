package dev.lucas.slumdrugs.mod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SlumDrugsMod.ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForcingFrameBlockEntity>> FORCING_FRAME =
            TYPES.register("forcing_frame", () -> new BlockEntityType<>(
                    ForcingFrameBlockEntity::new, ModBlocks.FORCING_FRAME.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE =
            TYPES.register("centrifuge", () -> new BlockEntityType<>(
                    CentrifugeBlockEntity::new, ModBlocks.CENTRIFUGE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DryingLoftBlockEntity>> DRYING_LOFT =
            TYPES.register("drying_loft", () -> new BlockEntityType<>(
                    DryingLoftBlockEntity::new, ModBlocks.DRYING_LOFT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PressingBenchBlockEntity>> PRESSING_BENCH =
            TYPES.register("pressing_bench", () -> new BlockEntityType<>(
                    PressingBenchBlockEntity::new, ModBlocks.PRESSING_BENCH.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SealingPressBlockEntity>> SEALING_PRESS =
            TYPES.register("sealing_press", () -> new BlockEntityType<>(
                    SealingPressBlockEntity::new, ModBlocks.SEALING_PRESS.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageCrateBlockEntity>> STORAGE_CRATE =
            TYPES.register("storage_crate", () -> new BlockEntityType<>(
                    StorageCrateBlockEntity::new, ModBlocks.STORAGE_CRATE.get()));

    private ModBlockEntities() {}
}
