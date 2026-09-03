# Runic Skills 2.0.5 — Stability, Perk Semantics, Compatibility & Inventory-Tab Remediation Specification

**Target mod:** Runic Skills  
**Target release:** `2.0.5`  
**Minecraft:** `1.20.1`  
**Loader:** Forge `47.x` — explicitly validate on **Forge 47.4.23**  
**Java:** `17`  
**Planning baseline:** current repository `master` at commit `03275acdf766f7d4c1d0cffb925e41d56597ca9b` (`2.0.4`)  
**Reported-against version:** `2.0.2`  
**Repository:** <https://github.com/otectus/runic-skills>

> **Purpose:** This is an implementation specification, not a patch. A coding agent should be able to execute the work item-by-item, produce Runic Skills 2.0.5, and verify the reported defects without requiring additional architectural context.

---

## 1. Release objective

Runic Skills 2.0.5 should be a focused correctness and compatibility release that resolves five classes of player-facing problems:

1. **Lucky Break and Mending Boost have incorrect runtime semantics.**
   - Both are currently fed into the generic passive equipment-repair loop.
   - Lucky Break should prevent durability loss probabilistically.
   - Mending Boost should increase repair caused by the vanilla Mending mechanic.
   - Neither perk should passively heal the held item merely because the player has the perk.

2. **Crafting-related Intelligence perks can participate in unsafe crafting-event behavior.**
   - At least one shared crafting handler mutates inventory without restricting itself to the logical server.
   - `Master Researcher` performs a very broad synchronous recipe/ingredient scan in the craft event.
   - `Efficient Crafting` does not actually implement its stated “do not consume materials” behavior; it approximates it by adding an output item.
   - These paths become especially risky in large modpacks and when many perks are active.

3. **Combat has multiple independently guarded secondary-damage paths rather than one coherent re-entry model.**
   - This is a credible source of cross-skill/perk interaction bugs.
   - The exact exception behind the reported Iron Golem crash **cannot be proven without the player’s crash report/stack trace**, so the implementation must both harden the known architecture and add diagnostics capable of identifying any remaining trigger.
   - 2.0.5 must not claim “save corruption” unless evidence proves it; a deterministic combat retrigger near a persisted hostile/angry entity can also make a world appear unloadable.

4. **Possible ModernFix interaction must be addressed generically, not by blaming or special-casing ModernFix.**
   - Static inspection shows a meaningful overlap: Runic Skills’ broad recipe scan calls `Ingredient.test`, while ModernFix replaces/optimizes that method.
   - This can cause Not Enough Crashes to show ModernFix frames even when Runic Skills initiated the expensive or exceptional call chain.
   - The correct fix is to remove unsafe hot-path scanning and isolate third-party recipe/ingredient failures.

5. **The inventory tab strip is fixed-position and collides with other inventory UI.**
   - The supplied screenshots show overlapping/occluded tabs rather than a simple texture corruption.
   - Current placement uses hard-coded coordinates and only knows how to offset for the recipe book.
   - It does not reserve space for CustomNPCs, potion/status-effect UI, or arbitrary neighboring tab systems.
   - 2.0.5 must make the Runic Skills strip automatically avoid common collisions **and** make its position manually movable/persistable.

The release should preserve 2.0.4’s prior audit fixes. Do not “solve” these issues by reverting the 2.0.4 remediation work.

---

## 2. Investigation baseline and evidence standard

### 2.1 Source snapshot reviewed

The specification was derived from current `master`, commit:

```text
03275acdf766f7d4c1d0cffb925e41d56597ca9b
Release 2.0.4: audit remediation, 16 findings closed
```

Relevant files inspected:

- `src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java`
- `src/main/java/com/otectus/runicskills/registry/events/CraftingEventHandler.java`
- `src/main/java/com/otectus/runicskills/registry/events/ScholarPerkHandler.java`
- `src/main/java/com/otectus/runicskills/registry/events/EnchantingLorePerkHandler.java`
- `src/main/java/com/otectus/runicskills/registry/events/FortunePerkHandler.java`
- `src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java`
- `src/main/java/com/otectus/runicskills/mixin/MixItemStack.java`
- `src/main/java/com/otectus/runicskills/mixin/MixInventoryScreen.java`
- `src/main/java/com/otectus/runicskills/client/gui/DrawTabs.java`
- `src/main/java/com/otectus/runicskills/handler/HandlerConfigClient.java`
- `src/main/java/com/otectus/runicskills/integration/LegendaryTabsIntegration.java`
- `src/main/java/com/otectus/runicskills/mixin/RunicSkillsMixinPlugin.java`
- `src/main/resources/runicskills.mixins.json`
- `gradle.properties`
- `CHANGELOG.md`
- `docs/Runic_Skills_2.0.4_Remediation_Spec.md`

ModernFix comparison point:

- `embeddedt/ModernFix`
- `src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_ingredients/IngredientMixin.java`
- inspected at commit `66d45c2b95085f1071dfa19a32896e2855dfb799`

### 2.2 Confidence labels used below

| Label | Meaning |
|---|---|
| **CONFIRMED** | The current source directly contains behavior sufficient to establish the defect or mismatch. |
| **HIGH-CONFIDENCE RISK** | Static source reveals a concrete unsafe pattern that strongly matches the report, but the exact reported exception was not available. |
| **UNCONFIRMED** | The report is plausible, but a crash report/log or runtime reproduction is required before attributing a specific exception/root cause. |

The coding agent must preserve this distinction in commits and changelog language. Do not present a hypothesis as a diagnosed crash cause.

---

## 3. Findings summary

| ID | Severity | Finding | Confidence | 2.0.5 disposition |
|---|---:|---|---|---|
| RS-205-01 | High | Lucky Break is wired into passive periodic repair instead of durability-loss avoidance | **CONFIRMED** | Rewire into durability damage path |
| RS-205-02 | High | Mending Boost is wired into passive periodic repair instead of Mending XP repair | **CONFIRMED** | Rewire into `ExperienceOrb` Mending path |
| RS-205-03 | High | Shared craft reward handler can mutate inventory on the client because it lacks a logical-server guard | **CONFIRMED** | Make all craft mutation server-authoritative |
| RS-205-04 | High | Efficient Crafting tooltip semantics do not match implementation; “saved materials” is implemented as bonus output | **CONFIRMED** | Implement true consumed-input preservation/refund |
| RS-205-05 | High | Master Researcher scans up to thousands of recipes synchronously from `ItemCraftedEvent`, invoking third-party ingredient logic | **CONFIRMED** | Replace hot scan with cached/bounded reverse lookup |
| RS-205-06 | High | Third-party recipe/ingredient failures can escape the Master Researcher scan and crash crafting | **HIGH-CONFIDENCE RISK** | Per-entry `RuntimeException` isolation + log-once |
| RS-205-07 | Medium/High | Master Artificer scans modded enchantments and calls `canEnchant` without per-entry isolation | **HIGH-CONFIDENCE RISK** | Harden integration boundary |
| RS-205-08 | Critical-risk architecture | Runic secondary damage is emitted through several `.hurt(...)` paths with ad-hoc/local guards instead of one origin/depth model | **CONFIRMED architecture risk** | Add central damage context/re-entry policy |
| RS-205-09 | Unknown exact severity | Reported multi-skill + Wisdom + Iron Golem crash cannot be assigned to one perk without stack trace | **UNCONFIRMED exact cause** | Reproduce, harden, instrument, test save reload |
| RS-205-10 | Medium/High | ModernFix is plausibly present in crafting stack traces because Runic Skills aggressively calls `Ingredient.test`; no direct Runic→ModernFix mixin dependency was found | **HIGH-CONFIDENCE interaction explanation** | Fix generic recipe contract usage; no ModernFix special-case unless reproduction proves necessary |
| RS-205-11 | Medium | Runic inventory tabs use fixed hard-coded placement and can overlap CustomNPCs/other tabs/effect display | **CONFIRMED** | Introduce layout engine, AUTO avoidance, manual position |
| RS-205-12 | Medium | Inventory tab render/click integration is mixin-bound to `InventoryScreen` with no generic reserved-region API | **CONFIRMED** | Prefer Forge screen events + shared layout model |
| RS-205-13 | Medium | 2.0.2 fixed the tab close-reset conflict, but not tab collision/placement | **CONFIRMED** | Preserve close-reset fix; address layout separately |

