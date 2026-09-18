package dev.lucas.slumdrugs.pack;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Native Minecraft cuboid models; vanilla texture references keep the pack small and consistent. */
public final class FurnitureModels {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public record Box(double[] from, double[] to, String texture) {}
    public static Map<String,List<Box>> definitions() {
        Map<String,List<Box>> all = new LinkedHashMap<>();
        var rack = new ArrayList<Box>();
        box(rack,1,0,2,15,2,14,"wood");
        for(int x:new int[]{2,12}) box(rack,x,2,6,x+2,15,8,"wood");
        box(rack,1,13,5,15,15,9,"wood");
        for(int x:new int[]{4,7,10}) { box(rack,x,10,6,x+1,13,7,"rope"); box(rack,x-1,5,5,x+2,10,8,"leaf"); }
        all.put("drying_rack",rack);
        var bench = table();
        box(bench,2,11,3,7,12,8,"stone");
        box(bench,2,12,3,3,14,8,"stone"); box(bench,6,12,3,7,14,8,"stone");
        box(bench,3,12,3,6,14,4,"stone"); box(bench,3,12,7,6,14,8,"stone");
        box(bench,4,12,5,5,16,6,"wood"); bottle(bench,11,4,"amber");
        box(bench,9,11,10,14,11.3,14,"paper"); all.put("processing_bench",bench);
        var packing=table(); box(packing,2,11,3,8,15,9,"parcel");
        box(packing,4.5,11,2.9,5.5,15.1,9.1,"rope");
        box(packing,10,11,3,13,15,6,"paper"); box(packing,10.7,15,3.7,12.3,15.2,5.3,"wood");
        box(packing,10,11,10,14,13,14,"rope"); all.put("packaging_station",packing);
        var crate=new ArrayList<Box>(); box(crate,1,0,1,15,13,15,"wood"); box(crate,0.5,13,0.5,15.5,15,15.5,"wood");
        for(int x:new int[]{2,12}) { box(crate,x,0,0.7,x+2,15,1,"iron"); box(crate,x,0,15,x+2,15,15.3,"iron"); box(crate,x,15,1,x+2,15.3,15,"iron"); }
        box(crate,6,8,0.3,10,12,0.8,"iron"); box(crate,7,9,0,9,11,0.3,"amber"); all.put("storage_crate",crate);
        for(boolean lamp:new boolean[]{false,true}) for(int stage=0;stage<=4;stage++) {
            var g=new ArrayList<Box>();
            box(g,0,0,0,16,2,16,"wood"); box(g,0,14,0,16,16,16,"wood");
            for(int x:new int[]{0,14}) for(int z:new int[]{0,14}) box(g,x,2,z,x+2,14,z+2,"iron");
            box(g,2,2,14.7,14,14,15,"glass"); box(g,0.8,2,2,1,14,14,"glass"); box(g,15,2,2,15.2,14,14,"glass");
            box(g,2,2,2,14,4,14,"soil"); box(g,4,13,4,12,14,12,lamp?"lamp":"iron");
            box(g,12,4,1,14,7,2,"iron"); box(g,12.5,5,0.8,13.5,6,1,lamp?"lamp":"amber");
            if(stage>0) for(int x:new int[]{5,10}) for(int z:new int[]{5,10}) {
                double h=4+stage*1.6; box(g,x,4,z,x+0.8,h,z+0.8,"stem");
                box(g,x-1.5,h-1,z-0.6,x+2.3,h,z+1.4,"leaf");
                if(stage>1) box(g,x-0.6,h-2,z-1.5,x+1.4,h-1,z+2.3,"leaf");
                if(stage==4) box(g,x-0.3,h,z-0.3,x+1.2,h+0.8,z+1.2,"bud");
            }
            all.put("growbox_"+stage+(lamp?"_on":"_off"),g);
        }
        return all;
    }
    private static ArrayList<Box> table() {
        var boxes=new ArrayList<Box>();
        for(int x:new int[]{1,12}) for(int z:new int[]{1,12}) box(boxes,x,0,z,x+3,10,z+3,"wood");
        box(boxes,0,9,0,16,11,16,"wood"); box(boxes,2,3,2,14,4,14,"wood"); return boxes;
    }
    private static void bottle(List<Box>b,int x,int z,String color) {
        box(b,x,11,z,x+3,14,z+3,color); box(b,x+1,14,z+1,x+2,15,z+2,"glass"); box(b,x+.8,15,z+.8,x+2.2,16,z+2.2,"wood");
    }
    private static void box(List<Box>b,double x,double y,double z,double X,double Y,double Z,String t) {
        b.add(new Box(new double[]{x,y,z},new double[]{X,Y,Z},t));
    }
    public static int write(Path root) throws IOException {
        Path models=root.resolve("assets/slumdrugs/models/furniture"), items=root.resolve("assets/slumdrugs/items/furniture");
        Files.createDirectories(models); Files.createDirectories(items);
        Map<String,String> textures=Map.ofEntries(
                Map.entry("wood","minecraft:block/spruce_planks"),Map.entry("iron","minecraft:block/iron_block"),
                Map.entry("stone","minecraft:block/stone"),Map.entry("paper","minecraft:block/white_wool"),
                Map.entry("parcel","minecraft:block/brown_wool"),Map.entry("rope","minecraft:block/stripped_oak_log"),
                Map.entry("glass","minecraft:block/glass"),Map.entry("soil","minecraft:block/dirt"),
                Map.entry("lamp","minecraft:block/sea_lantern"),Map.entry("leaf","minecraft:block/green_wool"),
                Map.entry("stem","minecraft:block/lime_terracotta"),Map.entry("bud","minecraft:block/yellow_terracotta"),
                Map.entry("amber","minecraft:block/orange_stained_glass"));
        for(var entry:definitions().entrySet()) {
            var model=new JsonObject(); model.add("textures",JSON.toJsonTree(textures));
            var elements=new JsonArray();
            for(Box b:entry.getValue()) {
                var e=new JsonObject(); e.add("from",JSON.toJsonTree(b.from())); e.add("to",JSON.toJsonTree(b.to()));
                var faces=new JsonObject();
                for(String direction:List.of("north","south","east","west","up","down")) {
                    var face=new JsonObject(); face.addProperty("texture","#"+b.texture()); faces.add(direction,face);
                }
                e.add("faces",faces); elements.add(e);
            }
            model.add("elements",elements);
            model.add("display",JsonParser.parseString("{\"gui\":{\"rotation\":[30,225,0],\"scale\":[0.65,0.65,0.65]},\"ground\":{\"scale\":[0.5,0.5,0.5]},\"fixed\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]}}"));
            Files.writeString(models.resolve(entry.getKey()+".json"),JSON.toJson(model));
            Files.writeString(items.resolve(entry.getKey()+".json"),"{\"model\":{\"type\":\"minecraft:model\",\"model\":\"slumdrugs:furniture/"+entry.getKey()+"\"}}");
        }
        return definitions().size()*2;
    }
}
