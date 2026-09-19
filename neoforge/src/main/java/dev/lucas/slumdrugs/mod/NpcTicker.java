package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.npc.Npc;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Turns aggression into behaviour, without a bar over anyone's head: an angry crew member
 * watches you, then follows you, then blocks your way, then swings. A player reads the street
 * from posture and distance, which is the whole point of the model in {@link Npc}.
 *
 * <p>Movement is driven straight through the navigation rather than through the villager brain.
 * That is a knowing simplification: the brain still wants them at work and will pull back when
 * we stop pushing, which reads acceptably as a crew member losing interest.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class NpcTicker {

    private static final int INTERVAL = 20;
    private static final double NOTICE_RANGE = 12;
    private static final double REACH = 2.5;
    private static final int DEMAND_INTERVAL = 20 * 10;
    private static final int ATTACK_INTERVAL = 20;

    private NpcTicker() {}

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (!Npcs.isOurs(villager)) return;

        long gameTime = level.getGameTime();
        if (gameTime % INTERVAL != 0) return;

        NpcData data = Npcs.data(villager);

        // Moods drift back toward where this crew's standing puts them. Crew standing is not
        // tracked yet, so neutral is the resting point for now.
        double resting = Npc.restingAggression(data.role(), 0, 0);
        double settled = Npc.settle(data.aggression(), resting, INTERVAL / 20.0 / 60.0);
        if (settled != data.aggression()) {
            data = data.withAggression(settled);
            villager.setData(ModAttachments.NPC.get(), data);
        }

        Npc.Stance stance = data.stance();
        if (stance == Npc.Stance.CALM) return;

        Player nearest = level.getNearestPlayer(villager, NOTICE_RANGE);
        if (nearest == null) return;

        villager.getLookControl().setLookAt(nearest);
        if (stance == Npc.Stance.WARY) return;

        double distance = villager.distanceTo(nearest);
        if (distance > REACH) {
            villager.getNavigation().moveTo(nearest, stance == Npc.Stance.HOSTILE ? 0.9 : 0.6);
            return;
        }

        if (stance == Npc.Stance.DEMANDING) {
            if (gameTime % DEMAND_INTERVAL == 0 && nearest instanceof ServerPlayer player)
                ProductItem.actionBar(player, Component.translatable("message.slumdrugs.demand",
                        villager.getName()).withStyle(style -> style.withColor(0xC08040)));
            return;
        }

        if (gameTime % ATTACK_INTERVAL == 0) {
            villager.swingForAttack(InteractionHand.MAIN_HAND);
            nearest.hurt(level.damageSources().mobAttack(villager), 3.0f);
        }
    }

    /** An angry villager does not trade. Anger closing the shop is the point of appeasing them. */
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager) || !Npcs.isOurs(villager)) return;
        if (Npcs.data(villager).stance() == Npc.Stance.CALM) return;

        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player)
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.refuses",
                    villager.getName()).withStyle(style -> style.withColor(0xB05050)));
    }
}
