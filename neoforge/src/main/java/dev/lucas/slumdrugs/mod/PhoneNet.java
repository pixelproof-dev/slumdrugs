package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.npc.Loyalty;
import dev.lucas.slumdrugs.sim.player.PhoneBook;
import dev.lucas.slumdrugs.sim.world.TownSites;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The phone's screen ({@code client.PhoneScreen}) and the server talking.
 *
 * <p>The server owns everything: the client is sent a {@link State} to draw and sends back an
 * {@link Action}. Nothing the client says is trusted beyond "this player pressed that": every
 * action is checked against the phone book and needs the phone in the inventory.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class PhoneNet {

    private PhoneNet() {}

    public record OrderRow(String id, String name, String substance, int units, long ticksLeft, int x, int y, int z) {
        static final StreamCodec<ByteBuf, OrderRow> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OrderRow::id,
                ByteBufCodecs.STRING_UTF8, OrderRow::name,
                ByteBufCodecs.STRING_UTF8, OrderRow::substance,
                ByteBufCodecs.VAR_INT, OrderRow::units,
                ByteBufCodecs.VAR_LONG, OrderRow::ticksLeft,
                ByteBufCodecs.VAR_INT, OrderRow::x,
                ByteBufCodecs.VAR_INT, OrderRow::y,
                ByteBufCodecs.VAR_INT, OrderRow::z,
                OrderRow::new);
    }

    /** Loyalty goes over as a whole number: the screen shows a word for it, never the figure. */
    public record ContactRow(String id, String name, String substance, int loyalty, boolean waiting) {
        static final StreamCodec<ByteBuf, ContactRow> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ContactRow::id,
                ByteBufCodecs.STRING_UTF8, ContactRow::name,
                ByteBufCodecs.STRING_UTF8, ContactRow::substance,
                ByteBufCodecs.VAR_INT, ContactRow::loyalty,
                ByteBufCodecs.BOOL, ContactRow::waiting,
                ContactRow::new);
    }

    /** What the town search last said. */
    public enum TownStatus { NOT_ASKED, FOUND, NONE, NO_NETWORK }

    /**
     * Everything the screen shows.
     *
     * @param open whether to open the screen, or only refresh it if it is already open
     */
    public record State(boolean open, List<OrderRow> orders, List<ContactRow> contacts,
                        int townStatus, int townX, int townZ) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, "phone_state"));
        public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, State::open,
                OrderRow.CODEC.apply(ByteBufCodecs.list(PhoneBook.MAX_OPEN * 4)), State::orders,
                ContactRow.CODEC.apply(ByteBufCodecs.list(PhoneBook.MAX_CONTACTS * 4)), State::contacts,
                ByteBufCodecs.VAR_INT, State::townStatus,
                ByteBufCodecs.VAR_INT, State::townX,
                ByteBufCodecs.VAR_INT, State::townZ,
                State::new);

        public TownStatus town() {
            TownStatus[] all = TownStatus.values();
            return townStatus >= 0 && townStatus < all.length ? all[townStatus] : TownStatus.NOT_ASKED;
        }

        @Override
        public Type<State> type() { return TYPE; }
    }

    public enum Verb { REFRESH, DECLINE, FORGET, LOCATE }

    public record Action(int verb, String contactId) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, "phone_action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::verb,
                ByteBufCodecs.stringUtf8(64), Action::contactId,
                Action::new);

        public Action(Verb verb, String contactId) { this(verb.ordinal(), contactId); }

        @Override
        public Type<Action> type() { return TYPE; }
    }

    /** Ticks between two town searches from one player: the search asks where the villages are. */
    private static final long LOCATE_COOLDOWN = 20 * 5;

    /** How far the phone looks for a town, in regions of {@link TownSites#REGION} blocks. */
    static final int REACH = 6;

    private record Located(TownStatus status, int x, int z, long at) {}

    /** The last town search per player, so reopening the phone shows the answer again. */
    private static final Map<UUID, Located> LAST = new HashMap<>();

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        // The screen is client code, so its handler is registered from ClientSetup.
        registrar.playToClient(State.TYPE, State.CODEC);
        registrar.playToServer(Action.TYPE, Action.CODEC, PhoneNet::onAction);
    }

    /** Opens the phone for a player: the item's use. */
    public static void open(ServerPlayer player) {
        send(player, true);
    }

    private static void onAction(Action action, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !Phone.carries(player)) return;
        Verb[] verbs = Verb.values();
        if (action.verb() < 0 || action.verb() >= verbs.length) return;
        PhoneBook book = Phone.of(player);
        switch (verbs[action.verb()]) {
            case REFRESH -> {}
            case DECLINE -> {
                if (book.decline(action.contactId()) && player.level() instanceof ServerLevel level)
                    Phone.settleIfNear(level, book, action.contactId());
            }
            case FORGET -> {
                if (book.forget(action.contactId()) && player.level() instanceof ServerLevel level)
                    Phone.settleIfNear(level, book, action.contactId());
            }
            case LOCATE -> locate(player);
        }
        send(player, false);
    }

    private static void locate(ServerPlayer player) {
        long now = player.level().getGameTime();
        Located last = LAST.get(player.getUUID());
        // Too soon: the last answer stands rather than searching twice.
        if (last != null && now - last.at() < LOCATE_COOLDOWN) return;
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            LAST.put(player.getUUID(), new Located(TownStatus.NO_NETWORK, 0, 0, now));
            return;
        }
        Optional<TownSites.Site> town = Towns.nearest(level, player.getBlockX(), player.getBlockZ(), REACH);
        LAST.put(player.getUUID(), town
                .map(site -> new Located(TownStatus.FOUND, site.x(), site.z(), now))
                .orElseGet(() -> new Located(TownStatus.NONE, 0, 0, now)));
    }

    static void send(ServerPlayer player, boolean open) {
        PhoneBook book = Phone.of(player);
        long now = player.level().getGameTime();
        List<OrderRow> orders = book.orders().stream().map(o -> new OrderRow(o.contactId(), o.name(), o.substance(),
                o.units(), o.ticksLeft(now), o.x(), o.y(), o.z())).toList();
        List<ContactRow> contacts = book.contacts().stream().map(c -> new ContactRow(c.id(), c.name(), c.substance(),
                (int) Math.round(Loyalty.clamp(c.loyalty())), book.orderFrom(c.id()).isPresent())).toList();
        Located town = LAST.getOrDefault(player.getUUID(), new Located(TownStatus.NOT_ASKED, 0, 0, 0));
        PacketDistributor.sendToPlayer(player, new State(open, orders, contacts, town.status().ordinal(), town.x(), town.z()));
    }
}
