#!/usr/bin/env python3
"""Puts people into a converted building, and works out where the floors are.

A structure without markers generates empty: `StructurePlacer.people()` turns a jigsaw block
whose target is `slumdrugs:npc/<role>` into a villager of that role, and nothing else does.
Downloaded buildings have no markers, so every one of them has to be given some.

Standing on the right block matters, and neither the floor level nor a usable spot is obvious
from outside, so this finds both:

    python3 tools/add_markers.py precinct --report
    python3 tools/add_markers.py precinct constable constable constable
    python3 tools/add_markers.py corner_shop trader@2 --replace

A role may name the storey it belongs on -- `trader@2` -- which matters more than it sounds:
spread alone will happily put the shopkeeper on the roof, because the roof is the spot
furthest from everything else. Use --report to see which layers are floors.

A spot is a cell of *enclosed* air with something solid under it and air above -- the cutter
already dropped every air cell open to the outside, so anything left is indoors by
construction. Spots are picked spread out rather than clustered, because three constables in
one corner read as a bug.

The ground offset the placer wants (docs/STRUCTURES.md) is printed by --report: it is the first
free layer above the building's own footing.
"""
import collections
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import mcnbt  # noqa: E402

OUT = pathlib.Path(__file__).resolve().parent.parent / 'neoforge/src/main/resources/data/slumdrugs/structure'
ROLES = ('trader', 'customer', 'resident', 'healer', 'broker',
         'constable', 'bruiser', 'lieutenant')

# A hand is not a person who stands somewhere: Hands.java only hires a RESIDENT, and a hand
# nobody has paid quits and turns back into one. Marking a building with `hand` therefore
# produces a resident a second later, which looks like a bug and is not one.
NOT_FROM_A_MARKER = {'hand': 'a hand is hired from a resident, and quits back to one if unpaid'}

# The crews of Standing.Crew, and "local": the building's own crew, chosen where it stands.
CREWS = {'ashfall', 'tidewater', 'choir', 'quarry', 'local'}


def ints(v):
    return [int(x) for x in (v.values if hasattr(v, 'values') else v)]


def name_of(entry):
    if isinstance(entry, str):
        return entry
    return entry.get('Name') or entry.get('id') or entry.get('') or ''


def read(name):
    root = mcnbt.read(OUT / f'{name}.nbt')
    palette = [name_of(e) for e in root['palette']]
    filled = {}
    for block in root['blocks']:
        pos = tuple(ints(block['pos']))
        filled[pos] = palette[int(block['state'])]
    return root, palette, filled, ints(root['size'])


def solid(block):
    """Whether a cell holds a person up. Air and plants do not."""
    if not block or block.endswith(':air'):
        return False
    tail = block.split(':')[-1]
    return not any(t in tail for t in ('carpet', 'snow', 'torch', 'button', 'pressure_plate'))


def floors(filled, size):
    """How much solid floor each layer has, which is how a storey shows itself."""
    per_y = collections.Counter()
    for (x, y, z), block in filled.items():
        if solid(block):
            per_y[y] += 1
    area = size[0] * size[2]
    return [(y, per_y[y], round(100 * per_y[y] / area)) for y in sorted(per_y)]


def spots(filled, size):
    """Every cell somebody could stand in: enclosed air, solid below, air above."""
    out = []
    for (x, y, z), block in filled.items():
        if solid(block) or y + 1 >= size[1]:
            continue
        below = filled.get((x, y - 1, z))
        above = filled.get((x, y + 1, z))
        if solid(below) and not solid(above) and (x, y + 1, z) in filled:
            out.append((x, y, z))
    return sorted(out)


def spread(candidates, count):
    """Pick spots far apart, so a crew does not stand in one corner."""
    if not candidates or count <= 0:
        return []
    chosen = [max(candidates, key=lambda p: (p[1], p[0], p[2]))]   # start high: upper storeys
    while len(chosen) < count and len(chosen) < len(candidates):
        def far(p):
            return min((p[0] - c[0]) ** 2 + (p[1] - c[1]) ** 2 * 4 + (p[2] - c[2]) ** 2
                       for c in chosen)
        chosen.append(max((p for p in candidates if p not in chosen), key=far))
    return chosen


