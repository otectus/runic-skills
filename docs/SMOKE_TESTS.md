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

## 7. 2.0.0 verification

The release that changed the protocol, the config authority, the save schema and the whole of the
Powers presentation. Rows 7.1, 7.2, 7.5 and 7.9 are **release-blockers**.

| # | Test | Result | Notes |
|---|---|---|---|
| 7.1 | 1.9.0 client vs 2.0.0 server | | **Blocker.** Protocol 9 vs 10. Expect refusal at connect with a named Runic Skills error, not a hang or a silent desync. |
| 7.2 | A 1.9.0 world loads on 2.0.0 | | **Blocker.** `CapabilitySanitizer` runs as the 1 to 2 migration. Skills, passives, perk ranks and equipped Powers all preserved; one summary line per player in the log, not one per key. Re-load a second time and confirm the migration is idempotent. |
| 7.3 | Hand-edit a saved skill level to `-5` and to `2147483647`, reload | | Both clamped to the stored bounds, reported once, and the player is playable. These are corruption bounds, not balance caps — lowering `skillMaxLevel` must **not** destroy earned progress. |
| 7.4 | Client and server with deliberately different `runicskills.common.json5` | | Every gameplay read resolves to the server's values; the Skills screen shows the server's perk gates and budgets. Disconnect, open singleplayer: the local file applies again. |
| 7.5 | `/skillsreload` after editing a perk's required level | | **Blocker.** The change takes effect without a restart, and the command reports which fields were `LIVE_SERVER` versus `RESTART_REQUIRED`. |
| 7.6 | Ctrl-click a passive `+` with 10 levels affordable | | Applies 10, atomically, in one packet. Pre-2.0.0 the rate limiter discarded all but the first and it silently bought one. `PassiveLevelUpEvent` fires **once**, spanning the whole move. |
| 7.7 | `/skills <player> <skill> add 2147483647` | | Saturates at the configured cap. It used to wrap negative and write a negative level into the save. |
| 7.8 | Install a nickname or chat mod, earn a title | | The title renders as a prefix; the nickname keeps its own text **and its styling**. Nothing writes to the vanilla custom name. A name written by a pre-2.0.0 build is cleared once, on login. |
| 7.9 | Dedicated server, no YACL, no optional mods | | **Blocker.** Boots to "Done". Join, level a skill, toggle a perk, equip a cross-cutting Power — all work with Iron's Spells absent. |
| 7.10 | Magic Resist vs an arrow, a thrown trident, and a mob's magic attack | | Arrows and tridents are **not** resisted; magic is. The reverse of pre-2.0.0 behaviour. Datapack-extend `runicskills:affected_by_magic_resistance` and confirm the addition is honoured. |
| 7.11 | Craft with a locked result in the grid | | No ghost item: the result slot clears in sync, and a shift-click or quick-move is refused authoritatively at `mayPickup`, not just hidden. |
| 7.12 | Repeatedly click a rejected enchantment button | | The price does not walk down toward 1. The discount applies where offers are generated, not where they are spent. |
| 7.13 | The 13 new mixins, with their target blocks | | Stonecutter, hopper, dispenser, minecart, brewing stand, thrown potion, furnace, grindstone, container-click attribution, slot take, player action, and the two Apotheosis menus. Each perk that hangs off one fires, and each block behaves vanilla with the perk off. |

## 8. 2.0.1 Power VFX and accessibility

The matrix from the VFX programme. Run the whole of it before tagging; a proc that misleads a player
about their own build is worse than one they cannot see.

| # | Test | Result | Notes |
|---|---|---|---|
| 8.1 | A damage-amplifier Power procs on a mob 20 blocks away | | The rune draws **on the mob**, not around you. Through 2.0.0 every proc spawned eight enchant particles at the local player whatever had happened. |
| 8.2 | Second player nearby during a proc | | They see it, at the right place. Owner-only procs show them nothing while still showing the owner a HUD card. |
| 8.3 | Grayscale display (or a screenshot desaturated) | | Mark, Seal and Crown are still tellable apart from silhouette and motion alone: broken ring, closed ring, triple ring. This is the criterion colour is not allowed to carry. |
| 8.4 | Deuteranopia / protanopia / tritanopia simulation | | School still identifiable from motion and sound after a look at the legend. |
| 8.5 | `powerVfxQuality = OFF` | | **Zero** runic particles. The HUD card and the sound still play. |
| 8.6 | `powerVfxQuality = REDUCED` | | Same silhouette and motion, roughly 40% of the particles, shorter afterglow. |
| 8.7 | Ten simultaneous proc sources | | Caps hold: at most 64 tracked procs, 128 particles, 8 sounds, 3 HUD cards. Repeats of one Power increment its card counter rather than adding cards. No frame-rate cliff. |
| 8.8 | A chain reaction (Pyroclasm through several corpses) | | Each detonation draws at **its own corpse**, not at the caster. Audio does not stack into clipping. |
| 8.9 | GUI scale 1-4, 720p through ultrawide | | HUD cards stay inside the safe area and clear of the hotbar and status bars; a long translated Power name is clamped, not clipped off both edges. |
| 8.10 | `highContrastRunes = true` | | White glyph on a dark accent. Tier and school stay readable, since neither depends on hue. |
| 8.11 | Disconnect during a proc, then reconnect | | No card survives the world change, no particle leaks, no coalescing key references a dead entity id. |
| 8.12 | Resource reload (F3+T) with cards on screen | | Descriptors and sprites swap without retaining old instances or crashing. |
| 8.13 | Powers panel | | Every Power has its own icon, not the placeholder square. A row pulses when **your** Power fires and not when someone else's does. Denial reasons read as sentences. Tab and arrow keys reach the equip buttons; the narrator reads them. |
| 8.14 | Set the language to `de_de`, hover a skill on the Skills screen | | The level-up tooltip reads as German text. A literal `%s` here is the placeholder-arity bug returning. |
| 8.15 | Scroll a Powers column past its last row, then back | | The list starts moving again on the first scroll back, with no dead zone. |

