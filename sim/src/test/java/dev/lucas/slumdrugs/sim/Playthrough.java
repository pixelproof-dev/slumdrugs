package dev.lucas.slumdrugs.sim;

import dev.lucas.slumdrugs.sim.drug.Cultivation;
import dev.lucas.slumdrugs.sim.drug.Drying;
import dev.lucas.slumdrugs.sim.drug.Refining;
import dev.lucas.slumdrugs.sim.drug.Sealing;
import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.economy.MarketState;
import dev.lucas.slumdrugs.sim.npc.Loyalty;
import dev.lucas.slumdrugs.sim.player.Progression;
import dev.lucas.slumdrugs.sim.player.Suspicion;
import dev.lucas.slumdrugs.sim.station.GrowboxState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A week of play, on the rules alone. One player grows sunleaf in a few frames, dries it,
 * presses it and sells it, following a simple policy, second by second for seven game days
 * of twenty minutes each. The output is the curve the tuning numbers produce: when the
 * Backroom and the Workshop open, coin per hour, how hot the Watch runs, how many raids.
 *
 * <p>It runs as part of {@code ./gradlew :sim:check} and fails when the curve leaves the
 * envelope the design asks for, so a tuning change that makes the first week trivial or
 * hopeless is caught here, not by a player.
 *
 * <p>The platform's defaults that are not sim constants (growth time, base yield, customer
 * hand and markup, the broker's rates, sunleaf's base price) are mirrored at the top. If
 * {@code Tuning} changes them, change them here too.
 */
public final class Playthrough {

    // Mirrors of Tuning and the platform's constants.
    static final int GROWTH_SECONDS = 600;
    static final int WATER_SECONDS = 900;
    static final int BASE_YIELD = 3;
    static final int CUSTOMER_HAND = 4;
    static final double CUSTOMER_MARKUP = 1.6;
    static final double BROKER_LOOSE = 1.2;
    static final double BROKER_PARCEL = 1.4;
    static final int SUNLEAF_BASE_POINTS = 10;
    static final int COMPOST_PENCE = 2 * Coin.SHILLING;
    static final int COMPOST_QUALITY = 60;
    static final int CELL_SECONDS = 180;
    static final int CUSTOMERS = 5;

    static final int DAY_SECONDS = 20 * 60;
    static final int WEEK_SECONDS = 7 * DAY_SECONDS;

    /**
     * How the player plays.
     *
     * @param frames    forcing frames planted from the start
     * @param street    sell loose to regulars by hand; otherwise to the broker
     * @param sealed    when selling to the broker, seal parcels first
     * @param layLowAt  stop selling at this suspicion until it fades; 101 never stops
     * @param compost   compost charges bought per planting, when the purse allows
     */
    record Policy(String name, int frames, boolean street, boolean sealed, double layLowAt, int compost) {}

    record Outcome(String name, int harvested, int sold, long pence, long spent, int backroomAt, int workshopAt,
                   int raids, double peakSuspicion, double lastQuality, long[] penceByDay) {
        String at(int seconds) {
            if (seconds < 0) return "never";
            return String.format(Locale.ROOT, "day %d %02d:%02d", seconds / DAY_SECONDS + 1,
                    seconds % DAY_SECONDS / 60, seconds % 60);
        }
    }

    private record Bundle(int quality, int units, int hungAt) {}
    private record Lot(int quality, int units) {}

    private static final class Customer {
        double loyalty = Loyalty.START;
        final int floor;
        Customer(int floor) { this.floor = floor; }
    }

    static Outcome play(Policy p, long seed) {
        Random random = new Random(seed);
        MarketState market = new MarketState(MarketState.Settings.defaults());
        Suspicion suspicion = new Suspicion();
        Progression progress = new Progression();
        Suspicion.Settings watch = Suspicion.Settings.defaults();
        double basePence = Coin.baseUnitPence(SUNLEAF_BASE_POINTS);

        List<GrowboxState> frames = new ArrayList<>();
        int[] charges = new int[p.frames()];
        int seedQuality = 50;
        long purse = Coin.STARTING_PURSE, spent = 0;
        for (int i = 0; i < p.frames(); i++) frames.add(new GrowboxState(0));

        List<Bundle> loft = new ArrayList<>();
        List<Lot> toPress = new ArrayList<>();
        List<Lot> stock = new ArrayList<>();
        int pressBusyUntil = 0;
        int heldUntil = 0;
        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < CUSTOMERS; i++) customers.add(new Customer(Loyalty.floor(random.nextInt(1000))));
        int nextCustomer = 0;

        int harvested = 0, sold = 0, raids = 0, backroomAt = -1, workshopAt = -1;
        double peak = 0, lastQuality = 0;
        long[] byDay = new long[7];
        long earned = 0;

