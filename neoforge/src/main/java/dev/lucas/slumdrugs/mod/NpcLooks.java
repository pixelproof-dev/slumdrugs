package dev.lucas.slumdrugs.mod;

import com.mojang.logging.LogUtils;
import dev.lucas.slumdrugs.sim.npc.Looks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

/**
 * The skins there are, as the server knows them.
 *
 * <p>A server never loads assets, so it cannot look in the skin folder the client draws from.
 * The build lists that folder into {@code data/slumdrugs/npc_looks.txt} (neoforge/build.gradle.kts,
 * task {@code npcLooks}), and this reads it. Being data, a server's own datapack can replace it.
 */
public final class NpcLooks {

    private static final Logger LOG = LogUtils.getLogger();

    private static final Identifier LIST = Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, "npc_looks.txt");

    private static ResourceManager readFrom;
    private static List<Looks.Look> looks = List.of();

    private NpcLooks() {}

    /** Read once per resource manager, which the server replaces on /reload. */
    public static synchronized List<Looks.Look> all(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        if (resources == readFrom) return looks;
        readFrom = resources;
        looks = resources.getResource(LIST).map(resource -> {
            try (BufferedReader in = resource.openAsReader()) {
                return in.lines().map(String::strip).filter(line -> !line.isEmpty())
                        .map(Looks.Look::of).toList();
            } catch (IOException unreadable) {
                LOG.warn("could not read {}; everybody wears a default skin", LIST, unreadable);
                return List.<Looks.Look>of();
            }
        }).orElseGet(() -> {
            LOG.warn("{} is missing; everybody wears a default skin", LIST);
            return List.of();
        });
        return looks;
    }
}
