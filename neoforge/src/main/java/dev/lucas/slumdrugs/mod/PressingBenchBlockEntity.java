package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Refining;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/**
 * A screw press worked by hand. One batch of dried material sits under the plate; each pull
 * on the screw is a stroke, and enough strokes add up to one run of {@link Refining.Method#PRESS}.
 * Nothing ticks — the press only moves when someone moves it.
 */
public final class PressingBenchBlockEntity extends BlockEntity {

    /** Units one batch holds. */
    public static final int BATCH_UNITS = 16;

    /** Game ticks between strokes that count. Faster than this is just rattling the handle. */
    public static final int STROKE_TICKS = 8;

    private ItemStack batch = ItemStack.EMPTY;
    private int strokes;
    private long lastStroke;

    public PressingBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRESSING_BENCH.get(), pos, state);
    }

    public boolean empty() { return batch.isEmpty(); }
    public int strokes() { return strokes; }
    public int strokesNeeded() { return Refining.Method.PRESS.strokes(); }

    /**
     * Puts dried material under the plate: a new batch, or more of the same onto one that has
     * not been worked yet. Returns the units taken, zero if the press will not take them.
     */
    public int load(ItemStack stack) {
        int room;
        if (batch.isEmpty()) {
            room = BATCH_UNITS;
        } else if (strokes == 0 && ItemStack.isSameItemSameComponents(batch, stack)) {
            room = BATCH_UNITS - batch.getCount();
        } else {
            return 0;
        }
        int units = Math.min(room, stack.getCount());
        if (units <= 0) return 0;
        if (batch.isEmpty()) batch = stack.copyWithCount(units); else batch.grow(units);
        setChanged();
        return units;
    }

    /** One pull on the screw. False if there is nothing under the plate or the last pull was too recent. */
    public boolean stroke(Level level) {
        long now = level.getGameTime();
        if (batch.isEmpty() || now - lastStroke < STROKE_TICKS) return false;
        strokes++;
        lastStroke = now;
        setChanged();
        return true;
    }

    public boolean done() { return !batch.isEmpty() && strokes >= strokesNeeded(); }

    /**
     * Resolves the batch: product at what the press makes of it, and compost for whatever the
     * method wasted. Clears the plate.
     */
    public List<ItemStack> press() {
        String drug = ModItems.drugOf("dried_", batch);
        if (drug == null) return List.of(unload());

        var result = Refining.run(Refining.Method.PRESS,
                new Refining.Batch(ModComponents.qualityOf(batch), batch.getCount()), 0, 0);
        String grower = batch.get(ModComponents.GROWER.get());

        var out = new java.util.ArrayList<ItemStack>(2);
        out.add(ModComponents.withQuality(
                new ItemStack(ModItems.get("product_" + drug).get(), result.units()), result.quality(), grower));
        if (result.wasteUnits() > 0)
            out.add(ModComponents.withQuality(
                    new ItemStack(ModItems.get("fertilizer").get(), result.wasteUnits()), result.compostQuality(), grower));

        batch = ItemStack.EMPTY;
        strokes = 0;
        setChanged();
        return out;
    }

    /** Takes the batch back out, unpressed. */
    public ItemStack unload() {
        ItemStack out = batch;
        batch = ItemStack.EMPTY;
        strokes = 0;
        setChanged();
        return out;
    }

    /** Breaking the bench drops the batch unpressed. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !batch.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), batch);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!batch.isEmpty()) output.store("batch", ItemStack.CODEC, batch);
        output.putInt("strokes", strokes);
        output.putLong("last_stroke", lastStroke);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        batch = input.read("batch", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        strokes = input.getIntOr("strokes", 0);
        lastStroke = input.getLongOr("last_stroke", 0);
    }
}
