# SlumDrugs — Standalone Mod

**Game Design Document · draft v0.1 · 2026-09-18**

A design for taking SlumDrugs from a Paper plugin (v1.2.2) to a full mod: real blocks,
items, entities and screens, plus the systems a plugin cannot reach — rival crews fighting
over turf, named boss encounters, a hired crew of your own, and a MineColonies interface that
wires the quarter into a living colony economy. All of it stays inside Minecraft's own era:
emeralds, lanterns, bells, the Watch — no modern city wearing a Minecraft skin.

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

### Era

**Everything sits inside Minecraft's own timeline: villager-medieval, not modern.** Emeralds
and barter, lanterns and bells, the Watch and the magistrate, wax seals and writs, hounds and
crossbows. No engines, no electricity, no firearms, no telephones, no bureaucracy the vanilla
world does not have.

Where a modern crime-fiction idea is worth keeping, it wears a period costume instead of being
cut: insurance becomes a **guild surety**, a licence becomes a **charter**, forged identity
papers become a **writ of pardon**, a police radio becomes a **bell tower**, a forensics lab
becomes a **magistrate's ledger of seized wax seals**. Redstone is the only technology and it
stays sparing — a bell wire, a trapped door, a lamp on a timer.

The test for any asset, name or mechanic: *would it look at home next to a vanilla village?*
If not, it does not ship. Appendix D maps every term.

### Fantasy in one line

---

## 2. Platform and assumptions

| Item | Decision | Confidence |
| --- | --- | --- |
| Loader | NeoForge. Forge proper is not the live loader on modern versions | High |
| MC target | **Open — see the ecosystem check below.** 1.21.1 is where the mod ecosystem actually lives; 26.3 is where the plugin lives | **Decision needed** |
| Java | 25 for a 26.x target; 21 for a 1.21.1 target | Follows the MC target |
| Sides | Required on client *and* server. Single-player supported, multiplayer is the design target | High |
| Optional deps | MineColonies (compat module), JEI/EMI (recipe display), Jade/WTHIT (block tooltips), FTB Teams / player-claim mods (turf boundaries) | Medium |
| Hard deps | None beyond the loader. GeckoLib is an open question for boss animation (§13) | Medium |

### Ecosystem check (researched 2026-09-19)

| Mod | Newest Minecraft version supported | Note |
| --- | --- | --- |
| Minecraft itself | **26.3 "Wilderness Bound"**, released 2026-09-15 | Four days old at the time of writing |
| NeoForge | Ships for 26.x, calver-aligned (`26.3.0.x`) | The loader is ready |
| Create | **1.21.1** | No 26.x build |
| Applied Energistics 2 | **1.21–1.21.1** | Careful: AE2's own *mod* version reads `26.1.2`, which is not a Minecraft version |
| MineColonies | **1.21.1**, actively snapshotting (2026-09-16) | No 26.x build |

The conclusion matters more than the table: **the big content mods have not moved to 26.x at
all** — not 26.1, not 26.2, not 26.3 — and MineColonies was still shipping 1.21.1 snapshots
three days ago. So a 26.3 build of this mod would have no Create, no AE2 and, decisively, **no
MineColonies to interface with**. Section 5.13 is unbuildable on 26.3 today.

That splits the target cleanly:

- **1.21.1** — the mod lands in the pack people actually play. MineColonies compat is real,
  Create interop is possible, Jade/JEI/EMI are all there. The cost is divergence from the
  Paper plugin, which targets 26.3.
- **26.3** — matches the plugin, stays current with the game, and is alone out there. Fine for
  a standalone mod, fatal for the colony integration until MineColonies ports.
- **Both** — 1.21.1 first for the ecosystem, a 26.x branch later. The `sim` module (§8) is
  version-proof by design, so a second target costs the platform layer only, not the game.

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
| 0 | Hand to mouth | Pots, hand-drying, street sales, purse | Start |
| 1 | Backroom | Forcing frame, drying rack, first regulars, journal | First 20 units sold |
| 2 | Workshop | Pressing bench, sealing table, storage crate, first crew hire, first turf cell | Standing 15 · 60 emeralds banked |
| 3 | Apothecary | Alembic, grafting, wholesale contracts, crew standing, **Boss 1** | Tier-2 turf cell held 3 days |
| 4 | Guild | Second quarter, chartered fronts, laundering, colony contracts, war footing, **Boss 2–3** | Crew war won |
| 5 | Kingpin | Quarter control, broker network, **Boss 4**, retirement | 60% district influence |

