package dev.lucas.slumdrugs.economy;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.drug.Quality;
import dev.lucas.slumdrugs.player.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Local demand. Each drug has a pool of units the district will absorb; sales drain it,
 * time refills it, and competing suppliers drain it too. Prices follow the pool level.
 */
public final class Market {

    private final SlumDrugsPlugin plugin;
    private final Map<String, Double> demand = new HashMap<>();
    private final File file;

    public Market(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market.yml");
    }

    public double max() { return plugin.getConfig().getDouble("market.demand-max", 64); }

    public double demand(Drug d) {
        return demand.computeIfAbsent(d.id, k -> max() * 0.75);
    }

    /** 0..1 share of the demand pool that is still open. */
    public double ratio(Drug d) {
        return Math.max(0, Math.min(1, demand(d) / max()));
    }

    /** Multiplier from demand: 0.4 when flooded, 1.2 when starved for supply. */
    public double demandFactor(Drug d) {
        return 0.4 + 0.8 * Math.sqrt(ratio(d));
    }

    /** Price per unit for a specific buyer and seller. */
    public double unitPrice(Drug d, int quality, PlayerData seller, int trust) {
        double p = d.basePrice * Quality.priceFactor(quality) * demandFactor(d);
        p *= 1.0 + seller.rep("traders") / 400.0;
        p *= 1.0 + Math.max(0, trust) / 300.0;
        return Math.max(1, p);
    }

    public void consume(Drug d, int units) {
        demand.put(d.id, Math.max(0, demand(d) - units));
    }

    /** Called once per minute. Gang reputation of active sellers is averaged to reduce rival pressure. */
    public void tickMinute(double avgGangRep) {
        double refill = plugin.getConfig().getDouble("market.demand-refill-per-minute", 2.0);
        double rival = plugin.getConfig().getDouble("market.rival-drain-per-minute", 0.6) * (1.0 - Math.max(0, avgGangRep) / 150.0);
        for (Drug d : plugin.drugs().all()) {
            double v = demand(d) + refill - rival;
            demand.put(d.id, Math.max(0, Math.min(max(), v)));
        }
    }

    public void load() {
        demand.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) demand.put(k, y.getDouble(k));
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Double> e : demand.entrySet()) y.set(e.getKey(), e.getValue());
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save market.yml: " + ex.getMessage());
        }
    }
}
