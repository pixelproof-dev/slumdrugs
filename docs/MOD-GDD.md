# SlumDrugs — Standalone Mod

**Game Design Document · draft v0.1 · 2026-09-18**

A design for taking SlumDrugs from a Paper plugin (v1.2.2) to a full mod: real blocks,
items, entities and screens, plus the systems a plugin cannot reach — gang warfare over
city turf, named boss encounters, a hired crew, and a MineColonies interface that wires
the district into a living colony economy.

This document is design intent, not a commitment. Numbers are starting points for tuning,
not balance decisions. Nothing here is implemented yet.

---

## 1. Vision

You arrive in a condemned river quarter with a pocket of seeds and no leverage. You end
up either owning the district or getting out clean before it owns you.

### Pillars

1. **The chain is the game.** Seed → plant → harvest → dry → process → package → sell.
   Every system (quality, heat, turf, crew) hangs off that spine and is legible at a glance.
2. **Everything is fictional.** Invented plants and minerals, invented effects, abstract
   verbs (dry, press, seal). No real substances, no real chemistry, no procedures.
3. **The world pushes back.** Rivals, the law, your own dependence and your customers'
   loyalty all react to what you did last week. Nothing is a static spawner.
4. **Reversible by default.** Every world edit is journalled and restorable, every
   subsystem is config-togglable, and the mod never silently rewrites player builds.
5. **An ending exists.** Retirement is a real win condition, not a soft-lock into grinding.

### Fantasy in one line

*Small-time grower → local supplier → crew boss → the reason the district has a curfew.*

---

## 2. Platform and assumptions

| Item | Decision | Confidence |
| --- | --- | --- |
| Loader | NeoForge (the user asked for "Forge"; on modern MC that is NeoForge in practice) | **Verify** which loader ships for the 26.3-era target before M0 |
| MC target | Same generation the plugin targets (Paper API 26.3, pack_format 97) | Verify |
| Java | 25, matching the current toolchain | High |
| Sides | Required on client *and* server. Single-player supported, multiplayer is the design target | High |
| Optional deps | MineColonies (compat module), JEI/EMI (recipe display), Jade/WTHIT (block tooltips), FTB Teams / player-claim mods (turf boundaries) | Medium |
| Hard deps | None beyond the loader. GeckoLib is an open question for boss animation (§13) | Medium |

**Why a mod at all.** As a plugin, custom visuals depend on a forced resource pack, custom
items are vanilla materials wearing a `CustomModelData` mask, stations are barrier blocks
with an `ItemDisplay` glued on top, and NPCs are villagers with tags. As a mod, all of those
become the real thing: registered items, blocks with block entities, entities with brains,
and menus with their own screens. The cost is that every client needs the jar.

---

## 3. What carries over from the plugin

The port question, answered with the current source in hand. Main source is 7,070 LOC.

| Package | LOC | Fate in the mod | Difficulty |
| --- | --- | --- | --- |
| `crime/` (HeatManager) | 342 | Near 1:1 — pure simulation plus a handful of API calls | Low |
| `player/` (condition, data, recovery) | 684 | Logic ports; storage swaps YAML → data attachments | Low–Med |
| `economy/` (Market, EconomyService) | 141 | Market ports as-is; Vault has no equivalent — own wallet becomes primary | Low |
| `drug/` (Drug, registry, quality, transfers) | 678 | Model ports; `Items.java` rewritten against real items + data components | Medium |
| `farm/` (FarmManager, Plot) | 492 | Logic ports; block reads/writes swap to `Level`/`BlockState` | Medium |
| `effect/` (EffectsManager) | 175 | Particles, sounds and mob effects all have equivalents | Low–Med |
| `world/` (District, takeover, survey, snapshot) | 1,441 | Journal and survey logic port; block accessors rewritten; gains a real structure generator | Medium |
| `station/` | 732 | Rewritten as Blocks + BlockEntities. `FurnitureDisplay` (53) deleted outright | Med–High |
| `npc/` | 748 | Rewritten as registered entities with goals/brains instead of tagged villagers | High |
| `listener/` + `ui/Menu` | 604 | Rewritten: Bukkit events → loader event bus; inventory holders → `AbstractContainerMenu` + `Screen` + payloads | High |
| `command/` (DrugsCommand) | 378 | Rewritten against Brigadier (and gets better tab completion for free) | Medium |
| `pack/` + `PackExport` | 425 | **Deleted.** Textures and models ship inside the jar | — (removal) |
| root (`Msg`, `Keys`, plugin class) | 270 | `Component` is native; keys become `ResourceLocation` + component types | Low |

