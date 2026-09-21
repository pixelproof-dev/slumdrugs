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

Runs `SimChecks`, a plain `main()` with no test framework, currently 86,396 assertions.
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
| Centrifuge | Dried in the vessels, charcoal on top, coal at the side | Runs on its own; the screen shows progress | Product in the vessels |
| Storage crate | Anything | — | The chest screen, 27 slots |

A parcel is used like any item to break the seal and get its 8 units back. A bundle left on
the loft long past done slowly loses quality, and none of the stations tick except the
centrifuge; the loft and the presses only move when someone moves them. Breaking any station
drops what it held. The rules behind them are `Drying`, `Sealing` and `Refining` in `sim`.

Every station has a crafting recipe under `data/slumdrugs/recipe/`: wood, glass and string
for the frame and the loft, iron and copper on wood for the workshop pieces. Brokers buy
sealed parcels at a better rate per unit than loose goods; customers only take loose goods.

### Progression

`Progression` in `sim` turns two counters — units sold and coin earned through the merchant
screen — into a tier, and the tier decides which stations a player may set up. The gate is
on placing, not crafting: `StationBlockItem` refuses with a message, and creative players
are never gated.

| Tier | Opens at | Unlocks |
| --- | --- | --- |
| Hand to mouth | start | Forcing frame, drying loft |
| Backroom | 20 units sold | nothing yet; the design's first regulars and journal go here |
| Workshop | 60 coin earned | Pressing bench, sealing press, storage crate, centrifuge |
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
| `/slum frame info` | The nearest forcing frame: crop, growth, water, soil, compost, and what it would yield |
| `/slum frame grow` | Ripen it now |
| `/slum frame water <seconds>` | Set its water |
| `/slum refine <method> <quality> <units>` | A dry run against the rules — no blocks needed |
| `/slum structure place <piece> [rotation]` | Places a saved building, sunk so its ground floor meets the terrain |
| `/slum progress get [targets]` | Tier, units sold, coin earned, and what the next gate costs |
| `/slum progress set units\|coin <value> [targets]` | Move a player up or down the ladder |
| `/slum progress reset [targets]` | Back to hand to mouth |

`/slum refine` and `/slum frame info` exist to check the simulation against the numbers on a
running server without building anything, which matters while no client has been launched.
