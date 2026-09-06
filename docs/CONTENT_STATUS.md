# Content Status — what the mod admits it is not doing

Companion to [`PERK_AUDIT.md`](PERK_AUDIT.md). That document covers perks; this one covers the
mechanism that makes incompleteness visible at runtime, and the Powers it currently applies to.

## The problem it solves

RS10-004 found the mod shipping three different kinds of dishonesty at once, none of them
distinguishable from the others — or from finished content — by a player looking at the screen:

- content that did nothing at all;
- content that did something materially different from its tooltip;
- content whose mod was not installed.

A player could spend a scarce perk rank or Power slot on any of them and find out only by noticing
that nothing happened.

## The mechanism

[`ContentStatus`](../src/main/java/com/otectus/runicskills/registry/content/ContentStatus.java) is
the vocabulary; [`ContentStatusIndex`](../src/main/java/com/otectus/runicskills/registry/content/ContentStatusIndex.java)
is the whole list of exceptions. Everything not named there is `FULL`.

| Status | Selectable by default | Meaning |
| --- | --- | --- |
| `FULL` | yes | Implemented, and the tooltip describes what the code does. |
| `PARTIAL` | yes | Part of the design landed; the tooltip covers the part that works. |
| `APPROXIMATE` | yes | A documented substitution delivers the same intent by a different lever; the tooltip describes the substitution. |
| `INERT` | **no** | Registered so saves keep resolving; no behaviour at all. |
| `UNAVAILABLE_DEPENDENCY` | **no** | Implemented, but its mod is not installed. Never declared — resolved from the runtime mod list. |

What `INERT` costs a player, in every place it could otherwise cost them something:

- **Not equippable.** `PowerEligibility` denies with `INERT_CONTENT`, checked ahead of every skill
  gate — meeting the requirements for an inert Power would still buy nothing.
- **Not visible.** `RegistryPowers.isHiddenFromUi` hides it unconditionally. This is deliberately
  stricter than the disabled-content rule, which an operator can choose to show greyed out: a
  greyed-out row is something a player might reasonably wait for.
- **Not charged for.** `PowerEligibility.spentPowerPoints` skips it, so a Power sitting in a slot
  from a save made before this existed does not spend the budget that pays for working Powers.
- **Not lost.** The id stays in the slot and in the save. Removing it silently would be data loss;
  the player can unequip it whenever they like, and `unequipUnknownPower` covers the case where the
  id no longer resolves at all.

`powerEnableExperimentalContent` (default off) brings inert content back, badged as experimental —
for developing it, and for testing a dispatcher against an installed Iron's Spells.

**One honest deviation from the plan.** RS10-004's acceptance criteria say inert selections should
consume no *slots* either. They still do, until the player removes them. Making an inert occupant
free its slot would let a player fill the freed slot and then exceed the cap the moment that Power
is implemented; evicting it automatically would delete a saved choice. Between over-subscription and
data loss, leaving the slot occupied and clearly labelled is the least bad of the three, and the
player can reclaim it in one click.

## Current exceptions

### Inert (0)

There used to be nineteen, all of them Iron's Spells school Powers with no dispatcher case:
`marrow_sense`, `kinetic_affinity`, `piercing_insight`, `arcane_echo`, `black_hole_resonance`,
`creeper_cascade_mastery`, `fang_follow_through`, `shield_wall`, `ember_trail`, `heat_haze`,
`scorched_earth`, `wings_of_judgment`, `frost_echo`, `reforge_the_shadow`, `shatter`,
`conduit_mark`, `static_cling`, `blight_spread`, `venomous_harvest`.

All nineteen now have one, in `IronsSpellbooksSchoolPowerDispatcher`. Eighteen are Full; only
`reforge_the_shadow` needed a substitution, and it is listed below. Iron's Spells is still a
`compileOnly` dependency, so none of this can be exercised by the GameTest server — no spell,
spell entity or spell event exists there. That is why `power_no_effect_allowlist.txt` and the
runtime table are the record, and why both are machine-checked against each other.

