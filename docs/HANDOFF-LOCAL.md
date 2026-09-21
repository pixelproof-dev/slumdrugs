# SlumDrugs mod — handoff for the local session

For the session that sits at a machine with a Minecraft client. Everything below was built
and verified in a remote session that could compile, run the simulation checks, start the
dedicated server headless and drive it over RCON, but never open a client. What it could not
see is listed plainly, and that is most of what this session is for.

Read `docs/MOD-NOTES.md` for build details and the command reference, `docs/GUIDE.md` for how
the game plays, `docs/STRUCTURES.md` for pieces and markers.

## Where things stand

Branch `claude/last-status-dw2niz` on `pixelproof-dev/slumdrugs`, 21 commits ahead of
`5f31a2d`, all pushed, working tree clean. `./gradlew build` is green: 94,118 simulation
assertions, the week-of-play envelope, the jar. CI runs the build, `tools/check_data.py` and
`tools/smoke_server.sh` on every push.

| | |
| --- | --- |
| Loader | NeoForge `26.3.0.6-beta`, ModDevGradle 2.0.147, Gradle 9.1.0, Java 25 |
| Build | `./gradlew build` (fetches the toolchain and NeoForge itself) |
| Client | `./gradlew :neoforge:runClient` |
| Server | `tools/smoke_server.sh`, or `./gradlew :neoforge:runServer` |
| Checks | `python3 tools/check_data.py`, `./gradlew :sim:check` |

**26.3 postdates every model's training data.** Do not write API calls from memory; read the
decompiled sources under `neoforge/build/moddev/artifacts/` first. The table "Writing
against 26.3" in `docs/MOD-NOTES.md` has 19 rows of patterns that a recalled 1.21 gets wrong,
four of them found this session (`ChunkPos` is a record, `getDayTime` is
`getDefaultClockTime`, `WORLD_SURFACE_WG` is generation-only, structure palettes at the
current DataVersion spell `id`/`properties`).

## What was built since the last local session

In the order it landed:

1. **Station models redesigned** (`tools/build_models.py`, palette in `tools/make_textures.py`).
   Ten distinct silhouettes: brass glasshouse, peaked loft, capstan press, wax-pot sealing
   bench, braced crate, octagonal centrifuge, copper still with swan neck, end-grain cutting
   block with a balance, trellised grafting bench, clerk's desk. `docs/BLOCKBENCH-BRIEFS.md`.
