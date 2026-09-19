package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Refining;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Spins a batch apart, in the shape of a brewing stand: a medium on top, fuel at the side,
 * three vessels below. The rules are {@link Refining}'s — this class moves items and counts
 * ticks, and decides nothing about what a batch is worth.
 */
public final class CentrifugeBlockEntity extends BaseContainerBlockEntity {

    /** Charcoal is the separating medium; coal in the fuel slot halves the run. */
    public static final int REAGENT_QUALITY = 50;

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

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CENTRIFUGE.get(), pos, state);
    }

    @Override protected Component getDefaultName() { return Component.translatable("block.slumdrugs.centrifuge"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return CentrifugeMenu.SIZE; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CentrifugeMenu(containerId, inventory, this, data);
    }

    /** Which substance a vessel holds, or null if it holds nothing we can separate. */
    private static String vesselDrug(ItemStack stack) {
        for (String drug : ModItems.SUBSTANCES) {
            if (!ModItems.CROPS.contains(drug)) continue;
            if (stack.is(ModItems.get("dried_" + drug).get())) return drug;
        }
        return null;
    }

    private boolean hasWork() {
        if (!items.get(CentrifugeMenu.SLOT_REAGENT).is(Items.CHARCOAL)) return false;
        for (int i = 0; i < CentrifugeMenu.VESSELS; i++)
            if (vesselDrug(items.get(CentrifugeMenu.SLOT_VESSEL_FIRST + i)) != null) return true;
        return false;
    }

    private boolean powered() { return items.get(CentrifugeMenu.SLOT_FUEL).is(Items.COAL); }

    public void serverTick(Level level) {
        if (!hasWork()) {
            if (progressTicks != 0 || totalTicks != 0) {
                progressTicks = 0;
                totalTicks = 0;
                setChanged();
            }
            return;
        }

        if (totalTicks == 0) totalTicks = Refining.Method.SEPARATE.seconds(powered()) * 20;
        if (++progressTicks < totalTicks) return;

        finish();
        progressTicks = 0;
        totalTicks = 0;
        setChanged();
    }

    /** Separates every loaded vessel, spends one charcoal and one coal if it was used. */
    private void finish() {
        boolean didWork = false;
        for (int i = 0; i < CentrifugeMenu.VESSELS; i++) {
            int slot = CentrifugeMenu.SLOT_VESSEL_FIRST + i;
            ItemStack input = items.get(slot);
            String drug = vesselDrug(input);
            if (drug == null) continue;

            var result = Refining.run(Refining.Method.SEPARATE,
                    new Refining.Batch(ModComponents.qualityOf(input), input.getCount()),
                    REAGENT_QUALITY, 0);

            ItemStack product = new ItemStack(ModItems.get("product_" + drug).get(), result.units());
            String grower = input.get(ModComponents.GROWER.get());
            items.set(slot, ModComponents.withQuality(product, result.quality(), grower));
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
