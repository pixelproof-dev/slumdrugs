package dev.lucas.slumdrugs.npc;

import dev.lucas.slumdrugs.Keys;
import dev.lucas.slumdrugs.Msg;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.drug.Items;
import dev.lucas.slumdrugs.drug.Quality;
import dev.lucas.slumdrugs.player.PlayerData;
import dev.lucas.slumdrugs.station.StationManager;
import dev.lucas.slumdrugs.ui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Spawns and drives every NPC: customers, residents, the medic, the fixer, traders and gang muscle. */
public final class NpcManager {

    private static final String[] FIRST = {
            "Marlo", "Dessa", "Kip", "Rusty", "Nen", "Odd Tam", "Vera", "Salt", "Pim", "Gret",
            "Hollow Jen", "Bramble", "Cass", "Wick", "Trudy", "Ovid"};
    private static final String[] HAUNTS = {"the tavern", "the back alley", "the market stalls", "the stairwell", "the canal steps"};

    private final SlumDrugsPlugin plugin;
    private final Keys keys;
    private final Map<String, CustomerProfile> profiles = new LinkedHashMap<>();
    private final Map<String, Long> haggled = new HashMap<>();
    private final Map<java.util.UUID, Long> lastThug = new HashMap<>();
    private BukkitTask task;

    public NpcManager(SlumDrugsPlugin plugin, Keys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    public Map<String, CustomerProfile> profiles() { return profiles; }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    // ------------------------------------------------------------------ profiles

    /** Builds the customer roster. Deterministic for a given seed so a district keeps its regulars. */
    public void buildProfiles(long seed) {
        profiles.clear();
        Random r = new Random(seed);
        List<Drug> drugs = new ArrayList<>(plugin.drugs().all());
        if (drugs.isEmpty()) return;
        int count = plugin.getConfig().getInt("district.customer-profiles", 8);
        List<String> names = new ArrayList<>(List.of(FIRST));
        for (int i = 0; i < count && !names.isEmpty(); i++) {
            String name = names.remove(r.nextInt(names.size()));
            Drug drug = drugs.get(r.nextInt(drugs.size()));
            int minQuality = new int[]{0, 0, 25, 40, 60}[r.nextInt(5)];
            double budget = 60 + r.nextInt(240);
            boolean nightOwl = r.nextBoolean();
            long open = nightOwl ? 12000 + r.nextInt(3000) : 1000 + r.nextInt(4000);
            long close = nightOwl ? 22000 + r.nextInt(1500) : 10000 + r.nextInt(3000);
            Villager.Profession prof = switch (r.nextInt(6)) {
                case 0 -> Villager.Profession.LIBRARIAN;
                case 1 -> Villager.Profession.FISHERMAN;
                case 2 -> Villager.Profession.BUTCHER;
                case 3 -> Villager.Profession.LEATHERWORKER;
                case 4 -> Villager.Profession.MASON;
                default -> Villager.Profession.NONE;
            };
            String haunt = HAUNTS[r.nextInt(HAUNTS.length)];
            String id = "c" + i;
            profiles.put(id, new CustomerProfile(id, name, drug.id, minQuality, budget, open, close, prof, haunt));
        }
    }

    // ------------------------------------------------------------------ spawning

    public LivingEntity spawn(String type, String id, Location at, String display) {
        EntityType entityType = "thug".equals(type) ? EntityType.ZOMBIE : EntityType.VILLAGER;
        Entity e = at.getWorld().spawnEntity(at, entityType);
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return null;
        }
        le.setPersistent(true);
        le.setRemoveWhenFarAway(false);
        le.customName(Msg.mm(display));
        le.setCustomNameVisible(true);
        le.getPersistentDataContainer().set(keys.npcType, PersistentDataType.STRING, type);
        if (id != null) le.getPersistentDataContainer().set(keys.npcId, PersistentDataType.STRING, id);
        le.getPersistentDataContainer().set(keys.spawnedAt, PersistentDataType.LONG, System.currentTimeMillis());
        if(plugin.district().takeoverId()!=null && plugin.district().contains(at))
            le.getPersistentDataContainer().set(keys.takeoverId,PersistentDataType.STRING,plugin.district().takeoverId());

        if (le instanceof Villager v) {
            v.setProfession(professionFor(type, id));
            v.setVillagerType(Villager.Type.PLAINS);
            v.setVillagerLevel(2);
            v.setInvulnerable(!"customer".equals(type));
        }
        if (le instanceof Zombie z) {
            z.setShouldBurnInDay(false);
            var eq = z.getEquipment();
            if (eq != null) {
                eq.setItemInMainHand(new ItemStack(Material.IRON_AXE));
                eq.setHelmet(new ItemStack(Material.LEATHER_HELMET));
                eq.setItemInMainHandDropChance(0f);
                eq.setHelmetDropChance(0f);
            }
        }
        return le;
    }

