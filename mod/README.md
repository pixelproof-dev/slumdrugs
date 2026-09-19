# SlumDrugs — mod

A separate Gradle build. The Paper plugin at the repository root is untouched and still
builds on its own.

## Layout

```
mod/
  sim/        the simulation: no Minecraft, no loader, no I/O
  <loader>/   the platform layer — added once the loader is decided
```

`sim` holds the rules: quality curves, recovery, package splitting, growth, demand, the
bounded room flood used by the takeover safety checks. It has **no dependencies at all**,
which is the point — it compiles and verifies in about a second without a game, and it is
identical whichever mod loader the platform layer ends up using.

Anything that needs a `Level`, an `ItemStack`, a config file or a network packet belongs in
the platform layer, not here.

## Build

```
./gradlew -p mod check
```

Runs `SimChecks`, a plain `main()` with no test framework, currently 66,710 assertions.
A failure prints the label of the rule that broke.

## Status

`sim` is live. The platform layer is not written yet: Minecraft 26.3 shipped on 2026-09-15,
Fabric has stable 26.3 support, NeoForge is at `26.3.0.x-beta`, and the loader decision is
open — see `../docs/MOD-GDD.md` §2.

## Platform layer (NeoForge)

Decided 2026-09-19: NeoForge, pinned to `26.3.0.6-beta` — the only 26.3 builds that exist.
Built with ModDevGradle 2.0.147 on Gradle 9.1.0 and Java 25, all of which 26.x requires.

Fabric was the alternative and had the better numbers on availability alone (stable 26.3
loader and API today, against NeoForge betas). It lost on integration: MineColonies is
officially NeoForge-only, which would have deleted §5.13 of the design permanently rather
than deferring it.

```
./gradlew -p mod build            # sim checks + the mod jar
./gradlew -p mod :neoforge:runClient   # launches a dev client (not yet run in CI)
```

The first slice registers the 21 items that already had artwork in the plugin — seeds, raw,
dried, product and sealed parcels for each substance, plus compost and remedy — as real
registry entries with models, a creative tab, and `en_us` plus `de_de` translations.
