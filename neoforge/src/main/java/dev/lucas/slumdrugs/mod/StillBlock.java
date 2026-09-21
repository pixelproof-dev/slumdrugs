package dev.lucas.slumdrugs.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The still. While it runs the firebox glows, the swan neck steams and the wash can be heard. */
public final class StillBlock extends RefineryBlock {

    public StillBlock(Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StillBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends RefineryBlockEntity> type() { return ModBlockEntities.STILL.get(); }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING)) return;
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        // Steam off the top of the neck, where it turns over.
        level.addParticle(ParticleTypes.SMOKE, x + 0.4 + (random.nextDouble() - 0.5) * 0.12, y + 1.4,
                z + 0.5 + (random.nextDouble() - 0.5) * 0.12, 0, 0.02, 0);
        // Flames licking through the grate.
        if (random.nextInt(3) == 0)
            level.addParticle(ParticleTypes.FLAME, x + 0.4 + (random.nextDouble() - 0.5) * 0.5, y + 0.12,
                    z + 0.5 + (random.nextDouble() - 0.5) * 0.5, 0, 0.004, 0);
        if (random.nextInt(9) == 0)
            level.playLocalSound(x + 0.5, y + 0.5, z + 0.5, ModSounds.STILL_BUBBLE.get(), SoundSource.BLOCKS,
                    0.35f, 0.8f + random.nextFloat() * 0.4f, false);
        if (random.nextInt(12) == 0)
            level.playLocalSound(x + 0.5, y + 0.2, z + 0.5, ModSounds.STILL_FIRE.get(), SoundSource.BLOCKS,
                    0.4f + random.nextFloat() * 0.4f, random.nextFloat() * 0.6f + 0.7f, false);
    }
}
