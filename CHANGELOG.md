# Changelog

## [1.2.2] - 2026-05-14

Second log-noise hotfix. The 1.2.1 fix for `ServerNetworking.acceptsVersion` used `NetworkRegistry.ABSENT.equals(peerVersion)` but the predicate's WARN still fired every ~5 seconds in single-player. Either Forge 47.3.0's `NetworkRegistry.ABSENT` constant value drifts from the open-source value (likely the trailing 🤔 emoji suffix differing across charset configurations), or the predicate receives a wrapped/processed peerVersion that doesn't exactly equal the constant. Switched to defensive prefix matching (`startsWith("ABSENT")` / `startsWith("ALLOWVANILLA")`) — the textual prefix is invariant across Forge versions even when the emoji suffix isn't.

Also refreshes the README to point at the latest jar, document the public Forge event API as the canonical 1.2.0+ scripting hook, and append the four new Apotheosis perks to the integrations table. Extends `docs/SMOKE_TESTS.md` with a new "1.2.x verification" section (13 test rows) covering all 1.2.x changes: new perks, event API, tooltip wrap, bulk-level, HUD overlay layers, integration master toggles, and the log-noise fixes.

### Fixed
- **`ServerNetworking.acceptsVersion` WARN spam still present after 1.2.1.** Replaced `NetworkRegistry.ABSENT.equals(...)` / `ACCEPTVANILLA.equals(...)` with `peerVersion.startsWith("ABSENT")` / `startsWith("ALLOWVANILLA")`. Defensive against the constant-value drift that prevented the 1.2.1 fix from matching at runtime — confirmed by retesting `latest.log` after the 1.2.1 install, which still showed the WARN firing every 5 seconds with `peer reports ABSENT ?`.

### Changed
- `README.md` installation jar reference 1.1.0 → 1.2.2; appended a milestone sentence describing 1.2.x feature highlights (Forge event API, four new Apotheosis perks, eight integration toggles, tooltip word-wrap, named-layer HUD overlays, bulk-level passives) and a brief mention of the 1.2.1 + 1.2.2 hotfixes.
- `README.md` KubeJS scripting section extended to reference the public Forge event API (`SkillLevelUpEvent`, `PassiveLevelUpEvent`, `PerkToggleEvent.Pre`/`Post`, `TitleEarnedEvent`) as the canonical 1.2.0+ hook, with a pointer to `docs/API_EVENTS.md`. Legacy `SKILL_LEVELUP` reflection bridge documented as deprecated.
- `README.md` Apotheosis integration row updated with the four 1.2.0-shipped perks (Apothic Apprentice, Gem-Threaded Armor, Spellsocket, Resonant Affixes).
- `docs/SMOKE_TESTS.md`: new "Section 6 — 1.2.x verification" with 13 test rows covering every 1.2.x change.

### Notes
- Save-compatible with 1.2.x and 1.1.x. No NBT, no `PROTOCOL_VERSION`, no config, no perk behavior changes — pure log noise + documentation.
- Tested: `./gradlew build` passes; on the user's CurseForge instance (no Cataclysm, no BetterCombat, no PointBlank installed) the 1.2.2 install over the 1.2.1 install should show **zero Runic Skills WARN/ERROR lines** in a 60s post-spawn idle window.

## [1.2.1] - 2026-05-14

Log-noise hotfix. Four issues surfaced by the 1.2.0 post-release smoke test on a real CurseForge instance (`latest.log` review across the 1.2.0 session and four 1.1.0 archives for baseline comparison). Zero functional changes — no NBT, no `PROTOCOL_VERSION`, no perk behavior. Drop-in over 1.2.0.

### Fixed

- **`ServerNetworking.acceptsVersion` log spam in single-player.** The 1.2.0 protocol-mismatch WARN fired every ~5 seconds against Forge's periodic channel-acceptance probes (which pass `NetworkRegistry.ABSENT` and `NetworkRegistry.ACCEPTVANILLA` sentinel strings on LAN advertising / ping handlers). Now filters those two sentinels out before logging, so the WARN only fires on a real peer reporting a mismatched version string — which is the case server operators actually want to see. Regression introduced in 1.2.0 Phase C4.
- **`EntityKilledCondition` / `EntityKilledByCondition` ERROR-spam on every title-tick.** The title-check tick handler (`TickEventHandler.onPlayerTickLow`, every 200 ticks = 10s) evaluates each title's conditions for each online player. Default title configs reference Cataclysm and Ice and Fire entities; modpacks without those mods saw 2-12 ERROR lines per minute steady state per player. Now: warn-once per unique missing entity name per JVM session via a `ConcurrentHashMap.newKeySet()` guard, demoted from ERROR to WARN with clearer wording (`"Title condition references unknown entity '<id>'. The title will never unlock until that entity's mod is installed."`). Pre-existing bug, finally addressed.
- **`getLatestVersion` 404 logged a full stack trace as WARN** at every mod-load. The catch-all `Exception` handler printed the full `FileNotFoundException` for the routine "VERSION file not yet published on GitHub" case. Now catches `FileNotFoundException`, `SocketTimeoutException`, and `UnknownHostException` separately and demotes to a single DEBUG line; the generic catch-all retains WARN+stack for genuinely unexpected failures. Pre-existing.
- **`MixTargetFinder` and `MixGunItem` Mixin-target-not-found WARN** at startup when BetterCombat / PointBlank are absent. The mixins correctly compile inert (no transformations are applied), but Mixin's class loader emits a WARN when it can't find the target class. Added `@Pseudo` to both — Mixin now silently skips the target-load attempt for these inherently optional-mod-targeting mixins. Pre-existing.

### Notes
- Save-compatible with 1.2.0; no schema, protocol, or config changes.
- Tested: `./gradlew build` passes. Runtime smoke against the user's local CurseForge instance (no Cataclysm, no BetterCombat, no PointBlank installed) should show zero spurious WARN/ERROR lines from Runic Skills over a 60s idle window after spawn — a sharp contrast from the 14+ WARN and 30+ ERROR lines in the 1.2.0 `latest.log` over the same window.

## [1.2.0] - 2026-05-14

Balanced content + quality release. Ships a public Forge event API (`SkillLevelUpEvent`, `PassiveLevelUpEvent`, `PerkToggleEvent.Pre`/`Post`, `TitleEarnedEvent`) — external Java mods and KubeJS can now hook level-ups and perk toggles without reflection. Adds 8 per-integration master toggles so pack authors can soft-disable any major integration without removing the dep mod. Adds 4 deferred-backlog perks (Apothic Apprentice, Gem-Threaded Armor, Spellsocket, Resonant Affixes). Tooltip width-clamp at GUI scale 4. HUD overlays moved to named layers so resource packs can relocate them. Bulk-level passives via Shift/Ctrl/Alt-click. Translated protocol-mismatch log line for server ops. CI workflow added. Save-compatible with 1.1.0.

### Added — Public Forge event API (since 1.2.0)

- **`SkillLevelUpEvent`** — fired on the Forge bus from `SkillLevelUpSP.handle` after validation succeeds, before capability mutation and the client sync packet. `@Cancelable`; cancelling aborts the level-up cleanly without consuming XP. Extends `PlayerEvent`. Fields: `Skill skill`, `int oldLevel`, `int newLevel`.
- **`PassiveLevelUpEvent`** — fired from both `PassiveLevelUpSP.handle` and `PassiveLevelDownSP.handle`. Subscribers wanting only the level-up direction should filter `newLevel > oldLevel`. `@Cancelable`. Fields: `Passive passive`, `int oldLevel`, `int newLevel`.
- **`PerkToggleEvent.Pre` / `.Post`** — fired around `TogglePerkSP.handle`. `Pre` fires after the built-in validation chain succeeds, before any state mutation; cancelling aborts the toggle and resyncs the client. `Post` fires after rank/cooldown writes and is non-cancelable. Both have `int oldRank`, `int newRank`, `boolean wasEnabled`, `boolean isEnabled`.
- **`TitleEarnedEvent`** — fired from `Title.setRequirement` whenever `unlockTitle` flips false→true. Non-cancelable. Fields: `Title title`.

All four events are documented as a public API and will be maintained across minor versions. KubeJS hooks them natively via its Forge-event bridge — `onForgeEvent("net.minecraftforge.event.entity.player.PlayerEvent$SkillLevelUpEvent", event => ...)` works out of the box. Legacy KubeJS scripts using the older `SkillLevelUpEventJS` surface still work unchanged through the `KubeJSIntegration` shim (which is now marked `@Deprecated(forRemoval = true)`; removal is scheduled for a future major).

