package dev.lucas.slumdrugs.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * The pressing bench. Right-click with dried material to load it, empty-handed to pull the
 * screw; the last pull drops the product. Sneak and click to take the batch back out.
 */
public final class PressingBenchBlock extends BaseEntityBlock {

    public PressingBenchBlock(Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PressingBenchBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (ModItems.drugOf("dried_", stack) == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof PressingBenchBlockEntity bench)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        int loaded = bench.load(stack);
        if (loaded == 0) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.press_full"));
            return InteractionResult.SUCCESS;
        }
        stack.consume(loaded, player);
        level.playSound(null, pos, ModSounds.PRESS_LOAD.get(), SoundSource.BLOCKS, 0.8f, 0.9f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PressingBenchBlockEntity bench)) return InteractionResult.PASS;
        if (bench.empty()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown()) {
            popResource(level, pos, bench.unload());
            return InteractionResult.SUCCESS;
        }

        if (!bench.stroke(level)) return InteractionResult.SUCCESS;

        if (bench.done()) {
            for (ItemStack out : bench.press()) popResource(level, pos, out);
            level.playSound(null, pos, ModSounds.PRESS_TURN.get(), SoundSource.BLOCKS, 0.7f, 0.7f);
            return InteractionResult.SUCCESS;
        }

        ProductItem.actionBar(player, Component.translatable("message.slumdrugs.press_stroke",
                bench.strokes(), bench.strokesNeeded()));
        level.playSound(null, pos, ModSounds.PRESS_DONE.get(), SoundSource.BLOCKS, 0.5f, 1.2f);
        return InteractionResult.SUCCESS;
    }
}
