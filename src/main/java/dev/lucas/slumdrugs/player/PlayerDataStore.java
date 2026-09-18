package dev.lucas.slumdrugs.player;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Loads and saves PlayerData as YAML. Data is cached while the player is online. */
public final class PlayerDataStore {

    private final SlumDrugsPlugin plugin;
    private final Map<UUID, PlayerData> cache = new HashMap<>();
    private final File dir;

    public PlayerDataStore(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "players");
        dir.mkdirs();
    }

    public PlayerData get(Player p) {
        PlayerData d = get(p.getUniqueId());
        d.name = p.getName();
        return d;
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    public Collection<PlayerData> loaded() {
        return cache.values();
    }

    public void unload(UUID uuid) {
        PlayerData d = cache.remove(uuid);
        if (d != null) save(d);
    }

    public void saveAll() {
        for (PlayerData d : cache.values()) save(d);
    }

    private PlayerData load(UUID uuid) {
        PlayerData d = new PlayerData(uuid);
        d.balance = plugin.getConfig().getDouble("economy.starting-balance", 50);
        File f = new File(dir, uuid + ".yml");
        if (!f.exists()) return d;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        d.name = y.getString("name", "");
        d.balance = y.getDouble("balance", d.balance);
        d.heat = y.getDouble("heat", 0);
        d.lastFullSleep = y.getLong("last-full-sleep", 0);
        d.lastRecoveryAt = y.getLong("last-recovery-at", f.lastModified());
        d.withdrawalSuppressedUntil = y.getLong("withdrawal-suppressed-until", 0);
        d.lastCravingPrompt = y.getLong("last-craving-prompt", 0);
        d.lastInspection = y.getLong("last-inspection", 0);
        d.lastRivalOffer = y.getLong("last-rival-offer", 0);
        d.totalSales = y.getInt("total-sales", 0);
        ConfigurationSection rep = y.getConfigurationSection("reputation");
        if (rep != null) for (String k : rep.getKeys(false)) d.reputation.put(k, rep.getInt(k));
        ConfigurationSection trust = y.getConfigurationSection("trust");
        if (trust != null) for (String k : trust.getKeys(false)) d.trust.put(k, trust.getInt(k));
        ConfigurationSection cond = y.getConfigurationSection("conditions");
        if (cond != null) {
            for (String drug : cond.getKeys(false)) {
                ConfigurationSection c = cond.getConfigurationSection(drug);
                if (c == null) continue;
                PlayerData.Condition x = d.condition(drug);
                x.intoxPeak = c.getDouble("intox-peak");
                x.intoxStart = c.getLong("intox-start");
                x.intoxEnd = c.getLong("intox-end");
                x.tolerance = c.getDouble("tolerance");
                x.dependence = c.getDouble("dependence");
                x.lastUse = c.getLong("last-use");
                x.uses = c.getInt("uses");
            }
        }
        ConfigurationSection con = y.getConfigurationSection("contract");
        if (con != null) {
            PlayerData.Contract k = new PlayerData.Contract();
            k.customerId = con.getString("customer");
            k.customerName = con.getString("customer-name", "?");
            k.drug = con.getString("drug");
            k.units = con.getInt("units");
            k.minQuality = con.getInt("min-quality");
            k.reward = con.getDouble("reward");
            k.deadline = con.getLong("deadline");
            d.contract = k;
        }
        ConfigurationSection del = y.getConfigurationSection("delivery");
        if (del != null) {
            PlayerData.Delivery k = new PlayerData.Delivery();
            k.drug = del.getString("drug");
            k.units = del.getInt("units");
            k.dropBox = del.getString("drop-box");
            k.reward = del.getDouble("reward");
            k.deadline = del.getLong("deadline");
            k.intercepted = del.getBoolean("intercepted", false);
            d.delivery = k;
        }
        ConfigurationSection job = y.getConfigurationSection("job");
        if (job != null) {
            PlayerData.LegalJob k = new PlayerData.LegalJob();
            k.residentName = job.getString("resident", "?");
            k.material = Material.matchMaterial(job.getString("material", "BREAD"));
            if (k.material == null) k.material = Material.BREAD;
            k.amount = job.getInt("amount", 8);
            k.pay = job.getDouble("pay", 10);
            d.job = k;
        }
        plugin.condition().recover(d, System.currentTimeMillis());
        return d;
    }

    public void save(PlayerData d) {
        plugin.condition().recover(d, System.currentTimeMillis());
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", d.name);
        y.set("balance", d.balance);
        y.set("heat", d.heat);
        y.set("last-full-sleep", d.lastFullSleep);
        y.set("last-recovery-at", d.lastRecoveryAt);
        y.set("withdrawal-suppressed-until", d.withdrawalSuppressedUntil);
        y.set("last-craving-prompt", d.lastCravingPrompt);
        y.set("last-inspection", d.lastInspection);
        y.set("last-rival-offer", d.lastRivalOffer);
        y.set("total-sales", d.totalSales);
        for (Map.Entry<String, Integer> e : d.reputation.entrySet()) y.set("reputation." + e.getKey(), e.getValue());
        for (Map.Entry<String, Integer> e : d.trust.entrySet()) y.set("trust." + e.getKey(), e.getValue());
        for (Map.Entry<String, PlayerData.Condition> e : d.conditions.entrySet()) {
            String p = "conditions." + e.getKey() + ".";
            PlayerData.Condition x = e.getValue();
            y.set(p + "intox-peak", x.intoxPeak);
            y.set(p + "intox-start", x.intoxStart);
            y.set(p + "intox-end", x.intoxEnd);
            y.set(p + "tolerance", x.tolerance);
            y.set(p + "dependence", x.dependence);
            y.set(p + "last-use", x.lastUse);
            y.set(p + "uses", x.uses);
        }
        if (d.contract != null) {
            y.set("contract.customer", d.contract.customerId);
            y.set("contract.customer-name", d.contract.customerName);
            y.set("contract.drug", d.contract.drug);
            y.set("contract.units", d.contract.units);
            y.set("contract.min-quality", d.contract.minQuality);
            y.set("contract.reward", d.contract.reward);
            y.set("contract.deadline", d.contract.deadline);
        }
        if (d.delivery != null) {
            y.set("delivery.drug", d.delivery.drug);
            y.set("delivery.units", d.delivery.units);
            y.set("delivery.drop-box", d.delivery.dropBox);
            y.set("delivery.reward", d.delivery.reward);
            y.set("delivery.deadline", d.delivery.deadline);
            y.set("delivery.intercepted", d.delivery.intercepted);
        }
        if (d.job != null) {
            y.set("job.resident", d.job.residentName);
            y.set("job.material", d.job.material.name());
            y.set("job.amount", d.job.amount);
            y.set("job.pay", d.job.pay);
        }
        try {
            y.save(new File(dir, d.uuid + ".yml"));
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save player data for " + d.uuid + ": " + ex.getMessage());
        }
    }
}
