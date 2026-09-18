package dev.lucas.slumdrugs;

import dev.lucas.slumdrugs.drug.Drug;
import dev.lucas.slumdrugs.pack.ResourcePackBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exports the default pack without starting a Minecraft server. */
public final class PackExport {
    public static List<Drug> definitions() throws Exception {
        var result = new ArrayList<Drug>();
        try (var input = PackExport.class.getResourceAsStream("/drugs.yml")) {
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            var root = yaml.getConfigurationSection("drugs");
            for (String id : root.getKeys(false)) {
                var section = root.getConfigurationSection(id);
                var product = new Drug.Product();
                product.material = Material.valueOf(section.getString("product.material"));
                Drug.Crop crop = null;
                if (section.isConfigurationSection("crop")) {
                    crop = new Drug.Crop();
                    crop.seedMaterial = Material.valueOf(section.getString("crop.seed-material"));
                    crop.rawMaterial = Material.valueOf(section.getString("crop.raw-material"));
                    crop.driedMaterial = Material.valueOf(section.getString("crop.dried-material"));
                }
                result.add(new Drug(id, section.getString("name"), section.getString("color"),
                        1, 1, 1, 1, 1, 1, List.of(), List.of(), false, crop, product));
            }
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        int files = new ResourcePackBuilder(new File(args[0]), definitions()).build();
        FurniturePreview.write(java.nio.file.Path.of(args[0],"furniture-preview.html"));
        System.out.println("Exported " + files + " pack files and resourcepack.zip to " + args[0]);
    }
}
