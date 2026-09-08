#!/usr/bin/env python3
"""Runic Skills perk icon generator.

16x16 RGBA pixel-art icons composed from a hand-authored glyph library,
themed 3-tone palettes, and small badge overlays.

Char scheme in glyph grids:
  '.' transparent   '#' outline   '1','2','3' primary dark/mid/light
  'a','b','c' secondary dark/mid/light   'w' white highlight
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUTLINE = (27, 23, 39, 255)
WHITE = (255, 246, 220, 255)

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
    'night':    ((44, 53, 94), (81, 99, 158), (148, 166, 223)),
    'shadow':   ((59, 64, 79), (105, 113, 132), (179, 187, 205)),
    'leather':  ((86, 55, 30), (135, 90, 52), (185, 135, 88)),
    'slate':    ((44, 48, 58), (78, 85, 100), (120, 130, 148)),
    'obsidian': ((65, 48, 86), (109, 78, 141), (168, 131, 198)),
    'prismarine':((22, 92, 88), (52, 158, 148), (118, 218, 205)),
    'diamond':  ((28, 120, 130), (70, 200, 210), (160, 248, 250)),
    'void':     ((48, 33, 70), (89, 60, 122), (152, 112, 187)),
    'sand':     ((150, 128, 72), (208, 184, 118), (240, 224, 168)),
    'orange':   ((160, 82, 14), (226, 132, 34), (252, 186, 92)),
}

def load_glyphs():
    glyphs = {}
    for fn in ('glyphs1.txt', 'glyphs2.txt', 'glyphs3.txt', 'glyphs4.txt'):
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
    # Short rows are transparent padding, never a resize. Reject overflow rather than silently
    # cutting off authored pixels, which hid malformed glyphs in the previous generator.
    errs = []
    valid = set('.#123abcw')
    for name in list(glyphs):
        if len(glyphs[name]) != 16 or any(len(r) > 16 for r in glyphs[name]):
            errs.append(f'{name}: glyph must contain 16 rows of at most 16 pixels')
        rows = [r.ljust(16, '.') for r in glyphs[name]]
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
    'anchorb': ['..###..', '..#3#..', '..#3#..', '#.#3#.#', '#3#3#3#', '.#333#.', '..###..'],
    'arrowb': ['...#...', '..#3#..', '.#333#.', '#33333#', '..#3#..', '..#3#..', '..###..'],
    'arrowsb': ['..#.#..', '.#3#3#.', '#33#33#', '.#3#3#.', '..#.#..', '.......', '.......'],
    'burstb': ['#..#..#', '.##3##.', '.#333#.', '#33w33#', '.#333#.', '.##3##.', '#..#..#'],
    'pawb': ['.#...#.', '#3#.#3#', '.#.#.#.', '..#3#..', '.#333#.', '.#333#.', '..###..'],
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
        '#3#333#',
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
def render_icon(glyph_rows, prim, sec, badge=None, badge_pal=None, glyphs=None):
    """Carved silhouettes with consistent upper-left light and an ink contour.

    The authored masks remain at native resolution. Surface bevels replace flat fills with a
    three-tone ramp; no blur, resampling, random marks or identifier hashes enter the artwork.
    The action badge has its own material ramp and is separated by a one-pixel ink keyline.
    """
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
                color = cmap[ch]
                if ch in '123abc':
                    family = '123' if ch in '123' else 'abc'
                    ramp = p if ch in '123' else s
                    def same(nx, ny):
                        return 0 <= nx < 16 and 0 <= ny < 16 and glyph_rows[ny][nx] in family
                    # A lit north/west lip and dark south/east bevel give even a small tool head
                    # or leaf a readable carved shape. Explicit dark marks remain authored cuts.
                    tone = 0 if ch in '1a' else 2 if not same(x, y - 1) or not same(x - 1, y) \
                        else 0 if not same(x, y + 1) or not same(x + 1, y) else 1
                    color = ramp[tone] + (255,)
                px[x, y] = color
    if badge:
        b = BADGES[badge]
        bp = PALETTES[badge_pal or 'gold']
        bmap = {'#': OUTLINE, '1': bp[0] + (255,), '2': bp[1] + (255,), '3': bp[2] + (255,), 'w': WHITE}
        bh, bw = len(b), len(b[0])
        ox, oy = 16 - bw, 16 - bh
        for y, row in enumerate(b):
            for x, ch in enumerate(row):
                if ch != '.':
                    if ch not in bmap:
                        raise ValueError(f'unknown badge pixel {ch!r} in {badge}')
                    color = bmap[ch]
                    if ch == '3' and y > 0 and b[y - 1][x] in '123w':
                        color = bp[1 if y < bh - 2 else 0] + (255,)
                    px[ox + x, oy + y] = color
    return img

def main():
    # The historical entrypoint now regenerates shipped icons directly; there is no manual-copy
    # step capable of leaving source artwork newer than the textures in the jar.
    import build
    build.main()


if __name__ == '__main__':
    main()
