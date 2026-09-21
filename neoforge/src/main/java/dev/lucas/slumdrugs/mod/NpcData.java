package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lucas.slumdrugs.sim.npc.Loyalty;
import dev.lucas.slumdrugs.sim.npc.Npc;

/** What makes a villager one of ours: a role, a crew, and how angry they currently are. */
public record NpcData(Npc.Role role, String crew, double aggression, double loyalty) {

    public static final NpcData NONE = new NpcData(Npc.Role.RESIDENT, "", 0);

    public static final MapCodec<NpcData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("role").forGetter(d -> d.role().name()),
            Codec.STRING.optionalFieldOf("crew", "").forGetter(NpcData::crew),
            Codec.DOUBLE.optionalFieldOf("aggression", 0.0).forGetter(NpcData::aggression),
            Codec.DOUBLE.optionalFieldOf("loyalty", Loyalty.START).forGetter(NpcData::loyalty)
    ).apply(instance, (role, crew, aggression, loyalty) -> new NpcData(parse(role), crew, aggression, loyalty)));

    public NpcData(Npc.Role role, String crew, double aggression) { this(role, crew, aggression, Loyalty.START); }

    public NpcData {
        if (role == null) role = Npc.Role.RESIDENT;
        if (crew == null) crew = "";
        aggression = Npc.clamp(aggression);
        loyalty = Loyalty.clamp(loyalty);
    }

    private static Npc.Role parse(String name) {
        try {
            return Npc.Role.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return Npc.Role.RESIDENT;
        }
    }

    public Npc.Stance stance() { return Npc.stance(role, aggression); }

    public NpcData withAggression(double value) { return new NpcData(role, crew, value, loyalty); }

    public NpcData withLoyalty(double value) { return new NpcData(role, crew, aggression, value); }
}
