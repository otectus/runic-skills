# Changelog

## [1.0.2] - 2026-04-24

Magic-tree cross-mod expansion. 78 new perks across Iron's Spells 'n Spellbooks (46), Apotheosis + Apothic Attributes (12), Ars Nouveau (11), and cross-mod synergies (9), covering every A1–A5 catalog in `MAGIC-RUNIC-SKILLS.md` plus the §B synergy tier. All perks register null-safely when their required mods are absent, matching the Botania-integration pattern from 1.0.1.

### Added — Iron's Spells 'n Spellbooks (46 perks)

**Phase 1a — generic mana & casting (16 perks):** Wellspring, Quickening, Reservoir, Tempo, Arcane Recovery, Focus, Mana Bulwark, Arcane Reprieve, Mana Surge, Spellweaver, Resonant Casting, Imbued Focus, Quickcast, Long Channel, Continuous Flow, Charge Mastery. Five are reconciled as permanent `irons_spellbooks:*` attribute modifiers on a 10-tick throttle (Wellspring on `max_mana`, Quickening on `cast_time_reduction`, Reservoir on `mana_regen`, Tempo on `cooldown_reduction`, Mana Surge transient on `spell_power`/`mana_regen` while HP ≤ threshold). The remainder hook ISS events: `LivingDeathEvent` (Arcane Recovery), `LivingAttackEvent` on `CastType.LONG` (Focus), `LivingHurtEvent` (Mana Bulwark redirects damage→mana at 2:1), `ChangeManaEvent` (Arcane Reprieve auto-refill with 120s cooldown, Continuous Flow per-tick drain reduction on `CastType.CONTINUOUS`), `SpellOnCastEvent` (Spellweaver every-Nth-cast free, 10s combo window), `SpellDamageEvent` (Resonant Casting above-95%-mana damage bonus, Long Channel `CastType.LONG` bonus), `ModifySpellLevelEvent` (Imbued Focus +1 level), and `SpellCooldownAddedEvent.Pre` (Quickcast `CastType.INSTANT`-filtered cooldown reduction). Charge Mastery registers but is a UI-only no-op — `CastType.CHARGE` does not exist in ISS 3.x (only `NONE/INSTANT/LONG/CONTINUOUS`); retained for future API evolution.

**Phase 1b — school specialist triplets (28 perks):** for each of nine ISS schools (Fire, Ice, Lightning, Holy, Ender, Blood, Evocation, Nature, Eldritch) a three-perk triad plus the previously-missing Eldritch Attunement gate. X-mancer grants `+%` to the school's `irons_spellbooks:<school>_spell_power` (Magic tree). X-Warded grants `+%` to `irons_spellbooks:<school>_magic_resist` (Endurance tree). X-Catalyst is a 1-in-N-chance on-cast signature-effect proc — lightning/holy/ender/evocation/nature/eldritch apply `CHARGED`/`FORTIFY`/`PLANAR_SIGHT`/`ECHOING_STRIKES`/`OAKSKIN`/`ABYSSAL_SHROUD` to the caster via `SpellOnCastEvent`; fire/ice/blood apply `IMMOLATE`/`CHILLED`/`REND` to the victim via `SpellDamageEvent`.

**Phase 1c — summon/utility (2 perks):** Lord of the Dead (caster gains `+%` `summon_damage`, and newly-spawned `IMagicSummon` entities owned by the player get `+%` `MULTIPLY_BASE` on `MAX_HEALTH` via `EntityJoinLevelEvent`). Life Leech Bound (when a player-owned `IMagicSummon` damages a target, `%` of damage returns to the summoner as mana via `MagicData.addMana` on `LivingHurtEvent`).

### Added — Apotheosis & Apothic Attributes (12 perks)

