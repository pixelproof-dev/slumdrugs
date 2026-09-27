#!/usr/bin/env python3
"""Cuts a box out of a world save and writes it as a vanilla structure file.

The way a building gets out of a downloaded map and into the mod. A structure block can only
take 48 blocks per axis and WorldEdit needs a build that speaks the vanilla format, so for
anything larger than a cottage this reads the region files itself:

    python3 tools/cut_structure.py "<world dir>" trade_hall -5 -62 1 46 -35 61

Both corners are inclusive and may be given in any order. The lowest corner becomes the
piece's origin, which is the convention in docs/STRUCTURES.md, so cut one layer of earth
below the ground you want the building to stand on: footing at 0, the ground layer at 1, and
the ground offset the placer needs is then 2.

Air is not neutral. Air that is saved *deletes* what it lands on, so a raw box would punch a
rectangle through the terrain around the building. Air open to the outside of the box is
therefore dropped from the file, which is what a structure block writes for structure_void
and what both placement paths honour; air sealed inside walls is kept, so rooms and cellars
still carve. The two are told apart with a flood fill from the faces of the box.

What it does not do: markers. A piece wants a jigsaw block with target `slumdrugs:npc/<role>`
where each person stands (see docs/STRUCTURES.md), and that is a decision about the building,
not something to guess from block data.
"""
import collections
import os
import pathlib
import struct
import sys
import zlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import mcnbt  # noqa: E402

OUT = pathlib.Path(__file__).resolve().parent.parent / 'neoforge/src/main/resources/data/slumdrugs/structure'
AIR = 'minecraft:air'


def source_version(world):
    """What level.dat claims the world is, used only when the chunks do not say.

    Prefer `chunk_version` below. level.dat is rewritten the moment a newer game opens the
    world, while the chunks themselves are only upgraded when somebody flies near them, so
    after one glance at a downloaded map its level.dat says 26.3 and its chunks still say
    1.21.4. Believing it there would write a structure file stamped too new, the data fixer
    would not run, and every block renamed in between would quietly fail to load: a 1.21 map
    says `minecraft:chain`, 26.x calls it `iron_chain`, and the chains would simply be gone.
    """
    data = mcnbt.read(world / 'level.dat')['Data']
    return int(data['DataVersion'])


def region_dir(world):
    """Where the overworld's chunks live; 26.x moved them under dimensions/."""
    for c in (world / 'dimensions/minecraft/overworld/region', world / 'region'):
        if c.is_dir():
            return c
    raise SystemExit(f'no region folder in {world}')


def chunk(path, cx, cz):
    with open(path, 'rb') as f:
        header = f.read(4096)
        slot = ((cx & 31) + (cz & 31) * 32) * 4
        offset = int.from_bytes(header[slot:slot + 3], 'big')
        if offset == 0:
            return None
        f.seek(offset * 4096)
        length = struct.unpack('>i', f.read(4))[0]
        compression = f.read(1)[0]
        raw = f.read(length - 1)
    if compression == 2:
        raw = zlib.decompress(raw)
    elif compression != 1:          # 1 is gzip, which mcnbt.loads unwraps itself
        raise SystemExit(f'chunk {cx},{cz}: compression {compression} is not supported')
    return mcnbt.loads(raw)[1]


def state_name(entry):
    """A chunk palette entry, in either spelling. 26.x writes a default state as {'': id}."""
    if isinstance(entry, str):
        return entry, None, 'id'
    if 'Name' in entry:
        return entry['Name'], (dict(entry['Properties']) if entry.get('Properties') else None), 'Name'
    name = entry.get('id') or entry.get('')
    props = entry.get('properties')
    return name, (dict(props) if props else None), 'id'


def section_indices(block_states):
    """The 4096 palette indices of one section, unpacked; entries never span two longs."""
    palette = block_states.get('palette') or []
    data = block_states.get('data')
    if not palette:
        return palette, None
    if data is None:
        return palette, [0] * 4096
    bits = max(4, (len(palette) - 1).bit_length())
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
    return palette, out


