package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.station.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A petrol generator: coal, charcoal or a can of generator fuel in, power out to whatever it
 * touches. Loud and smoky on purpose (MOD-GDD.md §5.16): it is the power a player makes for
 * themselves, away from the town's meter and its bill.
 */
public final class GeneratorBlock extends PowerBlock {

    public GeneratorBlock(Properties properties) { super(properties); }

    @Override
    protected BlockEntityType<? extends PowerBlock.Entity> type() { return ModBlockEntities.GENERATOR.get(); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Entity(pos, state); }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        long perLump = Power.fuelValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (perLump <= 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof Entity generator)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int lumps = Power.fuelFits(generator.fuel, perLump, stack.getCount());
        if (lumps == 0) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.generator_full"));
            return InteractionResult.SUCCESS;
        }
        generator.addFuel(lumps * perLump);
        stack.consume(lumps, player);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 1.2f);
        ProductItem.actionBar(player, status(generator));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity generator)) return InteractionResult.PASS;
        if (!level.isClientSide()) ProductItem.actionBar(player, status(generator));
        return InteractionResult.SUCCESS;
    }

    private static Component status(Entity generator) {
        long seconds = generator.fuel / Power.GENERATOR_OUTPUT;
        return Component.translatable("message.slumdrugs.generator_status", fe(generator.energy.amount()),
                fe(generator.energy.capacity()), seconds / 60, seconds % 60);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 1.05, z = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.3, y, z + (random.nextDouble() - 0.5) * 0.3, 0, 0.04, 0);
        if (random.nextInt(4) == 0)
            level.playLocalSound(x, y, z, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.5f, 0.6f, false);
    }

    public static final class Entity extends PowerBlock.Entity {
        long fuel;
        private boolean made;

        public Entity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.GENERATOR.get(), pos, state, Power.Role.SOURCE, Power.GENERATOR_BUFFER);
        }

        void addFuel(long amount) {
            fuel += amount;
            setChanged();
        }

        @Override
        protected void work(ServerLevel level) {
            long[] step = Power.generate(energy.amount(), energy.capacity(), fuel);
            made = step[0] > 0;
            if (!made) return;
            energy.add(step[0]);
            fuel = step[1];
            setChanged();
        }

        @Override
        protected boolean running() { return made; }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putLong("fuel", fuel);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            fuel = Math.max(0, input.getLongOr("fuel", 0));
        }
    }
}
