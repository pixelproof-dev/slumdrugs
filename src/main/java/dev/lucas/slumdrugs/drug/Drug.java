package dev.lucas.slumdrugs.drug;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Set;

/** Immutable definition of one fictional substance, loaded from drugs.yml. */
public final class Drug {

    public record EffectSpec(PotionEffectType type, int amplifier) {}

    /** One bench-recipe input: either a plugin item (kind + drug) or a plain material. */
    public record Ingredient(String kind, String drug, Material material, int amount) {
        public boolean isPluginItem() { return kind != null; }
    }

    public static final class Crop {
        public Material block;
        public Material soil;
        public Material seedMaterial;
        public String seedName;
        public Material rawMaterial;
        public String rawName;
        public Material driedMaterial;
        public String driedName;
        public double growthMinutes;
        public int yield;
        public Set<String> preferredBiomes;
        public double tempMin;
        public double tempMax;
        public int lightMin;
        public int drySeconds;
    }

    public static final class Product {
        public Material material;
        public String name;
        public List<Ingredient> inputs;
        public int output;
    }

    public final String id;
    public final String name;
    public final String color;
    public final double basePrice;
    public final double dose;
    public final int durationTicks;
    public final double toleranceGain;
    public final double dependenceGain;
    public final int withdrawalDelayMinutes;
    public final List<EffectSpec> effects;
    public final List<EffectSpec> withdrawalEffects;
    public final boolean hallucinations;
    public final Crop crop;
    public final Product product;

    public Drug(String id, String name, String color, double basePrice, double dose, int durationTicks,
                double toleranceGain, double dependenceGain, int withdrawalDelayMinutes,
                List<EffectSpec> effects, List<EffectSpec> withdrawalEffects, boolean hallucinations,
                Crop crop, Product product) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.basePrice = basePrice;
        this.dose = dose;
        this.durationTicks = durationTicks;
        this.toleranceGain = toleranceGain;
        this.dependenceGain = dependenceGain;
        this.withdrawalDelayMinutes = withdrawalDelayMinutes;
        this.effects = effects;
        this.withdrawalEffects = withdrawalEffects;
        this.hallucinations = hallucinations;
        this.crop = crop;
        this.product = product;
    }

    public boolean isGrown() { return crop != null; }

    /** Name wrapped in the drug's colour tag for MiniMessage. */
    public String colored() { return "<" + color + ">" + name + "</" + color + ">"; }
}