---

# PART I — DURABILITY PERKS

## 4. RS-205-01 — Lucky Break must prevent durability loss, not repair items

### 4.1 Current behavior

In `PerkEffectsHandler`’s periodic attribute/tick processing, `repairRate` is accumulated from multiple perks. Current source includes both:

```java
if (PerkUtil.isPerkActive(player, Perks.MENDING_BOOST)) {
    repairRate += c.mendingBoostPercent;
}
if (PerkUtil.isPerkActive(player, Perks.LUCKY_BREAK)) {
    repairRate += c.luckyBreakPercent;
}
```

Later, when `repairRate > 0`, the handler probabilistically derives a repair amount and directly reduces `ItemStack` damage for the first damaged equipment stack it finds.

This is why the player observes an item in the main-hand/equipment set continually regaining durability.

The tooltip contract for Lucky Break is effectively:

> Tool durability loss has a configured chance to be ignored.

Passive item healing is not equivalent.

### 4.2 Required implementation

Remove Lucky Break from the passive repair accumulator entirely.

Lucky Break belongs in the point where durability damage is about to be applied. Runic Skills already has the correct architectural hook: `MixItemStack`, which modifies the damage amount passed through `ItemStack.hurt(...)` and already composes durability-avoidance perks such as Unbreakable/Unbreaking Mastery/Gadgeteer/Lock Expert.

Add Lucky Break to that single avoidance calculation.

### 4.3 Eligibility must match the tooltip

Do **not** silently make Lucky Break protect every damageable stack merely because `ItemStack.isDamageableItem()` is true.

Create a helper such as:

```text
com.otectus.runicskills.common.durability.DurabilityPerkRules
```

with a method conceptually equivalent to:

```java
boolean isLuckyBreakEligible(ItemStack stack)
```

Recommended 2.0.5 eligibility contract:

- eligible:
  - vanilla/tool-like `TieredItem`
  - shears
  - items explicitly included in a Runic Skills data tag
- not eligible by default:
  - armor
  - purely cosmetic durability-bearing items
  - arbitrary modded items whose durability has non-tool semantics

Recommended extensibility:

```text
data/runicskills/tags/items/lucky_break_eligible.json
data/runicskills/tags/items/lucky_break_ineligible.json   // optional override
```

If adding tags is judged too large for 2.0.5, implement a narrow class-based rule now and leave a clearly documented extension point. Do not broaden to all equipment simply for convenience.

### 4.4 Probability composition

`MixItemStack` currently composes multiple durability-avoidance chances and caps the aggregate chance. Preserve the established cap unless there is an intentional balance change.

Recommended:

```java
double chance = existingAvoidanceChance;

if (isLuckyBreakEligible(stack) && hasLuckyBreak(player)) {
    chance += config.luckyBreakPercent / 100.0D;
}

chance = Mth.clamp(chance, 0.0D, 0.90D);
```

Then use the existing per-durability-point roll behavior.

Requirements:

- no negative chance
- no `NaN`
- no probability above the established cap
- no client-only authoritative durability mutation
- one-point and multi-point durability events must both behave predictably

### 4.5 Acceptance tests

1. Player has Lucky Break, damaged pickaxe in main hand, stands idle for five real minutes:
   - **damage value must not decrease**.
2. Damage the eligible tool 10,000 one-point iterations using a seeded RNG in a unit/game test:
   - observed avoidance rate must be within a sensible statistical tolerance of configured chance.
3. Armor takes durability damage with Lucky Break active:
   - Lucky Break alone must not protect it if armor is outside the chosen tooltip contract.
4. Combine Lucky Break with every existing avoidance perk:
   - aggregate probability must never exceed cap.
5. Damage an item by more than one durability point:
   - behavior must use the established per-point semantics, not all-or-nothing unless the existing system explicitly defines otherwise.
6. Modded eligible tool included by tag:
   - perk applies.
7. Modded excluded item:
   - perk does not apply.

### 4.6 Definition of done

- Lucky Break never contributes to periodic repair.
- Its tooltip and runtime behavior agree.
- Existing durability perks continue to work.
- No duplicate roll path is introduced elsewhere.

---

## 5. RS-205-02 — Mending Boost must amplify actual Mending repair

### 5.1 Current behavior

Mending Boost currently contributes to the same periodic `repairRate` accumulator as passive auto-repair perks.

Its contract, however, is:

> Mending repair rate increased by the configured percentage.

That means the perk should matter **only when vanilla Mending is repairing an item from XP**.

### 5.2 Required hook

For Minecraft 1.20.1, implement a narrow mixin around the vanilla Mending processing in:

```text
net.minecraft.world.entity.ExperienceOrb#repairPlayerItems(Player, int)
```

Prefer modifying the Mending repair amount in that method rather than globally changing an XP conversion helper with no player context.

Suggested new file:

```text
src/main/java/com/otectus/runicskills/mixin/MixExperienceOrb.java
```

and register it in:

```text
src/main/resources/runicskills.mixins.json
```

### 5.3 Required semantics

Given:

```text
vanillaRepair = vanilla amount repairable from the consumed XP
boost = mendingBoostPercent / 100
```

compute something equivalent to:

```java
boostedRepair = floor(vanillaRepair * (1.0 + boost))
```

with these invariants:

- never repair more than the selected stack’s current damage
- never generate repair when no XP orb is being processed
- never apply to a non-Mending item
- never repair for a player who does not have the perk
- preserve vanilla item-selection behavior among multiple Mending items
- preserve vanilla leftover-XP recursion/flow
- do not spend additional XP solely because the perk grants bonus repair unless the perk description is changed to say that

Example:

```text
Vanilla repair from 1 XP: 2 durability
Mending Boost: +50%
2 × 1.5 = 3 durability repaired
```

### 5.4 Compatibility constraint

Avoid copying the entirety of vanilla `repairPlayerItems` unless necessary. A small expression/argument modification is less likely to conflict with other XP/Mending mods.

If a full overwrite/cancel-and-reimplement approach becomes unavoidable:

- document why
- reproduce vanilla selection/remainder behavior exactly
- add a mixin conflict audit
- test with common XP/Mending-changing mods

### 5.5 Acceptance tests

| Scenario | Expected |
|---|---|
| Mending Boost active, damaged non-Mending item, XP collected | No perk repair |
| Mending Boost active, damaged Mending item, no XP | No repair |
| Mending Boost inactive, Mending item + XP | Vanilla repair |
| +50% boost, 2 vanilla repair | 3 repair |
| repair would exceed remaining damage | clamp to zero damage, never negative |
| multiple Mending items | preserve vanilla target selection |
| idle five minutes with damaged held item | zero repair from Mending Boost |

---

## 6. Passive repair loop cleanup

After removing Lucky Break and Mending Boost, review every remaining contributor to `repairRate`.

For each contributor, classify it explicitly as one of:

- **true passive repair** — valid in periodic tick
- **event-triggered repair** — move to relevant event
- **durability prevention** — move to `ItemStack.hurt`
- **Mending-specific** — move to XP/Mending hook

Do not leave semantics encoded merely by sharing a numeric `repairRate` variable.

Recommended refactor:

```java
PassiveRepairAccumulator
DurabilityAvoidanceCalculator
MendingRepairModifier
```

These can be lightweight helpers rather than large framework classes; the goal is semantic separation so this mistake cannot recur.

---

# PART II — CRAFTING STABILITY AND INTELLIGENCE PERKS

