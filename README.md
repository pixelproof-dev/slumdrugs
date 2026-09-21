# SlumDrugs

A NeoForge mod for Minecraft 26.3. Fictional substances, growing and processing, customers,
dependency and recovery, rival crews, and a quarter that rots or industrialises around what
you do.

Set one notch into the industrial age: brick, brass, glass, lanterns, canals and warehouses,
the first dynamos. The test for anything that ships is whether it would look at home in a
quarter lit by lanterns, next to a vanilla village.

## Status

Builds and packages, and has run in a local dev client. Everything below compiles and passes
the simulation checks; only the forcing frame, the centrifuge and the trader house have been
seen working in play so far.

Working today:

- 31 items with quality, grower, seal, strain and cut carried as real data components, shown on tooltips
- 10 working stations, all craftable: a forcing frame with five growth stages that reads
  warmth and damp, a drying loft whose rails show what hangs, a hand-worked pressing bench, a
  sealing press that stamps parcels with the sealer's name, a storage crate, a cutting bench
  for stretching goods with filler, a grafting bench for crossing seed lines, a counting
  house desk, and a centrifuge and a still laid out like a brewing stand
- Money as coin items, loose or stamped, at the design's price ladder
- Street sales paid by quality, potency and demand, brokers repriced from a living market
- Strains: four traits on the seed, bred at the grafting bench, felt all down the chain
- Crews with standing that rests their mood, tribute that buys it back, and a memory for the dead
- Regulars with loyalty and a quality floor; a gaol with bail in stamped coin; bribes that go in a book
- Named strain lines, essence from the still, the healer's treatment and clean streaks
- A server config for every number, tonic mode, and an advancement tree that unlocks recipes by tier
- A three-step tier ladder gating the workshop stations on units sold and coin earned, read
  back through a journal item
- The Watch: suspicion per sale, a bell two minutes ahead, a raid that takes what you carry
- A condition HUD: intoxication meter, craving pip, withdrawal timer, the Watch's interest
- Cultivation where seed, soil and compost decide both the quality and the size of a harvest
- Refining: press, centrifuge and still, each trading volume for strength, with spent mash
  returning as compost
- The condition system: use, tolerance, dependence, craving, withdrawal, recovery and the remedy draught
- NPCs as villagers carrying an aggression model, trading through the vanilla merchant screen
- A full `/slum` command tree, including dry runs of the rules

## Build

```
./gradlew build
```

Needs nothing but the wrapper; Gradle fetches Java 25 and NeoForge itself.

```
sim/        the simulation: no Minecraft, no loader, no I/O — 93,416 assertions, about a second
neoforge/   the platform layer: registries, blocks, block entities, commands, NPCs
```

Every rule lives in `sim` and is verified without launching a game. Anything touching a
`Level`, a `BlockState` or a packet belongs in `neoforge`. Keeping that line is the point.

## Documentation

- [Player's guide](docs/GUIDE.md) — from the first seed to the Workshop, in English; also
  [auf Deutsch](docs/GUIDE.de.md)
- [Mod notes](docs/MOD-NOTES.md) — build details, the command reference, and the table of
  26.3 API changes that a recalled 1.21 pattern gets wrong
- [Game design document](docs/MOD-GDD.md) — the full design this is built against
- [World generation handoff](docs/HANDOFF-WORLDGEN.md) — context for work on structures and
  settlements

## The Paper plugin

SlumDrugs began as a Paper plugin and shipped as one through version 1.2.2. **It is retired**
and no longer developed; the mod replaces it.

The released artifacts are still here and their links still work:

- [SlumDrugs 1.2.2 plugin](https://raw.githubusercontent.com/pixelproof-dev/slumdrugs/main/release/SlumDrugs-1.2.2.jar)
- [Textures and 3D furniture pack](https://raw.githubusercontent.com/pixelproof-dev/slumdrugs/main/release/SlumDrugs-ResourcePack-1.2.0.zip)
- [Complete source ZIP](https://raw.githubusercontent.com/pixelproof-dev/slumdrugs/main/release/SlumDrugs-Source-1.2.2.zip)

Its source was removed from the working tree in favour of the mod. The last commit that
contains it is **`1886950`**, and its documentation is kept under
[`docs/legacy/`](docs/legacy/) — the [takeover design](docs/legacy/VILLAGE-TAKEOVER.md) in
particular is still the reference for journalled, reversible world edits.

## Content

Every substance, effect and process in this mod is invented. There are no real-world names, no
real chemistry and no procedures that map onto anything real; the verbs are dry, press, seal
and separate. Dependence is modelled with a recovery path that always works, withdrawal is
deliberately shallow, and camera-altering effects are off unless a player turns them on.
