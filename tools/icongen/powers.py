#!/usr/bin/env python3
"""Render explicitly art-directed Power emblems using the shared carved-pixel style.

Registry owns ids, tiers and schools; specs_powers.tsv owns each mechanic's silhouette
and action badge. No hash-generated rune or collision substitution is permitted.
"""
from pathlib import Path
import re
import gen

REPO = Path(__file__).resolve().parents[2]
REGISTRY = REPO / 'src/main/java/com/otectus/runicskills/registry/RegistryPowers.java'
OUT_DIR = REPO / 'src/main/resources/assets/runicskills/textures/power'
SCHOOL_PALETTES = {
    'FIRE': 'fire', 'ICE': 'ice', 'LIGHTNING': 'amethyst', 'HOLY': 'holy',
    'ENDER': 'ender', 'EVOCATION': 'emerald', 'NATURE': 'nature', 'BLOOD': 'crimson',
    'ELDRITCH': 'eldritch', 'PROJECTILE': 'steel', 'CHANNEL': 'prismarine',
    'SUMMON': 'moss', 'MOBILITY': 'arcane', 'WEAPON_CASTER': 'copper',
    'UTILITY': 'bone', 'TINKERING': 'copper', 'ANGLING': 'water', 'WEAPON_MASTERY': 'steel', 'AQUAMANCY': 'water',
}


def parse_registry():
    found = re.findall(
        r'(?:issPower|crossPower)\(\s*"([a-z0-9_]+)"\s*,\s*PowerTier\.([A-Z]+)\s*,'
        r'\s*PowerSchool\.([A-Z_]+)', REGISTRY.read_text(encoding='utf-8'))
    if not found or len(found) != len({p[0] for p in found}):
        raise ValueError('Power registrations missing or duplicated')
    return found


def icons(glyphs, specs):
    registry = parse_registry()
    if set(specs) != {p[0] for p in registry}:
        raise ValueError('Power art specs differ from registry: ' + str(
            set(specs) ^ {p[0] for p in registry}))
    for pid, tier, school in registry:
        glyph, secondary, badge, badge_pal, concept = specs[pid]
        primary = SCHOOL_PALETTES[school]
        icon = gen.render_icon(glyphs[glyph], primary, secondary, badge, badge_pal)
        # Carved tier tally: visible without colour, separated from the mechanic's centre.
        for x in range(1, 8):
            icon.putpixel((x, 15), gen.OUTLINE)
        for i in range({'MARK': 1, 'SEAL': 2, 'CROWN': 3}[tier]):
            icon.putpixel((2 + i * 2, 15), gen.PALETTES[primary][2] + (255,))
        yield pid, school, icon, concept


def read_specs():
    specs = {}
    for line in (Path(__file__).parent / 'specs_powers.tsv').read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'):
            continue
        parts = line.split('\t')
        if len(parts) != 6 or parts[0] in specs or not parts[-1].strip():
            raise ValueError('Malformed, duplicate or unexplained Power art spec: ' + line)
        specs[parts[0]] = parts[1:]
    return specs


def main():
    glyphs, errors = gen.load_glyphs()
    if errors:
        raise ValueError(errors)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    for pid, school, icon, concept in icons(glyphs, read_specs()):
        icon.save(OUT_DIR / (pid + '.png'))
        count += 1
    print(f'Wrote {count} explicitly designed Power icons')


if __name__ == '__main__':
    main()
