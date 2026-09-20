# Authoring structures

How a building you made in-game becomes something the mod generates.

## Exporting

1. Stand the build on flat ground with nothing of the landscape in it you do not want copied.
2. `/give @s minecraft:structure_block`, place it at one corner, set it to **Save** mode.
3. Give it a name in the form `slumdrugs:trader_house`.
4. Set **relative position** and **size** so the box holds the build. The corner marker is the
   piece's origin — everything the mod does with the piece is measured from there.
5. Turn **Include entities** off unless the piece is meant to ship with them.
6. Save. The file lands in `<world>/generated/slumdrugs/structures/trader_house.nbt`.
7. Copy it to `neoforge/src/main/resources/data/slumdrugs/structure/trader_house.nbt`.

## Conventions

- **Origin at the ground-floor corner**, not at the cellar floor. Depth below the origin is
  what the placer uses to know how far to dig.
- **Sizes in odd numbers** where a piece has a front, so it can be centred on a plot.
- **Air is not neutral.** Structure saves record air, and placed air removes whatever was
  there. Keep the box tight around the build.
- Cellars are welcome and wanted — the design links them to the drains later (§5.12).

## Palette rules

The era test from `MOD-GDD.md`: *would it look at home in a quarter lit by lanterns, next to a
vanilla village?* In practice that means brick, stone, timber, glass panes, copper, lanterns,
campfires for chimney smoke — and none of the things that read as modern or magical.

**Every block in a piece must exist when the piece loads.** Vanilla blocks always do. Of ours,
these exist today and are safe to build with:

| Block | Use |
| --- | --- |
| `slumdrugs:forcing_frame` | A grower's cellar. Plantable, five visible stages |
| `slumdrugs:drying_loft` | Lofts and back rooms |
| `slumdrugs:pressing_bench` | Workshops |
| `slumdrugs:sealing_press` | Workshops |
| `slumdrugs:storage_crate` | Anywhere goods sit |
| `slumdrugs:centrifuge` | Works towns and better |

Anything else of ours does not exist yet — a piece that references it will fail to load.

## Wiring a piece in

Once the `.nbt` is in place, a piece needs:

- a template pool entry, so a jigsaw can pick it;
- or a direct placement, for a one-off building;
- a structure set with spacing, so it generates at a sensible density;
- a biome tag saying where it belongs.

All of that is datapack JSON under `neoforge/src/main/resources/data/slumdrugs/worldgen/`.
