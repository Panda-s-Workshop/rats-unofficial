"""Migrate Minecraft 26.1 advancement / loot table / predicate-bearing JSON to the 26.3 formats.

Usage:
  python -I criteria-migrate.py --kind <advancement|loot_table|loot_modifier|enchantment|villager_trade|predicate> FILE...
  python -I criteria-migrate.py --auto ROOT...     (picks the kind from the data folder each file sits in)
  add --dry to only report which files would change.

Rules were derived from the vanilla 26.1.2 -> 26.3 data and the 26.3 codecs (see notes/criteria.md).
The file's line endings (CRLF/LF), trailing newline and 2-space indent are preserved; key order is kept.
Warnings for constructs the script could not map with certainty are printed to stderr with the file path.
"""
import json
import os
import sys

WARN = []
CUR = ['']


def warn(msg):
    WARN.append(f'{CUR[0]}: {msg}')


def ns(t):
    if t is None:
        return None
    return t if ':' in t else 'minecraft:' + t


def rename(d, old, new):
    """Rename key old -> new in place, keeping the key's position."""
    if old not in d:
        return d
    items = list(d.items())
    d.clear()
    for k, v in items:
        d[new if k == old else k] = v
    return d


def tagify(v):
    """TagKey codec ('ns:path') -> HolderSet codec ('#ns:path')."""
    if isinstance(v, str) and not v.startswith('#'):
        return '#' + v
    return v


# ---------------------------------------------------------------- number providers
def mig_num(v, kind):
    """NumberProvider -> ContextIntProvider (kind='int') or ContextFloatProvider (kind='float')."""
    if isinstance(v, bool):
        return v
    if isinstance(v, (int, float)):
        if kind == 'int':
            if float(v) != int(v):
                warn(f'non-integral number {v} in an int provider; truncated to {int(v)}')
            return int(v)
        return v
    if isinstance(v, str):
        return v  # a reference to a context provider registry entry
    if not isinstance(v, dict):
        return v
    t = ns(v.get('type'))
    if t is None:
        if 'value' in v and 'min' not in v:
            t = 'minecraft:constant'
        else:
            t = 'minecraft:uniform'  # 26.1 fell back to uniform when "type" was missing; 26.3 has no fallback
        v = {'type': t, **v}
    if t == 'minecraft:constant':
        if 'value' in v:
            v['value'] = mig_num(v['value'], kind) if kind == 'int' else v['value']
    elif t == 'minecraft:uniform':
        for k in ('min', 'max'):
            if k in v:
                v[k] = mig_num(v[k], kind)
    elif t == 'minecraft:binomial':
        if kind == 'float':
            warn('binomial is int-only in 26.3; wrapped in minecraft:from_int')
            inner = dict(v)
            inner['n'] = mig_num(inner.get('n'), 'int')
            inner['p'] = mig_num(inner.get('p'), 'float')
            return {'type': 'minecraft:from_int', 'input': inner}
        v['n'] = mig_num(v.get('n'), 'int')
        v['p'] = mig_num(v.get('p'), 'float')
    elif t == 'minecraft:sum':
        v['type'] = 'minecraft:add'
        rename(v, 'summands', 'inputs')
        v['inputs'] = [mig_num(x, kind) for x in v.get('inputs', [])]
    elif t == 'minecraft:enchantment_level':
        if kind == 'int':
            warn('enchantment_level is float-only in 26.3; wrapped in minecraft:from_float')
            return {'type': 'minecraft:from_float', 'input': v}
    elif t in ('minecraft:score', 'minecraft:storage', 'minecraft:environment_attribute'):
        warn(f'number provider {t} not verified against 26.3; left unchanged')
    return v


