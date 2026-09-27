# Modern station models — Blockbench and Minecraft

The ten stations now use custom present-day models: cool steel, powder-coated housings,
plastic, reflective tent lining, printed labels, digital displays and small colour accents.
The geometry is authored by `tools/build_models.py`; the pixel materials are authored by
`tools/make_textures.py`. No external texture downloads are needed.

## Open in Blockbench

Unzip `release/SlumDrugs-Modern-Stations.zip`, then use **File → Open Model** to open a
`.bbmodel` from `blockbench/`. All textures are embedded. Each cuboid remains editable,
with explicit face UVs, named elements and Minecraft-compatible rotations.

There are **27 projects**: all ten stations plus the growth, light, loading, drying and
working variants. The matching Minecraft JSONs, PNG materials, animations, blockstates,
item definitions and previews are included in the archive.

The generator also leaves the projects in `build/blockbench-modern/`. The overview is
`build/model-previews/modern-stations-overview.png`.

## The ten stations

| Model | Design and details |
| --- | --- |
| `grow_tent_stage0..4` | Open zipped tent, rolled door with orange straps, reflective walls, suspended three-bar light, two grow pots, ventilation unit, controller; five crop stages and light-off variants. |
| `drying_rack` | Three steel mesh trays, blue pull handles, green/brown material according to dryness, rear clip fan, rubber feet; empty and partially loaded variants. |
| `cloning_bench` | Six rockwool plugs and seedlings beneath a transparent propagation lid, mint handle, cutter and dispenser on a steel workbench. |
| `cutting_bench` | Steel table with backsplash, white work mat, digital scales, card, blade and labelled container. |
| `pressing_bench` | Orange hydraulic crosshead, twin uprights, chrome ram, platen, gauge, side pump handle and caution strip. |
| `vacuum_sealer` | Raised clamshell lid, black gasket, display, film roll, clear feed sheet and stacked labelled bags. |
| `still` | Stainless vessel, banded column, sight window, gauge, condenser, receiver and digital hotplate; animated running display. |
| `centrifuge` | White benchtop housing, dark lid seam, visible rotor, blue handle, vents and digital panel; animated rotor and running display. |
| `storage_crate` | Two ribbed blue polymer crates with dark reinforced rims, recessed hand grips, barcode label and ribbed lid. |
| `cash_counter` | Note counter with feed rollers, banknote hopper, output tray, banded cash stacks and a raised desk lamp. |

## Compatibility

The modern models also replace the legacy model filenames used by the currently registered
blocks. Existing `forcing_frame`, `drying_loft`, `grafting_bench`, `sealing_press` and
`counting_house` blocks therefore receive the new appearance immediately. The future IDs
`grow_tent`, `drying_rack`, `cloning_bench`, `vacuum_sealer` and `cash_counter` have matching
asset definitions ready. This does not rename Java registrations or save data.

Models are decorative geometry, at 16 units per block. The tallest reach 25.5 units; place
them with clear space above. Existing collision shapes, gameplay and particles are unchanged.
Screen and rotor animations are pixel animation strips, not custom entity renderers.

## Rebuild and check

Run from the repository root:

```
python tools/make_textures.py
python tools/build_models.py
python tools/check_data.py
./gradlew build
python tools/render_models.py
```

The standard renderer uses a fixed camera that may crop tall models; the supplied overview
uses a raised centre and consistent reduced scale. The software previews approximate glass
and shading and are not in-game screenshots.

Validation on 2026-09-27:

- Data checker: `68 items, 10 blocks, 0 problems`.
- Gradle: `BUILD SUCCESSFUL`; `PASS: 98673 simulation assertions` and the playthrough passed.
- All 27 project/model pairs passed bounds, legal rotations, texture-index and embedded-PNG checks.
- All ten primary models were rendered from front and rear; front overview visually reviewed.
- No Minecraft client or Blockbench UI validation was performed for this asset pass.

## Editing ownership

Regenerating overwrites both JSON models and `.bbmodel` projects. For permanent edits,
change the generator, or deliberately take ownership of an exported model and remove its
write call from the generator. Edit all relevant state variants together. Compatibility
copies must receive the same geometry until the Java rename is complete.
