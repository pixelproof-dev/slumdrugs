package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.npc.Hire;
import dev.lucas.slumdrugs.sim.npc.Npc;
import dev.lucas.slumdrugs.sim.npc.Turf;
import dev.lucas.slumdrugs.sim.player.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * The hired hand in the world. A Workshop grower clicks a resident with a stamped sovereign
 * and the resident works for them: once a second they look for a station within reach, walk
 * to it, and work it, one action a second. Dry bundles come down off the loft; a loaded press
 * gets a pull, and the last pull drops the product where a player's would. Each new day they
 * take two shillings from the nearest storage crate, smallest coin first, keeping the change;
 * a day with no wage in reach and they are a resident again. The rules are {@link Hire}'s.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Hands {

    private static final double WORK_REACH = 2.8;

    private Hands() {}

    /** A stamped sovereign into a resident's hand, from someone with a Workshop: they are hired. */
    @SubscribeEvent
    public static void hire(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof Villager villager) || !Npcs.isOurs(villager)) return;
        NpcData data = Npcs.data(villager);
        if (data.role() != Npc.Role.RESIDENT) return;
        ItemStack held = event.getItemStack();
        if (!Purse.isCoin(held) || !Purse.isStamped(held)) return;

        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        Progression.Tier tier = player.getData(ModAttachments.PROGRESSION.get()).tier(Tuning.progression());
        if (!Hire.canHire(tier)) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.hire_tier_low", villager.getName()));
            return;
        }
        long each = Purse.valueOf(held) / held.getCount();
        int coins = (int) Math.max(1, (Hire.HIRE_PENCE + each - 1) / each);
        if (coins > held.getCount()) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.hire_need_sovereign", villager.getName()));
            return;
        }
        held.consume(coins, player);
        villager.setData(ModAttachments.NPC.get(), data.asRole(Npc.Role.HAND, Turfs.id(player)).withPaidDay(day(level)));
        Npcs.dress(level, villager, Npc.Role.HAND);
        ProductItem.actionBar(player, Component.translatable("message.slumdrugs.hired", villager.getName()));
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8f, 1.0f);
    }

    /** Which day it is on the world's own clock. 26.3 calls the day time the default clock. */
    private static long day(ServerLevel level) { return level.getDefaultClockTime() / 24000L; }

    /** Once a second for every hand: wages first, then work. Returns the data as it now stands. */
    static NpcData tick(ServerLevel level, Villager villager, NpcData data) {
        long today = day(level);
        if (Hire.wageDue(data.paidDay(), today)) {
            if (paid(level, villager.blockPosition())) {
                data = data.withPaidDay(today);
            } else {
                data = data.asRole(Npc.Role.RESIDENT, "");
                villager.setData(ModAttachments.NPC.get(), data);
                Npcs.dress(level, villager, Npc.Role.RESIDENT);
                ServerPlayer owner = owner(level, Npcs.data(villager).crew());
                if (owner != null) owner.sendSystemMessage(Component.translatable("message.slumdrugs.hand_quit", villager.getName())
                        .withStyle(s -> s.withColor(0xB05050)));
                return data;
            }
            villager.setData(ModAttachments.NPC.get(), data);
        }
        work(level, villager);
        return data;
    }

    private static ServerPlayer owner(ServerLevel level, String crew) {
        if (!Turf.isPlayer(crew)) return null;
        try {
            return level.getServer().getPlayerList().getPlayer(UUID.fromString(crew.substring(Turf.PLAYER_PREFIX.length())));
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    /** Takes a day's wage from the nearest crate in reach. False if no crate in reach has it. */
    private static boolean paid(ServerLevel level, BlockPos around) {
        StorageCrateBlockEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-Hire.REACH, -2, -Hire.REACH), around.offset(Hire.REACH, 2, Hire.REACH))) {
            if (!(level.getBlockEntity(p) instanceof StorageCrateBlockEntity crate)) continue;
            double d = p.distSqr(around);
            if (d < best) { best = d; nearest = crate; }
        }
        if (nearest == null) return false;

        int size = nearest.getContainerSize();
        long[] values = new long[size];
        int[] counts = new int[size];
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = nearest.getItem(slot);
            if (!Purse.isCoin(stack)) continue;
            values[slot] = ((CoinItem) stack.getItem()).pence();
            counts[slot] = stack.getCount();
        }
        int[] taken = Hire.take(Hire.WAGE_PENCE, values, counts);
        if (taken == null) return false;
        for (int slot = 0; slot < size; slot++) if (taken[slot] > 0) nearest.removeItem(slot, taken[slot]);
        nearest.setChanged();
        return true;
    }

    /** Finds the nearest station with something to do, walks to it, and does one thing. */
    private static void work(ServerLevel level, Villager villager) {
        BlockPos around = villager.blockPosition();
        BlockPos target = null;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-Hire.REACH, -2, -Hire.REACH), around.offset(Hire.REACH, 2, Hire.REACH))) {
            var be = level.getBlockEntity(p);
            boolean todo = be instanceof DryingLoftBlockEntity loft ? loft.anyReady(level)
                    : be instanceof PressingBenchBlockEntity bench && !bench.empty();
            if (!todo) continue;
            double d = p.distSqr(around);
            if (d < best) { best = d; target = p.immutable(); }
        }
        if (target == null) return;

        villager.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (Math.sqrt(best) > WORK_REACH) {
            villager.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6);
            return;
        }
        villager.swingForAttack(InteractionHand.MAIN_HAND);
        var be = level.getBlockEntity(target);
        if (be instanceof DryingLoftBlockEntity loft) {
            for (int rail = 0; rail < DryingLoftBlockEntity.RAILS; rail++) {
                if (!loft.ready(level, rail)) continue;
                ItemStack down = loft.takeDown(level, rail);
                if (!down.isEmpty()) Containers.dropItemStack(level, target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, down);
            }
            DryingLoftBlock.show(level, target, level.getBlockState(target), loft);
            level.playSound(null, target, ModSounds.LOFT_TAKE.get(), SoundSource.BLOCKS, 0.8f, 1.1f);
        } else if (be instanceof PressingBenchBlockEntity bench) {
            if (!bench.stroke(level)) return;
            if (bench.done()) {
                for (ItemStack out : bench.press())
                    Containers.dropItemStack(level, target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, out);
                level.playSound(null, target, ModSounds.PRESS_TURN.get(), SoundSource.BLOCKS, 0.7f, 0.7f);
            } else {
                level.playSound(null, target, ModSounds.PRESS_DONE.get(), SoundSource.BLOCKS, 0.5f, 1.2f);
            }
        }
    }
}
