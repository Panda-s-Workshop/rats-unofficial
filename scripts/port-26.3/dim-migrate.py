"""Migrate Bumblezone's dimension data (26.1 -> 26.3).

usage: python -I dim-migrate.py <data/the_bumblezone dir> [--selftest <mc261 data/minecraft> <mc263 data/minecraft>]

Covers: worldgen/noise, worldgen/density_function, worldgen/noise_settings (+ new worldgen/material_rule),
worldgen/biome, dimension_type. Rules derived from vanilla 26.1 vs 26.3 data and codecs.
"""
import json
import math
import os
import sys

# ---------------------------------------------------------------- noise parameters (NormalNoise.createParity port)

def _octaves(base_octave, base_amplitude, count, normalize, mods):
    freq = math.pow(2.0, base_octave)
    amp = base_amplitude
    if normalize:
        amp *= math.pow(0.5, -(count - 1)) / (math.pow(0.5, -count) - 1.0)
    out = []
    for i in range(count):
        m = 1.0 if not mods else mods[i]
        if m != 0.0:
            out.append(abs(amp * m))
        freq *= 2.0
        amp *= 0.5
    return out


def _norm_factor(target_amp, octave_abs_amps):
    var = sum((0.2702247831245211 * a) ** 2 for a in octave_abs_amps)
    dev = math.sqrt(var)
    if dev == 0.0:
        return 0.0
    return (target_amp * (1.0 / 3.0)) / (dev * math.sqrt(2.0))


def _parity_norm(base_amp, count, mods):
    lo, hi = 10**9, -10**9
    for i in range(count):
        m = 1.0 if not mods else mods[i]
        if m != 0.0:
            lo, hi = min(lo, i), max(hi, i)
    span = hi - lo
    return base_amp * 0.5 * (1.0 / 3.0) / (0.1 * (1.0 + 1.0 / (span + 1)))


def parity_noise(first_octave, amplitudes):
    octs = _octaves(first_octave, 1.0, len(amplitudes), True, amplitudes)
    target = sum(octs)
    new_nf = _norm_factor(target, octs)
    base_amp = 1.0 if new_nf == 0.0 else _parity_norm(1.0, len(amplitudes), amplitudes) / new_nf
    out = {"base_octave": first_octave}
    if len(amplitudes) != 1:
        out["octave_count"] = len(amplitudes)
    if base_amp != 1.0:
        out["base_amplitude"] = base_amp
    if any(a != 1.0 for a in amplitudes):
        out["amplitude_modifiers"] = [float(a) for a in amplitudes]
    return out


def migrate_noise(d):
    if "firstOctave" not in d:
        return d
    return parity_noise(d["firstOctave"], d["amplitudes"])

# ---------------------------------------------------------------- density functions

BINARY = {"add", "mul", "min", "max", "sub", "div"}
CACHES = {"cache_once", "cache_2d", "flat_cache", "cache_all_in_cell"}
CELL_XZ, CELL_Y = 4, 4  # Bumblezone noise settings used size_horizontal 1 / size_vertical 1


def strip_ns(t):
    return t.split(":", 1)[1] if t.startswith("minecraft:") else t


def migrate_df(f):
    if isinstance(f, (int, float, str)):
        return f
    assert isinstance(f, dict), f
    f = dict(f)
    t = strip_ns(f.get("type", ""))
    if t in BINARY:
        f["left"] = migrate_df(f.pop("argument1"))
        f["right"] = migrate_df(f.pop("argument2"))
    elif t == "y_clamped_gradient":
        f = {"type": "minecraft:gradient", "axis": "y", "from_coordinate": f["from_y"], "to_coordinate": f["to_y"],
             "from_value": f["from_value"], "to_value": f["to_value"]}
    elif t in CACHES:
        f = {"type": "minecraft:cache", "input": migrate_df(f["argument"])}
    elif t == "interpolated":
        f = {"type": "minecraft:interpolated", "cell_size_xz": CELL_XZ, "cell_size_y": CELL_Y, "input": migrate_df(f.pop("argument"))}
    else:
        for k in ("argument", "input"):
            if k in f:
                f["input"] = migrate_df(f.pop(k))
        for k in ("shift_x", "shift_y", "shift_z", "when_in_range", "when_out_of_range", "alpha", "first", "second"):
            if k in f:
                f[k] = migrate_df(f[k])
        if t not in ("noise", "clamp", "the_bumblezone:biome_function", "constant") and not t.startswith("the_bumblezone:"):
            print("  ! unhandled density function type", t)
    return f

# ---------------------------------------------------------------- noise settings

ROUTER_KEEP = ["temperature", "vegetation", "continents", "erosion", "depth", "ridges", "final_density"]


