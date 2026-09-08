# Tinkers' Construct 1.20.1-3.11.2.166 hook manifest

Native seam inventory for Tinkers' Construct 3.11.2.166 and Mantle 1.11.97, updated for Runic Skills 2.1.0. Static descriptors, runtime handler-call verification and GameTests provide separate evidence; an optional injector being offered or merged alone does not prove that it matched.

**Status:** Stage S4 implementation (Keystone Tinker perk). Hooks H1, H2, H4, H6, H8, H9, H10 have implementations (H6 is via registered modifiers, not mixins).

## Hook reference

One table row per hook seam, H1–H12 from the implementation plan.

| # | Seam | Class | Method + Descriptor | Injection point | Actor source | Phase | 3.12 status |
|---|---|---|---|---|---|---|---|
| H1 | Native wear | `slimeknights.tconstruct.library.tools.helper.ToolDamageUtil` | `damage(IToolStackView;ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z` | INVOKE `directDamage` inside the 4-arg method | LivingEntity param, cast to Player when instanceof | post-modifier, pre-ordinary-loss | 3.12 adds `beforeDamageTool` overload with ModifierId; 3.11 4-arg still compiles |
| H2 | Station take | `slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity` | `onCraft(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V` | METHOD HEAD | Player param (arg0) | pre-input-shrink | implemented, MixTinkerStationBlockEntity |
| H3 | Station quick-move | `slimeknights.mantle.inventory.MultiModuleContainerMenu` | `quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;` | (no hook needed) | Player param | pre-take, post-move | resolved: native flow refuses before commit |
| H4 | Normal take | `slimeknights.tconstruct.tables.menu.slot.LazyResultSlot` | `getItem(I)` and `removeItem(I)` → `LazyResultContainer` | RETURN of `getItem`, `removeItem`, `removeItemNoUpdate` | Player via `ContainerInteraction` | post-copy | implemented, MixLazyResultContainer |
| H5 | Repair recipe | `slimeknights.tconstruct.tables.recipe.TinkerStationRepairRecipe` | `getValidatedResult(...)` calls `ToolDamageUtil.repair` | INVOKE target descriptor `(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;I)V` | implicit from recipe context; Player available via `TinkerStationBlockEntity.onCraft` (H2) | post-repair-factor-application | repair factor applied in recipe, not in sink |
| H6 | Workmanship rebuild | `slimeknights.tconstruct.library.tools.nbt.ToolStack` | `rebuildStats()V` and `ModifierHooks.TOOL_STATS` | pre- or post-rebuild, depending on when to stamp | implicit from modifier context | post-add, pre-rebuild or post-rebuild | implemented; KeystoneModifier VOLATILE_DATA addSlots, WorkmanshipModifier TOOL_STATS; deviation: 3.11 `LazyResultContainer` computes cached result with null player on every path, so recipe-level actor validation is preview-only and authoritative check is at `Slot.mayPickup` |
| H7 | Tags | `slimeknights.tconstruct.common.TinkerTags.Items` | (constants only; see tag table below) | at rule application time | none (classification only) | — | not compared against 3.12 |
| H8 | Harvest AoE | `slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic` | `breakBlock(...): boolean` and `breakExtraBlock(...)` | WRAP around `breakBlock` call in `breakExtraBlock`; RETURN after `breakBlock` | ServerPlayer via ToolHarvestContext | post-removal, post-protection | implemented, MixToolHarvestLogic |
| H9 | Ranged launch / return | `ModifiableBowItem.releaseUsing`, `ModifiableCrossbowItem.fireCrossbow`, `ThrownTool.tryPickup`, plus `EntityJoinLevelEvent` | launch: WRAP method; return: HEAD of `tryPickup` | LivingEntity param at launch; Player at pickup | launch: before projectile spawn; return: at genuine return detection | implemented, MixModifiableBowItem, MixModifiableCrossbowItem, MixThrownTool, TConstructCombatBridge.onProjectileSpawned |
| H10 | Melting / casting | `slimeknights.tconstruct.smeltery.block.entity.module.MeltingModule.heatItem`, `slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity.serverTick` (private) | `heatItem(II)V` is AT HEAD on speed argument; `serverTick(Level, BlockPos)V` is AT HEAD, method is private and injected by name | MODIFY speed arg for melt, INJECT at head for casting | none (state tracked in entity fields) | mid-tick, fractional progress | implemented, MixMeltingModule, MixCastingBlockEntity; `serverTick` private method injectable by name |
| H11 | Repair kit | `slimeknights.tconstruct.tools.item.RepairKitItem.getRepairAmount`, `CraftingTableRepairKitRecipe.assemble` | (multiple; see repair section) | at or around formula / result assembly | implicit from recipe context or item use chain | post-get-amount, pre-apply | CraftingTableRepairKitRecipe IS a CustomRecipe; fires vanilla `ItemCraftedEvent` |
| H12 | Version detection | `ModList.get().getModContainerById("tconstruct").get().getModInfo().getVersion()` | (Forge API) | at mod/integration startup | none (static method) | startup | TConstruct does not export a version field; use standard Forge idiom |

