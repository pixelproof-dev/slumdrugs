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

Then five station blocks: forcing frame, drying loft, pressing bench, sealing press and
storage crate. Their models are composed from vanilla textures, so they look like something
without needing new art. The forcing frame is the one with behaviour: plant a seed, watch
five visible stages, harvest by hand. Its block entity holds `GrowboxState` from `sim` and
does nothing itself — world clock in, block state out, NBT both ways. That is the whole
architecture in one class, and it means the growth rules stay covered by the assertions.

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
