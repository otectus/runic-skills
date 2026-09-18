#!/usr/bin/env python3
"""Re-pin ``catalogue.json`` to the decoded pixels of the icons that actually ship.

As of the 2.2.2 icon refresh the shipped skill and Power sets are checked-in art:
``build.py`` and ``powers.py`` no longer reproduce them and running either would
overwrite the shipped PNGs. This script is the only supported way to update the
catalogue: it reads each entry's PNG, recomputes the decoded-RGBA SHA-256 that
``RunicIconIntegrityTest`` checks, and writes the hashes back, leaving every
other field, the key order and the entry order untouched.

Usage (from the repository root):

    python tools/icongen/repin_catalogue.py            # rewrite the hashes
    python tools/icongen/repin_catalogue.py --check    # verify only, exit 1 on drift
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

from PIL import Image

TOOL_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOL_DIR.parent.parent
CATALOGUE = TOOL_DIR / "catalogue.json"
ASSETS = REPO_ROOT / "src" / "main" / "resources" / "assets" / "runicskills"

# Byte-for-byte what RunicIconIntegrityTest builds (lines 60-81): 16*16*4 bytes,
# row-major, R,G,B,A per pixel. Image.convert("RGBA").tobytes() is that layout.
JSON_KWARGS = {"indent": 2, "ensure_ascii": False}


def decoded_sha256(path: Path) -> str:
    with Image.open(path) as image:
        rgba = image.convert("RGBA").tobytes()
    return hashlib.sha256(rgba).hexdigest()


def serialise(catalogue: list) -> str:
    return json.dumps(catalogue, **JSON_KWARGS) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true",
                        help="report entries whose pinned hash differs; write nothing")
    args = parser.parse_args(argv)

    original = CATALOGUE.read_text(encoding="utf-8")
    catalogue = json.loads(original)

    # A diff of hash lines only is only achievable if this script's serialisation
    # round-trips the untouched file exactly; refuse to rewrite if it does not.
    if serialise(catalogue) != original:
        print(f"refusing to rewrite: re-serialising {CATALOGUE} is not byte-identical "
              f"to the file on disk", file=sys.stderr)
        return 2

    missing: list[str] = []
    drifted: list[str] = []
    for entry in catalogue:
        icon = ASSETS / entry["path"]
        if not icon.is_file():
            missing.append(entry["path"])
            continue
        digest = decoded_sha256(icon)
        if digest != entry.get("pixels_sha256"):
            drifted.append(entry["path"])
            entry["pixels_sha256"] = digest

    if missing:
        for path in missing:
            print(f"missing icon: {path}", file=sys.stderr)
        return 2

    if args.check:
        if drifted:
            print(f"{len(drifted)} catalogue entr{'y' if len(drifted) == 1 else 'ies'} "
                  f"no longer match the shipped art:", file=sys.stderr)
            for path in drifted:
                print(f"  {path}", file=sys.stderr)
            print("run: python tools/icongen/repin_catalogue.py", file=sys.stderr)
            return 1
        print(f"catalogue pinned: {len(catalogue)} entries match the shipped art")
        return 0

    if drifted:
        CATALOGUE.write_text(serialise(catalogue), encoding="utf-8")
        print(f"re-pinned {len(drifted)} of {len(catalogue)} entries in {CATALOGUE}")
    else:
        print(f"nothing to do: all {len(catalogue)} entries already match the shipped art")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
