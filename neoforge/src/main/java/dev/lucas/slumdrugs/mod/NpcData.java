package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lucas.slumdrugs.sim.npc.Loyalty;
import dev.lucas.slumdrugs.sim.npc.Npc;

/**
 * What makes a villager one of ours: a role, a crew, how angry they currently are, how they
 * feel about the player as a customer, and for a hired hand the last day they were paid.
 */
public record NpcData(Npc.Role role, String crew, double aggression, double loyalty, long paidDay) {

    public static final NpcData NONE = new NpcData(Npc.Role.RESIDENT, "", 0);

    public static final MapCodec<NpcData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("role").forGetter(d -> d.role().name()),
            Codec.STRING.optionalFieldOf("crew", "").forGetter(NpcData::crew),
            Codec.DOUBLE.optionalFieldOf("aggression", 0.0).forGetter(NpcData::aggression),
            Codec.DOUBLE.optionalFieldOf("loyalty", Loyalty.START).forGetter(NpcData::loyalty),
            Codec.LONG.optionalFieldOf("paid_day", -1L).forGetter(NpcData::paidDay)
    ).apply(instance, (role, crew, aggression, loyalty, paidDay) -> new NpcData(parse(role), crew, aggression, loyalty, paidDay)));

    public NpcData(Npc.Role role, String crew, double aggression) { this(role, crew, aggression, Loyalty.START, -1); }

    public NpcData(Npc.Role role, String crew, double aggression, double loyalty) { this(role, crew, aggression, loyalty, -1); }

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

    public NpcData withAggression(double value) { return new NpcData(role, crew, value, loyalty, paidDay); }

    public NpcData withLoyalty(double value) { return new NpcData(role, crew, aggression, value, paidDay); }

    public NpcData withPaidDay(long day) { return new NpcData(role, crew, aggression, loyalty, day); }

    /** Taken on, or let go: the role and the employer change together. */
    public NpcData asRole(Npc.Role newRole, String newCrew) { return new NpcData(newRole, newCrew, aggression, loyalty, paidDay); }
}
