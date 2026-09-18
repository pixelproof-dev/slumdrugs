> Historical 1.2.1 notes. Version 1.2.2 enables this feature by default and supplies the GitHub URL; see the root README for current setup.

# SlumDrugs 1.2.1

Adds plugin-managed resource-pack requests on join. The resource pack itself is unchanged: use SlumDrugs-ResourcePack-1.2.0.zip.

Stop the server, replace the older SlumDrugs JAR with SlumDrugs-1.2.1.jar, and keep the plugin data directory. Add this section to plugins/SlumDrugs/config.yml (existing config files are not overwritten):

```yaml
resource-pack:
  enabled: true
  url: "https://YOUR-PUBLIC-DIRECT-DOWNLOAD-LINK.zip"
  sha1: "79984106053fc8b4cdaae4ad5ae546330078e6f7"
  required: true
  prompt: "<gold>SlumDrugs</gold><gray> needs its textures and furniture pack.</gray>"
```

Replace the example URL with the real public direct ZIP download URL. The plugin does not upload or host the pack. No server.properties resource-pack settings are needed; avoid sending the same pack using both mechanisms. Start the server. Later config changes can be applied with /drugs reload and take effect on the next join.

When required is true, declining the pack or failing to download/apply it disconnects the joining player. When false, the pack is optional. Other plugins' resource-pack statuses are ignored. Invalid URL/hash configuration disables this feature and logs a warning, avoiding join failures from a malformed configuration. The default is disabled until a real URL is supplied. Update sha1 whenever the hosted ZIP changes.

Validated with Java 25 / Paper API 26.3.build.18-alpha: build passed and 67,600 regression assertions passed, including URL/hash validation and all pack status classifications. Not tested on a live Minecraft client/server.

Paper API reference: https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html