### Endings

- **Retire clean** — stamped coin ≥ the retirement threshold, suspicion 0, dependence 0, hand
  the quarter to a lieutenant. Grants a world-level legacy perk to your next character.
- **Fall** — taken at full bounty with no one to stand bail, or overdose death with
  permadeath-lite enabled: crew scatters, turf reverts, stash seized. World continues.
- **Kingpin** — hold ≥ 80% influence for seven days after Boss 4. The quarter stops generating
  hostile events and starts generating tribute.

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
| Subtlety | Reduces suspicion per sale and how far a hound smells it |

Crossing two seeds at a **Grafting Bench** gives offspring near the parent mean with
variance scaled by inverse relatedness; repeated inbreeding narrows variance and adds a
defect chance. Stabilising a strain (5 generations within tolerance) lets you name it —
named strains carry a reputation of their own with customers and factions.

Two new substances arrive at higher tiers as designed progression content, not as a
different kind of thing: **Nightvein** (cave vine, tier 3, high heat, high margin) and
**Tidecap** (coastal fungus, tier 4, colony-facing, low potency but huge volume).

### 5.2 Cultivation

- **Plots** stay location-based and register as they do today, but read from real block
  state rather than a parallel map where possible.
- **Environment** per plot: light level, warmth (biome, nearby fire and lava), and damp
  (water proximity, rain, an enclosed frame). Each substance has a comfort band; distance
  from it scales growth time and final quality.
