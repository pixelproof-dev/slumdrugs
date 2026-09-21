package dev.lucas.slumdrugs.mod.client;

import dev.lucas.slumdrugs.mod.ModComponents;
import dev.lucas.slumdrugs.mod.ModItems;
import dev.lucas.slumdrugs.mod.ModMenus;
import dev.lucas.slumdrugs.mod.Purse;
import dev.lucas.slumdrugs.mod.SlumDrugsMod;
import dev.lucas.slumdrugs.sim.drug.Quality;
import dev.lucas.slumdrugs.sim.drug.Sealing;
import dev.lucas.slumdrugs.sim.drug.Strain;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client-only wiring. Nothing here may be touched from common or server code.
 *
 * <p>26.3 unified the event buses: {@code @EventBusSubscriber} no longer takes a bus.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CENTRIFUGE.get(), CentrifugeScreen::new);
    }

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EFFECTS,
                Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, "condition"), new ConditionHud());
    }

    /**
     * Quality, grower and seal on the tooltip of anything that carries them. One place for
     * every item, rather than each item class repeating it.
     */
    @SubscribeEvent
    public static void describeGoods(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        var lines = event.getToolTip();

        if (Purse.isCoin(stack))
            lines.add(Component.translatable(Purse.isStamped(stack) ? "tooltip.slumdrugs.stamped" : "tooltip.slumdrugs.loose")
                    .withStyle(Purse.isStamped(stack) ? ChatFormatting.GOLD : ChatFormatting.GRAY));

        Integer quality = stack.get(ModComponents.QUALITY.get());
        if (quality != null)
            lines.add(Component.translatable("tooltip.slumdrugs.quality", quality,
                    Component.translatable("tooltip.slumdrugs.grade." + Quality.Grade.of(quality).name().toLowerCase(java.util.Locale.ROOT)))
                    .withStyle(ChatFormatting.GRAY));

        Strain strain = stack.get(ModComponents.STRAIN.get());
        if (strain != null) {
            if (ModItems.drugOf("seed_", stack) != null)
                lines.add(Component.translatable("tooltip.slumdrugs.strain", strain.potency(), strain.vigour(),
                        strain.hardiness(), strain.subtlety()).withStyle(ChatFormatting.DARK_GREEN));
            else
                lines.add(Component.translatable("tooltip.slumdrugs.potency", strain.potency()).withStyle(ChatFormatting.DARK_GREEN));
        }

        double cut = ModComponents.cutOf(stack);
        if (cut > 0)
            lines.add(Component.translatable("tooltip.slumdrugs.cut", (int) Math.round(cut * 100)).withStyle(ChatFormatting.RED));

        String grower = stack.get(ModComponents.GROWER.get());
        if (grower != null)
            lines.add(Component.translatable("tooltip.slumdrugs.grower", grower).withStyle(ChatFormatting.DARK_GRAY));

        if (ModItems.drugOf("package_", stack) != null) {
            lines.add(Component.translatable("tooltip.slumdrugs.parcel", Sealing.UNITS_PER_PARCEL).withStyle(ChatFormatting.GRAY));
            String seal = stack.get(ModComponents.SEAL.get());
            if (seal != null)
                lines.add(Component.translatable("tooltip.slumdrugs.seal", seal).withStyle(ChatFormatting.GOLD));
        }
    }
}
