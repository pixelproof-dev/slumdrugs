"""Authors the station block models as code, the way the textures are: every model is a short
recipe of boxes, octagons and slanted slabs, so a change is a diff and the whole set stays in
one style. Writes the JSON the game reads; tools/render_models.py previews it.

Vanilla model rules honoured here: one rotation axis per element, at multiples of 22.5 up to
45; coordinates between -16 and 32; a round thing is an octagon made of four bars, the two
diagonal ones a hair shorter so no two top faces fight."""
import json, pathlib, math

OUT = pathlib.Path('neoforge/src/main/resources/assets/slumdrugs/models/block')
OUT.mkdir(parents=True, exist_ok=True)

T = 'slumdrugs:block/'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


class Model:
    def __init__(self, particle):
        self.textures = {'particle': particle}
        self.elements = []

    def tex(self, key, path):
        self.textures[key] = path
        return '#' + key

    def box(self, frm, to, tex, faces=None, rot=None, uv_full=True, only=None, skip=(), shade=None):
        """A box. `faces` overrides the texture per face; `only` limits which faces exist."""
        el = {'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': {}}
        for f in FACES:
            if only and f not in only: continue
            if f in skip: continue
            t = (faces or {}).get(f, tex)
            face = {'texture': t}
            if uv_full: face['uv'] = [0, 0, 16, 16]
            el['faces'][f] = face
        if rot: el['rotation'] = rot
        if shade is not None: el['shade'] = shade
        self.elements.append(el)
        return el

    def octagon(self, cx, y0, y1, cz, width, tex, top=None, bottom=None, axis='y', eps=0.02):
        """A regular octagonal prism from four bars: two straight, two at 45 degrees.

        `axis` is the prism's axis: 'y' for a drum or disc lying flat, 'x' or 'z' for a wheel
        standing up (then y0/y1 are the extent along that axis and cx, cz are the other two
        centre coordinates in the order x, z with the axis one skipped)."""
        w = width / 2
        a = width * math.tan(math.radians(22.5)) / 2
        caps = {}
        if top: caps['up' if axis == 'y' else ('east' if axis == 'x' else 'south')] = top
        if bottom: caps['down' if axis == 'y' else ('west' if axis == 'x' else 'north')] = bottom
        if axis == 'y':
            bars = [((cx - w, y0, cz - a), (cx + w, y1, cz + a)), ((cx - a, y0, cz - w), (cx + a, y1, cz + w))]
            origin = [cx, (y0 + y1) / 2, cz]
            def shrink(b): return ((b[0][0], b[0][1] + eps, b[0][2]), (b[1][0], b[1][1] - eps, b[1][2]))
        elif axis == 'x':
            # cx is the x-centre of the wheel's thickness; y0/y1 its x extent; cz its z centre.
            xc, yc, zc = (y0 + y1) / 2, cx, cz
            bars = [((y0, yc - w, zc - a), (y1, yc + w, zc + a)), ((y0, yc - a, zc - w), (y1, yc + a, zc + w))]
            origin = [xc, yc, zc]
            def shrink(b): return ((b[0][0] + eps, b[0][1], b[0][2]), (b[1][0] - eps, b[1][1], b[1][2]))
        else:
            xc, yc, zc = cx, cz, (y0 + y1) / 2
            bars = [((xc - w, yc - a, y0), (xc + w, yc + a, y1)), ((xc - a, yc - w, y0), (xc + a, yc + w, y1))]
            origin = [xc, yc, zc]
            def shrink(b): return ((b[0][0], b[0][1], b[0][2] + eps), (b[1][0], b[1][1], b[1][2] - eps))
        for frm, to in bars:
            self.box(frm, to, tex, faces=caps)
        for frm, to in bars:
            frm, to = shrink((frm, to))
            self.box(frm, to, tex, faces=caps, rot={'origin': origin, 'axis': axis, 'angle': 45})

    def cross(self, cx, y0, y1, cz, width, tex):
        """Two crossed vertical planes, for plants and hanging bundles."""
        w = width / 2
        for angle in (45, -45):
            self.box((cx - w, y0, cz), (cx + w, y1, cz), tex, only=('north', 'south'),
                     rot={'origin': [cx, y0, cz], 'axis': 'y', 'angle': angle}, shade=False)

    def write(self, name, parent='minecraft:block/block'):
        obj = {'parent': parent, 'textures': self.textures, 'elements': self.elements, 'display': DISPLAY}
        (OUT / f'{name}.json').write_text(json.dumps(obj, indent=2) + '\n')
        return name


