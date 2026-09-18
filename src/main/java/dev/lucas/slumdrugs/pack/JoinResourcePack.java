package dev.lucas.slumdrugs.pack;

import dev.lucas.slumdrugs.Msg;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.net.URI;
import java.util.*;

/** Sends only this plugin's pack; does not clear packs supplied by other plugins. */
public final class JoinResourcePack implements Listener {
    private static final UUID PACK_ID=UUID.fromString("9658e353-fbc2-48ad-b927-b3e7471ccbec");
    private final SlumDrugsPlugin plugin;
    private final Map<UUID,Boolean> requests=new HashMap<>();
    private Settings settings;
    public record Settings(String url,String sha1,boolean required,String prompt) {
        public Settings {
            URI uri=URI.create(url);
            if(!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException("resource-pack.url must be a public HTTP(S) direct ZIP URL");
            url=uri.toASCIIString();
            sha1=sha1.toLowerCase(Locale.ROOT);
            if(!sha1.matches("[0-9a-f]{40}"))throw new IllegalArgumentException("resource-pack.sha1 must contain 40 hexadecimal characters");
        }
    }
    public JoinResourcePack(SlumDrugsPlugin plugin){this.plugin=plugin;reload();}
    public void reload() {
        settings=null;
        if(!plugin.getConfig().getBoolean("resource-pack.enabled",false))return;
        try {
            settings=new Settings(plugin.getConfig().getString("resource-pack.url","").trim(),
                    plugin.getConfig().getString("resource-pack.sha1","").trim(),
                    plugin.getConfig().getBoolean("resource-pack.required",true),
                    plugin.getConfig().getString("resource-pack.prompt","<gold>SlumDrugs</gold><gray> needs its textures and furniture pack.</gray>"));
        }catch(IllegalArgumentException ex){plugin.getLogger().warning("Join resource pack disabled: "+ex.getMessage());}
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        var player=event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            Settings s=settings;
            if(!player.isOnline() || s==null)return;
            try {
                requests.put(player.getUniqueId(),s.required());
                player.setResourcePack(PACK_ID,s.url(),HexFormat.of().parseHex(s.sha1()),Msg.mm(s.prompt()),s.required());
            }catch(RuntimeException ex){requests.remove(player.getUniqueId());plugin.getLogger().warning("Could not send SlumDrugs pack: "+ex.getMessage());}
        },20L);
    }
    public static boolean failed(PlayerResourcePackStatusEvent.Status status) {
        return switch(status){case DECLINED,FAILED_DOWNLOAD,INVALID_URL,FAILED_RELOAD,DISCARDED->true;default->false;};
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if(!PACK_ID.equals(event.getID()))return;
        Boolean required=requests.get(event.getPlayer().getUniqueId());
        if(required==null)return;
        if(event.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED){requests.remove(event.getPlayer().getUniqueId());return;}
        if(!failed(event.getStatus()))return;
        requests.remove(event.getPlayer().getUniqueId());
        plugin.getLogger().warning("SlumDrugs pack for "+event.getPlayer().getName()+": "+event.getStatus());
        if(required)event.getPlayer().kick(Msg.mm("<gray>The SlumDrugs resource pack is required. Enable server resource packs and reconnect. If the download fails, contact the server admin.</gray>"));
    }
    @EventHandler public void quit(PlayerQuitEvent event){requests.remove(event.getPlayer().getUniqueId());}
}
