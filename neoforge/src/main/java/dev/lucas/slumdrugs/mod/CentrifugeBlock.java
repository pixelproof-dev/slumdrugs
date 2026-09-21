package dev.lucas.slumdrugs.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The centrifuge. While it runs the drum's band spins, the spigot drips and it whirrs. */
public final class CentrifugeBlock extends RefineryBlock {

    public CentrifugeBlock(Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CentrifugeBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends RefineryBlockEntity> type() { return ModBlockEntities.CENTRIFUGE.get(); }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING)) return;
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        // A drip from the spigot at the front, and a breath of vapour from the hatch on top.
        if (random.nextInt(4) == 0)
            level.addParticle(ParticleTypes.DRIPPING_HONEY, x + 0.5, y + 0.06, z + 0.9, 0, 0, 0);
        if (random.nextInt(6) == 0)
            level.addParticle(ParticleTypes.SMOKE, x + 0.5 + (random.nextDouble() - 0.5) * 0.1, y + 1.1,
                    z + 0.5 + (random.nextDouble() - 0.5) * 0.1, 0, 0.01, 0);
        if (random.nextInt(10) == 0)
            level.playLocalSound(x + 0.5, y + 0.6, z + 0.5, ModSounds.CENTRIFUGE_SPIN.get(), SoundSource.BLOCKS,
                    0.25f, 1.3f + random.nextFloat() * 0.3f, false);
    }
}