---

## 9. 2.0.5 KubeJS server progression

| # | Test | Result | Notes |
|---|---|---|---|
| 9.1 | Dedicated server, no KubeJS on the mod list | | Server boots to "Done". KubeJS not present; no bridge to install. |
| 9.2 | Dedicated server, KubeJS 2001.6.5-build.14 installed | | Server boots to "Done". Bridge installs; progression hooks are ready. |
| 9.3 | Dedicated server, KubeJS 2001.6.5-build.26 installed | | Server boots to "Done". Bridge installs; progression hooks are ready. |
| 9.4 | `kubejs/server_scripts/` folder exists, no scripts in it | | `/kubejs reload server_scripts` reports 0 scripts, 0 errors. |
| 9.5 | Script: `event.setCanceled(true)` denies the level-up | | Purchase fails. Level does not rise. XP not charged. GUI resync shows pre-attempt level. |
| 9.6 | Script: `event.setCancelled(true)` denies the level-up (British spelling) | | Same as 9.5. |
| 9.7 | Script: `event.cancel()` denies the level-up | | Same as 9.5. |
| 9.8 | Script: `event.deny('your reason here')` denies with a message | | Same as 9.5. Player sees "your reason here" in chat, once, on the server post only. |
| 9.9 | Denied purchase: player has 100 XP, denies at level-up cost of 50, check balance | | Still has 100 XP. (Denial is before charge.) |
| 9.10 | Allowed purchase: player has 100 XP, costs 50 to level, check balance | | Now has 50 XP. Level is +1. Passives/perks/titles reconciled. |
| 9.11 | GUI purchase from Skills screen | | Fires server event once with `cause == 'purchase'`. |
| 9.12 | `/skills <player> <skill> add 1` increases by 1 | | Fires server event once with `cause == 'command'`. |
| 9.13 | `/skills <player> <skill> set 2` then set 12 (jump from 2→12) | | Fires one server event with `oldLevel == 2, newLevel == 12`. |
| 9.14 | `/skills <player> <skill> subtract 1` decreases (no server post for decreases) | | Forge `SkillLevelUpEvent` fires (it fires for both directions). KubeJS server post does not fire (only for increases). GUI does not fire client post. |
| 9.15 | Script: `if (event.cause !== 'purchase') return;` then deny | | `/skills` commands obey the gate. Deny only affects player-initiated purchases. |
| 9.16 | Script: deny without the cause check | | `/skills` commands also trigger the gate. (Ops can lock themselves out if they are not careful.) |
| 9.17 | `event.hasAdvancement('minecraft:adventure/kill_a_mob')` returns true for a player who has it | | Advancement helper works. |
| 9.18 | `event.hasAdvancement('minecraft:adventure/kill_a_mob')` returns false for a player who does not have it | | Advancement helper works. |
| 9.19 | `event.hasAdvancement('invalid:id')` malformed | | No crash. Returns false. One DEBUG line in `logs/kubejs/server.log` naming the bad id. |
| 9.20 | `/kubejs reload server_scripts` with valid script in the folder | | Expect 0 errors. Script loads. |
| 9.21 | `/probejs dump` lists the event | | Event appears in both SERVER and CLIENT contexts in the generated stubs. |
| 9.22 | Multiplayer: client without KubeJS, server with it | | Client joins. Can level skills. Gate is enforced server-side (client cannot see or bypass it). Record observed behaviour. |

---

## Reporting gaps

Release-blockers: rows 1.1, 1.4, 3.1, 3.5, 3.7, 3.8 (the historical dedicated-server and
comment-triage fixes) and rows 7.1, 7.2, 7.5, 7.9 (the 2.0.0 protocol, migration, reload and
dedicated-server rows). A failure in any of them stops the release.

**For 2.0.1 specifically**, add a protocol row to §7: a **2.0.0** client against a 2.0.1 server must
be refused at connect. 2.0.1 changed the proc packet's shape and the config schema hash inside
protocol 10, so it bumped to 11 — if that refusal does not happen, the two will agree on a version
and then misread each other, which is the exact failure the number exists to prevent.

Anything else that fails goes in the changelog as a known limitation, in the release it ships in.
Earlier revisions routed failures to `COMMENT_TRIAGE.md`; that file stopped being maintained after
1.3.7 and now lives in [`history/`](history/), so it is not a destination for new findings.

**Sections 1 through 6 have never had their Result cells filled in.** They are kept because the
regressions they cover are real, but an empty cell means "not run", not "passed".