**Rough split:** ~2,400 LOC of platform-neutral simulation survives with mechanical edits,
~4,200 LOC is rewritten against different APIs, ~440 LOC disappears. The regression runner
(67,600 assertions, no test framework) survives and should stay the model — see §9.

**Parity port effort:** 4–8 focused weeks for one developer to reach feature parity with
1.2.2 as a mod. The three real time sinks are menus + networking, entities, and re-testing
everything by hand because none of it has had a live gameplay pass yet.

**What the port buys immediately:** no forced pack download, no barrier-block furniture, no
`CustomModelData` collisions with other content, real recipe-book/JEI integration, proper
creative tabs, block-entity persistence instead of a parallel `stations.yml`, and client-side
rendering (HUD, screens, shaders) that a plugin simply cannot do.

---

## 4. Core loop and progression

### Minute-to-minute

Tend plants → collect harvest → run it through a station → read the quality result →
decide sell vs. improve → find a buyer → watch heat → spend.

### Session

Pick an objective (a contract, a turf cell, a crew hire, a boss lead), run the chain toward
it, absorb one or two reactive events (inspection, rival push, craving), bank the result.

### Campaign — six tiers

| Tier | Name | Unlocks | Gate |
| --- | --- | --- | --- |
| 0 | Hand to mouth | Pots, hand-drying, street sales, wallet | Start |
| 1 | Backroom | Growbox, drying rack, first regulars, journal | First 20 units sold |
| 2 | Workshop | Processing bench, packaging, storage crate, first crew hire, first turf cell | Reputation 15 · $500 banked |
| 3 | Lab | Extraction, strain crossing, wholesale contracts, faction standing, **Boss 1** | Tier-2 turf cell held 3 days |
| 4 | Syndicate | Second district, front businesses, laundering, colony contracts, war footing, **Boss 2–3** | Faction war won |
| 5 | Kingpin | City control, fixer network, **Boss 4**, retirement | 60% district influence |

### Endings

- **Retire clean** — launder ≥ $X, heat 0, dependence 0, hand the district to a lieutenant.
  Grants a world-level legacy perk to your next character.
- **Fall** — arrested at max wanted with no bail, or overdose death with permadeath-lite
  enabled: crew scatters, turf reverts, stash seized. World continues.
- **Kingpin** — hold ≥ 80% influence for 7 in-game days after Boss 4. The district stops
  generating hostile events and starts generating tribute.

---

## 5. Systems

### 5.1 Substances and strains

The five existing substances stay canon: **Sunleaf**, **Frostroot**, **Emberbloom**,
**Glowcap**, **Sparkshard**. Each keeps its data-driven definition (price, dose, duration,
tolerance/dependence gain, effects, withdrawal effects, crop chain).

New on top: **strains**. A strain is a mutable instance of a substance with four traits,
each 0–100, stored on the seed item:

| Trait | Effect |
| --- | --- |
| Potency | Dose and price multiplier |
| Vigour | Growth speed, yield |
| Hardiness | Tolerance of wrong light/temperature/humidity |
| Subtlety | Reduces heat per sale and K9 detection radius |

Crossing two seeds at a **Propagation Table** gives offspring near the parent mean with
variance scaled by inverse relatedness; repeated inbreeding narrows variance and adds a
defect chance. Stabilising a strain (5 generations within tolerance) lets you name it —
named strains carry a reputation of their own with customers and factions.

