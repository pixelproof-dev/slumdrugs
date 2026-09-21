package dev.lucas.slumdrugs.mod.client;

import dev.lucas.slumdrugs.mod.ModAttachments;
import dev.lucas.slumdrugs.mod.Tonic;
import dev.lucas.slumdrugs.mod.Tuning;
import dev.lucas.slumdrugs.sim.player.Condition;
import dev.lucas.slumdrugs.sim.player.Suspicion;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * The condition, drawn: an intoxication meter, a craving pip and a withdrawal timer, in the
 * bottom-left corner where nothing of vanilla's lives. It draws nothing at all for a sober,
 * clean player, so the screen of someone who never touches the stuff is untouched.
 *
 * <p>Reads the synced attachment and nothing else; the server decides every number.
 */
@OnlyIn(Dist.CLIENT)
public final class ConditionHud implements GuiLayer {

    private static final int MARGIN = 4;
    private static final int METER_WIDTH = 62;
    private static final int METER_HEIGHT = 5;

    private static final int FRAME = 0xA0000000;
    private static final int TRACK = 0x60FFFFFF;
    private static final int METER = 0xFFB08A4A;
    private static final int METER_HIGH = 0xFFD05050;
    private static final int PIP = 0xFFB0A070;
    private static final int TEXT = 0xFFE8E0D0;
    private static final int WITHDRAWAL = 0xFF9A6BA8;
    private static final int WATCH = 0xFFC0A050;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.isSpectator()) return;

        Condition condition = player.getData(ModAttachments.CONDITION.get());
        var settings = Tuning.loaded() ? Tuning.condition() : Condition.Settings.defaults();
        var watchSettings = Tuning.loaded() ? Tuning.suspicion() : Suspicion.Settings.defaults();
        long now = minecraft.level.getGameTime() * 50L;
        boolean tonic = Tonic.on();
        boolean craving = !tonic && condition.craving(now, settings);
        int severity = tonic ? 0 : condition.withdrawalSeverity(now, settings);
        double untilWithdrawal = tonic ? -1 : condition.minutesUntilWithdrawal(now, settings);

        Suspicion suspicion = player.getData(ModAttachments.SUSPICION.get());
        long untilRaid = suspicion.untilRaid(now);

        Font font = minecraft.font;
        int x = MARGIN;
        int y = graphics.guiHeight() - MARGIN - METER_HEIGHT - 2;

        // The Watch, above everything else, only once it has noticed.
        Suspicion.Level watchLevel = suspicion.level(watchSettings);
        if (watchLevel.ordinal() >= Suspicion.Level.NOTICED.ordinal()) {
            Component watch = untilRaid >= 0
                    ? Component.translatable("hud.slumdrugs.raid", (untilRaid + 999) / 1000)
                    : Component.translatable("hud.slumdrugs.suspicion_" + watchLevel.name().toLowerCase(java.util.Locale.ROOT));
            graphics.text(font, watch, x, y - 2 * font.lineHeight - 2, untilRaid >= 0 ? METER_HIGH : WATCH, true);
        }

        boolean anything = condition.intoxication > 0.5 || craving || severity > 0
                || (untilWithdrawal >= 0 && condition.intoxication < 5);
        if (!anything) return;

        // The meter: how high they are, red once the next dose would be one too many.
        graphics.fill(x - 1, y - 1, x + METER_WIDTH + 1, y + METER_HEIGHT + 1, FRAME);
        graphics.fill(x, y, x + METER_WIDTH, y + METER_HEIGHT, TRACK);
        int filled = (int) Math.round(METER_WIDTH * Math.max(0, Math.min(1, condition.intoxication / 100.0)));
        if (filled > 0)
            graphics.fill(x, y, x + filled, y + METER_HEIGHT,
                    condition.intoxication >= Condition.OVERDOSE_THRESHOLD * 0.75 ? METER_HIGH : METER);

        // The pip: lit while craving, and beside it the word.
        int pipX = x + METER_WIDTH + 4;
        graphics.fill(pipX - 1, y - 1, pipX + METER_HEIGHT + 1, y + METER_HEIGHT + 1, FRAME);
        if (craving) graphics.fill(pipX, y, pipX + METER_HEIGHT, y + METER_HEIGHT, PIP);

        // The line above: withdrawal and how long it has left, or how long until it starts.
        int textY = y - font.lineHeight - 1;
        if (severity > 0) {
            graphics.text(font, Component.translatable("hud.slumdrugs.withdrawal", severity,
                    minutes(condition.withdrawalMinutesLeft(settings))), x, textY, WITHDRAWAL, true);
        } else if (untilWithdrawal > 0 && condition.intoxication < 5) {
            graphics.text(font, Component.translatable("hud.slumdrugs.until_withdrawal",
                    minutes(untilWithdrawal)), x, textY, TEXT, true);
        } else if (condition.intoxication > 0.5) {
            graphics.text(font, Component.translatable(Tonic.key("hud.slumdrugs.intoxication"),
                    (int) Math.round(condition.intoxication)), x, textY, TEXT, true);
        }
    }

    /** Whole minutes, never "0 min" for something that is still ahead. */
    private static int minutes(double value) { return (int) Math.max(1, Math.ceil(value)); }
}
