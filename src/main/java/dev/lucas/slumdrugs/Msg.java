package dev.lucas.slumdrugs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;

/** Small helpers for MiniMessage-formatted output. */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<dark_gray>[<gold>Slum</gold>]</dark_gray> ";

    private Msg() {}

    public static Component mm(String text) {
        return MM.deserialize(text);
    }

    public static void send(CommandSender to, String text) {
        to.sendMessage(MM.deserialize(PREFIX + text));
    }

    public static void raw(CommandSender to, String text) {
        to.sendMessage(MM.deserialize(text));
    }

    public static void bar(Player to, String text) {
        to.sendActionBar(MM.deserialize(text));
    }

    public static void title(Player to, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        to.showTitle(Title.title(
                MM.deserialize(title),
                MM.deserialize(subtitle),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }

    /** Speech from an NPC, e.g. "Rusty: I could use a hand." */
    public static void npc(Player to, String name, String text) {
        to.sendMessage(MM.deserialize("<yellow>" + name + "</yellow><gray>:</gray> <white>" + text + "</white>"));
    }

    /** Clickable button, e.g. [Accept]. */
    public static String button(String label, String command, String color) {
        return "<click:run_command:'" + command + "'><hover:show_text:'" + command + "'><" + color + ">[" + label + "]</" + color + "></hover></click>";
    }

    /** A 10-segment gauge like ▮▮▮▮▯▯▯▯▯▯ 42%. */
    public static String gauge(double value, double max, String color) {
        double ratio = max <= 0 ? 0 : Math.max(0, Math.min(1, value / max));
        int filled = (int) Math.round(ratio * 10);
        StringBuilder sb = new StringBuilder("<").append(color).append(">");
        for (int i = 0; i < filled; i++) sb.append('▮');
        sb.append("</").append(color).append("><dark_gray>");
        for (int i = filled; i < 10; i++) sb.append('▯');
        sb.append("</dark_gray> <gray>").append(Math.round(ratio * 100)).append("%</gray>");
        return sb.toString();
    }

    public static String money(double amount) {
        return String.format(Locale.ROOT, "$%.0f", amount);
    }

    public static String minutes(long millis) {
        long m = Math.max(0, millis / 60000L);
        long s = Math.max(0, (millis / 1000L) % 60);
        return m + "m " + s + "s";
    }
}
