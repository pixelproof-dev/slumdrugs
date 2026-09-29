package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.station.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Power between blocks, by touch (sim {@link Power}).
 *
 * <p>Every powered block of ours holds a {@link Buffer} and calls {@link #exchange} once a
 * second. Between two of ours the roles decide which way power runs and how much. Anything
 * else that speaks the platform's energy capability — another mod's cable, battery or machine —
 * is fed by our sources and batteries and may feed our batteries and stations through the same
 * capability, which is registered here for every one of them. That capability is the whole of
 * the compatibility with AE2, Create's electric addons and the rest: no module, no code of theirs.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class PowerNet {

    private PowerNet() {}

    /** A block of ours that holds power. */
    public interface Node {
        Power.Role powerRole();

        Buffer energy();
    }

    /**
     * FE held by a block. Other mods may put power into stores and stations and take it out of
     * sources and stores; the limits say which. Our own moves go through {@link #set} and are
     * not limited by them.
     */
    public static final class Buffer extends SimpleEnergyHandler {
        private final Runnable changed;

        public Buffer(int capacity, Power.Role role, Runnable changed) {
            super(capacity,
                    role == Power.Role.SOURCE ? 0 : Power.CONTACT_RATE,
                    role == Power.Role.STATION ? 0 : Power.CONTACT_RATE);
            this.changed = changed;
        }

        public long amount() { return energy; }

        public long capacity() { return capacity; }

        public void add(long delta) {
            set((int) Math.max(0, Math.min(capacity, energy + delta)));
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            changed.run();
        }

        public void save(ValueOutput output) {
            output.putInt("energy", energy);
        }

        public void load(ValueInput input) {
            energy = Math.max(0, Math.min(capacity, input.getIntOr("energy", 0)));
        }
    }

    /**
     * One second of sharing with the six neighbours. Each pair is settled by whichever of the two
     * gives, so a pair that both tick moves power once, not twice.
     *
     * <p>Stations are served first, then our other blocks, then other mods'. A generator with a
     * battery on one side and a grow tent on the other runs the tent and banks the rest; served
     * in the order the sides happen to come, the battery would take the lot while the light went
     * out.
     */
    public static void exchange(Level level, BlockPos pos, Node self) {
        Buffer mine = self.energy();
        for (int pass = 0; pass < 3; pass++) {
            for (Direction side : Direction.values()) {
                if (mine.amount() <= 0) return;
                BlockPos next = pos.relative(side);
                if (!level.isLoaded(next)) continue;
                if (level.getBlockEntity(next) instanceof Node other) {
                    boolean station = other.powerRole() == Power.Role.STATION;
                    if (pass != (station ? 0 : 1)) continue;
                    Buffer theirs = other.energy();
                    long move = Power.flow(self.powerRole(), mine.amount(), mine.capacity(),
                            other.powerRole(), theirs.amount(), theirs.capacity());
                    if (move > 0) {
                        mine.add(-move);
                        theirs.add(move);
                    }
                    continue;
                }
                // Something of another mod's. Stations keep what they have; the rest hand it over.
                if (pass != 2 || self.powerRole() == Power.Role.STATION) continue;
                EnergyHandler foreign = level.getCapability(Capabilities.Energy.BLOCK, next, side.getOpposite());
                if (foreign != null) EnergyHandlerUtil.move(mine, foreign, Power.CONTACT_RATE, null);
            }
        }
    }

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.BATTERY_BANK.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.POWER_METER.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.FORCING_FRAME.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.DRYING_LOFT.get(), (be, side) -> be.energy());
    }
}
