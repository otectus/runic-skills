# Follow-ups — out-of-scope findings & residual risk (post-1.3.7 audit)

Items surfaced during the configuration-reliability audit that were deliberately **not** changed,
to keep the audit diff scoped. Each is logged with evidence so it can be picked up later.

## Migration / compatibility notes (1.3.6 → 1.3.7)

- **Behavior change:** `/skillsreload` now reloads `runicskills.common.json5`, `runicskills.titles.json5`
  and `runicskills.convergence-items.json5` from disk in addition to `runicskills.lockItems.json5`.
  Previously only the lock list was reloaded. This is the intended fix (Report 1), but pack authors
  who relied on the old behavior (editing common config in-world having *no* effect until restart)
  will now see their edits apply on reload. No save-data or worldgen impact.
- **Config compatibility:** existing `runicskills.*.json5` files load unchanged — field names and
  formats are untouched. A new optional `enableItemLocks`-style field already existed; nothing is
  renamed. A malformed file is now copied to `<name>.invalid` before defaults are rewritten (new
  side-file, never deletes the original content).
- **Logger name:** `ConfigHolder` now logs under `com.otectus.runicskills.config.storage.ConfigHolder`
  instead of the `RunicSkills` logger. Log *content* and level are unchanged; only the channel name
  differs. (Done so the class is unit-testable without bootstrapping Forge.)
- **No protocol bump:** the config sync packet *contents* did not change in this audit, so
  `ServerNetworking.PROTOCOL_VERSION` ("5") is unchanged. 1.3.7 clients/servers interoperate with
  1.3.6 at the wire level.

## Confirmed but out-of-scope (recommend a follow-up PR)

1. **`/registeritem` can throw on the default (immutable) lock list.** On the very first launch the
   in-memory `lockItemList` is the `List.of(...)` default (immutable); `RegisterItem` calls
   `.add()`/`.remove()`/`.set()` on it, which throws `UnsupportedOperationException` until the file
   has been loaded from disk once (Gson yields an `ArrayList`). Fix: have `HandlerLockItemsConfig`
   default to a mutable `new ArrayList<>(List.of(...))`, or copy before mutating in `RegisterItem`.
   Evidence: `RegisterItem.java:54,75,88`; `HandlerLockItemsConfig.java:24`.
2. **Stale CHANGELOG history.** `CHANGELOG.md` jumps from stacked `[Unreleased]` sections straight to
   `[1.1.0]`; the shipped 1.3.0–1.3.6 releases were never versioned in it. The `[Unreleased] — Phase 1
   dead-perk wiring` section also describes work not in 1.3.7. Recommend reconciling the history.
3. **Doc command-name drift.** README/CurseForge reference `/skills reload` and `/skills register`,
   but the registered commands are `/skillsreload` and `/registeritem` (one word, top-level). The
   sections this audit touched were corrected; the remaining references should be swept.
4. **`config/runicskills-common.json5` path is wrong in several doc spots.** Actual path is
   `config/RunicSkills/runicskills.common.json5`. Corrected in the Configuration / modpack-maker
   sections; other mentions remain.
5. **Botania "Band of Aura: Passive Channel" is unimplemented** (`BotaniaIntegration` TODO). The perk
   is registered with config/lang/texture but its passive-channel hook is a stub.
6. **Unused YACL group lang key** `yacl3.config.runicskills:config.category.common.group.skills` is
   defined but no `@AutoGen` field uses it. Harmless; remove or wire it.

## Verified NOT a bug (recorded so they aren't re-flagged)

- **`PowerEquipSP.readUtf()` is not an unbounded read.** In 1.20.1 `FriendlyByteBuf.readUtf()`
  delegates to `readUtf(32767)` (Short.MAX_VALUE) — identical bound to the explicit form used by other
  packets. No DoS surface; left as-is to avoid churn.
- **`PowerOverridesSyncCP` UNSET sentinel** round-trips correctly (write `Integer.MIN_VALUE` → read
  `Integer.MIN_VALUE` → mapped back to `UNSET`). Not data corruption.

## Larger refactors that would unlock more automated tests

- Extracting the pure lock-gate decision (`enableItemLocks` short-circuit + per-skill level compare)
  out of `SkillCapability.canUse` into a static helper would let it be unit-tested headless. Same for
  the integration-gating predicate and packet write/read symmetry (currently only reachable with a
  Minecraft classpath). Left for a dedicated testability pass.
