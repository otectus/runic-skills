# Perk Effect-Coverage Audit (1.3.8)

This document is the human-readable companion to the machine-enforced backlog in
[`src/test/resources/perk_no_effect_allowlist.txt`](../src/test/resources/perk_no_effect_allowlist.txt),
checked by [`PerkEffectCoverageTest`](../src/test/java/com/otectus/runicskills/registry/PerkEffectCoverageTest.java).

## What "has an effect" means

A perk has a runtime effect iff some source file **other than** `RegistryPerks.java` references its
constant (`RegistryPerks.<NAME>`). That reference is where a handler/integration reads
`isEnabled(player)` / `getActiveValue(player)` and acts. A perk that is only mentioned inside its own
registration is **inert** — registered, with config/lang/texture, but no gameplay hook.

## Current numbers

| Metric | Count |
| --- | --- |
| Registered perks (`RegistryObject<Perk>`) | 522 |
| Perks with a real effect site | 182 |
| Perks still inert (tracked backlog) | 340 |

The 340 inert perks are enumerated in the allowlist. The test enforces three invariants so the backlog
stays honest and can only shrink:

1. **No silent inertness** — every registered perk must have an effect site or be in the allowlist, so a
   newly-registered perk cannot quietly join the inert pile.
2. **No stale backlog** — once a perk gains an effect site, its allowlist line must be deleted (the test
   fails on a still-listed-but-implemented perk).
3. **No dead names** — every allowlist entry must name a currently-registered perk.

## Implemented in this pass (removed from the backlog)

Strength (in [`CombatEventHandler`](../src/main/java/com/otectus/runicskills/registry/events/CombatEventHandler.java)):

- **CLEAVE** — splashes `cleavePercent` of the hit to other enemies within `cleaveRangeBlocks`
  (reentrancy-guarded so splash hits don't recurse or inherit the melee bonuses).
- **TITANS_GRIP** — bonus `titansGripPercent` damage with a heavy/two-handed Spartan weapon and an
  occupied offhand. *Limitation:* truly bypassing Spartan's own two-handed/offhand restriction needs a
  mixin into Spartan internals and is intentionally out of scope; the perk rewards the described
  playstyle instead.
- **GLADIATOR** — bonus `gladiatorPercent` melee damage while a shield is in the offhand. *Limitation:*
  the lang text references a "shield bash" (a Spartan Shields / Better Combat mechanic with no vanilla
  event to hook); gating on an equipped shield is the non-invasive equivalent.
- **RUNIC_MIGHT** — bonus `runicMightPercent` damage when the wielded weapon's id path contains
  `runic`/`rune` (covers runic-ore weapons across mods without a brittle allow-list).

Constitution defensive batch (additive reductions, clamped at 80% total):

- **SEARING_RESISTANCE** (`searingResistancePercent`) — reduces fire/lava damage.
- **WITHER_RESISTANCE** (`witherResistancePercent`) — reduces wither damage.
- **ARMOR_OF_FAITH** (`armorOfFaithPercent`) — reduces magic damage.
- **SURVIVAL_INSTINCT** (`survivalInstinctPercent`) — reduces all damage below 30% HP.
- **BLOOD_SHIELD** (`bloodShieldPercent`) — flat reduction of all incoming damage.
- **RUNIC_FORTIFICATION** (`runicFortificationPercent`) — flat reduction of all incoming damage.

`PRIMAL_FURY` and `UNSTOPPABLE_FORCE` (two of the originally-deferred Strength six) were already
implemented in `CombatEventHandler` before this pass and were verified, not re-done.

## Backlog categories

The remaining 340 inert perks fall into two buckets (informational; the test treats both the same):

- **Mod-gated** — `BOTANIA_*`, `APOTHEOSIS_*`, `APOTHIC_*`, `ARS_*`, and the Blood Magic `BLOOD_*`
  perks. These register as `null` at runtime when their optional mod is absent, so they are correctly
  inert in packs (like Lorecraft) that don't ship that mod. They get effect sites when their
  integration's perk hooks are completed.
- **Pending native implementation** — the long tail of Strength/Constitution/Dexterity/Endurance/
  Fortune/Intelligence/Wisdom perks. Most already have a `*Percent`/`*Amplifier` config field (designed
  but unwired) and follow the same handler-branch pattern used for the batch above.

## How to retire a backlog entry

1. Implement the effect in the appropriate event handler or integration class, reading the perk's config
   value via `getActiveValue(player)` (or the config field directly) and gating on `isEnabled(player)`.
2. Delete the perk's line from `perk_no_effect_allowlist.txt`.
3. `./gradlew test` — `PerkEffectCoverageTest` confirms the perk is now covered and the backlog shrank.
