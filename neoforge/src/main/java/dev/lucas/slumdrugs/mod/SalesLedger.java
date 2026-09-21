package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Sealing;
import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.player.Progression;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

/**
 * Counts what a player sells through the merchant screen into their {@link Progression}.
 * Only the mod's goods count as units, and only emeralds count as coin, so buying seed from
 * the trader moves nothing.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class SalesLedger {

    private SalesLedger() {}

    @SubscribeEvent
    public static void traded(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MerchantOffer offer = event.getMerchantOffer();

        int units = unitsIn(offer.getItemCostA()) + offer.getItemCostB().map(SalesLedger::unitsIn).orElse(0);
        long pence = Purse.valueOf(offer.getResult());
        if (units == 0) return;

        // What the broker takes, the street has seen: it comes out of demand like any sale.
        String drug = drugIn(offer.getItemCostA());
        if (drug != null && player.level() instanceof ServerLevel level) {
            var market = Market.of(level);
            market.consume(drug, units);
            level.setData(ModAttachments.MARKET.get(), market);
        }
        record(player, units, pence);
        Watch.noticed(player, units, ModItems.drugOf("package_", offer.getItemCostA().itemStack()) != null,
                ModComponents.strainOf(offer.getItemCostA().itemStack()).subtletyFactor());
    }

    /** Counts a sale, however it was made, in whole shillings, and tells the player when it moved them up. */
    public static void record(ServerPlayer player, int units, long pence) {
        Progression progress = player.getData(ModAttachments.PROGRESSION.get());
        Progression.Settings gates = Tuning.progression();
        Progression.Tier before = progress.tier(gates);
        progress.sold(units, (int) (pence / Coin.SHILLING));
        player.syncData(ModAttachments.PROGRESSION.get());

        Progression.Tier after = progress.tier(gates);
        if (after != before)
            player.sendSystemMessage(Component.translatable("message.slumdrugs.tier_reached", JournalItem.tierName(after))
                    .withStyle(s -> s.withColor(0xE0B040)));
        Advancements.tiers(player, after);
    }

    /** Units of our goods in one side of a cost: loose product by the unit, parcels by the parcelful. */
    private static int unitsIn(ItemCost cost) {
        ItemStack stack = cost.itemStack();
        if (ModItems.drugOf("product_", stack) != null) return cost.count();
        if (ModItems.drugOf("package_", stack) != null) return cost.count() * Sealing.UNITS_PER_PARCEL;
        return 0;
    }

    private static String drugIn(ItemCost cost) {
        String drug = ModItems.drugOf("product_", cost.itemStack());
        return drug != null ? drug : ModItems.drugOf("package_", cost.itemStack());
    }
}
