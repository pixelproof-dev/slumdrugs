package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.player.Progression;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Coin in hands and in offers. Money is items, so paying someone means making stacks, and
 * the merchant screen's fixed exchanges are built from the same coins. Loose coin is what the
 * street pays; stamped coin is what the counting house makes of it.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Purse {

    private Purse() {}

    public static boolean isCoin(ItemStack stack) { return stack.getItem() instanceof CoinItem; }

    public static boolean isStamped(ItemStack stack) { return stack.getOrDefault(ModComponents.STAMPED.get(), false); }

    /** Pence in a stack of coin, zero for anything else. */
    public static long valueOf(ItemStack stack) {
        return stack.getItem() instanceof CoinItem coin ? (long) coin.pence() * stack.getCount() : 0;
    }

    private static ItemStack coins(String name, int count, boolean stamped) {
        ItemStack stack = new ItemStack(ModItems.get(name).get(), count);
        if (stamped) stack.set(ModComponents.STAMPED.get(), true);
        return stack;
    }

    /** A sum as the fewest coins, largest first. */
    public static List<ItemStack> stacks(long pence, boolean stamped) {
        Coin.Split split = Coin.split(pence);
        List<ItemStack> out = new ArrayList<>(3);
        if (split.sovereigns() > 0) out.add(coins("coin_sovereign", split.sovereigns(), stamped));
        if (split.shillings() > 0) out.add(coins("coin_shilling", split.shillings(), stamped));
        if (split.pennies() > 0) out.add(coins("coin_penny", split.pennies(), stamped));
        return out;
    }

    /** Hands a sum to a player, dropping what their inventory will not take. */
    public static void pay(Player player, long pence, boolean stamped) {
        for (ItemStack stack : stacks(pence, stamped))
            if (!player.getInventory().add(stack)) player.drop(stack, false, Prediction.SERVER_ONLY);
    }

    /**
     * One stack for a merchant to hand over, since an offer has one result. The largest
     * denomination that fits a stack, rounded to it; a little is lost on a big sum, which is
     * the broker's rounding and not the player's.
     */
    public static ItemStack single(long pence) {
        if (pence < Coin.SHILLING) return coins("coin_penny", (int) Math.max(1, pence), false);
        long shillings = Math.round(pence / (double) Coin.SHILLING);
        if (shillings <= 64) return coins("coin_shilling", (int) Math.max(1, shillings), false);
        long sovereigns = Math.round(pence / (double) Coin.SOVEREIGN);
        return coins("coin_sovereign", (int) Math.max(1, Math.min(64, sovereigns)), false);
    }

    /** What a merchant asks: sovereigns and shillings, rounded up to the shilling. Any coin will do, loose or stamped. */
    public static ItemCost cost(long pence) {
        long shillings = Math.max(1, (pence + Coin.SHILLING - 1) / Coin.SHILLING);
        if (shillings <= 64) return new ItemCost(ModItems.get("coin_shilling").get(), (int) shillings);
        long sovereigns = (shillings + 19) / 20;
        return new ItemCost(ModItems.get("coin_sovereign").get(), (int) Math.min(64, sovereigns));
    }

    /** The shillings left over once a big price is asked in sovereigns, or nothing. */
    public static Optional<ItemCost> secondCost(long pence) {
        long shillings = Math.max(1, (pence + Coin.SHILLING - 1) / Coin.SHILLING);
        if (shillings <= 64) return Optional.empty();
        long remainder = shillings % 20;
        return remainder == 0 ? Optional.empty()
                : Optional.of(new ItemCost(ModItems.get("coin_shilling").get(), (int) remainder));
    }

    /** Ten shillings on a player's first day: enough for seed, not for a press. */
    @SubscribeEvent
    public static void firstDay(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Progression progress = player.getData(ModAttachments.PROGRESSION.get());
        if (progress.started) return;
        progress.started = true;
        player.syncData(ModAttachments.PROGRESSION.get());
        long purse = Tuning.STARTING_PURSE.get();
        if (purse <= 0) return;
        pay(player, purse, false);
        player.sendSystemMessage(Component.translatable("message.slumdrugs.starting_purse",
                Coin.format(purse)).withStyle(s -> s.withColor(0xE0B040)));
    }
}