    private Villager.Profession professionFor(String type, String id) {
        return switch (type) {
            case "medic" -> Villager.Profession.CLERIC;
            case "fixer" -> Villager.Profession.WEAPONSMITH;
            case "trader" -> Villager.Profession.FARMER;
            case "guard" -> Villager.Profession.ARMORER;
            case "resident" -> Villager.Profession.NITWIT;
            case "customer" -> {
                CustomerProfile p = profiles.get(id);
                yield p == null ? Villager.Profession.NONE : p.profession;
            }
            default -> Villager.Profession.NONE;
        };
    }

    public String typeOf(Entity e) {
        return e.getPersistentDataContainer().get(keys.npcType, PersistentDataType.STRING);
    }

    public String idOf(Entity e) {
        return e.getPersistentDataContainer().get(keys.npcId, PersistentDataType.STRING);
    }

    /** Removes every NPC this plugin spawned, used on district rebuild. */
    public int despawnAll() {
        int n = 0;
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class)) {
                String t = typeOf(e);
                if (t != null && !"hallucination".equals(t)
                        && !e.getPersistentDataContainer().has(keys.reusedVillager,PersistentDataType.BYTE)) {
                    e.remove();
                    n++;
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ interaction

    /** Returns true if the interaction was handled by an NPC. */
    public boolean interact(Player p, Entity e) {
        String type = typeOf(e);
        if (type == null) return false;
        switch (type) {
            case "customer" -> openCustomer(p, idOf(e));
            case "medic" -> openClinic(p);
            case "fixer" -> openFixer(p);
            case "trader" -> openTrader(p);
            case "resident" -> resident(p, e);
            case "guard" -> guard(p);
            case "thug" -> { return false; }
            default -> { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------------ customers

    public void openCustomer(Player p, String id) {
        CustomerProfile profile = profiles.get(id);
        if (profile == null) return;
        Drug drug = plugin.drugs().get(profile.drug);
        if (drug == null) return;

        PlayerData pd = plugin.players().get(p);
        long worldTime = p.getWorld().getTime();
        if (!profile.openAt(worldTime)) {
            Msg.npc(p, profile.name, "Not now. Find me around " + profile.haunt + ", " + profile.hours() + ".");
            return;
        }

        int trust = pd.trust(profile.id);
        Menu menu = new Menu(Menu.Type.CUSTOMER, null, profile.id);
        Inventory inv = Bukkit.createInventory(menu, 27, Msg.mm("<dark_gray>" + profile.name + "</dark_gray>"));
        menu.bind(inv);

        double unit = plugin.market().unitPrice(drug, Math.max(profile.minQuality, 50), pd, trust);
        List<String> head = new ArrayList<>();
        head.add("<gray>Wants:</gray> " + drug.colored());
        head.add("<gray>Minimum grade:</gray> " + Quality.Grade.of(profile.minQuality).colored());
        head.add("<gray>Budget today:</gray> <green>" + Msg.money(profile.budget) + "</green>");
        head.add("<gray>Around:</gray> <white>" + profile.hours() + "</white> <dark_gray>(" + profile.haunt + ")</dark_gray>");
        head.add("<gray>Trust:</gray> " + Msg.gauge(trust, 100, "aqua"));
        head.add("<gray>Local demand:</gray> " + Msg.gauge(plugin.market().ratio(drug), 1, "gold"));
        inv.setItem(4, icon(Material.PAPER, "<yellow>" + profile.name + "</yellow>", head));

        int have = matchingUnits(p, drug, profile.minQuality);
        inv.setItem(11, icon(drug.product.material, "<white>Sell 1 unit</white>", List.of(
                "<gray>You carry <white>" + have + "</white> suitable units.</gray>",
                "<gray>About</gray> <green>" + Msg.money(unit) + "</green> <gray>each.</gray>",
                have > 0 ? "<green>Click to sell one.</green>" : "<red>Nothing they would buy.</red>")));
        if (have > 0) menu.actions.put(11, () -> sell(p, profile, 1));

        int bulk = Math.min(have, 10);
        inv.setItem(13, icon(Material.PAPER, "<white>Sell " + bulk + " units</white>", List.of(
                "<gray>Sell as many as their budget and</gray>",
                "<gray>the local demand allow.</gray>",
                bulk > 0 ? "<green>Click to sell.</green>" : "<red>Nothing to sell.</red>")));
        if (bulk > 0) menu.actions.put(13, () -> sell(p, profile, bulk));

        boolean canHaggle = System.currentTimeMillis() - haggled.getOrDefault(p.getName() + profile.id, 0L) > 300_000L;
        inv.setItem(15, icon(Material.EMERALD, "<gold>Haggle</gold>", List.of(
                "<gray>Push for a better price.</gray>",
                "<gray>Works better with trust and</gray>",
                "<gray>a good name among traders.</gray>",
                canHaggle ? "<green>Click to try.</green>" : "<red>They have had enough of that today.</red>")));
        if (canHaggle) menu.actions.put(15, () -> haggle(p, profile));

        boolean contractReady = trust >= 30 && pd.contract == null;
        inv.setItem(22, icon(Material.WRITABLE_BOOK, "<aqua>Ask about a bulk order</aqua>", List.of(
                trust >= 30 ? "<gray>They trust you enough to talk volume.</gray>" : "<gray>Needs trust 30. You have " + trust + ".</gray>",
                pd.contract == null ? "" : "<red>You already have an open contract.</red>",
                contractReady ? "<green>Click to ask.</green>" : "")));
        if (contractReady) menu.actions.put(22, () -> offerContract(p, profile));

        p.openInventory(inv);
    }

    private int matchingUnits(Player p, Drug drug, int minQuality) {
        Items items = plugin.items();
        int total = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (!drug.id.equals(items.drugId(it))) continue;
            String kind = items.kind(it);
            if (!Items.PRODUCT.equals(kind) && !Items.PACKAGE.equals(kind)) continue;
            if (items.quality(it) < minQuality) continue;
            total += items.units(it);
        }
        return total;
    }

    private void sell(Player p, CustomerProfile profile, int wanted) {
        Drug drug = plugin.drugs().get(profile.drug);
        if (drug == null) return;
        PlayerData pd = plugin.players().get(p);
        Items items = plugin.items();
        int trust = pd.trust(profile.id);

        double demandLeft = plugin.market().demand(drug);
        if (demandLeft < 1) {
            Msg.npc(p, profile.name, "Everyone around here is already holding. Come back when the street dries up.");
            p.closeInventory();
            return;
        }

        int sold = 0;
        double paid = 0;
        int qualitySum = 0;
        int contractUnits = 0;
        double budget = profile.budget * (1.0 + trust / 200.0);

        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && sold < wanted; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir()) continue;
            if (!drug.id.equals(items.drugId(it))) continue;
            String kind = items.kind(it);
            boolean isPackage = Items.PACKAGE.equals(kind);
            if (!Items.PRODUCT.equals(kind) && !isPackage) continue;
            int quality = items.quality(it);
            if (quality < profile.minQuality) continue;

            double unit = plugin.market().unitPrice(drug, quality, pd, trust);
            if (!Double.isFinite(unit) || unit <= 0) continue;
            int amount = Math.min(items.units(it), Math.min(wanted - sold,
                    Math.min((int) Math.floor(demandLeft - sold), (int) Math.floor((budget - paid) / unit))));
            if (amount <= 0) continue;
            dev.lucas.slumdrugs.drug.GoodsTransfer.takeFromSlot(p, items, drug, i, amount);
            // Refresh references: splitting a package can put change into a later slot.
            contents = p.getInventory().getContents();
            sold += amount;
            paid += unit * amount;
            qualitySum += quality * amount;
            if (pd.contract != null && quality >= pd.contract.minQuality) contractUnits += amount;
        }

        if (sold == 0) {
            Msg.npc(p, profile.name, "Not what I am after, or not good enough. I need "
                    + Quality.Grade.of(profile.minQuality).label + " or better.");
            p.closeInventory();
            return;
        }

        plugin.economy().deposit(p, paid);
        plugin.market().consume(drug, sold);
        pd.totalSales += sold;
        pd.addTrust(profile.id, Math.min(6, 1 + sold / 4));
        pd.addRep("traders", 1);

        int avgQuality = qualitySum / sold;
        if (avgQuality >= 70) pd.addTrust(profile.id, 2);

        plugin.heat().onSale(p, sold, avgQuality);
        if (plugin.district().contains(p.getLocation())) plugin.district().nudge(-0.35);

        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.9f, 1.0f);
        Msg.npc(p, profile.name, reaction(avgQuality, profile.minQuality));
        Msg.send(p, "Sold <white>" + sold + "</white> units for <green>" + Msg.money(paid) + "</green>"
                + " <gray>(trust " + pd.trust(profile.id) + ")</gray>");

        if (pd.contract != null && pd.contract.customerId.equals(profile.id) && drug.id.equals(pd.contract.drug)
                && System.currentTimeMillis() < pd.contract.deadline && contractUnits > 0) {
            pd.contract.units -= contractUnits;
            if (pd.contract.units <= 0) completeContract(p, pd);
            else Msg.send(p, "<aqua>Contract:</aqua> <gray>" + pd.contract.units + " units still owed.</gray>");
        }
        p.closeInventory();
    }

    private String reaction(int quality, int minQuality) {
        if (quality >= 80) return "That is the good stuff. I will find you again.";
        if (quality >= 60) return "Clean work. Same time next week?";
        if (quality >= minQuality + 10) return "Fine. It does the job.";
        return "Barely passable, but I will take it.";
    }

    private void haggle(Player p, CustomerProfile profile) {
        PlayerData pd = plugin.players().get(p);
        haggled.put(p.getName() + profile.id, System.currentTimeMillis());
        double chance = 0.35 + pd.trust(profile.id) / 250.0 + pd.rep("traders") / 300.0;
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            pd.addTrust(profile.id, 3);
            pd.addRep("traders", 2);
            Msg.npc(p, profile.name, "You drive a hard bargain. Fine, a little more.");
            Msg.send(p, "<green>They will pay better for a while.</green>");
        } else {
            pd.addTrust(profile.id, -4);
            Msg.npc(p, profile.name, "Do not push me. I have other suppliers.");
            Msg.send(p, "<red>Trust with " + profile.name + " dropped.</red>");
        }
        p.closeInventory();
    }

    private void offerContract(Player p, CustomerProfile profile) {
        PlayerData pd = plugin.players().get(p);
        Drug drug = plugin.drugs().get(profile.drug);
        if (drug == null || pd.contract != null) return;

        PlayerData.Contract c = new PlayerData.Contract();
        c.customerId = profile.id;
        c.customerName = profile.name;
        c.drug = drug.id;
        c.units = 12 + ThreadLocalRandom.current().nextInt(24);
        c.minQuality = Math.max(profile.minQuality, 40);
        c.reward = c.units * drug.basePrice * 0.6;
        c.deadline = System.currentTimeMillis() + (30 + ThreadLocalRandom.current().nextInt(30)) * 60_000L;
        pd.contract = c;

        Msg.npc(p, profile.name, "I need " + c.units + " units of " + drug.name + ", "
                + Quality.Grade.of(c.minQuality).label + " or better. Sell them to me before the deadline and there is a bonus.");
        Msg.send(p, "<aqua>Contract accepted:</aqua> <gray>" + c.units + " units, bonus <green>"
                + Msg.money(c.reward) + "</green>, " + Msg.minutes(c.deadline - System.currentTimeMillis()) + " to deliver.</gray>");
        p.closeInventory();
    }

    private void completeContract(Player p, PlayerData pd) {
        PlayerData.Contract c = pd.contract;
        pd.contract = null;
        plugin.economy().deposit(p, c.reward);
        pd.addTrust(c.customerId, 12);
        pd.addRep("traders", 6);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        Msg.send(p, "<green><bold>Contract complete.</bold></green> <gray>Bonus <green>" + Msg.money(c.reward) + "</green> from " + c.customerName + ".</gray>");
    }

    // ------------------------------------------------------------------ clinic

    public void openClinic(Player p) {
        Menu menu = new Menu(Menu.Type.CLINIC, null, null);
        Inventory inv = Bukkit.createInventory(menu, 27, Msg.mm("<dark_gray>Clinic</dark_gray>"));
        menu.bind(inv);
        PlayerData pd = plugin.players().get(p);
        long now = System.currentTimeMillis();
        double worst = plugin.condition().worstWithdrawal(pd, now);

        List<String> state = new ArrayList<>();
        state.add("<gray>Withdrawal right now:</gray> " + Msg.gauge(worst, 1, "red"));
        double dep = 0;
        for (PlayerData.Condition c : pd.conditions.values()) dep = Math.max(dep, c.dependence);
        state.add("<gray>Worst dependence:</gray> " + Msg.gauge(dep, 100, "red"));
        state.add("<gray>Recovery is always possible. Time, sleep</gray>");
        state.add("<gray>and treatment all bring it down.</gray>");
        inv.setItem(4, icon(Material.GLASS_BOTTLE, "<aqua>Your chart</aqua>", state));

        double price = plugin.getConfig().getDouble("clinic.treatment-price", 60);
        inv.setItem(11, icon(Material.GOLDEN_APPLE, "<green>Treatment</green> <gray>(" + Msg.money(price) + ")</gray>", List.of(
                "<gray>Cuts dependence by "
                        + (int) plugin.getConfig().getDouble("clinic.treatment-dependence-reduction", 25) + " points</gray>",
                "<gray>and suppresses withdrawal for "
                        + plugin.getConfig().getInt("clinic.treatment-suppress-minutes", 15) + " minutes.</gray>",
                "<green>Click to be treated.</green>")));
        menu.actions.put(11, () -> {
            if (plugin.condition().treat(p)) p.closeInventory();
        });

        double remedyPrice = plugin.getConfig().getDouble("clinic.remedy-price", 25);
        inv.setItem(15, icon(Material.HONEY_BOTTLE, "<aqua>Buy a remedy</aqua> <gray>(" + Msg.money(remedyPrice) + ")</gray>", List.of(
                "<gray>A portable dose that eases</gray>",
                "<gray>withdrawal for five minutes.</gray>",
                "<green>Click to buy one.</green>")));
        menu.actions.put(15, () -> {
            if (!plugin.economy().withdraw(p, remedyPrice)) {
                Msg.npc(p, "Medic", "Not enough for that, friend.");
                return;
            }
            StationManager.give(p, plugin.items().remedy(1));
            Msg.send(p, "<aqua>Bought a remedy.</aqua>");
            p.closeInventory();
        });

        inv.setItem(22, icon(Material.WHITE_BED, "<white>Advice</white>", List.of(
                "<gray>Sleep a full night: recovery runs</gray>",
                "<gray>two and a half times faster for ten minutes.</gray>",
                "<gray>Staying clean for "
                        + plugin.getConfig().getInt("condition.dependence-decay-delay-minutes", 20)
                        + " minutes starts recovery on its own.</gray>")));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ fixer (gang jobs)

    public void openFixer(Player p) {
        PlayerData pd = plugin.players().get(p);
        Menu menu = new Menu(Menu.Type.FIXER, null, null);
        Inventory inv = Bukkit.createInventory(menu, 27, Msg.mm("<dark_gray>The Warehouse</dark_gray>"));
        menu.bind(inv);

        inv.setItem(4, icon(Material.IRON_BARS, "<red>Vosk</red>", List.of(
                "<gray>Runs the warehouse crew.</gray>",
                "<gray>Your standing:</gray> " + Msg.gauge(Math.max(0, pd.rep("gangs")), 100, "red"),
                "<gray>Work for him and the guards notice.</gray>")));

        if (pd.delivery == null) {
            inv.setItem(13, icon(Material.PAPER, "<gold>Take a delivery run</gold>", List.of(
                    "<gray>Carry a package to a drop box.</gray>",
                    "<gray>Pays well. Raises gang standing.</gray>",
                    "<red>Raises heat, and runs can be intercepted.</red>",
                    "<green>Click to accept.</green>")));
            menu.actions.put(13, () -> giveDelivery(p, pd));
        } else {
            inv.setItem(13, icon(Material.PAPER, "<gold>Current run</gold>", List.of(
                    "<gray>" + pd.delivery.units + " units of " + pd.delivery.drug + "</gray>",
                    "<gray>to the " + pd.delivery.dropBox + " drop box.</gray>",
                    "<gray>Time left: " + Msg.minutes(pd.delivery.deadline - System.currentTimeMillis()) + "</gray>")));
        }

        inv.setItem(15, icon(Material.EMERALD, "<green>Pay off the guards</green> <gray>(" + Msg.money(120) + ")</gray>", List.of(
                "<gray>A negotiated fine. Clears most of your heat</gray>",
                "<gray>and avoids a raid, at a price.</gray>",
                "<green>Click to pay.</green>")));
        menu.actions.put(15, () -> {
            if (!plugin.economy().withdraw(p, 120)) {
                Msg.npc(p, "Vosk", "Come back with the money.");
                return;
            }
            plugin.heat().clear(p, 45);
            Msg.npc(p, "Vosk", "Consider it handled. For now.");
            p.closeInventory();
        });
        p.openInventory(inv);
    }

    private void giveDelivery(Player p, PlayerData pd) {
        List<Drug> drugs = new ArrayList<>(plugin.drugs().all());
        if (drugs.isEmpty()) return;
        Drug drug = drugs.get(ThreadLocalRandom.current().nextInt(drugs.size()));
        PlayerData.Delivery d = new PlayerData.Delivery();
        d.drug = drug.id;
        d.units = 6 + ThreadLocalRandom.current().nextInt(10);
        var destinations = plugin.district().dropBoxes().stream().map(plugin.district()::dropBoxName)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (destinations.isEmpty()) { Msg.npc(p, "Vosk", "No delivery routes are available."); return; }
        d.dropBox = destinations.get(ThreadLocalRandom.current().nextInt(destinations.size()));
        d.reward = d.units * drug.basePrice * 0.9 + 40;
        d.deadline = System.currentTimeMillis() + 20 * 60_000L;
        pd.delivery = d;
        pd.addRep("gangs", 2);
        plugin.heat().add(p, plugin.getConfig().getDouble("heat.faction-job", 3));
        Msg.npc(p, "Vosk", "Take " + d.units + " units of " + drug.name + " to the " + d.dropBox
                + " drop box. Twenty minutes. Do not get seen.");
        Msg.send(p, "<gold>Delivery accepted.</gold> <gray>Reward <green>" + Msg.money(d.reward) + "</green>.</gray>");
        p.closeInventory();
    }

    // ------------------------------------------------------------------ trader

    public void openTrader(Player p) {
        Menu menu = new Menu(Menu.Type.TRADER, null, null);
        Inventory inv = Bukkit.createInventory(menu, 27, Msg.mm("<dark_gray>Market Stall</dark_gray>"));
        menu.bind(inv);
        int slot = 10;
        for (Drug d : plugin.drugs().grown()) {
            double price = d.basePrice * 1.5;
            inv.setItem(slot, icon(d.crop.seedMaterial, d.colored() + " <gray>seeds</gray>",
                    List.of("<gray>Price:</gray> <green>" + Msg.money(price) + "</green>",
                            "<gray>Grows on " + Items.pretty(d.crop.soil) + ".</gray>",
                            "<green>Click to buy one.</green>")));
            final Drug drug = d;
            final double cost = price;
            menu.actions.put(slot, () -> {
                if (!plugin.economy().withdraw(p, cost)) {
                    Msg.send(p, "<red>Not enough money.</red>");
                    return;
                }
                StationManager.give(p, plugin.items().seed(drug, 1));
                Msg.send(p, "<green>Bought</green> " + drug.colored() + " <gray>seeds.</gray>");
            });
            slot++;
            if (slot == 17) slot = 19;
            if (slot > 25) break;
        }
        inv.setItem(4, icon(Material.COMPOSTER, "<green>Fertilizer</green> <gray>(" + Msg.money(15) + ")</gray>",
                List.of("<gray>Speeds growth and lifts quality.</gray>", "<green>Click to buy two.</green>")));
        menu.actions.put(4, () -> {
            if (!plugin.economy().withdraw(p, 15)) {
                Msg.send(p, "<red>Not enough money.</red>");
                return;
            }
            StationManager.give(p, plugin.items().fertilizer(2));
            Msg.send(p, "<green>Bought fertilizer.</green>");
        });
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ residents and guards

    private void resident(Player p, Entity e) {
        PlayerData pd = plugin.players().get(p);
        String name = e.getPersistentDataContainer().get(keys.npcId, PersistentDataType.STRING);
        if (name == null) name = "Neighbour";

        if (pd.job != null && pd.job.residentName.equals(name)) {
            int have = 0;
            for (ItemStack it : p.getInventory().getContents()) {
                if (it != null && it.getType() == pd.job.material && !plugin.items().isPluginItem(it)) have += it.getAmount();
            }
            if (have >= pd.job.amount) {
                p.getInventory().removeItem(new ItemStack(pd.job.material, pd.job.amount));
                plugin.economy().deposit(p, pd.job.pay);
                pd.addRep("residents", 5);
                plugin.heat().add(p, -3);
                plugin.district().nudge(2.0);
                Msg.npc(p, name, "You actually came back. Here, take it.");
                Msg.send(p, "<green>Job done.</green> <gray>Earned " + Msg.money(pd.job.pay)
                        + " and the neighbours think better of you.</gray>");
                pd.job = null;
            } else {
                Msg.npc(p, name, "Still need " + (pd.job.amount - have) + " more "
                        + Items.pretty(pd.job.material).toLowerCase() + ".");
            }
            return;
        }

        if (pd.job != null) {
            Msg.npc(p, name, "Ask " + pd.job.residentName + " first. You already took their work.");
            return;
        }

        PlayerData.LegalJob job = new PlayerData.LegalJob();
        job.residentName = name;
        Material[] wanted = {Material.BREAD, Material.COAL, Material.OAK_PLANKS, Material.COBBLESTONE, Material.COD, Material.LEATHER};
        job.material = wanted[ThreadLocalRandom.current().nextInt(wanted.length)];
        job.amount = 8 + ThreadLocalRandom.current().nextInt(17);
        job.pay = job.amount * 2.5 + 10;
        pd.job = job;
        Msg.npc(p, name, "Bring me " + job.amount + " " + Items.pretty(job.material).toLowerCase()
                + " and I will pay you " + Msg.money(job.pay) + ". Honest work, if you want it.");
    }

    private void guard(Player p) {
        PlayerData pd = plugin.players().get(p);
        if (pd.heat > 60) {
            Msg.npc(p, "Guard", "I know what you have been doing. Keep walking.");
        } else if (pd.heat > 25) {
            Msg.npc(p, "Guard", "You have been seen in the wrong places lately.");
        } else if (pd.rep("residents") > 30) {
            Msg.npc(p, "Guard", "The neighbours speak well of you. Keep it that way.");
        } else {
            Msg.npc(p, "Guard", "Move along. Nothing to see in this district.");
        }
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData pd = plugin.players().get(p);
            if (pd.contract != null && now > pd.contract.deadline) {
                Msg.send(p, "<red>Contract expired.</red> <gray>" + pd.contract.customerName + " found another supplier.</gray>");
                pd.addTrust(pd.contract.customerId, -10);
                pd.addRep("traders", -3);
                pd.contract = null;
            }
            if (pd.delivery != null && now > pd.delivery.deadline) {
                Msg.send(p, "<red>The delivery window closed.</red> <gray>Vosk is not pleased.</gray>");
                pd.addRep("gangs", -5);
                pd.delivery = null;
            }
            if (plugin.district().contains(p.getLocation())) {
                investigate(p, pd);
                muscle(p, pd, now);
            }
        }
    }

    /** Guards drift toward players who have drawn attention, and say so before doing anything. */
    private void investigate(Player p, PlayerData pd) {
        if (pd.heat < 35) return;
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 24, 10, 24)) {
            if (!"guard".equals(typeOf(e)) || !(e instanceof org.bukkit.entity.Mob guard)) continue;
            if (guard.getLocation().distanceSquared(p.getLocation()) < 16) {
                if (ThreadLocalRandom.current().nextInt(12) == 0) {
                    Msg.npc(p, "Guard", pd.heat > 70
                            ? "Empty your pockets or walk away slowly. Your choice."
                            : "I have my eye on you.");
                }
                continue;
            }
            guard.getPathfinder().moveTo(p.getLocation(), 0.9);
        }
    }

    /** Gang muscle turns up when the crew has a grudge and the player is carrying. */
    private void muscle(Player p, PlayerData pd, long now) {
        if (pd.rep("gangs") > -20) return;
        int interval = plugin.getConfig().getInt("district.thug-spawn-interval-seconds", 240);
        if (now - lastThug.getOrDefault(p.getUniqueId(), 0L) < interval * 1000L) return;

        int max = plugin.getConfig().getInt("district.max-thugs", 4);
        int alive = 0;
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 40, 20, 40)) {
            if ("thug".equals(typeOf(e))) alive++;
        }
        if (alive >= max) return;

        lastThug.put(p.getUniqueId(), now);
        int count = Math.min(max - alive, 1 + ThreadLocalRandom.current().nextInt(2));
        for (int i = 0; i < count; i++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            Location at = p.getLocation().clone().add(Math.cos(angle) * 12, 0, Math.sin(angle) * 12);
            at.setY(at.getWorld().getHighestBlockYAt(at) + 1);
            LivingEntity e = spawn("thug", null, at, "<dark_red>Crew Enforcer</dark_red>");
            if (e instanceof org.bukkit.entity.Mob mob) mob.setTarget(p);
        }
        Msg.send(p, "<dark_red>The crew sent someone for you.</dark_red>");
        p.playSound(p.getLocation(), Sound.ENTITY_VINDICATOR_AMBIENT, 0.8f, 0.9f);
    }

    // ------------------------------------------------------------------ helpers

    public static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.mm("<!italic>" + name));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) if (!s.isEmpty()) l.add(Msg.mm("<!italic>" + s));
        m.lore(l);
        it.setItemMeta(m);
        return it;
    }
}
