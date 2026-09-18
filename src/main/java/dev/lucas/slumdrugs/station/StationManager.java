package dev.lucas.slumdrugs.station;

import dev.lucas.slumdrugs.Msg;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.drug.Items;
import dev.lucas.slumdrugs.drug.Quality;
import dev.lucas.slumdrugs.ui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Placement, interaction and persistence for drying racks, benches, packaging stations and crates. */
public final class StationManager {

    private final SlumDrugsPlugin plugin;
    private final Map<String, Station> stations = new LinkedHashMap<>();
    private final Map<String, Inventory> openCrates = new LinkedHashMap<>();
    private final File file;
    private BukkitTask task;
    private final FurnitureDisplay furniture;

    public StationManager(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stations.yml");
        this.furniture = new FurnitureDisplay(plugin);
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    public void stop() {
        if (task != null) task.cancel();
        furniture.clear();
        for (Inventory inventory : new ArrayList<>(openCrates.values()))
            for (var viewer : new ArrayList<>(inventory.getViewers())) viewer.closeInventory();
    }

    public int count() { return stations.size(); }

    public Station at(Location l) { return stations.get(Station.key(l)); }

    public void register(Location l, StationType type, UUID owner) {
        stations.put(Station.key(l), new Station(l, type, owner));
    }

    public void placed(Location location, float yaw) {
        Station station=at(location);
        if(station==null) return;
        station.yaw=Math.round(yaw/90f)*90f;
        furniture.sync(station);
        save();
    }

    public void chunk(org.bukkit.Chunk chunk, boolean loading) {
        for(Station station:stations.values()) {
            var l=station.location;
            if(l.getWorld().equals(chunk.getWorld()) && l.getBlockX()>>4==chunk.getX() && l.getBlockZ()>>4==chunk.getZ()) {
                if(loading) furniture.sync(station); else furniture.remove(station);
            }
        }
    }

    /** Removes a station and returns the items that should drop with it. */
    public List<ItemStack> unregister(Location l) {
        Inventory live = openCrates.remove(Station.key(l));
        if (live != null) {
            saveCrate(l, live);
            for (var viewer : new ArrayList<>(live.getViewers())) viewer.closeInventory();
        }
        Station s = stations.remove(Station.key(l));
        List<ItemStack> drops = new ArrayList<>();
        if (s == null) return drops;
        furniture.remove(s);
        drops.add(plugin.items().station(s.type, 1));
        if(s.type==StationType.GROWBOX && s.growbox.drug!=null) {
            Drug crop=plugin.drugs().get(s.growbox.drug);
            if(crop!=null && crop.isGrown()) {
                s.growbox.advance(System.currentTimeMillis(),crop.crop.growthMinutes*60);
                drops.add(s.growbox.progress>=1 ? plugin.items().raw(crop,60+s.growbox.fertilizer*8,
                        s.growbox.grower,Items.newBatchId(),crop.crop.yield) : plugin.items().seed(crop,1));
            }
        }
        for (Station.Batch b : s.batches) if (b.input != null) drops.add(b.input);
        if (s.storage != null) for (ItemStack it : s.storage) if (it != null && !it.getType().isAir()) drops.add(it);
        return drops;
    }

    // ------------------------------------------------------------------ interaction

    public void interact(Player p, Station station, ItemStack inHand) {
        if (p.isSneaking() && inHand.getType()==Material.STICK && !plugin.items().isPluginItem(inHand)) {
            station.yaw=(station.yaw+90)%360; furniture.sync(station); save();
            Msg.send(p,"<gray>Furniture rotated.</gray>"); return;
        }
        switch (station.type) {
            case DRYING_RACK -> dryingRack(p, station, inHand);
            case PROCESSING_BENCH -> openBench(p, station);
            case PACKAGING_STATION -> packaging(p, station, inHand);
            case STORAGE_CRATE -> openCrate(p, station);
            case GROWBOX -> growbox(p,station,inHand);
        }
    }

    private void growbox(Player p, Station station, ItemStack hand) {
        GrowboxState state=station.growbox;
        Drug crop=plugin.drugs().get(state.drug);
        if(crop!=null && crop.isGrown()) state.advance(System.currentTimeMillis(),crop.crop.growthMinutes*60);
        Items items=plugin.items();
        if(p.isSneaking() && hand.getType().isAir()) {
            state.lamp=!state.lamp; state.updatedAt=System.currentTimeMillis();
            Msg.send(p,state.lamp?"<yellow>Grow lamp on.</yellow>":"<gray>Grow lamp off. Growth paused.</gray>");
        } else if(hand.getType()==Material.WATER_BUCKET && !items.isPluginItem(hand)) {
            if(state.waterSeconds>=1200) { Msg.send(p,"<gray>The water tank is full.</gray>"); return; }
            state.waterSeconds=1200;
            if(p.getGameMode()!=org.bukkit.GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount()-1); give(p,new ItemStack(Material.BUCKET));
            }
            Msg.send(p,"<aqua>Water tank filled: 20 minutes of active growing.</aqua>");
        } else if(items.is(hand,Items.SEED) && state.drug==null) {
            Drug selected=plugin.drugs().get(items.drugId(hand));
            if(selected==null || !selected.isGrown()) return;
            state.drug=selected.id; state.grower=p.getName(); state.progress=0; state.fertilizer=0;
            state.updatedAt=System.currentTimeMillis();
            if(p.getGameMode()!=org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount()-1);
            Msg.send(p,"<green>Planted </green>"+selected.colored()+"<gray>. Add water and keep the lamp on.</gray>");
        } else if(items.is(hand,Items.FERTILIZER) && state.drug!=null && state.progress<1 && state.fertilizer<3) {
            state.fertilizer++;
            if(p.getGameMode()!=org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount()-1);
            Msg.send(p,"<green>Fertilized: "+state.fertilizer+"/3.</green>");
        } else if(hand.getType().isAir() && crop!=null && crop.isGrown() && state.progress>=1) {
            give(p,items.raw(crop,60+state.fertilizer*8,state.grower,Items.newBatchId(),crop.crop.yield));
            give(p,items.seed(crop,1)); state.clear();
            Msg.send(p,"<green>Harvest collected. Plant another seed to start again.</green>");
        } else {
            Msg.send(p,"<gold>Growbox</gold> <gray>"+(state.drug==null?"Empty":state.drug+" · "+Math.round(state.progress*100)+"%")
                    +" · water "+Math.round(state.waterSeconds/60)+" min · fertilizer "+state.fertilizer+"/3.</gray>");
            Msg.send(p,"<gray>Use seeds, a water bucket or fertilizer. Empty hand to harvest; sneak + empty hand toggles the lamp; sneak + stick rotates.</gray>");
        }
        furniture.sync(station); save();
    }

