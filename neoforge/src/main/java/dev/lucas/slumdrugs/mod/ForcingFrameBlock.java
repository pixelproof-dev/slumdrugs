package dev.lucas.slumdrugs.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A glazed frame you plant one crop in. Right-click with seed to plant, empty-handed to harvest.
 * The visible stage mirrors the simulation, so a player reads progress off the block itself.
 */
public final class ForcingFrameBlock extends BaseEntityBlock {

    /** 0 empty, 1-3 growing, 4 ripe. */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);

    public ForcingFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForcingFrameBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.FORCING_FRAME.get()) return null;
        return (lvl, pos, st, be) -> {
            // Once a second is plenty: growth is measured in minutes, and this runs per frame.
            if (lvl.getGameTime() % 20 == 0 && be instanceof ForcingFrameBlockEntity frame)
                frame.serverTick(lvl, pos, st);
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, net.minecraft.world.InteractionHand hand,
                                          BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ForcingFrameBlockEntity frame)) return InteractionResult.PASS;

        // Compost goes onto a growing crop, and improves what it yields.
        if (stack.is(ModItems.get("fertilizer").get())) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!frame.addFertiliser(ModComponents.qualityOf(stack))) return InteractionResult.PASS;
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }

        if (frame.state().drug != null) return InteractionResult.PASS;

        String drug = seedDrug(stack);
        if (drug == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        frame.plant(level, drug, player.getName().getString(), ModComponents.qualityOf(stack));
        level.setBlock(pos, state.setValue(STAGE, frame.state().stage()), 2);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ForcingFrameBlockEntity frame)) return InteractionResult.PASS;
        if (!frame.ripe()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        String drug = frame.crop();
        String grower = frame.state().grower;
        var harvest = frame.harvest(level, pos);

        popResource(level, pos, ModComponents.withQuality(
                new ItemStack(ModItems.get("raw_" + drug).get(), harvest.units()),
                harvest.quality(), grower));
        popResource(level, pos, ModComponents.withQuality(
                new ItemStack(ModItems.get("seed_" + drug).get(), harvest.seeds()),
                harvest.seedQuality(), grower));

        level.setBlock(pos, state.setValue(STAGE, 0), 2);
        return InteractionResult.SUCCESS;
    }

    /** Which substance a held seed plants, or null if it is not one of ours. */
    private static @Nullable String seedDrug(ItemStack stack) {
        for (String drug : ModItems.CROPS)
            if (stack.is(ModItems.get("seed_" + drug).get())) return drug;
        return null;
    }
}
