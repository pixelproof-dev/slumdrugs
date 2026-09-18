package dev.lucas.slumdrugs.pack;

import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.station.StationType;
import org.bukkit.Material;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a resource pack that matches whatever is in drugs.yml: one item definition per base
 * material, a model per custom item and a placeholder texture tinted with the substance colour.
 * Replace the textures with real art; the JSON does not need to change.
 */
public final class ResourcePackBuilder {

    /** Resource pack format for Minecraft 26.3. */
    private static final int PACK_FORMAT = 97;

    private record Entry(Material base, String modelId, String color, String shape) {}

    private final File dataFolder;
    private final java.util.Collection<Drug> drugs;

    public ResourcePackBuilder(SlumDrugsPlugin plugin) {
        this(plugin.getDataFolder(), plugin.drugs().all());
    }

    public ResourcePackBuilder(File dataFolder, java.util.Collection<Drug> drugs) {
        this.dataFolder = dataFolder;
        this.drugs = drugs;
    }

    /** Builds the pack into plugins/SlumDrugs/resourcepack. Returns the number of files written. */
    public int build() throws IOException {
        File root = new File(dataFolder, "resourcepack");
        File items = new File(root, "assets/minecraft/items");
        File models = new File(root, "assets/slumdrugs/models/item");
        File textures = new File(root, "assets/slumdrugs/textures/item");
        items.mkdirs();
        models.mkdirs();
        textures.mkdirs();
        int written = 0;

        List<Entry> entries = collect();

        write(new File(root, "pack.mcmeta"), """
                {
                  "pack": {
                    "min_format": [%d, 1],
                    "max_format": [%d, 1],
                    "description": "SlumDrugs items"
                  }
                }
                """.formatted(PACK_FORMAT, PACK_FORMAT));
        written++;

        // One item definition per vanilla base item, listing every custom model that uses it.
        Map<Material, List<Entry>> byBase = new LinkedHashMap<>();
        for (Entry e : entries) byBase.computeIfAbsent(e.base(), k -> new ArrayList<>()).add(e);

        for (Map.Entry<Material, List<Entry>> group : byBase.entrySet()) {
            String itemName = group.getKey().getKey().getKey();
            StringBuilder cases = new StringBuilder();
            for (int i = 0; i < group.getValue().size(); i++) {
                Entry e = group.getValue().get(i);
                if (i > 0) cases.append(",\n");
                cases.append("""
                                {
                                  "when": "slumdrugs:%s",
                                  "model": { "type": "minecraft:model", "model": "slumdrugs:item/%s" }
                                }""".formatted(e.modelId(), e.modelId()).indent(8).stripTrailing());
            }
            write(new File(items, itemName + ".json"), """
                    {
                      "model": {
                        "type": "minecraft:select",
                        "property": "minecraft:custom_model_data",
                        "index": 0,
                        "fallback": { "type": "minecraft:model", "model": "minecraft:item/%s" },
                        "cases": [
                    %s
                        ]
                      }
                    }
                    """.formatted(itemName, cases.toString()));
            written++;
        }

        for (Entry e : entries) {
            write(new File(models, e.modelId() + ".json"), """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": { "layer0": "slumdrugs:item/%s" }
                    }
                    """.formatted(e.modelId()));
            written++;
            File target = new File(textures, e.modelId() + ".png");
            if (target.exists()) continue;
            try (var bundled = ResourcePackBuilder.class.getResourceAsStream("/textures/" + e.modelId() + ".png")) {
                if (bundled != null) { Files.copy(bundled, target.toPath()); written++; }
                else if (texture(target, e.color(), e.shape())) written++;
            }
        }

        write(new File(root, "README.txt"), """
                SlumDrugs resource pack
                =======================
                Generated from drugs.yml by /drugs pack.

                Includes 25 PixelLab item textures: products, seeds, harvests, packages,
                supplies and station icons. Existing PNG files are never overwritten.
                Replace any texture in assets/slumdrugs/textures/item to customize it.

                To use it:
                  1. Use the resourcepack.zip generated beside this folder.
                  2. Drop the zip in your client's resourcepacks folder, or host it and set
                     resource-pack in server.properties.

                Plant growth stages use the vanilla crop models, so a growing plot already
                shows its stage without any pack changes.
                """);
        written++;
        written += FurnitureModels.write(root.toPath());
        zip(root, new File(dataFolder, "resourcepack.zip"));
        return written;
    }

