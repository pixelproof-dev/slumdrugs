package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.npc.Npc;
import dev.lucas.slumdrugs.sim.player.Suspicion;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Loose coin into a constable's hand. It takes some of what they have noticed off their
 * mind, calms the one who took it, and goes in the book: the design makes every bribed
 * officer a liability when the Warden's inspection reaches them, and the count is kept for
 * that day. Stamped coin is refused; a constable does not take money with a name on it.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Bribes {

    private Bribes() {}

    @SubscribeEvent
    public static void offer(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof Villager villager) || !Npcs.isOurs(villager)) return;
        NpcData data = Npcs.data(villager);
        if (data.role() != Npc.Role.CONSTABLE) return;
        ItemStack held = event.getItemStack();
        if (!Purse.isCoin(held)) return;

        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (Purse.isStamped(held)) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.bribe_stamped", villager.getName()));
            return;
        }

        long pence = Purse.valueOf(held);
        held.consume(held.getCount(), player);
        Suspicion suspicion = Watch.of(player);
        double off = suspicion.bribed(pence, Tuning.BRIBE_PER_SHILLING.get(), Tuning.BRIBE_CAP.get());
        player.syncData(ModAttachments.SUSPICION.get());
        villager.setData(ModAttachments.NPC.get(), data.withAggression(Npc.appease(data.aggression(), off * 2)));

        ProductItem.actionBar(player, Component.translatable("message.slumdrugs.bribed",
                villager.getName(), Coin.format(pence), (int) Math.round(off)));
        villager.level().playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8f, 0.9f);
    }
}