# ---------------------------------------------------------------- predicates
ENTITY_SIMPLE = {
    'type': 'minecraft:entity_type', 'distance': 'minecraft:distance', 'movement': 'minecraft:movement',
    'effects': 'minecraft:effects', 'nbt': 'minecraft:nbt', 'flags': 'minecraft:flags',
    'periodic_tick': 'minecraft:periodic_tick', 'team': 'minecraft:team', 'components': 'minecraft:components',
    'predicates': 'minecraft:predicates',
}
ENTITY_LOCATION = {'location': 'minecraft:location', 'stepping_on': 'minecraft:stepping_on',
                   'movement_affected_by': 'minecraft:movement_affected_by'}
ENTITY_NESTED = {'vehicle': 'minecraft:vehicle', 'passenger': 'minecraft:passenger',
                 'targeted_entity': 'minecraft:targeted_entity'}
TYPE_SPECIFIC = {'minecraft:slime': 'minecraft:type_specific/cube_mob'}


def mig_entity(p):
    if not isinstance(p, dict):
        return p
    if any(k.startswith('minecraft:') for k in p):  # already 26.3
        return p
    out = {}
    for k, v in p.items():
        if k in ENTITY_SIMPLE:
            out[ENTITY_SIMPLE[k]] = v
        elif k in ENTITY_LOCATION:
            out[ENTITY_LOCATION[k]] = mig_location(v)
        elif k in ENTITY_NESTED:
            out[ENTITY_NESTED[k]] = mig_entity(v)
        elif k == 'equipment':
            out['minecraft:equipment'] = {s: mig_item(i) for s, i in v.items()}
        elif k == 'slots':
            out['minecraft:slots'] = {s: mig_item(i) for s, i in v.items()}
        elif k == 'type_specific':
            ts = dict(v)
            t = ns(ts.pop('type', None))
            if t is None:
                warn('type_specific without type; dropped')
                continue
            if t == 'minecraft:player' and 'looking_at' in ts:
                ts['looking_at'] = mig_entity(ts['looking_at'])
            if t == 'minecraft:lightning' and 'entity_struck' in ts:
                ts['entity_struck'] = mig_entity(ts['entity_struck'])
            if t.startswith('minecraft:'):
                key = TYPE_SPECIFIC.get(t, 'minecraft:type_specific/' + t.split(':', 1)[1])
            else:
                key = t  # modded sub predicate: its registry id is the key now
            out[key] = ts
        else:
            warn(f'unknown entity predicate key {k!r}; kept')
            out[k] = v
    return out


def mig_item(p):
    # ItemPredicate keeps its fields in 26.3 (items/count/components/predicates).
    return p


def mig_block(p):
    return p


def mig_location(p):
    if not isinstance(p, dict):
        return p
    if 'block' in p:
        p['block'] = mig_block(p['block'])
    return p


def mig_damage_source(p):
    if not isinstance(p, dict):
        return p
    for t in p.get('tags', []):
        if isinstance(t, dict) and 'id' in t:
            t['id'] = tagify(t['id'])
    for k in ('direct_entity', 'source_entity'):
        if k in p:
            p[k] = mig_entity(p[k])
    return p


def mig_damage(p):
    if not isinstance(p, dict):
        return p
    if 'source_entity' in p:
        p['source_entity'] = mig_entity(p['source_entity'])
    if 'type' in p:
        p['type'] = mig_damage_source(p['type'])
    return p


