import json, os, sys, collections
def paths(o, p, out):
    if isinstance(o, dict):
        for k, v in o.items():
            q = p + "." + (k if ":" not in k else "<ns:id>" if not k.startswith("minecraft:") else k)
            out.add(q); paths(v, q, out)
    elif isinstance(o, list):
        for v in o: paths(v, p + "[]", out)
def collect(root, cat):
    s = set(); d = os.path.join(root, cat)
    for dp, _, fs in os.walk(d):
        for f in fs:
            if f.endswith(".json"):
                try: paths(json.load(open(os.path.join(dp, f), encoding="utf-8")), "", s)
                except Exception as e: pass
    return s
old, new = sys.argv[1], sys.argv[2]
for cat in sys.argv[3:]:
    a, b = collect(old, cat), collect(new, cat)
    gone = sorted(a - b)
    print(f"=== {cat}: {len(gone)} paths gone, {len(b-a)} new")
    for g in gone[:40]: print("  -", g)
