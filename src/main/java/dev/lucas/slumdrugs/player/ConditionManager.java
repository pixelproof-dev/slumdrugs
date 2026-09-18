package dev.lucas.slumdrugs.player;

import dev.lucas.slumdrugs.Msg;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.drug.Quality;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The gradual condition model. Six distinct values per substance:
 * intoxication (current high), tolerance, dependence, craving, withdrawal and recovery.
 * Nothing here is ever permanent: every value decays with time, rest or a clinic visit.
 */
public final class ConditionManager {

    private final SlumDrugsPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private BukkitTask task;
    private int second;

    public ConditionManager(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) hideBar(p);
        bars.clear();
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    // ------------------------------------------------------------------ using

    public void use(Player p, Drug d, int quality) {
        PlayerData pd = plugin.players().get(p);
        recover(pd, System.currentTimeMillis());
        PlayerData.Condition c = pd.condition(d.id);
        long now = System.currentTimeMillis();

        double current = c.intoxication(now);
        double potency = Quality.durationFactor(quality) * (1.0 - 0.7 * c.tolerance / 100.0);
        potency = Math.max(0.15, potency);
        double peak = Math.min(100, current + d.dose * potency);
        int durationTicks = (int) Math.max(100, d.durationTicks * potency);

        c.intoxPeak = peak;
        c.intoxStart = now;
        c.intoxEnd = now + durationTicks * 50L;
        c.tolerance = Math.min(100, c.tolerance + d.toleranceGain * (1.0 - c.tolerance / 130.0));
        c.dependence = Math.min(100, c.dependence + d.dependenceGain * (1.0 + c.tolerance / 100.0));
        c.lastUse = now;
        c.uses++;

        boolean nausea = cfg().getBoolean("condition.nausea", false);
        for (Drug.EffectSpec e : d.effects) {
            if (!nausea && e.type().equals(PotionEffectType.NAUSEA)) continue;
            int amp = e.amplifier();
            if (c.tolerance > 70 && amp > 0) amp--;
            p.addPotionEffect(new PotionEffect(e.type(), durationTicks, amp, false, true, true));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.8f, 0.9f);

        String tolNote = c.tolerance > 60 ? " <gray>It barely does anything anymore.</gray>" : c.tolerance > 30 ? " <gray>Weaker than it used to be.</gray>" : "";
        Msg.send(p, "You use " + d.colored() + " <gray>(" + Quality.Grade.of(quality).colored() + "<gray>).</gray>" + tolNote);

        double threshold = cfg().getDouble("condition.overdose-threshold", 95);
        if (peak >= threshold) {
            overdose(p, d, c);
        }
    }

