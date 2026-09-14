# Crafting, durability and Tinkers' Construct audit — 2026-09-08

This report follows the checked-out implementation, `CLAUDE.md` and `MODMAP.md`.
Older design documents explain intent; they do not establish that an effect works.
Paths below are relative to `src/main/java/com/otectus/runicskills/` unless stated otherwise.

## Priorities and implemented fixes

| Priority | Finding and evidence | Resolution |
|---|---|---|
| P1 | `MixResultSlot` refunded the inputs to **every** normal result-slot take. Iron ingots → block → ingots and repair-by-crafting could return their consumed inputs. | Efficient Crafting now checks the native recipe before consumption and refunds only manufacture. The existing remainder diff still excludes buckets and used tools. Fake players and reward re-entry are refused. |
| P1 | `CraftOperationContext.classifyGrid` claimed to identify compression, but only compared identical item ids. An iron ingot is not an iron block. Consequently Convergence could also pay on reversible compression. | New `CraftingConversionIndex` finds reverse item transformations across static shaped, shapeless and stonecutting recipes once after each reload. Shared classification now labels those recipes `CONVERSION`; ordinary log → planks manufacture remains eligible for material saving. |
| P1 | `MixStonecutterMenu.setupResultSlot` rolled rewards on preview generation. Selecting recipes repeatedly let players choose a successful bonus before paying. Reversible stonecutting recipes also allowed material multiplication. | The existing `AbstractContainerMenu.clicked` transaction now snapshots a native stonecutting result and awards inventory bonuses only for input units actually consumed. Refused/full-inventory clicks and recipe selection pay nothing. Static reversible recipes, equipment, tagged/capability-bearing outputs and `craft_reward_denied` outputs are excluded. The obsolete preview mixin was removed. |
| P1 | `WorkshopFocusService.maybeRevalidate` initialized its timestamp to `Long.MIN_VALUE`; `tick - lastRevalidatedTick` overflowed negative and the throttle returned forever. Focus records never expired through the sweep. | Reuse the already tested `GameTimeWindow.ready` arithmetic. Heartbeat independently refuses an expired record, so it cannot refresh an expired claim between sweeps. |
| P2 | Destroyed/unloaded casting tables remained associated until the entire focus ended, occupying the association limit and potentially applying old ownership to a replacement. | Heartbeat releases invalid associations, removes their reverse-index entries and issues a fresh revision token. A replacement requires an explicit association. |
| P2 | `EnchantmentHelper.setEnchantments(empty, enchantedBook)` only removes `Enchantments`, leaving `StoredEnchantments` untouched. Curse Breaker therefore did not remove curses from enchanted books. This was verified against the cached Forge 47.4.23 mapped bytecode. | Clear stored enchantments before rebuilding the book's enchantments; convert a fully disenchanted book to an ordinary book while preserving its name/metadata. Reset the prior-work penalty after curse removal. Inputs remain unchanged until a real take. |
| P2 | TConstruct rules marked a duplicate id rejected at any equal priority. A later unique higher-priority override could remain poisoned by lower-priority duplicates; reversing file order changed the result. | A new higher-priority winner clears lower-priority ambiguity. Equal-priority winners remain rejected. |
| P2 | The pack-rule lookup compared only its first two matches, allowing two agreeing rules to hide a third equal-priority disagreement. | Compare every matching rule at the winning priority for both equipment requirements and craft reward policy. Lower priorities remain irrelevant. |
| P2 | More than 2,048 TConstruct rules silently installed a truncated rule index, dropping whatever equipment locks or reward denials happened to be read last. | Reject the oversized reload and retain the previous good index. On first load return no partial index. The diagnostic identifies the file and limit. |
| P2 | Many Hands declared a `max_contributors` datapack value but used only its fixed four-player constant. | Apply the value to the owner-plus-allies capacity, bounded to 2–4. A reload lowering the limit trims older pending contributions before the next operation. Default behavior remains four participants. |

## Shared crafting and durability trace

Definitions originate in `registry/RegistryPerks.java`; values originate in the enabled
perk/configuration and server snapshots. Perk gating still goes through `Perk.isEnabled`.

