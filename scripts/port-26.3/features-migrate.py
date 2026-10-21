"""Migrate Bumblezone worldgen feature JSON from MC 26.1 to 26.3.

Usage: python -I features-migrate.py <resources-root> [<resources-root> ...] [--dry-run]

For every data/**/worldgen/configured_feature/ directory (tags excluded):
  * moves each file to the sibling worldgen/feature/ directory (same relative path),
  * inlines "config" beside "type" (26.3 features are their own configured instance),
  * minecraft:tree: drops dirt_provider/force_dirt (already ignored on 26.1) and adds
    below_trunk_provider = "minecraft:soil_beneath_tree" (26.1's implicit default, now required).
For every worldgen/placed_feature/ file (rewritten in place, only when something changes):
  * minecraft:random_offset {xz_spread, y_spread} -> minecraft:offset {x, y, z} (x and z get the xz provider).
Everywhere in those files:
  * block/fluid states {"Name", "Properties"} -> {"id", "properties"} (26.3 StateHolder codec), except inside
    "state_nbt" (raw NBT for block entities),
  * {"type": "minecraft:simple_state_provider", "state": S} -> S (the type is gone; providers accept a plain state).
Keeps each file's line endings and trailing newline, 2-space indent, and short numeric arrays on one line.
"""
import json
import os
import re
import sys
from pathlib import Path

args = [a for a in sys.argv[1:] if not a.startswith("--")]
DRY = "--dry-run" in sys.argv
changes = []


def convert_state(o):
    if isinstance(o, dict) and "Name" in o and set(o) <= {"Name", "Properties"} and isinstance(o["Name"], str):
        n = {"id": o["Name"]}
        if "Properties" in o:
            n["properties"] = o["Properties"]
        return n, True
    return o, False


def walk(o, key=None):
    """Return (new_obj, changed)."""
    if key == "state_nbt":
        return o, False
    changed = False
    if isinstance(o, dict):
        o, c = convert_state(o)
        changed |= c
        if o.get("type") == "minecraft:simple_state_provider" and set(o) == {"type", "state"}:
            o = o["state"]
            changed = True
            return walk(o, key)[0], True
        if o.get("type") == "minecraft:random_offset":
            n = {"type": "minecraft:offset"}
            for k, v in o.items():
                if k == "xz_spread":
                    n["x"] = v
                elif k == "y_spread":
                    n["y"] = v
                elif k != "type":
                    n[k] = v
            n["z"] = o["xz_spread"]
            o = {k: n[k] for k in ["type", "x", "y", "z"] + [k for k in n if k not in ("type", "x", "y", "z")]}
            changed = True
        out = {}
        for k, v in o.items():
            nv, c = walk(v, k)
            changed |= c
            out[k] = nv
        return out, changed
    if isinstance(o, list):
        out = []
        for v in o:
            nv, c = walk(v, key)
            changed |= c
            out.append(nv)
        return out, changed
    return o, False


def migrate_feature(d):
    if "config" in d:
        cfg = d["config"]
        n = {"type": d["type"]}
        if d["type"] == "minecraft:tree":
            cfg = {k: v for k, v in cfg.items() if k not in ("dirt_provider", "force_dirt")}
            if "below_trunk_provider" not in cfg:
                n["below_trunk_provider"] = "minecraft:soil_beneath_tree"
        for k, v in cfg.items():
            n[k] = v
        for k, v in d.items():
            if k not in ("type", "config"):
                n[k] = v
        d = n
    return walk(d)[0]


NUM_ARRAY = re.compile(r"\[\s*(-?\d+(?:\.\d+)?(?:\s*,\s*-?\d+(?:\.\d+)?)*)\s*\]")


def dump(d, crlf, trail):
    s = json.dumps(d, indent=2, ensure_ascii=False)
    s = NUM_ARRAY.sub(lambda m: "[" + ", ".join(x.strip() for x in m.group(1).split(",")) + "]", s)
    if trail:
        s += "\n"
    if crlf:
        s = s.replace("\n", "\r\n")
    return s.encode("utf-8")


def read(p):
    raw = p.read_bytes().decode("utf-8")
    return json.loads(raw.replace("\r\n", "\n")), "\r\n" in raw, raw.endswith("\n")


for root in args:
    for dp, dirs, files in os.walk(root):
        dpp = Path(dp)
        parts = dpp.parts
        if "tags" in parts:
            continue
        if dpp.name == "configured_feature" and dpp.parent.name == "worldgen":
            dest_root = dpp.parent / "feature"
            for f in sorted(dpp.rglob("*.json")):
                d, crlf, trail = read(f)
                out = dest_root / f.relative_to(dpp)
                changes.append(f"move {f} -> {out}")
                if not DRY:
                    if out.exists():
                        raise SystemExit(f"refusing to overwrite {out}")
                    out.parent.mkdir(parents=True, exist_ok=True)
                    out.write_bytes(dump(migrate_feature(d), crlf, trail))
                    f.unlink()
            if not DRY:
                for sub in sorted((p for p in dpp.rglob("*") if p.is_dir()), key=lambda p: -len(p.parts)):
                    sub.rmdir()
                dpp.rmdir()
        elif dpp.name == "placed_feature" and dpp.parent.name == "worldgen":
            for f in sorted(dpp.rglob("*.json")):
                d, crlf, trail = read(f)
                nd, changed = walk(d)
                if changed:
                    changes.append(f"rewrite {f}")
                    if not DRY:
                        f.write_bytes(dump(nd, crlf, trail))

print("\n".join(changes))
print(len(changes), "changes", "(dry run)" if DRY else "")