Two new substances arrive at higher tiers as designed progression content, not as a
different kind of thing: **Nightvein** (cave vine, tier 3, high heat, high margin) and
**Tidecap** (coastal fungus, tier 4, colony-facing, low potency but huge volume).

### 5.2 Cultivation

- **Plots** stay location-based and register as they do today, but read from real block
  state rather than a parallel map where possible.
- **Environment** per plot: light level, temperature (biome + nearby heat sources),
  humidity (water proximity, rain, closed growbox). Each substance has a comfort band;
  distance from it scales growth time and final quality.
- **Growbox** becomes a real multiblock-lite: base block + lamp + filter slot. Filters
  (charcoal, iron mesh) suppress the smell radius that guards and K9s detect.
- **Hydroponics** at tier 3: faster, higher ceiling, fails hard on power loss.
- Fertiliser keeps its charge model (3 charges, +0.35 boost) and gains a quality-risk
  variant that trades stability for potency.

### 5.3 Processing

Existing chain — dry, process, package — extended with two stages:

| Station | In | Out | Tier |
| --- | --- | --- | --- |
| Drying rack | Raw | Dried | 1 |
| Processing bench | Dried + reagents | Product | 2 |
| Packaging station | Product | Sealed package (n units) | 2 |
| **Extractor** | Product | Concentrate (½ volume, ×1.8 dose) | 3 |
| **Cutting table** | Product + filler | More volume, lower quality, higher OD risk | 3 |

Cutting is the moral pressure valve: it is always the profitable choice and always the one
that makes customers sick, loses loyalty and raises overdose incidents. Design intent is
that a player who cuts everything gets rich fast and loses their regulars in two weeks.

### 5.4 Quality

Keep the current 0–100 quality with grower and batch labels on the item. Quality becomes a
product of: strain potency × environment fit × process skill × station tier × (1 − cut ratio).
Batch labels matter more in the mod: seized goods carry the label, and the law can trace a
batch back to the plot it came from if the same label shows up twice in evidence.

### 5.5 Condition

Unchanged in spirit, more legible in practice. Intoxication, tolerance, dependence,
withdrawal, craving, overdose and recovery all keep the existing tuning as defaults
(tolerance decay 0.15/min, dependence decay 0.08/min after a 20-minute delay, rest bonus
×2.5, overdose threshold 95). What the mod adds:

- A proper HUD: an intoxication meter, a craving pip, a withdrawal timer. No more guessing.
- Client-side effects stay **opt-in** exactly as today: camera movement off, shaky aim off,
  nausea off by default. Darkness pulses on. This is a deliberate accessibility stance.
- Recovery gains a path with structure: clinic treatment, remedy items, a rest bonus, and a
  multi-day "clean streak" that unlocks a permanent tolerance-ceiling reduction.
- `no-vice` server mode reskins the whole condition layer into "tonic fatigue" with no
  dependence or withdrawal, for servers that want the economy game without the theme.

### 5.6 Customers and demand

Customer profiles stay deterministic per district seed, so a district keeps its regulars.
Each profile gains:

- **Schedule** — a visiting window in day ticks, a haunt, and a walk route between them.
- **Loyalty** (0–100) — raised by quality, fair prices and availability; dropped by cutting,
  no-shows and rival poaching. High loyalty unlocks standing orders; zero loyalty turns them
  into a rival's customer or, worse, an informant.
- **Preference** — a favourite substance and a quality floor. Selling below their floor
  costs loyalty even at a good price.
- **Risk** — how likely they are to sell you out under pressure. Visible only through tells.

Market demand keeps the existing model (per-drug demand pool, refill per minute, rival
drain) and gains a per-cell price modifier so turf control has a direct economic payoff.

### 5.7 Economy

- The built-in wallet becomes primary — Vault does not exist here. Compat with modded
  currencies is a stretch goal behind a small `CurrencyBridge` interface.