### Added — Perks (4)

Four perks from the 1.1.0 "Skipped" backlog land this release. The remaining five and the eleven Phase-3 capstones are either dropped permanently (most lack a public API in their upstream mod) or deferred to 1.3.0+ (see below).

- **Apothic Apprentice** (Fortune, Apotheosis) — higher-tier socket bonus. `+N` effective sockets on top of Socket Virtuoso's bonus. Trivial counterpart to the existing perk; both stack additively when enabled. Default `requiredLevel = 26`, bonus = 2 sockets.
- **Gem-Threaded Armor** (Endurance, Apotheosis) — flat ARMOR per equipped socket. Hook `LivingEquipmentChangeEvent`; iterate `dev.shadowsoffire.apotheosis.adventure.affix.socket.SocketHelper.getSockets` across all equipment slots; apply a transient ADDITION modifier on `Attributes.ARMOR` keyed by a fixed UUID. Reconciles on equipment change, not per tick. Default `requiredLevel = 20`, +0.5 armor per socket.
- **Spellsocket** (Magic, Apotheosis + ISS) — `ModifySpellLevelEvent` adds `+1` effective spell level per `socketsPerLevel` equipped sockets, capped at `maxBonus`. Same iteration pattern as Affix Focus. Default `requiredLevel = 22`, 3 sockets per +1 level, max +3.
- **Resonant Affixes** (Magic, Apotheosis + ISS) — ISS `SpellDamageEvent` multiplies outgoing spell damage by `1 + (rare+ affix count × percent)`. Mirrors Affix Affinity's iteration pattern but on spell damage rather than melee. Default `requiredLevel = 24`, +3% per Rare-or-better item.

All four config keys live in `HandlerCommonConfig` under the `apothic_attributes` group with corresponding `*RequiredLevel` and magnitude knobs. Lang keys added to `en_us.json`; other locales fall back to English.

### Added — Per-integration master toggles (8 booleans)

New `enable<Mod>Integration` booleans in `HandlerCommonConfig` under the `integrations` group, default `true`. When false, the integration class is **never registered** with the Forge event bus — every event handler in the integration is inert. Perks belonging to the integration remain in the registry (so save data is stable across toggle flips). Synced through `CommonConfigSyncCP` so the client can render UI honestly. The existing lock-item toggles (`*EnableLockItems`) remain a finer-grained subset that gates only the lock-item generation.

Coverage: Spartan Weaponry, Blood Magic, Ice and Fire, Iron's Spells, Ars Nouveau, Apotheosis, Botania, Jewelcraft.

### Added — Tooltip word-wrap helper

`TooltipWrap.wrap(List<Component>, int maxWidthPx)` clamps long perk/passive tooltip lines via `Minecraft.getInstance().font.split(...)`. Applied at the end of `PerkTooltip.tooltip()` and `PassiveTooltip.tooltip()` with a 200px clamp — eliminates offscreen tooltip overflow at GUI scale 4 / 4K. Lines that already fit pass through unchanged so translation keys are preserved in the common case.

### Added — Bulk-level passives

Shift-click a passive ± button to apply ±5; Ctrl-click ±10; Alt-click clears (or maxes) the passive subject to skill-level gates. Each click sends N `PassiveLevelUpSP` / `PassiveLevelDownSP` packets; the server validates each independently and silently rejects increments past the cap. Skill level-up still uses single-click (server rate-limit on `SkillLevelUpSP` makes bulk-level less useful there). Implemented in `RunicSkillsScreen.bulkClickAmount`.

### Added — CI workflow

`.github/workflows/build.yml` runs `./gradlew build` on push and pull request — compiles, runs `checkSidedImports`, reobfuscates, and assembles the jar on a clean Ubuntu image. Catches the dedicated-server class-load regression class on a clean classpath (no YACL, no L2Tabs, no optional mods); 1.0.0 (Legendary Tabs) and 1.1.0 (YACL) shipped that bug separately, so a no-optional-mods CI build is the highest-value smoke gate. Build artifact (jar) uploaded for 7 days.

### Changed

- **HUD overlays migrated to `RegisterGuiOverlaysEvent`.** `OverlaySkillGui` and `OverlayTitleGui` previously piggy-backed on `CustomizeGuiOverlayEvent.DebugText` (the F3 overlay event — worked but conceptually wrong). Now registered as named layers `runicskills:skill_overlay` and `runicskills:title_overlay` above the hotbar layer. Same render code, same visual position; resource packs can now relocate them via the standard `above`/`below` overlay APIs. Tick subscribers remain on the Forge bus for state updates.
- **`KubeJSIntegration` deprecated** in favor of the new Forge events. `postLevelUpEvent` is now `@Deprecated(forRemoval = true)` — its reflective fast-path cache stays for back-compat with existing pack scripts; new scripts should subscribe to `SkillLevelUpEvent` on the Forge bus directly. Migration documented in [docs/API_EVENTS.md](docs/API_EVENTS.md).
- **`@Nullable` annotations** on `RegistryPerks.getPerk`, `RegistryPassives.getPassive`, `RegistrySkills.getSkill` (returns null for unknown registry names). IntelliJ + IDEA plugin now surfaces unchecked dereferences as warnings — closes the residual P1 #5 leads from the 0.9.3 audit.
- **Protocol-mismatch log line** (1.2.0). `ServerNetworking.acceptsVersion` now logs a clear warning when a connecting peer reports a different `PROTOCOL_VERSION` — server ops can diagnose mismatches from the log instead of debugging a generic Forge disconnect. The player-facing disconnect message remains Forge's generic "channel mismatch" because the kick is initiated by Forge's negotiation layer; full message translation would require an invasive negotiation-layer mixin not worth the risk.

### Fixed

- (No new bug fixes this release — 1.1.0 was the catch-up consolidation.)

### Removed from roadmap (won't ship)

After triaging each deferred design-doc perk against the actual upstream API surface, the following are dropped permanently because they require invasive mixins into private dispatch paths or assume APIs that don't exist:

- **Pack Caller** — ISS `SummonManager` is private and event-less.
- **Eldritch Apprentice** — Eldritch research XP lives in a private capability with no extraction hook.
- **Spawner Mage** / **Spawner Sanctuary** — ISS summons ≠ vanilla `SpawnerBlockEntity`; the perks conflate two mechanics. Apotheosis has no spawner event either.
- **Split-Caster** — ISS 3.15 has no spell-casting-split event; pervasive mixin into spell dispatch is too invasive for the value.
- **Glyph-Imbued Gem** — gem metadata is immutable post-socketing; no post-apply hook.
- **Enchanter's Insight** — design assumes Ars uses enchantments (it uses glyphs); conceptual mismatch.
- **Enchanter-Arms** — no Apotheosis event for enchantment-apply success.
- **Apparatus Synergy** — Apotheosis Loot Apparatus is datapack-only; no hook.
- **Mythical Scribe** — Ars has no glyph-discovery XP event.

### Deferred to 1.3.0+

Doable but blocked on implementation budget or scoped to wait until the new 1.2.0 event API has bedded in:

- **Sourcelink Affix** — Ars `SpellResolveEvent.Post` + affix-name match for source refund. Drops to lower priority because it depends on Apotheosis affix-name stability.
- **Adaptive Caster** — per-player school-cast history map with decay; signal-state pattern needs more design.
- **Ars Familiar Attunement** — `EntityJoinLevelEvent` filtered by `IFamiliar`. Needs verification against Ars 4.12.x familiar-spawn paths.
- **Botania Mana Overflow** — `TickEvent.PlayerTickEvent` + nearby-pool drain on mana-cap. Counterpart to Tidewoven/Resonance.
- **ISS Cascade Attunement** — N-casts-of-X-unlock-Y-for-30s transient state map.
- **Datapack-driven titles** — Forge's `IForgeRegistry<Title>` is frozen after `RegistryEvent`; full datapack support requires a parallel runtime title store with its own evaluation/sync plumbing. The existing `HandlerTitlesConfig.titleList` YACL config already lets pack authors author titles, just without vanilla `/reload` hot-swap. Targeted for 1.3.0.
- **Gemsmith**, **Lucky Loot**, **Library Dedication**, **Ars Scholar**, **Glyphsmith**, **Bookwyrm's Apprentice** — all require reflection into private upstream state; group into a future "Ars / Apotheosis deep-integration" pass.
- **Ritualist**, **Dead King's Debt**, **Ritualized Reforge**, **Arcane Syncretism** — multi-event coordination across mods; benefit from the 1.2.0 event API once external mods start adopting it.
- **GameTest scaffolding** — initial gradle wiring and test-class scaffold are designed but the structure-file setup and per-class loader plumbing want a focused release. CI build of the main jar lands now; full GameTest run in CI lands later.

