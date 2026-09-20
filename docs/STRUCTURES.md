# Authoring structures

How a building you made in-game becomes something the mod generates.

## Exporting

1. Stand the build on flat ground with nothing of the landscape in it you do not want copied.
2. `/give @s minecraft:structure_block`, place it at one corner, set it to **Save** mode.
3. Give it a name in the form `slumdrugs:trader_house`.
4. Set **relative position** and **size** so the box holds the build **including the cellar**.
5. Turn **Include entities** off unless the piece is meant to ship with them.
6. Save. The file lands in `<world>/generated/slumdrugs/structures/trader_house.nbt`.
7. Copy it to `neoforge/src/main/resources/data/slumdrugs/structure/trader_house.nbt`.

## Conventions

- **The origin is always the lowest corner of the box.** A structure block saves upward from
  its own position, so nothing can exist below the origin. For a building with a cellar the
  origin is therefore the cellar floor, not the ground floor.
- **Record the ground offset**: how many blocks up from the origin the ground floor sits
  (cellar depth plus foundation). This is the number a placer needs, and it cannot be read back
  out of the `.nbt`. Write it in the table below when a piece is added.
- **Sizes in odd numbers** where a piece has a front, so it can be centred on a plot.
- **Air is not neutral.** Structure saves record air, and placed air removes whatever was
  there. Keep the box tight around the build.

### Pieces

| Piece | Size (X × Y × Z) | Ground offset | Notes |
| --- | --- | --- | --- |
| `trader_house` | 19 × 23 × 17 | 9 | Timber frame over a stone footing, brick chimney, cellar |

The piece's own height is read from the `.nbt` at placement time; only the ground offset has to
be written down, because the file does not carry it.

## Cellars

A piece that goes below ground cannot simply be dropped at the surface, and the jigsaw JSON has
no offset field. `project_start_to_heightmap` aligns the piece's **origin** to the terrain
surface — which, for a piece whose origin is the cellar floor, puts the whole cellar above
ground and the house floating over it.

**This is solved for single buildings.** `StructurePlacer` computes
`origin = surface − groundOffset` and places the piece centred on a column, and
`/slum structure place <piece> [rotation]` runs it. That is the fastest way to check a new
piece: drop the `.nbt` in, reload, place it, walk in.

The remaining three options matter once buildings have to generate on their own:

1. **Place it from code.** A structure of our own computes `y = surface − groundOffset` and has
   exact control over digging and filling. The design needs a settlement placer anyway (§5.12),
   and this is the only option that also lets the placement be journalled and reversible.
2. **Split the piece.** Save the house with its origin at the ground floor, save the cellar
   separately, and connect them with a jigsaw block facing down. Pure JSON, works with vanilla
   jigsaw, at the cost of authoring two pieces and getting the connector alignment right.
3. **Absolute start height.** `"start_height": {"absolute": N}` as the ancient city does. Only
   sensible where the Y is known in advance, so not for a building on natural terrain.

Whichever is used, set `terrain_adaptation` so the buried part is not left hanging in a cave:
`beard_thin` and `beard_box` fill underneath and carve around, `bury` sinks the piece into the
ground, `encapsulate` wraps it. The ancient city uses `beard_box`; a house with a cellar wants
`beard_thin` or `beard_box`.

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