- **Dirty vs. clean money.** Sales produce dirty money. Dirty money can buy from fixers and
  gangs; it cannot buy property, bail, colony contracts or the retirement ending.
- **Laundering** through fronts: a laundromat, a market stall, a taxi rank, or (with
  MineColonies) a colony business. Each front has a throughput cap per day and a cut, and
  each one is a liability the law can audit.
- **Property** — deeds to buildings and turf cells, bought clean, giving passive effects
  (storage, safehouse respawn, front throughput, patrol suppression).

### 5.8 Heat and the law

The existing heat system is the best-ported piece in the codebase, so it becomes the base
layer, with escalation above it.

| Layer | Range | Behaviour |
| --- | --- | --- |
| Personal heat | 0–100 | As today: inspections, informants, intercepted deliveries, rival offers, raids at 80 with a 120-second warning |
| Cell heat | 0–100 per turf cell | Drives patrol density, checkpoints and curfews in that cell |
| Wanted | 0–5 stars | Active pursuit state; decays with distance, disguise or a safehouse |

Additions: **evidence** (a seized batch label, a searched crate, a witness statement) that
persists and builds a case file; **warrants** that let officers enter your property legally;
**arrest** as a real state (jail cell, timer, bail cost, a lawyer NPC, confiscation of what
you carried); **K9 units** that detect unsealed goods within a radius reduced by packaging
tier, filters and strain subtlety; and **bribery** of a named precinct officer, who is also
a liability if internal affairs ever audits them.

Design rule kept from the plugin: raids never destroy blocks and always warn first.

### 5.9 Gangs and rivalry

The headline new system. Four powers, each with a doctrine, a home quarter, a substance
preference and a different way of hurting you.

| Faction | Doctrine | Wants | Pressure style |
| --- | --- | --- | --- |
| **Ashfall Crew** | Muscle and extortion | Emberbloom, protection money | Enforcer squads, station sabotage |
| **Tidewater Syndicate** | Smuggling and logistics | Volume, dock access | Price wars, supply cut-offs, customs tips to the law |
| **The Glass Choir** | Information and alchemy | Glowcap, secrets | Blackmail, informants, spiked product |
| **The Quarry Kings** | Mineral trade, tier 4+ | Sparkshard, tunnels | Siege of your safehouse, tunnel collapses |

**Standing** with each runs −100…+100 and moves on everything you do: selling in their
quarter, taking their contracts, hitting their runners, buying their product, backing a
rival in a dispute. Standing is zero-sum between opposed pairs — the Choir hates every
point you gain with Ashfall.

**Turf.** The district is a grid of cells (the plugin's `cell-size: 9`, `radius-cells: 3`
becomes the default 7×7). Every cell holds an influence vector across the factions and you.
Influence moves ≈ +0.4 per sale made in the cell, +2 per rival runner removed, +5 per
protection payment refused and survived, −1/day of absence, −3 per rival event you ignore.
A cell flips at 51% and consolidates at 75%.

**War.** Standing ≤ −60 with a faction opens a war, which escalates through five stages —
tagging → runner brawls → station sabotage → assault waves → a siege of your safehouse —
each with a way to de-escalate (tribute, a hostage returned, a ceded cell, a public favour).
A war that runs its course ends in a boss confrontation.

**Diplomacy.** Tribute, timed supply contracts, joint operations against a third faction,
ceasefires with a duration, and betrayal (huge one-time gain, permanent standing floor).

**Player gangs (multiplayer).** Players can form a crew that shares turf, stash access and
standing. Server config picks the stance: cooperative (one shared district), competitive
(turf contested between player crews, PvP in contested cells only), or free-for-all.

### 5.10 Bosses

Four staged encounters, each ending an arc and unlocking a tier. Each has a named entity, a
purpose-built arena, three phases, a non-lethal resolution path, and loot that changes how
you play rather than just what damage you do.

