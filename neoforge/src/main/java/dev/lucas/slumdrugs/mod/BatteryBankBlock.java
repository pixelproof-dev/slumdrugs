package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.station.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A rack of cells: takes power from a generator or the meter, gives it to the stations it
 * touches. It lets a generator run while nobody is there to feed it and the lights stay on, and
 * it is the block another mod's grid can charge and draw from (MOD-GDD.md §5.16).
 */
public final class BatteryBankBlock extends PowerBlock {

    public BatteryBankBlock(Properties properties) { super(properties); }

    @Override
    protected BlockEntityType<? extends PowerBlock.Entity> type() { return ModBlockEntities.BATTERY_BANK.get(); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Entity(pos, state); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity battery)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            long percent = battery.energy.amount() * 100 / battery.energy.capacity();
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.battery_status",
                    fe(battery.energy.amount()), fe(battery.energy.capacity()), percent));
        }
        return InteractionResult.SUCCESS;
    }

    public static final class Entity extends PowerBlock.Entity {
        public Entity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.BATTERY_BANK.get(), pos, state, Power.Role.STORE, Power.BATTERY_BUFFER);
        }

        @Override
        protected void work(ServerLevel level) {}

        /** The charge light: on while it holds anything worth drawing on. */
        @Override
        protected boolean running() { return energy.amount() >= Power.STATION_DRAW; }
    }
}