def read_box(world, lo, hi):
    """Every cell of the box as (name, properties), plus the block entities in it."""
    reg = region_dir(world)
    cells, entities, spelling, versions = {}, {}, set(), set()
    for cx in range(lo[0] >> 4, (hi[0] >> 4) + 1):
        for cz in range(lo[2] >> 4, (hi[2] >> 4) + 1):
            path = reg / f'r.{cx >> 5}.{cz >> 5}.mca'
            if not path.exists():
                continue
            data = chunk(path, cx, cz)
            if not data:
                continue
            if 'DataVersion' in data:
                versions.add(int(data['DataVersion']))
            for section in data.get('sections', []):
                base = int(section['Y']) * 16
                if base > hi[1] or base + 15 < lo[1]:
                    continue
                palette, indices = section_indices(section.get('block_states') or {})
                if not palette:
                    continue
                names = [state_name(e) for e in palette]
                spelling.update(how for _, _, how in names)
                for i, index in enumerate(indices):
                    y = base + (i >> 8)
                    if not lo[1] <= y <= hi[1]:
                        continue
                    x, z = cx * 16 + (i & 15), cz * 16 + ((i >> 4) & 15)
                    if lo[0] <= x <= hi[0] and lo[2] <= z <= hi[2]:
                        cells[(x, y, z)] = names[index][:2]
            for be in data.get('block_entities') or []:
                pos = (int(be.get('x', 0)), int(be.get('y', 0)), int(be.get('z', 0)))
                if all(lo[k] <= pos[k] <= hi[k] for k in range(3)):
                    entities[pos] = {k: v for k, v in be.items() if k not in ('x', 'y', 'z', 'keepPacked')}
    return cells, entities, spelling, versions


def open_air(cells, lo, hi):
    """Air cells reachable from the faces of the box: the ones that must not be written."""
    air = {p for p, s in cells.items() if s[0] == AIR}
    outside, queue = set(), collections.deque()
    for p in air:
        if any(p[k] in (lo[k], hi[k]) for k in range(3)):
            outside.add(p)
            queue.append(p)
    while queue:
        x, y, z = queue.popleft()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            if n in air and n not in outside:
                outside.add(n)
                queue.append(n)
    return outside


def write_piece(name, size, cells, entities, skip, version, old_spelling):
    palette, index, blocks = [], {}, []
    for pos in sorted(cells, key=lambda p: (p[1], p[2], p[0])):
        if pos in skip:
            continue
        bname, props = cells[pos]
        key = (bname, tuple(sorted((props or {}).items())))
        if key not in index:
            index[key] = len(palette)
            entry = {'Name': bname} if old_spelling else {'id': bname}
            if props:
                entry['Properties' if old_spelling else 'properties'] = {k: str(v) for k, v in sorted(props.items())}
            palette.append(entry)
        block = {'pos': [mcnbt.Int(v) for v in pos], 'state': mcnbt.Int(index[key])}
        if pos in entities:
            block['nbt'] = entities[pos]
        blocks.append(block)

    OUT.mkdir(parents=True, exist_ok=True)
    mcnbt.write(OUT / f'{name}.nbt', {
        'size': [mcnbt.Int(v) for v in size],
        'entities': [],
        'blocks': blocks,
        'palette': palette,
        'DataVersion': mcnbt.Int(version),
    })
    kept_air = sum(1 for p in cells if p not in skip and cells[p][0] == AIR)
    return (f'{name} {size[0]}x{size[1]}x{size[2]}: {len(blocks)} cells written, '
            f'{len(palette)} states, {len(entities)} block entities, '
            f'{kept_air} air kept inside, {len(skip)} open air dropped, DataVersion {version}')


