# Tinkers' Construct Integration (2.0.7)

Player and administrator guide to using Runic Skills with Tinkers' Construct 1.20.1.

## What version is supported?

- **Stable (supported):** Tinkers' Construct 1.20.1-3.11.2.166 + Mantle 1.20.1-1.11.97
- **Beta (conservative mode):** Tinkers' 1.20.1-3.12.x runs without Runic mixins; see below
- **Earlier or later versions:** Run in conservative mode (classification and repair only, no wear avoidance)

Compilation is always against the stable Tinkers' API pin (build.gradle:329-334); the `-PtinkersProfile` property only swaps the runtime classpath between stable and beta (build.gradle:392-406). At runtime, Tinkers' is detected once and a profile is chosen. The version shows in the startup log via `tconstructCompatibilityDiagnostics` (default on).

## How does the profile get detected?

`TConstructProfile.detect()` reads Forge's mod container for the loaded Tinkers' Construct version, once at startup. The major and minor version (e.g., "3.11" from "1.20.1-3.11.2.166") decides the profile; patch versions do not differ. The profile gates which Runic mixins are applied during class transformation, so it cannot change without a restart.

## What does "conservative mode" mean?

When Tinkers' is absent, on an unrecognised version (e.g., 3.12 in 2.0.7), or when `enableTConstructIntegration` is off, conservative mode engages:

- **Enabled:** Equipment classification (what role a tool plays), repair eligibility (which native repair hook to use), and optional material-tier skill requirements (see below).
- **Disabled:** Wear avoidance (Lucky Break, Precision Tools do not reduce Tinkers' durability loss), workmanship stamping (Tool Smith / Weapon Smith bonuses do not persist on native tools), and the `runicskills:workmanship` modifier.

A player on 3.12 has working classification and repair, so their Tinkers' perks function; they simply do not get wear reduction or craftsmanship bonuses until a later release that compiles against 3.12.

The diagnostics command (`/skills tinkers compat` in S3) shows which capability is in which state and why.

## Configuration

All Tinkers' config flags are in the server config file (`runicskills-common.json5` in the server-side directory).

| Field | Default | Restart? | Meaning |
| --- | --- | --- | --- |
| `enableTConstructIntegration` | `true` | YES | Master toggle. When off, no Tinkers' equipment is classified, no wear or repair hook is installed, and the `runicskills:workmanship` modifier is not registered. |
| `enableTConstructPerks` | `true` | no | Gates all Tinker's perks; the first check in `TConstructPerkHandler.active()`. |
| `enableTConstructPowers` | `true` | no | Gates all Artifice Powers (the twelve tc_* Powers); checked by `PowerEligibility` and dispatcher on every proc. |
| `enableTConstructLockItems` | `false` | no | Should Tinkers' tools carry automatic skill requirements derived from their material tier? **Off by default, deliberately** — turning it on in an existing world can lock players out of equipment they already own. Explicit item locks are enforced either way. |
| `tconstructCompatibilityDiagnostics` | `true` | no | Log the Tinkers' compatibility summary at startup and report it via `/skills tinkers compat` (S3). Each of ten capabilities (CLASSIFICATION, REPAIR, WEAR_AVOIDANCE, WORKMANSHIP, STACK_REQUIREMENTS, STATION_TRANSACTIONS, HARVEST_AOE, PROJECTILES, WORKSHOP, KEYSTONE) reports status and reason. |
| `tconstructRepairBonusCap` | `0.5` | no | Ceiling on extra durability Runic repairs may add to a Tinkers' repair (§5.3): 0.5 means at most half again what the materials paid for. |
| `tconstructMaterialCostDiscountMode` | `false` | no | Reserved for a future feature. Has no code path in 2.0.7; changing it does nothing. |

## Equipment classification

The `TConstructEquipmentAdapter` recognises native tools (items in the `tconstruct:modifiable` tag) and reads their roles from Tinkers' own tags.

| Tinkers' Tag | Maps to Runic Role | Notes |
| --- | --- | --- |
| `tconstruct:durability` | `TOOL` | Required for durability perks to act |
| `tconstruct:harvest` | `DIGGER`, `TOOL` | Mining tools |
| `tconstruct:melee_weapon` | `MELEE_WEAPON` | Swords, axes, and tools used in melee |
| `tconstruct:ranged` | `RANGED_WEAPON` | Bows, crossbows |
| `tconstruct:armor` | `ARMOR` | Armor pieces |
| `tconstruct:shields` | `SHIELD` | Shields |

Tools in other Tinkers' tags (staves, fishing rods, special-use items) are classified only if they carry one of the above tags. Tinkers' data-driven tool definitions apply their own tags, so a modpack's custom tools are automatically classified without this file knowing about them.

## Wear avoidance

Perks that reduce durability loss (Lucky Break, Precision Tools, Unbreakable, Unbreaking Mastery, Gadgeteer, Lock Expert) feed into a single probability. The combined chance is capped at 0.90 (90%), so an item can never be completely immune to wear.

Wear avoidance is checked once, at the point where `ToolDamageUtil.damage` passes through `directDamage` — after native modifiers have had their say, before ordinary loss is committed. This is the phase documented in the integration spec as "post-modifier, pre-ordinary-loss".

**Only ordinary-use actions are reduced.** Wear avoidance applies only when:
- The player is performing a confirmed action (block break or melee swing, each with its own seam in the codebase).
- The durability loss is ordinary use, not a modifier paying for an ability, a tank draining a resource, or tool damage from something outside an action (e.g., a falling block).

An unidentified origin (no positively identified action frame) gets native behaviour — the full, unreduced loss.

## Repair routing

Auto Repair and other Runic-origin repairs on native tools go through `TConstructRepairBridge.repair()`:

1. Check if the tool is a modifiable item, is not broken or unbreakable, and has damage to repair.
2. Compute the tool's native repair factor from its modifiers (the same composition `TinkerStationRepairRecipe` performs).
3. If the factor is ≤ 0, the tool forbids repair (return 0 points spent).
4. Scale the request by the factor: `scaled = floor(points * factor)`.
5. Call `ToolDamageUtil.repair()` to commit it.
6. Return the committed amount (max damage - current damage), not the request.

This ensures a Runic repair respects the tool's own rules, including modifiers that forbid or reduce repair. A modifier with a 0.5 repair factor means a 10-point Runic repair spends only 5 points on that tool.

## Workmanship (craftsmanship bonuses)

Three smithing perks stamp craftsmanship onto items:
- **Tinker's Touch:** `runicskills.bonusDurability` (maximum durability bonus)
- **Tool Smith:** `runicskills.toolSmith` (mining speed bonus)
- **Weapon Smith:** `runicskills.weaponSmith` (attack damage bonus)

On Tinkers' tools, these are stored in namespaced persistent data (the tool's NBT subtree), read and written by the `runicskills:workmanship` modifier. The modifier applies the durability bonus as a `TOOL_STATS.DURABILITY` percentage at stat rebuild.

