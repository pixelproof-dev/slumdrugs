package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Drying;
import dev.lucas.slumdrugs.sim.drug.Refining;
import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.economy.MarketState;
import dev.lucas.slumdrugs.sim.npc.Standing;
import dev.lucas.slumdrugs.sim.player.Condition;
import dev.lucas.slumdrugs.sim.player.Progression;
import dev.lucas.slumdrugs.sim.player.Suspicion;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every number a server might want to move, in one file the server owner can edit. The
 * defaults are the design's and the sim's; the sim itself never reads this, it is handed
 * the settings records built here. A server config, so clients see the same values.
 */
public final class Tuning {

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ---------------------------------------------------------------- stations
    public static final ModConfigSpec.IntValue DRYING_SECONDS = B.comment("Seconds a bundle hangs before it is dried")
            .push("stations").defineInRange("dryingSeconds", Drying.SECONDS, 1, 36000);
    public static final ModConfigSpec.IntValue STROKE_SECONDS = B.comment("Seconds of a press run one pull of the screw is worth")
            .defineInRange("strokeSeconds", Refining.HAND_STROKE_SECONDS, 1, 600);
    public static final ModConfigSpec.DoubleValue GROWTH_SECONDS = B.comment("Seconds a frame takes to grow a crop, before compost and vigour")
            .defineInRange("growthSeconds", 600.0, 1.0, 86400.0);
    public static final ModConfigSpec.IntValue CUSTOMER_HAND = B.comment("Units a customer takes per sale")
            .defineInRange("customerHand", 4, 1, 64);
    public static final ModConfigSpec.DoubleValue CUSTOMER_MARKUP = B.comment("What a regular pays over the standard price")
            .defineInRange("customerMarkup", 1.6, 0.1, 10.0);

    // ---------------------------------------------------------------- money
    public static final ModConfigSpec.DoubleValue PENCE_PER_POINT = B.pop().comment("Pence per point of a substance's base price; ten points is a Standard unit")
            .push("money").defineInRange("pencePerBasePoint", Coin.PENCE_PER_BASE_POINT, 0.1, 100.0);
    public static final ModConfigSpec.DoubleValue STAMP_CUT = B.comment("Share the counting house keeps for stamping loose coin")
            .defineInRange("stampCut", Coin.STAMP_CUT, 0.0, 0.9);
    public static final ModConfigSpec.IntValue STARTING_PURSE = B.comment("Pence a new player is handed on their first day")
            .defineInRange("startingPursePence", Coin.STARTING_PURSE, 0, 100000);
    public static final ModConfigSpec.IntValue TREATMENT_PENCE = B.comment("Stamped pence the healer asks for a treatment")
            .defineInRange("treatmentPence", 12 * Coin.SHILLING, 0, 100000);
    public static final ModConfigSpec.IntValue BAIL_PENCE = B.comment("Stamped pence that buy a player out of the cell")
            .defineInRange("bailPence", 2 * Coin.SOVEREIGN, 0, 1000000);

