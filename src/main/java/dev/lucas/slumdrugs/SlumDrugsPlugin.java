package dev.lucas.slumdrugs;

import dev.lucas.slumdrugs.command.DrugsCommand;
import dev.lucas.slumdrugs.crime.HeatManager;
import dev.lucas.slumdrugs.drug.DrugRegistry;
import dev.lucas.slumdrugs.drug.Items;
import dev.lucas.slumdrugs.economy.EconomyService;
import dev.lucas.slumdrugs.economy.Market;
import dev.lucas.slumdrugs.effect.EffectsManager;
import dev.lucas.slumdrugs.farm.FarmManager;
import dev.lucas.slumdrugs.listener.FarmListener;
import dev.lucas.slumdrugs.listener.MenuListener;
import dev.lucas.slumdrugs.listener.NpcListener;
import dev.lucas.slumdrugs.listener.PlayerListener;
import dev.lucas.slumdrugs.listener.StationListener;
import dev.lucas.slumdrugs.npc.NpcManager;
import dev.lucas.slumdrugs.player.ConditionManager;
import dev.lucas.slumdrugs.player.PlayerDataStore;
import dev.lucas.slumdrugs.station.StationManager;
import dev.lucas.slumdrugs.world.District;
import dev.lucas.slumdrugs.world.DistrictPopulator;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SlumDrugsPlugin extends JavaPlugin {

    private Keys keys;
    private Items items;
    private DrugRegistry drugs;
    private PlayerDataStore players;
    private EconomyService economy;
    private Market market;
    private ConditionManager condition;
    private EffectsManager effects;
    private FarmManager farms;
    private StationManager stations;
    private NpcManager npcs;
    private HeatManager heat;
    private District district;
    private DistrictPopulator populator;
    private dev.lucas.slumdrugs.pack.JoinResourcePack joinPack;
    private dev.lucas.slumdrugs.world.VillageTakeover takeover;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        keys = new Keys(this);
        items = new Items(keys);
        drugs = new DrugRegistry(this);
        players = new PlayerDataStore(this);
        economy = new EconomyService(this);
        market = new Market(this);
        effects = new EffectsManager(this, keys);
        condition = new ConditionManager(this);
        farms = new FarmManager(this);
        stations = new StationManager(this);
        npcs = new NpcManager(this, keys);
        heat = new HeatManager(this);
        district = new District(this, keys);
        populator = new DistrictPopulator(this, district, npcs);
        takeover = new dev.lucas.slumdrugs.world.VillageTakeover(this);

        drugs.load();
        drugs.registerRecipes();
        economy.hook();
        market.load();
        district.load();
        npcs.buildProfiles(district.exists() ? district.seed() : 1234567L);
        farms.load();
        stations.load();

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new FarmListener(this), this);
        Bukkit.getPluginManager().registerEvents(new StationListener(this, keys), this);
        Bukkit.getPluginManager().registerEvents(new NpcListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);

        Bukkit.getPluginManager().registerEvents(takeover, this);
        Bukkit.getPluginManager().registerEvents(takeover.safety(), this);

        joinPack = new dev.lucas.slumdrugs.pack.JoinResourcePack(this);
        Bukkit.getPluginManager().registerEvents(joinPack, this);

        DrugsCommand command = new DrugsCommand(this);
        var cmd = getCommand("drugs");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        condition.start();
        farms.start();
        stations.start();
        npcs.start();
        heat.start();
        takeover.start();

        Bukkit.getScheduler().runTaskTimer(this, this::autosave, 20L * 300L, 20L * 300L);
        getLogger().info("SlumDrugs enabled.");
    }

    @Override
    public void onDisable() {
        takeover.stop();
        condition.stop();
        farms.stop();
        stations.stop();
        npcs.stop();
        heat.stop();
        effects.clearAll();
        drugs.unregisterRecipes();
        autosave();
        getLogger().info("SlumDrugs disabled.");
    }

    private void autosave() {
        players.saveAll();
        farms.save();
        stations.save();
        market.save();
        district.save();
    }

    /** Reloads config and drug definitions without restarting the server. */
    public void reloadAll() {
        reloadConfig();
        joinPack.reload();
        drugs.load();
        drugs.registerRecipes();
        economy.hook();
    }

    public Keys keys() { return keys; }
    public Items items() { return items; }
    public DrugRegistry drugs() { return drugs; }
    public PlayerDataStore players() { return players; }
    public EconomyService economy() { return economy; }
    public Market market() { return market; }
    public ConditionManager condition() { return condition; }
    public EffectsManager effects() { return effects; }
    public FarmManager farms() { return farms; }
    public StationManager stations() { return stations; }
    public NpcManager npcs() { return npcs; }
    public HeatManager heat() { return heat; }
    public dev.lucas.slumdrugs.world.VillageTakeover takeover() { return takeover; }
    public District district() { return district; }
    public DistrictPopulator populator() { return populator; }
}
