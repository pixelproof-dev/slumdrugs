package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Refining;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A machine in the shape of a brewing stand: a reagent on top, fuel at the side, three vessels
 * below, and one of {@link Refining}'s methods deciding what comes out. The centrifuge and the
 * still are the same machine with a different method and a different reagent; this class moves
 * items and counts ticks, and decides nothing about what a batch is worth.
 */
public abstract class RefineryBlockEntity extends BaseContainerBlockEntity {

    private NonNullList<ItemStack> items = NonNullList.withSize(CentrifugeMenu.SIZE, ItemStack.EMPTY);
    private int progressTicks;
    private int totalTicks;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return index == 0 ? progressTicks : totalTicks; }
        @Override public void set(int index, int value) {
            if (index == 0) progressTicks = value; else totalTicks = value;
        }
        @Override public int getCount() { return 2; }
    };

    protected RefineryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** How this machine refines. */
    protected abstract Refining.Method method();

    /** What goes in the top slot, and what it is worth as a reagent, 0-100. */
    protected abstract Item reagent();
    protected abstract int reagentQuality();

    /** What halves the run when it sits in the side slot. */
    protected Item fuel() { return net.minecraft.world.item.Items.COAL; }

    /** The stage this machine makes: product, or the still's essence. */
    protected String outputStage() { return "product_"; }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return CentrifugeMenu.SIZE; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CentrifugeMenu(containerId, inventory, this, data);
    }

    /** Which substance a vessel holds, or null if it holds nothing we can refine. */
    private static String vesselDrug(ItemStack stack) {
        return ModItems.drugOf("dried_", stack);
    }

    private boolean hasWork() {
        if (!items.get(CentrifugeMenu.SLOT_REAGENT).is(reagent())) return false;
        for (int i = 0; i < CentrifugeMenu.VESSELS; i++)
            if (vesselDrug(items.get(CentrifugeMenu.SLOT_VESSEL_FIRST + i)) != null) return true;
        return false;
    }

    private boolean powered() { return items.get(CentrifugeMenu.SLOT_FUEL).is(fuel()); }

    public void serverTick(Level level) {
        if (!hasWork()) {
            if (progressTicks != 0 || totalTicks != 0) {
                progressTicks = 0;
                totalTicks = 0;
                setChanged();
            }
            return;
        }

        if (totalTicks == 0) totalTicks = method().seconds(powered()) * 20;
        if (++progressTicks < totalTicks) return;

        finish();
        progressTicks = 0;
        totalTicks = 0;
        setChanged();
    }

    /** Refines every loaded vessel, spends one reagent and one fuel if it was used. */
    private void finish() {
        boolean didWork = false;
        for (int i = 0; i < CentrifugeMenu.VESSELS; i++) {
            int slot = CentrifugeMenu.SLOT_VESSEL_FIRST + i;
            ItemStack input = items.get(slot);
            String drug = vesselDrug(input);
            if (drug == null) continue;

            var result = Refining.run(method(),
                    new Refining.Batch(ModComponents.qualityOf(input), input.getCount()),
                    reagentQuality(), 0);

            ItemStack product = new ItemStack(ModItems.get(outputStage() + drug).get(), result.units());
            String grower = input.get(ModComponents.GROWER.get());
            items.set(slot, ModComponents.inherit(input, ModComponents.withQuality(product, result.quality(), grower)));
            didWork = true;
        }
        if (!didWork) return;

        items.get(CentrifugeMenu.SLOT_REAGENT).shrink(1);
        if (powered()) items.get(CentrifugeMenu.SLOT_FUEL).shrink(1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("progress", progressTicks);
        output.putInt("total", totalTicks);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(CentrifugeMenu.SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        progressTicks = input.getIntOr("progress", 0);
        totalTicks = input.getIntOr("total", 0);
    }
}
