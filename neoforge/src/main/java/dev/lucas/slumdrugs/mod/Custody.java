package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player in the Watch's hands: held at a spot until a time, or until bail is paid in
 * stamped coin. Nothing when {@code until} is zero.
 */
public record Custody(long until, double x, double y, double z, long bail) {

    public static final Custody FREE = new Custody(0, 0, 0, 0, 0);

    public static final Codec<Custody> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("until").forGetter(Custody::until),
            Codec.DOUBLE.fieldOf("x").forGetter(Custody::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Custody::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Custody::z),
            Codec.LONG.fieldOf("bail").forGetter(Custody::bail)
    ).apply(instance, Custody::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Custody> STREAM = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Custody::until,
            ByteBufCodecs.DOUBLE, Custody::x,
            ByteBufCodecs.DOUBLE, Custody::y,
            ByteBufCodecs.DOUBLE, Custody::z,
            ByteBufCodecs.VAR_LONG, Custody::bail,
            Custody::new);

    public boolean held(long now) { return until > now; }

    public long secondsLeft(long now) { return Math.max(0, (until - now + 999) / 1000); }
}