- **Forcing frame** (the plugin's growbox, renamed to fit) becomes a real multiblock-lite:
  a frame, a lantern and a screen slot. Charcoal and woven screens damp the smell that
  watchmen and hounds follow.
- **Flood beds** at tier 3: a terraced, irrigated bed that grows faster and reaches higher
  quality, and fails hard the moment the water channel is broken.
- Fertiliser (compost and bonemeal, as now) keeps its charge model — 3 charges, +0.35 boost —
  and gains a forced-growth variant that trades stability for potency.

### 5.3 Processing

Existing chain — dry, press, seal — extended with two stages:

| Station | In | Out | Tier |
| --- | --- | --- | --- |
| Drying rack | Raw | Dried | 1 |
| Pressing bench | Dried + reagents | Product | 2 |
| Sealing table | Product | Wax-sealed parcel (n units) | 2 |
| **Alembic** | Product | Essence (½ volume, ×1.8 dose) | 3 |
| **Cutting board** | Product + filler | More volume, lower quality, higher OD risk | 3 |

Cutting is the moral pressure valve: it is always the profitable choice and always the one
that makes customers sick, loses loyalty and raises overdose incidents. Design intent is
that a player who cuts everything gets rich fast and loses their regulars in two weeks.

### 5.4 Quality

Keep the current 0–100 quality with the grower's name and a batch mark on the item, but make
the mark physical: every parcel leaves the sealing table under a **wax seal**, and every
grower's seal is distinct. Quality becomes a product of strain potency × environment fit ×
craft skill × station tier × (1 − cut ratio).

Seals matter more in the mod than labels did in the plugin: goods seized by the Watch keep
their seal, and the magistrate's clerk files it. The same seal turning up twice in the ledger
is how a case gets built against you — which makes an unsealed street sale cheap and quiet,
and a sealed parcel valuable and traceable.

### 5.5 Condition

Unchanged in spirit, more legible in practice. Intoxication, tolerance, dependence,
withdrawal, craving, overdose and recovery all keep the existing tuning as defaults
(tolerance decay 0.15/min, dependence decay 0.08/min after a 20-minute delay, rest bonus
×2.5, overdose threshold 95). What the mod adds:

- A proper HUD: an intoxication meter, a craving pip, a withdrawal timer. No more guessing.
- Client-side effects stay **opt-in** exactly as today: camera movement off, shaky aim off,
  nausea off by default. Darkness pulses on. This is a deliberate accessibility stance.
- Recovery gains a path with structure: a stay at the infirmary, remedy draughts, a rest
  bonus, and a
  multi-day "clean streak" that unlocks a permanent tolerance-ceiling reduction.
- **Tonic mode** (`no-vice`) reskins the whole condition layer into "tonic fatigue" with no
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

- **Emeralds are the money**, as they are in vanilla. Small sums in emeralds, large sums in
  emerald blocks (9). The plugin's abstract wallet becomes a real purse; the `$` figures in
  Appendix B are restated as emeralds in Appendix C.
- **Loose vs. stamped.** Street sales pay in **loose emeralds** — uncut, unmarked, no record.
  The market's tally clerk will **stamp** them into coin the guilds and the magistrate accept.
  Loose emeralds buy from brokers, fences and crews. Only stamped coin buys deeds, charters,
  bail, colony contracts and the retirement ending.
- **Laundering** is getting loose stone stamped, and it needs a front that plausibly takes
  coin all day: a **dye house**, a **mill**, a **ferry**, a **tavern**, a **market stall**, or
  (with MineColonies) a chartered colony business. Each has a daily throughput cap and takes a
  cut, and each is a liability the magistrate's clerk can audit.
- **Property** — deeds to buildings and turf cells, bought in stamped coin, giving passive
  effects: storage, a safehouse respawn, front throughput, quieter streets.

The split is what makes the spend side interesting: the money you earn is not the money that
buys the endgame, and converting between them is itself a system with a cost, a cap and a risk.

#### What money buys

Design rule: every sink must convert money into exactly one of four things — **time saved**,
**risk reduced**, **permanent progression**, or **status**. A sink that does none of those is
a tax and gets cut. Second rule: each tier needs at least one sink priced at 3–5× that tier's
session income, or wealth plateaus and the economy stops being a game.

**Loose emeralds — the street buys.** Fast, no questions, no record.

| Sink | Buys | Converts to |
| --- | --- | --- |
| The broker's stock | Rare seeds, stabilised lines, reagents, fillers, screens | Progression |
| Station kits | Upgrade tiers: capacity, speed, quality floor, smell damping | Time |
| Crew hiring | Signing purses, and the gifts that keep a crew from skimming | Time + risk |
| Hired bruisers | A one-off crew for a turf push or a night's defence | Risk |
| Bribes | Sergeant of the Watch (quarter suspicion damped), the bell-ringer (early warning of a raid), the magistrate's clerk (a seal lost from the ledger), a colony official | Risk |
| Word from the tavern | Watch rotas, the informant list, rival stash locations, a whisper that an inspection is coming | Risk |
| Tribute | War de-escalation, ceasefire, safe passage through a rival quarter | Risk |
| Passage | Boatmen's dock rights, undercroft digging, courier routes | Time |
| Stash | Lockboxes, hidden caches under the floor, a second safehouse | Progression |
| The gravedigger's discretion | Witnesses quieted and seized goods lost before they reach the ledger | Risk |
| **Writ of pardon** (forged) | One-time bounty wipe and a new name. Deliberately brutal pricing | Risk |

**Stamped coin — the legitimate buys.** Slow to get, and the only money the endgame accepts.

| Sink | Buys | Converts to |
| --- | --- | --- |
| Deeds | Buildings, turf-cell titles, glasshouse land | Permanence |
| Fronts | Buying and upgrading a front's daily throughput | Permanence |
| Repairs | Quarter condition — boarded windows opened, lanterns hung, streets that look lived in | Status |
| The speaker | An advocate on retainer, bail, an appeal before the magistrate | Risk |
| Infirmary | Treatment, remedy draughts, a long stay to break dependence | Risk |
| **Guild surety** | A bond that pays back a share of a seized stash after a raid | Risk |
| **Charters** | Market charter, a toll pass, a transport writ — fewer stops at the gate | Risk |
| Colony | Hut funding, research sponsorship, supply contracts (MineColonies) | Permanence |
| Apprenticeship | A master sells skill in Husbandry, Apothecary, Trade or Underworld | Permanence |
| Alms | Giving to the quarter: resident loyalty, thinner patrols, fewer informants | Status |

#### Upkeep — the sinks that never stop

Recurring costs are the only reliable answer to late-game wealth, and they double as narrative
pressure. Every dawn or every eighth day: crew wages, safehouse rent, a front operator's cut,
protection to whichever crew holds your cell, bribe retainers (a bribe **lapses** — it is not
a purchase), the guild surety's premium, colony contract fees, and lamp oil for the frames.
Miss a payment and the thing it bought turns on you: an unpaid runner skims, a lapsed bribe
becomes a tip-off, unpaid protection starts a war.

#### Vanity and legacy

Status sinks with no mechanical payoff are load-bearing in a game about getting rich: carved
furniture and painted murals, cutting your stabilised line into the market's tally board,
owning the tavern, hanging your banner over a turf cell, a wax seal design the whole quarter
recognises.

The terminal sink is **retirement**. Stamped coin left over at the ending becomes an endowment:
permanent world-level perks for your next character — a starting line of seed, a kept contact,
a standing bribe, a cell already paid for. Money that would be dead at the ending becomes the
next run's opening move.

### 5.8 Suspicion and the Watch

No police, no forensics, no paperwork Minecraft would not recognise. There is **the Watch**, a
**bailiff**, a **magistrate**, and a notice board. The plugin's heat system is the best-ported
piece in the codebase, so it stays the base layer with escalation above it. "Heat" remains the
name in code; in the fiction it is **suspicion**.

| Layer | Range | Behaviour |
| --- | --- | --- |
| Suspicion (personal) | 0–100 | As today: inspections, informants, intercepted deliveries, rival offers, a raid at 80 announced two hours ahead by the bell |
| Quarter suspicion | 0–100 per cell | Patrol density, gate stops, and whether the curfew bell rings at dusk |
| Bounty | 0–5 | Active pursuit, posted on the notice board; falls with distance, a hooded cloak, or a night in a safehouse |

What is added above the ported layer:

- **Evidence** is physical: a seized wax seal, a searched crate, a witness who talks. The
  magistrate's clerk files each one, and a case is built when the same seal appears twice.
- **Writs of search** let the bailiff enter property you own, legally, and dig.
- **The gaol** makes arrest a real state rather than a death: the stocks for a petty stop
  (short, humiliating, cheap), a cell with a timer for the rest, bail in stamped coin, an
  advocate who can shorten it, and confiscation of everything you were carrying.
- **Houndsmen** walk wolves that follow the smell of unsealed goods. Radius shrinks with wax
  sealing, charcoal screens and a strain's Subtlety.
- **Iron golems** answer the bell for a full raid. They are the reason you do not simply fight
  the Watch at tier 2.
- **Bribery** buys a sergeant, a bell-ringer or a clerk — each a person, each a liability if
  the Warden's inspection ever reaches them.

Design rule kept from the plugin: raids never destroy blocks and always warn first.

### 5.9 Crews and rivalry

The headline new system. Four powers, each built from vanilla mobs so they look like they
belong, each with a doctrine, a home quarter, a substance and a different way of hurting you.

| Crew | Made of | Doctrine | Wants | Pressure style |
| --- | --- | --- | --- | --- |
| **Ashfall Crew** | Vindicators and villager bruisers | Axes and extortion | Emberbloom, protection money | Enforcer squads, station sabotage |
| **Tidewater Boatmen** | Pillagers, crossbows, boats | Smuggling and volume | Dock rights, wholesale | Price wars, supply cut-offs, a word to the gate watch |
| **The Glass Choir** | Witches, vexes, an illusioner | Secrets and draughts | Glowcap, leverage | Blackmail, informants, spiked product |
| **The Quarry Kings** | Masked outcasts with picks and powder | Stone and tunnels | Sparkshard, the undercroft | Siege, collapsed tunnels, buried stashes |

**Standing** with each runs −100…+100 and moves on everything you do: selling in their quarter,
taking their contracts, hitting their runners, buying their product, backing a rival in a
dispute. Standing is zero-sum between opposed pairs — the Choir hates every point you gain
with Ashfall.

**Turf.** The quarter is a grid of cells (the plugin's `cell-size: 9`, `radius-cells: 3`
becomes the default 7×7). Every cell holds an influence vector across the crews and you.
Influence moves ≈ +0.4 per sale made in the cell, +2 per rival runner removed, +5 per
protection demand refused and survived, −1 per day of absence, −3 per rival event ignored.
A cell flips at 51% and consolidates at 75%. Control is visible: banners, lanterns, who is
standing on the corner.

**War.** Standing ≤ −60 opens a war, escalating through five stages — **banners painted over →
runner brawls → station sabotage → assault waves → siege of your safehouse** — each with a way
to step back: tribute, a hostage returned, a ceded cell, a public favour at the tavern. A war
that runs its course ends in a boss confrontation.

**Diplomacy.** Tribute, timed supply contracts, joint raids against a third crew, ceasefires
sworn in the tavern with a duration, and betrayal — a huge one-time gain and a permanent
standing floor.

**Player crews (multiplayer).** Players can swear a crew that shares turf, stash and standing.
Server config picks the stance: cooperative (one shared quarter), competitive (turf contested
between player crews, PvP only in contested cells), or free-for-all.

### 5.10 Bosses

Four staged encounters, each ending an arc and unlocking a tier. Each has a named entity built
from vanilla parts, a purpose-built arena, three phases, a non-lethal resolution, and loot that
changes how you play rather than what damage you do.

| # | Boss | Arena | Signature mechanics | Non-lethal out | Drops |
| --- | --- | --- | --- | --- | --- |
| 1 | **Kell the Collector** (Ashfall) | Burning warehouse | Axe slams, summons vindicators, grabs and throws, stacked crates as cover that burn away | Pay triple tribute mid-fight | Ashfall banner deed, an enforcer's oath, a fireproof crate |
| 2 | **Harbourmaster Vyne** (Boatmen) | Docks and drifting barges | Nets that root, winch and cargo hooks swinging overhead, a phase where the tide floods the arena | Deliver her rival's manifest | Smuggling charts, a bulk contract board, dock rights |
| 3 | **The Choirmaster** (Glass Choir) | A fugue arena built from your own quarter | Illusioner mirror-images that mimic your last actions, blinding fog that raises intoxication, hallucination adds only you can see | Answer three riddles out of your own journal | Line stabiliser, truth draught, the Choir's ledger (names every informant) |
| 4 | **Lord Warden Rade** (the Watch) | The keep's bell-tower under lantern light | Rings the bell to call watch waves, lantern glare that blinds, hound pairs, an iron golem as the heavy, and a **capture** move that takes you to the gaol instead of killing you | Hand over the Choir's ledger | The ledger purged, a standing bribe network, the retirement trigger |

Boss design rules: no sponge phases; every mechanic telegraphs; each fight is survivable at the
tier that unlocks it using that tier's own tools; death costs the stash you carried, never your
turf.

Optional hidden fifth: **The Quiet Partner**, the market itself — a lore encounter for players
who read the whole journal. Deliberately vague for now.

### 5.11 Your crew

Hire from the broker. Each has a job, a wage, a skill (0–100), a loyalty and a vice.

| Job | Does | Fails by |
| --- | --- | --- |
| Tender | Works the plots and frames on a schedule | Overwatering, letting the lantern go out |
| Apothecary | Runs racks, bench and alembic while you are away | Ruining batches at low skill |
| Runner | Carries standing orders, sells at a fixed margin | Getting taken, skimming |
| Lookout | Warns of patrols, damps quarter suspicion | Falling asleep at low loyalty |
| Bruiser | Holds stations and turf, escorts deliveries | Starting fights you did not want |

Crew cost is real — wages each dawn, in loose emeralds or stamped coin — and crew are a
liability: an underpaid runner skims, a pressured lookout turns informant, a captured member
becomes evidence. High-loyalty crew can be raised to lieutenant and hold a cell for you, which
is the mechanism that makes the retirement ending possible.

### 5.12 The quarter, property and the city

Two ways to get a quarter, and this is a deliberate fix for the plugin's weakest point:

1. **Natural generation (new default).** A rundown quarter generates as a structure in a
   configured biome and distance band — lodgings, workshops, a tavern, an infirmary, a
   warehouse and a market — so no existing player build or village is ever touched.
2. **Annexation (opt-in).** The existing village takeover, kept intact: journalled,
   restorable, player-edit aware, refuses to run without a snapshot, skips unsafe houses,
   preserves native villagers. Off by default in the mod.

The quarter keeps a **condition** score (starts at 25) that shifts with your investment and
the crews' hold, and now drives visible change: boarded windows opened, lanterns lit, painted
banners changing owner, who walks the street and when. Buildings become ownable property with
upgrade slots. An **undercroft** — old mine tunnels and cellars beneath the quarter — links
safehouses for quiet movement and doubles as tier-4 growing space away from every window.

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
you keep healthy becomes the best laundering front in the game: a **chartered business**
stamps loose emeralds into coin at a rate scaled by real colony production.

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

- **Items** ~70: seeds, raw, dried, product, sealed parcel, essence per substance (7 × 6),
  remedy draughts, composts, screens, reagents, fillers, deeds, charters, contracts, journal,
  line stabiliser, seized-goods sacks, wax seals, boss uniques.
- **Blocks** ~25: forcing frame (4 stages × lantern states), drying rack, pressing bench,
  sealing table, alembic, cutting board, grafting bench, storage crate, drop crate, safehouse
  door, front counter, planters, notice board, quarter decoration set.
- **Entities** ~14: customer, resident, watchman, healer, broker, trader, bruiser, houndsman
  with wolves, runner, lookout, hired muscle, hallucination, plus 4 bosses. Iron golems answer
  the bell; vindicators, pillagers, witches and an illusioner body the rival crews.
- **Structures**: rundown quarter (jigsaw, ~20 pieces), docks, watch house and gaol, undercroft
  tunnels, abandoned glasshouse, 4 boss arenas.
- **Screens** ~12: each station, journal (5 tabs), contracts, crew, turf map, infirmary, broker.
- **Audio** ~30 cues; **particles** ~8; **advancements** ~40; **i18n**: `en_us` + `de_de` at 1.0.


## 7. UI and UX

- **HUD** (client config, all individually toggleable): intoxication meter, craving pip,
  heat bar shown only above 0, wanted stars, active contract line.
- **Journal** — a written book, the mod's codex and quest log in one screen: Substances (with
  discovered strain data), Contacts (customers, loyalty, tells), Turf (influence map),
  Heat (case files, known evidence), Crew, Contracts, Factions.
