# Changelog

## [2.1.0] - 2026-09-06 — Tinkers' Thinking and Tinkers' Jewelry perks, Katanas covered by the core seams

No network protocol change (stays at 12) and no capability or NBT schema change: the add-on state
`TcAddonState` holds is server-memory only and never touches a save. No new mixins either — neither
adapter contributes one; both are wired entirely through the FORGE events `TConstructPerkHandler`
already subscribes and through `ForgeRegistries`/`IToolContext.getModifierLevel` lookups by
registry id. `runicskills.mixins.json`'s common list is unchanged from 2.0.7 (5 client, 47 common,
of which 15 target Tinkers' Construct — see the 2.0.7 entry's Release verification).

### Tinkers' Thinking (three perks, no companion mod)

Detected by mod id alone; no compile dependency and no class of the add-on is ever named. Both
triggers are reached from the add-on's own registry-visible mob effects rather than its internal
state, because `TinkerDataCapability.TinkerDataKey` declares neither `equals` nor `hashCode` and
`TinkerDataKey.of` allocates a fresh instance per call — no consumer, with or without a compile
dependency, can read a level the add-on stored under its own key. This is recorded as a known
constraint, not worked around by guessing: detection keys on the mob effects the add-on applies
instead (`tinkers_thinking:last_effort`, `tinkers_thinking:sculk_power`).

- **Last Thought** (`tc_thinking_last_thought`, Wisdom 16): triggers on a death Tinkers' Thinking's
  own `OnDeath.onLivingDying` refused (cancelled `LivingDeathEvent` plus the `last_effort` effect).
  Adds `tcThinkingLastThoughtPercent`% (default 12) durability-loss avoidance for
  `tcThinkingLastThoughtSeconds`s (default 15) afterward, through the new add-on wear-avoidance
  channel. **This is a reprieve after the save, not a discount on its cost, because the add-on's own
  death save spends no durability at all** — there is no cost to discount.
- **Studied Recall** (`tc_thinking_studied_recall`, Wisdom 20): triggers when the add-on's
  `OnExpPickUp.onPlayerPickupXp` cancels an experience pickup and converts it into
  `sculk_power` instead. Returns `tcThinkingStudiedRecallPercent`% (default 15) of the consumed
  orb's value as ordinary experience through the new add-on experience-recovery channel — bounded
  above by what the orb was worth, so it is a partial recovery and never a net gain.
- **Embellished Focus** (`tc_thinking_embellished_focus`, Tinkering 24): triggers while the main-hand
  weapon carries one of Tinkers' Thinking's own melee modifiers (`attack_advanced`, `bane_of_pigs`,
  `clay`, `hellish`, `overdose`, `lightly_attack`, `sharp_circumstance` — read off the shipped jar's
  modifier folder, mapped by `TraitFeatureRegistry.Feature.THINKING_EMBELLISHMENT`). Adds
  `tcThinkingEmbellishedFocusPercent`% (default 4) to the existing melee-damage sum.

### Tinkers' Jewelry (four perks, one reserved)

No compile dependency and no stub source set: everything is addressed by material id, modifier id,
or the `tconstruct:modifiable/durability` tag, the way Tinkers' itself addresses it. Two of the four
read a worn piece from vanilla equipment slots rather than Curios, since Curios types are not on
this build's classpath; **a piece worn in a Curios-only slot does not count for Gem Attunement.**

- **Jeweler's Setting** (`tc_jeweler_setting`, Tinkering 12): triggers on any Tinker Station take that
  delivers a jewelry-material piece (not only a repair — setting a stone is the moment a piece is
  finished). Adds `tcJewelerSettingPercent`% (default 10) durability-loss avoidance on that specific
  piece for `tcJewelerSettingSeconds`s (default 30) through the add-on wear-avoidance channel.
- **Gem Attunement** (`tc_gem_attunement`, Wisdom 16): triggers while the player wears a jewelry piece
  in a vanilla equipment slot. Adds `tcGemAttunementPercent`% (default 3) to the melee-damage sum.
- **Undying Lustre** (`tc_undying_lustre`, Constitution 20): triggers only inside the death-resolution
  bracket, on the specific ring Tinkers' Jewelry's own `DamageItemEvents.undying` is charging (which
  spends durability through the same `ToolDamageUtil.damage` seam this mod already owns). Adds
  `tcUndyingLustrePercent`% (default 25) durability-loss avoidance to that save's cost, through the
  add-on wear-avoidance channel.
- **Polished Facet** (`tc_polished_facet`, Tinkering 20): triggers on a paid station repair of a
  jewelry-material piece. Adds `tcPolishedFacetPercent`% (default 8) to the existing paid-repair
  share, bounded by `tconstructRepairBonusCap` with Material Harmony and the repair Powers. **Keys on
  the piece being jewelry-material, not on the add-on's `polish` modifier** — `polish.json` is a
  single `tconstruct:modifier_slot` grant of one upgrade slot with no repair semantics of any kind, so
  a perk keyed on it would be named after a mechanic that does not exist.
- **`tc_subspace_reserve` is a reserved, unregistered id.** Tinkers' Jewelry's `subspace` modifier
  sizes a private inventory (`SubSpaceCapability`/`SubSpaceMenu`) behind the `tinkersjewelry:subspace`
  attribute and carries no durability, damage, repair or progression quantity for a Runic channel to
  join. Same treatment as `tc_medallion_concord`: the diagnostic reports `UPSTREAM_UNAVAILABLE` with
  the reason rather than shipping a selectable no-op.

