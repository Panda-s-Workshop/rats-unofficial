"""Statically check mixin @At / @Inject target strings against 26.3 jars.

usage: python -I mixincheck.py <javap.exe> <src-root> <jar> [<jar> ...]

Checks every string of the form "Lowner/Class;member(desc)ret" or "Lowner/Class;field:Ldesc;" found in mixin sources:
the owner class must exist in one of the jars, and the member must exist (searching the owner's superclasses too).
Also checks `method = "name(desc)ret"` selectors against the @Mixin target class when it is a single class literal.
Prints only problems.
"""
import os
import re
import subprocess
import sys
import zipfile

javap, src_root, jars = sys.argv[1], sys.argv[2], sys.argv[3:]

classes = {}
for j in jars:
    with zipfile.ZipFile(j) as z:
        for n in z.namelist():
            if n.endswith(".class"):
                classes.setdefault(n[:-6], j)

cache = {}


def members(cls):
    """Return (super, interfaces, set of 'name(desc)' / 'name:desc') for an internal class name."""
    if cls in cache:
        return cache[cls]
    jar = classes.get(cls)
    if jar is None:
        cache[cls] = None
        return None
    out = subprocess.run([javap, "-p", "-s", "-cp", jar, cls.replace("/", ".")], capture_output=True, text=True)
    sup, ifaces, mem = None, [], set()
    lines = out.stdout.splitlines()
    for i, line in enumerate(lines):
        line = line.strip()
        m = re.search(r"\b(?:class|interface|enum|record)\s+\S+(?:\s+extends\s+([\w.$<>, ?]+?))?(?:\s+implements\s+([\w.$<>, ?]+))?\s*\{", line)
        if m and sup is None and i < 3:
            ext = m.group(1)
            if ext:
                parts = [p.strip().split("<")[0] for p in ext.split(",")]
                if " interface " in f" {line} ":
                    ifaces += parts
                else:
                    sup = parts[0]
            if m.group(2):
                ifaces += [p.strip().split("<")[0] for p in m.group(2).split(",")]
        if line.startswith("descriptor:"):
            desc = line.split(":", 1)[1].strip()
            prev = lines[i - 1].strip()
            nm = re.search(r"([\w$<>]+)\s*\(", prev)
            if nm and "(" in desc:
                name = nm.group(1)
                if name == cls.split("/")[-1].split("$")[-1] or prev.split("(")[0].strip().endswith(cls.replace("/", ".")):
                    name = "<init>"
                mem.add(name + desc)
            else:
                fm = re.search(r"([\w$]+);\s*$", prev)
                if fm:
                    mem.add(fm.group(1) + ":" + desc)
        if line.startswith("static {}"):
            mem.add("<clinit>()V")
    res = (sup.replace(".", "/") if sup else None, [x.replace(".", "/") for x in ifaces], mem)
    cache[cls] = res
    return res


def has_member(cls, member, depth=0):
    if depth > 12 or cls is None:
        return False
    info = members(cls)
    if info is None:
        return False
    sup, ifaces, mem = info
    if member in mem:
        return True
    # allow name-only match for fields given without desc
    for parent in [sup] + ifaces:
        if parent and has_member(parent, member, depth + 1):
            return True
    return False


TARGET = re.compile(r'"L([\w/$]+);([\w$<>]+)(\([^"]*\)[^"]*|:[^"]+)"')
MIXIN = re.compile(r'@Mixin\(\s*(?:value\s*=\s*)?\{?\s*([\w.]+)\.class')
METHOD = re.compile(r'method\s*=\s*\{?\s*"([\w$<>]+)(\([^"]*\)[^"]*)"')


def has_name(cls, name, depth=0):
    if depth > 12 or cls is None:
        return False
    info = members(cls)
    if info is None:
        return False
    sup, ifaces, mem = info
    if any(m.split("(")[0].split(":")[0] == name for m in mem):
        return True
    return any(has_name(parent, name, depth + 1) for parent in [sup] + ifaces if parent)


SHADOW = re.compile(r'@Shadow(?:\([^)]*\))?\s+(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|protected|private|static|final|abstract|native)\s+)*[\w.<>\[\], ?]+?\s+(\w+)\s*[(;=]')
ACCESSOR = re.compile(r'@(?:Accessor|Invoker)\(\s*(?:value\s*=\s*)?"([\w$<>]+)"')
problems = 0
for dp, _, fs in os.walk(src_root):
    for f in fs:
        if not f.endswith(".java"):
            continue
        p = os.path.join(dp, f)
        src = open(p, encoding="utf-8").read()
        if "@Mixin" not in src:
            continue
        rel = p.replace("\\", "/").split("/the_bumblezone/")[-1]
        imports = dict(re.findall(r"import\s+([\w.]+\.(\w+));", src) and
                       [(m[1], m[0]) for m in re.findall(r"import\s+([\w.]+\.(\w+));", src)])
        for m in TARGET.finditer(src):
            owner, name, desc = m.group(1), m.group(2), m.group(3)
            if owner not in classes:
                print(f"{rel}: missing class {owner} (target {name}{desc})")
                problems += 1
                continue
            member = name + desc if desc.startswith("(") else name + ":" + desc[1:]
            if not has_member(owner, member):
                print(f"{rel}: missing member {owner}.{member}")
                problems += 1
        mm = MIXIN.search(src)
        if mm:
            simple = mm.group(1).split(".")[0]
            fq = imports.get(simple, mm.group(1))
            owner = fq.replace(".", "/")
            if "." in mm.group(1) and simple in imports:  # nested class like Foo.Bar
                owner = imports[simple].replace(".", "/") + "$" + "$".join(mm.group(1).split(".")[1:])
            if owner in classes:
                for sm in SHADOW.finditer(src):
                    name = sm.group(1)
                    direct = members(owner)[2]
                    if not any(m.split("(")[0].split(":")[0] == name for m in direct):
                        print(f"{rel}: @Shadow {name} not declared in {owner}")
                        problems += 1
                for am in ACCESSOR.finditer(src):
                    name = am.group(1)
                    if not has_name(owner, name):
                        print(f"{rel}: @Accessor/@Invoker {name} not in {owner} hierarchy")
                        problems += 1
                for meth in METHOD.finditer(src):
                    member = meth.group(1) + meth.group(2)
                    if not has_member(owner, member):
                        print(f"{rel}: @Mixin({owner}) has no method {member}")
                        problems += 1
            else:
                print(f"{rel}: @Mixin target class not found: {owner}")
                problems += 1
print("problems:", problems)
