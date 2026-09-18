package dev.lucas.slumdrugs.crime;

import dev.lucas.slumdrugs.Msg;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Attention from the district. Heat rises with conspicuous work and falls when the player keeps
 * a low profile. Consequences are always telegraphed: raids announce themselves well in advance
 * and never destroy anything the player built.
 */
public final class HeatManager {

    private final SlumDrugsPlugin plugin;
    private BukkitTask task;
    private int minute;

    public HeatManager(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Once per real minute: the decay and refill rates in config are per minute.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 60L, 20L * 60L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    // ------------------------------------------------------------------ heat

    public void add(Player p, double amount) {
        PlayerData pd = plugin.players().get(p);
        double before = pd.heat;
        pd.heat = Math.max(0, Math.min(100, pd.heat + amount));
        if (amount > 0 && crossed(before, pd.heat, 30)) {
            Msg.send(p, "<gold>People are starting to talk about you.</gold>");
        }
        if (amount > 0 && crossed(before, pd.heat, 60)) {
            Msg.send(p, "<red>The guards have your description.</red> <gray>Lie low, or pay someone off.</gray>");
        }
    }

    public void clear(Player p, double amount) {
        PlayerData pd = plugin.players().get(p);
        pd.heat = Math.max(0, pd.heat - amount);
        pd.raidAt = 0;
        pd.raidWarnedAt = 0;
        Msg.send(p, "<green>The attention on you has cooled.</green> <gray>Heat is now " + Math.round(pd.heat) + ".</gray>");
    }

    private static boolean crossed(double before, double after, double line) {
        return before < line && after >= line;
    }

    /** Called after a sale. Witnesses decide how much attention it drew. */
    public void onSale(Player p, int units, int quality) {
        double base = plugin.getConfig().getDouble("heat.sale-base", 1) * Math.max(1, units / 4.0);
        double guardWeight = plugin.getConfig().getDouble("heat.witnessed-sale-guard", 10);
        double residentWeight = plugin.getConfig().getDouble("heat.witnessed-sale-resident", 4);

        boolean guardSaw = false;
        int residentsSaw = 0;
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 18, 8, 18)) {
            String type = plugin.npcs().typeOf(e);
            if (type == null) continue;
            if (!(e instanceof LivingEntity le) || !le.hasLineOfSight(p)) continue;
            if ("guard".equals(type)) guardSaw = true;
            else if ("resident".equals(type)) residentsSaw++;
        }

