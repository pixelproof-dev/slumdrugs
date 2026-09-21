package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Drying;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Three rails, each holding one bundle of raw harvest and the moment it went up. Nothing ticks:
 * what a bundle is worth is a function of how long it has hung, and {@link Drying} answers that
 * when someone takes it down. The block state mirrors how many rails are taken, so a player
 * reads the loft from across the room.
 */
public final class DryingLoftBlockEntity extends BlockEntity {

    public static final int RAILS = 3;

    /** Units one rail takes. A harvest from a well-kept frame fills a rail or two. */
    public static final int BUNDLE_UNITS = 8;

    private NonNullList<ItemStack> bundles = NonNullList.withSize(RAILS, ItemStack.EMPTY);
    private final long[] hungAt = new long[RAILS];

    public DryingLoftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRYING_LOFT.get(), pos, state);
    }

    /** Rails in use. */
    public int hung() {
        int n = 0;
        for (ItemStack bundle : bundles) if (!bundle.isEmpty()) n++;
        return n;
    }

    public double secondsHung(Level level, int rail) {
        return Math.max(0, level.getGameTime() - hungAt[rail]) / 20.0;
    }

    public boolean ready(Level level, int rail) {
        return !bundles.get(rail).isEmpty() && Drying.ready(secondsHung(level, rail), Drying.SECONDS);
    }

    public boolean anyReady(Level level) {
        for (int rail = 0; rail < RAILS; rail++) if (ready(level, rail)) return true;
        return false;
    }

    /** 0-1 for the bundle furthest from done, which is the one worth waiting for. */
    public double leastProgress(Level level) {
        double least = 1;
        for (int rail = 0; rail < RAILS; rail++)
            if (!bundles.get(rail).isEmpty())
                least = Math.min(least, Drying.progress(secondsHung(level, rail), Drying.SECONDS));
        return least;
    }

    /**
     * Hangs up to a rail's worth from the stack on the first free rail. Returns how many units
     * went up, zero when every rail is taken. The stack itself is not touched; the caller
     * consumes from it, so creative mode keeps its copy.
     */
    public int hang(Level level, ItemStack stack) {
        for (int rail = 0; rail < RAILS; rail++) {
            if (!bundles.get(rail).isEmpty()) continue;
            int units = Math.min(BUNDLE_UNITS, stack.getCount());
            bundles.set(rail, stack.copyWithCount(units));
            hungAt[rail] = level.getGameTime();
            setChanged();
            return units;
        }
        return 0;
    }

    /**
     * Takes a rail down: dried at what it earned if it is ready, otherwise the raw bundle as it
     * went up. Empty if the rail was empty.
     */
    public ItemStack takeDown(Level level, int rail) {
        ItemStack bundle = bundles.get(rail);
        if (bundle.isEmpty()) return ItemStack.EMPTY;

        double hung = secondsHung(level, rail);
        ItemStack out;
        String drug = ModItems.drugOf("raw_", bundle);
        if (drug != null && Drying.ready(hung, Drying.SECONDS)) {
            out = ModComponents.inherit(bundle, ModComponents.withQuality(
                    new ItemStack(ModItems.get("dried_" + drug).get(), bundle.getCount()),
                    Drying.quality(ModComponents.qualityOf(bundle), hung, Drying.SECONDS),
                    bundle.get(ModComponents.GROWER.get())));
        } else {
            out = bundle.copy();
        }
        bundles.set(rail, ItemStack.EMPTY);
        hungAt[rail] = 0;
        setChanged();
        return out;
    }

    /** Breaking the loft drops the bundles as they hang: raw, whatever they had earned. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, bundles);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, bundles);
        for (int rail = 0; rail < RAILS; rail++) output.putLong("hung_" + rail, hungAt[rail]);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        bundles = NonNullList.withSize(RAILS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, bundles);
        for (int rail = 0; rail < RAILS; rail++) hungAt[rail] = input.getLongOr("hung_" + rail, 0);
    }
}