## Tinker Tags (H7)

The complete field list from `TinkerTags.Items` (151 entries). All values are `forge:` or `tconstruct:` namespaced `TagKey<Item>` constants.

### Tag namespace mapping

- `common(String)` → `slimeknights.mantle.Mantle.commonResource(str)` → `new ResourceLocation("forge", str)`
- `local(String)` → `slimeknights.tconstruct.TConstruct.getResource(str)` → `tconstruct:` namespace

### Item-classification tags

Used to identify what category a tool or item belongs to:

| Tag field | ResourceLocation | Purpose |
|---|---|---|
| `MODIFIABLE` | `tconstruct:modifiable` | Any item that can be modified (base predicate) |
| `DURABILITY` | `tconstruct:modifiable/durability` | Items with durability that mods can repair/reinforce |
| `HARVEST` | `tconstruct:modifiable/harvest` | Tools that mine blocks (picks, shovels, axes, hoes) |
| `HARVEST_PRIMARY` | `tconstruct:modifiable/harvest/primary` | Primary harvest tool (e.g., pick for stone) |
| `STONE_HARVEST` | `tconstruct:modifiable/harvest/stone` | Stone-harvest tools |
| `MELEE` | `tconstruct:modifiable/melee` | Melee weapons (swords, clubs, etc.) |
| `MELEE_WEAPON` | `tconstruct:modifiable/melee/weapon` | Distinct melee weapon (not unarmed) |
| `MELEE_PRIMARY` | `tconstruct:modifiable/melee/primary` | Primary melee (sword-class) |
| `SWORD` | `tconstruct:modifiable/melee/sword` | Sword-shaped weapons |
| `UNARMED` | `tconstruct:modifiable/melee/unarmed` | Fist weapons, gauntlets |
| `PARRY` | `tconstruct:modifiable/melee/parry` | Parry-capable weapons |
| `ARMOR` | `tconstruct:modifiable/armor` | Any wearable armor |
| `BOOTS` | `tconstruct:modifiable/armor/boots` | Foot armor |
| `LEGGINGS` | `tconstruct:modifiable/armor/leggings` | Leg armor |
| `CHESTPLATES` | `tconstruct:modifiable/armor/chestplate` | Chest armor |
| `HELMETS` | `tconstruct:modifiable/armor/helmets` | Head armor |
| `WORN_ARMOR` | `tconstruct:modifiable/armor/worn` | Armor currently worn by an entity |
| `HELD_ARMOR` | `tconstruct:modifiable/armor/held` | Armor held in hand |
| `SHIELDS` | `tconstruct:modifiable/shields` | Shield-type items |
| `RANGED` | `tconstruct:modifiable/ranged` | Projectile launchers |
| `LAUNCHERS` | `tconstruct:modifiable/ranged/launcher` | Generic launcher tools |
| `BOWS` | `tconstruct:modifiable/ranged/bows` | Bow-class weapons |
| `LONGBOWS` | `tconstruct:modifiable/ranged/longbows` | Long-range bows |
| `BALLISTAS` | `tconstruct:modifiable/ranged/ballistas` | Ballista-class heavy ranged |
| `CROSSBOWS` | `tconstruct:modifiable/ranged/crossbows` | Crossbow-class weapons |
| `STAFFS` | `tconstruct:modifiable/staffs` | Staff-class weapons |
| `FISHING_RODS` | `tconstruct:modifiable/fishing_rods` | Fishing rod tools |
| `SMALL_RANGED` | `tconstruct:modifiable/ranged/small` | Small projectile launchers |
| `BROAD_RANGED` | `tconstruct:modifiable/ranged/broad` | Broad-area projectile launchers |
| `AMMO` | `tconstruct:modifiable/ammo` | Projectile ammo (arrows, bolts, etc.) |
| `THROWN_AMMO` | `tconstruct:modifiable/ammo/thrown` | Thrown-weapon ammo |
| `BALLISTA_AMMO` | `tconstruct:modifiable/ballista_ammo` | Ballista-specific ammo |