def cut(world, name, corner_a, corner_b, keep_air=False):
    lo = tuple(min(a, b) for a, b in zip(corner_a, corner_b))
    hi = tuple(max(a, b) for a, b in zip(corner_a, corner_b))
    size = tuple(hi[k] - lo[k] + 1 for k in range(3))

    cells, entities, spelling, versions = read_box(world, lo, hi)
    if len(versions) > 1:
        raise SystemExit(
            'the chunks in that box are not all the same version: ' + repr(sorted(versions)) +
            '. A structure file carries one DataVersion, so a mixed box cannot be fixed up '
            'correctly. Cut from an untouched copy of the map rather than one a newer game '
            'has already opened and partly upgraded.')
    version = versions.pop() if versions else source_version(world)
    # Keep the source's own spelling: a file that says one thing in a palette and
    # another in its DataVersion is a file the fixer will read wrong.
    old = 'Name' in spelling
    if not cells:
        raise SystemExit('nothing in that box — are the coordinates right, and is the area generated?')
    skip = set() if keep_air else open_air(cells, lo, hi)

    return write_piece(name, size, cells, entities, skip, version, old_spelling=old)


# ---------------------------------------------------------------- WorldEdit files

def parse_state(text):
    """`minecraft:oak_stairs[facing=north,half=top]` as a name and a property map."""
    if '[' not in text:
        return text, None
    name, _, rest = text.partition('[')
    props = {}
    for pair in rest.rstrip(']').split(','):
        if '=' in pair:
            k, _, v = pair.partition('=')
            props[k.strip()] = v.strip()
    return name, (props or None)


def varints(data):
    """The Sponge schematic block array: one varint per cell, in y, z, x order."""
    out, value, shift = [], 0, 0
    for byte in data:
        byte &= 0xFF
        value |= (byte & 0x7F) << shift
        if byte & 0x80:
            shift += 7
        else:
            out.append(value)
            value, shift = 0, 0
    return out


def from_schem(path, name, keep_air=False):
    """Converts a WorldEdit .schem, which the game cannot read, into a structure file."""
    root = mcnbt.read(path)
    root = root.get('Schematic', root)          # v3 wraps everything one level deeper
    w, h, l = int(root['Width']), int(root['Height']), int(root['Length'])
    version = int(root.get('DataVersion', 0))
    blocks_tag = root.get('Blocks') or root
    palette_tag = blocks_tag['Palette']
    raw = blocks_tag.get('BlockData') or blocks_tag.get('Data')

    by_id = {}
    for text, idx in palette_tag.items():
        by_id[int(idx)] = parse_state(text)
    ids = varints(raw.values)
    if len(ids) != w * h * l:
        raise SystemExit(f'{path.name}: {len(ids)} cells for a {w}x{h}x{l} box')

    cells = {}
    for i, sid in enumerate(ids):
        y, rest = divmod(i, w * l)
        z, x = divmod(rest, w)
        cells[(x, y, z)] = by_id[sid]

    entities = {}
    for be in root.get('BlockEntities') or blocks_tag.get('BlockEntities') or []:
        pos = [int(v) for v in be['Pos'].values]
        rest = {k: v for k, v in be.items() if k not in ('Pos', 'Id')}
        rest['id'] = be.get('Id', rest.get('id'))
        entities[tuple(pos)] = rest

    lo, hi = (0, 0, 0), (w - 1, h - 1, l - 1)
    skip = set() if keep_air else open_air(cells, lo, hi)
    # The palette spelling has to match the version the file declares, because that is what
    # decides whether the game runs its fixer over it at all: 26.3 writes id/properties and
    # reads a Name entry as air, while anything older is fixed up on load and must keep the
    # old spelling. A schematic carries no chunks to read the spelling off, so it comes from
    # the version. Getting this wrong is silent -- the piece places as a hole in the ground.
    return write_piece(name, (w, h, l), cells, entities, skip, version,
                       old_spelling=version < 5023)


if __name__ == '__main__':
    first = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if first is not None and first.suffix in ('.schem', '.schematic'):
        print(from_schem(first, sys.argv[2], '--keep-air' in sys.argv))
        raise SystemExit
    if len(sys.argv) < 9:
        raise SystemExit(__doc__.strip().splitlines()[4].strip())
    world = pathlib.Path(sys.argv[1])
    name = sys.argv[2]
    nums = [int(v) for v in sys.argv[3:9]]
    print(cut(world, name, tuple(nums[:3]), tuple(nums[3:]), '--keep-air' in sys.argv))
