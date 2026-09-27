# Asset brief — present-day city

A standalone job. Everything needed to do it is in this file and the three it points at; you do
not need the conversation it came out of, and you do not need to read the Java.

The mod's setting moved from gaslight to the present day on 2026-09-27 (MOD-GDD.md §1). The
simulation is unaffected — the chain, the economy, the heat, the crews all work the same. What
does not survive is the costume: **every one of the 100 textures and all ten station models is
brass, soot and walnut, and has to be remade in concrete, steel and sodium light.**

## The prompt, if you are handing this to an agent

> Work through `docs/ASSETS-MODERN.md` in the slumdrugs repo. It is a self-contained brief for
> redrawing the mod's art after a change of setting from Victorian to present day. Produce the
> station models and the item textures it lists, to the names in `docs/RENAME-MODERN.md`, on a
> branch of your own. Touch only `neoforge/src/main/resources/assets/`, `tools/make_textures.py`,
> `tools/build_models.py` and the two art docs; the Java rename is somebody else's job and will
> collide if you do it too. Before handing anything back, run the three checks at the bottom of
> the brief and paste their output.

## Rules of the road, because this runs beside other work

- **Yours:** `neoforge/src/main/resources/assets/slumdrugs/**`, `tools/make_textures.py`,
  `tools/build_models.py`, `docs/ART-PROMPTS.md`, `docs/BLOCKBENCH-BRIEFS.md`.
- **Not yours:** anything under `src/main/java`, anything under `data/` (recipes, structures,
  loot), and `docs/MOD-GDD.md`. The id rename in the Java is a separate job already under way.
- Work on your own branch. The asset tree and the Java tree do not overlap, so the two branches
  can land in either order.
- **Names come from `docs/RENAME-MODERN.md`, not from what the code says today.** The Java still
  spells things `forcing_frame` and `coin_shilling`; they are being renamed to `grow_tent` and
  `cash_note` separately. Draw to the new names. Until both halves land, `tools/check_data.py`
  reports the old names as missing and yours as orphans — that is expected, and it is the one
  failure you may hand back unfixed. Everything else on that list is a real problem.

## The look

Read MOD-GDD.md §1 for the setting. For the art specifically, the shift is:

| was | is now |
| --- | --- |
| brass, copper, soot | steel, aluminium, powder-coated grey |
| walnut and pine furniture | laminate, melamine, folding tables |
| oil lamps, gaslight, firebox glow | LED strips, sodium orange, a cold blue standby light |
| wax, twine, brown paper | heat-seal film, zip ties, vacuum bags |
| hand-lettered labels, ledgers | printed labels, a phone screen, a clipboard |
| earthy desaturated apothecary palette | cool grey and concrete, with **one** saturated accent |

The accent is the point. A modern workshop is grey; what reads across a room is the one coloured
thing on it — a green power LED, an orange extension lead, a blue barrel. Keep the greys close
together in value and let the accent carry the silhouette.

Everything else about the drawing is unchanged from the old prompts and should stay, because it
is what makes the set look like Minecraft: **16×16, three shades plus a darker outline, hard
pixel edges, no anti-aliasing, no gradients, light from the upper left, silhouette filling about
12 of the 16 pixels.** Lift that tail from `docs/ART-PROMPTS.md` verbatim.

## Substance colours — canon

Eight products (MOD-GDD.md §1). Three colours carry over from the old set on purpose, so the
palette does not lurch:

| product | colour | note |
| --- | --- | --- |
| Daybreak | `#ffb03a` | warm amber, the cheap one |
| Coldsnap | `#9be7ff` | pale cyan — unchanged from frostroot |
| Redline | `#ff3d3d` | hot red |
| Neon | `#d58cff` | magenta — unchanged from glowcap |
| Voltage | `#fff26b` | electric yellow — unchanged from sparkshard |
| Blackout | `#3b2f63` | deep indigo, nearly black |
| Riptide | `#2fd3b5` | teal |
| Flatline | `#8d9b8a` | grey-green, deliberately dull |

Two families stay visually consistent, as before: **seeds** share one silhouette and differ only
in tint; **sealed bags** share one silhouette and differ only in the colour of the label.

