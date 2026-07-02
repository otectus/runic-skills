# Runic Skills — Configuration Reliability Audit & Implementation Pass

You are working in the Runic Skills Minecraft mod repository. Perform a full maintenance audit and implementation pass focused on configuration reliability: the config lifecycle, YACL/config UI behavior, item locking, skill/perk/passive/power enablement, config sync, and modpack-author usability.

Execute in phases, in order. Do not begin implementation (Phase 3) until Phases 0–2 are complete and written down. Every fix must be driven by confirmed evidence from this codebase, not by pattern-matching against what "usually" causes bugs like these.

## Anchor reports

Two real user reports, verbatim:

> 1. "Whenever I try to disable the item locking, nothing happens. I may be doing this wrong because my way of turning off the item blocking was deleting the item blocking config file."
>
> 2. "Was making a modpack and downloaded YACL on the most recent version but when I go to configurate Runic Skills nothing happens."

Treat these as symptoms of broader failure modes, not isolated bugs — but both must end this pass with a confirmed root cause, an implemented fix (or a documented blocker), and a verification step.

## Ground rules (apply to every phase)

**Evidence rule.** Label every finding CONFIRMED (file/line evidence or a reproduction) or SUSPECTED (plausible but unproven). Only CONFIRMED findings justify code changes; if you fix a SUSPECTED issue anyway, state why the fix is safe regardless of whether the suspicion is correct. Never report a fix as verified without the command output, test result, or an explicit manual step that proves it.

**Baseline first.** Before changing anything, run the project's available build/test/check commands and record the results in AUDIT.md, so pre-existing failures are never mistaken for regressions you introduced.

**Scope control.** Fix root causes within the audit scope. No drive-by refactors, no formatting churn, no new dependencies, no config file renames, and no behavioral default changes — unless the default itself is the bug, in which case call it out explicitly. Findings outside scope go to FOLLOW_UPS.md instead of growing the diff.

**Dedicated-server safety.** No client-only classes (YACL screens, ModMenu integration, anything touching `Screen`) may be classloaded on a dedicated server. Guard using the loader's idiom — separate client classes, client entrypoints, dist/environment-safe indirection — not same-class if-statements, since class resolution can occur before the check runs.

**Save and config compatibility.** Existing worlds and old config files must continue loading. Any migration must be explicit, logged, and documented.

**Logging standard.** No empty or context-free catch blocks remain in any audited path. Config UI construction failures, config parse failures, migration/default regeneration, invalid registry IDs, and sync failures log at WARN/ERROR with context (file path, field, offending value, mod id). User-actionable problems — YACL missing or version-mismatched, malformed config — produce one clear message, not log spam.

**Reviewable changes.** Make changes in logical units with clear messages. If the repo uses git, work on a branch such as `audit/config-reliability` and commit per fix cluster (adjust to repo conventions if they differ).

## Phase 0 — Orientation and baseline

Record the following in AUDIT.md before auditing anything:

- Loader(s) and versions (Fabric / Forge / NeoForge, multiloader layout or not), Minecraft version(s), build system, and the exact build/test/check commands available in this repo.
- YACL integration facts: required or optional dependency? Declared version range in `fabric.mod.json` / `mods.toml` / `neoforge.mods.toml`; how YACL presence is detected at runtime; which YACL API version the code compiles against; and how that range compares to current YACL releases for this Minecraft version.
- Config inventory: every file the mod creates (exact paths and formats, including `runicskills.lockItems.json5`), the class that owns each, and when each is created, loaded, saved, regenerated, migrated, and reloaded.
- Every config UI entry point: ModMenu entrypoint, Forge/NeoForge config screen extension point, commands, keybinds.
- Baseline build/test results.

## Phase 1 — Root-cause the two anchor reports

For each report, enumerate the candidate failure chains, then confirm or eliminate each one with evidence. Starting hypotheses below — verify them, do not assume them.

**Report 1 — item locking won't disable:**

- A master toggle (`enableItemLocks` or equivalent) doesn't exist, or exists but one or more enforcement paths never check it: static lock items, integration-generated lock items, cached lock entries, tooltips, right-click/use prevention, equipment checks, hand-dropping behavior (`dropLockedItems`), client-side prediction, server-side enforcement, and sync state.
- The toggle exists and works but is invisible in the generated UI (e.g., `@SerialEntry` without `@AutoGen`), so users never find it and resort to deleting the file.
- Deleting `runicskills.lockItems.json5` silently regenerates default locks on next launch — the user's "fix" is actually a reset.
- The toggle's new value never reaches runtime: not synced to the client, only applied after a restart with no indication, or overwritten when integrations regenerate their locks.

**Report 2 — config screen does nothing:**

- YACL version mismatch: the "most recent version" of YACL likely targets a newer Minecraft version. If YACL is optional and detected by mod id alone, the presence check passes but class resolution throws, the exception is swallowed, and the screen silently no-ops.
- The screen factory returns null or the parent screen, or exceptions during screen construction are caught — by the mod, by ModMenu, or by the loader's config screen host — without logging.
- The ModMenu entrypoint / Forge or NeoForge config screen factory isn't registered, is registered on the wrong dist, or is registered unconditionally while directly referencing YACL classes that may be absent.
- Screen construction aborts on malformed generated options: an invalid registry ID in a lock list, a bad default value, a missing translation key, or a custom controller failure.

Write the confirmed chain(s) for both reports into AUDIT.md with file/line evidence before moving on.

## Phase 2 — Systematic audit

For every setting in scope, trace the full pipeline: declaration → UI generation annotations → disk serialization → load/regeneration/migration → sync payload → server-side enforcement → client-side behavior. A setting only "works" if every stage agrees.