### Notes

- **Save-compatible with 1.1.0.** No NBT schema changes; the four new perk ranks default to 0 on first load. The 8 new config booleans default to `true` (preserve existing behavior). `PROTOCOL_VERSION` stays at `5` — no wire-format breaking changes. The 1.1.0→1.2.0 upgrade is drop-in.
- **Tested:** `./gradlew clean build` passes (compileJava + `checkSidedImports` + reobf + jar assembly). Runtime smoke testing per `docs/SMOKE_TESTS.md` is required before CurseForge upload — primary regression rows: client tooltip render at GUI scale 4 (no overflow), Apothic Apprentice + Socket Virtuoso stacking on a rare gem-socketed item, Spellsocket bonus visible in F3 spell-level overlay, Shift-click bulk-level a passive from 0 to 5 in one click.

## [1.1.0] - 2026-05-08

Consolidated post-1.0.0 release. Bundles the Botania integration (originally tagged 1.0.1), the magic-tree cross-mod expansion (originally tagged 1.0.2), the dedicated-server YACL safety refactor that the README has been advertising since 1.0.1 was actually fixed, the L2Tabs class-load fix (same shape as the 1.0.0 Legendary Tabs fix, missed at the time), and triage of three CurseForge user reports. Total of 120 new perks across five mod integrations plus a meaningful capability change — the mod now actually runs on dedicated servers without YACL installed.

### Added — Botania (42 perks; 18 with effects, 24 default-disabled pending future implementation)

- **Optional integration**, class-load-isolated. `BOTANIA_*` perks register only when `botania` is loaded and the perk's `required_level >= 0`; otherwise the `RegistryObject<Perk>` is `null` and every event path short-circuits via the existing `RegistryPerks.X != null` idiom. Botania API confined to two files:
  - `integration/BotaniaCompat.java` — wraps `vazkii.botania.api.BotaniaForgeCapabilities`, `ManaItemHandler`, `ManaReceiver`, `ManaPool`. Offers `drainNearbyPool` / `hasNearbyPoolMana` / `drainPlayerMana` / `chargePlayerMana` / `getPlayerManaTotal`. Scans bounded to a 12×6×12 AABB.
  - `integration/BotaniaIntegration.java` — Forge-bus event subscriber, registered via `RunicSkills.tryLoadIntegration("botania", ...)`. Hooks `ManaProficiencyEvent`, `ManaDiscountEvent`, `ManaItemsEvent` and Forge `TickEvent.PlayerTickEvent`, `LivingHurtEvent`, `CriticalHitEvent`, `LivingDeathEvent`, `BlockEvent.BreakEvent`, `LivingEntityUseItemEvent.Finish`.
- **Tier layout** mirrors Botania's rune / season / sin / Gaia progression:
  - **Wisdom low-tier** — Petal-Reader, Rune of Mana: Resonance, Sparkle-Sense, Dowser's Twig, Green Thumb, Livingbark Student.
  - **Wisdom mid-tier (Seasonal)** — Spring: Agricultor's Eye, Summer: Forager's Palate, Autumn: Loot-Hunter's Intuition, Winter: Still Listener, Manaseer's Lens, Corporea Query.
  - **Wisdom high-tier (Sin / Gaia / Elven)** — Greed: Cartographer-Prospector, Pride: Far Reach, Sloth: Lazy Swap, Envy: Mirror's Read, Elven Knowledge, Gaia's Witness, Oracle of the Nine Runes.
  - **Magic low-tier (Rune)** — Inner Wellspring, Rune of Water: Tidewoven, Rune of Fire: Emberheart, Rune of Earth: Stone-Rooted, Rune of Air: Featherstep, Band of Aura: Passive Channel.
  - **Magic mid-tier (Seasonal / Lens)** — Spring: Verdant Pulse, Summer: Solar Conduit, Autumn: Harvest Tithe, Winter: Frostbound, Lens Mastery: Velocity, Lens Mastery: Potency.
  - **Magic high-tier (Sin / Gaia / relic)** — Lust: Pixie Affinity, Gluttony: Cake Combustion, Greed: Magnetite, Sloth: Unbound Step, Envy: Mirrored Wrath, Pride: Crown of Reach, Wrath: Thundercall, Gaia's Gift: Relic Attunement, Terrasteel Ascension, Flügel's Grace, Manastorm.
- **Full effect implementations (18 perks):** Tidewoven, Resonance, Inner Wellspring, Mirrored Wrath, Emberheart, Solar Conduit, Featherstep, Frostbound, Thundercall, Harvest Tithe, Green Thumb, Livingbark Student, Cake Combustion, Stone-Rooted, Terrasteel Ascension, Crown of Reach, Far Reach, Magnetite. Attribute modifiers piggy-back on the existing `RegistryAttributes.RegisterAttribute` helper; reach bonuses use Forge's `ENTITY_REACH` and `BLOCK_REACH` attributes.
- **Default-disabled (24 perks)** — every keybind-activated, custom-render, custom-capability, and projectile/physics perk has its `*RequiredLevel` defaulted to `-1`, eliding the perk from the registry until a future implementation pass ships its handler. Pack authors who want them visible (for cosmetic display or a future patch) can set the level back to a positive value: Petal-Reader, Sparkle-Sense, Dowser's Twig, Spring: Agricultor's Eye, Summer: Forager's Palate, Autumn: Loot-Hunter's Intuition, Winter: Still Listener, Manaseer's Lens, Corporea Query, Greed: Cartographer-Prospector, Sloth: Lazy Swap, Envy: Mirror's Read, Elven Knowledge, Gaia's Witness, Oracle of the Nine Runes, Band of Aura: Passive Channel, Spring: Verdant Pulse, Lens Velocity, Lens Potency, Lust: Pixie Affinity, Sloth: Unbound Step, Gaia's Gift: Relic Attunement, Flügel's Grace, Manastorm.
- **Perk icons** reuse Botania's own 16×16 item textures via the `botania:` namespace. Nothing redistributed; paths resolve at render time from Botania's assets.
- Build: `maven.blamejared.com` added to repositories, `compileOnly fg.deobf("vazkii.botania:Botania:1.20.1-451-FORGE:api")` added to dependencies. No runtime jar shipped.
- mods.toml: optional `botania` dependency, `mandatory = false`, `ordering = "AFTER"`, `versionRange = "[1.20.1-441,)"`.

### Added — Iron's Spells 'n Spellbooks (46 perks)

**Phase 1a — generic mana & casting (16 perks):** Wellspring, Quickening, Reservoir, Tempo, Arcane Recovery, Focus, Mana Bulwark, Arcane Reprieve, Mana Surge, Spellweaver, Resonant Casting, Imbued Focus, Quickcast, Long Channel, Continuous Flow, Charge Mastery. Five are reconciled as permanent `irons_spellbooks:*` attribute modifiers on a 10-tick throttle (Wellspring → `max_mana`, Quickening → `cast_time_reduction`, Reservoir → `mana_regen`, Tempo → `cooldown_reduction`, Mana Surge transient on `spell_power`/`mana_regen` while HP ≤ threshold). The remainder hook ISS events: `LivingDeathEvent` (Arcane Recovery), `LivingAttackEvent` on `CastType.LONG` (Focus), `LivingHurtEvent` (Mana Bulwark redirects damage→mana at 2:1), `ChangeManaEvent` (Arcane Reprieve auto-refill with 120s cooldown, Continuous Flow per-tick drain reduction on `CastType.CONTINUOUS`), `SpellOnCastEvent` (Spellweaver every-Nth-cast free, 10s combo window), `SpellDamageEvent` (Resonant Casting above-95%-mana damage bonus, Long Channel `CastType.LONG` bonus, Charge Mastery `CastType.LONG` bonus), `ModifySpellLevelEvent` (Imbued Focus +1 level), and `SpellCooldownAddedEvent.Pre` (Quickcast `CastType.INSTANT`-filtered cooldown reduction).

Charge Mastery: ISS 3.x has no `CastType.CHARGE` (only `NONE/INSTANT/LONG/CONTINUOUS`), so the perk treats `CastType.LONG` as the held-cast equivalent and applies a configurable `chargeMasteryPercent` damage bonus (default `+25%`). Stacks multiplicatively with Long Channel.