        double heat = base;
        if (guardSaw) {
            heat += guardWeight;
            Msg.send(p, "<red>A guard was watching.</red>");
        }
        if (residentsSaw > 0) {
            heat += residentWeight * Math.min(3, residentsSaw);
            plugin.players().get(p).addRep("residents", -1);
        }
        boolean night = p.getWorld().getTime() > 13000 && p.getWorld().getTime() < 23000;
        if (night) heat *= 0.6;
        add(p, heat);
    }

    public void onTheft(Player p) {
        add(p, plugin.getConfig().getDouble("heat.theft", 5));
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        minute++;
        double decay = plugin.getConfig().getDouble("heat.decay-per-minute", 1.0);
        long now = System.currentTimeMillis();
        double gangSum = 0;
        int gangCount = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData pd = plugin.players().get(p);
            gangSum += pd.rep("gangs");
            gangCount++;

            boolean carrying = carryingGoods(p);
            double rate = decay * (carrying ? 0.6 : 1.4);
            pd.heat = Math.max(0, pd.heat - rate);

            checkRaid(p, pd, now);
            if (minute % 3 == 0) randomEvent(p, pd, now);
        }

        plugin.market().tickMinute(gangCount == 0 ? 0 : gangSum / gangCount);
    }

    private boolean carryingGoods(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && plugin.items().isGoods(it)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ events

    private void randomEvent(Player p, PlayerData pd, long now) {
        if (pd.heat < 15) return;
        double roll = ThreadLocalRandom.current().nextDouble();

        if (plugin.getConfig().getBoolean("events.inspections", true)
                && pd.heat >= plugin.getConfig().getInt("events.inspection-min-heat", 30)
                && now - pd.lastInspection > 12 * 60_000L && roll < 0.18) {
            inspection(p, pd);
            return;
        }
        if (plugin.getConfig().getBoolean("events.informants", true) && roll < 0.3) {
            informant(p, pd);
            return;
        }
        if (plugin.getConfig().getBoolean("events.rival-offers", true)
                && now - pd.lastRivalOffer > 15 * 60_000L && roll < 0.45) {
            rivalOffer(p, pd, now);
            return;
        }
        if (plugin.getConfig().getBoolean("events.intercepted-deliveries", true)
                && pd.delivery != null && !pd.delivery.intercepted && roll < 0.55) {
            intercept(p, pd);
        }
    }

    private void inspection(Player p, PlayerData pd) {
        pd.lastInspection = System.currentTimeMillis();
        double fine = plugin.getConfig().getDouble("events.fine-base", 40) * (1 + pd.heat / 100.0);
        Msg.raw(p, "<gold><bold>Inspection</bold></gold> <gray>A guard stops you in the street.</gray>");
        Msg.npc(p, "Guard", "Routine check. Or you can settle it here, quietly, for " + Msg.money(fine) + ".");
        Msg.raw(p, "  " + Msg.button("Pay the fine", "/drugs fine", "green")
                + " " + Msg.button("Refuse", "/drugs refuse", "red"));
    }

    /** Called by the command when the player accepts a negotiated fine. */
    public void payFine(Player p) {
        PlayerData pd = plugin.players().get(p);
        double fine = plugin.getConfig().getDouble("events.fine-base", 40) * (1 + pd.heat / 100.0);
        if (!plugin.economy().withdraw(p, fine)) {
            Msg.send(p, "<red>You cannot cover the fine.</red>");
            return;
        }
        pd.heat = Math.max(0, pd.heat - 25);
        pd.addRep("guards", 3);
        Msg.send(p, "<green>Settled.</green> <gray>The guard moves on.</gray>");
    }

    /** Called by the command when the player refuses. */
    public void refuseFine(Player p) {
        PlayerData pd = plugin.players().get(p);
        add(p, 12);
        pd.addRep("guards", -5);
        int confiscated = confiscate(p, 0.3);
        if (confiscated > 0) Msg.send(p, "<red>They searched you and took " + confiscated + " items.</red>");
        else Msg.send(p, "<gray>They find nothing, but they will remember your face.</gray>");
    }

    private void informant(Player p, PlayerData pd) {
        plugin.effects().unease(p);
        String[] lines = {
                "<gray><italic>Someone in the crowd has been watching you a little too long.</italic></gray>",
                "<gray><italic>A neighbour turns away quickly when you look up.</italic></gray>",
                "<gray><italic>You hear your name in a conversation that stops when you pass.</italic></gray>"};
        Msg.raw(p, lines[ThreadLocalRandom.current().nextInt(lines.length)]);
        add(p, 2);
    }

    private void rivalOffer(Player p, PlayerData pd, long now) {
        pd.lastRivalOffer = now;
        List<Drug> drugs = new ArrayList<>(plugin.drugs().all());
        if (drugs.isEmpty()) return;
        Drug d = drugs.get(ThreadLocalRandom.current().nextInt(drugs.size()));
        Msg.npc(p, "Stranger", "There is a crew moving " + d.name + " cheap on the east side. "
                + "Buy them out and the street is yours for a while. Or let them flood it.");
        Msg.raw(p, "  " + Msg.button("Buy them out (" + Msg.money(150) + ")", "/drugs buyout " + d.id, "green")
                + " " + Msg.button("Ignore", "/drugs ignore", "gray"));
    }

    /** Called by the command when the player buys out a rival. */
    public void buyout(Player p, String drugId) {
        Drug d = plugin.drugs().get(drugId);
        if (d == null) return;
        if (!plugin.economy().withdraw(p, 150)) {
            Msg.send(p, "<red>Not enough money.</red>");
            return;
        }
        plugin.market().tickMinute(100);
        PlayerData pd = plugin.players().get(p);
        pd.addRep("gangs", 4);
        add(p, 4);
        Msg.send(p, "<green>You buy out the rival crew.</green> <gray>Demand for " + d.name + " recovers.</gray>");
    }

    private void intercept(Player p, PlayerData pd) {
        pd.delivery.intercepted = true;
        Msg.raw(p, "<red><bold>Intercepted</bold></red> <gray>Word is out about your delivery route.</gray>");
        Msg.npc(p, "Vosk", "They are waiting on your route. Take the long way or drop it and run.");
        add(p, 5);
    }

    // ------------------------------------------------------------------ raids

    private void checkRaid(Player p, PlayerData pd, long now) {
        if (!plugin.getConfig().getBoolean("raids.enabled", true)) return;
        double threshold = plugin.getConfig().getDouble("raids.threshold", 80);

        if (pd.raidAt > 0) {
            if (now >= pd.raidAt) {
                pd.raidAt = 0;
                executeRaid(p, pd);
            } else {
                long left = (pd.raidAt - now) / 1000L;
                if (left % 30 < 1) {
                    Msg.bar(p, "<red>Raid in " + left + "s</red> <gray>- hide your goods</gray>");
                }
            }
            return;
        }

        if (pd.heat >= threshold && now - pd.raidWarnedAt > 10 * 60_000L) {
            int warning = plugin.getConfig().getInt("raids.warning-seconds", 120);
            pd.raidWarnedAt = now;
            pd.raidAt = now + warning * 1000L;
            Msg.title(p, "<red>Raid incoming</red>", "<gray>You have " + warning + " seconds</gray>", 10, 60, 20);
            Msg.raw(p, "<red><bold>A raid is coming.</bold></red> <gray>You have " + warning
                    + " seconds. Stash your goods in a crate, get off the street, or pay the fine.</gray>");
            Msg.raw(p, "  " + Msg.button("Pay them off (" + Msg.money(200) + ")", "/drugs payoff", "green"));
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.7f);
        }
    }

    /** Called by the command: cancels an incoming raid for a price. */
    public void payoff(Player p) {
        PlayerData pd = plugin.players().get(p);
        if (pd.raidAt == 0) {
            Msg.send(p, "<gray>There is nothing to pay off right now.</gray>");
            return;
        }
        if (!plugin.economy().withdraw(p, 200)) {
            Msg.send(p, "<red>You cannot cover that.</red>");
            return;
        }
        pd.raidAt = 0;
        pd.heat = Math.max(0, pd.heat - 40);
        pd.addRep("guards", -2);
        pd.addRep("gangs", 2);
        Msg.send(p, "<green>The raid is called off.</green> <gray>Someone owed someone a favour.</gray>");
    }

    private void executeRaid(Player p, PlayerData pd) {
        if (!p.isOnline()) return;
        Msg.title(p, "<red>Raid</red>", "<gray>Guards sweep the district</gray>", 5, 50, 20);
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.6f);

        int taken = 0;
        if (plugin.getConfig().getBoolean("raids.confiscate-inventory", true)) {
            taken = confiscate(p, 1.0);
        }

        int plots = 0;
        if (plugin.getConfig().getBoolean("raids.uproot-nearby-plots", false)) {
            plots = plugin.farms().removeNear(p.getLocation(), 20);
        }

        int officers = plugin.getConfig().getInt("raids.officers", 4);
        int lifetime = plugin.getConfig().getInt("raids.officer-lifetime-seconds", 180);
        List<LivingEntity> spawned = new ArrayList<>();
        for (int i = 0; i < officers; i++) {
            double angle = Math.PI * 2 * i / officers;
            Location at = p.getLocation().clone().add(Math.cos(angle) * 10, 0, Math.sin(angle) * 10);
            at.setY(at.getWorld().getHighestBlockYAt(at) + 1);
            LivingEntity e = plugin.npcs().spawn("officer", null, at, "<red>District Guard</red>");
            if (e != null) spawned.add(e);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (LivingEntity e : spawned) if (e.isValid()) e.remove();
        }, lifetime * 20L);

        pd.heat = Math.max(0, pd.heat - 55);
        pd.addRep("guards", -8);
        plugin.district().nudge(-3.0);
        Msg.raw(p, "<red>The raid hits.</red> <gray>" + taken + " items confiscated"
                + (plots > 0 ? ", " + plots + " plots seized" : "") + ". Nothing you built was destroyed.</gray>");
    }

    /** Removes a share of the player's illegal goods. Returns how many item stacks were taken. */
    private int confiscate(Player p, double share) {
        int taken = 0;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || !plugin.items().isGoods(it)) continue;
            if (ThreadLocalRandom.current().nextDouble() > share) continue;
            p.getInventory().setItem(i, null);
            taken++;
        }
        return taken;
    }

    /** True if this block is protected from raid damage. Crates never break. */
    public boolean isProtected(Material material) {
        return material == Material.CHEST || material == Material.BARREL;
    }
}
