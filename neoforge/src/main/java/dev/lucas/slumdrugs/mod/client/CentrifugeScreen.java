package dev.lucas.slumdrugs.mod.client;

import dev.lucas.slumdrugs.mod.CentrifugeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Borrows the brewing stand's background and its slot positions, so the machine reads as
 * familiar equipment rather than a new puzzle. No new art, and nothing is redistributed:
 * the texture is referenced from the game the player already owns.
 */
public final class CentrifugeScreen extends AbstractContainerScreen<CentrifugeMenu> {

    private static final Identifier BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/container/brewing_stand.png");
    private static final Identifier PROGRESS_SPRITE =
            Identifier.withDefaultNamespace("container/brewing_stand/brew_progress");

    public CentrifugeScreen(CentrifugeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int xo = (width - imageWidth) / 2;
        int yo = (height - imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo, 0.0F, 0.0F,
                imageWidth, imageHeight, 256, 256);

        int filled = Math.round(menu.progress() * 28);
        if (filled > 0)
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS_SPRITE, 9, 28, 0, 0,
                    xo + 97, yo + 16, 9, filled);
    }
}
