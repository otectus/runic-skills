#!/usr/bin/env python3
"""Prove, without launching Minecraft, that this mod's Tinkers' mixins will find their targets
in a real production pack.

Dev and gametests run Mojang-mapped; production runs SRG-mapped. That gap is the entire content of
RS10-041: `@Mixin(targets = ..., remap = false)` made every member reference literal, so hooks onto
vanilla-declared methods resolved in dev and threw InvalidInjectionException at class load in the
pack, which for Mixin is fatal for the whole config. `checkMixinRemapping` proves the naming is
internally consistent and `verifyShippedRefmap` proves the refmap ships; neither has ever seen a
production-mapped jar. This has.

For every mixin under mixin/tconstruct/ it works out the name Mixin will actually look for at
runtime -- the refmap's SRG name where the injector is remapped, the literal selector where it is
not -- and then reads the real, obfuscated mod jars out of the pack and checks that the member is
there under that exact name and descriptor.

    python tools/verify_against_pack.py
    python tools/verify_against_pack.py --jar build/libs/runicskills-2.0.7.jar --mods "<pack>/mods"

Exit code 0 if every reference resolves, 1 otherwise.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import struct
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MIXIN_ROOT = os.path.join(REPO, "src", "main", "java", "com", "otectus", "runicskills", "mixin")
# The pack to check against is machine-specific, so it is not hard-coded here. Set
# RUNIC_PACK_MODS to a pack's mods/ directory, or pass --mods; without either, the script
# says what it needs rather than failing on somebody else's path.
DEFAULT_MODS = os.environ.get("RUNIC_PACK_MODS")


# --------------------------------------------------------------------------------------------
# class files
# --------------------------------------------------------------------------------------------

def parse_class(data: bytes):
    """Return (name, super, [interfaces], {(name, desc)}) for a .class file.

    A production mod jar is obfuscated, so nothing here may assume a readable name: the point is
    to read the names that are genuinely in the bytes.
    """
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    pos = 10
    count = struct.unpack_from(">H", data, 8)[0]
    pool = {}
    i = 1
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, pos)[0]
            pool[i] = data[pos + 2:pos + 2 + length].decode("utf-8", "replace")
            pos += 2 + length
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = ("ref", struct.unpack_from(">H", data, pos)[0])
            pos += 2
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            i += 1
        elif tag == 15:
            pos += 3
        else:
            raise ValueError("unknown constant pool tag %d" % tag)
        i += 1

    def cls_name(index):
        if index == 0:
            return None
        entry = pool.get(index)
        return pool.get(entry[1]) if isinstance(entry, tuple) else None

    pos += 2  # access flags
    this_name = cls_name(struct.unpack_from(">H", data, pos)[0]); pos += 2
    super_name = cls_name(struct.unpack_from(">H", data, pos)[0]); pos += 2
    n_ifaces = struct.unpack_from(">H", data, pos)[0]; pos += 2
    ifaces = []
    for _ in range(n_ifaces):
        ifaces.append(cls_name(struct.unpack_from(">H", data, pos)[0]))
        pos += 2

    def skip_attributes(p):
        n = struct.unpack_from(">H", data, p)[0]
        p += 2
        for _ in range(n):
            length = struct.unpack_from(">I", data, p + 2)[0]
            p += 6 + length
        return p

    members = set()
    for _ in range(2):  # fields, then methods
        n = struct.unpack_from(">H", data, pos)[0]
        pos += 2
        for _ in range(n):
            name = pool.get(struct.unpack_from(">H", data, pos + 2)[0])
            desc = pool.get(struct.unpack_from(">H", data, pos + 4)[0])
            members.add((name, desc))
            pos = skip_attributes(pos + 6)
    return this_name, super_name, ifaces, members


class JarPool:
    """Every jar in the pack's mods/ directory, searched by internal class name."""

    def __init__(self, mods_dir, extra=()):
        self.zips = []
        for path in list(extra) + sorted(
                os.path.join(mods_dir, n) for n in (os.listdir(mods_dir) if os.path.isdir(mods_dir) else [])):
            if path.lower().endswith(".jar"):
                try:
                    self.zips.append((os.path.basename(path), zipfile.ZipFile(path)))
                except Exception:
                    pass
        self.cache = {}

    def load(self, internal):
        if internal in self.cache:
            return self.cache[internal]
        entry = internal + ".class"
        result = None
        for name, zf in self.zips:
            try:
                data = zf.read(entry)
            except KeyError:
                continue
            try:
                parsed = parse_class(data)
            except Exception as exc:  # a jar we cannot read is a finding, not a crash
                parsed = None
                print("  ! %s in %s: %s" % (internal, name, exc))
            result = (name, parsed)
            break
        self.cache[internal] = result
        return result

    def declares(self, internal, member, desc, inherited=False):
        """(jar, declaring class) if the member is there, else None."""
        seen, queue = set(), [internal]
        while queue:
            cur = queue.pop(0)
            if cur is None or cur in seen:
                continue
            seen.add(cur)
            found = self.load(cur)
            if found is None:
                continue
            jar, parsed = found
            if parsed is None:
                continue
            _, sup, ifaces, members = parsed
            if (member, desc) in members or (desc is None and any(m == member for m, _ in members)):
                return jar, cur
            if not inherited:
                return None
            queue.append(sup)
            queue.extend(ifaces)
        return None


