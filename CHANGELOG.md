# Changelog

## [1.5.4] - 2026-07-02 — Original perk icon set

### Changed — every perk icon replaced with an original generated texture
All 461 registered perk icons were replaced with original 16x16 pixel-art textures in a
consistent visual style (shared outline, 3-tone shading, themed palettes, corner badges
encoding the effect modifier). The 82 perks that previously borrowed Iron's Spellbooks /
Ars Nouveau / Apotheosis item sprites now ship their own icons under
`assets/runicskills/textures/skill/<skill>/<perk_id>.png`, so integration perks no longer
render as missing textures when the perk-icon path changes upstream. The
`ironsItem`/`arsItem`/`apothItem` helpers in `HandlerResources` were removed accordingly.

- 21 orphaned PNGs from removed perk sets (old Blood Magic / Enigmatic Legacy era, plus two
  stale `passive_*` duplicates under `wisdom/`) were deleted; passive, locked and null icons
  are untouched.
- New `PerkTextureResolutionTest` asserts every referenced texture resolves on disk, that no
  perk constant points at a foreign namespace, and that every registered perk id has a
  matching `textures/skill/<skill>/<id>.png`.
- All 38 Passive attribute icons (`passive_*.png`) — shown as the first row of each
  skill page — were regenerated in the same style, replacing the remaining old
  16x16/20x20 item-style art (including the icons with the baked-in "S" badge).
- Full per-perk and per-passive tracking tables: `docs/PERK_ICON_AUDIT.md`.

## [1.5.3] - 2026-06-21 — Apotheosis "Fortune 0" fix, earned-level perk budget, lock source metadata

### Fixed — bogus "You need Fortune 0" denial on Apotheosis-affixed gear
Equipping armor that carried Apotheosis affix data could be blocked with the nonsensical
message *"You need Fortune 0 to use … items!"*, with no matching config entry to be found.

Root cause: in `ApotheosisIntegration.onEquipAffixItem`, the item was dropped and
`item.setCount(0)` **emptied the stack first**, and only *then* did the code recompute the
required Fortune level and rarity for the message — on a now-empty stack, which returns `0` /
"Unknown". The real requirement was lost and surfaced as "Fortune 0". An item whose rarity was
not present in the `apothRarity*Level` config (default-deny → `Integer.MAX_VALUE`) hit the same
path and also displayed as `0`.

Fixes:
- The required level and rarity are now captured **before** the stack is mutated, and a required
  level of `0` (common/ungated rarity, no affixes, or gating disabled) **never** blocks or
  messages. The denial now reads *"Requires Fortune X to use <Rarity> Apotheosis-affixed gear."*
