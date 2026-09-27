package dev.lucas.slumdrugs.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lucas.slumdrugs.sim.economy.MarketState;
import dev.lucas.slumdrugs.sim.player.Condition;
import dev.lucas.slumdrugs.sim.player.PhoneBook;
import dev.lucas.slumdrugs.sim.player.Progression;
import dev.lucas.slumdrugs.sim.player.Suspicion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
                    Codec.LONG.fieldOf("last_sleep").forGetter(c -> c.lastSleep),
                    Codec.LONG.optionalFieldOf("soothed_until", 0L).forGetter(c -> c.soothedUntil),
                    Codec.DOUBLE.optionalFieldOf("tolerance_ceiling", 100.0).forGetter(c -> c.toleranceCeiling),
                    Codec.LONG.optionalFieldOf("streak_rewarded_at", 0L).forGetter(c -> c.streakRewardedAt)
            ).apply(instance, Condition::of));

    /** The same fields over the wire, for the HUD. Sent to the player it belongs to and nobody else. */
    private static final StreamCodec<RegistryFriendlyByteBuf, Condition> CONDITION_STREAM = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, c -> c.intoxication,
            ByteBufCodecs.DOUBLE, c -> c.tolerance,
            ByteBufCodecs.DOUBLE, c -> c.dependence,
            ByteBufCodecs.VAR_LONG, c -> c.lastUse,
            ByteBufCodecs.VAR_LONG, c -> c.lastSleep,
            ByteBufCodecs.VAR_LONG, c -> c.soothedUntil,
            ByteBufCodecs.DOUBLE, c -> c.toleranceCeiling,
            ByteBufCodecs.VAR_LONG, c -> c.streakRewardedAt,
            Condition::of);

    /**
     * Kept through death on purpose: dying does not cure anyone, and a player who could reset
     * their dependence by dying would never engage with recovery at all.
     *
     * <p>Synced to its owner only, and only when the server says so: whoever changes the
     * condition calls {@code syncData} afterwards.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Condition>> CONDITION =
            TYPES.register("condition", () -> AttachmentType.builder(Condition::new)
                    .serialize(CONDITION_CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, CONDITION_STREAM)
                    .build());

    private static final com.mojang.serialization.MapCodec<Progression> PROGRESSION_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.INT.fieldOf("units_sold").forGetter(p -> p.unitsSold),
                    Codec.INT.fieldOf("coin_earned").forGetter(p -> p.coinEarned),
                    Codec.BOOL.optionalFieldOf("started", false).forGetter(p -> p.started)
            ).apply(instance, Progression::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, Progression> PROGRESSION_STREAM = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, p -> p.unitsSold,
            ByteBufCodecs.VAR_INT, p -> p.coinEarned,
            ByteBufCodecs.BOOL, p -> p.started,
            Progression::new);

    /** What a player has sold and earned, and so which tier they stand on. Death does not demote anyone. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Progression>> PROGRESSION =
            TYPES.register("progression", () -> AttachmentType.builder(Progression::new)
                    .serialize(PROGRESSION_CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, PROGRESSION_STREAM)
                    .build());

    private static final com.mojang.serialization.MapCodec<Suspicion> SUSPICION_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.DOUBLE.fieldOf("value").forGetter(s -> s.value),
                    Codec.LONG.optionalFieldOf("raid_at", 0L).forGetter(s -> s.raidAt),
                    Codec.INT.optionalFieldOf("bribes", 0).forGetter(s -> s.bribes)
            ).apply(instance, Suspicion::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, Suspicion> SUSPICION_STREAM = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, s -> s.value,
            ByteBufCodecs.VAR_LONG, s -> s.raidAt,
            ByteBufCodecs.VAR_INT, s -> s.bribes,
            Suspicion::new);

    /** How much the Watch has noticed. Kept through death: the Watch does not forget a face. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Suspicion>> SUSPICION =
            TYPES.register("suspicion", () -> AttachmentType.builder(Suspicion::new)
                    .serialize(SUSPICION_CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, SUSPICION_STREAM)
                    .build());

    private static final com.mojang.serialization.MapCodec<MarketState> MARKET_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("demand").xmap(saved -> {
                MarketState market = new MarketState(Market.settings());
                market.restore(saved);
                return market;
            }, MarketState::snapshot);

    /**
     * Local demand, one per level. A settlement map would want one per settlement; until the
     * map exists, the world is the settlement.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MarketState>> MARKET =
            TYPES.register("market", () -> AttachmentType.builder(
                            () -> new MarketState(Market.settings()))
                    .serialize(MARKET_CODEC)
                    .build());

    /** In the Watch's hands, or free. Not kept through death: a corpse is not in custody. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Custody>> CUSTODY =
            TYPES.register("custody", () -> AttachmentType.builder(() -> Custody.FREE)
                    .serialize(Custody.CODEC.fieldOf("custody"))
                    .sync((holder, to) -> holder == to, Custody.STREAM)
                    .build());

    /** Standing with every crew. Kept through death: the crews remember too. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Standings>> STANDINGS =
            TYPES.register("standings", () -> AttachmentType.builder((java.util.function.Supplier<Standings>) Standings::new)
                    .serialize(Standings.CODEC.fieldOf("standings"))
                    .copyOnDeath()
                    .build());

    /** Who holds which corner, one map per level. A corner is a chunk. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TurfMap>> TURF =
            TYPES.register("turf", () -> AttachmentType.builder((java.util.function.Supplier<TurfMap>) TurfMap::new)
                    .serialize(TurfMap.CODEC.fieldOf("cells"))
                    .build());

    /**
     * Not copied on death: a villager that dies is gone, and their replacement is a new person.
     *
     * <p>Synced to everybody who can see the villager, role and crew only, so the client can
     * draw one of ours as a person rather than a villager ({@code client.NpcRenderer}).
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NpcData>> NPC =
            TYPES.register("npc", () -> AttachmentType.builder(() -> NpcData.NONE)
                    .serialize(NpcData.CODEC)
                    .sync(NpcData.LOOKS)
                    .build());

    private static final Codec<PhoneBook.Contact> CONTACT = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(PhoneBook.Contact::id),
            Codec.STRING.fieldOf("name").forGetter(PhoneBook.Contact::name),
            Codec.STRING.fieldOf("substance").forGetter(PhoneBook.Contact::substance),
            Codec.DOUBLE.fieldOf("loyalty").forGetter(PhoneBook.Contact::loyalty),
            Codec.INT.fieldOf("x").forGetter(PhoneBook.Contact::x),
            Codec.INT.fieldOf("y").forGetter(PhoneBook.Contact::y),
            Codec.INT.fieldOf("z").forGetter(PhoneBook.Contact::z)
    ).apply(i, PhoneBook.Contact::new));

    private static final Codec<PhoneBook.Order> ORDER = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("contact").forGetter(PhoneBook.Order::contactId),
            Codec.STRING.fieldOf("name").forGetter(PhoneBook.Order::name),
            Codec.STRING.fieldOf("substance").forGetter(PhoneBook.Order::substance),
            Codec.INT.fieldOf("units").forGetter(PhoneBook.Order::units),
            Codec.LONG.fieldOf("placed").forGetter(PhoneBook.Order::placed),
            Codec.LONG.fieldOf("due").forGetter(PhoneBook.Order::due),
            Codec.INT.fieldOf("x").forGetter(PhoneBook.Order::x),
            Codec.INT.fieldOf("y").forGetter(PhoneBook.Order::y),
            Codec.INT.fieldOf("z").forGetter(PhoneBook.Order::z)
    ).apply(i, PhoneBook.Order::new));

    private static final com.mojang.serialization.MapCodec<PhoneBook> PHONE_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    CONTACT.listOf().optionalFieldOf("contacts", java.util.List.of()).forGetter(PhoneBook::contacts),
                    ORDER.listOf().optionalFieldOf("orders", java.util.List.of()).forGetter(PhoneBook::orders),
                    Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("owed", java.util.Map.of()).forGetter(PhoneBook::owed)
            ).apply(i, PhoneBook::new));

    /**
     * The burner phone's contacts and open orders ({@link Phone}). Kept through death: the
     * phone may be lost, the numbers are in the player's head. Server only; nothing on the
     * client reads it, the phone speaks in chat.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PhoneBook>> PHONE =
            TYPES.register("phone", () -> AttachmentType.builder(PhoneBook::new)
                    .serialize(PHONE_CODEC)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {}
}
