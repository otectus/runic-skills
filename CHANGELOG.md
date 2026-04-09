# Changelog

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
