package dev.lucas.slumdrugs.economy;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Money. Uses Vault when present, otherwise the plugin's own wallet stored in PlayerData. */
public final class EconomyService {

    private final SlumDrugsPlugin plugin;
    private Economy vault;

    public EconomyService(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        vault = null;
        if (!plugin.getConfig().getBoolean("economy.use-vault", true)) return;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        try {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                vault = rsp.getProvider();
                plugin.getLogger().info("Hooked Vault economy: " + vault.getName());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Vault present but no economy provider; using built-in wallet.");
        }
    }

    public boolean usingVault() { return vault != null; }

    public double balance(Player p) {
        if (vault != null) return vault.getBalance(p);
        return plugin.players().get(p).balance;
    }

    public boolean withdraw(Player p, double amount) {
        if (amount <= 0) return true;
        if (vault != null) return vault.withdrawPlayer(p, amount).transactionSuccess();
        var d = plugin.players().get(p);
        if (d.balance < amount) return false;
        d.balance -= amount;
        return true;
    }

    public void deposit(Player p, double amount) {
        if (amount <= 0) return;
        if (vault != null) {
            vault.depositPlayer(p, amount);
            return;
        }
        plugin.players().get(p).balance += amount;
    }
}