Both wear-avoidance contributions (Last Thought, Jeweler's Setting, Undying Lustre) are summed and
clamped once by the new `tconstructNewWearAvoidanceCap` (default 0.25) before joining the one
avoidance sum every wear perk shares; the experience contribution is summed and clamped once by the
new `tconstructNewExperienceBonusCap` (default 0.20).

### Tinkers' Katanas needs no adapter

The jar ships zero Java classes. Its `tinkers_katanas:katana` and `:fuma_shuriken` are JsonThings
tool definitions that register into `tconstruct:modifiable/melee/primary`, `/one_handed`,
`/durability`, `/bonus_slots` and (the shuriken) `/ranged`, so classification, wear, repair, ranged
and keystone already reach them unchanged with no new code. Two optional entries were added to
`runicskills:lucky_break_eligible` (`tinkers_katanas:katana`, `tinkers_katanas:fuma_shuriken`) so
`DurabilityPerkRules` still recognises them as tools on an install where the Tinkers'-side tags are
overridden.

### Config

New flags: `enableTinkersThinkingIntegration`, `enableTinkersJewelryIntegration` (both default on).
New shared caps: `tconstructNewWearAvoidanceCap` (0.25), `tconstructNewExperienceBonusCap` (0.20).
Seven percent/duration tuning fields (`tcThinkingLastThoughtPercent`, `tcThinkingLastThoughtSeconds`,
`tcThinkingStudiedRecallPercent`, `tcThinkingEmbellishedFocusPercent`, `tcJewelerSettingPercent`,
`tcJewelerSettingSeconds`, `tcGemAttunementPercent`, `tcUndyingLustrePercent`,
`tcPolishedFacetPercent` — nine fields in total) and seven required-level fields
(`tcThinkingLastThoughtRequiredLevel`, `tcThinkingStudiedRecallRequiredLevel`,
`tcThinkingEmbellishedFocusRequiredLevel`, `tcJewelerSettingRequiredLevel`,
`tcGemAttunementRequiredLevel`, `tcUndyingLustreRequiredLevel`, `tcPolishedFacetRequiredLevel`).

### Testing

- New GameTests: `TinkersThinkingPerksGameTest` (`lastThoughtReducesDeathWear()`,
  `studiedRecallAddsXpShare()`, `embellishedFocusAddsMeleeShare()`) and
  `TinkersJewelryPerksGameTest` (`jewelerSettingAffectsStationTake()`,
  `gemAttunementClassifiesJewelryMaterial()`, `undyingLustreReducesSaveWear()`,
  `polishedFacetAffectsRepair()`). `TcAddonAbsenceGameTest` gains
  `subspaceIsReservedAndUnregistered()` and `katanasIsDataOnlyAndNeedsNoAdapter()`, and its two
  existing dormancy methods now cover the two new add-ons as well.
- New opt-in gametest profiles: `-PtinkersAddons=thinking` puts Tinkers' Thinking 0.1.6.6.3 — the
  exact build the reference pack runs — on the runtime classpath, giving `TinkersThinkingPerksGameTest`
  live coverage. `-PtinkersProfile=stable -PtinkersAddons=tcintegrations,botania,ars` puts Botania
  1.20.1-455-forge and Ars Nouveau 4.12.7 alongside TCIntegrations, so `ADDON_BOTANIA_REPAIR_CHARGE`
  and `ADDON_ARS_ARMOR_REPAIR` are exercised live instead of asserted `ABSENT` only; it registers no
  gametest class of its own, so the whole-suite count matches the M1 baseline. Tinkers' Jewelry has no
  live profile: Modrinth's newest 1.20.1 Forge build is 1.1.0 while the reference pack runs 1.2.0, so
  a profile against 1.1.0 would be a green run about a jar nobody uses; `TinkersJewelryPerksGameTest`
  still registers whenever `tinkersjewelry` is loaded, for a pack developer who supplies 1.2.0
  themselves.
- Gametest counts (whole-suite total, measured 2026-09-06): M0 (no profile) 116, unchanged.
  M1 (`-PtinkersProfile=stable`) 202, up from 200 — the two new `TcAddonAbsenceGameTest` methods.
  `-PtinkersAddons=thinking` 205 (M1 + the three Thinking methods).
  `-PtinkersProfile=stable -PtinkersAddons=tcintegrations,botania,ars` 202 (matches M1; no new class).
  See [`docs/TCONSTRUCT_TEST_MATRIX.md`](docs/TCONSTRUCT_TEST_MATRIX.md).

## [2.0.7] - 2026-09-05 — Salvage moved to grindstone, crafting rewards streamlined, Tinkers' Construct 3.11 preparation

Network protocol bumped to 12 (from 11). Config schema gains three fields: `autoRepairPointsPerSecondAt100`, `craftRewardMaxExtraOutputs`, `recyclingEnabled`, and six Tinkers' workshop fields: `tconstructWorkshopFocusSeconds`, `tconstructWorkshopFocusRadius`, `tconstructMaxFocusedWorkshops`, `tconstructMaxCastingAssociations`, `tconstructWorkshopBonusCap`, `tconstructAllowAutomationRewards`. Dev Forge is 47.4.23; `forge_version_range` remains `[47,)`. Build: optional compile-only Tinkers' Construct 3.11.2.166 and Mantle 1.11.97 dependencies, runtime selectable via `-PtinkersProfile=stable|beta`.

### Economy and core fixes

**Crafting rewards:**
- **Bonus-copy perks now route through one operation policy**, denying rewards for repairs, conversions, unstackable/damageable/NBT/capability items, and compression (all inputs equal). One `craftRewardMaxExtraOutputs` cap (default 1) covers all eligible perks together per craft, honoring per-perk roles (Assembly Line, Mass Production, Alloy Master, Master Woodworker, Medieval Architecture).
- **Master Tinkerer's restore now applies at result creation**, before Tinker's Touch stamping and before shift-click copies the result, so the restored durability is visible and does not regress on shift-click.

**Salvage redesigned:**
- **Block-break salvage (Resource Efficiency, Salvage Expert breaking crafting blocks) has been removed.** Both perks now operate at the grindstone via `GrindstoneEvent.OnPlaceItem`, consuming the target once and returning materials subject to the cost cap and a datapack allowlist (`data/<ns>/runicskills/recycling/*.json`). Recipes are no longer inferred from the recipe manager.
- **Disassembler and Salvage Master move to item-destroy triggers** (on-break or on-empty), reading the recycling allowlist to determine what materials return. An item breaking now has a stated chance to recover its materials, rather than trying to salvage "on demand" when a block is broken.
- **Recycling rules are datapack-driven:** each file under `data/<ns>/runicskills/recycling/*.json` (keyed by item id) names an input item count, output items+counts, a recovery cap, and an NBT-empty requirement. Unknown fields reject the rule (a misspelled field is caught, not silently skipped); rules naming absent items are skipped. On reload failure, last-good rules are retained. Shipped defaults cover crafting table, furnace, smoker, blast furnace, anvil (2 tiers), grindstone, and common vanilla tools.

**Lucky Charm:**
- Now applies once at effect application (via a `@ModifyVariable` on `LivingEntity.addEffect`), not at every observer update. Duration is reduced via NBT save/load to preserve hidden-effect chains and curative items. Infinite-duration effects are skipped.

**Auto Repair:**
- Durability credit now uses fractional accumulation: once per second, `repairRate / 100 * autoRepairPointsPerSecondAt100` raw points accrue to a budget. When a full point accrues, it repairs the next eligible slot via rotation, ensuring no slot is favoured and small percentages repair proportionally slower. Budget clears on logout, death, perk disable, or when no eligible item exists. Creative-mode breaks no longer pay Treasure Hunter.

**Treasure Hunter:**
- Payout only on confirmed block removal (posting a new `BlockBreakCommittedEvent` after `ServerPlayerGameMode.removeBlock` succeeds and `canHarvestBlock` passes). Cancellations from LOWEST-priority listeners yield zero payout.

**Equipment roles:**
- New `StackLockProvider` interface and `EquipmentProfileService` utility class for behaviour-neutral stack-aware classification and repair provision. Vanilla rules (TieredItem/ShearsItem + tags) are preserved in `VanillaEquipmentAdapter`. Tinkers' integration hooks in S2 without changing vanilla item behaviour.

### Build and Tinkers' foundation

- **Gradle:** optional compile-only dependencies for Tinkers' Construct and Mantle pinned to stable 3.11.2.166 and 1.11.97. Runtime classpath controlled by `-PtinkersProfile=stable|beta` property (default: profile disabled, M0 baseline). New `checkSidedImports` rule forbids `slimeknights.` imports outside `integration/tconstruct/**`, `mixin/tconstruct/**`, `client/integration/tconstruct/**`, and gametest directories.
- **Tinkers' hook manifest:** all thirteen hook seams from the spec are resolved in `docs/TCONSTRUCT_HOOKS.md` and `docs/tconstruct/compat-manifest.json` (machine-readable). Mixin targets, SHA-256 hashes, and version detection are confirmed from the real 3.11.2.166 jar. No Tinkers' mixins are implemented yet (S2+ work).

### Tinkers' Construct foundation

- **Version support:** Tinkers' Construct 1.20.1-3.11.2.166 and Mantle 1.11.97 are the minimum and compiled-against versions. Tinkers' 3.12 is recognised but runs in conservative mode (classification and repair work; wear avoidance and workmanship are not mixed in). `TConstructProfile.detect()` reads the version once at startup from Forge's mod container.
- **Conservative mode:** When Tinkers' is absent, on an unrecognised version, or when `enableTConstructIntegration` is off, classification and repair routing still work (ordinary API calls against stable signatures); native wear reduction and workmanship stamping are skipped (no mixin is applied). A player on 3.12 has functional durability perks without wear avoidance; a future 2.0.8 will compile against 3.12.
- **Profile detection:** The major and minor version decide the profile; patch versions do not differ. The profile gates mixin application during class transformation and cannot change without a restart.
- **Equipment classification:** Native tools (items in `tconstruct:modifiable`) are recognised and roles (TOOL, DIGGER, MELEE_WEAPON, RANGED_WEAPON, ARMOR, SHIELD) are read from Tinkers' own tags, so modpack tools are automatically classified.
- **Wear avoidance (S2):** A new mixin on `ToolDamageUtil.damage` redirects the `directDamage` call, applying the combined wear probability (perks: Lucky Break, Precision Tools, Unbreakable, Unbreaking Mastery, Gadgeteer, Lock Expert; capped at 90%) only when the player is performing an ordinary action (confirmed block break or melee swing). Unidentified origins get native behaviour.
- **Repair factor:** Native repairs evaluate the tool's own repair factor (composed from modifiers via `ModifierHooks.REPAIR_FACTOR`) before calling `ToolDamageUtil.repair`, respecting modifiers that forbid or reduce repair.
- **Workmanship modifier (`runicskills:workmanship`):** Stores Tool Smith / Weapon Smith / Tinker's Touch bonuses in namespaced persistent data, reads and applies only the durability bonus as a `TOOL_STATS.DURABILITY` percentage. Lazy migration of old root-tag stamps (first read/write moves the value, adds the modifier, rebuilds once). Idempotent by construction (a pure function of the stamped percentage); stamps keep the maximum of old and new values.
- **Keystone modifier (`runicskills:keystone`):** Registered with a VOLATILE_DATA hook adding one UPGRADE slot (KeystoneModifier.java:40-54); the modifier id is a save-visible identity, so registering it in 2.0.7 allows tools stamped now to load correctly in future releases.
- **Stack-aware requirements (material-tier locks, off by default):** `enableTConstructLockItems` (default false) gates automatic material-tier skill requirements derived from a tool's highest material tier. Precedence: explicit pack rule > existing item-id lock > automatic profile > allowed. Unknown materials do not crash and are reported as "undetermined" in inspection. Levels scale from stock cap of 32 to the server's `skillMaxLevel`.
- **Diagnostics:** `tconstructCompatibilityDiagnostics` (default on) logs the capability status at startup and reports it via `/skills tinkers compat` (stage S3). Each of ten capabilities (CLASSIFICATION, REPAIR, WEAR_AVOIDANCE, WORKMANSHIP, STACK_REQUIREMENTS, STATION_TRANSACTIONS, HARVEST_AOE, PROJECTILES, WORKSHOP, KEYSTONE) reports SUPPORTED, VERSION_UNVERIFIED, UPSTREAM_INCOMPATIBLE, HOOK_UNAVAILABLE, DISABLED_BY_CONFIG, or ABSENT with a reason.
- **GameTests:** `NativeWearGameTest`, `NativeRepairGameTest`, `WorkmanshipGameTest`, `StackRequirementGameTest`, `CompatibilityStatusGameTest`.

### Testing

- New GameTests: `CraftRewardPolicyGameTest`, `CraftResultTransformGameTest`, `RecyclingGameTest`, `LuckyCharmGameTest`, `AutoRepairBudgetGameTest`, `TreasureHunterCommitGameTest`. Extended `DurabilityPerksGameTest` with assertions that `EquipmentProfileService` classifies vanilla items identically to prior rules.

### Tinkers' Construct station, harvesting and projectiles (S3a)

**Station operations:**
- Native Tinker Station takes are classified by recipe type (assemble, repair, part swap, modify) and paid via the same reward policy as vanilla crafts, never twice.
- The delivered result is transformed once (Master Tinkerer restore + Tinker's Touch stamp) in `LazyResultContainer` at copy time, applying to both click and shift-click paths.
- Skill-lock checks apply at `Slot.mayPickup` before any take via `ForeignResultSlots` registration, the same seam that checks a crafting table.
- Per-player quotes (not shared) include fingerprints of inputs and base result, allowing the server to detect if the setup changed between quote and take via monotonically incrementing revision tokens.

**Harvesting:**
- Each block of a native area harvest (hammers, excavators) opens its own action frame (NATIVE_AOE_CHILD) sharing the swing's root action id with all siblings.
- Each child block that actually breaks posts `BlockBreakCommittedEvent`, published from `ToolHarvestLogic.breakBlock` after loot drops.
- Protection checks (claim mods, protection plugins) run natively and refuse before the seam, so Runic never attempts to reward a refused break.
- One root proc per swing — perks claiming "once per action" use the shared root id.

**Projectiles:**
- Native bow, crossbow, and launcher releases open a RANGED action frame for the duration of the release method. All projectiles spawned inside take the same root action id.
- As each projectile spawns via `EntityJoinLevelEvent`, its launch facts are recorded to persistent data: owner UUID, root action id, launcher item id, and hand. Bounded to 16 claims and 2 KiB per projectile; over-budget writes are refused entirely (no partial snapshots).
- Genuine returns (thrown tools flying back with physics disabled) are detected at `ThrownTool.tryPickup`, distinct from pickups of items lying on the ground or put there by commands (which never reach the seam).

**Mixins:** `MixTinkerStationBlockEntity`, `MixLazyResultContainer`, `MixToolHarvestLogic`, `MixModifiableBowItem`, `MixModifiableCrossbowItem`, `MixThrownTool`.

### Tinkers' workshop, station panel, commands and networking (S3b)

**Workshop focus:**
- A player standing at a smeltery, foundry, melter, or alloyer may focus it to accelerate both melting and casting. Focus is claimed via a button in the native station panel or via `/skills tinkers workshop focus` at the controller they are facing. Casting tables and basins within eight blocks of the controller may be associated with the focus.
- A focus lasts `tconstructWorkshopFocusSeconds` (default 60 seconds) before expiring, and is immediately dropped on logout, dimension change, or walking past `tconstructWorkshopFocusRadius` (default 16 blocks).
- The server holds at most `tconstructMaxFocusedWorkshops` (default 32) active focuses; revalidation (distance, dimension, controller) happens at most once per second.
- A focus issued a new revision token on each change, so replayed requests (L07's completed action token) are stale by definition and refused.

**Melting and casting bonus:**
- A focused workshop melts and casts faster. Smelter and Overclock perks feed the melting speed; Overclock feeds the casting speed. The combined bonus is capped at `tconstructWorkshopBonusCap` (default 0.25, or 25%).
- Bonuses below one unit are carried as fractional debt between ticks, so modest bonuses are not lost to truncation.
- The completing tick (the native one that finishes a melt or cast) is always native code, so recipes, fuel, and outputs are never mutated by bonus logic.

**Station panel:**
- A small panel beside native Tinker Station, Melter, Alloyer, and Crafting Station screens shows the player's would-get result (after Runic transforms), their focus status, and a keyboard-accessible Focus button.
- The panel recognises native screens by class name; upstream renames leave it inert rather than breaking.
- Placement avoids the native modifier, repair and information tabs, JEI controls, and add-on extensions.

**Commands:**
- `/skills tinkers inspect [<player>]` — lists how the held item is classified and what it requires.
- `/skills tinkers workshop focus` — claims the controller the player is facing.
- `/skills tinkers workshop release` — drops the caller's own focus.
- `/skills tinkers compat` — shows compatibility status for all Tinkers' capabilities (CLASSIFICATION, REPAIR, WEAR_AVOIDANCE, WORKMANSHIP, STACK_REQUIREMENTS, STATION_TRANSACTIONS, HARVEST_AOE, PROJECTILES, WORKSHOP).
- **Permission change (2.0.7):** The `/skills` root's operator gate moved to the `player` argument, allowing `/skills tinkers inspect` and `/skills tinkers workshop` to run without OP while `/skills <other player> <skill> <level>` still requires OP level 2. Nothing became more permissive.

**Networking:**
- Protocol 12 adds three packets: `WorkshopFocusSP` (server-bound, carries focus request), `WorkshopStatusCP` (client-bound, sent at most twice per second with focus status), and `StationQuoteCP` (client-bound, sent when station inputs or result change, with a per-player preview).
- Validation is strict: sender is a real player, rate limits apply, container id matches, revision token matches current, distance is checked before chunk-load checks, and chunk-loaded state is verified.

**Mixins:** `MixMeltingModule` (AT HEAD on speed argument; `serverTick` is private and injected by name), `MixCastingBlockEntity` (AT HEAD on cooling).

### Tinkers' Construct perks (S4a)

**Fifteen new perks, single-rank each, levels scaled by skill cap (§10.2), all dormant when Tinkers' is absent and gated on `enableTConstructPerks` and their required capability:**

- **Wear avoidance (§5.2, one shared cap `tconstructMaxAvoidance`):**
  - **Repair Memory** (Tinkering 8): After paying for station repair, durability ignored for X uses (up to Y, default 16 uses / 120 seconds). Contributed percentage: `tcRepairMemoryPercent` (default 10%). One charge per root action; charges clear on tool swap, logout, or expiry.
  - **Slime Steward** (Endurance 16): Tool with spent overslime shield wears slower. Contributed percentage: `tcSlimeStewardPercent` (default 5%). Reads native shield, never writes it; non-invasive.

- **Melee and mining (three shared caps: `tconstructNewDamageBonusCap`, `tconstructNewMiningBonusCap`, `tconstructNewActionSpeedBonusCap`):**
  - **Tempered Edge** (Strength 12): Fully wound-up melee strike bonus +`tcTemperedEdgePercent`% (default 6%), once per `tcTemperedEdgeCooldownTicks` (default 60 ticks). Bonus joins `tconstructNewDamageBonusCap`.
  - **Counterweight** (Dexterity 12): Holding broad native weapon, swing speed +`tcCounterweightPercent`% (default 5%). Bonus joins `tconstructNewActionSpeedBonusCap`.
  - **Precision Footing** (Endurance 12): Mining with correct tool, grounded and not sprinting, speed +`tcPrecisionFootingPercent`% (default 8%). Bonus joins `tconstructNewMiningBonusCap`.
  - **Adaptive Grip** (Tinkering 24): Switch between mining and melee with same hybrid tool, next action +`tcAdaptiveGripPercent`% (default 8%) for `tcAdaptiveGripWindowSeconds` seconds (default 4). Consumed once per committed action, not per role. Bonus joins either cap depending on the action committed. Requires `tcAdaptiveGripSwitchSeconds` (default 8) between mode changes.

- **Ranged (no shared cap; `PROJECTILES` capability):**
  - **Measured Draw** (Dexterity 16): Fully charged native bow or fired crossbow, inaccuracy reduced to `(1 - tcMeasuredDrawPercent/100)` of original (default 10% reduction = 0.90x multiplier). Applied at release before projectile velocity is set.
  - **Returning Hand** (Dexterity 20): After thrown tool returns natively, next throw within `tcReturningHandSeconds` seconds (default 8) draws `tcReturningHandPercent`% faster (default 10%), once per `tcReturningHandCooldownTicks` (default 100 ticks). Bonus joins `tconstructNewActionSpeedBonusCap`.

- **Armor and workshop (attribute and facility perks):**
  - **Plate Discipline** (Constitution 16): Wearing 3+ Tinkers' armor pieces, +`tcPlateDisciplineAmount` knockback resistance (default 0.05 per piece state). Applied as a transient attribute modifier, not permanent.
  - **Ember Guard** (Constitution 20): Fire damage while focused on a heated workshop (fuel present, temperature > 0), reduction -`tcEmberGuardPercent`% (default 5%). Bonus joins `tconstructNewDamageReductionCap`.
  - **Cast Keeper** (Tinkering 4): Completing a cast at focused workshop, `tcCastKeeperPercent`% chance (default 15%) to return the consumed disposable cast.
  - **Thermal Rhythm** (Tinkering 8): Melting at focused workshop, +`tcThermalRhythmPercent`% progress (default 10%), never produces extra metal or fuel. Bonus joins `tconstructWorkshopBonusCap`.
  - **Workshop Cadence** (Tinkering 24): Three casts (configurable `tcWorkshopCadenceCasts`, default 3) of the same recipe under focus within a window (`tcWorkshopCadenceWindowSeconds`, default 60), then +`tcWorkshopCadencePercent`% cooling speed (default 10%) for `tcWorkshopCadenceSeconds` seconds (default 30), once per `tcWorkshopCadenceCooldownTicks` (default 200 ticks). Bonus joins `tconstructWorkshopBonusCap`.

- **Repair bonuses (shared cap `tconstructRepairBonusCap`, default 0.50):**
  - **Material Harmony** (Tinkering 12): Paid repair on a tool built from 3+ distinct materials, +`tcMaterialHarmonyPercent`% extra durability restored (default 5%), capped by `tconstructRepairBonusCap`. Reads material variant ids, counts each distinct material once.
  - **Field Service** (Tinkering 20): Repair kit used at crafting table, +`tcFieldServicePercent`% extra durability restored (default 10%), capped by `tconstructRepairBonusCap`. Station repairs are Repair Expert's (game design, not technical limitation).

**Shared caps (all inclusive sums, applied once per effect):**
- `tconstructNewDamageBonusCap` (default 0.30) — melee damage bonus ceiling
- `tconstructNewMiningBonusCap` (default 0.25) — mining speed bonus ceiling
- `tconstructNewActionSpeedBonusCap` (default 0.20) — action speed bonus ceiling (attack speed, thrown-tool draw speed)
- `tconstructNewDamageReductionCap` (default 0.25) — incoming damage reduction ceiling
- `tconstructRepairBonusCap` (default 0.50) — repair bonus ceiling (Material Harmony + Field Service + vanilla Repair Expert)
- `tconstructMaxAvoidance` (default 0.90) — wear avoidance ceiling (Repair Memory + Slime Steward + six vanilla wear perks)
- `tconstructWorkshopBonusCap` (default 0.25) — melting and casting speed bonus ceiling (Thermal Rhythm + Workshop Cadence)

**Level scaling:**
All base levels are scaled from the stock cap 32 to `skillMaxLevel` using: `max(1, min(cap, ceil(baseLevel * cap / 32)))`. So a level-32 perk on cap-64 becomes level 64; on cap-16 it becomes level 16.

**Perk-budget fix:**
Unresolvable saved perk ids (from removed mods) no longer count against the budget, so a player with a dormant `tc_*` perk on their profile when Tinkers' is absent is not locked out of other perks. See `RegistryPerks.countEnabledPerks(SkillCapability)` and `PerkBudgetDormancyGameTest`.

**Mixin:**
- `MixThrowingModule` (S4a): calls `TConstructPerkHandler.returningHandCharge()` when a native thrown tool is launched, so returning-hand tracking can be updated; string target, `remap = false`, applied only when `enableTConstructPerks` and PROJECTILES capability supported (S4b adds the return detection).

**Keystone Tinker (tc_keystone_tinker) (S4 completion):**
Fits a permanent upgrade slot on a native tool at the Tinker Station for one netherite ingot and one amethyst shard. The slot is granted by the `runicskills:keystone` modifier's VOLATILE_DATA hook; one per tool, authoritative at `Slot.mayPickup` to ensure ineligible smiths and FakePlayer never consume materials. Permanence: no removal recipe ships, and the slot survives turning the service off with `enableTConstructPerks`. Turning the whole integration off with `enableTConstructIntegration` unregisters the modifier, so the slot it grants is not applied until the integration is enabled again. Eligible tools are those in `#tconstruct:modifiable/bonus_slots` (datapack-editable via `runicskills:keystone_eligible` tag delegation). Six GameTests assert eligible take with click and shift-click, ineligible refusal with both paths, FakePlayer rejection, double-keystone refusal, and permanence after disable.

**GameTests:**
- `TcCorePerksGameTest` — 16 test methods covering enablement, inactivity, dormancy, and boundary conditions (e.g., Repair Memory one charge per action, Tempered Edge melee-only, Adaptive Grip role-switch-only, Plate Discipline 3+ pieces); some perks have multiple tests (Repair Memory, Tempered Edge) and Material Harmony, Field Service share one.
- `PerkBudgetDormancyGameTest` (base suite, always runs) — ensures dormant perk ids do not count against the budget even in M0 where Tinkers' is absent.

### Tinkers' Construct Powers (Artifice) (S4b)

**Twelve new Powers, all FULL status, school `runicskills:tinkering` (Artifice), gated on `enableTConstructPowers` and their required capability seam:**

**Marks (4, tier cost 1 PP):**
- **First Heat** (tc_first_heat, 100t cooldown): Three primary hits with native weapon in sequence window prepare the fourth for melee damage. Triggers on the fourth hit, joins the melee damage cap.
- **Plumb Line** (tc_plumb_line, 120t): Three committed mining actions with native tool on ground in sequence window prepare mining speed. Joins the mining speed cap.
- **Quench** (tc_quench, 600t): Substantial paid repair grants fire damage reduction for a duration, minimum restoration threshold. Joins the incoming reduction cap.
- **Working Memory** (tc_working_memory, 600t): Paid part change prepares repair restoration on the next paid repair of that tool within a window. Joins station rewards.

**Seals (4, tier cost 2 PP):**
- **Hammer and Tongs** (tc_hammer_and_tongs, 200t): Shield block stopping sufficient damage prepares the next melee hit for damage bonus within window. Joins the melee cap.
- **Temper Reserve** (tc_temper_reserve, 1200t): Substantial paid repair grants that tool wear avoidance for duration. Joins the wear avoidance cap.
- **Resonant Return** (tc_resonant_return, 300t): After thrown tool returns, next throw within window carries first-hit damage bonus. Joins the projectile damage cap.
- **Workshop Aegis** (tc_workshop_aegis, 400t): Completing an attributed cast grants damage reduction while in workshop. Joins the incoming reduction cap.

**Crowns (4, tier cost 3 PP):**
- **The Great Work** (tc_great_work, 3600t): One assembly, one paid repair and one manual cast within sequence window trigger Inspired: mining speed and wear avoidance for duration. Joins both caps.
- **Last Temper** (tc_last_temper, 3600t): Lethal ordinary wear on usable native Tinkers' tool leaves it on exactly 1 durability. Does not compose into a cap; per-player, one per cooldown. Tested separately as a wear clamp (D08 spec).
- **Heart of the Foundry** (tc_foundry_heart, 6000t): N melting operations from personal insertions prepare melting/cooling progress bonus for duration. Joins workshop acceleration cap.
- **Many Hands, One Forge** (tc_many_hands, 3600t): You and ally both working same focused workshop within window grant every contributor repair restoration for duration. Joins the repair bonus cap.

**Eligibility:** Reachable at configured governing skill threshold. Unequippable with `MISSING_CAPABILITY` reason when Tinkers' absent, the required capability unsupported, or `enableTConstructPowers` off. Eligibility rechecked on every proc; saves retain ids so reinstalling Tinkers' later restores them.

**Cooldown persistence:** Cooldown debt survives logout (§15.1). Serialized as remaining ticks into `runicskills:tc_state` (schema 1, 12-entry map bound, per `PowerCooldownDebt`), written at the moment a cooldown starts (not save time), read back into runtime map at login never shortened by session-local cooldown that started first. Only the twelve Artifice ids persisted; addon keys cannot grow player NBT.

**Presentation:** School `runicskills:tinkering`, primary colour 0xB86E2E (tan-brown) + glow 0xF0DFA8 (sand), motion RISING. Icons are checked-in assets regenerated by manually running `python tools/icongen/powers.py`; collision probe walks `sorted(power_ids)` parsed from RegistryPowers.java across all schools, so three Powers share regenerated icons at positions where rune slots differ (still distinct per Power). The build only verifies coverage via `PowerIconCoverageTest`.

**GameTests:**
- `TcArtificePowersGameTest` — one method per Power (C09 spec: correct behaviour when equipped, nothing when not equipped, cooldown gates second firing, effect magnitude stays within the seam cap). Each drives the real seam except Last Temper.
- `LastTemperGameTest` — covers D08 spec separately (lethal loss leaves 1 durability, cooldown spent, second loss inside cooldown not saved, survivable loss untouched and free).
- `CooldownPersistenceGameTest` — suite in base package (L01 spec: cooldown round-trip survives save/load, only Artifice ids persisted).

### Tinkers' Construct add-on integrations (S5)

**Seven new perks from optional add-on layers, each gated on the add-on's `enable…Integration` flag and its required capability seam. Four unavailable companions (Botania, Ars Nouveau, Create, Malum) preclude live behavior test execution but dormancy is verified in every profile.**

**Four from TCIntegrations (requires Tinkers' Construct + TCIntegrations, plus companion mods for each effect):**
- **Mana Polisher** (tc_mana_polisher, Tinkering 12): Botania-backed tool repair, mana cost −`tcManaPolisherPercent`% (default 10%), min 1. Capability: `ADDON_BOTANIA_REPAIR_CHARGE`. Mixin gate: `MixManaModifier` (`require = 0`, optional seam).
- **Source Tempering** (tc_source_tempering, Magic 12): Ars Nouveau armour repair, next spell +`tcSourceTemperingPercent`% damage (default 5%) within `tcSourceTemperingWindowSeconds`s (default 8). Capability: `ADDON_ARS_ARMOR_REPAIR`. Mixin gate: `MixArsNouveauBaseModifier` (`require = 0`, optional seam).
- **Clockwork Alternation** (tc_clockwork_alternation, Tinkering 12): Create Mechanical Arm offhand attack, next tool use avoids `tcClockworkAlternationPercent`% durability (default 10%) within window (default 5s). Capability: `ADDON_OFFHAND_MELEE`. Mixin gate: `MixToolAttackUtil` (required; no companion mod check in mixin, only in registry).
- **Soulsteel Resolve** (tc_soulsteel_resolve, Constitution 16): Malum soul-stained equipment, primary hit grants knockback resistance +`tcSoulsteelResolveAmount` (default 0.06) for `tcSoulsteelResolveSeconds`s (default 4). Capability: `ADDON_SOUL_STAINED`. Mixin gate: `MixToolAttackUtil`.

**One from Tinkers' Levelling Addon (requires add-on only):**
- **Seasoned Hands** (tc_seasoned_hands, Tinkering 12): Tool experience award ×(1 + `tcSeasonedHandsPercent`/100, default 10%). Fractional remainder carries per player per tool item; no scaling past the add-on's cap. Capability: `ADDON_TOOL_LEVELLING`. Mixin gate: `MixToolLevellingUtil` (required).

**One from Tinkers' Delight (requires add-on + Farmer's Delight):**
- **Banquet of Cinders** (tc_banquet_of_cinders, Wisdom 12): Primary melee damage +`tcBanquetOfCindersPercent`% (default 3%) only while Nourishment effect active; summed under `tconstructNewDamageBonusCap`. Capability: `ADDON_CULINARY_EFFECT`. No mixin gate (reads `ModEffects.NOURISHMENT`).

**One from Tinkers' Advanced (requires add-on + EtSTLib library):**
- **Charged Craft** (tc_charged_craft, Tinkering 12): FE cost −`tcChargedCraftPercent`% (default 10%), min 1 FE, for identified tool operations only (machines and cables pay full price). Capability: `ADDON_TOOL_ENERGY`. Mixin gate: `MixToolEnergyUtil` (required). Note: Tinkers' Advanced beta.13 (main project) cannot boot dedicated server (upstream unqualified EventBusSubscriber); test environment uses split Core beta.5.

**Reserved (unregistered, no artifact available):**
- **Medallion Concord** (tc_medallion_concord, Wisdom 16, Tinkers' Ingenuity): No public 1.20.1 artifact could be resolved from any maven; perk id retained but unregistered. Capability: `ADDON_MEDALLION` reports `UPSTREAM_UNAVAILABLE` so the absence is explained rather than mysterious.

**Adapter class architecture:** Each add-on has its own adapter class in `integration/tconstruct/addons/`, loaded reflectively by `TcAddonRegistry` only after presence check. If the adapter load fails, the perk stays dormant with a logged reason. Adapter `install()` methods register mixin hooks (e.g., `MeleeDamageContributor`), read upstream APIs, and verify seam shapes reflectively before committing to injection points.

**Mixin plugin gate:** `RunicSkillsMixinPlugin.applyAddon(simpleName, addonModId)` checks three conditions in order: Tinkers' present, version supported, integration enabled; then the add-on's own mod id. Result recorded so `TConstructCompatibilityStatus.describe()` reports why a mixin did or did not apply.

**Capability diagnostic:** Seven capabilities added to `TConstructCompatibilityStatus.Capability` enum. Each is set by `TcAddonRegistry.describe()` based on: adapter installed and running, companion mods present (if required), seam shape verified (optional mixins), and mixin applied. Possible statuses: `SUPPORTED`, `ABSENT`, `UPSTREAM_INCOMPATIBLE`, `HOOK_UNAVAILABLE`, `DISABLED_BY_CONFIG`, `UPSTREAM_UNAVAILABLE`.

**Temporary state:** Four perks (Clockwork Alternation, Seasoned Hands carry, Source Tempering, Soulsteel Resolve) hold per-player server memory in `TcAddonState` (sibling to `TConstructPerkState`). Clears on logout, death, respec, dimension change, server stop, and perk disable. State never travels with items; no benefit survives a relog without continuous session.

**Config expansion:** Four `enable…Integration` flags (enableTcIntegrationsIntegration, enableTinkersLevellingIntegration, enableTinkersDelightIntegration, enableTinkersAdvancedIntegration) plus 13 tuning fields per add-on (cooldown ticks, window seconds, percent/amount values). Levels scale per-perk by skill cap (formula in S4a section).

**GameTests (§18.3 C08 spec: active, inactive, dormant, boundary):**
- `TcAddonAbsenceGameTest.addonPerksExistOnlyWithTheirAddon()` — runs every profile, asserts registry presence exactly matches add-on presence.
- `TcAddonAbsenceGameTest.addonCapabilitiesNameWhatIsMissing()` — capabilities report `ABSENT` when missing companion, no false `SUPPORTED`.
- `TcAddonAbsenceGameTest.medallionConcordIsReservedAndUnregistered()` — id never registered, capability always `UPSTREAM_UNAVAILABLE`.
- `TcAddonPerksGameTest.seasonedHandsScalesOneAward()`, `seasonedHandsIgnoresCommandAndNegativeAwards()`, `banquetPaysOnlyWhileNourished()` — live behavior on M4/M6 profiles.
- `LevellingCoexistenceGameTest` — four methods asserting player XP and tool XP never mix, carry never travels, cap respected, perk off/on produces only intended difference.
- `TcChargedCraftGameTest.chargedCraftDiscountsWithAPositiveMinimum()` — live behavior on M5/M6 profiles (if Advanced can boot).

**Profiles:**
- **M0 (absent):** No add-on perks registered.
- **M1 (stable 3.11.2.166):** No add-ons; all seven perks dormant, capabilities `ABSENT`.
- **M3 (TCIntegrations only):** Four perks registered, companions absent so capabilities `ABSENT`.
- **M4 (Levelling + Delight):** Seasoned Hands and Banquet tests live; Levelling coexistence tests live.
- **M5 (Advanced):** Charged Craft test live (if server boots).
- **M6 (all add-ons):** All live tests combined (minus companion-dependent seams).

See [`docs/TCONSTRUCT_TEST_MATRIX.md`](docs/TCONSTRUCT_TEST_MATRIX.md) for exact artifact versions, SHA-256 hashes, gametest counts, and run commands per profile.

### Pack rules, scripting and migration (S6)

**Pack rules (datapack-authored, off by default in the sense that no default rule ships):**
- New `runicskills/tconstruct_rules` datapack folder (`data/<ns>/runicskills/tconstruct_rules/*.json`), parsed by `TConstructRulesLoader` into an immutable, revisioned `PackRuleIndex`. A pack may state a `use_requirement` (skill levels a native tool's definition/materials/role/tier/action must meet) or a `craft_reward_policy` (whether a station result may be paid a bonus copy). Precedence is pack rule > existing item-id lock > the automatic material-tier profile (still gated by `enableTConstructLockItems`); a `craft_reward_policy` allow can never lift the mandatory bonus-copy exclusions, only a refusal is honoured. Unknown fields refuse the file by name; a duplicate id or an equal-priority disagreement at lookup time produces no verdict rather than picking by filesystem order; any unreadable file in a reload refuses the whole reload and keeps the last-good ruleset — except when there is no last-good ruleset yet, in which case the files that did parse are installed and the failure is only reported. See [`docs/TCONSTRUCT_PACK_RULES.md`](docs/TCONSTRUCT_PACK_RULES.md).

**KubeJS tinkering events:**
- Three new server-only events observing native Tinker Station operations: `tinkerOperationCheck` (cancellable pre-commit gate; a throwing script denies), `tinkerOperationCompleted` (read-only, post-commit), and `tinkerToolLevelChanged` (read-only, posted on a real Tinkers' Levelling Addon transition). A denial from `tinkerOperationCheck` stops only the Runic payout, repair top-up, or Keystone service; the native Tinkers' operation is never cancelled from it. See [`docs/KUBEJS.md`](docs/KUBEJS.md#tinkers-construct-tinkering-events).

**Advancement triggers:**
- Three new criteria for quest packs, registered whether or not Tinkers' Construct is installed: `runicskills:tinker_assembly`, `runicskills:tinker_paid_repair`, and `runicskills:great_work`. Fired only from the committed, once-per-take points inside the station bridge and the Great Work Power completion, never from a preview or quote. Documented in [`docs/API_EVENTS.md`](docs/API_EVENTS.md#advancement-criteria-quest-pack-integration).

**GameTests:** `RulesReloadGameTest` (five methods covering a valid reload, an unknown field, a broken reload leaving the last-good ruleset, an equal-priority conflict, and a duplicate id), `DormantContentGameTest` (three methods covering saved `tc_` selections surviving a load/save, a dormant perk id costing no budget, and an unavailable Power being neither active nor re-equippable), and `KubeJsTinkerEventsGameTest` (three methods covering the hook contract, a throwing script, and — where a live KubeJS server-scripts environment is available — a real script denying a Keystone service without affecting other operations).

### Fixed

**Capability registration.** `RegisterCapabilitiesEvent` is an `IModBusEvent`, posted only to the MOD bus, but the listener that called `event.register(SkillCapability.class)` lived on `PlayerLifecycleHandler`, a FORGE-bus `@Mod.EventBusSubscriber`. It never fired, so `RegistryCapabilities.SKILL` was an unregistered `Capability` in every shipped build — a latent defect predating 2.0.7, not introduced by it. It went unnoticed because `LazySkillCapability` hands the capability out itself and nothing on the hot path calls `Capability#isRegistered()`. The listener now lives on `RegistryCapabilities`, a MOD-bus `@Mod.EventBusSubscriber` where the capability is declared; `PlayerLifecycleHandler`'s other FORGE-bus listeners are unchanged.

**Salvage perk descriptions.** `resource_efficiency.description` and `salvage_expert.description` still described breaking a crafting or crafted block, though both perks now add a recovery chance at a grindstone (`WorkshopPerkHandler.onGrindstoneChange`). `salvage_master.description` said salvaging yielded more materials, though it adds to the recovery chance rolled when an item breaks in the inventory (`WorkshopPerkHandler.onItemDestroyed`) — the yield wording belongs to Salvage Luck, not Salvage Master. All three lang strings are corrected; see [`docs/RECYCLING_RULES.md`](docs/RECYCLING_RULES.md) for the mechanism.

**Tinkers' mixins crashed the released 2.0.7 jar in a Tinkers' pack.** `@Mixin(targets = ..., remap = false)` makes every member reference in a Tinkers'-targeting mixin literal. `MixModifiableBowItem`, `MixThrownTool`, and `MixLazyResultContainer` hook `releaseUsing`, `tryPickup`, `getItem`, `removeItem`, and `removeItemNoUpdate` — vanilla-declared methods that ship SRG-obfuscated (`m_5551_`, `m_142470_`, `m_8020_`, `m_7407_`, `m_8016_`) inside the Tinkers' jar. Dev and GameTests run Mojang-mapped, so the literal names resolved there and the 200-test Tinkers' suite never caught it; a real pack does not, and Mixin treats a failed injector as fatal for the whole mixin config, so the released jar crashed on load. The three affected mixins now declare `remap = true` with descriptor-qualified selectors on the individual injectors; the class-level `remap = false` is unchanged, since class names are not obfuscated. (The nested `@At` in `MixModifiableBowItem` targeting `ModifierUtil.getInaccuracy` stays `remap = false`, because that member is Tinkers'-declared, not vanilla.)

**Arcane Reforging has been silently inactive for every player, in every shipped build.** The same class of defect was already present in `MixReforgingResultSlot`, which hooks Apotheosis's `ReforgingMenu.ReforgingResultSlot#onTake` — itself an override of vanilla `Slot#onTake`, and therefore SRG-renamed in production exactly like the Tinkers' hooks above. With `remap = false` and `require = 0`, the mixin matched in dev and matched nothing at all in production, with no crash and no log line to notice. Fixed the same way: the injector is now `remap = true` with the descriptor spelled out.

**Tinkers' mixins no longer take down the mixin config on a failed hook, and say so.** Every injector under `mixin/tconstruct/**` now declares `require = 0, expect = 1`: none of these hooks is load-bearing — each one only adds an effect, so its absence costs a perk, never correctness — and a hard crash in a large pack over one missing Tinkers' hook is the wrong failure mode. `TConstructHookLedger` records, from Mixin's own `postApply` callback, which hooks actually applied (as opposed to which ones were merely offered), so the silence a lenient `require = 0` would otherwise cause is made visible instead: a `TCONSTRUCT_COMPAT` line in the startup log, and `/skills tinkers compat`, now distinguish a hook that was offered and did not match (`HOOK_UNAVAILABLE`) from one that was never offered because Tinkers' is absent (`ABSENT`) or the integration is off (`DISABLED_BY_CONFIG`).

**New build checks catch this class of bug before a build ships.** `checkMixinRemapping` (wired into `check`) resolves every hooked member through the real class hierarchy of the target jars with ASM, classifies it by its declaring package, and fails the build if a `remap = false` mixin references a `net.minecraft.**`-declared member — or, by the mirror rule, if a `remap = true` mixin references a member that isn't vanilla-declared. It is what found the Arcane Reforging defect above. `verifyShippedRefmap` asserts the *shipped* jar carries the expected SRG refmap entries and the bundled MixinExtras, and that README names the jar it just inspected. New `tools/verify_against_pack.py` proves, without launching the game, that every member each Tinkers' mixin will look for at runtime exists in a real pack's obfuscated jars — see [`docs/PACK_VERIFICATION.md`](docs/PACK_VERIFICATION.md).

**Distribution defect: the plain jar never bundled MixinExtras, and Forge does not supply it.** Forge 1.20.1 47.x does not provide MixinExtras — confirmed absent from the launcher libraries and from the forge/fmlcore/fmlearlydisplay jars of the 47.4.22 runtime in a live pack — and seven mixins depend on MixinExtras annotations, three of them core (`MixItemStack`, `MixGrindstoneMenu`, `MixExperienceOrb`). The unclassified `runicskills-<version>.jar` was the un-bundled `jar` task output, so it worked only on packs where some unrelated mod happened to bundle MixinExtras itself. The `jar`/`jarJar` classifiers are swapped: the distributable `runicskills-<version>.jar` is now the `jarJar` bundle, carrying MixinExtras under `META-INF/jarjar/`; the un-bundled build is `runicskills-<version>-slim.jar` and is not for distribution.

### Release verification

- 468 unique perk ids registered in `RegistryPerks`, of which 23 are `tc_` (16 core + 7 add-on). The spec proposed 24; the 24th, `tc_medallion_concord`, is reserved and unregistered because Tinkers' Ingenuity has no 1.20.1 artifact.
- 87 `RegistryObject<Power>` declarations in `RegistryPowers` (75 pre-existing + 12 Artifice).
- 52 mixins: 5 client, 47 common, of which 15 target Tinkers' Construct (10 core + 5 add-on).
- GameTests: 116 pass with no profile, 200 with `-PtinkersProfile=stable`.
- `check_mod.py`: 0 errors, 1 warning — a false positive at `CustomNpcsIntegration.java:17`, where the only `net.minecraft.client.` text is a String constant fed to `Class.forName` in a reflective drift probe; no client type enters the constant pool.
- The packaged jar ships no third-party classes and no API stub classes (MixinExtras remains a declared jar-in-jar dependency under `META-INF/jarjar/`), with the version expanded into `META-INF/mods.toml`.

## [2.0.6] - 2026-09-04 — Inventory tabs: the built-in strip is the guaranteed fallback

No protocol, config schema, or save-data change; network protocol stays at 11. Dev Forge is 47.4.23; `forge_version_range` remains `[47,)`.

### Inventory tabs

**Fixed:**
- **Suppression is now evidence-based**, not presence-based. Before 2.0.6, Runic Skills disabled its own tab strip whenever Legendary Tabs, L2Tabs or CustomNPCs were merely installed, even if their registration failed or they had no tab strip on the current screen. Any API break, version mismatch or configuration that prevented the external tab from loading left the player with no Skills tab at all. Now:
  - **Legendary Tabs**: `LegendaryTabsIntegration.registerClientTab()` probes `sfiomn.legendarytabs.api.tabs_menu.TabBase` and only sets the active flag after `TabsMenu.register` succeeds. A Legendary Tabs 1.x/2.0 mismatch or API break is caught, logged once as WARN, and downgraded to the built-in strip instead of escaping `enqueueWork` and failing mod load.
  - **L2Tabs**: already probes before claiming registration (unchanged in 2.0.6).
  - **CustomNPCs**: split into `isNativeTabsPreferred()` (probe passed and `customNpcsNativeTabs` is on) and `isNativeTabsActive()` (that, and the tab is on the screen currently being drawn, reported per frame by the client integration). Screens where CustomNPCs has no strip to join now fall back to Runic Skills' own strip instead of nothing. — `integration/InventoryTabOwnership.java`.
- **CustomNPCs tab insertion no longer renumbers upstream's tabs before checking the insert will fit.** Pre-2.0.6 the renumber ran first and the insert second, so whenever the insert failed — the `addRenderableWidget` reflection not resolving, which latches and fails for the rest of the session — the renumber still ran on every frame of this per-frame pass, walking CustomNPCs' own tabs one slot right per frame until they left the screen. The order is now insert-first, renumber-second — `client/integration/CustomNpcsTabsClientIntegration.java:280–300`.
- **CustomNPCs: the full strip on the Skills screen waits for evidence of an upstream strip on another screen.** A CustomNPCs build or configuration that doesn't add a strip can no longer put the player with a CustomNPCs arrangement on the Skills screen and a separate Runic Skills arrangement on the inventory. The strip is only built once `upstreamStripSeen` is true — `client/integration/CustomNpcsTabsClientIntegration.java:94–104`.
- **AUTO anchor selection now picks the least-covered candidate when all collide.** When the inventory has many other mods' widgets, every anchor position overlaps. The old unconditional fallback to TOP_LEFT could hide the entire strip behind another mod's widgets; the new search picks the position with the smallest overlay instead — `client/gui/InventoryTabLayout.java:147–164`.
- **Shift-right-click on the inventory tab strip now resets the drag offset to (0, 0).** A strip nudged into a corner and no longer visible could only be recovered by editing the TOML; it is now one click away — `client/event/InventoryTabsScreenHandler.java:132–145`.
- **RunicSkillsScreen.onClose unconditionally clears the DrawTabs click latch.** Ownership can change between frames now (CustomNPCs reports per-screen); leaving the latch armed because an external strip owned the last frame is how a click that closed the Skills screen becomes a tab switch on the next one — `client/screen/RunicSkillsScreen.java:925–939`.

**Config comments:**
- `inventoryTabsEnabled` rewrites "the built-in strip is drawn so the tab is never missing entirely" (it always is now).
- `inventoryTabsOffsetY` and `inventoryTabsDragToMove` explain the Shift-drag and the new Shift-right-click reset.
- `customNpcsNativeTabs` explains the per-screen fallback.

**Testing:**
- New JUnit test: `InventoryTabLayoutTest.everythingBlockedPicksLeastCovered()` covers the least-covered anchor selection.

## [2.0.5] - 2026-09-03 — Stability, perk semantics, crafting authority & inventory tabs

No protocol, config schema, or save-data change; network protocol stays at 11. Client TOML gains `tabs` configuration group with safe defaults. Dev Forge is 47.4.23; `forge_version_range` remains `[47,)`.

### Confirmed fixes

- **Lucky Break** now prevents durability loss on tool-like items (TieredItem, shears, and items in the `runicskills:lucky_break_eligible` tag; `runicskills:lucky_break_ineligible` excludes) via `MixItemStack`, instead of passively repairing the held item — `common/durability/DurabilityPerkRules.java`, `mixin/MixItemStack.java`.
- **Mending Boost** now amplifies real Mending repair in `ExperienceOrb.repairPlayerItems` via the new `MixExperienceOrb` mixin without spending extra XP; it no longer repairs passively — `mixin/MixExperienceOrb.java`.
- Passive repair accumulator moved to `common/durability/PassiveRepairAccumulator.java`; Lucky Break and Mending Boost removed from it.
- **Crafting rewards are server-authoritative**: every craft handler starts from `ServerPlayer` and rejects `FakePlayer` explicitly (note `FakePlayer` extends `ServerPlayer`) — `PerkEffectsHandler.onCraft`, `CraftingEventHandler`, `FortunePerkHandler`, `LocksIntegration`, `OvergearedIntegration`, `MixStonecutterMenu`.
- **Bonus-output crafting perks** (Assembly Line, Mass Production, Alloy Master, Master Woodworker, Medieval Architecture) roll independently instead of summing into one saturating chance; new `common/crafting/CraftingExecutionGuard.java` re-entry guard.
- **Efficient Crafting** now really preserves consumed materials: `mixin/MixResultSlot.java` + `common/crafting/CraftingRefund.java`; remainders (buckets, bottles, damaged tools) never duplicated; modded `ResultSlot` subclasses skipped.
- **Master Researcher** uses `common/crafting/MasterResearcherRecipeIndex.java` (rebuilt on datapack reload, cleared on server stop) with a candidate budget instead of scanning up to 4096 recipes per craft; Inventor draws from the same index; per-recipe/ingredient `RuntimeException` isolation with `common/util/LogOnce.java`; no partial unlock state. **Master Artificer** isolates each `canEnchant` call and caches candidates per item. Phrase: "improved compatibility with modded recipe/ingredient implementations, including configurations using ModernFix".
- **Inventory tabs**: new layout engine `client/gui/InventoryTabLayout.java` with AUTO anchor selection avoiding the recipe book, the potion-effect panel and any region contributed via `client/gui/InventoryTabReservedRegions.java` or the new `api/client/InventoryTabLayoutEvent`; Shift-drag moves the strip and the offset persists in the client TOML (`inventoryTabsEnabled`, `inventoryTabsAnchor`, `inventoryTabsOffsetX/Y`, `inventoryTabsAvoidRecipeBook`, `inventoryTabsAvoidEffects`, `inventoryTabsDragToMove` in `handler/HandlerConfigClient.java`); tooltip/click/drag/close moved to Forge `ScreenEvent`s in `client/event/InventoryTabsScreenHandler.java` (which absorbed `InventoryTabsCloseHandler`); render/hover/tooltip/click share one set of rectangles; the strip routes around every visible widget another mod adds to the inventory screen outside the inventory panel (other mods' tab strips and side buttons; CustomNPCs is the known case), so no per-mod adapter is needed; Legendary Tabs / L2Tabs suppression unchanged; the 2.0.2 close/reset fix preserved. With CustomNPCs installed the Skills tab now lives inside CustomNPCs' own tab strip (Inventory, Skills, Factions, Quests) on the inventory, Faction and Quest screens and on the Skills screen, and Runic Skills draws no second strip and no second inventory icon; `customNpcsNativeTabs` (client TOML, default true) restores the separate strip when false; the integration compiles against a hand-written stub and probes the real classes at runtime, falling back to the separate strip if they differ.

### Perk semantics

- **Precision Tools** — durability-loss avoidance on tool-like items (`DurabilityPerkRules.isTool`, the `lucky_break_*` tags) with chance X/(100+X) so expected lifetime is exactly +X% (`common/durability/DurabilityMath.bonusDurabilityToAvoidance`, `mixin/MixItemStack`).
- **Tinker's Touch** — crafted damageable items are stamped (`common/util/ItemBonusTags`, `mixin/MixCraftingMenu` at `slotChangedCraftingGrid`) and `MixItemStack`'s `getMaxDamage` hook scales max durability; implemented at slot-change rather than `ItemCraftedEvent` because shift-click copies the result before the event fires.
- **Tool Smith / Weapon Smith** — anvil-repaired tools/weapons are stamped in `AnvilPerkHandler.onAnvilResult`; consumers are `PerkEffectsHandler.onBreakSpeed` (mining speed) and the new `registry/events/SmithingPerkHandler` (`ItemAttributeModifierEvent`, attack-damage modifier with UUID declared in `registry/RunicAttributeModifiers.Scope.ITEM`).
- **Runic Engineering** — X% chance (deterministic per input pair) that repairing a runic item (registry id contains "runic"/"rune") raises its lowest non-max enchantment by one level.
- **Heritage Builder** — optional MineColonies integration (`integration/MineColoniesIntegration`, `common/util/HeritageBuilderHook`, `mixin/MixPathingStuckHandler`) — colony buildings whose owner is online with the perk: raider block breaks cancelled X% of the time; X% of explosion-affected blocks spared (note: redundant under MineColonies' default `turnoffexplosionsincolonies` setting; matters when servers allow explosion damage). Compile-only dependency; nothing loads without MineColonies.
- **MixAnvilMenu** (`createResult` RETURN) hands the real computed anvil result to `AnvilPerkHandler`; state plainly that Enchantment Amplifier and Enchantment Stacking previously never fired on a vanilla anvil because Forge's `AnvilUpdateEvent` runs before vanilla computes the output (empty unless another mod fills it) and now do.
- Config percentages are baked into the item at craft/repair time (stamps use the max rule, never additive).
- Auto Repair is now the only passive-repair perk (`PassiveRepairAccumulator`).

### Hardening

Unified `common/combat/DamageContext.java` — every Runic-emitted secondary damage call carries an origin (Limit Breaker, Cleave, Bulwark reflect, Weapon Caster reflect, power echo, summon burst, channel splash, spell effects), standard outgoing modifiers apply only to primary hits, secondaries cannot spawn secondaries, hard depth ceiling of 4; the local `CLEAVING` set and `IN_REFLECT`/`IN_ECHO`/`IN_BURST`/`IN_SPLASH` guards were removed in its favour; every damage `event.setAmount` in the mod's event handlers and integrations — 72 sites across 23 files — now passes through `common/combat/DamageMath.safeAmount`, which keeps the original amount when a multiplier would produce NaN, infinite or negative damage; opt-in diagnostics `common/combat/CombatDiagnostics.java` enabled with `-Drunicskills.debug.combat=true` or DEBUG logging. Hardened recursive cross-perk secondary-damage interactions and added safeguards for large multi-skill builds.

### KubeJS

**Fixed:**
