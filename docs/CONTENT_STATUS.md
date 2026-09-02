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

### Perks (0)

Empty, and the point of the exercise. See [`PERK_AUDIT.md`](PERK_AUDIT.md).

## How this is kept honest

`ContentStatusTest` asserts the runtime table and the build-time
[`power_no_effect_allowlist.txt`](../src/test/resources/power_no_effect_allowlist.txt) name the same
Powers, in both directions, that no entry names a Power that does not exist, and that the equip path,
the UI and the budget all still consult the table. Two mechanisms describing one fact are only useful
while they agree.
