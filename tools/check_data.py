#!/usr/bin/env python3
"""Checks the mod's data and assets against each other, without starting the game.

Everything the game would only complain about at startup or, worse, show silently wrong:
lang files that do not parse, an item with no name in one language, a recipe whose result
does not exist, an advancement whose icon or parent is missing, a blockstate pointing at
a model that is not there, a model referencing a texture nobody drew. Vanilla ids are
checked against the Minecraft jar when one is on disk (after `./gradlew build`), and
skipped with a note when it is not.

    python3 tools/check_data.py

Exit status is the number of problems, so CI fails on the first one.
"""
import json
import pathlib
import re
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / 'neoforge/src/main/resources'
ASSETS = RES / 'assets/slumdrugs'
DATA = RES / 'data/slumdrugs'
JAVA = ROOT / 'neoforge/src/main/java'
NS = 'slumdrugs'

problems = []
notes = []


def problem(msg):
    problems.append(msg)


def load(path):
    try:
        return json.loads(path.read_text(encoding='utf-8'))
    except json.JSONDecodeError as e:
        problem(f'{path.relative_to(ROOT)}: invalid JSON ({e})')
        return None


def java_text():
    return '\n'.join(p.read_text(encoding='utf-8') for p in JAVA.rglob('*.java'))


# ---------------------------------------------------------------- registrations
def registered():
    """Item and block ids, read out of ModItems and ModBlocks the way the game would build them."""
    items = ModItemsParser.parse((JAVA / 'dev/lucas/slumdrugs/mod/ModItems.java').read_text())
    blocks_src = (JAVA / 'dev/lucas/slumdrugs/mod/ModBlocks.java').read_text()
    blocks = re.findall(r'registerBlock\("([a-z_0-9]+)"', blocks_src)
    block_items = re.findall(r'blockItem\("([a-z_0-9]+)"', blocks_src)
    for b in block_items:
        if b not in blocks:
            problem(f'ModBlocks: blockItem("{b}") has no registerBlock')
    return items + block_items, blocks


class ModItemsParser:
    @staticmethod
    def parse(src):
        def lst(name):
            m = re.search(name + r'\s*=\s*List\.of\(([^)]*)\)', src)
            return re.findall(r'"([a-z_]+)"', m.group(1)) if m else []
        crops, subs = lst('CROPS'), lst('SUBSTANCES')
        items = []
        for d in crops:
            items += [f'seed_{d}', f'raw_{d}', f'dried_{d}']
        for d in subs:
            items += [f'product_{d}', f'essence_{d}', f'package_{d}']
        # A name that ends in an underscore is a prefix completed at runtime; the loops above cover those.
        items += [n for n in re.findall(r'(?:registerSimpleItem|simple)\("([a-z_0-9]+)"', src) if not n.endswith('_')]
        items += [n for n in re.findall(r'registerItem\("([a-z_0-9]+)"', src) if not n.endswith('_')]
        seen, out = set(), []
        for i in items:
            if i not in seen:
                seen.add(i); out.append(i)
        return out


# ---------------------------------------------------------------- vanilla
def vanilla():
    jars = sorted((ROOT / 'neoforge/build/moddev/artifacts').glob('minecraft-patched-*[0-9a-z].jar'))
    jars = [j for j in jars if 'sources' not in j.name and 'merged' not in j.name]
    if not jars:
        notes.append('no Minecraft jar under neoforge/build/moddev/artifacts; vanilla ids not checked')
        return None
    z = zipfile.ZipFile(jars[0])
    names = set(z.namelist())
    lang = json.loads(z.read('assets/minecraft/lang/en_us.json'))
    items = {k.split('.', 2)[2] for k in lang if k.startswith('item.minecraft.') and k.count('.') == 2}
    blocks = {k.split('.', 2)[2] for k in lang if k.startswith('block.minecraft.') and k.count('.') == 2}
    textures = {n[len('assets/minecraft/textures/'):-4] for n in names
                if n.startswith('assets/minecraft/textures/') and n.endswith('.png')}
    models = {n[len('assets/minecraft/models/'):-5] for n in names
              if n.startswith('assets/minecraft/models/') and n.endswith('.json')}
    tags = {n[len('data/minecraft/tags/item/'):-5] for n in names
            if n.startswith('data/minecraft/tags/item/') and n.endswith('.json')}
    return {'items': items | blocks, 'textures': textures, 'models': models, 'item_tags': tags}