| # | Boss | Arena | Signature mechanics | Non-lethal out | Drops |
| --- | --- | --- | --- | --- | --- |
| 1 | **Kell the Collector** (Ashfall) | Burning warehouse | Summons thugs, ground slam, grabs and throws, crates as cover that burn away | Pay triple tribute mid-fight | Ashfall brand deed, enforcer contract, fireproof crate |
| 2 | **Harbourmaster Vyne** (Tidewater) | Docks and moving barges | Nets that root, crane hazards, water phase where the arena floods | Deliver her rival's manifest | Smuggling route map, bulk contract terminal, tide charts |
| 3 | **The Choirmaster** (Glass Choir) | Fugue arena built from your own district | Clones that mirror your last actions, spiked fog that raises intoxication, hallucination adds that only you can see | Answer three riddles from your own journal | Strain stabiliser, truth serum, the Choir's ledger (reveals every informant) |
| 4 | **Commissioner Rade** (Law) | Precinct rooftop under floodlights | Officer waves, flashbangs, K9 pairs, an arrest attempt instead of a kill move; you can lose by capture | Hand over the Choir's ledger | Case file purge, permanent bribery network, the retirement trigger |

Boss design rules: no bullet-sponge phases; every mechanic telegraphs; each fight is
survivable at the tier it unlocks with the tier's own tools; death costs the stash you
carried, never your turf.

Optional hidden fifth: **The Quiet Partner**, the market itself — a lore encounter for
players who read the whole journal. Design later, deliberately vague for now.

### 5.11 Crew

Hire NPCs from the fixer. Each has a job, a wage, a skill (0–100), a loyalty and a vice.

| Job | Does | Fails by |
| --- | --- | --- |
| Grower | Tends plots on a schedule | Overwatering, forgetting the lamp |
| Drier / Chemist | Runs stations while you are away | Ruining batches at low skill |
| Runner | Delivers standing orders, sells at a fixed margin | Getting caught, skimming |
| Lookout | Warns of patrols, lowers cell heat | Falling asleep at low loyalty |
| Muscle | Defends stations and turf, escorts deliveries | Starting fights you did not want |

Crew cost is real (wages daily, in clean or dirty money), and crew are a liability: an
underpaid runner skims, a pressured lookout flips informant, a captured member becomes
evidence. High-loyalty crew can be promoted to lieutenant and hold a cell for you — that is
the mechanism that makes the retirement ending possible.

### 5.12 District, property and the city

Two ways to get a district, and this is a deliberate fix for the plugin's weakest point:

1. **Natural generation (new default).** A rundown quarter generates as a structure in a
   configured biome/distance band, so no existing player build or village is ever touched.
2. **Annexation (opt-in).** The existing village takeover, kept intact: journalled,
   restorable, player-edit aware, refuses to run without a snapshot, skips unsafe houses,
   preserves native villagers. Off by default in the mod.

The district itself keeps a **condition** score (starts at 25) that shifts with your
investment and the gangs' hold, and now drives visible change: boarded windows open up,
lighting improves, graffiti changes owner, NPC density and behaviour shift. Buildings
become ownable property with upgrade slots. A **sewer network** under the quarter links
safehouses for fast travel and heat-free transport, and doubles as tier-4 grow space.

### 5.13 MineColonies interface

The compat module (`slumdrugs-minecolonies`), optional, fail-soft, loaded only when the mod
is present. Design goals: a colony should feel like a *market and a moral consequence*,
not a vending machine.

**Colony as a market node.** Every colony within range becomes a demand node with its own
price curve, absorption cap and policy — *clean*, *ambivalent* or *corrupt* — derived from
its happiness, guard level and any research the player has bribed through. Corrupt colonies
pay more and ask fewer questions.

**Colonists as customers.** A subset of citizens are mapped, via a datapack JSON table from
colony job → customer archetype, into real customer profiles with the colony's schedule.
You sell at the gates at night, through a runner contract, or via a colonist middleman.

