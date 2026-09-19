package dev.lucas.slumdrugs.mod.client;

import dev.lucas.slumdrugs.mod.ModMenus;
import dev.lucas.slumdrugs.mod.SlumDrugsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

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
}
