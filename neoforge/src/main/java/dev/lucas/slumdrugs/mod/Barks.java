package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.npc.Loyalty;
import dev.lucas.slumdrugs.sim.npc.Npc;
import dev.lucas.slumdrugs.sim.player.Condition;
import dev.lucas.slumdrugs.sim.player.Progression;
import dev.lucas.slumdrugs.sim.player.Suspicion;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the people of the quarter say when you pass close. A line is picked from what they
 * can see of you: a regular's loyalty and whether you carry what they want, the street's
 * demand for a broker, your dependence for the healer, your suspicion for a constable, a
 * crew member's mood and standing. Nothing here changes anything; it is how the state of the
 * street is read without a screen.
 *
 * <p>One line per person every few minutes, and never more than one every half minute at the
 * listener, so the street talks without nagging. Lines live in the lang files under
 * {@code bark.slumdrugs.<context>.<n>}, three per context.
 */
public final class Barks {

    private static final double RANGE = 4.0;
    private static final long NPC_COOLDOWN = 20L * 60 * 3;
    private static final long PLAYER_COOLDOWN = 20L * 30;
    static final int VARIANTS = 3;

    private static final Map<UUID, Long> lastByNpc = new HashMap<>();
    private static final Map<UUID, Long> lastByPlayer = new HashMap<>();

    private Barks() {}

    /** Called once a second for each of ours with the nearest player; says something when it fits. */
    static void maybe(ServerLevel level, Villager villager, NpcData data, ServerPlayer player) {
        long now = level.getGameTime();
        if (villager.distanceTo(player) > RANGE || !villager.hasLineOfSight(player)) return;
        if (now - lastByNpc.getOrDefault(villager.getUUID(), -NPC_COOLDOWN) < NPC_COOLDOWN) return;
        if (now - lastByPlayer.getOrDefault(player.getUUID(), -PLAYER_COOLDOWN) < PLAYER_COOLDOWN) return;

        String context = context(level, villager, data, player);
        if (context == null) return;
        lastByNpc.put(villager.getUUID(), now);
        lastByPlayer.put(player.getUUID(), now);

        int n = 1 + level.getRandom().nextInt(VARIANTS);
        player.sendSystemMessage(Component.translatable("bark.slumdrugs.format", villager.getName(),
                        Component.translatable("bark.slumdrugs." + context + "." + n))
                .withStyle(style -> style.withColor(0xA8A8A0).withItalic(true)));
    }

    /** Which line fits: the role first, then what they can see of the player. Null for no line. */
    static String context(ServerLevel level, Villager villager, NpcData data, ServerPlayer player) {
        Suspicion.Level heat = Watch.of(player).level(Tuning.suspicion());
        boolean hot = heat.ordinal() >= Suspicion.Level.WATCHED.ordinal();
        return switch (data.role()) {
            case CUSTOMER -> {
                double loyalty = data.loyalty();
                if (Loyalty.lost(loyalty)) yield "customer.lost";
                if (hot) yield "customer.nervous";
                boolean carrying = carrying(player, NpcTrades.preferred(villager));
                if (loyalty >= Loyalty.STANDING_ORDER) yield "customer.devoted";
                if (loyalty >= 60) yield "customer.warm";
                if (loyalty <= 35) yield "customer.cool";
                yield carrying ? "customer.wanting" : "customer.indifferent";
            }
            case BROKER -> {
                if (hot) yield "broker.hot";
                var market = Market.of(level);
                double low = 1, high = 0;
                for (String drug : ModItems.SUBSTANCES) {
                    low = Math.min(low, market.ratio(drug));
                    high = Math.max(high, market.ratio(drug));
                }
                if (low < 0.3) yield "broker.flooded";
                if (high > 0.9) yield "broker.hungry";
                yield "broker.idle";
            }
            case TRADER -> {
                if (level.isRaining()) yield "trader.rain";
                Progression progress = player.getData(ModAttachments.PROGRESSION.get());
                yield progress.tier(Tuning.progression()) == Progression.Tier.HAND_TO_MOUTH ? "trader.new" : "trader.regular";
            }
            case HEALER -> {
                Condition condition = player.getData(ModAttachments.CONDITION.get());
                if (condition.dependence >= 60) yield "healer.worried";
                if (condition.dependence >= 25) yield "healer.gentle";
                yield "healer.idle";
            }
            case CONSTABLE -> switch (heat) {
                case RAID, HUNTED -> "constable.hunted";
                case WATCHED -> "constable.watched";
                case NOTICED -> "constable.noticed";
                case CLEAR -> Watch.of(player).bribes > 0 ? "constable.bought" : "constable.clear";
            };
            case BRUISER, LIEUTENANT -> switch (data.stance()) {
                case HOSTILE -> null;
                case DEMANDING -> "crew.demanding";
                case WARY -> "crew.wary";
                case CALM -> Crews.standing(player, data) >= 30 ? "crew.friendly" : "crew.calm";
            };
            case RESIDENT -> {
                if (hot) yield "resident.hot";
                yield level.isDarkOutside() ? "resident.night" : "resident.day";
            }
        };
    }

    private static boolean carrying(ServerPlayer player, String drug) {
        var item = ModItems.get("product_" + drug).get();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            if (inventory.getItem(slot).is(item)) return true;
        return false;
    }
}
