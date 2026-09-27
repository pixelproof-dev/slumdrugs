package dev.lucas.slumdrugs.mod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.lucas.slumdrugs.mod.NpcData;
import dev.lucas.slumdrugs.mod.SlumDrugsMod;
import dev.lucas.slumdrugs.sim.npc.Looks;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The skins in {@code assets/slumdrugs/textures/entity/npc/}, ready to draw.
 *
 * <p>A skin is an ordinary 64 × 64 player skin. Its file name says who wears it —
 * {@code resident_female_2.png}, {@code constable_male.png}, {@code bruiser_male_ashfall.png};
 * the rules are in sim {@link Looks}. Slim arms are recognised from the picture, so a skin
 * downloaded from anywhere can be dropped in as it is.
 *
 * <p>The server chooses each person's skin, together with a name that fits it, and sends the
 * choice along with the role. Only when it has not ({@code look} empty: somebody from a server
 * without this list, or the moment before the ticker gets to an old villager) is a skin picked
 * here, from the role, and a role with no skins at all wears one of the game's defaults.
 */
final class NpcSkins {

    private static final Logger LOG = LogUtils.getLogger();

    static final String FOLDER = "textures/entity/npc";

    private final Map<String, PlayerSkin> byId;
    private final List<Looks.Look> looks;

    private NpcSkins(Map<String, PlayerSkin> byId) {
        this.byId = byId;
        this.looks = byId.keySet().stream().sorted().map(Looks.Look::of).toList();
    }

    /** Reads the folder. Renderers are rebuilt on every resource reload, so this is too. */
    static NpcSkins load(ResourceManager resources) {
        Map<String, PlayerSkin> byId = new HashMap<>();
        var found = new TreeMap<>(resources.listResources(FOLDER, id -> id.getPath().endsWith(".png")));
        for (var entry : found.entrySet()) {
            Identifier id = entry.getKey();
            if (!id.getNamespace().equals(SlumDrugsMod.ID)) continue;
            String name = id.getPath().substring(FOLDER.length() + 1, id.getPath().length() - ".png".length());
            PlayerModelType model = model(id, entry.getValue());
            if (model == null) continue;
            byId.put(name, new PlayerSkin(new ClientAsset.ResourceTexture(id, id), null, null, model, true));
        }
        NpcSkins skins = new NpcSkins(byId);
        List<String> roles = new ArrayList<>();
        skins.looks.stream().map(Looks.Look::role).distinct().forEach(roles::add);
        LOG.info("npc skins: {} for {}", byId.size(), roles.isEmpty() ? "nobody, everybody wears a default" : roles);
        return skins;
    }

    PlayerSkin pick(NpcData data, UUID id) {
        PlayerSkin chosen = byId.get(data.look());
        if (chosen != null) return chosen;
        return Looks.pick(looks, data.role(), data.crew(), Looks.Sex.ANY, id.getLeastSignificantBits())
                .map(look -> byId.get(look.id()))
                .orElseGet(() -> DefaultPlayerSkin.get(id));
    }

    /**
     * Wide or slim, read off the picture: a slim skin's arms are three pixels wide, which leaves
     * the last two columns of the right arm's back face empty. Null for anything that is not a
     * 64 × 64 skin, which the player model would draw scrambled.
     */
    private static PlayerModelType model(Identifier id, Resource resource) {
        try (InputStream in = resource.open(); NativeImage image = NativeImage.read(in)) {
            if (image.getWidth() != 64 || image.getHeight() != 64) {
                LOG.warn("npc skin {} is {} x {}, not 64 x 64; left out", id, image.getWidth(), image.getHeight());
                return null;
            }
            for (int y = 20; y < 32; y++)
                for (int x = 54; x < 56; x++)
                    if (ARGB.alpha(image.getPixel(x, y)) != 0) return PlayerModelType.WIDE;
            return PlayerModelType.SLIM;
        } catch (IOException unreadable) {
            LOG.warn("npc skin {} could not be read; left out", id, unreadable);
            return null;
        }
    }
}
