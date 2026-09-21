package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Cutting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * A table with a blade and a heap of filler. Product goes on the board, filler goes on the
 * heap up to equal parts, and one pull of the blade turns them into more, worse product that
 * carries how much of it is filler. The rule is {@link Cutting}'s.
 */
public final class CuttingBenchBlockEntity extends BlockEntity {

    public static final int BATCH_UNITS = 16;

    private ItemStack batch = ItemStack.EMPTY;
    private int filler;

    public CuttingBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CUTTING_BENCH.get(), pos, state);
    }

    public static boolean isFiller(ItemStack stack) {
        return stack.is(Items.SUGAR) || stack.is(Items.BONE_MEAL);
    }

    public boolean empty() { return batch.isEmpty() && filler == 0; }
    public int units() { return batch.getCount(); }
    public int filler() { return filler; }

    /** Product onto the board: one kind at a time, up to a batch. Returns the units taken. */
    public int load(ItemStack stack) {
        int room;
        if (batch.isEmpty()) room = BATCH_UNITS;
        else if (ItemStack.isSameItemSameComponents(batch, stack)) room = BATCH_UNITS - batch.getCount();
        else return 0;
        int units = Math.min(room, stack.getCount());
        if (units <= 0) return 0;
        if (batch.isEmpty()) batch = stack.copyWithCount(units); else batch.grow(units);
        setChanged();
        return units;
    }

    /** Filler onto the heap, never more than the board will take. Returns how much it took. */
    public int addFiller(int count) {
        int taken = Math.max(0, Math.min(Cutting.maxFiller(BATCH_UNITS) - filler, count));
        if (taken == 0) return 0;
        filler += taken;
        setChanged();
        return taken;
    }

    /** One pull of the blade. Empty if there is nothing on the board. */
    public ItemStack cut() {
        if (batch.isEmpty()) return ItemStack.EMPTY;
        int used = Math.min(filler, Cutting.maxFiller(batch.getCount()));
        var result = Cutting.cut(ModComponents.qualityOf(batch), batch.getCount(), used,
                ModComponents.cutOf(batch));

        ItemStack out = batch.copyWithCount(result.units());
        ModComponents.withQuality(out, result.quality(), batch.get(ModComponents.GROWER.get()));
        ModComponents.withCut(out, result.cutRatio());

        filler -= used;
        batch = ItemStack.EMPTY;
        setChanged();
        return out;
    }

    /** Board and heap back as they went in. */
    public List<ItemStack> unload() {
        List<ItemStack> out = new ArrayList<>(2);
        if (!batch.isEmpty()) out.add(batch);
        if (filler > 0) out.add(new ItemStack(Items.SUGAR, filler));
        batch = ItemStack.EMPTY;
        filler = 0;
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
        if (!batch.isEmpty()) output.store("batch", ItemStack.CODEC, batch);
        output.putInt("filler", filler);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        batch = input.read("batch", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        filler = input.getIntOr("filler", 0);
    }
}
