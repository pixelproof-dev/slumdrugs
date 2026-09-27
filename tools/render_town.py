#!/usr/bin/env python3
"""Draws a town from above, straight out of a saved world.

A town is laid by `/slum structure town` and there is no other way to see whether the streets
meet, whether a building ended up in the road, or whether the whole thing is a grey square. A
screenshot needs somebody at a keyboard; this needs a saved world and nothing else.

    python3 tools/render_town.py neoforge/run/citytest -60 -60 107 build/town.png

The arguments are the world, the town's north-west corner, its span, and where to write. Colour
comes from the topmost non-air block of each column, so the picture is a map rather than a
render: roads read as grey, roofs as whatever they are made of, ground as green.
"""
import pathlib
import re
import struct
import sys
import zlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import mcnbt  # noqa: E402
from PIL import Image  # noqa: E402

# Enough to tell a street from a roof from a field. Anything unlisted falls back on its name.
COLOURS = {
    'gray_concrete': (74, 78, 82), 'light_gray_concrete': (142, 145, 148),
    'white_concrete': (236, 238, 240), 'black_concrete': (30, 32, 34),
    'grass_block': (110, 150, 74), 'dirt': (134, 96, 67), 'water': (60, 90, 190),
    'sand': (219, 207, 163), 'stone': (128, 128, 128), 'oak_planks': (162, 130, 78),
}
FALLBACK = [
    (re.compile(r'glass|window'), (200, 226, 235)),
    (re.compile(r'leaves'), (72, 120, 56)),
    (re.compile(r'log|wood|plank'), (150, 118, 70)),
    (re.compile(r'concrete|terracotta|quartz|smooth_stone|andesite|calcite'), (186, 186, 182)),
    (re.compile(r'brick'), (150, 96, 84)),
    (re.compile(r'water'), (60, 90, 190)),
]


def colour(name):
    short = name.split(':')[-1]
    if short in COLOURS:
        return COLOURS[short]
    for pattern, rgb in FALLBACK:
        if pattern.search(short):
            return rgb
    # Deterministic grey-green for anything unnamed, so it is visible but not mistaken for road.
    h = sum(ord(c) * (i + 3) for i, c in enumerate(short))
    return (90 + h % 60, 100 + h % 50, 80 + h % 40)


def chunks(path):
    with open(path, 'rb') as f:
        header, body = f.read(4096), f.read()
    for i in range(1024):
        off = int.from_bytes(header[i * 4:i * 4 + 3], 'big')
        if off == 0:
            continue
        start = off * 4096 - 4096
        if start + 5 > len(body):
            continue
        length = struct.unpack('>i', body[start:start + 4])[0]
        comp = body[start + 4]
        raw = body[start + 5:start + 4 + length]
        try:
            if comp == 2:
                raw = zlib.decompress(raw)
            elif comp != 1:
                continue
            yield i % 32, i // 32, mcnbt.loads(raw)[1]
        except Exception:
            continue


def cells(section):
    states = section.get('block_states') or {}
    palette = states.get('palette') or []
    names = [e if isinstance(e, str) else (e.get('Name') or e.get('id') or '') for e in palette]
    if not names:
        return None, None
    data = states.get('data')
    if data is None:
        return names, [0] * 4096
    bits = max(4, (len(names) - 1).bit_length())
    per, mask = 64 // bits, (1 << bits) - 1
    out = []
    for word in data.values:
        word &= 0xFFFFFFFFFFFFFFFF
        for slot in range(per):
            if len(out) >= 4096:
                break
            out.append((word >> (slot * bits)) & mask)
    while len(out) < 4096:
        out.append(0)
    return names, out


def region_dir(world):
    for c in (world / 'dimensions/minecraft/overworld/region', world / 'region'):
        if c.is_dir():
            return c
    raise SystemExit(f'no region folder in {world}')


def draw(world, x0, z0, span, out, scale=4):
    top = {}
    reg = region_dir(pathlib.Path(world))
    cx0, cx1 = x0 >> 4, (x0 + span - 1) >> 4
    cz0, cz1 = z0 >> 4, (z0 + span - 1) >> 4
    for path in sorted(reg.glob('*.mca')):
        rx, rz = (int(v) for v in path.stem.split('.')[1:3])
        if not (rx * 32 <= cx1 and (rx + 1) * 32 > cx0 and rz * 32 <= cz1 and (rz + 1) * 32 > cz0):
            continue
        for lx, lz, data in chunks(path):
            cx, cz = rx * 32 + lx, rz * 32 + lz
            if not (cx0 <= cx <= cx1 and cz0 <= cz <= cz1):
                continue
            for section in data.get('sections', []):
                names, idx = cells(section)
                if not names:
                    continue
                base = int(section['Y']) * 16
                for i, p in enumerate(idx):
                    name = names[p]
                    if not name or name.endswith('air'):
                        continue
                    y = base + (i >> 8)
                    x, z = cx * 16 + (i & 15), cz * 16 + ((i >> 4) & 15)
                    if x0 <= x < x0 + span and z0 <= z < z0 + span and top.get((x, z), (-9999,))[0] < y:
                        top[(x, z)] = (y, name)

    img = Image.new('RGB', (span, span), (20, 20, 24))
    px = img.load()
    for (x, z), (_, name) in top.items():
        px[x - x0, z - z0] = colour(name)
    img = img.resize((span * scale, span * scale), Image.NEAREST)
    pathlib.Path(out).parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    return len(top)


if __name__ == '__main__':
    if len(sys.argv) < 6:
        raise SystemExit(__doc__.strip().splitlines()[5].strip())
    drawn = draw(sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), sys.argv[5])
    print(f'{drawn} columns drawn to {sys.argv[5]}')