def legs(m, tex, inset=1, size=2.5, height=8, y0=0):
    for x in (inset, 16 - inset - size):
        for z in (inset, 16 - inset - size):
            m.box((x, y0, z), (x + size, height, z + size), tex)


# ---------------------------------------------------------------- forcing frame
def forcing_frame(stage):
    m = Model(T + 'brass')
    brass, glass, soil, walnut = m.tex('brass', T + 'brass'), m.tex('glass', T + 'glass'), m.tex('soil', T + 'soil'), m.tex('walnut', T + 'walnut')
    lamp = m.tex('lamp', T + 'lamp')
    # A deep bed with a soil top.
    m.box((0.5, 0, 0.5), (15.5, 4, 15.5), walnut, faces={'up': soil})
    # Brass corner posts and a top rail.
    for x in (0, 14.5):
        for z in (0, 14.5):
            m.box((x, 0, z), (x + 1.5, 11, z + 1.5), brass)
    for (f, t) in (((0, 10, 0), (16, 11, 1.5)), ((0, 10, 14.5), (16, 11, 16)), ((0, 10, 0), (1.5, 11, 16)), ((14.5, 10, 0), (16, 11, 16))):
        m.box(f, t, brass)
    # Glass walls, a hair inside the posts.
    m.box((1.5, 4, 0.9), (14.5, 10, 1.1), glass)
    m.box((1.5, 4, 14.9), (14.5, 10, 15.1), glass)
    m.box((0.9, 4, 1.5), (1.1, 10, 14.5), glass)
    m.box((14.9, 4, 1.5), (15.1, 10, 14.5), glass)
    # A gabled glass roof: two panes leaning in to a brass ridge, gable ends filled.
    m.box((-0.5, 11, 0), (16.5, 11.3, 8.4), glass, rot={'origin': [8, 11, 0], 'axis': 'x', 'angle': 22.5})
    m.box((-0.5, 11, 7.6), (16.5, 11.3, 16), glass, rot={'origin': [8, 11, 16], 'axis': 'x', 'angle': -22.5})
    m.box((-0.7, 13.9, 7.2), (16.7, 15, 8.8), brass)
    # Gable-end brass struts that follow the slope.
    for x in (0, 15):
        m.box((x, 11, 0), (x + 1, 11.6, 8.4), brass, rot={'origin': [8, 11, 0], 'axis': 'x', 'angle': 22.5})
        m.box((x, 11, 7.6), (x + 1, 11.6, 16), brass, rot={'origin': [8, 11, 16], 'axis': 'x', 'angle': -22.5})
    # The lantern that keeps it warm, hung from the ridge inside.
    m.box((7, 12.2, 7.2), (9, 13.9, 8.8), brass, only=('up', 'down'))
    m.box((6.6, 9.4, 6.6), (9.4, 12.3, 9.4), lamp, shade=False)
    m.box((7.6, 12.3, 7.6), (8.4, 13.9, 8.4), brass)
    # The crop.
    if stage >= 1:
        plant = m.tex('plant', T + f'plant_{min(stage, 4)}')
        size = {1: 5, 2: 7, 3: 9, 4: 10}[stage]
        m.cross(8, 4, 4 + size, 8, size, plant)
        if stage >= 3:
            m.cross(4.5, 4, 4 + size - 2, 5, size - 2, plant)
            m.cross(11.5, 4, 4 + size - 3, 11, size - 3, plant)
    return m.write(f'forcing_frame_stage{stage}')