**Lazy migration:** Tools made by earlier releases carry the old integer stamp in the root tag. The first read or write moves it into persistent data, adds the `runicskills:workmanship` modifier if not already present, and rebuilds. Unrecognised data on the stack is left alone.

**Idempotent:** The bonus is a pure function of the stamped percentage. A hundred stat rebuilds (part swaps, trait changes, modifying the tool, etc.) produce identical results.

**Never accumulative:** The stamp keeps the maximum of old and new values, never a sum. A player who levels Tinker's Touch from rank 1 to rank 4 earns the rank-4 bonus on all crafted items, not a cumulative stack.

The `runicskills:workmanship` modifier implements `ToolStatsModifierHook` and applies the durability bonus at stat rebuild (WorkmanshipModifier.java:49, 64-86). The `runicskills:keystone` modifier is registered in 2.0.7 with no hooks (KeystoneModifier.java:19-23), so tools stamped with keystone load correctly in a 2.0.8 that adds its behaviour.

## Perks

Sixteen Tinkers' Construct perks debut in 2.0.7 (stage S4a) and S4 completion, covering native tool wear avoidance, repair bonuses, melee and mining enhancements, ranged accuracy and return, attribute buffs, workshop acceleration, and permanent upgrades. All are dormant when Tinkers' is absent; saved ids do not count against the perk budget and do not lock players out of active perks (see `PerkBudgetDormancyGameTest`).

All effects are gated on `enableTConstructPerks` (default on) and the capability the seam depends on (e.g., WEAR_AVOIDANCE, CLASSIFICATION, WORKSHOP). A missing dependency leaves a perk with no effect but keeps its id selectable so saves remain valid.

**Shared caps:**

| Cap | Default | Used By |
| --- | --- | --- |
| `tconstructNewDamageBonusCap` | 0.30 | Tempered Edge, Adaptive Grip (melee) |
| `tconstructNewMiningBonusCap` | 0.25 | Precision Footing, Adaptive Grip (mining) |
| `tconstructNewActionSpeedBonusCap` | 0.20 | Counterweight, Returning Hand |
| `tconstructNewDamageReductionCap` | 0.25 | Ember Guard |
| `tconstructRepairBonusCap` | 0.50 | Material Harmony, Field Service |
| `tconstructMaxAvoidance` | 0.90 | Repair Memory, Slime Steward (summed with other wear perks) |
| `tconstructWorkshopBonusCap` | 0.25 | Thermal Rhythm, Workshop Cadence |

All caps apply *after* summing effects; two perks that both raise melee damage are combined first, then capped, never separately. This is identical to the composition of vanilla durability perks under one wear ceiling.

**Level scaling:**

Every perk's base level was written against the stock cap of 32. On a server that changes `skillMaxLevel`, base levels scale using the formula:

```
max(1, min(skillCap, ceil(baseLevel * skillCap / 32)))
```

So a level-32 perk on a cap-32 server (32 = 32) remains level 32; on a cap-16 server it becomes level 16; on a cap-64 server it becomes level 64. This keeps perk ordering intact and keeps all perks reachable.

**Dormancy rule:**

When Tinkers' is absent, every `tc_*` perk is registered as null (RegistryPerks.java:3856-3997 use conditional registration). On login, if a saved perk id does not resolve in the registry, `RegistryPerks.countEnabledPerks` skips it in the budget count; the saved id stays so reinstalling Tinkers' later restores it.

**Temporary state and lifecycle:**

Perks that track personal, temporary state (Repair Memory charges and window, Adaptive Grip tool and cooldown, Workshop Cadence streak, Returning Hand window) hold that state in server memory via `TConstructPerkState`, not in the player capability. It clears on logout, death, respec (via `PlayerEvent.Clone`), dimension change, perk disable, or server stop — so a benefit earned in one session never survives a relog without the session being continuous. An unidentified tool change (e.g., swapping from one hammer to another) also clears the charge, because the binding is to the stack object itself, not to an item type.

**Perk-budget fix (S1):**

Prior to 2.0.7, unresolvable perk ids counted against the player's active-perk budget, potentially locking them out of using other perks when an optional mod was removed. In 2.0.7, `RegistryPerks.countEnabledPerks(SkillCapability)` consults only resolvable entries, so a dormant perk id is not charged against any budget. See `PerkBudgetDormancyGameTest` and [`CONTENT_STATUS.md`](CONTENT_STATUS.md).

### Keystone Tinker (S4 completion)

**How the player uses it:** Place a tool (and the recipe inputs — one netherite ingot and one amethyst shard) in the Tinker Station. On a successful craft, the tool gains one extra upgrade slot, permanently. One keystone per tool; a second attempt is refused with an error message.

**Preview vs commit seams:** The station computes one result for every player with no player attached, so recipe-level actor validation is preview-only. The authoritative check is at `Slot.mayPickup`, which both click and shift-click paths pass through before anything is consumed. The error line shows the refusal reason: ineligible smith, already fitted, or service unavailable.