## The work

### Station models — ten blocks

Models are **generated, not hand-modelled**: `tools/build_models.py` writes every block model
JSON from boxes, octagons and crossed planes. Read BLOCKBENCH-BRIEFS.md first — it explains why
(vanilla JSON allows one rotation axis at ±22.5 or ±45 only, and Blockbench will happily let you
build geometry the exporter then silently drops). Change the script and re-run it; do not
hand-edit the JSON.

| id (new) | was | what it should read as now |
| --- | --- | --- |
| `grow_tent` | forcing_frame | Zip-up tent on a frame, reflective silver inside, a grow light overhead. The tell is the light. |
| `drying_rack` | drying_loft | Steel shelving, mesh trays, a clip fan. The tell is the stack of mesh. |
| `cloning_bench` | grafting_bench | Propagation tray under a domed lid, scalpel, rockwool cubes. |
| `cutting_bench` | — | Steel table, scales, card, blade. Name unchanged. |
| `pressing_bench` | — | Hydraulic bench press with a gauge. Name unchanged. |
| `vacuum_sealer` | sealing_press | Clamshell sealer, roll of film, finished bags stacked. |
| `still` | — | Stainless column still, glass sight tube, hotplate. Name unchanged. |
| `centrifuge` | — | Benchtop centrifuge, lid, digital readout. Name unchanged. |
| `storage_crate` | — | Stacked plastic crates with a printed label, not a wooden box. |
| `cash_counter` | counting_house | Note counter, banded bricks of cash, a desk lamp. |

Four of them have a second look for a second state and keep that structure: the tent's light off,
the rack's trays full, the still running, the centrifuge spinning. The blockstate files pick
between them; that machinery already works and needs no change.

### Item textures — about 68 sprites

The full id list is `docs/RENAME-MODERN.md`. Grouped:

- **Substance chain**, six stages × eight products. The three grown ones have all six; the rest
  skip seed/raw/dried: `seed_`, `raw_`, `dried_`, `product_`, `essence_`, `package_`.
- **Money**: `cash_note`, `cash_roll`, `cash_brick` — a single bill, a banded roll, a
  shrink-wrapped brick. The escalation has to be legible at 16 pixels.
- **Paper and trade**: `property_deed`, `business_licence`, `contract`, `forged_id`, `notebook`.
- **The law**: `evidence_bag`, `wanted_notice`, `informant_file`, `police_scanner`, `ski_mask`,
  `lockpicks`.
- **Workshop consumables**: `carbon_filter`, `filler`, `solvent`, `seal_film`, `label_gun`,
  `clone_cutter`, `seed_pouch`, `fertilizer`, `remedy`, `mirror_glass`, `shadow_silt`.
- **Parts**: `coil_pack`, `battery_cell`, `carbon_rod`, `pump_valve`, `line_stabiliser`,
  `generator_fuel`.
- **New**: `burner_phone` — a cheap candybar handset with the screen lit. This one matters more
  than its size suggests: it is how a player finds the city at all (MOD-GDD.md §1), so it should
  look like the most important thing in the hotbar.

## Two traps in 26.3 that keep costing days

Both are in MOD-NOTES.md and both have already bitten this project:

1. **Every item needs a model *definition* as well as a model.** A file at
   `assets/slumdrugs/items/<name>.json` pointing at `assets/slumdrugs/models/item/<name>.json`.
   Without the first one the game silently draws the missing-model cube — the texture is perfect
   and the item still looks broken. `tools/check_data.py` checks for this; believe it.
2. **There is no `render_type` field any more.** 26.3 works transparency out per quad from the
   sprite's own pixels. If you carried a `"render_type": "cutout"` over from a 1.20 tutorial,
   delete it.

## Before handing anything back

```bash
python3 tools/build_models.py
python3 tools/check_data.py
./gradlew build
python3 tools/render_models.py
```

Paste the output of the middle two. `check_data.py` ending in `0 problems` — apart from the
rename mismatch described above — is the bar. And look at the previews in
`build/model-previews/` before you call it done: every model in this set has been wrong at least
once in a way that only showed up in a picture.
