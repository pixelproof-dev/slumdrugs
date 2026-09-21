package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Cultivation;
import dev.lucas.slumdrugs.sim.drug.Strain;
import dev.lucas.slumdrugs.sim.station.GrowboxState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
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
    private Strain strain = Strain.AVERAGE;
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

    /** How far around the frame heat and water count. */
    private static final int CLIMATE_REACH = 2;

    /**
     * Warmth 0-1: the biome's own temperature, plus anything burning nearby. A frame by a
     * furnace in the snow is a warm frame.
     */
    public double warmth(Level level, BlockPos pos) {
        double biome = (level.getBiome(pos).value().getBaseTemperature() + 0.5) / 2.0;
        double heat = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-CLIMATE_REACH, -1, -CLIMATE_REACH),
                pos.offset(CLIMATE_REACH, 1, CLIMATE_REACH))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.LAVA) || s.is(Blocks.FIRE) || s.is(Blocks.MAGMA_BLOCK)) heat += 0.25;
            else if ((s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE) || s.is(Blocks.FURNACE)
                    || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.SMOKER))
                    && s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT)) heat += 0.2;
            else if (s.is(Blocks.LANTERN) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)) heat += 0.05;
            else if (s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE) || s.is(Blocks.SNOW_BLOCK)) heat -= 0.1;
        }
        return Math.max(0, Math.min(1, biome + Math.max(-0.3, Math.min(0.5, heat))));
    }

    /** Damp 0-1: water nearby, rain on the frame, and a biome that rains at all. */
    public double damp(Level level, BlockPos pos) {
        double damp = 0.2;
        if (level.getBiome(pos).value().hasPrecipitation()) damp += 0.1;
        if (level.isRainingAt(pos.above())) damp += 0.3;
        int water = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-CLIMATE_REACH, -1, -CLIMATE_REACH),
                pos.offset(CLIMATE_REACH, 1, CLIMATE_REACH)))
            if (level.getFluidState(p).is(Fluids.WATER)) water++;
        damp += Math.min(0.4, water * 0.1);
        return Math.max(0, Math.min(1, damp));
    }

    /** Light and climate together. An unlit frame in the wrong weather is poor, never dead. */
    public double environmentFit(Level level, BlockPos pos) {
        double light = state.lamp ? 1.0 : 0.6;
        Cultivation.Band band = state.drug == null ? Cultivation.Band.any() : Substances.profile(state.drug).band();
        return light * Cultivation.climateFit(warmth(level, pos), damp(level, pos), band, strain.climateFloor());
    }

    public Cultivation.Inputs inputs(Level level, BlockPos pos) {
        return new Cultivation.Inputs(seedQuality, soil(level, pos),
                fertiliserCharges, fertiliserQuality, environmentFit(level, pos));
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

    public Strain strain() { return strain; }

    public void plant(Level level, String drug, String grower, int seedQuality, Strain strain) {
        this.seedQuality = Math.max(0, Math.min(100, seedQuality));
        this.strain = strain == null ? Strain.AVERAGE : strain;
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

    /** Resolves the planting and clears the frame. The caller drops what comes back. A vigorous line yields more. */
    public Cultivation.Harvest harvest(Level level, BlockPos pos) {
        Cultivation.Harvest grown = Cultivation.harvest(BASE_YIELD, inputs(level, pos),
                level.getRandom().nextDouble());
        Cultivation.Harvest harvest = new Cultivation.Harvest(grown.quality(),
                Math.max(1, (int) Math.round(grown.units() * strain.vigourFactor())), grown.seeds(), grown.seedQuality());
        state.clear();
        fertiliserCharges = 0;
        fertiliserQuality = 0;
        setChanged();
        return harvest;
    }

    /** Advances growth and keeps the visible stage in step. Server side, once a second. */
    public void serverTick(Level level, BlockPos pos, BlockState blockState) {
        int before = state.stage();
        // A vigorous line grows faster: the same progress over fewer seconds.
        state.advance(clock(level), GROWTH_SECONDS / strain.vigourFactor());
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
        output.putInt("potency", strain.potency());
        output.putInt("vigour", strain.vigour());
        output.putInt("hardiness", strain.hardiness());
        output.putInt("subtlety", strain.subtlety());
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
        strain = new Strain(input.getIntOr("potency", 50), input.getIntOr("vigour", 50),
                input.getIntOr("hardiness", 50), input.getIntOr("subtlety", 50));
        fertiliserCharges = input.getIntOr("fertiliser_charges", 0);
        fertiliserQuality = input.getIntOr("fertiliser_quality", 0);
        state.fertilizer = fertiliserCharges;
    }
}
