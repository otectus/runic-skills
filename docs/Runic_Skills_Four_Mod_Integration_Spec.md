# Runic Skills: Simply Swords, Simply More, T.O. Magic and Tide Integration Specification

**Audience:** Coding agent and modpack maintainer<br>
**Research date:** September 7, 2026<br>
**Target:** Minecraft 1.20.1, Forge, Java 17<br>
**Deliverable:** Implementation plan, compatibility contracts, 32 proposed perks, 24 proposed Powers, configuration, and acceptance criteria<br>
**Status:** Research and design complete within the evidence limits below. No integration code was implemented or launched during this review.

## 1. Recommended direction

Add all four integrations. Give each a distinct purpose:

| Integration | Intended experience |
|---|---|
| Simply Swords | Weapon mastery built around deliberate attacks, awakening, runic equipment, and the rhythm between native abilities and melee |
| Simply More | Mounted combat, shield-breaking follow-ups, specialized weapon handling, and adaptation between Mimicry forms |
| T.O. Magic ’n Extras | Aqua magic, spell-and-weapon rotations, evolving relics, careful summon support, and meaningful defensive play |
| Tide | Skilled preparation, readable habitats, thoughtful equipment choices, and collection milestones that preserve the fishing game |

Build on Runic Skills’ existing skills, perk budgets, reactive Powers, equipment adapters, and action accounting. Keep native progression, costs, drops, crafting rules, and ability behavior authoritative. The creative additions should reward decisions and mastery without multiplying every existing bonus.

All content and numerical values proposed in this document are **new design recommendations**, not descriptions of features already implemented by Runic Skills or the integrated mods. The default figures are initial balancing targets requiring playtesting. Names such as `IntegrationAvailability` and `TideCatchCommitted` below describe **new Runic abstractions to implement**, not upstream APIs.

### 1.1 Baseline and branch decision

The repository’s default `master` branch points to **`ac7ab2d0c88a786b64a08ab18e0a8bf091830886`**, whose build properties identify version **2.0.6**. The branch `feature/tinkers-integration-2.1.0` points to **`3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7`**, dated September 6, 2026, identifies version **2.1.0**, and is one commit ahead. It contains the equipment, action, repair, and integration machinery most useful here. [Runic branch comparison][rs-compare] · [2.1.0 version][rs-version] · [master build properties][rs-master-properties]

**Implementation baseline:** the 2.1.0 branch, or a later branch demonstrably containing its changes. Recheck the actual implementation HEAD before editing. Do not recreate those systems on an older branch, silently discard the Tinkers work, or assume the public default branch already includes it. This document does not assign the next release number.

Runic uses vanilla experience to purchase its ten skills. Keep that progression model. Fishing catches, forge previews, native awakening XP, spell pulses, and weapon transformations must not directly grant free skill levels. [Runic README][rs-readme] · [ProgressionService][rs-progression]

### 1.2 Non-negotiable outcomes

1. Each optional integration has independent switches for its gates, perks, Powers, and specialized features.
2. A successful Runic requirement check never overrides an upstream restriction.
3. UI explanations and server decisions come from the same resolved rule.
4. Benefits occur once at their intended scope: attack, activation, cast, catch, or committed workshop operation.
5. Missing mods and unavailable hooks preserve saved selections without leaving purchasable effects that do nothing.
6. Full support is claimed only for tested artifact combinations, including production jars and a dedicated server.
7. Existing worlds receive no newly enabled automatic equipment locks merely because Runic was updated.

## 2. Version and dependency contract

### 2.1 Research targets

These are research targets and candidate validation profiles, not a certificate that the complete combination runs correctly.

| Component | Verified release or source target | Evidence and implementation implication |
|---|---|---|
| Runic Skills | 2.1.0 branch, SHA `3fe056a…` | Use the full SHA above. Inspect source implementation rather than treating generated documentation as authoritative. |
| Simply Swords | Forge 1.20.1 **1.70.2**, file **8746028**, August 27, 2026 | Current release inspected through official metadata. [Release][ss-release] |
| Simply Swords source | `Architectury-1.20.1`, SHA `9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf`, August 26, 2026 | Source still declares **1.70.1-1.20.1**. Its APIs are useful evidence, but their exact equivalence to the 1.70.2 binary needs verification. [Properties][ss-properties] |
| Simply More | Forge 1.20.1 **1.1.4**, file **8736447**, August 26, 2026 | The author describes this as an interim compatibility update for Simply Swords 1.70.x. Do not use the future 1.3.0 backport as a shipped baseline. [Release][sm-release] |
| Simply More source | `v1.20.1`, SHA `a7cff4f128b502dc43845947d81457bfe450741f` | Declares 1.1.4; Forge build uses Simply Swords file 8723370, which is 1.70.1. [Properties][sm-properties] · [Forge build][sm-build] |
| T.O. Magic ’n Extras | **6.3.0-1.20.1**, file **7424522**, January 6, 2026 | Actual artifact `traveloptics-6.3.0-1.20.1.jar`; mod ID `traveloptics` verified from its manifest. [Release][tom-release] |
| Tide | **Tide 2.1.1 Hotfix**, Forge 1.20.1, file **8571673**, August 3, 2026 | Use the hotfix artifact. [Release][tide-release] |
| Tide source | `Lightning-64/Tide-2`, SHA `876b95f31328f4e698d5150f7d840ab033d1b06d` | Current implementation. Source version/date align with the release; binary equivalence remains untested. [Repository][tide-repo] · [Build][tide-build] |

**Simply Swords has a material migration boundary.** Its 1.70.1 release notes warn against updating existing saves from versions before 1.70.0, identify incompatible old configuration, and caution about addons. Create a fresh validation instance for the 1.70 profile. An existing pack using 1.56 requires a separate adapter/profile; this review did not establish its legacy combat hooks. Do not automatically upgrade that pack to satisfy this spec. [Migration warning][ss-migration] · [Legacy 1.56 properties][ss-legacy]

### 2.2 Dependency facts to encode in the compatibility manifest

| Module | Verified dependency facts | Required handling |
|---|---|---|
| Simply Swords | Source Forge manifest: `simplyswords`; Architectury ≥9.2.14; Fzzy Config ≥0.6.6; SimplyTooltips ≥0.1 on the client | Read the shipped artifact’s own manifest before locking a test profile. Do not require client-only tooltip classes on a dedicated server. [Manifest][ss-manifest] |
| Simply More | `simplymore`; Simply Swords, Architectury, and Cloth Config required; source’s Simply Swords lower bound is permissive | Verify the specific pair. A broad declared range does not establish carver, socket, or transformation compatibility. [Manifest][sm-manifest] |
| T.O. Magic | Binary manifest requires `irons_spellbooks` ≥1.20.1-3.15.0, `alexscaves` ≥2.0.0, and `attributeslib` ≥1.3.7; Cataclysm range is literally `[2.57.,)` | Record the unusual Cataclysm range verbatim as evidence; use an actual resolved dependency set for tests. Official Relations also lists Curios as required, although it is absent from this jar’s direct dependency entries. [Release artifact][tom-release] · [Relations][tom-dependencies] |
| Tide | `tide`; Forge ≥47; Cloth Config ≥11 in the Forge template | Curios support is optional for Tide. Select the 1.20.1 Forge preprocessing/build target when examining the multi-version source. [Manifest][tide-manifest] · [Forge properties][tide-properties] |

Runic’s inspected build uses Iron’s Spellbooks file **7402504**, version **1.20.1-3.15.0**. Simply Swords’ source compiles against 3.16.2 while separately declaring a lower native minimum. Those are different kinds of evidence. Test a mutually compatible installed ISS version across the full pack; do not mechanically downgrade ISS to match one compile declaration. [Runic build][rs-build] · [ISS release][iss-release] · [Simply Swords properties][ss-properties] · [SimplySwords entry point][ss-entry]

### 2.3 Compatibility profiles

Implement a manifest recording Minecraft version, loader, mod versions, artifact identifiers/hashes, source revision when known, hook descriptors, expected injection counts, remapping owner, and the test result for each capability.

Use these initial profile names:

- `ss_170_forge1201`: current Simply Swords lane.
- `sm_114_ss170_forge1201`: current paired lane.
- `tom_630_forge1201`: T.O. companion plus the resolved required dependency set.
- `tide2_211_hotfix_forge1201`: current Tide lane.
- `legacy_unverified`: diagnostic classification only; never pretend current APIs exist on older jars.

A supported version string is necessary but insufficient. Registration and signature checks establish whether an adapter can load; focused runtime tests establish behavior. Unsupported capabilities become unavailable with a precise reason. Unknown versions may retain existing generic Runic behavior, but receive no unverified native-state mutations or advanced bonuses.

## 3. Reuse and strengthen the existing Runic architecture

### 3.1 Existing systems to extend

Paths in this table are relative to `src/main/java/com/otectus/runicskills/` at the 2.1.0 baseline.

| Existing code | Verified role | Required extension |
|---|---|---|
| `common/equipment/EquipmentProfileService.java` | Ordered adapters, vanilla fallback, equipment deny-tag precedence | Add narrowly owned adapters. Simply More must not be swallowed by a broad Simply Swords superclass match. [Source][rs-equipment] |
| `integration/lock/LockProviderRegistry.java` | Separate generated ID locks and live stack-aware providers | Compose new rules with existing manual locks; detect contested ownership. [Source][rs-locks] |
| `integration/lock/LockAction.java` | Existing actions are USE, ATTACK, EQUIP, CRAFT, TAKE | Add explicit activation/cast/fishing/workshop contexts where needed; never treat every action as ordinary use. Preserve old serialized meanings. [Source][rs-lock-actions] |
| `common/actions/RunicActionContext.java` | Root action identity and effect claims | Extend to native activations; carry identity into asynchronous projectiles. Check its current 16-claim bound against the expanded catalog. [Source][rs-actions] |
| `common/actions/ProjectileSnapshot.java` and `common/combat/DamageContext.java` | Provenance for delayed actions and Runic secondary damage | Distinguish native ability damage from a new Runic secondary effect. Avoid both lost attribution and repeated scaling. [Snapshots][rs-projectile] · [Damage context][rs-damage] |
| `registry/powers/PowerEligibility.java` and `PowerDispatch.java` | Shared active/equip checks, points, prerequisite chains, proc reporting | Add module capability predicates and route all new dispatch through this authority. [Eligibility][rs-power-eligibility] · [Dispatch][rs-power-dispatch] |
| `integration/IronsSpellbooksIntegration.java` | Generic spell gating, cost changes, spell damage handling | Add qualified addon school mappings and supplemental T.O. rules through one spell pipeline. [Source][rs-iss] |
| `integration/StarcatcherIntegration.java` | Existing fishing bonuses using Forge events and drop-based identification | Give proven Tide provenance priority and prevent both adapters paying for one catch. [Source][rs-starcatcher] |
| `common/durability/RepairService.java` | Shared repair routing | Reuse for actual repairs; do not imitate repairs with arbitrary NBT writes. [Source][rs-repair] |

