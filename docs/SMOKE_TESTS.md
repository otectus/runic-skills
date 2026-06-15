# Runic Skills smoke-test matrix

A living checklist for runtime verification. Each row is one combination + one fixture; mark
`PASS` / `FAIL` / `N/A` per release. The matrix is intentionally over-broad — it's faster to
flag a skipped row than to find out post-release that an integration crashes.

Build-time guarantees (run automatically by CI / `./gradlew check`):

- `./gradlew compileJava` — code compiles.
- `./gradlew checkSidedImports` — no `net.minecraft.client.*` or `com.mojang.blaze3d.*`
  imports outside the client allowlist; **no executable YACL class imports
  (`ConfigClassHandler`, `serializer.*`, `gui.*`, `api.*`) outside `client/config/`**.
  This second check is what keeps dedicated servers bootable when YACL is absent.

Everything below this line is runtime smoke testing that must be done by hand.

---

## 1. Bare-bones boot

| # | Combo | Result | Notes |
|---|---|---|---|
| 1.1 | Runic Skills jar alone, dedicated server, **no YACL installed** | | Primary regression test for the 1.1.0 YACL refactor. Pre-1.1.0 this crashed with `NoClassDefFoundError: dev/isxander/yacl3/...` during mod construction. Expected: server reaches "Done" line. |
| 1.2 | Runic Skills jar alone, integrated client, no YACL | | Client should boot, Skills key (Y) should open the screen, and clicking the mod's "Configure" button on the mod list should fall back to the previous screen with a log warning rather than crash. |
| 1.3 | Runic Skills jar + YACL, integrated client | | YACL config screen should open, all groups visible, all fields render. New 1.1.0 fields visible: `enableScholarEnchantmentHiding`, `disabledPerks`, `disabledPassives` (under `general`). |
| 1.4 | Runic Skills jar alone, dedicated server, **no L2Tabs installed** | | Regression test for the L2Tabs class-load fix. Pre-1.1.0, ClientProxy held a direct `TabRegistry` import which the JVM verifier eager-loaded. Expected: server (and client too) boots clean. |
| 1.5 | Runic Skills jar alone, integrated client, **no Legendary Tabs installed** | | Existing 1.0.0 fix — regression check that we didn't break it. |

## 2. Phase 1 / 1.0.x regression checks

| # | Test | Result | Notes |
|---|---|---|---|
| 2.1 | `enableItemLocks=false` → trident usable, no "Requirements:" tooltip | | Existing 0.9.7 fix; verify still working. |
| 2.2 | `dropLockedItems=true` + `enableItemLocks=true` → locked item drops from main hand | | TickEventHandler:26. |
| 2.3 | `maxActivePerks=3` → fourth perk activation rejected, GUI resyncs | | 1.0.0 feature. |
| 2.4 | `disabledPerks=["scholar"]` → Scholar perk effects suppressed | | Logic test. |
| 2.5 | `disabledPerks=["berserker"]` → Berserker rank-up blocked, NBT preserved | | Sanity check. |
| 2.6 | Skill UI hover halo correctly aligned (4px outer glow on each side) | | 1.0.0 fix. |
| 2.7 | Skill UI tooltip not overpainted by adjacent cells | | 1.0.0 fix. |

## 3. 1.1.0 fix verification

| # | Test | Result | Notes |
|---|---|---|---|
| 3.1 | `/globallimit 256` from singleplayer with cheats | | **Comment 3 fix.** Pre-1.1.0 this returned "This command can't be called client side!" Expected: "Updating playersMaxGlobalLevel, new level: 256". |
| 3.2 | `/globallimit 256` from dedicated-server console | | Should still work (rcon path was the only working path before). |
| 3.3 | `/globallimit 256` from a multiplayer op player | | Same regression — should work in 1.1.0. |
| 3.4 | YACL screen: `disabledPerks` and `disabledPassives` appear under `general` group | | **Comment 1a fix.** The string-list controllers should accept registry-path entries. |
| 3.5 | `disabledPerks=["scholar"]` → enchantment names still visible on tooltips | | **Comment 2 fix.** Pre-1.1.0 this hid every enchantment in the world. |
| 3.6 | `enableScholarEnchantmentHiding=true` → hiding does occur | | The 1.1.0 opt-in restores the historical behaviour for packs that want it. |
| 3.7 | Server log no longer spams "Generated N lock items" lines on each `/skillsreload` | | **Comment 1b fix.** Demoted to DEBUG. |
| 3.8 | Charge Mastery on a held-cast (CastType.LONG) ISS spell → +25% damage observed | | New in 1.1.0. Default `chargeMasteryPercent = 25`. |
| 3.9 | Network protocol bump 4 → 5 — old 1.0.x clients hitting a 1.1.0 server fail fast | | `ServerNetworking.java:18`. |

## 4. Optional-mod presence/absence

| # | Combo | Result | Notes |
|---|---|---|---|
| 4.1 | RS + Iron's Spells | | Existing perks still fire. Mana Bulwark damage→mana redirect; Arcane Reprieve cooldown / refill. |
| 4.2 | RS + Ars Nouveau | | Form-filter perks; school perks. |
| 4.3 | RS + Apotheosis + Apothic Attributes | | Socket Virtuoso, Affix Affinity damage cap, stat-stick reconciliation. |
| 4.4 | RS + Apotheosis (gem rarity gating, 1.5.0) | | Socketing a gem requires a Fortune level scaled by gem rarity (uncommon→4, rare→10, epic→18, mythic→26, ancient→32). `apothEnableGemRarityGating=false` removes the requirement. Botania / Blood Magic / Enigmatic Legacy perks and lock providers were removed in 1.5.0 — no longer applicable. |
| 4.5 | RS + ISS + Ars (Schoolbridge) | | Fire Schoolbridge bleeds ISS fire_spell_power into Ars ELEMENTAL_FIRE damage. |
| 4.6 | RS + ISS + Ars + Apotheosis (Triple Threat) | | Tick-reconciled +% modifiers active when all three mods are loaded. |
| 4.7 | RS + ISS + Apotheosis (Affix Focus) | | `ModifySpellLevelEvent` adds spell levels per Rare+ Apothic affix item. |

