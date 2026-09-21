package dev.lucas.slumdrugs.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The cutting bench. Right-click with product to put it on the board, with sugar or bone meal
 * to heap up filler, empty-handed to pull the blade. Sneak and click to clear it.
 */
public final class CuttingBenchBlock extends BaseEntityBlock {

    public CuttingBenchBlock(Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CuttingBenchBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        boolean filler = CuttingBenchBlockEntity.isFiller(stack);
        if (!filler && ModItems.drugOf("product_", stack) == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof CuttingBenchBlockEntity bench)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        int taken = filler ? bench.addFiller(stack.getCount()) : bench.load(stack);
        if (taken == 0) {
            ProductItem.actionBar(player, Component.translatable(
                    filler ? "message.slumdrugs.cut_heap_full" : "message.slumdrugs.cut_board_full"));
            return InteractionResult.SUCCESS;
        }
        stack.consume(taken, player);
        level.playSound(null, pos, filler ? SoundEvents.SAND_PLACE : SoundEvents.WOOL_PLACE,
                SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CuttingBenchBlockEntity bench)) return InteractionResult.PASS;
        if (bench.empty()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown()) {
            for (ItemStack out : bench.unload()) popResource(level, pos, out);
            return InteractionResult.SUCCESS;
        }

        if (bench.units() == 0) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.cut_no_goods"));
            return InteractionResult.SUCCESS;
        }
        ItemStack out = bench.cut();
        if (!out.isEmpty()) popResource(level, pos, out);
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7f, 1.3f);
        return InteractionResult.SUCCESS;
    }
}
