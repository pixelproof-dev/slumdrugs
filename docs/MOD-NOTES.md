# SlumDrugs — mod notes

Build details, the command reference, and what 26.3 changed.

## Layout

```
sim/        the simulation: no Minecraft, no loader, no I/O
neoforge/   the platform layer
```

`sim` holds the rules: quality curves, recovery, package splitting, growth, demand, the
bounded room flood used by the takeover safety checks. It has **no dependencies at all**,
which is the point — it compiles and verifies in about a second without a game, and it is
identical whichever mod loader the platform layer ends up using.

Anything that needs a `Level`, an `ItemStack`, a config file or a network packet belongs in
the platform layer, not here.

## Build

```
./gradlew check
```

Runs `SimChecks`, a plain `main()` with no test framework, currently 93,068 assertions.
A failure prints the label of the rule that broke.

## Status

Both modules are live and the build is green. The mod has run in a local dev client, where
the trader house places by command and the stations are usable. The village wiring for the
trader house exists only locally so far and is not yet in this repository.

## Platform layer (NeoForge)

Decided 2026-09-19: NeoForge, pinned to `26.3.0.6-beta` — the only 26.3 builds that exist.
Built with ModDevGradle 2.0.147 on Gradle 9.1.0 and Java 25, all of which 26.x requires.

Fabric was the alternative and had the better numbers on availability alone (stable 26.3
loader and API today, against NeoForge betas). It lost on integration: MineColonies is
officially NeoForge-only, which would have deleted §5.13 of the design permanently rather
than deferring it.

```
./gradlew build            # sim checks + the mod jar
./gradlew :neoforge:runClient   # launches a dev client (not yet run in CI)
```

The first slice registers the 21 items that already had artwork in the plugin — seeds, raw,
dried, product and sealed parcels for each substance, plus compost and remedy — as real
registry entries with models, a creative tab, and `en_us` plus `de_de` translations.

Then five station blocks: forcing frame, drying loft, pressing bench, sealing press and
storage crate, and later the centrifuge. Their models are composed from vanilla textures, so
they look like something without needing new art. The forcing frame was the first with
behaviour: plant a seed, watch
five visible stages, harvest by hand. Its block entity holds `GrowboxState` from `sim` and
does nothing itself — world clock in, block state out, NBT both ways. That is the whole
architecture in one class, and it means the growth rules stay covered by the assertions.

### Stations

Every station is worked by hand with right-clicks, except the two with a screen. Items keep
their quality and grower through the whole chain; the tooltip shows both.

| Station | Put in | Work it | Take out |
| --- | --- | --- | --- |
| Forcing frame | A seed; compost while it grows | Wait; the block shows five stages | Empty hand when ripe: raw harvest and seed |
| Drying loft | Raw harvest, up to 8 units on each of three rails | Wait 90 s; the rails show what hangs | Empty hand: every dried bundle. Sneak: everything, raw if unready |
| Pressing bench | Dried material, one batch of up to 16 | Empty hand pulls the screw; five pulls press the batch | The last pull drops the product. Sneak: the batch back |
| Sealing press | Product on the bench, honeycomb in the pot | Empty hand pulls the lever once per parcel | A parcel of 8 units under the puller's seal. Sneak: everything back |
| Centrifuge | Dried in the vessels, charcoal on top, coal at the side | Runs on its own; the screen shows progress | Product in the vessels, three quarters of it, stronger |
| Still | Dried in the vessels, sugar on top, coal at the side | Runs on its own; the same screen | Product in the vessels, half of it, strongest of all |
| Cutting bench | Product on the board, sugar or bone meal on the heap up to equal parts | Empty hand pulls the blade | More product, worse, marked with how much is filler. Sneak: everything back |
| Grafting bench | Two seeds of one plant, one in each pot | Empty hand makes the cross | One seed near the parents' mean; both parents spent. Sneak: the parents back |
| Counting house desk | Emeralds, or loose coin | — | Stamped shillings one to one for emeralds; stamped coin less a tenth for loose. Sneak with a coin: change |
| Storage crate | Anything | — | The chest screen, 27 slots |

A parcel is used like any item to break the seal and get its 8 units back. A bundle left on
the loft long past done slowly loses quality, and none of the stations tick except the
centrifuge and the still; the loft and the presses only move when someone moves them. Breaking any station
drops what it held. The rules behind them are `Drying`, `Sealing`, `Refining` and `Cutting` in `sim`.

