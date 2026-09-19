package dev.lucas.slumdrugs.sim;

import dev.lucas.slumdrugs.sim.drug.Cultivation;
import dev.lucas.slumdrugs.sim.drug.Quality;
import dev.lucas.slumdrugs.sim.drug.Refining;
import dev.lucas.slumdrugs.sim.drug.UnitTransfer;
import dev.lucas.slumdrugs.sim.economy.MarketState;
import dev.lucas.slumdrugs.sim.npc.Npc;
import dev.lucas.slumdrugs.sim.player.Condition;
import dev.lucas.slumdrugs.sim.player.Recovery;
import dev.lucas.slumdrugs.sim.station.GrowboxState;
import dev.lucas.slumdrugs.sim.world.RoomFlood;

import java.util.List;

/**
 * Regression checks for the simulation, carried over from the plugin's runner.
 * Deliberately a plain main() with no test framework: the point is that the rules
 * can be verified without a game, a server or a dependency.
 */
public final class SimChecks {

    private static int checks;

    private static void check(boolean success, String label) {
        checks++;
        if (!success) throw new AssertionError(label);
    }

    private static void close(double actual, double expected, String label) {
        check(Math.abs(actual - expected) < 1e-8, label + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        roomFlood();
        growbox();
        recovery();
        condition();
        unitTransfer();
        quality();
        cultivation();
        refining();
        market();
        npcs();
        System.out.println("PASS: " + checks + " simulation assertions.");
    }

    // ---------------------------------------------------------------- room flood

    private static void roomFlood() {
        var seed = new RoomFlood.Cell(0, 0);
        check(RoomFlood.flood(seed, c -> Math.abs(c.x()) <= 2 && Math.abs(c.z()) <= 2, 144).size() == 25,
                "bounded room flood");
        check(RoomFlood.flood(seed, c -> true, 144).isEmpty(), "unbounded room rejected");
        check(RoomFlood.flood(seed, c -> c.x() >= 0 && c.x() < 3 && c.z() >= 0 && c.z() < 3, 8).isEmpty(),
                "area cap rejects whole room");
        check(RoomFlood.flood(seed, c -> false, 144).isEmpty(), "blocked seed");
        check(RoomFlood.flood(seed, c -> Math.abs(c.x()) == Math.abs(c.z()), 144).size() == 1,
                "diagonal rooms disconnected");
    }

    // ---------------------------------------------------------------- growbox

    private static void growbox() {
        var grow = new GrowboxState(0);
        grow.drug = "sunleaf";
        grow.waterSeconds = 120;
        grow.advance(60000, 600);
        close(grow.progress, .1, "elapsed growth");
        close(grow.waterSeconds, 60, "growth consumes water");
        grow.advance(600000, 600);
        close(grow.progress, .2, "offline growth bounded by water");
        close(grow.waterSeconds, 0, "water never negative");

        grow.waterSeconds = 1200;
        grow.lamp = false;
        grow.advance(1200000, 600);
        close(grow.progress, .2, "lamp off pauses growth");
        close(grow.waterSeconds, 1200, "paused growth saves water");

        grow.lamp = true;
        grow.fertilizer = 3;
        grow.advance(1800000, 600);
        close(grow.progress, 1, "fertilised crop reaches maturity");
        check(grow.stage() == 4, "mature visual stage");

        double water = grow.waterSeconds;
        grow.advance(1900000, 600);
        close(grow.waterSeconds, water, "mature crop stops consuming water");

        grow.clear();
        check(grow.stage() == 0 && grow.fertilizer == 0, "harvest resets crop");
    }

    // ---------------------------------------------------------------- recovery

    private static void recovery() {
        long minute = 60000L;
        close(Recovery.tolerance(80, 0, 60 * minute, .15), 71, "offline tolerance");
        close(Recovery.tolerance(10, 10, 0, .15), 10, "clock rollback");
        close(Recovery.dependence(80, 0, 10 * minute, 0, 20 * minute, 0, .08, 2.5), 80, "before delay");
        close(Recovery.dependence(80, 0, 60 * minute, 0, 20 * minute, 0, .08, 2.5), 76.8, "offline delay boundary");
        close(Recovery.dependence(80, 0, 60 * minute, 0, 20 * minute, 25 * minute, .08, 2.5), 75.6, "rest overlap only");

        double split = Recovery.dependence(80, 0, 30 * minute, 0, 20 * minute, 25 * minute, .08, 2.5);
        split = Recovery.dependence(split, 30 * minute, 60 * minute, 0, 20 * minute, 25 * minute, .08, 2.5);
        close(split, 75.6, "save/load recovery agrees with continuous recovery");
        close(Recovery.dependence(5, 0, 10000 * minute, 0, 0, 0, .08, 2.5), 0, "recovery lower bound");
    }

    // ---------------------------------------------------------------- condition

    private static void condition() {
        long minute = 60000L;
        var settings = Condition.Settings.defaults();

        // A dose lands in full on a clean user and is blunted, never blocked, on a hardened one.
        var fresh = new Condition(0, 0, 0);
        var hardened = new Condition(0, 100, 0);
        double freshDose = fresh.effectiveDose(20, 50);
        double hardenedDose = hardened.effectiveDose(20, 50);
        check(hardenedDose < freshDose, "tolerance blunts a dose");
        check(hardenedDose >= freshDose * 0.5, "tolerance never blocks a dose entirely");
        check(fresh.effectiveDose(20, 100) > fresh.effectiveDose(20, 0), "better goods hit harder");
        close(fresh.effectiveDose(0, 100), 0, "no dose, no effect");

        // Using raises all three, and nothing leaves the scale.
        var user = new Condition(0, 0, 0);
        double gained = user.use(20, 50, 3, 1.5, 10 * minute);
        check(gained > 0, "using gets you high");
        check(user.tolerance == 3 && user.dependence == 1.5, "using builds tolerance and dependence");
        check(user.lastUse == 10 * minute, "using is remembered");
        for (int i = 0; i < 200; i++) user.use(20, 100, 3, 1.5, 10 * minute);
        check(user.intoxication <= 100 && user.tolerance <= 100 && user.dependence <= 100,
                "nothing exceeds the scale however hard it is pushed");

        // Overdose is a warning the player can act on, not a surprise.
        var high = new Condition(90, 0, 0);
        check(high.wouldOverdose(20, 50), "a dose on top of a high player would overdose");
        check(!new Condition(0, 0, 0).wouldOverdose(20, 50), "a first dose is safe");

        // Coming down: intoxication goes first, the rest much later.
        var coming = new Condition(60, 50, 50);
        coming.lastUse = 0;
        coming.advance(0, 10 * minute, settings);
        check(coming.intoxication < 30, "intoxication wears off in minutes");
        check(coming.tolerance > 45, "tolerance takes far longer");
        check(coming.dependence == 50, "dependence does not fall while the delay runs");

        var clean = new Condition(0, 50, 50);
        clean.lastUse = 0;
        clean.advance(0, 120 * minute, settings);
        check(clean.dependence < 50, "dependence falls once someone has stopped");
        check(clean.dependence >= 0, "dependence never goes negative");

        // Recovery is always reachable: no state is a dead end.
        var worst = new Condition(100, 100, 100);
        worst.lastUse = 0;
        worst.advance(0, 100000 * minute, settings);
        check(worst.intoxication == 0 && worst.tolerance == 0 && worst.dependence == 0,
                "every condition recovers completely given time");

        // A night's sleep speeds it up.
        var rested = new Condition(0, 0, 50);
        var tired = new Condition(0, 0, 50);
        rested.slept(25 * minute);
        rested.advance(0, 60 * minute, settings);
        tired.advance(0, 60 * minute, settings);
        check(rested.dependence < tired.dependence, "sleep speeds recovery");

        // Craving and withdrawal need dependence, sobriety and time -- all three.
        var light = new Condition(0, 0, 10);
        check(!light.craving(60 * minute, settings), "a light user never craves");
        var dependent = new Condition(0, 0, 50);
        dependent.lastUse = 0;
        check(!dependent.craving(5 * minute, settings), "craving waits for the delay");
        check(dependent.craving(20 * minute, settings), "craving arrives after the delay");
        var stillHigh = new Condition(50, 0, 50);
        stillHigh.lastUse = 0;
        check(!stillHigh.craving(60 * minute, settings), "someone still high is not craving");

        check(!new Condition(0, 0, 30).withdrawing(60 * minute, settings),
                "withdrawal needs more dependence than craving does");
        var heavy = new Condition(0, 0, 50);
        heavy.lastUse = 0;
        check(!heavy.withdrawing(20 * minute, settings), "withdrawal waits longer than craving");
        check(heavy.withdrawing(40 * minute, settings), "withdrawal arrives after its own delay");

        // Severity is shallow and ordered.
        long late = 100 * minute;
        check(new Condition(0, 0, 45).withdrawalSeverity(late, settings) == 1, "mild withdrawal");
        check(new Condition(0, 0, 70).withdrawalSeverity(late, settings) == 2, "moderate withdrawal");
        check(new Condition(0, 0, 90).withdrawalSeverity(late, settings) == 3, "worst withdrawal");
        check(new Condition(0, 0, 90).withdrawalSeverity(0, settings) == 0, "no withdrawal before the delay");
        for (int d = 0; d <= 100; d++) {
            var c = new Condition(0, 0, d);
            int severity = c.withdrawalSeverity(late, settings);
            check(severity >= 0 && severity <= 3, "severity stays in range");
            if (d < Condition.WITHDRAWAL_MIN) check(severity == 0, "no withdrawal below the threshold");
        }

        boolean rejected = false;
        try { new Condition.Settings(0, 1, 1, 0, 0, 0, 1); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "intoxication must be able to wear off");
        rejected = false;
        try { new Condition.Settings(1, 1, 1, 0, 0, 0, 0.5); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "rest must not slow recovery");
    }

    // ---------------------------------------------------------------- unit transfer

    private static void unitTransfer() {
        check(UnitTransfer.plan(3, 64, 1).equals(new UnitTransfer(1, 2, 63)), "single unit out of a sealed stack");
        check(UnitTransfer.plan(3, 64, 70).equals(new UnitTransfer(70, 1, 58)), "partial second package");
        check(UnitTransfer.plan(2, 10, 100).equals(new UnitTransfer(20, 0, 0)), "limited inventory");

        for (int stacks = 0; stacks <= 64; stacks++)
            for (int per = 1; per <= 64; per++)
                for (int wanted : new int[]{0, 1, 10, 63, 64, 65, 127, 4096}) {
                    var plan = UnitTransfer.plan(stacks, per, wanted);
                    check(plan.consumed() + plan.remainingPackages() * per + plan.looseChange() == stacks * per,
                            "unit conservation");
                    check(plan.consumed() <= wanted && plan.looseChange() < per, "exact request limit");
                }

        boolean rejected = false;
        try { UnitTransfer.plan(1, 0, 1); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "zero-size package rejected");
    }

    // ---------------------------------------------------------------- quality

    private static void quality() {
        check(Quality.clamp(-5) == 0 && Quality.clamp(250) == 100, "quality clamped to range");
        check(Quality.Grade.of(0) == Quality.Grade.POOR, "poor grade");
        check(Quality.Grade.of(34) == Quality.Grade.POOR, "grade boundary below standard");
        check(Quality.Grade.of(35) == Quality.Grade.STANDARD, "standard grade boundary");
        check(Quality.Grade.of(100) == Quality.Grade.PREMIUM, "premium grade");
        check(Quality.Grade.parse("premium", Quality.Grade.POOR) == Quality.Grade.PREMIUM, "grade parsed by name");
        check(Quality.Grade.parse(null, Quality.Grade.GOOD) == Quality.Grade.GOOD, "grade fallback");
        close(Quality.priceFactor(0), 0.5, "price factor floor");
        close(Quality.priceFactor(100), 1.2, "price factor ceiling");
        close(Quality.durationFactor(0), 0.7, "duration factor floor");
        close(Quality.durationFactor(100), 1.2, "duration factor ceiling");
        for (int q = 1; q <= 100; q++)
            check(Quality.priceFactor(q) > Quality.priceFactor(q - 1), "price factor rises with quality");
    }

    // ---------------------------------------------------------------- cultivation

    private static void cultivation() {
        var neutral = Cultivation.Inputs.neutral();
        check(Cultivation.quality(neutral) == 50, "average seed in tilled ground returns average quality");

        // Seed quality dominates: same preparation, different line.
        var poorSeed = new Cultivation.Inputs(0, Cultivation.Soil.TILLED, 0, 0, 1);
        var goodSeed = new Cultivation.Inputs(100, Cultivation.Soil.TILLED, 0, 0, 1);
        check(Cultivation.quality(poorSeed) == 20, "poor seed floors at the baseline");
        check(Cultivation.quality(goodSeed) == 80, "best seed alone does not reach the ceiling");
        check(Cultivation.quality(goodSeed) - Cultivation.quality(poorSeed) == 60,
                "seed quality is worth 60 points, more than everything else combined");

        // Soil ranks strictly, in both quality and yield.
        int last = Integer.MIN_VALUE;
        double lastYield = 0;
        for (var soil : Cultivation.Soil.values()) {
            int q = Cultivation.quality(new Cultivation.Inputs(50, soil, 0, 0, 1));
            check(q > last, "better soil raises quality: " + soil);
            check(soil.yieldFactor >= lastYield, "better soil never yields less: " + soil);
            last = q;
            lastYield = soil.yieldFactor;
        }

        // Compost has diminishing returns and is capped.
        int c0 = Cultivation.quality(new Cultivation.Inputs(50, Cultivation.Soil.TILLED, 0, 100, 1));
        int c1 = Cultivation.quality(new Cultivation.Inputs(50, Cultivation.Soil.TILLED, 1, 100, 1));
        int c2 = Cultivation.quality(new Cultivation.Inputs(50, Cultivation.Soil.TILLED, 2, 100, 1));
        int c3 = Cultivation.quality(new Cultivation.Inputs(50, Cultivation.Soil.TILLED, 3, 100, 1));
        check(c1 > c0 && c2 > c1 && c3 > c2, "each charge of compost helps");
        check(c1 - c0 > c2 - c1 && c2 - c1 > c3 - c2, "compost has diminishing returns");
        check(Cultivation.quality(new Cultivation.Inputs(50, Cultivation.Soil.TILLED, 99, 100, 1)) == c3,
                "compost beyond the cap is wasted, not stacked");
        check(Cultivation.quality(new Cultivation.Inputs(50, Cultivation.Soil.TILLED, 3, 0, 1)) == c0,
                "worthless compost does nothing whatever the charge count");

        // Bad conditions cost quality; perfect conditions cost nothing.
        check(Cultivation.quality(new Cultivation.Inputs(50, Cultivation.Soil.TILLED, 0, 0, 0.5))
                < Cultivation.quality(neutral), "poor conditions lower quality");

        // Everything right reaches the ceiling; everything wrong reaches the floor.
        check(Cultivation.quality(new Cultivation.Inputs(100, Cultivation.Soil.RICH, 3, 100, 1)) == 100,
                "a perfect planting reaches 100");
        check(Cultivation.quality(new Cultivation.Inputs(0, Cultivation.Soil.BARE, 0, 0, 0)) == 0,
                "the worst planting floors at 0");

        // Yield: more inputs, more goods, never below one.
        check(Cultivation.units(3, neutral, 50) == 3, "neutral planting returns the base yield");
        check(Cultivation.units(3, new Cultivation.Inputs(100, Cultivation.Soil.RICH, 3, 100, 1), 100) > 3,
                "a good planting returns more than the base yield");
        check(Cultivation.units(1, new Cultivation.Inputs(0, Cultivation.Soil.BARE, 0, 0, 0), 0) >= 1,
                "even the worst planting returns something");
        for (int q = 1; q <= 100; q++)
            check(Cultivation.units(10, neutral, q) >= Cultivation.units(10, neutral, q - 1),
                    "yield never falls as quality rises");

        // Seed drift: centred just below the crop, bounded, and deterministic for a given roll.
        check(Cultivation.seedQuality(50, 0.5) == 48, "seed drifts slightly below the crop");
        check(Cultivation.seedQuality(50, 0.0) == 42, "worst roll loses six points");
        check(Cultivation.seedQuality(50, 1.0) == 54, "best roll gains four points");
        check(Cultivation.seedQuality(50, 0.5) == Cultivation.seedQuality(50, 0.5), "seed drift is deterministic");
        for (int q = 0; q <= 100; q++)
            for (double roll = 0; roll <= 1.0001; roll += 0.05) {
                int seed = Cultivation.seedQuality(q, roll);
                check(seed >= 0 && seed <= 100, "seed quality stays in range");
            }
        check(Cultivation.seedQuality(0, 0) == 0 && Cultivation.seedQuality(100, 1) == 100,
                "seed quality clamps at both ends");

        // A whole harvest resolves consistently with its parts.
        var rich = new Cultivation.Inputs(80, Cultivation.Soil.COMPOSTED, 2, 70, 0.9);
        var harvest = Cultivation.harvest(3, rich, 0.5);
        check(harvest.quality() == Cultivation.quality(rich), "harvest quality matches the rule");
        check(harvest.units() == Cultivation.units(3, rich, harvest.quality()), "harvest units match the rule");
        check(harvest.seeds() >= 1, "a harvest always returns at least one seed");
        check(Cultivation.harvest(3, new Cultivation.Inputs(100, Cultivation.Soil.RICH, 3, 100, 1), 0.5).seeds() == 2,
                "a doubled harvest returns a spare seed");

        boolean rejected = false;
        try { Cultivation.units(0, neutral, 50); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "a crop with no base yield is rejected");
        rejected = false;
        try { new Cultivation.Inputs(50, null, 0, 0, 1); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "soil is required");
    }

    // ---------------------------------------------------------------- refining

    private static void refining() {
        var batch = new Refining.Batch(50, 8);

        // The press keeps volume and adds nothing; it only changes what the item is.
        var pressed = Refining.run(Refining.Method.PRESS, batch, 100, 0);
        check(pressed.units() == 8, "the press keeps every unit");
        check(pressed.quality() == 50, "the press does not improve the batch");
        check(!Refining.needsReagent(Refining.Method.PRESS), "the press runs without a reagent");

        // The centrifuge trades volume for strength.
        var spun = Refining.run(Refining.Method.SEPARATE, batch, 0, 0);
        check(spun.units() == 6, "the centrifuge returns three quarters");
        check(spun.quality() > pressed.quality(), "the centrifuge concentrates");
        check(spun.wasteUnits() == 2, "what does not come through becomes waste");
        check(Refining.needsReagent(Refining.Method.SEPARATE), "the centrifuge wants a reagent");

        // The still trades harder.
        var distilled = Refining.run(Refining.Method.DISTIL, batch, 0, 0);
        check(distilled.units() == 4, "the still returns half");
        check(distilled.quality() > spun.quality(), "the still concentrates further");

        // Reagent quality is worth real points, and only where it applies.
        check(Refining.run(Refining.Method.SEPARATE, batch, 100, 0).quality()
                > Refining.run(Refining.Method.SEPARATE, batch, 0, 0).quality(), "a good reagent helps");
        check(Refining.run(Refining.Method.PRESS, batch, 100, 0).quality()
                == Refining.run(Refining.Method.PRESS, batch, 0, 0).quality(), "the press ignores the reagent");

        // Skill matters a little and cannot carry a bad batch.
        var skilled = Refining.run(Refining.Method.SEPARATE, batch, 0, 1);
        check(skilled.quality() - spun.quality() == 5, "a steady hand is worth five points");
        check(Refining.run(Refining.Method.SEPARATE, new Refining.Batch(0, 4), 0, 1).quality() < 20,
                "skill cannot rescue a worthless batch");

        // Nothing is ever destroyed entirely, and nothing exceeds the scale.
        for (int q = 0; q <= 100; q += 5)
            for (int units = 1; units <= 64; units++)
                for (var method : Refining.Method.values()) {
                    var r = Refining.run(method, new Refining.Batch(q, units), q, 0.5);
                    check(r.units() >= 1, "every batch returns at least one unit");
                    check(r.units() + r.wasteUnits() == units || r.units() == 1,
                            "units and waste account for the input");
                    check(r.quality() >= 0 && r.quality() <= 100, "quality stays on the scale");
                    check(r.compostQuality() >= 0 && r.compostQuality() <= 100, "compost stays on the scale");
                }

        // Spent mash feeds the ground it came from, slightly degraded.
        check(Refining.compostQuality(100) == 80, "the best batch makes good compost");
        check(Refining.compostQuality(0) == 0, "a worthless batch makes worthless compost");
        for (int q = 1; q <= 100; q++)
            check(Refining.compostQuality(q) < q, "compost is always poorer than the crop");

        // Power buys time, never quality.
        for (var method : Refining.Method.values()) {
            check(method.seconds(true) < method.seconds(false), "power halves the run: " + method);
            check(method.seconds(true) >= 1, "a powered run still takes time: " + method);
        }
        check(Refining.Method.SEPARATE.seconds(false) == 40, "a hand-cranked centrifuge takes forty seconds");

        boolean rejected = false;
        try { new Refining.Batch(50, 0); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "an empty batch is rejected");
        rejected = false;
        try { Refining.run(null, batch, 0, 0); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "a run needs a method");
    }

    // ---------------------------------------------------------------- market

    private static void market() {
        var market = new MarketState(MarketState.Settings.defaults());
        var ids = List.of("sunleaf", "frostroot");

        close(market.demand("sunleaf"), 48, "fresh demand starts three-quarters open");
        market.consume("sunleaf", 48);
        close(market.demand("sunleaf"), 0, "demand drains to zero, not below");
        market.consume("sunleaf", 999);
        close(market.demand("sunleaf"), 0, "oversupply cannot push demand negative");
        close(market.demandFactor("sunleaf"), 0.4, "flooded market pays the floor");

        for (int i = 0; i < 1000; i++) market.tickMinute(ids, 0);
        close(market.demand("sunleaf"), 64, "refill is capped at the maximum");
        close(market.demandFactor("sunleaf"), 1.2, "starved market pays the ceiling");

        double cheap = market.unitPrice("sunleaf", 10, 0, 0, 0);
        double dear = market.unitPrice("sunleaf", 10, 100, 0, 0);
        check(dear > cheap, "quality raises the unit price");
        check(market.unitPrice("sunleaf", 10, 100, 50, 60) > dear, "reputation and trust raise the price");
        check(market.unitPrice("sunleaf", 0, 0, 0, 0) >= 1, "price never falls below one coin");

        // Rival drain shrinks as crew reputation rises.
        var a = new MarketState(MarketState.Settings.defaults());
        var b = new MarketState(MarketState.Settings.defaults());
        a.consume("sunleaf", 40);
        b.consume("sunleaf", 40);
        a.tickMinute(ids, 0);
        b.tickMinute(ids, 150);
        check(b.demand("sunleaf") > a.demand("sunleaf"), "crew reputation blunts rival drain");

        var restored = new MarketState(MarketState.Settings.defaults());
        restored.restore(a.snapshot());
        close(restored.demand("sunleaf"), a.demand("sunleaf"), "snapshot round-trip");
        restored.restore(java.util.Map.of("sunleaf", 9999.0));
        close(restored.demand("sunleaf"), 64, "restore clamps a corrupted save");

        boolean rejected = false;
        try { new MarketState.Settings(0, 1, 1); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid market settings rejected");
    }

    // ---------------------------------------------------------------- npcs

    private static void npcs() {
        // Roles that cannot turn on you never do, whatever their aggression says.
        for (var role : Npc.Role.values())
            if (!role.hostileCapable)
                check(Npc.stance(role, 100) == Npc.Stance.CALM, "a peaceful role stays calm: " + role);

        // The bands are ordered and each one is reachable.
        check(Npc.stance(Npc.Role.BRUISER, 0) == Npc.Stance.CALM, "calm band");
        check(Npc.stance(Npc.Role.BRUISER, 24) == Npc.Stance.CALM, "calm band upper edge");
        check(Npc.stance(Npc.Role.BRUISER, 25) == Npc.Stance.WARY, "wary band");
        check(Npc.stance(Npc.Role.BRUISER, 49) == Npc.Stance.WARY, "wary band upper edge");
        check(Npc.stance(Npc.Role.BRUISER, 50) == Npc.Stance.DEMANDING, "demanding band");
        check(Npc.stance(Npc.Role.BRUISER, 74) == Npc.Stance.DEMANDING, "demanding band upper edge");
        check(Npc.stance(Npc.Role.BRUISER, 75) == Npc.Stance.HOSTILE, "hostile band");
        check(Npc.stance(null, 100) == Npc.Stance.CALM, "no role, no aggression");

        // Standing drives where a mood settles; turf depth sharpens it.
        double friendly = Npc.restingAggression(Npc.Role.BRUISER, 100, 0);
        double hated = Npc.restingAggression(Npc.Role.BRUISER, -100, 0);
        check(friendly == 0, "a crew that likes you rests calm");
        check(hated > friendly, "a crew that hates you rests angry");
        check(Npc.restingAggression(Npc.Role.BRUISER, -100, 1)
                > Npc.restingAggression(Npc.Role.BRUISER, -100, 0),
                "their own turf makes it worse");
        check(Npc.restingAggression(Npc.Role.HEALER, -100, 1) == 0, "a healer never rests angry");
        for (int standing = -100; standing <= 100; standing += 5)
            for (double depth = 0; depth <= 1.001; depth += 0.25) {
                double resting = Npc.restingAggression(Npc.Role.BRUISER, standing, depth);
                check(resting >= 0 && resting <= 100, "resting aggression stays in range");
            }

        // Settling is gradual in both directions and lands exactly on the target.
        check(Npc.settle(100, 0, 1) == 98, "anger fades slowly");
        check(Npc.settle(0, 100, 1) == 2, "anger builds slowly");
        check(Npc.settle(100, 0, 1000) == 0, "given time it reaches the target exactly");
        check(Npc.settle(0, 100, 1000) == 100, "and from below too");
        check(Npc.settle(50, 50, 10) == 50, "a settled mood does not drift");
        check(Npc.settle(50, 0, 0) == 50, "no time, no change");

        // Provocation and appeasement, both bounded.
        check(Npc.provoke(50, 30) == 80, "a provocation raises aggression");
        check(Npc.provoke(90, 999) == 100, "aggression cannot exceed the scale");
        check(Npc.appease(50, 30) == 20, "tribute lowers aggression");
        check(Npc.appease(10, 999) == 0, "aggression cannot go below zero");
        check(Npc.provoke(50, -10) == 50, "a negative provocation does nothing");
        check(Npc.appease(50, -10) == 50, "a negative appeasement does nothing");

        // A lieutenant pulls the crew partway, never all the way.
        double pulled = Npc.spread(0, 100);
        check(pulled > 0 && pulled < 100, "the crew takes its lead without becoming a copy");
        check(Npc.spread(100, 0) < 100, "a calm boss calms the crew too");
        check(Npc.spread(50, 50) == 50, "a matched crew does not move");
        for (int member = 0; member <= 100; member += 10)
            for (int boss = 0; boss <= 100; boss += 10) {
                double result = Npc.spread(member, boss);
                check(result >= 0 && result <= 100, "spread stays in range");
                check(Math.abs(result - boss) <= Math.abs(member - boss) + 1e-9,
                        "spread never moves the crew away from the boss");
            }
    }
}
