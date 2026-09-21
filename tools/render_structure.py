#!/usr/bin/env python3
"""Draws a structure file as an isometric picture, so a generated building can be looked at
without a client. Blocks are coloured by material, not textured: this shows massing,
proportion and where things are, not how it looks in the game.

    python3 tools/render_structure.py                       # every piece, two views each
    python3 tools/render_structure.py resident_house        # one piece

Writes build/structure-previews/<name>.png (from the south-east) and <name>_cut.png (the
same with the roof and the south wall removed, to see inside).
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools'))
import mcnbt  # noqa: E402
from render_models import write_png  # noqa: E402

STRUCTURES = ROOT / 'neoforge/src/main/resources/data/slumdrugs/structure'
OUT = ROOT / 'build/structure-previews'

COLOURS = {
    'cobblestone': (128, 128, 128), 'mossy_cobblestone': (110, 128, 100), 'stone_bricks': (120, 120, 118),
    'bricks': (150, 84, 68), 'dirt': (120, 85, 58), 'grass_block': (95, 140, 60), 'gravel': (130, 126, 122),
    'oak_planks': (178, 144, 88), 'spruce_planks': (118, 88, 52), 'dark_oak_planks': (68, 44, 24),
    'dark_oak_log': (54, 36, 20), 'oak_log': (110, 88, 56), 'dark_oak_stairs': (72, 48, 26), 'dark_oak_slab': (72, 48, 26),
    'spruce_stairs': (118, 88, 52), 'spruce_slab': (118, 88, 52), 'cobblestone_slab': (128, 128, 128),
    'glass_pane': (190, 225, 235), 'glass': (190, 225, 235), 'oak_door': (160, 124, 70), 'oak_fence': (160, 124, 70),
    'oak_trapdoor': (160, 124, 70), 'oak_pressure_plate': (170, 134, 80), 'lantern': (255, 210, 110),
    'campfire': (255, 140, 40), 'red_bed': (190, 40, 40), 'chest': (150, 110, 60), 'barrel': (130, 96, 56),
    'crafting_table': (140, 100, 60), 'bookshelf': (160, 120, 70), 'flower_pot': (170, 96, 66),
    'jigsaw': (230, 60, 200), 'red_carpet': (190, 40, 40), 'brown_carpet': (110, 70, 40), 'ladder': (150, 118, 70),
}
SKIP = {'air', 'structure_void', 'cave_air', 'void_air'}


def colour(name):
    n = name.split(':', 1)[-1]
    if n in COLOURS: return COLOURS[n]
    for k, c in COLOURS.items():
        if k in n: return c
    return (200, 200, 200)


def load(path):
    root = mcnbt.read(path)
    pal = root['palette']
    sx, sy, sz = [int(v) for v in root['size']]
    voxels = {}
    for b in root['blocks']:
        entry = pal[int(b['state'])]
        name = (entry.get('id') or entry.get('Name', 'air')).split(':', 1)[-1]
        if name in SKIP: continue
        x, y, z = [int(v) for v in b['pos']]
        voxels[(x, y, z)] = name
    return (sx, sy, sz), voxels


def render(size, voxels, cell=12):
    """Isometric from the south-east: x grows to the right-down, z to the left-down, y up."""
    sx, sy, sz = size
    hw, hh = cell, cell // 2
    W = (sx + sz) * hw + 2 * cell
    H = (sx + sz) * hh + sy * cell + 2 * cell
    buf = [bytearray((30, 30, 34) * W) for _ in range(H)]
    ox = sz * hw + cell
    oy = cell

    def project(x, y, z):
        # screen x: +x right, +z left; screen y: down with x and z, up with y
        px = ox + (x - z) * hw
        py = oy + (x + z) * hh + (sy - 1 - y) * cell
        return px, py

    def put(px, py, c):
        if 0 <= px < W and 0 <= py < H:
            buf[py][px * 3:px * 3 + 3] = bytes(c)

    def shade(c, f):
        return tuple(max(0, min(255, int(v * f))) for v in c)

    # Painter's order: far to near. Farther means smaller x+z, and lower y draws first.
    for (x, y, z) in sorted(voxels, key=lambda p: (p[0] + p[2], p[1])):
        c = colour(voxels[(x, y, z)])
        px, py = project(x, y, z)
        # Top face: a rhombus centred at (px, py), width 2*hw, height 2*hh.
        for dy in range(-hh, hh + 1):
            span = hw - abs(dy) * 2
            for dx in range(-span, span + 1):
                put(px + dx, py + dy, shade(c, 1.0))
        # Right face (+x side) and left face (+z side), each a parallelogram of height cell.
        for dx in range(0, hw + 1):
            top = py + dx // 2
            for dy in range(0, cell):
                put(px + dx, top + dy, shade(c, 0.62))
        for dx in range(-hw, 1):
            top = py + (-dx) // 2
            for dy in range(0, cell):
                put(px + dx, top + dy, shade(c, 0.8))
    return W, H, buf


def cutaway(size, voxels):
    """Everything above the ceiling and the whole south wall gone, to see the room."""
    sx, sy, sz = size
    ys = sorted({y for (_, y, _) in voxels})
    # The ceiling is the highest full layer of interior blocks; take everything from there up.
    top = None
    for y in ys:
        layer = [p for p in voxels if p[1] == y]
        if len(layer) >= (sx - 4) * (sz - 4) and y > 1:
            top = y
            break
    keep = {p: n for p, n in voxels.items() if (top is None or p[1] < top) and p[2] < sz - 2}
    return keep


def main(names):
    OUT.mkdir(parents=True, exist_ok=True)
    files = [STRUCTURES / f'{n}.nbt' for n in names] if names else sorted(STRUCTURES.glob('*.nbt'))
    for f in files:
        size, voxels = load(f)
        w, h, buf = render(size, voxels)
        write_png(OUT / f'{f.stem}.png', w, h, buf)
        w, h, buf = render(size, cutaway(size, voxels))
        write_png(OUT / f'{f.stem}_cut.png', w, h, buf)
        print('rendered', f.stem, size, len(voxels), 'blocks')


if __name__ == '__main__':
    main(sys.argv[1:])