- Unmapped rarities are reported distinctly (*"…rarity isn't configured — ask an admin to set the
  apothRarity\*Level options."*) instead of as a meaningless numeric level.
- Items with an active affix gate now show an **"Apotheosis Affix Gate: Requires Fortune X"**
  tooltip line, distinct from manual/integration item-lock attribution.
- The decision logic is extracted to the pure, unit-tested `ApothGateMath`.
- Affix-rarity gating stays **on by default** (affixed gear is rarity-based loot, intended RPG
  progression). The `apothEnableAffixRarityGating` config comment now explains that it gates *any*
  Apotheosis-affixed equipment (including vanilla armor/tools that rolled an affix), that it is
  **separate** from gem-socket gating, and how to disable it. Gem-rarity gating already only
  affected gem socketing and is unchanged.

### Added — perk budget scaled from *earned* global level
The optional `perksPerGlobalLevel` cap now scales from a player's **earned** global level
(`max(0, totalSkillLevels − startingBaseline)`) instead of the raw skill-level sum. A fresh
player is earned-level 0, so `0.5` grants the first perk slot after 2 earned levels and 3 slots
after 6 — matching the intuitive "0.5 perks per level" request. `getGlobalLevel()` itself is
**unchanged** (commands, FTB Quests, docs still see the raw sum). Legacy behavior is preserved:
the default `perksPerGlobalLevel = 0` disables the scaled cap entirely.

- New optional `maxPerkBudgetCap` (default 0 = none) clamps the scaled budget to a hard ceiling
  without affecting the flat `maxActivePerks` cap. Synced to clients and re-applied by
  `/skillsreload`.
- **Over-budget = frozen until respec.** If a config reload, skill-level loss, or a lowered
  `perksPerGlobalLevel` leaves a player above budget, perk activation is frozen (existing perks
  are **not** disabled or lost) and the player is told to respec. Disabling perks remains allowed.
- Perk tooltips now show the active/cap count, the **earned global level that unlocks the next
  slot**, and an over-budget warning.

### Added — item-lock source metadata
`LockItem` gained an optional `Source` field (backwards-compatible; legacy `lockItems.json5`
without it loads unchanged and a null source is never written). Every integration-generated lock
is stamped with its provider id, and locked-item tooltips now show **"Locked by: …"** so players
can tell whether a lock came from manual config, Ice and Fire, Iron's Spells, Spartan, etc. —
distinct from the Apotheosis affix gate, which is a runtime check rather than an item lock.

### Integration compatibility audit
All advertised integration toggles default **on** (`enableIceAndFireIntegration`,
`iceFireEnableLockItems`, `enableSpartanIntegration`, `spartanEnableLockItems`,
`enableIronsSpellbooksIntegration`, `enableIronsSpellbooksLockItems`). Ice and Fire and Iron's
Spells both register lock providers that scan their whole namespace and tier gear by keyword
(`LockGen`), in addition to curated lists. Exact per-item coverage is best confirmed in a loaded
dev client; the new "Locked by:" tooltip makes that verification straightforward.

| Integration | Perks / passives | Item locks | Event hooks |
| --- | --- | --- | --- |
| Apotheosis / Apothic Attributes | ✅ (gem/affix perks, 10 attribute perks) | ⚠️ affix-rarity gate (runtime, not item-lock entries) + gem-socket gate | ✅ |
| Ice and Fire (`iceandfire`) | ✅ (Dragon Slayer, Mythic Fortitude) | ✅ curated + namespace discovery | ✅ |
| Spartan Weaponry / Shields / Fire | — | ✅ curated + discovery (incl. `spartanfire`) | — |
| Iron's Spells (`irons_spellbooks`) | ✅ (schools, many built-ins) | ✅ namespace discovery (gear tiers; ingredients/ink intentionally unlocked) | ✅ |
| Locks Reforged, Samurai Dynasty, More Vanilla, Jewelcraft | — | ✅ curated | some |
| Epic Knights, Aquaculture, Cataclysm, Mowzie's, Undergarden, … | varies | ✅ generic namespace discovery | varies |
| TacZ, CGM, Scorched Guns, Curios, FTB Quests, KubeJS, Ars Nouveau | ✅ / hooks | — (no item-lock entries) | ✅ |

"Has perks" does **not** imply "has item-lock coverage" — the two are tracked separately, which
was the source of earlier confusion.

### Changed — network protocol bump `6` → `7`
`CommonConfigSyncCP` gained the `maxPerkBudgetCap` field, changing the packet payload. Mixed
1.5.3 / pre-1.5.3 client-server pairs are now rejected cleanly at the connection screen instead
of passing the handshake and desyncing on a misread config-sync buffer. **Both sides must run
1.5.3+.**

### Removed — stale `botania` optional dependency in mods.toml
All Botania integration was removed in 1.5.0; the leftover optional-dependency declaration (and
its version-range enforcement) is now gone too.

### Build — version-consistency guard
`./gradlew check` now fails if `VERSION`, `gradle.properties` `mod_version`, and the top
CHANGELOG entry disagree — the stale-`VERSION` drift that showed users false "new version
available" banners can no longer reach a release build.

### Fixed — full security/stability review remediation
A complete codebase review (security, stability, performance, build) landed these fixes:

- **`/skillsreload` title desync**: reloading replaced every title's registry binding with null,
  so ALL titles silently stopped being evaluated (with per-scan "desync" warnings) until a
  restart. Reload now re-binds titles to the frozen registry, re-runs the default-merge (newly
  shipped built-in titles appear without a restart), and reports config titles added after
  startup as restart-required.
- **Memory leaks on long-running servers**: per-player combat memory (`PerkEffectsHandler`'s
  seven UUID-keyed maps), Power runtime target tags, and the Barrage/Long Note/Pyroclasm
  dispatcher state are now all purged on logout.
- **Atomic config saves**: `ConfigHolder.save()` writes a `.tmp` sibling and moves it into
  place, so a crash or full disk mid-write can no longer truncate a config and silently reset
  it to defaults on the next boot.
- **Load-time config validation**: core progression fields (`skillMaxLevel`,
  `perksPerGlobalLevel`, `maxActivePerks`, `maxPerkBudgetCap`, …) are clamped into their
  documented ranges when read from disk, with a WARN naming the field and both values — the
  YACL UI ranges previously only applied inside the config screen.
- **At-cap level-up packets**: a level-up request at `skillMaxLevel` consumed XP, fired
  `SkillLevelUpEvent`, and pinged the quest bridge even though the level couldn't change.
  Now rejected up front (creative included — creative bypasses the cost, never the cap).
- **Client decode hardening**: `NoticeOverlayCP` and `CommonConfigSyncCP` now bound their
  list/array counts before allocating (matching `PerkGroupsSyncCP`), so a corrupt or hostile
  server can't crash the client decode thread or force huge allocations.
- **NPE-proof skill lookups**: `getSkillLevel` now resolves unknown skill names to the default
  level 1 instead of unboxing null (e.g. a lock referencing a skill from a removed addon).
- **AttributesLib isolation**: the ten Apothic Attributes perks moved to their own
  reflectively-loaded `ApothicAttributesPerksIntegration`, so an AttributesLib version mismatch
  degrades only those perks instead of also killing affix-rarity and gem gating.
- **Combat/tick performance**: capability lookups are memoized per player-tick (a melee hit
  previously re-resolved the capability hundreds of times across eight handlers); per-perk
  attribute-modifier UUIDs are precomputed (was ~40 MD5 hashes per player per second); attribute
  modifiers (incl. MAX_HEALTH) are only touched when their value actually changes; title
  conditions are parsed once per config load instead of re-split on every 10-second scan.
- **Clean-checkout build**: the Gradle wrapper jar and the two compile-only stub jars in
  `libs/` were blanket-ignored by `.gitignore`'s `*.jar` rule, so a fresh clone could not
  build. They are now tracked via targeted ignore exceptions.

## [1.5.2] - 2026-06-18 — Legacy config-read fix + 20 new titles

### Fixed — legacy snake_case configs silently mis-parsed
Config files written before the 1.1.0 `ConfigHolder` refactor were serialized by the old
YACL serializer, which named **nested-POJO** fields in `snake_case` (`title_id`,
`hide_requirements`, `item`, `skill`, `level`). The current loader uses plain Gson with
IDENTITY field naming, so none of those keys matched the Pascal-case Java fields. Gson
constructed each object via its no-arg constructor and overrode nothing:

- **Titles:** every entry in a legacy `titles.json5` collapsed to the constructor default
  (`rookie`). After the registry's dedup-by-id, only **Rookie** survived — so the Title tab
  showed just Rookie + the two system titles (Administrator, Titleless) instead of the full
  list. This was the reported "only three titles" bug.
- **Item locks:** every entry in a legacy `lockItems.json5` collapsed to the default
  (`minecraft:diamond` / Strength 2), silently breaking configured item gating.

Fixed by adding Gson `@SerializedName(value = "<Pascal>", alternate = {"<snake>"})` to the
nested model fields (`TitleModel`, `LockItem`, `LockItem.Skill`). Both legacy snake_case and
current Pascal-case files now load correctly; new writes stay Pascal. Top-level `@SerialEntry`
fields (`common.json5`) were written verbatim camelCase and were never affected.

### Added — title default-merge on load
`ConfigHolder` loads an existing file as-is with no merge, so titles added to the built-in
defaults never reached players who already had a config. `RegistryTitles.load()` now unions
any built-in title whose id is missing from the loaded list (preserving existing entries and
custom tunings) and saves — which also rewrites a legacy snake_case file in Pascal-case.

### Added — 20 modpack-themed titles
Boss kills (Ice & Fire gorgon/hydra/sea serpent, Cataclysm ignis/ender guardian, Bosses of
Mass Destruction night lich/obsidilith, Mowzie's ferrous wroughtnaut, vanilla warden/wither),
spellcaster titles (spellweaver/summoner/elementalist), Tinkering titles
(artificer/master artificer/engineer), martial hybrids (gladiator/berserker/monk), and a
spelunker title (mine ancient debris).

## [1.5.1] - 2026-06-18 — Item-gating enforcement fix ("inert in hand")

Item locks were defined and shown in tooltips but not reliably enforced: a player below an
item's skill requirement could still **wield, attack with, and mine** using a locked item
(e.g. the iron sword at 1 Strength despite its "requires 8 Strength" tooltip). The decision
logic was correct; enforcement was incomplete and bypassable. This release makes a locked
item **inert in hand** — it stays in the inventory/hand but cannot attack, mine, or be used
until its requirements are met. `dropLockedItems` remains an opt-in and is unchanged.

### Fixed — gating enforcement gaps
- **Melee attacks were bypassable.** Enforcement relied solely on `AttackEntityEvent`
  (`CombatEventHandler.onPlayerAttackEntity`), which combat-overhaul mods such as **Better
  Combat** never fire (they perform their own hit detection — note `MixTargetFinder`), so
  locked weapons could still deal damage. Added a server-side `LivingAttackEvent` backstop at
  `HIGHEST` priority that cancels the hit when the attacker's main-hand item is locked. This
  fires before `LivingHurtEvent` and the bonus-damage handlers, so it is order-independent and
  catches modded weapons. The original `AttackEntityEvent` cancel is kept for the client-side
  early-out and one-shot overlay warning.
- **Block-breaking/mining was ungated.** A locked tool could break blocks freely. Added a
  `BlockEvent.BreakEvent` cancel and a zero-break-speed guard in `onPlayerMining` so the block
  never visibly cracks.
- **Wielding was effectively unenforced under default config.** With the above, holding a
  locked item is now functionally inert without requiring `dropLockedItems`.

### Changed — single source of truth + overlay anti-spam
- Extracted the level comparison into a pure, Forge-free `common/util/LockCheck` helper
  (unit-tested headlessly like `PerkCapMath`); `SkillCapability.canUse` now delegates to it.
- Added `SkillCapability.canUseItemSilent` for the per-hit / per-break backstops so they do
  not spam the `SkillOverlayCP` client overlay — the one-shot warning still comes from the
  discrete action events (attack swing, left/right-click).
- Closed a latent unboxing NPE in the gating path via a null-safe `safeLevel` (a skill from a
  no-longer-loaded addon now resolves to the default level 1 instead of throwing).

### Notes / out of scope
- Gun mods: TacZ already gates server-side. Crayfish, Scorched Guns 2 and PointBlank remain
  **client-side gated only** (bypassable by a modified client) — a server-authoritative fire
  gate is a possible follow-up.
- Added `LockCheckTest` (7 cases: requirement boundary, null/empty, multi-skill, unknown-skill default).

## [1.5.0] - 2026-06-15 — Over-GUI denial messaging, perk-backlog drain, mod-perk purge & proper Apotheosis

Broad audit/stabilization pass, a unified over-GUI denial-message system (network protocol 5 → 6),
the Runecraft lock-provider coverage audit, a large drain of the inert-perk backlog, removal of the
Botania / Blood Magic / Enigmatic Legacy perk sets (mods absent from the target pack), and proper
Apotheosis incorporation (audit, enchantment-cap perk, and gem rarity gating).

### Removed — Botania / Blood Magic / Enigmatic Legacy perks (not in the Runecraft pack)
Purged every perk tied to these three mods (identified authoritatively by each perk's
`<Integration>.isModLoaded()` gate, not just naming), removing the bloat and config surface for mods
the target pack doesn't ship:
- **Botania (~40 perks):** the entire `BotaniaIntegration` perk block (all `BOTANIA_*`, the rune /
  seasonal / sin / capstone perks) plus `BotaniaIntegration`, `BotaniaCompat`, the `botania` config
  group (83 fields), all `perk.runicskills.botania_*` lang, the namespace texture helper, and the
  Botania `compileOnly` build dependency.
- **Blood Magic (12 perks):** `BLOOD_MASTERY`, `RITUAL_SAGE`, `CRIMSON_BOND`, `BLOOD_SACRIFICE_RECOVERY`,
  `BLOOD_SHIELD`, `BLOOD_WARD`, `BLOOD_RITUALIST`, `BLOOD_INSCRIPTION`, `SACRED_GEOMETRY`,
  `BLOOD_CHANNEL`, `BLOOD_EMPOWER`, `RITUAL_EFFICIENCY` + `BloodMagicIntegration` and its lock-item
  provider. **Kept:** the Iron's Spellbooks *blood-school* perks (`BLOOD_ATTUNEMENT`, `BLOOD_MANCER`,
  `BLOOD_WARDED`, `BLOOD_CATALYST` — ISS is in the pack) and `BLOOD_FURY` (generic crit life-steal,
  re-gated to drop its Blood-Magic dependency).
- **Enigmatic Legacy (7 perks):** `ANCIENT_STRENGTH`, `CURSE_WARD`, `ARTIFACT_HUNTER`,
  `ENIGMATIC_VITALITY`, `ENIGMATIC_PROTECTION`, `ENIGMATIC_WISDOM`, `ENIGMATIC_UNDERSTANDING` +
  `EnigmaticLegacyIntegration` and the `enigmaticlegacy` lock-discovery provider. **Kept:** the generic
  perks that were merely flavored that way (`ARMOR_OF_FAITH`, `SOUL_SUSTENANCE`, `MYSTIC_ANALYSIS`,
  `SAGES_FOCUS`) and `CATACLYSMS_WRATH` (gated on Cataclysm, which is in the pack).
- Removed the corresponding effect sites (`BLOOD_WARD`/`BLOOD_EMPOWER` in `PerkEffectsHandler`,
  `BLOOD_SHIELD` in `CombatEventHandler`), the integration enable-toggles and their `CommonConfigSyncCP`
  sync, 125 config fields, 126 lang keys, 61 orphaned texture constants, and `BOTANIA_RUNIC_SKILLS_INTEGRATION.md`.

### Apotheosis — proper incorporation (audit + wire inert perks)
Audited the 14 existing Apotheosis perks against the real 7.4.8 deobf API: **zero drift** — every
`ALObjects.Attributes.*` id, `GetItemSocketsEvent`/`ItemSocketingEvent` usage, and `AffixHelper`/
`SocketHelper`/`DynamicHolder` call is correct. Wired the inert `APOTHEOSIS_WISDOM` via Placebo's
`GetEnchantmentLevelEvent` (the same event Apotheosis uses to extend caps), raising present
enchantments' effective levels by the perk's amplifier for the holder. `APOTHEOSIS_GEMS` stays
allowlisted with a documented reason: gem rarity is rolled inside `GemLootPoolEntry` with no
player-attributable Forge event in 7.4.8, so there is no faithful hook short of fragile loot-internal ASM.

### Added — Apotheosis gem rarity gating
Gems are not affix items, so the existing Fortune affix-rarity gating never covered them. Now gem
**socketing** is gated by the gem's own `LootRarity` (i.e. how powerful it is), reusing the same
per-rarity Fortune thresholds (`apothRarityUncommonLevel` … `apothRarityAncientLevel`). Implemented
via Apotheosis's `ItemSocketingEvent.CanSocket` (`@HasResult` → `setResult(DENY)`), reading rarity
through `GemInstance.unsocketed(stack).rarity()`, with an over-GUI denial banner. Toggle:
`apothEnableGemRarityGating` (default on; common gems are always ungated).

### Fixed — crashes & correctness
- **P0 capability-sync NPEs.** `SyncSkillCapabilityCP.send()`/`.handle()` dereferenced the `@Nullable`
  `SkillCapability.get()`/`getLocal()` with no guard; `send()` is reached from ~30 sites incl. player
  world-join, so a capability-attach race could crash login. Both are now null-guarded.
- **`/registeritem` first-launch crash.** `HandlerLockItemsConfig.lockItemList`, plus `LockItem.Skills`
  (field default and varargs constructor) were immutable `List.of(...)`/`Stream.toList()`; the command's
  `.add()/.remove()/.set()` threw `UnsupportedOperationException` until the config file had been written
  once. All three are now mutable `ArrayList`s.
- **`InteractionEventHandler` NPE** on unregistered/removed-mod items (`Objects.requireNonNull(getKey())`)
  — now deny-safe, matching the `SkillCapability` fix.
- **`PlayerLifecycleHandler` title NPE** — re-fetched the title and dereferenced the nullable result
  despite null-checking a different variable (fires on every chat/tab render); reuses the checked ref.
- **`CraftingEventHandler` LUCKY_DROP** captured a `Player` ref in a deferred `TickTask` that could be
  offline a tick later; now captures the UUID and re-resolves.
- **`TacZIntegration`** — guarded the unchecked `ModernKineticGunItem` cast (ClassCastException) and the
  unchecked `SkillCapability.get()` (NPE).
- **Capability serialization** — `serializeNBT`/`copyFrom` used unguarded `.get()` for skill/passive/title
  maps (NPE/dataloss if a registry entry was missing); now `getOrDefault` like the perk path.

### Fixed — robustness & hardening
- Client-bound packet decoders `DynamicConfigSyncCP`, `PerkGroupsSyncCP`, `PowerOverridesSyncCP` now
  bounds-check counts/splits (`DecoderException`) so a malformed/hostile server can't crash clients
  (matches the existing `ConfigSyncCP` guard).
- Rate limiters added to `PassiveLevelUpSP`/`PassiveLevelDownSP`/`SetPlayerTitleSP`/`OpenEnderChestSP`.
- Null guards on `Perk.getToggle()`, `Skill.getLevel()`, `OverlaySkillGui`, and `MixForgeGui`'s
  camera-entity cast (spectator/detached camera).
- `MixTrueInvisibilityEffect` now gated on `irons_spellbooks` in the mixin plugin (no classloader WARN
  when ISS is absent).
- `/globallimit` and `/updateskilllevel` reject `< 1` (a non-positive `skillMaxLevel` broke the rank
  divisor); `/updateskilllevel` now reports failure instead of success on its rejection path.

### Fixed — denial messages now render over open GUIs
- **Item-lock warning hidden behind the crafting menu.** The skill-requirement overlay
  (`runicskills:skill_overlay`) is a Forge HUD layer, which Forge does **not** render while a container
  `Screen` is open — so trying to craft a too-high-level item (e.g. a lockpick) fired the
  "You can't use this item yet" warning but it never drew. `OverlaySkillGui` now also renders via a
  `ScreenEvent.Render.Post` hook, so the warning always shows over the GUI. The HUD and screen paths are
  mutually exclusive (no double-draw).
- **Other denial messages were also behind/again in chat.** Spell-gating (Iron's Spellbooks
  `school_locked`/`spell_gated`, Ars Nouveau `ars_spell_gated`/`ars_familiar_gated`) used
  `sendSystemMessage` (chat, hidden behind screens), and the Apotheosis affix-rarity gate was sent
  through `PlayerMessagesCP`, which never handled that key — so it **displayed nothing at all** (and
  carried only one of its two args). All of these now route through a new `OverlayNoticeGui` /
  `NoticeOverlayCP` over-GUI banner. The Apotheosis banner now passes both the required Fortune level
  and the rarity name.
- Network `PROTOCOL_VERSION` bumped `5 → 6` for the added `NoticeOverlayCP` packet.

### Integration — lock-provider coverage audit (Runecraft)
- Re-verified lock namespaces against the Runecraft item-registry dump and the mod jars' asset folders.
  Added two previously-uncovered gear mods: **Epic Knights: Antique Legacy** (real namespace
  `antiquelegacy`, not `epic_knights_antique_legacy`) and **Call of the Yucatan** (`call_of_yucutan`).
- `docs/INTEGRATION_MATRIX.md` updated (1.3.9) with the audit provenance and corrected install flags.
- Corrected stale "scaffold / lands in batch 4" javadoc + log lines on the Saints' Dragons, Nichirin
  Dynasty, and Enigmatic Legacy integrations — their `onLivingHurt` perk effects (DRACONIC_FURY,
  NICHIRIN_BLADE, ANCIENT_STRENGTH) are already implemented, not pending.

### Docs / metadata
- `pack.mcmeta` data-pack format 12 → 15 (1.20.1); `mod_description` filled in; dead `mapping_channel`/
  `mapping_version` properties removed; `CLAUDE.md` mixin count corrected (14: 4 client / 10 common).
- README/CurseForge command names (`/skillsreload`, `/registeritem`, `/respec`, `/listskills`) and config
  paths (`config/RunicSkills/runicskills.common.json5`) corrected.
- Removed dead `CounterAttackSP` packet class and an unused YACL group lang key.
- Added a guard comment in `RegistryCommonEvents` explaining the deliberate static-via-annotation /
  instance-via-manual-register split (so it isn't "deduplicated" into breakage).

### Perk backlog (completion pass — ongoing)
New `registry/events/PerkEffectsHandler` (data-driven effect tables) plus extensions to
`CombatEventHandler`. Implemented real gameplay effects for **122** of the 340 inert perks
(allowlist 340 → 218), each gated on `isEnabled` + its existing config field and removed from
`perk_no_effect_allowlist.txt` (enforced by `PerkEffectCoverageTest`). Categories wired so far:
damage-type/conditional damage reduction, knockback resistance, block-reflect, attribute modifiers
(movement/armor/attack-speed/health/reach/luck), experience multipliers + XP-orb healing, mob & ore/
block bonus drops, mining speed, outgoing-damage conditionals (mounted/boss/weapon-id/life-steal),
arrow on-hit effects + recovery + ranged bonuses, absorption/regen/barrier, food (saturation/buffs/
iron-stomach), crafting output, anvil-cost reduction, passive item repair, and potion-duration perks
(via the `BENEFICIAL_EFFECT` attribute). Approximations are flagged with `APPROX` comments.

**1.4.0 batch — +49 perks (allowlist 218 → 169).** A second drain of the backlog, all faithful
vanilla-1.20.1 hooks gated on `isEnabled` + the perk's config field:
- *Dodge subsystem:* `DODGE_ROLL`/`EVASION`/`SPELL_DODGE` cancel a qualifying `LivingHurtEvent` and
  open a window consumed by `PHANTOM_STRIKE` (next-hit bonus).
- *Survive-lethal:* `UNDYING_WILL` (chance) and `MYTHICAL_BERSERKER` (+ rage window) cancel
  `LivingDeathEvent` to 1 HP with a 60 s lockout (not a permanent totem); `BLOODLUST` (post-kill
  attack-speed window) and `STALWART_STRIKER` (heal on hostile-mob kill) on the killer side.
- *Defense:* `THICK_SKIN` (flat), `SHIELD_WALL` (blocking), `SIEGE_DEFENSE` (inside any structure),
  `DRACONIC_CONSTITUTION` (elemental), `SAMURAI_RESOLVE` (post-hit window), `ADAPTATION` (same-source
  decay), `IMMOVABLE_OBJECT` (no knockback while blocking), `POISON_IMMUNITY`.
- *Combat/ranged:* `CRITICAL_MASTERY` (`CriticalHitEvent`), `RAPID_FIRE`/`CROSSBOW_EXPERT` (draw
  speed), `MULTISHOT_MASTERY` (`EntityJoinLevelEvent` fan), `RICOCHET`/`TRICK_SHOT` (arrow on-hit),
  `CHAIN_LIGHTNING_STRIKE`, `ENCHANTED_MISSILES`, `THORNS_MASTERY`, `TRACKING` (Glowing).
- *Attributes:* `FLEET_FOOTED`/`SWIMMERS_ENDURANCE` (move/swim speed), `WAR_TACTICIAN` (attack speed),
  `TELEKINESIS` (block reach), and the LUCK-loot group (`TREASURE_SENSE`, `SCAVENGER`, `RARE_FIND`,
  `MASTER_LOOTER`, `LUCKY_EXPLORER`, `LUCKY_FISHING`, `ADVENTURERS_LUCK`).
- *Gathering/world:* `VEIN_MINER` (bounded flood-fill), `LUMBERJACK`/`TERRAFORMER` (break speed),
  `PROSPECTOR`/`FORTUNES_FAVOR`/`SERENDIPITY` (bonus ore), `RAINBOW_LOOT` (enchanted drops),
  `NATURES_BLESSING` (heal on natural blocks), `UNBREAKABLE`/`UNBREAKING_MASTERY` (durability),
  `SMOKE_BOMB` (low-HP invisibility), `PHOENIX_RISING` (respawn HP).

The remaining 169 inert perks and the Power backlog continue in subsequent batches; many of those
describe effects with no faithful vanilla-1.20.1 hook (mod-pool mechanics, enchant-table internals,
stamina/zipline/colony/dynamic-light/ore-X-ray features) that require the optional mod's API or custom
client rendering — these stay transparently allowlisted rather than faked.

## [1.3.7] - 2026-06-10 — Configuration reliability audit

Audit pass focused on config lifecycle, the YACL config screen, item locking, and integration gating. Driven by two user reports: "disabling item locking does nothing" and "the YACL config screen does nothing."

### Fixed

- **`/skillsreload` now reloads every config file, not just lock items.** `HandlerSkill.ForceRefresh()` previously re-read only `runicskills.lockItems.json5`, so edits to `runicskills.common.json5` — including the `enableItemLocks` master toggle, the disabled perk/passive/power lists, integration toggles and multipliers — never took effect until a full restart (and the command re-synced the stale in-memory values to clients). A new `Configuration.reloadAll()` reloads all four holders before rebuilding the lock cache and re-syncing. (Root cause of "disabling item locking does nothing.")
- **The config screen no longer silently no-ops on a YACL version mismatch.** `YaclConfigUiBuilder.buildScreen` caught only `NoClassDefFoundError | RuntimeException`; a present-but-incompatible YACL (e.g. installing "the latest" YACL whose API drifted from the 3.5.0 build) throws `LinkageError` subtypes that escaped the catch, so clicking *Configure* did nothing. It now catches `LinkageError | RuntimeException`, logs one actionable ERROR naming the installed YACL version, and shows a vanilla fallback screen pointing at the log instead of a blank no-op. (Root cause of "the YACL config screen does nothing.")
- **Integration master toggles now gate lock-item generation.** Disabling e.g. `enableSpartanIntegration` previously still injected that integration's generated locks (only the finer `spartanEnableLockItems` was checked). Setting `enableItemLocks = false` now also stops *all* integration lock generation, not just enforcement.
- **`disabledPowers` is now editable in the config screen** — it was persisted but missing its `@AutoGen` annotation, unlike `disabledPerks`/`disabledPassives`.
- Config (re)generation now logs at INFO naming the file; an unparseable config is backed up to `<name>.invalid` before defaults are rewritten, so a typo never silently destroys a hand-edited file.
- Hardened registry lookups (`canUseItem/Block/Entity`, the lock tooltip, `/registeritem`) against `NullPointerException` for unregistered/modded entries.
- Defensive `ResourceLocation` parsing for user-supplied config strings (lock-item ids, advancement title conditions) — a malformed entry is logged and skipped instead of crashing config load.
- Replaced production-disabled `assert x != null` guards (Shulker-bullet mixin, client message packet) with real null checks.
- Added the 17 missing YACL config-group translation keys; documented the intentionally file-only config fields.
- Resolved an unresolved git merge conflict in `gradle.properties` and synced the version across `gradle.properties`, `VERSION`, and `CLAUDE.md`.

### Added

- JUnit test suite for config round-trip/recovery, JSON5 comment stripping, and lock-evaluation gating.
- `AUDIT.md`, `VERIFICATION.md`, and `FOLLOW_UPS.md`; a "Disabling item locking" section in the README/CurseForge description.

## [1.3.8] - 2026-06-11 — Scaled perk cap, centralized lock providers, perk-coverage guard

Builds on the perk audit (522 registered perks, 340 still inert at the start of this pass) and the
config-reliability work in 1.3.7. Adds the user-requested scaled perk cap, replaces the hard-coded lock
injection with a registry that the build enforces, broadens item-lock coverage to the mods actually in
the Lorecraft pack (and the rest of the advertised list), and lands a guard so no registered perk can be
silently inert again. See [`docs/PERK_AUDIT.md`](docs/PERK_AUDIT.md) and
[`docs/INTEGRATION_MATRIX.md`](docs/INTEGRATION_MATRIX.md).

### Added

- **Scaled active-perk cap (`perksPerGlobalLevel`).** Optional cap = `floor(globalLevel * perksPerGlobalLevel)`, on top of the existing flat `maxActivePerks`. When both are non-zero the smaller wins; a derived cap of 0 (very low global level) never overrides the flat cap. Pure math lives in [`PerkCapMath.computeEffectiveCap`](src/main/java/com/otectus/runicskills/common/util/PerkCapMath.java) (unit-tested), wrapped by `RegistryPerks.effectivePerkCap`. Enforced server-side on the 0→1 enable transition in `TogglePerkSP`, synced to clients via `CommonConfigSyncCP`, and surfaced as an "Active perks: X / Y" line in the perk tooltip (`tooltip.perk.active_cap`).
- **Centralized lock-provider registry.** New [`LockItemProvider`](src/main/java/com/otectus/runicskills/integration/lock/LockItemProvider.java) + [`LockProviderRegistry`](src/main/java/com/otectus/runicskills/integration/lock/LockProviderRegistry.java) replace the hard-coded chain in `HandlerSkill.injectIntegrationItems()`. The 7 curated integrations (Spartan, Blood Magic, Ice & Fire, Locks, Samurai Dynasty, More Vanilla, Jewelcraft) are wrapped as adapters with identical order/semantics (`putIfAbsent` manual-override precedence preserved).
- **Iron's Spells item locks.** New `IronsSpellbooksLockProvider` (registry-scan, no ISS API imports so it's safe to register unconditionally) locks spellbooks, staves, scrolls, mage armor, rings/amulets and upgrade orbs by Magic/Intelligence/Endurance. Config: `enableIronsSpellbooksLockItems`, `ironsLevelMultiplier`.
- **Registry-driven discovery for installed + advertised mods.** New `GenericNamespaceLockProvider` + [`LockGen`](src/main/java/com/otectus/runicskills/integration/lock/LockGen.java) keyword classifier add lock coverage for Epic Knights (5 namespaces incl. Japanese Armory / Dark Ages / Ice & Fire add-ons), Aquaculture, Galosphere, Undergarden, Deeper Darker, Dragonsteel, Cataclysm, Mowzie's Mobs, Farmers Delight, Siege Machines, plus the previously-uninjected README mods (Enigmatic Legacy, Fantasy Armor, Nature's Aura, Bosses of Mass Destruction, Jet & Elias, Nichirin Dynasty, Saints Dragons, Stalwart Dungeons). Shared config: `discoveredLockLevelMultiplier`, `disabledDiscoveredLockMods`. Providers no-op when their mod is absent.
- **Perk-coverage guard + backlog.** [`PerkEffectCoverageTest`](src/test/java/com/otectus/runicskills/registry/PerkEffectCoverageTest.java) asserts every registered perk has an effect site or is in the explicit backlog ([`perk_no_effect_allowlist.txt`](src/test/resources/perk_no_effect_allowlist.txt)), that implemented perks are removed from the backlog, and that the backlog has no dead names — so the inert set is transparent and can only shrink.
- **Build/test guards.** `checkLockProviders` Gradle task (wired into `check`) + `LockProviderRegistryTest` fail the build if an integration with `generateLockItems()` is not registered.

