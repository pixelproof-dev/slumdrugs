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

# ================================================================ station palette
# The second pass: every station gets its own material rather than a vanilla stand-in, so
# the set reads as one workshop. Flat, low-noise surfaces, because the models stretch them
# over faces of every size; the detail lives in the geometry.

def flat(base, grain=None, salt=0, amount=25, fleck=None, fleck_rate=18):
    px = grid()
    for y in range(H):
        for x in range(W):
            c = base
            if grain is not None and noise(x, y, salt) < amount: c = grain
            if fleck is not None and noise(x, y, salt + 100) < fleck_rate: c = fleck
            px[y][x] = c
    return px

def rows(px, ys, colour):
    for y in ys:
        for x in range(W): px[y][x] = colour
    return px

def cols(px, xs, colour):
    for x in xs:
        for y in range(H): px[y][x] = colour
    return px

# Brass: warm yellow metal with a soft vertical highlight and a dark seam.
BR_D, BR_M, BR_L, BR_H = (108, 78, 30, 255), (160, 122, 48, 255), (200, 160, 74, 255), (236, 206, 120, 255)
px = grid()
COL = [BR_M, BR_L, BR_H, BR_H, BR_L, BR_L, BR_M, BR_M, BR_D, BR_M, BR_M, BR_L, BR_L, BR_M, BR_D, BR_D]
for y in range(H):
    for x in range(W): px[y][x] = COL[x]
png(OUT/'brass.png', px)
# A brass band with rivets, for rings and rails.
px = [row[:] for row in px]
rows(px, (0, 15), BR_D); rows(px, (1, 14), BR_H)
for x in range(2, W, 4): px[7][x] = BR_H; px[8][x] = BR_D
png(OUT/'brass_band.png', px)

