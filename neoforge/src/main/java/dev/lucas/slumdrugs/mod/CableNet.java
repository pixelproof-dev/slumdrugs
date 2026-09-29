package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.station.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Runs of cable, and moving power along them once a second.
 *
 * <p>A network is every cable joined to every other, and the blocks they touch. Working one out
 * is a walk along the cables, so it is kept until something next to a cable changes and then
 * thrown away and walked again on the next tick ({@link #invalidate}). Each network moves power
 * once a second, on whichever of its cables ticks first; the rest find it done.
 */
public final class CableNet {

    private CableNet() {}

    /** Something a cable touches that is not a cable: the block and the side it is touched on. */
    private record End(BlockPos pos, Direction side) {}

    private static final class Net {
        final Set<BlockPos> cables = new HashSet<>();
        final Set<End> ends = new LinkedHashSet<>();
        long movedAt = -1;
    }

    private static final Map<Level, Map<BlockPos, Net>> KNOWN = new WeakHashMap<>();

    static void invalidate(Level level) {
        KNOWN.remove(level);
    }

    static void tick(ServerLevel level, BlockPos cable) {
        Net net = of(level, cable);
        long now = level.getGameTime();
        if (net.movedAt == now) return;
        net.movedAt = now;
        move(level, net);
    }

    private static Net of(Level level, BlockPos start) {
        Map<BlockPos, Net> known = KNOWN.computeIfAbsent(level, l -> new HashMap<>());
        Net net = known.get(start);
        if (net != null) return net;
        net = new Net();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
        net.cables.add(start);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (!level.isLoaded(next)) continue;
                if (level.getBlockState(next).getBlock() instanceof CableBlock) {
                    if (net.cables.add(next)) queue.add(next);
                } else {
                    net.ends.add(new End(next, side.getOpposite()));
                }
            }
        }
        for (BlockPos pos : net.cables) known.put(pos, net);
        return net;
    }

    /**
     * One second: our blocks on the run are routed by sim {@link Power#route}, with the power
     * other mods pushed into the cables counted as a source; what sources have left then goes to
     * other mods' blocks on the run.
     */
    private static void move(ServerLevel level, Net net) {
        List<PowerNet.Buffer> buffers = new ArrayList<>();
        List<Power.Role> roles = new ArrayList<>();
        List<EnergyHandler> foreign = new ArrayList<>();
        Set<PowerNet.Buffer> seen = new HashSet<>();
        for (BlockPos pos : net.cables)
            if (level.getBlockEntity(pos) instanceof CableBlock.Entity cable && cable.input().amount() > 0) {
                buffers.add(cable.input());
                roles.add(Power.Role.SOURCE);
            }
        for (End end : net.ends) {
            if (level.getBlockEntity(end.pos()) instanceof PowerNet.Node node) {
                // A block touched by two cables of the run is still one block.
                if (!seen.add(node.energy())) continue;
                buffers.add(node.energy());
                roles.add(node.powerRole());
                continue;
            }
            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, end.pos(), end.side());
            if (handler != null) foreign.add(handler);
        }
        if (buffers.isEmpty()) return;

        int n = buffers.size();
        long[] amounts = new long[n], capacities = new long[n];
        for (int i = 0; i < n; i++) {
            amounts[i] = buffers.get(i).amount();
            capacities[i] = buffers.get(i).capacity();
        }
        long[] delta = Power.route(roles.toArray(Power.Role[]::new), amounts, capacities);
        for (int i = 0; i < n; i++) if (delta[i] != 0) buffers.get(i).add(delta[i]);

        // Other mods' blocks on the run take from our sources and batteries, last.
        for (EnergyHandler sink : foreign) {
            for (int i = 0; i < n; i++) {
                if (roles.get(i) == Power.Role.STATION) continue;
                PowerNet.Buffer from = buffers.get(i);
                long offer = Math.min(from.amount(), Power.CONTACT_RATE);
                if (offer <= 0) continue;
                try (Transaction tx = Transaction.openRoot()) {
                    int taken = sink.insert((int) offer, tx);
                    tx.commit();
                    from.add(-taken);
                }
            }
        }
    }
}
