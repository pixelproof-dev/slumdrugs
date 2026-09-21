package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.drug.Sealing;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A wax-sealed parcel: a fixed count of one product under one seal. Using it breaks the seal
 * and hands back the units, at the quality and under the grower's name they went in with.
 * The seal itself is gone once broken, which is the point of it.
 */
public final class ParcelItem extends Item {

    private final String drug;

    public ParcelItem(Properties properties, String drug) {
        super(properties);
        this.drug = drug;
    }

    public String drug() { return drug; }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack parcel = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        ItemStack product = ModComponents.withQuality(
                new ItemStack(ModItems.get("product_" + drug).get(), Sealing.UNITS_PER_PARCEL),
                ModComponents.qualityOf(parcel), parcel.get(ModComponents.GROWER.get()));
        parcel.consume(1, player);
        if (!player.getInventory().add(product)) player.drop(product, false, Prediction.SERVER_ONLY);

        level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS,
                SoundSource.PLAYERS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }
}