**Consequence.** Sales into a colony accumulate a **vice** stat: happiness drops, work
output drops, crime events rise, guards start patrolling further out and eventually the
colony sends a raid at your district. Selling remedy to the healer's hut, or funding a hut
upgrade with clean money, walks vice back down — the legitimate path is slower and always
available.

**Supply, both directions.** Buy bulk ingredients through the colony request system; sell
legitimate goods (remedy, fertiliser, dried non-psychoactive crops) for standing. A colony
you keep healthy becomes the best laundering front in the game: a *registered business*
converts dirty to clean at a rate scaled by real colony production.

**Interface shape.** A thin `ColonyBridge` service interface in core with a no-op default;
the compat module implements it against the MineColonies API (colony manager, colony,
citizen data, request manager, raid events — **the exact API surface must be verified
against the current release before M5**). Every call is behind the interface, so an API
break disables the integration instead of crashing the mod.

**Fallback.** Without MineColonies, the same design runs against the mod's own district
NPCs. Nothing in the critical path requires the integration.

### 5.14 Other compatibility

| Mod | Integration | Priority |
| --- | --- | --- |
| JEI / EMI | Station recipes, strain crossing table, processing chains | High |
| Jade / WTHIT | Station status, plot environment readout, growth timer | High |
| FTB Teams / claims | Turf cells respect claims; contested-cell PvP rules | Medium |
| Create | Mechanical drying and packaging as an alternate tier-3 path | Stretch |
| Farmer's Delight | Shared crop idioms, cooking overlap for remedies | Stretch |

---

## 6. Content manifest (target for 1.0)

- **Items** ~70: seeds, raw, dried, product, package, concentrate per substance (7 × 6),
  remedy tiers, fertilisers, filters, reagents, fillers, deeds, contracts, journal,
  stabiliser, evidence bags, boss uniques.
- **Blocks** ~25: growbox (4 stages × lamp states), drying rack, processing bench,
  packaging station, extractor, cutting table, propagation table, storage crate, drop box,
  safehouse door, front-business counter, planters, district decoration set.
- **Entities** ~14: customer, resident, guard, medic, fixer, trader, thug, officer, K9,
  runner, lookout, muscle, hallucination, plus 4 bosses.
- **Structures**: rundown quarter (jigsaw, ~20 pieces), docks, precinct, sewer network,
  abandoned greenhouse, 4 boss arenas.
- **Screens** ~12: each station, journal (5 tabs), contracts, crew, turf map, clinic, fixer.
- **Audio** ~30 cues; **particles** ~8; **advancements** ~40; **i18n**: `en_us` + `de_de` at 1.0.

---

## 7. UI and UX

- **HUD** (client config, all individually toggleable): intoxication meter, craving pip,
  heat bar shown only above 0, wanted stars, active contract line.
- **Journal** — the mod's codex and quest log in one book-style screen: Substances (with
  discovered strain data), Contacts (customers, loyalty, tells), Turf (influence map),
  Heat (case files, known evidence), Crew, Contracts, Factions.
- **Turf map** — a screen-level district map, cells tinted by controlling faction, with
  influence deltas since last visit.
- **Station screens** — direct manipulation with live readouts: the growbox shows its
  environment dials and what is out of band; the bench shows why a batch scored what it did.
  Design rule: the player should never have to consult a wiki to learn why quality dropped.
- **Notifications** — one channel, rate-limited, never a wall of chat. Urgent events
  (raid warning, war escalation) use a toast plus a sound, not spam.

---

## 8. Technical architecture

### Module layout

```
slumdrugs/
  sim/                 no Minecraft imports at all — the entire simulation
  mod/                 registries, blocks, entities, menus, networking, worldgen
  mod/client/          screens, HUD, renderers, particles (client-only source set)
  compat/minecolonies/ ColonyBridge implementation, optional
  compat/jei/          recipe categories, optional
  datagen/             models, blockstates, recipes, loot, advancements, lang
```

