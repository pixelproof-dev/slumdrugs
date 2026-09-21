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
 * The grafting bench. Right-click with a seed to pot it, twice for two parents of the same
 * substance, empty-handed to make the cross. Sneak and click to take the parents back.
 */
public final class GraftingBenchBlock extends BaseEntityBlock {

    public GraftingBenchBlock(Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GraftingBenchBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (ModItems.drugOf("seed_", stack) == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof GraftingBenchBlockEntity bench)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (!bench.pot(stack)) {
            ProductItem.actionBar(player, Component.translatable(bench.ready()
                    ? "message.slumdrugs.graft_full" : "message.slumdrugs.graft_mismatch"));
            return InteractionResult.SUCCESS;
        }
        stack.consume(1, player);
        level.playSound(null, pos, ModSounds.GRAFTING_POT.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof GraftingBenchBlockEntity bench)) return InteractionResult.PASS;
        if (bench.empty()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown()) {
            for (ItemStack out : bench.unload()) popResource(level, pos, out);
            return InteractionResult.SUCCESS;
        }
        if (!bench.ready()) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.graft_one_parent"));
            return InteractionResult.SUCCESS;
        }
        ItemStack child = bench.graft(level.getRandom());
        if (!child.isEmpty()) popResource(level, pos, child);
        level.playSound(null, pos, ModSounds.GRAFTING_SNIP.get(), SoundSource.BLOCKS, 0.8f, 1.1f);
        return InteractionResult.SUCCESS;
    }
}