    private List<Entry> collect() {
        List<Entry> out = new ArrayList<>();
        for (Drug d : drugs) {
            if (d.isGrown()) {
                out.add(new Entry(d.crop.seedMaterial, "seed_" + d.id, d.color, "seed"));
                out.add(new Entry(d.crop.rawMaterial, "raw_" + d.id, d.color, "leaf"));
                out.add(new Entry(d.crop.driedMaterial, "dried_" + d.id, d.color, "leaf"));
            }
            out.add(new Entry(d.product.material, "product_" + d.id, d.color, "dose"));
            out.add(new Entry(Material.PAPER, "package_" + d.id, d.color, "package"));
        }
        out.add(new Entry(Material.BONE_MEAL, "fertilizer", "#7a5c3a", "dose"));
        out.add(new Entry(Material.HONEY_BOTTLE, "remedy", "#6fd7d0", "dose"));
        for (StationType t : StationType.values()) {
            if(t==StationType.GROWBOX) continue; // Growbox uses its 3D model in inventory too.
            out.add(new Entry(t.block, "station_" + t.id, "#b0b0b0", "crate"));
        }
        return out;
    }

    // ------------------------------------------------------------------ textures

    /** Draws a simple 16x16 placeholder so the pack loads with something visible. */
    private boolean texture(File file, String color, String shape) throws IOException {
        try {
            Color c = parse(color);
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Color dark = c.darker();
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    boolean on = switch (shape) {
                        case "seed" -> (x - 8) * (x - 8) + (y - 9) * (y - 9) < 12;
                        case "leaf" -> Math.abs(x - y) < 4 && x > 2 && x < 14 && y > 2 && y < 14;
                        case "package" -> x > 2 && x < 14 && y > 3 && y < 13;
                        case "crate" -> x > 1 && x < 15 && y > 1 && y < 15;
                        default -> (x - 8) * (x - 8) + (y - 8) * (y - 8) < 25;
                    };
                    if (!on) continue;
                    boolean edge = switch (shape) {
                        case "package", "crate" -> x == 3 || x == 13 || y == 4 || y == 12;
                        default -> false;
                    };
                    img.setRGB(x, y, (edge ? dark : c).getRGB() | 0xFF000000);
                }
            }
            ImageIO.write(img, "png", file);
            return true;
        } catch (IOException | RuntimeException ex) {
            throw new IOException("Could not write texture " + file.getName(), ex);
        }
    }

    private static Color parse(String color) {
        if (color == null) return Color.WHITE;
        String s = color.trim();
        if (s.startsWith("#")) {
            try {
                return new Color(Integer.parseInt(s.substring(1), 16));
            } catch (NumberFormatException ignored) {
                return Color.WHITE;
            }
        }
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "red" -> new Color(0xFF5555);
            case "green" -> new Color(0x55FF55);
            case "blue" -> new Color(0x5555FF);
            case "yellow" -> new Color(0xFFFF55);
            case "aqua" -> new Color(0x55FFFF);
            case "gold" -> new Color(0xFFAA00);
            case "gray" -> new Color(0xAAAAAA);
            default -> Color.WHITE;
        };
    }

    private static void write(File file, String content) throws IOException {
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }

    private static void zip(File root, File target) throws IOException {
        var temporary = target.toPath().resolveSibling(target.getName() + ".tmp");
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(temporary));
             var paths = Files.walk(root.toPath())) {
            for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
                var entry = new java.util.zip.ZipEntry(root.toPath().relativize(path).toString().replace('\\', '/'));
                entry.setTime(0);
                out.putNextEntry(entry);
                Files.copy(path, out);
                out.closeEntry();
            }
        }
        Files.move(temporary, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