The `sim` boundary is the single most important decision in this document. The plugin
already proves it works: `HouseSurvey`, `Quality`, `Recovery`, `UnitTransfer` and the
journal logic are testable today with a dependency-free runner and 67,600 assertions. Keep
that, push more logic into it, and the mod stays testable without ever booting a client.

### Data model

| State | Storage |
| --- | --- |
| Item identity (substance, strain, quality, grower, batch, units, sealed) | Data components on the stack |
| Station contents and progress | Block entity NBT |
| Plots | Block entity or chunk-attached data, whichever benchmarks better |
| Player condition, wallet, reputation, skills | Player data attachment, synced to client for HUD |
| District, turf, factions, wars, contracts, case files | Level `SavedData` |
| Takeover journals | Kept as standalone files, atomic write, as today |

### Content as data

Substances, strains, recipes, customer archetypes, faction definitions, contract templates
and boss loot all load from datapack JSON. A server can retheme the entire mod — including
into `no-vice` mode — without touching code. Config TOML covers toggles and rates only.

### Networking

Small typed payloads: HUD state sync (throttled, dirty-flag only), screen actions,
journal page requests, turf map snapshot. No per-tick entity sync beyond vanilla.

### Performance budget

Target ≤ 1 ms/tick server-side with 50 stations, 30 NPCs and 8 players. Station ticks stay
bucketed (the current 40-tick cadence is a good default), NPC sim runs on a staggered
1-second cadence, turf resolves once per in-game hour, and all file IO is off-thread with an
async save queue. Chunk-unloaded stations tick lazily on catch-up, not continuously.

### Migration from the plugin

`/slumdrugs import` reads a plugin data folder and converts `district.yml`, `stations.yml`
and `players/*.yml` into mod state. Honest limitation to document loudly: items already in
player inventories carry Bukkit persistent data on vanilla materials and **cannot** be
converted — the importer grants replacement items by inventory scan where it can identify
them, and says so where it cannot.

---

## 9. Testing and quality

- Keep the dependency-free regression runner and grow it; every `sim` rule gets assertions.
- Add game tests for the things `sim` cannot see: block entity persistence across reload,
  menu round-trips, takeover-and-restore on a generated village, structure placement.
- **Explicit debt carried over:** the plugin has never had a live gameplay test. The mod's
  M0 exit criterion is a recorded playthrough of the full chain on a dedicated server with
  two clients, not a green build.

---

## 10. Content responsibility

Deliberate positions, inherited from the plugin and extended:

1. Every substance, effect and process is invented. No real-world names, no real chemistry,
   no synthesis steps, no procedures that map onto anything real. Processing verbs are
   abstract: dry, press, seal, cut with a generic filler.
2. Dependence and withdrawal are modelled as a system with a visible, always-available
   recovery path. Withdrawal effects stay mild by design.
3. Camera-altering effects (shake, nausea, aim nudge) stay **off** by default and require
   explicit opt-in. Darkness pulses can be disabled.
4. `condition.enabled: false` removes dependence and withdrawal entirely; `no-vice` mode
   reskins substances as tonics for servers that want the economy without the theme.
5. No depiction of real-world harm to minors, no sexual content, no real-world hate groups
   in faction writing. Factions are invented criminal organisations with invented grudges.

---

## 11. Production plan

Single developer, focused weeks. Multiply by 2.5–3 for evenings-and-weekends pace.

| Milestone | Scope | Effort |
| --- | --- | --- |
| **M0 Parity port** | Everything in 1.2.2, running as a mod: items, stations, farm, NPCs, heat, district, commands, migration importer | 4–8 wk |
| **M1 Native content** | Real models and blocks, proper screens, HUD, journal, natural district generation, JEI/Jade | 3–5 wk |
| **M2 Law and crew** | Evidence, warrants, arrest and bail, K9, wanted levels, crew hiring and jobs | 3–4 wk |
| **M3 Gangs and turf** | Factions, standing, influence grid, war escalation, diplomacy, player crews | 4–6 wk |
| **M4 Bosses** | 4 encounters, arenas, AI, loot, unlocks (art and animation heavy) | 3–5 wk |
| **M5 MineColonies** | `ColonyBridge`, market nodes, colonist customers, vice, requests, fronts | 2–4 wk |
| **M6 Release** | Strains and extraction polish, balance pass, advancements, `de_de`, docs, live playtest | 3–4 wk |
| | **Total** | **22–36 wk** |

