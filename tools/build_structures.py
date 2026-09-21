#!/usr/bin/env python3
"""Writes the mod's generated buildings as vanilla structure files.

Like the models, a building here is code: a footprint, walls, a roof, the furniture and the
markers, written to `data/slumdrugs/structure/<name>.nbt` in the format a structure block
saves. The pieces use the trader house's vocabulary (cobblestone footing, dark oak frame,
spruce and oak infill, dark oak stair roofs, brick chimneys, lanterns) so they sit beside it.

Two things a hand-built piece would have to add by hand are built in:

- **Markers.** A jigsaw block whose target is `slumdrugs:npc/<role>` is where a person of
  that role stands when the piece is placed. `StructurePlacer` spawns them and replaces the
  jigsaw with its final state. The same marker works in a piece saved from a structure block.
- **Chests** carry the `LootTable` tag, so they fill from the mod's chest table on first open.

    python3 tools/build_structures.py          # writes every piece
    python3 tools/render_structure.py          # draws them for a look

Conventions from docs/STRUCTURES.md: the origin is the lowest corner; y=0 is the footing,
y=1 the floor layer that replaces the ground's top block, so the ground offset is 2. Outside
the walls the box is `structure_void`, which leaves the terrain alone; inside it is air.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import mcnbt  # noqa: E402

DATA_VERSION = 5023  # Minecraft 26.3
OUT = pathlib.Path(__file__).resolve().parent.parent / 'neoforge/src/main/resources/data/slumdrugs/structure'
LOOT = 'slumdrugs:chests/trader_house'


class Build:
    """A block grid with a palette, written out as one structure."""

    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}
        for x in range(sx):
            for y in range(sy):
                for z in range(sz):
                    self.blocks[(x, y, z)] = ('minecraft:structure_void', None, None)

    def set(self, x, y, z, name, props=None, nbt=None):
        assert (x, y, z) in self.blocks, (x, y, z)
        self.blocks[(x, y, z)] = ('minecraft:' + name if ':' not in name else name, props, nbt)

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def get(self, x, y, z):
        return self.blocks[(x, y, z)][0]

    def write(self, name):
        palette, index = [], {}
        blocks = []
        for (x, y, z), (bname, props, nbt) in sorted(self.blocks.items()):
            key = (bname, tuple(sorted((props or {}).items())))
            if key not in index:
                index[key] = len(palette)
                # 26.3 spells a block state `id` and `properties`; the older `Name` and
                # `Properties` are what the data fixer upgrades in a piece saved by an older game.
                entry = {'id': bname}
                if props: entry['properties'] = {k: str(v) for k, v in sorted(props.items())}
                palette.append(entry)
            block = {'pos': [mcnbt.Int(x), mcnbt.Int(y), mcnbt.Int(z)], 'state': mcnbt.Int(index[key])}
            if nbt: block['nbt'] = nbt
            blocks.append(block)
        root = {
            'size': [mcnbt.Int(v) for v in self.size],
            'entities': [],
            'blocks': blocks,
            'palette': palette,
            'DataVersion': mcnbt.Int(DATA_VERSION),
        }
        OUT.mkdir(parents=True, exist_ok=True)
        mcnbt.write(OUT / f'{name}.nbt', root)
        used = sum(1 for b in self.blocks.values() if b[0] not in ('minecraft:structure_void', 'minecraft:air'))
        return f'{name} {self.size[0]}x{self.size[1]}x{self.size[2]}, {used} blocks, {len(palette)} states'


# ---------------------------------------------------------------- vocabulary
def stairs(facing, half='bottom'):
    return {'facing': facing, 'half': half, 'shape': 'straight', 'waterlogged': 'false'}


def pane(axis):
    """A pane in a wall that runs along x (joins east-west) or z (joins north-south)."""
    ns = 'true' if axis == 'z' else 'false'
    ew = 'true' if axis == 'x' else 'false'
    return {'north': ns, 'south': ns, 'east': ew, 'west': ew, 'waterlogged': 'false'}


def fence(**sides):
    p = {'north': 'false', 'south': 'false', 'east': 'false', 'west': 'false', 'waterlogged': 'false'}
    p.update({k: 'true' for k in sides})
    return p


def marker(role, crew=''):
    """A jigsaw the placer turns into a person of `role`, then into air."""
    target = f'slumdrugs:npc/{role}' + (f'/{crew}' if crew else '')
    return ('jigsaw', {'orientation': 'up_north'}, {
        'id': 'minecraft:jigsaw', 'name': 'minecraft:empty', 'target': target, 'pool': 'minecraft:empty',
        'final_state': 'minecraft:air', 'joint': 'rollable'})


def chest(facing, loot=LOOT):
    return ('chest', {'facing': facing, 'type': 'single', 'waterlogged': 'false'},
            {'id': 'minecraft:chest', 'LootTable': loot, 'Items': []})


# ---------------------------------------------------------------- resident house
def resident_house():
    """A small timber house for one resident, a regular waiting outside its door.

    Seven by seven inside, one room: hearth and chimney on the west wall, a bed in the
    north-east corner, a table and two chairs, a chest and a barrel. A gabled dark oak roof
    with the ridge running east-west and eaves a block past the walls all round. The door is
    on the south wall, with a lamp post and a doorstep."""
    b = Build(11, 11, 11)
    # Walls run x=1..9, z=1..9; the ring at x=0/10 and z=0/10 is only ever eaves.
    b.fill(1, 0, 1, 9, 0, 9, 'cobblestone')                     # footing
    b.fill(1, 1, 1, 9, 1, 9, 'cobblestone')                     # floor layer: the base course
    b.fill(2, 1, 2, 8, 1, 8, 'spruce_planks')                   # the floor itself
    b.fill(2, 2, 2, 8, 4, 8, 'air')                             # the room
    # Wall infill, then the frame over it.
    for y in (2, 3, 4):
        b.fill(1, y, 1, 9, y, 1, 'spruce_planks'); b.fill(1, y, 9, 9, y, 9, 'spruce_planks')
        b.fill(1, y, 1, 1, y, 9, 'spruce_planks'); b.fill(9, y, 1, 9, y, 9, 'spruce_planks')
    for x in (1, 9):
        for z in (1, 9):
            b.fill(x, 1, z, x, 5, z, 'dark_oak_log', {'axis': 'y'})
    for x in (5,):
        b.fill(x, 2, 1, x, 4, 1, 'dark_oak_log', {'axis': 'y'})  # a post mid-wall, north
    for z in (5,):
        b.fill(9, 2, z, 9, 4, z, 'dark_oak_log', {'axis': 'y'})  # and east
    b.fill(1, 5, 1, 9, 5, 1, 'dark_oak_log', {'axis': 'x'}); b.fill(1, 5, 9, 9, 5, 9, 'dark_oak_log', {'axis': 'x'})
    b.fill(1, 5, 2, 1, 5, 8, 'dark_oak_log', {'axis': 'z'}); b.fill(9, 5, 2, 9, 5, 8, 'dark_oak_log', {'axis': 'z'})
    b.fill(2, 5, 2, 8, 5, 8, 'oak_planks')                      # ceiling, the attic floor
    # Windows.
    for x in (3, 7):
        b.set(x, 3, 1, 'glass_pane', pane('x')); b.set(x, 3, 9, 'glass_pane', pane('x'))
    for z in (3, 7):
        b.set(9, 3, z, 'glass_pane', pane('z'))
    b.set(1, 3, 3, 'glass_pane', pane('z'))
    # The door, south wall, and a doorstep outside it.
    door = {'facing': 'north', 'hinge': 'left', 'open': 'false', 'powered': 'false'}
    b.set(5, 2, 9, 'oak_door', dict(door, half='lower')); b.set(5, 3, 9, 'oak_door', dict(door, half='upper'))
    b.set(5, 1, 10, 'cobblestone')
    b.set(5, 2, 10, 'air')
    # Hearth and chimney on the west wall.
    b.fill(1, 1, 5, 1, 10, 5, 'bricks')
    b.set(1, 2, 4, 'bricks'); b.set(1, 2, 6, 'bricks')
    b.set(2, 2, 5, 'campfire', {'facing': 'north', 'lit': 'true', 'signal_fire': 'false', 'waterlogged': 'false'})
    b.set(2, 3, 5, 'bricks')
    b.set(2, 4, 5, 'bricks')
    # The roof: a gable with the ridge along x at z=5, four courses each side, eaves at z=0 and z=10.
    for k in range(5):
        y = 5 + k
        south, north = 10 - k, k
        if south == north:
            b.fill(0, y, 5, 10, y, 5, 'dark_oak_slab', {'type': 'bottom', 'waterlogged': 'false'})
            break
        b.fill(0, y, south, 10, y, south, 'dark_oak_stairs', stairs('north'))
        b.fill(0, y, north, 10, y, north, 'dark_oak_stairs', stairs('south'))
        # Under the eaves course, the wall plate shows; between the slopes, the gable ends and the attic.
        for z in range(north + 1, south):
            for x in range(0, 11):
                if y == 5 and (x in (0, 10) or z in (0, 10)):
                    continue  # the ring below the eaves stays open
                if 1 <= x <= 9 and 1 <= z <= 9 and y >= 6:
                    b.set(x, y, z, 'spruce_planks' if x in (1, 9) else 'air')
    b.fill(1, 10, 5, 1, 10, 5, 'bricks')                        # the chimney clears the ridge
    # Furniture.
    b.set(7, 2, 2, 'red_bed', {'facing': 'north', 'part': 'head', 'occupied': 'false'})
    b.set(7, 2, 3, 'red_bed', {'facing': 'north', 'part': 'foot', 'occupied': 'false'})
    b.set(*(3, 2, 2), *chest('south'))
    b.set(2, 2, 2, 'barrel', {'facing': 'up', 'open': 'false'})
    b.set(8, 2, 8, 'crafting_table')
    b.set(4, 2, 6, 'oak_fence', fence()); b.set(4, 3, 6, 'oak_pressure_plate', {'powered': 'false'})
    b.set(3, 2, 6, 'spruce_stairs', stairs('east')); b.set(5, 2, 6, 'spruce_stairs', stairs('west'))
    b.set(5, 4, 5, 'lantern', {'hanging': 'true', 'waterlogged': 'false'})
    b.set(2, 2, 8, 'flower_pot')
    b.set(8, 2, 4, 'bookshelf')
    # Outside: a lamp post by the door.
    b.set(7, 2, 10, 'oak_fence', fence()); b.set(7, 3, 10, 'lantern', {'hanging': 'false', 'waterlogged': 'false'})
    # The people: a resident inside, a regular at the door.
    b.set(*(6, 2, 4), *marker('resident'))
    b.set(*(3, 2, 10), *marker('customer'))
    return b.write('resident_house')


PIECES = {'resident_house': resident_house}

if __name__ == '__main__':
    for name, build in PIECES.items():
        print('wrote:', build())