### 3.2 Targeted architectural work

**Availability must have one meaning.** Introduce a small `IntegrationAvailability` service returning module state, supported capabilities, and explanation. Separate dependency presence, adapter health, configuration, and player eligibility.

The current Power eligibility path includes dependency and Tinkers capability checks, while `spentPowerPoints` consults the declared content selectability path. That latter path does not by itself evaluate every dynamic capability. Extend availability handling consistently across selection, effect execution, point accounting, and dormant selections; add a direct regression case for a registered Power whose dependency or native hook becomes unavailable. This is a source-level discrepancy to address, not a claim that a reproduced player bug was observed. [Power eligibility][rs-power-eligibility] · [Content status][rs-content]

**Use fully qualified school identifiers.** The ISS integration currently indexes secondary school bonuses by path strings, and `PowerRuntime.DamageTypeMemory` uses a fixed school enum. Add a registry-keyed school descriptor map for addon school attribution. Preserve legacy mappings and unknown-school behavior. This must not apply Aqua bonuses to an unrelated mod merely because a path is similar. [ISS integration][rs-iss] · [Power runtime][rs-power-runtime]

**Persist new cooldown debt.** The existing `PowerCooldownDebt` implementation specifically persists the twelve Artifice IDs. Extend the mechanism with an allowlisted registry of persistent cooldowns and a versioned state section for these integrations. Do not assume ordinary runtime cooldown maps already prevent logout resets. [Cooldown debt][rs-cooldown]

**Keep new classifications precise.** Add fishing-rod, spell-carrier, and native-ability roles through additive metadata or explicit role extensions. A name containing “rod” must not decide whether an item belongs to Magic or fishing. Material tiers, rarity text, and attack attributes are fallback evidence, not universal classification rules.

### 3.3 Proposed module structure

Keep the common contract free of optional-mod imports:

| Proposed area | Responsibility |
|---|---|
| `integration/common/` | Availability, version manifests, action traits, bounded rule evaluation, diagnostics |
| `integration/simplyswords/` | Profiles, awakening/gem reads, activation lifecycle, native scaling attribution, forge commits |
| `integration/simplymore/` | Specific weapon families, mounted predicates, shield-break outcomes, Mimicry identity |
| Optional `runicskills-tom-compat` project/module | T.O. public API binding, Aqua descriptor, native capability status |
| `integration/tide/` | Rod/attachment profiles, catch transactions, journal reads, minigame-specific assistance |
| Corresponding client adapters | Tooltips, journal enhancements, visual feedback; loaded only on client |

Register optional subscribers only after dependency checks, then check live configuration at each entry point. Turning an integration off and back on through an allowed reload must work without requiring a subscriber that was never registered.

## 4. Gating and action authority

### 4.1 Three configuration profiles

| Profile | Automatic new gear/ability gates | Perks and Powers | Intended use |
|---|---|---|---|
| **Compatibility** — default | Off | On when dependencies and required capabilities are supported | Existing worlds and packs |
| **Adventure** — opt-in | On, using the reference requirements below | On | Deliberate Runic progression |
| **Pack authored** | Explicit item, tag, spell, ability and action rules | Individually controlled | Curated packs with scripts and custom progression |

Existing manual locks continue to apply in every profile. Disabling these new automatic gates must not remove them. Native costs, unlocks, crafting restrictions, and fishing conditions apply in every profile.

Do not gate pickup, trading, storage, ordinary fish food, beginner bait, the fishing journal, cosmetic bobbers, or removal of already equipped items by default. A player must be able to put down unusable equipment and clear a fishing line.

### 4.2 Reference equipment requirements

Numbers below use a skill cap of 32 and apply only when automatic gates are enabled. They are **proposed class rules**, not a completed mapping of every registry item. The implementation agent must produce a reviewed per-item manifest covering the installed artifacts before enabling a family rule.

| Family/action | Suggested requirement |
|---|---|
| Simply Swords iron-equivalent light/medium attack | Strength 6 or Dexterity 6, selected by actual weapon family |
| Simply Swords diamond-equivalent attack | Governing combat skill 12 |
| Simply Swords netherite-equivalent attack | Governing combat skill 18 |
| Simply Swords unique relic attack | Governing combat skill 22; explicit exceptions for early relics |
| Runic gem benefit | Magic 12, plus the native socket/awakening gate |
| Nether gem benefit | Magic 20, plus the native socket/awakening gate |
| Native relic ability | Governing combat skill 20 and Magic 14; individual ability rules may replace the generated thresholds |
| Simply More mounted lance attack | Strength 12 and Endurance 8; native mounted predicate also required for mounted benefits |
| Simply More grandsword/great spear | Strength 14 and Endurance 8 |
| Simply More backhand blades/deer horns | Dexterity 10 |
| Simply More Mimicry ability/form | Dexterity 22 and Wisdom 16, plus native form availability and any destination-form rule |
| T.O. ordinary Aqua spell | Existing ISS spell-level gate; no second copy of that same gate |
| T.O. advanced unique spell | Curated Magic 24 and Wisdom 16, plus every native prerequisite |
| T.O. advanced weapon action | Strength or Dexterity 18 and Magic 16, selected by item/action |
| T.O. advanced armor activation | Constitution 18 and Magic 16; native armor/set predicate remains authoritative |
| Tide beginner rod/bait | No additional automatic integration requirement; existing explicit vanilla rod locks remain |
| Tide upgraded rod | Fortune 8/14/20 by verified equipment tier |
| Tide specialist line or functional hook | Dexterity 8/14/20 or Tinkering 8/14, according to function |
| Tide lava fishing setup | Endurance 12 and Fortune 12, in addition to native medium access |
| Tide void fishing setup | Endurance 20 and Fortune 20, in addition to native medium access |

Do not introduce a generic “all uniques require max level” rule. Inspect acquisition stage, native power, and ordinary-use usefulness. A powerful ability can require more training than carrying and making basic attacks with its weapon.

For new positive reference requirements, use `ScaledRequirement.forConfiguredCap`, whose existing conversion is `ceil(referenceLevel × skillCap / 32)`, clamped to the valid cap. Preserve nonpositive disabling values. Leave legacy raw-level configuration semantics unchanged. [ScaledRequirement][rs-scaled]

### 4.3 Rule resolution

Use this sequence:

1. Resolve the actual actor, hand, item state, action and integration capability.
2. Preserve any upstream refusal, existing explicit Runic lock, and applicable server script veto.
3. Resolve an explicit new pack rule first, then curated integration rules, then a conservative family fallback.
4. Combine simultaneous requirements for the same skill using the maximum, not their sum.
5. Recheck at the authoritative action boundary and report all unmet requirements through one bounded message.

A pack replacement rule may replace the new generated requirement; it does not override a native refusal or erase an older explicit lock. Exclusion tags suppress automatic integration rules, not unrelated server protections.

Profile inspection and tooltip generation must not awaken a weapon, roll an implicit, install a socket, initialize a spell container, consume bait, or create default state that changes the item.

### 4.4 Action lifecycle and attribution

Every eligible operation needs a server-owned context containing:

- Server-session action ID and root ID; actor UUID and dimension.
- Actual hand and a bounded item/family snapshot.
- Action kind: direct melee, native ability, native gem proc, manual spell, channel pulse, summon action, fishing cast, fishing result, or workshop commit.
- Native origin, native scaling already applied, and Runic secondary-origin marker.
- Configuration revision, applicable capability, and effect claims.
- Stable provenance for deferred projectiles, channels and returned/thrown weapons.

Only direct player actions qualify for direct-action bonuses. A minion’s hit, damage-over-time tick, reflected hit, repeated packet, and extra target in one sweep must not count as another player attack or cast.

Validate first; let upstream perform its operation; detect the actual relevant success; reserve and apply the Runic effect once; then announce the proc. Never use `PowerDispatch.fireProc` as a cancellation hook: it reports an already committed effect. [PowerDispatch][rs-power-dispatch]

Extend asynchronous attribution deliberately. A thread-local context does not survive to a projectile that lands thirty ticks later. Freeze origin/scaling data at emission; check current effect availability before a new Runic benefit. Preserve already committed native costs.

### 4.5 Shared Power progression and balance language

Runic Powers are equipped reactive abilities. Add no activation keybind for this catalog. The existing model has five Mark slots, three Seal slots, one Crown slot, and point costs 1/2/3. Current default governing-skill gates resolve to **13/21/29**; Seals also require **Intelligence 10** and Crowns **global level 180**. Use live configured percentages and the existing prerequisite chain rather than hardcoding these displayed values. [Power metadata][rs-power] · [Power tiers][rs-power-tier] · [Power eligibility][rs-power-eligibility]

New categories:

| Category | Contents | Prerequisite behavior |
|---|---|---|
| `runicskills:weapon_mastery` | Simply Swords and Simply More Powers | Shared category; More remains dependency-gated independently |
| `runicskills:aquamancy` | T.O. Powers in this catalog | Classification category; distinct from the native Aqua school registry key |
| `runicskills:angling` | Tide Powers | Works without Iron’s Spellbooks |

All new perk rows default to **one rank** and use existing perk purchase/activation budgets. No perk is free merely because its integration is installed. Further ranks require explicit bounded formulas.

