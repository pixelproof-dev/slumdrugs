package dev.lucas.slumdrugs.sim.economy;

import dev.lucas.slumdrugs.sim.drug.Quality;

import java.util.HashMap;
import java.util.Map;

/**
 * Local demand. Each substance has a pool of units the settlement will absorb; sales drain it,
 * time refills it, and rival suppliers drain it too. Prices follow the pool level.
 *
 * <p>Platform-free on purpose: no config lookup, no file I/O. The platform layer supplies
 * {@link Settings} and persists {@link #snapshot()}.
 */
public final class MarketState {

    /** Tuning, validated once instead of re-read from a config on every call. */
    public record Settings(double demandMax, double refillPerMinute, double rivalDrainPerMinute) {
        public Settings {
            if (!(demandMax > 0)) throw new IllegalArgumentException("demandMax must be positive");
            if (refillPerMinute < 0 || rivalDrainPerMinute < 0)
                throw new IllegalArgumentException("Rates must not be negative");
        }
        public static Settings defaults() { return new Settings(64, 2.0, 0.6); }
    }

    private final Settings settings;
    private final Map<String, Double> demand = new HashMap<>();

    public MarketState(Settings settings) { this.settings = settings; }

    public Settings settings() { return settings; }

    /** A substance nobody has traded yet starts three-quarters open. */
    public double demand(String drugId) {
        return demand.computeIfAbsent(drugId, k -> settings.demandMax() * 0.75);
    }

    /** 0..1 share of the demand pool that is still open. */
    public double ratio(String drugId) {
        return Math.max(0, Math.min(1, demand(drugId) / settings.demandMax()));
    }

    /** Multiplier from demand: 0.4 when flooded, 1.2 when starved for supply. */
    public double demandFactor(String drugId) {
        return 0.4 + 0.8 * Math.sqrt(ratio(drugId));
    }

    /** Price per unit for a specific buyer and seller. Never drops below one coin. */
    public double unitPrice(String drugId, double basePrice, int quality, double sellerTraderRep, int buyerTrust) {
        double p = basePrice * Quality.priceFactor(quality) * demandFactor(drugId);
        p *= 1.0 + sellerTraderRep / 400.0;
        p *= 1.0 + Math.max(0, buyerTrust) / 300.0;
        return Math.max(1, p);
    }

    public void consume(String drugId, int units) {
        demand.put(drugId, Math.max(0, demand(drugId) - units));
    }

    /** Called once per simulated minute. Crew reputation of active sellers reduces rival pressure. */
    public void tickMinute(Iterable<String> drugIds, double avgCrewRep) {
        double rival = settings.rivalDrainPerMinute() * (1.0 - Math.max(0, avgCrewRep) / 150.0);
        for (String id : drugIds) {
            double v = demand(id) + settings.refillPerMinute() - rival;
            demand.put(id, Math.max(0, Math.min(settings.demandMax(), v)));
        }
    }

    public Map<String, Double> snapshot() { return Map.copyOf(demand); }

    public void restore(Map<String, Double> saved) {
        demand.clear();
        saved.forEach((k, v) -> demand.put(k, Math.max(0, Math.min(settings.demandMax(), v))));
    }
}