# ---------------------------------------------------------------- loot conditions
def mig_condition(c):
    if isinstance(c, str):
        return c  # holder reference to a data/<ns>/predicate entry
    if isinstance(c, list):
        return conds_to_one(c)
    if not isinstance(c, dict):
        return c
    if 'condition' in c and isinstance(c['condition'], str):
        rename(c, 'condition', 'type')
    t = ns(c.get('type'))
    if t is None:
        warn('condition without a type; left unchanged')
        return c
    c['type'] = t
    if t == 'minecraft:block_state_property':
        c['type'] = 'minecraft:match_block'
        rename(c, 'block', 'blocks')
        rename(c, 'properties', 'state')
    elif t == 'minecraft:entity_properties':
        if 'predicate' in c:
            c['predicate'] = mig_entity(c['predicate'])
    elif t == 'minecraft:damage_source_properties':
        if 'predicate' in c:
            c['predicate'] = mig_damage_source(c['predicate'])
    elif t == 'minecraft:match_tool':
        if 'predicate' in c:
            c['predicate'] = mig_item(c['predicate'])
    elif t == 'minecraft:location_check':
        if 'predicate' in c:
            c['predicate'] = mig_location(c['predicate'])
    elif t == 'minecraft:inverted':
        c['term'] = mig_condition(c['term'])
    elif t in ('minecraft:any_of', 'minecraft:all_of'):
        c['terms'] = [mig_condition(x) for x in c.get('terms', [])]
    elif t == 'minecraft:random_chance':
        if 'chance' in c:
            c['chance'] = mig_num(c['chance'], 'float')
    elif t == 'minecraft:random_chance_with_enchanted_bonus':
        if 'unenchanted_chance' in c:
            c['unenchanted_chance'] = mig_num(c['unenchanted_chance'], 'float')
    elif t == 'minecraft:reference':
        return c.get('name')  # LootItemCondition.CODEC is a holder codec: a bare id references a predicate file
    elif t == 'minecraft:value_check':
        c['type'] = 'minecraft:int_value_check'
        c['value'] = mig_num(c.get('value'), 'int')
        rename(c, 'range', 'test')
        c['test'] = mig_range(c['test'])
        warn('value_check -> int_value_check (unverified against vanilla data)')
    elif t == 'minecraft:entity_scores':
        c['scores'] = {k: mig_range(v) for k, v in c.get('scores', {}).items()}
    elif t == 'minecraft:time_check':
        if 'value' in c:
            c['value'] = mig_range(c['value'])
    return c


def mig_range(r):
    """IntRange -> IntRangePredicate (int point or {min,max} line of int providers)."""
    if isinstance(r, dict):
        out = dict(r)
        for k in ('min', 'max'):
            if k in out:
                out[k] = mig_num(out[k], 'int')
        return out
    return mig_num(r, 'int')


def conds_to_one(lst):
    lst = [mig_condition(x) for x in lst]
    if not lst:
        return None
    if len(lst) == 1:
        return lst[0]
    return {'type': 'minecraft:all_of', 'terms': lst}


def move_conditions(d):
    """'conditions': [..] -> 'condition': {..} (omitted when empty), keeping the key position."""
    if 'conditions' in d and isinstance(d['conditions'], list):
        one = conds_to_one(d['conditions'])
        rename(d, 'conditions', 'condition')
        if one is None:
            del d['condition']
        else:
            d['condition'] = one
    elif 'condition' in d and not isinstance(d.get('condition'), str):
        d['condition'] = mig_condition(d['condition'])


