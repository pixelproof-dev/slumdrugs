package dev.lucas.slumdrugs.mod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Entry point. This class owns registration and wiring only — every rule lives in the
 * {@code sim} module, which knows nothing about Minecraft and is verified without one.
 */
@Mod(SlumDrugsMod.ID)
public final class SlumDrugsMod {

    public static final String ID = "slumdrugs";

    public SlumDrugsMod(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
    }
}