    // ------------------------------------------------------------------ drying

    private void dryingRack(Player p, Station station, ItemStack inHand) {
        Items items = plugin.items();
        if (items.is(inHand, Items.RAW)) {
            if (station.batches.size() >= 9) {
                Msg.send(p, "<gray>The rack is full.</gray>");
                return;
            }
            Drug drug = plugin.drugs().get(items.drugId(inHand));
            if (drug == null || !drug.isGrown()) return;

            Station.Batch batch = new Station.Batch();
            batch.input = inHand.clone();
            batch.startedAt = System.currentTimeMillis();
            long seconds = (long) (drug.crop.drySeconds * (1.0 + 0.15 * (inHand.getAmount() - 1)));
            batch.finishAt = batch.startedAt + seconds * 1000L;
            station.batches.add(batch);

            inHand.setAmount(0);
            p.playSound(station.location, Sound.BLOCK_SMOKER_SMOKE, 0.7f, 1.0f);
            Msg.send(p, "Hung " + batch.input.getAmount() + "x " + drug.colored()
                    + " <gray>to dry. Ready in " + Msg.minutes(seconds * 1000L) + ".</gray>");
            return;
        }
        openRack(p, station);
    }

    private void openRack(Player p, Station station) {
        Menu menu = new Menu(Menu.Type.DRYING_RACK, station.location, null);
        Inventory inv = Bukkit.createInventory(menu, 9, Msg.mm("<dark_gray>Drying Rack</dark_gray>"));
        menu.bind(inv);
        long now = System.currentTimeMillis();

        if (station.batches.isEmpty()) {
            inv.setItem(4, info(Material.DEAD_BUSH, "<gray>Empty</gray>",
                    List.of("<gray>Right-click the rack holding raw</gray>", "<gray>harvest to hang it up.</gray>")));
        }
        for (int i = 0; i < station.batches.size() && i < 9; i++) {
            Station.Batch b = station.batches.get(i);
            double prog = b.progress(now);
            boolean done = prog >= 1.0;
            Drug drug = plugin.drugs().get(plugin.items().drugId(b.input));
            String name = drug == null ? "<gray>Batch</gray>" : drug.colored();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Amount:</gray> <white>" + b.input.getAmount() + "</white>");
            lore.add("<gray>Progress:</gray> " + Msg.gauge(prog, 1, done ? "green" : "yellow"));
            lore.add(done ? "<green>Click to collect.</green>" : "<gray>Ready in " + Msg.minutes(b.finishAt - now) + ".</gray>");
            inv.setItem(i, info(done ? Material.DRIED_KELP : Material.KELP, name, lore));

            final int index = i;
            if (done) menu.actions.put(i, () -> {
                int current = station.batches.indexOf(b);
                if (at(station.location) == station && current >= 0) collect(p, station, current);
            });
        }
        p.openInventory(inv);
    }

