# Runic icon source and validation

The 2.1.0 set contains **641 shipped 16×16 RGBA icons**: 475 Perks, 38 Passives,
40 Skill progression variants (four for each of ten Skills), 87 Powers, and the
Powers menu control. Existing resource paths and the four Skill rank thresholds
are preserved.

## Art direction

Each emblem uses an explicit mechanic silhouette and, where appropriate, a small
action badge: a shield protects, a clock changes timing, a droplet restores or
consumes a resource, an arrow increases or redirects, and a heart concerns life.
Tools and armor carry carved details; upper-left lighting, three-tone materials,
and a shared dark ink contour unify the set. Dark-material ramps retain contrast
on the GUI. No random or hash-selected runes stand in for mechanics.

Powers use their school colour family and one, two, or three short tally cuts at
the foot for Mark, Seal, or Crown. Skill rank variants use four two-pixel progress
steps. Those rank marks remain distinguishable without colour. Each final image
has a unique **decoded pixel** digest, including across icon families.

Artwork is authored and displayed at native 16×16 resolution. The main GUI centers
Perks and Passives in its existing 24×24 frames without 1.25× stretching. Review
sheets show actual native size on light and dark surfaces plus a 3× nearest-neighbour
inspection copy. There is no antialiasing, fractional alpha, or texture filtering
in the generated PNGs.

## Sources

- `glyphs1.txt` through `glyphs4.txt`: authored pixel masks, with later corrections
  overriding earlier shapes. `.` is transparent, `#` the outline, `1/2/3` the
  primary material, `a/b/c` the secondary, and `w` the highlight. Masks must have
  exactly 16 rows and no row wider than 16; short rows receive transparent padding.
- `specs.tsv`, `specs_passives.tsv`, `specs_skills.tsv`, `specs_powers.tsv`: explicit
  silhouettes, materials, badges, and a mechanic explanation for every entry.
- `perks_manifest.json`, `passives_manifest.json`: output-path mappings. They do
  not copy gameplay descriptions or gate values, which belong to the registry
  and language resources.
- `legacy_perks.json`: 17 historical authoring entries that are no longer registered
  and have no shipped texture on this branch. They remain in the art library but
  are excluded from generation; this list is an explicit orphan allowance.
- `gen.py`: shared carved-pixel renderer and compatibility entrypoint.
- `powers.py`: school/tier registry parser and semantic Power renderer.
- `build.py`: complete generator, registry comparison, and image verification.
- `catalogue.json`: reviewed output inventory, mechanic explanations and decoded
  RGBA SHA-256 hashes. Generated together with the icons.

## Workflow

Python 3 and Pillow are required only for authoring. From the repository root:

```text
python tools/icongen/build.py --sheets
python tools/icongen/build.py --check
```

Generation writes directly into `src/main/resources/assets/runicskills/textures/`;
there is no manual copy from an ignored staging folder. Unchanged assets are not
rewritten. `--check` makes no changes to shipped assets and fails on source/output
drift, missing registrations, unknown specs, duplicate decoded images, malformed
masks, incorrect dimensions, or partial alpha. `--sheets` writes reviewed sheets
under both `tools/icongen/sheet_*.png` and `build/reports/icons/`.

The historical `python tools/icongen/gen.py` command now runs the complete generator.
`python tools/icongen/powers.py` remains available for a Power-only preview; run the
complete generator afterward to refresh the catalogue and cross-family checks.

Gradle's regular `test`/`check` runs `RunicIconIntegrityTest`,
`PerkTextureResolutionTest`, and `PowerIconCoverageTest` without needing Python.
They verify actual PNG pixels, palette/alpha bounds, native dimensions, catalogue
coverage and uniqueness. Review the sheets at native size after changing masks,
and exercise the GUI at multiple Minecraft GUI scales before a release.

## Powers control and interaction checks

The main overview has a 20×20 icon-only button inset six GUI pixels from its upper
right corner. The emblem stays 16×16 with two pixels of padding; Minecraft's GUI
scale handles integer physical scaling. A vanilla `Button` subclass supplies
keyboard activation, focus order, click sound, tooltip scheduling and localized
narration. Hover uses a pale green edge, keyboard focus a gold edge, and an
unavailable local capability dims/disables the control. Detail/title pages use
that corner for their own controls, so the Powers button appears on the overview.
Escape from a Powers panel opened this way returns to the same Skills screen.

Powers equip buttons refresh after authoritative server state changes, including
in-place capability updates. Equipped disabled entries remain removable even with
the hide option enabled. A missing-addon recovery control lists the next unavailable
id in its tooltip and frees that slot through the existing validated server path.
