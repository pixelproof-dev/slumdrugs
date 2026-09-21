package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.player.Progression;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The advancement tree is the journal's second home: it shows the recipes as they become
 * relevant and marks the tiers. Station advancements trigger themselves on placing; the tier
 * ones cannot, so the ledger awards them here whenever a player's tier changes, and on login
 * for anyone who climbed before the tree existed.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Advancements {

    private Advancements() {}

    private static void award(ServerPlayer player, String path) {
        AdvancementHolder holder = player.level().getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, path));
        if (holder == null) return;
        player.getAdvancements().award(holder, "reached");
    }

    /** Every tier up to and including the one reached. */
    public static void tiers(ServerPlayer player, Progression.Tier reached) {
        award(player, "root");
        for (Progression.Tier tier : Progression.Tier.values()) {
            if (tier.ordinal() > reached.ordinal()) break;
            if (tier == Progression.Tier.HAND_TO_MOUTH) continue;
            award(player, "tier/" + tier.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        tiers(player, player.getData(ModAttachments.PROGRESSION.get()).tier(Tuning.progression()));
    }
}