def check_id(ref, kind, ours, van, where):
    """An id like slumdrugs:still or minecraft:paper, against what exists."""
    if ':' not in ref:
        ref = 'minecraft:' + ref
    ns, path = ref.split(':', 1)
    if ns == NS:
        if path not in ours:
            problem(f'{where}: {kind} {ref} is not registered')
    elif ns == 'minecraft':
        if van and path not in van:
            problem(f'{where}: {kind} {ref} does not exist in vanilla')
    else:
        problem(f'{where}: {kind} {ref} from an unknown namespace')


# ---------------------------------------------------------------- checks
def check_lang(items, blocks, src):
    langs = {}
    for p in sorted((ASSETS / 'lang').glob('*.json')):
        d = load(p)
        if d is not None:
            langs[p.stem] = d
    if 'en_us' not in langs:
        problem('lang/en_us.json missing or unreadable')
        return
    en = langs['en_us']
    for i in items:
        key = f'block.{NS}.{i}' if i in blocks else f'item.{NS}.{i}'
        if key not in en:
            problem(f'lang/en_us.json: no name for {key}')
    if f'itemGroup.{NS}' not in en:
        problem(f'lang/en_us.json: no itemGroup.{NS}')
    # Every other language carries exactly the same keys.
    for name, d in langs.items():
        if name == 'en_us':
            continue
        for k in sorted(set(en) - set(d)):
            problem(f'lang/{name}.json: missing {k}')
        for k in sorted(set(d) - set(en)):
            problem(f'lang/{name}.json: {k} has no en_us counterpart')
    # Keys the code uses. A complete literal must exist; a prefix that is completed at runtime
    # must have at least one key under it.
    for key in sorted(set(re.findall(r'"((?:[a-z_]+)\.' + NS + r'\.[a-zA-Z_.]*[a-zA-Z])"(?!\s*\+)', src))):
        if key not in en:
            problem(f'code uses translation key {key}, not in en_us.json')
    for prefix in sorted(set(re.findall(r'"((?:[a-z_]+)\.' + NS + r'\.[a-zA-Z_.]*[._])"\s*\+', src))):
        if not any(k.startswith(prefix) for k in en):
            problem(f'code builds keys under {prefix}, none in en_us.json')
    # Keys the data files use.
    for p in sorted(DATA.rglob('*.json')):
        for key in re.findall(r'"translate"\s*:\s*"([^"]+)"', p.read_text(encoding='utf-8')):
            if key not in en:
                problem(f'{p.relative_to(RES)}: translate key {key} not in en_us.json')


def check_models(items, blocks, van):
    van_tex = van['textures'] if van else None
    van_models = van['models'] if van else None
    our_models = {str(p.relative_to(ASSETS / 'models'))[:-5] for p in (ASSETS / 'models').rglob('*.json')}
    our_tex = {str(p.relative_to(ASSETS / 'textures'))[:-4] for p in (ASSETS / 'textures').rglob('*.png')}
    for b in blocks:
        p = ASSETS / 'blockstates' / f'{b}.json'
        if not p.exists():
            problem(f'block {b}: no blockstate'); continue
        d = load(p)
        if d is None: continue
        for m in re.findall(r'"model"\s*:\s*"([^"]+)"', p.read_text()):
            ns, path = (m.split(':', 1) if ':' in m else ('minecraft', m))
            if ns == NS and path not in our_models:
                problem(f'blockstates/{b}.json: model {m} missing')
    for i in items:
        if not (ASSETS / 'models/item' / f'{i}.json').exists():
            problem(f'item {i}: no item model')
    for p in sorted((ASSETS / 'models').rglob('*.json')):
        d = load(p)
        if d is None: continue
        where = str(p.relative_to(ASSETS))
        parent = d.get('parent')
        if parent:
            ns, path = (parent.split(':', 1) if ':' in parent else ('minecraft', parent))
            if ns == NS and path not in our_models:
                problem(f'{where}: parent {parent} missing')
            elif ns == 'minecraft' and van_models is not None and path not in van_models:
                problem(f'{where}: parent {parent} not in vanilla')
        slots = d.get('textures', {})
        for k, v in slots.items():
            if v.startswith('#'):
                if v[1:] not in slots:
                    problem(f'{where}: texture slot {k} points at unbound #{v[1:]}')
                continue
            ns, path = (v.split(':', 1) if ':' in v else ('minecraft', v))
            if ns == NS and path not in our_tex:
                problem(f'{where}: texture {v} missing')
            elif ns == 'minecraft' and van_tex is not None and path not in van_tex:
                problem(f'{where}: texture {v} not in vanilla')
        for e in d.get('elements', []):
            for f, face in e.get('faces', {}).items():
                t = face.get('texture', '')
                if t.startswith('#') and t[1:] not in slots:
                    problem(f'{where}: face {f} uses unbound texture {t}')
            for c in e['from'] + e['to']:
                if not -16 <= c <= 32:
                    problem(f'{where}: coordinate {c} outside -16..32')
            r = e.get('rotation')
            if r and r.get('angle') not in (-45, -22.5, 0, 22.5, 45):
                problem(f'{where}: rotation angle {r.get("angle")} is not allowed')


