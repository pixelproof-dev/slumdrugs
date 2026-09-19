package dev.lucas.slumdrugs.mod;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Laid out on the brewing stand's slot positions on purpose: a player who has used a brewing
 * stand already knows where everything is.
 */
public final class CentrifugeMenu extends AbstractContainerMenu {

    public static final int SLOT_REAGENT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_VESSEL_FIRST = 2;
    public static final int VESSELS = 3;
    public static final int SIZE = SLOT_VESSEL_FIRST + VESSELS;

    private final Container container;
    private final ContainerData data;

    /** Client side: the contents arrive through the usual synchronisation. */
    public CentrifugeMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(SIZE), new SimpleContainerData(2));
    }

    public CentrifugeMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.CENTRIFUGE.get(), containerId);
        checkContainerSize(container, SIZE);
        checkContainerDataCount(data, 2);
        this.container = container;
        this.data = data;

        addSlot(new Slot(container, SLOT_REAGENT, 79, 17));   // charcoal, the separating medium
        addSlot(new Slot(container, SLOT_FUEL, 17, 17));      // coal, which halves the run
        addSlot(new Slot(container, SLOT_VESSEL_FIRST, 56, 51));
        addSlot(new Slot(container, SLOT_VESSEL_FIRST + 1, 79, 58));
        addSlot(new Slot(container, SLOT_VESSEL_FIRST + 2, 102, 51));

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));

        addDataSlots(data);
    }

    /** 0 when idle, otherwise how far through the current run we are, 0-1. */
    public float progress() {
        int total = data.get(1);
        return total <= 0 ? 0 : Math.min(1f, data.get(0) / (float) total);
    }

    @Override
    public boolean stillValid(Player player) { return container.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean fromMachine = slotIndex < SIZE;

        if (fromMachine) {
            if (!moveItemStackTo(stack, SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, SIZE, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }
}
