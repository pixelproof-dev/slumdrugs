package dev.lucas.slumdrugs.drug;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.station.StationType;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Loads drugs.yml and registers the few vanilla crafting recipes (furniture and fertilizer). */
public final class DrugRegistry {

    private final SlumDrugsPlugin plugin;
    private final Map<String, Drug> drugs = new LinkedHashMap<>();
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    public DrugRegistry(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
    }

    public Collection<Drug> all() { return drugs.values(); }

    public Drug get(String id) { return id == null ? null : drugs.get(id.toLowerCase(Locale.ROOT)); }

    public List<Drug> grown() {
        List<Drug> out = new ArrayList<>();
        for (Drug d : drugs.values()) if (d.isGrown()) out.add(d);
        return out;
    }

    /** Finds the drug whose crop block matches, or null. */
    public Drug byCropBlock(Material block) {
        for (Drug d : drugs.values()) if (d.isGrown() && d.crop.block == block) return d;
        return null;
    }

    public void load() {
        drugs.clear();
        File file = new File(plugin.getDataFolder(), "drugs.yml");
        if (!file.exists()) plugin.saveResource("drugs.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("drugs");
        if (root == null) {
            plugin.getLogger().warning("drugs.yml has no 'drugs' section");
            return;
        }
        for (String id : root.getKeys(false)) {
            try {
                Drug d = parse(id.toLowerCase(Locale.ROOT), root.getConfigurationSection(id));
                drugs.put(d.id, d);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping drug '" + id + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + drugs.size() + " substances: " + String.join(", ", drugs.keySet()));
    }

    private Drug parse(String id, ConfigurationSection s) {
        String name = s.getString("name", id);
        String color = s.getString("color", "white");
        double price = s.getDouble("base-price", 10);
        double dose = s.getDouble("dose", 25);
        int duration = s.getInt("duration", 1200);
        double tol = s.getDouble("tolerance-gain", 4);
        double dep = s.getDouble("dependence-gain", 2);
        int wd = s.getInt("withdrawal-delay-minutes", 20);
        List<Drug.EffectSpec> effects = effects(s.getStringList("effects"));
        List<Drug.EffectSpec> wdEffects = effects(s.getStringList("withdrawal-effects"));
        boolean hallu = s.getBoolean("hallucinations", false);

        Drug.Crop crop = null;
        ConfigurationSection c = s.getConfigurationSection("crop");
        if (c != null) {
            crop = new Drug.Crop();
            crop.block = mat(c.getString("block"), "crop.block");
            crop.soil = mat(c.getString("soil", "FARMLAND"), "crop.soil");
            crop.seedMaterial = mat(c.getString("seed-material"), "crop.seed-material");
            crop.seedName = c.getString("seed-name", name + " Seeds");
            crop.rawMaterial = mat(c.getString("raw-material"), "crop.raw-material");
            crop.rawName = c.getString("raw-name", "Raw " + name);
            crop.driedMaterial = mat(c.getString("dried-material"), "crop.dried-material");
            crop.driedName = c.getString("dried-name", "Dried " + name);
            crop.growthMinutes = c.getDouble("growth-minutes", 12);
            crop.yield = c.getInt("yield", 2);
            Set<String> biomes = new HashSet<>();
            for (String b : c.getStringList("preferred-biomes")) biomes.add(b.toLowerCase(Locale.ROOT));
            crop.preferredBiomes = biomes;
            List<Double> t = c.getDoubleList("temperature");
            crop.tempMin = t.size() > 0 ? t.get(0) : 0.3;
            crop.tempMax = t.size() > 1 ? t.get(1) : 1.2;
            crop.lightMin = c.getInt("light-min", 9);
            crop.drySeconds = c.getInt("dry-seconds", 90);
        }

        ConfigurationSection p = s.getConfigurationSection("product");
        if (p == null) throw new IllegalArgumentException("missing product section");
        Drug.Product product = new Drug.Product();
        product.material = mat(p.getString("material"), "product.material");
        product.name = p.getString("name", name);
        product.output = p.getInt("output", 1);
        List<Drug.Ingredient> inputs = new ArrayList<>();
        for (String line : p.getStringList("inputs")) inputs.add(ingredient(line));
        if (inputs.isEmpty()) throw new IllegalArgumentException("product has no inputs");
        product.inputs = inputs;

        return new Drug(id, name, color, price, dose, duration, tol, dep, wd, effects, wdEffects, hallu, crop, product);
    }

    /** "dried:sunleaf:2" (plugin item) or "PAPER:1" (plain material). */
    private Drug.Ingredient ingredient(String line) {
        String[] parts = line.split(":");
        if (parts.length == 3) {
            return new Drug.Ingredient(parts[0].toLowerCase(Locale.ROOT), parts[1].toLowerCase(Locale.ROOT), null, Integer.parseInt(parts[2].trim()));
        }
        if (parts.length == 2) {
            return new Drug.Ingredient(null, null, mat(parts[0], "input"), Integer.parseInt(parts[1].trim()));
        }
        throw new IllegalArgumentException("bad ingredient '" + line + "'");
    }

    private static Material mat(String name, String field) {
        if (name == null) throw new IllegalArgumentException("missing " + field);
        Material m = Material.matchMaterial(name);
        if (m == null) throw new IllegalArgumentException("unknown material '" + name + "' in " + field);
        return m;
    }

    private List<Drug.EffectSpec> effects(List<String> lines) {
        List<Drug.EffectSpec> out = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split(":");
            String key = parts[0].trim().toLowerCase(Locale.ROOT);
            int amp = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            PotionEffectType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(NamespacedKey.minecraft(key));
            if (type == null) {
                plugin.getLogger().warning("Unknown effect '" + key + "'");
                continue;
            }
            out.add(new Drug.EffectSpec(type, amp));
        }
        return out;
    }

    // ------------------------------------------------------------------ vanilla recipes

    public void registerRecipes() {
        unregisterRecipes();
        Items items = plugin.items();

        ShapelessRecipe fert = new ShapelessRecipe(key("fertilizer"), items.fertilizer(2));
        fert.addIngredient(2, Material.BONE_MEAL);
        fert.addIngredient(Material.ROTTEN_FLESH);
        fert.addIngredient(Material.DIRT);
        add(fert);

        ShapelessRecipe rack = new ShapelessRecipe(key("drying_rack"), items.station(StationType.DRYING_RACK, 1));
        rack.addIngredient(Material.SMOKER);
        rack.addIngredient(2, Material.STICK);
        add(rack);

        ShapelessRecipe bench = new ShapelessRecipe(key("processing_bench"), items.station(StationType.PROCESSING_BENCH, 1));
        bench.addIngredient(Material.BARREL);
        bench.addIngredient(Material.IRON_INGOT);
        bench.addIngredient(Material.GLASS_BOTTLE);
        add(bench);

        ShapelessRecipe pack = new ShapelessRecipe(key("packaging_station"), items.station(StationType.PACKAGING_STATION, 1));
        pack.addIngredient(Material.CRAFTER);
        pack.addIngredient(2, Material.PAPER);
        add(pack);

        ShapelessRecipe crate = new ShapelessRecipe(key("storage_crate"), items.station(StationType.STORAGE_CRATE, 1));
        crate.addIngredient(Material.CHEST);
        crate.addIngredient(4, Material.IRON_BARS);
        add(crate);
        ShapelessRecipe growbox = new ShapelessRecipe(key("growbox"),items.station(StationType.GROWBOX,1));
        growbox.addIngredient(Material.DISPENSER);
        growbox.addIngredient(2,Material.GLASS);
        growbox.addIngredient(Material.SEA_LANTERN);
        growbox.addIngredient(Material.DIRT);
        add(growbox);
    }

    private void add(ShapelessRecipe r) {
        Bukkit.addRecipe(r);
        recipeKeys.add(r.getKey());
    }

    public void unregisterRecipes() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k);
        recipeKeys.clear();
    }

    private NamespacedKey key(String s) {
        return new NamespacedKey(plugin, s);
    }
}