In the tables, **Guard N** means N temporary damage-absorption points in a separate Runic guard pool, with a shared default cap of 4 points per recipient. Refresh to the greater remaining amount rather than adding repeatedly. Expiration clears only this pool; it never edits vanilla or another mod’s absorption amount. Combat benefits require a legitimate hostile interaction and respect teams, ownership, claims, and server PvP policy. Seconds are converted once to ticks.


## 5. Simply Swords integration

### 5.1 Native mechanics and hooks

The current source exposes `SimplySwordsAPI` entry points for swing processing, ability activation, delegated weapon hits, and ability magic damage. Those methods can perform real damage and callbacks. Observe their outcome; never invoke them again just to make a Runic listener recognize an action. Public methods are not evidence that a cancellable integration event exists. [SimplySwordsAPI][ss-api]

Use `AwakeningApi` for native level/unlock questions and `GemPowerComponent` for socket/power identity. Preserve `isAbilityUnlocked` and `areGemPowersActive`. The component is Simply Swords’ own abstraction on 1.20.1, not Minecraft 1.21’s component API. Use observational access such as `WeaponImplicitRegistry.peekWeaponImplicit` in tooltips; initialization helpers can alter a stack. [Awakening API][ss-awakening] · [Gem component][ss-gems] · [Implicit registry][ss-implicit]

| Concern | Source seam to inspect against the actual jar | Integration contract |
|---|---|---|
| Rebound ability input | `PlayerWeaponAbilityManager.handleInput/start` | Gate the server action even when vanilla right-click was not used |
| Channels | `PlayerWeaponAbilityChannelManager.tickPlayer/finish` | Stop Runic eligibility when hand/item changes; preserve native cleanup and already paid costs |
| Actual attack hand | `BetterCombatServerNetworkMixin`, `BetterCombatCompat` | Attribute the real offhand; do not install a second native swing dispatch |
| Magic scaling | `ForgeHelperMethods` and `HelperMethods.abilityScaledDamage` | Preserve native scaling and annotate it before generic Runic damage handling |
| Workshop | `RunicForgeScreenHandler` load, preview, commit, quick-move and close paths | Require a committed state change and exact resource accounting |
| Native gems | Component dispatch and awakening checks | A socketed gem must not fire through a Runic bypass while native progression disables it |

Sources: [Ability manager][ss-ability-manager] · [Channel manager][ss-channel-manager] · [Better Combat seam][ss-bettercombat] · [Forge helpers][ss-forge-helper] · [Damage helper][ss-helper] · [Runic Forge][ss-forge]

Simply Swords already has a native ISS bridge covering spell power, school scaling, resistance, mana and cooldown reduction. Mark these ability hits as already processed by that bridge. Do not run another global/school/awakening multiplier over them merely because the owner is a player holding a sword. New Runic conditional bonuses must be their own documented bounded contribution.

Native implicits already cover many classic weapon effects. Prioritize handling, recovery, information, and cadence in the new catalog rather than duplicating every bleed, execute, haste, or armor-piercing mechanic. [Implicit registry][ss-implicit]

### 5.2 Custom perks: eight proposals

All IDs below are in `runicskills:`. “Native success” requires a verified outcome, not a keypress, callback entry, or damage attempt.

| ID / name | Skill / reference level | Proposed behavior | Limits and hook requirements |
|---|---|---|---|
| `ss_measured_steel` — Measured Steel | Strength 8 | A charged direct hit with a heavy weapon adds 5% to that primary physical hit | Charge ≥90%; once per attack root; no ability, implicit, projectile or sweep-child replication; shared damage cap |
| `ss_patient_temper` — Patient Temper | Tinkering 10 | 10% chance to prevent one point of ordinary weapon wear | Actual wear seam; no ability-payment durability, passive repair, or workshop costs; shared wear budget |
| `ss_resonant_reading` — Resonant Reading | Wisdom 12 | Expanded tooltip explains native awakening, occupied sockets, active powers and unmet Runic requirements | Read-only; show unknown facts as unavailable; never initialize a component or roll an implicit |
| `ss_gemguard` — Gemguard | Constitution 16 | A successful native gem effect grants Guard 1 for 3 seconds | One claim per native activation; 8-second cooldown; gem must be natively active |
| `ss_spellsteel_discipline` — Spellsteel Discipline | Magic 18 | After a paid native weapon ability, the next direct hit within 5 seconds grants 10% movement speed for 2 seconds | One pending charge; 10-second cooldown; no free-cast, passive tick, or reflected-damage trigger |
| `ss_returning_grip` — Returning Grip | Dexterity 20 | A genuinely returned thrown weapon that hit an eligible target grants 15% knockback resistance for 2 seconds | Requires return/owner provenance; pickups of dropped weapons do not qualify; 8-second cooldown |
| `ss_awake_and_ready` — Awake and Ready | Endurance 22 | Completing an unlocked awakening-dependent ability grants 10% movement speed for 3 seconds | Native unlock required; once per completed activation; 12-second cooldown; no native level gains |
| `ss_relic_care` — Relic Care | Tinkering 24 | A supported manual repair of a unique weapon restores up to 10% additional durability | Floor additional points; never exceed max durability; no additional material, gem, tablet, XP or output; unsupported repair routes remain unchanged |

### 5.3 Custom Powers: six proposals

All six use `runicskills:weapon_mastery`. Required levels come from the existing tier resolver.

| ID / name | Tier / skill | Trigger and effect | Cooldown / state |
|---|---|---|---|
| `ss_mark_of_the_draw` — Mark of the Draw | Mark / Strength | First charged direct hit after 8 seconds without outgoing combat damage grants Guard 2 for 3 seconds | 12 seconds; one attack claim |
| `ss_returning_steel` — Returning Steel | Mark / Dexterity | Successful owner return arms the next direct hit within 5 seconds to apply 10% Slowness for 2 seconds | 12 seconds; one charge; respect target immunity |
| `ss_resonant_breath` — Resonant Breath | Mark / Magic | A successful native gem effect arms one subsequent manual weapon ability to grant Guard 2 for 3 seconds on completion | 15 seconds; arm expires after 8 seconds; no additional gem execution |
| `ss_seal_of_the_interval` — Seal of the Interval | Seal / Dexterity | Complete a paid native ability and then a charged direct hit within 6 seconds to gain 15% movement speed for 3 seconds | 20 seconds; ordered distinct actions |
| `ss_seal_of_gemguard` — Seal of Gemguard | Seal / Constitution | A valid native gem proc after taking hostile damage within 4 seconds grants Guard 3 for 4 seconds | 25 seconds; damage must actually have landed |
| `ss_awakened_arsenal` — Crown of the Awakened Arsenal | Crown / Wisdom | Complete direct hit → unlocked native ability → direct hit within 12 seconds; gain Guard 4 and 15% knockback resistance for 6 seconds | 60 seconds; one real weapon family; no forced awakening, cooldown reset or repeated native attack |

Returning effects require a version-specific return capability. If the installed version lacks a trustworthy return seam, those entries stay unavailable rather than guessing from the player’s inventory.

### 5.4 Forge and socket integrity

Previewing, inserting, extracting, closing, or reopening the native forge is not automatically a successful upgrade. The inspected handler can extract contents during insertion and rebuild state on removal. Capture input state, committed result, materials actually consumed, returned components, and operation identity. [Runic Forge][ss-forge]

Preserve every upstream component and unknown NBT field. No new default perk grants extra sockets, rerolls implicits, duplicates gems, refunds rare tablets, or increases awakening levels. A future socket feature would need a separate transaction design and evidence that the installed upstream pair supports it.

## 6. Simply More integration

### 6.1 Preserve its specialized weapon identities

Simply More’s published weapon families include lances, grandswords, great katanas, backhand blades, khopeshs, daggers, pernachs, quarterstaffs, great spears and deer horns. Its mounted lance and shield-disabling grandsword mechanics provide distinct opportunities. Use its actual family tags instead of substring matching. [Project description][sm-project] · [Tag registry][sm-tags]

Key source findings:

- Native lance benefits require a living mount and an offhand predicate involving attack-damage modifiers. “Offhand empty” is not the exact rule. Reuse the native predicate or a verified equivalent. [Lance item][sm-lance] · [Lance effect][sm-lance-effect]
- Shield disable must be observed as an actual native outcome. Ordinary damage against a shield user is insufficient. Avoid stacking an additional general bleed/healing penalty over the existing behavior. [Living entity mixin][sm-living]
- Mimicry changes form through multiple paths, including inventory interaction, and can replace the stack while copying state. Maintain one family identity and Runic cooldown across forms; recalculate action requirements for the actual result. Manual form cycling does not count as a combat success. [Mimicry][sm-mimicry]
- The inspected carver mixin still checks legacy `runic_power`/`nether_power` strings and writes `socket_empty`. That makes the Simply More 1.1.4 × Simply Swords 1.70.2 socket operation a specific required test. It is not sufficient evidence to call the shipped combination broken. [Carver mixin][sm-carver]

### 6.2 Custom perks: eight proposals

| ID / name | Skill / reference level | Proposed behavior | Limits and hook requirements |
|---|---|---|---|
| `sm_couched_discipline` — Couched Discipline | Strength 10 | A qualifying mounted lance hit after 6 blocks of real forward travel grants 15% knockback resistance for 3 seconds | Native mount/offhand predicate; no additional mounted damage multiplier; 8-second cooldown |
| `sm_saddleward` — Saddleward | Constitution 14 | A qualifying mounted lance hit grants the ridden living mount Guard 2 for 4 seconds | Only the ridden, player-authorized mount; one mount per player; 12-second cooldown |
| `sm_breach_reader` — Breach Reader | Wisdom 14 | After a native grandsword shield disable, the next direct hit within 5 seconds grants Guard 2 for 3 seconds | 12 seconds; does not extend the native shield disable |
| `sm_long_measure` — Long Measure | Dexterity 12 | A great-spear or great-katana hit in the outer quarter of verified legal reach grants 10% movement speed for 2 seconds | 6-second cooldown; never extend reach; disable distance-sensitive bonus if the combat adapter cannot resolve reach |
| `sm_countergrip` — Countergrip | Dexterity 18 | A backhand-blade/deer-horn counter-hit within 3 seconds of surviving hostile melee damage grants Guard 2 for 3 seconds | 12 seconds; self, reflected and friendly damage excluded |
| `sm_measured_blows` — Measured Blows | Tinkering 16 | 10% chance to spare one point of ordinary pernach/quarterstaff wear on a charged direct hit | Shared wear accounting; no separate repair loop |
| `sm_many_forms_one_hand` — Many Forms, One Hand | Wisdom 22 | First direct hit after an automatic combat-driven Mimicry transition grants 12% movement speed for 3 seconds | 10 seconds across the whole family; manual inventory transformations excluded |
| `sm_relic_care` — Relic Care: More | Tinkering 24 | Supported manual repairs gain up to 10% additional restored durability | Same conservation rules as Simply Swords Relic Care; one repair-efficiency owner per stack, so superclass overlap cannot pay twice |

