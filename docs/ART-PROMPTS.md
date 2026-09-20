# Pixel art prompts — items

All item textures are **16×16**, like vanilla. The current ones are 64×64, carried over from
the plugin, and look wrong sitting next to vanilla items in a hotbar: four times the pixel
density of everything around them.

Substance colours are canon, from the plugin's `drugs.yml`:

| Substance | Colour | Character |
| --- | --- | --- |
| Sunleaf | `#7bd35a` | Leafy herb, cheap, the starter |
| Frostroot | `#9be7ff` | Pale ice blue. Root → crystal shards → powder ("Frostdust") |
| Emberbloom | `#ff7a3d` | Amber resin with an inner glow, the expensive one |
| Glowcap | `#d58cff` | Violet mushroom, faintly luminous |
| Sparkshard | `#fff26b` | Pale electric yellow mineral, faceted |

## Style block

Prepend this to every prompt:

> 16×16 pixel art item icon, Minecraft Java Edition vanilla style, transparent background.
> Hard pixel edges, no anti-aliasing, no gradients, no blur, no outline glow. Limited palette:
> three or four shades of the material plus one darker shade for the outline. Light from the
> upper left, shadow lower right. Centred, silhouette filling about 12 of the 16 pixels, clearly
> readable at actual size. Slightly desaturated, earthy, nineteenth-century apothecary feel.
> No text, no frame, no drop shadow.

## Two families that must stay consistent

**Seeds** share one silhouette — a small loose scatter of four or five seeds in a shallow heap,
lower middle of the icon — and differ only in seed shape and tint. **Sealed parcels** share one
silhouette — a small waxed-paper parcel tied with twine, a blob of wax where the twine crosses —
and differ only in the colour of the wax. A player should read the family instantly and the
substance from the colour.

## Sunleaf

- **seed_sunleaf** — a scatter of four flat teardrop seeds, pale straw with a faint green cast,
  one seed slightly apart from the heap.
- **raw_sunleaf** — a fresh cut sprig: three serrated leaves on a short stem, vivid green
  `#7bd35a`, a lighter green along the upper edges, a darker green underside.
- **dried_sunleaf** — the same sprig curled and brittle, desaturated olive fading to brown at
  the tips, no highlight.
- **product_sunleaf** — a short roll wrapped in off-white paper, twisted closed at one end, a
  few green herb flecks showing at the open end. Diagonal, lower left to upper right.
- **package_sunleaf** — the parcel silhouette, green wax seal.

## Frostroot

- **seed_frostroot** — a scatter of small round dark seeds with a cold blue sheen on the lit side.
- **raw_frostroot** — a pale forked root, ice blue `#9be7ff`, with three tiny frost crystals
  growing along it, hanging slightly diagonal.
- **dried_frostroot** — a loose cluster of thin angular crystal shards, translucent ice blue,
  white specular pixels on two facets only.
- **product_frostroot** — a small paper fold holding pale blue powder, the fold open at the top
  so a little of the powder shows.
- **package_frostroot** — the parcel silhouette, ice blue wax seal.

## Emberbloom

- **seed_emberbloom** — a scatter of dark red-brown knobbly seeds, one catching a warm highlight.
- **raw_emberbloom** — a lump of amber resin, semi-translucent orange `#ff7a3d`, lighter at the
  centre as though lit from inside, irregular rounded shape.
- **dried_emberbloom** — the same lump hardened and matte, deep burnt orange with a few darker
  cracks, no inner light.
- **product_emberbloom** — a small corked bottle of orange tincture, dark glass, the liquid a
  brighter orange band across the middle.
- **package_emberbloom** — the parcel silhouette, orange wax seal.

## Glowcap

- **product_glowcap** — a single mushroom cap, violet `#d58cff`, pale gills underneath, two or
  three lighter pixels on the crown suggesting a faint glow. No stem, or a very short one.
- **package_glowcap** — the parcel silhouette, violet wax seal.

## Sparkshard

- **product_sparkshard** — a jagged mineral shard standing upright, pale electric yellow
  `#fff26b`, three flat facets in different shades, one bright pixel where the light catches an
  edge.
- **package_sparkshard** — the parcel silhouette, yellow wax seal.

## The two loose items

- **fertilizer** (Compost Charge) — a small burlap pouch tied at the neck with twine, dark
  crumbly compost spilling from the top, one or two straw pieces sticking out. Muted brown and
  sackcloth tan.
- **remedy** (Remedy Draught) — a small apothecary bottle, rounded body, short neck, cork
  stopper, cloudy pale green liquid, a tiny blank paper label on the body. Reads as medicine,
  not as a potion: no sparkle, no swirl.

## A caveat worth knowing before generating

Image models are poor at genuine 16×16. They usually produce a large image that *looks* pixelated
rather than an actual 16-pixel grid, with soft edges and far more colours than the prompt asked
for. Expect to downsample to exactly 16×16 and then clean up by hand — the grid, the palette and
the outline are almost always wrong on the first pass. Drawing these directly in Aseprite or
Blockbench is often faster than fixing a generated one.

## Blocks

None needed. All six blocks compose their models from vanilla textures — spruce planks, glass,
coarse dirt, smooth stone, cut copper, barrel, copper grate — and that is deliberate: they sit
correctly next to a vanilla village without any art being drawn.