def mark(name, wanted, replace=False):
    """wanted is a list of (role, storey or None), in the order they are placed."""
    root, palette, filled, size = read(name)
    marked = [i for i, n in enumerate(palette) if n == 'minecraft:jigsaw']
    if marked and not replace:
        raise SystemExit(f'{name} already has markers; pass --replace to move them')
    if marked:
        keep = [b for b in root['blocks'] if int(b['state']) not in marked]
        root['blocks'] = keep
        filled = {p: v for p, v in filled.items() if v != 'minecraft:jigsaw'}

    free = spots(filled, size)
    picked = []
    for role, storey in wanted:
        here = [p for p in free if storey is None or p[1] == storey]
        if not here:
            raise SystemExit(f'{name}: nothing to stand on for {role}'
                             + (f' on y={storey}' if storey is not None else ''))
        # away from whoever is already placed, but only within the storey asked for
        spot = spread(here, 1)[0] if not picked else max(
            here, key=lambda p: min((p[0]-c[0])**2 + (p[1]-c[1])**2 * 4 + (p[2]-c[2])**2
                                    for c in picked))
        picked.append(spot)
        free.remove(spot)

    old = 'Name' in (root['palette'][0] if isinstance(root['palette'][0], dict) else {'Name': 1})
    state = len(root['palette'])
    root['palette'].append({'Name' if old else 'id': 'minecraft:jigsaw',
                            ('Properties' if old else 'properties'): {'orientation': 'up_north'}})

    by_pos = {tuple(ints(b['pos'])): b for b in root['blocks']}
    for spot, (role, _) in zip(picked, wanted):
        block = by_pos[spot]
        block['state'] = mcnbt.Int(state)
        block['nbt'] = {'joint': 'rollable', 'final_state': 'minecraft:air', 'name': 'minecraft:npc',
                        'pool': 'minecraft:empty', 'id': 'minecraft:jigsaw',
                        'target': f'slumdrugs:npc/{role}'}
    mcnbt.write(OUT / f'{name}.nbt', root)
    return picked


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise SystemExit(__doc__.strip().splitlines()[0])
    piece = sys.argv[1]
    rest = sys.argv[2:]

    replace = '--replace' in rest
    rest = [a for a in rest if a != '--replace']

    if '--report' in rest:
        root, palette, filled, size = read(piece)
        print(f'{piece}: {size[0]}x{size[1]}x{size[2]}, {len(filled)} cells')
        print('  floor by layer (y, solid cells, % of footprint):')
        for y, n, pct in floors(filled, size):
            if pct >= 25:
                print(f'      y={y:<3} {n:>5}  {pct:>3}%')
        places = spots(filled, size)
        per_y = collections.Counter(y for _, y, _ in places)
        print(f'  {len(places)} places to stand, by layer: '
              + ', '.join(f'y={y}:{n}' for y, n in sorted(per_y.items())))
        raise SystemExit

    wanted = []
    for arg in rest:
        who, _, storey = arg.partition('@')
        # A crew role may name its crew -- bruiser/choir -- or take the building's own with
        # bruiser/local, which the placer resolves from where the building stands.
        role, _, crew = who.partition('/')
        if crew and crew not in CREWS:
            raise SystemExit(f'unknown crew {crew!r}; one of {", ".join(sorted(CREWS))}')
        if crew and role not in ('bruiser', 'lieutenant'):
            raise SystemExit(f'only crew roles carry a crew, not {role}')
        if role in NOT_FROM_A_MARKER:
            raise SystemExit(f'{role}: {NOT_FROM_A_MARKER[role]}. Use resident instead.')
        if role not in ROLES:
            raise SystemExit(f'unknown role {role!r}; one of {", ".join(ROLES)}')
        wanted.append((who, int(storey) if storey else None))
    for spot, (role, _) in zip(mark(piece, wanted, replace), wanted):
        print(f'  {role:11} at {spot}')
