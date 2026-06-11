# Runic Skills — Configuration Reliability Audit

Audit branch: `audit/config-reliability`. Date: 2026-06-10. Scope: config lifecycle, YACL/config UI, item locking, skill/perk/passive/power enablement, sync, integration gating, logging, plus a general codebase bug sweep.

Evidence rule: findings are labelled **CONFIRMED** (file:line evidence) or **SUSPECTED**. Only CONFIRMED findings drive code changes.

---

## Phase 0 — Orientation & baseline

### Loader / build facts
- **Loader:** Forge `47.3.0`, single-loader (not multiloader). **MC** `1.20.1`. **Java** 17 (Gradle toolchain; host JDK is 21 but the toolchain resolves 17). **Gradle** 8.10 + ForgeGradle. **Mappings:** Parchment `2023.09.03-1.20.1`.
- **Build/check commands:** `./gradlew compileJava` (compile-only), `./gradlew build` (full), `./gradlew runClient` (dev client), `./gradlew :checkSidedImports` (custom lint forbidding YACL executable-class imports outside `client/config/`). JUnit test task added by this audit (`./gradlew test`).

### YACL integration facts
- **Optional**, client-only. `mods.toml`: `modId = "yet_another_config_lib_v3"`, `mandatory = false`, `versionRange = "[3.4.2,)"` (no upper bound), `side = "CLIENT"`.
- Compiled against `dev.isxander:yet-another-config-lib:3.5.0+1.20.1-forge` (`gradle.properties:45`), API package `dev.isxander.yacl3.config.v2.api.*`. `compileOnly` + `runtimeOnly` in `build.gradle`.
- **Runtime detection:** the config screen factory is registered unconditionally; YACL classes resolve only when `YaclConfigUiBuilder.buildScreen` is actually invoked (method-reference isolation in `RunicSkillsClient.ClientProxy`). `ConfigHolder.generateGui()` reaches the builder via reflection.
- **Version gap risk:** `[3.4.2,)` accepts any newer YACL, including releases whose API has drifted from 3.5.0 — the basis of Report 2.

### Config inventory (all JSON5 under `<configdir>/RunicSkills/`, owned via `ConfigHolder<T>`)
| File | Owner | Created/loaded |
|------|-------|----------------|
| `runicskills.common.json5` | `HandlerCommonConfig` | `Configuration.Init()` at mod construct |
| `runicskills.lockItems.json5` | `HandlerLockItemsConfig` | `Configuration.Init()` |
| `runicskills.titles.json5` | `HandlerTitlesConfig` | `Configuration.Init()` |
| `runicskills.convergence-items.json5` | `HandlerConvergenceItemsConfig` | `Configuration.Init()` |

Lifecycle: `ConfigHolder.load()` reads UTF-8, strips JSON5 comments, parses with plain Gson; on missing/empty/parse-fail it writes `defaultSupplier.get()` to disk. **Deleting a file silently regenerates defaults.** No field-level migration (Gson leaves missing fields at their default). Datapack-backed reload listeners exist for perk groups, power overrides, skill visuals (separate from the config files).

### Config UI entry points
- **Forge "Configure" button:** `ConfigScreenHandler.ConfigScreenFactory` registered in `RunicSkillsClient.ClientProxy.clientSetup` → `YaclConfigUiBuilder::buildScreen`. Builds a YACL screen for **`HandlerCommonConfig` only** (lock/titles/convergence configs are file-only).
- **Keybind** `Y` (`OPEN_RUNICSKILLS_SCREEN`) opens the in-game `RunicSkillsScreen`, **not** the config screen.
- **Command** `/skillsreload` (permission level 2).
- No ModMenu (Forge), no `mods.toml configGuiFactory`.