# --------------------------------------------------------------------------------------------
# mixin sources
# --------------------------------------------------------------------------------------------

INJECTORS = ("Inject|Redirect|ModifyArgs|ModifyArg|ModifyVariable|ModifyConstant|WrapMethod|"
             "WrapOperation|ModifyExpressionValue|ModifyReturnValue|WrapWithCondition")


def strip_comments(src):
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if c in '"\'':
            q, out_start = c, i
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                    continue
                i += 1
                if src[i - 1] == q:
                    break
            out.append(src[out_start:i])
        elif src.startswith("//", i):
            end = src.find("\n", i)
            end = n if end < 0 else end
            out.append(" " * (end - i))
            i = end
        elif src.startswith("/*", i):
            end = src.find("*/", i + 2)
            end = n - 2 if end < 0 else end
            out.append("".join(ch if ch == "\n" else " " for ch in src[i:end + 2]))
            i = end + 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def match_paren(src, open_idx):
    depth, i, n = 0, open_idx, len(src)
    while i < n:
        c = src[i]
        if c in '"\'':
            q = c
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                    continue
                i += 1
                if src[i - 1] == q:
                    break
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def literals(expr):
    return "".join(m.group(1) for m in re.finditer(r'"((?:[^"\\]|\\.)*)"', expr))


def attr(body, name):
    m = re.search(r"(?:^|[,(\s])" + name + r"\s*=\s*", body, re.S)
    if not m:
        return None
    rest = body[m.end():]
    if rest.lstrip().startswith("{"):
        return rest[rest.index("{"):rest.index("}") + 1]
    return rest.split(",")[0]


def selectors(expr, consts):
    if expr is None:
        return []
    body = expr.strip()
    if body.startswith("{"):
        body = body[1:-1]
    out = []
    for part in re.split(r",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", body):
        lit = literals(part)
        out.append(lit if lit else consts.get(part.strip()))
    return [s for s in out if s]


def parse_mixin(path):
    src = strip_comments(open(path, encoding="utf-8").read())
    consts = {m.group(1): literals(m.group(2))
              for m in re.finditer(r'String\s+(\w+)\s*=\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+);', src, re.S)}
    idx = src.find("@Mixin(")
    if idx < 0:
        return None
    open_idx = src.index("(", idx)
    head = src[open_idx + 1:match_paren(src, open_idx)]
    class_remap = (attr(head, "remap") or "true").strip() == "true"
    targets = [t.replace(".", "/") for t in selectors(attr(head, "targets"), consts)]

    refs = []
    for m in re.finditer(r"@(" + INJECTORS + r")\b", src):
        open_idx = src.find("(", m.end())
        close = match_paren(src, open_idx)
        body = src[open_idx + 1:close]
        line = src.count("\n", 0, m.start()) + 1
        ats, stripped = [], body
        for am in re.finditer(r"@At\s*\(", body):
            aopen = body.index("(", am.start())
            aclose = match_paren(body, aopen)
            ats.append(body[aopen + 1:aclose])
            stripped = stripped[:am.start()] + " " * (aclose + 1 - am.start()) + stripped[aclose + 1:]
        inj_remap = (attr(stripped, "remap") or str(class_remap).lower()).strip() == "true"
        for sel in selectors(attr(stripped, "method"), consts):
            refs.append(("method", line, sel, inj_remap))
        for at_body in ats:
            at_remap = (attr(at_body, "remap") or str(inj_remap).lower()).strip() == "true"
            for sel in selectors(attr(at_body, "target"), consts):
                refs.append(("@At", line, sel, at_remap))
    return targets, refs