**Allowlist tag:** The recipe applies only to tools in `runicskills:keystone_eligible`. The tag delegates to `#tconstruct:modifiable/bonus_slots`, which packs can edit to add or remove eligible items.

**Permanence:** There is no removal recipe. The keystone persists if the integration is disabled; the modifier is volatile and recomputed on every rebuild, so the slot is a function of "this tool carries a keystone" rather than a stored number. Disabling the service stops new keystones but does not reach back into tools already paid for.

**Tests (TcKeystoneTinkerGameTest):** `anEligibleSmithFitsOneSlotByClick()`, `anEligibleSmithFitsOneSlotByShiftClick()`, `anIneligibleSmithIsRefusedAndPaysNothing()`, `aFakePlayerFitsNothing()`, `aSecondKeystoneIsRefused()`, `disablingTheServiceKeepsAPaidSlot()`.

## Powers (Artifice)

Twelve Tinkers' Construct Powers debut in 2.0.7 (stage S4b). All are dormant when Tinkers' is absent; the Power ids remain selectable and saved so reinstalling Tinkers' later restores them. All effects are gated on `enableTConstructPowers` (default on) and the seam the Power needs. A missing seam (Tinkers' absent, unsupported version, or capability unavailable) leaves a Power unequippable with the `MISSING_CAPABILITY` denial.

**By tier:**

- **Marks (4):** First Heat, Plumb Line, Quench, Working Memory
- **Seals (4):** Hammer and Tongs, Temper Reserve, Resonant Return, Workshop Aegis
- **Crowns (4):** The Great Work, Last Temper, Heart of the Foundry, Many Hands, One Forge

Each uses a shared capability seam (CLASSIFICATION, STATION_TRANSACTIONS, WEAR_AVOIDANCE, PROJECTILES, or WORKSHOP) and composes damage, mining speed, reduction, avoidance or repair bonus into the corresponding cap. Last Temper is the one exception — it is a wear clamp (deterministic, not probabilistic), never repaired, and does not compose into a cap.

**Internal cooldowns:** Registered defaults range from 100 ticks (First Heat) to 6000 ticks (Heart of the Foundry), overridable per Power via datapack. Cooldown debt survives logout: written at the moment a cooldown starts, serialized as remaining ticks into `runicskills:tc_state` (schema 1), and restored at login via `PowerCooldownDebt.restore(ServerPlayer)`.

**Presentation:** School `runicskills:tinkering` (Artifice), colour primary 0xB86E2E (tan-brown) + glow 0xF0DFA8 (sand), motion RISING. Icons are checked-in assets regenerated by manually running `python tools/icongen/powers.py`; collision probe walks `sorted(power_ids)` parsed from RegistryPowers.java across all schools, so three Powers share regenerated icons (distinct rune slots persist). The build only verifies coverage via `PowerIconCoverageTest`.

**GameTests:**
- `TcArtificePowersGameTest` — one method per Power (C09 spec: equipped vs unequipped, cooldown gates second firing, magnitude inside the seam cap). Drives the real seams except Last Temper.
- `LastTemperGameTest` — separate class covering D08 spec (lethal loss leaves 1 durability, cooldown spent, second lethal loss is not saved).
- `CooldownPersistenceGameTest` — base suite (L01 spec: cooldown survives save file round-trip, only Artifice ids are persisted).

## Stack-aware requirements (material-tier locks)

Tinkers' tools are built from materials. Each material has a tier (0 for wood, rising to 4 for end-game materials, and 5+ for special recipes). `enableTConstructLockItems` (default **off**) gates automatic material-tier profiles.

**Precedence (§7.2):**
1. An explicit pack rule for this exact tool definition and materials (stage S6 feature, not in 2.0.7 yet).
2. An existing configured lock on the item ID. This provider declines when one exists, so the manual override takes precedence.
3. Automatic material profile (when enabled).
4. No match — the item is allowed.

**Automatic profiles (when enabled):**

| Tier | Tinkering | Functional Skill | Notes |
| --- | --- | --- | --- |
| 0 (wood) | — | — | No requirement |
| 1 | 1 | Per action (see below) | Stone, copper |
| 2 | 8 | Per action | Iron, quartz, electrum |
| 3 | 16 | Per action | Manyullyn, liver, hepatizon |
| 4 | 24 | Per action | Cobalt, osgloglas |
| 5+ | — | — | Requires explicit pack rule; no automatic lock |

The functional skill depends on the tool's primary tags and the action:

| Action | Harvest-Primary | Melee-Primary | Ranged | Armor | Hybrid (both primary) |
| --- | --- | --- | --- | --- | --- |
| **ATTACK** | — | Strength | — | — | Strength |
| **EQUIP** | — | — | — | Constitution | Constitution |
| **USE** | Endurance | Strength | Dexterity | Constitution | — |

A tool tagged as both harvest-primary and melee-primary (genuinely hybrid) requires only Tinkering when merely held (USE), not the functional skill, because asking for both would punish versatile equipment.

All levels are scaled from the stock cap of 32 to the server's `skillMaxLevel`, using ceiling and a cap, so a server that doubles the max doubles the thresholds rather than making late-game tools unreachable.

**Unknown materials are not a punishment:** If the material registry is not fully loaded, or a material is unrecognised, the item is allowed with an "undetermined" fact recorded. The inspection command (S3) will say the requirement could not be worked out instead of claiming the player meets an inferred requirement.

## Station operations

A native Tinker Station take (assemble, repair, part swap, modify) is classified by the recipe's own type and paid once per take even if the native menu re-enters Runic's reward handler through another mod's behaviour. 

**Operation classification:**
- **Assemble** (`ToolBuildingRecipe`) — yields bonus copies per policy
- **Repair** (`TinkerStationRepairRecipe`, `ISpecializedRepairRecipe`, `IModifierRepairRecipe`) — no bonus copies
- **Part swap** (`TinkerStationPartSwapping`) — no bonus copies
- **Modify** (modifier machinery recipes) — no bonus copies; a rename is a modify
- **Unknown** (unrecognised recipe type) — no bonus copies; safe default for add-on recipes