- **Turf map** — a filled map on a cartography table, cells marked with each crew's banner,
  showing what moved since your last visit.
- **Station screens** — direct manipulation with plain readouts: the forcing frame shows
  warmth, damp and light and which one is out of band; the bench shows why a batch scored
  what it did.
  Design rule: the player should never have to consult a wiki to learn why quality dropped.
- **Notifications** — one channel, rate-limited, never a wall of chat. Urgent events (the
  raid bell, war escalation) use a toast plus a sound, not spam.
- **Look and feel** — parchment, wood, stone and wrought iron, lit by lantern. No glass
  panels, no glowing readouts, no sans-serif dashboards. Every screen should look like
  something a villager could have built.

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
| **M2 The Watch and your crew** | Evidence, writs of search, the gaol and bail, houndsmen, bounty, crew hiring and jobs | 3–4 wk |
| **M3 Crews and turf** | Rival crews, standing, influence grid, war escalation, diplomacy, player crews | 4–6 wk |
| **M4 Bosses** | 4 encounters, arenas, AI, loot, unlocks (art and animation heavy) | 3–5 wk |
| **M5 MineColonies** | `ColonyBridge`, market nodes, colonist customers, vice, requests, fronts | 2–4 wk |
| **M6 Release** | Strains and extraction polish, balance pass, advancements, `de_de`, docs, live playtest | 3–4 wk |
| | **Total** | **22–36 wk** |

