# Blockbench briefs — station blocks

The six station blocks are currently plain cubes assembled from vanilla textures. They read as
furniture only once they are modelled. Items stay flat sprites: vanilla items are 2D, and a 3D
item icon looks wrong in a hotbar next to everything else.

## Setup, the same for every model

- Format **Java Block/Item**. Model space is the 16×16×16 block grid.
- Textures stay **vanilla paths** (`minecraft:block/cut_copper` and so on) until we draw our own.
  Assign them in Blockbench as texture slots named `#side`, `#top`, `#frame` and reference the
  vanilla path, so a later swap to our own art is a one-line change per model.
- **Set a particle texture.** Without it the block throws missing-texture particles when broken.
  It is the single most common thing forgotten in a hand-built model.
- Export to `neoforge/src/main/resources/assets/slumdrugs/models/block/<name>.json`; the
  blockstate file already points there.

### The rotation limit that breaks exports

Vanilla JSON allows **one rotation axis per element**, at **±22.5 or ±45 degrees only**.
Blockbench lets you rotate freely and will happily show it, then the export silently loses it.
Anything round — the centrifuge drum, the wax pot — has to be faked with two or three boxes at
0° and 45°, not with an actual rotation.

---

## `centrifuge`

Cast-iron base, brass drum, hand crank. The one machine in the set, so it should look heavier
than the woodwork around it.

| Element | From | To | Texture |
| --- | --- | --- | --- |
| Base plinth | 2, 0, 2 | 14, 3, 14 | `block/deepslate_tiles` |
| Four feet | 2, 0, 2 / 12, 0, 2 / 2, 0, 12 / 12, 0, 12 | +2, 1, +2 each | `block/iron_block` |
| Drum, square core | 3, 3, 3 | 13, 12, 13 | `block/cut_copper` |
| Drum, 45° box | 3, 3, 3 | 13, 12, 13 | same, rotated 45° on Y to octagonalise |
| Lid | 4, 12, 4 | 12, 14, 12 | `block/copper_grate` |
| Crank shaft | 13, 7, 7 | 16, 9, 9 | `block/iron_block` |
| Crank handle | 15, 9, 7 | 16, 12, 8 | `block/iron_block` |
| Fuel hatch | 1, 4, 6 | 3, 8, 10 | `block/furnace_front` |

The two drum boxes at 0° and 45° are the whole trick: from any angle it reads round without a
single rotated face beyond what vanilla allows.

---

## `forcing_frame`

A glazed cold frame over a soil bed, with a sloped lid. Five stages, so the crop element is the
only part that changes.

| Element | From | To | Texture |
| --- | --- | --- | --- |
| Soil bed | 1, 0, 1 | 15, 5, 15 | `block/coarse_dirt` |
| Four corner posts | 0, 0, 0 / 14, 0, 0 / 0, 0, 14 / 14, 0, 14 | +2, 10, +2 each | `block/spruce_log` |
| Glass panels, four sides | 2, 5, 0 | 14, 9, 1 (and the three mirrored) | `block/glass` |
| Lid frame | 1, 9, 1 | 15, 10, 15 | `block/spruce_planks`, rotated 22.5° on X |
| Lid glass | 2, 9, 2 | 14, 10, 14 | `block/glass`, same rotation |
| **Crop** | 4, 5, 4 | 12, 9, 12 | two crossed planes, `block/wheat_stage2/4/6/7` by stage |

Stage 0 has no crop element at all — an empty frame should look empty through the glass.

---

## `drying_loft`

An open rack. What is hanging on it should be visible from outside, because that is how a player
sees it is working.

| Element | From | To | Texture |
| --- | --- | --- | --- |
| Two uprights | 1, 0, 1 / 13, 0, 1 | +2, 16, +2 | `block/spruce_log` |
| Three rails | 1, 6, 1 / 1, 10, 1 / 1, 14, 1 | 15, +1, 3 each | `block/spruce_planks` |
| Hanging bundles | below each rail | thin planes, 2 deep | `block/dried_kelp_side` |
| Back brace | 1, 2, 1 | 15, 3, 3 | `block/spruce_planks` |

---

## `pressing_bench`

A work table with a screw press standing on it.

| Element | From | To | Texture |
| --- | --- | --- | --- |
| Table top | 0, 10, 0 | 16, 12, 16 | `block/stripped_oak_log` |
| Four legs | 1, 0, 1 / 12, 0, 1 / 1, 0, 12 / 12, 0, 12 | +3, 10, +3 each | `block/oak_planks` |
| Press posts | 3, 12, 6 / 11, 12, 6 | +2, 16, +4 | `block/iron_block` |
| Crossbar | 3, 15, 6 | 13, 16, 10 | `block/iron_block` |
| Screw shaft | 7, 12, 7 | 9, 16, 9 | `block/iron_block` |
| Press plate | 5, 12, 5 | 11, 13, 11 | `block/smooth_stone` |

---

## `sealing_press`

A bench with a lever press and a pot of wax beside it. The wax is the colour cue that ties it to
the sealed parcels.

| Element | From | To | Texture |
| --- | --- | --- | --- |
| Table top | 0, 9, 0 | 16, 11, 16 | `block/spruce_planks` |
| Four legs | 1, 0, 1 / 12, 0, 1 / 1, 0, 12 / 12, 0, 12 | +3, 9, +3 each | `block/spruce_planks` |
| Lever arm | 6, 11, 7 | 14, 12, 9 | `block/iron_block`, rotated 22.5° on Z |
| Stamp block | 7, 11, 4 | 11, 13, 8 | `block/cut_copper` |
| Wax pot | 2, 11, 2 | 6, 14, 6 | `block/cauldron_side` |
| Wax surface | 3, 13, 3 | 5, 14, 5 | `block/red_concrete` |

---

## `storage_crate`

Plain, sturdy, no hinges. It should look like the cheapest thing in the room.

| Element | From | To | Texture |
| --- | --- | --- | --- |
| Body | 1, 0, 1 | 15, 13, 15 | `block/barrel_side` |
| Lid | 0, 13, 0 | 16, 15, 16 | `block/barrel_top` |
| Corner battens | four verticals at the corners | 1 thick, full height | `block/stripped_spruce_log` |
| Rope handle | 0, 6, 5 | 1, 8, 11 | `block/brown_wool` |

---

## Afterwards

Each model needs its **item display** set, or it will look wrong in the hand and in the hotbar.
In Blockbench: Display tab, `gui` rotation 30 / 225 / 0 at scale 0.625, `thirdperson_righthand`
rotation 0 / 45 / 0 at scale 0.4. Those are the vanilla block-item values; copying them keeps our
blocks consistent with everything else in the inventory.