    private void collect(Player p, Station station, int index) {
        if (index >= station.batches.size()) return;
        Station.Batch b = station.batches.get(index);
        if (System.currentTimeMillis() < b.finishAt) return;

        Items items = plugin.items();
        Drug drug = plugin.drugs().get(items.drugId(b.input));
        if (drug == null) {
            station.batches.remove(index);
            return;
        }
        int quality = Quality.clamp(items.quality(b.input) + ThreadLocalRandom.current().nextInt(-3, 6));
        ItemStack out = items.dried(drug, quality, items.grower(b.input), items.batch(b.input), b.input.getAmount());
        station.batches.remove(index);

        give(p, out);
        p.playSound(station.location, Sound.BLOCK_COMPOSTER_FILL_SUCCESS, 0.8f, 1.1f);
        Msg.send(p, "Collected " + out.getAmount() + "x " + drug.colored() + " <gray>(dried, "
                + Quality.Grade.of(quality).colored() + "<gray>).</gray>");
        openRack(p, station);
    }

    // ------------------------------------------------------------------ processing bench

    private void openBench(Player p, Station station) {
        Menu menu = new Menu(Menu.Type.PROCESSING_BENCH, station.location, null);
        List<Drug> all = new ArrayList<>(plugin.drugs().all());
        int rows = Math.max(1, (int) Math.ceil(all.size() / 9.0));
        Inventory inv = Bukkit.createInventory(menu, rows * 9, Msg.mm("<dark_gray>Processing Bench</dark_gray>"));
        menu.bind(inv);

        for (int i = 0; i < all.size() && i < rows * 9; i++) {
            Drug d = all.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Makes</gray> <white>" + d.product.output + "x</white> " + d.product.name);
            lore.add("<gray>Needs:</gray>");
            boolean canMake = true;
            for (Drug.Ingredient ing : d.product.inputs) {
                int have = count(p, ing);
                if (have < ing.amount()) canMake = false;
                String label = ing.isPluginItem()
                        ? ingredientName(ing)
                        : Items.pretty(ing.material());
                lore.add((have >= ing.amount() ? "  <green>✔</green> " : "  <red>✖</red> ")
                        + "<gray>" + ing.amount() + "x " + label + " <dark_gray>(" + have + ")</dark_gray></gray>");
            }
            lore.add(canMake ? "<green>Click to process.</green>" : "<red>Missing ingredients.</red>");
            inv.setItem(i, info(d.product.material, d.colored(), lore));
            if (canMake) {
                final Drug drug = d;
                menu.actions.put(i, () -> process(p, station, drug));
            }
        }
        p.openInventory(inv);
    }

