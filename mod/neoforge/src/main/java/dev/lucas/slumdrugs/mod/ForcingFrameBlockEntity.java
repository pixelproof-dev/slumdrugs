package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.station.GrowboxState;
import net.minecraft.core.BlockPos;
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

    public static final int YIELD = 3;

    private final GrowboxState state = new GrowboxState(0);

    public ForcingFrameBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FORCING_FRAME.get(), pos, blockState);
    }

    public GrowboxState state() { return state; }

    /**
     * World time in milliseconds. Game time is monotonic, survives a restart and advances when
     * players sleep, so a frame catches up the way the rest of the world does.
     */
    public static long clock(Level level) { return level.getGameTime() * 50L; }

    public void plant(Level level, String drug, String grower) {
        state.drug = drug;
        state.grower = grower;
        state.progress = 0;
        state.fertilizer = 0;
        state.waterSeconds = PLANTING_WATER_SECONDS;
        state.updatedAt = clock(level);
        setChanged();
    }

    public boolean ripe() { return state.drug != null && state.progress >= 1; }

    /** Clears the crop and reports what was growing, for the caller to drop. */
    public String harvest() {
        String drug = state.drug;
        state.clear();
        setChanged();
        return drug;
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
    }
}