### Fixed (perks wired)

- **Deferred Strength perks** — `CLEAVE` (AoE splash to nearby enemies, reentrancy-guarded), `TITANS_GRIP` (heavy-Spartan-weapon + offhand damage bonus), `GLADIATOR` (offhand-shield melee bonus), `RUNIC_MIGHT` (runic-weapon damage bonus). `PRIMAL_FURY`/`UNSTOPPABLE_FORCE` were already implemented and verified. New config: `cleavePercent`, `cleaveRangeBlocks`, `titansGripPercent`.
- **Constitution defensive perks** — `SEARING_RESISTANCE`, `WITHER_RESISTANCE`, `ARMOR_OF_FAITH`, `SURVIVAL_INSTINCT`, `BLOOD_SHIELD`, `RUNIC_FORTIFICATION` now reduce the appropriate damage type (additive, clamped at 80% total) via a new `onLivingHurtConstitutionDefense` handler, reusing their existing `*Percent` config fields.

### Known limitations

- 340 perks remain inert and are tracked in the enforced backlog (`perk_no_effect_allowlist.txt`); they are mod-gated (their optional mod is absent) or pending native implementation in later batches. The guard guarantees none are *silently* inert.
- `TITANS_GRIP` and `GLADIATOR` approximate their lang text (Spartan two-handed bypass / shield bash) without invasive mixins into Spartan internals; see `docs/PERK_AUDIT.md`.
- Lock providers for mods not present locally are compiled and registered but only runtime-verified when that mod is installed.