## 5. Misc

| # | Test | Result | Notes |
|---|---|---|---|
| 5.1 | Existing 1.0.x save loads on 1.1.0 — perks/passives preserved | | Save compatibility: no NBT schema changes. |
| 5.2 | Existing `runicskills.common.json5` loads on 1.1.0 | | The new ConfigHolder strips JSON5 comments and parses with plain Gson. Field names unchanged. |
| 5.3 | `/skillsreload` propagates config / lock-items / perk-groups to all clients | | Existing 0.9.7 / 1.0.0 plumbing. |
| 5.4 | Client login → server-authoritative `enableScholarEnchantmentHiding` overrides local value | | New in 1.1.0; verify the synced bool flips the mixin behaviour. |

## 6. 1.2.x verification

| # | Test | Result | Notes |
|---|---|---|---|
| 6.1 | Single-player idle for 60s after spawn — zero "Runic Skills network protocol mismatch" lines in `latest.log` | | **L1.2 fix verification.** 1.2.0 introduced periodic-probe WARN spam; 1.2.1's `equals` filter didn't match; 1.2.2 uses `startsWith` for `ABSENT` / `ALLOWVANILLA` prefixes. |
| 6.2 | Modpack without Cataclysm — missing-entity title-condition WARNs each fire exactly once at login, never again | | **L2 fix.** Pre-1.2.1 these ERROR'd every 10s × player; 1.2.1 warn-once cache via `ConcurrentHashMap.newKeySet()`. |
| 6.3 | Mod loads with no internet access — single DEBUG line from `getLatestVersion`, no WARN stack trace | | **L3 fix.** Pre-1.2.1 logged full `FileNotFoundException` stack on every boot; 1.2.1 catches FNF/SocketTimeout/UnknownHost specifically. |
| 6.4 | Mod loads without BetterCombat / PointBlank installed — no "@Mixin target ... was not found" lines | | **L4 fix.** Pre-1.2.1 logged two WARN lines per absent target mod; 1.2.1 adds `@Pseudo` to both mixins. The unrelated "Error loading class: ..." lines from Forge's class transformer remain (one per absent mod), and are acceptable informational noise. |
| 6.5 | Apothic Apprentice + Socket Virtuoso both enabled on a rare gem-socketed item — sockets stack additively | | **1.2.0 new perk.** Both contribute to `event.setSockets(event.getSockets() + bonus)` in `ApotheosisIntegration.onGetItemSockets`. |
| 6.6 | Gem-Threaded Armor enabled — F3+H shows armor modifier from `runicskills:gem_threaded_armor` UUID scaling with equipped socket count | | **1.2.0 new perk.** `LivingEquipmentChangeEvent` recomputes total sockets via `SocketHelper.getSockets` and applies ADDITION modifier on `Attributes.ARMOR`. |
| 6.7 | Spellsocket enabled + 6 equipped sockets — `ModifySpellLevelEvent` adds +2 effective spell levels (3 sockets/level cap) | | **1.2.0 new perk.** Capped at `spellsocketMaxBonus` (default 3). |
| 6.8 | Resonant Affixes enabled + 4 Rare-or-better affix items equipped — ISS spell damage shows +12% bonus (3% × 4) | | **1.2.0 new perk.** `SpellDamageEvent` multiplier; mirrors Affix Affinity's iteration pattern. |
| 6.9 | Java mod subscribes to `SkillLevelUpEvent` on Forge bus — event fires; cancellation prevents level-up and XP consumption | | **1.2.0 public Forge event API.** Verify all four events (`SkillLevelUpEvent`, `PassiveLevelUpEvent`, `PerkToggleEvent.Pre`/`Post`, `TitleEarnedEvent`). |
| 6.10 | Tooltip render at GUI scale 4 / 4K — no offscreen tooltip overflow on multi-rank perks (shift-hover for description) | | **1.2.0 tooltip word-wrap.** `TooltipWrap.wrap(list, 200)` clamps lines via `font.split`. |
| 6.11 | Shift-click passive ± button → applies 5 levels; Ctrl-click → 10; Alt-click → max (subject to skill-level cap) | | **1.2.0 bulk-level.** `RunicSkillsScreen.bulkClickAmount` reads `Screen.hasShift/Ctrl/AltDown()`. |
| 6.12 | Skills HUD overlay layer registered as `runicskills:skill_overlay` (and `:title_overlay`) — resource packs can `above`/`below` it via overlay APIs | | **1.2.0 `RegisterGuiOverlaysEvent` migration.** Replaces the prior `CustomizeGuiOverlayEvent.DebugText` piggy-back. |
| 6.13 | Per-integration master toggle (e.g. `enableApotheosisIntegration=false`) — that integration's perks remain in registry but events inert | | **1.2.0 integration toggles.** `RunicSkills.<init>` gates each `tryLoadIntegration` / direct-instantiate path. Synced via `CommonConfigSyncCP`. (Botania toggle removed in 1.5.0 along with the integration.) |

---

## Reporting gaps

If any row in sections 1.1, 1.4, 3.1, 3.5, 3.7, or 3.8 fails, that's a release-blocker —
those are the comment-triage and dedicated-server fixes that this release exists to ship.
Other rows can be flagged in COMMENT_TRIAGE.md or the changelog as known limitations.