**Delivery and locking:**
- The result is transformed once via `CraftResultTransformer`, applying Master Tinkerer damage restore and Tinker's Touch stamping (TConstructStationBridge.java:150-155; MixLazyResultContainer.java:60-87).
- A player's skill-lock check applies at `MixSlot.mayPickup` before any take via `ForeignResultSlots` registration (TConstructStationBridge.java:101-103).
- On shift-click, Mantle's `quickMoveStack` moves a `copy()` from the lazy result container; the transform lands on that copy (MixLazyResultContainer.java:60-68, matching plan §6.2 "delivered copy is transformed once").
- The vanilla `ItemCraftedEvent` dispatcher recognises station containers and yields to `TConstructStationBridge.onStationCraft`, which calls `CraftRewardDispatcher.pay`, so the take is never paid twice (TConstructStationBridge.java:186-205; CraftRewardDispatcher.java:43-50).

**Quotes and revisions:**
- A player viewing a station receives a per-player private quote including the preview result after Runic changes (TConstructStationBridge.java:234-246). The quote's fingerprints — cheap hashes of inputs and base result — let the server detect if the inputs changed between quote-time and take-time (StationQuote.java:37-39).
- Quote revisions increment monotonically per server session, shared across all stations, uniquely identifying each quote (TConstructStationBridge.java:76; plan §6.2 "revision token").

## Harvesting

Area-of-effect harvests (hammers, excavators) break multiple blocks, each triggering durability loss and Runic perks independently, yet sharing one root action id.

**Per-block publication:**
- Each child block of a native area harvest opens a child action frame (ActionOrigin.NATIVE_AOE_CHILD) before calling the break logic, closing it after (MixToolHarvestLogic.java:60-79; ActionOrigin.java:23-34; TConstructCombatBridge.java:142-150).
- The child inherits the swing's root action id, so nine blocks from one hammer are nine distinct actions sharing one root — exactly what combo counters and "one payout per swing" logic need (TConstructCombatBridge.java:142-150; plan §4.3).
- Each child break that succeeds posts its own `BlockBreakCommittedEvent` as a native-origin child, published from `ToolHarvestLogic.breakBlock` after the vanilla `Block.playerDestroy` equivalent (MixToolHarvestLogic.java:82-99; TConstructCombatBridge.java:160-166).

**Protection unchanged:**
- Every block, root and child, passes through the native protection check (`ForgeHooks.onBlockBreakEvent`) with a real `ServerPlayer` before removal. A refused block never reaches the break seam, so Runic never learns it was attempted (MixToolHarvestLogic.java:29-39; plan C05).
- One root proc per swing — Treasure Hunter and other "once per action" perks claim their effect against the root action id (RunicActionContext.java:160-164; plan §4.3).

## Projectiles

A projectile launched from a native bow, crossbow, or launcher carries a snapshot recording the facts at launch time, surviving flight and any server restart.

**Launch snapshot:**
- When a native bow releases or a native crossbow fires, a `RANGED` action frame opens for the duration of the release method (MixModifiableBowItem.java:36-48; MixModifiableCrossbowItem.java:32-45). All projectiles spawned inside this frame — including multishot arrows — are created inside the same action, so each takes the same root action id (plan §9.2 "each retains the same root ID").
- As each projectile spawns, the `EntityJoinLevelEvent` handler reads the open `RANGED` frame's facts and writes them to the projectile's persistent data: owner UUID, root action id, launcher item registry id, and hand (TConstructCombatBridge.java:97-119; ProjectileSnapshot.java:65-106).
- The snapshot is bounded: at most 16 recorded Runic proc claims and 2 KiB of serialized data per projectile (ProjectileSnapshot.java:48-52). An over-budget write is refused entirely, not truncated, leaving the projectile with no Runic data rather than partial data (ProjectileSnapshot.java:89-106).

**Return detection:**
- A genuine return is detected at `ThrownTool.tryPickup`, the only place a projectile is picked up and returned to an inventory under its own native return behaviour (MixThrownTool.java:36-42). The distinction: a thrown tool with returning modifier has its physics disabled during the return flight; a tool lying on the ground has physics on. A tool picked up by a command never reaches this method (plan C04; ProjectileSnapshot.java:121-134).
- A dispenser-spawned projectile or one created by command never receives a snapshot and is therefore ineligible for all downstream perks (TConstructCombatBridge.java:97-109; ProjectileSnapshot.java:131-134).

## Diagnostics

The integration status is published to the startup log when `tconstructCompatibilityDiagnostics` is on, and can be queried at any time via `/skills tinkers compat` (see Commands below). The status shows all ten capabilities and their current state. See "Status" definitions further down.

Each capability lists a `Status`:
- **`SUPPORTED`** — Working, on a version this release was built and tested against.
- **`VERSION_UNVERIFIED`** — Tinkers' is present on an unrecognised version (e.g., 3.12 in 2.0.7). Ordinary API calls (classification, repair) still work; mixins (wear avoidance, workmanship) are not applied.
- **`UPSTREAM_INCOMPATIBLE`** — The installed version is known to differ in a way this capability needs. E.g., 3.12's `ToolDamageUtil.damage` has a different signature.
- **`HOOK_UNAVAILABLE`** — The seam this capability needs did not match (a mixin that did not apply). Usually a sign of an upstream patch release moving a call.
- **`DISABLED_BY_CONFIG`** — The server operator turned it off via config.
- **`ABSENT`** — Tinkers' is not installed.

## Workshop focus

A player standing at a smeltery, foundry, melter, or alloyer may focus it to accelerate both melting and casting. The focus is claimed via a button in the station panel (when looking at a native Tinker Station, Melter, or Alloyer) or via `/skills tinkers workshop focus` at the controller they are facing. A casting table or basin within eight blocks of the controller may be associated with the focus to also accelerate its cooling.

