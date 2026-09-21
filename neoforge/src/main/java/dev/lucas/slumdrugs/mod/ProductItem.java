package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.player.Condition;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A finished substance, and the only item in the mod a player puts in their mouth. Using one
 * is instant rather than an eating animation, because the interesting part is what it costs,
 * not the two seconds it takes.
 */
public final class ProductItem extends Item {

    private final String drug;

    public ProductItem(Properties properties, String drug) {
        super(properties);
        this.drug = drug;
    }

    public String drug() { return drug; }

    /** 26.3 dropped Player#displayClientMessage; the overlay flag lives on ServerPlayer. */
    static void actionBar(Player player, Component message) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server)
            server.sendSystemMessage(message, true);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        var profile = Substances.profile(drug);
        Condition condition = player.getData(ModAttachments.CONDITION.get());
        long now = level.getGameTime() * 50L;
        condition.advance(condition.lastUse, now, Condition.Settings.defaults());

        int quality = ModComponents.qualityOf(stack);

        // Too much on top of too much: the dose still lands, and it hurts rather than helps.
        boolean overdose = condition.wouldOverdose(profile.dose(), quality);
        double landed = condition.use(profile.dose(), quality,
                profile.toleranceGain(), profile.dependenceGain(), now);

        if (overdose) {
            player.hurt(level.damageSources().magic(), 6.0f);
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 300, 0));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1));
            actionBar(player, Component.translatable("message.slumdrugs.overdose")
                    .withStyle(style -> style.withColor(0xD05050)));
        } else {
            double strength = landed / Math.max(1, profile.dose());
            Substances.instances(profile, strength).forEach(player::addEffect);
            actionBar(player, Component.translatable("message.slumdrugs.used",
                    Component.translatable("item.slumdrugs.product_" + drug),
                    (int) Math.round(condition.intoxication)));
        }

        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(),
                SoundSource.PLAYERS, 0.6f, 1.1f);
        stack.consume(1, player);
        player.syncData(ModAttachments.CONDITION.get());
        return InteractionResult.SUCCESS;
    }
}
