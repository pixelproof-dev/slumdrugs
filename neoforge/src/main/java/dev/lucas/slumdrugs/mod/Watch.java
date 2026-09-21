package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.npc.Npc;
import dev.lucas.slumdrugs.sim.player.Suspicion;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * The Watch, as behaviour. Suspicion fades on its own; past the top of the scale the bell
 * rings, and two minutes later the Watch arrives: whatever goods the player is carrying are
 * taken, and any constable nearby is stirred up. Lay low before the bell's time is up and it
 * falls silent. No block is ever touched, and nothing happens without warning.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Watch {

    private static final int INTERVAL = 20;
    private static final double CONSTABLE_RANGE = 24;

    /** Stages of the chain the Watch will take off a player. */
    private static final List<String> CONTRABAND = List.of("raw_", "dried_", "product_", "package_");

    private Watch() {}

    public static Suspicion of(ServerPlayer player) {
        return player.getData(ModAttachments.SUSPICION.get());
    }

    /** A sale has been seen. Called by whoever made it; a subtle line is seen less. */
    public static void noticed(ServerPlayer player, int units, boolean sealed, double subtlety) {
        Suspicion suspicion = of(player);
        Suspicion.Level before = suspicion.level();
        suspicion.sold((int) Math.round(units * Math.max(0, subtlety)), sealed);
        player.syncData(ModAttachments.SUSPICION.get());
        Suspicion.Level after = suspicion.level();
        if (after != before && after.ordinal() > before.ordinal() && after != Suspicion.Level.RAID)
            ProductItem.actionBar(player, Component.translatable(
                    "message.slumdrugs.suspicion_" + after.name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(s -> s.withColor(0xC0A050)));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        long gameTime = level.getGameTime();
        if (gameTime % INTERVAL != 0) return;

        Suspicion suspicion = of(player);
        long now = gameTime * 50L;
        suspicion.decay(INTERVAL / 20.0 / 60.0);

        if (suspicion.shouldCallRaid()) {
            suspicion.callRaid(now);
            player.sendSystemMessage(Component.translatable("message.slumdrugs.raid_called")
                    .withStyle(s -> s.withColor(0xD05050)));
            level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 0.7f);
        } else if (suspicion.raidLapsed()) {
            suspicion.cancelRaid();
            player.sendSystemMessage(Component.translatable("message.slumdrugs.raid_lapsed")
                    .withStyle(s -> s.withColor(0x90A090)));
        } else if (suspicion.raidDue(now)) {
            raid(level, player, suspicion);
        }

        if (suspicion.level().ordinal() >= Suspicion.Level.WATCHED.ordinal() && gameTime % (INTERVAL * 10) == 0)
            stirConstables(level, player, 30);

        player.syncData(ModAttachments.SUSPICION.get());
    }

    /** The Watch arrives: goods off the player, constables on them, suspicion back to watched. */
    private static void raid(ServerLevel level, ServerPlayer player, Suspicion suspicion) {
        int taken = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!contraband(stack)) continue;
            taken += stack.getCount();
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        suspicion.raided();
        stirConstables(level, player, 60);

        player.sendSystemMessage(Component.translatable("message.slumdrugs.raided", taken)
                .withStyle(s -> s.withColor(0xD05050)));
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.5f, 0.8f);
    }

    private static boolean contraband(ItemStack stack) {
        for (String stage : CONTRABAND) if (ModItems.drugOf(stage, stack) != null) return true;
        return false;
    }

    /** Constables in range come up to at least this much aggression: wary, or demanding. */
    private static void stirConstables(ServerLevel level, ServerPlayer player, double floor) {
        AABB box = player.getBoundingBox().inflate(CONSTABLE_RANGE);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, box, Npcs::isOurs)) {
            NpcData data = Npcs.data(villager);
            if (data.role() != Npc.Role.CONSTABLE || data.aggression() >= floor) continue;
            villager.setData(ModAttachments.NPC.get(), data.withAggression(floor));
        }
    }
}