    private String ingredientName(Drug.Ingredient ing) {
        Drug d = plugin.drugs().get(ing.drug());
        String base = d == null ? ing.drug() : d.name;
        return switch (ing.kind()) {
            case Items.DRIED -> "Dried " + base;
            case Items.RAW -> "Raw " + base;
            case Items.PRODUCT -> base + " product";
            default -> base;
        };
    }

    private void process(Player p, Station station, Drug drug) {
        if (at(station.location) != station) { p.closeInventory(); return; }
        List<Integer> qualities = new ArrayList<>();
        for (Drug.Ingredient ing : drug.product.inputs) {
            if (count(p, ing) < ing.amount()) {
                Msg.send(p, "<red>You are missing ingredients.</red>");
                return;
            }
        }
        for (Drug.Ingredient ing : drug.product.inputs) qualities.addAll(take(p, ing));

        int quality;
        if (qualities.isEmpty()) {
            quality = 45 + ThreadLocalRandom.current().nextInt(11);
        } else {
            double sum = 0;
            for (int q : qualities) sum += q;
            quality = Quality.clamp(sum / qualities.size() + ThreadLocalRandom.current().nextInt(-4, 7));
        }

        ItemStack out = plugin.items().product(drug, quality, p.getName(), Items.newBatchId(), drug.product.output);
        give(p, out);
        p.playSound(station.location, Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.0f);
        station.location.getWorld().spawnParticle(Particle.COMPOSTER, station.location.clone().add(0.5, 1.1, 0.5), 10, 0.3, 0.2, 0.3, 0.01);
        Msg.send(p, "Processed " + drug.product.output + "x " + drug.colored() + " <gray>at</gray> "
                + Quality.Grade.of(quality).colored() + " <dark_gray>(" + quality + ")</dark_gray>");
        openBench(p, station);
    }

    // ------------------------------------------------------------------ packaging

    private void packaging(Player p, Station station, ItemStack inHand) {
        Items items = plugin.items();
        if (items.is(inHand, Items.PRODUCT)) {
            Drug drug = plugin.drugs().get(items.drugId(inHand));
            if (drug == null) return;
            int units = inHand.getAmount();
            ItemStack pack = items.pack(drug, items.quality(inHand), items.grower(inHand), items.batch(inHand), units);
            inHand.setAmount(0);
            give(p, pack);
            p.playSound(station.location, Sound.BLOCK_BARREL_OPEN, 0.7f, 1.2f);
            Msg.send(p, "Sealed <white>" + units + "</white> units of " + drug.colored()
                    + " <gray>with your label.</gray>");
            return;
        }
        if (items.is(inHand, Items.PACKAGE)) {
            Drug drug = plugin.drugs().get(items.drugId(inHand));
            if (drug == null) return;
            int perPack = items.units(inHand) / Math.max(1, inHand.getAmount());
            int total = items.units(inHand);
            ItemStack out = items.product(drug, items.quality(inHand), items.grower(inHand), items.batch(inHand), 1);
            inHand.setAmount(0);
            while (total > 0) {
                ItemStack portion = out.clone();
                portion.setAmount(Math.min(out.getMaxStackSize(), total));
                total -= portion.getAmount();
                give(p, portion);
            }
            Msg.send(p, "<gray>Opened all packages of </gray>" + drug.colored()
                    + " <dark_gray>(" + perPack + " per pack)</dark_gray>");
            return;
        }
        Msg.send(p, "<gray>Hold finished product to seal it, or a package to open it.</gray>");
    }

    // ------------------------------------------------------------------ storage crate

