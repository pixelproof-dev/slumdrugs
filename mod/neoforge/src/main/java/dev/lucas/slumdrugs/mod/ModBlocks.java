package dev.lucas.slumdrugs.mod;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Stations, as real blocks. The plugin had to fake these with barriers and display entities. */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SlumDrugsMod.ID);

    /** Wood-framed working furniture: quick to break, quiet, nothing exotic. */
    private static BlockBehaviour.Properties wooden() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0f)
                .sound(SoundType.WOOD);
    }

    public static final DeferredBlock<ForcingFrameBlock> FORCING_FRAME =
            BLOCKS.registerBlock("forcing_frame", ForcingFrameBlock::new, ModBlocks::wooden);
    public static final DeferredBlock<Block> DRYING_LOFT = BLOCKS.registerSimpleBlock("drying_loft", ModBlocks::wooden);
    public static final DeferredBlock<Block> PRESSING_BENCH = BLOCKS.registerSimpleBlock("pressing_bench", ModBlocks::wooden);
    public static final DeferredBlock<Block> SEALING_PRESS = BLOCKS.registerSimpleBlock("sealing_press", ModBlocks::wooden);
    public static final DeferredBlock<Block> STORAGE_CRATE = BLOCKS.registerSimpleBlock("storage_crate", ModBlocks::wooden);

    static {
        // Block items live in the item registry and in the creative tab, in this order.
        ModItems.blockItem("forcing_frame", FORCING_FRAME);
        ModItems.blockItem("drying_loft", DRYING_LOFT);
        ModItems.blockItem("pressing_bench", PRESSING_BENCH);
        ModItems.blockItem("sealing_press", SEALING_PRESS);
        ModItems.blockItem("storage_crate", STORAGE_CRATE);
    }

    private ModBlocks() {}
}
