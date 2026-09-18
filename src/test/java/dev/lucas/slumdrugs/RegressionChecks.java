package dev.lucas.slumdrugs;

import dev.lucas.slumdrugs.drug.UnitTransfer;
import dev.lucas.slumdrugs.pack.ResourcePackBuilder;
import dev.lucas.slumdrugs.player.Recovery;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Regression checks run by Gradle check; no live server or external test framework needed. */
public final class RegressionChecks {
    private static int checks;
    private static void check(boolean success, String label) {
        checks++;
        if (!success) throw new AssertionError(label);
    }
    private static void close(double actual, double expected, String label) {
        check(Math.abs(actual - expected) < 1e-8, label + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) throws Exception {
        var seed=new dev.lucas.slumdrugs.world.HouseSurvey.Cell(0,0);
        var room=dev.lucas.slumdrugs.world.HouseSurvey.flood(seed,c->Math.abs(c.x())<=2 && Math.abs(c.z())<=2,144);
        check(room.size()==25,"bounded house flood");
        check(dev.lucas.slumdrugs.world.HouseSurvey.flood(seed,c->true,144).isEmpty(),"unbounded house rejected");
        check(dev.lucas.slumdrugs.world.HouseSurvey.flood(seed,c->c.x()>=0&&c.x()<3&&c.z()>=0&&c.z()<3,8).isEmpty(),"house area cap rejects whole room");
        check(dev.lucas.slumdrugs.world.HouseSurvey.flood(seed,c->false,144).isEmpty(),"blocked seed");
        check(dev.lucas.slumdrugs.world.HouseSurvey.flood(seed,c->Math.abs(c.x())==Math.abs(c.z()),144).size()==1,"diagonal rooms disconnected");
        Path journal=Files.createTempDirectory(Path.of("build"),"journal-check-").resolve("takeover.yml");
        dev.lucas.slumdrugs.world.TakeoverSnapshot.atomic(journal,"prepared\n");
        dev.lucas.slumdrugs.world.TakeoverSnapshot.atomic(journal,"applied\n");
        check(Files.readString(journal).equals("applied\n"),"journal atomic replacement");
        check(!Files.exists(journal.resolveSibling("takeover.yml.tmp")),"journal temp committed");
        check(dev.lucas.slumdrugs.world.TakeoverSnapshot.restoreCompatible("minecraft:stone","minecraft:cobblestone","minecraft:stone",false),"restore partial transaction original");
        check(dev.lucas.slumdrugs.world.TakeoverSnapshot.restoreCompatible("minecraft:stone","minecraft:cobblestone","minecraft:cobblestone",false),"restore converted state");
        check(!dev.lucas.slumdrugs.world.TakeoverSnapshot.restoreCompatible("minecraft:stone","minecraft:cobblestone","minecraft:diamond_block",false),"restore protects unrelated edit");
        check(dev.lucas.slumdrugs.world.TakeoverSnapshot.restoreCompatible("minecraft:stone","minecraft:wheat[age=0]","minecraft:wheat[age=7]",true),"restore grown crop");
        check(!dev.lucas.slumdrugs.world.TakeoverSnapshot.restoreCompatible("minecraft:stone","minecraft:wheat[age=0]","minecraft:carrots[age=7]",true),"restore protects changed crop species");
        check(!dev.lucas.slumdrugs.world.TakeoverSafety.palette(org.bukkit.Material.DIAMOND_BLOCK),"valuable player block outside palette");
        check(dev.lucas.slumdrugs.world.TakeoverSafety.palette(org.bukkit.Material.OAK_PLANKS),"vanilla village palette");
        var packSettings=new dev.lucas.slumdrugs.pack.JoinResourcePack.Settings("https://example.org/pack.zip","A".repeat(40),true,"Pack");
        check(packSettings.sha1().equals("a".repeat(40)),"pack hash normalized");
        for(String bad:java.util.List.of("", "file:///pack.zip", "ftp://example.org/pack.zip", "https://example.org/pack.zip#fragment")) {
            boolean rejected=false;
            try {new dev.lucas.slumdrugs.pack.JoinResourcePack.Settings(bad,"a".repeat(40),true,"Pack");}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"invalid pack URL rejected");
        }
        boolean badHash=false;
        try {new dev.lucas.slumdrugs.pack.JoinResourcePack.Settings("https://example.org/pack.zip","wrong",true,"Pack");}catch(IllegalArgumentException expected){badHash=true;}
        check(badHash,"invalid pack hash rejected");
        for(var status:org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.values()) {
            boolean expected=java.util.Set.of("DECLINED","FAILED_DOWNLOAD","INVALID_URL","FAILED_RELOAD","DISCARDED").contains(status.name());
            check(dev.lucas.slumdrugs.pack.JoinResourcePack.failed(status)==expected,"resource pack terminal status "+status);
        }
        long minute = 60000L;
        var grow = new dev.lucas.slumdrugs.station.GrowboxState();
        grow.drug="sunleaf"; grow.updatedAt=0; grow.waterSeconds=120;
        grow.advance(60000,600);
        close(grow.progress,.1,"growbox elapsed growth");
        close(grow.waterSeconds,60,"growbox consumes water");
        grow.advance(600000,600);
        close(grow.progress,.2,"offline growth bounded by water");
        close(grow.waterSeconds,0,"water never negative");
        grow.waterSeconds=1200; grow.lamp=false; grow.advance(1200000,600);
        close(grow.progress,.2,"lamp pauses growth");
        close(grow.waterSeconds,1200,"paused lamp saves water");
        grow.lamp=true; grow.fertilizer=3; grow.advance(1800000,600);
        close(grow.progress,1,"fertilized crop reaches maturity");
        check(grow.stage()==4,"mature visual stage");
        double water=grow.waterSeconds; grow.advance(1900000,600);
        close(grow.waterSeconds,water,"mature crop stops consuming water");
        grow.clear(); check(grow.stage()==0 && grow.fertilizer==0,"harvest resets crop");
        check(dev.lucas.slumdrugs.pack.FurnitureModels.definitions().size()==14,"all furniture and growbox variants");
        for(var model:dev.lucas.slumdrugs.pack.FurnitureModels.definitions().entrySet()) {
            check(!model.getValue().isEmpty(),"nonempty model "+model.getKey());
            for(var box:model.getValue()) for(int axis=0;axis<3;axis++)
                check(box.from()[axis]>=0 && box.to()[axis]<=16 && box.to()[axis]>box.from()[axis],"valid one-block cuboid");
        }
        close(Recovery.tolerance(80, 0, 60 * minute, .15), 71, "offline tolerance");
        close(Recovery.tolerance(10, 10, 0, .15), 10, "clock rollback");
        close(Recovery.dependence(80, 0, 10 * minute, 0, 20 * minute, 0, .08, 2.5), 80, "before delay");
        close(Recovery.dependence(80, 0, 60 * minute, 0, 20 * minute, 0, .08, 2.5), 76.8, "offline delay boundary");
        close(Recovery.dependence(80, 0, 60 * minute, 0, 20 * minute, 25 * minute, .08, 2.5), 75.6, "rest overlap only");
        double split = Recovery.dependence(80, 0, 30 * minute, 0, 20 * minute, 25 * minute, .08, 2.5);
        split = Recovery.dependence(split, 30 * minute, 60 * minute, 0, 20 * minute, 25 * minute, .08, 2.5);
        close(split, 75.6, "save/load recovery agrees with continuous recovery");
        close(Recovery.dependence(5, 0, 10000 * minute, 0, 0, 0, .08, 2.5), 0, "recovery lower bound");
        check(UnitTransfer.plan(3, 64, 1).equals(new UnitTransfer(1, 2, 63)), "single unit out of sealed stack");
        check(UnitTransfer.plan(3, 64, 70).equals(new UnitTransfer(70, 1, 58)), "partial second package");
        check(UnitTransfer.plan(2, 10, 100).equals(new UnitTransfer(20, 0, 0)), "limited inventory");
        for (int stacks = 0; stacks <= 64; stacks++) for (int per = 1; per <= 64; per++) {
            for (int wanted : new int[]{0, 1, 10, 63, 64, 65, 127, 4096}) {
                var plan = UnitTransfer.plan(stacks, per, wanted);
                check(plan.consumed() + plan.remainingPackages() * per + plan.looseChange() == stacks * per,
                        "unit conservation");
                check(plan.consumed() <= wanted && plan.looseChange() < per, "exact request limit");
            }
        }
        Path root = Files.createTempDirectory(Path.of("build"), "pack-regression-");
        var builder = new ResourcePackBuilder(root.toFile(), PackExport.definitions());
        builder.build();
        for(String name:dev.lucas.slumdrugs.pack.FurnitureModels.definitions().keySet()) {
            var modelPath=root.resolve("resourcepack/assets/slumdrugs/models/furniture/"+name+".json");
            check(Files.exists(modelPath),"exported furniture model "+name);
            var definition=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("resourcepack/assets/slumdrugs/items/furniture/"+name+".json"))).getAsJsonObject();
            check(definition.getAsJsonObject("model").get("model").getAsString().equals("slumdrugs:furniture/"+name),"furniture item reference");
        }
        Path texture = root.resolve("resourcepack/assets/slumdrugs/textures/item/product_sunleaf.png");
        byte[] original = Files.readAllBytes(texture);
        byte[] sentinel = "artist-custom-texture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(texture, sentinel);
        builder.build();
        check(java.util.Arrays.equals(Files.readAllBytes(texture), sentinel), "custom texture survives rebuild");
        Files.write(texture, original);
        try (var zip = new java.util.zip.ZipFile(root.resolve("resourcepack.zip").toFile())) {
            check(zip.getEntry("pack.mcmeta") != null, "zip metadata at root");
            check(zip.getEntry("assets/slumdrugs/models/item/product_glowcap.json") != null, "zip includes models");
        }
        var metadata = com.google.gson.JsonParser.parseString(Files.readString(root.resolve("resourcepack/pack.mcmeta")))
                .getAsJsonObject().getAsJsonObject("pack");
        check(metadata.has("min_format") && metadata.has("max_format"), "modern pack metadata");
        var textureNames = new java.util.ArrayList<String>();
        for (String id : new String[]{"sunleaf", "frostroot", "emberbloom", "glowcap", "sparkshard"}) {
            textureNames.add("product_" + id);
            textureNames.add("package_" + id);
        }
        for (String id : new String[]{"sunleaf", "frostroot", "emberbloom"})
            for (String kind : new String[]{"seed", "raw", "dried"}) textureNames.add(kind + "_" + id);
        textureNames.addAll(java.util.List.of("fertilizer", "remedy", "station_drying_rack",
                "station_processing_bench", "station_packaging_station", "station_storage_crate"));
        check(textureNames.size() == 25, "all 25 inventory textures covered");
        for (String id : textureNames) {
            try (var stream = RegressionChecks.class.getResourceAsStream("/textures/" + id + ".png")) {
                check(stream != null, "bundled texture " + id);
                var image = ImageIO.read(stream);
                check(image.getWidth() == 64 && image.getHeight() == 64, "64px texture " + id);
                check(image.getColorModel().hasAlpha() && (image.getRGB(0, 0) >>> 24) == 0, "transparent texture " + id);
            }
            var modelPath = root.resolve("resourcepack/assets/slumdrugs/models/item/" + id + ".json");
            check(Files.exists(modelPath), "model exists " + id);
            var model = com.google.gson.JsonParser.parseString(Files.readString(modelPath)).getAsJsonObject();
            check(model.getAsJsonObject("textures").get("layer0").getAsString().equals("slumdrugs:item/" + id),
                    "model references correct texture " + id);
        }
        System.out.println("PASS: " + checks + " regression assertions (takeover safeguards, recovery, package conservation, pack and textures).");
    }
}
