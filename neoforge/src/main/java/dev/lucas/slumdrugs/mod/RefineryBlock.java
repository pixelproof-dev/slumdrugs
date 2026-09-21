package dev.lucas.slumdrugs.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A machine with a screen: right-click to open it, and a {@code working} state the block
 * entity sets while a batch runs, so the block itself shows it from across the room. What
 * "working" looks like is each machine's own: the still's firebox glows and its neck steams,
 * the centrifuge's band spins.
 */
public abstract class RefineryBlock extends BaseEntityBlock {

    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    protected RefineryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WORKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WORKING);
    }

    /** The block entity type this machine ticks. */
    protected abstract BlockEntityType<? extends RefineryBlockEntity> type();

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != type()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof RefineryBlockEntity refinery) refinery.serverTick(lvl);
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof RefineryBlockEntity refinery)
            player.openMenu(refinery);
        return InteractionResult.SUCCESS;
    }
}
