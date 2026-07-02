#!/usr/bin/env python3
"""Runic Skills perk icon generator.

16x16 RGBA pixel-art icons composed from a hand-authored glyph library,
themed 3-tone palettes, and small badge overlays.

Char scheme in glyph grids:
  '.' transparent   '#' outline   '1','2','3' primary dark/mid/light
  'a','b','c' secondary dark/mid/light   'w' white highlight
"""
import os, sys, json
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUTLINE = (24, 18, 28, 255)
WHITE = (255, 255, 255, 255)

# name -> (dark, mid, light)
PALETTES = {
    'steel':    ((73, 79, 92), (140, 148, 163), (205, 212, 224)),
    'iron':     ((96, 96, 104), (160, 160, 168), (222, 222, 228)),
    'gold':     ((146, 98, 17), (224, 165, 37), (252, 222, 96)),
    'wood':     ((74, 50, 25), (125, 86, 44), (176, 128, 75)),
    'darkwood': ((48, 32, 18), (84, 57, 30), (125, 86, 44)),
    'stone':    ((58, 58, 62), (108, 108, 115), (155, 155, 163)),
    'crimson':  ((96, 14, 20), (183, 36, 46), (235, 92, 92)),
    'blood':    ((70, 6, 14), (140, 16, 30), (200, 44, 58)),
    'fire':     ((150, 46, 10), (232, 112, 22), (255, 200, 64)),
    'ember':    ((110, 24, 8), (200, 70, 16), (255, 150, 40)),
    'ice':      ((26, 88, 132), (74, 172, 222), (176, 232, 252)),
    'frost':    ((60, 120, 160), (130, 200, 235), (215, 245, 255)),
    'storm':    ((160, 128, 12), (240, 210, 52), (255, 246, 150)),
    'holy':     ((172, 132, 42), (240, 212, 112), (255, 250, 214)),
    'nature':   ((32, 92, 32), (72, 162, 62), (142, 222, 112)),
    'moss':     ((26, 66, 30), (52, 112, 50), (100, 170, 88)),
    'ender':    ((62, 20, 92), (132, 62, 192), (202, 142, 242)),
    'eldritch': ((18, 62, 72), (42, 122, 132), (104, 202, 202)),
    'arcane':   ((82, 32, 122), (152, 82, 212), (212, 162, 250)),
    'mana':     ((26, 52, 132), (62, 112, 222), (142, 182, 252)),
    'water':    ((20, 62, 142), (52, 122, 222), (122, 192, 252)),
    'emerald':  ((16, 92, 52), (42, 182, 102), (122, 242, 172)),
    'lapis':    ((22, 42, 112), (44, 84, 192), (104, 144, 242)),
    'bone':     ((140, 140, 128), (200, 200, 190), (245, 245, 238)),
    'copper':   ((140, 70, 40), (200, 112, 72), (242, 162, 122)),
    'redstone': ((112, 12, 12), (202, 32, 22), (255, 92, 72)),
    'amethyst': ((104, 46, 140), (170, 100, 214), (224, 170, 252)),
    'pink':     ((150, 50, 90), (220, 100, 150), (250, 170, 205)),
    'night':    ((22, 27, 62), (48, 58, 115), (95, 110, 185)),
    'shadow':   ((32, 32, 40), (62, 62, 75), (100, 100, 115)),
    'leather':  ((86, 55, 30), (135, 90, 52), (185, 135, 88)),
    'slate':    ((44, 48, 58), (78, 85, 100), (120, 130, 148)),
    'obsidian': ((26, 18, 40), (54, 40, 78), (92, 72, 125)),
    'prismarine':((22, 92, 88), (52, 158, 148), (118, 218, 205)),
    'diamond':  ((28, 120, 130), (70, 200, 210), (160, 248, 250)),
    'void':     ((20, 10, 34), (48, 26, 76), (96, 60, 136)),
    'sand':     ((150, 128, 72), (208, 184, 118), (240, 224, 168)),
    'orange':   ((160, 82, 14), (226, 132, 34), (252, 186, 92)),
}

def load_glyphs():
    glyphs = {}
    for fn in ('glyphs1.txt', 'glyphs2.txt', 'glyphs3.txt'):
        cur = None
        rows = []
        for line in open(os.path.join(HERE, fn)):
            line = line.rstrip('\n')
            if not line.strip():
                continue
            if line.startswith(':'):
                if cur:
                    glyphs[cur] = rows
                cur = line[1:].strip()
                rows = []
            else:
                rows.append(line)
        if cur:
            glyphs[cur] = rows
    # normalize: pad rows to 16 chars, pad/trim to 16 rows
    errs = []
    valid = set('.#123abcw')
    for name in list(glyphs):
        rows = [r[:16].ljust(16, '.') for r in glyphs[name][:16]]
        while len(rows) < 16:
            rows.append('.' * 16)
        for i, r in enumerate(rows):
            bad = set(r) - valid
            if bad:
                errs.append(f'{name}: row {i} bad chars {bad}')
        glyphs[name] = rows
    return glyphs, errs

