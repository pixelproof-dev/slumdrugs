# SlumDrugs 1.2.0 — village takeover

Built for Paper 26.3, Java 25. Build verified against cached Paper API 26.3.build.18-alpha.

## Install
Stop the server, replace the old plugin JAR in plugins/ with SlumDrugs-1.2.0.jar, and start it again. Keep the existing plugin data folder. The matching resource-pack ZIP includes all 25 supplied textures and the 3D furniture from 1.1.0.

New installations get the configuration below. Existing config files are preserved; absent takeover settings use these same defaults. Add the section to the existing config if you want to change them, then /drugs reload.

```yaml
village-takeover:
  enabled: true
  automatic: true
  houses: 3
  search-radius: 96
  min-distance-between: 512
  reuse-villagers: true
  snapshot: true
```

## Commands and behavior
- `/drugs district takeover` converts a nearby eligible village manually; requires slumdrugs.admin.
- `/drugs district restore` restores the active takeover's recorded blocks and removes its plugin NPC tags/spawned NPCs; requires slumdrugs.admin.
- `/drugs district build` retains the original builder. Restore an active takeover before using it.
- Automatic discovery checks one online player every five seconds, rotating through players. It reads generated village metadata in loaded chunks, never runs a terrain-generating structure search, and waits for the village and its immediate margin to be loaded. Ineligible villages are retried after a minute.
- One active district is supported. Existing districts prevent automatic and manual takeovers. Converted centres remain recorded after restore, so the same village cannot be taken twice. Minimum spacing applies to this history.
- House detection scans a bounded block snapshot volume for beds, doors and workstations, then flood-fills roofed rooms. Beds are ordinary blocks in 26.3, so detection does not rely on bed tile entities.
- Up to three safe houses become the den, clinic and stash house. Fewer houses merge the stash into the den. With one house the fixer is outside; the medic prefers an existing cleric. The server log states the selected buildings and any skipped basements.
- There are three registered delivery destinations: tavern, warehouse and outpost. Delivery jobs now choose from the district's actual registered barrels.
- Basements are only excavated when the full underground volume is suitable. Small buildings, caves, liquids or unknown/player-edited blocks cause the basement to be skipped. Grow rooms contain ladder access, a lamp and crops registered with the existing farming system when a wheat-based substance is configured.
- Existing adult villagers receive role tags without changing profession, appearance, name or position. Existing golems become guards. Missing roles get newly spawned NPCs. Sneak-right-click an adopted villager to use its vanilla trading interaction.
- Repeating `/drugs populate` does not duplicate previously assigned takeover roles. The NPC ledger intentionally does not respawn missing UUIDs, since they may be in unloaded chunks.
- Takeover condition values still affect gameplay, but `District.refresh()` does not make unjournaled cosmetic edits to a converted village.

## Safety and restore limits
Player placements, breaks, successful bed use, bucket actions and tracked piston movements are recorded in chunk persistent data from installation onward. Minecraft has no reliable historical block-ownership or bed-use record: pre-installation player builds that resemble a vanilla village cannot be identified with certainty. A conservative palette, tile-entity checks, nonempty/loot-bearing container checks and online respawn-bed checks reduce that risk. Third-party edits outside Bukkit events are not ownership-tracked.

The full block-data journal `takeover-<id>.yml` is atomically written before conversion. Original tile entities are not replaced. Keep this file and district.yml. Disabling `snapshot` also disables conversion so no irreversible takeover can occur.

Restore preflights all recorded blocks before editing any of them. Nonempty containers and conflicting post-conversion block changes stop it with coordinates; empty the container or return the conflicting block to its takeover state and try again. Normal crop aging and farmland moisture changes are allowed. Restore handles interrupted apply/restore journals on startup and defers cleanup of unloaded NPCs until their entities load. It restores recorded block data, not elapsed world simulation, dead villagers, player inventories or transactions. It does not resurrect entities.

Recovery failures are logged and automatic conversion is blocked until the underlying journal/world issue is resolved and the server restarted. Keep a normal world backup before first live use.

## Validation
`gradlew.bat --offline --no-daemon build exportPack` passed on Java 25 with 67,586 assertions, including house flood bounds, journal atomic replacement, restore conflicts, crop changes and existing plugin/pack checks. API symbols compile against Paper 26.3. The inherited StationManager API deprecation warning remains.

No live Minecraft server gameplay test was performed. Test discovery, each house-count mode, restart, delivery barrels and restore on a copy of your world before rollout.

## Source and changes
SlumDrugs-Source-1.2.0.zip includes complete source and Gradle wrapper. village-takeover.patch contains the exact changes to the existing integration files plus all five new world classes. Source includes the additional farming, NPC listener and regression-test hooks.

Paper's 26.3 API index documents the removed bed block entity: https://jd.papermc.io/paper/26.3/index-all.html