**Phase 1b — school specialist triplets (28 perks):** for each of nine ISS schools (Fire, Ice, Lightning, Holy, Ender, Blood, Evocation, Nature, Eldritch) a three-perk triad plus the previously-missing Eldritch Attunement gate. X-mancer grants `+%` to the school's `<school>_spell_power` (Magic tree). X-Warded grants `+%` to `<school>_magic_resist` (Endurance tree). X-Catalyst is a 1-in-N-chance on-cast signature-effect proc — lightning/holy/ender/evocation/nature/eldritch apply `CHARGED`/`FORTIFY`/`PLANAR_SIGHT`/`ECHOING_STRIKES`/`OAKSKIN`/`ABYSSAL_SHROUD` to the caster; fire/ice/blood apply `IMMOLATE`/`CHILLED`/`REND` to the victim.

**Phase 1c — summon/utility (2 perks):** Lord of the Dead (caster gains `+%` `summon_damage`; newly-spawned `IMagicSummon` entities owned by the player get `+%` `MULTIPLY_BASE` on `MAX_HEALTH` via `EntityJoinLevelEvent`). Life Leech Bound (when a player-owned summon damages a target, `%` of damage returns as mana via `MagicData.addMana`).

### Added — Apotheosis & Apothic Attributes (12 perks)

Socket Virtuoso (`+N` effective sockets via `GetItemSocketsEvent`). Affix Affinity (counts Rare-or-better equipped affixes via `AffixHelper.getRarity()`, multiplies `ATTACK_DAMAGE` and caps damage reduction at `1 - count × reduction` on `LivingHurtEvent`). Ten stat-stick perks reconciled on a 10-tick throttle against `ALObjects.Attributes.*`: Apothic Critical Mastery (`CRIT_CHANCE` + `CRIT_DAMAGE`), Vampiric Fangs (`LIFE_STEAL`), Reaper's Edge (`CURRENT_HP_DAMAGE`), Evasive (`DODGE_CHANCE`), Arrow Mastery (`ARROW_DAMAGE` + `ARROW_VELOCITY`), Earthbreaker (`MINING_SPEED`), Apothic Scholar (`EXPERIENCE_GAINED`), Spectral Ward (`PROT_PIERCE` flat + `PROT_SHRED` percent), Ghostbound (`GHOST_HEALTH`), Heart of the Healer (`HEALING_RECEIVED` + `OVERHEAL`). Two rename clashes resolved against existing Fortune-tree `CRITICAL_MASTERY` and Intelligence-tree `SCHOLAR` by prefixing `apothic_`.

### Added — Ars Nouveau (11 perks)

**Form & utility (4 perks):** Form Focus filters by `Spell.getCastMethod()` identity on the `MethodProjectile`/`MethodTouch`/`MethodSelf` singletons — Projectile and Self reduce mana cost via `SpellCostCalcEvent`, Touch boosts outgoing damage via `SpellDamageEvent.Pre`. Wild Manipulation reduces cost for any spell whose recipe contains a `SpellSchools.MANIPULATION`-tagged glyph (via a shared `spellContainsSchool` helper).

**Per-school (7 perks):** Hedgewitch (Water cost + damage), Emberforged (Fire damage), Stormcaller (Air damage), Geomancer (Earth damage), Conjurer (Conjuration cost), Abjurer (Abjuration damage/magnitude), Arcane Weaver (Manipulation damage). All cost reductions floor at 1 Source. The doc's Necromant variant was dropped — Ars Nouveau 4.12.x `SpellSchools` has no NECROMANCY enum (only Abjuration/Conjuration/Manipulation and the four elementals).

### Added — Cross-mod synergy (9 perks)

Six **Schoolbridges** (require ISS + Ars): each reads the caster's ISS `<school>_spell_power` attribute and bleeds a configurable fraction into outgoing Ars damage of the matched school — Fire→ELEMENTAL_FIRE, Ice→ELEMENTAL_WATER, Lightning→ELEMENTAL_AIR, Nature→ELEMENTAL_EARTH, Holy→ABJURATION, Ender→MANIPULATION. **Unified Arcana** (ISS + Ars): refunds a percent of Ars cast cost to the caster's ISS mana pool via `SpellResolveEvent.Post`. **Triple Threat** (ISS + Ars + Apotheosis): tick-reconciled `+%` modifiers on `max_mana`/`mana_regen`/`spell_power`, only active when all three mods are loaded. **Affix Focus** (ISS + Apotheosis): on `ModifySpellLevelEvent`, grants `+N` effective spell levels when the player has `N` or more Rare-or-better Apothic affix items equipped. Every cross-mod perk is null-registered when any required mod is missing — perks disappear from the tree rather than rendering as inert icons.

### Added — Comment-triage configs

- **`enableScholarEnchantmentHiding`** (`HandlerCommonConfig`, default `false`). Decouples the Scholar perk from the global enchantment-hiding mixin — see Fixed below. Mirrored through `CommonConfigSyncCP`.
- **`chargeMasteryPercent`** (`HandlerCommonConfig`, default `25`, range `1–200`). Damage-bonus percent on `CastType.LONG` ISS spells when Charge Mastery is enabled.
- **`docs/SMOKE_TESTS.md`** — runtime regression checklist, populated for the 1.1.0 fixes.
- **`COMMENT_TRIAGE.md`** at repo root — public-facing per-comment ledger mapping each CurseForge report to its fix or "needs more info" status.

### Fixed
- **Dedicated server crashed on boot when YACL was absent.** `mods.toml` had YACL declared `mandatory=true, side="CLIENT"` so the FML manifest check passed, but `RunicSkills.<init>` unconditionally called `Configuration.Init()`, triggering the static initializer of every `Handler*Config` class — each of which built a `ConfigClassHandler` from `dev.isxander.yacl3.config.v2.api.*`. With YACL declared `runtimeOnly` and absent from the server's `mods/` folder, the JVM threw `NoClassDefFoundError: dev/isxander/yacl3/config/v2/api/ConfigClassHandler` mid-construction. README has been advertising "YACL is **not** required server-side" since 1.0.1 — the code now matches. Fixed by introducing `com.otectus.runicskills.config.storage.ConfigHolder<T>`, a server-safe wrapper that mirrors the previous `ConfigClassHandler` API surface (`.instance()`, `.load()`, `.save()`, `.generateGui()`) without referencing any YACL type in its bytecode. Persistence uses plain Gson against the existing `runicskills.*.json5` files, with a JSON5-comment-stripper for backward compat with YACL-written files. The YACL UI moves to `client/config/YaclConfigUiBuilder` (loaded only when the user opens the in-game config screen, reached via reflection from `ConfigHolder`). `mods.toml` flips to `mandatory=false`.
- **Client crashed at mod construction when L2Tabs was absent.** Identical shape to the 1.0.0 Legendary Tabs crash, missed at the time: `RunicSkillsClient$ClientProxy` is a `@EventBusSubscriber` class loaded by Forge via `Class.forName(..., true, loader)` at mod construction. The `clientSetup` method contained an inline lambda `() -> TabRegistry.registerTab(3500, TabRunicSkills::new, ...)` — the verifier eager-resolved `TabRunicSkills`'s `BaseTab` superclass, which fails when L2Tabs is absent even though the `if (isModLoaded())` guard means the lambda would never run. Fixed by extracting registration into `client/integration/L2TabsClientIntegration.registerTab()` and passing it as a method reference, mirroring the Legendary Tabs treatment.
- **`/globallimit <n>` rejected every in-game invocation as "client-side"** (CurseForge report). The `execute` method had a backwards guard: `if (source.getEntity() instanceof Player) { fail }`. In singleplayer the integrated server's command source IS the local `ServerPlayer`, and on dedicated servers any op invoking the command also runs as a `ServerPlayer`, so the guard rejected every legitimate path; only rcon/console worked. Brigadier commands registered via `RegisterCommandsEvent` already only run on the logical server, and `requires(s -> s.hasPermission(2))` already gates op-only access. Guard removed.
- **`disabledPerks` and `disabledPassives` were invisible in the YACL config UI** (CurseForge report — "how do you disable certain perks?"). Both fields had `@SerialEntry` and `@ListGroup` annotations but no `@AutoGen`, so they persisted to disk but never appeared in the in-game config screen. Added `@AutoGen(category="common", group="general")` to both.
- **Scholar perk → enchantment-hiding side effect** (CurseForge report — "is there a way to disable hiding the enchantments?"). The `MixItemStack.appendEnchantmentNames` mixin keyed off `RegistryPerks.SCHOLAR.isEnabled()` to decide whether to globally hide every enchantment name on every item. When a user added `"scholar"` to `disabledPerks` (the natural way to "turn off" the perk), `isEnabled()` returned `false`, so the mixin took the hide branch — globally erasing enchantment text from every tooltip in the world. Decoupled: a new `enableScholarEnchantmentHiding` config (default `false`) is the only thing that controls hiding. The Scholar perk is now solely about its XP/enchanting bonus.
- **Per-integration "Generated N lock items" log lines spammed at INFO** (CurseForge report — "ice and fire disabled repeating forever"). The closest match in source was the INFO log at `IceAndFireIntegration.java:75` firing once per `HandlerSkill.ForceRefresh()`. Demoted all six per-integration "Generated N lock items" log lines (Spartan, Blood Magic, Ice and Fire, Locks Reforged, Samurai Dynasty, More Vanilla, Jewelcraft) from INFO to DEBUG.
- **Item-lock requirement tooltips ignored the `enableItemLocks` master toggle** (originally noted in 1.0.1). `RegistryClientEvents.onTooltipDisplay` appended the "Requirements:" block whenever `HandlerSkill.getValue(...)` returned a non-null list, without consulting the config that gates server-side enforcement. Players who disabled item locks could freely use tools like the trident but still saw "Requires Level X" text. Fixed by checking `enableItemLocks` before rendering — when locks are off, the requirement block is hidden so the UI matches enforcement. The synced value from `CommonConfigSyncCP` is authoritative on joined clients; pre-join/main-menu tooltips fall back to the local config value.

