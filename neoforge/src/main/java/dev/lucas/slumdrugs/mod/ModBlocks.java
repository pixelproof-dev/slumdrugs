package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.player.Progression;
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
    public static final DeferredBlock<DryingLoftBlock> DRYING_LOFT =
            BLOCKS.registerBlock("drying_loft", DryingLoftBlock::new, ModBlocks::wooden);
    public static final DeferredBlock<PressingBenchBlock> PRESSING_BENCH =
            BLOCKS.registerBlock("pressing_bench", PressingBenchBlock::new, ModBlocks::wooden);
    public static final DeferredBlock<SealingPressBlock> SEALING_PRESS =
            BLOCKS.registerBlock("sealing_press", SealingPressBlock::new, ModBlocks::wooden);
    public static final DeferredBlock<StorageCrateBlock> STORAGE_CRATE =
            BLOCKS.registerBlock("storage_crate", StorageCrateBlock::new, ModBlocks::wooden);

    /** Brass and iron rather than wood: this one is machinery. */
    public static final DeferredBlock<CentrifugeBlock> CENTRIFUGE =
            BLOCKS.registerBlock("centrifuge", CentrifugeBlock::new, () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f)
                    .sound(SoundType.COPPER));

    static {
        // Block items live in the item registry and in the creative tab, in this order. The
        // tier is the one that lets a player set the station up; see Progression for why the
        // frame and the loft are open from the start.
        ModItems.blockItem("forcing_frame", FORCING_FRAME, Progression.Tier.HAND_TO_MOUTH);
        ModItems.blockItem("drying_loft", DRYING_LOFT, Progression.Tier.HAND_TO_MOUTH);
        ModItems.blockItem("pressing_bench", PRESSING_BENCH, Progression.Tier.WORKSHOP);
        ModItems.blockItem("sealing_press", SEALING_PRESS, Progression.Tier.WORKSHOP);
        ModItems.blockItem("storage_crate", STORAGE_CRATE, Progression.Tier.WORKSHOP);
        ModItems.blockItem("centrifuge", CENTRIFUGE, Progression.Tier.WORKSHOP);
    }

    private ModBlocks() {}
}