### Approximate (10)

| Power | Designed | Ships |
| --- | --- | --- |
| `sacrifice_cascade` | refund per remaining summon, capped | flat mana refund |
| `counterspell_riposte` | bonus vs the countered target, plus cooldown refund | a global damage window |
| `thunder_lord` | strikes *and* bolt cooldown reduction | the strikes |
| `warmages_covenant` | ignore 25% spell resist | +25% damage |
| `step_between` | halve the next cast's time | +20% damage on the next spell |
| `folded_space` | half mana *and* no cooldown | half mana |
| `herald_of_dawn` | triggers when any ally falls low | triggers when you fall low |
| `forbidden_knowledge` | doubles a specific drop pool | doubles every drop |
| `glacial_sovereign` | Chilled accumulation, extension and Ice Tomb | the extension |
| `reforge_the_shadow` | buffs your Ice Shadows | buffs your summoned Polar Bear |

Each has a comment at its dispatcher explaining which lever was substituted and why, and **each has
had its tooltip rewritten to describe the lever that ships**. That is what makes them selectable: an
approximation the player is told about is a design decision, while one they are not told about is a
bug.

### Perks (16 core + 7 add-on Tinkers' — FULL; others empty)

Twenty-three Tinkers' Construct perks added in 2.0.7: 16 core (S4a and S4 completion) and 7 add-on (S5), all status FULL. All are dormant when Tinkers'
is absent; the perk ids do not count against the budget (see `PerkBudgetDormancyGameTest` and
`RegistryPerks.countEnabledPerks`). All effects are gated on `enableTConstructPerks` (or the add-on equivalent) and the
capability they require (see [`TCONSTRUCT_INTEGRATION.md`](TCONSTRUCT_INTEGRATION.md) Perks section and Add-on integrations section).

| ID | Name | Skill | Test |
| --- | --- | --- | --- |
| `tc_cast_keeper` | Cast Keeper | Tinkering | `TcCorePerksGameTest.castKeeperReturnsNothingWithoutAFocus()` |
| `tc_thermal_rhythm` | Thermal Rhythm | Tinkering | `TcCorePerksGameTest.thermalRhythmContributesToMelting()` |
| `tc_repair_memory` | Repair Memory | Tinkering | `TcCorePerksGameTest.repairMemoryAddsAvoidanceAndSpendsOneChargePerAction()` |
| `tc_material_harmony` | Material Harmony | Tinkering | `TcCorePerksGameTest.repairBonusesCannotOverrepair()` |
| `tc_tempered_edge` | Tempered Edge | Strength | `TcCorePerksGameTest.temperedEdgeRaisesAFullyWoundUpHit()`, `temperedEdgeIgnoresAHitOutsideASwing()` |
| `tc_counterweight` | Counterweight | Dexterity | `TcCorePerksGameTest.counterweightAppliesOnlyToABroadToolInHand()` |
| `tc_precision_footing` | Precision Footing | Endurance | `TcCorePerksGameTest.precisionFootingSpeedsUpGroundedMining()` |
| `tc_slime_steward` | Slime Steward | Endurance | `TcCorePerksGameTest.slimeStewardIgnoresAToolWithoutOverslime()` |
| `tc_plate_discipline` | Plate Discipline | Constitution | `TcCorePerksGameTest.plateDisciplineNeedsThreeWornPieces()` |
| `tc_measured_draw` | Measured Draw | Dexterity | `TcCorePerksGameTest.measuredDrawScalesInaccuracy()` |
| `tc_returning_hand` | Returning Hand | Dexterity | `TcCorePerksGameTest.returningHandAppliesOnceAndClampsAtFullCharge()` |
| `tc_ember_guard` | Ember Guard | Constitution | `TcCorePerksGameTest.emberGuardNeedsAFocusOnAHeatedWorkshop()` |
| `tc_field_service` | Field Service | Tinkering | `TcCorePerksGameTest.repairBonusesCannotOverrepair()` |
| `tc_workshop_cadence` | Workshop Cadence | Tinkering | `TcCorePerksGameTest.workshopCadenceIsSilentUntilItsStreakCompletes()` |
| `tc_adaptive_grip` | Adaptive Grip | Tinkering | `TcCorePerksGameTest.adaptiveGripArmsOnARoleSwitchWithOneTool()` |
| `tc_keystone_tinker` | Keystone Tinker | Tinkering | `TcKeystoneTinkerGameTest.anEligibleSmithFitsOneSlotByClick()`, `anEligibleSmithFitsOneSlotByShiftClick()`, `anIneligibleSmithIsRefusedAndPaysNothing()`, `aFakePlayerFitsNothing()`, `aSecondKeystoneIsRefused()`, `disablingTheServiceKeepsAPaidSlot()` |

