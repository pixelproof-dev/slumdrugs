# Buildings — what the mod needs and how big it may be

Twelve buildings. The list is not a wish: every one of them is a place a system in the code
already needs and currently has nowhere to happen. `Npc.Role` has nine roles and the mod ships
markers for three of them, which is why a village gets a trader, a resident and a customer and
nothing else — no law, no broker, no clinic, no crew.

This replaces the five Victorian pieces (`trader_house`, `resident_house`, `trade_hall`,
`steampunk_farm`, `police_station`) after the setting moved to the present day, MOD-GDD.md §1.

## The size budget, measured rather than guessed

One 48 × 151 × 48 tile cut from the Newisle city map came to **88 127 cells and 287 KB**. That
is the number to keep in mind, because it is what rules out copying a whole city: a 320 × 320
core would be about forty of those tiles, twelve megabytes in the jar and roughly four million
blocks to place. A building has to be cheap enough that a handful of them can go up beside one
settlement without the server noticing.

**Budget per building: under 30 000 cells, which lands around 80 KB.** That is comfortably met
by anything up to about 24 × 24 with four floors, or 16 × 16 with twelve. Air open to the
outside costs nothing — the cutter drops it — so height is cheaper than footprint.

Two limits set the footprint from the other side:

- The plot search allows a lean of `3 + longest_side / 8`, capped at ten
  (`VillageTraderHouse.leanLimit`). Measured over 25 villages, a 17-wide building found ground
  23 times out of 25; a 52-wide one managed 5. **Stay under 32 and a building will nearly always
  find somewhere to stand.** Past that it becomes a thing you place by hand.
- Whatever is cut has to carry **one layer of earth under its floor**, so the building has a
  footing rather than hovering. See STRUCTURES.md; the ground offset in `StructurePlacer.Piece`
  is the y of the first free layer above that.

## People markers — the part that keeps getting forgotten

A building without markers generates empty. Three of the five current pieces have none, and
`tools/check_data.py` says so on every run.

A marker is a **jigsaw block** with its target set to `slumdrugs:npc/<role>` and its final state
`minecraft:air`, standing on the floor where the person should be. The nine roles:

| role | who they are |
| --- | --- |
| `trader` | sells seed and supplies |
| `customer` | buys from you, on a schedule, at a quality they insist on |
| `resident` | lives there, sees things, talks |
| `healer` | treats dependence, for a price |
| `broker` | buys what the shops will not |
| `constable` | the law |
| `bruiser` | crew muscle |
| `lieutenant` | crew leadership; their mood spreads |


Crew roles may name their crew: `slumdrugs:npc/bruiser/<crew>`.

**Never mark a building with `hand`.** A hand is not somebody who stands anywhere: `Hands.java`
only hires a resident, and a hand nobody has paid quits and turns back into one. A `hand` marker
therefore produces a resident a second after the building lands, which looks like a bug. Mark
the person who works the place as a `resident` and let the player hire them.
`tools/add_markers.py` refuses the role outright.

## Tier 1 — without these the loop does not close

| id | footprint | who is inside | what happens there |
| --- | --- | --- | --- |
| `lockup` | 11 × 11 × 6 | nobody | A rented garage with a roller door. Empty by design: this is where the player starts and where the first stations go. The only building that must have floor space rather than furniture. |
| `corner_shop` | 13 × 13 × 8 | `trader` | Counter, shelves, a chiller. Sells seed, fertiliser, filler, seal film. The first place a player has to find. |
| `apartment_block` | 16 × 20 × 16 | `resident` ×2, `customer` ×2 | Four flats, a stairwell, post boxes. Your customer base lives here; the residents are what makes the street notice you. |
| `precinct` | 24 × 20 × 10 | `constable` ×3 | Front desk, back office, **two holding cells with doors that shut**. Where a taken player ends up (§5, the gaol), so the cells are load-bearing, not decoration. |

## Tier 2 — the economy

| id | footprint | who is inside | what happens there |
| --- | --- | --- | --- |
| `pawn_shop` | 13 × 13 × 8 | `broker` | Grilles on the window, everything behind glass. Buys what the shops will not; sells lockpicks and a forged ID. |
| `clinic` | 16 × 16 × 8 | `healer` | Waiting room, one treatment room. Dependence has a visible way out and this is it (§10.2). |
| `laundrette` | 13 × 13 × 7 | `resident` | Rows of machines, a back room with a desk. The front: loose cash goes in, banded cash comes out. The `cash_counter` station belongs in the back. |
| `warehouse` | 24 × 24 × 12 | `resident` ×2 | Roller shutter, pallet racking, an office up a metal stair. Bulk storage and the obvious place for a handover. |

## Tier 3 — the city reads as a city

| id | footprint | who is inside | what happens there |
| --- | --- | --- | --- |
| `tower_block` | 16 × 16 × 56 | `resident` ×4, `customer` ×2 | The skyline piece. Tall rather than wide on purpose: height is cheap, footprint is not, and a 16-wide tower finds ground almost anywhere. |
| `garage` | 16 × 16 × 8 | `lieutenant`, `bruiser` ×2 | Two bays, a pit, a sofa that should not be there. The crew's own place — walking in uninvited should feel like a mistake. |
| `strip_mall` | 32 × 14 × 10 | `trader`, `broker` | Three shop fronts in a row, one shuttered. The widest thing in the set and already at the edge of what finds a plot. |
| `car_park` | 24 × 24 × 14 | nobody | Two decks, a ramp, lights that flicker. No NPC: it exists so meetings have somewhere to happen that is not a shop. |

## What to do with them once they exist

Drop the `.nbt` into `neoforge/src/main/resources/data/slumdrugs/structure/`, add the id and its
ground offset to `StructurePlacer.PIECES`, and it generates beside settlements on its own — the
plot search, the levelling and the people all already work. `tools/cut_structure.py` cuts from a
world save or converts a WorldEdit `.schem`, including ones WorldEdit itself refuses to open.

Cut from an **untouched** copy of a downloaded map. A newer game rewrites level.dat the moment it
opens a world but upgrades chunks only when somebody flies near them, so a map you have looked at
has two versions in it; the cutter now refuses such a box rather than writing a file whose blocks
quietly fail to load.
