package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Sealing;
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
 * A lever press with a pot of wax beside it. Product stacks up on the bench, honeycomb goes
 * in the pot, and each pull seals one parcel under the name of whoever pulled. The counts
 * are {@link Sealing}'s.
 */
public final class SealingPressBlockEntity extends BlockEntity {

    /** Units the bench holds at once. */
    public static final int STOCK_UNITS = 64;

    /** Wax the pot holds, in honeycomb. */
    public static final int WAX_MAX = 16;

    private ItemStack stock = ItemStack.EMPTY;
    private int wax;

    public SealingPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SEALING_PRESS.get(), pos, state);
    }

    public boolean empty() { return stock.isEmpty() && wax == 0; }
    public int units() { return stock.getCount(); }
    public int wax() { return wax; }

    /** Adds product to the bench: one substance, one quality, one grower at a time. Returns the units taken. */
    public int addStock(ItemStack stack) {
        int room;
        if (stock.isEmpty()) room = STOCK_UNITS;
        else if (ItemStack.isSameItemSameComponents(stock, stack)) room = STOCK_UNITS - stock.getCount();
        else return 0;

        int units = Math.min(room, stack.getCount());
        if (units <= 0) return 0;
        if (stock.isEmpty()) stock = stack.copyWithCount(units); else stock.grow(units);
        setChanged();
        return units;
    }

    /** Puts honeycomb in the pot. Returns how many combs it took. */
    public int addWax(int combs) {
        int taken = Math.max(0, Math.min(WAX_MAX - wax, combs));
        if (taken == 0) return 0;
        wax += taken;
        setChanged();
        return taken;
    }

    public boolean enoughStock() { return stock.getCount() >= Sealing.UNITS_PER_PARCEL; }
    public boolean enoughWax() { return wax >= Sealing.WAX_PER_PARCEL; }

    /** One pull: one parcel, sealed by the named hand. Empty if there was not enough of something. */
    public ItemStack seal(String sealer) {
        if (!enoughStock() || !enoughWax()) return ItemStack.EMPTY;
        String drug = ModItems.drugOf("product_", stock);
        if (drug == null) return ItemStack.EMPTY;

        ItemStack parcel = ModComponents.withQuality(
                new ItemStack(ModItems.get("package_" + drug).get(), 1),
                ModComponents.qualityOf(stock), stock.get(ModComponents.GROWER.get()));
        parcel.set(ModComponents.SEAL.get(), sealer);
        ModComponents.withCut(parcel, ModComponents.cutOf(stock));

        stock.shrink(Sealing.UNITS_PER_PARCEL);
        if (stock.isEmpty()) stock = ItemStack.EMPTY;
        wax -= Sealing.WAX_PER_PARCEL;
        setChanged();
        return parcel;
    }

    /** Clears the bench and the pot; everything comes back as it went in. */
    public List<ItemStack> unload() {
        List<ItemStack> out = new ArrayList<>(2);
        if (!stock.isEmpty()) out.add(stock);
        if (wax > 0) out.add(new ItemStack(Items.HONEYCOMB, wax));
        stock = ItemStack.EMPTY;
        wax = 0;
        setChanged();
        return out;
    }

    /** Breaking the press drops the stock and the wax as they went in. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level == null) return;
        for (ItemStack out : unload()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), out);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!stock.isEmpty()) output.store("stock", ItemStack.CODEC, stock);
        output.putInt("wax", wax);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stock = input.read("stock", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        wax = input.getIntOr("wax", 0);
    }
}
