package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import dev.lucas.slumdrugs.sim.npc.Standing;

import java.util.HashMap;
import java.util.Map;

/** A player's standing with every crew they have met. The rule is {@link Standing}'s. */
public final class Standings {

    public static final Codec<Standings> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
            .xmap(Standings::new, s -> Map.copyOf(s.values));

    private final Map<String, Double> values;

    public Standings() { this(Map.of()); }

    public Standings(Map<String, Double> values) { this.values = new HashMap<>(values); }

    public double with(String crew) { return Standing.of(values, crew); }

    public void move(String crew, double delta) { Standing.apply(values, crew, delta); }

    public void set(String crew, double value) {
        if (crew == null || crew.isBlank()) return;
        values.put(crew, Standing.clamp(value));
    }

    public Map<String, Double> all() { return Map.copyOf(values); }
}
