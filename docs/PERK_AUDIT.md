# Perk Effect-Coverage Audit

This document is the human-readable companion to the machine-enforced backlog in
[`src/test/resources/perk_no_effect_allowlist.txt`](../src/test/resources/perk_no_effect_allowlist.txt),
checked by [`PerkEffectCoverageTest`](../src/test/java/com/otectus/runicskills/registry/PerkEffectCoverageTest.java).

## What source coverage proves

A perk passes the coverage test when some source file **other than** `RegistryPerks.java` references
its constant (`RegistryPerks.<NAME>`). A gameplay consumer reads `isEnabled(player)` /
`getActiveValue(player)` and acts. A reference is a useful regression gate, but it cannot prove that
an upstream event fires, a trigger is correctly timed, or every mod combination works. The
[2.1.0 content trace](CONTENT_TRACE_2.1.0.md) inventories each registration, requirement, description,
configuration and consumer, together with shared networking/persistence routes. Behavioral
regressions and the executed compatibility profiles complement that source review.

## Current numbers

| Metric | Count |
| --- | --- |
| Registered perk declarations (`RegistryObject<Perk>`, full optional catalogue) | 475 |
| Perks with source references outside registration | 475 |
| Perks without an effect reference | **0** |

**The source-reference backlog is closed.** The allowlist file is empty and the test enforces that it
stays that way: a newly registered perk with no effect site fails the build unless somebody deliberately adds a
line to the allowlist and says why.

For scale, this backlog was 340 entries at the 1.3.8 audit and 129 when the 1.10 audit re-counted it
(RS10-004).

## How the last 75 were closed

**Scope note.** This section covers the final pass only. Across the whole of 2.0.0 the backlog went
from 129 to 0: **112 perks were implemented and 17 removed.** The seventy-five below — 65
implemented, 10 removed — are the last batch of that, and they are the ones documented here because
they are the ones whose reasoning was not already recorded elsewhere. The other seven removals were
the build/colony set (`colony_advisor`, `colony_builder`, `construction_haste`,
`dimensional_builder`, `master_mason`, `scaffold_master`, `structural_engineer`), all describing
block-placement or build speed: Minecraft has no placement-speed mechanic to accelerate. The full
release-level account is the 2.0.0 entry in [`CHANGELOG.md`](../CHANGELOG.md).

The split is not arbitrary: a perk was implemented wherever the game — vanilla, or a mod this build
actually compiles against — has a mechanic its tooltip can honestly be pointed at, and removed only
where it does not.

### Implemented as written

Most perks named something the game already models, and simply had no hook. Those were wired
straight to it: `STONE_CUTTER_EFFICIENCY` to the stonecutter's result slot, `LINGUIST` to the number
of offers a villager generates, `MASTER_RESEARCHER` and `INVENTOR` to the recipe book,
`ENCHANTMENT_TRANSFER` to the anvil, `SOURCE_ATTUNEMENT` and `SOURCE_WELL` to Ars Nouveau's own mana
events, the four Iron's Spells perks to the four attributes their tooltips named.

### Implemented after reinterpretation, with the tooltip corrected to match

Some perks were written against a system this build cannot see — a colony mod, a backpack mod, a
zipline, a mana pool in a pack that has none. Where the *idea* had a faithful equivalent in reach,
the perk was pointed at that equivalent **and its tooltip was rewritten to say so**. A tooltip
describing behaviour the code does not have is indistinguishable from a bug to the player, so a
reinterpretation that leaves the old text in place is not an implementation; it is a second bug.

Forty-nine tooltips were rewritten in this pass. Examples:

| Perk | Was | Now |
| --- | --- | --- |
| `COLONY_GUARDIAN` | "…while in colony territories" | "…while inside a village" (vanilla's own `isVillage`) |
| `ZIPLINE_EXPERT` | "Zipline travel speed increased" | "Minecarts you ride travel faster" |
| `WAYSTONE_TINKER` | "Waystone teleportation cost reduced" | "Ender pearls deal less damage on arrival" |
| `ARCHITECT` | "Placed blocks gain bonus hardness" | "Blocks around you have a chance to survive each explosion" |
| `GRAND_SAGE` | "All wisdom-based bonuses are amplified" | "Raising Wisdom costs less experience" |
| `SOUL_BINDING` | "Items with Soul Bound enchantment never drop" | "The item in your hand goes to your ender chest on death" |
| `MECHANICAL_KNOWLEDGE` | "Redstone devices work faster" | "Hoppers near you move items faster" |
| `LOCK_EXPERT` | "All locks take less time to pick" | "Lock picks lose less durability" |

The reasoning for each individual case is in the javadoc at its effect site, not here — that is where
somebody changing the code will read it.

### Removed

Ten perks described a mechanic that does not exist in this build and could not be reached from it.
Each had a config value, an icon and a tooltip promising something the game had no way to deliver,
which is worse than not shipping the perk at all. They are gone, along with their config fields,
icons and lang keys.

| Perks | Why |
| --- | --- |
| `AURA_ATTUNEMENT`, `AURA_MANIPULATION`, `AURA_OF_VITALITY`, `AURA_SHIELD`, `NATURE_SAGE`, `NATURES_WISDOM` | All six act on Nature's Aura's aura value. Nature's Aura is not a dependency of this build — not even `compileOnly` — so there is no API to call and nothing that could be tested. `NaturesAuraIntegration` went with them. |
| `BACKPACK_ENGINEER`, `GADGET_UPGRADE` | Both describe backpack slots. No backpack mod is a dependency, and vanilla has no expandable container to enlarge. |
| `CIRCUIT_BREAKER` | "Redstone signal range increased by N blocks." Redstone power is a block-state property bounded at 15; there is no range to extend without replacing the whole redstone system. |
| `CLOCKWORK_MASTERY` | "Timed mechanisms are N% more accurate." Vanilla's timings are exact. There is no inaccuracy to reduce. |

**Existing saves keep their data dormant.** The capability's orphan-retention store preserves
unrecognised NBT keys, so a player who had one of these selected keeps the entry inertly and would
get it back if a pack ever re-added the perk. Perk registries carry `.disableSync()`, so a smaller
perk set does not break the login handshake.

If Nature's Aura is ever added as a dependency, those six are the natural first candidates to
restore: their config fields, icons and lang keys are recoverable from this commit's parent.

### Gated rather than removed

Three perks were kept but had a mod gate added, so they no longer register at all in a pack that
cannot support them: `MODULAR_EQUIPMENT` (Apotheosis sockets are the only "equipment modification
slot" in reach), `LOCK_EXPERT` and `SAFE_BUILDER` (Locks Reforged). This is the middle option
between implementing and deleting, and it is the right one whenever the mechanic is real but
optional.

## How to retire a backlog entry

1. Implement the effect in the appropriate event handler, mixin or integration, reading the perk's
   config value and gating on `isEnabled(player)`.
2. If the behaviour differs at all from what the tooltip says, **change the tooltip**. The perk is
   not finished until the two agree.
3. Delete the perk's line from `perk_no_effect_allowlist.txt`.
4. `./gradlew test` — `PerkEffectCoverageTest` confirms the perk is now covered and the backlog
   shrank.

## What this audit cannot tell you

The test is a *syntactic* check: it proves a perk's constant is read somewhere outside its
registration. It does not prove the effect is correct, balanced, or reachable in a given pack. In
particular, the perks gated on Apotheosis, Ars Nouveau, Iron's Spells, Ice and Fire, Samurai Dynasty,
Locks Reforged and Siege Machines depend on those mods' APIs or registry ids. Optional runtime
profiles now execute selected integrations, including the real Iron's Spells profile and the
Tinkers' companion profiles. A profile only validates its recorded cases; it does not certify every
perk in that mod or every combination. Read the executed release verification report and
[Tinkers' test matrix](TCONSTRUCT_TEST_MATRIX.md) for the tested versions and remaining live-play
coverage. The nineteen formerly inert Iron's Spells Powers now have runtime handlers, as described
in [Content Status](CONTENT_STATUS.md).