Mounted travel is accumulated from server movement while riding the same living mount. Teleports, dimension changes and mount changes reset it. Rearm only after genuine separation; circling inside a target’s hitbox must not repeatedly count as a charge.

### 6.3 Custom Powers: six proposals

These share `runicskills:weapon_mastery` with Simply Swords, while requiring Simply More’s specific capabilities.

| ID / name | Tier / skill | Trigger and effect | Cooldown / state |
|---|---|---|---|
| `sm_first_pass` — First Pass | Mark / Endurance | A valid mounted lance charge hit grants 15% movement speed to the mount for 3 seconds | 15 seconds; one charge; clear on dismount |
| `sm_broken_guard` — Broken Guard | Mark / Strength | A confirmed grandsword shield disable grants Guard 2 for 4 seconds | 15 seconds; native result only |
| `sm_measured_reach` — Measured Reach | Mark / Dexterity | Two distinct successful great-spear or quarterstaff attacks at legal outer reach within 6 seconds grant 15% knockback resistance for 3 seconds | 12 seconds; no sweep-child counting |
| `sm_reversal` — Reversal of Fortune | Seal / Dexterity | Survive hostile melee damage, then counter-hit with backhand blades or deer horns within 3 seconds; gain Guard 3 for 4 seconds | 25 seconds; one pending attacker record |
| `sm_hold_the_breach` — Hold the Breach | Seal / Constitution | A confirmed grandsword shield disable grants Guard 2 for 4 seconds to the user and up to two eligible allies within 4 blocks | 30 seconds; no NPCs by default; team/ownership policy; never damage the area |
| `sm_changing_arsenal` — The Changing Arsenal | Crown / Wisdom | Land direct attacks in three distinct natively available Mimicry forms within 20 seconds; gain Guard 4 and 15% movement speed for 6 seconds | 60 seconds; family-wide cooldown; no forced forms or native cooldown resets |

### 6.4 Mimicry continuity contract

Track actor, family identity, previous/current form, transition cause, and associated root action. Preserve a Runic-owned identity only through verified native replacement operations. Do not copy arbitrary player state between unrelated items or issue free new identities whenever an item enters a slot.

Gate a requested destination form before native transformation if a supported veto exists. Otherwise gate its subsequent use and clearly report that transition-level gating is unavailable. Prevent attacks, channels and Powers from borrowing the previous form’s lower requirement. Keep safe removal possible.

Inventory clicks, creative copies, dropped-item pickup, save/load and manual form rotation must not manufacture adaptation progress. Existing native copied-NBT behavior is not sufficient evidence that every new Runic cooldown field will survive; test it.


## 7. T.O. Magic ’n Extras integration

### 7.1 Packaging and evidence boundary

The user-confirmed project is **T.O. Magic ’n Extras**, not a separate mod named “T.O Magic.” Its official page describes Aqua magic, evolving equipment, talent curios, native equipment actions and special acquisition restrictions. It also identifies the project as closed source and directs addons to its internal API while requiring T.O. as a dependency. [Official project and addon terms][tom-project]

**Recommended packaging:** an optional `runicskills-tom-compat` companion jar, built in the same repository, declaring Runic Skills and `traveloptics` as required dependencies. Runic core remains independently usable. Put all T.O.-specific imports in that companion and keep its content metadata and shared contracts consistent with core. Use original Runic assets and implementation; do not copy T.O. code or redistribute its jar.

Research inspected the official 6.3.0 artifact’s manifest and public class signatures, not private implementation source. Artifact SHA-256:

`878a0fb5a2057de530698fd63091b9fa1c23af0139054af108e9d566840a0fa9`

The following public members were verified in that binary. [Official artifact][tom-release]

| Public API class, under `com.gametechbc.traveloptics` | Verified useful surface | What it establishes |
|---|---|---|
| `api.init.TravelopticsSchools` | `AQUA_RESOURCE`; `AQUA` as a `RegistryObject<SchoolType>` | Resolve the native school with the public constant or `AQUA.getId()`; no guessed namespace |
| `api.init.TravelopticsAttributes` | `AQUA_SPELL_POWER`, `AQUA_MAGIC_RESIST` | Native attributes are available; verify their numerical conventions before applying modifiers |
| `api.item.armor.IKeybindArmor` | `onKeyPacket(Player, ItemStack, int)` | Native armor activation is a separate action path |
| `api.item.weapons.IWeaponAbility` | `use`, `hurtEnemy`, `releaseUsing`, `onUseTick` | Several weapon phases exist; none alone proves an all-actions veto |
| `api.item.weapons.WeaponAbilityType` | `LEVEL_0` through `LEVEL_3` | Ability tiers exist; this does not establish a writable progression API |
| `api.item.weapons.MagicMultiSpellItem` and `api.item.GeoMagicSwordItem` | `getSpells()`, `initializeSpellContainer(ItemStack)` | Spell-bearing equipment exists; initialization must never be a tooltip side effect |
| `api.spells.AbstractUniqueSpell` and `AbstractWeaponSpell` | `allowLooting()`, `allowCrafting()` | Native acquisition restrictions need preservation |

A callable interface is not a cancellation event. No documented universal preactivation veto, paid native-activation completion event, or native level/charge mutation contract was established. Therefore **complete keybind/weapon-ability gating is an implementation acceptance requirement, not something this report certifies**. Obtain the supported upstream extension path before implementing those features. Generic ISS precast coverage must not be advertised as complete armor-keybind coverage.

### 7.2 Spell and equipment integration

1. Register an Aqua descriptor using the public school key. Suggested secondary Runic skill: **Endurance**. Keep its coefficient separately configurable and avoid duplicating existing generic Magic/Wisdom scaling.
2. Use the existing ISS precast pipeline for ordinary T.O. spells that actually pass through it. Add curated per-spell requirements and explicit source categories.
3. Record cast initiation, native permission, effective level, mana charged, completion, cancellation, and emitted objects. One recast sequence has a root cast identity with bounded child phases.
4. Whitelist normal paid casts for new cost reductions. Default-exclude free casts, item/armor procs, mana-generating spells, intentional scroll-only boss summons, weapon-bound special spells, and unclassified origins.
5. Keep native crafting/looting flags, required weapons, boss acquisition, cooldowns, weapon tiers and talent-slot rules.
6. Classify normal melee, weapon abilities, armor keybinds, talent effects, spells and summons separately. Never infer a summon’s owner from proximity.
7. Verify the ordering of native resistance, armor effects and Runic modifiers with the installed dependency set. The 6.3.0 release specifically changes summon behavior and contains a deliberate high-damage counterspell challenge; this is a reason to test encounter mechanics, not to add automatic encounter bypasses. [Release notes][tom-release]

For a discount, use one aggregate modifier stage. The new T.O. contribution may reduce an eligible final paid cost by at most 10% by default, with a floor of 1 mana. Existing intentional free casts stay free but receive no discount-triggered reward. This new budget does not retroactively rewrite the player’s established generic perks.

Do not add a mana-refund Power in this update. Prevent a weapon-generated cast and its parent activation from each arming the same rotation benefit.

### 7.3 Custom perks: eight proposals

| ID / name | Skill / reference level | Proposed behavior | Limits and hook requirements |
|---|---|---|---|
| `tom_aqua_attunement` — Aqua Attunement | Magic 8 | A small native Aqua spell-power modifier, initially targeting a 5% relative increase | One native attribute modifier; confirm native units; do not also add a matching damage-event multiplier |
| `tom_measured_current` — Measured Current | Endurance 10 | Completing a normal paid Aqua cast grants 10% movement speed for 3 seconds | Once per cast root; 8-second cooldown; no channel-pulse or free-proc triggers |
| `tom_changing_tides` — Changing Tides | Wisdom 14 | Complete Aqua and then a different manual spell school within 8 seconds; the next eligible Aqua cast costs 5% less | One charge for 8 seconds; 15-second cooldown; safe paid-cast allowlist and aggregate discount cap |
| `tom_bound_companion` — Bound Companion | Constitution 16 | A paid summon cast grants Guard 2 for 8 seconds to one newly created owner-verified summon | One recipient per root cast; no duplication across summon swarms; no target/owner rewriting |
| `tom_relic_discipline` — Relic Discipline | Strength 18 | A direct hit with a natively advanced T.O. weapon grants 15% knockback resistance for 3 seconds | Verified native tier read; 10-second cooldown; does not grant a tier or multiply ability damage |
| `tom_talent_composure` — Talent Composure | Intelligence 18 | Completing a normal paid T.O. cast while a valid talent is legally equipped grants Guard 1 for 3 seconds | 10-second cooldown; Curios/native slot validity; no extra slots or duplicated talent application |
| `tom_pressure_reader` — Pressure Reader | Wisdom 22 | A successful player-performed native counterspell grants Guard 3 for 4 seconds | 25-second cooldown; unavailable until a trustworthy success hook is established; never auto-counterspell |
| `tom_artificers_poise` — Artificer’s Poise | Tinkering 24 | A completed paid native armor activation prepares the next eligible Aqua cast within 6 seconds to grant 15% knockback resistance for 3 seconds | 20-second cooldown; requires verified activation commit; does not alter native armor state or costs |

