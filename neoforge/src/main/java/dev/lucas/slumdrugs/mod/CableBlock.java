package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.station.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

/**
 * A power cable: an orange extension lead. It joins up with the cables and powered blocks around
 * it, and everything on one run of cable is one network ({@link CableNet}), routed by sim
 * {@link Power#route}: stations first, batteries with what is left.
 *
 * <p>Nothing to configure and nothing lost on the way — MOD-GDD.md §5.16 wants power without a
 * cabling puzzle, and a cable that only connects is that. Another mod's generator can push into
 * a cable through the energy capability, and another mod's machines on the run are fed from it.
 */
public final class CableBlock extends PipeBlock implements EntityBlock {

    public CableBlock(Properties properties) {
        super(5.0f, properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.values())
            state = state.setValue(PROPERTY_BY_DIRECTION.get(side), connects(context.getLevel(), context.getClickedPos(), side));
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction side, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        // Something next to the run changed: the network it belongs to is worked out again.
        if (level instanceof Level world) CableNet.invalidate(world);
        return state.setValue(PROPERTY_BY_DIRECTION.get(side), connects(level, pos, side));
    }

    /** A cable joins cables, our powered blocks, and anything that speaks the energy capability. */
    static boolean connects(LevelReader level, BlockPos pos, Direction side) {
        BlockPos next = pos.relative(side);
        BlockState there = level.getBlockState(next);
        if (there.getBlock() instanceof CableBlock) return true;
        if (level.getBlockEntity(next) instanceof PowerNet.Node) return true;
        return level instanceof Level world
                && world.getCapability(Capabilities.Energy.BLOCK, next, side.getOpposite()) != null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        CableNet.invalidate(level);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        CableNet.invalidate(level);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CABLE.get()) return null;
        return (lvl, pos, st, be) -> {
            if (lvl.getGameTime() % 20 == 0 && lvl instanceof ServerLevel server) CableNet.tick(server, pos);
        };
    }

    /**
     * What a cable holds: power pushed in by another mod's generator, waiting for the network to
     * route it. Our own blocks are routed directly and never go through it.
     */
    public static final class Entity extends BlockEntity {
        final PowerNet.Buffer input;

        public Entity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.CABLE.get(), pos, state);
            // Taken in from outside, given out only by the network.
            input = new PowerNet.Buffer(Power.CABLE_BUFFER, Power.Role.STATION, this::setChanged);
        }

        public PowerNet.Buffer input() { return input; }

        @Override
        public void setRemoved() {
            super.setRemoved();
            if (level != null) CableNet.invalidate(level);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            input.save(output);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            this.input.load(input);
        }
    }
}
