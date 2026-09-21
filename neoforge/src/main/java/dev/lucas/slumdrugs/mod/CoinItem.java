package dev.lucas.slumdrugs.mod;

import net.minecraft.world.item.Item;

/** One denomination. Loose unless the counting house has stamped it, which is a component. */
public final class CoinItem extends Item {

    private final int pence;

    public CoinItem(Properties properties, int pence) {
        super(properties);
        this.pence = pence;
    }

    /** What one of these is worth. */
    public int pence() { return pence; }
}
