package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Quality;
import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.economy.MarketState;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The level's demand, ticked once a world minute, and the one place a substance's base price
 * becomes pence. Every price in the mod goes through here, so flooding the street with
 * sunleaf is felt at the broker and the customer alike.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Market {

    private static final int MINUTE = 20 * 60;

    private Market() {}

    /** The market's tuning, or the sim's defaults before the config has loaded. */
    public static MarketState.Settings settings() {
        return Tuning.loaded() ? Tuning.market() : MarketState.Settings.defaults();
    }

    private static double pencePerPoint() {
        return Tuning.loaded() ? Tuning.PENCE_PER_POINT.get() : Coin.PENCE_PER_BASE_POINT;
    }

    public static MarketState of(ServerLevel level) {
        return level.getData(ModAttachments.MARKET.get());
    }

    /** Pence for a lot of units, at a quality, with a buyer's own markup. Never below a penny. */
    public static long pence(ServerLevel level, String drug, int quality, int units, double markup) {
        double perUnit = of(level).unitPrice(drug, Coin.baseUnitPence(Substances.profile(drug).basePrice(), pencePerPoint()), quality, 0, 0);
        return Math.max(1, Math.round(units * perUnit * markup));
    }

    /** The same for a particular stack, whose line's potency is part of what it is worth. */
    public static long pence(ServerLevel level, String drug, net.minecraft.world.item.ItemStack stack, int units, double markup) {
        return pence(level, drug, ModComponents.qualityOf(stack), units, markup * ModComponents.strainOf(stack).potencyFactor());
    }

    /** The merchant screen's fixed price: standard quality, today's demand. */
    public static long standardPence(ServerLevel level, String drug, int units, double markup) {
        return pence(level, drug, 50, units, markup);
    }

    /** A price with no demand in it, for a level we cannot see; standard quality. */
    public static long flatPence(String drug, int units, double markup) {
        double perUnit = Coin.baseUnitPence(Substances.profile(drug).basePrice(), pencePerPoint()) * Quality.priceFactor(50);
        return Math.max(1, Math.round(units * perUnit * markup));
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
