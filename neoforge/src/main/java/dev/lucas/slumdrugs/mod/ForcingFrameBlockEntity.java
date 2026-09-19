package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Cultivation;
import dev.lucas.slumdrugs.sim.station.GrowboxState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Holds a {@link GrowboxState} and does nothing else: the growth rules, the water budget and
 * the stage boundaries all live in the sim module, where they are verified without a game.
 * This class is the adapter — world clock in, block state out, NBT both ways.
 */
public final class ForcingFrameBlockEntity extends BlockEntity {

    /** Seconds of growth for a full crop, before fertiliser. */
    public static final double GROWTH_SECONDS = 600;

    /** Water a freshly planted frame is given, generous enough to finish unfertilised. */
    public static final double PLANTING_WATER_SECONDS = 900;

    /** Units a plant returns before soil, compost and quality are taken into account. */
    public static final int BASE_YIELD = 3;

    private final GrowboxState state = new GrowboxState(0);
    private int seedQuality = 50;
    private int fertiliserCharges;
    private int fertiliserQuality;

    public ForcingFrameBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FORCING_FRAME.get(), pos, blockState);
    }

    public GrowboxState state() { return state; }

    /**
     * World time in milliseconds. Game time is monotonic, survives a restart and advances when
     * players sleep, so a frame catches up the way the rest of the world does.
     */
    public static long clock(Level level) { return level.getGameTime() * 50L; }

    public int fertiliserCharges() { return fertiliserCharges; }

    /** The ground the frame stands in. Prepared soil is the cheapest quality a player can buy. */
    public Cultivation.Soil soil(Level level, BlockPos pos) {
        var below = level.getBlockState(pos.below());
        if (below.is(Blocks.FARMLAND)) return Cultivation.Soil.TILLED;
        if (below.is(Blocks.ROOTED_DIRT)) return Cultivation.Soil.COMPOSTED;
        if (below.is(Blocks.MUD) || below.is(Blocks.PODZOL) || below.is(Blocks.MOSS_BLOCK))
            return Cultivation.Soil.RICH;
        return Cultivation.Soil.BARE;
    }

    /**
     * Light is the only part of the environment modelled so far; warmth and damp are still to
     * come, so an unlit frame is simply a poor one rather than a dead one.
     */
    private double environmentFit() { return state.lamp ? 1.0 : 0.6; }

    public Cultivation.Inputs inputs(Level level, BlockPos pos) {
        return new Cultivation.Inputs(seedQuality, soil(level, pos),
                fertiliserCharges, fertiliserQuality, environmentFit());
    }

    /** Adds a dose of compost. Returns false when the frame has had all it will take. */
    public boolean addFertiliser(int quality) {
        if (state.drug == null || fertiliserCharges >= Cultivation.MAX_CHARGES) return false;
        // Charges of different grades average out, so topping up with poor compost dilutes.
        fertiliserQuality = (fertiliserQuality * fertiliserCharges + quality) / (fertiliserCharges + 1);
        fertiliserCharges++;
        state.fertilizer = fertiliserCharges;
        setChanged();
        return true;
    }

    public void plant(Level level, String drug, String grower, int seedQuality) {
        this.seedQuality = Math.max(0, Math.min(100, seedQuality));
        fertiliserCharges = 0;
        fertiliserQuality = 0;
        state.drug = drug;
        state.grower = grower;
        state.progress = 0;
        state.fertilizer = 0;
        state.waterSeconds = PLANTING_WATER_SECONDS;
        state.updatedAt = clock(level);
        setChanged();
    }

    public boolean ripe() { return state.drug != null && state.progress >= 1; }

    /** What was growing, or null if the frame is empty. */
    public String crop() { return state.drug; }

    /** Resolves the planting and clears the frame. The caller drops what comes back. */
    public Cultivation.Harvest harvest(Level level, BlockPos pos) {
        Cultivation.Harvest harvest = Cultivation.harvest(BASE_YIELD, inputs(level, pos),
                level.getRandom().nextDouble());
        state.clear();
        fertiliserCharges = 0;
        fertiliserQuality = 0;
        setChanged();
        return harvest;
    }

    /** Advances growth and keeps the visible stage in step. Server side, once a second. */
    public void serverTick(Level level, BlockPos pos, BlockState blockState) {
        int before = state.stage();
        state.advance(clock(level), GROWTH_SECONDS);
        setChanged();
        int after = state.stage();
        if (after != before && blockState.getValue(ForcingFrameBlock.STAGE) != after)
            level.setBlock(pos, blockState.setValue(ForcingFrameBlock.STAGE, after), Block_UPDATE_FLAGS);
    }

    /** Send to clients, do not trigger neighbour updates for a cosmetic change. */
    private static final int Block_UPDATE_FLAGS = 2;

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (state.drug != null) output.putString("drug", state.drug);
        output.putString("grower", state.grower);
        output.putDouble("progress", state.progress);
        output.putDouble("water", state.waterSeconds);
        output.putInt("fertilizer", state.fertilizer);
        output.putBoolean("lamp", state.lamp);
        output.putLong("updated", state.updatedAt);
        output.putInt("seed_quality", seedQuality);
        output.putInt("fertiliser_charges", fertiliserCharges);
        output.putInt("fertiliser_quality", fertiliserQuality);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        state.drug = input.getString("drug").orElse(null);
        state.grower = input.getString("grower").orElse("unknown");
        state.progress = input.getDoubleOr("progress", 0);
        state.waterSeconds = input.getDoubleOr("water", 0);
        state.fertilizer = input.getIntOr("fertilizer", 0);
        state.lamp = input.getBooleanOr("lamp", true);
        state.updatedAt = input.getLongOr("updated", 0);
        seedQuality = input.getIntOr("seed_quality", 50);
        fertiliserCharges = input.getIntOr("fertiliser_charges", 0);
        fertiliserQuality = input.getIntOr("fertiliser_quality", 0);
        state.fertilizer = fertiliserCharges;
    }
}