    // ---------------------------------------------------------------- market
    public static final ModConfigSpec.DoubleValue DEMAND_MAX = B.pop().comment("Units of one substance a settlement will absorb")
            .push("market").defineInRange("demandMax", 64.0, 1.0, 100000.0);
    public static final ModConfigSpec.DoubleValue DEMAND_REFILL = B.comment("Units of demand that return per world minute")
            .defineInRange("refillPerMinute", 2.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue RIVAL_DRAIN = B.comment("Units of demand rivals take per world minute")
            .defineInRange("rivalDrainPerMinute", 0.6, 0.0, 1000.0);

    // ---------------------------------------------------------------- progression
    public static final ModConfigSpec.IntValue BACKROOM_UNITS = B.pop().comment("Units sold that open the Backroom")
            .push("progression").defineInRange("backroomUnits", Progression.BACKROOM_UNITS, 0, 100000);
    public static final ModConfigSpec.IntValue WORKSHOP_COIN = B.comment("Shillings earned that open the Workshop")
            .defineInRange("workshopShillings", Progression.WORKSHOP_COIN, 0, 1000000);

    // ---------------------------------------------------------------- the watch
    public static final ModConfigSpec.DoubleValue NOTICED_AT = B.pop().comment("Suspicion at which the player is noticed")
            .push("watch").defineInRange("noticedAt", Suspicion.NOTICED_AT, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue WATCHED_AT = B.defineInRange("watchedAt", Suspicion.WATCHED_AT, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue HUNTED_AT = B.defineInRange("huntedAt", Suspicion.HUNTED_AT, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue RAID_AT = B.comment("Suspicion at which the bell rings")
            .defineInRange("raidAt", Suspicion.RAID_AT, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue PER_LOOSE_UNIT = B.comment("Suspicion per unit sold loose")
            .defineInRange("perLooseUnit", Suspicion.PER_LOOSE_UNIT, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue PER_SEALED_UNIT = B.comment("Suspicion per unit sold under seal")
            .defineInRange("perSealedUnit", Suspicion.PER_SEALED_UNIT, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DECAY_PER_MINUTE = B.comment("Suspicion that fades per quiet world minute")
            .defineInRange("decayPerMinute", Suspicion.DECAY_PER_MINUTE, 0.0, 100.0);
    public static final ModConfigSpec.IntValue RAID_WARNING_SECONDS = B.comment("Seconds between the bell and the Watch's arrival")
            .defineInRange("raidWarningSeconds", (int) (Suspicion.RAID_WARNING_MILLIS / 1000), 0, 36000);
    public static final ModConfigSpec.DoubleValue AFTER_RAID = B.comment("Where suspicion lands after a raid")
            .defineInRange("afterRaid", Suspicion.AFTER_RAID, 0.0, 100.0);
    public static final ModConfigSpec.IntValue CELL_SECONDS = B.comment("Seconds a player is held when the Watch takes them in")
            .defineInRange("cellSeconds", 180, 0, 36000);
    public static final ModConfigSpec.DoubleValue BRIBE_PER_SHILLING = B.comment("Suspicion a shilling of bribe takes off a constable's mind")
            .defineInRange("bribePerShilling", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue BRIBE_CAP = B.comment("The most suspicion one bribe can remove")
            .defineInRange("bribeCap", 15.0, 0.0, 100.0);

    // ---------------------------------------------------------------- crews
    public static final ModConfigSpec.DoubleValue HIT_COST = B.pop().comment("Standing lost for hitting one of a crew's own")
            .push("crews").defineInRange("hitCost", -Standing.HIT, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue KILL_COST = B.comment("Standing lost for killing one")
            .defineInRange("killCost", -Standing.KILL, 0.0, 200.0);
    public static final ModConfigSpec.DoubleValue TRIBUTE_CAP = B.comment("The most standing one tribute can buy")
            .defineInRange("tributeCap", Standing.TRIBUTE_CAP, 0.0, 100.0);

    // ---------------------------------------------------------------- condition
    public static final ModConfigSpec.BooleanValue TONIC_MODE = B.pop().comment(
            "Tonic mode: the condition layer becomes tonic fatigue, with no dependence and no withdrawal")
            .push("condition").define("tonicMode", false);
    public static final ModConfigSpec.DoubleValue INTOXICATION_DECAY = B.comment("Intoxication that wears off per minute")
            .defineInRange("intoxicationDecayPerMinute", 4.0, 0.01, 100.0);
    public static final ModConfigSpec.DoubleValue TOLERANCE_DECAY = B.defineInRange("toleranceDecayPerMinute", 0.15, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue DEPENDENCE_DECAY = B.defineInRange("dependenceDecayPerMinute", 0.08, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DEPENDENCE_DELAY = B.comment("Minutes after the last use before dependence starts to fall")
            .defineInRange("dependenceDelayMinutes", 20, 0, 10000);
    public static final ModConfigSpec.IntValue CRAVING_DELAY = B.defineInRange("cravingDelayMinutes", 15, 0, 10000);
    public static final ModConfigSpec.IntValue WITHDRAWAL_DELAY = B.defineInRange("withdrawalDelayMinutes", 30, 0, 10000);
    public static final ModConfigSpec.DoubleValue REST_MULTIPLIER = B.comment("How much faster recovery runs after a night's sleep")
            .defineInRange("restMultiplier", 2.5, 1.0, 20.0);
    public static final ModConfigSpec.DoubleValue REMEDY_DEPENDENCE = B.comment("Dependence a remedy draught takes off")
            .defineInRange("remedyDependence", Condition.REMEDY_DEPENDENCE, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue REMEDY_TOLERANCE = B.defineInRange("remedyTolerance", Condition.REMEDY_TOLERANCE, 0.0, 100.0);
    public static final ModConfigSpec.IntValue REMEDY_MINUTES = B.comment("Minutes a draught holds withdrawal off")
            .defineInRange("remedyMinutes", (int) (Condition.REMEDY_MILLIS / 60000), 0, 10000);
    public static final ModConfigSpec.DoubleValue TREATMENT_DEPENDENCE = B.comment("Dependence the healer's treatment takes off")
            .defineInRange("treatmentDependence", 20.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue CLEAN_STREAK_DAYS = B.comment("Days clean that lower the tolerance ceiling for good")
            .defineInRange("cleanStreakDays", 3, 1, 365);

    public static final ModConfigSpec SPEC = B.pop().build();

    private Tuning() {}

    public static Suspicion.Settings suspicion() {
        return new Suspicion.Settings(NOTICED_AT.get(), WATCHED_AT.get(), HUNTED_AT.get(), RAID_AT.get(),
                PER_LOOSE_UNIT.get(), PER_SEALED_UNIT.get(), DECAY_PER_MINUTE.get(),
                RAID_WARNING_SECONDS.get() * 1000L, AFTER_RAID.get());
    }

    public static Progression.Settings progression() {
        return new Progression.Settings(BACKROOM_UNITS.get(), WORKSHOP_COIN.get());
    }

    public static Condition.Settings condition() {
        return new Condition.Settings(INTOXICATION_DECAY.get(), TOLERANCE_DECAY.get(), DEPENDENCE_DECAY.get(),
                DEPENDENCE_DELAY.get() * 60000L, CRAVING_DELAY.get() * 60000L, WITHDRAWAL_DELAY.get() * 60000L,
                REST_MULTIPLIER.get());
    }

    public static MarketState.Settings market() {
        return new MarketState.Settings(DEMAND_MAX.get(), DEMAND_REFILL.get(), RIVAL_DRAIN.get());
    }

    /**
     * Whether the config has loaded. Before a server starts, and on a client at the title
     * screen, the values are not there yet; callers that can run then fall back to defaults.
     */
    public static boolean loaded() { return SPEC.isLoaded(); }
}