| Content | Definition-to-gameplay path | Conservation/behavior checked |
|---|---|---|
| Efficient Crafting | Result slot → `MixResultSlot` → `CraftOperationContext` → `CraftingRefund.plan` | One decision per consumed craft; manufacture only; refunds actual one-unit grid changes and does not refund container remainders. |
| Assembly Line, Mass Production, Alloy Master, Master Woodworker, Medieval Architecture | Crafted event/station bridge → `CraftRewardDispatcher.distribute` → `CraftRewardPolicy` | One shared extra-output budget, server authority, non-reentrant payment; repair/conversion/equipment/capability exclusions. Alloy/wood specialties currently select by registered item path. |
| Crafting Luck stat | `RegistryAttributes.CRAFTING_LUCK` → same dispatcher | Shares the crafting-perk budget rather than granting an independent unlimited copy. |
| Convergence | `CraftingEventHandler.onPlayerCraft` → manufacture classification → `ConvergencePerk.drop` | The new reverse-recipe classification also closes compression payouts here. |
| Tinker's Touch, Master Tinkerer | `MixCraftingMenu` / delivered station copy → `CraftResultTransformer` → `ItemBonusTags` | Restore precedes durability stamping; native results are transformed before delivered copies are split. Master Tinkerer excludes repairs. |
| Stone Cutter Efficiency | Native result click → `StonecuttingRewards.begin/finish` | Preview and take are separate. Each consumed input pays at most its configured bonus; normal take and shift take share the same path. |
| Curse Breaker | Grindstone `removeNonCurses` result → `MixGrindstoneMenu` | Removes stored book curses as well as equipment enchantments; ordinary players retain curses. |
| Enchantment Transfer, Enchantment Amplifier, Enchantment Stacking, Runic Engineering | `MixAnvilMenu.createResult` → `AnvilPerkHandler` | Acts on the real output before shift delivery; stable rolls avoid rename rerolls; repair-only benefits inspect actual restored durability. |
| Tool Smith, Weapon Smith | Same anvil path → `ItemBonusTags` → break-speed and smithing attribute handlers | Bonuses stay with the worked item; repeated stamp uses the existing maximum rather than adding indefinitely. |
| Repair Efficiency stat | `CraftingEventHandler.onAnvilRepair` | Reduces anvil break chance with a zero floor. Native repair bonuses use their separate repair channel. |
| Auto Repair | Passive equipment pass → `PassiveRepairAccumulator` → `RepairBudget` → `RepairService` → selected equipment adapter | Fractional credit and rotating slots; only the documented passive perk contributes. Native Tinkers repair evaluates its repair factor. |
| Lucky Break, Precision Tools, Unbreakable, Unbreaking Mastery, Gadgeteer, Lock Expert | Vanilla/native wear seam → `WearAvoidance` | Single combined avoidance cap; vanilla/native eligibility is resolved before spending. Precision Tools converts lifetime bonus to avoidance probability. Large damage amounts use bounded sampling. |
| Mending Boost | `MixExperienceOrb` → actual Mending conversion | Extra repair belongs to the orb-to-durability path, not passive regeneration. |
| Resource Efficiency, Salvage Expert | Grindstone place event → `WorkshopPerkHandler` → `RecyclingIndex` | Input is actually consumed on native take; no block-break-plus-material payout. |
| Disassembler, Salvage Master, Salvage Luck | Destroy-item event / salvage calculation → datapack `RecyclingRule` | Explicit allowed inputs and recovery cap; not inferred from arbitrary crafting recipes. |
| Master Researcher, Inventor | `MasterResearcherRecipeIndex` → recipe candidates/discovery | Index is invalidated on reload rather than scanning the entire pack for every proc; hostile ingredients are isolated. |
| Smelter, Overclock | Furnace mixin; native focus snapshot → melting/casting increments | Progress increases without rerunning whole native operations. Fractional progress carries rather than truncating small bonuses to zero. |
| Brewing Apparatus, Alchemic Transmutation | Brewing stand mixin | Brewing progression and paid brew effects remain on the native brewing path. |

## Tinkers' Construct entry, stats and save boundary

`RunicSkills.tryLoadIntegration("tconstruct", ...TConstructBootstrap)` resolves the optional
bootstrap only when installed. The bootstrap registers the equipment adapter, stack lock resolver,
native item stamps, station result recognition/permission guards, perk/power contributions,
workshop attribution and optional add-on adapters. `RunicSkillsMixinPlugin` and
`TConstructHookLedger` gate native injection by version, configuration and actual hook shape.

`TConstructEquipmentAdapter` derives roles and durability from native tool tags, material definitions,
modifier state and `ToolStack` stats. `TConstructRequirementResolver` composes automatic material-tier
requirements with explicit item/pack rules. `TConstructRepairBridge` applies native repair factors
to offered Runic repair; paid repair bonuses use the already-scaled committed native amount and
are not amplified a second time. Wear enters after native tool modifiers, through the
`ToolDamageUtil.damage` → `directDamage` call; direct ability/resource costs keep their native cost.
Projectile snapshots preserve owner and launch state; harvesting scopes distinguish an area tool's
root action from individual committed block breaks.

