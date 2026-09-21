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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The drying loft. Right-click with raw harvest to hang a bundle, empty-handed to take down
 * whatever is dried; sneak and click to take everything down as it is.
 */
public final class DryingLoftBlock extends BaseEntityBlock {

    /** How many rails are hung, 0-3. Purely visual; the block entity is the truth. */
    public static final IntegerProperty BUNDLES = IntegerProperty.create("bundles", 0, DryingLoftBlockEntity.RAILS);

    public DryingLoftBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BUNDLES, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BUNDLES);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DryingLoftBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        // Anything but raw harvest is not for the loft; let the empty-hand path decide.
        if (ModItems.drugOf("raw_", stack) == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof DryingLoftBlockEntity loft)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        int hung = loft.hang(level, stack);
        if (hung == 0) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.loft_full"));
            return InteractionResult.SUCCESS;
        }
        stack.consume(hung, player);
        level.setBlock(pos, state.setValue(BUNDLES, loft.hung()), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DryingLoftBlockEntity loft)) return InteractionResult.PASS;
        if (loft.hung() == 0) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        boolean everything = player.isShiftKeyDown();
        if (!everything && !loft.anyReady(level)) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.loft_drying",
                    (int) Math.round(loft.leastProgress(level) * 100)));
            return InteractionResult.SUCCESS;
        }

        for (int rail = 0; rail < DryingLoftBlockEntity.RAILS; rail++) {
            if (!everything && !loft.ready(level, rail)) continue;
            ItemStack down = loft.takeDown(level, rail);
            if (!down.isEmpty()) popResource(level, pos, down);
        }
        level.setBlock(pos, state.setValue(BUNDLES, loft.hung()), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.8f, 1.1f);
        return InteractionResult.SUCCESS;
    }
}