Cutting is the design's moral pressure valve. A cut batch carries a `cut` component, 0-1,
that survives sealing and opening; the tooltip shows it in red. Cut goods sell for their
lower quality, draw suspicion as if there were twice as many of them because sick customers
talk, and tip a user into overdose sooner: the overdose line comes down by up to forty
percent with the cut. Customer loyalty, which the design has cutting cost most, waits on
customers having loyalty at all. The design gates cutting at the Apothecary; that tier is not
reachable yet, so the Workshop has it.

Every station has a crafting recipe under `data/slumdrugs/recipe/`: wood, glass and string
for the frame and the loft, iron and copper on wood for the workshop pieces. The centrifuge
and the still share `RefineryBlockEntity` and the brewing-stand menu; each supplies its
method and its reagent. The design's alembic makes essence; until essence exists as an item
the still makes product at what the DISTIL rule says it is worth.

### Climate

The frame's environment is light and climate together. Warmth comes from the biome's base
temperature plus anything burning within two blocks (lava, fire and magma most, lit furnaces
and campfires less, torches barely; ice and snow cool). Damp comes from water within two
blocks, rain on the frame, and whether the biome rains at all. Each grown substance has a
comfort band in `Substances`: sunleaf temperate, frostroot cold and wet, emberbloom hot and
dry. `Cultivation.climateFit` scores the distance outside the band and bottoms out at 0.4, so
a frame in the wrong place is poor, never dead. `/slum frame info` prints all three numbers.

### Economy

Money is items: a copper penny, a silver shilling of twelve, a gold sovereign of twenty
shillings. `Coin` in `sim` splits sums and takes the counting house's tenth; `Purse` in the
platform layer makes stacks, pays players, and builds the merchant screen's costs and
results. Every price runs in pence at the design's scale: a Standard unit at the plugin's
base price of ten is about two shillings. Coin is loose until the counting house stamps it,
which is a component; the design's stamped-only sinks (deeds, charters, bail) do not exist
yet, so today the stamp is a mark and a tenth. A new player is handed ten shillings loose on
their first day. Merchants ask in shillings and sovereigns, rounded up to the shilling, and
pay in one stack of the largest denomination that fits, so a big parcel loses a little to the
broker's rounding. The three coin items are named after the art prompts; their textures are
placeholders the drawn ones overwrite.

The merchant screen cannot read quality, so customers do not use it. A player clicks a
customer with product in hand and sells a handful of four, paid by the batch's quality and
by demand; each customer wants one substance, fixed per person. Brokers and traders keep
the merchant screen at standard quality, but their offers are rebuilt every five minutes
from the market. Brokers buy sealed parcels at a better rate per unit than loose goods, and
sell glowcap and sparkshard at a steep markup, since nothing produces those two yet.

The market is the sim's `MarketState` as a level attachment, ticked once a world minute:
every sale drains that substance's pool, time refills it, rivals drain it. `Market` is the
one place shillings become emeralds, at ten to one. `/slum market` reads and sets the pools.

### Recovery

The remedy draught holds withdrawal off for five minutes and takes a little dependence and
tolerance with it. It refuses while the last one is still working, so it cannot be chained
into a cure; the way out is still to stop. The design's infirmary stay and clean streak are
not built.

### Strains

`Strain` in `sim` is four traits on the seed, 0-100 each, as one component; unbred seed is
average and carries no mark. Potency multiplies the dose and the street price. Vigour
multiplies growth speed and yield. Hardiness raises the floor a bad climate can push a frame
to. Subtlety multiplies the suspicion a sale draws. The frame plants the line, the harvest
carries it, and the seed it returns drifts by up to three points per trait. Every station
carries the line forward with `ModComponents.inherit`, so product knows its line: tooltips
show the four traits on seed and potency on goods.

The grafting bench crosses two seeds of one plant into one seed at the parents' mean, moved
by up to ten points per trait, and spends both parents. Not built from the design: relatedness
and inbreeding defects, and stabilising a line over five generations to name it.

### Crews and standing

