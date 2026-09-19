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

    private ModBlockEntities() {}
}
