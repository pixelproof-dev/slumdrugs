package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Sealing;
import dev.lucas.slumdrugs.sim.npc.Npc;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Optional;

/**
 * What each role will trade, through the vanilla merchant screen. Using the game's own trading
 * interface is deliberate: the mod gets a familiar, fully working shop without writing or
 * maintaining a screen of its own.
 *
 * <p>Offers are priced at Standard quality, because a merchant offer is a fixed exchange and
 * cannot read the quality of what a player hands over. They do read demand: the offers are
 * rebuilt every few minutes from the level's market, so a flooded street pays less at the
 * broker too. Quality-sensitive selling is {@link StreetSales}, and customers use nothing else.
 */
public final class NpcTrades {

    private static final int MAX_USES = 12;

    private NpcTrades() {}

    /** Emeralds for one unit at Standard quality and today's demand, floored at one. */
    private static int price(Villager villager, String drug, double markup) {
        return villager.level() instanceof ServerLevel level
                ? Market.standardEmeralds(level, drug, 1, markup)
                : Market.flatEmeralds(drug, 1, markup);
    }

    private static int parcelPrice(Villager villager, String drug, double markup) {
        return villager.level() instanceof ServerLevel level
                ? Market.standardEmeralds(level, drug, Sealing.UNITS_PER_PARCEL, markup)
                : Market.flatEmeralds(drug, Sealing.UNITS_PER_PARCEL, markup);
    }

    /** The one substance a customer takes. Fixed per person, so a regular stays a regular. */
    public static String preferred(Villager villager) {
        return ModItems.SUBSTANCES.get(Math.floorMod(villager.getUUID().hashCode(), ModItems.SUBSTANCES.size()));
    }

    public static void fill(Villager villager, Npc.Role role) {
        var offers = villager.getOffers();
        offers.clear();

        switch (role) {
            case TRADER -> {
                // Seed and supplies, the honest end of the street.
                for (String drug : ModItems.CROPS)
                    offers.add(sell(ModItems.get("seed_" + drug).get(), 1, price(villager, drug, 1.5)));
                offers.add(sell(ModItems.get("fertilizer").get(), 1, 2));
                for (String drug : ModItems.CROPS)
                    offers.add(buy(ModItems.get("raw_" + drug).get(), 4, price(villager, drug, 0.8)));
            }
            case BROKER -> {
                // Buys finished goods at a cut, sells what the shops will not stock. A sealed
                // parcel is the wholesale unit and pays better per unit than loose goods: the
                // seal is what the broker is paying for, since it names who to blame.
                for (String drug : ModItems.SUBSTANCES)
                    offers.add(buy(ModItems.get("product_" + drug).get(), 1, price(villager, drug, 1.2)));
                for (String drug : ModItems.SUBSTANCES)
                    offers.add(buy(ModItems.get("package_" + drug).get(), 1, parcelPrice(villager, drug, 1.4)));
                offers.add(sell(Items.CHARCOAL, 4, 1));
                offers.add(sell(ModItems.get("seed_sunleaf").get(), 1, price(villager, "sunleaf", 2.5)));
                // The two substances nobody grows come in from elsewhere, dear, until the
                // glassworks and the quarry that make them exist.
                for (String drug : ModItems.SUBSTANCES)
                    if (!ModItems.CROPS.contains(drug))
                        offers.add(sell(ModItems.get("product_" + drug).get(), 1, price(villager, drug, 3.0)));
            }
            case HEALER -> offers.add(sell(ModItems.get("remedy").get(), 1, 5));
            default -> { /* customers buy by hand; residents, constables and crew keep no stall */ }
        }
    }

    /** The villager sells: player pays emeralds, receives goods. */
    private static MerchantOffer sell(net.minecraft.world.item.Item item, int count, int emeralds) {
        return new MerchantOffer(new ItemCost(Items.EMERALD, Math.max(1, emeralds)),
                Optional.empty(), new ItemStack(item, count), MAX_USES, 2, 0.05f);
    }

    /** The villager buys: player hands over goods, receives emeralds. */
    private static MerchantOffer buy(net.minecraft.world.item.Item item, int count, int emeralds) {
        return new MerchantOffer(new ItemCost(item, Math.max(1, count)),
                Optional.empty(), new ItemStack(Items.EMERALD, Math.max(1, emeralds)), MAX_USES, 3, 0.05f);
    }
}
