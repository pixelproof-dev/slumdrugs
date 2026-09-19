# SlumDrugs

A NeoForge mod for Minecraft 26.3. Fictional substances, growing and processing, customers,
dependency and recovery, rival crews, and a quarter that rots or industrialises around what
you do.

Set one notch into the industrial age: brick, brass, glass, lanterns, canals and warehouses,
the first dynamos. The test for anything that ships is whether it would look at home in a
quarter lit by lanterns, next to a vanilla village.

## Status

Builds and packages. **It has never been launched** — no client or server has run it, so
treat everything below as untested in play.

Working today:

- 22 items with quality and grower carried as real data components
- 6 blocks: a forcing frame with five growth stages, drying loft, pressing bench, sealing
  press, storage crate, and a centrifuge laid out like a brewing stand
- Cultivation where seed, soil and compost decide both the quality and the size of a harvest
- Refining: press, centrifuge and still, each trading volume for strength, with spent mash
  returning as compost
- The condition system: use, tolerance, dependence, craving, withdrawal and recovery
- NPCs as villagers carrying an aggression model, trading through the vanilla merchant screen
- A full `/slum` command tree, including dry runs of the rules

## Build

```
./gradlew build
```

Needs nothing but the wrapper; Gradle fetches Java 25 and NeoForge itself.

```
sim/        the simulation: no Minecraft, no loader, no I/O — 85,866 assertions, about a second
neoforge/   the platform layer: registries, blocks, block entities, commands, NPCs
```

Every rule lives in `sim` and is verified without launching a game. Anything touching a
`Level`, a `BlockState` or a packet belongs in `neoforge`. Keeping that line is the point.

## Documentation

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
