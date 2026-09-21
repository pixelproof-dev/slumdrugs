package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.player.Progression;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The player's own ledger. Using it reads back where they stand: the tier, what they have
 * sold and earned, and what the next door costs. It is the one place progression is spelled
 * out, because the design forgoes a recipe viewer and something has to carry that weight.
 */
public final class JournalItem extends Item {

    public JournalItem(Properties properties) { super(properties); }

    static Component tierName(Progression.Tier tier) {
        return Component.translatable("tier.slumdrugs." + tier.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        Progression progress = player.getData(ModAttachments.PROGRESSION.get());
        player.sendSystemMessage(Component.translatable("journal.slumdrugs.heading", tierName(progress.tier()))
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("journal.slumdrugs.sold", progress.unitsSold, progress.coinEarned)
                .withStyle(ChatFormatting.GRAY));

        Progression.Gate gate = progress.gate();
        if (!gate.reachable())
            player.sendSystemMessage(Component.translatable("journal.slumdrugs.ceiling", tierName(gate.next()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        else if (gate.unitsNeeded() > 0)
            player.sendSystemMessage(Component.translatable("journal.slumdrugs.gate_units", gate.unitsNeeded(), tierName(gate.next()))
                    .withStyle(ChatFormatting.GRAY));
        else
            player.sendSystemMessage(Component.translatable("journal.slumdrugs.gate_coin", gate.coinNeeded(), tierName(gate.next()))
                    .withStyle(ChatFormatting.GRAY));

        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }
}
