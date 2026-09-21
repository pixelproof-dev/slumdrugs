package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Quality;
import dev.lucas.slumdrugs.sim.drug.Sealing;
import dev.lucas.slumdrugs.sim.npc.Npc;
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
 * cannot read the quality of what a player hands over. Quality-sensitive selling — the
 * customer who pays more for a good batch — needs its own flow and is not this.
 */
public final class NpcTrades {

    private static final int MAX_USES = 12;

    private NpcTrades() {}

    /** Emeralds for a unit at Standard quality, floored at one. */
    private static int price(String drug, double multiplier) {
        int base = Substances.profile(drug).basePrice();
        return Math.max(1, (int) Math.round(base * Quality.priceFactor(50) * multiplier / 10.0));
    }

    public static void fill(Villager villager, Npc.Role role) {
        var offers = villager.getOffers();
        offers.clear();

        switch (role) {
            case TRADER -> {
                // Seed and supplies, the honest end of the street.
                for (String drug : ModItems.CROPS)
                    offers.add(sell(ModItems.get("seed_" + drug).get(), 1, price(drug, 1.5)));
                offers.add(sell(ModItems.get("fertilizer").get(), 1, 2));
                for (String drug : ModItems.CROPS)
                    offers.add(buy(ModItems.get("raw_" + drug).get(), 4, price(drug, 0.8)));
            }
            case BROKER -> {
                // Buys finished goods at a cut, sells what the shops will not stock. A sealed
                // parcel is the wholesale unit and pays better per unit than loose goods: the
                // seal is what the broker is paying for, since it names who to blame.
                for (String drug : ModItems.SUBSTANCES)
                    offers.add(buy(ModItems.get("product_" + drug).get(), 1, price(drug, 1.2)));
                for (String drug : ModItems.SUBSTANCES)
                    offers.add(buy(ModItems.get("package_" + drug).get(), 1,
                            price(drug, 1.4 * Sealing.UNITS_PER_PARCEL)));
                offers.add(sell(Items.CHARCOAL, 4, 1));
                offers.add(sell(ModItems.get("seed_sunleaf").get(), 1, price("sunleaf", 2.5)));
            }
            case HEALER -> offers.add(sell(ModItems.get("remedy").get(), 1, 5));
            case CUSTOMER -> {
                // A regular: takes one substance, pays over the odds for it.
                String drug = ModItems.SUBSTANCES.get(
                        Math.floorMod(villager.getUUID().hashCode(), ModItems.SUBSTANCES.size()));
                offers.add(buy(ModItems.get("product_" + drug).get(), 1, price(drug, 1.6)));
            }
            default -> { /* residents, constables and crew do not keep a stall */ }
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