## 7. RS-205-03 — Make all crafting mutation server-authoritative

### 7.1 Confirmed defect

`PerkEffectsHandler.onCraft(PlayerEvent.ItemCraftedEvent)` currently obtains a generic `Player`, excludes `FakePlayer`, then may insert a bonus result into the player inventory.

It does **not** first require:

```java
player instanceof ServerPlayer
```

or otherwise reject `player.level().isClientSide`.

An `ItemCraftedEvent` can participate in client and server execution paths. Inventory mutation from both logical sides is a classic source of ghost stacks, desynchronization, duplicate effects, and unstable interaction with other inventory/crafting mods.

### 7.2 Required correction

Any handler that changes:

- inventory contents
- item counts
- recipe unlock state
- perk proc state
- persistent skill data
- server-authoritative XP/rewards

must begin from a `ServerPlayer`.

Canonical shape:

```java
@SubscribeEvent
public void onCraft(PlayerEvent.ItemCraftedEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) {
        return;
    }
    ...
}
```

If fake-player crafting is intentionally unsupported, preserve an explicit guard or document that `FakePlayer` is rejected by the `ServerPlayer`/specific check.

### 7.3 Audit all crafting listeners

Do not fix only the handler named in the report. Search the entire codebase for:

- `ItemCraftedEvent`
- `PlayerEvent.ItemCraftedEvent`
- `ResultSlot`
- `CraftingContainer`
- `Recipe`
- direct inventory insertions during craft
- recipe unlock calls
- enchantment application during craft

Every path must state its side authority.

---

## 8. RS-205-04 — Efficient Crafting must not counterfeit “saved materials” with bonus output

### 8.1 Confirmed semantic mismatch

Current shared crafting logic includes multiple perk chances and, on success, copies the crafted result and inserts one additional result item.

For `Efficient Crafting`, the intended behavior is described as a chance to **not consume crafting materials**.

Those are not equivalent.

Examples where “+1 output” is incorrect:

- recipe output count is greater than one
- recipe has several expensive ingredients
- recipe uses container items/remainders
- crafting converts one unique item into multiple outputs
- modded recipes have custom remainder logic
- recipe’s material value is not equivalent to exactly one result item

This can become an item-value duplication bug.

### 8.2 Required design

Implement Efficient Crafting at the crafting transaction boundary.

Preferred architecture:

```text
MixResultSlot
CraftingTransactionContext
```

The system must snapshot relevant input state **before** vanilla consumes the recipe, then allow vanilla to complete its normal result-take/remainder handling, and only afterward restore/refund the units that were actually consumed when the perk proc succeeds.

### 8.3 Transaction model

Suggested context:

```java
record CraftingTransaction(
    UUID playerId,
    ResourceLocation recipeId,
    List<ItemStack> inputsBefore,
    ItemStack resultBeforeTake,
    long sequence
) {}
```

Do not persist this data.

Use a scope with guaranteed cleanup:

```java
try {
    context.push(...);
    // vanilla result take / consume
} finally {
    context.clear(...);
}
```

A `ThreadLocal` can be appropriate for a narrow synchronous vanilla crafting call if the implementation guarantees nested safety; a menu/slot-scoped context may be safer if multiple hooks are needed.

### 8.4 Refund algorithm requirements

When Efficient Crafting procs:

1. Compare pre-consumption input slots with post-vanilla input/remainder slots.
2. Determine what ingredient units vanilla actually consumed.
3. Restore only those units.
4. Do **not** duplicate:
   - empty buckets
   - bottles
   - damaged reusable tools
   - recipe remainders
   - custom container items
5. Never restore more complete recipe sets than the actual number of crafted operations.
6. Shift-click/mass craft must be handled per real execution count.
7. If the modded menu bypasses the vanilla path and consumption cannot be inferred safely:
   - do not guess
   - skip the refund for that unsupported transaction
   - optionally emit one debug/log-once compatibility message naming the menu/recipe class

### 8.5 Alternative only if exact refund is infeasible for 2.0.5

The only acceptable reduced-scope alternative is to change the perk contract itself to truthfully say:

> Chance to create one bonus crafted output.

That would be a balance/design change, not a bug fix. Because the player report is about correctness, the preferred 2.0.5 path is to implement actual material preservation.

Do not keep a misleading tooltip.

---

## 9. Separate crafting perk proc chances

The existing shared craft handler accumulates multiple perk percentages into one numeric `chance`.

That means activating many crafting perks can cause:

- aggregate probability above `1.0`
- unconditional bonus output
- one perk changing the effective behavior of another
- inability to reason about which perk proc produced a reward

Refactor each perk to independent semantics.

Example:

```java
if (hasAssemblyLine(player) && roll(config.assemblyLinePercent)) {
    applyAssemblyLine(...);
}

if (hasMassProduction(player) && roll(config.massProductionPercent)) {
    applyMassProduction(...);
}

if (hasEfficientCrafting(player) && roll(config.efficientCraftingPercent)) {
    markMaterialPreservation(...);
}
```

If two perks intentionally stack into a single proc, encode that as a named formula and test it. Do not accidentally stack them by summing unrelated percentages.

Probability utility requirements:

```java
double chance01(double percent)
```

must:

- reject or clamp non-finite values
- normalize to `[0, 1]`
- make the percent/unit convention explicit

---

## 10. Craft re-entry guard

Introduce a narrow crafting reward/application guard so a Runic-created reward cannot trigger another Runic craft reward through an unusual menu/mod callback.

Conceptually:

```text
CraftingExecutionGuard
- player UUID
- recipe/result identity
- nested depth
```

Requirements:

- no persistent state
- `try/finally`
- nested call safe
- cleanup even when a third-party recipe throws
- do not suppress a legitimate later craft merely because an earlier transaction failed

---

# PART III — MASTER RESEARCHER, MASTER ARTIFICER, AND MODERNFIX

## 11. RS-205-05/06 — Master Researcher must stop scanning thousands of recipes in the craft event

### 11.1 Confirmed current pattern

`ScholarPerkHandler.onItemCrafted` contains a `MAX_RECIPES_SCANNED = 4096` safeguard, but it still performs a potentially enormous synchronous recipe walk after a craft.

For candidate recipes it obtains ingredient data and performs matching including calls equivalent to:

```java
candidate.test(crafted)
craftedIngredient.test(candidate.getItems()[0])
```

This has three problems:

1. **Hot-path cost:** thousands of recipes can be visited because the player clicked a crafting result.
2. **Third-party execution:** `Recipe#getIngredients`, `Ingredient#getItems`, and `Ingredient#test` can execute modded or transformed code.
3. **Exception blast radius:** one malformed/buggy third-party recipe/ingredient can abort the player’s crafting event.

This is especially relevant to the user’s report that maxed Intelligence plus crafting perks can crash.

### 11.2 Replace with a reverse index

Build a `MasterResearcherRecipeIndex` from the active `RecipeManager`.

The index should be prepared:

- on server/datapack recipe reload
- invalidated/rebuilt when recipes change
- cleared on server shutdown

Candidate keys can include:

- direct ingredient item IDs
- resolved ingredient item entries
- tags where safely resolvable
- output item ID where useful to the perk’s intended relation

Do not require a full scan at craft time.

Craft-time flow should look like:

```text
crafted stack
    ↓
lookup candidate recipe IDs from index
    ↓
bounded candidate verification
    ↓
filter already-known recipes
    ↓
perk RNG
    ↓
award unlock(s)
```

### 11.3 Bounded fallback

If some dynamic/custom ingredient cannot be indexed statically:

- put it in a small fallback bucket
- enforce a strict candidate budget
- do not scan the entire recipe registry on every craft
- record a debug metric when the budget is exhausted

A budget should be expressed as a candidate verification limit, not “scan 4096 and hope.”

### 11.4 Third-party exception isolation

Every call across a mod-defined recipe/ingredient boundary should be treated as untrusted extension code.

