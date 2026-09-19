# SlumDrugs — Standalone Mod

**Game Design Document · draft v0.1 · 2026-09-18**

A design for taking SlumDrugs from a Paper plugin (v1.2.2) to a full mod: real blocks,
items, entities and screens, plus the systems a plugin cannot reach — rival crews fighting
over turf, named boss encounters, a hired crew of your own, and a MineColonies interface that
wires the quarter into a living colony economy. All of it stays inside Minecraft's own era:
coin, lanterns, bells, the Constabulary — no modern city wearing a Minecraft skin.

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

**Minecraft's own world, one notch into the industrial age: gaslight, not spaceships.**
Brick and brass, glass and soot, canals and warehouses, lanterns hung on chains, a constable's
whistle and a magistrate's writ. Steam, water and muscle are the power — a mill wheel, a press,
a pump — and at the top end the quarter's first **dynamos**, arc lamps humming over a workshop
that can afford them. That is the ceiling: current exists, computers do not. No engines with
brains, no screens, no radio, no firearms. The Constabulary carries crossbows and lanterns, and
the fastest message in the quarter is still a boy who runs.

Gaslight giving way to the first electric light is a real, narrow window in history, and it is
exactly the right one: it lets the mod have a power system (§5.16) that other tech mods can
plug into, without ever looking like science fiction.

Why this and not full medieval, decided 2026-09-19: Create — the mod this world would most
plausibly share a pack with once it ports — is early-industrial itself, brass and cogs and
steam, roughly the nineteenth century. MineColonies builds everything from village to colonial
town. Gaslight sits comfortably beside both *and* beside a vanilla village, which full steampunk
does not. It is also simply where this genre lives: the Watch, the magistrate, wax seals, the
gaol, a reward posted on a board — that is Victorian crime fiction, not medieval fantasy.

Where a modern crime-fiction idea is worth keeping, it wears a period costume instead of being
cut: insurance becomes a **guild surety**, a licence becomes a **charter**, forged identity
papers become a **writ of pardon**, a police radio becomes a **bell and a whistle**, a forensics
lab becomes a **magistrate's ledger of seized wax seals**. Redstone is the only technology and
it stays sparing — a bell wire, a trapped door, a lamp on a timer.

The test for any asset, name or mechanic: *would it look at home in a quarter lit by lanterns,
next to a vanilla village?* If not, it does not ship. Appendix D maps every term.

### Fantasy in one line

---

## 2. Platform and assumptions

| Item | Decision | Confidence |
| --- | --- | --- |
| Loader | NeoForge. Forge proper is not the live loader on modern versions | High |
| MC target | **26.3**, the same generation as the plugin. Decided 2026-09-19 | Decided |
| Java | 25, matching the 26.3 target and the plugin's toolchain | High |
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

**Decision (2026-09-19): target 26.3**, the same generation as the plugin. The reasoning is
that the mod should track the game rather than an ageing pack, and that one codebase generation
across plugin and mod is worth more than early access to other people's mods.

The cost is real and has to be designed around rather than wished away:

- **The mod ships alone.** No Create, no AE2, no MineColonies, no JEI, no Jade on 26.3 today.
  Every integration in §5.15 is therefore *additive only*: nothing in the critical path may
  assume another mod exists, and the mod must be a complete game with zero optional deps
  installed. That was already a design rule; on 26.3 it becomes the design rule.
- **The colony interface is designed now and built later** (§5.14). The `ColonyBridge`
  interface, the native demand nodes and the chartered fronts all ship; the MineColonies
  implementation waits for MineColonies.
- **No recipe viewer at launch.** Without JEI or EMI, the in-game journal has to carry the
  whole recipe and chain explanation itself. That raises §7's priority rather than lowering it.
- **The `sim` module keeps a 1.21.1 port cheap** if the ecosystem argument ever wins: only the
  platform layer is version-bound.

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
| 1 | Backroom | Forcing frame, drying loft, first regulars, journal | First 20 units sold |
| 2 | Workshop | Pressing bench, sealing press, storage crate, first crew hire, first turf cell | Standing 15 · 60 shillings banked |
| 3 | Apothecary | Alembic, grafting, wholesale contracts, crew standing, **Boss 1** | Tier-2 turf cell held 3 days |
| 4 | Guild | Second quarter, chartered fronts, laundering, colony contracts, war footing, **Boss 2–3** | Crew war won |
| 5 | Kingpin | Quarter control, broker network, **Boss 4**, retirement | 60% district influence |

The tiers map onto the settlement ladder in §5.12: you start as the only illicit thing in a
clean village, tier 2 wants a restless village to sell into, tier 3 needs a works town with a
foreman worth removing, tier 4 opens a quarter with real crews, and tier 5 is the Port. The
progression is geographic as much as economic — you climb by travelling, and by dragging quiet
places up the Vice ladder behind you.

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

Three new substances arrive at higher tiers as designed progression content, not as a
different kind of thing: **Nightvein** (cave vine, tier 3, high suspicion, high margin),
**Tidecap** (coastal fungus, tier 4, colony-facing, low potency but huge volume), and
**Hollowcap** (tier 4, the substance that opens the Hollow — see §5.17). Hollowcap is invented
whole, like the rest: it has no real-world counterpart and no real-world effects, and what it
does — slipping you sideways into a mirror of the quarter — is a game mechanic, not a
depiction of anything.

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

Existing chain — dry, press, seal — extended with two stages. Everything is hand- or
water-driven; if Create ever reaches this version, §5.15 adds mechanical variants of the
press and the drying loft rather than replacing them:

| Station | In | Out | Tier |
| --- | --- | --- | --- |
| Drying loft | Raw | Dried | 1 |
| Pressing bench | Dried + reagents | Product | 2 |
| Sealing press | Product | Wax-sealed parcel (n units) | 2 |
| **Alembic** | Product | Essence (½ volume, ×1.8 dose) | 3 |
| **Cutting bench** | Product + filler | More volume, lower quality, higher OD risk | 3 |

Cutting is the moral pressure valve: it is always the profitable choice and always the one
that makes customers sick, loses loyalty and raises overdose incidents. Design intent is
that a player who cuts everything gets rich fast and loses their regulars in two weeks.

### 5.4 Quality

Keep the current 0–100 quality with the grower's name and a batch mark on the item, but make
the mark physical: every parcel leaves the sealing press under a **wax seal**, and every
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

- **Money is money.** Not an abstract balance and not raw emeralds: minted **coin**, as items
  you can hold, drop, hide, lose and be robbed of. Three denominations — a copper **penny**, a
  silver **shilling** (12 pence), a gold **sovereign** (20 shillings) — so a purse has weight
  and a payoff has a shape. Every screen shows the normalised total, so nobody does arithmetic
  they did not ask for.
- **Emeralds are the raw metal.** Vanilla villagers still trade in them; the **counting house**
  exchanges them for coin at 1 emerald to 1 shilling, which is why the ladder in Appendix C
  reads the same either way.
- **Loose vs. stamped.** Street sales pay in **loose coin** — unmarked, no record, no questions.
  The counting house will **stamp** it, taking a cut, and stamped coin is the only money the
  guilds, the land agent and the magistrate accept. Loose coin buys from brokers, fences and
  crews; stamped coin buys deeds, charters, bail, colony contracts and the retirement ending.
- **Laundering** is getting loose coin stamped, and it needs a front that plausibly takes money
  all day: a **dye works**, a **mill**, a **canal ferry**, a **public house**, a **market
  stall**, or (when it lands) a chartered colony business. Each has a daily throughput cap and
  takes a cut, and each is a liability the magistrate's clerk can audit.
- **Property** — deeds to buildings and turf cells, bought in stamped coin, giving storage, a
  safehouse respawn, front throughput and quieter streets.

The split is what makes the spend side interesting: the money you earn is not the money that
buys the endgame, and converting between them is itself a system with a cost, a cap and a risk.

#### The shops

Money that only buys seeds is not money. The quarter runs a real retail layer, and most of it
has nothing to do with the trade you are in:

| Merchant | Sells |
| --- | --- |
| General store | Food, rope, oil, candles, cloth, everyday consumables |
| Ironmonger | Tools, nails, locks, chain, iron and brass stock |
| Timber yard & brickworks | Building materials by the stack, at a price that makes a renovation a real decision |
| Machine works | Dynamo parts, pump and press components, battery glass (§5.16) |
| Chandler | Lamp oil, arc-lamp carbons, fuel for the dynamo |
| Furniture maker | Carved and painted furniture for your rooms — pure vanity, deliberately good |
| Land agent | Deeds, leases, glasshouse plots, the paperwork of owning the place |
| Apothecary's supplier | Reagents, glassware, screens, remedy ingredients |
| Hiring hall | Labour by the day — crew, and casual hands for a build |
| The black market | What the others will not stock, at three times the price, for loose coin |

The point is that a rich player should be able to *build* something with money — a lit,
furnished, well-supplied quarter — and not only buy more inputs for the chain.

#### What money buys

Design rule: every sink must convert money into exactly one of four things — **time saved**,
**risk reduced**, **permanent progression**, or **status**. A sink that does none of those is
a tax and gets cut. Second rule: each tier needs at least one sink priced at 3–5× that tier's
session income, or wealth plateaus and the economy stops being a game.

**Loose coin — the street buys.** Fast, no questions, no record.

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

The headline new system. Four powers, and — this is the important design choice — **they are
made of villagers, not illagers.** Rival crews are the quarter's own people gone bad: custom
villager-derived entities with crew colours, not a hostile mob faction. That makes them
negotiable, corruptible and unsettling in a way a vindicator never is, and it means the
quarter's population and its gangs are the same population.

| Crew | Who they are | Doctrine | Wants | Pressure style |
| --- | --- | --- | --- | --- |
| **Ashfall Crew** | Foundry and furnace men | Muscle and extortion | Emberbloom, protection money | Enforcer squads, station sabotage |
| **Tidewater Boatmen** | Canal crews and warehousemen | Smuggling and volume | Dock rights, wholesale | Price wars, supply cut-offs, a word to the Constabulary |
| **The Glass Choir** | Glassworks hands and their mystics | Secrets and draughts | Glowcap, Hollowcap, leverage | Blackmail, informants, spiked product |
| **The Quarry Kings** | Quarrymen and tunnellers | Stone and the drains | Sparkshard, the undercroft | Siege, collapsed tunnels, buried stashes |

#### Aggression

Every crew member carries an **aggression** value, 0–100, and the whole system is legible
through behaviour rather than a bar over their head.

| Aggression | Behaviour | Tell |
| --- | --- | --- |
| 0–24 | Trades, talks, takes a bribe | Hands empty, goes about their business |
| 25–49 | Follows, warns you off, reports you | Turns to watch you, falls in behind |
| 50–74 | Demands tribute, shoves, blocks doorways | Hand on a cosh, crew closes in |
| 75–100 | Attacks on sight | Weapon drawn, whistles for the others |

Aggression rises with their crew's standing against you, how deep into their turf you are,
recent injuries you caused, undercutting their price in their own cell, and night; it falls
with distance, tribute, time and a shared drink in the public house. It is **per member and
per crew**: a single bruiser can be furious while his crew is merely wary, and a lieutenant's
mood spreads to the people around him.

