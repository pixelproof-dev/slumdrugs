package dev.lucas.slumdrugs.sim.drug;

/**
 * Product into wax-sealed parcels. A parcel is a fixed count of units under one seal, which
 * is what makes it both the wholesale quantity and a piece of evidence: the seal names the
 * hand that pressed it, and the count is the same on every one.
 */
public final class Sealing {

    /** Units under one seal. */
    public static final int UNITS_PER_PARCEL = 8;

    /** Wax spent per parcel. */
    public static final int WAX_PER_PARCEL = 1;

    private Sealing() {}

    /** How many parcels a loose count fills. */
    public static int parcels(int units) {
        return Math.max(0, units) / UNITS_PER_PARCEL;
    }

    /** What is left over once the full parcels are sealed. */
    public static int loose(int units) {
        return Math.max(0, units) % UNITS_PER_PARCEL;
    }

    /** How many parcels a stock of wax allows. */
    public static int parcelsFor(int units, int wax) {
        return Math.min(parcels(units), Math.max(0, wax) / WAX_PER_PARCEL);
    }
}
