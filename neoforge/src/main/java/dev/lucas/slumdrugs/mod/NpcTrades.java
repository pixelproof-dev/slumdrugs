package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Sealing;
import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.npc.Loyalty;
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

    /** Pence for one unit at Standard quality and today's demand. */
    private static long price(Villager villager, String drug, double markup) {
        return villager.level() instanceof ServerLevel level
                ? Market.standardPence(level, drug, 1, markup)
                : Market.flatPence(drug, 1, markup);
    }

    private static long parcelPrice(Villager villager, String drug, double markup) {
        return villager.level() instanceof ServerLevel level
                ? Market.standardPence(level, drug, Sealing.UNITS_PER_PARCEL, markup)
                : Market.flatPence(drug, Sealing.UNITS_PER_PARCEL, markup);
    }

    /** The quality a customer insists on. Fixed per person too. */
    public static int floor(Villager villager) {
        return Loyalty.floor(villager.getUUID().hashCode() >>> 3);
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
                offers.add(sell(ModItems.get("fertilizer").get(), 1, 2 * Coin.SHILLING));
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
                offers.add(sell(Items.CHARCOAL, 4, Coin.SHILLING));
                offers.add(sell(ModItems.get("seed_sunleaf").get(), 1, price(villager, "sunleaf", 2.5)));
                // The two substances nobody grows come in from elsewhere, dear, until the
                // glassworks and the quarry that make them exist.
                for (String drug : ModItems.SUBSTANCES)
                    if (!ModItems.CROPS.contains(drug))
                        offers.add(sell(ModItems.get("product_" + drug).get(), 1, price(villager, drug, 3.0)));
            }
            case HEALER -> offers.add(sell(ModItems.get("remedy").get(), 1, 5 * Coin.SHILLING));
            default -> { /* customers buy by hand; residents, constables and crew keep no stall */ }
        }
    }

    /** The villager sells: player pays coin, receives goods. */
    private static MerchantOffer sell(net.minecraft.world.item.Item item, int count, long pence) {
        return new MerchantOffer(Purse.cost(pence), Purse.secondCost(pence),
                new ItemStack(item, count), MAX_USES, 2, 0.05f);
    }

    /** The villager buys: player hands over goods, receives loose coin. */
    private static MerchantOffer buy(net.minecraft.world.item.Item item, int count, long pence) {
        return new MerchantOffer(new ItemCost(item, Math.max(1, count)),
                Optional.empty(), Purse.single(pence), MAX_USES, 3, 0.05f);
    }
}