Catch:

```java
RuntimeException
```

around the **single candidate** being evaluated.

Do **not** catch:

- `Throwable`
- `Error`
- JVM fatal conditions

On failure:

- skip the broken candidate
- continue remaining candidates
- log once per recipe/class/error signature at WARN or DEBUG according to severity
- include recipe ID and owner namespace when available
- do not spam one log per craft

Example diagnostic:

```text
[Runic Skills] Master Researcher skipped recipe some_mod:foo because its ingredient
matcher threw IllegalStateException. The craft was allowed to continue.
```

### 11.5 No partial reward state

Collect candidate recipe IDs first. Only mutate unlock/research state after candidate evaluation completes.

This prevents:

```text
unlock A
third-party recipe throws
event aborts
half-completed perk state
```

---

## 12. RS-205-07 — Harden Master Artificer and similar registry scans

`EnchantingLorePerkHandler.onItemCrafted` is already server-player-oriented, which is good, but Master Artificer evaluates registered enchantments and can invoke mod-defined `Enchantment#canEnchant(crafted)`.

Apply the same boundary rule:

- isolate each mod-defined callback with `RuntimeException`
- log once
- skip only the faulty entry
- never let one optional enchantment crash an ordinary crafting event

Also consider a deterministic filter before calling `canEnchant`:

- skip null/empty entries
- skip obviously incompatible categories where vanilla metadata can answer safely
- avoid repeated full registry work if results can be cached per item class/item ID and reload lifecycle

---

## 13. RS-205-10 — ModernFix compatibility: what is and is not proven

### 13.1 What static inspection shows

Runic Skills does not appear to have a direct mixin targeting a ModernFix class, and its mixin plugin contains optional-integration gating for its own supported integrations rather than a ModernFix-specific patch.

ModernFix’s `perf.faster_ingredients` mixin replaces/optimizes `Ingredient.test(ItemStack)` using cached item/tag structures.

Master Researcher performs a large number of `Ingredient.test` / ingredient-resolution calls from a crafting event.

Therefore a crash report collected by Not Enough Crashes may legitimately contain a ModernFix-transformed `Ingredient.test` frame even when the initiating behavior is Runic Skills’ recipe scan.

### 13.2 What must not be claimed

Without the user’s actual crash report:

- do not claim ModernFix is broken
- do not claim ModernFix caused the craft crash
- do not add `if (ModernFixLoaded)` behavior merely to hide the symptom
- do not disable Master Researcher only when ModernFix is present

### 13.3 Correct compatibility strategy

Make Runic Skills a well-behaved caller:

- no broad synchronous scan
- bounded candidate work
- stable cache lifecycle
- no mutation from client craft event
- per-entry RuntimeException isolation
- no assumptions about concrete `Ingredient` implementation
- no reliance on mutable arrays returned by another mod
- no repeated `getItems()` calls for the same candidate in the same transaction

### 13.4 Required runtime matrix

Test:

| Runic Skills | ModernFix | Not Enough Crashes | Expected |
|---|---|---|---|
| 2.0.5 | Off | Off | Stable |
| 2.0.5 | On | Off | Stable |
| 2.0.5 | Off | On | Stable |
| 2.0.5 | On | On | Stable |

Use Forge `47.4.23` for at least one full matrix pass.

If a crash remains **only** with ModernFix enabled after this refactor, capture:

- full crash report
- `latest.log`
- mixin audit/export for the implicated class
- ModernFix version
- exact recipe/item used
- top and bottom of the exception chain

Only then add a targeted compatibility shim.

---

# PART IV — COMBAT / WISDOM / MULTI-SKILL CRASH

## 14. RS-205-08/09 — Known architecture and diagnosis boundary

### 14.1 Reported reproduction

The player reported:

- multiple skills maxed, including Strength, Endurance, Fortune, etc.
- Wisdom then maxed with many perks
- attack an Iron Golem
- crash
- world subsequently crashes whenever loaded/played
- Wisdom-only maxed in a fresh world did **not** reproduce

This strongly suggests a combinatorial path, but does not identify which perk pair or exception.

### 14.2 Confirmed source architecture risk

`CombatEventHandler` and other handlers emit secondary damage by calling `LivingEntity#hurt(...)`.

Examples include:

- Strength `LIMIT_BREAKER`
- Cleave/splash behavior
- defensive reflection such as `BULWARK`
- other power/integration-specific secondary damage paths

Some have a local guard. For example, cleaving uses a `ThreadLocal<Set<UUID>>` to avoid re-entering its own cleave path.

However, there is no single global concept of:

```text
this damage came from Runic Skills secondary effect X
depth = N
which normal perk handlers are allowed to run on it
```

As a result, one secondary damage event can re-enter generic Forge combat events and be seen by unrelated Wisdom, Strength, magic, defensive, or compatibility handlers.

Wisdom’s outgoing damage modifiers such as enchantment-based bonuses do not need to be faulty in isolation to participate in a problematic nested call generated by another skill.

### 14.3 Why an Iron Golem is a useful stress target

An Iron Golem creates a useful reproduction environment because:

- it can take player melee damage
- it retaliates
- retaliation exercises incoming/defensive perk paths
- it has enough health for multiple proc chains
- player attack plus retaliation can expose nested outgoing/incoming/reflection behavior quickly

This is a test characteristic, not proof that Iron Golem code itself is defective.

---

## 15. Introduce a unified `DamageContext`

Add a central transient combat context, e.g.:

```text
src/main/java/com/otectus/runicskills/common/combat/DamageContext.java
```

Suggested origin enum:

```java
enum Origin {
    PRIMARY,
    LIMIT_BREAKER,
    CLEAVE,
    BULWARK_REFLECT,
    CHAIN_LIGHTNING,
    POWER_ECHO,
    SPELL_EFFECT,
    OTHER_RUNIC_SECONDARY
}
```

Suggested frame:

```java
record Frame(
    UUID owner,
    Origin origin,
    int depth
) {}
```

Storage:

```java
ThreadLocal<Deque<Frame>>
```

or another server-thread-scoped stack.

### 15.1 Required API shape

```java
try (DamageContext.Scope ignored =
         DamageContext.push(player.getUUID(), Origin.LIMIT_BREAKER)) {
    target.hurt(source, amount);
}
```

`Scope.close()` must pop exactly its own frame.

Invariants:

- always `try/finally` / `AutoCloseable`
- nested scopes supported
- no cross-player contamination
- no persistent/NBT state
- clear root stack after outermost call
- diagnostics available without exposing mutable stack internals

### 15.2 Hard depth ceiling

Set a conservative safety ceiling, e.g. `4`, for Runic-generated secondary damage.

If a secondary proc attempts to exceed it:

- do not emit another secondary damage call
- rate-limit a warning/debug diagnostic
- preserve the original combat event

This protects against Runic↔third-party reflection loops.

The ceiling is a safety net; correct origin policy should normally stop recursion earlier.

---

## 16. Define an explicit secondary-damage policy

Do not let each event handler guess whether a secondary damage event should get all normal perks.

Recommended policy:

| Origin | Standard outgoing multipliers? | Can create another same-origin proc? | Notes |
|---|---:|---:|---|
| `PRIMARY` | Yes | Yes, once according to perk rules | Normal player attack |
| `LIMIT_BREAKER` | **No** | No | Already bonus damage; do not multiply into another full attack |
| `CLEAVE` | No | No | Preserve current splash intent |
| `BULWARK_REFLECT` | No | No | Reflection must not become a new melee perk chain |
| `CHAIN_LIGHTNING` | Usually no | No | Use the power’s own scaling |
| `POWER_ECHO` | Usually no | No | Prevent self-amplification |
| `SPELL_EFFECT` | Policy-defined | No by default | Only magic-specific modifiers if explicitly intended |

A coding agent must audit every Runic Skills `.hurt(...)` call and assign it an origin.

