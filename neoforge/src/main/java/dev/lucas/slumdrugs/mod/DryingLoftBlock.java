package dev.lucas.slumdrugs.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The drying loft. Right-click with raw harvest to hang a bundle, empty-handed to take down
 * whatever is dried; sneak and click to take everything down as it is. The bundles hang green
 * and turn brown once every one of them is ready, so the loft says "come and get it" itself.
 */
public final class DryingLoftBlock extends BaseEntityBlock {

    /** How many rails are hung, 0-3. Purely visual; the block entity is the truth. */
    public static final IntegerProperty BUNDLES = IntegerProperty.create("bundles", 0, DryingLoftBlockEntity.RAILS);

    /** Whether everything hanging is dried. Visual too. */
    public static final BooleanProperty DRY = BooleanProperty.create("dry");

    public DryingLoftBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BUNDLES, 0).setValue(DRY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BUNDLES, DRY);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DryingLoftBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.DRYING_LOFT.get()) return null;
        return (lvl, pos, st, be) -> {
            // Once a second: drying is measured in minutes, and this only turns the bundles brown.
            if (lvl.getGameTime() % 20 == 0 && be instanceof DryingLoftBlockEntity loft) show(lvl, pos, st, loft);
        };
    }

    /** Keeps the visible bundle count and dryness in step with the rails. */
    static void show(Level level, BlockPos pos, BlockState state, DryingLoftBlockEntity loft) {
        BlockState next = state.setValue(BUNDLES, loft.hung()).setValue(DRY, loft.allReady(level));
        if (next != state) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
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
        show(level, pos, state, loft);
        level.playSound(null, pos, ModSounds.LOFT_HANG.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
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
        show(level, pos, state, loft);
        level.playSound(null, pos, ModSounds.LOFT_TAKE.get(), SoundSource.BLOCKS, 0.8f, 1.1f);
        return InteractionResult.SUCCESS;
    }
}