### Baseline build result (pre-fix, after resolving Finding #1)
- `./gradlew :checkSidedImports compileJava` → **BUILD SUCCESSFUL in 41s**. One pre-existing deprecation warning: `KubeJSIntegration.postLevelUpEvent` used at `RunicSkillsScreen.java:969` (the `@Deprecated(forRemoval=true)` API). No errors. `:checkSidedImports` passes (no YACL leakage server-side).
- Note: `gradle.properties` carried an **unresolved git merge conflict** (Finding #1) that had to be resolved *before* a meaningful baseline could be taken; the working tree is otherwise dirty with ~1140 pre-existing changes (mostly icon PNGs) unrelated to this audit.

---

## Phase 1 — Confirmed root causes for the two anchor reports

### Report 1 — "Whenever I try to disable item locking, nothing happens… my way of turning off item blocking was deleting the item blocking config file."

Three compounding causes (the toggle itself is **not** broken):

- **R1-A — `/skillsreload` never reloads the common config (CONFIRMED, P0).** `SkillsReloadCommand.execute` calls `HandlerSkill.ForceRefresh()`, which only does `HandlerLockItemsConfig.HANDLER.load()` (`HandlerSkill.java:51-56`). `HandlerCommonConfig` — owner of `enableItemLocks` — is never re-read from disk, yet the command then re-syncs the **stale in-memory** value to every client via `CommonConfigSyncCP.sendToPlayer`. So editing `enableItemLocks=false` and running `/skillsreload` changes nothing; only a full restart reloads `common.json5`. Same gap affects `disabledPerks/Passives/Powers`, integration toggles, and all multipliers.
- **R1-B — discoverability / file-naming trap (CONFIRMED, P0).** The master toggle lives in `runicskills.common.json5` (`HandlerCommonConfig.java:81-84`). The user instead deletes the obviously-named `runicskills.lockItems.json5`, which **silently regenerates 500+ default locks** on next load (`ConfigHolder.load:88-94`). Their "fix" is a reset. No docs point to `enableItemLocks`.
- **R1-C — links to Report 2 (CONFIRMED).** With the config screen not opening, the toggle can't be found via the UI either.
- **Enforcement is correct:** both `canUse` overloads short-circuit on `enableItemLocks` (`SkillCapability.java:356,371`); every path (interaction, combat, `MixCraftingMenu`, equipment, tick-drop, curios, client prediction, tooltip) funnels through them. This is a **lifecycle + discoverability** failure, not an enforcement gap.

### Report 2 — "Downloaded YACL on the most recent version but when I go to configure Runic Skills nothing happens."

- **R2-A — catch clause too narrow for version drift (CONFIRMED, P0).** `YaclConfigUiBuilder.buildScreen` catches only `NoClassDefFoundError | RuntimeException` (`YaclConfigUiBuilder.java:54`). A present-but-mismatched YACL (the "most recent version" scenario, enabled by the unbounded `[3.4.2,)` range) throws `LinkageError` subtypes — `NoSuchMethodError`, `NoSuchFieldError`, `AbstractMethodError`, `IncompatibleClassChangeError` — none of which are `NoClassDefFoundError` or `RuntimeException`. They escape the catch and propagate to Forge's screen host → no-op or crash.
- **R2-B — silent fallback, no actionable message (CONFIRMED, P0).** When the error *is* caught (YACL fully absent), the handler logs a single **WARN** and returns the **parent screen**, so the "Configure" button visibly does nothing and the user gets no on-screen explanation.

---

## Phase 2 — Findings table

| # | Area | Finding | Status | Sev | Evidence | Proposed fix |
|---|------|---------|--------|-----|----------|--------------|
| 1 | Build | Unresolved git merge conflict in `gradle.properties` (`mod_version` 1.3.2 vs 1.3.6) | CONFIRMED | P0 | gradle.properties:55-59 | Resolve to 1.3.6 (done Phase 0); bump to 1.3.7 |
| 2 | Reload | `/skillsreload` reloads only lockItems, not common/titles/convergence (R1-A) | CONFIRMED | P0 | HandlerSkill.java:51-56 | Reload all four holders before rebuild+resync |
| 3 | Config UI | `buildScreen` catch misses `LinkageError` from version drift (R2-A) | CONFIRMED | P0 | YaclConfigUiBuilder.java:54 | Catch `LinkageError \| RuntimeException` |
| 4 | Config UI | Silent return-to-parent, WARN only, no in-game message (R2-B) | CONFIRMED | P0 | YaclConfigUiBuilder.java:55-58 | One ERROR + vanilla `YaclUnavailableScreen` |
| 5 | Annotation | `disabledPowers` lacks `@AutoGen` (hidden) while perks/passives shown | CONFIRMED | P1 | HandlerCommonConfig.java:63-65 | Add `@AutoGen(common, general)` |
| 6 | Integration | Lock generation ignores integration master toggle & `enableItemLocks` | CONFIRMED | P1 | HandlerSkill.injectIntegrationItems; SpartanIntegration.java:175 | Gate generation on master toggle + `enableItemLocks` |
| 7 | Docs | No "disabling item locking" docs; file naming misleads | CONFIRMED | P1 | README/CURSEFORGE | Add docs section (4 actions) |
| 8 | NPE | `canUseItem/Block/Entity` `requireNonNull(getKey())` → NPE on unregistered items | CONFIRMED | P1 | SkillCapability.java:336,348,352 | Null-check, deny-safe |
| 9 | NPE | Same pattern in tooltip + `/registeritem` | CONFIRMED | P2 | RegistryClientEvents.java:31; RegisterItem.java:44 | Null-check |
| 10 | Lang | 17 `@AutoGen` group keys missing from `en_us.json` (cosmetic) | CONFIRMED | P2 | en_us.json | Add keys |
| 11 | Net | `PowerEquipSP` `readUtf()` — initially flagged as unbounded | **NOT A BUG** | — | PowerEquipSP.java:37 | `readUtf()` already delegates to `readUtf(32767)`; no change |
| 12 | Robustness | `assert x != null` no-op in prod (packet handlers/mixins) | CONFIRMED | P2 | PlayerMessagesCP.java:40; MixShulkerBullet.java | Explicit null guards |
| 13 | Version | VERSION=1.3.2, CLAUDE.md=1.1.0, release=1.3.6 — drift | CONFIRMED | P2 | VERSION; CLAUDE.md | Sync + bump to 1.3.7 |
| 14 | Incomplete | Botania "Band of Aura: Passive Channel" TODO | CONFIRMED | P3 | BotaniaIntegration.java | Document in FOLLOW_UPS |
| 15 | Logging | No INFO when config defaults are (re)generated | CONFIRMED | P2 | ConfigHolder.java:88-95 | INFO naming file + reason |

**SUSPECTED / not bugs:** `PowerOverridesSyncCP` UNSET sentinel round-trips correctly (write MIN_VALUE → read MIN_VALUE → UNSET) — not corruption. `ConfigParser`/`AdvancementCondition` `ResourceLocation` parsing on malformed user strings can throw `IllegalArgumentException` — defensive wrap is safe regardless, so fixed.

**Intentionally serialized-but-hidden (not fixed, documented):** ~376 `*RequiredLevel` ints and ~46 passive-level `int[]` arrays in `HandlerCommonConfig` are file-only by design (a 376-row screen is unusable; arrays are length-locked). `treasureHunterItemList` uses a custom `trashList[...]` DSL unsuited to a list editor. These get an explanatory code comment + README note rather than UI exposure.

---

## Phase 3 — Fixes implemented

Files changed and why:

| File | Change |
|------|--------|
| `gradle.properties` | Resolved unresolved git merge conflict; `mod_version` → 1.3.7 (#1, #13). |
| `VERSION`, `CLAUDE.md` | Synced version to 1.3.7 (#13). |
| `config/Configuration.java` | New `reloadAll()` reloading all four config holders; `Init()` delegates to it (#2/R1-A). |
| `handler/HandlerSkill.java` | `ForceRefresh()` calls `Configuration.reloadAll()`; `injectIntegrationItems` gates each integration on its master toggle and short-circuits when `enableItemLocks` is off (#2, #6). |
| `client/config/YaclConfigUiBuilder.java` | Catch broadened to `LinkageError \| RuntimeException`; one actionable ERROR with detected YACL version; returns fallback screen (#3/#4, R2). |
| `client/config/YaclUnavailableScreen.java` | New vanilla-only fallback screen (no YACL types) (#4). |
| `config/storage/ConfigHolder.java` | INFO log on (re)generation; `.invalid` backup of unparseable files; own slf4j logger (#15). |
| `handler/HandlerCommonConfig.java` | `@AutoGen` on `disabledPowers` (#5). |
| `common/capability/SkillCapability.java` | Null-safe registry lookups in `canUseItem/Block/Entity` (#8). |
| `client/event/RegistryClientEvents.java`, `common/command/RegisterItem.java` | Null-safe registry lookups (#9). |
| `mixin/MixShulkerBullet.java`, `network/packet/client/PlayerMessagesCP.java` | `assert != null` → real null guards (#12). |
| `config/ConfigParser.java`, `config/conditions/AdvancementCondition.java` | Defensive `ResourceLocation` parsing (SUSPECTED, safe). |
| `assets/runicskills/lang/en_us.json` | 17 missing YACL group keys; `disabledPowers` label/desc; config-unavailable strings (#5, #10). |
| `build.gradle`, `src/test/...ConfigHolderTest.java` | JUnit 5 infra + 10 headless config-lifecycle tests. |
| `CHANGELOG.md`, `README.md`, `CURSEFORGE_DESCRIPTION.md`, `COMMENT_TRIAGE.md` | 1.3.7 notes; "Disabling item locking" section; anchor-report triage (#7). |

## Final gate

- `./gradlew test` → 10/10 PASS (`ConfigHolderTest`).
- `./gradlew build` → **BUILD SUCCESSFUL** (compile, jar, reobf, jarJar, `:checkSidedImports`, `check`). Only the pre-existing `KubeJSIntegration.postLevelUpEvent` deprecation warning remains (unrelated to this audit; logged in FOLLOW_UPS history).
- `:checkSidedImports` passes → no YACL executable type leaks outside `client/config/`; the new `YaclUnavailableScreen` is vanilla-only and dedicated-server safe.
