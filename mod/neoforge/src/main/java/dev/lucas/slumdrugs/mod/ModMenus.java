package dev.lucas.slumdrugs.mod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> TYPES =
            DeferredRegister.create(Registries.MENU, SlumDrugsMod.ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CentrifugeMenu>> CENTRIFUGE =
            TYPES.register("centrifuge",
                    () -> new MenuType<>(CentrifugeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}
}
