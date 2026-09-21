package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.economy.Coin;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Arrest as a state rather than a death. When the Watch arrives with a constable to do the
 * taking, the player is held where they stand for the cell's time: walk off and you are
 * walked back. Bail in stamped coin, held in hand and used, ends it early. That is the first
 * thing stamped coin buys, and the reason the counting house takes its tenth.
 *
 * <p>No gaol building yet: the cell is wherever you were caught. When settlements exist the
 * Watch will walk you to theirs.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Gaol {

    private static final double LEASH = 1.5;

    private Gaol() {}

    public static Custody of(ServerPlayer player) { return player.getData(ModAttachments.CUSTODY.get()); }

    public static boolean held(ServerPlayer player) {
        return of(player).held(player.level().getGameTime() * 50L);
    }

    /** The Watch takes the player in, here, for the configured time. */
    public static void take(ServerPlayer player) {
        int seconds = Tuning.CELL_SECONDS.get();
        if (seconds <= 0) return;
        long now = player.level().getGameTime() * 50L;
        Custody custody = new Custody(now + seconds * 1000L, player.getX(), player.getY(), player.getZ(),
                Tuning.BAIL_PENCE.get());
        player.setData(ModAttachments.CUSTODY.get(), custody);
        player.syncData(ModAttachments.CUSTODY.get());
        player.sendSystemMessage(Component.translatable("message.slumdrugs.taken_in", seconds, Coin.format(custody.bail()))
                .withStyle(style -> style.withColor(0xD05050)));
    }

    private static void release(ServerPlayer player, String why) {
        player.setData(ModAttachments.CUSTODY.get(), Custody.FREE);
        player.syncData(ModAttachments.CUSTODY.get());
        player.removeEffect(MobEffects.DARKNESS);
        player.sendSystemMessage(Component.translatable("message.slumdrugs." + why).withStyle(style -> style.withColor(0x90A090)));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Custody custody = of(player);
        if (custody.until() == 0) return;
        long now = player.level().getGameTime() * 50L;
        if (!custody.held(now)) {
            release(player, "released");
            return;
        }
        if (player.level().getGameTime() % 10 != 0) return;

        // Walk off and you are walked back; the cell is dark and slow.
        double dx = player.getX() - custody.x(), dz = player.getZ() - custody.z();
        if (dx * dx + dz * dz > LEASH * LEASH || Math.abs(player.getY() - custody.y()) > LEASH)
            player.teleportTo(custody.x(), custody.y(), custody.z());
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0, true, false));
    }

    /** Stamped coin used in the cell is bail. Loose coin is not accepted; the magistrate keeps books. */
    @SubscribeEvent
    public static void bail(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = event.getItemStack();
        if (!Purse.isCoin(held) || !held(player)) return;
        event.setCanceled(true);

        Custody custody = of(player);
        if (!Purse.isStamped(held)) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.bail_loose"));
            return;
        }
        long offered = Purse.valueOf(held);
        if (offered < custody.bail()) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.bail_short",
                    Coin.format(custody.bail()), Coin.format(offered)));
            return;
        }
        held.consume(held.getCount(), player);
        if (offered > custody.bail()) Purse.pay(player, offered - custody.bail(), true);
        release(player, "bailed");
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}