def migrate_noise_settings(d, material_rule_id):
    out = {}
    out["noise"] = {"min_y": d["noise"]["min_y"], "height": d["noise"]["height"]}
    out["default_block"] = d["default_block"]
    out["default_fluid"] = d["default_fluid"]
    r = d["noise_router"]
    router = {k: migrate_df(r[k]) for k in ROUTER_KEEP}
    router["chunk_surface_level"] = migrate_df(r.get("preliminary_surface_level", 0))
    out["noise_router"] = router
    out["material_rule"] = material_rule_id
    # SpawnTargetPoint is now a map of density function -> range. Bumblezone's climate router is all zeroes, so the old
    # target was meaningless; an empty list makes getOrigin fall back to the default.
    out["spawn_target"] = []
    out["sea_level"] = d["sea_level"]
    out["disable_mob_generation"] = d["disable_mob_generation"]
    if d.get("aquifers_enabled"):
        print("  ! aquifers_enabled was true; 26.3 needs an explicit aquifers config")
    out["legacy_random_source"] = d["legacy_random_source"]
    return out, d["surface_rule"]

# ---------------------------------------------------------------- biomes

def spawn_count(e):
    lo, hi = e.pop("minCount"), e.pop("maxCount")
    return lo if lo == hi else {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}


def migrate_biome(d):
    d = dict(d)
    attrs = d.setdefault("attributes", {})
    spawners = d.pop("spawners", None)
    costs = d.pop("spawn_costs", None)
    prob = d.pop("creature_spawn_probability", None)
    if prob is not None:
        attrs["minecraft:gameplay/creature_world_gen_spawn_probability"] = prob
    if spawners is not None or costs is not None:
        by_cat = {}
        for cat, entries in (spawners or {}).items():
            if not entries:
                continue
            new = []
            for e in entries:
                e = dict(e)
                c = spawn_count(e)
                new.append({"type": e["type"], "count": c, "weight": e["weight"]})
            by_cat[cat] = new
        attrs["minecraft:gameplay/natural_mob_spawns"] = {
            "argument": {"spawn_costs": costs or {}, "spawns_by_category": by_cat},
            "modifier": "overlay",
        }
    return d

# ---------------------------------------------------------------- dimension type

def migrate_dimension_type(d):
    bed = d.get("attributes", {}).get("minecraft:gameplay/bed_rule")
    if isinstance(bed, dict) and "explodes" in bed:
        bed["destroy_on_use"] = bed.pop("explodes")
    return d

# ---------------------------------------------------------------- io

def load(p):
    return json.load(open(p, encoding="utf-8"))


def save(p, d):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(d, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def walk(d):
    for dp, _, fs in os.walk(d):
        for f in sorted(fs):
            if f.endswith(".json"):
                yield os.path.join(dp, f)


def selftest(old_root, new_root):
    bad = 0
    for p in walk(os.path.join(old_root, "worldgen", "noise")):
        rel = os.path.relpath(p, os.path.join(old_root, "worldgen", "noise"))
        q = os.path.join(new_root, "worldgen", "noise", rel)
        if not os.path.exists(q):
            continue
        mine, real = migrate_noise(load(p)), load(q)
        for k in set(mine) | set(real):
            a, b = mine.get(k), real.get(k)
            if isinstance(a, float) and isinstance(b, float):
                ok = abs(a - b) < 1e-9 * max(1, abs(b))
            else:
                ok = a == b
            if not ok:
                bad += 1
                print("noise mismatch", rel, k, a, b)
    print("noise selftest mismatches:", bad)


def main():
    if "--selftest" in sys.argv:
        i = sys.argv.index("--selftest")
        selftest(sys.argv[i + 1], sys.argv[i + 2])
        return
    root = sys.argv[1]
    wg = os.path.join(root, "worldgen")
    for p in walk(os.path.join(wg, "noise")):
        save(p, migrate_noise(load(p))); print("noise", os.path.basename(p))
    for p in walk(os.path.join(wg, "density_function")):
        save(p, migrate_df(load(p))); print("density_function", os.path.basename(p))
    for p in walk(os.path.join(wg, "noise_settings")):
        name = os.path.splitext(os.path.basename(p))[0]
        rule_id = "the_bumblezone:" + name
        ns, rule = migrate_noise_settings(load(p), rule_id)
        save(p, ns)
        save(os.path.join(wg, "material_rule", name + ".json"), rule)
        print("noise_settings", name, "-> material_rule", rule_id)
    for p in walk(os.path.join(wg, "biome")):
        save(p, migrate_biome(load(p))); print("biome", os.path.basename(p))
    for p in walk(os.path.join(root, "dimension_type")):
        save(p, migrate_dimension_type(load(p))); print("dimension_type", os.path.basename(p))


if __name__ == "__main__":
    main()