Do not enable Relic Discipline, Pressure Reader or Artificer’s Poise merely because their display names and metadata exist. Their native capabilities must be proven. The first release can expose their planned status in developer diagnostics, but players must not pay for them until they work.

### 7.4 Custom Powers: six proposals

All use `runicskills:aquamancy`.

| ID / name | Tier / skill | Trigger and effect | Cooldown / state |
|---|---|---|---|
| `tom_undertow` — Undertow | Mark / Magic | First eligible Aqua hit marks one hostile target for 5 seconds; a subsequent direct melee hit applies 10% Slowness for 2 seconds | 12 seconds; one target record; no extra damage instance |
| `tom_sheltering_current` — Sheltering Current | Mark / Endurance | Complete a normal paid Aqua cast after taking hostile damage within 4 seconds; gain Guard 2 for 4 seconds | 15 seconds; no native damage-cap changes |
| `tom_companions_wake` — Companion’s Wake | Mark / Constitution | One newly summoned, verified owned ally from a paid cast gains 15% knockback resistance for 6 seconds | 15 seconds; once per cast; no free resummoning |
| `tom_stillwater` — Stillwater | Seal / Wisdom | Complete two distinct eligible Aqua spell IDs within 10 seconds; gain Guard 3 and 10% movement speed for 4 seconds | 25 seconds; channel/recast repetitions do not satisfy “distinct” |
| `tom_artificers_accord` — Artificer’s Accord | Seal / Tinkering | Complete a paid native equipment activation, then a normal paid Aqua cast within 6 seconds; gain Guard 3 for 5 seconds | 30 seconds; unavailable without the native activation-commit capability |
| `tom_confluence` — Confluence | Crown / Magic | Complete Aqua → another school → a third distinct spell ID within 15 seconds; next eligible Aqua cast within 8 seconds gains a 10% primary damage budget | 60 seconds; total added damage ≤4 health points per cast; shared across all targets/pulses; no additional projectile or repeated spell |

Confluence’s budget is applied to the native cast’s primary damage path once. A 40-projectile cast does not receive forty separate four-point bonuses. Utility and excluded spells do not consume a damage charge; it expires normally and cannot be transferred to a native equipment proc.

## 8. Tide integration

### 8.1 Build on Tide 2’s actual fishing lifecycle

Tide 2 supplies charged casting, customizable rods, functional hooks and lines, cosmetic bobbers, bait, a fishing minigame, a journal and data-driven fish conditions. Its current code is in `Lightning-64/Tide-2`. [Current repository][tide-repo]

The important technical boundary is the catch transaction:

| Stage | Verified source behavior | Runic requirement |
|---|---|---|
| Cast | `TideFishingRodItem.castHook`; rod state combines native equipment/bait data | Capture the actual hand, rod and attachment state; authorize before creating the cast |
| Hook identity | `TideFishingHook` extends Projectile; `HookAccessor` wraps it for vanilla fishing state | Use the real Tide hook UUID, not a drop namespace |
| Minigame | `FishCatchMinigame.onWin` retrieves; failure invalidates the catch | A callback or nonzero wear return alone is not a successful fish |
| Forge fishing event | Copied output stacks are posted before catch delivery, XP, bait and journal work; cancellation and rod-wear edits are not consumed | Treat this event as observation, never as the new gate or reward transaction |
| Delivery | Fish/item, crate and pulled-entity paths differ | Classify outcome explicitly; preserve Fishing Real and other native handoffs |
| Journal | Catch logging and record fields have their own conditions | Use unlocked canonical species, not raw map size or optional size statistics |

Sources: [Rod][tide-rod] · [Hook accessor][tide-accessor] · [Catch implementation][tide-hook] · [Minigame][tide-minigame] · [Player data][tide-player-data] · [Fish statistics][tide-fish-stats]

Ordinary catches already award vanilla XP; the perfect-catch and diamond-rod paths can add more. This integration adds **zero bonus catch XP by default**. Do not attach another reward loop to the generic event. [Catch implementation][tide-hook]

### 8.2 Implement a native catch bridge

Create proposed Runic records `TideCastContext` and `TideCatchCommitted`. Neither is an existing upstream event.

A cast context includes actor, dimension, real hook UUID, cast generation, rod/hand, attachment snapshot, medium, provider, configuration revision and single-use result status. A committed result distinguishes:

- Fish, including a canonical species ID and optional exact variant.
- Other item.
- Crate.
- Pulled world entity.
- Failure, timeout, empty retrieve, canceled/ineligible cleanup.
- A valid native external delivery/conversion handoff.

A successful native commit may contain several output stacks; it generates one cast-level Runic event. Do not reroll the catch, manually log a second journal entry, or spawn another copy of its native output. A successful Fishing Real handoff may produce no ordinary item entity, so “an item entity appeared” is not a universal commit test.

**Safe cleanup:** always allow the player to clear their line. If equipment, permissions or requirements become invalid after casting, invalidate the pending catch and perform native cleanup without creating a new Runic reward. Do not strand the bobber by simply canceling every reel action.

Route every proven Tide-origin Forge fishing event away from Runic’s generic reward handler. For ambiguous provenance, grant no new integration benefit. Resolve the actor/real hook identity before inspecting output IDs; vanilla fish and other mods’ fish can come through Tide.

### 8.3 Minigame and third-party boundaries

The Tide release notes mention Starcatcher compatibility. However, the inspected `CompatHelper` contains NeoForge-only implementations of its Starcatcher start/complete bridge; the Forge branch returns false. The source therefore does not establish Forge Starcatcher minigame support. Test the shipped Forge hotfix and record what provider actually runs. Do not force-enable the missing provider path. [Release notes][tide-release] · [Compatibility helper][tide-compat]

Tide’s own minigame and a third-party minigame are distinct assistance capabilities. An unsupported provider may still permit verified catch-level benefits, but it gets no timing/window modifications. Preserve upstream `doMinigame`, third-party provider settings and fish-data settings; never rewrite Tide’s config. [Server config][tide-config]

The inspected server message passes a result byte to `FishCatchMinigame.handleClientEvent`; it does not establish server replay of timing input. Bind Runic effects to a live server cast, owner, hook and single-use result. Reject duplicate/stale Runic claims. Do not add high-value perfect-only rewards or claim this makes the native minigame cheat-proof. [Message handler][tide-message] · [Minigame][tide-minigame]

### 8.4 Ecology, loot and journal rules

Native fish predicates remain authoritative. Restrict any new weighting to the already eligible fish set. Preserve season, biome, dimension, medium, weather, time, depth and other upstream conditions; exclude nonpositive-weight entries. Never let an Aqua spell, a Luck buff, or a Runic skill create lava/void fishing access. [Fish data][tide-fish-data] · [Fishing context][tide-context] · [Selector][tide-selector]

Tide already includes player Luck in its selection context. Its fish-category weighting is not equivalent to a simple fish-rarity bonus. Add **no new global Luck modifier by default**. If a pack enables species weighting, apply it within the eligible fish pool without changing the fish/item/crate category roll. [Fish selector][tide-fish-selector] · [Entry weighting][tide-entry]

Journal note-only records do not count as caught species. Canonicalize parent/variant relationships for collection milestones, and preserve exact variants for size or display where appropriate. Optional size recording must not decide whether a catch happened. Existing entries can establish display progress, but loading them must not pay new one-time rewards. [Player data][tide-player-data] · [Fish statistics][tide-fish-stats]

### 8.5 Custom perks: eight proposals

| ID / name | Skill / reference level | Proposed behavior | Limits and hook requirements |
|---|---|---|---|
| `tide_measured_cast` — Measured Cast | Dexterity 6 | Reduce cast preparation duration by 10% | Native charge-duration seam; final duration ≥6 ticks; no bite-time or instant-catch effect |
| `tide_patient_hands` — Patient Hands | Tinkering 8 | 10% chance to spare one point of wear on a successful fish retrieval | Commit-specific wear accounting; pulled entities and failed retrieves excluded |
| `tide_read_the_water` — Read the Water | Wisdom 10 | With the journal or Fish Finder available, explain failed native habitat conditions for a selected known fish | No undiscovered names, global scans, or condition bypass |
| `tide_sure_line` — Sure Line | Dexterity 14 | Add 0.02 of normalized track width to Tide’s normal success window | Do not expand perfect window; shared Runic window cap 0.06; provider-specific capability |
| `tide_baitkeeper` — Baitkeeper | Fortune 16 | 10% chance to preserve one eligible bait unit during a successful fish cast | One occupied slot/unit per cast; intercept native consumption; do not refund a copied stack |
| `tide_many_waters` — Many Waters | Endurance 18 | First successful catch in a new biome family during a 10-minute session prepares the next cast 10% faster | One pending charge; four habitat rewards per session; boundary oscillation cannot rearm |
| `tide_field_naturalist` — Field Naturalist | Wisdom 22 | At 10/25/50 known caught species, unlock richer journal filters and comparisons of known habitat requirements | Information benefit; no bonus fish, native unlock writes or skill XP |
| `tide_careful_landing` — Careful Landing | Endurance 24 | A successful legal lava/void fish catch gets a further 10% chance to spare one ordinary wear point | Shared wear cap; no medium access, fire immunity or void immunity |

Baitkeeper must account for the upstream multi-output consumption loop. It can prevent at most one actual unit from being consumed once per cast, even with several bait slots and outputs. If the specific consumption seam cannot enforce conservation, leave it unavailable with that reason. [Catch implementation][tide-hook] · [Bait utilities][tide-bait]

### 8.6 Custom Powers: six proposals

All use `runicskills:angling`. Cast-level preparation bonuses affect the next cast; a benefit earned on one successful catch cannot retroactively change that result.

