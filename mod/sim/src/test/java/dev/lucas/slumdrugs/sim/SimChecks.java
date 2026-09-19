package dev.lucas.slumdrugs.sim;

import dev.lucas.slumdrugs.sim.drug.Quality;
import dev.lucas.slumdrugs.sim.drug.UnitTransfer;
import dev.lucas.slumdrugs.sim.economy.MarketState;
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
        unitTransfer();
        quality();
        market();
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
}
