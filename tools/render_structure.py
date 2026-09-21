#!/usr/bin/env python3
"""Draws a structure file as an isometric picture with the game's own block textures, so a
generated building can be looked at without a client.

    python3 tools/render_structure.py                       # every piece
    python3 tools/render_structure.py resident_house        # one piece

Writes to build/structure-previews/:
    <name>.png          from the south-east, whole
    <name>_back.png     from the north-west
    <name>_cut.png      from the south-east with the roof and the south wall removed
    <name>_plan_y<n>.png  one plan per floor, straight down

Blocks are drawn as their shapes, not as cubes: stairs, slabs, panes, doors, fences, lanterns,
beds and the rest are built from eighth-of-a-block cells, and every cell face carries the
matching part of the texture. Faces hidden behind other cells are skipped. It needs the
Minecraft jar on disk (after `./gradlew build`) for the textures; the mod's own textures are
read from the resources. This is a picture of the massing and the layout, not of lighting.
"""
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools'))
import mcnbt  # noqa: E402
from render_models import read_png, write_png  # noqa: E402

STRUCTURES = ROOT / 'neoforge/src/main/resources/data/slumdrugs/structure'
OUT = ROOT / 'build/structure-previews'
N = 8  # cells per block edge
SKIP = {'air', 'structure_void', 'cave_air', 'void_air'}

# ---------------------------------------------------------------- textures
_jar = None
_cache = {}


def jar():
    global _jar
    if _jar is None:
        jars = [j for j in sorted((ROOT / 'neoforge/build/moddev/artifacts').glob('minecraft-patched-*.jar'))
                if not any(k in j.name for k in ('sources', 'merged'))]
        if not jars:
            sys.exit('Run ./gradlew build first so the Minecraft jar is available for textures.')
        _jar = zipfile.ZipFile(jars[0])
    return _jar


def texture(name):
    """A 16x16 RGBA grid for a texture name, or None. Ours are looked up first."""
    if name in _cache: return _cache[name]
    px = None
    ours = ROOT / f'neoforge/src/main/resources/assets/slumdrugs/textures/block/{name}.png'
    tmp = pathlib.Path('/tmp/slumdrugs-render-tex'); tmp.mkdir(exist_ok=True)
    if ours.exists():
        px = read_png(ours)[2]
    else:
        member = f'assets/minecraft/textures/block/{name}.png'
        if member in jar().namelist():
            dest = tmp / f'{name}.png'
            dest.write_bytes(jar().read(member))
            px = read_png(dest)[2]
    _cache[name] = px
    return px


def tint(px, rgb):
    return [[(int(p[0] * rgb[0]), int(p[1] * rgb[1]), int(p[2] * rgb[2]), p[3]) for p in row] for row in px]


WOODS = ('dark_oak', 'oak', 'spruce', 'birch', 'jungle', 'acacia', 'mangrove', 'cherry', 'bamboo', 'crimson', 'warped', 'pale_oak')


def textures_for(name, props):
    """(top, side) textures for a block, by name and a little knowledge of how vanilla names them."""
    def t(n): return texture(n)
    for wood in WOODS:
        if name == f'{wood}_log' or name == f'stripped_{wood}_log':
            return t(name + '_top'), t(name)
        if name in (f'{wood}_planks', f'{wood}_stairs', f'{wood}_slab', f'{wood}_fence', f'{wood}_fence_gate', f'{wood}_pressure_plate', f'{wood}_trapdoor'):
            planks = t(f'{wood}_planks')
            return (t(f'{wood}_trapdoor'), t(f'{wood}_trapdoor')) if name.endswith('trapdoor') else (planks, planks)
        if name == f'{wood}_door':
            return t(f'{wood}_door_top'), t(f'{wood}_door_top' if props.get('half') == 'upper' else f'{wood}_door_bottom')
    if name.endswith('_stairs') or name.endswith('_slab'):
        base = name.rsplit('_', 1)[0]
        base = {'cobblestone': 'cobblestone', 'stone_brick': 'stone_bricks', 'brick': 'bricks', 'smooth_stone': 'smooth_stone'}.get(base, base)
        return t(base), t(base)
    if name == 'grass_block':
        return tint(t('grass_block_top') or [], (0.55, 0.8, 0.35)), t('grass_block_side')
    if name == 'glass_pane': return t('glass'), t('glass')
    if name == 'campfire': return t('campfire_fire'), t('campfire_log')
    if name.endswith('_bed'): return t(name[:-4] + '_wool'), t(name[:-4] + '_wool')
    if name.endswith('_carpet'): return t(name[:-7] + '_wool'), t(name[:-7] + '_wool')
    if name == 'chest': return tint(t('oak_planks') or [], (0.9, 0.75, 0.5)), tint(t('oak_planks') or [], (0.8, 0.65, 0.4))
    if name == 'barrel': return t('barrel_top'), t('barrel_side')
    if name == 'crafting_table': return t('crafting_table_top'), t('crafting_table_side')
    if name == 'bookshelf': return t('oak_planks'), t('bookshelf')
    if name == 'jigsaw': return t('jigsaw_top'), t('jigsaw_side')
    if name == 'chiseled_bookshelf': return t('chiseled_bookshelf_top'), t('chiseled_bookshelf_side')
    top = t(name + '_top') or t(name)
    side = t(name + '_side') or t(name)
    return top, side


