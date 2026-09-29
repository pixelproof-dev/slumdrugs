#!/usr/bin/env python3
"""Placeholder art for the power blocks: generator, battery bank, town meter.

Writes 16x16 textures, block models (cube_bottom_top, an "_on" variant each), blockstates on
the `running` property, and the item model definitions. Deterministic pixel geometry in the
modern palette of ASSETS-MODERN.md: close greys and one accent. Replace with Blockbench models
when they exist; the ids stay.

    python3 tools/build_power_art.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
AS = ROOT / 'neoforge/src/main/resources/assets/slumdrugs'


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


INK = rgb('#182531')
STEEL_D, STEEL, STEEL_L = rgb('#2a2f36'), rgb('#3a4048'), rgb('#525a64')
GREY_D, GREY, GREY_L = rgb('#8d969e'), rgb('#aab2ba'), rgb('#c9d0d6')
ORANGE, ORANGE_D = rgb('#e9a146'), rgb('#b8742a')
BLUE_D, BLUE, BLUE_L = rgb('#23507e'), rgb('#3572b0'), rgb('#5f9bd6')
LED_OFF, LED_ON = rgb('#1f3a28'), rgb('#62f08a')
AMBER_ON = rgb('#ffcf6b')
WHITE, RED = rgb('#e8ecef'), rgb('#d24b4b')


def canvas(fill):
    im = Image.new('RGBA', (16, 16), fill)
    return im, im.load()


def frame(px, dark):
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = dark


def generator_side(on):
    im, px = canvas(STEEL)
    frame(px, STEEL_D)
    for y in (3, 5, 7, 9):  # vent slats
        for x in range(3, 13):
            px[x, y] = INK if not on else (ORANGE_D if x % 3 else INK)
        for x in range(3, 13):
            px[x, y - 1] = STEEL_L
    for x in range(1, 15):  # the one accent: an orange band
        px[x, 12] = ORANGE
        px[x, 13] = ORANGE_D
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = STEEL_L
    px[13, 10] = LED_ON if on else LED_OFF
    return im


def generator_top(on):
    im, px = canvas(STEEL)
    frame(px, STEEL_D)
    for y in range(3, 13):
        for x in range(3, 13):
            if (x + y) % 2 == 0:
                px[x, y] = STEEL_D
    for (x, y) in ((6, 6), (7, 6), (8, 6), (9, 6), (6, 9), (7, 9), (8, 9), (9, 9),
                   (6, 7), (6, 8), (9, 7), (9, 8)):
        px[x, y] = INK  # the exhaust
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = AMBER_ON if on else STEEL_D
    return im


def plain(fill, edge):
    im, px = canvas(fill)
    frame(px, edge)
    return im


def battery_side(on):
    im, px = canvas(STEEL)
    frame(px, STEEL_D)
    for cell in range(3):
        x0 = 2 + cell * 4
        for y in range(3, 14):
            for x in range(x0, x0 + 3):
                px[x, y] = BLUE if x != x0 else BLUE_L
            px[x0 + 2, y] = BLUE_D
        for x in range(x0, x0 + 3):
            px[x, 2] = GREY_L  # caps
    for y in range(3, 14):  # the charge bar
        px[14, y] = (LED_ON if y >= 8 else LED_OFF) if on else LED_OFF
    return im


def battery_top(on):
    im, px = canvas(STEEL)
    frame(px, STEEL_D)
    for (x, y) in ((4, 4), (5, 4), (4, 5), (5, 5)):
        px[x, y] = RED
    for (x, y) in ((10, 10), (11, 10), (10, 11), (11, 11)):
        px[x, y] = INK
    for x in range(3, 13):
        px[x, 8] = STEEL_D
    return im


def meter_side(on):
    im, px = canvas(GREY)
    frame(px, GREY_D)
    for y in range(3, 10):  # the dial window
        for x in range(4, 12):
            px[x, y] = WHITE
    for x in range(4, 12):
        px[x, 3] = GREY_D
    for i, x in enumerate(range(5, 11)):  # counter digits
        px[x, 8] = INK if i % 2 == 0 else STEEL
    px[8, 5] = RED
    px[9, 4] = RED
    px[7, 6] = INK
    for x in range(4, 12):
        px[x, 11] = GREY_L
    px[12, 13] = LED_ON if on else LED_OFF
    px[3, 13] = GREY_D
    return im


def save(img, name):
    img.save(AS / 'textures/block' / f'{name}.png')


def model(name, side, top, bottom):
    (AS / 'models/block' / f'{name}.json').write_text(json.dumps({
        'parent': 'minecraft:block/cube_bottom_top',
        'textures': {'side': f'slumdrugs:block/{side}', 'top': f'slumdrugs:block/{top}',
                     'bottom': f'slumdrugs:block/{bottom}', 'particle': f'slumdrugs:block/{side}'}},
        indent=2) + '\n')


def block(name, side, top, bottom_fill, edge):
    for on in (False, True):
        suffix = '_on' if on else ''
        save(side(on), f'{name}_side{suffix}')
        save(top(on), f'{name}_top{suffix}')
        model(f'{name}{suffix}', f'{name}_side{suffix}', f'{name}_top{suffix}', f'{name}_bottom')
    save(plain(bottom_fill, edge), f'{name}_bottom')
    (AS / 'blockstates' / f'{name}.json').write_text(json.dumps({'variants': {
        'running=false': {'model': f'slumdrugs:block/{name}'},
        'running=true': {'model': f'slumdrugs:block/{name}_on'}}}, indent=2) + '\n')
    # Item model and its 26.3 definition, the way the stations have them.
    (AS / 'models/item' / f'{name}.json').write_text(json.dumps({'parent': f'slumdrugs:block/{name}'}, indent=2) + '\n')
    (AS / 'items' / f'{name}.json').write_text(json.dumps(
        {'model': {'type': 'minecraft:model', 'model': f'slumdrugs:item/{name}'}}, indent=2) + '\n')


def cable():
    """The power cable: a thin core and one arm per connected side, as the chorus plant does."""
    im, px = canvas(ORANGE)
    for y in range(16):
        for x in range(16):
            if (x + y) % 6 == 0:
                px[x, y] = ORANGE_D  # a spiral wrap, so it reads as a lead and not a bar
            elif (x + y) % 6 == 1:
                px[x, y] = rgb('#f6c27a')
    save(im, 'power_cable')

    tex = {'cable': 'slumdrugs:block/power_cable', 'particle': 'slumdrugs:block/power_cable'}
    lo, hi = 5.5, 10.5
    face = {'uv': [lo, lo, hi, hi], 'texture': '#cable'}
    core = {'parent': 'minecraft:block/block', 'textures': tex, 'elements': [{
        'from': [lo, lo, lo], 'to': [hi, hi, hi],
        'faces': {d: dict(face) for d in ('north', 'south', 'east', 'west', 'up', 'down')}}]}
    arm_face = {'uv': [0, lo, lo, hi], 'texture': '#cable'}
    side = {'parent': 'minecraft:block/block', 'textures': tex, 'elements': [{
        'from': [lo, lo, 0], 'to': [hi, hi, lo],
        'faces': {'north': dict(face, cullface='north'), 'east': dict(arm_face), 'west': dict(arm_face),
                  'up': dict(arm_face), 'down': dict(arm_face)}}]}
    (AS / 'models/block/power_cable_core.json').write_text(json.dumps(core, indent=2) + '\n')
    (AS / 'models/block/power_cable_side.json').write_text(json.dumps(side, indent=2) + '\n')

    arm = 'slumdrugs:block/power_cable_side'
    parts = [{'apply': {'model': 'slumdrugs:block/power_cable_core'}}]
    for when, rot in (('north', {}), ('east', {'y': 90}), ('south', {'y': 180}), ('west', {'y': 270}),
                      ('up', {'x': 270}), ('down', {'x': 90})):
        parts.append({'when': {when: 'true'}, 'apply': dict({'model': arm}, **rot)})
    (AS / 'blockstates/power_cable.json').write_text(json.dumps({'multipart': parts}, indent=2) + '\n')

    # In the hand it is a coil of lead with a plug, not a tiny cube.
    icon, ipx = canvas((0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            r2 = (x - 7.5) ** 2 + (y - 8.5) ** 2
            if 16 <= r2 <= 42:
                ipx[x, y] = ORANGE if (x + y) % 3 else ORANGE_D
            elif 9 <= r2 < 16 or 42 < r2 <= 52:
                ipx[x, y] = INK
    for (x, y) in ((12, 2), (13, 2), (12, 3), (13, 3), (14, 1), (14, 4)):
        ipx[x, y] = GREY_L if (x, y) not in ((14, 1), (14, 4)) else GREY_D
    icon.save(AS / 'textures/item/power_cable.png')
    (AS / 'models/item/power_cable.json').write_text(json.dumps(
        {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'slumdrugs:item/power_cable'}}, indent=2) + '\n')
    (AS / 'items/power_cable.json').write_text(json.dumps(
        {'model': {'type': 'minecraft:model', 'model': 'slumdrugs:item/power_cable'}}, indent=2) + '\n')


if __name__ == '__main__':
    (AS / 'textures/block').mkdir(parents=True, exist_ok=True)
    block('generator', generator_side, generator_top, STEEL_D, INK)
    block('battery_bank', battery_side, battery_top, STEEL_D, INK)
    block('power_meter', meter_side, lambda on: plain(GREY, GREY_D), GREY_D, STEEL)
    cable()
    print('power art written')
