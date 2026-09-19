package dev.lucas.slumdrugs.mod;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Map;

/**
 * What each invented substance does. The numbers are carried over from the plugin's
 * drugs.yml; the effects are ordinary vanilla mob effects, scaled by how much of the dose
 * actually landed. Nothing here corresponds to anything real.
 *
 * <p>Data-driven definitions are the eventual home for this (the GDD's datapack plan); this
 * table exists so the loop can be played before that lands.
 */
public final class Substances {

    /** One effect and how strongly it comes on. */
    public record Effect(Holder<MobEffect> effect, int amplifier) {}

    /**
     * @param basePrice      shillings per unit at Standard quality, from the plugin's drugs.yml
     * @param dose           intoxication a full unit adds
     * @param durationTicks  how long the effects last at full strength
     * @param toleranceGain  added to tolerance per unit used
     * @param dependenceGain added to dependence per unit used
     */
    public record Profile(int basePrice, double dose, int durationTicks, double toleranceGain,
                          double dependenceGain, List<Effect> effects) {}

    private static final Map<String, Profile> PROFILES = Map.of(
            "sunleaf", new Profile(10, 20, 1200, 3, 1.5, List.of(
                    new Effect(MobEffects.REGENERATION, 0), new Effect(MobEffects.LUCK, 0))),
            "frostroot", new Profile(28, 30, 900, 6, 4, List.of(
                    new Effect(MobEffects.SPEED, 1), new Effect(MobEffects.HASTE, 1),
                    new Effect(MobEffects.STRENGTH, 0))),
            "emberbloom", new Profile(45, 40, 1000, 7, 6, List.of(
                    new Effect(MobEffects.REGENERATION, 1), new Effect(MobEffects.RESISTANCE, 0),
                    new Effect(MobEffects.SLOWNESS, 0))),
            "glowcap", new Profile(18, 30, 1400, 3, 1, List.of(
                    new Effect(MobEffects.NIGHT_VISION, 0), new Effect(MobEffects.JUMP_BOOST, 1))),
            "sparkshard", new Profile(35, 35, 800, 8, 7, List.of(
                    new Effect(MobEffects.HASTE, 2), new Effect(MobEffects.SPEED, 0),
                    new Effect(MobEffects.STRENGTH, 1), new Effect(MobEffects.HUNGER, 1))));

    private Substances() {}

    public static Profile profile(String drug) {
        Profile profile = PROFILES.get(drug);
        if (profile == null) throw new IllegalArgumentException("No such substance: " + drug);
        return profile;
    }

    /**
     * Effects for a dose that landed at {@code strength} of full. A blunted dose runs shorter,
     * which is how tolerance is felt rather than merely tracked.
     */
    public static List<MobEffectInstance> instances(Profile profile, double strength) {
        double scale = Math.max(0.2, Math.min(1.5, strength));
        int duration = Math.max(20, (int) (profile.durationTicks() * scale));
        return profile.effects().stream()
                .map(e -> new MobEffectInstance(e.effect(), duration, e.amplifier()))
                .toList();
    }
}