### Add-on perks (7 shipped, 1 reserved — 2.0.7 S5)

Seven new add-on perks from Tinkers' Construct add-on layers: four from TCIntegrations (on companion mods Botania, Ars Nouveau, Create, Malum), one from Tinkers' Levelling Addon, one from Tinkers' Delight, and one from Tinkers' Advanced. All are dormant when their add-on is absent; the perk ids remain selectable and do not count against the budget. All effects are gated on the add-on-specific `enable…Integration` flag and the capability the effect requires.

| ID | Name | Required Add-on | Governing Skill | Base Level | Trigger | Effect & Cap Channel | Test Method(s) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `tc_mana_polisher` | Mana Polisher | TCIntegrations + Botania | Tinkering | 16 | Tool self-repairs with Botania mana | Mana cost −`tcManaPolisherPercent`% (default 10%), min 1 | (Dormant: Botania not in test classpath) |
| `tc_source_tempering` | Source Tempering | TCIntegrations + Ars Nouveau | Magic | 16 | Armour repair spends Ars source | Next Ars spell +`tcSourceTemperingPercent`% damage (default 5%), window `tcSourceTemperingWindowSeconds`s (default 8) | (Dormant: Ars not in test classpath) |
| `tc_clockwork_alternation` | Clockwork Alternation | TCIntegrations + Create | Tinkering | 16 | Offhand/main-hand hits alternate | Next tool use avoids `tcClockworkAlternationPercent`% durability (default 10%), window `tcClockworkAlternationWindowSeconds`s (default 5) | (Dormant: Create not in test classpath) |
| `tc_soulsteel_resolve` | Soulsteel Resolve | TCIntegrations + Malum | Constitution | 20 | Primary hit with soul-stained gear | Knockback resistance +`tcSoulsteelResolveAmount` (default 0.06) for `tcSoulsteelResolveSeconds`s (default 4) | (Dormant: Malum not in test classpath) |
| `tc_seasoned_hands` | Seasoned Hands | Tinkers' Levelling Addon | Tinkering | 16 | Tool gains experience from action | Tool XP award ×(1 + `tcSeasonedHandsPercent`/100, default 10%), carried per player per tool item | `TcAddonPerksGameTest.seasonedHandsScalesOneAward()`, `seasonedHandsIgnoresCommandAndNegativeAwards()`, `LevellingCoexistenceGameTest.toolExperienceIsNotPlayerExperience()`, `theCarryDoesNotTravel()`, `nothingIsGrantedPastTheAddonsCap()`, `theOnlyDifferenceIsTheIntendedOne()` |
| `tc_banquet_of_cinders` | Banquet of Cinders | Tinkers' Delight + Farmer's Delight | Wisdom | 12 | Primary melee hit, Nourishment active | Damage +`tcBanquetOfCindersPercent`% (default 3%), summed under `tconstructNewDamageBonusCap` | `TcAddonPerksGameTest.banquetPaysOnlyWhileNourished()` |
| `tc_charged_craft` | Charged Craft | Tinkers' Advanced | Tinkering | 24 | Identified tool operation | FE cost −`tcChargedCraftPercent`% (default 10%), min 1 FE | `TcChargedCraftGameTest.chargedCraftDiscountsWithAPositiveMinimum()` |
| `tc_medallion_concord` | Medallion Concord (reserved) | Tinkers' Ingenuity (unresolved) | Wisdom | 16 | — | — | (Unregistered: Tinkers' Ingenuity has no 1.20.1 artifact; perk id retained as UPSTREAM_UNAVAILABLE per spec §10.3) |

