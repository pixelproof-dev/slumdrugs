package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Refining;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Spins a batch apart. Charcoal is the separating medium; coal in the fuel slot halves the run. */
public final class CentrifugeBlockEntity extends RefineryBlockEntity {

    public static final int REAGENT_QUALITY = 50;

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CENTRIFUGE.get(), pos, state);
    }

    @Override protected Component getDefaultName() { return Component.translatable("block.slumdrugs.centrifuge"); }
    @Override protected Refining.Method method() { return Refining.Method.SEPARATE; }
    @Override protected Item reagent() { return Items.CHARCOAL; }
    @Override protected int reagentQuality() { return REAGENT_QUALITY; }
}
