package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.npc.Npc;
import dev.lucas.slumdrugs.sim.npc.Standing;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * What moves standing with a crew, and what standing moves. Hitting one of theirs costs;
 * killing one costs more and stirs the rest, because the quarter remembers. Tribute in coin,
 * pressed into a crew member's hand, buys some of it back and calms the one who took it.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Crews {

    /** How far a death is felt. */
    private static final double MOURNING_RANGE = 32;

    /** How angry the crew gets when one of theirs is killed, at least. */
    private static final double MOURNING_AGGRESSION = 60;

    /** Aggression a hit adds to the one hit. */
    private static final double HIT_PROVOCATION = 15;

    private Crews() {}

    public static Standings of(Player player) {
        return player.getData(ModAttachments.STANDINGS.get());
    }

    /** Standing between a player and a villager's crew, zero when either has none. */
    public static double standing(Player player, NpcData data) {
        return data.crew().isBlank() ? 0 : of(player).with(data.crew());
    }

    private static void moved(ServerPlayer player, String crew, double delta) {
        Standings standings = of(player);
        standings.move(crew, delta);
        player.setData(ModAttachments.STANDINGS.get(), standings);
        Standing.Crew known = Standing.Crew.byId(crew);
        String label = known != null ? known.label : crew;
        ProductItem.actionBar(player, Component.translatable("message.slumdrugs.standing",
                label, (int) Math.round(standings.with(crew)))
                .withStyle(s -> s.withColor(delta < 0 ? 0xB05050 : 0x70B090)));
    }

    /** A blow lands: the one hit gets angrier, the crew thinks less of you. */
    @SubscribeEvent
    public static void hit(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof Villager villager) || !Npcs.isOurs(villager)) return;
        NpcData data = Npcs.data(villager);
        villager.setData(ModAttachments.NPC.get(), data.withAggression(Npc.provoke(data.aggression(), HIT_PROVOCATION)));
        if (!data.crew().isBlank()) moved(player, data.crew(), Standing.HIT);
    }

    /** One of theirs dies by a player's hand: the crew inherits his anger, and remembers. */
    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !Npcs.isOurs(villager)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        NpcData dead = Npcs.data(villager);
        if (dead.crew().isBlank()) return;

        moved(player, dead.crew(), Standing.KILL);
        double inherited = Math.max(MOURNING_AGGRESSION, dead.aggression());
        AABB box = villager.getBoundingBox().inflate(MOURNING_RANGE);
        for (Villager other : level.getEntitiesOfClass(Villager.class, box, Npcs::isOurs)) {
            NpcData data = Npcs.data(other);
            if (!data.crew().equals(dead.crew()) || data.aggression() >= inherited) continue;
            other.setData(ModAttachments.NPC.get(), data.withAggression(inherited));
        }
    }

    /** Coin into a crew member's hand: they calm down, and the crew thinks better of you. */
    @SubscribeEvent
    public static void tribute(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof Villager villager) || !Npcs.isOurs(villager)) return;
        ItemStack held = event.getItemStack();
        if (!Purse.isCoin(held)) return;
        NpcData data = Npcs.data(villager);
        if (!data.role().hostileCapable || data.crew().isBlank()) return;

        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        long pence = Purse.valueOf(held);
        held.consume(held.getCount(), player);
        double points = Standing.tribute(pence);
        villager.setData(ModAttachments.NPC.get(), data.withAggression(Npc.appease(data.aggression(), points * 3)));
        ProductItem.actionBar(player, Component.translatable("message.slumdrugs.tribute",
                villager.getName(), Coin.format(pence)));
        moved(player, data.crew(), points);
        villager.level().playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8f, 1.0f);
    }
}
