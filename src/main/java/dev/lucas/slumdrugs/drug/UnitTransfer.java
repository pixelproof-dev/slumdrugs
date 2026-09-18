package dev.lucas.slumdrugs.drug;

/** Exact package splitting: untouched packages stay sealed; one partial package yields change. */
public record UnitTransfer(int consumed, int remainingPackages, int looseChange) {
    public static UnitTransfer plan(int packages, int perPackage, int wanted) {
        if (packages < 0 || perPackage < 1 || wanted < 0)
            throw new IllegalArgumentException("Invalid transfer quantities");
        int taken = (int) Math.min((long) packages * perPackage, wanted);
        int opened = taken == 0 ? 0 : (taken - 1) / perPackage + 1;
        return new UnitTransfer(taken, packages - opened, opened * perPackage - taken);
    }
}
