package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.player.Condition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runs the condition forward and lets the player feel it without a single pixel of interface:
 * vanilla effects, the action bar and time.
 *
 * <p>Withdrawal is deliberately shallow — a nuisance that pushes you toward a fix or toward
 * stopping, never a spiral a player cannot climb out of. Recovery runs whether they use again
 * or not.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class ConditionTicker {

    /** Once a second is plenty for something measured in minutes. */
    private static final int INTERVAL = 20;

    /** Roughly every two minutes while craving. */
    private static final int CRAVING_INTERVAL = 20 * 120;

    private ConditionTicker() {}

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var level = player.level();
        long gameTime = level.getGameTime();
        if (gameTime % INTERVAL != 0) return;

        Condition condition = player.getData(ModAttachments.CONDITION.get());
        var settings = Tuning.condition();
        long now = gameTime * 50L;
        long previous = Math.max(condition.lastUse, now - INTERVAL * 50L);
        condition.advance(previous, now, settings);
        // A long enough time clean lowers the ceiling for good.
        if (condition.cleanStreak(now, Tuning.CLEAN_STREAK_DAYS.get() * 20L * 60000L))
            player.sendSystemMessage(Component.translatable("message.slumdrugs.clean_streak",
                    Tuning.CLEAN_STREAK_DAYS.get(), (int) condition.toleranceCeiling).withStyle(s -> s.withColor(0x70B090)));
        // Once a second, to the owner only: what the HUD draws.
        player.syncData(ModAttachments.CONDITION.get());

        int severity = Tonic.on() ? 0 : condition.withdrawalSeverity(now, settings);
        if (severity > 0) {
            applyWithdrawal(player, severity);
            if (gameTime % CRAVING_INTERVAL == 0)
                ProductItem.actionBar(player, Component.translatable(
                        "message.slumdrugs.withdrawal_" + severity).withStyle(s -> s.withColor(0x9A6BA8)));
        } else if (!Tonic.on() && condition.craving(now, settings) && gameTime % CRAVING_INTERVAL == 0) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.craving")
                    .withStyle(s -> s.withColor(0xB0A070)));
        }
    }

    /** Mild and short, reapplied while it lasts. Nothing here can kill anyone. */
    private static void applyWithdrawal(ServerPlayer player, int severity) {
        int duration = INTERVAL * 3;
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 0, true, false));
        if (severity >= 2)
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, duration, 0, true, false));
        if (severity >= 3)
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, true, false));
    }

    /** A full night speeds recovery for a while — the one thing that makes stopping easier. */
    @SubscribeEvent
    public static void wakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getData(ModAttachments.CONDITION.get()).slept(player.level().getGameTime() * 50L);
    }
}
