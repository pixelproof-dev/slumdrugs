package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lucas.slumdrugs.sim.player.Condition;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Per-player state. The plugin kept this in its own YAML; here the world save carries it. */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SlumDrugsMod.ID);

    private static final com.mojang.serialization.MapCodec<Condition> CONDITION_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.DOUBLE.fieldOf("intoxication").forGetter(c -> c.intoxication),
                    Codec.DOUBLE.fieldOf("tolerance").forGetter(c -> c.tolerance),
                    Codec.DOUBLE.fieldOf("dependence").forGetter(c -> c.dependence),
                    Codec.LONG.fieldOf("last_use").forGetter(c -> c.lastUse),
                    Codec.LONG.fieldOf("last_sleep").forGetter(c -> c.lastSleep)
            ).apply(instance, Condition::of));

    /**
     * Kept through death on purpose: dying does not cure anyone, and a player who could reset
     * their dependence by dying would never engage with recovery at all.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Condition>> CONDITION =
            TYPES.register("condition", () -> AttachmentType.builder(Condition::new)
                    .serialize(CONDITION_CODEC)
                    .copyOnDeath()
                    .build());

    /** Not copied on death: a villager that dies is gone, and their replacement is a new person. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NpcData>> NPC =
            TYPES.register("npc", () -> AttachmentType.builder(() -> NpcData.NONE)
                    .serialize(NpcData.CODEC)
                    .build());

    private ModAttachments() {}
}