### Changed
- **Bumped network channel `PROTOCOL_VERSION` from `3` to `5`.** Two contributing wire-format growths: (a) `CommonConfigSyncCP` carries five new fields from the 1.0.0-era kill-switches (`maxActivePerks`, `disabledPerks`, `disabledPassives`, `perkSwapCooldownTicks`, `skillLevelUpCostMultiplier`) plus the new `PerkGroupsSyncCP` packet for datapack-driven perk groups; (b) one new boolean `enableScholarEnchantmentHiding` for the Scholar/enchantment-hiding decoupling. Old 1.0.0 clients connected to 1.1.0 servers (and vice versa) get a clean connection refusal instead of a silent state-corruption bug. Clients and servers must update together.
- **`mods.toml` YACL dependency** flipped from `mandatory=true` to `mandatory=false`, ordering set to `AFTER`. YACL is now genuinely optional everywhere; the server side doesn't need it (config persists via plain Gson) and the client side falls back to the parent screen with a log warning when the user clicks Configure without YACL installed.
- **Sided-import lint extended** to forbid `dev.isxander.yacl3.*` executable-class imports (`ConfigClassHandler`, `serializer.*`, `gui.*`, `api.*`) outside `client/config/`. Annotation-only imports (`@SerialEntry`, `@AutoGen`, `@IntField`, `@FloatField`, `@Boolean`, `@ListGroup`) remain allowed because their RUNTIME retention doesn't trigger class loading on the server. Single best guardrail against re-introducing the dedicated-server crash.
- **ISS compile dep bumped** from curse-maven file id `5539243` → `7402504` (pre-3.15 → 3.15.x). The older artifact predates `SpellCooldownAddedEvent` which Phase-1a Quickcast needs.
- **Phase 1a texture-path audit.** Corrected ten `HandlerResources` entries to match the real ISS item sheet: `upgrade_orb_cast_time`→`cast_time_ring`, `upgrade_orb_mana_regen`→`mana_ring`, `antique_amulet`→`concentration_amulet`, `mana_potion`→`enchanted_ward_amulet`, `greater_mana_potion`→`greater_healing_potion`, `apprentice_spellbook`→`chronicle`, `affinity_ring`→`arcane_rune`, `scroll_of_haste`→`scroll`, `ancient_codex`→`chronicle_old`, `arcane_debris`→`arcane_essence`. Perks were functional before but rendered as missing-texture pink/black boxes.
- **`apothItem()` helper path fix.** Apotheosis uses `textures/items/` (plural) not the vanilla `textures/item/`. Every Apotheosis perk icon now resolves correctly.
- **Two design-doc rename clashes resolved:** doc's "Mana Shield" → `MANA_BULWARK` (doc's "Arcane Barrier" was already in use as an Endurance-tree perk), doc's "Second Wind" → `ARCANE_REPRIEVE` (Constitution tree already has `SECOND_WIND`).
- **`VERSION` file synced** to `1.1.0` — through 1.0.1 it was left at `1.0.0`, partially corrected to `1.0.2` in the 1.0.2 source-only release, and now matches the published version.

### Removed
- `attribItem()` helper from `HandlerResources`. `attributeslib` has no `textures/item/*.png` assets — the helper was dead code.

### Skipped (design-doc flagged risky, deferred to 1.2.0+)
Pack Caller (requires `SummonManager` internals), Eldritch Apprentice (Eldritch-research XP has no public API), Gemsmith (deep affix-gem iteration on `GetAffixModifiersEvent`), Lucky Loot (global loot modifier with no player-context routing), Spawner Mage, Enchanter's Insight, Library Dedication, Ritualist, Ars Scholar, Glyphsmith, Mythical Scribe, Bookwyrm's Apprentice, Enchanter-Arms, Apparatus Synergy, Split-Caster; plus eleven Phase-3 capstones — Resonant Affixes, Gem-Fueled Casting, Spellsocket, Adaptive Caster, Apothic Apprentice, Glyph-Imbued Gem, Sourcelink Affix, Dead King's Debt, Spawner Sanctuary, Ritualized Reforge, Gem-Threaded Armor, Arcane Syncretism. Reasons documented in the original phase commit messages.

### Notes
- **Save-compatible with 1.0.0.** No NBT schema changes. Existing `runicskills.common.json5` files (whether written by YACL pre-1.1.0 or by pack admins) load cleanly through `ConfigHolder`'s comment-stripper. Field names are unchanged; values are preserved. Players who unlocked any of the 24 default-disabled Botania perks before will simply see them disappear from the tree — their NBT rank entries are preserved and will reactivate if a pack admin sets the `*RequiredLevel` back to a positive value.
- **Tested:** `./gradlew clean build` passes (compile + sided-imports + new YACL forbid + jar assembly + reobf). Runtime smoke testing per `docs/SMOKE_TESTS.md` is required before CurseForge upload — primary regression rows: dedicated server boot without YACL installed (the flagship fix this release exists for), client boot without L2Tabs, `/globallimit` from singleplayer with cheats, `disabledPerks=["scholar"]` no longer hiding enchantments.
- This release supersedes the source-only 1.0.1 (Botania) and 1.0.2 (magic-tree) tags; only 1.1.0 ships to CurseForge. The 1.0.x tags remain in git history for reference.

## [1.0.0] - 2026-04-24

First stable release. Consolidates all 0.9.x work (item-lock master toggle, perk/passive kill switches, perk-swap cooldown, perk-group datapacks, Legendary Tabs hardening, tooltip/config fixes) under a 1.0 milestone. No further pre-1.0 versions will be cut.


### Added
- **`maxActivePerks` config** (`HandlerCommonConfig`, group `general`, default `0` = unlimited, range `0–256`). Caps the number of perks a player can have enabled at once. Enforced server-side in `TogglePerkSP` on the `rank 0 → ≥1` transition (rank-ups on already-active perks bypass the cap, matching existing school-attunement semantics). Iron's Spells school attunements count against this cap in addition to `ironsMaxSchoolSelections`. Mirrored through `CommonConfigSyncCP`.
- **`disabledPerks` / `disabledPassives` kill-switch lists** (`HandlerCommonConfig`, `@ListGroup` of `String`). Disabled entries cannot be enabled / leveled-up and their effects are suppressed; rank and level data are preserved in NBT so re-enabling restores state. Perks: `Perk.isEnabled()` returns `false` for disabled perks, so every event handler short-circuits automatically. Passives: `RegistryAttributes.modifierAttributes` passes `enabled=false` to `amplifyAttribute`, which removes the modifier — single choke point means all attribute effects drop to zero. `/skillsreload` now re-runs `modifierAttributes` for every connected player so passive-disable changes take effect without relog. Registry path (`"berserker"`) and full-id (`"runicskills:berserker"`) forms are both accepted. Mirrored through `CommonConfigSyncCP`.
- **`perkSwapCooldownTicks` config** (`HandlerCommonConfig`, default `0` = no cooldown, range `0–72000`). Per-player cooldown between perk enables. Piggybacks on the existing `perkCooldowns` map via new `SkillCapability.COOLDOWN_PERK_SWAP` constant, which already ticks down every server tick via `TickEventHandler`. Applies only on rank-0 → rank-≥1 transitions; rank-ups and disables bypass. Persists in save NBT so logging out during cooldown preserves remaining time.
- **`skillLevelUpCostMultiplier` config** (`HandlerCommonConfig`, default `1.0`, range `0.1–10.0`). Scales the vanilla XP cost of leveling a skill. Applied in both `SkillLevelUpSP.requiredPoints` (XP-points cost) and `requiredExperienceLevels` (level-gate) so high-level players can't bypass an increased cost. Mirrored through `CommonConfigSyncCP` so the GUI cost display stays synced with the server.
- **Data-driven perk groups** — new datapack loader at `data/<namespace>/perk_groups/*.json`. Schema: `{ "max_active": int, "perks": [string], "message": string? }`. New classes: `PerkGroup` (record), `PerkGroupManager` (static volatile map, `firstBlockingGroup(capability, perkName)` helper), `PerkGroupsReloadListener` (extends Forge `SimpleJsonResourceReloadListener`, subscribed via `AddReloadListenerEvent`), `PerkGroupsSyncCP` (new PLAY_TO_CLIENT packet, sent on login and `/skillsreload`). Enforced in `TogglePerkSP` alongside the existing hardcoded school-attunement check — both systems run independently. No default groups are shipped; opt-in for pack makers. Lenient JSON parsing tolerates trailing commas / comments. Per-file parse errors are logged and skipped without blocking the rest of the load.