# ---------------------------------------------------------------- shapes
def cells_for(name, props):
    """Which of the N^3 cells a block fills, as a set of (cx, cy, cz)."""
    full = {(x, y, z) for x in range(N) for y in range(N) for z in range(N)}
    h = N // 2
    if name.endswith('_slab'):
        lo = props.get('type') != 'top'
        return {c for c in full if (c[1] < h) == lo}
    if name.endswith('_stairs'):
        f = props.get('facing', 'north'); top = props.get('half') == 'top'
        def high(c):
            x, y, z = c
            return {'north': z < h, 'south': z >= h, 'west': x < h, 'east': x >= h}[f]
        return {c for c in full if ((c[1] >= h) if top else (c[1] < h)) or high(c)}
    if name == 'glass_pane' or name.endswith('_pane'):
        ns = props.get('north') == 'true' or props.get('south') == 'true'
        ew = props.get('east') == 'true' or props.get('west') == 'true'
        out = set()
        for x, y, z in full:
            centre = h - 1 <= x <= h and h - 1 <= z <= h
            if centre or (ns and h - 1 <= x <= h) or (ew and h - 1 <= z <= h):
                out.add((x, y, z))
        return out
    if name.endswith('_fence') or name.endswith('_wall'):
        out = {c for c in full if h - 1 <= c[0] <= h and h - 1 <= c[2] <= h}
        for side, cond in (('north', lambda c: c[2] < h), ('south', lambda c: c[2] >= h), ('west', lambda c: c[0] < h), ('east', lambda c: c[0] >= h)):
            if props.get(side) == 'true':
                out |= {c for c in full if cond(c) and h - 1 <= (c[0] if side in ('north', 'south') else c[2]) <= h and 3 <= c[1] <= 6}
        return out
    if name.endswith('_door'):
        f = props.get('facing', 'north')
        # A closed door lies against the face opposite to the way it faces.
        return {c for c in full if {'north': c[2] >= N - 2, 'south': c[2] < 2, 'east': c[0] < 2, 'west': c[0] >= N - 2}[f]}
    if name == 'lantern':
        hang = props.get('hanging') == 'true'
        return {c for c in full if 2 <= c[0] <= 5 and 2 <= c[2] <= 5 and ((3 <= c[1] <= 7) if hang else (c[1] <= 4))}
    if name == 'campfire': return {c for c in full if c[1] < 2}
    if name.endswith('_bed'): return {c for c in full if c[1] < 4}
    if name.endswith('_pressure_plate'): return {c for c in full if c[1] < 1}
    if name.endswith('_carpet'): return {c for c in full if c[1] < 1}
    if name == 'flower_pot': return {c for c in full if 3 <= c[0] <= 4 and 3 <= c[2] <= 4 and c[1] < 3}
    if name == 'chest': return {c for c in full if 1 <= c[0] <= 6 and 1 <= c[2] <= 6 and c[1] < 7}
    if name.endswith('_trapdoor'):
        if props.get('open') == 'true':
            f = props.get('facing', 'north')
            return {c for c in full if {'north': c[2] >= N - 2, 'south': c[2] < 2, 'east': c[0] < 2, 'west': c[0] >= N - 2}[f]}
        upper = props.get('half') == 'top'
        return {c for c in full if ((c[1] >= N - 2) if upper else (c[1] < 2))}
    if name == 'ladder':
        f = props.get('facing', 'north')
        return {c for c in full if {'north': c[2] >= N - 1, 'south': c[2] < 1, 'east': c[0] < 1, 'west': c[0] >= N - 1}[f]}
    if name in ('iron_chain', 'chain'): return {c for c in full if 3 <= c[0] <= 4 and 3 <= c[2] <= 4}
    return full


