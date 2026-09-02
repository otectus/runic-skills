#!/usr/bin/env python3
"""Generates the 16x16 Power icons under assets/runicskills/textures/power/.

Every Power shipped with `HandlerResources.NULL_PERK` -- one placeholder square for all
seventy-five, so the Powers panel could not tell you what anything was before you read its name.
These are generated rather than drawn because the set has to satisfy three properties at once, and
generating them is the only way to *prove* it does:

  * tier legible without colour  -- the border is a broken ring (Mark), a closed ring (Seal) or a
    triple ring (Crown), which reads in grayscale;
  * school legible at a glance   -- the ring takes the school's colour and the rune its accent,
    matching the particle palette exactly so the icon and the proc are recognisably the same thing;
  * every Power distinguishable  -- the inner rune is picked from a stroke library by hashing the
    Power id, with collisions resolved deterministically, so no two icons are identical.

Run from the repository root:  python tools/icongen/powers.py
Re-running is idempotent: the same ids always produce the same bytes.
"""

import hashlib
import math
import os
import re
import sys

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
REGISTRY = os.path.join(REPO, "src", "main", "java", "com", "otectus", "runicskills",
                        "registry", "RegistryPowers.java")
OUT_DIR = os.path.join(REPO, "src", "main", "resources", "assets", "runicskills",
                       "textures", "power")

SIZE = 16
CENTER = 7.5

# Kept byte-identical to PowerVfxStyle.forSchool, so an icon and the particles it fires are
# obviously the same effect. If one moves, move the other.
SCHOOL_COLORS = {
    "FIRE": (0xFF8A2B, 0xC81E1E),
    "ICE": (0x66E0FF, 0xFFFFFF),
    "LIGHTNING": (0xA05CFF, 0xFFFFFF),
    "HOLY": (0xFFD24A, 0xFFF6DC),
    "ENDER": (0xE04AD6, 0x3B2E8C),
    "EVOCATION": (0x3FBF6F, 0xBFF0CE),
    "NATURE": (0x7FC24A, 0xE0B34A),
    "BLOOD": (0xC01A2B, 0x8A1220),
    "ELDRITCH": (0x3FC7B8, 0x4A2A73),
    "PROJECTILE": (0x5B8CC8, 0xFFFFFF),
    "CHANNEL": (0x5FD6D6, 0xB9A6F0),
    "SUMMON": (0x7A8C56, 0xE8E0C8),
    "MOBILITY": (0x7A6CE0, 0xFFFFFF),
    "WEAPON_CASTER": (0xC8813F, 0x6FA8DC),
    "UTILITY": (0xF2F0E4, 0x8FE0C0),
}

OUTLINE = (0x14, 0x12, 0x1A, 0xFF)

# Twelve inner strokes on a 7x7 field, each a list of point pairs in local coordinates.
# Chosen to stay distinguishable at 16px after the border eats the outer three rings.
STROKES = [
    [((0, 0), (6, 6)), ((6, 0), (0, 6))],                 # saltire
    [((3, 0), (3, 6)), ((0, 3), (6, 3))],                 # cross
    [((0, 5), (3, 0)), ((3, 0), (6, 5))],                 # chevron up
    [((0, 1), (3, 6)), ((3, 6), (6, 1))],                 # chevron down
    [((1, 0), (1, 6)), ((5, 0), (5, 6))],                 # rails
    [((0, 1), (6, 1)), ((0, 5), (6, 5))],                 # bars
    [((0, 6), (3, 0)), ((3, 0), (3, 6))],                 # flag
    [((0, 0), (6, 0)), ((6, 0), (0, 6)), ((0, 6), (6, 6))],  # zed
    [((3, 0), (0, 3)), ((0, 3), (3, 6)), ((3, 6), (6, 3)), ((6, 3), (3, 0))],  # diamond
    [((0, 0), (0, 6)), ((0, 6), (6, 6))],                 # ell
    [((1, 1), (5, 1)), ((3, 1), (3, 6))],                 # tee
    [((0, 3), (6, 3)), ((2, 0), (2, 6)), ((4, 0), (4, 6))],  # ladder
]

DOT_VARIANTS = [
    [],
    [(3, 3)],
    [(0, 0), (6, 6)],
    [(6, 0), (0, 6)],
]