### Tool-size and role tags

| Tag field | ResourceLocation | Purpose |
|---|---|---|
| `MULTIPART_TOOL` | `tconstruct:modifiable/multipart` | Tools with multiple parts |
| `SINGLEPART_TOOL` | `tconstruct:modifiable/multipart/single` | Single-part tools |
| `AOE` | `tconstruct:modifiable/aoe` | Area-of-effect tools |
| `SMALL_TOOLS` | `tconstruct:modifiable/small` | Small/light tools |
| `BROAD_TOOLS` | `tconstruct:modifiable/broad` | Broad-area tools |
| `SPECIAL_TOOLS` | `tconstruct:modifiable/special` | Special-purpose tools |

### Restrictions and opt-outs

| Tag field | ResourceLocation | Purpose |
|---|---|---|
| `UNRECYCLABLE` | `tconstruct:modifiable/unrecyclable` | Item cannot be salvaged/recycled |
| `UNSALVAGABLE` | `tconstruct:modifiable/unsalvageable` | Item cannot be salvaged |
| `UNSWAPPABLE` | `tconstruct:modifiable/unswappable` | Item parts cannot be swapped |

All 151 tag entries are namespace-prefixed constants; none are raw vanilla tag names.

## Ranged hooks (H9) detail

Ranged launching, impact, and returning are multi-stage with hooks present at each stage:

- **Launch (via releaseUsing or finishUsingItem):** `ModifiableBowItem.releaseUsing(ItemStack, Level, LivingEntity, int)`, `ModifiableCrossbowItem.releaseUsing(...)` and static `fireCrossbow(IToolStackView, LivingEntity, boolean, InteractionHand, CompoundTag)`, `ModifiableLauncherItem.finishUsingItem(ItemStack, Level, LivingEntity)` all carry LivingEntity directly. Modifier hook: `ProjectileLaunchModifierHook.onProjectileLaunch(IToolStackView, ModifierEntry, LivingEntity, Projectile, AbstractArrow, ModDataNBT, boolean)`.

- **Impact:** Modifier hook `ProjectileHitModifierHook.onProjectileHitEntity/onProjectileHitBlock(...)` all carry LivingEntity. Launcher-level: `LauncherHitModifierHook.onLauncherHitEntity/onLauncherHitBlock(...)`.

- **Returning:** IS a MobEffect (`ReturningEffect extends TinkerEffect`), not an item-to-inventory method. The effect ticks, firing a Forge `ReturningTeleportEvent` with LivingEntity at teleport time. `ModifiableArrow.getPickupItem()` is standard vanilla arrow walk-over pickup, unrelated to Returning.

- **Tool data on projectile:** `ModifiableArrow` implements `ToolProjectile`, synced via `EntityDataAccessor STACK`. Generic fallback: `EntityModifierCapability` for non-Arrow projectiles.

## Repair kit (H11) detail

Repair kits exist in three forms in 3.11.2.166:

- **Tinker Station portable repair kit:** `RepairKitItem.getRepairAmount()` returns a flat amount per item type. Station route uses `TinkerStationRepairRecipe.updateInputs(...)` to shrink inputs.

- **Crafting-table 3x3 repair kit:** `CraftingTableRepairKitRecipe extends CustomRecipe`. Assembles a repaired tool in the output slot. **Important:** This recipe fires vanilla `ItemCraftedEvent` through the normal `CraftingMenu` path, indistinguishable from a shaped recipe at event time. Classification must inspect `instanceof CraftingTableRepairKitRecipe` or the recipe id to distinguish repair from manufacture.

- **Modifier-family repair recipes:** `ModifierMaterialRepairKitRecipe`, `ModifierRepairCraftingRecipe`, `ModifierRepairTinkerStationRecipe`, and related interfaces (`ISpecializedRepairRecipe`, `IModifierRepairRecipe`, `IModifierMaterialRepairRecipe`) also exist, specialized per-modifier repair logic.