### Changed
- **Bumped network channel `PROTOCOL_VERSION` from `3` to `4`.** Wire format of `CommonConfigSyncCP` has grown (six new fields: `maxActivePerks`, `disabledPerks`, `disabledPassives`, `perkSwapCooldownTicks`, `skillLevelUpCostMultiplier`; plus the new `PerkGroupsSyncCP` packet). Old clients connected to 0.9.9 servers (and vice versa) will get a clean connection refusal instead of a silent state-corruption bug. Please ensure clients and servers update together.
- `/skillsreload` now also broadcasts `PerkGroupsSyncCP` to every connected player so datapack perk-group changes propagate without relog.
- `Perk.isEnabled(Player)` and `Perk.isEnabled()` now consult `RegistryPerks.isDisabled(perk)` before returning true, covering every perk-effect event handler in a single choke point.
- `RegistryAttributes.modifierAttributes` now checks `RegistryPassives.isDisabled(passive)` per-passive and removes rather than adds the attribute modifier for disabled entries.

### Fixed
- **Item-lock requirement tooltips ignored the `enableItemLocks` master toggle.** `RegistryClientEvents.onTooltipDisplay` appended the "Requirements:" block whenever `HandlerSkill.getValue(...)` returned a non-null list, without consulting the config that gates server-side enforcement in `SkillCapability.canUse(...)`. Players who disabled item locks could freely use tools like the trident but still saw "Requires Level X" text, leading to widespread confusion that the lock was still active. Fixed by checking `HandlerCommonConfig.HANDLER.instance().enableItemLocks` before rendering — when locks are off, the requirement block is hidden so the UI matches enforcement. The synced value from `CommonConfigSyncCP` is authoritative on joined clients; pre-join/main-menu tooltips fall back to the local config value, matching the existing behavior of `ClientCapabilityAccess.canUseItemClient`.
- **Mod failed to load when Legendary Tabs was not installed.** `RunicSkillsClient$ClientProxy` is a `@Mod.EventBusSubscriber` class, so Forge's `AutomaticEventSubscriber.inject` loads it at mod construction via `Class.forName(..., true, loader)`. The `clientSetup` method contained an inline lambda `() -> TabsMenu.register(new LegendaryTabRunicSkills())`, which the Java compiler desugared into a synthetic `lambda$clientSetup$3` method on ClientProxy itself. The JVM verifier walks that method body at class-load time, finds the `INVOKESTATIC sfiomn/.../TabsMenu.register(TabBase)` + `NEW com/otectus/.../LegendaryTabRunicSkills` pair, and has to check that `LegendaryTabRunicSkills` is assignable to `TabBase`. That assignability check eager-resolves `TabBase`, which fails with `NoClassDefFoundError` when Legendary Tabs is absent — even though the `if (LegendaryTabsIntegration.isModLoaded())` guard means the lambda would never actually run. Fixed by replacing the inline lambda with a method reference `LegendaryTabsClientIntegration::registerTab` that delegates to a new `registerTab()` static method on the existing client-integration class. ClientProxy's bytecode now only references `LegendaryTabsClientIntegration` (a plain class not in the optional-mod namespace); the `sfiomn.*` types stay confined to `LegendaryTabsClientIntegration` and `LegendaryTabRunicSkills`, which are only class-loaded when the `isModLoaded()` guard passes. The inline `sfiomn.legendarytabs.api.tabs_menu.TabsMenu.register(...)` call and the now-unused `import com.otectus.runicskills.client.gui.LegendaryTabRunicSkills` were removed from `RunicSkillsClient.java`.
- **Skill-selection hover border misaligned.** `skill_card_hover.png` is 74×26 (a symmetric green halo with 4px of glow on each horizontal side of the underlying 66×26 button), but `RunicSkillsScreen.drawOverview` was passing `OVERVIEW_SLOT_WIDTH` (66) as both the blit size *and* the `textureWidth` argument to `GuiGraphics.blit`. With a mis-declared texture width, OpenGL normalised UV against 66 while the image is really 74 pixels wide — so the entire halo got squashed horizontally into the 66-wide button rect, pulling the visible border inside the button outline instead of glowing around it. Fixed by introducing `OVERVIEW_HOVER_TEX_WIDTH`/`HEIGHT` constants (74×26), passing them as the real texture dimensions, and shifting the blit position by `(74-66)/2 = 4px` left so the halo sits centered around the button with the expected 4px outer glow on each side.
- **Skill-selection tooltip could be overpainted by adjacent cells.** `drawOverview` was calling `Utils.drawToolTip` *inside* the skill-iteration loop, so any cell drawn after the hovered one (specifically the right-column neighbour) rendered on top of the tooltip. Fixed by capturing the hovered skill in a local, completing the loop, and rendering the tooltip once after every cell has painted. Defensive against future art changes even though the 74×26 halo fits entirely within the 11px inter-column gap.

### Notes
- Vanilla `/reload` updates perk-group state server-side but does not auto-push to clients (matches existing lock-items behavior). Use `/skillsreload` for full propagation.
- All five new config options default to behavior-preserving values (`0`, empty list, `1.0`); upgrading an existing world changes nothing until the admin opts in.
- No NBT schema changes; save-compatible with 0.9.7.

## [0.9.7] - 2026-04-22

### Added
- **`enableItemLocks` master toggle** in `HandlerCommonConfig` (default `true`). When off, every entry in `runicskills.lockItems.json5` *and* every integration-generated lock is ignored — `SkillCapability.canUse(...)` short-circuits to `true`. This is the single switch users were looking for when they "disabled itemlock" expecting items like the trident to become usable; previously, the only "lock" toggles in the YACL UI were `dropLockedItems` (only controls auto-dropping, not the lock check) and the per-integration `*EnableLockItems` flags (only gate integration-generated entries, not the vanilla list that contains the trident). The new flag is mirrored into `ClientCapabilityAccess.canUseItemClient` so client-side checks (used by `MixGunItem` and gun-mod integrations) honour it too.
- `ConfigSyncCP.sendToAllPlayers()` — broadcasts the lock-items list to every connected client. Wired into `/skillsreload` and `/registeritem` so cache changes propagate without requiring every player to relog.

### Changed
- `dropLockedItems` config comment now notes that it has no effect when `enableItemLocks` is off.
- Bumped network channel `PROTOCOL_VERSION` from `2` to `3`. `CommonConfigSyncCP` now carries the `enableItemLocks` boolean ahead of `dropLockedItems`; the wire format is incompatible with the previous version.
- `/skillsreload` now also re-sends `CommonConfigSyncCP` and `DynamicConfigSyncCP` to every connected player so config edits made between joins are picked up without requiring relogs.

### Fixed
- **Trident (and every other vanilla locked item) appeared "stuck" locked even after the user toggled lock-related options off.** Root cause: there was no master toggle. `dropLockedItems` and the `*EnableLockItems` per-integration flags do not gate the vanilla `lockItemList` entries (which include `minecraft:trident#strength:20;dexterity:18`), so anyone trying to "disable itemlock" had no effect on vanilla items. The new `enableItemLocks` toggle gives a single switch.
- **`/registeritem <skill> <level>` did not refresh the cache when adding a skill to an existing locked-item entry.** The "remove" and "add new item" branches called `HandlerSkill.ForceRefresh()`; the "add skill to existing item" branch only saved the file to disk, so the new requirement was silently ignored until the next reload. Now refreshes on all three branches.
- **Lock-items cache was server-only after `/skillsreload` and `/registeritem`.** Connected clients kept their stale `Skills` map, and since `InteractionEventHandler` (which calls `canUseItem`) runs on both sides, a client with the stale cache would cancel right-clicks before the server even saw them. Both commands now broadcast `ConfigSyncCP` to every player.