| ID / name | Tier / skill | Trigger and effect | Cooldown / state |
|---|---|---|---|
| `tide_stillwater_oath` — The Stillwater Oath | Mark / Dexterity | A successful fish catch prepares the next cast 15% faster | 20 seconds; one charge lasting 60 seconds; shared preparation cap |
| `tide_unbroken_thread` — Unbroken Thread | Mark / Tinkering | Two successful fish catches with distinct cast IDs arm the next successful fish retrieval with a 20% chance to spare one wear point | 30 seconds; charge lasts 90 seconds; shared wear cap |
| `tide_anglers_almanac` — The Angler’s Almanac | Mark / Wisdom | Three distinct caught species within 5 minutes enable 60 seconds of expanded habitat comparisons for already known fish | 90 seconds; uses the native journal/Finder; no secret discovery |
| `tide_favor_from_the_deep` — A Favor from the Deep | Seal / Fortune | Five distinct species caught within 10 minutes prepare one fish roll with ×1.10 weight for one journal-selected, already caught species that is currently eligible | 120 seconds; one cast; category roll unchanged; optional weighting capability |
| `tide_keeper_of_the_banks` — Keeper of the Banks | Seal / Wisdom | Catch three distinct species across two biome families within 5 minutes; arm one 20% bait-preservation roll and one cast-preparation benefit of 15% | 120 seconds; charges expire after 90 seconds; maximum one conserved bait unit |
| `tide_between_ember_and_star` — Between Ember and Star | Crown / Endurance | Successful fish catches in two distinct native mediums within 10 minutes arm the next two legal casts with 20% faster preparation and +0.02 normal success-window width | 180 seconds; charges last 120 seconds; native access required; no forced species, medium or perfect result |

The Crown requires both preparation and the active provider’s window-assistance capability. If the provider cannot support the complete effect, the Power is unavailable; do not silently sell a reduced version. A pack can intentionally disable the weighting Seal without disabling all other angling content.


## 9. Cross-mod behavior and balance

### 9.1 Integration ownership

Assign one owner to each native action. Simply More owns its items even when they inherit Simply Swords classes. Simply Swords may supply the shared native mechanism without creating a second Runic action. T.O. weapon-generated spells keep their parent activation provenance. Tide owns catches from its real hook, including outputs with another namespace.

Keep this separate from **effect eligibility**. A single owned action can legitimately satisfy several equipped perks, but each effect has one claim and all applicable effects share the configured budgets.

### 9.2 Proposed global bounds for this catalog

| Quantity | Initial default |
|---|---|
| Additional damage from new integration modifiers | At most +10% to the applicable primary damage; stricter individual caps also apply |
| Confluence added damage | At most 4 health points over the entire originating cast |
| New movement-speed bonuses | Strongest applicable contribution, capped at +20%; no multiplication of several new buffs |
| New knockback resistance | Strongest applicable contribution, capped at +0.20 in the native 0–1 unit |
| Runic Guard | At most 4 points per recipient from this catalog |
| New slow | One owned -10% movement contribution; no stacking; respect native immunity |
| Additional ordinary wear avoidance from this catalog | One aggregated trial, capped at 30% before combining with existing avoidance |
| Combined Runic ordinary wear avoidance | Preserve an overall maximum of 90%; ability-payment wear excluded |
| Additional repair efficiency from these integrations | Maximum 10%; one owner per repair; no output/resource duplication |
| New T.O. mana discount | Aggregate maximum 10%, eligible paid casts only, paid-cost floor 1 |
| Tide preparation reduction | Maximum 25% from this catalog; native resulting charge duration never below 6 ticks |
| Tide normal success window | Added width ≤0.06 of normalized track; final total width ≤0.85; native perfect window unchanged |
| Tide bait conservation | Probability ≤25%; no more than one actually consumed unit conserved per cast |
| Tide species weighting | Multiplier ≤1.15; already eligible fish only; category chances unchanged |
| New catch/activation/forge XP | Zero by default |

These bounds apply to new contributions; do not silently rebalance unrelated existing perks or overwrite native modifiers. In the catalogs, 15% knockback resistance means an additive +0.15 in the native 0–1 unit, subject to the shared ceiling and the attribute’s valid range. If a native window already exceeds the proposed final width ceiling, add zero rather than shrink the upstream result. Native “always succeed” configuration, if deliberately set by the pack, remains the pack’s choice.

Implement Guard as an owned damage budget, with a visible remaining amount and expiry. At a verified server damage boundary, consume at most the eligible positive damage and the pool’s remaining balance, once per incoming damage action. Cancelled or fully blocked hits consume nothing. Preserve native damage-bypass rules and verify ordering with armor, shields and external absorption. If that interaction cannot be established for a supported profile, keep Guard-dependent entries unavailable until it is resolved; do not silently substitute a different effect.

### 9.3 Optional complementary features

These are small follow-on features, not extra free combat perks:

| Combination | Proposed feature | Boundary |
|---|---|---|
| T.O. + Tide | Aqua Field Notes: known-fish habitat view distinguishes an actual environmental condition from a combat Wet effect | Read native fishing conditions; no weather changes, free access, or fish generation |
| Simply Swords/More + T.O. | Unified weapon tooltip shows ordinary attack, native ability and embedded spell requirements separately | One display of each actual modifier/cost; no duplicate school scaling |
| Simply Swords/More + Tinkers integration | Shared workshop explanation and repair provenance | Do not make non-Tinkers weapons accept Tinkers modifiers, slots or materials |
| Tide + Starcatcher/Stardew Fishing | Provider badge and one catch-origin diagnostic | Only advertise the provider supported by the selected loader and tested artifact |
| All four + existing quest bridge | Optional milestones for a real awakened-ability sequence, mounted pass, spell rotation or distinct catch collection | Emit one committed Runic milestone; keep quest task state and rewards owned by the quest system |

Do not expand this update into unrelated content integrations simply because those mods occur in a dependency list.

## 10. Configuration, pack rules and diagnostics

### 10.1 Configuration model

The following is a **proposed new schema**. It is not a configuration block accepted by the current release.

Use one versioned integration settings document, for example `config/RunicSkills/runicskills.integrations.json5`, integrated with the existing load/reload and server snapshot mechanism. Alternatively retain established flat-field conventions if that is necessary for compatibility; preserve the same semantics and document aliases. The implementation must not create a second unsynchronized source of gameplay truth.

```json
{
  "schema_version": 1,
  "profile": "compatibility",
  "modules": {
    "simply_swords": {
      "mode": "auto",
      "automatic_equipment_gates": false,
      "native_ability_gates": false,
      "perks": true,
      "powers": true,
      "workshop_features": true
    },
    "simply_more": {
      "mode": "auto",
      "automatic_equipment_gates": false,
      "native_ability_gates": false,
      "perks": true,
      "powers": true,
      "mimicry_features": true
    },
    "tom": {
      "mode": "auto",
      "automatic_equipment_gates": false,
      "native_ability_gates": false,
      "perks": true,
      "powers": true,
      "aqua_mapping": true,
      "native_activation_features": true
    },
    "tide": {
      "mode": "auto",
      "automatic_equipment_gates": false,
      "perks": true,
      "powers": true,
      "journal_features": true,
      "minigame_assistance": true,
      "eligible_species_weighting": true,
      "extra_catch_xp": 0
    }
  },
  "limits": {
    "guard_points": 4,
    "new_movement_bonus": 0.20,
    "new_damage_bonus": 0.10,
    "tide_preparation_reduction": 0.25,
    "tide_success_window_added": 0.06,
    "tide_bait_conservation_chance": 0.25,
    "tide_bait_units_conserved_per_cast": 1
  }
}
```

“True” requests a feature when its capabilities are verified; it cannot force-load a missing adapter. `mode` accepts `off`, `auto`, and `observe`. Observe mode generates diagnostics without applying new gates or benefits. Changing launch-time adapter selection requires restart; safe gameplay tunables may reload.

Also expose:

- Per-entry enablement, required skill/reference level, magnitude, probability, duration, cooldown and prerequisite override.
- Namespaced item/tag/spell/ability allowlists and exclusions, including cost-reduction exclusions.
- Boss/player target policies, team/claim checks, environmental immunity tags and FakePlayer policy.
- Hidden-versus-visible unavailable content, detailed tooltip mode and reduced-motion/VFX settings.
- Native minigame provider policy, journal spoiler policy, cast expiry, catch ledger bounds and species normalization.
- Debug logging levels, adapter report export and unknown-registry-entry reporting.

Every number must reject NaN, infinity and overflow, clamp to a declared range, and have a documented unit. A zero cooldown or disabled gate must have one explicit meaning throughout configuration, tooltips and code.

### 10.2 Proposed datapack rule example

Resource location: `data/<namespace>/runicskills/integrations/*.json`. Register a reload listener for that directory and validate the whole candidate rule index before replacing the active one.

```json
{
  "schema_version": 1,
  "module": "simply_swords",
  "rules": [
    {
      "id": "runicskills:ss_heavy_relic_attack",
      "priority": 100,
      "match": {
        "item_tag": "runicskills:integration/simply_swords/heavy_relics"
      },
      "actions": ["attack"],
      "requirements": {
        "strength": {
          "reference_level": 22,
          "scale": "skill_cap_32"
        }
      }
    }
  ]
}
```

This tag and rule are **new Runic content** to author. Populate the tag from inspected registry entries. Do not ship a fabricated list of upstream item IDs.

Resolution must be deterministic: explicit replacement rule, then highest priority, then deterministic resource-ID ordering; conflicting same-priority rules produce a diagnostic. Match maps use registered IDs and optional tags, not display names. Unknown IDs never silently resolve to air. A required unresolved match invalidates that rule; a declared optional absent dependency leaves it dormant.

Separate reads of known state from writes. Store only Runic-owned data under a namespaced key; never convert native Simply Swords gem data, Mimicry forms, T.O. progression or Tide rod data into an invented replacement format.

### 10.3 Hook manifest and commands

For every native seam, record:

| Field | Required evidence |
|---|---|
| Artifact identity | Mod/loader/MC version, file identifier, SHA-256 |
| Class/member | Fully qualified declaring class and exact descriptor |
| Mappings | Source namespace, production namespace, remapping decision |
| Lifecycle | Preconditions, cancellation point, costs, commit observation, cleanup |
| Attribution | Actor, hand, stack/family, cast/hook/root identity |
| Capabilities | Which gates, perks and Powers depend on it |
| Verification | Signature probe, transformed production class check and named runtime scenario |
| Failure behavior | Exact unavailable reason and native behavior retained |

Proposed operator commands:

- `/skills integrations status`
- `/skills integrations inspect hand`
- `/skills integrations explain <player> <action>`
- `/skills integrations validate`
- `/skills integrations dump`