`runicskills:workmanship`, `runicskills:keystone`, the keystone recipe serializer and all existing ids
are unchanged. Paid item state remains readable when integration effects are disabled.
Temporary focus, preparation and contribution state remains session memory; Crown cooldown debt
uses the existing capability persistence. No save migration or mandatory dependency was introduced.

## All 16 native Tinkers perks

All listed ids are defined in `RegistryPerks`; runtime calls below are in
`integration/tconstruct/` unless a mixin is named.

| Id | Runtime behavior and final effect |
|---|---|
| `tc_cast_keeper` | Casting completion → `TConstructPerkHandler.castKeeperReturn`; returns one actually consumed cast after eligibility/chance checks. |
| `tc_thermal_rhythm` | Focus measurement → `thermalRhythmBonus` → melting increment, under the workshop cap. |
| `tc_repair_memory` | Committed qualifying native repair → tool-bound charge set → ordinary-wear contributor; one charge per root action. |
| `tc_material_harmony` | Delivered repair → distinct non-cosmetic materials → share of paid native restoration, capped with other repair bonuses. |
| `tc_tempered_edge` | Primary native melee stage → readiness/cooldown check → additive outgoing-damage contribution. |
| `tc_counterweight` | Player equipment reconciliation → transient attack-speed modifier for usable native melee equipment. |
| `tc_precision_footing` | BreakSpeed → grounded/non-sprinting/effective-native-tool gate → capped mining-speed contribution. |
| `tc_slime_steward` | Native overslime-support/shield check → wear avoidance only while the existing overslime shield is spent. |
| `tc_plate_discipline` | Equipment reconciliation → at least three usable native armor pieces → transient knockback resistance. |
| `tc_measured_draw` | Fully charged native bow/crossbow launch mixins → `measuredDrawFactor` → multiplicative inaccuracy reduction. |
| `tc_returning_hand` | Genuine thrown-tool return → preparation → next native throwing charge is increased and capped at full charge. |
| `tc_ember_guard` | Fire-tagged incoming hit → active focused controller burning fuel → capped incoming-damage reduction. |
| `tc_field_service` | Crafting-table native repair-kit recipe → grid/result native damage difference → capped paid repair bonus. |
| `tc_workshop_cadence` | Attributed completed casts of one recipe within a window → temporary casting-progress contribution. |
| `tc_adaptive_grip` | Committed mining/melee role changes on the same native hybrid tool → prepared next-action damage/mining bonus. |
| `tc_keystone_tinker` | `KeystoneService` + native station recipe + authoritative take guard → one permanent upgrade slot on an eligible tool for its material cost. |

## All 12 Artifice Powers

`TConstructPowers` is the shared definition/tuning/tooltip table. `RegistryPowers` preserves these
ids even without Tinkers; `PowerEligibility` explains missing capability/configuration reasons.
`TConstructPowerDispatcher` reads the same values and the existing Power cooldown/dispatch services.

| Id | Trigger → consumed benefit |
|---|---|
| `tc_first_heat` | Ready primary native hits within a window → next prepared primary hit damage. |
| `tc_plumb_line` | Committed grounded mining root actions → temporary mining speed. |
| `tc_quench` | Qualifying paid repair → temporary fire-tagged damage reduction. |
| `tc_working_memory` | Meaningful material part swap → next paid repair of the matching tool at that station. Preview does not spend cooldown. |
| `tc_hammer_and_tongs` | Shield block above configured prevented damage → next native primary melee hit. |
| `tc_temper_reserve` | Substantial paid repair → temporary avoidance on the actual repaired tool. |
| `tc_resonant_return` | Native thrown-tool return → next launch snapshot → first accepted projectile hit bonus. |
| `tc_workshop_aegis` | Attributed cast → temporary incoming reduction while focus remains valid; bypass categories stay excluded. |
| `tc_great_work` | Distinct assembly, repair and cast within a sequence window → Inspired mining/wear contributions. |
| `tc_last_temper` | Ordinary wear would break a usable native tool with more than one durability → clamp that spend to leave exactly one, spending persistent cooldown debt. |
| `tc_foundry_heart` | Personally inserted, focused melting inputs complete → counted operations → capped temporary melting/casting progress. |
| `tc_many_hands` | Owner and consenting team allies contribute at a focused workshop → contributors' temporary paid-repair bonus; participant override is now honored. |