def check_recipes(items, van):
    van_items = van['items'] if van else None
    van_tags = van['item_tags'] if van else None
    for p in sorted((DATA / 'recipe').glob('*.json')):
        d = load(p)
        if d is None: continue
        where = str(p.relative_to(RES))
        result = d.get('result', {})
        rid = result.get('id') if isinstance(result, dict) else result
        if not rid:
            problem(f'{where}: no result id'); continue
        check_id(rid, 'result', items, van_items, where)
        ings = []
        if 'key' in d:
            ings += list(d['key'].values())
        ings += d.get('ingredients', [])
        for ing in ings:
            for one in (ing if isinstance(ing, list) else [ing]):
                if isinstance(one, dict):
                    one = one.get('item') or one.get('tag') or ''
                if one.startswith('#'):
                    tag = one[1:]
                    ns, path = (tag.split(':', 1) if ':' in tag else ('minecraft', tag))
                    if ns == 'minecraft' and van_tags is not None and path not in van_tags:
                        problem(f'{where}: item tag {one} not in vanilla')
                    elif ns == NS and not (DATA / 'tags/item' / f'{path}.json').exists():
                        problem(f'{where}: item tag {one} not defined')
                else:
                    check_id(one, 'ingredient', items, van_items, where)
        if 'pattern' in d:
            keys = set(d.get('key', {}))
            used = {c for row in d['pattern'] for c in row if c != ' '}
            for c in sorted(used - keys):
                problem(f'{where}: pattern uses "{c}" with no key')
            for c in sorted(keys - used):
                problem(f'{where}: key "{c}" unused in pattern')


def check_loot(items, blocks, van):
    van_items = van['items'] if van else None
    for b in blocks:
        if not (DATA / 'loot_table/blocks' / f'{b}.json').exists():
            problem(f'block {b}: no loot table, it will drop nothing')
    for p in sorted((DATA / 'loot_table').rglob('*.json')):
        d = load(p)
        if d is None: continue
        where = str(p.relative_to(RES))
        for pool in d.get('pools', []):
            for e in pool.get('entries', []):
                if e.get('type') == 'minecraft:item':
                    name = e.get('name', '')
                    if name in ('minecraft:air', 'air'):
                        problem(f'{where}: minecraft:air is not a valid loot item (use minecraft:empty)')
                    else:
                        check_id(name, 'loot item', items, van_items, where)


def check_advancements(items, blocks, van):
    van_items = van['items'] if van else None
    advs = {str(p.relative_to(DATA / 'advancement'))[:-5] for p in (DATA / 'advancement').rglob('*.json')}
    for p in sorted((DATA / 'advancement').rglob('*.json')):
        d = load(p)
        if d is None: continue
        where = str(p.relative_to(RES))
        parent = d.get('parent')
        if parent:
            ns, path = (parent.split(':', 1) if ':' in parent else ('minecraft', parent))
            if ns == NS and path not in advs:
                problem(f'{where}: parent {parent} missing')
        icon = d.get('display', {}).get('icon', {})
        iid = icon.get('id') if isinstance(icon, dict) else icon
        if iid:
            check_id(iid, 'icon', items, van_items, where)
        crit = d.get('criteria', {})
        if not crit:
            problem(f'{where}: no criteria')
        for req in d.get('requirements', []):
            for r in req:
                if r not in crit:
                    problem(f'{where}: requirement {r} names no criterion')
        for c in crit.values():
            cond = c.get('conditions', {}).get('location', {})
            if isinstance(cond, dict) and 'blocks' in cond:
                check_id(cond['blocks'], 'block', blocks, van_items, where)
        for rid in d.get('rewards', {}).get('recipes', []):
            ns, path = (rid.split(':', 1) if ':' in rid else ('minecraft', rid))
            if ns == NS and not (DATA / 'recipe' / f'{path}.json').exists():
                problem(f'{where}: reward recipe {rid} missing')


def main():
    src = java_text()
    items, blocks = registered()
    van = vanilla()
    check_lang(items, blocks, src)
    check_models(items, blocks, van)
    check_recipes(items, van)
    check_loot(items, blocks, van)
    check_advancements(items, blocks, van)
    for n in notes:
        print('note:', n)
    for p in problems:
        print('problem:', p)
    print(f'{len(items)} items, {len(blocks)} blocks, {len(problems)} problems')
    sys.exit(min(len(problems), 125))


if __name__ == '__main__':
    main()