# Badges: 7x7 grids, '.'=transparent, '#'=outline, '1','2','3'=badge ramp, 'w'=white
BADGES = {
    'plus': [
        '..###..',
        '..#3#..',
        '###3###',
        '#33333#',
        '###3###',
        '..#3#..',
        '..###..',
    ],
    'up': [
        '..###..',
        '.#333#.',
        '#33333#',
        '###3###',
        '..#3#..',
        '..#3#..',
        '..###..',
    ],
    'sparkle': [
        '...#...',
        '..#3#..',
        '.#333#.',
        '#33w33#',
        '.#333#.',
        '..#3#..',
        '...#...',
    ],
    'clockb': [
        '.#####.',
        '#33333#',
        '#3#3#3#',  # placeholder replaced below
        '#3#333#',
        '#33333#',
        '.#####.',
        '.......',
    ],
    'shieldb': [
        '#######',
        '#33333#',
        '#33333#',
        '.#333#.',
        '.#333#.',
        '..#3#..',
        '...#...',
    ],
    'no': [
        '.#...#.',
        '#3#.#3#',
        '.#3#3#.',
        '..#3#..',
        '.#3#3#.',
        '#3#.#3#',
        '.#...#.',
    ],
    'flameb': [
        '...#...',
        '..#3#..',
        '..#33#.',
        '.#333#.',
        '.#3w3#.',
        '.#333#.',
        '..###..',
    ],
    'snowb': [
        '...#...',
        '.#.3.#.',
        '..#3#..',
        '#3#3#3#',
        '..#3#..',
        '.#.3.#.',
        '...#...',
    ],
    'boltb': [
        '...##..',
        '..#3#..',
        '.#3#...',
        '#33##..',
        '.##3#..',
        '..#3...',
        '.#3....',
    ],
    'leafb': [
        '....##.',
        '..##33#',
        '.#3333#',
        '.#3333#',
        '#3333#.',
        '#333#..',
        '.##....',
    ],
    'dropb': [
        '...#...',
        '..#3#..',
        '..#3#..',
        '.#333#.',
        '.#3w3#.',
        '.#333#.',
        '..###..',
    ],
    'sunb': [
        '...#...',
        '.#.3.#.',
        '..###..',
        '#3#w#3#',
        '..###..',
        '.#.3.#.',
        '...#...',
    ],
    'starb': [
        '...#...',
        '..#3#..',
        '.#3w3#.',
        '#3www3#',
        '.#3w3#.',
        '..#3#..',
        '...#...',
    ],
    'eyeb': [
        '.......',
        '.#####.',
        '#33333#',
        '#3#w#3#',
        '#33333#',
        '.#####.',
        '.......',
    ],
    'skullb': [
        '.#####.',
        '#33333#',
        '#3#3#3#',
        '#33333#',
        '.#3#3#.',
        '.#####.',
        '.......',
    ],
    'coinb': [
        '..###..',
        '.#333#.',
        '#3w333#',
        '#33333#',
        '#33333#',
        '.#333#.',
        '..###..',
    ],
    'heartb': [
        '.......',
        '.##.##.',
        '#3w#33#',
        '#33333#',
        '.#333#.',
        '..#3#..',
        '...#...',
    ],
    'pearlb': [
        '.......',
        '..###..',
        '.#3w3#.',
        '.#333#.',
        '.#333#.',
        '..###..',
        '.......',
    ],
    'moonb': [
        '..###..',
        '.#33#..',
        '#33#...',
        '#33#...',
        '#33#...',
        '.#33##.',
        '..####.',
    ],
    'downb': [
        '..###..',
        '..#3#..',
        '..#3#..',
        '###3###',
        '#33333#',
        '.#333#.',
        '..#3#..',
    ],
}
BADGES['clockb'] = [
    '.#####.',
    '#33333#',
    '#3#3333',
    '#3##333',
    '#33333#',
    '.#####.',
    '.......',
]

def render_icon(glyph_rows, prim, sec, badge=None, badge_pal=None, glyphs=None):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    p = PALETTES[prim]
    s = PALETTES[sec] if sec else p
    cmap = {
        '#': OUTLINE,
        '1': p[0] + (255,), '2': p[1] + (255,), '3': p[2] + (255,),
        'a': s[0] + (255,), 'b': s[1] + (255,), 'c': s[2] + (255,),
        'w': WHITE,
    }
    for y, row in enumerate(glyph_rows):
        for x, ch in enumerate(row):
            if ch != '.':
                px[x, y] = cmap[ch]
    if badge:
        b = BADGES[badge]
        bp = PALETTES[badge_pal or 'gold']
        bmap = {'#': OUTLINE, '1': bp[0] + (255,), '2': bp[1] + (255,), '3': bp[1] + (255,), 'w': WHITE}
        bh, bw = len(b), len(b[0])
        ox, oy = 16 - bw, 16 - bh
        for y, row in enumerate(b):
            for x, ch in enumerate(row):
                if ch != '.':
                    px[ox + x, oy + y] = bmap.get(ch, OUTLINE)
    return img