    private void openCrate(Player p, Station station) {
        Inventory shared = openCrates.get(station.key());
        if (shared != null) { p.openInventory(shared); return; }
        Menu menu = new Menu(Menu.Type.STORAGE_CRATE, station.location, null);
        Inventory inv = Bukkit.createInventory(menu, 27, Msg.mm("<dark_gray>Storage Crate</dark_gray>"));
        menu.bind(inv);
        if (station.storage != null) {
            for (int i = 0; i < Math.min(station.storage.length, 27); i++) inv.setItem(i, station.storage[i]);
        }
        openCrates.put(station.key(), inv);
        p.openInventory(inv);
        p.playSound(station.location, Sound.BLOCK_CHEST_OPEN, 0.6f, 1.0f);
    }

    /** Called from the close listener so crate contents survive. */
    public void saveCrate(Location l, Inventory inv) {
        Station s = at(l);
        if (s == null) return;
        s.storage = inv.getContents().clone();
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        long now = System.currentTimeMillis();
        for (Station s : stations.values()) {
            if(s.type==StationType.GROWBOX) {
                Drug crop=plugin.drugs().get(s.growbox.drug);
                if(crop!=null && crop.isGrown()) s.growbox.advance(now,crop.crop.growthMinutes*60);
            }
            furniture.sync(s);
            if (s.type != StationType.DRYING_RACK || s.batches.isEmpty()) continue;
            Location l = s.location;
            World w = l.getWorld();
            if (w == null || !w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            boolean anyDone = false;
            for (Station.Batch b : s.batches) if (now >= b.finishAt) anyDone = true;
            if (anyDone) {
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, l.clone().add(0.5, 1.0, 0.5), 2, 0.2, 0.1, 0.2, 0.005);
            }
        }
    }

    // ------------------------------------------------------------------ inventory helpers