# ---------------------------------------------------------------- loading
def load(path):
    root = mcnbt.read(path)
    pal = root['palette']
    sx, sy, sz = [int(v) for v in root['size']]
    blocks = {}
    for b in root['blocks']:
        entry = pal[int(b['state'])]
        name = (entry.get('id') or entry.get('Name', 'air')).split(':', 1)[-1]
        if name in SKIP: continue
        props = {k: str(v) for k, v in (entry.get('properties') or entry.get('Properties') or {}).items()}
        x, y, z = [int(v) for v in b['pos']]
        blocks[(x, y, z)] = (name, props)
    return (sx, sy, sz), blocks


def cells(blocks):
    """Every filled cell in the world's cell grid, with its top and side textures."""
    out = {}
    for (bx, by, bz), (name, props) in blocks.items():
        top, side = textures_for(name, props)
        if top is None and side is None: top = side = [[(230, 60, 200, 255)] * 16 for _ in range(16)]
        top = top or side; side = side or top
        for (cx, cy, cz) in cells_for(name, props):
            out[(bx * N + cx, by * N + cy, bz * N + cz)] = (top, side, cx, cy, cz)
    return out


# ---------------------------------------------------------------- drawing
def render(size, grid, cell=48, view='se'):
    """Isometric. 'se' looks from the south-east (x right-down, z left-down); 'nw' from the north-west."""
    sx, sy, sz = size
    s = cell / N  # pixels per cell edge
    W = int((sx + sz) * cell) + cell
    H = int((sx + sz) * cell / 2 + sy * cell) + cell
    buf = [bytearray((26, 26, 30) * W) for _ in range(H)]

    def flip(p):
        x, y, z = p
        return (sx * N - x, y, sz * N - z) if view == 'nw' else (x, y, z)

    def project(p):
        x, y, z = flip(p)
        px = cell / 2 + sz * cell + (x - z) * s
        py = cell / 2 + (x + z) * s / 2 + (sy * N - y) * s
        return px, py

    def put(px, py, c, alpha):
        if 0 <= px < W and 0 <= py < H:
            if alpha >= 250:
                buf[py][px * 3:px * 3 + 3] = bytes(c)
            else:
                a = alpha / 255.0
                old = buf[py][px * 3:px * 3 + 3]
                buf[py][px * 3:px * 3 + 3] = bytes(int(old[i] * (1 - a) + c[i] * a) for i in range(3))

    def face(p0, du, dv, tex, u0, v0, shade):
        """The parallelogram from p0 spanning du and dv (3D), textured from tex at cell offset (u0, v0)."""
        a = project(p0); b = project((p0[0] + du[0], p0[1] + du[1], p0[2] + du[2])); c = project((p0[0] + dv[0], p0[1] + dv[1], p0[2] + dv[2]))
        ax, ay = b[0] - a[0], b[1] - a[1]
        bx, by = c[0] - a[0], c[1] - a[1]
        det = ax * by - ay * bx
        if abs(det) < 1e-9: return
        xs = [a[0], b[0], c[0], b[0] + bx, ]; ys = [a[1], b[1], c[1], b[1] + by]
        for py in range(int(min(ys)), int(max(ys)) + 2):
            for px in range(int(min(xs)), int(max(xs)) + 2):
                qx, qy = px + 0.5 - a[0], py + 0.5 - a[1]
                u = (qx * by - qy * bx) / det
                v = (ax * qy - ay * qx) / det
                if 0 <= u < 1 and 0 <= v < 1:
                    tx = min(15, int((u0 + u) * 16 / N)); ty = min(15, int((v0 + v) * 16 / N))
                    r, g, bl, al = tex[ty][tx]
                    if al < 16: continue
                    put(px, py, (int(r * shade), int(g * shade), int(bl * shade)), al)

    # Which faces the camera sees: top, plus +x and +z for 'se', -x and -z for 'nw'.
    dx, dz = (1, 1) if view == 'se' else (-1, -1)
    order = sorted(grid, key=lambda p: (flip(p)[0] + flip(p)[2], p[1]))
    for (x, y, z) in order:
        top, side, cx, cy, cz = grid[(x, y, z)]
        if (x, y + 1, z) not in grid:
            face((x, y + 1, z), (1, 0, 0), (0, 0, 1), top, cx, cz, 1.0)
        if (x + dx, y, z) not in grid:
            x0 = x + 1 if dx > 0 else x
            face((x0, y + 1, z), (0, 0, 1), (0, -1, 0), side, cz, N - 1 - cy, 0.62 if view == 'se' else 0.8)
        if (x, y, z + dz) not in grid:
            z0 = z + 1 if dz > 0 else z
            face((x, y + 1, z0), (1, 0, 0), (0, -1, 0), side, cx, N - 1 - cy, 0.8 if view == 'se' else 0.62)
    return W, H, buf


