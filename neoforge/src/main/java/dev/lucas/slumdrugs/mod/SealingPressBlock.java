package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Sealing;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The sealing press. Right-click with product to stack it on the bench, with honeycomb to fill
 * the wax pot, empty-handed to pull the lever and seal one parcel. Sneak and click to clear it.
 */
public final class SealingPressBlock extends BaseEntityBlock {

    public SealingPressBlock(Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SealingPressBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        boolean wax = stack.is(Items.HONEYCOMB);
        if (!wax && ModItems.drugOf("product_", stack) == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof SealingPressBlockEntity press)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        int taken = wax ? press.addWax(stack.getCount()) : press.addStock(stack);
        if (taken == 0) {
            ProductItem.actionBar(player, Component.translatable(
                    wax ? "message.slumdrugs.seal_pot_full" : "message.slumdrugs.seal_bench_full"));
            return InteractionResult.SUCCESS;
        }
        stack.consume(taken, player);
        level.playSound(null, pos, wax ? ModSounds.SEAL_WAX.get() : ModSounds.SEAL_LOAD.get(),
                SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SealingPressBlockEntity press)) return InteractionResult.PASS;
        if (press.empty()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown()) {
            for (ItemStack out : press.unload()) popResource(level, pos, out);
            return InteractionResult.SUCCESS;
        }

        if (!press.enoughStock()) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.seal_short",
                    press.units(), Sealing.UNITS_PER_PARCEL));
            return InteractionResult.SUCCESS;
        }
        if (!press.enoughWax()) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.seal_no_wax"));
            return InteractionResult.SUCCESS;
        }

        ItemStack parcel = press.seal(player.getName().getString());
        if (parcel.isEmpty()) return InteractionResult.SUCCESS;
        popResource(level, pos, parcel);
        level.playSound(null, pos, ModSounds.SEAL_STAMP.get(), SoundSource.BLOCKS, 0.25f, 1.6f);
        return InteractionResult.SUCCESS;
    }
}