def main():
    glyphs, errs = load_glyphs()
    if errs:
        print('GLYPH ERRORS:')
        for e in errs:
            print(' ', e)
        sys.exit(1)
    print(f'{len(glyphs)} glyphs OK')
    if len(sys.argv) > 1 and sys.argv[1] == 'sheet-glyphs':
        # contact sheet of raw glyphs in steel/gold
        names = sorted(glyphs)
        cols = 10
        rows = (len(names) + cols - 1) // cols
        cell = 16 * 6 + 24
        sheet = Image.new('RGBA', (cols * cell, rows * cell + 10), (40, 40, 46, 255))
        d = ImageDraw.Draw(sheet)
        for i, n in enumerate(names):
            icon = render_icon(glyphs[n], 'steel', 'gold')
            big = icon.resize((96, 96), Image.NEAREST)
            cx, cy = (i % cols) * cell + 12, (i // cols) * cell + 4
            sheet.paste(big, (cx, cy), big)
            d.text((cx, cy + 98), n, fill=(230, 230, 230, 255))
        sheet.save(os.path.join(HERE, 'sheet_glyphs.png'))
        print('wrote sheet_glyphs.png')
        return
    # full spec generation: perk specs + passive specs share one uniqueness space
    def read_specs(fn):
        out = []
        for line in open(os.path.join(HERE, fn)):
            line = line.rstrip('\n')
            if not line or line.startswith('#'):
                continue
            parts = line.split('\t')
            pid, glyph, prim, sec, badge, bpal = (parts + [''] * 6)[:6]
            out.append((pid, glyph, prim, sec or None, badge or None, bpal or None))
        return out

    perk_specs = read_specs('specs.tsv')
    passive_specs = read_specs('specs_passives.tsv') if os.path.exists(os.path.join(HERE, 'specs_passives.tsv')) else []
    manifest = {p['id']: p for p in json.load(open(os.path.join(HERE, 'perks_manifest.json')))}
    passives = {p['id']: p for p in json.load(open(os.path.join(HERE, 'passives_manifest.json')))} \
        if passive_specs else {}
    # validation
    bad = False
    seen = {}
    for kind, specs, ids in (('perk', perk_specs, manifest), ('passive', passive_specs, passives)):
        for pid, glyph, prim, sec, badge, bpal in specs:
            if pid not in ids:
                print(f'unknown {kind}', pid); bad = True
            if glyph not in glyphs:
                print('unknown glyph', glyph, 'for', pid); bad = True
            for pal in (prim, sec, bpal):
                if pal and pal not in PALETTES:
                    print('unknown palette', pal, 'for', pid); bad = True
            if badge and badge not in BADGES:
                print('unknown badge', badge, 'for', pid); bad = True
            key = (glyph, prim, sec, badge, bpal)
            if key in seen:
                print('DUPLICATE combo', key, ':', seen[key], 'vs', f'{kind}:{pid}'); bad = True
            seen[key] = f'{kind}:{pid}'
        missing = set(ids) - {s[0] for s in specs}
        if missing:
            print(f'{kind}s missing specs:', len(missing), sorted(missing)[:30]); bad = True
    if bad:
        sys.exit(1)
    outdir = os.path.join(HERE, 'out')
    os.makedirs(outdir, exist_ok=True)
    per_skill = {}
    for pid, glyph, prim, sec, badge, bpal in perk_specs:
        skill = manifest[pid]['skill']
        img = render_icon(glyphs[glyph], prim, sec, badge, bpal)
        os.makedirs(os.path.join(outdir, skill), exist_ok=True)
        img.save(os.path.join(outdir, skill, pid + '.png'))
        per_skill.setdefault(skill, []).append((pid, img))
    passive_items = []
    for pid, glyph, prim, sec, badge, bpal in passive_specs:
        skill, fname = passives[pid]['skill'], passives[pid]['file']
        img = render_icon(glyphs[glyph], prim, sec, badge, bpal)
        os.makedirs(os.path.join(outdir, skill), exist_ok=True)
        img.save(os.path.join(outdir, skill, fname))
        passive_items.append((pid, img))
    if passive_items:
        per_skill['passives'] = sorted(passive_items)
    # contact sheets per skill
    for skill, items in per_skill.items():
        items.sort()
        cols = 8
        rows = (len(items) + cols - 1) // cols
        cell_w, cell_h = 170, 122
        sheet = Image.new('RGBA', (cols * cell_w, rows * cell_h + 8), (40, 40, 46, 255))
        d = ImageDraw.Draw(sheet)
        for i, (pid, icon) in enumerate(items):
            big = icon.resize((96, 96), Image.NEAREST)
            cx, cy = (i % cols) * cell_w + 8, (i // cols) * cell_h + 6
            sheet.paste(big, (cx, cy), big)
            d.text((cx, cy + 100), pid[:26], fill=(235, 235, 235, 255))
        sheet.save(os.path.join(HERE, f'sheet_{skill}.png'))
    print(f'generated {len(perk_specs)} perk + {len(passive_specs)} passive icons + {len(per_skill)} sheets')

if __name__ == '__main__':
    main()