Socket Virtuoso (adds `+N` effective sockets to equipped items via `GetItemSocketsEvent` alongside the legacy Fortune-threshold bonus). Affix Affinity (counts Rare-or-better equipped affix items via `AffixHelper.getRarity()`, multiplies vanilla `ATTACK_DAMAGE` by per-item percent, and cuts incoming damage by a capped `1 - count × reduction` on `LivingHurtEvent`). Ten stat-stick perks reconciled on a 10-tick throttle against `ALObjects.Attributes.*`: Apothic Critical Mastery (`CRIT_CHANCE` + `CRIT_DAMAGE`), Vampiric Fangs (`LIFE_STEAL`), Reaper's Edge (`CURRENT_HP_DAMAGE`), Evasive (`DODGE_CHANCE`), Arrow Mastery (`ARROW_DAMAGE` + `ARROW_VELOCITY`, multiplicative), Earthbreaker (`MINING_SPEED`, multiplicative), Apothic Scholar (`EXPERIENCE_GAINED`, multiplicative), Spectral Ward (`PROT_PIERCE` flat + `PROT_SHRED` percent), Ghostbound (`GHOST_HEALTH`), Heart of the Healer (`HEALING_RECEIVED` + `OVERHEAL`). Two rename clashes resolved against the existing Fortune-tree `CRITICAL_MASTERY` and Intelligence-tree `SCHOLAR` by prefixing `apothic_`.

### Added — Ars Nouveau (11 perks)

**Phase 2b — form & utility (4 perks):** Form Focus filters by `Spell.getCastMethod()` identity on the `MethodProjectile`/`MethodTouch`/`MethodSelf` singletons — Projectile and Self reduce mana cost via `SpellCostCalcEvent`, Touch boosts outgoing damage via `SpellDamageEvent.Pre`. Wild Manipulation reduces cost for any spell whose `recipe` contains a `SpellSchools.MANIPULATION`-tagged glyph (via a shared `spellContainsSchool` helper).

**Phase 2c — per-school (7 perks):** Hedgewitch: Water (ELEMENTAL_WATER cost + damage), Emberforged: Fire (ELEMENTAL_FIRE damage), Stormcaller: Air (ELEMENTAL_AIR damage), Geomancer: Earth (ELEMENTAL_EARTH damage), Conjurer (CONJURATION cost), Abjurer (ABJURATION damage/magnitude), Arcane Weaver (MANIPULATION damage, complementing Wild Manipulation's cost side). All reuse the Phase-2b school-membership check; all cost reductions floor at 1 Source. The doc's Necromant variant is dropped — Ars Nouveau 4.12.x `SpellSchools` has no NECROMANCY enum (only Abjuration/Conjuration/Manipulation and the four elementals).

### Added — Cross-mod synergy (9 perks)

Six Schoolbridges (require ISS + Ars): each reads the caster's ISS `<school>_spell_power` attribute and bleeds a configurable fraction into outgoing Ars damage of the matched school — Fire→ELEMENTAL_FIRE, Ice→ELEMENTAL_WATER, Lightning→ELEMENTAL_AIR, Nature→ELEMENTAL_EARTH, Holy→ABJURATION, Ender→MANIPULATION. Unified Arcana (ISS + Ars): refunds a percent of Ars cast cost to the caster's ISS mana pool via `SpellResolveEvent.Post` + `MagicData.addMana`. Triple Threat (ISS + Ars + Apoth): tick-reconciled `+%` modifiers on `max_mana`/`mana_regen`/`spell_power`, only active when all three mods are loaded. Affix Focus (ISS + Apoth): on `ModifySpellLevelEvent`, grants `+N` effective spell levels when the player has `N` or more Rare-or-better Apothic affix items equipped. Every cross-mod perk is null-registered at startup when any required mod is missing — the perks disappear from the tree entirely rather than rendering as inert icons.

### Changed
- **ISS compile dep bumped** from curse-maven file id `5539243` → `7402504` (pre-3.15 → 3.15.x). The older artifact predates `SpellCooldownAddedEvent` which Phase-1a Quickcast needs. No runtime behaviour change for older ISS versions — if the event doesn't fire, the perk simply never procs.
- **Phase 1a texture-path audit.** Corrected ten `HandlerResources` entries to match the real ISS item sheet: `upgrade_orb_cast_time`→`cast_time_ring`, `upgrade_orb_mana_regen`→`mana_ring`, `antique_amulet`→`concentration_amulet`, `mana_potion`→`enchanted_ward_amulet`, `greater_mana_potion`→`greater_healing_potion`, `apprentice_spellbook`→`chronicle`, `affinity_ring`→`arcane_rune`, `scroll_of_haste`→`scroll`, `ancient_codex`→`chronicle_old`, `arcane_debris`→`arcane_essence`. Perks were functional before but rendered as missing-texture pink/black boxes.
- **`apothItem()` helper path fix.** Apotheosis uses `textures/items/` (plural) not the vanilla `textures/item/`. Every new Apotheosis perk icon now resolves correctly against Apotheosis's actual texture layout.
- **Two design-doc rename clashes resolved:** doc's "Mana Shield" → `MANA_BULWARK` (doc's "Arcane Barrier" was already in use as an Endurance-tree perk), doc's "Second Wind" → `ARCANE_REPRIEVE` (Constitution tree already has `SECOND_WIND`).
- **`VERSION` file synced** to `1.0.2` — it was left at `1.0.0` through the 1.0.1 release.