Use appropriate existing permission levels for other-player inspection and exports. Player-facing self-inspection should be available without exposing internal class names. Dumps should include registry/profile/config evidence, not inventories or unnecessary player data.

Example player explanations:

- “Requires Dexterity 18. Your level: 14.”
- “This ability also requires the weapon’s native awakening unlock.”
- “Timing assistance is unavailable for the active fishing minigame.”
- “T.O. native armor activation support is unavailable for this version.”

Do not display “fully compatible” when only item classification works.

## 11. Persistence, UI and performance

### 11.1 Save and reload behavior

Use a versioned Runic integration-state section with bounded fields. Preserve existing skill levels, purchases, item locks and Tinkers state.

Persist cooldown debt when it starts, one-time informational milestones where needed, and dormant selected IDs. Use a registered ID allowlist and a bounded record count appropriate for the added catalog; do not reuse the Artifice-only twelve-entry limit.

Clear transient cast contexts, target sequences, pending damage bonuses and short-lived charges on death, logout or dimension change. Preserve cooldown debt so clearing a sequence cannot reset its cooldown. On restart, restore remaining cooldown time safely and avoid cross-world game-time collisions.

Retain unavailable selections in a dormant record that the player can inspect/remove. Do not delete paid ranks or grant repeated refunds on mod toggles. Reinstallation may restore eligibility, but reactivating effects must recheck slots, points, skills and configuration. Never automatically exceed capacity because a dependency returned.

On rules reload, build a complete immutable candidate, reject invalid required content, then publish atomically with a revision. In-flight actions retain provenance from their start; new Runic benefits use current availability. If a raised gate makes an ongoing action invalid, cancel/clean up at its supported boundary without creating a refund for a native cost that was already legitimately paid.

### 11.2 UI requirements

Use Runic’s established visual language and Powers screen:

- Show integration badge, governing skill, tier, trigger, magnitude, cooldown and prerequisite.
- Explain “native prerequisite” separately from “Runic requirement.”
- For dynamic gear, show normal attack, ability, casting and workshop requirements distinctly.
- Use original icons for every new perk and Power, with clear motifs: weapon cadence, mounted combat/forms, flowing water, angling/journal.
- Provide disabled/selected readability, localization keys, screen-reader-friendly text where the existing UI supports it, and reduced VFX.
- Reuse the existing Powers entry and layout conventions; avoid a second competing activation interface.
- Rate-limit denials and proc overlays. Display a catch bonus only after its actual commit.

Tooltips consume a server-synced description of effective rules and capabilities. Client inventory scans must never write native state.

### 11.3 Performance budgets

Implementation targets, to measure during profiling:

| Work | Budget/strategy |
|---|---|
| Item classification | Cached immutable registry/profile metadata plus bounded live stack reads; no full registry scan per hit |
| Active combat state | Bound per-player targets/sequences; at most 8 tracked hostile targets for new catalog logic |
| Catch state | At most one active native fishing context per player; discard expired entries promptly |
| Recent committed catch IDs | Bounded LRU, e.g. 32 per active player, plus consumed status on the live cast |
| Journal comparisons | Evaluate selected known fish at current loaded location; no world/chunk search |
| Area support | Radius ≤4 blocks and at most two allies for the specified Power |
| Pack rules | Index by namespace/tag/action; invalidate on relevant reload |
| Network | Send state changes and bounded feedback, not per-frame polling |
| Visuals | Reuse proc messages; owner-only informational effects where appropriate |

Audit root-effect claim capacity when registering the new catalog. An exhausted claim set must produce a bounded diagnostic and predictable suppression; it must not depend on hash iteration or silently disable whichever perk runs last. Increase limits only with a bounded worst-case analysis.

Server packets must use the connection’s sender, validate action and item identity, reject stale/repeated requests, and execute gameplay work on the logical server thread. Client claims about success, targets, costs or eligibility are never authoritative. Follow the Forge 1.20.1 side and networking rules. [Forge sides][forge-sides] · [Forge SimpleImpl][forge-network]

## 12. Implementation sequence and acceptance gates

### 12.1 Ordered work packages

| Phase | Work | Completion evidence |
|---|---|---|
| **P0: Baseline and artifacts** | Confirm implementation HEAD; pin a fresh current-version test instance; inspect dependency manifests and actual binary signatures | Version matrix, hashes and a runnable dependency set; legacy support explicitly scoped |
| **P1: Shared contracts** | Availability, action provenance, rule precedence, dynamic Power dormancy, persistent cooldown registry | Dependency/config loss tests; no optional imports in core paths; deterministic rules |
| **P2: Classification and gates** | Per-item coverage manifest; action gates; safe cleanup; tooltips and diagnostics | Every supported action route has a named positive/negative case, or an explicit unavailable status |
| **P3: Simply Swords** | Native activation and scaling ownership, gem/awakening reads, forge conservation, eight perks/six Powers | Production artifact checks plus attack/ability/forge tests |
| **P4: Simply More** | Mounted predicates, shield results, Mimicry continuity, paired carver test, eight perks/six Powers | Correct hand/form/root attribution, no cross-adapter double payout |
| **P5: Tide** | Native catch commit, generic-handler dedupe, journal conditions, provider-specific assistance, eight perks/six Powers | Catch/loot/bait/XP conservation and platform-specific provider tests |
| **P6: T.O. companion** | Public API binding, Aqua descriptor, cast pipeline, verified native hooks, eight perks/six Powers | API contract evidence; ordinary casting and native keybind coverage distinguished |
| **P7: Combined balance and release** | Complete UI/config/docs; test full pack, production jars, dependency removal and save migration | Release checklist with actual results and remaining capability limitations |

P6’s native hook work can proceed alongside P3–P5 when an implementation team is available. Do not bypass its evidence gates just to mark every planned feature complete.

### 12.2 Meaningful test matrix

| Area | Required scenarios | Must hold |
|---|---|---|
| Optional loading | None installed; each module alone with its dependencies; all four; dedicated server | Core starts; no absent/client class resolution; correct capability status |
| Version selection | SS 1.70.2; More 1.1.4 pairing; an unsupported/legacy SS version; Tide hotfix; T.O. exact profile | Correct adapter or explicit unavailable result; no broad range-based false certification |
| Manual locks | Existing explicit denial; new generated denial; exclusions; script veto; master toggles | Predictable precedence; disabling integration gates does not erase unrelated locks |
| Combat hand | Main hand, offhand, Better Combat alternate hand, two-hand behavior, miss, sweep, weapon swap | Actual item determines gate and bonus; no bonus from an unrelated held item |
| Simply Swords | Locked awakening, active/inactive gem, rebound key, interrupted channel, delayed native projectile | Native restrictions/costs hold; one effect claim; no replay of native callbacks |
| Scaling | Plain melee, gem effect, native ability with ISS, T.O. spell, Runic secondary hit | Each owner applies its intended contribution once; no recursive proc chain |
| Simply More | Living mount versus boat; unusual offhand; real travel versus teleport; shield block versus actual disable | Match native conditions; reward actual outcome |
| Mimicry | Automatic/manual forms, copied/replaced stack, inventory click, reload, relog, form becomes gated | Family cooldown survives; manual transformations mint no combat progress |
| Forge/carver | Preview, close/reopen, quick-move, full inventory, disconnect, last ingredient, occupied socket | Exact conservation; no bonus from previews; current SS/More pair verified |
| T.O. spell paths | Book, scroll, staff, weapon-carried spell, recast, channel, free proc, manual normal cast | Correct category and root identity; excluded costs remain excluded |
| T.O. native equipment | Armor packet, charged/released weapon ability, tier change, missing set/talent, removal | A verified gate before effects/costs, or honest unavailable capability |
| T.O. encounter/summon | Owned summon, multiple summons, third-party ally, counterspell encounter | No owner attacks introduced, free resummons, copied rewards or automatic boss bypass |
| Tide catch | Fish, vanilla fish, foreign fish, item, crate, pulled entity, empty, failure, timeout, multiple outputs | One committed outcome; native items/XP/journal remain single-owner |
| Tide concurrency | Offhand, swapped/dropped rod, stale/replayed result, same-tick duplicate callbacks, reload mid-cast | No duplicated Runic charge; safe line cleanup |
| Tide accessories | Multiple bait slots, last unit, multiple outputs, strongest line, native rod bonuses | Conservation cap; assistance remains bounded; no passive repair |
| Tide ecology | Wrong medium, weather/time/season/depth; zero-weight entries; datapack fish and parent variants | Native eligibility remains authoritative |
| Tide journal | Note-only records, existing saves, size disabled, hidden fish, removed fish data | Correct distinct-caught count; no load-time reward |
| Third-party fishing | Tide with Starcatcher on Forge; Stardew provider; Fishing Real handoff | Actual provider reported; one Runic owner; no fabricated Forge support |
| Power state | Equip/unequip, lower skill, disable module, remove dependency, restart, death, respec | Eligibility and budget coherent; cooldown debt retained per policy |
| Combined load | Maximum permitted perks/Powers, many targets, native rapid-fire/channel attacks | Numerical caps, bounded state, no runaway packets or effect recursion |

Add focused tests for the most consequential arithmetic and transaction logic. Do not substitute a source-text search for behavioral proof.

### 12.3 Production validation

Run the repository’s existing relevant gates, including build/tests, its Mixin remapping checks and shipped-refmap verification. Extend them to the new native seams. The Tinkers branch already documents why development tests can miss production remapping failures. Simply Swords and More source use Yarn names; verify the correct mapped declaring class and descriptor in the Forge artifact instead of pasting source names blindly. [Runic build gates][rs-build] · [Hook/remapping documentation][rs-tc-hooks]

Then launch the **distributed, reobfuscated artifact** with the selected dependencies in a dedicated server and matching client. Validate the dependency-absent profile too. If a test needs an external API that remains unavailable, mark that capability unresolved and do not label it tested.

For every one of the 32 perks and 24 Powers, require:

1. Stable ID, config, localization and original icon.
2. Working purchase/equip and active eligibility.
3. A real named trigger and successful effect.
4. A negative case proving it does not fire on the wrong action.
5. Cooldown, cap and cleanup verification.
6. Honest unavailable behavior when its mod or required hook is missing.

A partial release may explicitly ship a supported subset, but it must not claim completion of this full specification. Unimplemented entries belong in the development ledger rather than the normal player purchase list.

## 13. Evidence limits and research closure

This review used the user’s repository, current author-hosted release pages, pinned upstream source, Forge documentation, and the official T.O. binary’s manifest/public signatures. It was a targeted integration review, not a fresh exhaustive audit of every Runic feature.

Material unresolved items are bounded:

- Simply Swords’ inspected source says 1.70.1 while the published target is 1.70.2; confirm actual descriptors and behavior.
- Simply More’s carver and form behavior need the exact SS/More pair tested.
- T.O.’s public signatures establish usable types, not a universal native-activation cancellation or commit event.
- Tide’s source and release align, but this review did not match every hook to the distributed hotfix or launch its Forge provider combinations.
- No gameplay, performance, production launch, migration or balance test was run for these proposed integrations.

Research stopped after identities, version boundaries, main lifecycle contracts and consequential contradictions were supported or explicitly bounded. Further general searching would not replace the artifact inspection, supported upstream API guidance, or runtime tests listed above.

## 14. Source and implementation reference index

All sources were accessed September 7, 2026. Links to code use pinned revisions where available. The source paths identify inspection targets, not a promise that the same names or descriptors appear unchanged in an obfuscated runtime.

| Source group | Publisher / version or date | Why it matters |
|---|---|---|
| Runic source and comparison | Otectus; 2.1.0 branch commit September 6, 2026 | Actual progression, adapters, Powers, persistence and build machinery |
| Simply Swords release/source | Sweenus; release August 27, source commit August 26, 2026 | Migration boundary, native APIs, awakening/gems and scaling |
| Simply More release/source | RosemaryThyme / jay-jay0101; August 26, 2026 | Paired compatibility, specialized weapon rules and Mimicry |
| T.O. release and inspected binary | GameTechBC; January 6, 2026 | Confirmed identity, dependency manifest, public API signatures and scope limits |
| Iron’s Spellbooks release | Iron431; January 1, 2026 | Identity of the dependency pinned by Runic |
| Tide release/source | Lightning64 / Lightning-64; August 3, 2026 | Native catch transaction, conditions, journal and loader branches |
| Forge 1.20.1 documentation | MinecraftForge; retrieved September 7, 2026 | Logical-side and packet authority |


[rs-compare]: https://github.com/otectus/runic-skills/compare/ac7ab2d0c88a786b64a08ab18e0a8bf091830886...3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7
[rs-version]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/VERSION
[rs-master-properties]: https://github.com/otectus/runic-skills/blob/ac7ab2d0c88a786b64a08ab18e0a8bf091830886/gradle.properties
[rs-readme]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/README.md
[rs-progression]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/common/progression/ProgressionService.java
[rs-build]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/build.gradle
[rs-equipment]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/common/equipment/EquipmentProfileService.java
[rs-locks]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/integration/lock/LockProviderRegistry.java
[rs-lock-actions]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/integration/lock/LockAction.java
[rs-actions]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/common/actions/RunicActionContext.java
[rs-projectile]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/common/actions/ProjectileSnapshot.java
[rs-damage]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/common/combat/DamageContext.java
[rs-power-eligibility]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/registry/powers/PowerEligibility.java
[rs-power-dispatch]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/registry/powers/PowerDispatch.java
[rs-iss]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/integration/IronsSpellbooksIntegration.java
[rs-starcatcher]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/integration/StarcatcherIntegration.java
[rs-repair]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/common/durability/RepairService.java
[rs-content]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/registry/content/ContentStatusIndex.java
[rs-power-runtime]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/common/powers/PowerRuntime.java
[rs-cooldown]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/common/powers/PowerCooldownDebt.java
[rs-scaled]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/common/perk/ScaledRequirement.java
[rs-power]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/registry/powers/Power.java
[rs-power-tier]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/src/main/java/com/otectus/runicskills/registry/powers/PowerTier.java
[rs-tc-hooks]: https://github.com/otectus/runic-skills/blob/3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7/docs/TCONSTRUCT_HOOKS.md
[ss-release]: https://www.curseforge.com/minecraft/mc-mods/simply-swords/files/8746028
[ss-migration]: https://www.curseforge.com/minecraft/mc-mods/simply-swords/files/8723370
[ss-properties]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/gradle.properties
[ss-legacy]: https://github.com/Sweenus/SimplySwords/blob/e6ee1db59c6a84778e29b77bd16bd95334574ae7/gradle.properties
[ss-manifest]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/forge/src/main/resources/META-INF/mods.toml
[ss-entry]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/common/src/main/java/net/sweenus/simplyswords/SimplySwords.java
[ss-api]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/common/src/main/java/net/sweenus/simplyswords/api/SimplySwordsAPI.java
[ss-awakening]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/common/src/main/java/net/sweenus/simplyswords/api/AwakeningApi.java
[ss-gems]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/common/src/main/java/net/sweenus/simplyswords/power/GemPowerComponent.java
[ss-implicit]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/common/src/main/java/net/sweenus/simplyswords/api/WeaponImplicitRegistry.java
[ss-ability-manager]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/common/src/main/java/net/sweenus/simplyswords/world/PlayerWeaponAbilityManager.java
[ss-channel-manager]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/common/src/main/java/net/sweenus/simplyswords/world/PlayerWeaponAbilityChannelManager.java
[ss-bettercombat]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/common/src/main/java/net/sweenus/simplyswords/mixin/compat/BetterCombatServerNetworkMixin.java
[ss-forge-helper]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/forge/src/main/java/net/sweenus/simplyswords/forge/ForgeHelperMethods.java
[ss-helper]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/common/src/main/java/net/sweenus/simplyswords/util/HelperMethods.java
[ss-forge]: https://github.com/Sweenus/SimplySwords/blob/9f9ce185f81e5fafbde5d6c4c4ffa7bccfde78bf/common/src/main/java/net/sweenus/simplyswords/screen/RunicForgeScreenHandler.java
[sm-release]: https://www.curseforge.com/minecraft/mc-mods/simply-more/files/8736447
[sm-project]: https://www.curseforge.com/minecraft/mc-mods/simply-more
[sm-properties]: https://github.com/jay-jay0101/Simply-More/blob/a7cff4f128b502dc43845947d81457bfe450741f/gradle.properties
[sm-build]: https://github.com/jay-jay0101/Simply-More/blob/a7cff4f128b502dc43845947d81457bfe450741f/forge/build.gradle
[sm-manifest]: https://github.com/jay-jay0101/Simply-More/blob/a7cff4f128b502dc43845947d81457bfe450741f/forge/src/main/resources/META-INF/mods.toml
[sm-tags]: https://github.com/jay-jay0101/Simply-More/blob/a7cff4f128b502dc43845947d81457bfe450741f/common/src/main/java/net/rosemarythyme/simplymore/registry/ModTagRegistry.java
[sm-lance]: https://github.com/jay-jay0101/Simply-More/blob/a7cff4f128b502dc43845947d81457bfe450741f/common/src/main/java/net/rosemarythyme/simplymore/item/normal/LanceItem.java
[sm-lance-effect]: https://github.com/jay-jay0101/Simply-More/blob/a7cff4f128b502dc43845947d81457bfe450741f/common/src/main/java/net/rosemarythyme/simplymore/effect/LanceEffect.java
[sm-living]: https://github.com/jay-jay0101/Simply-More/blob/a7cff4f128b502dc43845947d81457bfe450741f/common/src/main/java/net/rosemarythyme/simplymore/mixin/LivingEntityMixin.java
[sm-mimicry]: https://github.com/jay-jay0101/Simply-More/blob/a7cff4f128b502dc43845947d81457bfe450741f/common/src/main/java/net/rosemarythyme/simplymore/item/uniques/MimicryItem.java
[sm-carver]: https://github.com/jay-jay0101/Simply-More/blob/a7cff4f128b502dc43845947d81457bfe450741f/common/src/main/java/net/rosemarythyme/simplymore/mixin/UniqueSwordMixin.java
[tom-release]: https://www.curseforge.com/minecraft/mc-mods/to-tweaks-irons-spells/files/7424522
[tom-project]: https://www.curseforge.com/minecraft/mc-mods/to-tweaks-irons-spells
[tom-dependencies]: https://www.curseforge.com/minecraft/mc-mods/to-tweaks-irons-spells/relations/dependencies
[iss-release]: https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks/files/7402504
[tide-release]: https://www.curseforge.com/minecraft/mc-mods/tide/files/8571673
[tide-repo]: https://github.com/Lightning-64/Tide-2
[tide-build]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/build.gradle.kts
[tide-manifest]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/templates/META-INF/mods.toml
[tide-properties]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/versions/1.20.1-forge/gradle.properties
[tide-rod]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/registries/items/TideFishingRodItem.java
[tide-accessor]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/registries/entities/misc/fishing/HookAccessor.java
[tide-hook]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/registries/entities/misc/fishing/TideFishingHook.java
[tide-minigame]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/data/minigame/FishCatchMinigame.java
[tide-player-data]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/data/player/TidePlayerData.java
[tide-fish-stats]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/data/player/FishStats.java
[tide-compat]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/compat/CompatHelper.java
[tide-config]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/config/TideServerConfig.java
[tide-message]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/network/messages/MinigameServerMsg.java
[tide-fish-data]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/data/fishing/FishData.java
[tide-context]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/data/fishing/FishingContext.java
[tide-selector]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/data/fishing/selector/FishingRandomSelector.java
[tide-fish-selector]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/data/fishing/selector/FishSelector.java
[tide-entry]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/data/fishing/selector/FishingEntry.java
[tide-bait]: https://github.com/Lightning-64/Tide-2/blob/876b95f31328f4e698d5150f7d840ab033d1b06d/src/main/java/com/li64/tide/util/BaitUtils.java
[forge-sides]: https://docs.minecraftforge.net/en/1.20.1/concepts/sides/
[forge-network]: https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/
