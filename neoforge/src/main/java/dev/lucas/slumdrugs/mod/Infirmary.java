package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.npc.Npc;
import dev.lucas.slumdrugs.sim.player.Condition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The healer's treatment: stamped coin in hand, clicked on the healer, buys a real cut to
 * dependence and a long hold on withdrawal. Stamped, because the infirmary keeps books and
 * is the second thing the counting house's tenth is for. The rule is {@link Condition#treat}.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Infirmary {

    private Infirmary() {}

    @SubscribeEvent
    public static void treat(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof Villager villager) || !Npcs.isOurs(villager)) return;
        if (Npcs.data(villager).role() != Npc.Role.HEALER) return;
        ItemStack held = event.getItemStack();
        if (!Purse.isCoin(held)) return;

        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long price = Tuning.TREATMENT_PENCE.get();
        if (!Purse.isStamped(held)) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.treatment_loose", villager.getName()));
            return;
        }
        long offered = Purse.valueOf(held);
        if (offered < price) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.treatment_short",
                    Coin.format(price), Coin.format(offered)));
            return;
        }

        Condition condition = player.getData(ModAttachments.CONDITION.get());
        long now = player.level().getGameTime() * 50L;
        if (!condition.treat(now, Tuning.TREATMENT_DEPENDENCE.get(), Condition.TREATMENT_MILLIS)) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.treatment_working"));
            return;
        }
        held.consume(held.getCount(), player);
        if (offered > price) Purse.pay(player, offered - price, true);
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.MINING_FATIGUE);
        player.removeEffect(MobEffects.WEAKNESS);
        player.syncData(ModAttachments.CONDITION.get());

        ProductItem.actionBar(player, Component.translatable("message.slumdrugs.treated",
                villager.getName(), (int) condition.dependence).withStyle(s -> s.withColor(0x70B090)));
        villager.level().playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_WORK_CLERIC, SoundSource.NEUTRAL, 0.8f, 1.0f);
    }
}