### Add-on perks (Tinkers' Thinking, Tinkers' Jewelry) (7 shipped, 1 reserved — 2.1.0)

Seven further add-on perks: three from Tinkers' Thinking (no companion mod) and four from Tinkers'
Jewelry (Curios is the companion for the two that need a worn piece). Neither add-on is a compile
dependency and neither contributes a mixin; both are reached from FORGE events
`TConstructPerkHandler` already subscribes, and observed by registry id (modifier id, mob effect id,
material namespace) rather than by class. All are dormant when their add-on is absent; the perk ids
remain selectable and do not count against the budget. All effects are gated on the add-on's own
`enable…Integration` flag and the capability the effect requires.

| ID | Name | Add-on | Skill | Base Level | Trigger | Effect & Channel | Test Method(s) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `tc_thinking_last_thought` | Last Thought | Tinkers' Thinking | Wisdom | 16 | A death Tinkers' Thinking's own death save refused (cancelled `LivingDeathEvent` + `tinkers_thinking:last_effort`) | `tcThinkingLastThoughtPercent`% (default 12) wear avoidance for `tcThinkingLastThoughtSeconds`s (default 15); add-on wear-avoidance channel | `TinkersThinkingPerksGameTest.lastThoughtReducesDeathWear()` |
| `tc_thinking_studied_recall` | Studied Recall | Tinkers' Thinking | Wisdom | 20 | The add-on cancels an experience pickup and converts it to `sculk_power` | Returns `tcThinkingStudiedRecallPercent`% (default 15) of the consumed orb as experience; add-on experience-recovery channel | `TinkersThinkingPerksGameTest.studiedRecallAddsXpShare()` |
| `tc_thinking_embellished_focus` | Embellished Focus | Tinkers' Thinking | Tinkering | 24 | Main-hand weapon carries a Tinkers' Thinking melee modifier (`TraitFeatureRegistry.Feature.THINKING_EMBELLISHMENT`) | `tcThinkingEmbellishedFocusPercent`% (default 4) added to melee-damage sum | `TinkersThinkingPerksGameTest.embellishedFocusAddsMeleeShare()` |
| `tc_jeweler_setting` | Jeweler's Setting | Tinkers' Jewelry | Tinkering | 12 | A Tinker Station take delivers a jewelry-material piece | `tcJewelerSettingPercent`% (default 10) wear avoidance on that piece for `tcJewelerSettingSeconds`s (default 30); add-on wear-avoidance channel | `TinkersJewelryPerksGameTest.jewelerSettingAffectsStationTake()` |
| `tc_gem_attunement` | Gem Attunement | Tinkers' Jewelry | Wisdom | 16 | A jewelry piece is worn in a vanilla equipment slot (not Curios-only) | `tcGemAttunementPercent`% (default 3) added to melee-damage sum | `TinkersJewelryPerksGameTest.gemAttunementClassifiesJewelryMaterial()` |
| `tc_undying_lustre` | Undying Lustre | Tinkers' Jewelry | Constitution | 20 | Inside a death-resolution bracket, on the ring the add-on's own undying save is charging | `tcUndyingLustrePercent`% (default 25) wear avoidance on that save's cost; add-on wear-avoidance channel | `TinkersJewelryPerksGameTest.undyingLustreReducesSaveWear()` |
| `tc_polished_facet` | Polished Facet | Tinkers' Jewelry | Tinkering | 20 | A paid station repair of a jewelry-material piece | `tcPolishedFacetPercent`% (default 8) added to the paid-repair share, bounded by `tconstructRepairBonusCap` | `TinkersJewelryPerksGameTest.polishedFacetAffectsRepair()` |
| `tc_subspace_reserve` | Subspace Reserve (reserved) | Tinkers' Jewelry | Endurance | 24 | — | Unregistered: `tinkersjewelry:subspace` is inventory storage with no durability, damage, repair or progression quantity for a Runic channel; capability reports `UPSTREAM_UNAVAILABLE` | `TcAddonAbsenceGameTest.subspaceIsReservedAndUnregistered()` |