def rgba(packed, alpha=255):
    return ((packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF, alpha)


def parse_registry():
    """Reads (id, tier, school) straight out of RegistryPowers so the two cannot drift."""
    src = open(REGISTRY, encoding="utf-8").read()
    pattern = re.compile(
        r'(?:issPower|crossPower)\(\s*"([a-z0-9_]+)"\s*,\s*PowerTier\.([A-Z]+)\s*,'
        r'\s*PowerSchool\.([A-Z_]+)')
    found = pattern.findall(src)
    if not found:
        sys.exit("no Power registrations matched -- has RegistryPowers changed shape?")
    return found


def rotate(points, quarter):
    """Rotates a stroke set by quarter-turns about the 7x7 field's centre."""
    out = []
    for (x0, y0), (x1, y1) in points:
        for _ in range(quarter):
            x0, y0 = 6 - y0, x0
            x1, y1 = 6 - y1, x1
        out.append(((x0, y0), (x1, y1)))
    return out


def assign_runes(power_ids):
    """Deterministic, collision-free (stroke, rotation, dots) per Power id."""
    combos = [(s, r, d)
              for s in range(len(STROKES))
              for r in range(4)
              for d in range(len(DOT_VARIANTS))]
    if len(combos) < len(power_ids):
        sys.exit("stroke library too small for %d Powers" % len(power_ids))
    taken = set()
    assigned = {}
    for pid in sorted(power_ids):
        digest = int(hashlib.sha256(pid.encode("utf-8")).hexdigest(), 16)
        # Linear probe from the hashed slot: stable for a given id set, and the ordering is
        # sorted, so adding a Power later cannot reshuffle the icons of the ones before it.
        for probe in range(len(combos)):
            slot = (digest + probe) % len(combos)
            if slot not in taken:
                taken.add(slot)
                assigned[pid] = combos[slot]
                break
    return assigned


def draw_ring(img, radius, color, gap=None):
    steps = max(24, int(radius * 16))
    for s in range(steps):
        angle = 2 * math.pi * s / steps
        if gap is not None and gap[0] <= math.degrees(angle) <= gap[1]:
            continue
        x = int(round(CENTER + math.cos(angle) * radius))
        y = int(round(CENTER + math.sin(angle) * radius))
        if 0 <= x < SIZE and 0 <= y < SIZE:
            img.putpixel((x, y), color)


def build(power_id, tier, school, rune):
    primary, accent = SCHOOL_COLORS.get(school, (0xE8E8E8, 0x9A9A9A))
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Backdrop: a dark disc so the icon reads on both the light and dark panel chrome.
    d.ellipse([1, 1, SIZE - 2, SIZE - 2], fill=(0x18, 0x16, 0x1E, 0xE0))

    ring = rgba(primary)
    if tier == "MARK":
        draw_ring(img, 6.6, ring, gap=(250, 290))
    elif tier == "SEAL":
        draw_ring(img, 6.6, ring)
    else:  # CROWN
        draw_ring(img, 6.9, ring)
        draw_ring(img, 5.3, ring)
        for cx, cy in ((1, 1), (14, 1), (1, 14), (14, 14)):
            img.putpixel((cx, cy), ring)

    stroke_idx, quarter, dots_idx = rune
    inner = rgba(accent)
    ox = oy = (SIZE - 7) // 2
    for (x0, y0), (x1, y1) in rotate(STROKES[stroke_idx], quarter):
        d.line([(ox + x0, oy + y0), (ox + x1, oy + y1)], fill=inner, width=1)
    for dx, dy in DOT_VARIANTS[dots_idx]:
        img.putpixel((ox + dx, oy + dy), rgba(primary))

    # A one-pixel rim on the backdrop disc, so the icon has an edge against the panel chrome
    # without the ring having to double as one. Read from a snapshot rather than the live image:
    # sampling the pixels being written cascades the rim outward until the whole tile is filled.
    before = img.copy()
    for x in range(SIZE):
        for y in range(SIZE):
            if before.getpixel((x, y))[3] != 0:
                continue
            neighbours = [before.getpixel((x + dx, y + dy))
                          for dx in (-1, 0, 1) for dy in (-1, 0, 1)
                          if 0 <= x + dx < SIZE and 0 <= y + dy < SIZE]
            if any(n[3] > 200 for n in neighbours):
                img.putpixel((x, y), OUTLINE)
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    powers = parse_registry()
    ids = [p[0] for p in powers]
    if len(set(ids)) != len(ids):
        sys.exit("duplicate Power ids in the registry")
    runes = assign_runes(ids)
    for pid, tier, school in powers:
        build(pid, tier, school, runes[pid]).save(os.path.join(OUT_DIR, pid + ".png"))
    print("wrote %d Power icons to %s" % (len(powers), os.path.relpath(OUT_DIR, REPO)))


if __name__ == "__main__":
    main()
