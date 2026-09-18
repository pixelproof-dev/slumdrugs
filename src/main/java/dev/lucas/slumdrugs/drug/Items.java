package dev.lucas.slumdrugs.drug;

import dev.lucas.slumdrugs.Keys;
import dev.lucas.slumdrugs.Msg;
import dev.lucas.slumdrugs.station.StationType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Creates and reads every custom item. All items are vanilla materials tagged with persistent data. */
public final class Items {

    public static final String SEED = "seed";
    public static final String RAW = "raw";
    public static final String DRIED = "dried";
    public static final String PRODUCT = "product";
    public static final String PACKAGE = "package";
    public static final String FERTILIZER = "fertilizer";
    public static final String REMEDY = "remedy";
    public static final String STATION = "station";

    private final Keys keys;

    public Items(Keys keys) {
        this.keys = keys;
    }

    // ------------------------------------------------------------------ creation

    public ItemStack seed(Drug d, int amount) {
        ItemStack it = new ItemStack(d.crop.seedMaterial, amount);
        it.editMeta(m -> {
            name(m, "<" + d.color + ">" + d.crop.seedName + "</" + d.color + ">");
            lore(m, List.of(
                    "<gray>Plant on " + pretty(d.crop.soil) + ".</gray>",
                    "<gray>Likes: " + tempRange(d) + "</gray>",
                    "<gray>Needs light " + d.crop.lightMin + "+, grows ~" + (int) d.crop.growthMinutes + " min.</gray>",
                    "<dark_gray>Yield " + d.crop.yield + " per plant</dark_gray>"));
            tag(m, SEED, d.id);
            model(m, "seed_" + d.id);
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return it;
    }

    public ItemStack raw(Drug d, int quality, String grower, String batch, int amount) {
        ItemStack it = new ItemStack(d.crop.rawMaterial, amount);
        it.editMeta(m -> {
            name(m, "<" + d.color + ">" + d.crop.rawName + "</" + d.color + ">");
            lore(m, labelLore(quality, grower, batch, "<gray>Needs drying on a Drying Rack.</gray>"));
            tag(m, RAW, d.id);
            label(m, quality, grower, batch);
            model(m, "raw_" + d.id);
        });
        return it;
    }

    public ItemStack dried(Drug d, int quality, String grower, String batch, int amount) {
        ItemStack it = new ItemStack(d.crop.driedMaterial, amount);
        it.editMeta(m -> {
            name(m, "<" + d.color + ">" + d.crop.driedName + "</" + d.color + ">");
            lore(m, labelLore(quality, grower, batch, "<gray>Process at a Processing Bench.</gray>"));
            tag(m, DRIED, d.id);
            label(m, quality, grower, batch);
            model(m, "dried_" + d.id);
        });
        return it;
    }

    public ItemStack product(Drug d, int quality, String grower, String batch, int amount) {
        ItemStack it = new ItemStack(d.product.material, amount);
        it.editMeta(m -> {
            name(m, "<" + d.color + ">" + d.product.name + "</" + d.color + ">");
            lore(m, labelLore(quality, grower, batch, "<gray>Right-click to use. Package before selling in bulk.</gray>"));
            tag(m, PRODUCT, d.id);
            label(m, quality, grower, batch);
            model(m, "product_" + d.id);
            m.setEnchantmentGlintOverride(Quality.Grade.of(quality) == Quality.Grade.PREMIUM);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        });
        return it;
    }

    public ItemStack pack(Drug d, int quality, String grower, String batch, int units) {
        ItemStack it = new ItemStack(Material.PAPER, 1);
        it.editMeta(m -> {
            name(m, "<" + d.color + ">" + d.product.name + " Package</" + d.color + "> <gray>x" + units + "</gray>");
            List<String> l = labelLore(quality, grower, batch, "<gray>Sealed package of " + units + " units.</gray>");
            l.add("<dark_gray>Customers buy packages. Unpack at a Packaging Station.</dark_gray>");
            lore(m, l);
            tag(m, PACKAGE, d.id);
            label(m, quality, grower, batch);
            m.getPersistentDataContainer().set(keys.units, PersistentDataType.INTEGER, units);
            model(m, "package_" + d.id);
        });
        return it;
    }

    public ItemStack fertilizer(int amount) {
        ItemStack it = new ItemStack(Material.BONE_MEAL, amount);
        it.editMeta(m -> {
            name(m, "<green>Compost Fertilizer</green>");
            lore(m, List.of("<gray>Right-click a plant to feed it.</gray>", "<gray>Speeds growth and improves quality.</gray>"));
            tag(m, FERTILIZER, null);
            model(m, "fertilizer");
        });
        return it;
    }

    public ItemStack remedy(int amount) {
        ItemStack it = new ItemStack(Material.HONEY_BOTTLE, amount);
        it.editMeta(m -> {
            name(m, "<aqua>Clinic Remedy</aqua>");
            lore(m, List.of("<gray>Right-click to drink.</gray>", "<gray>Eases withdrawal for a while and</gray>", "<gray>lowers dependence a little.</gray>"));
            tag(m, REMEDY, null);
            model(m, "remedy");
        });
        return it;
    }

    public ItemStack station(StationType type, int amount) {
        ItemStack it = new ItemStack(type.block, amount);
        it.editMeta(m -> {
            name(m, "<gold>" + type.label + "</gold>");
            lore(m, List.of("<gray>" + type.description + "</gray>", "<dark_gray>Place it down to use.</dark_gray>"));
            tag(m, STATION, null);
            m.getPersistentDataContainer().set(keys.station, PersistentDataType.STRING, type.id);
            model(m, "station_" + type.id);
            if(type==StationType.GROWBOX) m.setItemModel(new org.bukkit.NamespacedKey("slumdrugs","furniture/growbox_0_on"));
        });
        return it;
    }

    // ------------------------------------------------------------------ reading

    public String kind(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(keys.kind, PersistentDataType.STRING);
    }

    public boolean is(ItemStack it, String kind) {
        return kind.equals(kind(it));
    }

    /** Raw, dried, product or package: anything a guard would confiscate. */
    public boolean isGoods(ItemStack it) {
        String k = kind(it);
        return RAW.equals(k) || DRIED.equals(k) || PRODUCT.equals(k) || PACKAGE.equals(k);
    }

    public boolean isPluginItem(ItemStack it) {
        return kind(it) != null;
    }

    public String drugId(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(keys.drug, PersistentDataType.STRING);
    }

    public int quality(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 50;
        Integer q = it.getItemMeta().getPersistentDataContainer().get(keys.quality, PersistentDataType.INTEGER);
        return q == null ? 50 : q;
    }

    public String grower(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return "unknown";
        String g = it.getItemMeta().getPersistentDataContainer().get(keys.grower, PersistentDataType.STRING);
        return g == null ? "unknown" : g;
    }

    public String batch(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return "----";
        String b = it.getItemMeta().getPersistentDataContainer().get(keys.batch, PersistentDataType.STRING);
        return b == null ? "----" : b;
    }

    /** Units of product represented by this stack (a package counts its sealed units). */
    public int units(ItemStack it) {
        String k = kind(it);
        if (PRODUCT.equals(k)) return it.getAmount();
        if (PACKAGE.equals(k)) {
            Integer u = it.getItemMeta().getPersistentDataContainer().get(keys.units, PersistentDataType.INTEGER);
            return (u == null ? 1 : u) * it.getAmount();
        }
        return 0;
    }

    public StationType stationType(ItemStack it) {
        if (!is(it, STATION)) return null;
        return StationType.byId(it.getItemMeta().getPersistentDataContainer().get(keys.station, PersistentDataType.STRING));
    }

    public static String newBatchId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("#");
        for (int i = 0; i < 4; i++) sb.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers

    private void tag(ItemMeta m, String kind, String drug) {
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(keys.kind, PersistentDataType.STRING, kind);
        if (drug != null) pdc.set(keys.drug, PersistentDataType.STRING, drug);
    }

    private void label(ItemMeta m, int quality, String grower, String batch) {
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(keys.quality, PersistentDataType.INTEGER, Quality.clamp(quality));
        pdc.set(keys.grower, PersistentDataType.STRING, grower == null ? "unknown" : grower);
        pdc.set(keys.batch, PersistentDataType.STRING, batch == null ? newBatchId() : batch);
    }

    private static List<String> labelLore(int quality, String grower, String batch, String hint) {
        Quality.Grade g = Quality.Grade.of(quality);
        List<String> l = new ArrayList<>();
        l.add("<gray>Grade:</gray> " + g.colored() + " <dark_gray>(" + quality + ")</dark_gray>");
        l.add("<gray>Grower:</gray> <white>" + grower + "</white>");
        l.add("<gray>Batch:</gray> <white>" + batch + "</white>");
        l.add(hint);
        return l;
    }

    private static void name(ItemMeta m, String mini) {
        m.displayName(Msg.mm("<!italic>" + mini));
    }

    private static void lore(ItemMeta m, List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String s : lines) out.add(Msg.mm("<!italic>" + s));
        m.lore(out);
    }

    private static void model(ItemMeta m, String id) {
        CustomModelDataComponent c = m.getCustomModelDataComponent();
        c.setStrings(List.of("slumdrugs:" + id));
        m.setCustomModelDataComponent(c);
    }

    private static String tempRange(Drug d) {
        String t;
        if (d.crop.tempMax <= 0.4) t = "cold";
        else if (d.crop.tempMin >= 1.4) t = "hot";
        else t = "temperate";
        return t + " climates";
    }

    public static String pretty(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
