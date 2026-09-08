# Patient Temper native durability boundary

The pinned Simply Swords 1.70.2 Forge artifact enables Patient Temper only after
the delegated-hit reader and all three optional mixins have verified call sites.
The perk requires Tinkering 10 by default, scaled with the configured skill cap.
Its default chance is 10%, configurable up to 30%.

The melee boundary is `Player.attack` calling `ItemStack.hurtEnemy`, followed by
the ordinary `SwordItem.hurtEnemy` durability call. Mining uses the standard
`SwordItem.mineBlock` durability call inside the outermost block-break action.
The exact actor, held stack and action must still match at the wear decision.
The existing manual item requirements and shared availability predicate apply.

Native gem execution is scoped out before its callbacks run. Delegated weapon
hits, Runic secondary damage, nested actions, allied melee targets, prohibited
PvP and fake players receive no contribution. Simply More owns its own items,
even when their classes inherit Simply Swords behavior. Unidentified ability
payments and repair/workshop operations never enter an eligible wear scope.

Patient Temper contributes to the existing Runic probability for the first point
of wear, with a single combined roll capped at 90%. Remaining points use only
existing conservation. One root claim prevents a repeated native durability call
from multiplying the new perk. Native Unbreaking and break callbacks retain
their existing ordering. No durability is refunded after the native call.

The native GameTest exercises actual `Player.attack` and `ItemStack.mineBlock`
with a registered Simply Swords weapon. It checks purchase/configuration states,
successful and failed rolls, two-point mining, repeated calls, nested and gem
scopes, unheld items and unidentified payments. A controlled random source checks
that the new chance joins one trial and cannot exceed the existing 90% cap.
The test's explicit mining action scope isolates wear from block-drop fixtures;
it does not certify every upstream multi-block mining implementation.

Current artifact hashes and production profile results are in
[validation-2.1.1.json](validation-2.1.1.json). Client rendering and unsupported
upstream attack implementations remain outside this validation scope.
