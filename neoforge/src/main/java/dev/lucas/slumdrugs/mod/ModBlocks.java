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

    /** The frame's lantern lights the room while a crop is comfortable in it. */
    public static final DeferredBlock<ForcingFrameBlock> FORCING_FRAME =
            BLOCKS.registerBlock("forcing_frame", ForcingFrameBlock::new, () -> wooden()
                    .lightLevel(state -> state.getValue(ForcingFrameBlock.LIT) ? 9 : 0));
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

    public static final DeferredBlock<CuttingBenchBlock> CUTTING_BENCH =
            BLOCKS.registerBlock("cutting_bench", CuttingBenchBlock::new, ModBlocks::wooden);

    public static final DeferredBlock<GraftingBenchBlock> GRAFTING_BENCH =
            BLOCKS.registerBlock("grafting_bench", GraftingBenchBlock::new, ModBlocks::wooden);

    /** A desk, not a machine: it holds nothing, so it is a plain block. */
    public static final DeferredBlock<CountingHouseBlock> COUNTING_HOUSE =
            BLOCKS.registerBlock("counting_house", CountingHouseBlock::new, ModBlocks::wooden);

    /** The still is machinery too: copper over a firebox, which glows while it runs. */
    public static final DeferredBlock<StillBlock> STILL =
            BLOCKS.registerBlock("still", StillBlock::new, () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5f)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> state.getValue(RefineryBlock.WORKING) ? 8 : 0));

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
        ModItems.blockItem("still", STILL, Progression.Tier.WORKSHOP);
        // The design puts cutting at the Apothecary; that tier is not reachable yet, so the
        // Workshop has it, and the pressure valve is open from the first press.
        ModItems.blockItem("cutting_bench", CUTTING_BENCH, Progression.Tier.WORKSHOP);
        // The design's counting house is an institution in the settlement. Until settlements
        // exist a player builds the desk, from the backroom on.
        ModItems.blockItem("counting_house", COUNTING_HOUSE, Progression.Tier.BACKROOM);
        // Grafting is Apothecary work in the design; the Workshop has it until that tier opens.
        ModItems.blockItem("grafting_bench", GRAFTING_BENCH, Progression.Tier.WORKSHOP);
    }

    private ModBlocks() {}
}