## Notable divergences from 3.12

- **H1 wear:** 3.12 adds `beforeDamageTool(IToolStackView, ModifierId, int)` overload; 3.11 has only `onDamageTool(IToolStackView, ModifierEntry, int)`. The 3.11 4-arg `damage` method still compiles in 3.12, forwarding with `ModifierId.EMPTY`.

- **H6 workmanship:** No `ModifiableStaffItem` class exists in 3.11. Staves are data-driven as `tool_definitions/*_staff.json`, not a separate Java item class.

- **H9 ranged return:** Not a modifier method or item-interaction. Returning is a `MobEffect` + `ReturningTeleportEvent`, not an inventory-pickup method. This is the correct understanding for both versions.

- **H11 repair kit:** `CraftingTableRepairKitRecipe` is a vanilla `CustomRecipe` in 3.11. Indistinguishable from a shaped recipe at the `ItemCraftedEvent` level without explicit classification.

## Deviations found during implementation

- **(H3) Mantle's quickMoveStack:** No escrow hook needed. Mantle's `quickMoveStack` calls `moveItemStackTo` inside helpers and returns early on a full inventory before any `onTake`/`onCraft`, so the native path already refuses before it commits rather than committing into nowhere (TConstructStationBridge.java:54-61; MixSlot.java).

- **(H4) LazyResultContainer transform:** The delivered-copy transform lives in `LazyResultContainer`'s `getItem`, `removeItem`, and `removeItemNoUpdate` methods, gated on an open `ContainerInteraction` (TConstructStationBridge.java:63-68; MixLazyResultContainer.java:60-87). This is needed because `onTake` is too late on shift-click — the items have already been split and moved by then.

- **(H9) ReturningTeleportEvent not used:** The plan's manifest named `ReturningTeleportEvent` as the return seam, but that event is fired by `ReturningEffect` (a status effect) with a `LivingEntity` (the player), not with a projectile. Tinkers' "Returning" modifier for thrown tools is a volatile flag that switches on vanilla loyalty behavior. Return is actually detected at `ThrownTool.tryPickup` with `noPhysics` true and owner match (MixThrownTool.java:36-42; TConstructCombatBridge.java:131-135).

- **(H8) No releaseUsing on ModifiableCrossbowItem:** Crossbows do not release on `releaseUsing` the way bows do — that method finishes charging. The launch seam is `fireCrossbow`, the static method that actually spawns the bolt (MixModifiableCrossbowItem.java:32-45; plan §9 "H9 ranged launch / return").

- **(H8) ToolHarvestLogic breakBlock fires per-child events:** The primary block reuses vanilla's event (which this mod does not reach because the tool cancels vanilla's break). Each AoE child posts `BlockBreakCommittedEvent` from within the breakBlock return (MixToolHarvestLogic.java:82-99; TConstructCombatBridge.java:160-166). Protection check is already done natively by `ForgeHooks.onBlockBreakEvent` per child before removal, so refused children never reach the seam.

- **(H8) RENAME never reaches onCraft in 3.11:** The plan spec §3's "repair/modify/part-swap never receive bonus copies" implied rename as a fourth category. But in 3.11.2.166, `TinkerStationBlockEntity.onCraft` returns immediately when there is no recipe match, so pure-rename cases do not fire a crafting event and have nothing to classify (TConstructStationBridge.java:113-116).

## How this is used

The `docs/tconstruct/compat-manifest.json` lists each hook with:
- Class and method descriptor
- Expected match count (1 for all H1–H12)
- Chosen injection point (INVOKE target or AT keyword)
- Actor source (who supplies the LivingEntity)
- Phase (pre/post)
- Status (`resolved`, `unresolved`, or S2+ stage where it ships)

When an S2+ stage implements a mixin for a hook, the descriptor is verified against the running jar and the mixin plugin gates it on profile + config flags so the mixin is not applied in M0.

## Mapping and remapping