# ---------------------------------------------------------------- drying loft
def drying_loft(bundles):
    m = Model(T + 'walnut')
    walnut, pine, twine, bundle = m.tex('walnut', T + 'walnut'), m.tex('pine', T + 'pine'), m.tex('twine', T + 'twine'), m.tex('bundle', T + 'bundle')
    # Four tall posts and a low shelf to tie the frame together.
    for x in (0.5, 14):
        for z in (0.5, 14):
            m.box((x, 0, z), (x + 1.5, 14, z + 1.5), walnut)
    m.box((0.5, 3, 0.5), (15.5, 3.8, 15.5), pine)
    # Two rails the bundles hang from, front and back, and a thin cord between them.
    m.box((0, 12.5, 3), (16, 13.5, 4), walnut)
    m.box((0, 12.5, 12), (16, 13.5, 13), walnut)
    m.box((1, 13, 3.5), (15, 13.3, 12.5), twine, only=('up', 'down', 'north', 'south'))
    # A peaked slatted roof that keeps the rain off and the air moving.
    for dz, angle in ((0, 22.5), (8.2, -22.5)):
        for i, x in enumerate((-0.6, 3.6, 7.8, 12)):
            m.box((x, 14, dz), (x + 3.4, 14.6, dz + 8.2), pine,
                  rot={'origin': [8, 14, 0 if angle > 0 else 16], 'axis': 'x', 'angle': angle})
    m.box((-1, 16.9, 7.3), (17, 17.9, 8.7), walnut)
    # The bundles, tips down, hung from the cord across the middle: fill from the middle out.
    order = [8, 4, 12][:bundles]
    for x in order:
        m.box((x - 0.6, 12.4, 7.4), (x + 0.6, 13.2, 8.6), twine)
        m.cross(x, 4.5, 12.5, 8, 5, bundle)
    return m.write('drying_loft' if bundles == 3 else f'drying_loft_{bundles}')


# ---------------------------------------------------------------- pressing bench
def pressing_bench():
    m = Model(T + 'walnut')
    walnut, iron, brass, herb, pine = m.tex('walnut', T + 'walnut'), m.tex('iron', T + 'iron'), m.tex('brass', T + 'brass'), m.tex('herb', T + 'herb'), m.tex('pine', T + 'pine')
    band = m.tex('band', T + 'brass_band')
    # A thick bench on stout legs with a stretcher.
    m.box((0, 8, 0), (16, 10.5, 16), walnut)
    legs(m, walnut, inset=1, size=3, height=8)
    m.box((1, 2, 7), (15, 3.5, 9), pine)
    # The iron press frame: two uprights and a heavy crossbar, well above the block.
    m.box((2.5, 10.5, 6), (5, 22, 10), iron)
    m.box((11, 10.5, 6), (13.5, 22, 10), iron)
    m.box((1.5, 22, 5.5), (14.5, 24.5, 10.5), iron)
    # The screw, and the capstan wheel that turns it, with four handles.
    m.octagon(8, 13, 27, 8, 2.4, iron)
    m.octagon(8, 25, 26.2, 8, 9, brass, top=band, bottom=band)
    for angle in (0, 45):
        m.box((0.5, 25.2, 7.4), (15.5, 26, 8.6), brass, rot={'origin': [8, 25.6, 8], 'axis': 'y', 'angle': angle})
        m.box((7.4, 25.2, 0.5), (8.6, 26, 15.5), brass, rot={'origin': [8, 25.6, 8], 'axis': 'y', 'angle': angle})
    # The plate bearing down on a pressed cake, and a brass tray to catch what runs out.
    m.box((4, 13, 5.5), (12, 14.2, 10.5), iron)
    m.box((4.5, 10.5, 6), (11.5, 13, 10), herb)
    m.box((3, 10.5, 11), (13, 11.2, 15), brass, faces={'up': band})
    m.box((13, 10.5, 12), (15.5, 11, 14), brass)
    return m.write('pressing_bench')


