package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.npc.Loyalty;
import dev.lucas.slumdrugs.sim.npc.Npc;
import dev.lucas.slumdrugs.sim.npc.Turf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Selling to a regular, by hand. The merchant screen cannot see quality, so customers do not
 * use it: a player clicks a customer with product in hand and is paid for what it is worth,
 * by the batch's quality and by how much of it the street has already seen.
 *
 * <p>Customers each want one substance and take a handful at a time. That is the whole
 * street-sale loop the design starts with; loyalty and schedules come later.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class StreetSales {

    /** Units a customer takes per sale. */
    public static final int HAND = 4;

    /** A regular pays over the odds. */
    public static final double MARKUP = 1.6;

    private StreetSales() {}

    /** The word for how a regular feels, for the action bar. */
    static String loyaltyWord(double loyalty) {
        if (loyalty >= Loyalty.STANDING_ORDER) return "loyalty.slumdrugs.devoted";
        if (loyalty >= 60) return "loyalty.slumdrugs.warm";
        if (loyalty > 35) return "loyalty.slumdrugs.indifferent";
        if (loyalty > Loyalty.LOST) return "loyalty.slumdrugs.cool";
        return "loyalty.slumdrugs.lost";
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof Villager villager) || !Npcs.isOurs(villager)) return;
        if (Npcs.data(villager).role() != Npc.Role.CUSTOMER) return;

        // Customers keep no stall, so there is nothing else the click could do.
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        String wants = NpcTrades.preferred(villager);
        NpcData data = Npcs.data(villager);
        ItemStack held = event.getItemStack();
        String offered = ModItems.drugOf("product_", held);

        // A regular you have lost buys nothing, and mentions that you asked.
        if (Loyalty.lost(data.loyalty())) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.customer_lost", villager.getName())
                    .withStyle(s -> s.withColor(0xB05050)));
            if (offered != null) Watch.noticed(player, 1, false, 1);
            return;
        }

        if (offered == null || !offered.equals(wants)) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.customer_wants",
                    villager.getName(), Component.translatable("item.slumdrugs.product_" + wants),
                    Component.translatable(loyaltyWord(data.loyalty()))));
            return;
        }

        var market = Market.of(level);
        int hand = Loyalty.hand(data.loyalty(), Tuning.CUSTOMER_HAND.get());
        int units = Math.min(hand, Math.min(held.getCount(), (int) market.demand(wants)));
        if (units <= 0) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.street_flooded",
                    villager.getName()).withStyle(s -> s.withColor(0xB05050)));
            return;
        }

        int quality = ModComponents.qualityOf(held);
        int floor = NpcTrades.floor(villager);
        boolean cut = ModComponents.cutOf(held) > 0;
        // Below their floor they still buy, at a grudging price; friends pay better, and so
        // does anyone on a corner that is yours.
        boolean ownCorner = Turfs.own(player, villager.blockPosition());
        double markup = Tuning.CUSTOMER_MARKUP.get() * Loyalty.priceFactor(data.loyalty()) * (quality < floor ? 0.7 : 1.0)
                * ModComponents.strainOf(held).reputationFactor() * Turf.priceFactor(ownCorner);
        long pence = Market.pence(level, wants, held, units, markup);

        double loyaltyAfter = Loyalty.afterSale(data.loyalty(), quality, floor, cut);
        villager.setData(ModAttachments.NPC.get(), data.withLoyalty(loyaltyAfter));

        held.consume(units, player);
        market.consume(wants, units);
        level.setData(ModAttachments.MARKET.get(), market);

        Purse.pay(player, pence, false);
        SalesLedger.record(player, units, pence);
        // Sick customers talk: cut goods are noticed as if there were twice as many of them.
        // On your own corner the street looks the other way a little.
        Watch.noticed(player, ModComponents.cutOf(held) > 0 ? units * 2 : units, false,
                ModComponents.strainOf(held).subtletyFactor() * Turf.suspicionFactor(ownCorner));

        String key = cut ? "message.slumdrugs.street_sale_cut" : quality < floor ? "message.slumdrugs.street_sale_poor"
                : Loyalty.lost(loyaltyAfter) ? "message.slumdrugs.street_sale_last" : "message.slumdrugs.street_sale";
        ProductItem.actionBar(player, Component.translatable(key,
                villager.getName(), Coin.format(pence), units, Component.translatable("item.slumdrugs.product_" + wants)));
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1.0f);
    }
}