`Standing` in `sim` names the design's four crews and their opposed pairs: Ashfall against the
Glass Choir, the Tidewater Boatmen against the Quarry Kings. A player's standing with each,
-100 to 100, lives in the `Standings` attachment and moves zero-sum between opposed pairs.
Hitting one of theirs costs five and provokes the one hit; killing one costs twenty-five and
every crew member within thirty-two blocks inherits the dead one's anger, at least sixty.
Coin pressed into a crew member's hand is tribute: a point a shilling up to ten, and it calms
the one who took it. The NPC ticker rests every crew member's mood on the nearest player's
standing with their crew, and a lieutenant's mood spreads to crew within eight blocks. The
journal lists standings; `/slum standing` reads and sets them. Crews still only come from
`/slum npc spawn <role> <crew>`, with any crew name; the four known ones get their opposites.

Not built: turf cells and influence, war stages, diplomacy, poaching, player crews. The
Workshop gate stays on coin alone rather than the design's standing fifteen, because a
player who meets no crew would otherwise be stuck.

### The Watch

`Suspicion` in `sim` is the plugin's heat, renamed. Every sale raises it, a sealed parcel more
than loose goods because the seal is evidence, and quiet minutes lower it. Past 20 the player
is noticed, past 40 watched, past 60 hunted, and at 80 the bell rings: two minutes later the
Watch arrives and takes every stage of the chain the player is carrying, then stands down to
watched. Nothing else is touched, and it never comes without the bell. While watched or
worse, constables nearby are stirred to wary, and to demanding during a raid. The HUD shows
the level and the countdown. `/slum suspicion` reads and sets it.

Not built from the design: writs, the gaol, houndsmen, golems, bribery, evidence filed by
seal. The suspicion attachment is per player; quarter suspicion and bounty wait on turf.

### Progression

`Progression` in `sim` turns two counters — units sold and coin earned through the merchant
screen — into a tier, and the tier decides which stations a player may set up. The gate is
on placing, not crafting: `StationBlockItem` refuses with a message, and creative players
are never gated.

| Tier | Opens at | Unlocks |
| --- | --- | --- |
| Hand to mouth | start | Forcing frame, drying loft |
| Backroom | 20 units sold | Counting house desk |
| Workshop | 60 shillings earned | Pressing bench, sealing press, storage crate, centrifuge, still, cutting bench, grafting bench |
| Apothecary and up | not reachable | needs standing, turf and influence, none of which exist |

Two deliberate departures from the design document, both to be tightened when the systems
they wait on land: tier 0's pots and hand-drying do not exist, so the frame and the loft are
open from the start; and standing is not modelled, so the Workshop opens on coin alone.

The **journal** item reads the tier, the counters and the next gate back to the player in
chat. It has an art prompt but no drawing yet, so its model borrows the vanilla writable
book. `SalesLedger` does the counting from `TradeWithVillagerEvent`: only the mod's goods
count as units, only emeralds as coin, so buying seed moves nothing.

### The condition HUD

`ConditionHud` is a GUI layer in the bottom-left corner: an intoxication meter that turns red
near the overdose line, a pip that lights while craving, and a line above that reads either
the intoxication, the minutes until withdrawal starts, or the withdrawal's severity and the
minutes it has left. It draws nothing for a sober, clean player.

The numbers come from the `Condition` attachment, which is now synced to its owner through
NeoForge's attachment sync (`AttachmentType.Builder#sync`). Sync is not automatic: every
place that writes the condition calls `syncData` afterwards — the ticker once a second, the
product item on use, and the condition commands. `Progression` is synced the same way.

### Parked

- **Residents for the trader house.** The house places, but nobody lives in it; villagers of
  ours still only come from `/slum npc spawn`. The house should bring its trader with it when
  it generates. This waits on the village wiring, which exists locally and is not merged yet,
  because both touch structure placement.
- **Spawn pool for the house's chests.** A chest loot table is ready at
  `data/slumdrugs/loot_table/chests/trader_house.json`: seed, compost, honeycomb, charcoal, a
  few emeralds, and once in a while a remedy or a journal. It is not referenced yet: the chests
  in `trader_house.nbt` need their `LootTable` tag set to `slumdrugs:chests/trader_house`
  when the structure is next exported, which is a structure edit rather than code.

### Writing against 26.3

Minecraft 26.3 postdates any model's training data, so every API here was read out of the
decompiled sources under `neoforge/build/moddev/artifacts/` before it was used, and the
compiler settled the rest. Three things that a recalled 1.21 pattern gets wrong:

| Recalled | Actually 26.3 |
| --- | --- |
| `saveAdditional(CompoundTag)` | `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)` |
| Blocks need a `MapCodec` and `simpleCodec` | The codec requirement is gone; there is no `simpleCodec` |
| `level.isClientSide` | private field — call `isClientSide()` |
| `registerBlock(name, ctor, properties)` | takes a `Supplier<Properties>`, not a `Properties` |
| `ResourceLocation` | renamed to `Identifier` |
| `Screen#renderBg(GuiGraphics, ...)` | `extractBackground(GuiGraphicsExtractor, ...)`, and `blit` takes a `RenderPipelines` argument first |
| `@EventBusSubscriber(bus = Bus.MOD)` | the buses are unified; there is no `bus` attribute |
| `Player#displayClientMessage(msg, overlay)` | gone; the overlay flag lives on `ServerPlayer#sendSystemMessage` |
| `EntityType.VILLAGER` | the constants moved to `EntityTypes`; villagers live in `entity.npc.villager` |
| `source.hasPermission(2)` | named permissions: `Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER))` |
| `player.drop(stack, false)` | takes a `Prediction` (`PREDICTED` / `SERVER_ONLY`) |
| `useItemOn` returns `PASS` for "not my item" | return `TRY_WITH_EMPTY_HAND`, or `useWithoutItem` never runs while anything is held |
| `BaseEntityBlock` renders invisible without `getRenderShape` | it renders the model; the override is gone |
| `onRemove` to drop a container's contents | `BlockEntity#preRemoveSideEffects`, which any `Container` block entity already does |

## Continuous integration

`.github/workflows/build.yml` runs `./gradlew build` on every push and pull request and keeps
the jar as an artifact. It has not launched a client or server; that is still done by hand.

## Commands

Everything the mod can do from a prompt. `/slumdrugs` is an alias of `/slum`. Anything that
changes the world wants the gamemaster permission; `version` and reading your own condition
do not.

| Command | Does |
| --- | --- |
| `/slum version` | What is registered |
| `/slum give <item> [count] [quality]` | Any mod item, with a quality component. Completes item names |
| `/slum condition get [targets]` | Intoxication, tolerance, dependence, and whether they are craving or withdrawing |
| `/slum condition set\|add <field> <value> [targets]` | Write a field directly |
| `/slum condition clear [targets]` | Back to sober and clean |
| `/slum npc spawn <role> [crew] [name]` | A villager of ours: customer, resident, trader, healer, broker, constable, bruiser, lieutenant |
| `/slum npc list [radius]` | Who is nearby, their crew, aggression and stance |
| `/slum npc aggression set\|provoke\|appease <value> [radius]` | Move their mood |
| `/slum npc remove [radius]` | Remove ours only — never a village's own villagers |
| `/slum frame info` | The nearest forcing frame: crop, growth, water, soil, compost, climate, and what it would yield |
| `/slum frame grow` | Ripen it now |
| `/slum frame water <seconds>` | Set its water |
| `/slum refine <method> <quality> <units>` | A dry run against the rules — no blocks needed |
| `/slum structure place <piece> [rotation]` | Places a saved building, sunk so its ground floor meets the terrain |
| `/slum progress get [targets]` | Tier, units sold, coin earned, and what the next gate costs |
| `/slum progress set units\|coin <value> [targets]` | Move a player up or down the ladder |
| `/slum progress reset [targets]` | Back to hand to mouth |
| `/slum market get` | Demand, price factor and broker price for every substance |
| `/slum market set <substance> <demand>` | Move a pool |
| `/slum suspicion get [targets]` | Suspicion, its level, and the raid countdown if one is called |
| `/slum suspicion set <value> [targets]` | Move it; below hunted cancels a called raid |
| `/slum coin <pence> [stamped]` | A purse of that value, as the fewest coins |
| `/slum strain <trait> <value>` | Set one trait of the line on the held stack |
| `/slum standing get` | Standing with every crew that knows you |
| `/slum standing set <crew> <value>` | Move it; opposed crews move the other way |

`/slum refine` and `/slum frame info` exist to check the simulation against the numbers on a
running server without building anything, which matters while no client has been launched.
