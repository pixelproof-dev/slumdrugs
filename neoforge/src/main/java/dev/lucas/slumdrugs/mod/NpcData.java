package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lucas.slumdrugs.sim.npc.Loyalty;
import dev.lucas.slumdrugs.sim.npc.Npc;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What makes a villager one of ours: a role, a crew, how angry they currently are, how they
 * feel about the player as a customer, for a hired hand the last day they were paid, and the
 * skin they wear ({@code look}, a file in textures/entity/npc; see sim Looks).
 */
public record NpcData(Npc.Role role, String crew, double aggression, double loyalty, long paidDay, String look) {

    public static final NpcData NONE = new NpcData(Npc.Role.RESIDENT, "", 0);

    public static final MapCodec<NpcData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("role").forGetter(d -> d.role().name()),
            Codec.STRING.optionalFieldOf("crew", "").forGetter(NpcData::crew),
            Codec.DOUBLE.optionalFieldOf("aggression", 0.0).forGetter(NpcData::aggression),
            Codec.DOUBLE.optionalFieldOf("loyalty", Loyalty.START).forGetter(NpcData::loyalty),
            Codec.LONG.optionalFieldOf("paid_day", -1L).forGetter(NpcData::paidDay),
            Codec.STRING.optionalFieldOf("look", "").forGetter(NpcData::look)
    ).apply(instance, (role, crew, aggression, loyalty, paidDay, look) -> new NpcData(parse(role), crew, aggression, loyalty, paidDay, look)));

    /**
     * What a client is told: the role, the crew and the skin, which is what a person looks like
     * from the street. Mood and loyalty stay on the server; a client that could read them would show a
     * player who is about to turn on them before it happens.
     */
    public static final StreamCodec<ByteBuf, NpcData> LOOKS = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, d -> d.role().name(),
            ByteBufCodecs.STRING_UTF8, NpcData::crew,
            ByteBufCodecs.STRING_UTF8, NpcData::look,
            (role, crew, look) -> new NpcData(parse(role), crew, 0, Loyalty.START, -1, look));

    public NpcData(Npc.Role role, String crew, double aggression) { this(role, crew, aggression, Loyalty.START, -1, ""); }

    public NpcData(Npc.Role role, String crew, double aggression, double loyalty) { this(role, crew, aggression, loyalty, -1, ""); }

    public NpcData {
        if (role == null) role = Npc.Role.RESIDENT;
        if (crew == null) crew = "";
        if (look == null) look = "";
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

    public NpcData withAggression(double value) { return new NpcData(role, crew, value, loyalty, paidDay, look); }

    public NpcData withLoyalty(double value) { return new NpcData(role, crew, aggression, value, paidDay, look); }

    public NpcData withPaidDay(long day) { return new NpcData(role, crew, aggression, loyalty, day, look); }

    public NpcData withLook(String value) { return new NpcData(role, crew, aggression, loyalty, paidDay, value); }

    /** Taken on, or let go: the role and the employer change, the face stays. */
    public NpcData asRole(Npc.Role newRole, String newCrew) { return new NpcData(newRole, newCrew, aggression, loyalty, paidDay, look); }
}
