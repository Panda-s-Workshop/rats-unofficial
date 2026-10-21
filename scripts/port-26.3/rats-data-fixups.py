"""Rats-specific 26.1 -> 26.3 data fixes that the shared scripts do not cover (run after criteria-migrate.py).

usage: python -I rats-data-fixups.py <data root>... [--dry]

  * item predicates (advancement `items` lists, match_tool `predicate`): the pre-1.20.5 `"tag": T` and
    `"enchantments": [...]` fields (silently ignored since 1.20.5, so the predicate matched any item) become
    `"items": "#T"` and `"predicates": {"minecraft:enchantments": [{"enchantments": E, "levels": L}]}`
  * advancement display icon: the pre-1.20.5 `nbt` string is dropped (ItemStackTemplate has id/count/components)
  * stonecutting recipes: a top-level `count` (ignored since 1.21) moves into `result.count`
  * neoforge:add_spawns biome modifiers: `minCount`/`maxCount` -> `count` (IntProvider)
  * trim materials: `asset_name` -> `palette_id` (`<ns>:trim/<asset_name>`)
Files keep their line endings; 2-space indent.
"""
import json, os, sys

DRY = '--dry' in sys.argv
STATS = {}


def bump(k):
    STATS[k] = STATS.get(k, 0) + 1


def fix_item_predicate(p):
    if not isinstance(p, dict):
        return p
    out = {}
    for k, v in p.items():
        if k == 'tag' and isinstance(v, str):
            out['items'] = v if v.startswith('#') else '#' + v
            bump('item predicate tag -> items')
        elif k == 'enchantments' and isinstance(v, list):
            preds = out.setdefault('predicates', {})
            preds['minecraft:enchantments'] = [
                {('enchantments' if kk == 'enchantment' else kk): vv for kk, vv in e.items()} for e in v]
            bump('item predicate enchantments -> predicates')
        else:
            out[k] = v
    return out


def walk_conditions(o):
    """Fix match_tool predicates anywhere in a loot condition / function tree."""
    if isinstance(o, dict):
        if o.get('type') == 'minecraft:match_tool' and 'predicate' in o:
            o['predicate'] = fix_item_predicate(o['predicate'])
        for v in o.values():
            walk_conditions(v)
    elif isinstance(o, list):
        for v in o:
            walk_conditions(v)


def mig_advancement(j):
    icon = j.get('display', {}).get('icon')
    if isinstance(icon, dict) and 'nbt' in icon:
        del icon['nbt']
        bump('advancement icon nbt dropped')
    for c in j.get('criteria', {}).values():
        cond = c.get('conditions')
        if isinstance(cond, dict) and isinstance(cond.get('items'), list):
            cond['items'] = [fix_item_predicate(x) for x in cond['items']]
        walk_conditions(cond)
    return j


def mig_loot(j):
    walk_conditions(j)
    return j


def mig_recipe(j):
    if j.get('type') == 'minecraft:stonecutting' and 'count' in j:
        n = j.pop('count')
        res = j.get('result')
        if isinstance(res, dict) and n != 1:
            res['count'] = n
        bump('stonecutting count -> result.count')
    return j


def spawner(e):
    if isinstance(e, dict) and 'minCount' in e:
        lo, hi = e.pop('minCount'), e.pop('maxCount')
        e['count'] = lo if lo == hi else {'type': 'minecraft:uniform', 'min_inclusive': lo, 'max_inclusive': hi}
        bump('spawner minCount/maxCount -> count')
    return e


def mig_biome_modifier(j):
    if j.get('type') == 'neoforge:add_spawns':
        s = j.get('spawners')
        j['spawners'] = [spawner(x) for x in s] if isinstance(s, list) else spawner(s)
    return j


def mig_trim(j, ns):
    if 'asset_name' in j:
        name = j.pop('asset_name')
        j = {'palette_id': f'{ns}:trim/{name}', **j}
        j.pop('override_armor_assets', None)
        bump('trim asset_name -> palette_id')
    return j


def kind(path):
    parts = path.replace(os.sep, '/').split('/')
    i = len(parts) - 1 - parts[::-1].index('data')
    return parts[i + 1], '/'.join(parts[i + 2:-1])


def main():
    roots = [a for a in sys.argv[1:] if not a.startswith('--')]
    changed = 0
    for root in roots:
        for dp, _, fs in os.walk(root):
            for f in sorted(fs):
                if not f.endswith('.json'):
                    continue
                p = os.path.join(dp, f)
                ns, folder = kind(p)
                raw = open(p, 'rb').read().decode('utf-8')
                j = json.loads(raw)
                before = json.dumps(j)
                if folder.startswith('advancement'):
                    j = mig_advancement(j)
                elif folder.startswith('loot_table') or folder.startswith('loot_modifiers'):
                    j = mig_loot(j)
                elif folder.startswith('recipe'):
                    j = mig_recipe(j)
                elif folder == 'neoforge/biome_modifier':
                    j = mig_biome_modifier(j)
                elif folder == 'trim_material':
                    j = mig_trim(j, ns)
                if json.dumps(j) == before:
                    continue
                changed += 1
                out = json.dumps(j, indent=2, ensure_ascii=False)
                if raw.endswith('\n'):
                    out += '\n'
                if '\r\n' in raw:
                    out = out.replace('\n', '\r\n')
                if not DRY:
                    open(p, 'wb').write(out.encode('utf-8'))
    for k, v in sorted(STATS.items()):
        print(v, k)
    print(changed, 'files', '(dry)' if DRY else 'changed')


if __name__ == '__main__':
    main()
