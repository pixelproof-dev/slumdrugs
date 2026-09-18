# SlumDrugs

Minecraft Paper plugin with fictional substances, growing and processing, customers, dependency and recovery, custom textures, 3D furniture, and reversible village takeovers.

## Downloads

- [SlumDrugs 1.2.2 plugin](https://raw.githubusercontent.com/pixelproof-dev/slumdrugs/main/release/SlumDrugs-1.2.2.jar)
- [Textures and 3D furniture pack](https://raw.githubusercontent.com/pixelproof-dev/slumdrugs/main/release/SlumDrugs-ResourcePack-1.2.0.zip)
- [Complete source ZIP](https://raw.githubusercontent.com/pixelproof-dev/slumdrugs/main/release/SlumDrugs-Source-1.2.2.zip)

The pack remains version 1.2.0 because its art is unchanged. Plugin 1.2.2 enables required join-time pack delivery by default using this repository's hosted ZIP.

## Install

Requires **Paper 26.3 and Java 25**. Stop the server and back up the world. Put the plugin JAR in `plugins/`, removing any older SlumDrugs JAR but preserving its data folder. Start the server. Fresh installations automatically offer the required pack to joining players; declining or failing to load it disconnects the player.

Existing config files are preserved. For an upgrade, add or replace this section in `plugins/SlumDrugs/config.yml`:

```yaml
resource-pack:
  enabled: true
  url: "https://raw.githubusercontent.com/pixelproof-dev/slumdrugs/main/release/SlumDrugs-ResourcePack-1.2.0.zip"
  sha1: "79984106053fc8b4cdaae4ad5ae546330078e6f7"
  required: true
  prompt: "<gold>SlumDrugs</gold><gray> needs its textures and furniture pack.</gray>"
```

Use `/drugs reload` after configuration changes and reconnect to receive the pack. No resource-pack settings in `server.properties` are needed; avoid sending the same pack twice. When publishing an edited ZIP, use a new versioned filename and update its URL and SHA-1 together.

## Commands

Admin commands require `slumdrugs.admin` (operators have access).

- `/drugs help` — command overview.
- `/drugs give station growbox` — obtain a growbox.
- `/drugs district takeover` — convert a nearby eligible loaded village.
- `/drugs district restore` — restore the converted village's recorded blocks.
- `/drugs district build` — original terrain-replacing builder.
- `/drugs pack` — export a pack locally on the server; this does not upload it to GitHub.

Automatic takeover is enabled by default when no district exists. Set `village-takeover.automatic: false` for manual conversion only. One active district is supported. Seeds are bought from the village trader, not generated as random chest loot.

## Safety and validation

Takeovers journal changed blocks before modifying them and preserve native villagers. Player edits are tracked from installation onward; older player builds cannot be identified reliably. Unsafe houses and basements are skipped. Restore refuses conflicting later edits and nonempty containers. See [takeover details](docs/VILLAGE-TAKEOVER.md).

Java 25 build and 67,600 regression assertions passed against Paper API 26.3.build.18-alpha. No live Minecraft client/server gameplay test has been performed; use a copied world for first testing.

## Design

[SlumDrugs as a standalone mod](docs/MOD-GDD.md) is a draft design document for a NeoForge/Forge version: what ports from this plugin, what gets rewritten, and the larger systems a mod makes possible (rival crews and turf, boss encounters, a hired crew, a MineColonies interface), all kept inside Minecraft's own era. Design only; nothing there is implemented.

## Build

Run `./gradlew build exportPack`, or `gradlew.bat build exportPack` on Windows.

JARs go to `build/libs/`; packs go to `build/distribution/`. Re-exported ZIPs may have different hashes. The checked-in release ZIP is the exact file matching the configured SHA-1.

Textures are in `src/main/resources/textures`; 3D furniture generation is in `pack/FurnitureModels.java`.
