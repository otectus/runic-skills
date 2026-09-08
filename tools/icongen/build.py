#!/usr/bin/env python3
"""Generate or verify the entire shipped skill/perk/passive/power icon catalogue.

Usage: python tools/icongen/build.py [--check] [--sheets]
Pillow is only needed by artists. Gradle's JUnit asset checks need no Python runtime.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

from PIL import Image, ImageDraw
import gen
import powers

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
ASSETS = REPO / 'src/main/resources/assets/runicskills'


def specs(name, fields):
    result = {}
    for line in (HERE / name).read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'):
            continue
        parts = line.split('\t')
        if len(parts) != fields or parts[0] in result or not parts[-1].strip():
            raise ValueError(f'{name}: malformed, duplicate or unexplained spec: {line}')
        result[parts[0]] = parts[1:]
    return result


def registration_ids(file, pattern):
    source = (REPO / 'src/main/java/com/otectus/runicskills/registry' / file).read_text(encoding='utf-8')
    return set(re.findall(pattern, source))


def matches(path, icon):
    if not path.is_file():
        return False
    with Image.open(path) as shipped:
        return shipped.mode == 'RGBA' and shipped.size == icon.size and shipped.tobytes() == icon.tobytes()


def catalogue():
    glyphs, errors = gen.load_glyphs()
    if errors:
        raise ValueError(errors)
    for kind, filename, manifest_name, registry, pattern in (
        ('perk', 'specs.tsv', 'perks_manifest.json', 'RegistryPerks.java', r'registerPerk\("(\w+)"'),
        ('passive', 'specs_passives.tsv', 'passives_manifest.json', 'RegistryPassives.java', r'registerPassive\("(\w+)"'),
    ):
        authored = specs(filename, 7)
        manifest = {p['id']: p for p in json.loads((HERE / manifest_name).read_text(encoding='utf-8'))}
        registered = registration_ids(registry, pattern)
        # Historical art sources remain in the authoring library, but were never shipped on this
        # branch. This explicit allowlist prevents new missing registrations hiding among them.
        legacy = set(json.loads((HERE / 'legacy_perks.json').read_text())) if kind == 'perk' else set()
        expected = registered | legacy
        if set(authored) != expected or set(manifest) != expected:
            raise ValueError(f'{kind} art/manifest differs from registry: '
                             f'{set(authored) ^ expected}, {set(manifest) ^ expected}')
        for pid, (glyph, primary, secondary, badge, badge_pal, concept) in authored.items():
            if pid not in registered:
                continue
            meta = manifest[pid]
            filename = meta.get('file', pid + '.png')
            icon = gen.render_icon(glyphs[glyph], primary, secondary or None, badge or None, badge_pal or None)
            yield f'textures/skill/{meta["skill"]}/{filename}', kind, meta['skill'], pid, icon, concept

    skill_specs = specs('specs_skills.tsv', 5)
    if set(skill_specs) != registration_ids('RegistrySkills.java', r'SKILLS.register\("(\w+)"'):
        raise ValueError('Skill art specs differ from registry')
    for sid, (glyph, primary, secondary, concept) in skill_specs.items():
        for tier in range(4):
            icon = gen.render_icon(glyphs[glyph], primary, secondary)
            # Four two-pixel carved steps communicate progress without relying on colour.
            # The remaining shape and resource paths stay consistent across all ranks.
            for x in range(1, 15):
                icon.putpixel((x, 15), gen.OUTLINE)
            for rank in range(4):
                color = gen.PALETTES[primary][2] if rank <= tier else (64, 56, 77)
                for dx in (0, 1):
                    icon.putpixel((2 + rank * 3 + dx, 15), color + (255,))
            yield f'textures/skill/{sid}/locked_{tier * 8}.png', 'skill', sid, f'{sid} / {tier + 1}', icon, concept

    for pid, school, icon, concept in powers.icons(glyphs, powers.read_specs()):
        yield f'textures/power/{pid}.png', 'power', school.lower(), pid, icon, concept

    icon = gen.render_icon(glyphs['sigil'], 'prismarine', 'amethyst')
    yield 'textures/gui/powers.png', 'control', 'gui', 'powers', icon, 'Open the Powers catalogue.'
    slow = gen.render_icon(glyphs['fish'], 'water', 'steel', 'clockb', 'amethyst')
    yield 'textures/mob_effect/integration_slow.png', 'effect', 'combat', 'integration_slow', slow, 'A bounded ten percent movement slow.'


def sheet(name, items):
    columns, cw, ch = 8, 166, 96
    canvas = Image.new('RGBA', (columns * cw, ((len(items) + columns - 1) // columns) * ch + 32), '#24212e')
    draw = ImageDraw.Draw(canvas)
    draw.text((12, 9), f'RUNIC SKILLS 2.1 / {name.upper()} / native 16px + 3x inspection', fill='#f5e4bd')
    for i, (label, icon) in enumerate(items):
        x, y = (i % columns) * cw + 8, (i // columns) * ch + 32
        # Real native size on both light and dark UI surfaces, plus integer nearest-neighbour.
        draw.rectangle((x, y + 8, x + 21, y + 29), fill='#b5b1ba')
        canvas.alpha_composite(icon, (x + 3, y + 11))
        canvas.alpha_composite(icon, (x + 31, y + 11))
        canvas.alpha_composite(icon.resize((48, 48), Image.Resampling.NEAREST), (x + 59, y))
        for line, text in enumerate([label[:26], label[26:52]]):
            if text:
                draw.text((x, y + 53 + line * 11), text, fill='#e7deef')
    out = REPO / 'build/reports/icons'
    out.mkdir(parents=True, exist_ok=True)
    canvas.save(out / f'{name}.png')
    # Keep the existing reviewed-art paths current for contributors browsing the repository.
    review_name = name.removeprefix('perk_') if name.startswith('perk_') else {
        'passive': 'passives', 'power': 'powers', 'skill': 'skills', 'control': 'control'}[name]
    canvas.save(HERE / f'sheet_{review_name}.png')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='check decoded pixels, never write assets')
    parser.add_argument('--sheets', action='store_true', help='write native-size review sheets to build/reports/icons')
    args = parser.parse_args()
    rows = list(catalogue())
    errors, seen, groups, manifest = [], {}, {}, []
    for path, kind, category, pid, icon, concept in rows:
        if icon.size != (16, 16) or icon.mode != 'RGBA':
            errors.append(f'{path}: not native 16x16 RGBA')
        if set(icon.getchannel('A').tobytes()) - {0, 255}:
            errors.append(f'{path}: non-pixel alpha or antialiasing')
        digest = hashlib.sha256(icon.tobytes()).hexdigest()
        if digest in seen:
            errors.append(f'duplicate pixels: {path} and {seen[digest]}')
        seen[digest] = path
        dest = ASSETS / path
        if args.check:
            if not matches(dest, icon):
                errors.append(f'{path}: shipped art differs from its authored source')
        groups.setdefault(f'{kind}_{category}' if kind == 'perk' else kind, []).append((pid, icon))
        manifest.append({'path': path, 'kind': kind, 'id': pid, 'concept': concept, 'pixels_sha256': digest})
    if errors:
        raise SystemExit('\n'.join(errors))
    manifest_path = HERE / 'catalogue.json'
    if args.check:
        if json.loads(manifest_path.read_text(encoding='utf-8')) != manifest:
            raise SystemExit('catalogue.json differs from authored sources; regenerate')
    else:
        for path, kind, category, pid, icon, concept in rows:
            dest = ASSETS / path
            dest.parent.mkdir(parents=True, exist_ok=True)
            if not matches(dest, icon):
                icon.save(dest)
        manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    if args.sheets:
        for name, items in groups.items():
            sheet(name, sorted(items))
    print(f'{"Verified" if args.check else "Generated"} {len(rows)} unique native icons; no missing art, duplicates or partial alpha.')


if __name__ == '__main__':
    main()
