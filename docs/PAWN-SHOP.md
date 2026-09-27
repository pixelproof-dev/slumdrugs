# Pawn shop

Original modern Minecraft structure, built for SlumDrugs. No downloaded map content.

- File: `neoforge/src/main/resources/data/slumdrugs/structure/pawn_shop.nbt`
- Size: **17 X x 11 Y x 15 Z** (17 x 15 footprint).
- Ground offset: **2**. One layer of earth, then the floor; occupants stand at local Y=2.
- Entrance: **south**, matching the settlement/city placement convention.
- One broker marker at **(8, 2, 5)** in the saved structure.
- 1,259 non-air/non-void blocks; below the 30,000-cell building budget.

The front has a block-built glowing PAWN sign, barred glass storefronts, a canopy, an entry
mat, trade/open signs and a projecting three-gold-block pawn emblem. The interior has
reclaimed shelving, second-hand goods, protected displays, an L-shaped counter with an open
service hatch, and a staff office with a desk, chair, storage and a loot chest. The flat roof
has a condenser and ductwork. Sea lantern strips light the sales floor.

The chest uses `slumdrugs:chests/pawn_shop`: 2–4 rolls from small quantities of iron, gold
nuggets, redstone, a compass, a clock or an emerald. Decorative display goods are vanilla
blocks; they do not introduce new trading systems. The broker uses the existing role.

## Use

After installing the rebuilt mod or restarting the dev client:

```
/slum structure place pawn_shop
```

The piece is registered in `StructurePlacer.PIECES`, so the existing city builder and
village plot search also consider it.

## Regenerate

```
python tools/build_pawn_shop.py
python tools/check_data.py
python tools/preview_pawn_shop.py
./gradlew build
```

The generator writes only this shop and its loot table. It checks two-block-high walking
routes from the entrance to the sales floor, broker aisle and office before saving. Do not
run the legacy `build_structures.py` command for this job; it generates older buildings.

Previews are under `build/structure-previews/pawn_shop_*.png`. They are software views of the
actual structure. Sign text, block-entity rendering, lighting and some small furniture are
approximated. The shared renderer is not modified by the dedicated preview adapter.

Checked: palette IDs and supported variant properties, NBT bounds, one broker marker,
service-hatch clearance, accessible entrance and office, data checker and Gradle build.
A live Minecraft client walk-through is still needed.
