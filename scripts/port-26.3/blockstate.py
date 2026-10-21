"""Convert 26.1 block/fluid state JSON objects to the 26.3 form, in place.

usage: python -I blockstate.py [--dry] <file-or-dir>...

{"Name": n}                      -> "n"
{"Name": n, "Properties": {}}    -> "n"
{"Name": n, "Properties": p}     -> {"id": n, "properties": p}
Only dicts whose keys are exactly {"Name"} or {"Name", "Properties"} are touched. Files keep their line endings and
are only rewritten when something changed (re-serialised with 2-space indent).
"""
import json
import os
import sys


def convert(o):
    changed = 0
    if isinstance(o, list):
        out = []
        for v in o:
            nv, c = convert(v)
            out.append(nv)
            changed += c
        return out, changed
    if isinstance(o, dict):
        keys = set(o)
        if "Name" in keys and keys <= {"Name", "Properties"} and isinstance(o["Name"], str):
            props = o.get("Properties") or {}
            if props:
                return {"id": o["Name"], "properties": props}, 1
            return o["Name"], 1
        out = {}
        for k, v in o.items():
            nv, c = convert(v)
            out[k] = nv
            changed += c
        return out, changed
    return o, 0


def files(paths):
    for p in paths:
        if os.path.isdir(p):
            for dp, _, fs in os.walk(p):
                for f in fs:
                    if f.endswith(".json") or f.endswith(".mcmeta"):
                        yield os.path.join(dp, f)
        else:
            yield p


def main():
    dry = "--dry" in sys.argv
    total = 0
    for p in files([a for a in sys.argv[1:] if a != "--dry"]):
        raw = open(p, encoding="utf-8", newline="").read()
        try:
            data = json.loads(raw)
        except Exception as e:
            print("skip (bad json)", p, e)
            continue
        new, c = convert(data)
        if c:
            total += c
            print(c, p)
            if not dry:
                s = json.dumps(new, indent=2, ensure_ascii=False) + "\n"
                if "\r\n" in raw:
                    s = s.replace("\n", "\r\n")
                open(p, "w", encoding="utf-8", newline="").write(s)
    print("converted", total)


if __name__ == "__main__":
    main()
