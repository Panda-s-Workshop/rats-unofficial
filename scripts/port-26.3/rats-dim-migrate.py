"""Migrate the Ratlantis dimension data (built-in `ratlantis` datapack) from 26.1 to 26.3.

usage: python -I rats-dim-migrate.py <datapack data/rats dir>

Covers (rules from vanilla 26.1 -> 26.3 data, codecs, and scripts/port-26.3/dim-migrate.py):
  * worldgen/noise_settings: noise cell size moves into `interpolated` (size_horizontal 1 / size_vertical 2 ->
    cell_size_xz 4 / cell_size_y 8); argument1/argument2 -> left/right, argument -> input; y_clamped_gradient ->
    gradient on y; cache_2d -> cache; shifted_noise -> noise with shift_x/shift_z; end_islands -> vanilla
    "minecraft:end/islands" (the same centre-island + outer-island function, now split in two);
    preliminary_surface_level -> chunk_surface_level; barrier/fluid/lava noises -> "aquifers" (exclusion 0: the
    router's depth is 0, so the old deep-dark check never applied); vein noises and ore_veins_enabled dropped (ore
    veins are material rules now and were disabled); surface_rule -> worldgen/material_rule/<name>.json
  * worldgen/biome: spawners/spawn_costs -> natural_mob_spawns attribute, creature_spawn_probability ->
    creature_world_gen_spawn_probability attribute, effects foliage/grass_color_override -> foliage/grass_color
    (26.1 key names; the *_override keys were ignored by 26.1), plus the snow_golem_melts and
    can_pillager_patrol_spawn attributes that replace the minecraft:snow_golem_melts / without_patrol_spawns tags
  * worldgen/configured_carver -> worldgen/carver with the config inline, in vanilla's 26.3 minecraft:cave form
  * block states -> {"id", "properties"} / plain id
"""
import json
import os
import sys

BINARY = {"add", "mul", "min", "max", "sub", "div"}
CELL_XZ, CELL_Y = 4, 8


def strip_ns(t):
    return t.split(":", 1)[1] if isinstance(t, str) and t.startswith("minecraft:") else t


def mig_df(f):
    if isinstance(f, (int, float, str)):
        return f
    f = dict(f)
    t = strip_ns(f.get("type", ""))
    if t in BINARY:
        return {"type": "minecraft:" + t, "left": mig_df(f["argument1"]), "right": mig_df(f["argument2"])}
    if t == "y_clamped_gradient":
        return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": f["from_y"], "to_coordinate": f["to_y"],
                "from_value": f["from_value"], "to_value": f["to_value"]}
    if t in ("cache_once", "cache_2d", "flat_cache", "cache_all_in_cell"):
        return {"type": "minecraft:cache", "input": mig_df(f["argument"])}
    if t == "interpolated":
        return {"type": "minecraft:interpolated", "cell_size_xz": CELL_XZ, "cell_size_y": CELL_Y,
                "input": mig_df(f["argument"])}
    if t in ("blend_density", "squeeze", "abs", "square", "cube", "half_negative", "quarter_negative"):
        return {"type": "minecraft:" + t, "input": mig_df(f["argument"])}
    if t == "end_islands":
        return "minecraft:end/islands"
    if t == "shifted_noise":
        out = {"type": "minecraft:noise", "noise": f["noise"]}
        for k in ("shift_x", "shift_y", "shift_z"):
            if f.get(k) not in (None, 0, 0.0):
                out[k] = mig_df(f[k])
        out["xz_scale"] = f["xz_scale"]
        out["y_scale"] = f["y_scale"]
        return out
    raise SystemExit(f"unhandled density function type {t}")


def state(o):
    if isinstance(o, dict) and "Name" in o and set(o) <= {"Name", "Properties"}:
        return {"id": o["Name"], "properties": o["Properties"]} if o.get("Properties") else o["Name"]
    if isinstance(o, dict):
        return {k: state(v) for k, v in o.items()}
    if isinstance(o, list):
        return [state(v) for v in o]
    return o


