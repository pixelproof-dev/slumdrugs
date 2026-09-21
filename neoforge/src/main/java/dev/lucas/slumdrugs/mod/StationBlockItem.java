package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.player.Progression;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Places a station, if the player has climbed far enough to set one up. The gate is on
 * placing rather than crafting or using: a player can be handed a press, or find one, and
 * still not be the kind of operator who runs one. Creative and other infinite-material
 * players are never gated.
 */
public final class StationBlockItem extends BlockItem {

    private final Progression.Tier required;

    public StationBlockItem(Block block, Progression.Tier required, Properties properties) {
        super(block, properties);
        this.required = required;
    }

    public Progression.Tier required() { return required; }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player != null && !player.hasInfiniteMaterials()
                && !player.getData(ModAttachments.PROGRESSION.get()).reached(required, Tuning.progression())) {
            ProductItem.actionBar(player, Component.translatable("message.slumdrugs.tier_locked",
                    getBlock().getName(), Component.translatable("tier.slumdrugs." + required.name().toLowerCase(java.util.Locale.ROOT))));
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}
