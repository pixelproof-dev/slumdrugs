package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.player.Condition;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The healer's draught. It holds withdrawal off for a while and takes a little off dependence
 * and tolerance, and it will not stack: one at a time, which is what keeps it help rather
 * than a cure. The rule is {@link Condition#remedy}.
 */
public final class RemedyItem extends Item {

    public RemedyItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        Condition condition = player.getData(ModAttachments.CONDITION.get());
        long now = level.getGameTime() * 50L;
        if (!condition.remedy(now, Tuning.REMEDY_DEPENDENCE.get(), Tuning.REMEDY_TOLERANCE.get(),
                Tuning.REMEDY_MINUTES.get() * 60000L)) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.remedy_working"));
            return InteractionResult.FAIL;
        }

        // The shakes stop at once; the effects the ticker laid on come off with them.
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.MINING_FATIGUE);
        player.removeEffect(MobEffects.WEAKNESS);
        ProductItem.actionBar(player, Component.translatable("message.slumdrugs.remedy_taken")
                .withStyle(s -> s.withColor(0x70B090)));

        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(),
                SoundSource.PLAYERS, 0.6f, 0.9f);
        stack.consume(1, player);
        player.syncData(ModAttachments.CONDITION.get());
        return InteractionResult.SUCCESS;
    }
}
