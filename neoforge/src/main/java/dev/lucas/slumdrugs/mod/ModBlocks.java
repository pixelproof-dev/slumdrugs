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

    /**
     * Wood-framed working furniture: quick to break, quiet, nothing exotic.
     *
     * <p>{@code noOcclusion} is not decoration. Every station's model is a set of cuboids with
     * gaps, not a filled cube, and a block that does not say so is treated as one: the game
     * culls the touching faces of its neighbours and you see straight through the floor to the
     * sky. Vanilla does the same on every piece of furniture — brewing stand, cauldron, lectern.
     */
    private static BlockBehaviour.Properties wooden() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0f)
                .sound(SoundType.WOOD)
                .noOcclusion();
    }

    /** The frame's lantern lights the room while a crop is comfortable in it. */
    public static final DeferredBlock<ForcingFrameBlock> FORCING_FRAME =
            BLOCKS.registerBlock("grow_tent", ForcingFrameBlock::new, () -> wooden()
                    .lightLevel(state -> state.getValue(ForcingFrameBlock.LIT) ? 9 : 0));
    public static final DeferredBlock<DryingLoftBlock> DRYING_LOFT =
            BLOCKS.registerBlock("drying_rack", DryingLoftBlock::new, ModBlocks::wooden);
    public static final DeferredBlock<PressingBenchBlock> PRESSING_BENCH =
            BLOCKS.registerBlock("pressing_bench", PressingBenchBlock::new, ModBlocks::wooden);
    public static final DeferredBlock<SealingPressBlock> SEALING_PRESS =
            BLOCKS.registerBlock("vacuum_sealer", SealingPressBlock::new, ModBlocks::wooden);
    public static final DeferredBlock<StorageCrateBlock> STORAGE_CRATE =
            BLOCKS.registerBlock("storage_crate", StorageCrateBlock::new, ModBlocks::wooden);

    /** Brass and iron rather than wood: this one is machinery. */
    public static final DeferredBlock<CentrifugeBlock> CENTRIFUGE =
            BLOCKS.registerBlock("centrifuge", CentrifugeBlock::new, () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f)
                    .sound(SoundType.COPPER)
                    .noOcclusion());

    public static final DeferredBlock<CuttingBenchBlock> CUTTING_BENCH =
            BLOCKS.registerBlock("cutting_bench", CuttingBenchBlock::new, ModBlocks::wooden);

    public static final DeferredBlock<GraftingBenchBlock> GRAFTING_BENCH =
            BLOCKS.registerBlock("cloning_bench", GraftingBenchBlock::new, ModBlocks::wooden);

    /** A desk, not a machine: it holds nothing, so it is a plain block. */
    public static final DeferredBlock<CountingHouseBlock> COUNTING_HOUSE =
            BLOCKS.registerBlock("cash_counter", CountingHouseBlock::new, ModBlocks::wooden);

    /** The still is machinery too: copper over a firebox, which glows while it runs. */
    public static final DeferredBlock<StillBlock> STILL =
            BLOCKS.registerBlock("still", StillBlock::new, () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5f)
                    .sound(SoundType.COPPER)
                    // The only station that builds its properties itself rather than from
                    // wooden(), so it was also the only one still culling its neighbours.
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(RefineryBlock.WORKING) ? 8 : 0));

    /** Steel boxes: power is machinery, and machinery is heavier than furniture. */
    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5f)
                .sound(SoundType.METAL);
    }

    public static final DeferredBlock<GeneratorBlock> GENERATOR =
            BLOCKS.registerBlock("generator", GeneratorBlock::new, () -> metal()
                    .lightLevel(state -> state.getValue(PowerBlock.RUNNING) ? 5 : 0));
    public static final DeferredBlock<BatteryBankBlock> BATTERY_BANK =
            BLOCKS.registerBlock("battery_bank", BatteryBankBlock::new, ModBlocks::metal);
    public static final DeferredBlock<PowerMeterBlock> POWER_METER =
            BLOCKS.registerBlock("power_meter", PowerMeterBlock::new, ModBlocks::metal);

    static {
        ModernNames.alias(BLOCKS, "forcing_frame");
        ModernNames.alias(BLOCKS, "drying_loft");
        ModernNames.alias(BLOCKS, "grafting_bench");
        ModernNames.alias(BLOCKS, "sealing_press");
        ModernNames.alias(BLOCKS, "counting_house");

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
        // Power only makes the chain faster (MOD-GDD.md §5.16), so nothing holds it back.
        ModItems.blockItem("generator", GENERATOR, Progression.Tier.HAND_TO_MOUTH);
        ModItems.blockItem("battery_bank", BATTERY_BANK, Progression.Tier.HAND_TO_MOUTH);
        ModItems.blockItem("power_meter", POWER_METER, Progression.Tier.HAND_TO_MOUTH);
    }

    private ModBlocks() {}
}
