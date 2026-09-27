package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.npc.Loyalty;
import dev.lucas.slumdrugs.sim.player.PhoneBook;
import dev.lucas.slumdrugs.sim.world.TownSites;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Regulars ringing the burner phone with orders. The rules — who rings, for how much, how long
 * they wait, what a delivery pays — are sim {@link PhoneBook}; this is where the phone rings,
 * where orders run out, and what the player is told.
 *
 * <p>Only a player carrying a phone is rung, which is what makes the phone worth crafting twice
 * over. Numbers are saved whether or not they carry one: the customer is the one who asks.
 * The handover itself is in {@link StreetSales}, because it is a sale.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Phone {

    private Phone() {}

    public static PhoneBook of(ServerPlayer player) {
        return player.getData(ModAttachments.PHONE.get());
    }

    static boolean carries(ServerPlayer player) {
        return player.getInventory().contains(stack -> stack.is(ModItems.get("burner_phone").get()));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now % 20 != 0) return;

        PhoneBook book = of(player);
        for (PhoneBook.Order gone : book.expire(now)) {
            player.sendSystemMessage(Component.translatable("phone.slumdrugs.gave_up", gone.name(),
                    substance(gone.substance())).withStyle(ChatFormatting.RED));
            settleIfNear(level, book, gone.contactId());
        }

        if (now % PhoneBook.RING_EVERY != 0) return;
        if (level.dimension() != Level.OVERWORLD || !carries(player)) return;
        long seed = now ^ player.getUUID().getLeastSignificantBits();
        List<PhoneBook.Order> placed = book.ring(now, seed, player.getBlockX(), player.getBlockZ());
        for (PhoneBook.Order order : placed) announce(player, order, now);
    }

    /** The phone rings: who, what, by when, and where, with the coordinates for a map mod. */
    static void announce(ServerPlayer player, PhoneBook.Order order, long now) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(),
                SoundSource.PLAYERS, 0.7f, 1.4f);
        player.sendSystemMessage(Component.translatable("phone.slumdrugs.order",
                order.name(), order.units(), substance(order.substance()), minutes(order.ticksLeft(now)),
                where(player, order)).withStyle(ChatFormatting.GOLD));
    }

    /** One line per open order, for the phone's screen. */
    static void listOrders(ServerPlayer player) {
        PhoneBook book = of(player);
        long now = player.level().getGameTime();
        if (book.orders().isEmpty()) {
            player.sendSystemMessage(Component.translatable(book.contacts().isEmpty()
                    ? "phone.slumdrugs.no_contacts" : "phone.slumdrugs.no_orders", book.contacts().size())
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        for (PhoneBook.Order order : book.orders())
            player.sendSystemMessage(Component.translatable("phone.slumdrugs.open_order",
                    order.name(), order.units(), substance(order.substance()), minutes(order.ticksLeft(now)),
                    where(player, order)).withStyle(ChatFormatting.YELLOW));
    }

    /** Remembers a regular after a sale. Says so the first time. */
    static void save(ServerPlayer player, Villager villager, String substance, double loyalty) {
        var contact = new PhoneBook.Contact(villager.getStringUUID(), villager.getName().getString(), substance,
                loyalty, villager.getBlockX(), villager.getBlockY(), villager.getBlockZ());
        if (of(player).save(contact))
            player.sendSystemMessage(Component.translatable(carries(player)
                    ? "phone.slumdrugs.saved" : "phone.slumdrugs.saved_no_phone", villager.getName())
                    .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Applies loyalty owed to a regular who was let down, now that the player is face to face
     * with them. Returns the data as it now stands.
     */
    static NpcData settle(ServerPlayer player, Villager villager, NpcData data) {
        double owed = of(player).settleOwed(villager.getStringUUID());
        if (owed == 0) return data;
        NpcData sore = data.withLoyalty(Loyalty.clamp(data.loyalty() + owed));
        villager.setData(ModAttachments.NPC.get(), sore);
        return sore;
    }

    /** A regular let down while loaded takes it out on their loyalty straight away. */
    private static void settleIfNear(ServerLevel level, PhoneBook book, String contactId) {
        UUID id;
        try {
            id = UUID.fromString(contactId);
        } catch (IllegalArgumentException malformed) {
            return;
        }
        if (!(level.getEntity(id) instanceof Villager villager) || !Npcs.isOurs(villager)) return;
        NpcData data = Npcs.data(villager);
        villager.setData(ModAttachments.NPC.get(), data.withLoyalty(Loyalty.clamp(data.loyalty() + book.settleOwed(contactId))));
    }

    static Component substance(String drug) {
        return Component.translatable("item.slumdrugs.product_" + drug);
    }

    private static int minutes(long ticks) {
        return (int) Math.max(1, Math.ceil(ticks / 1200.0));
    }

    /** Distance, direction and coordinates, as the phone gives them for towns. */
    private static Component where(ServerPlayer player, PhoneBook.Order order) {
        long dx = order.x() - player.getBlockX(), dz = order.z() - player.getBlockZ();
        long metres = Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        String bearing = TownSites.bearing(dx, dz).name().toLowerCase(Locale.ROOT);
        return Component.translatable("phone.slumdrugs.where", metres,
                Component.translatable("phone.slumdrugs.bearing." + bearing), order.x(), order.y(), order.z());
    }
}
