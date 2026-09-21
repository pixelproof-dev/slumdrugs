package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lucas.slumdrugs.sim.npc.Turf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Who holds which corner of a level. A corner is a chunk; the key is its packed position. */
public final class TurfMap {

    /** One corner: who holds it, who they took it from if a crew, and when. */
    public record Cell(String holder, String takenFrom, long since) {
        public static final Codec<Cell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("holder").forGetter(Cell::holder),
                Codec.STRING.optionalFieldOf("taken_from", "").forGetter(Cell::takenFrom),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(Cell::since)
        ).apply(instance, Cell::new));

        public Cell {
            if (holder == null) holder = "";
            if (takenFrom == null) takenFrom = "";
        }
    }

    public static final Codec<TurfMap> CODEC = Codec.unboundedMap(Codec.STRING, Cell.CODEC).xmap(saved -> {
        Map<Long, Cell> cells = new HashMap<>();
        saved.forEach((k, v) -> cells.put(Long.parseLong(k), v));
        return new TurfMap(cells);
    }, map -> {
        Map<String, Cell> out = new HashMap<>();
        map.cells.forEach((k, v) -> out.put(Long.toString(k), v));
        return out;
    });

    private final Map<Long, Cell> cells;

    public TurfMap() { this(Map.of()); }

    public TurfMap(Map<Long, Cell> cells) { this.cells = new HashMap<>(cells); }

    /** The corner at a key, or null for one nobody holds. */
    public Cell at(long key) { return cells.get(key); }

    public String holderAt(long key) {
        Cell cell = cells.get(key);
        return cell == null ? "" : cell.holder();
    }

    public void set(long key, Cell cell) {
        if (cell == null || cell.holder().isEmpty()) cells.remove(key); else cells.put(key, cell);
    }

    public void clear(long key) { cells.remove(key); }

    public int heldBy(String holder) {
        int n = 0;
        for (Cell c : cells.values()) if (c.holder().equals(holder)) n++;
        return n;
    }

    public List<Long> cellsOf(String holder) {
        List<Long> out = new ArrayList<>();
        cells.forEach((k, v) -> { if (v.holder().equals(holder)) out.add(k); });
        return out;
    }

    /** Whether a player took this corner from the crew named: the crew resents them on it. */
    public boolean tookFrom(long key, String player, String crew) {
        Cell cell = cells.get(key);
        return cell != null && cell.holder().equals(player) && !crew.isBlank() && cell.takenFrom().equals(crew);
    }

    public boolean isPlayerHeld(long key) { return Turf.isPlayer(holderAt(key)); }
}
