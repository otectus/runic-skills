# Runic Skills: progression consistency, crash fixes, configuration, and Pack Mule

**Implementation handoff for a coding agent**\
Repository: https://github.com/otectus/runic-skills\
Review date: 14 September 2026\
Reviewed branch: **master**\
Reviewed commit: **ee17728ea6eacb56d2651d825c9c656fb6d729cb**

## 1. Objective and instructions to the implementation agent

Implement the complete set of fixes and features below, including the related defects identified during this review. Start by reproducing the problems against the actual development checkout, then make the changes, run the relevant automated and gameplay checks, and update the documentation.

The required outcomes are:

1. Correct Spartan Weaponry and Spartan Shields progression inversions, then audit every existing integration for the same classes of requirement, classification, precedence, and recipe-progression error.
2. Fix [issue #7](https://github.com/otectus/runic-skills/issues/7), including equivalent recursion in other perks and interactions between those perks.
3. Make in-game configuration saving reliable.
4. Make raising per-skill and global level limits straightforward, with an accurate explanation of how those limits interact.
5. Enable Tinkers’ Construct automatic material requirements by default, with consistent action handling, useful explanations, and appropriate migration behavior.
6. Add **Pack Mule**, a three-rank Strength perk that raises ordinary eligible player-inventory stack limits from **64 to 128, 192, and 256**.
7. Correct additional related defects discovered while implementing these changes. Record unrelated findings separately with evidence and priority.

This document is the result of an audit and design task; production code was not changed during this review. The instructions above describe the work for the subsequent implementation agent.

### Working rules

- Read the checkout’s current repository instructions first. The reviewed tree contained **CLAUDE.md** and **MODMAP.md**, but no **AGENTS.md**. The user explicitly requested a repository crawl; use the map for orientation and inspect the relevant implementation.
- Record the branch, commit, working-tree changes, Minecraft version, loader, Java version, mod version, dependency pins, and available tests before editing. Preserve unrelated changes.
- Follow actual code and pinned dependency source when historical reports disagree with them. Update outdated comments after changing behavior.
- Keep Forge 1.20.1 compatibility. Do not introduce APIs from later Minecraft versions, Fabric, or NeoForge.
- Keep optional integrations optional and the dedicated server independent of client-only configuration classes.
- Do not replace working subsystems wholesale merely to create uniform-looking architecture. Extend the existing resolver, effect policy, progression service, configuration holder, and scoped-operation patterns where practical.
- Do not satisfy Pack Mule with a cosmetic count, additional hidden inventory slots, a backpack, or a hard cap of 127. The requested feature is a real stack of up to 256 in an ordinary eligible player-inventory slot.
- Compilation is necessary but insufficient. Inventory safety, native integrations, screen saving, and event recursion require behavioral checks.
- Do not claim all mod combinations have been verified unless they were actually exercised. Distinguish a verified adapter, a generic fallback, an unsupported case, and a test still blocked by missing dependencies.
- Choose the release number only after reconciling the baseline discrepancy below. Do not blindly label this 2.1.2 or overwrite an already published version.

## 2. Verified baseline and limits of this review

### Repository baseline

| Item | Observed value |
| --- | --- |
| Default branch | master |
| Commit | ee17728ea6eacb56d2651d825c9c656fb6d729cb |
| Commit date | 8 September 2026 |
| Version in gradle.properties and VERSION | 2.1.1 |
| Minecraft | 1.20.1 |
| Forge | 47.4.23 |
| Java toolchain | 17 |
| ForgeGradle | 6.0.24 |
| Gradle wrapper | 8.10 |
| Mappings | Parchment 2023.09.03-1.20.1 |
| YACL | 3.5.0+1.20.1-forge |
| Tinkers’ stable compile target | 1.20.1-3.11.2.166 |
| Stable Mantle target | 1.20.1-1.11.97 |
| Additional Tinkers’ runtime profile | 1.20.1-3.12.0.220 with conservative compatibility behavior |
| Network protocol constant | 14 |
| Built-in skills | Ten, including Fortune and Tinkering |
| Default per-skill maximum | 32 |
| Default global maximum | 256 |
| Current configurable per-skill range | 2–1000 |
| Current configurable global range | 32–99999 |
| Fresh skill level | 1 |
| Tinkers’ integration / perks / powers | Enabled by default |
| Automatic Tinkers’ material locks | Disabled by default |

Sources: [R1], [R2], [R3], [R18].

### Important version discrepancy

Issue #7 reports **Runic Skills 2.1.2 on Forge 47.4.10**. The public branch inspected here identifies itself as **2.1.1 on Forge 47.4.23**.

The offending Temporal Wisdom implementation is present in the inspected branch and matches the report’s essential behavior. That confirms the source defect without proving that the entire published 2.1.2 jar is identical to this checkout.

Before implementation:

- Obtain the affected release or the source commit that produced it when available.
- Compare the relevant handlers, configuration UI, bundled mixins, and refmap.
- Reproduce on the affected Forge version and validate the fixed build on the project’s pinned Forge version.
- Record any relevant differences. Do not lose release-only changes by starting from an older source snapshot.

The issue was open and had no comments when retrieved for this review. [R4]

### What was and was not verified

**Completed:** read-only repository checkout; repository and issue inspection through GitHub; targeted source tracing across lock generation, configuration storage/UI/sync, effect handlers, progression, Tinkers’ requirements, perk registration, inventory hooks, and tests; upstream source checks for YACL, Forge, and Spartan recipes.

**Automated execution attempted:** `./gradlew test --no-daemon`.

**Result:** Gradle failed before test execution because the wrapper could not download **gradle-8.10-bin.zip** from services.gradle.org; the environment reported **Network is unreachable**. No JUnit, Forge GameTest, client launch, or production-jar gameplay result is claimed.

All “confirmed” findings below mean **confirmed in source**, unless an upstream recipe or API check is explicitly stated. Gameplay reproduction remains part of the implementation work.

## 3. Findings and priorities

Priority definitions: **P0** blocks safe release through crashes or item loss; **P1** breaks normal progression, saving, or enforcement; **P2** improves consistency, diagnostics, or supportability.

| ID | Priority | Finding | Evidence and consequence |
| --- | --- | --- | --- |
| RS-PROG-01 | P1 | Wooden Spartan items are rediscovered and gated after being deliberately left unrestricted. | Curated generation skips material level zero; discovery considers only emitted locks “covered” and applies a base-8 fallback. [R5] |
| RS-PROG-02 | P1 | Fallback classification can change the skill identity of a weapon between material tiers. | A wooden rapier can become Strength/Dexterity gear while its curated stone counterpart uses Dexterity/Intelligence. [R5], [R6] |
| RS-PROG-03 | P1 | Generic namespace providers assign a common base without a material progression model. | The provider receives one base for an entire namespace; starter and advanced gear can receive the same inferred tier. Actual affected items require registry/recipe validation. [R6], [R7] |
| RS-EFFECT-01 | P0 | Temporal Wisdom reapplies an effect inside its own Added event. | EnchantingLorePerkHandler.onEffectAdded calls player.addEffect with a longer finite beneficial effect. [R4], [R8] |
| RS-EFFECT-02 | P0 | Blessing of Luck has the same recursive structure. | PerkEffectsHandler.onLuckApplied reapplies Luck without a recursion guard. [R9] |
| RS-EFFECT-03 | P0 | Hearty Feast has the same recursive structure during food use. | PerkEffectsHandler.onFoodEffectApplied reapplies a finite effect while its food-use predicate remains true. [R9] |
| RS-EFFECT-04 | P1 | Duration-transforming paths reconstruct incomplete effect state. | Short constructors omit icon/curative/hidden-state details; MixLivingEntity.respan explicitly passes a null hidden effect. IncomingEffectPolicy already provides a better starting point. [R9], [R10], [R11] |
| RS-CONFIG-01 | P1 | Saving through YACL can leave the Runic Skills holder stale and cause the next screen opening to overwrite the saved file. | The wrapper intercepts only its own onClose; YACL invokes its own onClose and setScreen(parent). Both open paths save the old holder before adapting it. [R12], [U1], [U2] |
| RS-CONFIG-02 | P1 | The UI uses a second writer with weaker persistence guarantees. | YACL’s serializer writes directly with TRUNCATE_EXISTING; ConfigHolder has its own atomic-save and unknown-key handling. [R13], [U3] |
| RS-CONFIG-03 | P1 | Config reload and lock sync can retain inconsistent derived state. | The UI’s holder.load is weaker than ForceRefresh; clients rebuild generated locks before the gameplay snapshot arrives, and that snapshot does not rebuild the lock map. [R14], [R15] |
| RS-CONFIG-04 | P1 | /globallimit accepts values outside the config’s declared range. | Its Brigadier argument allows every positive integer, then mutates and saves without the normal clamp/validation path. Reload can change the accepted value again. [R16] |
| RS-CONFIG-05 | P1 | The cap explanation is outdated and incomplete. | There are ten built-in skills, not eight; 256 is a configured budget, not an intrinsic mathematical maximum. Starting levels also count toward the global sum. [R2], [R17] |
| RS-TC-01 | P1 | Automatic material locks default to false. | This is explicit in the config and several historical documents. The requested new default supersedes that earlier design. [R2], [R19] |
| RS-TC-02 | P1 | Action-specific Tinkers’ rules are not consistently reached with the real action. | Both stack-aware capability entry points pass USE; interaction code also falls back to an ID-only check. Result-slot gating calls the USE entry point although the resolver exempts CRAFT. [R20], [R21], [R22] |
| RS-TC-03 | P1 | Known tiers above the automatic table can become less restricted than tier 4. | requirementFor returns empty above index 4. A known tier-5 tool can receive no automatic requirements. [R19] |
| RS-TC-04 | P1 | One unknown material can discard requirements from other known materials. | automaticProfile returns an empty allow when any material is undetermined, even if a known high-tier part was found. [R19] |
| RS-PACK-01 | P0 implementation constraint | 128–256 counts do not safely fit the ordinary 1.20.1 signed-byte wire representation. | Forge’s exact-version FriendlyByteBuf patch shows writeByte(count) and readByte(). Persistence also has a byte-reading path. Raising a slot limit alone is unsafe. [U4], [U6] |
| RS-AUDIT-01 | P2 | “No requirements” and “unrecognized” are conflated in parts of the generated-lock pipeline. | Missing entries and intentionally unrestricted items both disappear from a list of positive locks; empty manual requirements are also dropped. [R5], [R14] |

Do not treat every row as an independently reproduced user report. RS-PROG-03 identifies a verified architectural limitation whose per-item consequences must be measured. RS-PACK-01 is a constraint on the requested feature, not a claim that Pack Mule already exists and is broken.

## 4. Workstream A: consistent equipment progression

### 4.1 Reproduce the Spartan defect before changing it

At default configuration, with no manual override and multiplier 1:

| Item | Current source-derived result | Intended result after repair |
| --- | --- | --- |
| spartanweaponry:wooden_rapier | Fallback Strength 8, Dexterity 6 | Unrestricted starter weapon |
| spartanweaponry:stone_rapier | Curated Dexterity 2, Intelligence 2 | Preserve its deliberate stone-tier role/requirements |
| spartanweaponry:wooden_dagger | Fallback Strength 8, Dexterity 6 | Unrestricted starter weapon |
| spartanweaponry:stone_dagger | Curated Dexterity 2 | Preserve stone-tier requirement |
| spartanshields:wooden_basic_shield | Fallback Endurance 8, Constitution 6 | Unrestricted starter shield |
| spartanshields:stone_basic_shield | Curated Endurance 2, Constitution 2 | Preserve stone-tier requirements |
| spartanshields:wooden_tower_shield | Fallback Endurance 8, Constitution 6 | Unrestricted under the existing zero-tier policy |
| spartanshields:stone_tower_shield | Curated Endurance 4, Constitution 3 | Preserve the existing tower premium |

These values follow the inspected code. Confirm the actual runtime registry IDs and effective merged rules with the installed mod versions. Do not assert these numbers for a pack with its own overrides.

The root sequence is:

1. generateWeaponItems skips WOODEN because its material level is zero.
2. generateShieldItems skips the zero-level wooden shield row.
3. generateDiscoveredItems builds its covered set from **locks that were emitted**.
4. The skipped wooden IDs are therefore absent.
5. LockGen.gearLock applies a generic base of 8 and can assign a different category’s skills.

The fix must preserve the distinction between **handled and unrestricted** and **not handled**. Lowering the fallback base from 8 to 2 would still erase that distinction and misclassify items.

### 4.2 Do not assume every stone recipe consumes its wooden counterpart

Upstream 1.20.1 source confirms that the **stone basic shield consumes a wooden basic shield**. This makes the inversion a real prerequisite problem in that recipe chain. [U7]

The inspected **stone rapier** recipe instead consumes cobblestone and a handle; it does not consume a wooden rapier. It still has an inverted use/progression requirement, but not that exact recipe dependency. [U8]

Inspect the loaded recipes after datapacks and scripts apply. Separate:

- A recipe requiring a locked prerequisite item.
- A crafting result blocked by Runic Skills.
- A usable starter tier made unattractive by a higher requirement.
- An intentional branch into a different skill specialization.
- A different material name or recipe introduced by a pack.

Do not “fix” an assumed recipe graph by rewriting upstream recipes.

### 4.3 Represent resolution explicitly

Introduce a small internal resolution model, or extend the existing model, with at least these outcomes:

| Outcome | Meaning | May a lower-priority fallback continue? |
| --- | --- | --- |
| REQUIREMENTS | A recognized item has explicit requirements. | No, for the same policy owner. |
| UNRESTRICTED | A recognized item is deliberately usable without requirements. | No. |
| UNHANDLED | This provider does not know the item. | Yes. |
| UNDETERMINED | Relevant data exists but cannot be interpreted safely. | Only according to a documented conservative policy; expose the uncertainty. |

Requirements must carry provenance: provider, rule ID or classification, reference tier, scaling mode, multiplier, actual action, and final requirement map.

For the immediate Spartan fix, a separate set of **handled IDs** is sufficient if it survives the whole discovery phase. Mark verified wooden IDs handled even when no LockItem is emitted. Do the same for every intentional exemption, not just one material string.

Do not create a fake level-zero LockItem and assume it will work. HandlerSkill currently drops empty skill lists. An exemption must survive merging, caching, serialization, and reload.

### 4.4 Precedence and overrides

Preserve existing deliberate pack overrides while making precedence explicit:

1. Master feature switches decide whether their automatic policy participates.
2. Explicit applicable pack rules, including an explicit allow, follow the relevant subsystem’s documented priority.
3. Explicit manual item rules replace automatic ID requirements.
4. Curated native item/material classification outranks generic discovery.
5. Verified material/family inference follows.
6. A conservative generic fallback applies only to genuinely unhandled gear.
7. An unhandled non-gear item remains unrestricted.

Keep the existing Tinkers’ explicit stack-rule precedence unless intentionally migrated and documented; section 7 explains that path.

Define and validate a supported way for a pack to say **“this exact item is unrestricted.”** It must work even when automatic discovery is enabled. An explicit action/allow flag is clearer than silently changing the meaning of previously invalid empty entries. If you choose empty requirements as the representation, version and migrate the schema, and prove the allow survives every merge stage.

Normalize duplicate requirements for one skill deliberately. Reject contradictory duplicates or merge by the documented maximum; do not let map insertion order secretly lower a gate.

Keep these distinctions visible:

- Built-in default rule.
- Manual rule.
- Datapack rule.
- Native integration rule.
- Generic fallback.
- Explicit exemption.
- Unresolved classification.

### 4.5 Audit every current provider and runtime gate

Use the actual registry as the inventory, not a hard-coded claim about “all supported mods.”

| Area | Required review |
| --- | --- |
| Spartan | Weaponry, Shields, Cataclysm, Fire, discovered spartan* add-ons, starter gear, shields, quivers, ammunition, weapon-type identity |
| Ice & Fire | Curated materials and discovery pass; bone, chitin, dragonsteel, variants and add-ons |
| Locks integration | Lock/tool tiers, prerequisite paths, manual overrides |
| Samurai Dynasty | Material and weapon-family progression, equivalent classes |
| More Vanilla Tools/Armor | Material tables, missing starter entries, wood/wooden naming, tool-vs-weapon skill mappings |
| Jewelcraft | Material progression, equipment identity, non-equipment components |
| Iron’s Spells | Books, staves, scrolls, armor, rings/orbs; distinguish spell level from material/rarity when appropriate |
| Starcatcher | Rod and reusable-tackle tiers; bait and catches remain exempt where intended |
| Overgeared | Finished gear versus hammer/tongs/blueprints and parts; no accidental blade/head component gates |
| Generic providers | Every row in LockProviderRegistry, including Epic Knights, Aquaculture, Yucatan, Galosphere, Undergarden, Deeper and Darker, Dragonsteel, Cataclysm, Mowzie’s, Farmer’s Delight, Siege Machines, Fantasy Armor, Nature’s Aura, Bosses of Mass Destruction, Jet and Elia’s, Nichirin Dynasty, Saints Dragons, Stalwart Dungeons, culinary add-ons, and the Let’s Do family |
| Tinkers’ and add-ons | Actual tool definitions, materials, roles, actions, unknown data, material swaps, and rule reloads |
| Newer four-mod runtime | Simply Swords, Simply More, T.O. Magic/Travel Optics, and Tide through integration/common and their native adapters; these are not exhausted by the older LockProviderRegistry list |
| Vanilla defaults | Establish the baseline for comparable wooden, stone, iron, diamond, and netherite gear |
| Pack modifications | Effective recipes, tags, configured requirements, script overrides, and manual exemptions after reload |

For every real item examined, export:

~~~text
item_id
provider and dependency version
equipment family and action
material/tier evidence and confidence
native or reference requirements
multiplier and scaling mode
effective requirements
source/rule and override winner
explicit exemption, if any
recipe prerequisites and alternatives
warning category, if any
~~~

Retain the data needed to explain a finding, without dumping player inventories or unrelated player data.

The audit must detect:

- Tier inversions within a comparable material chain.
- Skill-family changes caused solely by fallback selection.
- Unrestricted starter tiers relocked by a completeness pass.
- High-tier gear accidentally treated as low-tier or unrestricted.
- Fabricated IDs and aliases that do not exist in the installed version.
- Namespace/mod-ID mismatches.
- False positives involving handles, blades, rods, repair components, decorative blocks, food, or materials.
- Substring collisions and suffix specificity, including parrying_dagger versus dagger.
- Requirements above the configured skill cap or impossible under the global budget.
- Prerequisite loops, with recipe alternatives considered.
- Multiple providers claiming the same item and choosing a result by incidental registration order.
- Multipliers and zero values whose actual behavior disagrees with the config description.
- Server/client differences caused by local discovery or stale cached configuration.

Do not infer progression entirely from attack damage or durability. Use native material APIs/tags, exact families, loaded recipes, and intentional design roles. Gold, magic equipment, and sidegrades do not form one universal ordering.

### 4.6 Progression invariants

For the same functional family and a documented direct material upgrade, lower-tier requirements should normally be no greater than higher-tier requirements **for each relevant skill**. Compare the requirement vector, not just the maximum level or the sum.

Permit a documented exception for a deliberate change in role. A rapier and a greatsword need not share the same skills. A wooden rapier and a stone rapier should not switch roles because one fell into a generic classifier.

For recipe checks:

- Follow prerequisite edges only where the loaded recipe actually has them.
- Respect ingredient alternatives and reusable tools.
- Distinguish “can craft” from “can wield.”
- Evaluate the minimum global level needed to meet the union of skill gates, including baseline levels in other skills.
- Report anomalies for review. Do not automatically flatten every requirement to the easiest alternative.

For scaling:

- Preserve zero as an intentional unrestricted outcome.
- Centralize rounding and range behavior.
- Separate literal manual levels from reference levels.
- Preserve legacy literal settings during migration.
- Provide a clearly labeled option for generated rules to scale relative to skillMaxLevel, using 32 as the reference where that is the authored scale.
- Existing Tinkers’ and newer integration reference-scaling behavior must not accidentally be applied twice.
- Do not silently clamp an intentional pack rule to make it reachable; explain an impossible rule and let the pack author change it.
- Do not silently reinterpret a documented positive multiplier as an off switch. Use the existing explicit switches unless a versioned config change defines different zero behavior.

### 4.7 Authoritative resolved lock state

The current ConfigSyncCP sends the configured list, and HandlerSkill.UpdateLockItems regenerates integration locks on the client. Login and reload send that packet before GameplayConfigCP. This can leave client rules built using an earlier local or server snapshot. [R14], [R15]

Replace this with a **server-resolved, immutable lock snapshot** for ID-based rules, carrying a revision shared with the relevant gameplay configuration.

Requirements:

- Clients install the resolved result; they do not rerun automatic discovery to decide server gameplay requirements.
- Batch or chunk large tables with strict entry/byte bounds. Install only a complete validated revision.
- Include exemptions and provenance needed for explanations.
- Keep a previous valid snapshot while a new revision is incomplete.
- Clear server-derived state on disconnect.
- Rebuild at server startup, successful config changes, and relevant datapack/tag/recipe reloads.
- Do not rebuild on every hover, inventory tick, or attack.
- Stack-dependent Tinkers’ rules need a synchronized rule/profile view or a bounded authoritative query. Do not turn one stack’s result into a permanent item-ID rule.

Extend the existing command hierarchy with an inspection/audit entry. Commands such as **/skills locks inspect** and **/skills locks audit** are proposed new interfaces, not existing commands. Ordinary players may inspect their own held item; exporting a full registry audit can remain operator-only.

## 5. Workstream B: issue #7 and related event recursion

### 5.1 Correct root-cause model

In Forge 1.20.1, Added is posted before the incoming effect is merged into the active-effect map. It is not cancelable and does not have an effect-replacement setter. Its “old” effect may be null. [U5]

Consequently, calling addEffect inside Added can recurse even before the first outer application has completed. Do not rely on a strictly increasing duration eventually preventing another event. Equal-duration or saturated-duration calls can still enter the event path.

The relevant current handlers are:

- EnchantingLorePerkHandler.onEffectAdded: Temporal Wisdom.
- PerkEffectsHandler.onLuckApplied: Blessing of Luck.
- PerkEffectsHandler.onFoodEffectApplied: Hearty Feast.

Fix all three together. Testing only Temporal Wisdom with the other two disabled is insufficient.

### 5.2 Preferred implementation: transform the incoming instance once

Reuse the existing two-argument **LivingEntity.addEffect(MobEffectInstance, Entity)** hook in MixLivingEntity and extend IncomingEffectPolicy.

Implement a small pure policy that accepts the incoming effect, the applicable bonuses, and verified application context, and returns either:

- The same instance when nothing changes.
- A copy with only the intended duration/amplifier changes.

Remove the self-reapplying event handlers after moving their behavior. Leave the normal Forge event and vanilla merge intact. Do not cancel and manually reconstruct the entire vanilla addEffect method.

**Do not add a second competing hook for the same transformation.** Consolidate the beneficial-duration path with the existing potion and harmful-effect logic.

Gameplay eligibility should be server authoritative. Client reception of already transformed effects must not extend them again. Preserve any deliberate client presentation behavior separately.

### 5.3 Explicit duration semantics

Use the following behavior as the implementation target:

| Source/condition | Temporal Wisdom | Blessing of Luck | Hearty Feast |
| --- | --- | --- | --- |
| Finite beneficial effect during the valid combat window | Applies | Applies if the effect is Luck | Applies only when genuinely granted by food |
| Finite beneficial effect outside combat | Does not apply | Applies if Luck | Applies if genuinely food-granted |
| Harmful food effect | Does not apply | Does not apply | Does not extend the debuff |
| Neutral effect | Does not apply | Does not apply | Does not apply by default |
| Infinite-duration effect | Leave infinite | Leave infinite | Leave infinite |
| Instantaneous effect | Do not create a meaningless duration extension or duplicate application | Same | Same |
| Perk disabled, rank absent, or percent nonpositive | No contribution | No contribution | No contribution |

The Hearty Feast restriction to beneficial food effects is a deliberate correction: the current handler can extend harmful food effects as well. Document this change.

For overlapping Runic Skills percentage bonuses on one incoming beneficial effect, **sum applicable percentages and apply once**. For example, 15% Temporal Wisdom plus 20% Blessing of Luck produces 35% additional duration, not an event-order-dependent recursive product.

For the existing potion-only flat duration bonus:

1. Compute the original finite duration.
2. Apply the sum of applicable percentage extensions once.
3. Add the existing potion-specific flat duration bonus once.
4. Apply existing potion amplifier changes once.
5. Bound and round deliberately.

Suggested finite-duration calculation:

~~~text
result = saturating_int(
    floor(original_ticks * (1 + sum_applicable_percent / 100))
    + eligible_flat_bonus_ticks
)
~~~

Compute with wide/finite-safe arithmetic. Preserve the infinite sentinel; do not allow overflow to create a negative/infinite effect. Do not impose an arbitrary short gameplay ceiling on a legitimate long potion. Bound to the supported finite representation and any explicitly documented configuration limit.

Preserve the existing harmful-reduction composition for Lion Heart and Lucky Charm, with regression tests. Do not accidentally turn their additive reduction into multiplicative shortening.

### 5.4 Preserve complete effect state

When copying an effect, preserve:

- Effect type.
- Amplifier, except where the existing amplifier perk changes it.
- Ambient flag.
- Particle visibility.
- Icon visibility independently of particle visibility.
- Hidden-effect chain.
- Forge curative-item data.
- Factor data and any applicable mod state.
- Source entity passed to the outer addEffect call.

Use and extend IncomingEffectPolicy’s save/load-copy approach where it faithfully represents the pinned runtime. Verify copy behavior with actual custom curatives and hidden effects. Do not assert that a vanilla serialization round trip automatically preserves every third-party subclass field.

If a custom effect cannot be safely copied, use an explicit supported adapter or leave it unchanged with bounded diagnostics. Do not silently erase its state.

The current MixLivingEntity.respan passes a null hidden effect and shares factor data through its constructor. Replace that incomplete reconstruction as part of this work. [R10], [R11]

### 5.5 Accurate food and potion context

“Player is currently using an edible item” does not prove that an effect came from that item. An unrelated mod can apply a buff while the player is eating.

Track the actual food/potion application scope around the relevant native operation. Reuse the existing item-finish context where appropriate, but make it:

- Server-side for gameplay.
- Specific to the player and operation.
- Nesting-safe.
- Cleared in a finally block, including exceptions.
- Able to distinguish a copied/shared effect from the source food effect.

A ThreadLocal is an operation-context tool, not permission to mutate Minecraft entities off the server thread.

Do not put temporary “already processed” flags into permanent item/player NBT. Do not identify self-generated effects solely by equal duration/amplifier, which can suppress legitimate unrelated applications.

### 5.6 Shared Flame and secondary propagation

UtilityPowerHandler already has an IN_SHARE guard. Preserve its intent while checking how it interacts with the consolidated duration policy.

Required behavior:

- One source application may produce one deliberate copy per eligible ally.
- Copies do not recursively redistribute to further allies.
- A recipient’s own eligible duration bonuses apply at most once.
- A source’s food/potion context must not leak into a recipient’s copy.
- The copy preserves the relevant effect metadata.
- Percentage rounding, infinite effects, and the “no copy” cases are defined and tested.
- Whether the share starts from the source’s transformed duration must be explicit; preserve that current ordering unless tests identify a reason to change it.
- Do not create duplicate proc notifications for one application.

If a guard remains necessary for legitimate secondary actions, use a scoped context keyed to the relevant origin/entity/effect. A process-wide boolean that suppresses unrelated players is not an adequate general solution.

### 5.7 Broader recursion and feedback-loop audit

Inspect every event handler and mixin that invokes the operation it observes, including:

| Event/action family | Look for |
| --- | --- |
| Effect add/remove/expire | addEffect, removeEffect, curing, renewal and ally-copy loops |
| Damage | hurt inside damage/attack handlers; retaliation and cleave retriggering standard modifiers |
| Healing | heal inside healing listeners and heal-to-damage conversions |
| XP | XP grants from XP listeners; mending/reward feedback; repeated grants before state commits |
| Crafting | Event callbacks, bonus output, refunds, quick-craft loops, remaining items |
| Durability | Repair triggering more repair, wear avoidance reentry, Tinkers’ charge hooks |
| Inventory | Pickup/spawn/merge callbacks emitting new pickups or repeated insertions |
| Progression | SkillLevelUpEvent or script callbacks requesting further progression before the first change commits |

Reuse existing protections such as DamageContext, RunicActionContext, CraftingExecutionGuard, ContainerInteraction, reward ledgers, and repair budgets. Their presence is a reason to inspect their boundaries, not to assume all callers use them correctly.

For each candidate, document the call cycle, stopping invariant, and exception cleanup. Fix verified cycles and add behavioral tests with finite operation counts.

Do not catch StackOverflowError as a gameplay fix. Do not make Forge event-bus exception-handler replacement a shipped workaround. The issue’s Sinytra/Log4j masking report is useful diagnostic context, not authorization to patch another project’s logging infrastructure.

### 5.8 Minimum effect regressions

- Reproduce Temporal Wisdom with a vanilla beneficial potion during combat.
- Repeat with the mod-triggered source from the report when available.
- Exercise Blessing of Luck alone and with Temporal Wisdom.
- Exercise Hearty Feast alone and in combat.
- Test all three together using a food-granted Luck effect.
- Add an external listener that counts Added events: expect one per intended target application, apart from explicit ally copies.
- Test an already active stronger/weaker effect and a hidden-effect chain.
- Test one-tick, ordinary, very long, infinite, harmful, neutral, and instantaneous effects.
- Test unusual icon/particle flags and custom curatives.
- Test exception cleanup and two players in the same tick.
- Test Shared Flame among several eligible players without propagation loops.
- Test reload, logout, death, dimension changes, and combat-window expiry.
- Confirm no double extension on the client or repeated extension every tick.

## 6. Workstream C: reliable configuration and understandable maximums

### 6.1 Fix the actual save lifecycle

The verified failure path is:

1. Runic Skills opens configuration and saves its current holder.
2. A separate YACL ConfigClassHandler loads and edits the same file.
3. YACL saves its own object.
4. YACL’s Done/close handler calls setScreen(parent) on its own screen.
5. ReloadOnCloseScreen.onClose is bypassed.
6. The Runic Skills holder can remain stale.
7. Reopening configuration saves that stale holder over the user’s edits.

This is independent of whether the chosen global cap is mathematically useful. The current schema permits a global value above 256; there is no inspected clamp that forces it back to 256 based on 32 × 8. [R2], [R12], [U1], [U2]

### 6.2 Use one persistence owner and an explicit save transaction

Keep ConfigHolder as the storage authority, or introduce one shared service around it. The UI should edit a detached draft and call the same validated commit operation as commands.

Suggested flow:

~~~text
open -> read current permitted scope -> detached draft
edit -> validate fields and cross-field relationships
save -> validate again -> persist atomically -> publish applicable state
     -> rebuild dependent views/caches -> synchronize -> report result
cancel -> discard pending draft
~~~

Requirements:

- Opening, rendering, resizing, changing tabs, or leaving the screen without saving must not rewrite the file.
- Do not call holder.save as a synchronization shortcut on screen entry.
- Save must update persistence and the authoritative runtime state deliberately.
- Do not depend on onClose, removed, or a future cache miss to make a successful save real.
- Use a supported YACL builder save callback or serializer adapter. Keep the callback attached to the actual YACL screen.
- Preserve unexposed fields and unknown keys when saving a draft.
- Preserve the existing malformed-file recovery policy. Opening the UI must not overwrite an unreadable file with defaults.
- Return a structured save outcome, including validation failures and I/O failures, instead of relying on log-only void methods.
- Report failure in the screen and retain the unsaved draft.
- Handle a changed underlying file/revision while a draft is open. Merge non-conflicting fields or show the conflict; do not overwrite unrelated external edits silently.
- Fix both buildScreen and any buildYacl/generateGui entry point still in use.
- Audit other configuration screens for the same adapter pattern.

A successful Save should remain successful after Done, Escape where allowed, navigation to a child screen, screen resize, returning to the title screen, reopening, and restarting the game.

### 6.3 Correct authority and application scope

The UI must explain which settings it is editing:

| Context | Required behavior |
| --- | --- |
| Title screen, no running world | Edit local defaults; apply when the local world starts. |
| Integrated singleplayer server | Save local settings and schedule the live apply on the integrated server thread. Wait for an application result. |
| LAN host | Same authoritative server apply; synchronize every connected player. |
| Remote multiplayer client | Show server gameplay values as read-only. Allow clearly separated editing of local defaults for future local play if useful. |
| Dedicated server operator | File edit plus /skillsreload, or existing permitted commands; no client-only library requirement. |

A player saving a local multiplayer default must not be told that the remote server’s cap changed.

ConfigHolder’s authoritative snapshot is static and shared within an integrated-server JVM. encodeForClients already uses local() to avoid echoing the client snapshot, but most gameplay readers call instance(). Trace and eliminate stale-snapshot use during integrated-server reload, especially before rebuilding generated locks.

Prefer explicit logical-side/context access at the service boundary. Do not import Minecraft client classes into common storage code or use physical Dist.CLIENT as a substitute for logical server ownership.

Unify configuration application with the existing ForceRefresh/SkillsReloadCommand work:

- Publish the validated configuration.
- Refresh perk, passive, and power metadata.
- Rebuild effective lock rules.
- Reconcile derived attributes and other required player state.
- Reconcile Pack Mule eligibility/capacity safely.
- Publish client snapshots for the same revision.
- Report restart-required values separately from values now in force.

Do not execute the existing command as a string from the UI merely to reach this logic. Extract a reusable server service.

### 6.4 Replace ambiguous cap wording

Use these definitions:

- **Maximum level per skill:** the highest configured level in one skill.
- **Global level budget:** the sum of skill levels the player may reach through ordinary progression.
- **Reachable maximum:** the sum of the maxima of the registered progressable skills.
- **Earned levels:** increases above the starting baseline; this is already a separate concept for perk-budget scaling.

There are ten built-in skills in this baseline. At cap 32, their total capacity is **320**, while the configured global budget remains **256**.

Because skills start at 1, a fresh built-in character has a global sum of 10, not zero. Eight skills at 32 plus the other two at 1 sum to **258**. Do not reuse the current description claiming that a total budget of 32 simply maximizes one skill/perk.

The global budget may intentionally be lower than the reachable maximum to require specialization. A larger number may be stored even if the per-skill caps currently prevent using all of it.

### 6.5 Proposed cap model

Preserve the existing keys for backward compatibility:

- skillMaxLevel
- playersMaxGlobalLevel

Add a mode such as **globalLevelCapMode** with two validated values:

| Mode | Effect |
| --- | --- |
| custom | Use playersMaxGlobalLevel as the explicit global budget. |
| sum_of_skill_caps | Derive the budget from the registered skill caps. Raising the per-skill cap automatically raises the total. |

Use a string field with the existing StringChoices validation if that best fits ConfigSchema. Do not add an unsupported enum type to the wire schema accidentally.

Migration/default policy:

- Existing configs migrate to **custom** and retain their values.
- Fresh ordinary defaults can remain 32/custom/256 to preserve the established specialization balance.
- The UI prominently offers **Allow all skills to reach maximum**, which selects sum_of_skill_caps.
- This feature removes the need to calculate and manually maintain the matching global number.
- Retain the last custom number while automatic mode is selected, so switching modes does not erase the user’s preference.
- Do not write the derived total back into the stored custom field.

Derive the count from the authoritative registered progressable skill set. Do not hard-code eight or ten. If future skills have individual caps or can be disabled, use the same progressable set and caps for both the preview and enforcement.

Use wide arithmetic for totals. Keep persistence bounds, network bounds, commands, UI validation, and CapabilityBounds consistent.

### 6.6 Provide useful presets and a live preview

Proposed presets for this ten-skill baseline:

| Preset | Per-skill cap | Mode | Effective global budget |
| --- | --- | --- | --- |
| Existing balance | 32 | custom | 256 |
| Max every skill | 32 | sum_of_skill_caps | 320 |
| Extended progression | 64 | sum_of_skill_caps | 640 |
| Long progression | 100 | sum_of_skill_caps | 1000 |
| High-cap progression | 1000 | sum_of_skill_caps | 10000 |

These are editable starting points, not additional hidden rules.

Display a live sentence such as:

> 10 skills × 64 levels = 640 total levels. Automatic mode lets every skill reach 64.

For custom mode:

> Your global budget is 256. With 10 skills capped at 32, full mastery would require 320.

If the user enters global 1024 with per-skill 32:

- Keep the entered number.
- Explain that the current reachable maximum is 320.
- Offer an explicit **Raise the per-skill cap to 103** action, because ceil(1024 / 10) = 103.
- Show that 103 × 10 = 1030 possible levels, with a custom budget of 1024.
- Do not silently reset 1024 or silently change the other field.

For impossible/restrictive low budgets, explain the starting baseline and the resulting inability to advance.

The current verified storage ceiling already allows skill levels up to 1000. Fully support and test that range through these controls. This request does not require pretending levels are unlimited. If you raise the technical ceiling beyond 1000, change CapabilityBounds, sanitizers, XP math, array/iteration assumptions, serialization and UI limits together, with corresponding tests.

### 6.7 Unify all cap consumers

Route the UI, purchases, commands, script/quest grants, power eligibility, perk budgets, titles, and diagnostics through shared cap calculations.

Audit:

- SkillLevelUpSP.
- ProgressionService and ProgressionHooks.
- SkillCapability global-level/baseline helpers and raw mutation methods.
- GlobalLimitCommand, SkillLevelCommand, and respec.
- RunicSkillsScreen and its view models.
- GameplayConfigSnapshot and ConfigSchema.
- CapabilityBounds and CapabilitySanitizer.
- ScaledRequirement and older literal perk requirements.
- Passive level arrays and attribute calculations.
- PowerEligibility, especially requirements expressed as percentages of the global cap.
- Quest/integration rewards that grant skill levels.

For normal player progression and ordinary grants, enforce per-skill and global limits consistently. If operator commands retain an intentional override ability, make that explicit and separate from ordinary progression. Do not add an accidental bypass through a new UI endpoint.

The raw SkillCapability.addSkillLevel method performs unchecked addition and assumes the skill entry exists. Migrate legitimate callers to the progression service, or harden/deprecate the raw method while preserving public integration contracts. Do not declare it an active gameplay exploit without confirming a reachable caller.

Lowering a cap must not erase earned levels or perk ranks. Preserve the current grandfathering principle: disallow further increases where appropriate, bound effective behavior deliberately, and let explicit respec/admin operations change stored progression.

Make event/script reentry and XP spending transactional. A callback must not buy multiple levels against the same unspent balance or charge XP for a denied/no-op change.

### 6.8 Config acceptance tests

- Change global 256 to 512; save, close, reopen, restart; 512 remains stored.
- Repeat for skillMaxLevel, the Tinkers’ lock toggle, a float, an array/list, and an unexposed preserved field.
- Open/close/cancel without edits; file contents and modification time remain unchanged.
- Save with an unknown JSON5 key; it survives.
- Start with malformed JSON5; opening the screen does not overwrite it.
- Simulate a write failure; the screen reports failure and runtime state remains coherent.
- Edit the file externally while the screen is open; no silent lost update.
- Apply live in singleplayer and LAN; server behavior and all clients agree immediately.
- Join a server with different values; local defaults stay intact.
- /globallimit and UI edits pass the same validator and agree after reload.
- Automatic cap updates when the per-skill cap changes and when a supported additional skill is registered.
- Validate cap 2, 16, 32, 64, 100, 1000 and custom budgets below/equal/above the reachable maximum.
- Verify no free purchases at a cap, no XP charge on denial, and no save truncation after cap reduction.

## 7. Workstream D: Tinkers’ material locks enabled by default

### 7.1 Change the default without discarding existing choices

Set **enableTConstructLockItems = true** for new/missing configuration values. Keep the existing integration/perk/power defaults enabled.

Existing explicit false values must remain false. The old release wrote false as its default, so an old false cannot reliably be distinguished from an intentional user choice. Do not flip every existing installation silently.

Give existing users a clear enabled-by-default preset/action through the fixed configuration UI. Update comments, generated descriptions, migration notes, tests, and current documentation that still say automatic locks are deliberately off.

Do not change unrelated automation-reward or experimental compatibility toggles merely because this toggle changes.

### 7.2 Preserve starter access and native material meaning

The existing reference table is:

| Native material tier | Tinkering at cap 32 | Functional skill at cap 32 |
| --- | --- | --- |
| 0 | No requirement | No requirement |
| 1 | 1 | 1 |
| 2 | 8 | 4 |
| 3 | 16 | 8 |
| 4 | 24 | 16 |

At the default starting level, tier 1 is effectively available.

Use native material definitions, not item-ID keywords. A wooden and an advanced pickaxe share the same tconstruct:pickaxe ID.

Repair the two related cases:

- **Known tier above 4:** apply at least the tier-4 automatic requirement unless an explicit higher-tier rule replaces it. A conservative saturated tier table is preferable to dropping all requirements. Do not equate “outside this old table” with “unknown material.”
- **Mixed known and unknown materials:** preserve the provable requirements from known functional parts and report the unresolved parts. Do not let an unknown part erase a known high-tier requirement.

If no meaningful material information is available, follow the conservative unsupported-data policy and expose that status. Do not invent a maximum-level penalty.

Audit which material parts actually affect the tool’s function. Exclude cosmetic embellishments, and verify the assumption that every native getMaterials entry is functional for each supported tool/add-on.

### 7.3 Pass the real action end to end

Add an action-aware capability/resolver entry point and migrate the callers that have an ItemStack in hand.

| Operation | Required action/behavior |
| --- | --- |
| Melee attack and damage backstop | ATTACK |
| Equip armor or appropriate equipment | EQUIP |
| Right-click item use, shield use, ranged charge/fire | USE or a deliberately added specific action |
| Mining/harvesting | A specific mining action if needed to distinguish it from generic use |
| Recipe preview and result take | CRAFT |
| Move/store/remove an existing item | TAKE; no automatic material trap |

The reviewed capability entry points always pass USE. InteractionEventHandler.shouldCancelInteraction discards the stack and consults only the registry ID. MixSlot applies the same generic-use entry point to crafting results. Fix these concrete connections, not just the resolver’s switch statement.

The current resolver intentionally exempts CRAFT and TAKE. Preserve that user experience for **automatic material use locks**: players may move and craft equipment even when they cannot wield it. Existing explicit item/crafting policies can retain their documented behavior. Workshop payment/keystone guards must still run before consumption.

Add a new mining action only if its distinction is required; update rule parsing, schema, tests, and diagnostics consistently. Never silently reinterpret previously authored USE rules.

Test hybrid weapons/tools. A mining tool must not need every skill associated with every possible action merely because it is held.

### 7.4 Cover actual enforcement and presentation

Verify:

- Melee, projectiles, bow/crossbow charging and release.
- Offhand shields and use.
- Mining start and authoritative block-break completion.
- Armor equip, right-click equip, dispenser equip where relevant, and login reconciliation.
- Tool assembly and material replacement.
- Existing inventories, result previews, shift-click, and removal.
- Creative and fake-player behavior according to the mod’s existing policy.
- Live config changes and datapack changes.

The tooltip, station preview, inspection command, and denial notice must agree on:

- Which material or explicit rule caused the requirement.
- Which skill levels are needed.
- The player’s current values.
- Whether an automatic profile is disabled or unavailable.
- Whether some material information is undetermined.

Use localized skill names in player-facing text. Cache by stack fingerprint and relevant config/rule revision without mutating the item to establish identity.

Retain **/skills tinkers inspect** and **/skills tinkers compat**, extending them where useful. Avoid a second competing Tinkers’ rule engine.

### 7.5 Tinkers’ validation matrix

- Fresh configuration with supported stable Tinkers’: locks are enabled.
- Existing explicit false: remains off after update and restart.
- Toggle on/off live through the UI: effective behavior and explanations update.
- Tier 0/1 starter tool remains accessible at the default cap.
- Tier 2/3/4 gates follow the table.
- Known tier 5+ does not become easier than tier 4.
- Mixed low/high parts, all unknown parts, and mixed known/unknown parts.
- Cosmetic modifier does not create an unrelated gate.
- Explicit pack allow and explicit manual override.
- Attack/use/equip/mining/craft/take distinctions.
- Stable and conservative beta profiles, clearly reporting unsupported features.
- Tinkers’ absent: normal server/client startup and no optional class-loading failure.
- Supported add-on equipment actually exercised with its pinned dependencies.


## 8. Workstream E: Pack Mule

### 8.1 Product specification

**Name:** Pack Mule\
**Registry ID:** runicskills:pack_mule\
**Skill:** Strength\
**Ranks:** Three\
**Increment:** +64 per rank\
**Maximum:** 256 for eligible stacks

| Active rank | Bonus capacity | Maximum eligible stack | Proposed Strength requirement at cap 32 |
| --- | --- | --- | --- |
| 0 | +0 | 64 | None |
| I | +64 | 128 | 8 |
| II | +128 | 192 | 16 |
| III | +192 | 256 | 24 |

The stack sizes and three ranks are user requirements. The **8/16/24 unlock levels are proposed balance defaults**, to be implemented as configurable reference requirements.

Use the existing multi-rank Perk machinery, perk enable/disable behavior, capability persistence, rank-up authority, and config refresh. Do not build a separate Pack Mule progression system. Reference requirements should scale through the established ScaledRequirement helper without changing the three stack-size rewards.

Pack Mule should be enabled and visible by default. A player still has to unlock/activate the perk through the normal system. Count it as one enabled perk for the active-perk budget, consistent with other multi-rank perks.

### 8.2 Eligibility and scope

The proposed default deliberately targets **ordinary items whose natural stack size is 64**. This interpretation produces the requested 128/192/256 progression without making tools, armor, potions, or other special items stackable.

| Location/item | Default behavior |
| --- | --- |
| Player’s 36 main inventory/hotbar slots | Eligible items receive that inventory owner’s Pack Mule capacity. |
| Player’s offhand | Eligible ordinary stacks receive the same capacity; equipment rules remain intact. |
| Carried cursor stack | May temporarily carry the acting player’s eligible extended count; every destination still enforces its own limit. |
| Armor/equipment-only slots | Preserve slot restrictions; normally unstackable. |
| Ordinary chest, barrel, hopper, furnace, machine | Preserve its normal stack/slot limits. |
| Player crafting grid, workbench grid, output/payment slots | Preserve native recipe and slot limits. Output transfers may aggregate into eligible player slots. |
| Backpack, bundle, storage item, modded internal inventory | Preserve that storage system’s rules. Do not assume it belongs to the player for Pack Mule. |
| Natural maximum 1 | Unchanged. |
| Natural maximum 16 | Unchanged by default. |
| Already enlarged stacks from another mod | Do not shrink them or claim compatibility automatically; use a verified composition adapter where supported. |
| Fake players and automation without a real owner | No perk capacity by default. |

Apply strict stack identity checks. Items with different NBT, names, enchantments, capabilities, stored contents, or relevant native state must not merge merely because their registry IDs match.

Use Forge’s applicable stacking/capability compatibility semantics. Exclude stateful items that cannot safely be aggregated even if they report a natural maximum of 64.

A pack should be able to blacklist unsafe item IDs/tags. A whitelist, if provided, must not bypass essential nonstackable/damageable/container safety constraints by default.

Determine the **inventory owner**, not merely the player clicking a menu. A high-rank player must not lend capacity to another player’s inventory, to a chest, or to automation through shared static state.

### 8.3 Configuration and presentation

Keep the controls small and useful:

- Enable Pack Mule.
- Three configurable reference unlock levels.
- Item/tag exclusions.
- Any explicitly supported compatibility mode.

The default rank count, +64 increment, and 256 maximum must match the request. Do not expose an apparently unrestricted “any stack size” field unless the implementation and serialization support it.

Suggested player description:

> Carry larger stacks of ordinary items in your inventory. Rank I: 128. Rank II: 192. Rank III: 256. Special items and storage containers keep their own limits.

Show the current capacity and next-rank capacity in the perk tooltip. Use localized text and an icon consistent with the existing perk assets. Update the relevant content ledger, icon inventory, localization parity, and regeneration inputs; do not edit a generated image set without updating its source workflow.

Render actual three-digit counts legibly at all supported GUI scales. Do not truncate 256 to a visually plausible two-digit number.

### 8.4 Required architecture

Separate **capacity**, **count representation**, and **transfers**:

| Component | Responsibility |
| --- | --- |
| PlayerStackPolicy | Determine eligibility and allowed count for a particular inventory owner, stack, destination slot, and action. |
| Extended count serialization | Preserve real counts in save data and network traffic. This does not grant anyone permission to hold that count. |
| Inventory transfer integration | Apply source/destination limits and preserve quantities during native operations. |
| Lifecycle reconciliation | Handle rank/config changes, death, reload, and over-limit saved stacks without deleting items. |
| Client view | Use server-synchronized rank/config state for prediction and display. |

Names are suggested, not mandatory.

Never mutate Item’s global maximum-stack-size field. Item singletons are shared across players, dimensions, containers, and both logical sides of an integrated server.

Never return 256 globally from ItemStack.getMaxStackSize merely because some player has Pack Mule.

If a narrow getMaxStackSize bridge is needed for vanilla call sites, it must be scoped to a verified player-slot operation with explicit owner and destination information. The existing ContainerInteraction identifies an actor for a click, but **that actor alone is insufficient**: a single menu click can touch a player slot, a chest slot, a crafting result, and a machine slot.

Use existing MixinExtras scoped-operation patterns and try/finally restoration. Add no unbounded per-tick scans over all items, recipes, or registries.

### 8.5 Lossless persistence before gameplay enablement

Minecraft 1.20.1 has multiple ItemStack serialization paths. Inspect the **actual Forge-patched generated source** for this project before writing mixins.

At minimum, cover:

- ItemStack.save and ItemStack.of/CompoundTag construction.
- Inventory.save/load and player save data.
- Offhand/equipment persistence.
- Carried-stack handling on menu close/disconnect.
- Capability data that stores ItemStacks.
- Codec-based and NBT-based paths used by relevant integrations.

The Forge ItemStack patch exposes a byte-reading Count path, while the ItemStack CODEC uses an integer Count. Do not assume that because one codec supports int, the ordinary player save path does too. [U6]

A suitable persistence approach is:

1. Preserve ordinary legacy Count data for normal-sized stacks.
2. Write an integer Count for extended stacks.
3. Read the complete validated integer when that numeric tag type is present, before constructing a stack that could be treated as empty.
4. Continue reading legitimate old byte Count values.
5. Preserve all other item NBT/capabilities exactly.
6. Keep the reader installed even when the Pack Mule feature is disabled.

Use a versioned alternative field only if there is a demonstrated compatibility advantage and it has one authoritative count. Avoid two fields that can disagree and silently duplicate items.

Do not infer a missing count from a wrapped legacy value. A byte value of zero or a negative number in an old corrupted save is not enough evidence to reconstruct 256 or 128 reliably.

Detect invalid or impossible counts without silently replacing meaningful item data with an empty stack. Preserve recoverable evidence through the established migration/recovery path.

### 8.6 Lossless network representation

Forge 1.20.1’s FriendlyByteBuf.writeItemStack writes a signed byte count, and readItem reads a signed byte before constructing the ItemStack. This covers more than inventory contents: held equipment, entity data, custom packets, and menu interaction paths can use the same codec. [U4]

**Raising the slot capacity without fixing both directions is a P0 item-loss defect.**

A concrete compatible-with-normal-counts design to prototype is:

- Counts in the legacy positive range retain their ordinary encoding.
- Reserve an invalid legacy negative count, for example -128, as an extended-count marker.
- After that marker, write the real count as a bounded VarInt.
- The paired reader consumes and validates the extra VarInt **before constructing the ItemStack**, then reads the normal share tag.
- Use the same behavior for Forge’s limited-share-tag and full-tag variants.
- Do not change every ByteBuf readByte/writeByte in the program. Target only the proven ItemStack count sites.
- Keep capacity validation separate: a representable count is not an authorized inventory mutation.

The implementation agent may choose a different proven encoding, but must document its layout and exercise every relevant packet path. Do not ship two competing count protocols or blindly change all vanilla item-count fields to VarInts.

Set an explicit, bounded serialization limit. It must support 256 and must be compatible with the declared supported extended-stack environment. If a co-installed mod has its own incompatible codec or larger count format, add and test an adapter or detect the incompatibility clearly. Never clamp foreign counts to 256 and discard the remainder.

Preserve the following:

- Packet order and following fields.
- Item identity and metadata.
- Share-tag/full-tag semantics.
- Empty-stack encoding.
- Collection lengths and menu state IDs.
- Both clientbound and serverbound reconstruction.
- Rejected/invalid count handling before allocation or large loops.

Bump the Runic Skills network protocol for the changed wire behavior and added messages. The reviewed value is 14; choose the next value after reconciling the actual release branch.

Both client and server must use compatible builds. Feature disablement must not dynamically switch the wire layout while a connection is active. Do not make server configuration determine whether a client decodes the next packet differently.

The vanilla client’s reported slot/cursor contents are not authoritative. Validate ownership, active rank, eligibility, destination limits, menu identity/state, and quantities on the server. Reject impossible operations and resynchronize from server state.

### 8.7 Inventory operations that must be integrated

Inspect and cover the actual 1.20.1 implementations and Forge wrappers:

| Area | Operations to exercise |
| --- | --- |
| Inventory | add, add to a specific slot, compatible-stack search, remaining-space calculation, pickup, selected slot, offhand, setItem, placeItemBackInInventory |
| Menus | clicked/doClick, moveItemStackTo, quick-move, carried stack, drag distribution, double-click collection, number-key swap, offhand swap, throw, creative clone |
| Slots | getMaxStackSize overloads, mayPlace, safeInsert, safeTake, tryRemove, special subclass limits |
| Player wrappers | Forge player/main/offhand inventory handlers, insert simulation, extraction and combined wrappers |
| Item use | Eating, placing blocks, throwing stackable items, recipe consumption and returned containers |
| Crafting | 2×2 and 3×3 grids, shift crafting, recipe book, remaining items, output take, existing bonus-output/refund hooks |
| Lifecycle | Login/logout, menu close, death, dimension change, rank/config changes, respec |
| Networking | Full menu content, one-slot updates, carried stack, clicks, creative actions, held-item equipment/entity updates |
| Compatibility | Sorting, recipe transfer, storage menus, graves, and other stack-size mods when supported |

Do not rely on slot.getMaxStackSize alone. Inventory merging and container transfers also consult the item’s maximum and sometimes a separate container limit.

Conversely, do not relax a whole container’s maximum merely because some of its slots are backed by the player inventory.

### 8.8 Transfer invariants

For each operation:

~~~text
total_before = items_in_sources + items_in_destinations + items_in_cursor
total_after  = items_in_sources + items_in_destinations + items_in_cursor
               + deliberately_created_drops
               + explicitly_consumed_items

For ordinary transfer:
total_before == total_after
~~~

Crafting, item use, and rewards require a corresponding ledger of legitimate consumption and production. Do not count a intended recipe output or reward as duplication, but do prove it is produced only once.

Required examples:

| Action | Expected result |
| --- | --- |
| Rank I picks up two compatible stacks of 64 | One stack of 128 if an eligible destination has room. |
| Rank II adds 64 to a compatible stack of 128 | One stack of 192. |
| Rank III adds 64 to a compatible stack of 192 | One stack of 256. |
| Add another item to a full 256 stack | Another slot/remainder; never 257 in that slot. |
| Move 256 to ordinary chest slots | Split into four native 64 stacks, subject to destination space. |
| Put carried 256 into one empty chest slot | Insert at most 64; retain 192 on the cursor or follow an equivalent native remainder behavior. |
| Move 256 toward a chest with room for only 80 | Transfer 80; retain 176 safely. |
| Move items from a chest into a rank-III inventory | Aggregate into eligible player slots up to 256. |
| Give/trade/drop items to a player without the perk | Normalize to the receiving destination’s limits. |
| Pick up an NBT-different item | Keep it separate, even when the current stack has spare count capacity. |
| Simulate a Forge insert/extract | Return accurate limits/remainders without mutation. |

Partial transfers must preserve native cancellation/payment behavior. A failed transfer must not trigger a crafting reward, consume ingredients, or duplicate a remainder.

### 8.9 Dropping, death, and world storage

World item entities and external storage should remain within their normal supported limits unless another verified system owns a different policy.

- Dropping one item removes exactly one.
- Dropping a whole extended stack produces the necessary native-sized item entities.
- Preserve tags, capabilities, ownership, pickup delay, and relevant throw behavior on every split.
- Do not remove the source amount before the destination/drop creation has a safe committed outcome.
- If a spawn/transfer is canceled, preserve or restore the uncommitted quantity.
- keepInventory retains the real extended counts when the player still qualifies.
- Ordinary death drops split safely and appear exactly once.
- Grave/corpse integrations need a proven boundary: normalize before handing the payload to a system that assumes native limits, or use a tested adapter that preserves extended counts.
- Do not assert universal grave-mod compatibility solely because ItemStack.save was patched.
- Hoppers, machines, redstone/comparator behavior, and storage-capacity calculations must not inherit Pack Mule simply from having handled its source stack.

### 8.10 Rank loss, disabling, and over-limit stacks

Never delete items by setting a 256 stack’s count to 64 on respec.

Use a lossless reconciliation policy:

1. Recompute the player’s effective capacity after rank/config changes.
2. Split over-limit stacks into eligible free space where possible.
3. If there is insufficient space, retain the residual quantity safely in its existing ownership domain.
4. Mark that stack as **over its current limit** in the view; it may shrink or be split but cannot grow.
5. Subsequent transfers must insert only the destination’s allowed amount.
6. Reconcile again when space becomes available, without an expensive full inventory scan every tick.
7. Preserve the state across logout/restart until normalization completes.

This is a migration exception for already owned items, not a way to create new oversized stacks without the perk.

A closed menu with an oversized cursor must have a deterministic outcome: return what fits, split legitimate drops when that is the normal close behavior, and preserve any uncommitted remainder if the transfer is canceled. A small persistent recovery record may be needed for exceptional interrupted transactions; it must be bounded, visible to the user when used, and not function as unlimited extra perk storage.

Test:

- Rank III → II → I → disabled.
- Strength requirement changes.
- disabledPerks and the Pack Mule master toggle.
- Active-perk budget changes.
- Respec with a completely full inventory and cursor.
- Config reload while a chest or crafting menu is open.
- Player death while an over-limit stack is waiting to normalize.
- Reconnect/restart before and after normalization.

Persistence and decoding fixes stay enabled while the feature is off, so old extended stacks remain readable.

Provide a normalization/export path and clear migration instructions before recommending removal of the mod or downgrade to a build without extended-count support. Do not promise that an unmodified older build can read the new representation.

### 8.11 Pack Mule test boundaries

Minimum counts:

~~~text
0, 1, 16, 63, 64, 65, 127, 128, 129, 191, 192, 193, 255, 256
~~~

Test 257 as a **disallowed new Pack Mule slot quantity**. Also test negative counts, malformed extended markers, truncated VarInts, and implausibly large counts at the appropriate serialization boundary.

Use eligible vanilla blocks/items, ordinary modded stackables, natural-16 items, unstackable tools/armor, damageable items, custom NBT, and capability-bearing items.

Required test levels:

1. Pure unit tests for eligibility, capacity, distribution, remainders, and overflow-safe arithmetic.
2. Property-based or randomized conservation tests over sequences of transfers.
3. Forge GameTests for real ItemStacks, NBT, wrappers, and player inventory operations.
4. Dedicated-server/client tests for wire round trips and prediction.
5. Real menu tests for all click types and lifecycle transitions.
6. Production reobfuscated jar tests; a passing development mixin is not proof the packaged hook works.

Test two players with different ranks simultaneously, including shared containers. Their capacities must remain independent.

Include legacy save round trips and repeated load/save cycles at 128, 192, 255, and 256. Counts that look correct for a single frame but disappear after a packet or restart fail acceptance.

### 8.12 Pack Mule release gate

Do not ship the perk as complete until:

- 256 is a real count in one eligible player-inventory slot.
- Save and packet round trips preserve it.
- Transfers, death, drops, respec, and disabling conserve quantities.
- Normal storage and special items keep their rules.
- Client and server agree.
- No new item exists solely because the client reported it.
- Compatibility limitations are accurately described.

If an environment dependency blocks a required test, preserve the implementation and report the exact unverified gate. Do not silently substitute 127, visual aggregation, or “coming later” for the requested feature.

## 9. Additional related consistency work

### 9.1 Confirmed issues to include, not defer as unrelated polish

The following are closely tied to the requested outcomes and belong in the same implementation:

- All three identified effect recursion paths.
- Effect metadata preservation in the shared transformation path.
- The config screen’s stale overwrite and second-writer behavior.
- Command/UI/file validation parity.
- Lock snapshot order and client-side regeneration.
- Explicit unlocked outcomes surviving fallback and merging.
- Real Tinkers’ action propagation.
- Known high tiers falling outside the automatic table.
- Mixed known/unknown Tinkers’ material handling.
- Clear distinction between native capacity, player capacity, and serializable count.

### 9.2 Leads requiring targeted reproduction

Treat these as investigation targets until verified:

| Lead | Why it deserves attention | Expected evidence |
| --- | --- | --- |
| Ingredient/component false positives | Generic matching still recognizes broad gear words; some multiword keywords use substring matching. | Actual registered non-gear item and the effective rule that locks it. |
| Weapon-family suffix ordering | identifyWeaponType checks dagger before parrying_dagger. | Whether this changes mastery behavior for a real item, not merely which enum name matched. |
| Spartan discovery opt-outs | Its bespoke scan does not visibly consult the same disabledDiscoveredLockItems list as GenericNamespaceLockProvider. | Config documentation versus actual opt-out behavior; define and repair scope consistently. |
| Integrated-server stale config | One static holder can expose the authoritative client snapshot to server readers during apply. | A live singleplayer reload trace showing which revision each derived cache used. |
| Global budget bypass | ProgressionService’s generic mutation path does not perform the purchase packet’s global-budget check. | Each caller’s intended policy, including whether admin overrides are deliberate. |
| Reentrant progression/XP spending | Public events/scripts run before the progression mutation/charge completes. | A bounded test subscriber that attempts a nested purchase or grant. |
| Capability bounds after raising ceilings | skillMaxLevel and MAX_SKILL_LEVEL currently agree at 1000. | All reads/writes still agree if a ceiling changes. |
| Starter parity in generic providers | One namespace-wide base cannot distinguish wood/stone from advanced materials. | Per-item registry and recipe audit, including intentional special cases. |
| Caches after datapack reload | Material, recipe, tag, and explicit-rule revisions can change independently. | Before/after effective requirements without requiring a reconnect. |
| Existing crafting/refund loops with oversized inputs | Pack Mule introduces source stacks that exceed native slot sizes. | Real quick-craft/recipe-book and remaining-item tests with conservation checks. |

Fix reproduced defects in these areas. Record a disproved lead as such rather than quietly keeping it in a list of known bugs.

### 9.3 Keep the remaining audit bounded and evidence-based

Inspect relevant native integration callbacks, TODOs, disabled features, and error paths. The current repository already contains historical audits and large content inventories. They are useful search indexes, not proof that a defect remains or a feature works.

For any additional finding, record:

- Affected version and dependency.
- Exact file/symbol.
- Triggering conditions.
- Reproduction steps.
- Expected versus actual behavior.
- Root cause.
- Fix scope.
- Regression coverage.
- Remaining uncertainty.

Do not expand this update into a redesign of every perk, a loader port, or a replacement for unrelated mods.

## 10. Implementation sequence

### Stage 0: establish and reproduce

- Reconcile public 2.1.1 source with reported 2.1.2.
- Record baseline and preserve the existing checkout.
- Capture Spartan effective requirements at default settings.
- Reproduce the three effect handlers independently.
- Reproduce YACL Save → Done → reopen.
- Build a small set of representative dependency profiles.
- Establish actual Forge-patched ItemStack and inventory method signatures before writing count hooks.

**Exit:** source-backed defect records and a known baseline, with unavailable artifacts clearly noted.

### Stage 1: eliminate crash recursion

- Consolidate incoming effect transformations.
- Preserve effect state and sources.
- Remove recursive self-reapplication.
- Verify Shared Flame and existing harmful/potion perks.
- Add event-count and nested-operation regressions.

**Exit:** the identified effect cycles terminate and produce the intended durations exactly once.

### Stage 2: make configuration commits reliable

- Remove screen-entry writes.
- Install one validated save owner with structured results.
- Extract a shared server apply/rebuild service.
- Correct integrated-server authority handling.
- Make UI, file reload, and /globallimit agree.

**Exit:** changed values survive closing/reopening/restart and live changes reach the right server/client state.

### Stage 3: repair progression rules and synchronization

- Distinguish unrestricted from unhandled.
- Correct Spartan starter coverage and role consistency.
- Audit and correct the provider inventory.
- Add consistent diagnostics and explicit exemptions.
- Publish server-resolved lock snapshots.
- Add the intuitive global-cap mode, presets, and previews.

**Exit:** representative progression chains are coherent and server/client explanations agree.

### Stage 4: enable Tinkers’ defaults safely

- Default automatic material locks to true for new/missing values.
- Preserve existing explicit choices.
- Pass real actions through every relevant path.
- Correct tier-5+ and mixed-unknown handling.
- Update UI, inspection, docs, and compatibility tests.

**Exit:** a fresh installation has useful material gates, accessible starter tools, and no crafting/removal traps.

### Stage 5: implement Pack Mule representation and transfers

- Register the multi-rank perk and synchronized settings.
- Implement/test extended count persistence and wire representation first.
- Implement owner-aware player inventory capacities.
- Cover native and Forge-wrapper insertion/removal/transfer paths.
- Add lifecycle normalization and recovery.
- Exercise client prediction and supported compatibility profiles.

**Exit:** all Pack Mule release gates in section 8.12 pass.

### Stage 6: package, verify, and document

- Run the existing repository checks plus the targeted new tests.
- Exercise the reobfuscated distributable on a dedicated server and client.
- Update protocol/schema consistency checks.
- Update current docs, migration notes, player-facing changes, and audit records.
- Record remaining unsupported combinations without presenting them as verified.

**Exit:** implementation, distributable, verification evidence, and documentation describe the same behavior.

## 11. Verification plan and acceptance matrix

### 11.1 Existing infrastructure to retain

The reviewed repository contains pure JUnit tests under **src/test/java** and Forge GameTests under **src/gametest/java**. Preserve that separation.

Useful existing tests include:

- ConfigHolderAtomicSaveTest, ConfigHolderResilienceTest, ConfigClampTest.
- ConfigSchemaTest and ConfigSchemaCoverageTest.
- ConfigAuthorityGameTest.
- LockGenTest and LockProviderRegistryTest.
- StackRequirementGameTest.
- ProgressionBoundsTest and ProgressionVetoGameTest.
- CraftingAuthorityGameTest, CraftRewardPolicyGameTest, EfficientCraftingGameTest.
- DurabilityPerksGameTest and DamageContextGameTest.

Existing build checks include sided imports, lock-provider wiring, version consistency, localization parity, mixin inventory, YACL autogen, mixin remapping, and shipped refmap checks. Wire new sources/assets into the relevant inventories rather than weakening the checks.

Suggested verification commands, after checking the current build configuration:

~~~bash
./gradlew test
./gradlew check
./gradlew build
./gradlew runGameTestServer
./gradlew runGameTestServer -PtinkersProfile=stable
./gradlew runGameTestServer -PtinkersProfile=beta
~~~

Check available run profiles and property behavior before invoking them. The default Tinkers’ runtime profile is off; a core test run is not Tinkers’ validation. Include any required targeted profiles for the newer four-mod integrations and Spartan artifacts.

### 11.2 Required runtime profiles

| Profile | Purpose |
| --- | --- |
| Core Runic Skills, required runtime dependencies only | No optional-mod class-loading regressions; base effects/config/Pack Mule |
| Core + pinned YACL | Actual Save/Done/Cancel/child-screen behavior |
| Spartan Weaponry only | Starter weapon progression and role identity |
| Spartan Shields only | Starter shield recipe chain and independence from Weaponry |
| Both Spartan mods + representative add-ons | Discovery coverage and shared toggles |
| Stable Tinkers’ + Mantle | Native material/action behavior |
| Conservative Tinkers’ beta profile | Accurate fallback/unsupported reporting |
| Representative Tinkers’ add-ons | Known high/unknown/mixed materials and native equipment |
| Magic source from issue #7 when available | Reproduce the reported trigger without blaming the effect source |
| Multi-integration pack | Cross-handler effects, overlapping gates, recipes, native menus |
| Two real clients on a dedicated server | Different Pack Mule ranks and different local config values |
| Singleplayer and LAN | Shared-JVM authority, saving, respec, disconnect/reopen |

Do not add Sinytra Connector merely to reproduce the core recursive bug. Test its reported logging interaction when reproducing that specific environment, after the underlying callback is fixed.

### 11.3 Acceptance matrix

| ID | Scenario | Pass condition |
| --- | --- | --- |
| A1 | New player with wooden Spartan weapons/shields | Starter items are not accidentally locked by fallback discovery. |
| A2 | Wood → stone → iron comparisons | Requirements follow verified roles and material progression. |
| A3 | Stone shield recipe | A locked wooden prerequisite does not block the intended starting chain. |
| A4 | Manual exemption | Lower-priority discovery cannot relock the item. |
| A5 | Provider audit | All active providers/native gates have an evidence-backed result or explicit unverified status. |
| A6 | Reload and login | Client effective locks match the server’s revision. |
| B1 | Temporal Wisdom in combat | No recursion; expected duration; one application per target. |
| B2 | Blessing of Luck | No recursion with ordinary Luck or interacting perks. |
| B3 | Hearty Feast | Only genuinely food-granted beneficial effects are extended once. |
| B4 | Shared Flame | Copies do not propagate recursively; metadata and owner context remain correct. |
| B5 | Hidden effects/custom curatives | No state lost during transformation. |
| C1 | UI Save → Done → reopen → restart | Saved values persist. |
| C2 | Cancel/open/resize | No unexpected disk writes. |
| C3 | Remote multiplayer | Local config editing does not masquerade as server changes. |
| C4 | Integrated server | Live rebuild uses the new authoritative configuration consistently. |
| C5 | Command versus file versus UI | Same validation and effective cap rules. |
| C6 | Automatic cap | Per-skill changes update total capacity without manual multiplication. |
| C7 | Lowered caps | Earned progression is preserved; further purchases follow policy. |
| D1 | Fresh Tinkers’ setup | Automatic material locks are on. |
| D2 | Existing explicit false | User choice remains off. |
| D3 | Craft/remove/store | Automatic use locks do not trap items or incorrectly block output taking. |
| D4 | Attack/equip/mining/use | Actual action selects the correct requirement. |
| D5 | Known tier above 4 | Does not become less restricted than tier 4. |
| D6 | Mixed unknown material | Known requirements survive; uncertainty is reported. |
| E1 | Pack Mule ranks I/II/III | Actual stack capacities are 128/192/256. |
| E2 | Natural-1 and natural-16 items | Their default stacking rules remain unchanged. |
| E3 | Ordinary storage | Native destination limits are preserved. |
| E4 | Two players, different ranks | No cross-player capacity leakage. |
| E5 | Save and network round trips | Exact identity/state/count at 128, 192, 255, and 256. |
| E6 | Every native click type | No loss, duplication, or invalid destination stack. |
| E7 | Death/drop/disconnect | All quantities preserved through the intended destination. |
| E8 | Respec/config disable/full inventory | Lossless normalization or safe over-limit retention. |
| E9 | Malformed client operation | Server rejects it and resynchronizes; no items granted. |
| E10 | Production jar | Relevant mixins function after reobfuscation. |

### 11.4 What counts as evidence

A useful test proves an outcome:

- A counter verifies how many real Added callbacks occurred.
- A save/reload test proves an actual 256 count survives.
- A transfer ledger proves total item conservation.
- A client/server session proves a packet round trip.
- A real registry/recipe pair proves a progression inversion is fixed.

A test that merely searches for a new method name or compares a constant to the value used to initialize it is not behavioral evidence. Keep existing source lints for their legitimate packaging/registration purpose, and add real tests where they cannot verify the risk.

Record command, profile, dependency versions, result, and relevant artifact/log location. A blocked test is **blocked**, not passed.

## 12. Deliverables and completion requirements

The implementation agent should produce:

1. **Production changes** implementing all requested outcomes and confirmed related fixes.
2. **Regression tests** covering the actual failure mechanisms and Pack Mule data safety.
3. **Progression audit data** with before/after requirement vectors, provenance, and verified recipe edges.
4. **Root-cause report** for issue #7, its sibling handlers, and the config reset.
5. **Configuration documentation** with the correct ten-skill baseline, automatic/custom modes, presets, file locations, authority behavior, and reload/restart scope.
6. **Tinkers’ migration notes** describing the new default and preservation of explicit existing settings.
7. **Pack Mule documentation** covering eligibility, ranks, storage boundaries, serialization compatibility, normalization, respec, and downgrade/removal preparation.
8. **Player-facing changelog** in plain language, focused on visible fixes and behavior.
9. **Build and verification summary** identifying the exact tested commit, distributable, protocol, profiles, passes, and blocked or unsupported cases.
10. **Remaining findings list** for unrelated issues, with evidence and without disguising unfinished required work as optional enhancements.

Update current authoritative documentation and generated inventories. Keep historical audits identifiable as history; do not rewrite past reports to pretend they described the new behavior.

The task is complete when every required acceptance criterion is met, or when a concrete external blocker is explicitly documented with the implementation preserved and the remaining verification clearly stated. “The constants were changed” is not completion for configuration, integration progression, or oversized inventory stacks.

## 13. Source map

References below identify the actual source inspected. Runic Skills file links are pinned to the reviewed commit. Upstream API/recipe references are pinned independently and are evidence for the version-specific behavior discussed, not a substitute for checking the exact runtime artifacts during implementation.

The Forge source reference is the **1.20.1 branch snapshot**, not the broader **1.20.x** branch, which contains later APIs. Recheck the generated source for Forge 47.4.23 before selecting injection targets.


- **R1: [Build metadata](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/gradle.properties).** Also compare build.gradle, VERSION, META-INF/mods.toml and update.json before selecting the next release number.

- **R2: [Common configuration](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java).** Cap defaults/ranges, Tinkers’ defaults, integration options and duration settings.

- **R3: [Repository instructions](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/CLAUDE.md).** Use with MODMAP.md and docs/README.md.

- **R4: [Issue #7](https://github.com/otectus/runic-skills/issues/7).** Temporal Wisdom report, affected version, stack trace, reproduction and diagnostic context.

- **R5: [Spartan generation](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/integration/SpartanIntegration.java).** Curated tables, zero-tier skips, completeness scan, generic base and weapon-type matching.

- **R6: [Generic gear classifier](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/integration/lock/LockGen.java).** Category selection, exclusions, scaling and gearLock.

- **R7: [Provider inventory](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/integration/lock/LockProviderRegistry.java).** Use with integration/lock/GenericNamespaceLockProvider.java and docs/INTEGRATION_MATRIX.md.

- **R8: [Temporal Wisdom](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/registry/events/EnchantingLorePerkHandler.java).** onCombat and onEffectAdded.

- **R9: [Related effect handlers](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java).** onLuckApplied and onFoodEffectApplied; inspect UtilityPowerHandler for Shared Flame.

- **R10: [Incoming-effect mixin](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/mixin/MixLivingEntity.java).** Existing two-argument addEffect transformation and respan metadata handling.

- **R11: [Incoming-effect policy](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/common/effects/IncomingEffectPolicy.java).** Existing harmful-duration policy and save/load copy.

- **R12: [YACL bridge](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/client/config/YaclConfigUiBuilder.java).** Use with client/config/ReloadOnCloseScreen.java; opening writes the holder, closing is delegated.

- **R13: [Configuration storage](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/config/storage/ConfigHolder.java).** Local/authoritative instances, unknown keys, save/load and atomic replacement.

- **R14: [Lock map and refresh](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/handler/HandlerSkill.java).** Manual/generated merge, empty rules, client regeneration and ForceRefresh.

- **R15: [Lock synchronization](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/network/packet/client/ConfigSyncCP.java).** Compare registry/events/PlayerLifecycleHandler.java, common/command/SkillsReloadCommand.java and config/snapshot/GameplayConfigSnapshot.java for send/apply ordering.

- **R16: [Global-limit command](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/common/command/GlobalLimitCommand.java).** Argument bounds and direct persistence path.

- **R17: [Skill capability](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/common/capability/SkillCapability.java).** Global sum and starting baseline; compare registry/RegistrySkills.java for the ten built-in entries.

- **R18: [Network protocol](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/network/ServerNetworking.java).** Protocol 14 and exact compatible-peer policy at the reviewed commit.

- **R19: [Tinkers’ requirements](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/integration/tconstruct/TConstructRequirementResolver.java).** Automatic material table, precedence, unknown material handling and action policy.

- **R20: [Capability lock entry points](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/common/capability/SkillCapability.java).** canUseItem, canUseItemSilent and stackLockVerdict.

- **R21: [Item/block interaction enforcement](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/registry/events/InteractionEventHandler.java).** ID-only shouldCancelInteraction path; compare CombatEventHandler for attack backstops.

- **R22: [Crafting result take enforcement](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/mixin/MixSlot.java).** mayPickup and the generic canUseItem call; compare MixCraftingMenu.

- **R23: [Existing ranked perk model](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/registry/perks/Perk.java).** Use with registry/RegistryPerks.java and common/perk/ScaledRequirement.java.

- **R24: [Scoped menu interaction](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/mixin/MixAbstractContainerMenu.java).** Use with common/util/ContainerInteraction.java. Actor context does not identify each destination’s capacity.

- **R25: [Progression service](https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/common/progression/ProgressionService.java).** Use with SkillLevelUpSP, CapabilityBounds, CapabilitySanitizer and ExperienceMath.

- **U1: [Pinned YACL screen](https://github.com/isXander/YetAnotherConfigLib/blob/566fb6c498d571281c94029f8dfbac5e5077600c/src/main/java/dev/isxander/yacl3/gui/YACLScreen.java).** finishOrSave, cancelOrReset and onClose from the commit tagged 3.5.0+1.20.1-forge.

- **U2: [Pinned YACL config handler](https://github.com/isXander/YetAnotherConfigLib/blob/566fb6c498d571281c94029f8dfbac5e5077600c/src/main/java/dev/isxander/yacl3/config/v2/impl/ConfigClassHandlerImpl.java).** Autogenerated GUI attaches the serializer save callback.

- **U3: [Pinned YACL serializer](https://github.com/isXander/YetAnotherConfigLib/blob/566fb6c498d571281c94029f8dfbac5e5077600c/src/main/java/dev/isxander/yacl3/config/v2/impl/serializer/GsonConfigSerializer.java).** save writes the generated document with TRUNCATE_EXISTING.

- **U4: [Forge 1.20.1 item-count wire patch](https://github.com/MinecraftForge/MinecraftForge/blob/3c3f496015632ab84560be77a57cc57ac42c49e1/patches/minecraft/net/minecraft/network/FriendlyByteBuf.java.patch).** writeItemStack, signed-byte count and readShareTag path.

- **U5: [Forge 1.20.1 effect application patch](https://github.com/MinecraftForge/MinecraftForge/blob/3c3f496015632ab84560be77a57cc57ac42c49e1/patches/minecraft/net/minecraft/world/entity/LivingEntity.java.patch).** Added event is posted before the active-map merge. Also read src/main/java/net/minecraftforge/event/entity/living/MobEffectEvent.java at the same commit.

- **U6: [Forge 1.20.1 ItemStack patch](https://github.com/MinecraftForge/MinecraftForge/blob/3c3f496015632ab84560be77a57cc57ac42c49e1/patches/minecraft/net/minecraft/world/item/ItemStack.java.patch).** Integer CODEC Count versus legacy CompoundTag count read; inspect generated mapped source for all persistence sites.

- **U7: [Spartan stone basic shield recipe](https://github.com/ObliviousSpartan/SpartanShields/blob/89e376d70bd18b05b3b6118a932fdd2c67697f2b/src/generated/resources/data/spartanshields/recipes/stone_basic_shield.json).** Confirms a wooden basic shield is consumed as an ingredient.

- **U8: [Spartan stone rapier recipe](https://github.com/ObliviousSpartan/SpartanWeaponry/blob/e45342c53cd2c6fb185d149019fd326e4c1ac696/src/generated/resources/data/spartanweaponry/recipes/stone_rapier.json).** Uses cobblestone and a handle, not a wooden rapier.

[R1]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/gradle.properties
[R2]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java
[R3]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/CLAUDE.md
[R4]: https://github.com/otectus/runic-skills/issues/7
[R5]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/integration/SpartanIntegration.java
[R6]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/integration/lock/LockGen.java
[R7]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/integration/lock/LockProviderRegistry.java
[R8]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/registry/events/EnchantingLorePerkHandler.java
[R9]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java
[R10]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/mixin/MixLivingEntity.java
[R11]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/common/effects/IncomingEffectPolicy.java
[R12]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/client/config/YaclConfigUiBuilder.java
[R13]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/config/storage/ConfigHolder.java
[R14]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/handler/HandlerSkill.java
[R15]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/network/packet/client/ConfigSyncCP.java
[R16]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/common/command/GlobalLimitCommand.java
[R17]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/common/capability/SkillCapability.java
[R18]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/network/ServerNetworking.java
[R19]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/integration/tconstruct/TConstructRequirementResolver.java
[R20]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/common/capability/SkillCapability.java
[R21]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/registry/events/InteractionEventHandler.java
[R22]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/mixin/MixSlot.java
[R23]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/registry/perks/Perk.java
[R24]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/mixin/MixAbstractContainerMenu.java
[R25]: https://github.com/otectus/runic-skills/blob/ee17728ea6eacb56d2651d825c9c656fb6d729cb/src/main/java/com/otectus/runicskills/common/progression/ProgressionService.java
[U1]: https://github.com/isXander/YetAnotherConfigLib/blob/566fb6c498d571281c94029f8dfbac5e5077600c/src/main/java/dev/isxander/yacl3/gui/YACLScreen.java
[U2]: https://github.com/isXander/YetAnotherConfigLib/blob/566fb6c498d571281c94029f8dfbac5e5077600c/src/main/java/dev/isxander/yacl3/config/v2/impl/ConfigClassHandlerImpl.java
[U3]: https://github.com/isXander/YetAnotherConfigLib/blob/566fb6c498d571281c94029f8dfbac5e5077600c/src/main/java/dev/isxander/yacl3/config/v2/impl/serializer/GsonConfigSerializer.java
[U4]: https://github.com/MinecraftForge/MinecraftForge/blob/3c3f496015632ab84560be77a57cc57ac42c49e1/patches/minecraft/net/minecraft/network/FriendlyByteBuf.java.patch
[U5]: https://github.com/MinecraftForge/MinecraftForge/blob/3c3f496015632ab84560be77a57cc57ac42c49e1/patches/minecraft/net/minecraft/world/entity/LivingEntity.java.patch
[U6]: https://github.com/MinecraftForge/MinecraftForge/blob/3c3f496015632ab84560be77a57cc57ac42c49e1/patches/minecraft/net/minecraft/world/item/ItemStack.java.patch
[U7]: https://github.com/ObliviousSpartan/SpartanShields/blob/89e376d70bd18b05b3b6118a932fdd2c67697f2b/src/generated/resources/data/spartanshields/recipes/stone_basic_shield.json
[U8]: https://github.com/ObliviousSpartan/SpartanWeaponry/blob/e45342c53cd2c6fb185d149019fd326e4c1ac696/src/generated/resources/data/spartanweaponry/recipes/stone_rapier.json