    private int count(Player p, Drug.Ingredient ing) {
        Items items = plugin.items();
        int total = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (ing.isPluginItem()) {
                if (ing.kind().equals(items.kind(it)) && ing.drug().equals(items.drugId(it))) total += it.getAmount();
            } else if (it.getType() == ing.material() && !items.isPluginItem(it)) {
                total += it.getAmount();
            }
        }
        return total;
    }

    /** Removes the ingredient from the player's inventory and returns the qualities consumed. */
    private List<Integer> take(Player p, Drug.Ingredient ing) {
        Items items = plugin.items();
        List<Integer> qualities = new ArrayList<>();
        int remaining = ing.amount();
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir()) continue;
            boolean match = ing.isPluginItem()
                    ? ing.kind().equals(items.kind(it)) && ing.drug().equals(items.drugId(it))
                    : it.getType() == ing.material() && !items.isPluginItem(it);
            if (!match) continue;
            int used = Math.min(remaining, it.getAmount());
            if (ing.isPluginItem()) for (int n = 0; n < used; n++) qualities.add(items.quality(it));
            it.setAmount(it.getAmount() - used);
            if (it.getAmount() <= 0) p.getInventory().setItem(i, null);
            remaining -= used;
        }
        return qualities;
    }

    public static void give(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    private static ItemStack info(Material material, String name, List<String> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.mm("<!italic>" + name));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.mm("<!italic>" + s));
        m.lore(l);
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------------ persistence

    public void load() {
        stations.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            World w = Bukkit.getWorld(s.getString("world", ""));
            StationType type = StationType.byId(s.getString("type"));
            if (w == null || type == null) continue;
            Location l = new Location(w, s.getInt("x"), s.getInt("y"), s.getInt("z"));
            UUID owner;
            try {
                owner = UUID.fromString(s.getString("owner", ""));
            } catch (IllegalArgumentException ex) {
                owner = new UUID(0, 0);
            }
            Station station = new Station(l, type, owner);
            station.yaw=(float)s.getDouble("yaw",0);
            station.growbox.drug=s.getString("growbox.drug");
            station.growbox.grower=s.getString("growbox.grower","unknown");
            station.growbox.progress=Math.max(0,Math.min(1,s.getDouble("growbox.progress",0)));
            station.growbox.waterSeconds=Math.max(0,Math.min(1200,s.getDouble("growbox.water-seconds",0)));
            station.growbox.fertilizer=Math.max(0,Math.min(3,s.getInt("growbox.fertilizer",0)));
            station.growbox.lamp=s.getBoolean("growbox.lamp",true);
            station.growbox.updatedAt=s.getLong("growbox.updated-at",System.currentTimeMillis());
            ConfigurationSection batches = s.getConfigurationSection("batches");
            if (batches != null) {
                for (String bk : batches.getKeys(false)) {
                    ConfigurationSection b = batches.getConfigurationSection(bk);
                    if (b == null) continue;
                    ItemStack input = fromBase64(b.getString("input"));
                    if (input == null) continue;
                    Station.Batch batch = new Station.Batch();
                    batch.input = input;
                    batch.startedAt = b.getLong("started");
                    batch.finishAt = b.getLong("finish");
                    station.batches.add(batch);
                }
            }
            String storage = s.getString("storage");
            if (storage != null) station.storage = arrayFromBase64(storage);
            stations.put(station.key(), station);
        }
        plugin.getLogger().info("Loaded " + stations.size() + " stations.");
    }

    public void save() {
        for (Station station : stations.values()) {
            Inventory live = openCrates.get(station.key());
            if (live != null) saveCrate(station.location, live);
        }
        YamlConfiguration y = new YamlConfiguration();
        for (Station s : stations.values()) {
            String key = s.key().replace(':', '_');
            y.set(key + ".world", s.location.getWorld().getName());
            y.set(key + ".x", s.location.getBlockX());
            y.set(key + ".y", s.location.getBlockY());
            y.set(key + ".z", s.location.getBlockZ());
            y.set(key + ".type", s.type.id);
            y.set(key + ".owner", s.owner.toString());
            y.set(key+".yaw",s.yaw);
            if(s.type==StationType.GROWBOX) {
                y.set(key+".growbox.drug",s.growbox.drug); y.set(key+".growbox.grower",s.growbox.grower);
                y.set(key+".growbox.progress",s.growbox.progress); y.set(key+".growbox.water-seconds",s.growbox.waterSeconds);
                y.set(key+".growbox.fertilizer",s.growbox.fertilizer); y.set(key+".growbox.lamp",s.growbox.lamp);
                y.set(key+".growbox.updated-at",s.growbox.updatedAt);
            }
            for (int i = 0; i < s.batches.size(); i++) {
                Station.Batch b = s.batches.get(i);
                y.set(key + ".batches.b" + i + ".input", toBase64(b.input));
                y.set(key + ".batches.b" + i + ".started", b.startedAt);
                y.set(key + ".batches.b" + i + ".finish", b.finishAt);
            }
            if (s.storage != null) y.set(key + ".storage", toBase64(s.storage));
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save stations.yml: " + ex.getMessage());
        }
    }

    private static String toBase64(ItemStack item) {
        return toBase64(new ItemStack[]{item});
    }

    private static String toBase64(ItemStack[] items) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             BukkitObjectOutputStream data = new BukkitObjectOutputStream(out)) {
            data.writeInt(items.length);
            for (ItemStack it : items) data.writeObject(it);
            data.flush();
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException ex) {
            return null;
        }
    }

    private static ItemStack fromBase64(String encoded) {
        ItemStack[] arr = arrayFromBase64(encoded);
        return arr == null || arr.length == 0 ? null : arr[0];
    }

    private static ItemStack[] arrayFromBase64(String encoded) {
        if (encoded == null) return null;
        try (ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream data = new BukkitObjectInputStream(in)) {
            int size = data.readInt();
            if (size < 0 || size > 54) return null;
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) items[i] = (ItemStack) data.readObject();
            return items;
        } catch (IOException | ClassNotFoundException | IllegalArgumentException ex) {
            return null;
        }
    }
}