# Copper: the still's skin. Rosier than brass, with a hammered look.
CU_D, CU_M, CU_L, CU_H = (122, 62, 40, 255), (176, 96, 60, 255), (214, 130, 84, 255), (240, 176, 124, 255)
px = grid()
for y in range(H):
    for x in range(W):
        n = noise(x // 2, y // 2, 31)
        px[y][x] = CU_H if n < 40 else CU_L if n < 120 else CU_M if n < 220 else CU_D
        if (x + y) % 8 == 0: px[y][x] = CU_D
png(OUT/'copper.png', px)

# Cast iron: near-black, faintly speckled, for frames, screws and fireboxes.
FE_D, FE_M, FE_L = (26, 26, 30, 255), (44, 44, 50, 255), (72, 72, 80, 255)
png(OUT/'iron.png', flat(FE_M, FE_D, 41, 60, FE_L, 8))

# Walnut: the dark furniture wood, straight grain.
WN_D, WN_M, WN_L = (46, 30, 20, 255), (74, 48, 30, 255), (100, 68, 42, 255)
px = flat(WN_M, WN_D, 51, 30)
cols(px, (3, 9, 14), WN_D); cols(px, (5, 11), WN_L)
png(OUT/'walnut.png', px)

# Pine: pale workshop wood.
PN_D, PN_M, PN_L = (150, 116, 70, 255), (196, 158, 100, 255), (222, 190, 130, 255)
px = flat(PN_M, PN_D, 61, 24)
cols(px, (2, 8, 13), PN_D); cols(px, (4, 10), PN_L)
png(OUT/'pine.png', px)

# End grain: the butcher block's top, a checker of pale and mid squares with rings.
px = grid()
for y in range(H):
    for x in range(W):
        cell = ((x // 4) + (y // 4)) % 2
        px[y][x] = PN_L if cell else PN_M
        cx, cy = (x % 4) - 1.5, (y % 4) - 1.5
        if 1.0 < (cx * cx + cy * cy) ** 0.5 < 1.8: px[y][x] = PN_D
png(OUT/'end_grain.png', px)

# Terracotta: the pots.
TC_D, TC_M, TC_L = (150, 78, 50, 255), (196, 110, 72, 255), (222, 146, 104, 255)
px = flat(TC_M, TC_D, 71, 20, TC_L, 10)
rows(px, (2,), TC_L); rows(px, (3,), TC_D)
png(OUT/'terracotta.png', px)

# Green leather: the clerk's desk inset.
GL_D, GL_M, GL_L = (30, 56, 40, 255), (44, 80, 56, 255), (62, 104, 72, 255)
px = flat(GL_M, GL_D, 81, 35, GL_L, 6)
for x in range(0, W, 5):
    for y in range(0, H, 5): px[y][x] = GL_L
png(OUT/'leather_green.png', px)

# Red wax: the seal, in a pot and on a parcel.
WX_D, WX_M, WX_L = (120, 22, 26, 255), (176, 36, 40, 255), (222, 74, 70, 255)
px = flat(WX_M, WX_D, 91, 18, WX_L, 10)
png(OUT/'wax.png', px)

# Paper: parcel wrapping, with a string cross and a wax spot on the top face texture.
PP_D, PP_M, PP_L = (176, 156, 118, 255), (214, 196, 156, 255), (236, 222, 186, 255)
px = flat(PP_M, PP_D, 101, 14, PP_L, 20)
png(OUT/'paper.png', px)
px = [row[:] for row in px]
rows(px, (7, 8), (120, 96, 60, 255)); cols(px, (7, 8), (120, 96, 60, 255))
for y in range(5, 11):
    for x in range(5, 11):
        if ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 < 2.6: px[y][x] = WX_M if (x + y) % 5 else WX_L
png(OUT/'parcel_top.png', px)

# Glass: pale, mostly clear, a diagonal highlight.
px = grid((190, 220, 230, 70))
for i in range(W):
    if 0 <= i - 3 < W: px[i][i - 3] = (255, 255, 255, 150)
    if 0 <= i - 4 < W: px[i][i - 4] = (255, 255, 255, 110)
for y in range(H): px[y][0] = px[y][15] = (150, 190, 200, 120)
for x in range(W): px[0][x] = px[15][x] = (150, 190, 200, 120)
png(OUT/'glass.png', px)

# Soil: dark, damp, a few pale grit flecks.
SO_D, SO_M, SO_L = (40, 30, 22, 255), (62, 46, 32, 255), (92, 72, 48, 255)
png(OUT/'soil.png', flat(SO_M, SO_D, 111, 60, SO_L, 8))

# Herb: a dense leaf mass for pressed cakes and canopy.
HB_D, HB_M, HB_L = (44, 66, 34, 255), (68, 98, 48, 255), (104, 136, 66, 255)
png(OUT/'herb.png', flat(HB_M, HB_D, 121, 70, HB_L, 30))

# Powder: the cutting bench's heap. Chalk white with a shadowed grain.
PW_D, PW_M, PW_L = (196, 192, 184, 255), (226, 224, 216, 255), (248, 248, 244, 255)
png(OUT/'powder.png', flat(PW_M, PW_D, 131, 22, PW_L, 40))

# Twine: pale rope for handles and bindings.
TW_D, TW_M, TW_L = (120, 98, 60, 255), (168, 142, 90, 255), (200, 176, 120, 255)
px = grid()
for y in range(H):
    for x in range(W): px[y][x] = TW_L if (x + y) % 4 == 0 else TW_D if (x + y) % 4 == 2 else TW_M
png(OUT/'twine.png', px)

# Gold: coin stacks on the desk.
GD_D, GD_M, GD_L = (140, 100, 24, 255), (206, 160, 50, 255), (244, 214, 110, 255)
px = flat(GD_M, GD_D, 141, 20, GD_L, 18)
rows(px, (3, 7, 11), GD_D); rows(px, (4, 8, 12), GD_L)
png(OUT/'gold_stack.png', px)

# Gauge: a white dial with a needle, for the centrifuge's face.
px = grid(BR_M)
for y in range(H):
    for x in range(W):
        r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if r < 6.5: px[y][x] = (240, 236, 224, 255)
        if 5.6 < r < 6.5: px[y][x] = FE_D
for i in range(5): px[7 - i][8 + i] = (180, 30, 30, 255)
for x in range(3, 13, 3): px[3][x] = FE_D; px[12][x] = FE_D
px[7][7] = px[7][8] = px[8][7] = px[8][8] = FE_D
png(OUT/'gauge.png', px)

# Firebox: an iron grate with embers behind it.
px = grid(FE_D)
for y in range(H):
    for x in range(W):
        if 4 <= y <= 11 and 2 <= x <= 13:
            n = noise(x, y, 151)
            px[y][x] = (240, 120, 30, 255) if n < 50 else (200, 60, 20, 255) if n < 130 else (90, 30, 20, 255)
for x in range(2, 14, 3):
    for y in range(4, 12): px[y][x] = FE_M
png(OUT/'firebox.png', px)

# Ledger: a dark cover with a pale label.
px = flat((60, 34, 28, 255), (44, 24, 20, 255), 161, 30)
for y in range(5, 11):
    for x in range(4, 12): px[y][x] = PP_L
png(OUT/'ledger.png', px)

# A crate label: paper with a stencilled mark.
px = flat(PP_M, PP_D, 171, 14)
for y in range(H): px[y][0] = px[y][15] = PP_D
for x in range(W): px[0][x] = px[15][x] = PP_D
for x in range(4, 12): px[6][x] = (60, 40, 30, 255); px[9][x] = (60, 40, 30, 255)
for y in range(6, 10): px[y][4] = (60, 40, 30, 255); px[y][11] = (60, 40, 30, 255)
png(OUT/'crate_label.png', px)

# Lantern glass: warm light.
png(OUT/'lamp.png', flat((255, 214, 120, 255), (255, 236, 170, 255), 181, 30))

# Plants for the frame, four stages, drawn on transparency as a flat sprite the model crosses.
def plant(stage):
    px = grid()
    top = {1: 11, 2: 8, 3: 5, 4: 3}[stage]
    for y in range(top, 16): px[y][8] = HB_D; px[y][7] = HB_M     # stem
    leaves = {1: [(13, 2)], 2: [(13, 3), (10, 3)], 3: [(13, 4), (10, 4), (7, 3)], 4: [(13, 4), (10, 4), (7, 3), (5, 2)]}[stage]
    for ly, span in leaves:
        for i in range(1, span + 1):
            px[ly - (i // 2)][7 - i] = HB_L if i < span else HB_M
            px[ly - (i // 2)][8 + i] = HB_L if i < span else HB_M
    if stage == 4:
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1)):
            px[top - 1 + dy][7 + dx] = (240, 200, 90, 255)
            px[top - 1 + dy][8 + dx] = (240, 200, 90, 255)
    return px
for s in range(1, 5): png(OUT/f'plant_{s}.png', plant(s))

# Herb bundle for the loft, hung tips down: a fuller sprite than the first pass.
px = grid()
for y in range(H):
    half = 2 + 5 * (y / 15)
    for x in range(W):
        if abs(x - 7.5) > half: continue
        if y > 11 and noise(x, y, 191) < 100: continue
        px[y][x] = HB_L if abs(x - 7.5) < half * 0.35 else HB_M if abs(x - 7.5) < half * 0.7 else HB_D
for x in range(5, 11): px[0][x] = TW_M; px[1][x] = TW_D
png(OUT/'bundle.png', px)

print("wrote:", ", ".join(sorted(p.name for p in OUT.glob('*.png'))))
