"""Authors the mod's block textures as code: 16x16, hard edges, fixed palettes.
Deterministic, so a regenerated texture is byte-identical and a change is a real change."""
import struct, zlib, pathlib

W = H = 16

def png(path, px):
    raw = b''.join(b'\x00' + bytes(v for p in row for v in p) for row in px)
    def chunk(t, d):
        c = t + d
        return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'
        + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0))
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

def grid(fill=(0, 0, 0, 0)):
    return [[fill]*W for _ in range(H)]

def noise(x, y, salt=0):
    """Deterministic value in 0..255 — stands in for hand-placed grain."""
    n = (x*374761393 + y*668265263 + salt*362437) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    return (n ^ (n >> 16)) & 255

OUT = pathlib.Path('neoforge/src/main/resources/assets/slumdrugs/textures/block')
OUT.mkdir(parents=True, exist_ok=True)

# ---------------------------------------------------------------- centrifuge drum
# Curved brass: a bright vertical highlight left of centre, darkening to both edges,
# a riveted seam, and a band across the middle. Tiles left to right.
DARK, MID, LIT, HI, TARNISH = (92,48,30,255), (150,86,48,255), (186,116,68,255), (222,164,104,255), (78,74,52,255)
px = grid()
# Smooth columnar shading with no random grain: scattered flecks read as wood at this size,
# an even falloff from a single highlight reads as rolled metal.
COLUMN = [MID, LIT, HI, HI, LIT, MID, MID, DARK, DARK, MID, MID, LIT, MID, DARK, DARK, DARK]
for y in range(H):
    for x in range(W):
        px[y][x] = COLUMN[x]
# Two riveted bands, top and bottom, plus a seam: horizontal repetition says metal, not stave.
for band in (2, 12):
    for x in range(W):
        px[band][x] = DARK
        px[band+1][x] = HI if x % 3 == 1 else MID
for y in range(4, 12):
    px[y][8] = DARK
    px[y][9] = MID
for x in range(W):
    if noise(x, 15, 7) < 30: px[15][x] = TARNISH
png(OUT/'centrifuge_drum.png', px)

# ---------------------------------------------------------------- centrifuge base
# Cast iron plinth: two vertical ribs, bolt heads along the bottom, grime in the recesses.
IDARK, IMID, ILIT, RUST = (30,30,36,255), (48,48,56,255), (70,70,80,255), (96,64,44,255)
px = grid()
for y in range(H):
    for x in range(W):
        c = IMID if noise(x, y, 2) > 40 else IDARK
        if x in (3, 4, 11, 12): c = ILIT if x in (3, 11) else IMID   # ribs, lit on one side
        px[y][x] = c
for x in range(1, W, 4):                                 # bolts along the lower edge
    px[13][x] = ILIT; px[14][x] = IDARK
for x in range(W):
    if noise(x, 0, 9) < 40: px[10][x] = RUST
png(OUT/'centrifuge_base.png', px)

# ---------------------------------------------------------------- centrifuge lid
# Seen from above: a round hatch inset in a square plate, four corner bolts, a hinge.
px = grid()
for y in range(H):
    for x in range(W):
        px[y][x] = MID if noise(x, y, 3) > 30 else LIT
for y in range(H):
    for x in range(W):
        r = ((x-7.5)**2 + (y-7.5)**2) ** 0.5
        if r < 5.6: px[y][x] = DARK if r > 4.6 else (MID if noise(x, y, 4) > 60 else LIT)
        if 5.6 <= r < 6.4: px[y][x] = HI                  # raised rim catching light
for x, y in ((2,2),(13,2),(2,13),(13,13)):                # corner bolts
    px[y][x] = HI; px[y+1][x] = DARK
for x in range(6, 10): px[1][x] = DARK                     # hinge
png(OUT/'centrifuge_lid.png', px)

# ---------------------------------------------------------------- shared ironwork
px = grid()
for y in range(H):
    for x in range(W):
        px[y][x] = IDARK if noise(x, y, 5) > 60 else IMID
for x, y in ((4,3),(11,9),(6,12)):                         # a few rivets, off grid
    px[y][x] = ILIT; px[y+1][x] = IDARK
for x in range(W):
    if 5 <= x <= 9 and noise(x, 6, 8) < 90: px[6][x] = ILIT   # a rubbed-bare scuff
png(OUT/'press_iron.png', px)

# ---------------------------------------------------------------- shared timber
WD, WM, WL, KNOT = (74,52,32,255), (104,74,46,255), (132,98,62,255), (58,38,24,255)
px = grid()
for x in range(W):
    base = WL if noise(x, 0, 6) > 128 else WM
    for y in range(H):
        c = base
        if noise(x, y, 11) < 30: c = WD                    # grain flecks
        px[y][x] = c
for y in range(H):                                          # straight grain lines
    for x in (2, 7, 12):
        if noise(x, y, 12) > 60: px[y][x] = WD
for dy in range(-1, 2):                                     # one knot
    for dx in range(-1, 2):
        px[9+dy][5+dx] = KNOT if abs(dx)+abs(dy) < 2 else WD
png(OUT/'station_timber.png', px)

# ---------------------------------------------------------------- hanging bundles
# Three bunches tied at the top, leaves splaying to a ragged lower edge. Transparent around
# them, which is what stops them reading as flat boards.
LEAF_D, LEAF_M, LEAF_L, TWINE = (52,62,34,255), (74,88,44,255), (96,112,58,255), (150,126,84,255)
px = grid()
bunches = ((1, 5, 13), (6, 5, 15), (11, 5, 12))          # x start, width, how far down it hangs
for bx, bw, depth in bunches:
    for y in range(1, depth):
        taper = (y - 1) / max(1, depth - 2)
        half = bw/2 * (0.45 + 0.55*taper)                # narrow at the tie, wide at the tips
        for x in range(bx, bx+bw):
            off = abs(x - (bx + bw/2 - 0.5))
            if off > half: continue
            if y > depth - 4 and noise(x, y, 21) < 90: continue   # ragged tips, not a straight cut
            c = LEAF_L if off < half*0.4 else (LEAF_M if off < half*0.75 else LEAF_D)
            if noise(x, y, 22) < 40: c = LEAF_D
            px[y][x] = c
    for x in range(bx+1, bx+bw-1):                        # the twine binding
        px[1][x] = TWINE
        px[2][x] = TWINE if noise(x, 2, 23) > 100 else LEAF_D
png(OUT/'loft_bundle.png', px)

print("wrote:", ", ".join(sorted(p.name for p in OUT.glob('*.png'))))