        for (int t = 0; t < WEEK_SECONDS; t++) {
            long now = t * 1000L;
            // Frames: plant when empty, harvest when ripe, keep the best seed.
            for (int i = 0; i < frames.size(); i++) {
                GrowboxState f = frames.get(i);
                if (f.drug == null) {
                    int bought = 0;
                    while (bought < p.compost() && purse >= COMPOST_PENCE) { purse -= COMPOST_PENCE; spent += COMPOST_PENCE; bought++; }
                    f.drug = "sunleaf"; f.progress = 0; f.waterSeconds = WATER_SECONDS; f.fertilizer = bought; f.updatedAt = now;
                    charges[i] = bought;
                }
                f.advance(now, GROWTH_SECONDS);
                if (f.stage() == 4) {
                    Cultivation.Inputs in = new Cultivation.Inputs(seedQuality,
                            charges[i] > 0 ? Cultivation.Soil.COMPOSTED : Cultivation.Soil.TILLED, charges[i], COMPOST_QUALITY, 1.0);
                    Cultivation.Harvest h = Cultivation.harvest(BASE_YIELD, in, random.nextDouble());
                    harvested += h.units();
                    seedQuality = Math.max(seedQuality, h.seedQuality());
                    loft.add(new Bundle(h.quality(), h.units(), t));
                    f.clear();
                }
            }
            // Loft: take bundles down as soon as they are dry.
            for (int i = loft.size() - 1; i >= 0; i--) {
                Bundle b = loft.get(i);
                if (Drying.ready(t - b.hungAt(), Drying.SECONDS)) {
                    int q = Drying.quality(b.quality(), t - b.hungAt(), Drying.SECONDS);
                    toPress.add(new Lot(q, b.units()));
                    loft.remove(i);
                }
            }
            if (t < heldUntil) continue;  // in the cell: nothing else happens
            // Press: one batch at a time, hand-driven.
            if (t >= pressBusyUntil && !toPress.isEmpty()) {
                Lot lot = toPress.remove(0);
                Refining.Result r = Refining.run(Refining.Method.PRESS, new Refining.Batch(lot.quality(), lot.units()), 0, 0.5);
                lastQuality = r.quality();
                pressBusyUntil = t + Refining.Method.PRESS.seconds;
                // Parcels are sealed from a pooled stock, as the sealing press pools it: one
                // lot, its quality the weighted mean of what went in.
                if (p.sealed() && !stock.isEmpty()) {
                    Lot pool = stock.remove(0);
                    int units = pool.units() + r.units();
                    stock.add(0, new Lot((pool.quality() * pool.units() + r.quality() * r.units()) / units, units));
                } else stock.add(new Lot(r.quality(), r.units()));
            }
            // Selling: one sale every ten seconds of attention, while the Watch allows.
            if (t % 10 == 0 && !stock.isEmpty() && suspicion.value < p.layLowAt()) {
                long pence = 0; int units = 0; boolean sealed = false;
                Lot lot = stock.get(0);
                if (p.street()) {
                    Customer c = customers.get(nextCustomer++ % customers.size());
                    if (!Loyalty.lost(c.loyalty)) {
                        units = Math.min(Loyalty.hand(c.loyalty, CUSTOMER_HAND), Math.min(lot.units(), (int) market.demand("sunleaf")));
                        if (units > 0) {
                            double markup = CUSTOMER_MARKUP * Loyalty.priceFactor(c.loyalty) * (lot.quality() < c.floor ? 0.7 : 1.0);
                            pence = Math.max(1, Math.round(units * market.unitPrice("sunleaf", basePence, lot.quality(), 0, 0) * markup));
                            c.loyalty = Loyalty.afterSale(c.loyalty, lot.quality(), c.floor, false);
                        }
                    }
                } else if (p.sealed()) {
                    if (lot.units() >= Sealing.UNITS_PER_PARCEL && market.demand("sunleaf") >= Sealing.UNITS_PER_PARCEL) {
                        units = Sealing.UNITS_PER_PARCEL; sealed = true;
                        pence = Math.max(1, Math.round(units * market.unitPrice("sunleaf", basePence, 50, 0, 0) * BROKER_PARCEL));
                    }
                } else {
                    units = Math.min(lot.units(), (int) market.demand("sunleaf"));
                    if (units > 0) pence = Math.max(1, Math.round(units * market.unitPrice("sunleaf", basePence, 50, 0, 0) * BROKER_LOOSE));
                }
                if (units > 0) {
                    if (lot.units() == units) stock.remove(0); else stock.set(0, new Lot(lot.quality(), lot.units() - units));
                    market.consume("sunleaf", units);
                    suspicion.sold(units, sealed, watch);
                    progress.sold(units, (int) (pence / Coin.SHILLING));
                    purse += pence; earned += pence; sold += units;
                    byDay[t / DAY_SECONDS] += pence;
                    if (backroomAt < 0 && progress.reached(Progression.Tier.BACKROOM)) backroomAt = t;
                    if (workshopAt < 0 && progress.reached(Progression.Tier.WORKSHOP)) workshopAt = t;
                }
            }
            // The Watch, once a second.
            suspicion.decay(1 / 60.0, watch);
            peak = Math.max(peak, suspicion.value);
            if (suspicion.shouldCallRaid(watch)) suspicion.callRaid(now, watch);
            else if (suspicion.raidLapsed(watch)) suspicion.cancelRaid();
            else if (suspicion.raidDue(now)) {
                stock.clear(); toPress.clear();
                suspicion.raided(watch);
                raids++;
                heldUntil = t + CELL_SECONDS;
            }
            // The street, once a minute.
            if (t % 60 == 0) {
                market.tickMinute(List.of("sunleaf"), 0);
                if (t % 300 == 0) for (Customer c : customers) c.loyalty = Loyalty.settle(c.loyalty);
            }
        }
        return new Outcome(p.name(), harvested, sold, earned, spent, backroomAt, workshopAt, raids, peak, lastQuality, byDay);
    }

    static final List<Policy> POLICIES = List.of(
            new Policy("careful street, 2 frames", 2, true, false, Suspicion.HUNTED_AT, 0),
            new Policy("careful street, 4 frames", 4, true, false, Suspicion.HUNTED_AT, 0),
            new Policy("street + compost, 2 frames", 2, true, false, Suspicion.HUNTED_AT, 2),
            new Policy("greedy street, 4 frames", 4, true, false, 101, 0),
            new Policy("broker loose, 2 frames", 2, false, false, Suspicion.HUNTED_AT, 0),
            new Policy("broker parcels, 4 frames", 4, false, true, Suspicion.HUNTED_AT, 0));

    static String hours(long pence, int seconds) { return String.format(Locale.ROOT, "%.0f", pence / (seconds / 3600.0)); }

    public static void main(String[] args) {
        List<Outcome> outcomes = new ArrayList<>();
        for (Policy p : POLICIES) outcomes.add(play(p, 7));

        System.out.println("A week of play, seven days of twenty minutes, sunleaf only, one player.");
        System.out.println();
        System.out.printf(Locale.ROOT, "%-28s %6s %6s %10s %8s %14s %14s %5s %6s%n",
                "policy", "grown", "sold", "earned", "d/hour", "backroom", "workshop", "raids", "peak");
        for (Outcome o : outcomes) {
            System.out.printf(Locale.ROOT, "%-28s %6d %6d %10s %8s %14s %14s %5d %6.0f%n", o.name(), o.harvested(), o.sold(),
                    Coin.format(o.pence()), hours(o.pence(), WEEK_SECONDS), o.at(o.backroomAt()), o.at(o.workshopAt()),
                    o.raids(), o.peakSuspicion());
        }
        System.out.println();
        System.out.println("Pence earned per day:");
        for (Outcome o : outcomes) {
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%-28s", o.name()));
            for (long d : o.penceByDay()) sb.append(String.format(Locale.ROOT, " %6d", d));
            System.out.println(sb);
        }

        // The envelope. Each line is a design statement the numbers must keep true.
        Outcome careful = outcomes.get(0);
        int failures = 0;
        failures += expect(careful.backroomAt() >= 0 && careful.backroomAt() <= 3 * DAY_SECONDS,
                "a careful player with two frames opens the Backroom within three days");
        failures += expect(careful.workshopAt() >= 0 && careful.workshopAt() <= WEEK_SECONDS,
                "and the Workshop within the week");
        failures += expect(careful.workshopAt() - careful.backroomAt() >= DAY_SECONDS,
                "with at least a day between the two, so each tier is felt");
        failures += expect(careful.raids() == 0, "and is never raided while laying low at Hunted");
        Outcome greedy = outcomes.get(3);
        failures += expect(greedy.raids() >= 1, "a player who never lays low is raided at least once in the week");
        Outcome parcels = outcomes.get(5);
        failures += expect(parcels.pence() < outcomes.get(1).pence(),
                "parcels to the broker earn less than the same frames sold by hand to regulars");
        if (failures > 0) {
            System.out.println(failures + " balance expectations failed.");
            System.exit(1);
        }
        System.out.println("PASS: the week stays inside the design's envelope.");
    }

    private static int expect(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        return ok ? 0 : 1;
    }
}
