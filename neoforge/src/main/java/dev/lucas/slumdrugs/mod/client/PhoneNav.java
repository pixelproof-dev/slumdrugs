package dev.lucas.slumdrugs.mod.client;

import dev.lucas.slumdrugs.mod.PhoneNet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * The phone's navigation: a line at the top of the screen with an arrow, a name and a distance,
 * until the player gets there. Set from the phone's screen, on an order or a town.
 *
 * <p>Client only and kept in memory: it is a convenience for the player at the keyboard, and
 * the server has no reason to know where they are headed.
 */
public final class PhoneNav implements GuiLayer {

    /** Where to, and whose order it is if it is one; an empty id for a town. */
    private record Target(String orderId, String label, int x, int z) {}

    /** Close enough to count as there. */
    private static final int ARRIVED = 4;

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static final int BOX = 0xB0101810;
    private static final int TEXT = 0xFF9BE089;

    private static Target target;

    static void to(String orderId, String label, int x, int z) {
        target = new Target(orderId, label, x, z);
    }

    static void clear() {
        target = null;
    }

    static boolean active() {
        return target != null;
    }

    static boolean headingTo(String orderId) {
        return target != null && target.orderId().equals(orderId);
    }

    /** An order delivered, declined or run out takes its navigation with it. */
    static void prune(PhoneNet.State state) {
        if (target == null || target.orderId().isEmpty()) return;
        if (state.orders().stream().noneMatch(o -> o.id().equals(target.orderId()))) target = null;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Target to = target;
        if (to == null || player == null) return;

        double dx = to.x() + 0.5 - player.getX(), dz = to.z() + 0.5 - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < ARRIVED) {
            target = null;
            player.sendOverlayMessage(Component.translatable("phone.slumdrugs.nav.arrived", to.label()));
            return;
        }

        // Minecraft's yaw: 0 faces +z, and it grows turning right. The same measure for the
        // target, then the difference is where the arrow points.
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = Mth.wrapDegrees(targetYaw - player.getYRot());
        String arrow = ARROWS[Math.floorMod((int) Math.round(relative / 45.0), ARROWS.length)];

        Font font = minecraft.font;
        String line = arrow + " " + to.label() + " · " + Math.round(distance) + " m";
        int width = font.width(line);
        int x = (graphics.guiWidth() - width) / 2;
        int y = 4;
        graphics.fill(x - 4, y - 2, x + width + 4, y + font.lineHeight + 1, BOX);
        graphics.text(font, line, x, y, TEXT, false);
    }
}