# ---------------------------------------------------------------- sealing press
def sealing_press():
    m = Model(T + 'pine')
    pine, walnut, brass, wax, paper, top, tc = (m.tex('pine', T + 'pine'), m.tex('walnut', T + 'walnut'), m.tex('brass', T + 'brass'),
                                                m.tex('wax', T + 'wax'), m.tex('paper', T + 'paper'), m.tex('parcel', T + 'parcel_top'), m.tex('tc', T + 'terracotta'))
    m.box((0, 8, 0), (16, 10, 16), pine)
    legs(m, walnut, inset=1, size=2.5, height=8)
    # A brass column carrying a long lever, knobbed, pulled down over the die.
    m.box((10.5, 10, 3), (13.5, 19, 6.5), brass)
    m.box((0.5, 17.3, 4.1), (14, 18.5, 5.4), brass, rot={'origin': [12, 17.9, 4.75], 'axis': 'z', 'angle': -22.5})
    m.octagon(4.75, 14.6, 16.4, 4.75, 3, walnut, axis='y')  # the knob, where the lever's end has come down
    # The die: a round brass seal on its shaft, just above a parcel waiting for it.
    m.octagon(6, 12.4, 13.4, 4.75, 4.4, brass, bottom=wax)
    m.box((5.5, 13.4, 4.25), (6.5, 15.2, 5.25), brass)
    m.box((3.5, 10, 2.25), (8.5, 12.4, 7.25), paper, faces={'up': top})
    # The wax pot: terracotta, warmed, a brass spoon standing in it.
    m.octagon(12, 10, 14.5, 11.5, 5.5, tc, top=wax)
    m.box((11.2, 13, 10.8), (12, 17.5, 11.6), brass, rot={'origin': [11.6, 13, 11.2], 'axis': 'z', 'angle': 22.5})
    # Sealed parcels stacked, ready to go.
    m.box((1, 10, 9.5), (6, 12.2, 14.5), paper, faces={'up': top})
    m.box((1.5, 12.2, 10), (5.5, 14.2, 14), paper, faces={'up': top})
    return m.write('sealing_press')


# ---------------------------------------------------------------- storage crate
def storage_crate():
    m = Model(T + 'pine')
    pine, walnut, twine, label, paper, top = (m.tex('pine', T + 'pine'), m.tex('walnut', T + 'walnut'), m.tex('twine', T + 'twine'),
                                              m.tex('label', T + 'crate_label'), m.tex('paper', T + 'paper'), m.tex('parcel', T + 'parcel_top'))
    m.box((1, 0, 1), (15, 12, 15), pine)
    # Corner battens and diagonal braces across every side.
    for x in (0.5, 13.5):
        for z in (0.5, 13.5):
            m.box((x, 0, z), (x + 2, 12.5, z + 2), walnut)
    for z, out in ((0.4, 0.4), (14.6, 15.6)):
        for angle in (45, -45):
            m.box((2.5, 5.4, z), (13.5, 6.6, z + 1), walnut, rot={'origin': [8, 6, z + 0.5], 'axis': 'z', 'angle': angle})
    for x in (0.4, 14.6):
        for angle in (45, -45):
            m.box((x, 5.4, 2.5), (x + 1, 6.6, 13.5), walnut, rot={'origin': [x + 0.5, 6, 8], 'axis': 'x', 'angle': angle})
    # The lid, propped open on its back hinge, and what is inside: parcels.
    m.box((0, 12, 0), (16, 13.5, 16), pine, rot={'origin': [8, 12, 16], 'axis': 'x', 'angle': -22.5})
    m.box((2, 9, 2), (14, 12, 14), paper, faces={'up': top})
    m.box((4, 12, 4), (12, 13.8, 12), paper, faces={'up': top})
    # Rope handles on the sides and a stencilled label on the front.
    for x in (-0.6, 15.6):
        m.box((x, 7, 5), (x + 1, 8, 11), twine)
        m.box((x, 5.5, 5), (x + 1, 7, 6), twine)
        m.box((x, 5.5, 10), (x + 1, 7, 11), twine)
    m.box((4.5, 3, 0.4), (11.5, 9, 0.9), label, only=('north',))
    return m.write('storage_crate')