## [Unreleased] — Phase 1 dead-perk wiring

Implementation of Phase 1 from the 2026-05 perk audit (`~/.claude/plans/you-are-a-senior-zazzy-thompson.md`). The audit confirmed five user-reported "active but ineffective" perks (Mowzie's Might, Sniper, Eagle Eye, Spell Quickening, Key Forge) as part of a wider pattern: 358 of 522 registered perks had zero references outside `RegistryPerks.java`. Phase 1 wires the five named perks; Phases 2-9 cover the remaining trees in subsequent commits.

### Fixed

- **Mowzie's Might** — `MOWZIES_MIGHT` was registered with config/lang/texture but never read by any code. [`MowziesMobsIntegration.onLivingHurt`](src/main/java/com/otectus/runicskills/integration/MowziesMobsIntegration.java#L57) now applies `mowziesMightPercent` damage bonus when the player's main hand matches a curated Mowzie weapon allow-list (`geomancer_staff`, `ice_crystal`, `naga_fang_dagger`, `solar_spear_item`, `axe_of_a_thousand_metals_item`, `wrought_axe`, `spear`, `dart`, `wing_blade`, `spear_throwing`). Missing IDs from the list are filtered at runtime via `ForgeRegistries.ITEMS.containsKey`, so updates to Mowzie that rename or drop items never crash.
- **Sniper** — `SNIPER` had no distance check in the arrow handler. [`CombatEventHandler.onPlayerShootArrow`](src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java#L549) now adds a flat `sniperPercent` bonus when the impact distance exceeds the new `sniperDistanceThreshold` config (default 30 blocks).
- **Eagle Eye** — `EAGLE_EYE` similarly had no distance scaling. Same handler now adds a linear ramp from `eagleEyeRampStartBlocks` (default 10) to `eagleEyeRampFullBlocks` (default 40); composes additively with Sniper for long-range archer builds.
- **Key Forge** — `KEY_FORGE` was registered against a non-existent "Sniper's Skills" integration in the audit notes; the real source is Locks Reforged (`locks:key` → `locks:master_key`). New [`LocksIntegration.onItemCrafted`](src/main/java/com/otectus/runicskills/integration/LocksIntegration.java#L40) handler rolls `keyForgePercent` chance to replace a crafted `locks:key` stack with a `locks:master_key` of the same count. LocksIntegration is now event-bus-registered in [`RunicSkills.java`](src/main/java/com/otectus/runicskills/RunicSkills.java#L121) when the locks mod is loaded.

### Deprecated

- **Spell Quickening** — `SPELL_QUICKENING` was a duplicate of the working `QUICKENING` perk ([`IronsSpellbooksIntegration.java:468`](src/main/java/com/otectus/runicskills/integration/IronsSpellbooksIntegration.java#L468) wires `QUICKENING` to `CAST_TIME_REDUCTION`, but no equivalent code exists for `SPELL_QUICKENING`). The two had overlapping tooltips and adding a second modifier would have stacked invisibly with the working perk. `spellQuickeningRequiredLevel` default is now `-1` so the registration's `< 0` gate disables the perk entirely; the `@IntField(min = 1)` annotation was relaxed to `min = -1` to permit the sentinel value. Players who already unlocked Spell Quickening keep their skill points; only the unlockable node is removed from the Magic tree.

### Added

- **`sniperDistanceThreshold`** (default 30) — Sniper's minimum impact distance, [`HandlerCommonConfig.java:1083`](src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java#L1083).
- **`eagleEyeRampStartBlocks`** (default 10) and **`eagleEyeRampFullBlocks`** (default 40) — Eagle Eye's ramp endpoints, [`HandlerCommonConfig.java:943`](src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java#L943).

### Phase 2A: Strength tree easy-wins

Four additional Strength perks wired into the existing [`CombatEventHandler.onLivingHurtStrengthAttacker`](src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java#L284) block. All reuse the additive-bonus composition pattern (`bonus += dmg * pct/100`).

- **PRIMAL_FURY** — bonus damage when the player's HP fraction is below 50%. Stacks with Berserker (different trigger surface — Berserker is crit-only).
- **SPARTANS_DISCIPLINE** — bonus damage when the main hand is any `spartanweaponry:*` item. Namespace match, no item-allow-list to keep stale.
- **SACRED_FIRE** — sets the target on fire for 4 seconds. No damage bonus; the perk has no `Value` array — it's a binary on/off trigger.
- **UNSTOPPABLE_FORCE** — `unstoppableForcePercent` chance per hit to apply Slowness II for 30 ticks (~1.5s). The percent is the proc chance, not a damage multiplier — matches the tooltip ("%s chance to briefly stun").

### Audit follow-up

- **Phase 2B (remaining Strength)** — BLOODLUST (kill→attack-speed; needs new transient-modifier infrastructure), BLOOD_FURY (Blood Magic-gated crit lifesteal), CLEAVE (multi-target AoE), TITANS_GRIP (shield + 2H — likely mixin), MYTHICAL_BERSERKER (last-stand variant), STALWART_STRIKER (dungeon-mob heal), WARLORDS_PRESENCE (ally aura), GLADIATOR (no shield-bash mechanic in vanilla to hook), POLEARM_MASTERY (Spartan polearm subset), DRAGON_BONE_MASTERY (Ice and Fire item allow-list), CATACLYSMS_WRATH (Cataclysm item allow-list), CHAIN_LIGHTNING_STRIKE (ISS lightning entity spawn), RUNIC_MIGHT (source mod unclear), SIEGE_BREAKER (Cataclysm boss detection).
- **Phase 3 (Dexterity tree, 36 dead perks)** — additional arrow perks (PRECISION_SHOT, SHARPSHOOTER, MULTISHOT_MASTERY, ...) and movement perks (ACROBAT, FLEET_FOOTED, ...).
- **Phase 4 (Magic tree, 35 dead perks)** — most are ISS attribute reconciliations via the existing `reconcileModifier` pattern.
- Phases 5-9 — remaining aptitudes (Constitution, Endurance, Intelligence, Wisdom, Building, Tinkering, Fortune).

## [Unreleased] — R0 Hardening

Implementation of the R0 hardening release from `plan-the-full-implementation-floofy-quokka.md` against findings in `perk-audit-2026-05-18.md`. Surgical bug fixes only; no perk content added.

### Fixed

- **B1 (P1) — `STEALTH_MASTERY` visibility values inverted.** [`mixin/MixLivingEntity.java:195`](src/main/java/com/otectus/runicskills/mixin/MixLivingEntity.java#L195) now reads `Value[1]` (sneak %) when crouched and `Value[0]` (unsneak %) when standing, matching the registration order in [`RegistryPerks.java:111-113`](src/main/java/com/otectus/runicskills/registry/RegistryPerks.java#L111). Pre-fix: crouching made the player MORE visible. The fix also uses `getActiveValue(player)` to pre-emptively bake in rank-awareness (R1 work).
- **B5 (P3) — `STEALTH_MASTERY` mixin guard order.** `isEnabled` check folded into the outer `null`-guard so the value array is only read when the perk would actually apply.
- **B6 (P3) — Integration `@SubscribeEvent` handlers ungated by `isModLoaded()`.** Added an early-return `if (!isModLoaded()) return;` to the `onLivingHurt` / `onItemUseFinish` handlers in [`IceAndFireIntegration`](src/main/java/com/otectus/runicskills/integration/IceAndFireIntegration.java#L196), [`MowziesMobsIntegration`](src/main/java/com/otectus/runicskills/integration/MowziesMobsIntegration.java#L22), [`CataclysmIntegration`](src/main/java/com/otectus/runicskills/integration/CataclysmIntegration.java#L33), [`FarmersDelightIntegration`](src/main/java/com/otectus/runicskills/integration/FarmersDelightIntegration.java#L24). These integration classes are loaded unconditionally; without the guard, every relevant Forge event paid the cost of a `ForgeRegistries` lookup even when the target mod was absent.
- **B7 (P3) — `ARS_HEDGEWITCH` declared two `Value`s the handler never read.** [`ArsNouveauIntegration.java:121` and `:271`](src/main/java/com/otectus/runicskills/integration/ArsNouveauIntegration.java#L121) now read the cost% and damage% from `perk.getActiveValue(player)[0]` / `[1]` instead of `HandlerCommonConfig.HANDLER.instance().arsHedgewitchCostPercent` / `…DamagePercent`. Tooltip substitution and gameplay now share the same source of truth.
- **Curios NPE silently swallowed.** [`handler/HandlerCurios.java:38`](src/main/java/com/otectus/runicskills/handler/HandlerCurios.java#L38) `catch (NullPointerException ignored)` replaced with a logged catch (`LOGGER.warn`). Still defaults to `DENY` for safety, but unexpected NPEs now surface in the log instead of hiding bugs.

### Added

- **S6 — Perk-keyed cooldown helpers** on `SkillCapability`: `getCooldown(Perk)` and `setCooldown(Perk, int)` derive the cooldown key as `"perk." + perk.getName()`. New perks needing a cooldown no longer require updating the hardcoded `COOLDOWN_*` constants. Existing constants (`COOLDOWN_LIMIT_BREAKER`, `COOLDOWN_PERK_SWAP`, `COOLDOWN_COUNTER_ATTACK`, `COOLDOWN_COUNTER_ATTACK_TIMER`) retained for the three existing call sites.
- **`disabledPowers` config field** in `HandlerCommonConfig`. Pre-existing in-progress Powers code referenced this without a declaration; added as a `List<String>` matching the `disabledPerks` / `disabledPassives` shape. Comment documents its semantics: equipped powers from the list are filtered out at runtime.

### Deferred

- **B8 — 17 non-uniform `RequiredLevel` field name standardisation.** Investigation showed the rename touches ~40 fields across `HandlerCommonConfig`, `RegistryPerks`, `ArsNouveauIntegration`, `DynamicConfigSyncCP`, and YACL UI lang labels (12+ locales). Combined with the existing-config-file migration risk, B8 doesn't fit R0. Tracked for a dedicated hygiene release after R13.

### Audit follow-up

- B2 (multi-rank values systemically ignored): completed in R1 (see below).
- B3 + B4 (Apotheosis rarity ordinal + interactor race): rolling out in R2.
- The 373 inert perks: rolling out per-tree in R3–R12.

## [Unreleased] — R1 Multi-rank rollout (B2)

Implementation of the R1 multi-rank rollout from `plan-the-full-implementation-floofy-quokka.md`. Strategy (a): mechanical replacement of every `perk.getValue()[N]` callsite with `perk.getActiveValue(player)[N]` so rank-aware values flow through to gameplay.

### Fixed

- **B2 (P1, systemic) — Multi-rank perk values silently ignored.** 28 `RegistryPerks.<NAME>.get().getValue()[N]` callsites across `CombatEventHandler` (10), `CraftingEventHandler` (10), `TickEventHandler` (4), `BotaniaIntegration` (16), `ApotheosisIntegration` (1), `IronsSpellbooksIntegration` (1), `MixLivingEntity` (2), `MixPlayer` (1), `MixVillager` (1), `MixEnchantmentMenu` (1), `TreasureHunterPerk` (1) replaced with `getActiveValue(player)[N]`. Previously, a multi-rank perk would always apply rank-1 values regardless of the player's actual rank. No currently-shipping perks were multi-rank, so this is a latent-bug fix, not a behavior change for existing playthroughs — but it unblocks every future multi-rank perk in R3–R12.

### Changed

- **`TreasureHunterPerk.drop()` signature.** Now takes a `Player` argument so rank-aware value lookup has a player context. Only caller (`CraftingEventHandler.java:49`) was updated.

## [Unreleased] — R3 Strength tree (in progress, 15 of 35 perks)

Per-tree perk-content rollout from `plan-the-full-implementation-floofy-quokka.md`, continued. Batch 1 closed the trivial weapon/armor modifiers; batch 2 closes the always-available, no-new-integration subset (state-bearing perks: target-HP gates, last-attacker memo, recent-hit ring buffer, save-from-fatal); batch 3 scaffolds the four new mod-integration classes; batch 4 wires the mod-gated Strength perks through those scaffolds via reflective namespace matching (no new build deps). 20 perks remain in R3: 6 vague-batch (deferred for clarification) + 10 in existing integrations (deferred to a follow-up cleanup batch) + 4 cosmetic/system-rewrite perks audit-flagged out of plan scope.

### Added — Strength perks (batch 1: trivial weapon/armor modifiers)

- **WEAPON_MASTER.** Flat % bonus damage on melee hits when the player's main hand holds a Sword / Axe / Trident. Tools (pickaxe, shovel) explicitly excluded. Multi-rank-aware via `getActiveValue(player)`.
- **ARMOR_PIERCING.** Bonus damage scales linearly with target armor (0 → 20 armor). Approximates "ignore X% of armor" in a `LivingHurtEvent`-side calculation rather than recomputing vanilla's armor formula.
- **HEAVY_STRIKES.** Bonus damage when the target is actively blocking with a shield (`target.isBlocking()`).
- **WARMONGER.** Bonus damage when the target's armor value > 0. Composes multiplicatively with `HEAVY_STRIKES` and `ARMOR_PIERCING` per lang descriptions.
- **POWER_ATTACK.** Critical hits get an additional % damage on top of vanilla's 1.5×. Wired into `onPlayerCriticalHit` after the existing `BERSERKER` branch.

### Added — Strength perks (batch 2: state-bearing on-hit triggers)

- **EXECUTE.** Bonus % damage when the target's post-armor HP fraction is below 25%. Threshold baked into the lang description; no config knob. Composes into the bonus accumulator alongside the batch-1 stack.
- **DEVASTATING_BLOW.** Bonus % damage on the opening hit (`target.getHealth() >= target.getMaxHealth()`); the `>=` (rather than `==`) tolerates float regen edge-cases.
- **VENGEANCE.** New victim-side handler `onLivingHurtStrengthVictim` (LOWEST priority) records the last `LivingEntity` to damage the player into a transient static `Map<UUID, AttackerMemo>` on `CombatEventHandler`. The attacker-side handler reads the memo and applies the % bonus when the current target UUID matches and the hit happened within `vengeanceWindowTicks` (default 600 ticks / 30 s, configurable). Self-damage and indirect damage (no living entity source) skip memo recording. Mirrors the R2 `ApotheosisIntegration.recentInteractors` pattern — transient, no NBT, no protocol bump.
- **BLADE_STORM.** Recent-hit ring buffer per attacker UUID (`Map<UUID, Deque<HitRecord>>`, cap 16, synchronized — modelled on `PowerRuntime.SpellHistory`). Each attacker-side hit pushes a `HitRecord(targetUUID, gameTime)`; when the count of distinct target UUIDs in the last `bladeStormWindowTicks` (default 80) reaches `bladeStormMinTargets` (default 2), the player gets a transient `ATTACK_SPEED` `AttributeModifier` (`MULTIPLY_BASE`, +`Value[0]%`) keyed by a deterministic UUID, refreshed on each subsequent qualifying hit and removed by the 100-tick `onServerTick` pruner when the window lapses. Attack-speed not damage — matches the lang description.
- **LAST_STAND.** Save-from-fatal interpretation. The new victim handler clamps a hit that would otherwise kill the player (`incoming >= currentHP`) to leave 1 HP, opens a 40-tick bonus-damage window in a static `Map<UUID, Long>`, and sets the S6 perk-keyed cooldown to 1200 ticks (60 s) via `cap.setCooldown(perk, 1200)`. While the active window is current, the attacker-side handler applies `+Value[0]%` outgoing damage. The clamp never amplifies a hit; further fatal hits inside the cooldown kill normally.

Three new tuning fields in `HandlerCommonConfig`: `vengeanceWindowTicks` (default 600), `bladeStormWindowTicks` (default 80), `bladeStormMinTargets` (default 2). All server-side reads — not synced to clients, no protocol bump.

New static state on [`CombatEventHandler`](src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java): `RECENT_HITS`, `LAST_ATTACKER`, `LAST_STAND_ACTIVE_UNTIL`, `BLADE_STORM_ACTIVE_UNTIL`. All four maps are pruned every 100 ticks in `onServerTick(Phase.END)`; the BLADE_STORM pruner additionally strips the `ATTACK_SPEED` modifier from any online player whose window has lapsed. State is transient (no NBT, no sync, dies with the server) — matches the R2 Apotheosis precedent.

### Added — Integration scaffolding (batch 3)

Four new mod-integration classes registered on the Forge bus via `RunicSkills.tryLoadIntegration(modId, fqcn)`. Each is a reflective-load class with a no-op `@SubscribeEvent onLivingHurt(LivingHurtEvent)` handler at NORMAL priority that batch 4 will populate with perk-effect code. The reflective-load path keeps the JVM from resolving the upstream APIs when the target mod is absent (same pattern as the existing Botania / Ars Nouveau / ISS / Apotheosis integrations).

- **[`SaintsDragonsIntegration`](src/main/java/com/otectus/runicskills/integration/SaintsDragonsIntegration.java)** (mod id `saintsdragons`) — landing site for `DRACONIC_FURY`, `GLADIATOR`, `TROPHY_HUNTER` (R3 batch 4) plus the ~5 cross-tree perks (`DRAGON_BREATH_SHIELD`, `DRAGON_HEART`, `HEARTY_FEAST`, `DRAGON_RIDER`, `UNDYING_WILL`) wired in R4/R5/R6.
- **[`NichirinDynastyIntegration`](src/main/java/com/otectus/runicskills/integration/NichirinDynastyIntegration.java)** (mod id `nichirin_dynasty`) — landing site for `NICHIRIN_BLADE`, `DRAGON_BONE_MASTERY` (R3 batch 4) plus `BLOODLUST` (later).
- **[`SamuraiDynastyIntegration`](src/main/java/com/otectus/runicskills/integration/SamuraiDynastyIntegration.java)** (mod id `samurai_dynasty`) — pre-existing 277-line lock-generator class extended with event-handler scaffolding. Lock-generation paths (weapon / armor / katana / accessory) and the HandlerSkill call site are untouched. Landing site for `CLEAVE`, `TITANS_GRIP`, `SAMURAIS_EDGE` (R3 batch 4) plus 6 cross-tree perks (`LIGHTNING_ROD`, `NINJA_TRAINING`, `OBSIDIAN_SKIN`, `POISON_ARROW`, `WIND_RUNNER`, `SAMURAI_RESOLVE`) across R5/R6.
- **[`EnigmaticLegacyIntegration`](src/main/java/com/otectus/runicskills/integration/EnigmaticLegacyIntegration.java)** (mod id `enigmaticlegacy`) — landing site for `ANCIENT_STRENGTH`, `CATACLYSMS_WRATH`, `CURSE_WARD` (R3 batch 4) plus ~8 cross-tree perks (`ARMOR_OF_FAITH`, `ARTIFACT_HUNTER`, `ENIGMATIC_PROTECTION`, `ENIGMATIC_UNDERSTANDING`, `ENIGMATIC_VITALITY`, `ENIGMATIC_WISDOM`, `MYSTIC_ANALYSIS`, `SAGES_FOCUS`, `SOUL_SUSTENANCE`) across R4/R7/R11.

Bootstrap wiring added to [`RunicSkills.java`](src/main/java/com/otectus/runicskills/RunicSkills.java) alongside the existing reflective-load block. Each scaffold class includes a defensive `isModLoaded()` guard inside its handler (R0 B6 hardening pattern — belt-and-braces against a future refactor changing the registration path).

### Added — Strength perks (batch 4: mod-gated, reflective namespace match)

Four mod-gated perks wired into their batch-3 scaffold handlers + one always-available perk (TROPHY_HUNTER) in `CombatEventHandler`. All five detect their target items / entities by ResourceLocation namespace + path patterns instead of importing the upstream mod APIs — no new build dependencies, no Compat-wrapper classes, no stub jars.

- **NICHIRIN_BLADE** ([`NichirinDynastyIntegration.onLivingHurt`](src/main/java/com/otectus/runicskills/integration/NichirinDynastyIntegration.java)). Bonus % damage when the player's main-hand item is in the `nichirin_dynasty` namespace.
- **SAMURAIS_EDGE** ([`SamuraiDynastyIntegration.onLivingHurt`](src/main/java/com/otectus/runicskills/integration/SamuraiDynastyIntegration.java)). Bonus % damage when the main-hand item is in `samurai_dynasty` and its registry-id path contains `katana`, `wakizashi`, `odachi`, or `nagamaki` — matches the lang's "katana weapons" while excluding polearms, clubs, and short blades.
- **ANCIENT_STRENGTH** ([`EnigmaticLegacyIntegration.onLivingHurt`](src/main/java/com/otectus/runicskills/integration/EnigmaticLegacyIntegration.java)). Bonus % damage when the main-hand item is in `enigmaticlegacy`.
- **DRACONIC_FURY** ([`SaintsDragonsIntegration.onLivingHurt`](src/main/java/com/otectus/runicskills/integration/SaintsDragonsIntegration.java)). Bonus % damage AND `target.setSecondsOnFire(2 + pct/25)` when the main-hand item is in `saintsdragons` and its path contains a draconic-anatomy keyword (`fang`/`claw`/`horn`/`tooth`/`wing`/`scale`/`dragon`). Two-part effect honours the lang's "bonus fire damage" with a visible ignite alongside the % bonus.
- **TROPHY_HUNTER** (`CombatEventHandler.onLivingHurtStrengthAttacker`). Bonus % damage when the target qualifies as elite/boss by either (a) entity-type namespace ∈ `trophyHunterBossNamespaces` (default: apotheosis, cataclysm, iceandfire, mowziesmobs, saintsdragons, bossesofmass), OR (b) `MOB_CATEGORY == MONSTER && getMaxHealth() >= trophyHunterMinHealthForElite` (default 100). Vanilla Ender Dragon (200 HP) and Wither (300 HP) match (b); Elder Guardian (80 HP) does not at the default threshold — adjust if it should.

New shared utility [`integration/IntegrationHelpers.java`](src/main/java/com/otectus/runicskills/integration/IntegrationHelpers.java) with three null-safe static methods (`itemFromMod`, `entityFromMod`, `itemPathContains`). Replaces the inline namespace-match idiom every integration was re-implementing. Reused by all 5 batch-4 handlers; older integrations (Spartan, IceAndFire, Mowzies, FarmersDelight) still use their inline copies but could migrate in a future hygiene pass.

Two new config fields in `HandlerCommonConfig` for TROPHY_HUNTER tunables: `trophyHunterBossNamespaces` (`List<String>`) and `trophyHunterMinHealthForElite` (int, default 100). The four mod-gated perks reuse their existing `*Percent` fields. No protocol bump.

### Deferred — R3 vague-batch (expanded from 3 to 6 — pending clarification session)

Six Strength perks register but their lang descriptions don't uniquely determine a mechanic. Per the parent plan's vague-perk pause protocol, they're held for a single decision-batch session before R3 ships as `1.3.0`:

- **`RUNIC_MIGHT`** (original) — "Runic ore weapons deal %s bonus damage." No vanilla "runic ore"; candidate gates: Apotheosis-affixed items, Ars Nouveau Source weapons, Ice and Fire dragonsteel, a datapack-driven item tag, or the mod's own items.
- **`PRIMAL_FURY`** (original) — "When below 50%% health, your desperation grants %s bonus attack damage." Threshold is baked into the lang but has no Value slot; unclear if 50% should be configurable, and how it stacks with `LAST_STAND` active window.
- **`UNSTOPPABLE_FORCE`** (original) — "Your strikes have a %s chance to briefly stun enemies." No vanilla stun effect, no duration slot in Value array. Candidate effects: `Slowness V`, `Wither I`, `irons_spellbooks:stun` (if ISS loaded), or a per-tick `setDeltaMovement(ZERO)`.
- **`CLEAVE`** (batch 4) — "Your melee attacks cleave through enemies, hitting additional nearby targets." No `Value[]` declaration in `RegistryPerks`, no `cleavePercent` config. Need: AABB radius, target-count cap, damage scaling (flat split, per-target percent, or both).
- **`TITANS_GRIP`** (batch 4) — "Your immense strength allows you to wield two-handed weapons alongside a shield." No `Value[]`. Need: definition of "two-handed weapon" (per-item-id list, per-attribute, or `SamuraiDynastyIntegration.WeaponType` enum), and the actual relaxation mechanic (allow shield in off-hand while wielding two-handed? bonus damage? speed-penalty removal?). Gated on `SpartanIntegration` per registration.
- **`GLADIATOR`** (batch 4) — "Your shield bash damage is increased by %s." Vanilla has no shield bash. Need: definition (mod-provided shield-bash event detection? Re-interpret as "damage while blocking with shield raised"? Spartan Shields integration?). Currently always-available — the resolution may move the gate.

Implementation lives in [`CombatEventHandler.onLivingHurtStrengthAttacker`](src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java) at NORMAL priority (attacker side) and [`CombatEventHandler.onLivingHurtStrengthVictim`](src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java) at LOWEST priority (victim side). Compile-clean; full `./gradlew build` passes including `:checkSidedImports`.

### Fixed (1.3.4 hotfix — boot crash regression)

- **`IllegalArgumentException: Duplicate registration rookie` at mod construction.** The 1.3.3 deploy crashed on first boot inside [`RegistryTitles.load`](src/main/java/com/otectus/runicskills/registry/RegistryTitles.java) — the per-title `forEach` over `HandlerTitlesConfig.titleList` invoked `DeferredRegister.register("rookie", …)` twice, with the second call throwing. Investigation found the on-disk `runicskills.titles.json5` has exactly one rookie entry, the compiled `List.of(new TitleModel(), …)` default has exactly one (the no-arg `TitleModel()` ctor sets `TitleId = "rookie"`), and YACL 3.6.6's `GsonConfigSerializer.loadSafely` does a plain reflective field-replacement (`field.set(instance, gson.fromJson(…))`). The doubling could not be explained from bytecode alone — the most plausible cause is a behavior shift between YACL 3.5.0 (compile-against version) and 3.6.6 (runtime version) in handling fields initialized to immutable `List.of(…)` defaults. Fix: defensive dedup in `RegistryTitles.load` — a `HashSet<String>` of seen `TitleId`s skips second occurrences with a one-line warning, and a null/empty TitleId guard precedes it. Correct regardless of where the doubling enters; surface area minimal. Investigation notes preserved in [the continue-r3-strength-tree plan file](C:\Users\crims\.claude\plans\continue-r3-strength-tree-curried-porcupine.md).

### Fixed (1.3.5 hotfix — world-join crash regression)

- **`Connection Lost — Invalid player data` on world entry.** The 1.3.4 dedup fixed the boot crash but exposed a downstream NPE: `serverPlayerTitles` iterated the raw `HandlerTitlesConfig.HANDLER.instance().titleList` (still containing the runtime-duplicated `TitleModel`) and called `getTitle().setRequirement(...)` on every entry. The skipped duplicate's `_title` field was never initialized (the 1.3.4 dedup skipped its `title.registry(TITLES)` call) → NPE → Forge aborts player placement and disconnects the client with the vanilla "Invalid player data" message. Fix: `RegistryTitles.load` now also replaces `HandlerTitlesConfig.HANDLER.instance().titleList = List.copyOf(uniqueTitles)` after registration, so downstream consumers see only the registered entries. `serverPlayerTitles` also gains a defensive `getTitle() != null` guard with a warning log as defense-in-depth.

### Fixed (1.3.6 hotfix — world-join crash, third stage: unregistered Powers packets)

- **`IllegalArgumentException: Invalid message PowerOverridesSyncCP` on world entry (same "Invalid player data" disconnect message).** Pre-existing latent bug unmasked by the 1.3.5 title-NPE fix. The 1.1.0 CHANGELOG entry claimed `PROTOCOL_VERSION` was bumped from 3 → 5 and that three new Powers packets (`PowerOverridesSyncCP`, `PowerProcCP`, `PowerEquipSP`) were added, but [`ServerNetworking.init`](src/main/java/com/otectus/runicskills/network/ServerNetworking.java#L21) had `PROTOCOL_VERSION = "4"` and zero `registerMessage(...)` calls for the three Powers packets. `PlayerLifecycleHandler.onPlayerLoggedInEvent` calls `PowerOverridesSyncCP.sendToPlayer` on join, which made Forge's `IndexedMessageCodec.build` throw `Invalid message …`; Forge's `firePlayerLoggedIn` propagated the exception and the server aborted player placement. The 1.3.4 title NPE was firing earlier in the same player-join code path and masked this — once 1.3.5 fixed the NPE, this surfaced. Fix: register `PowerOverridesSyncCP` (PLAY_TO_CLIENT), `PowerProcCP` (PLAY_TO_CLIENT), and `PowerEquipSP` (PLAY_TO_SERVER) in `ServerNetworking.init`, and bump `PROTOCOL_VERSION` to `"5"` to match the documented 1.1.0 wire format. No new build dependencies; no wire-format change beyond what 1.1.0 already documented.

## [Unreleased] — R2 Apotheosis hardening (B3 + B4)

Implementation of the R2 Apotheosis hardening release from `plan-the-full-implementation-floofy-quokka.md`.

### Fixed

- **B3 (P2) — Apotheosis rarity ordinal hardcode replaced with name-keyed lookup.** [`ApotheosisIntegration.getRequiredFortuneLevel`](src/main/java/com/otectus/runicskills/integration/ApotheosisIntegration.java#L80) and [`countRareAffixItems`](src/main/java/com/otectus/runicskills/integration/ApotheosisIntegration.java#L408) now match rarity by `DynamicHolder.getId().getPath()` (`"common"`, `"uncommon"`, `"rare"`, `"epic"`, `"mythic"`, `"ancient"`) instead of `ordinal()`. Unknown rarity paths log once via `warnOnceForUnknownRarity` and default-deny (`Integer.MAX_VALUE`) — a future Apotheosis update that inserts a new tier can no longer be silently equipped without a config update.
- **B4 (P2) — Apotheosis `lastInteractingPlayer` static-field race replaced with bounded UUID-keyed map.** Replaced the single `private static Player lastInteractingPlayer` with `recentInteractors: ConcurrentHashMap<UUID, Long>` keyed on player UUID and time-stamped to the current game tick. Reads via `resolveInteractor()` return only the most-recent interactor inside `INTERACTION_WINDOW_TICKS` (20 ticks / 1 second). Entries past the window are pruned every 100 ticks from `onPlayerTickPhase2a`. Two-player concurrent interactions no longer cross-pollinate socketing attribution; if no recent interactor exists, the perk-aware branch defaults to no-op and Apotheosis vanilla behavior takes over.

## [1.1.0] - 2026-05-08

Consolidated post-1.0.0 release. Bundles the Botania integration (originally tagged 1.0.1), the magic-tree cross-mod expansion (originally tagged 1.0.2), the eight-alpha Powers framework development (originally tagged 1.1.0-alpha1..alpha8), the dedicated-server YACL safety refactor that the README has been advertising since 1.0.1 was actually fixed, the L2Tabs class-load fix (same shape as the 1.0.0 Legendary Tabs fix, missed at the time), and triage of three CurseForge user reports. **120 perks across five mod integrations + 75 Powers metadata stubs (28 with wired behavior) + a meaningful capability change** — the mod now actually runs on dedicated servers without YACL installed.

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

### Added — Powers framework (75 catalogue, 28 wired, 11 of 15 Crowns)

A new tiered abilities subsystem (Marks / Seals / Crowns) sits as a peer to the existing Perks tree. 75 Powers metadata entries are registered (5 per ISS school × 9 + 5 per cross-cutting category × 6); 28 have wired event-handler behaviors; the remaining 47 are equip-eligible no-op stubs that the UI can enumerate while their handlers ship in a follow-up. ISS-school Powers null-register when `irons_spellbooks` is absent; cross-cutting Powers are mod-agnostic. See `RUNIC_SKILLS_POWERS.md` for the full design spec.

**Framework infrastructure:**
- **`Power` / `PowerTier` / `PowerSchool`** ([registry/powers/](src/main/java/com/otectus/runicskills/registry/powers/)) — `Power` is pure metadata (tier, school `ResourceLocation`, governing skill, level gate, ICD, optional required mod-id); behavior lives in `PowerEventDispatcher` keyed by `Power.getName()`, matching the existing Perk pattern.
- **`RegistryPowers`** — `DeferredRegister<Power>` parallel to `RegistryPerks`, with 75 `RegistryObject<Power>` entries and `getPower`/`getByTier`/`getBySchool`/`isDisabled` static helpers.
- **`SkillCapability` extended** — new fields `equippedMarks: List<String>` (cap 5), `equippedSeals: List<String>` (cap 3), `equippedCrown: String`, `powerCooldowns: Map<String,Long>` (absolute game-time), `powerWindows: Map<String,Long>`. NBT keys `power.equippedMarks` / `power.equippedSeals` / `power.equippedCrown` / `powerCooldowns` / `powerWindows`, round-tripped in `serializeNBT` / `deserializeNBT` / `copyFrom`. Slot caps enforced inside `equipPower()`.
- **`PowerOverrides` JSON loader** at `data/<ns>/powers/*.json`. Schema: `{ required_skill_level, icd_ticks, values: { k: number } }` — every field optional; packs can tune one knob without overriding the rest. `PowerOverridesReloadListener extends SimpleJsonResourceReloadListener` mirrors the `PerkGroupsReloadListener` pattern. Dispatcher reads via `PowerOverridesManager.valueOr(power, "damage_multiplier_bonus", fallback)`.
- **Three new network packets** — `PowerOverridesSyncCP` (server → client, pushes tuned values on login and `/skillsreload`), `PowerProcCP` (server → client, 8-particle enchant-rune burst at the player for proc feedback), `PowerEquipSP` (player → server, equip/unequip with server-authoritative skill/tier/mod-gate validation).
- **`PowerEventDispatcher`** ([registry/events/PowerEventDispatcher.java](src/main/java/com/otectus/runicskills/registry/events/PowerEventDispatcher.java)) — single Forge-bus subscriber registered only when ISS is loaded. Subscribes to `SpellOnCastEvent`, `SpellDamageEvent`, `LivingDamageEvent`, `LivingDeathEvent`, `LivingHurtEvent`, `CriticalHitEvent`, `SpellTeleportEvent`, `LivingDropsEvent`, `PlayerTickEvent`, `PlayerLoggedOutEvent`. Every handler short-circuits via `isEquipped(player, ro)` which honours the `disabledPowers` config.
- **`PowerRuntime`** ([common/powers/](src/main/java/com/otectus/runicskills/common/powers/)) — bundles 8 shared per-player services into one static container: nested `SpellHistory` (ring-buffer of recent casts), `DamageTypeMemory`, `ProcWindows`, `InternalCooldowns`, `TargetTags`, `AllyDetector` (same-team players + owned-summon chains), `PositionBuffer` (60-tick rolling snapshot for Unraveled), `SummonRegistry`. Cleared on `PlayerLoggedOutEvent`; transient (persistent Power state lives on `SkillCapability`).
- **`IronsSpellbooksPowerCompat`** — class-load-isolated wrapper for every `io.redspace.ironsspellbooks.*` symbol the dispatcher touches (`MagicData`, `AttributeRegistry.MAX_MANA`, `SchoolRegistry`, `SpellRegistry`, 10 `MobEffectRegistry` entries, event accessors). Same pattern as `BotaniaCompat`; ISS API is quarantined.
- **`disabledPowers` config** — list of Power registry ids or fully-qualified names, admin kill-switch parallel to `disabledPerks`/`disabledPassives`.

**75 Powers catalogue:**
- **45 ISS-school Powers** — 5 per school (Mark/Mark/Seal/Seal/Crown) across Fire, Ice, Lightning, Holy, Ender, Evocation, Nature, Blood, Eldritch. All Magic-gated (30/60/90 for Mark/Seal/Crown).
- **30 cross-cutting Powers** — 5 per category (Mark/Mark/Seal/Seal/Crown) across Projectile (Dexterity), Channel (Magic), Summon (Wisdom), Mobility (Dexterity), Weapon-Caster (Strength), Utility (Wisdom).

**28 wired behaviors (47 still-stubbed):**
- **11 Marks** — Sanctified Strike (HOLY +25% vs UNDEAD), Poisoner's Thumb (+15% vs poisoned), Brittle (+15% vs `chilled`), Blind Witness (+20% vs blinded), Crimson Tithe (heal 10% of blood-school damage dealt), Kindle (+20% FIRE vs `immolate`), Fortifying Bond (`fortify` self-pulse on HOLY casts), Forbidden Knowledge (Eldritch cast → 10s window where kills double drops), Rooted (`Regeneration I` pulse while standing still under `oakskin`), Step Between (`SpellTeleportEvent` opens a 30-tick window; next spell +20%), Vex Taunt (player-summon hits → 30% chance to redirect mob aggro).
- **6 Seals** — Skybreaker (airborne Lightning cast opens window for +25% next damage; lightning-school kill clears Ascension cooldown), Guided Fate (any projectile +20% vs `guiding_bolt`-marked target), Harvest the Weak (blood-school kill on ≤30%-HP target stacks `HEALTH_BOOST`), Crackle Arc (player-melee + self-`charged` chains 4 magic damage to nearest hostile within 4 blocks; 10-tick ICD), Sacrifice Cascade (`irons_spellbooks:sacrifice` cast applies REND in 8-block radius + flat 15-mana refund), Counterspell Riposte (`irons_spellbooks:counterspell` cast opens 4s window; next damage +20%).
- **11 Crowns** (9 of 9 ISS-school + 2 of 6 cross-cutting):
  - **The Heart's Toll (Blood)** — `SpellOnCastEvent` deducts 5% of current HP via `magic` source on blood-school casts; `SpellDamageEvent` applies +30% above the 10%-HP floor and −50% below it. JSON-tunable on all four magnitudes.
  - **Pyroclasm (Fire)** — entities dying with `immolate` near a Pyroclasm-equipped player detonate a 3-block fire AOE for 6 damage and reapply 4s `immolate`. Per-player rolling cap of 6 detonations per 2s window prevents chain-collapse.
  - **Unraveled (Ender)** — first consumer of `PowerRuntime.PositionBuffer`. Cancels `LivingDeathEvent`, teleports the player to a 60-tick-old snapshot, restores 30% HP / 50% mana, clears all `MobEffect`s. 10-minute ICD, committed only on a successful rewind (no-ops cleanly if there's no past position).
  - **The Apocrypha Awakens (Eldritch)** — inverted mana relationship: ≤20% mana → Eldritch costs 50% less + damage +40%; ≥80% mana → damage −20%; between thresholds → unchanged.
  - **Thunder Lord (Lightning)** — every lightning-school damage instance opens a 4s proc window; while active, `onPlayerTick` every 40 ticks strikes the nearest hostile mob within 10 blocks for 3 magic damage. Non-recursive (uses vanilla `magic()` source, not ISS lightning).
  - **The Grove Remembers (Nature)** — `LivingDamageEvent` from the player; if target carries ≥3 harmful `MobEffect`s, +30% damage on that hit.
  - **Herald of Dawn (Holy)** — `LivingHurtEvent` on the equipped player, only on the hit that *crosses* the 30%-HP threshold (HP-fraction was above 30% before this damage, ≤30% after); heals self for 8 HP, applies 8s `fortify`, AABB-scans 12 blocks for {allies (heal+fortify) | hostile mobs in combat (10 holy damage)}. 30s ICD.
  - **Glacial Sovereign (Ice)** — first non-trivial consumer of `PowerRuntime.SpellHistory.countSinceTick`. Every 20 ticks, if the player has cast ≥3 ice-school spells in the last 15s, scans a 12-block AABB and extends every active `chilled` instance on non-ally living entities by 20 ticks (capped at 200 ticks total).
  - **Folded Space (Mobility cross-cutting)** — `SpellTeleportEvent` opens a 60-tick window; the next teleport-class spell to cast within that window pays 50% mana cost. Teleport spells matched via explicit allowlist (`teleport`, `blink`, `frost_step`, `blood_step`) — Magic Missile and Eldritch Blast also live in Ender school and would falsely qualify under a school filter.
  - **Warmage's Covenant (Weapon-Caster cross-cutting)** — first `CriticalHitEvent` subscriber. A successful melee crit (vanilla or `Result.ALLOW`) opens a 10-tick window; the next `SpellDamageEvent` from the player consumes it for +25% damage.
  - **Trickster's Aria (Evocation)** — first ISS-targeted mixin in the project. `MixTrueInvisibilityEffect` injects at HEAD of `io.redspace.ironsspellbooks.effect.TrueInvisibilityEffect.onDealDamage(LivingHurtEvent)` and cancels the invisibility-strip when the attacker has the Power equipped, the suppress window is active, and the damage's directEntity is a `Projectile`. Melee/touch damage still breaks invis (preserves the spec's counterplay clause). Sibling `.firstshot` window: first projectile-form `SpellDamageEvent` while active fires for +50%. Mixin uses `@Pseudo` + `remap = false` so the project still loads cleanly when ISS is absent — sets the pattern for future ISS-targeted hooks.

**`PowersScreen` UI** ([client/screen/PowersScreen.java](src/main/java/com/otectus/runicskills/client/screen/PowersScreen.java)): three-column Marks/Seals/Crown panel with school-color-coded names (Fire orange, Ice cyan, Lightning yellow, Holy ivory, Ender violet, Blood crimson, Evocation green, Nature olive, Eldritch mint), Equip/Unequip vanilla `Button` per row, hover tooltip (translated name, tier, school, description, skill requirement, disabled-config flag), independent per-column scroll. Equip clicks send `PowerEquipSP`; the `SyncSkillCapabilityCP` resync rebuilds the buttons on the next frame. Runestone-slab visual polish: translucent dark title band with amber rules + amber title text; color-coded slot counter (`Marks 3/5 · Seals 2/3 · Crown 1/1`, red when full / green when free / dim grey when empty); per-column translucent backing panels with amber column-header underlines; equipped-row marker (2px amber stripe + `✓` prefix + bright-green name); amber-tinted hover band; footer hint band (`Esc to close · Scroll to navigate · Hover for details`). All chrome via `GuiGraphics.fill` calls — no new texture assets.

**`/powers` admin command** (op-2 gated, mirrors `/titles`): `/powers list [mark|seal|crown]` (color-coded by equipped/disabled/grey), `/powers view` (caller's slot counter + equipped names), `/powers equip <power>` (tab-completion, bypasses skill-level gate for testing but respects tier slots and `disabledPowers`), `/powers unequip <power>` (suggestions narrow to equipped names).

**`OPEN_POWERS_SCREEN` keybind** (default `U`, sits next to the existing `Y` for Skills). Registered in `RunicSkillsClient.ClientProxy.registerKeys`.

**75 new lang keys** in `en_us.json`: `power.runicskills.<id>` + `power.runicskills.<id>.description` for every Power, plus `key.runicskills.open_powers`, `screen.runicskills.powers.{title,equip,unequip,no_capability}`, 3 `tier.runicskills.*`, 6 `school.runicskills.*`. Other locales fall back to en_us pending native translations.

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
- **Bumped network channel `PROTOCOL_VERSION` from `3` to `5`.** Single coherent jump that absorbs every wire-format growth since the last public release: (a) five 1.0.0-era kill-switch fields on `CommonConfigSyncCP` (`maxActivePerks`, `disabledPerks`, `disabledPassives`, `perkSwapCooldownTicks`, `skillLevelUpCostMultiplier`) plus the new `PerkGroupsSyncCP` packet for datapack-driven perk groups; (b) one new boolean `enableScholarEnchantmentHiding` on `CommonConfigSyncCP` for the Scholar/enchantment-hiding decoupling; (c) three new Powers packets (`PowerOverridesSyncCP`, `PowerProcCP`, `PowerEquipSP`) plus the new equipped-Powers / `powerCooldowns` / `powerWindows` fields propagated via `SyncSkillCapabilityCP`. Old 1.0.0 clients connected to 1.1.0 servers (and vice versa) get a clean connection refusal instead of a silent state-corruption bug. Clients and servers must update together.
- **`mods.toml` YACL dependency** flipped from `mandatory=true` to `mandatory=false`, ordering set to `AFTER`. YACL is now genuinely optional everywhere; the server side doesn't need it (config persists via plain Gson) and the client side falls back to the parent screen with a log warning when the user clicks Configure without YACL installed.
- **Sided-import lint extended** to forbid `dev.isxander.yacl3.*` executable-class imports (`ConfigClassHandler`, `serializer.*`, `gui.*`, `api.*`) outside `client/config/`. Annotation-only imports (`@SerialEntry`, `@AutoGen`, `@IntField`, `@FloatField`, `@Boolean`, `@ListGroup`) remain allowed because their RUNTIME retention doesn't trigger class loading on the server. Single best guardrail against re-introducing the dedicated-server crash.
- **ISS compile dep bumped** from curse-maven file id `5539243` → `7402504` (pre-3.15 → 3.15.x). The older artifact predates `SpellCooldownAddedEvent` which Phase-1a Quickcast needs.
- **Phase 1a texture-path audit.** Corrected ten `HandlerResources` entries to match the real ISS item sheet: `upgrade_orb_cast_time`→`cast_time_ring`, `upgrade_orb_mana_regen`→`mana_ring`, `antique_amulet`→`concentration_amulet`, `mana_potion`→`enchanted_ward_amulet`, `greater_mana_potion`→`greater_healing_potion`, `apprentice_spellbook`→`chronicle`, `affinity_ring`→`arcane_rune`, `scroll_of_haste`→`scroll`, `ancient_codex`→`chronicle_old`, `arcane_debris`→`arcane_essence`. Perks were functional before but rendered as missing-texture pink/black boxes.
- **`apothItem()` helper path fix.** Apotheosis uses `textures/items/` (plural) not the vanilla `textures/item/`. Every Apotheosis perk icon now resolves correctly.
- **Two design-doc rename clashes resolved:** doc's "Mana Shield" → `MANA_BULWARK` (doc's "Arcane Barrier" was already in use as an Endurance-tree perk), doc's "Second Wind" → `ARCANE_REPRIEVE` (Constitution tree already has `SECOND_WIND`).
- **`PlayerLifecycleHandler` extensions** — `onPlayerLoggedInEvent` now also sends `PowerOverridesSyncCP` alongside the existing config / perk-group / skill-capability syncs; `onAddReloadListeners` now also registers `PowerOverridesReloadListener` alongside `PerkGroupsReloadListener`. `RunicSkillsClient.ClientProxy.registerKeys` now also registers `OPEN_POWERS_SCREEN` (default `U`).
- **`VERSION` file synced** to `1.1.0` — through 1.0.1 it was left at `1.0.0`, partially corrected to `1.0.2` in the 1.0.2 source-only release, and now matches the published version.

### Removed
- `attribItem()` helper from `HandlerResources`. `attributeslib` has no `textures/item/*.png` assets — the helper was dead code.

### Skipped (design-doc flagged risky, deferred to 1.2.0+)
- **Perks** — Pack Caller (requires `SummonManager` internals), Eldritch Apprentice (Eldritch-research XP has no public API), Gemsmith (deep affix-gem iteration on `GetAffixModifiersEvent`), Lucky Loot (global loot modifier with no player-context routing), Spawner Mage, Enchanter's Insight, Library Dedication, Ritualist, Ars Scholar, Glyphsmith, Mythical Scribe, Bookwyrm's Apprentice, Enchanter-Arms, Apparatus Synergy, Split-Caster; plus eleven Phase-3 capstones — Resonant Affixes, Gem-Fueled Casting, Spellsocket, Adaptive Caster, Apothic Apprentice, Glyph-Imbued Gem, Sourcelink Affix, Dead King's Debt, Spawner Sanctuary, Ritualized Reforge, Gem-Threaded Armor, Arcane Syncretism. Reasons documented in the original phase commit messages.
- **Powers — 4 cross-cutting Crowns remaining:** Arcanist's Barrage (Projectile), The Long Note (Channel), The Conductor (Summon — needs `SummonManager` wrap, same blocker as Pack Caller), The Still Mind (Utility).
- **Powers — design-deferred halves of shipped Crowns:** Glacial Sovereign's "Chilled accumulation rate doubled" half (application rate not mutable from the dispatcher) and Ice Tomb death-deny half (needs `LivingDeathEvent` cancel + scheduled re-kill timer scaffold); The Grove Remembers's heal-reduction half (vanilla `LivingHealEvent` lacks an attacker pointer for target-tagging); Folded Space's CD-bypass half (no per-spell cooldown override on `SpellOnCastEvent`); Thunder Lord's CD-reduction half (no per-spell-id cooldown mutator on cast events); Apocrypha Awakens's smooth damage curve between thresholds (shipped as discrete bands).
- **Powers — broader design items:** Power Points budget (the doc's 1-PP-per-50-total-skill formula isn't enforced; current MVP allows 5/3/1 for any qualified player; follow-up: add `maxActivePowers` config to `PowerEquipSP`); full ally-detection beyond same-team players + owned-summon chains; codec-driven `MapCodec`-polymorphic Trigger/Effect dispatch (shipped the simpler numeric-override JSON which covers the admin-tuning use case without the polymorphic registration burden).

### Notes
- **Save-compatible with 1.0.0.** No NBT schema breakage. Existing `runicskills.common.json5` files (whether written by YACL pre-1.1.0 or by pack admins) load cleanly through `ConfigHolder`'s comment-stripper. Field names are unchanged; values are preserved. The new Powers fields on `SkillCapability` default to empty (`equippedMarks` / `equippedSeals` empty lists, `equippedCrown` empty string, `powerCooldowns` / `powerWindows` empty maps) so 1.0.x saves migrate trivially. Players who unlocked any of the 24 default-disabled Botania perks before will simply see them disappear from the tree — their NBT rank entries are preserved and will reactivate if a pack admin sets the `*RequiredLevel` back to a positive value.
- **Tested:** `./gradlew clean build` passes (compile + sided-imports + new YACL forbid + jar assembly + reobf). Runtime smoke testing per `docs/SMOKE_TESTS.md` is required before CurseForge upload — primary regression rows: dedicated server boot without YACL installed (the flagship fix this release exists for), client boot without L2Tabs, `/globallimit` from singleplayer with cheats, `disabledPerks=["scholar"]` no longer hiding enchantments. Powers behavior tests recommended per Power; the most useful spot-checks are the doc-flagged risky ones (Unraveled rewind, Trickster's Aria invisibility-suppress + first-shot bonus, The Heart's Toll HP cost + damage curve, Glacial Sovereign chilled-extension on a stack of mobs).
- **Localization:** 9 of 17 locale files fall back to en_us for the new Powers strings and the `enableScholarEnchantmentHiding` / `chargeMasteryPercent` config labels; native translations are a follow-up.
- **Supersession note:** This entry replaces the source-only 1.0.1, 1.0.2, and 1.1.0-alpha1..alpha8 tags. Only 1.1.0 ships to CurseForge. The earlier tags remain in git history for reference.

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