Do not leave unclassified secondary-damage emissions after 2.0.5.

---

## 17. Apply context checks to generic damage handlers

Handlers that multiply or add to `LivingHurtEvent` damage should ask the central context whether they are allowed to process the event.

Example:

```java
if (!DamageContext.currentPolicy().allowsStandardOutgoingModifiers()) {
    return;
}
```

Replace/adapt local `CLEAVING` checks where the shared context supersedes them.

Do not retain two independent guard systems that can disagree.

---

## 18. Numeric hardening

Before committing a modified damage amount:

```java
double multiplier = ...;
float original = event.getAmount();
```

validate:

```text
multiplier finite
multiplier >= 0
result finite
result >= 0
```

If invalid:

- keep the original valid damage amount
- rate-limit a diagnostic
- include active perk IDs and origin in debug mode

Do not convert `NaN`/infinity into an arbitrary huge damage value.

If a maximum damage clamp is added, it must be justified and documented rather than silently changing balance.

---

## 19. Audit scope for secondary damage

Search the entire repository for:

```text
.hurt(
hurt(
LivingEntity#hurt
DamageSource
thorns(
playerAttack(
```

Include:

- base event handlers
- powers
- integrations
- spell-related handlers
- reflected damage
- AoE
- chain/echo effects
- delayed tasks/callbacks

For every `.hurt` call, record:

```text
file
perk/power
origin enum
whether standard outgoing perks may reapply
whether it can recurse
guard used
```

Keep this as a code comment table, test fixture, or development checklist until all calls are classified.

---

## 20. Reproduce and bisect the player’s crash

Because the stack trace is absent, runtime diagnosis remains part of the 2.0.5 implementation task.

### 20.1 Minimum reproduction

Create a clean Forge `47.4.23` test instance.

Test A — control:

1. Max Wisdom to level 32.
2. Activate all/large set of Wisdom perks.
3. Attack Iron Golem repeatedly.
4. Allow retaliation.
5. Save/quit/reload.

Test B — reported-style:

1. Max Strength to 32 + perks.
2. Max Endurance to 32 + perks.
3. Max Fortune to 32 + perks.
4. Add other commonly maxed trees from the report.
5. Max Wisdom last to 32 + perks.
6. Use an enchanted melee weapon so Wisdom enchantment-based damage perks are eligible.
7. Attack Iron Golem with:
   - normal attack
   - critical hit
   - sweep-capable attack
8. Allow golem to retaliate.
9. Repeat.

### 20.2 Binary/perk-family bisect

If Test B crashes:

1. Keep Wisdom enabled.
2. Toggle half of the other active skill trees off.
3. Re-run.
4. Narrow to one cross-tree pair.
5. Then bisect perks within each tree.

Prioritize:

- secondary-damage perks
- reflected-damage perks
- damage multipliers
- enchanted-item-based Wisdom bonuses
- low-health execute effects
- sweep/cleave effects

### 20.3 Stress pass

After the immediate crash is fixed:

- 1,000+ automated/mechanical attack cycles
- normal + enchanted weapon
- multiple targets
- golem retaliation
- player at high/low health
- armor on/off
- all relevant perks enabled

No unbounded stack growth, no repeated secondary-proc storm, no NaN/Inf damage.

---

## 21. “World crashes on load” recovery requirement

Do **not** automatically label the save corrupted.

A plausible failure mode is:

1. player and angry golem are saved near one another
2. world loads
3. combat/AI immediately resumes
4. same deterministic bad perk interaction fires
5. server crashes again

2.0.5 acceptance criterion:

> A save that repeatedly crashed on 2.0.2 due to the reproduced Runic combat chain must load and remain stable after the fixed code is installed, without deleting the player’s skill/perk data.

For development recovery/testing, it is acceptable to make a copy and:

- remove/relocate the golem
- relocate the player
- temporarily disable the isolated perk

to determine whether the save itself is corrupt or merely retriggering the same event.

Never ship code that silently strips a player’s perks on login.

---

## 22. Combat diagnostics

Add an opt-in debug config or logger category, default **off**.

When enabled, rate-limited lines should include:

```text
attacker UUID
target entity type + UUID
DamageSource msg id/type
DamageContext origin
depth
active relevant perk IDs
damage before
damage after
secondary effect attempted/emitted/suppressed
```

Do not log every combat event unconditionally in production.

When depth suppression fires, log once per short interval.

This will make any remaining player crash actionable.

---

# PART V — INVENTORY TAB GLITCHING, CUSTOMNPCS, EFFECTS, AND MOVABLE UI

## 23. RS-205-11/12 — What the screenshots indicate

The supplied screenshots show multiple tab systems attempting to occupy the same top inventory band:

- white-backed tabs from another UI/mod
- Runic Skills’ darker bordered tabs
- icons being partially occluded or drawn through one another
- tooltips occupying the same area

This is consistent with **coordinate collision and render-order overlap**, not simply a broken Runic icon texture.

The user explicitly states:

- Legendary Tabs is not installed
- CustomNPCs conflicts with the strip
- effects UI also conflicts
- they want the Runic Skills tabs movable

That request should be treated as a first-class 2.0.5 UI requirement.

---

## 24. Confirmed current layout limitation

`DrawTabs` computes tab placement from fixed assumptions equivalent to:

```java
tabX = (scaledWidth - textureWidth) / 2
     + type.index() * 27
     + recipeOffset;

tabY = (scaledHeight - textureHeight) / 2 - 28;
```

The only explicit alternate-space adjustment is a fixed recipe-book offset.

There is no concept of:

- active-effects reserved region
- other mods’ tab rectangles
- CustomNPCs inventory controls
- alternate anchor
- X/Y user offset
- screen-bound clamping
- persisted drag position

`MixInventoryScreen` injects rendering into `InventoryScreen.renderBg` and click handling into `mouseClicked`.

2.0.2’s Highlighter fix changed the **close/reset** mechanism; it did not solve placement collisions.

---

## 25. Introduce `InventoryTabLayout`

Add:

```text
src/main/java/com/otectus/runicskills/client/gui/InventoryTabLayout.java
```

Responsibilities:

- derive actual inventory screen bounds
- build rectangles for each Runic tab
- choose anchor
- apply manual offsets
- clamp/validate on-screen placement
- calculate reserved-region intersections
- expose the exact same rectangles to rendering, hover, tooltip, and click detection

Never calculate click bounds separately from render bounds.

Suggested data:

```java
record TabLayout(
    List<TabRect> tabs,
    Rectangle stripBounds,
    Anchor resolvedAnchor
) {}
```

---

## 26. Client configuration

Extend `HandlerConfigClient` with:

```toml
inventoryTabsEnabled = true
inventoryTabsAnchor = "AUTO"
inventoryTabsOffsetX = 0
inventoryTabsOffsetY = 0
inventoryTabsAvoidRecipeBook = true
inventoryTabsAvoidEffects = true
inventoryTabsDragToMove = true
```

Recommended anchor enum:

```text
AUTO
TOP_LEFT
TOP_RIGHT
LEFT
RIGHT
BOTTOM_LEFT
BOTTOM_RIGHT
```

Suggested offset limits:

```text
-500 .. 500
```

Offsets must be applied relative to the chosen anchor, not global screen origin.

### 26.1 Persistence

Manual movement must persist across:

- closing/reopening inventory
- world changes
- game restart

It is client-local and must not affect server/network compatibility.

---

## 27. AUTO placement behavior

AUTO should evaluate a deterministic list of candidate anchors.

For each candidate:

1. build Runic strip rectangle
2. apply safe spacing
3. verify screen bounds
4. reject if intersecting any reserved rectangle
5. choose first valid candidate
6. apply manual offset afterward, then clamp if necessary

Suggested priority can remain top-oriented to preserve existing UX, but collision avoidance is more important than exact legacy position.

---

## 28. Reserved regions

### 28.1 Vanilla inventory body

Use the actual `InventoryScreen` geometry:

```text
leftPos
topPos
imageWidth
imageHeight
```

Do not re-derive it solely from `scaledWidth`, `scaledHeight`, and magic texture constants.

### 28.2 Recipe book

When visible:

- reserve its real rectangle
- do not use a magic `+77` as the only compatibility rule

### 28.3 Active effects

When potion/status effects render next to the inventory:

- reserve the actual effect display region
- AUTO must relocate the Runic strip rather than draw through effect icons/text

Test both compact icon-only and text/list layouts if vanilla switches presentation according to available width.

### 28.4 CustomNPCs and other mods

Do not hard-code a fragile pixel offset just for one CustomNPCs build.

Add a small extensibility mechanism such as:

```text
InventoryTabLayoutEvent
```

or a client registry:

```java
InventoryTabReservedRegionRegistry
```

that allows compatibility code to contribute `Rectangle`/`Rect2i` reserved regions for the current screen.

This gives Runic Skills a generic way to coexist with:

- CustomNPCs
- future inventory-tab mods
- equipment-slot mods
- quest/status side panels

If CustomNPCs exposes no stable API for its actual screen rectangles, 2.0.5 should still work via AUTO candidates + manual movement. A version-specific reserved-region adapter may be added only if it can be implemented safely.

---

## 29. Make the strip manually movable

At minimum, offsets must be editable in client config.

Recommended in-game interaction:

- hold **Shift** while hovering the Runic tab strip
- drag to move the strip
- normal click without Shift retains tab activation
- release mouse to persist offset
- display a subtle move cursor/outline or tooltip while Shift-drag mode is active

Alternative:

- a small “Move Runic Skills tabs” toggle in config/UI

Requirements:

- do not steal normal inventory clicks
- do not activate a tab while dragging
- do not move other mods’ tabs
- clamp strip so it remains recoverable/on-screen
- provide a reset-to-default command/button/config reset path

Optional command if no config GUI exists:

```text
/runicskills client resetTabs
```

Only implement a command if the project already has an appropriate client-command pattern. Otherwise expose reset through config/default deletion.

---

## 30. Render and input integration

Prefer migrating built-in Runic tab rendering from the `renderBg` mixin injection to Forge screen events:

```text
ScreenEvent.Render.Post
ScreenEvent.MouseButtonPressed.Pre
ScreenEvent.Closing
```

Benefits:

- clearer render order
- less direct contention with other `InventoryScreen` mixins
- shared event-side layout calculation
- tooltip can render after the inventory background
- preserves the 2.0.2 lesson: avoid implicit/competing screen overwrites

If Forge 1.20.1 event ordering makes the exact `Render.Post` choice unsuitable, use the least invasive event phase that:

- renders above the inventory background
- does not cover final vanilla/mod tooltips incorrectly
- keeps clicks synchronized to visible rectangles

Keep `MixInventoryScreen` only for behavior that cannot be expressed reliably through Forge events.

---

## 31. Preserve existing tab integrations

Current special behavior must not regress:

- when Legendary Tabs is detected and is meant to own tab presentation, the built-in Runic strip remains suppressed
- L2/native tab integration remains honored
- the 2.0.2 close/reset fix remains effective

Do not interpret “user has no Legendary Tabs” as a reason to remove compatibility logic for users who do.

---

## 32. Tooltip and z-order rules

A Runic tooltip should appear only if:

- pointer is over the final laid-out Runic tab rectangle
- no drag is in progress
- that tab is actually visible/enabled

The icon and tab background must render in one coherent layer.

Do not “fix” visual overlap by assigning an extreme Z value that simply covers CustomNPCs/effects. Collision avoidance is the correct solution.

---

## 33. UI test matrix

Test all of these manually at GUI scales 1, 2, 3, and 4 where practical:

| Case | Expected |
|---|---|
| Vanilla inventory, no effects | legacy-like clean placement |
| Recipe book closed | no shift unless needed |
| Recipe book open | no overlap |
| 1 active effect | no overlap |
| many active effects | no overlap |
| CustomNPCs only | Runic strip remains readable/clickable |
| CustomNPCs + effects | AUTO chooses alternate location |
| CustomNPCs + recipe book + effects | AUTO or user offset provides usable placement |
| narrow window | tabs remain on-screen |
| wide window | stable anchor |
| Legendary Tabs present | built-in strip suppressed according to existing integration |
| L2/native tabs present | existing ownership behavior preserved |
| manual Shift-drag | strip moves without opening tab |
| restart game | moved position persists |
| reset | returns to AUTO/default offset |
| tooltip | never appears for stale pre-move rectangle |
| click after move | uses moved rectangle |

Take before/after screenshots as release evidence.

---

# PART VI — IMPLEMENTATION PLAN BY FILE

## 34. Required/expected code changes

### 34.1 Durability

**Modify**

```text
src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java
```

- remove `LUCKY_BREAK` from passive `repairRate`
- remove `MENDING_BOOST` from passive `repairRate`
- leave only true passive-repair perks
- later apply shared damage-context rules to outgoing/incoming damage paths
- make shared craft mutation server-only or retire it during crafting refactor

**Modify**

```text
src/main/java/com/otectus/runicskills/mixin/MixItemStack.java
```

- add Lucky Break to true durability avoidance
- use centralized eligibility helper
- preserve aggregate cap

**Add**

```text
src/main/java/com/otectus/runicskills/common/durability/DurabilityPerkRules.java
```

**Add**

```text
src/main/java/com/otectus/runicskills/mixin/MixExperienceOrb.java
```

**Modify**

```text
src/main/resources/runicskills.mixins.json
```

- register Mending hook
- register ResultSlot hook if used

---

### 34.2 Crafting

**Modify/refactor**

```text
src/main/java/com/otectus/runicskills/registry/events/CraftingEventHandler.java
src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java
```

- all authoritative work requires `ServerPlayer`
- separate perk proc semantics
- remove aggregate chance bucket
- prevent client inventory mutation

**Add, recommended**

```text
src/main/java/com/otectus/runicskills/common/crafting/CraftingExecutionGuard.java
src/main/java/com/otectus/runicskills/common/crafting/CraftingTransactionContext.java
src/main/java/com/otectus/runicskills/mixin/MixResultSlot.java
```

**Modify**

```text
src/main/java/com/otectus/runicskills/registry/events/ScholarPerkHandler.java
```

- replace 4096 hot scan with indexed/bounded candidate lookup
- isolate third-party runtime failures
- no partial unlock state

**Add, recommended**

```text
src/main/java/com/otectus/runicskills/common/crafting/MasterResearcherRecipeIndex.java
```

**Modify**

```text
src/main/java/com/otectus/runicskills/registry/events/EnchantingLorePerkHandler.java
```

- isolate modded `canEnchant` failures
- cache/filter where sensible

---

### 34.3 Combat

**Add**

```text
src/main/java/com/otectus/runicskills/common/combat/DamageContext.java
```

**Modify**

```text
src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java
src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java
```

- classify secondary damage
- replace local-only re-entry assumptions
- finite-number validation

**Audit and modify as needed**

```text
all power handlers
all integration handlers
all files containing LivingEntity#hurt/.hurt(
```

Every Runic-emitted secondary damage call must be context-wrapped.

---

### 34.4 Inventory tabs

**Modify/refactor**

```text
src/main/java/com/otectus/runicskills/client/gui/DrawTabs.java
```

- rendering only; consume final layout rectangles rather than compute hard-coded coordinates
- hover/click geometry from same layout object

**Add**

```text
src/main/java/com/otectus/runicskills/client/gui/InventoryTabLayout.java
src/main/java/com/otectus/runicskills/client/event/InventoryTabsScreenHandler.java
```

**Modify**

```text
src/main/java/com/otectus/runicskills/handler/HandlerConfigClient.java
```

- placement/visibility/offset/drag config

**Modify or reduce responsibility**

