"""Minimal software renderer for vanilla block model JSON, so models can be reviewed
without launching the game. Orthographic, z-buffered, nearest-neighbour textures,
Minecraft's own per-face shading."""
import json, math, struct, zlib, pathlib

# ---------------------------------------------------------------- png io
def read_png(path):
    """Handles the colour types vanilla actually ships: RGBA, RGB, palette and greyscale,
    with or without alpha. Animated textures are cropped to their first frame."""
    d = path.read_bytes(); i = 8; idat = b''; plte = None; trns = None
    while i < len(d):
        ln = struct.unpack('>I', d[i:i+4])[0]; typ = d[i+4:i+8]; data = d[i+8:i+8+ln]; i += 12+ln
        if typ == b'IHDR': w, h, bd, ct = struct.unpack('>IIBB', data[:10])
        elif typ == b'PLTE': plte = data
        elif typ == b'tRNS': trns = data
        elif typ == b'IDAT': idat += data
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    raw = zlib.decompress(idat)
    # Vanilla ships 4-bit palette images too, where a scanline is packed tighter than one
    # byte per pixel and the filter works on whole bytes.
    bits = channels*bd
    stride = (w*bits + 7)//8
    bpp = max(1, bits//8)
    rows = []; prev = bytearray(stride); pos = 0
    for _ in range(h):
        f = raw[pos]; pos += 1; line = bytearray(raw[pos:pos+stride]); pos += stride
        for x in range(stride):
            a = line[x-bpp] if x >= bpp else 0; b = prev[x]; c = prev[x-bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x]+a) & 255
            elif f == 2: line[x] = (line[x]+b) & 255
            elif f == 3: line[x] = (line[x]+(a+b)//2) & 255
            elif f == 4:
                pp = a+b-c; pa, pb, pc = abs(pp-a), abs(pp-b), abs(pp-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x]+pr) & 255
        rows.append(bytes(line)); prev = line
    px = []
    for y in range(h):
        r = []
        for x in range(w):
            if bd < 8:
                per = 8//bd; byte = rows[y][x//per]
                shift = 8 - bd*((x % per)+1)
                v = ((byte >> shift) & ((1 << bd)-1),)
            else:
                o = x*bpp; v = rows[y][o:o+bpp]
            if ct == 6:   r.append((v[0], v[1], v[2], v[3]))
            elif ct == 2: r.append((v[0], v[1], v[2], 255))
            elif ct == 4: r.append((v[0], v[0], v[0], v[1]))
            elif ct == 0: r.append((v[0], v[0], v[0], 255))
            else:
                idx = v[0]; q = idx*3
                alpha = trns[idx] if (trns and idx < len(trns)) else 255
                r.append((plte[q], plte[q+1], plte[q+2], alpha))
        px.append(r)
    if h > w: px = px[:w]; h = w          # animated: first frame only
    return w, h, px

def write_png(path, w, h, buf):
    raw = b''.join(b'\x00' + bytes(buf[y]) for y in range(h))
    def chunk(t, d):
        c = t + d
        return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'
        + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

# ---------------------------------------------------------------- geometry
FACES = {  # name -> the four corners as (x,y,z) picks from (from,to), plus uv order
 'down':  lambda a,b: [(a[0],a[1],b[2]),(b[0],a[1],b[2]),(b[0],a[1],a[2]),(a[0],a[1],a[2])],
 'up':    lambda a,b: [(a[0],b[1],a[2]),(b[0],b[1],a[2]),(b[0],b[1],b[2]),(a[0],b[1],b[2])],
 'north': lambda a,b: [(b[0],b[1],a[2]),(a[0],b[1],a[2]),(a[0],a[1],a[2]),(b[0],a[1],a[2])],
 'south': lambda a,b: [(a[0],b[1],b[2]),(b[0],b[1],b[2]),(b[0],a[1],b[2]),(a[0],a[1],b[2])],
 'west':  lambda a,b: [(a[0],b[1],a[2]),(a[0],b[1],b[2]),(a[0],a[1],b[2]),(a[0],a[1],a[2])],
 'east':  lambda a,b: [(b[0],b[1],b[2]),(b[0],b[1],a[2]),(b[0],a[1],a[2]),(b[0],a[1],b[2])],
}
SHADE = {'up':1.0,'down':0.5,'north':0.8,'south':0.8,'east':0.6,'west':0.6}

def rotate(p, rot):
    if not rot: return p
    ox, oy, oz = rot['origin']; ang = math.radians(rot['angle']); axis = rot['axis']
    x, y, z = p[0]-ox, p[1]-oy, p[2]-oz
    c, s = math.cos(ang), math.sin(ang)
    if axis == 'x': y, z = y*c - z*s, y*s + z*c
    elif axis == 'y': x, z = x*c + z*s, -x*s + z*c
    else: x, y = x*c - y*s, x*s + y*c
    return (x+ox, y+oy, z+oz)