**Focus lifetime and expiry:**
- A focus lasts `tconstructWorkshopFocusSeconds` (default 60 seconds) before expiring on its own.
- A focus is immediately dropped when the player logs out, changes dimension, or walks further than `tconstructWorkshopFocusRadius` (default 16 blocks) from the controller.
- The server holds at most `tconstructMaxFocusedWorkshops` (default 32) active focuses at once; a request is refused when this ceiling is reached.
- Revalidation (distance, dimension, controller still intact) happens at most once per second.

**Casting associations:**
- A focused player may associate up to `tconstructMaxCastingAssociations` (default 8) casting tables or basins with their focus.
- Each association must be within eight blocks of the controller the focus claims (not of the player), so tables belong to that workshop and not just happen to be nearby.
- Associated blocks are covered by the same expiry rules as the focus itself.
- An association is explicitly marked and cannot be claimed solely by proximity, so placing a casting table across the room never silently includes it.

**Focus revalidation:**
- Every ten ticks, the server checks one player's claim: whether they still have a focus, they still meet the distance and dimension requirements, the controller still exists and is a controller, and what their perks are currently worth.
- If a check fails, the focus is dropped and the player is notified via the next status update.
- A focus issued a new revision token when any change is accepted, so a replayed request (L07's completed action token) quotes a number the server no longer recognises and is refused.

## Melting and casting bonus

A focused workshop melts and casts faster. The speed bonus is applied at `MixMeltingModule` (melting increment is raised) and `MixCastingBlockEntity` (cooling timer is advanced), never to material yields, fuel consumption, or recipe assembly.

**Which perks feed the bonus:**
- **Melting:** Smelter (melting-only) + Overclock (any station)
- **Casting:** Overclock (any station)

Both are capped by `tconstructWorkshopBonusCap` (default 0.25, or 25% speed). Bonuses below one unit (e.g., 0.15 × speed = 1.5 extra) are carried as fractional debt between ticks, so a modest bonus is not lost to truncation.

**Completing ticks remain native:**
- The melting tick that fills `currentTime` to `requiredTime` is always the native code's own tick, so native completions and their side effects (emptying the input, finishing the recipe) are untouched.
- The casting tick that raises `timer` to `coolingTime` is always the native code's own tick, for the same reason.
- This is the same design as the vanilla furnace perk: bonus increments accelerate progress, but the completing increment is native.

## Station panel

A small panel appears to the left of a native Tinker Station, Melter, Alloyer, or Crafting Station screen. It shows:
- What this player would receive from the current recipe, after Runic transforms (Master Tinkerer restore, Tinker's Touch bonus durability).
- Whether the player currently holds a workshop focus, which controller it is, how many casting tables are associated, and how many seconds remain.
- An operator-toggleable flag showing whether automation (hopper feeds) counts as this player's work.
- A keyboard-accessible "Focus" button to claim the controller the player is looking at, or release an existing focus.

**Screen detection:**
- The panel recognises native screens by simple class name: TinkerStationScreen, CraftingStationScreen, PartBuilderScreen, ModifierWorktableScreen, MelterScreen, HeatingStructureScreen, AlloyerScreen.
- If an upstream screen rename occurs, the panel simply does not appear. It is a convenience, not a critical piece of the interface.

**Placement:**
- The panel sits immediately to the left of the native container, outside the native widget area (which includes modifier tabs, information panels, and space for add-on extensions).
- The Focus button is a real `Button` widget: keyboard-focusable, narratable, and reachable without a mouse.

## Commands

**`/skills tinkers inspect [<player>]`**
- Lists how the held item is classified (role, provider, durability), what skill requirements it carries, and whether the player meets them.
- With no argument, reads the caller's held item; with a player argument (requires OP level 2), reads that player's held item.
- Read-only inspection never modifies the item or its NBT.

**`/skills tinkers workshop focus`**
- Claims the controller the player is facing (within ordinary interaction reach, ~6 blocks).
- If the target is not a controller, attempts to associate it as a casting table instead.
- Identical to the panel's Focus button; both use the same service calls and validation.
- Player-accessible; no permission required.

**`/skills tinkers workshop release`**
- Drops the caller's own focus.
- Player-accessible; no permission required.

**`/skills tinkers compat`**
- Shows the compatibility status for all Tinkers' capabilities (CLASSIFICATION, REPAIR, WEAR_AVOIDANCE, WORKMANSHIP, STACK_REQUIREMENTS, STATION_TRANSACTIONS, HARVEST_AOE, PROJECTILES, WORKSHOP, KEYSTONE).
- Each capability shows whether it is SUPPORTED, VERSION_UNVERIFIED, UPSTREAM_INCOMPATIBLE, HOOK_UNAVAILABLE, DISABLED_BY_CONFIG, or ABSENT.
- OP level 2 additionally sees the reason string (version number, specific hook that did not apply).
- Player-accessible; no permission required.

**Permission model (2.0.7 change):**
- Before 2.0.7, `/skills` was entirely OP-gated at the root literal.
- In 2.0.7, the permission gate moved to the `player` argument (the target you are querying or modifying).
- This allows `/skills tinkers inspect` (a question about the caller's own held item) and `/skills tinkers workshop focus` (a player action) to run without OP, while `/skills <other player> <skill> <level>` (set another player's skill) still requires OP level 2.
- Existing operator-gated commands are still gated; nothing became more permissive.

## Networking

Three new packets in protocol 12 (up from 11):

**`WorkshopFocusSP` (server-bound)**
- Carries a focus request: container ID, action (FOCUS/ASSOCIATE/RELEASE), target position, and revision token.
- The container ID ensures the request describes a menu the player actually has open (no stale screen, no forged menu).
- The action is FOCUS (claim the controller), ASSOCIATE (add a casting block), or RELEASE (drop the focus).
- The target position is only used for FOCUS and ASSOCIATE; for RELEASE it is ignored.
- The revision token is the one the server last sent; a replayed or stale token is refused immediately.
- The position is checked for distance (before asking the chunk) and chunk-loaded state (before asking Tinkers' whether it is a controller), so an arbitrary coordinate cannot force a chunk load.

**`WorkshopStatusCP` (client-bound)**
- Sent at most twice a second (every ten ticks), only when the player has a focus or a native menu open.
- Carries the player's current focus status: whether they hold one, the controller position, the count of associated casting blocks, remaining ticks (clock value, not a stream), the revision token, the computed bonus percentage, the automation-rewards flag, the owner name, the block type in front of them (controller/casting/none), and its position.
- The remaining-ticks count is sent once; the client derives the countdown locally per frame.
- The block-type and position let the client's Focus button identify what is in front of the player without knowing anything about Tinkers' Construct.

**`StationQuoteCP` (client-bound)**
- Sent only when the station's inputs or its native result actually change, at most twice a second.
- Carries a preview of what this one player would receive from the station (after Runic transforms), the recipe ID, the operation kind (assemble/repair/modify/unknown), a revision number (incremented per server session), and the menu ID.
- The preview fingerprints in the quote (cheap hashes of inputs and result) let the server detect if the inputs changed between quote-time and take-time.
- Revisions are monotonically incremented per server session, shared across all stations, so each quote is uniquely identified.

**Validation rules (WorkshopFocusSP handler):**
- Sender must be a real player and must not exceed the rate limit (one per 250ms / 5 ticks, or at most 4 per second).
- The container ID must match the player's open menu; mismatches (stale screen, forged packet) are silently ignored.
- The revision token must match the player's current token; stale tokens are silently ignored.
- Only then is the world asked: distance is checked before chunk-load checks, so an arbitrary position cannot force loading.

## Diagnostics

The integration status shows ten capabilities, including WORKSHOP and KEYSTONE:

```
Tinker's Construct 1.20.1-3.11.2.166 (profile STABLE_311)
  CLASSIFICATION: SUPPORTED — native tool tags and stats
  REPAIR: SUPPORTED — native repair factor applied, then ToolDamageUtil.repair
  WEAR_AVOIDANCE: SUPPORTED — applied once at the directDamage call, after native modifiers
  WORKMANSHIP: SUPPORTED — runicskills:workmanship modifier
  STACK_REQUIREMENTS: DISABLED_BY_CONFIG — enableTConstructLockItems is off, so only explicit item locks apply
  STATION_TRANSACTIONS: SUPPORTED — observing native station takes, transforms, and payouts
  HARVEST_AOE: SUPPORTED — observing native area-of-effect harvests per-block
  PROJECTILES: SUPPORTED — recording launch facts on native ranged projectiles
  WORKSHOP: SUPPORTED — melting/casting bonus, focus claiming and release
  KEYSTONE: SUPPORTED — runicskills:keystone fitted at the station for one permanent upgrade slot
```

## Add-on integrations (S5)

Seven single-rank perks from Tinkers' Construct add-ons, each gated on its own `enable…Integration` flag and its required add-on mod id.

### TCIntegrations (Four perks, four companion mods)

The primary adapter, bundling optional-mod links for Botania, Ars Nouveau, Create, and Malum. Each effect is independent; the same jar supplies one perk on one install and supplies nothing on the next.

| Perk | Skill | Level | Required Add-on | Companion Mod | Config Flag | Capability | Trigger | Effect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `tc_mana_polisher` | Tinkering | 16 | TCIntegrations | Botania | `enableTcIntegrationsIntegration` | `ADDON_BOTANIA_REPAIR_CHARGE` | Tool repairs itself with Botania mana | Mana charge reduced by `tcManaPolisherPercent`% (default 10%), never below one mana |
| `tc_source_tempering` | Magic | 16 | TCIntegrations | Ars Nouveau | `enableTcIntegrationsIntegration` | `ADDON_ARS_ARMOR_REPAIR` | Armour repair spends Ars source | Next Ars Nouveau spell within `tcSourceTemperingWindowSeconds` seconds (default 8) deals `tcSourceTemperingPercent`% more damage (default 5%) |
| `tc_clockwork_alternation` | Tinkering | 16 | TCIntegrations | Create | `enableTcIntegrationsIntegration` | `ADDON_OFFHAND_MELEE` | Main-hand and offhand hits alternate | Next tool use within `tcClockworkAlternationWindowSeconds` seconds (default 5) ignores `tcClockworkAlternationPercent`% durability loss (default 10%) |
| `tc_soulsteel_resolve` | Constitution | 20 | TCIntegrations | Malum | `enableTcIntegrationsIntegration` | `ADDON_SOUL_STAINED` | Primary hit with soul-stained equipment | Knockback resistance +`tcSoulsteelResolveAmount` (default 0.06) for `tcSoulsteelResolveSeconds` seconds (default 4) |

**Mixin gates:** `MixManaModifier` (`require = 0`), `MixArsNouveauBaseModifier` (`require = 0`), `MixToolAttackUtil` (required). Optional mixins (Mana Polisher, Source Tempering) are reported as `UPSTREAM_INCOMPATIBLE` when the seam has moved.

### Tinkers' Levelling Addon (One perk)

| Perk | Skill | Level | Required Add-on | Config Flag | Capability | Trigger | Effect |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `tc_seasoned_hands` | Tinkering | 16 | Tinkers' Levelling Addon | `enableTinkersLevellingIntegration` | `ADDON_TOOL_LEVELLING` | Tool gains experience from player actions | Tool experience award scaled by `tcSeasonedHandsPercent`% (default 10%). Fractional remainder carries per player per tool item; no scaling past the add-on's own level cap |

**Specification (§12.3):** Player progression and tool progression stay separate. No tool experience becomes player experience. No player level grants tool experience. Scaling happens once at the seam, applied before the add-on's own level-up, history and stat rebuild. Carry is player-bound and tool-item-bound; switching tools drops it.

**Mixin gate:** `MixToolLevellingUtil` (required).

### Tinkers' Delight (One perk)

| Perk | Skill | Level | Required Add-on | Companion Mod | Config Flag | Capability | Trigger | Effect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `tc_banquet_of_cinders` | Wisdom | 12 | Tinkers' Delight | Farmer's Delight | `enableTinkersDelightIntegration` | `ADDON_CULINARY_EFFECT` | Player has Farmer's Delight nourishment effect | Accepted primary melee damage +`tcBanquetOfCindersPercent`% (default 3%), summed with other melee bonuses under `tconstructNewDamageBonusCap` |

**Specification (§12.6):** The perk contributes to melee damage only while the add-on's actual food effect is active. It does not apply the effect, duplicate a meal, or amplify every potion. The effect stays the add-on's own.

**No mixin gate:** This perk observes the player's active effects and reads configuration from `ModEffects.NOURISHMENT`, no bytecode injection needed.

### Tinkers' Advanced (One perk)

| Perk | Skill | Level | Required Add-on | Companion Lib | Config Flag | Capability | Trigger | Effect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `tc_charged_craft` | Tinkering | 24 | Tinkers' Advanced | EtSTLib | `enableTinkersAdvancedIntegration` | `ADDON_TOOL_ENERGY` | Identified tool operation extracts FE | FE cost reduced by `tcChargedCraftPercent`% (default 10%), never below one FE. Only player-initiated operations are discounted; machines, cables, and block-entity transfers pay full price |

**Specification (§12.7):** The discount applies at the exact seam (`ToolEnergyUtil.extractEnergy`) every native Tinkers' Advanced tool modifier calls. Simulation queries are discounted identically so a tool that asks "can I afford this?" at full price is not falsely told it cannot.

**Mixin gate:** `MixToolEnergyUtil` (required). Note: Tinkers' Advanced main project 3.0.0-beta.13 cannot boot a dedicated server (upstream EventBusSubscriber defect); test environment uses split Core beta.5.

### Reserved: Medallion Concord

| ID | Skill | Level | Required Add-on | Upstream Status |
| --- | --- | --- | --- | --- |
| `tc_medallion_concord` | Wisdom | 16 | Tinkers' Ingenuity (unresolved) | `UPSTREAM_UNAVAILABLE` |

**Status (2.0.7):** No public 1.20.1 Forge artifact for Tinkers' Ingenuity could be resolved from any maven. The perk id is reserved (registered nowhere) rather than shipped as a selectable no-op per spec §10.3. The capability is reported by `/skills tinkers compat` as `UPSTREAM_UNAVAILABLE` with an explanation, so a player can see the difference between "you did not install it" and "there is nothing to install".

**Resolution:** Re-check artifact availability before permanently descoping in a future release.

## Add-on integrations (2.1.0)

Seven further single-rank perks, from two more Tinkers' add-ons. Neither is a compile dependency and
neither contributes a mixin: both are addressed by registry id (modifier id, mob effect id, material
namespace) through `TraitFeatureRegistry` and `ForgeRegistries`, and both triggers are reached from
FORGE events `TConstructPerkHandler` already subscribes (the `LivingDeathEvent` bracket and the
station-delivery point). `TcAddonHooks` gained two new channels for this work,
`addWearAvoidanceContributor` and `addExperienceContributor`, each summed and clamped once by
`tconstructNewWearAvoidanceCap` and `tconstructNewExperienceBonusCap` respectively — a new channel
rather than folding a wear or experience effect into the existing melee sum because the composition
point for it did not yet exist. `addRepairBonusContributor` is not a new channel: it joins the
existing paid-repair share under the existing `tconstructRepairBonusCap`.

### Tinkers' Thinking (Three perks, no companion mod)

Detected by mod id only. `TinkerDataCapability.TinkerDataKey` declares neither `equals` nor
`hashCode`, and `TinkerDataKey.of` allocates a fresh instance per call, so no consumer — with or
without a compile dependency — can read a level the add-on stored under its own key. Both triggers
are instead recognised from the mob effects the add-on's own handlers apply
(`tinkers_thinking:last_effort`, `tinkers_thinking:sculk_power`), which are ordinary registry objects
any mod can look up by id. This is a known detection constraint, not a workaround: it means the perks
answer "did the add-on's save/conversion just happen" rather than "what level is the add-on's own
modifier", which is the only question the jar's own types make answerable at all.

| Perk | Skill | Level | Config Flag | Capability | Trigger | Effect |
| --- | --- | --- | --- | --- | --- | --- |
| `tc_thinking_last_thought` | Wisdom | 16 | `enableTinkersThinkingIntegration` | `ADDON_THINKING_DEATH_TRIGGER` | A death the add-on's `OnDeath.onLivingDying` refused | `tcThinkingLastThoughtPercent`% wear avoidance (default 12) for `tcThinkingLastThoughtSeconds`s (default 15) |
| `tc_thinking_studied_recall` | Wisdom | 20 | `enableTinkersThinkingIntegration` | `ADDON_THINKING_XP_TRIGGER` | The add-on's `OnExpPickUp.onPlayerPickupXp` cancels a pickup and converts it to `sculk_power` | Returns `tcThinkingStudiedRecallPercent`% (default 15) of the consumed orb as experience |
| `tc_thinking_embellished_focus` | Tinkering | 24 | `enableTinkersThinkingIntegration` | `ADDON_THINKING_EMBELLISHMENT` | Main-hand weapon carries a Tinkers' Thinking melee modifier | `tcThinkingEmbellishedFocusPercent`% (default 4) added to the melee-damage sum |

**One honest deviation.** Last Thought grants wear avoidance for a window *after* the add-on's death
save, rather than discounting the durability the save costs, because `OnDeath.onLivingDying` spends
no durability at all — there is no cost to discount, only a reprieve to reward.

**No mixin gate:** both triggers are observed from Runic's own event handlers; nothing is injected
into the add-on's code.

### Tinkers' Jewelry (Four perks, one reserved)

No compile dependency and no stub source set: everything is addressed the way Tinkers' addresses it
— a material id, a modifier id, an item in `tconstruct:modifiable/durability`. Two of the four read a
worn piece from vanilla equipment slots rather than Curios, because Curios types are not on this
build's classpath and a reflective walk of another mod's inventory was judged too much machinery for
a small perk: **a piece worn in a Curios-only slot does not count for Gem Attunement.**

| Perk | Skill | Level | Config Flag | Capability | Trigger | Effect |
| --- | --- | --- | --- | --- | --- | --- |
| `tc_jeweler_setting` | Tinkering | 12 | `enableTinkersJewelryIntegration` | `ADDON_JEWELRY_MATERIAL` | A Tinker Station take delivers a jewelry-material piece | `tcJewelerSettingPercent`% wear avoidance (default 10) on that piece for `tcJewelerSettingSeconds`s (default 30) |
| `tc_gem_attunement` | Wisdom | 16 | `enableTinkersJewelryIntegration` | `ADDON_JEWELRY_GEM_ATTRIBUTES` | A jewelry piece is worn in a vanilla equipment slot | `tcGemAttunementPercent`% (default 3) added to the melee-damage sum |
| `tc_undying_lustre` | Constitution | 20 | `enableTinkersJewelryIntegration` | `ADDON_JEWELRY_UNDYING` | Inside a death-resolution bracket, on the ring the add-on's own undying save is charging | `tcUndyingLustrePercent`% wear avoidance (default 25) on that save's cost |
| `tc_polished_facet` | Tinkering | 20 | `enableTinkersJewelryIntegration` | `ADDON_JEWELRY_POLISH` | A paid station repair of a jewelry-material piece | `tcPolishedFacetPercent`% (default 8) added to the paid-repair share, bounded by `tconstructRepairBonusCap` |

**One honest deviation.** Polished Facet keys on the repaired piece being a jewelry-material item,
not on the add-on's `polish` modifier: `data/tinkersjewelry/tinkering/modifiers/polish.json` is a
single `tconstruct:modifier_slot` grant of one upgrade slot and has no repair semantics of any kind.
Keying it on the modifier would have named the perk after a mechanic that does not exist.

**Reserved: `tc_subspace_reserve`.** Same treatment as Medallion Concord, for a different reason:
`tinkersjewelry:subspace` sizes a private inventory (`SubSpaceCapability`, `SubSpaceMenu`) behind an
attribute, and carries no durability, damage, repair or progression quantity for a Runic channel to
join. The id is unregistered and the capability (`ADDON_JEWELRY_SUBSPACE`) reports
`UPSTREAM_UNAVAILABLE` with the reason.

**No mixin gate:** the undying save's durability arrives at the existing `MixToolDamageUtil` (H1)
seam with no new injection; the station-delivery trigger reuses the existing station bridge.

**When is each capability ABSENT or HOOK_UNAVAILABLE?**
- `ABSENT` — the add-on's own mod id is not loaded, or (for `ADDON_JEWELRY_GEM_ATTRIBUTES` /
  `ADDON_JEWELRY_UNDYING`) Curios is not loaded (a configuration a Tinkers' Jewelry install cannot
  actually reach, since Curios is its mandatory dependency, but the capability still names it rather
  than assuming it).
- `DISABLED_BY_CONFIG` — the add-on's own `enable…Integration` flag is off.
- `HOOK_UNAVAILABLE` — not applicable to any of these seven: none of them rests on a mixin, so there
  is no hook that can fail to match.

### Tinkers' Katanas: data-only, no adapter

`tinkers_katanas` ships zero Java classes. Its `katana` and `fuma_shuriken` are JsonThings tool
definitions registered into `tconstruct:modifiable/melee/primary`, `/one_handed`, `/durability`,
`/bonus_slots` and (the shuriken) `/ranged`, so classification, wear, repair, ranged and keystone
already reach them with no adapter, no capability flag and no perk. `ADDON_KATANAS_CONTENT` reports
`SUPPORTED` when the mod is loaded (`ABSENT` otherwise) with a reason naming the tags rather than an
adapter that does not exist. Two optional entries were added to `runicskills:lucky_break_eligible`
(`tinkers_katanas:katana`, `tinkers_katanas:fuma_shuriken`) so `DurabilityPerkRules` still recognises
them as tools on an install where the Tinkers'-side tags are overridden.

## Known limitations

- Tinkers' 3.12 in 2.0.7 runs in conservative mode (no wear avoidance, no workmanship). A future release will compile against 3.12.
- Repair kits (the portable repair surface) are integrated via Field Service perk (S4a), which applies bonus durability to repair-kit crafts. Casting associations with repair stations are not yet supported.
- Material-cost discount mode (reserved config flag) has no implementation.
- **Add-on perks (S5):** Seven ship in 2.0.7; four unavailable upstreams preclude live behavior in the test environment (Botania, Ars Nouveau, Create, Malum for TCIntegrations perks) but dormancy is tested. `tc_medallion_concord` has no confirmed 1.20.1 upstream artifact and is reserved but unregistered.
- **Add-on perks (2.1.0):** Seven more ship, from Tinkers' Thinking and Tinkers' Jewelry. Tinkers'
  Thinking has live coverage via `-PtinkersAddons=thinking` against the exact build the reference
  pack runs. Tinkers' Jewelry has no live gametest profile: Modrinth's newest 1.20.1 Forge build is
  1.1.0 while the reference pack runs 1.2.0, so its four perks rest on `TcAddonAbsenceGameTest`'s
  dormancy invariants in the shipped profiles. `tc_subspace_reserve` has no confirmed Runic channel
  (the modifier is inventory storage) and is reserved but unregistered, on the same rule as
  `tc_medallion_concord`. Gem Attunement and Undying Lustre read vanilla equipment slots rather than
  Curios, so a piece worn in a Curios-only slot is not recognised.
- Pack rules for material-tier requirements are later-stage work (stage S6).