# ---------------------------------------------------------------- loot functions
def mig_function(f):
    if isinstance(f, str):
        return f
    if isinstance(f, list):
        return [mig_function(x) for x in f]
    if not isinstance(f, dict):
        return f
    if 'function' in f:
        rename(f, 'function', 'type')
    t = ns(f.get('type'))
    if t is None:
        warn('function without a type; left unchanged')
        return f
    f['type'] = t
    move_conditions(f)
    if t == 'minecraft:set_count':
        f['count'] = mig_num(f.get('count'), 'int')
    elif t == 'minecraft:set_damage':
        f['damage'] = mig_num(f.get('damage'), 'float')
    elif t == 'minecraft:enchant_with_levels':
        f['levels'] = mig_num(f.get('levels'), 'int')
        # "treasure" (pre-1.21 field) was already ignored by the 26.1 codec; it is left in place
    elif t == 'minecraft:enchanted_count_increase':
        f['count'] = mig_num(f.get('count'), 'float')
    elif t == 'minecraft:limit_count':
        lim = f.get('limit')
        if isinstance(lim, (int, float)) and not isinstance(lim, bool):
            f['limit'] = {'min': int(lim), 'max': int(lim)}
        elif isinstance(lim, dict):
            f['limit'] = {k: (mig_num(v, 'int') if k in ('min', 'max') else v) for k, v in lim.items()}
    elif t == 'minecraft:set_enchantments':
        f['enchantments'] = {k: mig_num(v, 'int') for k, v in f.get('enchantments', {}).items()}
    elif t == 'minecraft:set_ominous_bottle_amplifier':
        f['amplifier'] = mig_num(f.get('amplifier'), 'int')
    elif t == 'minecraft:set_random_dyes':
        f['number_of_dyes'] = mig_num(f.get('number_of_dyes'), 'int')
    elif t == 'minecraft:set_stew_effect':
        for e in f.get('effects', []):
            if 'duration' in e:
                e['duration'] = mig_num(e['duration'], 'int')
    elif t == 'minecraft:set_attributes':
        for m in f.get('modifiers', []):
            if 'amount' in m:
                m['amount'] = mig_num(m['amount'], 'float')
    elif t == 'minecraft:set_custom_model_data':
        fl = f.get('floats')
        if isinstance(fl, list):
            f['floats'] = [mig_num(x, 'float') for x in fl]
        elif isinstance(fl, dict) and isinstance(fl.get('values'), list):
            fl['values'] = [mig_num(x, 'float') for x in fl['values']]
    elif t == 'minecraft:exploration_map':
        if 'destination' in f:
            f['destination'] = tagify(f['destination'])
        else:
            f['destination'] = '#minecraft:on_treasure_maps'  # 26.1 default; 26.3 makes it required
    elif t == 'minecraft:set_loot_table':
        rename(f, 'name', 'loot_table_id')
        if 'type' in f and f['type'] != t:
            pass
        # the block entity "type" field is gone; the function's own "type" key now holds the function id
    elif t == 'minecraft:filtered':
        if 'item_filter' in f:
            f['item_filter'] = mig_item(f['item_filter'])
        for k in ('on_pass', 'on_fail'):
            if k in f:
                f[k] = mig_function(f[k])
    elif t == 'minecraft:modify_contents':
        if 'modifier' in f:
            f['modifier'] = mig_function(f['modifier'])
    elif t == 'minecraft:sequence':
        f['functions'] = [mig_function(x) for x in f.get('functions', [])]
    elif t == 'minecraft:reference':
        return f.get('name')  # LootItemFunctions.CODEC is a holder codec: a bare id references an item_modifier file
    return f


def move_functions(d, old='functions', new='modifier'):
    """'functions': [..] -> 'modifier': {..} | [..] (omitted when empty), keeping the key position."""
    if old in d and isinstance(d[old], list):
        lst = [mig_function(x) for x in d[old]]
        rename(d, old, new)
        if not lst:
            del d[new]
        else:
            d[new] = lst[0] if len(lst) == 1 else lst


# ---------------------------------------------------------------- loot tables
def mig_entry(e):
    if not isinstance(e, dict):
        return e
    t = ns(e.get('type'))
    move_conditions(e)
    move_functions(e)
    if t == 'minecraft:tag':
        if 'name' in e:
            rename(e, 'name', 'items')
            e['items'] = tagify(e['items'])
    elif t == 'minecraft:loot_table':
        if isinstance(e.get('value'), dict):
            e['value'] = mig_loot_table(e['value'])
    if 'children' in e:
        e['children'] = [mig_entry(x) for x in e['children']]
    return e


def mig_pool(p):
    move_conditions(p)
    move_functions(p)
    if 'rolls' in p:
        p['rolls'] = mig_num(p['rolls'], 'int')
    if 'bonus_rolls' in p:
        p['bonus_rolls'] = mig_num(p['bonus_rolls'], 'float')
    p['entries'] = [mig_entry(x) for x in p.get('entries', [])]
    return p


