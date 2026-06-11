# Runic Skills 1.3.7 — Verification

Manual + automated steps proving the audit fixes. Automated tests run headless via `./gradlew test`
(see `src/test/java/.../ConfigHolderTest.java`). The matrix below is the manual coverage.

Config directory: `<instance>/config/RunicSkills/`. Files: `runicskills.common.json5` (master toggle
`enableItemLocks` + disabled lists + integration toggles), `runicskills.lockItems.json5` (the lock list),
`runicskills.titles.json5`, `runicskills.convergence-items.json5`.

## Automated (headless)

```
./gradlew test
```
Expect: `ConfigHolderTest` — 10 tests PASSED. Covers parse/serialize round-trip, JSON5 comment
stripping (incl. `//` inside strings, unterminated block), missing-field migration, and recovery from
missing / empty / malformed files including the `.invalid` backup, and the "deleting the file
regenerates defaults (not disabled)" behavior from Report 1.

## Build / lint gate

```
./gradlew :checkSidedImports compileJava build
```
Expect: BUILD SUCCESSFUL. `:checkSidedImports` proves no YACL executable type leaks outside
`client/config/` (dedicated-server safety). One pre-existing deprecation warning
(`KubeJSIntegration.postLevelUpEvent`) is unrelated to this audit.

## Report 1 — item locking disables correctly

| # | Steps | Expected |
|---|-------|----------|
| 1 | Singleplayer. Edit `runicskills.common.json5`, set `enableItemLocks: false`. Run `/skillsreload`. | A previously locked item (e.g. `minecraft:iron_sword` at a low Strength level) is usable immediately, **no restart**. Tooltip lock lines disappear. |
| 2 | Set `enableItemLocks: true`, `/skillsreload`. | Locks return immediately. |
| 3 | Delete `runicskills.lockItems.json5`, relaunch. | Log shows `INFO Config .../runicskills.lockItems.json5 not found; writing defaults.` File regenerates with default locks. (Confirms deleting the file is a *reset*, not a disable.) |
| 4 | Dedicated server: same as #1 via server console `/skillsreload`. | All connected clients see locks lifted without relog (config re-synced from disk). |
| 5 | Put a syntax error in `runicskills.common.json5`, relaunch. | Log shows `WARN Failed to parse ...; regenerating defaults and keeping the unparseable file as runicskills.common.json5.invalid`. A `.invalid` copy exists; the game loads with defaults. |

## Report 2 — config screen behaviour across YACL states

| # | YACL state | Steps | Expected |
|---|-----------|-------|----------|
| 6 | Correct (3.4.2+ for 1.20.1) | Mods list → Runic Skills → Configure | YACL screen opens; `enableItemLocks`, `Disabled powers`, integration toggles all visible and editable; Save persists; on close, values apply without `/skillsreload`. |
| 7 | Absent | Remove YACL jar, launch, click Configure | A vanilla "Runic Skills config unavailable" screen appears naming the installed YACL (`absent`) and pointing at the log; **not** a silent no-op. Log has exactly one `ERROR` line. |
| 8 | Wrong/incompatible (e.g. a much newer YACL whose API drifted) | Swap in an incompatible YACL build, click Configure | Same fallback screen (names the found version); one `ERROR` line citing the `LinkageError` cause; no crash. |
| 9 | Dedicated server, YACL absent | Boot dedicated server (YACL is client-only) | Server boots cleanly; no `NoClassDefFoundError: dev/isxander/...`. (`:checkSidedImports` guards this at build time.) |

## Integration lock gating

| # | Steps | Expected |
|---|-------|----------|
| 10 | With an integration mod present (e.g. Spartan Weaponry), set `enableSpartanIntegration: false`, `/skillsreload`. | Spartan items are no longer locked (master toggle now gates lock generation, not just the event handler). |
| 11 | Set `enableSpartanIntegration: true` but `spartanEnableLockItems: false`, `/skillsreload`. | Spartan items unlocked (finer toggle still works); other Spartan hooks remain active. |
| 12 | Set `enableItemLocks: false`, `/skillsreload`. | No integration locks generated at all (no "Generated N lock items" debug lines), and nothing is enforced. |

## Disabled perk / passive / power

| # | Steps | Expected |
|---|-------|----------|
| 13 | Add a perk path to `disabledPerks`, `/skillsreload`. | The perk cannot be enabled or ranked up; existing rank's effects suppressed; save data intact (re-enabling restores it). |
| 14 | Add a passive to `disabledPassives`, `/skillsreload`. | Passive cannot be leveled; its attribute modifier is removed immediately (no relog). |
| 15 | Add a power to `disabledPowers` **via the config screen** (now that it is visible), Save. | The power cannot be equipped; previously-equipped entries are filtered at runtime. |

## Edit-in-UI apply path

| # | Steps | Expected |
|---|-------|----------|
| 16 | Open config screen, toggle `enableItemLocks` off, Save, close. | On close, `ReloadOnCloseScreen` reloads the holder; locks lift without `/skillsreload`. |