# ---------------------------------------------------------------- centrifuge
def centrifuge():
    m = Model(T + 'brass')
    brass, band, iron, lid, gauge, copper = (m.tex('brass', T + 'brass'), m.tex('band', T + 'brass_band'), m.tex('iron', T + 'iron'),
                                             m.tex('lid', T + 'centrifuge_lid'), m.tex('gauge', T + 'gauge'), m.tex('copper', T + 'copper'))
    # An iron base ring and four splayed legs.
    m.octagon(8, 0, 1.2, 8, 13, iron)
    for x, angle in ((2.5, 22.5), (12, -22.5)):
        m.box((x, 0, 7), (x + 1.5, 5, 9), iron, rot={'origin': [x + 0.75, 5, 8], 'axis': 'z', 'angle': angle})
    for z, angle in ((2.5, -22.5), (12, 22.5)):
        m.box((7, 0, z), (9, 5, z + 1.5), iron, rot={'origin': [8, 5, z + 0.75], 'axis': 'x', 'angle': angle})
    # The drum: a tall brass octagon with riveted bands top and bottom, a lid with the hatch.
    m.octagon(8, 4, 15, 8, 10, brass)
    m.octagon(8, 4, 5.4, 8, 11, band)
    m.octagon(8, 13.6, 15, 8, 11, band)
    m.octagon(8, 15, 16.2, 8, 8.5, band, top=lid)
    m.box((7.4, 16.2, 7.4), (8.6, 17.4, 8.6), brass)
    # The crank wheel on the east side, with its handle, and a gauge on the front.
    m.octagon(9.5, 12.8, 14.2, 8, 5.5, iron, axis='x')
    m.box((14.2, 8.9, 7.6), (15.2, 10.1, 8.4), iron)
    m.box((15.2, 7.4, 7.5), (16.4, 9.2, 8.5), brass)
    m.box((5.5, 9, 2.4), (10.5, 12.8, 3.2), brass, faces={'north': gauge})
    # A copper drain from the bottom to a spigot.
    m.box((7.2, 2, 7.2), (8.8, 4.2, 8.8), copper)
    m.box((7.2, 2, 8.8), (8.8, 3.6, 14.5), copper)
    m.box((6.9, 1, 13), (9.1, 3.6, 14.8), copper)
    return m.write('centrifuge')


# ---------------------------------------------------------------- still
def still():
    m = Model(T + 'copper')
    copper, iron, fire, band, glass, walnut = (m.tex('copper', T + 'copper'), m.tex('iron', T + 'iron'), m.tex('fire', T + 'firebox'),
                                               m.tex('band', T + 'brass_band'), m.tex('glass', T + 'glass'), m.tex('walnut', T + 'walnut'))
    # The firebox: an iron octagon with the grate glowing on every side.
    m.octagon(6.5, 0, 3, 8, 11, fire, top=iron)
    # The pot: an onion of stacked octagons, a band at the waist, and a narrow neck.
    for y0, y1, w in ((3, 5, 9.5), (5, 8.5, 11.5), (8.5, 11, 11), (11, 13, 9), (13, 14.5, 6.5), (14.5, 18, 4)):
        m.octagon(6.5, y0, y1, 8, w, copper)
    m.octagon(6.5, 7.8, 9, 8, 12, band)
    m.octagon(6.5, 18, 19.2, 8, 5.5, band)
    # The swan neck: up out of the cap, over, and down at an angle into the worm barrel.
    m.box((6, 19.2, 7.3), (7, 22, 8.7), copper)
    m.box((6, 21, 7.3), (14.5, 22.2, 8.7), copper)
    m.box((13.3, 12, 7.3), (14.7, 22.2, 8.7), copper, rot={'origin': [14, 22, 8], 'axis': 'z', 'angle': -22.5})
    # The worm barrel, a small cask of cold water the coil runs through, and the receiver.
    m.octagon(14.5, 0, 9, 8, 6.5, walnut, top=band)
    m.octagon(14.5, 3.5, 4.5, 8, 7, band)
    m.box((11.6, 1.5, 7.4), (14.5, 2.7, 8.6), copper)
    m.box((9.5, 0, 6.5), (12, 4.5, 9.5), glass)
    m.box((10.2, 4.5, 7.2), (11.3, 5.6, 8.8), walnut)
    return m.write('still')


