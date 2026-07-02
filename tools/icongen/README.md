# Perk icon generator

Generates every perk icon under `assets/runicskills/textures/skill/<skill>/<perk_id>.png`
as original 16x16 pixel art (Pillow required: `pip install Pillow`).

- `glyphs1..3.txt` — hand-authored 16x16 glyph library (`.` transparent, `#` outline,
  `1/2/3` primary ramp, `a/b/c` secondary ramp, `w` white). Later files override earlier
  ones, so put corrections in `glyphs3.txt`.
- `specs.tsv` — one line per perk: `id  glyph  primary  secondary  badge  badge_pal  concept`.
  The (glyph, palettes, badge) tuple must be unique per perk; the generator enforces this.
- `perks_manifest.json` — perk id → skill mapping used to pick the output folder
  (regenerate it from `RegistryPerks.java` if perks are added).
- `specs_passives.tsv` + `passives_manifest.json` — same scheme for the 38 Passive
  attribute icons (`passive_*.png`); the manifest also carries each passive's exact
  output filename (note `fortune` → `passive_luck.png`, `ars_flat_mana` →
  `passive_ars_mana.png`). Combo uniqueness is enforced across perks AND passives.

Usage from this directory:

    python3 gen.py                # writes out/<skill>/<id>.png + per-skill contact sheets
    python3 gen.py sheet-glyphs   # renders the raw glyph library for review

Then copy `out/<skill>/*.png` over
`src/main/resources/assets/runicskills/textures/skill/<skill>/`.
`PerkTextureResolutionTest` verifies every registered perk resolves to a PNG.
