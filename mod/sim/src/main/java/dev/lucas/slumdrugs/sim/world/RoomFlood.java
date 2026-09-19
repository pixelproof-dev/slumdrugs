package dev.lucas.slumdrugs.sim.world;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Bounded flood fill over a flat grid, used to decide whether a room is enclosed.
 * Returns an empty list when the region is unbounded, too large, or the seed is blocked —
 * refusing is the safe answer, because the caller uses it to decide what it may modify.
 */
public final class RoomFlood {

    public record Cell(int x, int z) {}

    /** Maximum distance from the seed in either axis before the region counts as unbounded. */
    public static final int REACH = 12;

    private RoomFlood() {}

    public static List<Cell> flood(Cell seed, Predicate<Cell> inRoom, int limit) {
        var seen = new LinkedHashSet<Cell>();
        var queue = new ArrayDeque<Cell>();
        queue.add(seed);
        while (!queue.isEmpty()) {
            Cell c = queue.removeFirst();
            if (seen.contains(c) || !inRoom.test(c)) continue;
            if (Math.abs(c.x() - seed.x()) > REACH || Math.abs(c.z() - seed.z()) > REACH || seen.size() >= limit)
                return List.of();
            seen.add(c);
            queue.add(new Cell(c.x() + 1, c.z()));
            queue.add(new Cell(c.x() - 1, c.z()));
            queue.add(new Cell(c.x(), c.z() + 1));
            queue.add(new Cell(c.x(), c.z() - 1));
        }
        return List.copyOf(seen);
    }
}