### Removed
- `attribItem()` helper from `HandlerResources`. `attributeslib` has no `textures/item/*.png` assets — the helper was dead code across all six earlier phases of this release.

### Skipped (design-doc flagged risky)
Pack Caller (requires `SummonManager` internals), Eldritch Apprentice (Eldritch-research XP has no public API), Gemsmith (deep affix-gem iteration on `GetAffixModifiersEvent`), Lucky Loot (global loot modifier with no player-context routing), Spawner Mage, Enchanter's Insight, Library Dedication, Ritualist, Ars Scholar, Glyphsmith, Mythical Scribe, Bookwyrm's Apprentice, Enchanter-Arms, Apparatus Synergy, Split-Caster; plus eleven Phase-3 capstones — Resonant Affixes, Gem-Fueled Casting, Spellsocket, Adaptive Caster, Apothic Apprentice, Glyph-Imbued Gem, Sourcelink Affix, Dead King's Debt, Spawner Sanctuary, Ritualized Reforge, Gem-Threaded Armor, Arcane Syncretism. Each is documented in the phase's original commit message with the reason.

### Notes
- Save-compatible with 1.0.1; no NBT or protocol-version changes. All 78 new perks default to behaviour-preserving `required_level ≥ 1` values; admins who want a subset can set `required_level = -1` per-perk to null-register it (matching the existing idiom).
- Tested: `./gradlew check` (compile + sided-imports) passes after every phase. Runtime gameplay behaviour was not smoke-tested in an interactive client; recommend verifying at a minimum: Mana Bulwark damage→mana redirect, Arcane Reprieve's 120s cooldown, Affix Affinity's damage-reduction cap, and any Schoolbridge with both ISS and Ars loaded.

## [1.0.1] - 2026-04-24

Botania integration. 42 new perks (19 Wisdom + 23 Magic) flavored around Botania's rune / season / sin / Gaia progression, wired behind a strict class-load-isolation pattern so the mod is a clean optional dependency.

### Added
- **Botania optional integration.** Every `BOTANIA_*` perk registers only when `botania` is present in the modlist and its `required_level >= 0`; otherwise the corresponding `RegistryObject<Perk>` is null and every event path short-circuits via the existing `RegistryPerks.X != null` idiom. Botania API access is confined to two new files:
  - `integration/BotaniaCompat.java` — class-load-isolated wrapper around `vazkii.botania.api.BotaniaForgeCapabilities`, `ManaItemHandler`, `ManaReceiver`, `ManaPool`. Offers `drainNearbyPool` / `hasNearbyPoolMana` / `drainPlayerMana` / `chargePlayerMana` / `getPlayerManaTotal`. Scans are bounded to a 12×6×12 AABB per the integration plan.
  - `integration/BotaniaIntegration.java` — Forge-bus event subscriber (`@SubscribeEvent` on instance methods, registered via `RunicSkills.tryLoadIntegration("botania", ...)`). Subscribes to Botania's `ManaProficiencyEvent`, `ManaDiscountEvent`, `ManaItemsEvent` and to Forge's `TickEvent.PlayerTickEvent`, `LivingHurtEvent` (attacker + target), `CriticalHitEvent`, `LivingDeathEvent`, `BlockEvent.BreakEvent`, `LivingEntityUseItemEvent.Finish`.