## [0.9.6] - 2026-04-22

### Added
- `LegendaryTabsClientIntegration.synchronizeTabStripAcrossScreens` — runs once at `FMLLoadCompleteEvent` (i.e. after every mod's `TabsMenu.register` call has drained) and reflectively does a two-way sync against Legendary Tabs' private `tabsScreens` map:
  - **Forward:** every tab registered on `InventoryScreen` is re-registered on `RunicSkillsScreen` with its original priority preserved, so the Skills page shows the same full tab strip a player sees on the vanilla inventory (same tabs, same order, same X anchor).
  - **Reverse:** the Skills tab is also registered on every *other* screen that already hosts Legendary Tabs' built-in `InventoryTab` (FirstAid Medkit, Curios, Travelers Backpack, Reskillable/Pufferfish skill pages, etc.), so it stays visible when the player switches between inventory-companion screens.
  - The whole pass is wrapped in try/catch against `NoSuchFieldException` / `IllegalAccessException` / `ClassCastException`; any failure logs a single warning and the mod falls back to the prior "Skills tab only on the Skills screen" behaviour.
- `libs/l2tabs-0.3.3.jar` — compile-time API stub (3.4 KB). Contains only the four type signatures (`BaseTab`, `TabToken`, `TabManager`, `TabRegistry` with its `TabFactory` functional interface) that `TabRunicSkills` and `RunicSkillsClient` reference. `compileOnly` keeps it out of the shipped jar; when the real L2Tabs 0.3.3 mod is installed, its classes shadow the stub at runtime. Lets the project build when the upstream L2Tabs jar isn't locally available, without any build-script changes. Ships with a `L2TABS_STUB_README.txt` documenting the arrangement.

### Changed
- **Skills tab now renders from Legendary Tabs' own `tab_menu_buttons.png` atlas** at `(u=27, v=92)` — the plain silver sword tile that no built-in tab class claims. Hover state uses the `+54 U` shift that every built-in tab uses. This removes the `renderItem(leveling_book, …)` overlay, the custom `legendary_tab.png` texture, and makes the Skills tab byte-for-byte identical in frame shape, shading, palette, and hover transition to every neighbouring tab — including the *exact* highlight bevel rows that hand-drawn reproductions were missing (which previously read as "1 pixel too tall" because the flat top edge lacked the real tab's 2-row white highlight strip).
- `LegendaryTabsClientIntegration` (new) — split out of `LegendaryTabsIntegration` so the shared class stays dedicated-server-safe (no `net.minecraft.client.*` imports — silences the `:checkSidedImports` lint task). The client file lives under `client/integration/`, fully inside the allowlist.
- Default `legendaryTabsPriority` lowered from `80` to **`15`**. Bytecode audit of Legendary Tabs 1.1.3.1 confirms `ScreenInfo.tabs` is a `TreeMap<Integer, List<TabBase>>` (ascending order = render order) and the built-in tab priorities are `InventoryTab=10`, `Backpacked/TravelersBackpack=20`, `Reskillable*=30`, `PassiveSkillTree/PufferfishsSkills=40`, `BodyDamage=50`, `Diet=60`, `FtbQuests=70`, `Maps (JourneyMap/MapAtlases/Xaeros)=75`, `FtbTeams=80`. Priority 15 sits strictly between Inventory and everything else, so Skills renders as the second tab in the strip regardless of which integrations are loaded. Config comment updated with the full priority table.

### Fixed
- **Skills tab rendered as a floating book icon instead of a proper tab inside Legendary Tabs.** Root cause: `LegendaryTabRunicSkills.render` drew only the item via `gfx.renderItem(…)` with no background. Legendary Tabs' `TabButton.renderWidget` does not invoke `super`, so tabs are fully responsible for drawing their own 26×22 frame; every built-in tab does so by blitting from the shared atlas. Fixed by switching to an atlas blit (see Changed).
- **Opening the Skills screen collapsed Legendary Tabs' strip to just the Skills icon.** `TabsMenu.initScreenButtons` keys off `screen.getClass()` (exact match, not `instanceof`) and iterates only tabs explicitly registered for that class. Fixed by the forward half of `synchronizeTabStripAcrossScreens`.
- **Skills tab disappeared when any other inventory-companion screen (Medkit, Curios, Backpack, …) was open.** Same root cause as above but in the other direction — our tab wasn't registered on those screens' classes. Fixed by the reverse half of `synchronizeTabStripAcrossScreens`.
- **Build failed with "Could not find dev.xkmc.l2tabs:l2tabs:0.3.3" cascading into 20+ follow-on resolution errors.** Single root cause: `libs/l2tabs-0.3.3.jar` was missing and no public Maven repository hosts the coordinate. When `:__obfuscated` configuration failed on that one dep, ForgeGradle's deobf pipeline aborted without populating `bundled_deobf_repo` for any of the other `fg.deobf(...)` entries. Fixed by shipping the compile-time stub (see Added).

### Removed
- `assets/runicskills/textures/gui/legendary_tab.png` — custom hand-drawn frame texture. No longer referenced now that the Skills tab blits directly from Legendary Tabs' shared atlas.

## [0.9.5] - 2026-04-22

### Added
- Legendary Tabs (Sfiomn) integration. When `legendarytabs` is present, Runic Skills registers a native `TabsMenu` tab (`LegendaryTabRunicSkills`) during `FMLClientSetupEvent` so the Skills tab participates in Legendary Tabs' own UI instead of being drawn twice.
- `LegendaryTabsIntegration` compat layer (detects the mod via `ModList` and gates the native-tab registration).
- `build.gradle` now pulls `sfiomn.legendarytabs:legendarytabs:1.20.1-1.1.3.1` as a `compileOnly` dependency, resolved from the local `libs/` directory (jar is not redistributed — drop it in yourself to build).
- `legendaryTabsPriority` config in `HandlerConfigClient` — defaults to `500`; controls ordering within Legendary Tabs' strip (lower = earlier).
- Three new lang keys (`tooltip.perk.rank`, `tooltip.perk.next_rank`, `tooltip.edit_title`) translated across all 17 supplied languages; they replace hard-coded English strings in the perk tooltip and the title-edit button.
- `HandlerConditions` now registers `EntityKilledBy` as the canonical title-condition name; the typoed `EntiyKilledBy` remains registered as a deprecated alias so existing title configs continue to work.

### Changed
- `MixInventoryScreen` now bails out of its render and mouse-click injects when Legendary Tabs is loaded, deferring to the native tab registration. Prevents double-rendering of the Runic Skills tab inside Legendary Tabs' wrapped screens. The two guard branches are consolidated into a `runicskills$externalTabsActive()` helper and `getRecipeBookComponent().isVisible()` is now called once per frame (also null-guarded) instead of twice.
- `KubeJSIntegration.postLevelUpEvent` caches its reflective `Class`/`Method`/field lookups on first successful resolve instead of redoing six reflective calls every level-up.
- `Utils.FONT_COLOR` is now `final`; added `SKILL_ABBR_COLOR`, `SKILL_LEVEL_COLOR`, `TITLE_SELECTED_COLOR`, `TITLE_UNSELECTED_COLOR` constants and switched the corresponding hard-coded values in `RunicSkillsScreen`.
- `MixVillager` haggler-delta map now drops entries that no longer correspond to offers on the villager, preventing a minor memory leak when a trade is completed between UI opens.

