package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Strain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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

    /** Whether the lantern inside burns: a crop is in, and the climate suits it. It gives light. */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public ForcingFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, LIT);
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

        // Anything else in hand is not for planting; the empty-hand path handles the harvest.
        if (frame.state().drug != null) return InteractionResult.TRY_WITH_EMPTY_HAND;

        String drug = seedDrug(stack);
        if (drug == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        // A seed of a line that has held for enough generations, renamed at an anvil, names the line.
        Strain line = ModComponents.strainOf(stack);
        var custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null && line.canName()) {
            line = line.withName(custom.getString());
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.line_named", line.name())
                    .withStyle(s -> s.withColor(0x70B090)));
        }
        frame.plant(level, drug, player.getName().getString(), ModComponents.qualityOf(stack), line);
        level.setBlock(pos, state.setValue(STAGE, frame.state().stage()).setValue(LIT, frame.warm(level, pos)), 2);
        level.playSound(null, pos, ModSounds.FRAME_PLANT.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
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
        Strain line = frame.strain();
        var harvest = frame.harvest(level, pos);

        // The harvest carries the line as it was; the seed it returns has drifted a little.
        popResource(level, pos, ModComponents.withStrain(ModComponents.withQuality(
                new ItemStack(ModItems.get("raw_" + drug).get(), harvest.units()),
                harvest.quality(), grower), line));
        var random = level.getRandom();
        double[] rolls = {random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble()};
        popResource(level, pos, ModComponents.withStrain(ModComponents.withQuality(
                new ItemStack(ModItems.get("seed_" + drug).get(), harvest.seeds()),
                harvest.seedQuality(), grower), line.drift(rolls)));

        level.setBlock(pos, state.setValue(STAGE, 0).setValue(LIT, false), 2);
        level.playSound(null, pos, ModSounds.FRAME_HARVEST.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Which substance a held seed plants, or null if it is not one of ours. */
    private static @Nullable String seedDrug(ItemStack stack) {
        for (String drug : ModItems.CROPS)
            if (stack.is(ModItems.get("seed_" + drug).get())) return drug;
        return null;
    }
}
