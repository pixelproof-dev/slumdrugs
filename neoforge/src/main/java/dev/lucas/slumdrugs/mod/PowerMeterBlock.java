package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.station.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import java.util.UUID;

/**
 * A prepaid meter on the town's grid: cash in, power out to whatever it touches. The town is
 * the power station — a meter works inside a built town and nowhere else ({@link Towns#inTown}).
 *
 * <p>It is cheap and it is on the record. The utility sees what goes through a meter, and a
 * grow room on the grid shows on the bill: every {@link Power#FE_PER_SUSPICION} drawn is a point
 * of suspicion on whoever paid for it (MOD-GDD.md §5.16). A generator in a lock-up is the
 * quiet alternative, at the price of hauling fuel.
 */
public final class PowerMeterBlock extends PowerBlock {

    public PowerMeterBlock(Properties properties) { super(properties); }

    @Override
    protected BlockEntityType<? extends PowerBlock.Entity> type() { return ModBlockEntities.POWER_METER.get(); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Entity(pos, state); }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!Purse.isCoin(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof Entity meter)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || !Towns.inTown(server, pos)) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.meter_no_grid"));
            return InteractionResult.SUCCESS;
        }
        long pence = Purse.valueOf(stack);
        meter.credit += pence * Power.FE_PER_PENNY;
        meter.payer = player.getStringUUID();
        meter.setChanged();
        stack.consume(stack.getCount(), player);
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0f, 1.3f);
        ProductItem.actionBar(player, Component.translatable("message.slumdrugs.meter_topped_up",
                Coin.format(pence), status(meter)));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity meter)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || !Towns.inTown(server, pos))
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.meter_no_grid"));
        else
            ProductItem.actionBar(player, status(meter));
        return InteractionResult.SUCCESS;
    }

    /** Credit, and what it comes to in hours of one grow light. */
    private static Component status(Entity meter) {
        long lightMinutes = meter.credit / Power.STATION_DRAW / 60;
        return Component.translatable("message.slumdrugs.meter_status", fe(meter.credit), lightMinutes / 60, lightMinutes % 60);
    }

    public static final class Entity extends PowerBlock.Entity {
        long credit;
        String payer = "";
        /** FE through the meter since the utility last looked. */
        private long unreported;
        private boolean connected;
        private boolean supplied;

        public Entity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.POWER_METER.get(), pos, state, Power.Role.SOURCE, Power.METER_BUFFER);
        }

        @Override
        protected void work(ServerLevel level) {
            connected = Towns.inTown(level, worldPosition);
            supplied = false;
            if (!connected || credit <= 0) return;
            long draw = Math.min(Power.METER_OUTPUT, Math.min(credit, energy.capacity() - energy.amount()));
            if (draw <= 0) return;
            energy.add(draw);
            credit -= draw;
            unreported += draw;
            supplied = true;
            setChanged();
            if (unreported >= Power.FE_PER_SUSPICION) report(level);
        }

        /** The bill reaches somebody who reads it. */
        private void report(ServerLevel level) {
            int points = (int) (unreported / Power.FE_PER_SUSPICION);
            unreported -= points * Power.FE_PER_SUSPICION;
            ServerPlayer player;
            try {
                player = level.getServer().getPlayerList().getPlayer(UUID.fromString(payer));
            } catch (IllegalArgumentException nobody) {
                return;
            }
            if (player == null) return;
            Watch.noticed(player, points, false, 1.0);
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.meter_noticed"));
        }

        @Override
        protected boolean running() { return connected && credit > 0; }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putLong("credit", credit);
            output.putString("payer", payer);
            output.putLong("unreported", unreported);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            credit = Math.max(0, input.getLongOr("credit", 0));
            payer = input.getStringOr("payer", "");
            unreported = Math.max(0, input.getLongOr("unreported", 0));
        }
    }
}
