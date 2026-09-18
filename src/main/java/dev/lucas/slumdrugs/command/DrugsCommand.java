package dev.lucas.slumdrugs.command;

import dev.lucas.slumdrugs.Msg;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.drug.Items;
import dev.lucas.slumdrugs.npc.CustomerProfile;
import dev.lucas.slumdrugs.player.PlayerData;
import dev.lucas.slumdrugs.station.StationManager;
import dev.lucas.slumdrugs.station.StationType;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Everything the player and the admin can type. */
public final class DrugsCommand implements CommandExecutor, TabCompleter {

    private final SlumDrugsPlugin plugin;

    public DrugsCommand(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help" -> help(sender);
            case "status" -> {
                if (player(sender) != null) plugin.condition().status(player(sender));
            }
            case "market" -> market(sender);
            case "customers" -> customers(sender);
            case "soil" -> soil(sender);
            case "district" -> district(sender, args);
            case "clinic" -> {
                Player p = player(sender);
                if (p != null) {
                    Location clinic = plugin.district().anchor("clinic");
                    if (clinic == null || !clinic.getWorld().equals(p.getWorld())
                            || clinic.distanceSquared(p.getLocation()) > 144)
                        Msg.send(p, "<gray>Visit the clinic in the district first.</gray>");
                    else plugin.npcs().openClinic(p);
                }
            }
            case "fine" -> {
                Player p = player(sender);
                if (p != null) plugin.heat().payFine(p);
            }
            case "refuse" -> {
                Player p = player(sender);
                if (p != null) plugin.heat().refuseFine(p);
            }
            case "payoff" -> {
                Player p = player(sender);
                if (p != null) plugin.heat().payoff(p);
            }
            case "buyout" -> {
                Player p = player(sender);
                if (p != null && args.length > 1) plugin.heat().buyout(p, args[1]);
            }
            case "ignore" -> {
                Player p = player(sender);
                if (p != null) Msg.send(p, "<gray>You let it go. The street sorts itself out.</gray>");
            }
            case "give" -> give(sender, args);
            case "populate" -> populate(sender);
            case "pack" -> pack(sender);
            case "heat" -> heat(sender, args);
            case "reload" -> {
                if (!admin(sender)) return true;
                plugin.reloadAll();
                Msg.send(sender, "<green>Reloaded config and substances.</green>");
            }
            default -> Msg.send(sender, "<red>Unknown option.</red> <gray>Try /drugs help.</gray>");
        }
        return true;
    }

    // ------------------------------------------------------------------ player commands

    private void help(CommandSender sender) {
        Msg.raw(sender, "<gold><bold>SlumDrugs</bold></gold>");
        Msg.raw(sender, " <white>/drugs status</white> <gray>your condition, money, heat and jobs</gray>");
        Msg.raw(sender, " <white>/drugs market</white> <gray>what the district is paying today</gray>");
        Msg.raw(sender, " <white>/drugs customers</white> <gray>known buyers, their tastes and hours</gray>");
        Msg.raw(sender, " <white>/drugs soil</white> <gray>check the ground you are standing on</gray>");
        Msg.raw(sender, " <white>/drugs district</white> <gray>where the slums are</gray>");
        Msg.raw(sender, " <white>/drugs clinic</white> <gray>open the clinic menu (must be at the clinic)</gray>");
        if (sender.hasPermission("slumdrugs.admin")) {
            Msg.raw(sender, "<gold>Admin</gold>");
            Msg.raw(sender, " <white>/drugs district takeover</white> <gray>convert a nearby village</gray>");
            Msg.raw(sender, " <white>/drugs district restore</white> <gray>restore the converted village</gray>");
            Msg.raw(sender, " <white>/drugs district build</white> <gray>generate the slum here</gray>");
            Msg.raw(sender, " <white>/drugs district condition <0-100></white> <gray>set and refresh the streets</gray>");
            Msg.raw(sender, " <white>/drugs populate</white> <gray>respawn every NPC</gray>");
            Msg.raw(sender, " <white>/drugs pack</white> <gray>generate the matching resource pack</gray>");
            Msg.raw(sender, " <white>/drugs give <kind> [drug] [amount]</white>");
            Msg.raw(sender, " <white>/drugs heat <amount></white>  <white>/drugs reload</white>");
        }
    }

    private void market(CommandSender sender) {
        Msg.raw(sender, "<gold><bold>Street prices</bold></gold> <gray>(local demand is limited and refills slowly)</gray>");
        PlayerData pd = sender instanceof Player p ? plugin.players().get(p) : null;
        for (Drug d : plugin.drugs().all()) {
            double ratio = plugin.market().ratio(d);
            double price = pd == null
                    ? d.basePrice * plugin.market().demandFactor(d)
                    : plugin.market().unitPrice(d, 50, pd, 0);
            String state = ratio > 0.7 ? "<green>hungry</green>" : ratio > 0.35 ? "<yellow>steady</yellow>" : "<red>flooded</red>";
            Msg.raw(sender, " " + d.colored() + " <gray>~</gray><green>" + Msg.money(price)
                    + "</green> <gray>per unit</gray>  " + Msg.gauge(ratio, 1, "gold") + " " + state);
        }
    }

    private void customers(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        PlayerData pd = plugin.players().get(p);
        long time = p.getWorld().getTime();
        Msg.raw(sender, "<gold><bold>Buyers</bold></gold>");
        if (plugin.npcs().profiles().isEmpty()) {
            Msg.raw(sender, " <gray>No district has been built yet.</gray>");
            return;
        }
        for (CustomerProfile c : plugin.npcs().profiles().values()) {
            Drug d = plugin.drugs().get(c.drug);
            String open = c.openAt(time) ? "<green>around now</green>" : "<gray>away</gray>";
            Msg.raw(sender, " <yellow>" + c.name + "</yellow> <gray>wants</gray> " + (d == null ? c.drug : d.colored())
                    + " <gray>at</gray> " + dev.lucas.slumdrugs.drug.Quality.Grade.of(c.minQuality).colored()
                    + "<gray>+, budget</gray> <green>" + Msg.money(c.budget) + "</green>");
            Msg.raw(sender, "   <gray>" + c.hours() + " near " + c.haunt + " · " + open
                    + " · trust " + pd.trust(c.id) + "</gray>");
        }
    }

    private void soil(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        var block = p.getLocation().getBlock();
        List<Drug> grown = plugin.drugs().grown();
        if (grown.isEmpty()) {
            Msg.send(sender, "<gray>No growable substances are configured.</gray>");
            return;
        }
        Drug best = grown.get(0);
        for (Drug d : grown) {
            var probe = new dev.lucas.slumdrugs.farm.Plot(block.getLocation(), d.id, p.getUniqueId(), p.getName());
            var bestProbe = new dev.lucas.slumdrugs.farm.Plot(block.getLocation(), best.id, p.getUniqueId(), p.getName());
            if (plugin.farms().score(probe, d) > plugin.farms().score(bestProbe, best)) best = d;
        }
        plugin.farms().report(p, block, best);
        Msg.raw(sender, " <gray>Best match for this spot:</gray> " + best.colored());
    }

    private void district(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("takeover")) {
            if (!admin(sender)) return;
            Player p = player(sender); if (p != null) plugin.takeover().takeover(p, true); return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("restore")) {
            if (admin(sender)) plugin.takeover().restore(sender); return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("build")) {
            if (!admin(sender)) return;
            if (plugin.district().takeoverId() != null) { Msg.send(sender, "<gray>Restore the village takeover first.</gray>"); return; }
            Location where;
            if (args.length >= 6) {
                org.bukkit.World w = plugin.getServer().getWorld(args[2]);
                if (w == null) {
                    Msg.send(sender, "<red>No world called " + args[2] + ".</red>");
                    return;
                }
                where = new Location(w, parseInt(args[3], 0), parseInt(args[4], 64), parseInt(args[5], 0));
            } else {
                Player p = player(sender);
                if (p == null) {
                    Msg.send(sender, "<gray>From the console, give coordinates:</gray> <white>/drugs district build <world> <x> <y> <z></white>");
                    return;
                }
                where = p.getLocation();
            }
            Msg.send(sender, "<gray>Building the district. This replaces terrain in the footprint.</gray>");
            plugin.district().generate(where);
            plugin.npcs().buildProfiles(plugin.district().seed());
            int n = plugin.populator().populate();
            Msg.send(sender, "<green>District built</green> <gray>with " + n + " residents and traders.</gray>");
            return;
        }
        if (args.length > 2 && args[1].equalsIgnoreCase("condition")) {
            if (!admin(sender)) return;
            try {
                int value = Integer.parseInt(args[2]);
                plugin.district().adjustCondition(value - plugin.district().condition());
                int changed = plugin.district().refresh();
                Msg.send(sender, "<green>Condition set to " + plugin.district().condition()
                        + "</green> <gray>(" + changed + " blocks changed).</gray>");
            } catch (NumberFormatException ex) {
                Msg.send(sender, "<red>Give a number from 0 to 100.</red>");
            }
            return;
        }

        if (!plugin.district().exists()) {
            Msg.send(sender, "<gray>No district yet. An admin can build one with</gray> <white>/drugs district build</white><gray>.</gray>");
            return;
        }
        Location o = plugin.district().origin();
        Msg.raw(sender, "<gold><bold>The district</bold></gold>");
        Msg.raw(sender, " <gray>Centre:</gray> <white>" + o.getBlockX() + ", " + o.getBlockY() + ", " + o.getBlockZ()
                + "</white> <gray>in</gray> <white>" + o.getWorld().getName() + "</white>");
        Msg.raw(sender, " <gray>Condition:</gray> " + Msg.gauge(plugin.district().condition(), 100, "green")
                + (plugin.district().condition() >= 60 ? " <green>recovering</green>" : " <red>run down</red>"));
        StringBuilder places = new StringBuilder();
        for (String key : plugin.district().anchors().keySet()) {
            if (key.startsWith("shack")) continue;
            places.append("<gray>").append(key.replace('_', ' ')).append("</gray>  ");
        }
        Msg.raw(sender, " <gray>Places:</gray> " + places);
    }

    // ------------------------------------------------------------------ admin

    private void populate(CommandSender sender) {
        if (!admin(sender)) return;
        int n = plugin.populator().populate();
        Msg.send(sender, "<green>Spawned " + n + " NPCs.</green>");
    }

    private void pack(CommandSender sender) {
        if (!admin(sender)) return;
        try {
            int files = new dev.lucas.slumdrugs.pack.ResourcePackBuilder(plugin).build();
            Msg.send(sender, "<green>Wrote " + files + " files</green> <gray>to plugins/SlumDrugs/resourcepack. "
                    + "Ready-to-use ZIP: plugins/SlumDrugs/resourcepack.zip.</gray>");
        } catch (Exception ex) {
            Msg.send(sender, "<red>Could not write the pack: " + ex.getMessage() + "</red>");
        }
    }

    private void heat(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player p = player(sender);
        if (p == null || args.length < 2) return;
        try {
            plugin.heat().add(p, Double.parseDouble(args[1]));
            Msg.send(sender, "<gray>Heat is now " + Math.round(plugin.players().get(p).heat) + ".</gray>");
        } catch (NumberFormatException ex) {
            Msg.send(sender, "<red>Give a number.</red>");
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player p = player(sender);
        if (p == null) return;
        if (args.length < 2) {
            Msg.send(sender, "<gray>/drugs give <seed|raw|dried|product|package|fertilizer|remedy|station> [id] [amount]</gray>");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        String id = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null;
        int amount = args.length > 3 ? parseInt(args[3], 1) : 1;

        if (kind.equals("fertilizer")) {
            StationManager.give(p, plugin.items().fertilizer(amount));
            Msg.send(sender, "<green>Given.</green>");
            return;
        }
        if (kind.equals("remedy")) {
            StationManager.give(p, plugin.items().remedy(amount));
            Msg.send(sender, "<green>Given.</green>");
            return;
        }
        if (kind.equals("station")) {
            StationType type = StationType.byId(id);
            if (type == null) {
                Msg.send(sender, "<red>Unknown station.</red> <gray>drying_rack, processing_bench, packaging_station, storage_crate, growbox</gray>");
                return;
            }
            StationManager.give(p, plugin.items().station(type, amount));
            Msg.send(sender, "<green>Given.</green>");
            return;
        }

        Drug drug = plugin.drugs().get(id);
        if (drug == null) {
            Msg.send(sender, "<red>Unknown substance.</red>");
            return;
        }
        int quality = args.length > 4 ? parseInt(args[4], 60) : 60;
        String batch = Items.newBatchId();
        switch (kind) {
            case "seed" -> {
                if (!drug.isGrown()) {
                    Msg.send(sender, "<red>" + drug.name + " is not grown from seed.</red>");
                    return;
                }
                StationManager.give(p, plugin.items().seed(drug, amount));
            }
            case "raw" -> {
                if (!drug.isGrown()) return;
                StationManager.give(p, plugin.items().raw(drug, quality, p.getName(), batch, amount));
            }
            case "dried" -> {
                if (!drug.isGrown()) return;
                StationManager.give(p, plugin.items().dried(drug, quality, p.getName(), batch, amount));
            }
            case "product" -> StationManager.give(p, plugin.items().product(drug, quality, p.getName(), batch, amount));
            case "package" -> StationManager.give(p, plugin.items().pack(drug, quality, p.getName(), batch, amount));
            default -> {
                Msg.send(sender, "<red>Unknown kind.</red>");
                return;
            }
        }
        Msg.send(sender, "<green>Given.</green>");
    }

    // ------------------------------------------------------------------ helpers

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) return p;
        Msg.send(sender, "<red>Only a player can do that.</red>");
        return null;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("slumdrugs.admin")) return true;
        Msg.send(sender, "<red>You do not have permission.</red>");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("help", "status", "market", "customers", "soil", "district", "clinic"));
            if (sender.hasPermission("slumdrugs.admin")) subs.addAll(List.of("give", "populate", "pack", "heat", "reload"));
            return StringUtil.copyPartialMatches(args[0], subs, out);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return StringUtil.copyPartialMatches(args[1],
                    List.of("seed", "raw", "dried", "product", "package", "fertilizer", "remedy", "station"), out);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("district")) {
            return StringUtil.copyPartialMatches(args[1], List.of("build", "condition", "takeover", "restore"), out);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (args[1].equalsIgnoreCase("station")) {
                List<String> ids = new ArrayList<>();
                for (StationType t : StationType.values()) ids.add(t.id);
                return StringUtil.copyPartialMatches(args[2], ids, out);
            }
            List<String> ids = new ArrayList<>();
            for (Drug d : plugin.drugs().all()) ids.add(d.id);
            return StringUtil.copyPartialMatches(args[2], ids, out);
        }
        return out;
    }
}