Because they are villagers, three things follow that illager mobs could never give: you can
**buy** individuals (turn a member informant), you can **poach** them (hire a rival's runner),
and killing them has a social cost — the quarter remembers, and the dead man's crew inherits
his aggression.

Illagers exist only as the **outsiders**: mercenaries a crew hires when a war goes badly.
Vindicators and pillagers arriving in the quarter mean the conflict has stopped being local,
which is scarier for being rare. Iron golems remain the Constabulary's heavy.

**Standing** with each crew runs −100…+100 and moves on everything you do: selling in their
quarter, taking their contracts, hitting their runners, buying their product, backing a rival
in a dispute. Standing is zero-sum between opposed pairs — the Choir hates every point you gain
with Ashfall — and it feeds directly into the aggression of every member you meet.

**Turf.** The quarter is a grid of cells (the plugin's `cell-size: 9`, `radius-cells: 3`
becomes the default 7×7). Every cell holds an influence vector across the crews and you.
Influence moves ≈ +0.4 per sale made in the cell, +2 per rival runner removed, +5 per
protection demand refused and survived, −1 per day of absence, −3 per rival event ignored.
A cell flips at 51% and consolidates at 75%. Control is visible: banners, lit or dark lamps,
who is standing on the corner and how they stand.

**War.** Standing ≤ −60 opens a war, escalating through five stages — **signs painted over →
runner brawls → station sabotage → assault waves → siege of your safehouse** — each with a way
to step back: tribute, a hostage returned, a ceded cell, a public favour. A war that runs its
course ends in a boss confrontation.

**Diplomacy.** Tribute, timed supply contracts, joint raids against a third crew, ceasefires
sworn in the public house with a duration, and betrayal — a huge one-time gain and a permanent
standing floor.

**Player crews (multiplayer).** Players can swear a crew that shares turf, stash and standing.
Server config picks the stance: cooperative (one shared quarter), competitive (turf contested
between player crews, PvP only in contested cells), or free-for-all.

### 5.10 Bosses

**Leaders scale with the settlement, and not every place has one.** A clean village has nobody
to beat — at most a home grower who wants to be left alone. A restless village has a fence who
can be leaned on, not fought. A **works town** has a **foreman**: a lieutenant-grade fight,
procedurally named and equipped, one or two per world, repeatable across towns so the middle of
the game has real encounters without hand-authoring twenty of them. Only a **quarter** has a
crew boss, and only the **Port** has the Warden.

That turns the boss list into a *map* rather than a chain: you can see, from the settlement
archetype, what is waiting there and roughly how hard. And **taking a leader means inheriting
the operation** — their turf, their contacts, their protection income and their enemies — not
just clearing an encounter.

The four authored fights, each ending an arc and unlocking a tier. Each has a named entity built
from vanilla parts, a purpose-built arena, three phases, a non-lethal resolution, and loot that
changes how you play rather than what damage you do.

| # | Boss | Arena | Signature mechanics | Non-lethal out | Drops |
| --- | --- | --- | --- | --- | --- |
| 1 | **Kell the Collector** (Ashfall) | Burning warehouse | Axe slams, summons vindicators, grabs and throws, stacked crates as cover that burn away | Pay triple tribute mid-fight | Ashfall banner deed, an enforcer's oath, a fireproof crate |
| 2 | **Harbourmaster Vyne** (Boatmen) | Docks and drifting barges | Nets that root, winch and cargo hooks swinging overhead, a phase where the tide floods the arena | Deliver her rival's manifest | Smuggling charts, a bulk contract board, dock rights |
| 3 | **The Choirmaster** (Glass Choir) | The Hollow (§5.17), wearing your own quarter | Illusioner mirror-images that mimic your last actions, blinding fog that raises intoxication, hallucination adds only you can see | Answer three riddles out of your own journal | Line stabiliser, truth draught, the Choir's ledger (names every informant) |
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
| Apothecary | Runs the loft, press and alembic while you are away | Ruining batches at low skill |
| Runner | Carries standing orders, sells at a fixed margin | Getting taken, skimming |
| Lookout | Warns of patrols, damps quarter suspicion | Falling asleep at low loyalty |
| Bruiser | Holds stations and turf, escorts deliveries | Starting fights you did not want |

Crew cost is real — wages each dawn, in loose or stamped coin — and crew are a
liability: an underpaid runner skims, a pressured lookout turns informant, a captured member
becomes evidence. High-loyalty crew can be raised to lieutenant and hold a cell for you, which
is the mechanism that makes the retirement ending possible.

### 5.12 Settlements: the world map

Not one district. The world holds many settlements, and each sits somewhere on two scales:
**Vice** — how much illicit trade it carries — and **Industry** — how far into the gaslight age
it has come. Both move, and you are the main reason they move.

| Archetype | Vice | Who runs it | What is there |
| --- | --- | --- | --- |
| **Clean village** | 0–10 | nobody | An ordinary vanilla village. At most a single **home grower**: one NPC with one forcing frame in a cellar, who sells you seed and quietly buys a handful of units. No Watch, no turf, no trouble |
| **Restless village** | 10–35 | a fence | A broker with city ties, three or four regulars, one constable walking a round, the first suspicion that matters |
| **Works town** | 35–60 | a **foreman** | One crew, not four. A protection racket, a mill or foundry, the first real turf cells and the first watch house |
| **Quarter** (city district) | 60–85 | a **crew boss** | The full game: four crews, the turf grid, the Constabulary, a magistrate, a gaol |
| **The Port** | 85–100 | the **Warden** — and whatever sits above him | Endgame: several quarters, bonded warehouses, the assizes |

Distribution is seeded per world: many clean villages, a few restless, one or two works towns,
one quarter, one port. Distance from the world origin biases it upward, so the frontier stays
clean and the interior is rotten. Nothing is hand-placed; the archetype is derived from village
size, wealth, structure richness and the seed.

**You push settlements up the ladder.** Bring seed to a clean village, recruit its home grower,
sell there twice a week, and its Vice climbs: a fence appears, then regulars, then somebody from
a city crew arrives to see who is taking their margin. That is the answer to "where is the
content" — every village on the map is a potential campaign, and the map is not a fixed list of
hand-authored places.

**The Watch pushes them back down.** A settlement whose Vice outruns its Industry draws
attention from outside: funded patrols, a new watch house, a magistrate on circuit. Vice falls
under pressure, and an operation you stop defending decays on its own.

#### Industry — how the villages become gaslight

This is how the aesthetic reaches the world without vandalising it, and the rule from the plugin
holds absolutely: **never rewrite a player's build, only add, and journal every block.**

A settlement's **Industry** score rises when money goes into it — alms, renovations, a chartered
front, a funded workshop — and each threshold unlocks one *additive* structure piece placed at a
validated anchor and recorded for restoration:

| Industry | What appears |
| --- | --- |
| 15 | Gas lamps along the paths, a lit village at night |
| 30 | A chimney and bellows on the smithy; coal piles |
| 45 | A water wheel on the mill, or a pump house at the well |
| 60 | A brick works or foundry shed, brass fittings on doors and wells |
| 75 | A dynamo shed and the first arc lamp over the square (§5.16) |
| 90 | A canal cut or a rail spur linking the settlement to its neighbour |

So the world industrialises **because of your money**, which is exactly the historical truth the
setting is built on, and it means the gaslight look is earned rather than imposed on a village
that never asked for it. Newly generated works towns and quarters carry the style from birth; old
villages grow into it.

The tension that keeps it from being a pure reward: **Industry funds the Watch.** A prosperous,
well-lit settlement has constables who are paid, equipped and awake. Making a place better
makes it harder to work in — and that is a decision, not an accident.

Structure pieces ship as a datapack structure set, so a server that wants a medieval add-on set
instead of an industrial one swaps the pack and changes nothing else.

#### Property and the undercroft

Buildings in any settlement become ownable with stamped coin, with upgrade slots for storage, a
safehouse respawn, front throughput and quieter streets. Beneath the older settlements run **the
drains** — brick tunnels and cellars that link safehouses, move goods without passing a gate, and
double as growing space away from every window.

Two ways a quarter comes into being, unchanged:

1. **Natural generation (default).** A rundown quarter generates as a structure in a configured
   biome and distance band, so no existing player build is ever touched.
2. **Annexation (opt-in, off by default).** The plugin's village takeover, kept intact:
   journalled, restorable, player-edit aware, refuses to run without a snapshot, skips unsafe
   houses, preserves native villagers.

### 5.13 Routes, jobs and getaways

Once the world is a map of settlements, three loops fall out of it that are most of the moment
to moment fun.

**Routes and arbitrage.** Prices differ per settlement and per crew hold. A works town starved
of Emberbloom pays double; the Port pays half for anything it already floods with. Nothing tells
you the true number — the journal carries **rumours**, which are directionally right and
sometimes wrong. Moving goods is physical: a pack donkey with chests, a canal boat, a minecart
line, later a Create train if it ever ports. Every route has friction — gate tolls, an inspection
at a settlement boundary where your packaging tier decides everything, bandits, a rival ambush on
a road you use too often. Runners will walk a route for you, and will skim, and will get taken.

**Jobs.** A one-night structure with prep, execution and a getaway: rob a bonded warehouse,
intercept a shipment on the canal, burn a rival's drying loft, break a crew member out of the
gaol, pass a bribe to a magistrate on circuit. Prep is where the systems meet — buy tools from
the ironmonger, bribe the constable off the round, scout the layout in the Hollow the night
before, and hire hands from the hiring hall who each have a mouth and a price.

**The getaway.** Deliberately the best part. Once the whistle goes, the fight is not the point —
the exit is. Constables converge on foot with lanterns and are *slow but relentless*; hounds
track goods, not you, so dropping the sack is a real option; the drains, the canal and a
pre-bought safehouse are all shortcuts you had to arrange beforehand. A clean getaway with a
full sack is the best feeling the mod can produce, and everything else is arranged to make it
possible and rare.

**Two reputations, not one.** **Fear** and **Respect** track separately. Fear gets compliance —
lower prices, faster tribute, crews that yield turf — and decays into informants and a Watch that
wants you specifically. Respect gets loyalty — crew who stay bought, customers who wait, a
settlement that hides you — and it is slower to build and harder to spend. Most playstyles pick
one; the interesting builds run both.

**Market days.** Each settlement has a periodic market or festival: demand spikes, strangers are
normal, the Watch is thinner in the crowd and thicker at the gates. Everyone's schedule shifts.
It is the single best day to sell and the worst day to move volume through a gate.

**Rats.** Somebody is talking. The journal narrows it down from what got seized and when; the
Hollow shows the mark outright if you are willing to pay for the slip. What you do with a rat —
cut them off, feed them false information, or something worse — moves Fear and Respect in
opposite directions.

### 5.14 MineColonies interface — designed now, built when it lands

The 26.3 decision falls due here: MineColonies has no 26.x build, so this integration cannot
ship with the mod. What ships is the shape of it, and a native path good enough that nobody
feels they are playing a stub.

**Interface shape, built now.** A thin `ColonyBridge` service interface in core with a no-op
default. Everything a colony could provide — demand nodes, customer archetypes, bulk supply,
a chartered laundering front, a hostile response — is expressed as bridge calls that the
mod's own quarter answers by default. When MineColonies ports, a compat module
(`slumdrugs-minecolonies`) implements the same interface against its API (colony manager,
colony, citizen data, request manager, raid events — **the exact surface to be verified
against whatever release lands on 26.x**). An API break disables the integration rather than
crashing the mod.

**The native path is primary, not a fallback.** The quarter's own customers, demand nodes and
chartered fronts are the real game. A colony, when one exists, makes that game bigger — it
never becomes the only way to play it.

When it does land, the design is:

**Colony as a market node.** Every colony within range becomes a demand node with its own
price curve, absorption cap and policy — *clean*, *ambivalent* or *corrupt* — derived from its
happiness, guard level and any research the player has bribed through. Corrupt colonies pay
more and ask fewer questions.

**Colonists as customers.** A subset of citizens are mapped, via a datapack JSON table from
colony job → customer archetype, into real customer profiles with the colony's schedule. You
sell at the gates at night, through a runner contract, or via a colonist middleman.

**Consequence.** Sales into a colony accumulate a **vice** stat: happiness drops, work output
drops, crime rises, guards patrol further out, and eventually the colony sends a raid at your
quarter. Selling remedy draughts to the healer's hut, or funding a hut with stamped coin,
walks vice back down — the legitimate path is slower and always available.

**Supply, both directions.** Buy bulk ingredients through the colony request system; sell
legitimate goods for standing. A colony you keep healthy becomes the best laundering front in
the game: a **chartered business** stamps loose coin at a rate scaled by real colony output.

### 5.15 Other compatibility

None of these exist on 26.3 yet, so all of them are additive and none may sit in the critical
path. The column that matters is the last one: what the mod does while the other mod is absent.

| Mod | Integration | Without it |
| --- | --- | --- |
| JEI / EMI | Station recipes, grafting, processing chains | The journal carries the full chain itself (§7) — mandatory, not a nicety |
| Jade / WTHIT | Station status, plot readout, growth timer | Right-click readout on the block |
| Any FE energy mod | **Power in and out through the standard energy capability** (§5.16) | The mod's own dynamo and storage cover the whole tier |
| AE2 | Stash and crate contents exposed as a storage network; power in | Storage crates and the ledger |
| Extreme Reactors / big power | Drop-in replacement for the dynamo at scale | The dynamo bank, which is deliberately annoying at scale |
| Create | Mechanical press, drying loft and pump as an alternate tier-3 path | Hand and water-driven versions of all three |
| FTB Teams / claims | Turf cells respect claims; contested-cell PvP rules | The mod's own crew membership |
| Farmer's Delight | Shared crop idioms, cooking overlap for remedies | — |

### 5.16 Power

A small, honest power system whose main design job is to **be replaceable**.

- **Unit and interface.** Energy is the platform's standard energy capability (Forge Energy /
  `IEnergyStorage` as NeoForge exposes it), not a private unit. That single decision is the
  whole "Schnittstelle": any mod that speaks FE — AE2, Extreme Reactors, Create's addons, a
  generator mod nobody has written yet — powers SlumDrugs machines the day it ports, with no
  compat module and no code from us. Our machines accept FE from anywhere; our dynamo exposes
  FE to anything.