Roughly 5–8 months full time, 12–18 months part time. Art is the hidden cost: ~120–200
textures and models, plus four bosses. That is a second skill set and probably a second
person, or a scope cut on visual ambition.

**Cut lines if time runs short**, in order: hidden fifth boss → strain breeding → the
undercroft → Create/Farmer's Delight compat → player-vs-player crews → extraction tier.
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
| Theme rejection by some servers | Reduced reach | Tonic mode and full toggles are first-class, not afterthoughts |
| Untested gameplay in the existing plugin | Unknown balance | M0 exit gate is a live playthrough, not a build |

---

## 13. Open questions

1. **Loader and MC version** — NeoForge for which target exactly? Everything else waits on it.
2. **GeckoLib or vanilla models** for bosses and NPCs? Affects dependency policy and art pipeline.
3. **One quarter or many?** The plugin supports one; the guild tier assumes several.
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
first live playtest. Money figures convert to emeralds per Appendix C — the plugin's `$10`
base price becomes about 2 e per Standard unit.

Demand cap 64 units · demand refill 2.0/min · rival drain 0.6/min · tolerance decay 0.15/min
· dependence decay 0.08/min after 20 min · rest bonus ×2.5 · craving after 15 min, every
4 min · overdose at 95 · raid threshold 80 with a two-hour bell warning · infirmary treatment
12 e · remedy draught 5 e · starting purse 10 e · quarter 9-block cells, radius 3 · crew
wages 1–3 e per dawn by job (new).

