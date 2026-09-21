package dev.lucas.slumdrugs.sim;

import dev.lucas.slumdrugs.sim.drug.Cultivation;
import dev.lucas.slumdrugs.sim.drug.Cutting;
import dev.lucas.slumdrugs.sim.drug.Drying;
import dev.lucas.slumdrugs.sim.drug.Quality;
import dev.lucas.slumdrugs.sim.drug.Refining;
import dev.lucas.slumdrugs.sim.drug.Sealing;
import dev.lucas.slumdrugs.sim.drug.Strain;
import dev.lucas.slumdrugs.sim.drug.UnitTransfer;
import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.economy.MarketState;
import dev.lucas.slumdrugs.sim.npc.Npc;
import dev.lucas.slumdrugs.sim.npc.Standing;
import dev.lucas.slumdrugs.sim.player.Condition;
import dev.lucas.slumdrugs.sim.player.Progression;
import dev.lucas.slumdrugs.sim.player.Recovery;
import dev.lucas.slumdrugs.sim.player.Suspicion;
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
        withdrawalTimers();
        remedy();
        suspicion();
        climate();
        progression();
        unitTransfer();
        quality();
        cultivation();
        refining();
        cutting();
        strains();
        drying();
        sealing();
        market();
        coin();
        standing();
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

    // ---------------------------------------------------------------- withdrawal timers

    private static void withdrawalTimers() {
        long minute = 60000L;
        var settings = Condition.Settings.defaults();

        // Below the threshold there is nothing to count down to.
        var light = new Condition(0, 0, 30);
        check(light.minutesUntilWithdrawal(minute, settings) == -1, "no withdrawal clock without dependence");
        check(light.withdrawalMinutesLeft(settings) == 0, "nothing left of a withdrawal that cannot start");

        // Above it, the clock runs from the last use down to the delay, and stops at zero.
        var heavy = new Condition(0, 0, 60);
        heavy.lastUse = 10 * minute;
        close(heavy.minutesUntilWithdrawal(10 * minute, settings), settings.withdrawalDelayMillis() / (double) minute,
                "the whole delay remains right after using");
        close(heavy.minutesUntilWithdrawal(20 * minute, settings), settings.withdrawalDelayMillis() / (double) minute - 10,
                "ten minutes on, ten fewer remain");
        check(heavy.minutesUntilWithdrawal(1000 * minute, settings) == 0, "the clock stops at zero");
        check(heavy.withdrawing(1000 * minute, settings), "and withdrawal has begun by then");

        // What is left is the distance back to the threshold at the plain decay rate.
        close(heavy.withdrawalMinutesLeft(settings), 20 / settings.dependenceDecayPerMinute(),
                "twenty points over the threshold at the plain rate");
        check(new Condition(0, 0, 100).withdrawalMinutesLeft(settings) > heavy.withdrawalMinutesLeft(settings),
                "deeper dependence takes longer");
    }

    // ---------------------------------------------------------------- progression

    private static void progression() {
        var p = new Progression();
        check(p.tier() == Progression.Tier.HAND_TO_MOUTH, "everyone starts hand to mouth");
        check(p.gate().next() == Progression.Tier.BACKROOM && p.gate().unitsNeeded() == Progression.BACKROOM_UNITS,
                "the first gate is units sold");

        // Coin alone does not open the backroom; units do.
        p.sold(0, 1000);
        check(p.tier() == Progression.Tier.HAND_TO_MOUTH, "coin without sales opens nothing");
        p.sold(Progression.BACKROOM_UNITS - 1, 0);
        check(p.tier() == Progression.Tier.HAND_TO_MOUTH, "one unit short is short");
        p.sold(1, 0);
        check(p.tier() == Progression.Tier.WORKSHOP, "with the coin already banked, the units open both doors");

        // The usual order: units first, then coin.
        var q = new Progression(Progression.BACKROOM_UNITS, 0);
        check(q.tier() == Progression.Tier.BACKROOM, "twenty units is the backroom");
        check(q.gate().coinNeeded() == Progression.WORKSHOP_COIN && q.gate().unitsNeeded() == 0,
                "the second gate is coin");
        q.sold(0, Progression.WORKSHOP_COIN);
        check(q.tier() == Progression.Tier.WORKSHOP, "sixty coin is the workshop");
        check(q.reached(Progression.Tier.BACKROOM) && !q.reached(Progression.Tier.APOTHECARY),
                "reached counts every tier below");

        // The ladder ends where the systems it needs end, and says so.
        var gate = q.gate();
        check(!gate.reachable() && gate.next() == Progression.Tier.APOTHECARY, "the apothecary is not reachable yet");
        q.sold(100000, 100000);
        check(q.tier() == Progression.REACHABLE, "no amount of trade passes the reachable tier");

        // Sales never count backwards, and the labels read.
        var r = new Progression(5, 5);
        r.sold(-3, -3);
        check(r.unitsSold == 5 && r.coinEarned == 5, "a refund is not a sale undone");
        check(new Progression(-1, -1).unitsSold == 0, "counters never start negative");
        check(Progression.Tier.KINGPIN.next() == Progression.Tier.KINGPIN, "the top has no next");
        for (var t : Progression.Tier.values()) check(!t.label.isBlank(), "every tier has a name");
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

    // ---------------------------------------------------------------- climate

    private static void climate() {
        var band = new Cultivation.Band(0.5, 0.8, 0.2, 0.6);

        // Inside the band on both axes is perfect; a null band, or one that likes everything, too.
        close(Cultivation.climateFit(0.6, 0.4, band), 1, "inside the band is a perfect fit");
        close(Cultivation.climateFit(0.5, 0.2, band), 1, "the band's edges are inside it");
        close(Cultivation.climateFit(0, 1, Cultivation.Band.any()), 1, "an untuned substance likes everything");
        close(Cultivation.climateFit(0, 1, null), 1, "no band is no penalty");

        // Outside, fit falls with distance and bottoms out at the floor.
        double near = Cultivation.climateFit(0.4, 0.4, band);
        double far = Cultivation.climateFit(0.0, 0.4, band);
        check(near < 1 && near > far, "further outside is a worse fit");
        close(far, Cultivation.CLIMATE_FLOOR, "far outside is the floor, not death");
        close(Cultivation.climateFit(0, 1, band), Cultivation.CLIMATE_FLOOR, "wrong on both axes is still the floor");
        for (double w = 0; w <= 1.0001; w += 0.05)
            for (double d = 0; d <= 1.0001; d += 0.05) {
                double fit = Cultivation.climateFit(w, d, band);
                check(fit >= Cultivation.CLIMATE_FLOOR && fit <= 1, "climate fit stays in range");
            }

        // Both axes cost the same, and the band tolerates a reversed pair by fixing it.
        close(Cultivation.climateFit(0.3, 0.4, band), Cultivation.climateFit(0.6, 0.8, band),
                "warmth and damp are weighed alike");
        var flipped = new Cultivation.Band(0.8, 0.5, 0.6, 0.2);
        check(flipped.warmthHigh() >= flipped.warmthLow() && flipped.dampHigh() >= flipped.dampLow(),
                "a reversed band is straightened");

        // Climate feeds the same environment input the frame already had.
        var good = new Cultivation.Inputs(50, Cultivation.Soil.TILLED, 0, 0, 1);
        var poor = new Cultivation.Inputs(50, Cultivation.Soil.TILLED, 0, 0, Cultivation.CLIMATE_FLOOR);
        check(Cultivation.quality(good) > Cultivation.quality(poor), "a bad climate costs quality");
    }

    // ---------------------------------------------------------------- remedy

    private static void remedy() {
        long minute = 60000L;
        var settings = Condition.Settings.defaults();
        var c = new Condition(0, 40, 70);
        c.lastUse = 0;
        long now = settings.withdrawalDelayMillis() + minute;
        check(c.withdrawing(now, settings), "deep dependence, long clean: withdrawing");

        // A draught takes a little off and holds withdrawal away for its duration.
        check(c.remedy(now), "the first draught works");
        close(c.dependence, 70 - Condition.REMEDY_DEPENDENCE, "a draught takes a little dependence off");
        close(c.tolerance, 40 - Condition.REMEDY_TOLERANCE, "and a little tolerance");
        check(!c.withdrawing(now, settings), "withdrawal is held off");
        check(c.withdrawalSeverity(now + Condition.REMEDY_MILLIS - 1, settings) == 0, "right up to the end");
        check(c.withdrawing(now + Condition.REMEDY_MILLIS, settings), "and comes back when it wears off");

        // It cannot be chained.
        check(!c.remedy(now + minute), "a second draught while the first works is refused");
        close(c.dependence, 70 - Condition.REMEDY_DEPENDENCE, "and takes nothing off");
        check(c.remedy(now + Condition.REMEDY_MILLIS), "once it wears off, another works");

        // It never goes below zero, and craving is not held off: only the hard state is.
        var light = new Condition(0, 1, 2);
        light.remedy(now);
        check(light.dependence == 0 && light.tolerance == 0, "a draught cannot go below zero");
        var craver = new Condition(0, 0, 30);
        craver.lastUse = 0;
        craver.remedy(now);
        check(craver.craving(now, settings), "a draught does not quiet a craving");

        // Restoring carries the timer.
        var restored = Condition.of(0, 0, 50, 0, 0, now + minute);
        check(restored.soothed(now) && !restored.soothed(now + 2 * minute), "the soothed timer survives a restore");
    }

    // ---------------------------------------------------------------- suspicion

    private static void suspicion() {
        long minute = 60000L;
        var s = new Suspicion();
        check(s.level() == Suspicion.Level.CLEAR && s.untilRaid(0) == -1, "nobody starts under suspicion");

        // Sealed goods draw more than loose ones.
        var loose = new Suspicion();
        var sealed = new Suspicion();
        loose.sold(8, false);
        sealed.sold(8, true);
        check(sealed.value > loose.value, "a seal is evidence");
        close(loose.value, 8 * Suspicion.PER_LOOSE_UNIT, "loose units at the loose rate");

        // Quiet time fades it, never below zero.
        loose.decay(4);
        close(loose.value, 8 * Suspicion.PER_LOOSE_UNIT - 4 * Suspicion.DECAY_PER_MINUTE, "quiet minutes fade it");
        loose.decay(1000);
        check(loose.value == 0, "fading stops at zero");

        // The levels climb in order and top out.
        for (int i = 0; i < 200; i++) s.sold(1, false);
        check(s.value == 100 && s.level() == Suspicion.Level.RAID, "suspicion tops out at a raid");
        check(Suspicion.Level.of(Suspicion.WATCHED_AT) == Suspicion.Level.WATCHED
                && Suspicion.Level.of(Suspicion.WATCHED_AT - 0.01) == Suspicion.Level.NOTICED, "levels sit on their thresholds");

        // A raid is called once, warned ahead, and lands on time.
        check(s.shouldCallRaid(), "at the top the bell rings");
        s.callRaid(10 * minute);
        check(!s.shouldCallRaid(), "it rings once");
        check(s.untilRaid(10 * minute) == Suspicion.RAID_WARNING_MILLIS, "the whole warning remains when called");
        check(!s.raidDue(10 * minute + Suspicion.RAID_WARNING_MILLIS - 1), "not a moment early");
        check(s.raidDue(10 * minute + Suspicion.RAID_WARNING_MILLIS), "and lands on time");
        s.raided();
        check(s.value == Suspicion.AFTER_RAID && s.raidAt == 0 && s.level() == Suspicion.Level.NOTICED,
                "after a raid the Watch remembers but stands down");

        // Laying low before it lands calls it off.
        var lying = new Suspicion(85, 0);
        lying.callRaid(0);
        check(!lying.raidLapsed(), "a called raid holds while suspicion is high");
        lying.decay(100);
        check(lying.raidLapsed(), "and lapses once the player has laid low");
        lying.cancelRaid();
        check(lying.raidAt == 0 && lying.untilRaid(0) == -1, "a cancelled raid is gone");
        check(new Suspicion(-5, -5).value == 0, "restored values are clamped");
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

        // A hand press is worked in strokes that add up to the method's run.
        check(Refining.Method.PRESS.strokes() == 5, "the pressing bench takes five pulls");
        for (var method : Refining.Method.values()) {
            check(method.strokes() * Refining.HAND_STROKE_SECONDS >= method.seconds(false),
                    "the strokes cover the run: " + method);
            check((method.strokes() - 1) * Refining.HAND_STROKE_SECONDS < method.seconds(false),
                    "no stroke is wasted: " + method);
        }
    }

    // ---------------------------------------------------------------- strains

    private static void strains() {
        double[] mid = {0.5, 0.5, 0.5, 0.5};
        double[] high = {1, 1, 1, 1};
        double[] low = {0, 0, 0, 0};

        // An average line does average things.
        check(Strain.AVERAGE.isAverage(), "the average is average");
        close(Strain.AVERAGE.potencyFactor(), 1.0, "average potency changes nothing");
        close(Strain.AVERAGE.vigourFactor(), 1.0, "average vigour changes nothing");
        close(Strain.AVERAGE.subtletyFactor(), 1.0, "average subtlety changes nothing");
        close(new Strain(100, 0, 0, 0).potencyFactor(), 1.3, "full potency is a third more");
        close(new Strain(0, 100, 0, 0).vigourFactor(), 1.2, "full vigour is a fifth more");
        close(new Strain(0, 0, 0, 100).subtletyFactor(), 0.6, "full subtlety hides most of a sale");
        close(new Strain(0, 0, 0, 0).subtletyFactor(), 1.4, "no subtlety shouts");
        close(new Strain(0, 0, 0, 0).climateFloor(), Cultivation.CLIMATE_FLOOR, "no hardiness is the usual floor");
        close(new Strain(0, 0, 100, 0).climateFloor(), Math.min(1, Cultivation.CLIMATE_FLOOR * 2), "full hardiness doubles the floor");
        check(Cultivation.climateFit(0, 1, new Cultivation.Band(0.5, 0.8, 0.2, 0.6), new Strain(0, 0, 100, 0).climateFloor())
                > Cultivation.climateFit(0, 1, new Cultivation.Band(0.5, 0.8, 0.2, 0.6)), "a hardy line minds a bad climate less");
        check(new Strain(200, -5, 50, 50).potency() == 100 && new Strain(200, -5, 50, 50).vigour() == 0, "traits are clamped");

        // A cross lands on the mean with middling rolls and within the spread otherwise.
        var a = new Strain(80, 20, 60, 40);
        var b = new Strain(40, 60, 60, 80);
        var child = Strain.cross(a, b, mid);
        check(child.equals(new Strain(60, 40, 60, 60)), "middling rolls give the parents' mean");
        var lucky = Strain.cross(a, b, high);
        var unlucky = Strain.cross(a, b, low);
        check(lucky.potency() == 60 + Strain.CROSS_SPREAD && unlucky.potency() == 60 - Strain.CROSS_SPREAD, "the spread is the spread");
        check(Strain.cross(null, null, mid).isAverage(), "no parents is the average");
        check(Strain.cross(new Strain(100, 100, 100, 100), new Strain(100, 100, 100, 100), high).potency() == 100, "a cross never leaves the scale");

        // Drift is smaller than a cross's spread, and centred.
        check(a.drift(mid).equals(a), "middling rolls drift nowhere");
        check(a.drift(high).potency() == 80 + Strain.DRIFT && a.drift(low).potency() == 80 - Strain.DRIFT, "drift is the drift");
        check(Strain.DRIFT < Strain.CROSS_SPREAD, "a harvest holds a line steadier than a cross does");

        boolean rejected = false;
        try { Strain.cross(a, b, new double[]{0.5}); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "a cross wants four rolls");
    }

    // ---------------------------------------------------------------- cutting

    private static void cutting() {
        // No filler changes nothing but the accounting.
        var plain = Cutting.cut(60, 8, 0, 0);
        check(plain.units() == 8 && plain.quality() == 60 && plain.cutRatio() == 0, "no filler, no change");

        // Filler adds units, dilutes quality and costs a little more for the handling.
        var half = Cutting.cut(60, 8, 8, 0);
        check(half.units() == 16, "equal parts doubles the batch");
        check(half.quality() == 30 - Cutting.HANDLING_LOSS, "and halves the quality, less the handling");
        close(half.cutRatio(), 0.5, "equal parts is half filler");

        // It will not take more than equal parts.
        var greedy = Cutting.cut(60, 8, 100, 0);
        check(greedy.units() == 16 && Cutting.maxFiller(8) == 8, "filler stops at equal parts");

        // Cutting cut goods stacks the ratio.
        var twice = Cutting.cut(half.quality(), half.units(), half.units(), half.cutRatio());
        close(twice.cutRatio(), 0.75, "cutting a half-cut batch by half leaves a quarter pure");
        check(twice.quality() < half.quality(), "and worse again");

        // The overdose line comes down with the cut, never below the penalty's floor.
        close(Cutting.overdoseThreshold(100, 0), 100, "pure goods keep the whole line");
        close(Cutting.overdoseThreshold(100, 1), 100 * (1 - Cutting.OVERDOSE_PENALTY), "fully cut brings it down by the penalty");
        var c = new Condition(50, 0, 0);
        check(!c.wouldOverdose(30, 50) && c.wouldOverdose(30, 50, 1), "a dose that was safe pure is not safe cut");

        for (int q = 0; q <= 100; q += 10)
            for (int units = 1; units <= 32; units += 3)
                for (int filler = 0; filler <= 40; filler += 5) {
                    var r = Cutting.cut(q, units, filler, 0);
                    check(r.units() >= units && r.units() <= 2 * units, "cut units stay between the input and double");
                    check(r.quality() >= 0 && r.quality() <= q, "cut quality never rises");
                    check(r.cutRatio() >= 0 && r.cutRatio() < 1, "a batch is never all filler");
                }
        boolean rejected = false;
        try { Cutting.cut(50, 0, 1, 0); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "an empty batch cannot be cut");
    }

    // ---------------------------------------------------------------- drying

    private static void drying() {
        int t = Drying.SECONDS;

        // Progress is a plain fraction of the drying time and stops at one.
        close(Drying.progress(0, t), 0, "nothing hung, nothing dried");
        close(Drying.progress(t / 2.0, t), 0.5, "half the time is half dried");
        close(Drying.progress(t * 10, t), 1, "progress never passes done");
        check(!Drying.ready(t - 1, t), "a second early is not ready");
        check(Drying.ready(t, t), "on time is ready");
        close(Drying.progress(5, 0), 1, "a zero drying time is instantly done");

        // Taken down early it is what it was; taken on time it is a little better.
        check(Drying.quality(50, 10, t) == 50, "an unready bundle keeps its input quality");
        check(Drying.quality(50, t, t) == 50 + Drying.BONUS, "a timely bundle earns the bonus");
        check(Drying.quality(50, t + Drying.GRACE_SECONDS, t) == 50 + Drying.BONUS,
                "the grace window keeps the bonus");
        check(Drying.quality(100, t, t) == 100, "the bonus does not leave the scale");

        // Neglect costs, slowly, and never everything.
        int justOver = Drying.quality(50, t + Drying.GRACE_SECONDS + t / 3.0 + 1, t);
        check(justOver == 50 + Drying.BONUS - 1, "the first third over grace costs a point");
        check(Drying.quality(50, t * 1000, t) == 50 + Drying.BONUS - Drying.MAX_LOSS,
                "over-drying bottoms out");
        check(Drying.quality(5, t * 1000, t) == 0, "a poor bundle can dry to nothing, not below");
        for (double hung = 0; hung <= t * 20; hung += t / 7.0) {
            int q = Drying.quality(60, hung, t);
            check(q >= 0 && q <= 100, "dried quality stays on the scale");
            if (hung >= t) check(q <= Drying.quality(60, Math.max(t, hung - t / 7.0), t),
                    "a dried bundle never improves by waiting");
        }
    }

    // ---------------------------------------------------------------- sealing

    private static void sealing() {
        int n = Sealing.UNITS_PER_PARCEL;
        check(Sealing.parcels(0) == 0 && Sealing.loose(0) == 0, "nothing seals into nothing");
        check(Sealing.parcels(n - 1) == 0 && Sealing.loose(n - 1) == n - 1, "short of a parcel stays loose");
        check(Sealing.parcels(n) == 1 && Sealing.loose(n) == 0, "exactly one parcel");
        check(Sealing.parcels(n * 3 + 2) == 3 && Sealing.loose(n * 3 + 2) == 2, "parcels and change");
        for (int units = 0; units <= 200; units++)
            check(Sealing.parcels(units) * n + Sealing.loose(units) == units, "parcels and loose account for the units");
        check(Sealing.parcelsFor(n * 4, 2) == 2, "wax limits how many parcels are sealed");
        check(Sealing.parcelsFor(n * 2, 10) == 2, "spare wax does not seal air");
        check(Sealing.parcelsFor(-5, -5) == 0, "negative stock seals nothing");

        // A parcel is the wholesale unit that the plugin's transfer maths already handles.
        var plan = UnitTransfer.plan(3, n, n + 1);
        check(plan.remainingPackages() == 1 && plan.looseChange() == n - 1, "opening a parcel returns change");
    }

    // ---------------------------------------------------------------- coin

    private static void coin() {
        // Twelve pence to the shilling, twenty shillings to the sovereign.
        check(Coin.SHILLING == 12 && Coin.SOVEREIGN == 240, "the denominations are the design's");
        var s = Coin.split(3 * Coin.SOVEREIGN + 4 * Coin.SHILLING + 6);
        check(s.sovereigns() == 3 && s.shillings() == 4 && s.pennies() == 6, "a sum splits largest first");
        check(s.pence() == 3 * Coin.SOVEREIGN + 4 * Coin.SHILLING + 6, "and adds back up");
        check(Coin.split(0).pence() == 0 && Coin.split(-5).pence() == 0, "nothing and less than nothing are nothing");
        for (long p = 0; p < 3000; p += 7) {
            var sp = Coin.split(p);
            check(sp.pence() == p, "every sum round-trips");
            check(sp.shillings() < 20 && sp.pennies() < 12, "no split carries a coin it could trade up");
        }

        // The base scale: ten points is about two shillings.
        close(Coin.baseUnitPence(10), 24, "a ten-point base is two shillings");
        check(Coin.baseUnitPence(-1) == 0, "no negative prices");

        // Stamping costs a tenth, rounded against the player, and never everything.
        check(Coin.stampFee(100) == 10 && Coin.stampFee(101) == 11, "the fee rounds up");
        check(Coin.stamped(100) == 90, "ninety of a hundred come back stamped");
        check(Coin.stamped(1) == 1, "a penny stamps to a penny");
        check(Coin.stamped(0) == 0, "nothing stamps to nothing");
        for (long p = 1; p < 2000; p++)
            check(Coin.stamped(p) >= 1 && Coin.stamped(p) <= p, "stamping keeps most and loses some");

        // The words.
        check(Coin.format(0).equals("0d"), "nothing reads as nought pence");
        check(Coin.format(6).equals("6d") && Coin.format(12).equals("1s") && Coin.format(18).equals("1s 6d"), "small sums read");
        check(Coin.format(2 * Coin.SOVEREIGN + 3).equals("2 sov 3d"), "sovereigns read with their change");
        check(Coin.STARTING_PURSE == 10 * Coin.SHILLING, "the starting purse is ten shillings");
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

    private static void standing() {
        var map = new java.util.HashMap<String, Double>();
        check(Standing.of(map, "ashfall") == 0 && Standing.of(map, null) == 0, "nobody starts with a standing");

        // A hit costs, and the opposed crew gains exactly what was lost.
        Standing.apply(map, "ashfall", Standing.HIT);
        close(Standing.of(map, "ashfall"), Standing.HIT, "a hit costs the hit");
        close(Standing.of(map, "choir"), -Standing.HIT, "and the Choir is glad of it");
        check(Standing.of(map, "tidewater") == 0 && Standing.of(map, "quarry") == 0, "the other pair is untouched");

        // A kill costs more; tribute buys back, a shilling a point, capped.
        Standing.apply(map, "ashfall", Standing.KILL);
        close(Standing.of(map, "ashfall"), Standing.HIT + Standing.KILL, "a kill costs the kill");
        close(Standing.tribute(5 * 12), 5, "five shillings, five points");
        close(Standing.tribute(100 * 12), Standing.TRIBUTE_CAP, "no tribute buys more than the cap");
        check(Standing.tribute(-12) == 0, "no negative tribute");

        // Clamped at both ends, and war has a line.
        Standing.apply(map, "quarry", -1000);
        check(Standing.of(map, "quarry") == Standing.MIN && Standing.of(map, "tidewater") == Standing.MAX, "standing is clamped both ways");
        check(Standing.atWar(Standing.WAR_AT) && !Standing.atWar(Standing.WAR_AT + 1), "war starts at the line");

        // A crew the design does not know moves alone, and a blank crew moves nothing.
        Standing.apply(map, "dockside", 10);
        check(Standing.of(map, "dockside") == 10 && map.size() == 5, "an unknown crew has no opposite");
        Standing.apply(map, "", 10);
        Standing.apply(map, null, 10);
        check(map.size() == 5, "no crew, no change");

        // Every crew has an opposite and the pairing is mutual.
        for (var c : Standing.Crew.values()) {
            check(c.opposed().opposed() == c, "opposition is mutual: " + c);
            check(Standing.Crew.byId(c.id) == c, "every crew is found by id: " + c);
        }
        check(Standing.Crew.byId("nobody") == null, "an unknown id is nobody");

        // And standing is what rests a crew member's mood.
        check(Npc.restingAggression(Npc.Role.BRUISER, -100, 0) > Npc.restingAggression(Npc.Role.BRUISER, 100, 0),
                "a crew that hates you rests angrier");
    }

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
