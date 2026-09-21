package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.economy.Coin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The counting house desk. Emeralds become stamped shillings, one for one. Loose coin becomes
 * stamped coin less the house's tenth. Sneak and click with any coin to break it into the
 * next denomination down. It keeps nothing, so it needs no block entity; the design makes it
 * an institution in the settlement, and until settlements exist it is a desk a player builds.
 */
public final class CountingHouseBlock extends Block {

    public CountingHouseBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        boolean emerald = stack.is(Items.EMERALD);
        if (!emerald && !Purse.isCoin(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown() && !emerald) {
            change(player, stack);
        } else if (emerald) {
            int count = stack.getCount();
            stack.consume(count, player);
            Purse.pay(player, (long) count * Coin.SHILLING, true);
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.exchanged",
                    count, Coin.format((long) count * Coin.SHILLING)));
        } else if (Purse.isStamped(stack)) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.already_stamped"));
            return InteractionResult.SUCCESS;
        } else {
            long loose = Purse.valueOf(stack);
            long stamped = Coin.stamped(loose);
            stack.consume(stack.getCount(), player);
            Purse.pay(player, stamped, true);
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.stamped",
                    Coin.format(loose), Coin.format(stamped), Coin.format(loose - stamped)));
        }
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);
        return InteractionResult.SUCCESS;
    }

    /** One coin into the denomination below it, keeping its stamp. */
    private static void change(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof CoinItem coin) || coin.pence() == Coin.PENNY) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.no_smaller_coin"));
            return;
        }
        boolean stamped = Purse.isStamped(stack);
        String smaller = coin.pence() == Coin.SOVEREIGN ? "coin_shilling" : "coin_penny";
        int count = coin.pence() == Coin.SOVEREIGN ? Coin.SOVEREIGN / Coin.SHILLING : Coin.SHILLING;
        stack.consume(1, player);
        ItemStack out = new ItemStack(ModItems.get(smaller).get(), count);
        if (stamped) out.set(ModComponents.STAMPED.get(), true);
        if (!player.getInventory().add(out)) player.drop(out, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }
}