## Appendix C — Price ladder, in emeralds

Anchors, not balance decisions. Small sums in emeralds (e), large sums in emerald blocks
(1 block = 9 e). The rule is that a tier's headline sink costs 3–5× that tier's session
income, so something is always out of reach. One unit of Standard-quality product sells for
about 2 e, which is where the plugin's `base-price: 10` lands once money is emeralds.

| Tier | Session income | Headline sink | Price | Upkeep/day |
| --- | --- | --- | --- | --- |
| 0 | 3–8 e | Forcing frame kit | 24 e | — |
| 1 | 10–25 e | Drying rack + first named line | 60 e | 3 e |
| 2 | 30–70 e | Pressing bench, first hire, first cell | 250 e (28 blocks) | 12 e |
| 3 | 80–200 e | Alembic and safehouse | 800 e (89 blocks) | 45 e |
| 4 | 250–600 e | Chartered front, second quarter | 3,000 e (333 blocks) | 180 e |
| 5 | 800–2,000 e | Quarter control, bribe network | 12,000 e (1,333 blocks) | 700 e |
| — | — | Forged writ of pardon (bounty wipe) | 2,500 e loose | — |
| — | — | Retirement threshold (lifetime stamped) | 25,000 e | — |

## Appendix D — Terminology

The reskin table. Left is the modern crime-fiction term this document started with; right is
what ships. If a new idea has no right-hand column, it is not ready.

