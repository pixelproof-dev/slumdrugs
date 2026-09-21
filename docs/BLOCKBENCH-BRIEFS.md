# Station models

The ten station blocks each have their own model, built to be told apart at a glance from
across a room. None of them is a cube. They are generated, not hand-modelled: `tools/build_models.py`
writes every block model JSON from a short description of boxes, octagons and crossed planes, so
a change to a model is a change to that script, run again. Items stay flat sprites: vanilla items
are 2D, and a 3D item icon looks wrong in a hotbar next to everything else.

```
python3 tools/make_textures.py     # the palette PNGs (deterministic; safe to re-run)
python3 tools/build_models.py      # all seventeen block models
python3 tools/render_models.py     # previews in build/model-previews/
```

## Why generated

Vanilla JSON allows one rotation axis per element, at ±22.5 or ±45 degrees only, with every
coordinate between -16 and 32. Blockbench lets you rotate freely, shows it, then the export
silently drops it. The generator only produces legal geometry: anything round is an octagon
made of four bars (two square, two at 45°), anything leafy is two planes crossed at 45°, and
every angle is one of the four allowed. The `Model` class has three primitives:

- `box(from, to, texture, faces=…, rot=…, only=…, skip=…, shade=…)`: one element, optional per-face
  texture override, optional single-axis rotation, optional face culling.
- `octagon(cx, y0, y1, cz, width, texture, top=…, bottom=…, axis=…)`: a drum or a pot. The diagonal
  pair is shrunk by 0.02 so the bars never z-fight.
- `cross(cx, y0, y1, cz, width, texture)`: a plant, a hanging bundle. Unshaded so it reads flat.

Every model is written with the vanilla block-item display transforms (gui 30/225/0 at 0.625,
third person 0/45/0 at 0.4), a particle texture, and `#slot` texture names, so a texture swap is a
one-line change in the script.

## The palette

The stations share one set of materials so they read as one workshop. The textures live in
`assets/slumdrugs/textures/block/` and are drawn by `tools/make_textures.py`: flat, low-noise
surfaces, because the models stretch them across faces of every size and the detail lives in the
geometry. They are placeholders in the sense that matters: the hand-drawn art overwrites them by
filename, and nothing in the models changes when it does.

| Slot | File | Where it shows |
| --- | --- | --- |
| brass, brass_band | `brass.png`, `brass_band.png` | frame posts, capstan wheel, drum, lever, fittings |
| copper | `copper.png` | the still's boiler, neck and worm |
| iron | `iron.png` | legs, press frame, scale beam, cleaver |
| walnut | `walnut.png` | dark furniture: bench tops, desk, loft posts |
| pine | `pine.png` | pale furniture: crate, sealing bench, grafting bench |
| end_grain | `end_grain.png` | the cutting block's top |
| terracotta | `terracotta.png` | wax pot, plant pots |
| leather_green | `leather_green.png` | the desk's writing slope |
| wax, paper, parcel_top | `wax.png`, `paper.png`, `parcel_top.png` | sealed parcels waiting on the benches |
| glass | `glass.png` | cold-frame panes, receiver flask |
| soil, herb, powder | `soil.png`, `herb.png`, `powder.png` | beds, a heap of dried herb, a heap of cut |
| twine, crate_label, gauge, firebox, ledger, lamp, gold_stack | one file each | the small tells |
| plant_1..4 | `plant_1.png` … `plant_4.png` | crop stages on the crossed planes |
| bundle | `bundle.png` | hanging herb in the loft |

## The models

Each is described by its silhouette, the one thing you recognise it by, and its footprint.
Extents past the block are allowed by the format and used sparingly for height and for parts
that should hang over the edge.

**`forcing_frame_stage0..4`.** A brass-framed glasshouse on a walnut bed: four brass posts, glass
walls, a gabled glass roof with a brass ridge, and a lit lantern hung inside under the ridge. The
crop is the only thing that changes between stages: nothing at stage 0, one crossed plant from
stage 1 growing taller, two more sprouting beside it from stage 3. The tell is the glow inside
the glass. Footprint 16×16, 15 high.

**`drying_loft_0..2`** and the plain `drying_loft`. Four tall walnut posts, a low pine shelf, two
rails with a cord slung between them, under a peaked roof of pine slats. Bundles of herb hang
from the rails as crossed planes: none, a few, a full row. The tell is the peaked slat roof and
what hangs beneath it. Footprint 18×16 (the eaves overhang), 18 high.

**`pressing_bench`.** A walnut bench with a tall iron frame standing on it and a brass capstan
wheel on top of the screw. Dried herb sits under the plate. The tell is the wheel, held well
above everything else in the room. Footprint 16×16, 27 high.

**`sealing_press`.** A pine bench with a brass lever press, a die under the lever, a terracotta
wax pot beside it, and finished parcels stacked at the other end. The tell is the pot of red wax
next to brown paper. Footprint 16×16, 19 high.

**`storage_crate`.** A pine crate with diagonal walnut braces on each side, the lid propped open
a crack, a paper label on the front and rope handles on the ends. The tell is the braces and the
open lid. Footprint 16×16, 14 high.

**`centrifuge`.** An octagonal brass drum with a band around its waist, standing on four iron
legs, with a lid, a gauge on the front and a crank on the side. The only thing in the set that is
mostly round and mostly metal. Footprint 16×16, 17 high.

**`still`.** A copper onion boiler on a firebox, its swan neck bending over and down into a
walnut worm barrel, a glass receiver at the outlet. Bands of brass where copper meets copper. The
tell is the swan neck. Footprint 18×12, 22 high.

**`cutting_bench`.** A thick end-grain block on walnut legs, a heap of powder on it, a cleaver
stuck upright, and a brass balance scale standing at the end with its pans hanging over the edge.
The tell is the scale. Footprint 18×16, 21 high.

**`grafting_bench`.** A pine potting bench with a trellis of twine-lashed laths at the back and
two terracotta pots on top, each with a young plant, tools hung on the trellis. The tell is the
trellis. Footprint 16×16, 22 high.

**`counting_house`.** A walnut clerk's desk with a green leather writing slope, an open ledger
on it, stacks of coin, an ink pot and a brass stamp. The tell is the green leather and gold.
Footprint 16×16, 20 high.

## Seeing a model without Blockbench

`tools/render_models.py` draws the model JSON with the textures and writes a PNG to
`build/model-previews/`. It needs nothing installed, only that `./gradlew build` has run once so
the Minecraft jar is on disk for any vanilla texture a model still references.

```
python3 tools/render_models.py                 # every block model
python3 tools/render_models.py centrifuge      # just one
```

It is an orthographic, z-buffered renderer using Minecraft's own per-face shading, with element
rotation. It shows proportion and silhouette faithfully. It does not show ambient occlusion,
block light, per-face UV mapping, or how a model behaves in the hand, so it answers "are the
proportions right" and not "does it look good in the world".

## Editing by hand

**blockbench.net/web** opens these files directly with `File → Import → JSON Model`. Anything
edited there and exported back will be overwritten the next time the generator runs, so either
make the change in `tools/build_models.py` or delete that model's function from the script and
own the JSON by hand from then on. The rotation limit above still applies to hand edits.