Tinkers' Katanas ships no Java classes and needs no adapter, capability or perk: its two tools
register into the `tconstruct:modifiable/*` tags the existing perks already read (see
[`TCONSTRUCT_INTEGRATION.md`](TCONSTRUCT_INTEGRATION.md) Add-on integrations section). Verified by
`TcAddonAbsenceGameTest.katanasIsDataOnlyAndNeedsNoAdapter()`.

### Artifice Powers (12 — 2.0.7 S4b)

Twelve new Tinkers' Construct Powers added in 2.0.7 (S4b), all status FULL. All are dormant when
Tinkers' is absent or when `enableTConstructPowers` is off; reachable at the configured governing
skill threshold and unequippable with the `missing_capability` reason when Tinkers' is absent, the
capability unsupported, or `enableTConstructPowers` is off. Cooldown debt persists across logout
as `runicskills:tc_state` (schema 1, bounds per `PowerCooldownDebt`).

**Marks (§11.2)** — 4 Powers, Tinkering school, internal cooldown default 100–600 ticks:

| ID | Name | Tier | Trigger | Effect & Cooldown Channel | Test |
| --- | --- | --- | --- | --- | --- |
| `tc_first_heat` | First Heat | Mark | Three primary hits with native weapon in sequence window (`window_seconds`, default 8s) prepare the fourth for +`damage_percent`% (default 10%) damage, lasts `prepared_seconds` (default 5s), readiness `readiness` (default 0.90). Cooldown 100t. | Melee damage composition | `TcArtificePowersGameTest.firstHeatPreparesTheFourthHit()` |
| `tc_plumb_line` | Plumb Line | Mark | Three committed mining actions with native tool on ground in sequence window (`window_seconds`, default 6s) prepare +`mining_percent`% (default 10%) mining speed for `prepared_seconds` (default 4s). Cooldown 120t. | Mining speed composition | `TcArtificePowersGameTest.plumbLinePreparesMiningSpeed()` |
| `tc_quench` | Quench | Mark | A substantial paid repair grants `reduction_percent`% (default 15%) fire damage reduction for `duration_seconds` (default 6s). Points restored: minimum `min_restored_points` (default 10) or `min_restored_percent`% of tool max (default 5%). Cooldown 600t. | Incoming damage reduction composition | `TcArtificePowersGameTest.quenchReducesFireDamageAfterAPaidRepair()` |
| `tc_working_memory` | Working Memory | Mark | A paid part change prepares +`repair_percent`% (default 10%) restoration on the next paid repair of that same tool within `window_seconds` (default 120s). Cooldown 600t. | Station transaction rewards | `TcArtificePowersGameTest.workingMemoryPaysTheNextRepairOfThatTool()` |

**Seals (§11.3)** — 4 Powers, Tinkering school, internal cooldown default 200–1200 ticks:

| ID | Name | Tier | Trigger | Effect & Cooldown Channel | Test |
| --- | --- | --- | --- | --- | --- |
| `tc_hammer_and_tongs` | Hammer and Tongs | Seal | A shield block that stops at least `min_blocked` health (default 2) prepares the next native melee hit for +`damage_percent`% (default 15%) damage within `window_seconds` (default 6s). Cooldown 200t. | Melee damage composition | `TcArtificePowersGameTest.hammerAndTongsAnswersARealShieldBlock()` |
| `tc_temper_reserve` | Temper Reserve | Seal | A substantial paid repair grants that tool +`avoidance_points`% (default 15%) wear avoidance for `duration_seconds` (default 20s). Points restored: minimum `min_restored_points` (default 10) or `min_restored_percent`% of max (default 10%). Cooldown 1200t. | Wear avoidance composition | `TcArtificePowersGameTest.temperReserveAddsAvoidanceToTheRepairedTool()` |
| `tc_resonant_return` | Resonant Return | Seal | After a thrown native tool returns, the next throw within `window_seconds` (default 8s) carries +`damage_percent`% (default 15%) damage on its first hit. Cooldown 300t. | Projectile damage composition | `TcArtificePowersGameTest.resonantReturnPreparesOneLaunch()` |
| `tc_workshop_aegis` | Workshop Aegis | Seal | Completing an attributed cast grants `reduction_percent`% (default 10%) damage reduction for `duration_seconds` (default 6s) while staying in the workshop. Cooldown 400t. | Incoming damage reduction composition | `TcArtificePowersGameTest.workshopAegisProtectsAfterACast()` |

**Crowns (§11.4)** — 4 Powers, Tinkering school, internal cooldown default 3600–6000 ticks:

| ID | Name | Tier | Trigger | Effect & Cooldown Channel | Test |
| --- | --- | --- | --- | --- | --- |
| `tc_great_work` | The Great Work | Crown | One assembly, one paid repair and one manual cast within `sequence_seconds` (default 300s) make you Inspired for `duration_seconds` (default 60s): +`mining_percent`% (default 10%) mining speed and +`avoidance_points`% (default 10%) wear avoidance. Cooldown 3600t. | Mining speed + wear avoidance composition | `TcArtificePowersGameTest.theGreatWorkNeedsAllThreeOperations()` |
| `tc_last_temper` | Last Temper | Crown | Ordinary wear that would break a usable native Tinkers' tool instead leaves it on exactly 1 durability. Per player, once per cooldown (3600t). Cooldown consumed only on a lethal loss. | Wear clamp (not a composition channel) | `LastTemperGameTest.aLethalLossLeavesExactlyOneDurabilityAndSpendsTheCooldown()` |
| `tc_foundry_heart` | Heart of the Foundry | Crown | `operations` (default 10) melting operations from personal insertions prepare +`progress_percent`% (default 15%) melting and cooling progress for `duration_seconds` (default 30s). Cooldown 6000t. | Workshop acceleration composition | `TcArtificePowersGameTest.heartOfTheFoundryCountsOnlyAttributedOperations()` |
| `tc_many_hands` | Many Hands, One Forge | Crown | You and an ally both working the same focused workshop within `window_seconds` (default 60s) grant every contributor +`repair_percent`% (default 10%) paid repair restoration for `duration_seconds` (default 60s). At most `max_contributors` (default 4) bonuses stack. Cooldown 3600t. | Repair bonus composition | `TcArtificePowersGameTest.manyHandsNeedsTheOwnerAndAnAlly()` |

**Eligibility & presentation:**

- Unequippable when Tinkers' Construct is absent, the required capability is unavailable, or `enableTConstructPowers` is off; the reason is the `MISSING_CAPABILITY` denial from `PowerEligibility`.
- Cooldown debt written at the moment a cooldown starts (not at save time). Serialized as remaining ticks, read back at login into the runtime map via `PowerCooldownDebt.restore(ServerPlayer)`, and never shortened by a session-local cooldown that has already started (no exploit by logout).
- VFX school: `runicskills:tinkering` (Artifice), colour 0xB86E2E (tan-brown) primary + 0xF0DFA8 (sand) glow, motion RISING, icon rune slots regenerated with collision-probe walk order (three Powers share re-generated icons, still distinct).

See [`PERK_AUDIT.md`](PERK_AUDIT.md) for the original audit of older content.

## How this is kept honest

`ContentStatusTest` asserts the runtime table and the build-time
[`power_no_effect_allowlist.txt`](../src/test/resources/power_no_effect_allowlist.txt) name the same
Powers, in both directions, that no entry names a Power that does not exist, and that the equip path,
the UI and the budget all still consult the table. Two mechanisms describing one fact are only useful
while they agree.
