package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Refining;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Boils a batch down. Half the volume comes through, and it is the strongest thing a player
 * can make. Sugar is the wash it works on; coal under it halves the run. The design calls
 * this the alembic and gives it essence to make; until essence exists as an item, the still
 * makes product at what the DISTIL rule says it is worth.
 */
public final class StillBlockEntity extends RefineryBlockEntity {

    /** Sugar is a good wash, better than the centrifuge's charcoal is a medium. */
    public static final int REAGENT_QUALITY = 70;

    public StillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STILL.get(), pos, state);
    }

    @Override protected Component getDefaultName() { return Component.translatable("block.slumdrugs.still"); }
    @Override protected Refining.Method method() { return Refining.Method.DISTIL; }
    @Override protected Item reagent() { return Items.SUGAR; }
    @Override protected int reagentQuality() { return REAGENT_QUALITY; }
}
