package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.station.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * A generator, a battery or a town meter: a block whose whole job is power. Ticks once a second,
 * like the stations, and shows {@link #RUNNING} while it is doing something worth seeing.
 */
public abstract class PowerBlock extends BaseEntityBlock {

    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    protected PowerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING);
    }

    protected abstract BlockEntityType<? extends Entity> type();

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != type()) return null;
        return (lvl, pos, st, be) -> {
            if (lvl.getGameTime() % 20 != 0 || !(be instanceof Entity power) || !(lvl instanceof ServerLevel server)) return;
            power.second(server);
            BlockState next = st.setValue(RUNNING, power.running());
            if (next != st) lvl.setBlock(pos, next, Block.UPDATE_CLIENTS);
        };
    }

    /** The block entity: a buffer, a second's work, and the sharing with neighbours. */
    public abstract static class Entity extends BlockEntity implements PowerNet.Node {

        private final Power.Role role;
        protected final PowerNet.Buffer energy;

        protected Entity(BlockEntityType<?> type, BlockPos pos, BlockState state, Power.Role role, int capacity) {
            super(type, pos, state);
            this.role = role;
            this.energy = new PowerNet.Buffer(capacity, role, this::setChanged);
        }

        @Override
        public Power.Role powerRole() { return role; }

        @Override
        public PowerNet.Buffer energy() { return energy; }

        /** One second: this block's own work, then the sharing. */
        void second(ServerLevel level) {
            work(level);
            PowerNet.exchange(level, worldPosition, this);
        }

        protected abstract void work(ServerLevel level);

        /** Whether the block shows itself at work: smoke, a lit dial. */
        protected abstract boolean running();

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            energy.save(output);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            energy.load(input);
        }
    }

    /** "12.3k", for the action bar. */
    static String fe(long amount) {
        if (amount >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", amount / 1_000.0);
        return Long.toString(amount);
    }
}