| Modern | In-world |
| --- | --- |
| Dollars / wallet | Emeralds / purse; emerald blocks for large sums |
| Dirty money | Loose emeralds — uncut, unmarked |
| Clean money | Stamped coin, assayed by the market's tally clerk |
| Money laundering | Getting loose stone stamped through a front |
| Laundromat / taxi rank | Dye house, mill, ferry, tavern, market stall |
| Police / precinct | The Watch / the watch house and gaol |
| Officer, detective | Watchman, bailiff |
| Police chief | The Lord Warden |
| Judge, court | Magistrate, the hearing |
| Lawyer | Speaker, an advocate on retainer |
| Search warrant | Writ of search |
| Case file, forensics | The magistrate's ledger of seized wax seals |
| Batch label | Wax seal, a grower's mark |
| K9 unit | Houndsman with wolves |
| Police radio, dispatcher | The bell tower and its ringer |
| SWAT / heavy response | Iron golems answering the bell |
| Arrest, jail, bail | Taken, the gaol or the stocks, bail in stamped coin |
| Wanted level | Bounty, posted on the notice board |
| Identity papers, ID reset | A forged writ of pardon |
| Insurance | Guild surety |
| Business licence, permit | Charter, toll pass, transport writ |
| Gang, syndicate, cartel | Crew, guild, the Boatmen, the Choir |
| Drug lab, chemist | Apothecary's bench and alembic |
| Extractor, concentrate | Alembic, essence |
| Hydroponics | Flood beds, irrigated terraces |
| Grow box, grow lamp | Forcing frame, lantern |
| Propagation table, genetics | Grafting bench, a line and its lineage |
| Clinic, rehab, medic | Infirmary, a long stay, the healer |
| Fixer | Broker, fence |
| Sewer network | The undercroft — old mine tunnels and cellars |
| Apartments | Lodgings |
| Burner phone, comms | A runner, a written book, the notice board |
| Skill tree | Apprenticeship under a master: Husbandry, Apothecary, Trade, Underworld |