Roughly 5–8 months full time, 12–18 months part time. Art is the hidden cost: ~120–200
textures and models, plus four bosses. That is a second skill set and probably a second
person, or a scope cut on visual ambition.

**Cut lines if time runs short**, in order: hidden fifth boss → strain genetics → sewer
network → Create/Farmer's Delight compat → player-vs-player crews → extraction tier.
Never cut: journalled reversibility, config toggles, the `sim` boundary.

---

## 12. Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Loader/MC version target is unconfirmed for the 26.3 generation | Blocks M0 entirely | Verify before writing code; the `sim` module is version-proof regardless |
| MineColonies API drift | Compat breaks each update | Everything behind `ColonyBridge`; integration disables itself rather than crashing |
| Scope creep — this document is already large | Never ships | M0 and M1 are a complete, shippable game on their own; ship them |
| Art volume | Slips the whole schedule | Datagen for everything procedural; placeholder-to-final pipeline as the plugin already does |
| Boss animation needs GeckoLib | Extra hard dependency | Prototype one boss with vanilla model parts first, decide with data |
| Simulation desync in multiplayer | Bad bug class | Server authoritative for all state; client gets display-only snapshots |
| Theme rejection by some servers | Reduced reach | `no-vice` mode and full toggles are first-class, not afterthoughts |
| Untested gameplay in the existing plugin | Unknown balance | M0 exit gate is a live playthrough, not a build |

---

## 13. Open questions

1. **Loader and MC version** — NeoForge for which target exactly? Everything else waits on it.
2. **GeckoLib or vanilla models** for bosses and NPCs? Affects dependency policy and art pipeline.
3. **One district or many?** The plugin supports one; the syndicate tier assumes several.
4. **PvP stance** — cooperative, competitive turf, or server-configurable (my recommendation)?
5. **Is MineColonies compat core or optional?** Designed here as optional; confirm.
6. **Keep village annexation at all**, now that natural generation exists? Recommendation:
   keep it, default off, as a documented power tool.
7. **Language** — `de_de` as a first-class release language alongside `en_us`?
8. **Plugin's future** — does the Paper plugin keep receiving features in parallel, or does
   it freeze at 1.2.2 as a maintenance branch once the mod starts?

---

## 14. Explicitly out of scope

Real-world substances or chemistry of any kind · voice acting · a dimension · a tech tree
beyond the processing chain · vehicles · anything requiring an external server or account ·
real-money transactions · automatic world edits outside a journalled, restorable operation.

---

## Appendix A — Naming

- Mod id: `slumdrugs`. Root package: `dev.lucas.slumdrugs`.
- Registry paths are lowercase and snake_case: `slumdrugs:dried_sunleaf`,
  `slumdrugs:processing_bench`, `slumdrugs:customer`.
- Factions, bosses and strains are invented proper nouns and never reference real people,
  organisations or places.

## Appendix B — Starting balance figures

Carried from the current `config.yml` as defaults for the mod, to be re-tuned after the
first live playtest: base prices 10 (Sunleaf) upward by substance · demand cap 64 units ·
demand refill 2.0/min · rival drain 0.6/min · tolerance decay 0.15/min · dependence decay
0.08/min after 20 min · rest bonus ×2.5 · craving after 15 min, every 4 min · overdose at 95
· raid threshold 80 with 120 s warning · clinic treatment $60 · remedy $25 · starting wallet
$50 · district 9-block cells, radius 3 · crew wages $3–8/in-game day by job (new).