def camera(p, yaw, pitch):
    x, y, z = p[0]-8, p[1]-8, p[2]-8
    cy, sy = math.cos(yaw), math.sin(yaw)
    x, z = x*cy + z*sy, -x*sy + z*cy
    cp, sp = math.cos(pitch), math.sin(pitch)
    y, z = y*cp - z*sp, y*sp + z*cp
    return x, y, z

def render(model_path, tex_dir, size=320, yaw=math.radians(-35), pitch=math.radians(30)):
    m = json.loads(model_path.read_text())
    slots = m.get('textures', {})
    cache = {}
    def texture(ref):
        while ref.startswith('#'): ref = slots.get(ref[1:], '')
        if ref not in cache:
            f = tex_dir / (ref.split(':')[-1].replace('/', '_') + '.png')
            cache[ref] = read_png(f) if f.exists() else (1, 1, [[(255, 0, 255, 255)]])
        return cache[ref]

    BG = (28, 28, 34)
    buf = [bytearray(BG*size) for _ in range(size)]
    zbuf = [[1e9]*size for _ in range(size)]
    scale = size/26.0

    tris = []
    for el in m.get('elements', []):
        a, b = el['from'], el['to']
        rot = el.get('rotation')
        for name, face in el['faces'].items():
            corners = [camera(rotate(p, rot), yaw, pitch) for p in FACES[name](a, b)]
            w, h, px = texture(face['texture'])
            uv = [(0, 0), (w, 0), (w, h), (0, h)]
            shade = SHADE[name] * (0.85 if el.get('shade') is False else 1.0)
            tris.append((corners, uv, px, w, h, shade))

    for corners, uv, px, tw, th, shade in tris:
        pts = [(size/2 + c[0]*scale, size/2 - c[1]*scale, c[2]) for c in corners]
        for tri in ((0, 1, 2), (0, 2, 3)):
            (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = (pts[i] for i in tri)
            (u0, v0), (u1, v1), (u2, v2) = (uv[i] for i in tri)
            minx, maxx = max(0, int(min(x0, x1, x2))), min(size-1, int(max(x0, x1, x2))+1)
            miny, maxy = max(0, int(min(y0, y1, y2))), min(size-1, int(max(y0, y1, y2))+1)
            den = (y1-y2)*(x0-x2) + (x2-x1)*(y0-y2)
            if abs(den) < 1e-9: continue
            for py in range(miny, maxy+1):
                for pxx in range(minx, maxx+1):
                    l0 = ((y1-y2)*(pxx-x2) + (x2-x1)*(py-y2))/den
                    l1 = ((y2-y0)*(pxx-x2) + (x0-x2)*(py-y2))/den
                    l2 = 1-l0-l1
                    if l0 < -0.001 or l1 < -0.001 or l2 < -0.001: continue
                    z = l0*z0 + l1*z1 + l2*z2
                    if z >= zbuf[py][pxx]: continue
                    u = int(l0*u0 + l1*u1 + l2*u2) % tw
                    v = int(l0*v0 + l1*v1 + l2*v2) % th
                    r, g, bl, al = px[v][u]
                    if al < 32: continue
                    src = (int(r*shade), int(g*shade), int(bl*shade))
                    if al < 255:
                        o = pxx*3; dst = buf[py][o:o+3]
                        src = tuple((src[i]*al + dst[i]*(255-al))//255 for i in range(3))
                    zbuf[py][pxx] = z
                    o = pxx*3; buf[py][o:o+3] = bytes(src)
    return size, buf

# ---------------------------------------------------------------- cli
if __name__ == '__main__':
    import sys, zipfile
    root = pathlib.Path(__file__).resolve().parent.parent
    models = root / 'neoforge/src/main/resources/assets/slumdrugs/models/block'
    # the plain client jar, not the sources or merged ones beside it
    jars = [j for j in sorted((root / 'neoforge/build/moddev/artifacts').glob('minecraft-patched-*.jar'))
            if not any(k in j.name for k in ('sources', 'merged'))]
    if not jars:
        sys.exit("Run ./gradlew build first so the Minecraft jar is available for textures.")

    tex = pathlib.Path('/tmp/slumdrugs-render-tex'); tex.mkdir(exist_ok=True)
    jar = zipfile.ZipFile(jars[0])
    for p in models.glob('*.json'):
        for ref in json.loads(p.read_text()).get('textures', {}).values():
            if not ref.startswith('minecraft:'): continue
            name = ref.split(':')[1]
            dest = tex / (name.replace('/', '_') + '.png')
            if not dest.exists(): dest.write_bytes(jar.read(f'assets/minecraft/textures/{name}.png'))

    out = root / 'build/model-previews'; out.mkdir(parents=True, exist_ok=True)
    wanted = sys.argv[1:] or sorted(p.stem for p in models.glob('*.json'))
    for name in wanted:
        size, buf = render(models / f'{name}.json', tex, size=300,
                           yaw=math.radians(-35), pitch=math.radians(-30))
        write_png(out / f'{name}.png', size, size, buf)
        print('rendered', out / f'{name}.png')