**A. Config lifecycle.** Verify behavior for each config file when deleted, empty, malformed, partially migrated from an older version, or missing newly added fields. Regeneration must log at INFO naming the file. Defaults must be safe, and nothing may silently re-enable a feature the user disabled. Deleting `runicskills.lockItems.json5` must not be the required way to disable item locking — there must be a clear master toggle with obvious docs.

**B. Item locking.** The master toggle must gate every enforcement path listed under Report 1 above. If any path ignores it — including integration-generated locks and cached entries — fix it. Add or update tests where possible.

**C. Annotation sweep.** Find every field annotated `@SerialEntry` or `@ListGroup`. Flag every field that should be user-configurable but lacks the YACL UI-generation annotations (`@AutoGen` with a correct category, plus controller annotations). This exact pattern caused a previous bug — fields persisting to disk but never appearing in the in-game screen. Fix every instance, and add a code comment on any field that is intentionally serialized-but-hidden.

**D. Skills, perks, passives, powers.** Disabled-perk/passive/power lists, max active perks, perk cooldowns, skill cost multipliers, integration toggles, and every per-feature toggle must be: visible in the UI, persisted correctly, synced correctly, enforced server-side, and reflected client-side. Disabling must block new use, rank-up, and equip. Define and document the behavior for save data that already references a now-disabled entry — handle it safely, with no destructive edits to player data.

**E. Sync and reload.** Server-authoritative settings must sync on: login, `/skillsreload`, datapack reload, config-screen save, integrated-server startup, and dimension change if relevant. Bump protocol/payload versions whenever payload contents change. Prove that stale client state cannot make disabled item locks, perks, powers, or integrations appear active.

**F. Integrations.** Every supported integration must respect both its master toggle and any finer-grained lock-item toggles. When disabled: no event handler registration, no lock generation, no applied effects, no misleading "integration loaded" log lines. Validate behavior with the integration mod present and absent.

**G. YACL controllers and list editors.** Review lock-item controllers, string-list controllers, object/list groups, dropdowns: defaults, parsing, validation, add/remove behavior, save/apply semantics. If any controller's `setFromString` is a no-op, the UI must not appear editable while discarding input — replace with explicit validation errors or a safer control.

**H. Error visibility.** Find every place where errors in the above paths are swallowed, demoted too far, or missing context, and bring them up to the logging standard.

Search starting points: `@SerialEntry`, `@ListGroup`, `@AutoGen`, `enableItemLocks`, `dropLockedItems`, lock caches / lock checks / lock item lists / generated locks, every YACL / ModMenu / config screen / UI builder / controller / generated option class, every disabled perk/passive/power check, all config sync packet payloads, all reload commands and events, and catch blocks in all of the above.

Deliverable: a findings table in AUDIT.md — Area | Finding | CONFIRMED/SUSPECTED | Severity | Evidence (file:line) | Proposed fix. (If running interactively, this is the natural checkpoint to review before fixes proceed.)

## Phase 3 — Implementation

Priority order: **P0** — anything causing user configuration to be ignored, hidden, reset, unsynced, or inaccessible (both anchor reports live here). **P1** — sync/enforcement gaps and integration leak-through. **P2** — logging and documentation polish. Run the build after completing each tier.

No shallow one-line changes unless the audit proves one is sufficient; each fix must cover the entire traced pipeline for that setting. Prefer robust fixes over workarounds.

Required outcomes (definition of done):

- Setting `enableItemLocks = false` — via the in-game UI or by editing the file — fully disables every form of item locking after the documented apply step (config save / `/skillsreload` / relog, as designed), with no file deletion involved, and integrations stop generating locks.
- Every supported config screen entry point either opens the screen or logs exactly one actionable ERROR stating the cause (e.g., "Runic Skills config UI requires YACL x.y for MC a.b; found z"). Where a fallback is feasible without touching YACL classes, surface a minimal in-game pointer to the log instead of a silent no-op.
- Deleting any config file regenerates documented defaults with an INFO log naming the file; the docs state plainly that deleting `runicskills.lockItems.json5` regenerates locks rather than disabling them.

## Phase 4 — Tests and verification

Automate everything that runs headless: config parse/serialize round-trips, migration from sample old-version configs, malformed-file recovery, lock evaluation logic with the master toggle on and off, and integration gating logic. Use the existing test or gametest setup if present; otherwise plain JUnit against the logic classes.

Write VERIFICATION.md with exact manual steps and expected results covering: singleplayer and dedicated server, each crossed with {fresh configs, deleted `runicskills.lockItems.json5`, malformed config, YACL correct version, YACL wrong-MC-version, YACL absent} — plus ModMenu/config screen entry, item locks enabled/disabled, integration locks enabled/disabled, disabled perk/passive/power flows, and `/skillsreload`.

## Phase 5 — Documentation

- Config comments and tooltips for every option touched or clarified.
- README / CurseForge description: a "Disabling item locking" section explaining the four distinct actions — turning off the whole system (master toggle), disabling specific lock entries, disabling integration-generated locks, and deleting/regenerating config files — including the exact config file path and the exact in-game UI location.
- Update COMMENT_TRIAGE.md: classify both anchor reports against the confirmed root causes and draft a suggested reply to each reporter.
- Migration/compatibility notes for anything migrated.

## Deliverables (in this order)

1. AUDIT.md — orientation facts, baseline results, confirmed root causes for both reports, full findings table.
2. Implemented fixes, with a list of files changed and why each changed.
3. VERIFICATION.md as specified above.
4. Updated documentation, comments, and tooltips per Phase 5.
5. FOLLOW_UPS.md — remaining risks and out-of-scope findings.
6. Final gate: run every available build/test/check command and include the exact commands and outcomes in your report. Fix failures where in scope; where a failure cannot be fixed within scope, document the exact failure and why it remains.