    private void overdose(Player p, Drug d, PlayerData.Condition c) {
        double dmg = 6.0;
        // Directly clamp the result so damage modifiers cannot turn this into a lethal hit.
        p.setHealth(Math.max(Math.min(p.getHealth(), 2.0), p.getHealth() - dmg));
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1, false, false, true));
        c.intoxPeak = 60;
        Msg.title(p, "<red>Too much.</red>", "<gray>Your heart is racing. Slow down.</gray>", 5, 50, 20);
        Msg.send(p, "<red>You took too much " + d.colored() + "<red>. Ease off for a while.</red>");
    }

    // ------------------------------------------------------------------ queries

    /** 0..1 how bad withdrawal from this drug is right now. */
    public double withdrawalSeverity(PlayerData pd, Drug d, long now) {
        PlayerData.Condition c = pd.conditions.get(d.id);
        if (c == null) return 0;
        double minDep = cfg().getDouble("condition.withdrawal-min-dependence", 40);
        if (c.dependence < minDep) return 0;
        if (now < pd.withdrawalSuppressedUntil) return 0;
        if (c.intoxication(now) > 5) return 0;
        long delay = Math.max(1, d.withdrawalDelayMinutes) * 60_000L;
        long since = now - c.lastUse;
        if (since < delay) return 0;
        double ramp = Math.min(1.0, (double) (since - delay) / (double) delay);
        return Math.min(1.0, (c.dependence / 100.0) * ramp);
    }

    public double worstWithdrawal(PlayerData pd, long now) {
        double worst = 0;
        for (Drug d : plugin.drugs().all()) worst = Math.max(worst, withdrawalSeverity(pd, d, now));
        return worst;
    }

    public boolean isCraving(PlayerData pd, Drug d, long now) {
        PlayerData.Condition c = pd.conditions.get(d.id);
        if (c == null) return false;
        if (c.dependence < cfg().getDouble("condition.craving-min-dependence", 20)) return false;
        if (c.intoxication(now) > 5) return false;
        return now - c.lastUse > cfg().getInt("condition.craving-delay-minutes", 15) * 60_000L;
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        second++;
        long now = System.currentTimeMillis();
        double dtMin = 1.0 / 60.0;
        double tolDecay = cfg().getDouble("condition.tolerance-decay-per-minute", 0.15);
        double depDecay = cfg().getDouble("condition.dependence-decay-per-minute", 0.08);
        long depDelay = cfg().getInt("condition.dependence-decay-delay-minutes", 20) * 60_000L;
        double restBonus = cfg().getDouble("condition.rest-bonus-multiplier", 2.5);
        long cravingInterval = cfg().getInt("condition.craving-interval-minutes", 4) * 60_000L;

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData pd = plugin.players().get(p);
            recover(pd, now);
            Drug highest = null;
            double highestIntox = 0;
            Drug worstWd = null;
            double worstSev = 0;

            for (Drug d : plugin.drugs().all()) {
                PlayerData.Condition c = pd.conditions.get(d.id);
                if (c == null) continue;

                double intox = c.intoxication(now);
                if (intox > highestIntox) {
                    highestIntox = intox;
                    highest = d;
                }

                if (isCraving(pd, d, now) && now - pd.lastCravingPrompt > cravingInterval
                        && ThreadLocalRandom.current().nextInt(60) == 0) {
                    pd.lastCravingPrompt = now;
                    craving(p, d, c);
                }

                double sev = withdrawalSeverity(pd, d, now);
                if (sev > worstSev) {
                    worstSev = sev;
                    worstWd = d;
                }
                if (sev > 0 && second % 5 == 0) applyWithdrawal(p, d, sev);
            }

            if (worstSev > 0) {
                if (p.isSprinting()) p.setExhaustion(p.getExhaustion() + (float) (0.4 * worstSev));
                if (cfg().getBoolean("condition.camera-movement", false)
                        && cfg().getBoolean("condition.shaky-aim", false)) shakyAim(p, worstSev);
            }

            if (highest != null && highest.hallucinations && highestIntox > 15) {
                plugin.effects().tick(p, highestIntox / 100.0);
            } else plugin.effects().clear(p.getUniqueId());
            if (highestIntox > 70 && cfg().getBoolean("condition.darkness-pulses", true)
                    && ThreadLocalRandom.current().nextInt(25) == 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
            }

            updateBar(p, highest, highestIntox, worstWd, worstSev);
        }
    }

    private void craving(Player p, Drug d, PlayerData.Condition c) {
        String[] lines = {
                "You keep thinking about " + d.colored() + "<gray>.</gray>",
                "Your hands feel restless. A bit of " + d.colored() + " <gray>would settle them.</gray>",
                "<gray>Everything feels dull. You want</gray> " + d.colored() + "<gray>.</gray>"
        };
        Msg.raw(p, "<italic><gray>" + lines[ThreadLocalRandom.current().nextInt(lines.length)] + "</gray></italic>");
        Msg.bar(p, "<gray>Craving</gray> " + d.colored());
        if (ThreadLocalRandom.current().nextBoolean()) p.setExhaustion(p.getExhaustion() + 3f);
        else p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, false, false, false));
    }

    private void applyWithdrawal(Player p, Drug d, double sev) {
        for (Drug.EffectSpec e : d.withdrawalEffects) {
            if (e.type().equals(PotionEffectType.NAUSEA) && !cfg().getBoolean("condition.nausea", false)) continue;
            int amp = sev > 0.75 ? Math.min(e.amplifier(), 1) : 0;
            p.addPotionEffect(new PotionEffect(e.type(), 140, amp, false, false, true));
        }
    }

    private void shakyAim(Player p, double sev) {
        if (!p.isHandRaised()) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        Material m = hand.getType();
        if (m != Material.BOW && m != Material.CROSSBOW && m != Material.TRIDENT) return;
        if (ThreadLocalRandom.current().nextDouble() > 0.35 * sev) return;
        float yaw = p.getLocation().getYaw() + (float) ((ThreadLocalRandom.current().nextDouble() - 0.5) * 3.0 * sev);
        float pitch = p.getLocation().getPitch() + (float) ((ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0 * sev);
        p.setRotation(yaw, Math.max(-90, Math.min(90, pitch)));
    }

    // ------------------------------------------------------------------ display

    private void updateBar(Player p, Drug high, double intox, Drug wd, double sev) {
        if (high == null && wd == null) {
            hideBar(p);
            return;
        }
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(Msg.mm(""), 0f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
            bars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        }
        if (high != null) {
            String label = intox > 70 ? "Very high" : intox > 35 ? "High" : "Buzzed";
            bar.name(Msg.mm(high.colored() + " <gray>· " + label + "</gray>"));
            bar.color(intox > 70 ? BossBar.Color.PURPLE : BossBar.Color.GREEN);
            bar.progress((float) Math.max(0, Math.min(1, intox / 100.0)));
        } else {
            String label = sev > 0.66 ? "Withdrawal · rough" : sev > 0.33 ? "Withdrawal" : "Withdrawal · mild";
            bar.name(Msg.mm("<red>" + label + "</red> <gray>·</gray> " + wd.colored() + " <dark_gray>(rest, clinic or time)</dark_gray>"));
            bar.color(BossBar.Color.RED);
            bar.progress((float) Math.max(0, Math.min(1, sev)));
        }
    }

    private void hideBar(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
    }

    public void onQuit(Player p) {
        hideBar(p);
    }

    /** Full status report in chat. */
    public void status(Player p) {
        PlayerData pd = plugin.players().get(p);
        long now = System.currentTimeMillis();
        Msg.raw(p, "<gold><bold>Your condition</bold></gold>");
        boolean any = false;
        for (Drug d : plugin.drugs().all()) {
            PlayerData.Condition c = pd.conditions.get(d.id);
            if (c == null || (c.tolerance < 0.5 && c.dependence < 0.5 && c.intoxication(now) < 0.5)) continue;
            any = true;
            double intox = c.intoxication(now);
            double sev = withdrawalSeverity(pd, d, now);
            Msg.raw(p, " " + d.colored() + " <dark_gray>(" + c.uses + " uses)</dark_gray>");
            Msg.raw(p, "   <gray>Intoxication</gray> " + Msg.gauge(intox, 100, d.color));
            Msg.raw(p, "   <gray>Tolerance   </gray> " + Msg.gauge(c.tolerance, 100, "yellow"));
            Msg.raw(p, "   <gray>Dependence  </gray> " + Msg.gauge(c.dependence, 100, "red"));
            String state;
            if (intox > 5) state = "<green>active</green>";
            else if (sev > 0) state = "<red>withdrawal " + Math.round(sev * 100) + "%</red>";
            else if (isCraving(pd, d, now)) state = "<gold>craving</gold>";
            else if (c.dependence > 0 && now - c.lastUse > cfg().getInt("condition.dependence-decay-delay-minutes", 20) * 60_000L) state = "<aqua>recovering</aqua>";
            else state = "<gray>stable</gray>";
            Msg.raw(p, "   <gray>State</gray> " + state + "  <gray>last use " + (c.lastUse == 0 ? "never" : Msg.minutes(now - c.lastUse) + " ago") + "</gray>");
        }
        if (!any) Msg.raw(p, " <gray>Clean. Nothing in your system.</gray>");
        if (now < pd.withdrawalSuppressedUntil) Msg.raw(p, " <aqua>Clinic treatment active for " + Msg.minutes(pd.withdrawalSuppressedUntil - now) + ".</aqua>");
        if (pd.restedRecently(now)) Msg.raw(p, " <aqua>Well rested: recovery is faster right now.</aqua>");
        Msg.raw(p, " <gray>Recovery: dependence falls on its own after " + cfg().getInt("condition.dependence-decay-delay-minutes", 20)
                + " min without use. Sleeping a full night and the clinic speed it up.</gray>");
        Msg.raw(p, " <gray>Heat</gray> " + Msg.gauge(pd.heat, 100, "gold") + "  <gray>Money</gray> <green>" + Msg.money(plugin.economy().balance(p)) + "</green>");
        Msg.raw(p, " <gray>Reputation:</gray> " + repLine(pd));
        if (pd.contract != null) Msg.raw(p, " <gray>Contract:</gray> " + pd.contract.units + " units of " + pd.contract.drug + " for " + pd.contract.customerName + " <gray>(" + Msg.minutes(pd.contract.deadline - now) + " left)</gray>");
        if (pd.delivery != null) Msg.raw(p, " <gray>Delivery:</gray> " + pd.delivery.units + " units of " + pd.delivery.drug + " to the " + pd.delivery.dropBox + " drop box <gray>(" + Msg.minutes(pd.delivery.deadline - now) + " left)</gray>");
        if (pd.job != null) Msg.raw(p, " <gray>Job:</gray> bring " + pd.job.amount + " " + dev.lucas.slumdrugs.drug.Items.pretty(pd.job.material) + " to " + pd.job.residentName + " <gray>for " + Msg.money(pd.job.pay) + "</gray>");
    }

    public static String repLine(PlayerData pd) {
        StringBuilder sb = new StringBuilder();
        for (String f : PlayerData.FACTIONS) {
            int r = pd.rep(f);
            String col = r > 20 ? "green" : r < -20 ? "red" : "white";
            sb.append("<gray>").append(f).append("</gray> <").append(col).append(">").append(r > 0 ? "+" : "").append(r).append("</").append(col).append(">  ");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ recovery

    public void recover(PlayerData pd, long now) {
        long from = pd.lastRecoveryAt;
        if (now <= from) return;
        for (PlayerData.Condition c : pd.conditions.values()) {
            c.tolerance = Recovery.tolerance(c.tolerance, from, now,
                    cfg().getDouble("condition.tolerance-decay-per-minute", 0.15));
            c.dependence = Recovery.dependence(c.dependence, from, now, c.lastUse,
                    cfg().getInt("condition.dependence-decay-delay-minutes", 20) * 60000L,
                    pd.lastFullSleep, cfg().getDouble("condition.dependence-decay-per-minute", 0.08),
                    cfg().getDouble("condition.rest-bonus-multiplier", 2.5));
        }
        pd.lastRecoveryAt = now;
    }

    /** Clinic treatment: paid, strong, reliable. */
    public boolean treat(Player p) {
        PlayerData pd = plugin.players().get(p);
        double price = cfg().getDouble("clinic.treatment-price", 60);
        if (!plugin.economy().withdraw(p, price)) {
            Msg.npc(p, "Medic", "Treatment is " + Msg.money(price) + ". Come back when you have it, no judgement.");
            return false;
        }
        double cut = cfg().getDouble("clinic.treatment-dependence-reduction", 25);
        for (PlayerData.Condition c : pd.conditions.values()) c.dependence = Math.max(0, c.dependence - cut);
        pd.withdrawalSuppressedUntil = System.currentTimeMillis() + cfg().getInt("clinic.treatment-suppress-minutes", 15) * 60_000L;
        for (Drug d : plugin.drugs().all()) for (Drug.EffectSpec e : d.withdrawalEffects) p.removePotionEffect(e.type());
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_WORK_CLERIC, 1f, 1f);
        Msg.npc(p, "Medic", "Breathe. The shakes will ease off now. Rest, eat, and stay away from the alley for a while.");
        return true;
    }

    /** Remedy item: weaker than a treatment but portable. */
    public void drinkRemedy(Player p) {
        PlayerData pd = plugin.players().get(p);
        for (PlayerData.Condition c : pd.conditions.values()) c.dependence = Math.max(0, c.dependence - 6);
        long until = System.currentTimeMillis() + 5 * 60_000L;
        pd.withdrawalSuppressedUntil = Math.max(pd.withdrawalSuppressedUntil, until);
        for (Drug d : plugin.drugs().all()) for (Drug.EffectSpec e : d.withdrawalEffects) p.removePotionEffect(e.type());
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1.2f);
        Msg.send(p, "<aqua>The remedy settles your nerves for a few minutes.</aqua>");
    }

    /** Called when the player has been in bed long enough to sleep. */
    public void onDeepSleep(Player p) {
        PlayerData pd = plugin.players().get(p);
        long now = System.currentTimeMillis();
        double sev = worstWithdrawal(pd, now);
        if (sev > 0.2 && cfg().getBoolean("condition.disturbed-sleep", true)
                && ThreadLocalRandom.current().nextDouble() < sev * 0.7) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || !p.isSleeping()) return;
                p.wakeup(false);
                for (PlayerData.Condition c : pd.conditions.values()) c.dependence = Math.max(0, c.dependence - 2);
                Msg.send(p, "<gray><italic>Nightmares. You wake up sweating, but the rest still helped a little.</italic></gray>");
            }, 40L);
        }
    }

    /** Called when the player leaves the bed; fullNight is true if the night was skipped. */
    public void onWake(Player p, boolean fullNight) {
        if (!fullNight) return;
        PlayerData pd = plugin.players().get(p);
        pd.lastFullSleep = System.currentTimeMillis();
        boolean had = false;
        for (PlayerData.Condition c : pd.conditions.values()) {
            if (c.dependence > 0) had = true;
            c.dependence = Math.max(0, c.dependence - 5);
        }
        if (had) Msg.send(p, "<aqua>A full night of sleep. Recovery is faster for the next ten minutes.</aqua>");
    }
}