# ---------------------------------------------------------------- cutting bench
def cutting_bench():
    m = Model(T + 'end_grain')
    grain, walnut, iron, powder, brass, pine = (m.tex('grain', T + 'end_grain'), m.tex('walnut', T + 'walnut'), m.tex('iron', T + 'iron'),
                                                m.tex('powder', T + 'powder'), m.tex('brass', T + 'brass'), m.tex('pine', T + 'pine'))
    # A butcher block: thick end-grain top on squat legs.
    m.box((0, 8, 0), (16, 12.5, 16), walnut, faces={'up': grain})
    legs(m, pine, inset=1.5, size=3, height=8)
    # The heap of powder, and three lines cut out of it.
    m.octagon(11, 12.5, 14.3, 11, 7, powder, top=powder)
    m.octagon(11, 14.3, 15.5, 11, 4.5, powder, top=powder)
    m.octagon(11, 15.5, 16.2, 11, 2, powder, top=powder)
    for i, z in enumerate((2.5, 4.5, 6.5)):
        m.box((3, 12.5, z), (9 - i, 12.9, z + 0.8), powder)
    # The cleaver, lying at an angle across the block, and the brass scale behind it.
    m.box((1.5, 12.5, 8.5), (8.5, 13.1, 12.5), iron, rot={'origin': [5, 12.8, 10.5], 'axis': 'y', 'angle': -22.5})
    m.box((8.3, 12.5, 10), (12.3, 13.5, 11.2), walnut, rot={'origin': [5, 12.8, 10.5], 'axis': 'y', 'angle': -22.5})
    m.box((3.2, 12.5, 14), (4.4, 21, 15.2), brass)
    m.box((-1, 20, 14.2), (8.6, 20.8, 15), brass, rot={'origin': [3.8, 20.4, 14.6], 'axis': 'z', 'angle': 22.5})
    m.octagon(-0.2, 16.4, 16.9, 14.6, 3.6, brass, top=brass)
    m.octagon(7.8, 19.2, 19.7, 14.6, 3.6, brass, top=powder)
    for x in (-0.2, 7.8):
        y = 16.9 if x < 0 else 19.7
        m.box((x - 0.15, y, 14.45), (x + 0.15, y + 2.4 if x < 0 else y + 0.9, 14.75), brass)
    return m.write('cutting_bench')


# ---------------------------------------------------------------- grafting bench
def grafting_bench():
    m = Model(T + 'pine')
    pine, walnut, tc, soil, plant, iron, twine = (m.tex('pine', T + 'pine'), m.tex('walnut', T + 'walnut'), m.tex('tc', T + 'terracotta'),
                                                  m.tex('soil', T + 'soil'), m.tex('plant', T + 'plant_2'), m.tex('iron', T + 'iron'), m.tex('twine', T + 'twine'))
    m.box((0, 8, 0), (16, 10, 16), pine)
    legs(m, walnut, inset=1, size=2.5, height=8)
    # A trellis at the back: two uprights and a lattice of slats at 45 degrees.
    m.box((0.5, 10, 14), (2, 22, 15.5), walnut)
    m.box((14, 10, 14), (15.5, 22, 15.5), walnut)
    m.box((0.5, 21, 14), (15.5, 22, 15.5), walnut)
    for i in range(-2, 3):
        m.box((8 - 7, 15.5 + i * 3.2, 14.55), (8 + 7, 16.1 + i * 3.2, 14.95), pine,
              rot={'origin': [8, 15.8 + i * 3.2, 14.75], 'axis': 'z', 'angle': 45})
        m.box((8 - 7, 15.5 + i * 3.2, 14.55), (8 + 7, 16.1 + i * 3.2, 14.95), pine,
              rot={'origin': [8, 15.8 + i * 3.2, 14.75], 'axis': 'z', 'angle': -45})
    # Two terracotta pots, tapered, with a sprig each.
    for x in (4.5, 11.5):
        m.octagon(x, 10, 12, 7, 4.2, tc)
        m.octagon(x, 12, 14.6, 7, 5.2, tc, top=soil)
        m.octagon(x, 14.6, 15.3, 7, 5.8, tc, top=soil)
        m.cross(x, 15.2, 20.4, 7, 5, plant)
    # The grafting knife, hooked blade up, and a spool of binding twine.
    m.box((5.5, 10, 2), (10.5, 10.7, 3.2), iron, rot={'origin': [8, 10.3, 2.6], 'axis': 'y', 'angle': 22.5})
    m.box((10.3, 10, 2.1), (13.3, 11.1, 3.1), walnut, rot={'origin': [8, 10.3, 2.6], 'axis': 'y', 'angle': 22.5})
    m.octagon(2.6, 10, 12.2, 3, 2.8, twine, top=pine)
    return m.write('grafting_bench')