- **The dynamo.** A coal- or water-driven generator, period-correct and deliberately modest:
  loud, thirsty, needs fuel hauled to it, and scales badly on purpose. A player who wants a
  real grid is *supposed* to want a better generator — and when Extreme Reactors or AE2 lands,
  that want is satisfied by another mod rather than by power creep in ours.
- **What power buys.** Nothing that gates the game. Arc lamps for the forcing frames (faster,
  steadier growth than a lantern), a powered press and still (throughput, not new recipes), a
  pump for the flood beds, a ventilation fan that damps smell, and lit safehouse security. Every
  one of these has a hand- or water-driven version that works forever at lower throughput.
- **Not a tech tree.** No machine tiers beyond powered/unpowered, no cables-as-puzzle, no
  automation minigame. This mod is about the chain and the quarter; power makes the chain
  faster, and that is all it is allowed to do.
- **Storage** is a battery bank in brass and glass, FE-exposed, so a Create or AE2 grid can
  charge it and draw from it later.

### 5.17 The Hollow

The mod's one piece of outright strangeness, and its tier-4 turn. **Hollowcap** taken at a
threshold dose does not give an effect — it moves you. You slip sideways into **the Hollow**: the
same quarter, the same streets, the wrong details. No people. Lamps lit that are dark outside.
Water running uphill in the drains. Things that remember being people.