def mig_loot_table(j):
    move_functions(j)
    if 'pools' in j:
        j['pools'] = [mig_pool(p) for p in j['pools']]
    return j


# ---------------------------------------------------------------- advancements
# Trigger fields that were ContextAwarePredicate (entity predicate object OR list of conditions) and are now a
# single LootItemCondition holder.
CAP_FIELDS = {'player', 'entity', 'bystander', 'cause', 'child', 'lightning', 'parent', 'partner', 'projectile',
              'source', 'villager', 'zombie', 'location'}
ITEM_FIELDS = {'item', 'rod', 'fired_from_weapon'}
ITEM_LIST_FIELDS = {'items', 'ingredients'}


def mig_cap(v):
    if isinstance(v, list):
        return conds_to_one(v)
    if isinstance(v, dict):
        if 'type' in v and isinstance(v['type'], str) and ('condition' in v or v['type'] in LOOT_CONDITION_TYPES):
            return mig_condition(v)
        if 'condition' in v:
            return mig_condition(v)
        return {'type': 'minecraft:entity_properties', 'entity': 'this', 'predicate': mig_entity(v)}
    return v


LOOT_CONDITION_TYPES = set()  # filled lazily; only used to recognise already-migrated values


def mig_criterion(cv):
    trig = ns(cv.get('trigger'))
    cond = cv.get('conditions')
    if not isinstance(cond, dict):
        return cv
    for k in list(cond.keys()):
        if k in CAP_FIELDS:
            new = mig_cap(cond[k])
            if new is None:
                del cond[k]
            else:
                cond[k] = new
        elif k == 'victims':
            cond[k] = [mig_cap(x) for x in cond[k]]
        elif k in ITEM_FIELDS:
            cond[k] = mig_item(cond[k])
        elif k in ITEM_LIST_FIELDS and isinstance(cond[k], list):
            cond[k] = [mig_item(x) for x in cond[k]]
    if trig in ('minecraft:recipe_crafted', 'minecraft:crafter_recipe_crafted'):
        rename(cond, 'recipe_id', 'recipes')
    elif trig == 'minecraft:recipe_unlocked':
        rename(cond, 'recipe', 'recipes')
    elif trig == 'minecraft:player_generates_container_loot':
        rename(cond, 'loot_table', 'loot_tables')
    elif trig == 'minecraft:brewed_potion':
        if isinstance(cond.get('potion'), str):
            cond['potion'] = {'potions': cond['potion']}
    elif trig in ('minecraft:bee_nest_destroyed', 'minecraft:enter_block', 'minecraft:slide_down_block'):
        rename(cond, 'block', 'blocks')
    elif trig in ('minecraft:player_killed_entity', 'minecraft:entity_killed_player'):
        if 'killing_blow' in cond:
            cond['killing_blow'] = mig_damage_source(cond['killing_blow'])
    elif trig in ('minecraft:player_hurt_entity', 'minecraft:entity_hurt_player'):
        if 'damage' in cond:
            cond['damage'] = mig_damage(cond['damage'])
    elif trig in ('minecraft:location', 'minecraft:slept_in_bed', 'minecraft:hero_of_the_village',
                  'minecraft:voluntary_exile'):
        pass
    if 'start_position' in cond:
        cond['start_position'] = mig_location(cond['start_position'])
    return cv


def mig_advancement(j):
    for cn, cv in j.get('criteria', {}).items():
        mig_criterion(cv)
    return j


# ---------------------------------------------------------------- block states
def mig_block_state(s):
    """26.1 {"Name":..,"Properties":..} -> 26.3 {"id":..,"properties":..} (StateHolder codec, lowercase keys)."""
    if isinstance(s, dict) and 'Name' in s and set(s) <= {'Name', 'Properties'}:
        out = {'id': s['Name']}
        if s.get('Properties'):
            out['properties'] = s['Properties']
        return out
    return s


