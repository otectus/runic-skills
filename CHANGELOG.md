# Changelog

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