### Fixed
- **Legendary Tabs integration — three distinct integration defects discovered via jar-disassembly audit:**
  - **(A) Duplicate tab strip rendered on the Skills screen.** `RunicSkillsScreen.render()` called `DrawTabs.render/mouseClicked/onClose` unconditionally, and the `MixInventoryScreen` bail-out only covered the vanilla `InventoryScreen`. Now gated behind `!L2TabsIntegration.isModLoaded() && !LegendaryTabsIntegration.isModLoaded()` at all three call sites.
  - **(B) Skills tab invisible on the vanilla inventory strip.** Default `legendaryTabsPriority` was `500`; disassembly of `sfiomn.legendarytabs.client.tabs_menu.InventoryTab` (priority `10`) and `FtbQuestsTab` (priority `70`) revealed that Legendary Tabs uses small priority integers and paginates overflow. At 500 our tab always landed on a later page. Default dropped to `80` so the Skills tab sits right after built-in tabs on page 1. Config comment updated to explain the priority convention for pack authors.
  - **(C) Legendary Tabs strip hidden on the Skills screen.** `LegendaryTabRunicSkills` registered `RunicSkillsScreen.class` with the vanilla-inventory `VANILLA_GUI_HEIGHT = 166`, but the Skills panel is actually **194** pixels tall (`PANEL_HEIGHT`). `TabsMenu.initScreenButtons` computed `topScreenPos = (screenHeight - 166) / 2` and drew the strip 14 pixels too low — underneath our panel background. Now registers `RunicSkillsScreen` with `RUNIC_SKILLS_GUI_HEIGHT = 194`.
- **Singleplayer world won't load — kicks player back to the multiplayer screen:** `MixPlayer.runicskills$modifyMaxAir` fires during `Entity.<init>` (specifically the `setAirSupply(getMaxAirSupply())` call in the constructor), which runs *before* `LivingEntity.defineSynchedData` registers `DATA_HEALTH_ID`. The perk check inside (`Perk.isEnabled(player) → player.isDeadOrDying() → player.getHealth() → SynchedEntityData.get(DATA_HEALTH_ID)`) NPE'd because the accessor hadn't been registered yet. Server thread threw "Couldn't place player in world / Invalid player data", disconnected the integrated-server client, and the UI flow bounced to the last-seen join screen. Fixed in two places: (1) `Perk.isEnabled(Player)` now uses `player.isRemoved()` (a simple field read) instead of `player.isDeadOrDying()` to gate dead players; (2) `MixPlayer.getMaxAirSupply` bails early when `SkillCapability.get(player)` returns `null`, which is always the case during `Entity.<init>` (capabilities are attached by Forge after the constructor returns), guaranteeing we never invoke `Perk.isEnabled` inside the constructor path.
- **Crash on attack-range modifier (Better Combat / AttackRangeExtensions):** `MixTargetFinder.apply$AttackRangeModifiers` had a switch statement with no `break;` between `case ADD` and `case MULTIPLY`, causing every additive modifier to also be applied multiplicatively (and vice versa). Rewritten as an arrow-form switch expression.
- **Crash when a skill-gated gun is fired (PointBlank):** `MixGunItem.tryFire` called `ci.cancel()` on a `CallbackInfoReturnable<Boolean>` with no return value set, which is illegal under Mixin and threw `IllegalStateException`. Now calls `ci.setReturnValue(false)`.
- **Crash on empty title queue:** `TitleQueue.peek()` and `dequeue()` no longer throw `NoSuchElementException` when the queue is empty; `peek()` returns `null` and `dequeue()` is a no-op.
- **NPE in `OverlaySkillGui`:** when `HandlerSkill.getValue(skill)` returned `null` (unknown skill key), `showWarning` still armed `showTicks > 0`, causing the render loop to NPE on `skills.size()`. Now bails early without setting the ticker.
- **Multiple NPE paths when the local skill capability is not yet synced:** `RunicSkillsScreen.drawTitleButton`, `handleOverviewClick`, the title list renderer, `buildDetailPageState`, and `RegistryClientEvents.onTooltipDisplay` now all null-guard `SkillCapability.getLocal()` and fall back to sensible empty-state rendering (requirement colour defaults to red, detail page returns null, title button shows blank).
- **`DrawTabs.renderTabVisual` matrix-stack leak:** nested `pushPose`/`popPose` pairs are now wrapped in `try/finally`, so a throwing `renderItem` (e.g. a buggy third-party item renderer) no longer leaks two pose frames into subsequent GUI rendering.
- **`TitleCommand` NPE on unset:** `setTitle(..., false)` now null-guards `SkillCapability.get(player)` before calling `setUnlockTitle`.
- **`SkillCondition.ProcessVariable` NPE:** returns a processed value of `0` and bails cleanly when the player's skill capability isn't attached yet.

### Removed
- `ScreenTabEvents` dead-placeholder class. The `LegendaryTabsIntegration` javadoc previously pointed to it as the active renderer for Legendary Tabs — in reality the native `TabBase` is used. Javadoc rewritten to describe the real mechanism.

## [0.9.2] - 2026-04-09

### Security
- **Critical:** removed `CounterAttackSP` exploit where the client could supply an arbitrary attack-damage modifier. Counter-attack damage is now computed entirely server-side in `CombatEventHandler` and the serverbound packet is no longer registered. The feature was previously broken (direction mismatch with the only sender) and now actually works.
- `SetPlayerTitleSP` now rejects unknown titles, missing capabilities, and titles the player has not unlocked. A malicious client can no longer force admin/locked titles. Added `titlesUseCustomName` config flag (default `true`) to gate the `setCustomName` write for compatibility with chat/nick mods.
- Replaced `ObjectInputStream`/`ObjectOutputStream` in `ConfigSyncCP` and `CommonConfigSyncCP` with `FriendlyByteBuf` field-by-field encoding plus bounded `readVarInt`/`readCollection` and a `MAX_SYNCED_LIST` cap. Closes a Java-serialization gadget surface and prevents unbounded allocations from oversized payloads.
- Bumped network channel `PROTOCOL_VERSION` from `1` to `2`. Old clients will fail fast on join instead of mis-decoding the new wire format.

### Fixed
- Joining a server no longer overwrites the user's local `runicskills-common.json5` config file. Removed `HandlerCommonConfig.HANDLER.save()` from `CommonConfigSyncCP` and `DynamicConfigSyncCP`.
- Eliminated dedicated-server crash risks: `SkillCapability`, `Passive`, and `Perk` no longer import `net.minecraft.client.*` or `Screen`. The no-arg `SkillCapability.get()` was replaced with a `getLocal()` indirection routed through a client-registered supplier; tooltip building moved to new `client/tooltip/PassiveTooltip` and `PerkTooltip` helpers.
- Fixed `MixVillager` runaway haggler discount: each call to `updateSpecialPrices` now undoes the previously-applied per-offer delta before applying a fresh one, instead of compounding indefinitely across trade-UI reopens.
- Fixed `Utils.intToRoman` (was duplicating the thousands array and missing the units array — `1994` now correctly produces `MCMXCIV`).
- Converted `MixPlayer.getMaxAirSupply` from a fragile bare implicit override to an explicit `@Inject(method = "getMaxAirSupply", at = @At("RETURN"), cancellable = true)` targeting `Entity` (where the method actually lives). Now resilient to vanilla signature drift.
- Documented `MixLivingEntity` `activeEffects` invariants so future patches understand which vanilla guarantees the mixin preserves.

### Changed
- Optional integrations (Curios, TacZ, CrayfishGuns, ScorchedGuns2, IronsSpellbooks, ArsNouveau, Apotheosis) are now loaded via `Class.forName(string)` so the integration class is never in `RunicSkills`' constant pool. The mod no longer crashes at load time when a dependency mod is absent. Each integration's `isModLoaded()` check still gates instantiation.
- `RegistryClientEvents` moved from `registry/` to `client/event/` (matches its actual sidedness).
- `LockItem` lost its legacy `formatString` / `getLockItemFromString` parser and the YACL string-encoded round-trip. `ConfigSyncCP` now serializes lock items field-by-field via `FriendlyByteBuf` (item id, skill enum ordinal, level) with bounds caps.
- Added `./gradlew checkSidedImports` lint task that fails the build on any `import net.minecraft.client.*` or `com.mojang.blaze3d.*` outside the client/integration allowlist. Wired into `check`, so `./gradlew build` runs it automatically.

### Removed
- Deleted `TetraIntegration` and all references in `CombatEventHandler`, `InteractionEventHandler`, `RegistryClientEvents`, plus the `tetra_*` Gradle dependencies. The integration was imported but never wired into the mod constructor — dead code.

## [0.9.1] - 2026-04-08

### Changed
- Renamed project from JustLevelingFork to Runic Skills
- Rebranded mod ID, package, and all internal references
- Changed GUI text color from dark grey to white for improved readability

## [0.9.0] - Initial Runic Skills release

### Added
- Forked from JustLevelingFork
- Redesigned perk and skill GUI with paginated layout
- Title selection panel with scrollable list
- New skill categories: Endurance, Fortune, Wisdom (replacing Defense, Luck, Building)
- Perk rank system with multi-rank support
- Passive attribute system per skill
- KubeJS integration for scripting
- Ars Nouveau, Tetra, and Apothic Attributes optional integrations
- YACL-based configuration UI