def plan(size, blocks, y, cell=24):
    """One floor, straight down: the blocks at height y, and the floor below them faintly."""
    sx, sy, sz = size
    W, H = sx * cell, sz * cell
    buf = [bytearray((26, 26, 30) * W) for _ in range(H)]
    for (bx, by, bz), (name, props) in sorted(blocks.items(), key=lambda kv: kv[0][1]):
        if by not in (y - 1, y): continue
        top, side = textures_for(name, props)
        tex = top or side
        if tex is None: continue
        shade = 1.0 if by == y else 0.35
        filled = cells_for(name, props)
        for cx in range(N):
            for cz in range(N):
                if not any((cx, cy, cz) in filled for cy in range(N)): continue
                for py in range(int(bz * cell + cz * cell / N), int(bz * cell + (cz + 1) * cell / N)):
                    for px in range(int(bx * cell + cx * cell / N), int(bx * cell + (cx + 1) * cell / N)):
                        r, g, b, a = tex[min(15, cz * 2)][min(15, cx * 2)]
                        if a < 16: continue
                        buf[py][px * 3:px * 3 + 3] = bytes((int(r * shade), int(g * shade), int(b * shade)))
    return W, H, buf


def cutaway(size, blocks):
    """The roof and everything above the ceiling gone, and the south wall, to see the room."""
    sx, sy, sz = size
    top = None
    for y in range(2, sy):
        layer = [p for p in blocks if p[1] == y]
        if len(layer) >= (sx - 4) * (sz - 4):
            top = y
            break
    return {p: v for p, v in blocks.items() if (top is None or p[1] < top) and p[2] < sz - 2}


def main(names):
    OUT.mkdir(parents=True, exist_ok=True)
    files = [STRUCTURES / f'{n}.nbt' for n in names] if names else sorted(STRUCTURES.glob('*.nbt'))
    for f in files:
        size, blocks = load(f)
        grid = cells(blocks)
        for view, suffix in (('se', ''), ('nw', '_back')):
            w, h, buf = render(size, grid, view=view)
            write_png(OUT / f'{f.stem}{suffix}.png', w, h, buf)
        w, h, buf = render(size, cells(cutaway(size, blocks)))
        write_png(OUT / f'{f.stem}_cut.png', w, h, buf)
        floors = sorted({y for (_, y, _) in blocks})
        for y in floors:
            if not any(p[1] == y and blocks[p][0] not in ('cobblestone',) for p in blocks): continue
            w, h, buf = plan(size, blocks, y)
            write_png(OUT / f'{f.stem}_plan_y{y}.png', w, h, buf)
        print('rendered', f.stem, size, len(blocks), 'blocks,', len(grid), 'cells')


if __name__ == '__main__':
    main(sys.argv[1:])