```text
src/main/java/com/otectus/runicskills/mixin/MixInventoryScreen.java
```

- prefer Forge screen events for render/input
- preserve only unavoidable mixin behavior

**Optional generic compatibility API**

```text
src/main/java/com/otectus/runicskills/api/client/InventoryTabLayoutEvent.java
```

or internal reserved-region registry if a public API is unnecessary.

---

### 34.5 Version/release metadata

**Modify**

```text
gradle.properties
CHANGELOG.md
```

Set:

```text
mod_version=2.0.5
```

Update any README/version badges or metadata generated from the version if required by the project.

---

# PART VII — TESTING

## 35. Automated test requirements

The existing project should gain focused regression coverage for each fixed class of defect.

### 35.1 Lucky Break tests

- eligibility true/false
- configured percentage normalization
- seeded repeated rolls
- aggregate cap with other avoidance perks
- idle tick does not repair from Lucky Break

### 35.2 Mending Boost tests

- inactive perk = vanilla result
- active perk = boosted Mending repair
- no XP = no repair
- no Mending enchantment = no repair
- never over-repairs
- multiple Mending items preserve vanilla selection semantics

### 35.3 Crafting tests

At minimum:

- client-side craft callback produces no authoritative mutation
- server craft callback produces exactly intended mutation
- independent proc chances do not sum accidentally
- Efficient Crafting restores consumed ingredients rather than creating arbitrary bonus output
- bucket/bottle/container remainder is not duplicated
- shift-click executes correct count
- inventory-full behavior is safe
- re-entry guard unwinds after exception
- result stack count >1
- shaped and shapeless
- repair recipe
- recipe book
- modded/custom recipe fixture

### 35.4 Master Researcher tests

Create synthetic fixtures:

1. ordinary recipe set
2. very large recipe set
3. custom `Ingredient` whose `test` throws `RuntimeException`
4. custom ingredient whose `getItems` throws `RuntimeException`
5. dynamic ingredient requiring fallback bucket

Assertions:

- craft event returns normally
- bad candidate is skipped
- good candidates still work
- no thousands-of-recipes scan occurs at craft time
- cache rebuild works after simulated recipe reload
- old cache does not leak across lifecycle

### 35.5 Master Artificer tests

Synthetic enchantment:

```java
@Override
public boolean canEnchant(ItemStack stack) {
    throw new IllegalStateException("test");
}
```

Craft must not crash.

### 35.6 Damage context tests

- primary → Limit Breaker secondary does not re-run standard outgoing stack
- cleave secondary terminates
- reflect secondary does not recursively reflect
- nested third-party-style reflect simulation terminates by policy/depth guard
- context stack is empty after success
- context stack is empty after thrown `RuntimeException`
- two players do not share context
- invalid multiplier does not write NaN/Inf damage

### 35.7 Layout tests

Pure layout calculations can be unit-tested without a rendered screen:

- candidate rectangles
- reserved rectangle intersections
- offsets
- clamping
- anchor resolution
- small-window behavior
- persisted offset conversion

---

## 36. Manual integration matrix

### 36.1 Runtime baseline

Mandatory:

```text
Minecraft 1.20.1
Forge 47.4.23
Java 17
Runic Skills 2.0.5
```

Also run the project’s normal Forge development/test version so CI/dev parity is maintained.

### 36.2 Crafting mod matrix

- vanilla only
- ModernFix
- Not Enough Crashes
- ModernFix + Not Enough Crashes
- representative large recipe modpack if available

### 36.3 UI mod matrix

- vanilla
- CustomNPCs setup that reproduces supplied screenshots
- active potion/status effects
- recipe book
- CustomNPCs + effects
- CustomNPCs + effects + recipe book
- Legendary Tabs
- L2/native tabs if supported

### 36.4 Combat matrix

- Wisdom only
- Strength only
- Endurance only
- Fortune only
- Wisdom + Strength
- Wisdom + Endurance
- Wisdom + Fortune
- Wisdom + all reported maxed trees
- enchanted and unenchanted weapon
- Iron Golem
- ordinary hostile
- armored target
- retaliation enabled
- server restart/load after combat

---

# PART VIII — PERFORMANCE AND SAFETY BUDGETS

## 37. Craft event performance budget

2.0.5 should make craft-time work proportional to a small set of relevant candidates, not the global recipe registry.

Track in dev/debug builds:

```text
candidate recipes considered
candidate ingredients tested
time spent in Master Researcher craft handler
exceptions skipped
```

A craft should not synchronously walk thousands of recipes in a large pack.

No fixed millisecond threshold needs to be made a hard game rule, but CI/dev profiling should show a clear order-of-magnitude reduction from the existing worst-case scan.

---

## 38. Logging policy

Use log-once/rate-limited diagnostics for:

- broken third-party recipe ingredient
- broken modded enchantment callback
- damage depth suppression
- unsupported modded crafting transaction
- invalid non-finite perk multiplier

Do not spam:

- every craft
- every damage tick
- every screen frame
- every AUTO layout attempt

Debug diagnostics should be useful enough for a player to attach `latest.log` to an issue.

---

# PART IX — SAVE, NETWORK, CONFIG, AND COMPATIBILITY

## 39. Save-data compatibility

No skill/perk NBT schema migration should be required for these fixes.

Requirements:

- existing 2.0.2/2.0.4 player skill levels remain intact
- active perk selections remain intact
- no automatic reset of Intelligence/Wisdom
- damage/crafting contexts are transient only
- recipe indexes are rebuilt, not saved into player/world NBT unless the project already has a justified cache system

---

## 40. Network protocol

The planned fixes are mostly:

- server event correctness
- mixin behavior
- transient cache/context
- client-only placement config

Do not bump the network protocol automatically.

Before release, check the project’s established policy:

- if no packet shape or synchronized common-config schema changes, preserve protocol
- if a new synced field or packet becomes necessary, bump according to existing compatibility policy and document it in changelog

The new tab anchor/offset settings should be **client-only**, so they should not require a protocol bump.

---

## 41. Config migration

All new client tab settings must have defaults that reproduce a sensible current-like placement when no conflict exists.

Existing config files must load without manual editing.

Example defaults:

```toml
inventoryTabsEnabled = true
inventoryTabsAnchor = "AUTO"
inventoryTabsOffsetX = 0
inventoryTabsOffsetY = 0
inventoryTabsAvoidRecipeBook = true
inventoryTabsAvoidEffects = true
inventoryTabsDragToMove = true
```

Unknown/invalid anchor string:

- fall back to `AUTO`
- log once
- do not crash client startup

---

# PART X — IMPLEMENTATION ORDER

## 42. Recommended PR/commit slices

### Commit 1 — durability semantic correction

- remove Lucky Break/Mending Boost from passive repair
- implement Lucky Break in `MixItemStack`
- implement Mending Boost in `ExperienceOrb`
- add tests

### Commit 2 — crafting authority and proc separation

- require server player
- split aggregate chance
- add crafting execution guard
- regression tests

### Commit 3 — Efficient Crafting transaction semantics

- ResultSlot/input snapshot
- exact consumed-material refund
- remainder/shift-click tests

### Commit 4 — Master Researcher/Artificer hardening

- reverse recipe index
- bounded fallback
- exception isolation
- cache lifecycle
- ModernFix matrix

### Commit 5 — unified combat damage context

- central origin/depth model
- audit all secondary `.hurt` calls
- numeric validation
- combat diagnostics/tests

### Commit 6 — inventory tab layout system

- layout engine
- AUTO avoidance
- effect/recipe reserved regions
- manual offsets/drag/reset
- migrate render/input path as appropriate
- CustomNPCs integration testing

### Commit 7 — release verification

- full Forge 47.4.23 matrix
- changelog
- version 2.0.5
- before/after UI screenshots
- clean world + affected-world reload test

Keep commits independently reviewable. Do not combine the combat architecture and UI rewrite in one opaque change.

---

