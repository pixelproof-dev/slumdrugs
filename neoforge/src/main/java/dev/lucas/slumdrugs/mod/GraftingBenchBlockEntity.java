package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Strain;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Two pots and a knife. A seed goes in each pot, the same substance in both, and a cut of
 * the knife crosses them: one seed comes out near the parents' mean, and both parents are
 * spent. The maths is {@link Strain#cross}; breeding toward a trait is a player's patience.
 */
public final class GraftingBenchBlockEntity extends BlockEntity {

    private ItemStack left = ItemStack.EMPTY;
    private ItemStack right = ItemStack.EMPTY;

    public GraftingBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAFTING_BENCH.get(), pos, state);
    }

    public boolean empty() { return left.isEmpty() && right.isEmpty(); }
    public boolean ready() { return !left.isEmpty() && !right.isEmpty(); }

    /**
     * One seed into the first empty pot. Refused when both are full or the substance does not
     * match the seed already potted. Returns whether it went in.
     */
    public boolean pot(ItemStack seed) {
        String drug = ModItems.drugOf("seed_", seed);
        if (drug == null) return false;
        if (left.isEmpty()) {
            left = seed.copyWithCount(1);
        } else if (right.isEmpty()) {
            if (!drug.equals(ModItems.drugOf("seed_", left))) return false;
            right = seed.copyWithCount(1);
        } else {
            return false;
        }
        setChanged();
        return true;
    }

    /** The cross. Empty unless both pots are full. */
    public ItemStack graft(RandomSource random) {
        if (!ready()) return ItemStack.EMPTY;
        double[] rolls = {random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble()};
        Strain child = Strain.cross(ModComponents.strainOf(left), ModComponents.strainOf(right), rolls);

        // The offspring takes the better parent's seed quality and the left one's grower.
        ItemStack out = left.copyWithCount(1);
        ModComponents.withQuality(out, Math.max(ModComponents.qualityOf(left), ModComponents.qualityOf(right)),
                left.get(ModComponents.GROWER.get()));
        ModComponents.withStrain(out, child);

        left = ItemStack.EMPTY;
        right = ItemStack.EMPTY;
        setChanged();
        return out;
    }

    public List<ItemStack> unload() {
        List<ItemStack> out = new ArrayList<>(2);
        if (!left.isEmpty()) out.add(left);
        if (!right.isEmpty()) out.add(right);
        left = ItemStack.EMPTY;
        right = ItemStack.EMPTY;
        setChanged();
        return out;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level == null) return;
        for (ItemStack out : unload()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), out);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!left.isEmpty()) output.store("left", ItemStack.CODEC, left);
        if (!right.isEmpty()) output.store("right", ItemStack.CODEC, right);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        left = input.read("left", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        right = input.read("right", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
}