# ---------------------------------------------------------------- counting house
def counting_house():
    m = Model(T + 'walnut')
    walnut, leather, brass, gold, ledger, paper, iron = (m.tex('walnut', T + 'walnut'), m.tex('leather', T + 'leather_green'), m.tex('brass', T + 'brass'),
                                                        m.tex('gold', T + 'gold_stack'), m.tex('ledger', T + 'ledger'), m.tex('paper', T + 'paper'), m.tex('iron', T + 'iron'))
    band = m.tex('band', T + 'brass_band')
    # A tall clerk's desk: a plinth, a body with a brass-handled drawer, a sloped top.
    m.box((0.5, 0, 0.5), (15.5, 1.5, 15.5), walnut)
    m.box((1.5, 1.5, 1.5), (14.5, 12, 14.5), walnut)
    m.box((3, 3, 1.2), (13, 8, 1.6), walnut)
    m.box((6.5, 5.2, 0.7), (9.5, 6, 1.3), brass)
    # The writing slope: leather on walnut, hinged at the back so it rises toward the front.
    m.box((0, 12, 0), (16, 13.5, 16), walnut, faces={'up': leather}, rot={'origin': [8, 12, 16], 'axis': 'x', 'angle': 22.5})
    m.box((0, 13.4, 14.5), (16, 15.2, 16), walnut)
    m.box((0.5, 15.2, 14.6), (15.5, 15.8, 15.9), band)
    # The open ledger, lying on the slope with the same tilt.
    m.box((4, 13.5, 3.5), (12, 14.3, 11.5), ledger, faces={'up': paper}, rot={'origin': [8, 12, 16], 'axis': 'x', 'angle': 22.5})
    m.box((7.85, 14.3, 3.6), (8.15, 14.5, 11.4), walnut, rot={'origin': [8, 12, 16], 'axis': 'x', 'angle': 22.5})
    # Coin stacks on the flat back ledge, the ink pot, and the counting-house stamp.
    for x, h in ((2.5, 2.2), (4.5, 3.4), (6.5, 1.6)):
        m.octagon(x, 15.8, 15.8 + h, 15.2, 1.6, gold, top=gold)
    m.octagon(12.5, 15.8, 17.4, 15.2, 2, iron, top=iron)
    m.box((12.3, 17.2, 15), (12.7, 19.5, 15.4), brass, rot={'origin': [12.5, 17.3, 15.2], 'axis': 'z', 'angle': -22.5})
    m.box((9, 15.8, 14.6), (11, 16.6, 15.8), brass)
    m.box((9.7, 16.6, 14.9), (10.3, 18.2, 15.5), walnut)
    return m.write('counting_house')


if __name__ == '__main__':
    written = []
    for s in range(5): written.append(forcing_frame(s))
    for b in range(4): written.append(drying_loft(b))
    written += [pressing_bench(), sealing_press(), storage_crate(), centrifuge(), still(),
                cutting_bench(), grafting_bench(), counting_house()]
    print('wrote:', ', '.join(written))
