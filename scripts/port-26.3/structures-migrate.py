"""Migrate Bumblezone processor_list / structure / structure_set / template_pool JSON from MC 26.1 to 26.3.

usage: python -I structures-migrate.py <common/src/main/resources dir> [--dry]

Changes (taken from vanilla 26.1 -> 26.3 data diffs):
  * Block states: 26.3 BlockState.CODEC is either(block id string, {"id": ..., "properties": {...}}).
    The old {"Name": ..., "Properties": {...}} form no longer parses.
    {"Name": X} -> "X";  {"Name": X, "Properties": P} -> {"id": X, "properties": P}.
  * Structure spawn_overrides entries: "minCount"/"maxCount" -> "count" (IntProvider):
    an int when equal, else {"type": "minecraft:uniform", "min_inclusive", "max_inclusive"}.
Files are rewritten only if something changed, keeping indentation style and CRLF/LF line endings.
"""
import json, os, re, sys

CATS = ['processor_list', 'structure', 'structure_set', 'template_pool']


def convert_state(o):
    if (isinstance(o, dict) and 'Name' in o and isinstance(o['Name'], str)
            and set(o.keys()) <= {'Name', 'Properties'}):
        props = o.get('Properties')
        if not props:
            return o['Name'], True
        return {'id': o['Name'], 'properties': props}, True
    return o, False


def walk(o, cat, path, stats):
    if isinstance(o, dict):
        new, changed = convert_state(o)
        if changed:
            stats['state'] += 1
            return new
        if cat == 'structure' and 'minCount' in o and 'maxCount' in o and 'type' in o:
            lo, hi = o['minCount'], o['maxCount']
            count = lo if lo == hi else {'type': 'minecraft:uniform', 'min_inclusive': lo, 'max_inclusive': hi}
            out = {}
            for k, v in o.items():
                if k == 'minCount':
                    out['count'] = count
                elif k == 'maxCount':
                    continue
                else:
                    out[k] = v
            stats['spawn'] += 1
            return out
        return {k: walk(v, cat, path + '.' + k, stats) for k, v in o.items()}
    if isinstance(o, list):
        return [walk(v, cat, path + '[]', stats) for v in o]
    return o


def dump_like(obj, original):
    t = original.replace('\r\n', '\n')
    m = re.search(r'\n( +|\t)\S', t)
    ind = m.group(1) if m else '  '
    best = None
    for sep in [(', ', ': '), (',', ': ')]:
        cand = json.dumps(json.loads(t), indent=ind, ensure_ascii=False, separators=sep)
        if cand == t.rstrip('\n'):
            best = sep
            break
    sep = best or (',', ': ')
    out = json.dumps(obj, indent=ind, ensure_ascii=False, separators=sep)
    if t.endswith('\n'):
        out += '\n'
    if '\r\n' in original:
        out = out.replace('\n', '\r\n')
    return out, best is not None


def roots(base):
    yield os.path.join(base, 'data')
    dp = os.path.join(base, 'datapacks')
    if os.path.isdir(dp):
        for d in sorted(os.listdir(dp)):
            yield os.path.join(dp, d, 'data')


def main():
    base = sys.argv[1]
    dry = '--dry' in sys.argv
    total = {'files': 0, 'state': 0, 'spawn': 0}
    reformatted = []
    for root in roots(base):
        if not os.path.isdir(root):
            continue
        for ns in sorted(os.listdir(root)):
            for cat in CATS:
                d = os.path.join(root, ns, 'worldgen', cat)
                for dp, _, fs in os.walk(d):
                    for f in sorted(fs):
                        if not f.endswith('.json'):
                            continue
                        p = os.path.join(dp, f)
                        raw = open(p, 'rb').read().decode('utf-8')
                        stats = {'state': 0, 'spawn': 0}
                        new = walk(json.loads(raw), cat, '', stats)
                        if not (stats['state'] or stats['spawn']):
                            continue
                        out, exact = dump_like(new, raw)
                        if not exact:
                            reformatted.append(p)
                        total['files'] += 1
                        total['state'] += stats['state']
                        total['spawn'] += stats['spawn']
                        if not dry:
                            open(p, 'wb').write(out.encode('utf-8'))
    print(total)
    for p in reformatted:
        print('formatting not round-trippable, rewritten with json.dumps:', p)


if __name__ == '__main__':
    main()