2. **Three checks beyond the build.** `tools/check_data.py` (data and assets against each
   other), `tools/smoke_server.sh` (the server, headless, then RCON commands from
   `tools/smoke_commands.txt`), `Playthrough` (a simulated week under six policies, fails
   when the balance leaves the design's envelope). Their first runs found real breakage,
   listed under "Fixed" below.
3. **Working states and sound.** Still glows, steams and bubbles; centrifuge band spins and
   spigot drips; the frame's lantern burns only while the crop is comfortable and gives
   light; loft bundles turn brown when dry. Every station sound is a `slumdrugs:` event in
   `sounds.json`, mapped to vanilla sounds, with subtitles.
4. **The street talks.** `Barks`: 29 contexts × 3 lines, en and de, chosen from loyalty,
   demand, suspicion, mood, standing, time of day.
5. **Player's guide**, `docs/GUIDE.md` and `docs/GUIDE.de.md`.
6. **Turf without war.** A chunk is a corner; `/slum turf claim` with a stamped sovereign in
   hand; crew corners only from standing 40; own corner pays 15% more and is noticed 15%
   less; crews resent you on a corner they lost. No war stages.
7. **A hired hand.** Click a resident with a stamped sovereign at Workshop tier: they work
   your loft and press within six blocks and take two shillings a day from the nearest
   crate, quitting when it is empty.
8. **A generated resident's house** with people markers, and the marker mechanism itself.

### Fixed along the way

- The trader house chest loot table used `minecraft:air` as an empty entry. **This stopped
  the dedicated server from starting at all.** Now `minecraft:empty`.
- Both lang files and five item models ended in a literal `\n` after the closing brace:
  invalid JSON, raw keys and missing models in the game. The five essence item textures were
  written as escaped text instead of bytes. All repaired.
- `@OnlyIn(Dist.CLIENT)` on `ConditionHud` and `CentrifugeScreen` is a load warning in 26.3.
  Removed.
- The Workshop gate opened at the same moment as the Backroom (sixty shillings is what the
  first twenty units earn). It is 240 shillings now; Backroom day 3, Workshop day 6 for a
  careful two-frame grower.
- The sealing press refused product of a different quality, so parcels of eight were hard to
  fill from harvests of three. It pools at the weighted mean now.
- `StructurePlacer` asked loaded chunks for the worldgen heightmap.

## What only a client can verify

Nothing below has been seen. Check them in this order; each is a short walk.

1. **Models and item icons.** Place all ten stations. Are the silhouettes right in the world
   and in the hand? The generated palette textures are placeholders that your drawn art
   overwrites by filename (`assets/slumdrugs/textures/block/*.png`, names in
   `docs/BLOCKBENCH-BRIEFS.md`). The item textures under `textures/item/` for coins and
   essence are placeholders too.
2. **Working states.** Load the still with dried sunleaf, sugar and coal: firebox animated
   and glowing, smoke off the neck, flames at the grate, bubbling and crackle sounds. Same
   for the centrifuge with charcoal: band animating, drip, whirr. Plant a frame: lantern lit
   and giving light when the climate fits, dark when it does not. Hang raw on the loft: green
   bundles, brown once dry.
3. **Sounds.** Every station action should make a sound with a subtitle. If one is silent,
   `sounds.json` names the wrong vanilla event.
4. **Barks.** Walk past spawned NPCs (`/slum npc spawn customer` and the rest). Grey italic
   line in chat with their name, at most one every 30 s.
5. **The resident house.** `/slum structure place resident_house`: two people move in, the
   chest fills on first open, the campfire smokes up the chimney, the door and doorstep sit
   at ground level. Walk it; anything you dislike, rebuild and re-export with a structure
   block. Markers survive that round trip.
6. **Turf and the hand.** Claim a corner, sell on it, watch the action bar. Hire a resident
   and put coin in a crate near a loft with bundles hanging.
7. **HUD and screens** for anything the model redesign or the state properties might have
   disturbed: the centrifuge screen, the condition HUD.

## Things that need the local side

- **Village wiring** for the trader house exists only on your machine. Merge it. It touches
  structure placement; `StructurePlacer.place` now also calls `people(level, box)` after
  `placeInWorld`, so route the jigsaw-placed pieces through the same call or copy that step.
- **The trader house needs a marker.** Place a jigsaw block where the trader stands, target
  `slumdrugs:npc/trader`, final state `minecraft:air`, and re-export the piece. Set the
  chests' loot table before saving: `/data merge block <pos> {LootTable:"slumdrugs:chests/trader_house"}`.
- **More pieces.** The generator (`tools/build_structures.py`) can produce the next ones the
  same way; `docs/MOD-NOTES.md` "Parked" and the previous chat list what the systems need:
  broker's warehouse, watch house with a cell, healer's infirmary, counting house, two crew
  quarters, a market square. Hand-built is better where it is a landmark; put markers in
  either way.

## Tuning knobs worth a look in play

All in `config/slumdrugs-server.toml` once a world has run once; defaults in `Tuning.java`.

- `watch.cellSeconds` 180 and the raid's cost: the week simulation shows a player who ignores
  the Watch and eats one raid still out-earns one who lays low by about a fifth. If the Watch
  should be feared, the raid needs to cost more.
- `progression.workshopShillings` 240, `backroomUnits` 20.
- `stations.growthSeconds` 600, `dryingSeconds` 90, `strokeSeconds` 4.
- `market.demandMax` 64, `refillPerMinute` 2, `rivalDrainPerMinute` 0.6.

## Parked, on purpose

War stages, houndsmen, crews for hire, deeds, the Apothecary tier and above, settlement
generation. Each is a deliberate stop that needs settlements and a client in front of it.
The residents-with-the-house problem is solved in general by markers; the trader house is
the one piece still without them.

## Tools, one line each

| | |
| --- | --- |
| `tools/make_textures.py` | writes the palette PNGs, deterministic |
| `tools/build_models.py` | writes every station model JSON, with working-state variants |
| `tools/render_models.py [name]` | draws a model with real textures to `build/model-previews/` |
| `tools/build_structures.py` | writes the generated pieces as structure NBT |
| `tools/render_structure.py [name]` | draws a piece, four views, to `build/structure-previews/` |
| `tools/mcnbt.py` | NBT read/write, no dependencies |
| `tools/rcon.py "cmd => expect"` | runs commands on a server over RCON and checks replies |
| `tools/check_data.py` | lang, models, textures, sounds, recipes, loot, advancements, structures |
| `tools/smoke_server.sh` | server up, commands, down; `SMOKE_COMMANDS=file` for other lists |