# PART XI — ACCEPTANCE CRITERIA / DEFINITION OF DONE

## 43. Player-report acceptance checklist

2.0.5 is not complete until all applicable boxes are satisfied.

### Durability

- [ ] Lucky Break never passively repairs an idle held item.
- [ ] Lucky Break probabilistically prevents eligible tool durability loss.
- [ ] Mending Boost never repairs without a Mending XP event.
- [ ] Mending Boost increases actual Mending repair.
- [ ] Combined durability perks remain bounded.

### Crafting

- [ ] No Runic craft reward mutates inventory from logical client.
- [ ] Maxed Intelligence can craft repeatedly without crash in vanilla test set.
- [ ] Maxed Intelligence can craft repeatedly with ModernFix enabled.
- [ ] Not Enough Crashes being installed does not change correctness.
- [ ] Efficient Crafting matches its tooltip.
- [ ] Container/remainder items are not duplicated.
- [ ] Shift-click/mass crafting is correct.
- [ ] Master Researcher no longer scans thousands of recipes synchronously per craft.
- [ ] One broken modded ingredient/enchantment cannot crash an ordinary craft.

### Combat

- [ ] Every Runic-emitted secondary damage call has a `DamageContext` origin.
- [ ] Standard outgoing modifiers do not accidentally reapply to disallowed secondary damage.
- [ ] Reflection/cleave/bonus-damage loops terminate.
- [ ] Damage amounts cannot become NaN/Inf through Runic multiplier composition.
- [ ] Wisdom-only control remains stable.
- [ ] Multi-maxed-skills + Wisdom + Iron Golem reproduction remains stable.
- [ ] Save/quit/reload after test remains stable.
- [ ] Any previously reproducible “crash every load” test save loads after fix without perk-data deletion.

### ModernFix

- [ ] No direct ModernFix-specific workaround exists unless a post-refactor reproduction proves it necessary.
- [ ] Craft tests pass ModernFix on/off.
- [ ] Recipe-index logic does not assume vanilla `Ingredient` internals.

### Inventory tabs

- [ ] Supplied overlap/glitch pattern is no longer reproducible in equivalent setup.
- [ ] Runic tabs avoid active-effect UI.
- [ ] Runic tabs can coexist with CustomNPCs setup.
- [ ] User can move tabs.
- [ ] Position persists.
- [ ] Position can be reset.
- [ ] Render, hover, tooltip, and click use identical final rectangles.
- [ ] Existing Legendary Tabs/L2 ownership integration still works.
- [ ] 2.0.2 close/reset regression remains fixed.

### Release

- [ ] `mod_version=2.0.5`
- [ ] changelog distinguishes confirmed fixes from architecture hardening
- [ ] Minecraft 1.20.1 / Forge 47.4.23 tested
- [ ] Java 17 tested
- [ ] no save migration unless explicitly documented
- [ ] network compatibility decision recorded

---

# PART XII — CRASH-REPORT FOLLOW-UP WITHOUT BLOCKING CONFIRMED FIXES

## 44. Additional evidence worth requesting from the reporter

The confirmed fixes above do not need to wait for more information.

For the exact Iron Golem crash attribution and any residual ModernFix-only crash, obtain:

1. full crash report from `crash-reports/`
2. `logs/latest.log`
3. Not Enough Crashes report, if separate
4. exact ModernFix version
5. mod list
6. weapon/item used against the Iron Golem
7. list/export of active perks if available
8. whether the golem was already attacking/angry
9. whether removing the golem or moving the player permits the affected world to load

When received, use the **first Runic Skills frame in the causal exception chain**, not merely the top transformed/mixin frame, to identify the next fix.

Do not delay RS-205-01 through RS-205-08 and RS-205-11/12 while waiting.

---

# PART XIII — SOURCE REFERENCES

## 45. Runic Skills source snapshot

All links below point to the reviewed 2.0.4 baseline commit so line movement on `master` does not erase the evidence.

- `PerkEffectsHandler.java`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/java/com/otectus/runicskills/registry/events/PerkEffectsHandler.java>

- `CraftingEventHandler.java`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/java/com/otectus/runicskills/registry/events/CraftingEventHandler.java>

- `ScholarPerkHandler.java`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/java/com/otectus/runicskills/registry/events/ScholarPerkHandler.java>

- `EnchantingLorePerkHandler.java`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/java/com/otectus/runicskills/registry/events/EnchantingLorePerkHandler.java>

- `CombatEventHandler.java`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java>

- `MixItemStack.java`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/java/com/otectus/runicskills/mixin/MixItemStack.java>

- `DrawTabs.java`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/java/com/otectus/runicskills/client/gui/DrawTabs.java>

- `MixInventoryScreen.java`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/java/com/otectus/runicskills/mixin/MixInventoryScreen.java>

- `HandlerConfigClient.java`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/java/com/otectus/runicskills/handler/HandlerConfigClient.java>

- `LegendaryTabsIntegration.java`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/java/com/otectus/runicskills/integration/LegendaryTabsIntegration.java>

- `runicskills.mixins.json`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/resources/runicskills.mixins.json>

- `RunicSkillsMixinPlugin.java`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/src/main/java/com/otectus/runicskills/mixin/RunicSkillsMixinPlugin.java>

- `gradle.properties`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/gradle.properties>

- `CHANGELOG.md`  
  <https://github.com/otectus/runic-skills/blob/03275acdf766f7d4c1d0cffb925e41d56597ca9b/CHANGELOG.md>

## 46. ModernFix comparison source

- `IngredientMixin.java` at reviewed ModernFix commit  
  <https://github.com/embeddedt/ModernFix/blob/66d45c2b95085f1071dfa19a32896e2855dfb799/src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_ingredients/IngredientMixin.java>

---

# PART XIV — CODING-AGENT EXECUTION BRIEF

## 47. Non-negotiable implementation rules

A coding agent executing this document must:

1. Start from current Runic Skills `master`/2.0.4, not revert to 2.0.2.
2. Preserve all valid 2.0.4 audit remediation.
3. Make Lucky Break and Mending Boost match their tooltips.
4. Make crafting mutation server-authoritative.
5. Remove the global Master Researcher craft-time recipe scan.
6. Implement Efficient Crafting truthfully or explicitly change its documented contract; preferred route is truthful material preservation.
7. Introduce one shared origin/depth model for all Runic secondary damage.
8. Treat the exact Iron Golem exception as unconfirmed until reproduced/logged.
9. Do not special-case ModernFix without a post-hardening reproduction.
10. Redesign Runic inventory tab layout so other UI is avoided rather than simply drawn over.
11. Give the player a persistent manual tab-position override.
12. Add regression tests before declaring the issue fixed.
13. Test Forge 47.4.23 specifically.
14. Validate save reload after the combat reproduction.
15. Release as 2.0.5 only after the acceptance checklist passes.

---

## 48. Suggested user-facing 2.0.5 changelog summary after implementation

Do not publish this verbatim until the tests pass, but the final release should be able to truthfully say something close to:

> **2.0.5 fixes several perk, crafting, combat, and inventory-UI issues reported with large perk builds.** Lucky Break now prevents durability loss instead of repairing items, Mending Boost only improves real Mending repairs, and crafting perks are now server-authoritative and safer around large/modded recipe sets. Secondary combat damage now has re-entry protection to prevent perk chains from feeding back into one another. The built-in inventory tabs also avoid more UI conflicts and can be repositioned, including setups with CustomNPCs and active effects.

Do **not** state “fixed ModernFix crash” unless a ModernFix-specific crash is actually reproduced and verified. Prefer:

> Improved compatibility with modded recipe/ingredient implementations, including configurations using ModernFix.

Likewise, if no original stack trace is recovered, phrase the combat work as:

> Fixed/hardened recursive cross-perk secondary-damage interactions and added safeguards for large multi-skill builds.

rather than naming a single Wisdom perk as the proven cause.

---

**End of specification.**
