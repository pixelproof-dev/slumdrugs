package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.world.TownSites;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.Optional;

/**
 * Lists the orders regulars have rung in ({@link Phone}), and points at the nearest town.
 *
 * <p>A town is as rare as a stronghold (MOD-GDD.md §1), and that rarity is only fair if it can be
 * found on purpose. This is how: use it and it names the distance, the direction and the
 * coordinates of the nearest town — built or not yet built, since a site that passes the test
 * becomes a town as soon as somebody walks up to it. The coordinates are there so they can go
 * straight into a map mod's waypoint.
 *
 * <p>It asks {@link Towns#nearest}, which applies the same test the builder does, so it never
 * leads anyone to open grass. That test includes a village search, which is why there is a
 * cooldown: a phone that could be spammed would let one player hold the server.
 */
public final class BurnerPhoneItem extends Item {

    /** How far out it looks, in regions of {@link TownSites#REGION} blocks: six kilometres. */
    private static final int REACH = 6;

    private static final int COOLDOWN_TICKS = 20 * 5;

    public BurnerPhoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || server.dimension() != Level.OVERWORLD) {
            player.sendSystemMessage(Component.translatable("phone.slumdrugs.no_network")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        server.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(),
                SoundSource.PLAYERS, 0.6f, 1.6f);
        if (player instanceof net.minecraft.server.level.ServerPlayer caller) Phone.listOrders(caller);

        Optional<TownSites.Site> found = Towns.nearest(server, player.getBlockX(), player.getBlockZ(), REACH);
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.translatable("phone.slumdrugs.no_signal",
                    REACH * TownSites.REGION).withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }

        TownSites.Site town = found.get();
        long dx = town.x() - player.getBlockX(), dz = town.z() - player.getBlockZ();
        long metres = Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        String bearing = TownSites.bearing(dx, dz).name().toLowerCase(Locale.ROOT);
        player.sendSystemMessage(Component.translatable("phone.slumdrugs.signal",
                        metres,
                        Component.translatable("phone.slumdrugs.bearing." + bearing),
                        town.x(), town.z())
                .withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }
}