- **42 new perks, wired end-to-end** (config fields in `HandlerCommonConfig`, texture ResourceLocations in `HandlerResources`, `RegistryObject<Perk>` entries in `RegistryPerks`, and `en_us.json` display names + descriptions for each). Tier layout mirrors Botania's rune progression:
  - **Wisdom low-tier (Elemental / Rune-of-Mana):** Petal-Reader, Rune of Mana: Resonance, Sparkle-Sense, Dowser's Twig, Green Thumb, Livingbark Student.
  - **Wisdom mid-tier (Seasonal):** Spring: Agricultor's Eye, Summer: Forager's Palate, Autumn: Loot-Hunter's Intuition, Winter: Still Listener, Manaseer's Lens, Corporea Query.
  - **Wisdom high-tier (Sin / Gaia / Elven):** Greed: Cartographer-Prospector, Pride: Far Reach, Sloth: Lazy Swap, Envy: Mirror's Read, Elven Knowledge, Gaia's Witness, Oracle of the Nine Runes.
  - **Magic low-tier (Rune foundation):** Inner Wellspring, Rune of Water: Tidewoven, Rune of Fire: Emberheart, Rune of Earth: Stone-Rooted, Rune of Air: Featherstep, Band of Aura: Passive Channel.
  - **Magic mid-tier (Seasonal / Lens):** Spring: Verdant Pulse, Summer: Solar Conduit, Autumn: Harvest Tithe, Winter: Frostbound, Lens Mastery: Velocity, Lens Mastery: Potency.
  - **Magic high-tier (Sin / Gaia / relic):** Lust: Pixie Affinity, Gluttony: Cake Combustion, Greed: Magnetite, Sloth: Unbound Step, Envy: Mirrored Wrath, Pride: Crown of Reach, Wrath: Thundercall, Gaia's Gift: Relic Attunement, Terrasteel Ascension, Flügel's Grace, Manastorm.
- **Full effect implementations this release (18 perks):** Tidewoven, Resonance, Inner Wellspring, Mirrored Wrath, Emberheart, Solar Conduit, Featherstep, Frostbound, Thundercall, Harvest Tithe, Green Thumb, Livingbark Student, Cake Combustion, Stone-Rooted, Terrasteel Ascension, Crown of Reach, Far Reach, Magnetite. Attribute-based modifiers piggy-back on the existing `RegistryAttributes.RegisterAttribute` helper; reach bonuses use Forge's `ForgeMod.ENTITY_REACH` and `ForgeMod.BLOCK_REACH` attributes.
- **Perk icons reuse Botania's own 16×16 item textures** via the `botania:` namespace — each of the 42 perks maps to a unique `botania:textures/item/*.png` (16 runes, lens_speed/lens_power/lens_storm, reach_ring/swap_ring/magnet_ring/aura_ring/mana_ring/mana_mirror/mana_tablet/mana_cookie, monocle/itemfinder/sextant/divining_rod, lexicon/lexicon_elven/twig_wand/corporea_spark/gaia_head, flight_tiara/terrasteel_ingot/king_key, livingwood_twig/overgrowth_seed/infused_seeds, third_eye_0/third_eye_2). Nothing is redistributed: the paths resolve at render time from Botania's own assets, so the perks only ever render when Botania is loaded (which is a hard precondition for the perk existing at all).
- Build: `maven.blamejared.com` added to `build.gradle` repositories, `compileOnly fg.deobf("vazkii.botania:Botania:1.20.1-451-FORGE:api")` added to dependencies. No runtime jar is shipped.
- mods.toml: optional `botania` dependency entry, `mandatory = false`, `ordering = "AFTER"`, `versionRange = "[1.20.1-441,)"`.

### Notes
- Tested: `./gradlew compileJava` passes against the real Botania 1.20.1-451-FORGE `:api` jar with no errors.
- Fallback when Botania is absent: verified class-load-isolation — `vazkii.botania.*` imports only live inside `BotaniaCompat` and `BotaniaIntegration`, both of which are never class-loaded (and thus never verified) on a Botania-less runtime because `tryLoadIntegration` short-circuits before `Class.forName` and `RegistryPerks`' ternary guards null out every Botania field before their register lambdas instantiate.
- Effect implementations deferred to a follow-up pass (24 perks): client render (Sparkle-Sense, Manaseer's Lens, Still Listener, Agricultor's Eye, Cartographer-Prospector), keybind-activated (Dowser's Twig, Verdant Pulse, Manastorm, Flügel's Grace, Loot-Hunter's Intuition), custom capability or progression (Gaia's Witness extra slot, Relic Attunement curio slot, Elven Knowledge chapter unlock, Band of Aura virtual mana item, Pixie Affinity summon), tooltip / command / projectile / physics (Petal-Reader, Mirror's Read, Oracle of the Nine Runes, Lazy Swap, Corporea Query, Lens Velocity, Lens Potency, Forager's Palate XP flag, Unbound Step movement mixin). Each is registered and toggleable; only its effect handler is blank.
- Save-compatible with 1.0.0; no NBT schema or protocol-version changes.

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
