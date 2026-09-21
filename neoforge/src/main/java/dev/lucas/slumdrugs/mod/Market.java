package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Quality;
import dev.lucas.slumdrugs.sim.economy.MarketState;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The level's demand, ticked once a world minute, and the one conversion from the rules'
 * shillings to the game's emeralds. Every price in the mod goes through here, so flooding
 * the street with sunleaf is felt at the broker and the customer alike.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Market {

    /** Shillings per emerald. The design's counting house rate, until coin exists as items. */
    public static final double SHILLINGS_PER_EMERALD = 10;

    private static final int MINUTE = 20 * 60;

    private Market() {}

    public static MarketState of(ServerLevel level) {
        return level.getData(ModAttachments.MARKET.get());
    }

    /** Emeralds for a lot of units, at a quality, with a buyer's own markup. Never below one. */
    public static int emeralds(ServerLevel level, String drug, int quality, int units, double markup) {
        double perUnit = of(level).unitPrice(drug, Substances.profile(drug).basePrice(), quality, 0, 0);
        return Math.max(1, (int) Math.round(units * perUnit * markup / SHILLINGS_PER_EMERALD));
    }

    /** The merchant screen's fixed price: standard quality, today's demand. */
    public static int standardEmeralds(ServerLevel level, String drug, int units, double markup) {
        return emeralds(level, drug, 50, units, markup);
    }

    /** A price with no demand in it, for a level we cannot see; standard quality. */
    public static int flatEmeralds(String drug, int units, double markup) {
        double perUnit = Substances.profile(drug).basePrice() * Quality.priceFactor(50);
        return Math.max(1, (int) Math.round(units * perUnit * markup / SHILLINGS_PER_EMERALD));
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % MINUTE != 0) return;
        // Crew reputation is not tracked yet, so rivals drain at their full rate.
        of(level).tickMinute(ModItems.SUBSTANCES, 0);
        level.setData(ModAttachments.MARKET.get(), of(level));
    }
}