def mig_state_provider(p):
    """BlockStateProvider: a simple_state_provider is now written as the bare block state."""
    if isinstance(p, dict) and ns(p.get('type')) == 'minecraft:simple_state_provider' and set(p) <= {'type', 'state'}:
        return mig_block_state(p['state'])
    return mig_block_state(p)


# ---------------------------------------------------------------- other files
def mig_enchantment(j):
    def walk(o):
        if isinstance(o, dict):
            for k, v in list(o.items()):
                if k == 'requirements':
                    o[k] = mig_condition(v)
                elif k == 'block_state':
                    o[k] = mig_state_provider(v)
                elif isinstance(v, dict) and 'Name' in v:
                    o[k] = mig_block_state(v)
                else:
                    walk(v)
        elif isinstance(o, list):
            for i, v in enumerate(o):
                if isinstance(v, dict) and 'Name' in v:
                    o[i] = mig_block_state(v)
                else:
                    walk(v)
    walk(j.get('effects', {}))
    return j


def mig_villager_trade(j):
    move_functions(j, 'given_item_modifiers', 'given_item_modifier')
    if 'merchant_predicate' in j:
        j['merchant_predicate'] = mig_condition(j['merchant_predicate'])
    return j


def mig_loot_modifier(j):
    move_conditions(j)
    return j


def mig_predicate(j):
    return mig_condition(j)


KINDS = {
    'advancement': mig_advancement, 'loot_table': mig_loot_table, 'loot_modifier': mig_loot_modifier,
    'enchantment': mig_enchantment, 'villager_trade': mig_villager_trade, 'predicate': mig_predicate,
    'item_modifier': mig_function,
}
FOLDERS = {'advancement': 'advancement', 'loot_table': 'loot_table', 'loot_modifiers': 'loot_modifier',
           'enchantment': 'enchantment', 'villager_trade': 'villager_trade', 'predicate': 'predicate',
           'item_modifier': 'item_modifier'}


def kind_for(path):
    parts = path.replace('\\', '/').split('/')
    if 'data' in parts:
        i = len(parts) - 1 - parts[::-1].index('data')
        if i + 2 < len(parts):
            return FOLDERS.get(parts[i + 2])
    return None


def migrate_obj(j, kind):
    return KINDS[kind](j)


def migrate_file(path, kind, dry=False):
    raw = open(path, 'rb').read()
    text = raw.decode('utf-8-sig' if raw.startswith(b'\xef\xbb\xbf') else 'utf-8')
    crlf = '\r\n' in text
    trailing = text.endswith('\n')
    j = json.loads(text)
    CUR[0] = path
    before = json.dumps(j, sort_keys=False)
    j = migrate_obj(j, kind)
    after = json.dumps(j, sort_keys=False)
    if before == after:
        return False
    out = json.dumps(j, indent=2, ensure_ascii=False)
    if trailing:
        out += '\n'
    if crlf:
        out = out.replace('\n', '\r\n')
    if not dry:
        with open(path, 'wb') as fh:
            fh.write(out.encode('utf-8'))
    return True


def main(argv):
    dry = '--dry' in argv
    argv = [a for a in argv if a != '--dry']
    if argv and argv[0] == '--kind':
        kind = argv[1]
        files = argv[2:]
        pairs = [(f, kind) for f in files]
    elif argv and argv[0] == '--auto':
        pairs = []
        for root in argv[1:]:
            for dp, _, fs in os.walk(root):
                for f in sorted(fs):
                    if f.endswith('.json'):
                        p = os.path.join(dp, f)
                        k = kind_for(p)
                        if k:
                            pairs.append((p, k))
    else:
        print(__doc__)
        return 2
    changed = 0
    for p, k in pairs:
        try:
            if migrate_file(p, k, dry):
                changed += 1
        except Exception as e:  # report and continue
            print(f'ERROR {p}: {e!r}', file=sys.stderr)
    for w in WARN:
        print('WARN', w, file=sys.stderr)
    print(f'{changed}/{len(pairs)} files {"would change" if dry else "changed"}')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