Every mixin under `mixin/tconstruct/**` is `@Mixin(targets = "...", remap = false)` — a string
target, because a target-mod class literal must never enter this mod's constant pool on an install
without that mod. Eleven target `slimeknights...` classes; four target other optional add-ons
instead (`MixArsNouveauBaseModifier` and `MixManaModifier` target `tcintegrations.*`,
`MixToolEnergyUtil` targets `com.c2h6s.etstlib.*`, `MixToolLevellingUtil` targets
`pyre.tinkerslevellingaddon.*`). That class-level `remap = false` is correct for all fifteen: class
*names* are never obfuscated. Individual **member references are a separate question**, decided per hook by who
declared the member:

- **Vanilla-declared members need `remap = true`, with the selector qualified by its full
  descriptor.** `MixModifiableBowItem` hooks `ModifiableBowItem.releaseUsing`, an override of
  vanilla `Item#releaseUsing`; `MixThrownTool` hooks `ThrownTool.tryPickup`, an override of vanilla
  `AbstractArrow#tryPickup` (redeclared by `ThrownTrident`, `ThrownTool`'s direct superclass);
  `MixLazyResultContainer` hooks `LazyResultContainer.getItem`,
  `.removeItem`, and `.removeItemNoUpdate`, all vanilla `Container` methods. Because Minecraft
  declared these first, they ship SRG-obfuscated inside the Tinkers' jar exactly as they do inside
  Forge's own — `m_5551_`, `m_142470_`, `m_8020_`, `m_7407_`, `m_8016_`. A literal name (`remap =
  false`) matches nothing at runtime.
- **Tinkers'-declared members stay literal, with `remap = false` stated explicitly on that
  reference.** The nested `@At` in `MixModifiableBowItem` targeting
  `ModifierUtil.getInaccuracy(IToolStackView, LivingEntity)` is such a member: Tinkers' declared it,
  so it is never SRG-renamed, and remapping it would make Mixin look for a name that does not exist.
- **`MixReforgingResultSlot`** (in `mixin/`, not `mixin/tconstruct/`) is the same rule applied to a
  different mod: it hooks `ReforgingMenu.ReforgingResultSlot#onTake`, an override of vanilla
  `Slot#onTake`, so it needs `remap = true` too.

**GameTests cannot catch a wrong verdict here.** `src/gametest` and the dev client both run
Mojang-mapped, so a literal (`remap = false`) reference to a vanilla-declared member resolves
correctly in both — the mismatch only exists between Mojang names (dev, tests) and SRG names
(the shipped, obfuscated jar). A mixin that gets this wrong compiles clean, passes every GameTest,
and fails to apply the moment the mixin config loads in production, which is fatal for the whole
config, not just the one hook.

**What actually covers it:** `checkMixinRemapping` (a Gradle task, wired into `check`) resolves
every hooked member through the real class hierarchy of the compile-classpath jars with ASM,
classifies it by its declaring package, and fails the build if a `remap = false` mixin references a
`net.minecraft.**`-declared member, or a `remap = true` mixin references a member that is not
vanilla-declared. `tools/verify_against_pack.py` goes one step further and checks the *shipped* jar's
refmap against the real, obfuscated jars in a live pack's `mods/` directory — see
[`PACK_VERIFICATION.md`](PACK_VERIFICATION.md).

## 2.1.0 stabilization changes

- Container clicks, vanilla melee actions and native station crafts use MixinExtras `WrapMethod` scopes with `try/finally`, including same-object recursion and foreign exceptions. Attack readiness is captured before vanilla can reset the ticker.
- The native lazy-result hooks calculate previews without arming benefits or consuming cooldowns. Accepted `onCraft` binds state to the delivered stack; per-click inventory identities locate Mantle shift-click copies. Cached native restoration supplies thresholds and script reports.
- The existing native wear seam permits the specifically identified Jewelry Undying Lustre death cost through its bounded save-cost reducer. Ordinary Runic wear perks do not apply to that cost.
- `TConstructMiningCP` is a clientbound, change-only snapshot of temporary mining bonuses and the selected hotbar slot. Channel protocol is 13; client state clears on disconnect. Native mining prediction and server execution share the same cap.
- Startup probes load approved core and add-on targets without initialization. After loading returns, `MixinHookVerification` checks each injector for a call in the transformed target and reports missing handler names; merging a method with no call cannot mark the capability supported. Verification waits for MixinExtras' late-applying extensions, which run after the plugin's `postApply` callback.
- Private quotes include native paid-repair bonuses, and equality includes the transformed output. Expiring a temporary bonus refreshes the quote without mutating station inputs or spending preparation.
