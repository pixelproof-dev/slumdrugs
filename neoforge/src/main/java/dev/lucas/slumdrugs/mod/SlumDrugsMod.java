package dev.lucas.slumdrugs.mod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;

/**
 * Entry point. This class owns registration and wiring only — every rule lives in the
 * {@code sim} module, which knows nothing about Minecraft and is verified without one.
 */
@Mod(SlumDrugsMod.ID)
public final class SlumDrugsMod {

    public static final String ID = "slumdrugs";

    public SlumDrugsMod(IEventBus modBus, ModContainer container) {
        ModComponents.TYPES.register(modBus);
        ModAttachments.TYPES.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.TYPES.register(modBus);
        ModMenus.TYPES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        // A server config: the server's numbers, sent to every client that joins it.
        container.registerConfig(ModConfig.Type.SERVER, Tuning.SPEC);
    }
}
