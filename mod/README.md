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