**The tether.** The dose buys you a timer, shown as a fraying line, and when it runs out you
snap back to where you left. There is no way to be trapped — that is a hard design rule, not a
tuning value. What there is, is a cost: **your body stays behind.** It stands where you left it,
breathing, worth robbing. Slipping in the middle of the street is how players learn to slip in a
locked room.

**What it is for.** The Hollow is where the quarter tells you the truth:

- Caches, buried stashes and the real shape of the drains are simply *visible* there.
- Informants and marked men carry a sign you cannot see outside.
- **Echoes** — the dead, the taken, the ones who never left — will talk, in riddles, for a price.
  The Choirmaster's fight (§5.10, Boss 3) happens here; the fugue arena *is* the Hollow, which is
  why it is built out of your own quarter.
- Hollow-only materials — mirror glass, shadow silt — feed the endgame recipes and nothing else.

**The cost, and the honesty about it.** Every slip spikes dependence and tolerance hard, and
repeated slipping **thins** you: at high thinning, echoes start showing up in the real quarter
(this reuses the plugin's existing hallucination system rather than inventing a second one), and
past a threshold something in the Hollow starts hunting rather than watching. Thinning recovers
with clean time, like everything else in §5.5.

**Build in two phases.** v1 is an overlay: same dimension, altered rendering, living entities
hidden, echoes spawned, other players invisible unless they are also under. v2 is a real
dimension that mirrors the quarter's structure. v1 is most of the feeling for a fraction of the
work, and it ships first.

**Togglable, like everything else.** `hollow.enabled: false` removes Hollowcap and the whole
subsystem; the substance is designed so nothing else depends on it, and tonic mode disables it
outright.

### 5.18 NPC dialogue

Villagers that answer a typed question in character. Three tiers, and the mod ships the first
two — the third is an opt-in server module, for a reason given at the end.

**Tier 1 — procedural, no model.** Authored fragments assembled from state: who they are, their
mood and aggression (§5.9), what they know, what happened in this settlement lately. Free,
offline, instant, deterministic, moddable. This is the floor every NPC always has, and it is
also the fallback whenever a higher tier is unavailable or times out.

**Tier 2 — written by a model at build time, shipped as data.** Generate thousands of lines
ahead of release, keyed by archetype × mood × topic × settlement tier, review them, bake them
into datapack JSON. Zero runtime cost, zero latency, no key, no privacy question, and every line
has been read by a human before it ships. **This is where most of the lore feeling actually
comes from**, and it should carry ~90% of interactions.

**Tier 3 — live model, opt-in.** The player types a question, the NPC answers it. Architecture:

- **Server-side only.** The key lives in the server config, never in a client. The client sends
  the typed question as a packet; the server calls the API; the reply comes back as a dialogue
  packet.
- **Never on the game thread.** Bounded executor, `CompletableFuture`, the NPC shows a "…" while
  it thinks, hard timeout at ~6 s falling back to tier 1. A blocked tick is a dead server.
- **Prompt layout built for caching**, and the order matters: a large shared **world bible**
  (setting, tone, what an NPC may never say, glossary) first — identical for every NPC and every
  player, so one cache entry serves the whole server — then the per-NPC **character card**, then
  volatile state and the last few exchanges, then the question. Only the tail changes per call.
- **Facts come from the mod, not the model.** The prompt carries a structured fact list (prices
  this NPC knows, who they have seen, their standing with the player) and the instruction to
  answer only from it and to say *"I wouldn't know"* otherwise. The model phrases; it does not
  invent world state. That is what keeps lore consistent across a server.
- **Memory**: a rolling summary per NPC per player in the NPC's data attachment, capped.
- **Limits**: per-player cooldown, per-server hourly cap, hard monthly budget, an admin kill
  switch, and every exchange logged for moderation.

#### Cost, measured rather than guessed

A turn is roughly 2,500 cached tokens (bible + card), 500 volatile, 120 out. Cache reads cost
~0.1× input; the 5-minute TTL stays warm by itself on a populated server.

| Model | $/MTok in / out | Per reply | 10 players × 4 h evening¹ | Note |
| --- | --- | --- | --- | --- |
| Claude Opus 5 | 5 / 25 | ~$0.0068 | ~$5.50 | Best writing; use `effort: "low"` for latency rather than disabling thinking |
| Claude Sonnet 5 | 2 / 10 | ~$0.0027 | ~$2.20 | The middle option |
| Claude Haiku 4.5 | 1 / 5 | ~$0.0016 | ~$1.30 | Fastest and cheapest — **but its minimum cacheable prefix is 4,096 tokens**, so the world bible must be genuinely large or caching silently does nothing |

¹ at ~20 NPC questions per player-hour, which is generous.

The Haiku caching minimum is the non-obvious trap: 512 tokens on Opus 5, 4,096 on Haiku 4.5. A
2,500-token prefix caches on Opus and silently does not on Haiku — same code, no error, just
`cache_creation_input_tokens: 0` and a bill that never drops. Either write a fat bible, or check
`usage.cache_read_input_tokens` and notice.

The model is the server owner's choice: the config takes a model id and the docs state the
tradeoff.

#### Safety, which for this mod is not optional

Players will try to make an NPC say something ugly, and — given the subject matter — will try to
get a dealer NPC to hand out real-world drug or chemistry information. The mod's whole stance
(§10) is that everything is invented, so: the world bible forbids real substances, chemistry and
procedures explicitly and tells the NPC to deflect *in character*; an output filter runs over
every reply before a player sees it; player text is untrusted input and the NPC has **no tools**,
so a successful injection can only make it talk nonsense, not act; everything is logged and
visible to admins; and tier 3 is **off by default**, accepted consciously per server.

#### Voice

Bake it, do not stream it. TTS for a few dozen lines per named character — the broker, the home
grower, the four bosses — generated at build time and shipped as `.ogg` in the jar. That is most
of the effect for about a week of work and no runtime cost. Live TTS needs an audio path
Minecraft does not hand you: a client-side component receiving audio over a packet, or a
dependency on a voice-chat mod's API, plus 1–3 s of added latency. Not for v1.

#### Effort

| Piece | Assisted |
| --- | --- |
| Tier 1 procedural dialogue | 2–4 days |
| Tier 2 baked, generated and reviewed | 2–4 days |
| Tier 3 live, done properly (async, caching, memory, limits, filter, admin, fallback) | 1–2 weeks |
| Baked voice for named characters | 3–5 days + TTS generation |
| Live voice | +1–2 weeks and a hard dependency — deferred |

#### The rule this breaks

§14 says the mod never requires an external server or account. Tier 3 does. That is why it is a
**separate optional module** with its own toggle rather than a core feature: the mod stays
complete, offline and free on tiers 1 and 2 alone, and a server that never sets a key should not
be able to tell that tier 3 exists.

## 6. Content manifest (target for 1.0)

- **Items** ~85: seeds, raw, dried, product, sealed parcel, essence per substance (8 × 6),
  **penny / shilling / sovereign**, remedy draughts, composts, screens, reagents, fillers,
  deeds, charters, contracts, journal, line stabiliser, seized-goods sacks, wax seals,
  dynamo and pump parts, arc-lamp carbons, mirror glass and shadow silt, boss uniques.
- **Blocks** ~25: forcing frame (4 stages × lantern states), drying loft, pressing bench,
  sealing press, alembic, cutting bench, grafting bench, storage crate, drop crate, safehouse
  door, front counter, planters, notice board, **dynamo, battery bank, arc lamp, pump**,
  counting-house desk, shop counters, quarter decoration set.
- **Entities** ~22: customer, resident, constable, healer, broker, ten merchant villagers
  (§5.7), **home grower** (the one NPC a clean village gets), houndsman with wolves, **crew
  member per crew with the aggression model (§5.9)**, runner, lookout, bruiser, hallucination,
  **echo** (the Hollow), plus the procedural **foreman** and 4 authored bosses. Iron golems
  answer the bell; illagers appear only as hired outsiders.
- **Structures**: rundown quarter (jigsaw, ~20 pieces), works town, docks, watch house and gaol,
  the drains, abandoned glasshouse, 4 boss arenas, and the **industry add-on set** (§5.12): gas
  lamps, smithy chimney, water wheel, pump house, foundry shed, dynamo shed, canal cut and rail
  spur, each placed additively at a validated anchor and journalled.
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

Two columns, because the first draft's numbers assumed one person typing everything and that
is not how this is being built. **Solo** is a developer writing it by hand. **Assisted** is the
same developer with Claude and ChatGPT writing most of the code. Focused weeks in both cases;
multiply by 2.5–3 for an evenings-and-weekends pace.

| Milestone | Scope | Solo | Assisted |
| --- | --- | --- | --- |
| **M0 Parity port** | Everything in 1.2.2 running as a mod: items, stations, farm, NPCs, suspicion, quarter, commands, migration importer | 4–8 wk | **1–2 wk** |
| **M1 Native content** | Models and blocks, screens, HUD, journal (carrying the whole recipe chain, since no JEI), natural quarter generation, coinage and the shops | 4–6 wk | **2–3 wk** |
| **M2 The Watch and your crew** | Evidence, writs of search, the gaol and bail, houndsmen, bounty, crew hiring and jobs | 3–4 wk | **1–1.5 wk** |
| **M3 Crews and turf** | Rival crews with the aggression model, standing, influence grid, war escalation, diplomacy, player crews | 4–6 wk | **1.5–2.5 wk** |
| **M3b Settlements and routes** | Vice and Industry per settlement, five archetypes, the industry add-on set, price spread, routes, inspections, jobs and getaways | 3–5 wk | **1.5–2.5 wk** |
| **M4 Bosses** | 4 encounters, arenas, AI, loot, unlocks | 3–5 wk | **2–3.5 wk** |
| **M5 Power and the bridge** | FE capability in and out, dynamo, battery, arc lamps, pump; `ColonyBridge` and the native demand nodes | 2–3 wk | **1 wk** |
| **M5b The Hollow** | Hollowcap, the tether, overlay rendering, echoes, Hollow materials, Boss 3 moved inside it | 2–3 wk | **1–2 wk** |
| **M6 Release** | Balance pass, advancements, `de_de`, docs, **live playtest** | 3–4 wk | **3–4 wk** |
| | **Total** | **30–48 wk** | **14–22 wk** |

### What compresses and what does not

Writing code compresses hard — roughly 60–75% off. Registry boilerplate, datagen, JSON content,
the port of 7,070 LOC of plugin logic, screens, packets: that is exactly the work a model does
well, and M0 in particular collapses from two months to about a fortnight.

Four things refuse to compress, and together they are most of the remaining number:

1. **Art.** 120–200 textures, block models, four bosses. Generated textures do not come out
   Minecraft-coherent, and Blockbench models still want a person. This stays the hidden cost and
   is the strongest argument for cutting visual ambition rather than systems.
2. **Playtesting and balance.** Nothing makes "play it for twenty hours and feel whether tier 3
   drags" faster. It is wall-clock, it needs a human, and it is the one thing this project has
   never done — the plugin shipped 1.2.2 with no live gameplay test at all.
3. **The build–run–observe loop.** Desync, chunk-load edges, dimension quirks, save migration.
   A model shortens each fix; it does not remove the serial launch-and-look cycle.
4. **26.3 itself.** The version is days old. There is little or no NeoForge 26.x API material in
   any current model's training data, so assistance is *weaker* here than it would be on 1.21.1 —
   expect more time reading actual NeoForge sources and more wrong first attempts than on a
   mature version. This is a real cost of the 26.3 decision, on top of shipping alone (§2).

**The risk that comes with going fast.** Assisted, this project produces perhaps 20,000 lines of
code that no one has ever run, on top of 7,070 that were never live-tested. That makes the
`sim` boundary and the regression harness (§8, §9) more important, not less: they are the only
things that scale with generated code. Budget the M6 playtest in full even when everything
before it came in early — especially then.

**Cut lines if time runs short**, in order: hidden fifth boss → the Hollow's v2 dimension →
strain breeding → the undercroft → Create/Farmer's Delight compat → player-vs-player crews →
extraction tier. Never cut: journalled reversibility, config toggles, the `sim` boundary, the
live playtest.

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
| Shipping alone on 26.3 — no Create, AE2, MineColonies or JEI | Smaller initial audience; the journal must do JEI's job | Standard energy capability and `ColonyBridge` mean each mod plugs in the day it ports, with no rework |

---

## 13. Open questions

**Resolved so far.** Loader and version: NeoForge on **26.3**, decided 2026-09-19 with the
ecosystem check in §2 on the table. Art direction: **gaslight / early industrial**. Rival crews:
**villagers with an aggression model**, not illager mobs. Money: **minted coin**, not an
abstract balance. Power: **the platform's standard energy capability**, so other mods plug in
without a compat module. MineColonies: optional, designed now, built when it ports.

Still open:

1. **GeckoLib or vanilla models** for bosses and crew? Affects dependency policy and art
   pipeline — and on 26.3 it also means waiting for GeckoLib to port.
2. **One quarter or many?** The plugin supports one; the guild tier assumes several.
3. **PvP stance** — cooperative, competitive turf, or server-configurable (my recommendation)?
4. **Does the Hollow become a real dimension** (v2), or does the overlay carry it for good? Ship
   v1 and decide with play data.
5. **Coin denominations** — 12 pence to the shilling and 20 to the sovereign is period-correct
   and slightly awkward; a flat 10/10 is duller and easier. Recommendation: keep 12/20, since
   every screen normalises anyway.
6. **Keep village annexation at all**, now that natural generation exists? Recommendation: keep,
   default off, as a documented power tool.
7. **Language** — `de_de` as a first-class release language alongside `en_us`?
8. **Plugin's future** — does the Paper plugin keep receiving features in parallel, or freeze at
   1.2.2 as a maintenance branch once the mod starts?

---

## 14. Explicitly out of scope

Real-world substances or chemistry of any kind · a tech tree beyond the processing chain ·
vehicles · real-money transactions · automatic world edits outside a journalled, restorable
operation.

Two carve-outs, both narrow. **Voice**: no voice acting, but baked TTS lines for named
characters are allowed (§5.18). **External services**: the core mod requires none and must stay
complete without one — the live-dialogue module (§5.18, tier 3) is the single exception, is
optional, off by default, and ships as its own jar.

---

## Appendix A — Naming

- Mod id: `slumdrugs`. Root package: `dev.lucas.slumdrugs`.
- Registry paths are lowercase and snake_case: `slumdrugs:dried_sunleaf`,
  `slumdrugs:processing_bench`, `slumdrugs:customer`.
- Factions, bosses and strains are invented proper nouns and never reference real people,
  organisations or places.

## Appendix B — Starting balance figures

Carried from the current `config.yml` as defaults for the mod, to be re-tuned after the
first live playtest. Money figures convert to coin per Appendix C — the plugin's `$10` base
price becomes about 2 shillings per Standard unit.

Demand cap 64 units · demand refill 2.0/min · rival drain 0.6/min · tolerance decay 0.15/min
· dependence decay 0.08/min after 20 min · rest bonus ×2.5 · craving after 15 min, every
4 min · overdose at 95 · raid threshold 80 with a two-hour bell warning · dispensary treatment
12s · remedy draught 5s · starting purse 10s · quarter 9-block cells, radius 3 · crew wages
1–3s per dawn by job (new).

## Appendix C — Price ladder, in coin

Anchors, not balance decisions. Figures are **shillings (s)**; the counting house exchanges
1 emerald for 1 shilling, so the ladder reads the same in either currency. The rule is that a
tier's headline sink costs 3–5× that tier's session income, so something is always out of
reach. One unit of Standard-quality product sells for about 2s, which is where the plugin's
`base-price: 10` lands once money is coin.

| Tier | Session income | Headline sink | Price | Upkeep/day |
| --- | --- | --- | --- | --- |
| 0 | 3–8 s | Forcing frame kit | 24 s | — |
| 1 | 10–25 s | Drying loft + first named line | 60 s | 3 s |
| 2 | 30–70 s | Pressing bench, first hire, first cell | 250 s (12 sovereigns) | 12 s |
| 3 | 80–200 s | Alembic and safehouse | 800 s (40 sovereigns) | 45 s |
| 4 | 250–600 s | Chartered front, second quarter | 3,000 s (150 sovereigns) | 180 s |
| 5 | 800–2,000 s | Quarter control, bribe network | 12,000 s (600 sovereigns) | 700 s |
| — | — | Forged writ of pardon (bounty wipe) | 2,500 s loose (125 sovereigns) | — |
| — | — | Retirement threshold (lifetime stamped) | 25,000 s (1,250 sovereigns) | — |

## Appendix D — Terminology

The reskin table. Left is the modern crime-fiction term this document started with; right is
what ships. If a new idea has no right-hand column, it is not ready.

| Modern | In-world |
| --- | --- |
| Dollars / wallet | Minted coin in a purse: penny, shilling, sovereign |
| Dirty money | Loose coin — unmarked, unstamped |
| Clean money | Stamped coin, struck at the counting house |
| Money laundering | Getting loose coin stamped through a front |
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
| Bank account, balance | Coin in your purse: penny, shilling, sovereign; the counting house |
| Power grid, generator | Dynamo, battery bank, arc lamp — gaslight's successor, not a reactor |
| Electronics, computers, screens | Do not exist. Current exists; intelligence in machines does not |
| Gang of hostile mobs | Villagers of the quarter with an aggression model (§5.9) |
| Salvia-style dissociative | **Hollowcap** and the Hollow (§5.17) — invented whole, no real-world counterpart |
| Shopping menu | Ten merchants with counters, stock and opening hours (§5.7) |