def mig_noise_settings(d, rule_id):
    r = d["noise_router"]
    assert d["noise"]["size_horizontal"] == 1 and d["noise"]["size_vertical"] == 2, d["noise"]
    surface = mig_df(r["preliminary_surface_level"])
    out = {
        "noise": {"min_y": d["noise"]["min_y"], "height": d["noise"]["height"]},
        "default_block": state(d["default_block"]),
        "default_fluid": state(d["default_fluid"]),
        "noise_router": {
            "temperature": mig_df(r["temperature"]),
            "vegetation": mig_df(r["vegetation"]),
            "continents": mig_df(r["continents"]),
            "erosion": mig_df(r["erosion"]),
            "depth": mig_df(r["depth"]),
            "ridges": mig_df(r["ridges"]),
            "chunk_surface_level": surface,
            "final_density": mig_df(r["final_density"]),
        },
        "material_rule": rule_id,
        "spawn_target": d["spawn_target"],
        "sea_level": d["sea_level"],
        "disable_mob_generation": d["disable_mob_generation"],
    }
    if d.get("aquifers_enabled"):
        out["aquifers"] = {
            "barrier": mig_df(r["barrier"]),
            "fluid_level_floodedness": mig_df(r["fluid_level_floodedness"]),
            "fluid_level_spread": mig_df(r["fluid_level_spread"]),
            "lava": mig_df(r["lava"]),
            "exclusion": 0.0,
            "surface_level": surface,
        }
    if d.get("ore_veins_enabled"):
        raise SystemExit("ore veins were enabled; port them to an ore_vein material rule")
    out["legacy_random_source"] = d["legacy_random_source"]
    return out, state(d["surface_rule"])


def mig_biome(d):
    d = dict(d)
    attrs = dict(d.pop("attributes", {}))
    prob = d.pop("creature_spawn_probability", None)
    spawners = d.pop("spawners", None)
    costs = d.pop("spawn_costs", None)
    if prob is not None:
        attrs["minecraft:gameplay/creature_world_gen_spawn_probability"] = prob
    if spawners is not None or costs is not None:
        by_cat = {}
        for cat, entries in (spawners or {}).items():
            if entries:
                new = []
                for e in entries:
                    lo, hi = e["minCount"], e["maxCount"]
                    count = lo if lo == hi else {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}
                    new.append({"type": e["type"], "count": count, "weight": e["weight"]})
                by_cat[cat] = new
        attrs["minecraft:gameplay/natural_mob_spawns"] = {
            "argument": {"spawn_costs": costs or {}, "spawns_by_category": by_cat}, "modifier": "overlay"}
    attrs["minecraft:gameplay/snow_golem_melts"] = True
    attrs["minecraft:gameplay/can_pillager_patrol_spawn"] = False
    renames = {"foliage_color_override": "foliage_color", "grass_color_override": "grass_color"}
    d["effects"] = {renames.get(k, k): v for k, v in d.get("effects", {}).items()}
    out = {"attributes": dict(sorted(attrs.items()))}
    out.update(d)
    return out


def mig_carver(d):
    assert d["type"] == "minecraft:cave", d["type"]
    c = d["config"]
    return {
        "type": "minecraft:cave",
        # 26.1's CaveWorldCarver hard-coded count and thickness (same values as vanilla's 26.3 minecraft:cave)
        "count": {"type": "minecraft:very_biased_to_bottom", "max_inclusive": 14, "min_inclusive": 0},
        "floor_level": c["floor_level"],
        "horizontal_radius_multiplier": c["horizontal_radius_multiplier"],
        "probability": c["probability"],
        "room_vertical_radius_multiplier": c["yScale"],
        "thickness": {"type": "minecraft:trapezoid", "max": 3.0, "min": 0.0, "plateau": 1.0},
        "vertical_radius_multiplier": c["vertical_radius_multiplier"],
        "weird_thickness_bias": True,
        "y": c["y"],
        # dropped: replaceable (carvers now skip #uncarvable), lava_level (the fluid picker places lava below
        # y -54 only, below this dimension), debug_settings
    }


def load(p):
    return json.loads(open(p, encoding="utf-8").read())


def save(p, d):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps(d, indent=2, ensure_ascii=False) + "\n")


def main():
    root = sys.argv[1]
    wg = os.path.join(root, "worldgen")
    ns = os.path.basename(os.path.normpath(root))
    for f in sorted(os.listdir(os.path.join(wg, "noise_settings"))):
        p = os.path.join(wg, "noise_settings", f)
        name = f[:-5]
        settings, rule = mig_noise_settings(load(p), f"{ns}:{name}")
        save(p, settings)
        save(os.path.join(wg, "material_rule", f), rule)
        print("noise_settings", name, "-> material_rule", f"{ns}:{name}")
    for f in sorted(os.listdir(os.path.join(wg, "biome"))):
        p = os.path.join(wg, "biome", f)
        save(p, mig_biome(load(p)))
        print("biome", f)
    cc = os.path.join(wg, "configured_carver")
    if os.path.isdir(cc):
        for f in sorted(os.listdir(cc)):
            save(os.path.join(wg, "carver", f), mig_carver(load(os.path.join(cc, f))))
            os.remove(os.path.join(cc, f))
            print("configured_carver -> carver", f)
        os.rmdir(cc)


if __name__ == "__main__":
    main()