## All 14 registered add-on perks

`TcAddonRegistry` checks add-on id, companion mods, flags, adapter installation and required hook
shape. `TcAddonHooks` carries contributions into the same capped channels as native perks.

| Id | Adapter → native observable → effect |
|---|---|
| `tc_mana_polisher` | `TcIntegrationsAdapter` + Botania mana-request seam → reduces the attributable repair charge before it is requested. |
| `tc_source_tempering` | TCIntegrations Ars armor repair completion → one prepared spell contribution via the Ars listener. |
| `tc_clockwork_alternation` | TCIntegrations native main/offhand melee identity → alternating-hand preparation → ordinary-wear avoidance. |
| `tc_seasoned_hands` | `TinkersLevellingAdapter` + tool-XP award seam → one scaled native tool-experience award. |
| `tc_banquet_of_cinders` | `TinkersDelightAdapter` → add-on food effect on player → shared melee-damage contribution. |
| `tc_soulsteel_resolve` | TCIntegrations Soul Stained attacking-tool trait → temporary defensive attribute reconciliation. |
| `tc_charged_craft` | `TinkersAdvancedAdapter` + positively identified tool energy operation → discounted FE cost. |
| `tc_thinking_last_thought` | `TinkersThinkingAdapter` → canceled death with native Last Effort effect → temporary wear avoidance. |
| `tc_thinking_studied_recall` | Thinking's sculk effect + canceled, consumed XP pickup → bounded share of consumed XP returned. |
| `tc_thinking_embellished_focus` | Thinking modifier ids on the native melee weapon → shared melee contribution. |
| `tc_jeweler_setting` | `TinkersJewelryAdapter` → committed station delivery containing a jewelry material → avoidance bound to the delivered piece. |
| `tc_gem_attunement` | Actually equipped jewelry, including the nested optional Curios reader → shared melee contribution. |
| `tc_undying_lustre` | Jewelry Undying trait during the bracketed death-resolution cost → bounded native save-wear reduction. |
| `tc_polished_facet` | Paid station repair of a jewelry-material piece → shared capped repair contribution. |

Tinkers' Katanas uses normal native tags/seams and needs no Java adapter. `tc_subspace_reserve`
and `tc_medallion_concord` are explicitly reserved and unregistered, not purchaseable no-ops.
Jewelry storage is not treated as a damage/repair statistic. Companion-gated Botania/Ars seams
must not be reported tested merely because core Tinkers loads.

## Validation and practical limits

New gameplay regressions exercise:

- Efficient Crafting compression, decompression and repair input consumption; existing tests retain
  guaranteed log-to-planks saving, exact one-unit refunds, zero chance and cake remainder handling.
- Stonecutter preview reselection, normal/shift committed output counts, full inventory refusal and
  a synthetic reversible datapack recipe pair and an explicit pack reward denial.
- Cursed enchanted books with and without the perk, ordinary-book output and untouched source.
- Lower-priority duplicates and three-way rule disagreement in every ordering, plus oversized reload retention.
- Native focus first-sweep sentinel behavior, expired heartbeat and removed association ownership.
- Many Hands' configured participant cap, bounded override values and actual excluded ally payout.

Relevant commands: `./gradlew build`; `./gradlew runGameTestServer`; and the native profile
`./gradlew runGameTestServer -PtinkersProfile=stable`. Build execution and consolidated results
are owned by the coordinating task; this report does not imply that every optional combination ran.

Remaining verification and constraints:

- Pack recipe graphs can contain multi-step or custom/dynamic conversions. The new index recognizes
  directly reversible static uniform-material pairs, not arbitrary transformations implemented by
  foreign code. Equipment/capability exclusions and existing reward denial tags still apply.
- Native Tinkers 3.12 remains conservative by design; matching supported 3.11 hook shapes does not
  justify enabling unverified 3.12 injection.
- Validate two actual multiplayer clients sharing a station/focus, quick-moving into near-full
  inventory, dimension changes, physical casting/melting, projectile return, and optional add-on
  resource charges in a real modpack. GameTests cannot establish every foreign event ordering.
- Recycling uses stable player/item/recipe rolls: a particular player's same salvage recipe has a
  repeatable outcome. This prevents preview rerolls but is not independent per physical item.
  Changing that requires a separately designed committed salvage result path.
- Lore Mastery's grindstone-orb origin currently uses an open-grindstone plus fresh-orb heuristic;
  it does not prove the orb came from that grindstone. A precise origin marker would require a
  dedicated native XP-spawn seam rather than a looser pickup test.
