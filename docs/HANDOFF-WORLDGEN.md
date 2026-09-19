# SlumDrugs mod — handoff for world generation

Context for a session that will work on world generation. Read `docs/MOD-NOTES.md` for build
details and `docs/MOD-GDD.md` §5.12 for the design this implements.

## What the mod is

A NeoForge mod for Minecraft 26.3: a criminal economy in a gaslight-era quarter — grow
invented substances, refine them, sell them, and deal with the Watch and rival crews. It began as a
Paper plugin, which shipped through 1.2.2 and is now retired; the mod replaces it.

Setting rule: **villager-medieval, one notch into the industrial age.** Brick, brass, glass,
lanterns, canals, warehouses, the first dynamos. Steam and water power exist; electricity
exists at the top end; computers, engines with brains and firearms do not. The test for any
structure: *would it look at home in a quarter lit by lanterns, next to a vanilla village?*

## Platform facts you will need

| | |
| --- | --- |
| Loader | NeoForge `26.3.0.6-beta` (only 26.3 builds that exist), ModDevGradle 2.0.147 |
| Build | Gradle 9.1.0, Java 25. `./gradlew build` |
| Sources | Decompiled MC at `neoforge/build/moddev/artifacts/minecraft-patched-26.3.0.6-beta-sources.jar` |

**Minecraft 26.3 shipped 2026-09-15 and postdates any model's training data.** Do not write
API calls from memory — read the decompiled sources first, then let the compiler settle the
rest. Eleven wrong assumptions were caught this way so far; the table in `docs/MOD-NOTES.md`
lists them (among others: `ResourceLocation` is now `Identifier`, blocks no longer need a
`MapCodec`, NBT is `ValueInput`/`ValueOutput`, entity type constants moved to `EntityTypes`,
permission levels became named permissions).

## Architecture rule that constrains you

```
sim/       no Minecraft, no loader, no I/O — all rules live here (85,866 assertions)
neoforge/  the platform layer: registries, blocks, entities, commands, networking
```

Anything that is a *rule* — how a settlement's vice rises, which archetype a village
qualifies as, how far apart quarters generate — belongs in `sim` as plain Java with
assertions in `SimChecks`. Anything that touches `Level`, `BlockState`, structures or
registries belongs in `neoforge`. Keeping that line is what lets the rules be verified in a
second without launching a game.

## What already exists

- 22 items with `quality` and `grower` data components; 6 blocks (forcing frame with five
  growth stages, drying loft, pressing bench, sealing press, storage crate, centrifuge with a
  brewing-stand-shaped menu).
- Cultivation (seed / soil / compost → quality and yield), refining (press, centrifuge,
  still), player condition (use, tolerance, dependence, withdrawal), all in `sim`.
- NPCs are **ordinary villagers carrying an attachment**, not a custom entity type — no
  model, no renderer, no client code. Roles and an aggression model are in `sim.npc.Npc`.
- A full command tree under `/slum` (see `docs/MOD-NOTES.md`), including dry runs of the rules.

**Nothing has ever been launched.** It compiles and packages; no client or server has run it.

## What world generation is meant to do

The design is `docs/MOD-GDD.md` §5.12. In short, the world is a map of settlements, each
carrying two scores:

- **Vice** 0–100 — how much illicit trade it carries.
- **Industry** 0–100 — how far into the gaslight age it has come.

Five archetypes derived from village size, wealth, structure richness and the world seed,
with distance from the world origin biasing upward, so the frontier stays clean and the
interior is rotten:

| Archetype | Vice | What generates |
| --- | --- | --- |
| Clean village | 0–10 | An ordinary vanilla village, plus at most one home grower NPC |
| Restless village | 10–35 | A fence, a few regulars, one constable |
| Works town | 35–60 | One crew, a mill or foundry, a watch house, turf cells |
| Quarter | 60–85 | The full thing: four crews, turf grid, magistrate, gaol |
| The Port | 85–100 | Endgame: several quarters, bonded warehouses |

Four jobs, roughly in order of value:

1. **A rundown quarter as a jigsaw structure** (~20 pieces: lodgings, workshops, tavern,
   infirmary, warehouse, market) in a configured biome and distance band. This is the default
   way a player gets a quarter, and it exists specifically so no existing build is touched.
2. **Archetype assignment** for existing villages — a rule in `sim`, reading a few facts the
   platform layer hands it, returning an archetype. Deterministic per seed.
3. **The industry add-on set**: additive structure pieces placed at validated anchors as a
   settlement's Industry rises — gas lamps along paths (15), smithy chimney (30), water wheel
   or pump house (45), brick works (60), dynamo shed and arc lamp (75), canal cut or rail spur
   (90). These are the mechanism by which the world becomes gaslight, and they must be
   *additive only*.
4. **The drains** — brick tunnels and cellars beneath older settlements, linking safehouses.

## Hard rules, not preferences

- **Never rewrite a player's build.** Add only, at anchors you validated.
- **Journal every block you change**, with a restore path. The plugin's `TakeoverSnapshot`
  is the reference implementation: write-ahead journal, atomic file replacement, refuses to run
  without a snapshot, refuses to restore over a conflicting later edit. The plugin source was
  removed when the mod took over the repository — read it at commit `1886950`, under
  `src/main/java/dev/lucas/slumdrugs/world/`, and the design in `docs/legacy/VILLAGE-TAKEOVER.md`.
- **Village annexation stays opt-in and off by default.** Natural generation is the default
  path; converting someone's village is a documented power tool, not a surprise.
- **No dependency on another mod** in the critical path. Nothing else is on 26.3 yet.
- Structure pieces ship as a **datapack structure set**, so a server can swap the industrial
  add-on set for a medieval one without touching code.

## Open questions

- One quarter per world or several? The plugin supported one; the design's later tiers assume
  several.
- Should archetype assignment run at chunk generation, or lazily the first time a player
  approaches a village? Lazy is cheaper and survives worlds that already exist.
- How far apart should quarters be? The plugin used 512 blocks minimum between conversions.
