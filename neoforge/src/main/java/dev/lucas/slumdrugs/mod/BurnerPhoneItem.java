package dev.lucas.slumdrugs.mod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A burner phone. Using it opens its screen ({@code client.PhoneScreen}): the orders regulars
 * have rung in ({@link Phone}), the contacts that have the number, and a search for the nearest
 * town — which is how a town, as rare as a stronghold (MOD-GDD.md §1), is found on purpose.
 *
 * <p>The server sends what the screen shows and decides everything it does ({@link PhoneNet});
 * the item only asks for the screen to open.
 */
public final class BurnerPhoneItem extends Item {

    public BurnerPhoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer caller)) return InteractionResult.SUCCESS;
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.4f, 1.8f);
        PhoneNet.open(caller);
        return InteractionResult.SUCCESS;
    }
}