# --------------------------------------------------------------------------------------------

def split_ref(sel):
    """`Lowner;name(desc)ret` / `Lowner;name:Ltype;` / `name(desc)ret` / `name` -> (owner, name, desc)."""
    owner, rest = None, sel
    if sel.startswith("L") and ";" in sel:
        owner = sel[1:sel.index(";")]
        rest = sel[sel.index(";") + 1:]
    if "(" in rest:
        return owner, rest[:rest.index("(")], rest[rest.index("("):]
    if ":" in rest:
        return owner, rest[:rest.index(":")], rest[rest.index(":") + 1:]
    return owner, rest, None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--jar", help="the shipped runicskills jar (default: newest in build/libs)")
    ap.add_argument("--mods", default=DEFAULT_MODS,
                    help="the pack's mods/ directory (default: $RUNIC_PACK_MODS)")
    args = ap.parse_args()

    if not args.mods:
        ap.error("no pack given: pass --mods <pack>/mods or set RUNIC_PACK_MODS")

    jar_path = args.jar
    if not jar_path:
        libs = os.path.join(REPO, "build", "libs")
        candidates = [os.path.join(libs, n) for n in os.listdir(libs)
                      # The shipped artifact is the jarJar bundle, which carries the bare name.
                      # "-slim" is the same mod without its bundled MixinExtras and is not what
                      # players run; "-all" is the pre-2.0.7 name for the bundle, excluded so an
                      # older build lying in build/libs cannot be picked over the current one.
                      if n.startswith("runicskills-") and n.endswith(".jar")
                      and not n.endswith("-all.jar") and not n.endswith("-slim.jar")]
        jar_path = max(candidates, key=os.path.getmtime)

    with zipfile.ZipFile(jar_path) as zf:
        refmap = json.loads(zf.read("runicskills.refmap.json")).get("mappings", {})

    pool = JarPool(args.mods)
    print("jar : %s" % jar_path)
    print("mods: %s (%d jars)" % (args.mods, len(pool.zips)))
    print()

    failures, total = [], 0
    tconstruct_root = os.path.join(MIXIN_ROOT, "tconstruct")
    for dirpath, _, files in os.walk(tconstruct_root):
        for name in sorted(files):
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
            parsed = parse_mixin(path)
            if parsed is None:
                continue
            targets, refs = parsed
            rel = os.path.relpath(path, MIXIN_ROOT).replace(os.sep, "/")
            mixin_class = "com/otectus/runicskills/mixin/" + rel[:-len(".java")]
            entries = refmap.get(mixin_class, {})

            print("%s -> %s" % (mixin_class.rsplit("/", 1)[-1], ", ".join(targets)))
            target_missing = any(pool.load(t) is None for t in targets)
            if target_missing:
                print("  SKIP  target class absent from this pack (optional add-on)")
                print()
                continue

            for kind, line, sel, remap in refs:
                total += 1
                mapped = entries.get(sel)
                runtime = mapped if (remap and mapped) else sel
                owner, member, desc = split_ref(runtime)
                owners = [owner] if owner else targets
                hit = None
                for o in owners:
                    hit = pool.declares(o, member, desc, inherited=(kind == "@At"))
                    if hit:
                        break
                source = "refmap SRG" if (remap and mapped) else "literal"
                if hit:
                    where = "" if hit[1] == owners[0] else " (via %s)" % hit[1]
                    print("  OK    %-6s %-20s -> %-12s in %s%s" % (kind, sel.split("(")[0], member, hit[0], where))
                elif pool.load(owners[0]) is None:
                    print("  SKIP  %-6s %-20s -> %s: %s not in this pack" % (kind, sel.split("(")[0], member, owners[0]))
                else:
                    print("  FAIL  %-6s %-20s -> %s (%s) NOT FOUND in %s" % (kind, sel.split("(")[0], member, source, owners[0]))
                    failures.append("%s:%d %s '%s' -> %s%s not declared by %s"
                                    % (rel, line, kind, sel, member, desc or "", owners[0]))
            print()

    print("%d references checked, %d failures" % (total, len(failures)))
    for f in failures:
        print("  FAIL %s" % f)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
