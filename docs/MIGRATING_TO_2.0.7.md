# Migrating to 2.0.7

For server admins and pack authors upgrading from 2.0.6. **Network protocol moves from 11 to 12** —
client and server must be on the same protocol, and a mismatched pair is refused at join with a
clear error (`network/ServerNetworking.java`, `PROTOCOL_VERSION`). Everything below assumes both
sides are on 2.0.7.

## Auto Repair now scales, and small percentages repair slower

Auto Repair's durability credit is fractional accumulation rather than a flat per-tick amount: once
per second, `repairRate / 100 * autoRepairPointsPerSecondAt100` raw points accrue to a budget, and a
full point repairs the next eligible slot by rotation. The practical effect is that **a low repair
percentage now restores durability proportionally slower than it did in 2.0.6** — the accumulator
has to build up a whole point before anything is actually repaired. The budget clears on logout,
death, server stop, or the perk going inactive. New config field `autoRepairPointsPerSecondAt100`
(default `4.0`) sets the scale.

## Salvage moved off block-breaking, onto the grindstone and item-destroy

**Resource Efficiency and Salvage Expert no longer trigger on breaking a crafting block.** They keep
their perk ids and their existing config percentages, but now trigger at the grindstone
(`GrindstoneEvent.OnPlaceItem`) against a datapack allowlist instead
(`registry/events/WorkshopPerkHandler.java`). **Disassembler and Salvage Master** keep their ids too,
and now trigger on an item actually breaking (hand or inventory), reading the same allowlist rather
than inferring recovery from whatever recipe happens to produce the item. The allowlist itself is a
new datapack format under `data/<ns>/runicskills/recycling/*.json` — see
[`docs/RECYCLING_RULES.md`](RECYCLING_RULES.md). A new config toggle, `recyclingEnabled` (default
`true`), turns the whole salvage path off; with it off, none of the four perks recover anything and
no recycling rule is consulted.

## Treasure Hunter no longer pays on a creative or cancelled break

Payout now requires a confirmed block removal — a new `BlockBreakCommittedEvent` posted after
`ServerPlayerGameMode.removeBlock` actually succeeds and `canHarvestBlock` passes. A creative-mode
break, or a break cancelled by another mod's lower-priority listener, pays nothing.

## Crafting bonus copies are capped per craft, and denied for repairs, conversions and equipment

Every bonus-copy crafting perk (Assembly Line, Mass Production, Alloy Master, Master Woodworker,
Medieval Architecture) now routes through one policy, `CraftRewardPolicy`, instead of rolling
independently. A single craft may earn at most `craftRewardMaxExtraOutputs` extra copies **in
total**, across every perk together (new config field, default `1`; `0` disables bonus copies
entirely). The policy denies a bonus copy outright for:

- anything that is not a plain manufacture (a repair, a part swap, a modify, a rename, a recycle, or
  a craft this mod could not classify);
- equipment — any result that is not stackable, or is damageable;
- a result carrying an item-handler, fluid-handler, or energy capability;
- a result carrying NBT, or a craft that consumed only one distinct input item type (a compression),
  unless the result is tagged `runicskills:craft_reward_allowed`;
- a craft that hands back a container item (a bucket, a bottle).

A datapack can also deny a bonus copy for a specific result outright with the
`runicskills:craft_reward_denied` tag, or (for Tinkers' station results specifically) with a
`craft_reward_policy` pack rule — see [`docs/TCONSTRUCT_PACK_RULES.md`](TCONSTRUCT_PACK_RULES.md).
An *allow* from either route can never lift the mandatory exclusions above.

## Lucky Charm applies once, at effect application

Lucky Charm's duration shortening (of harmful effects, capped at 90%) is now applied once, at the
moment the effect is added (a `@ModifyVariable` on `LivingEntity.addEffect`), instead of on every
observer update. An infinite-duration effect is left untouched.

## New config fields and their defaults

| Field | Default | Purpose |
|---|---|---|
| `autoRepairPointsPerSecondAt100` | `4.0` | Auto Repair's fractional-accumulation scale (see above). |
| `craftRewardMaxExtraOutputs` | `1` | Cap on bonus crafted copies per craft, across every perk together; `0` disables bonus copies. |
| `recyclingEnabled` | `true` | Master toggle for the grindstone/item-destroy salvage path. |
| `tconstructWorkshopFocusSeconds` | `60` | How long a Tinkers' workshop focus lasts before it expires on its own. |
| `tconstructWorkshopFocusRadius` | `16` | Distance a player may move from a focused workshop before it is dropped. |
| `tconstructMaxFocusedWorkshops` | `32` | Server-wide cap on simultaneously focused workshops. |
| `tconstructMaxCastingAssociations` | `8` | Per-workshop cap on associated casting blocks. |
| `tconstructWorkshopBonusCap` | `0.25` | Shared ceiling for workshop-related bonus percentages (Thermal Rhythm, Workshop Cadence, etc.). |
| `tconstructAllowAutomationRewards` | `false` | Whether workshop bonuses apply to automated (non-player) casts. |

`enableTConstructLockItems` (default `false`, unchanged from its introduction earlier in 2.0.7)
still gates the automatic material-tier lock profile; it is not turned on by anything in this
release, and neither is any pack rule — no `tconstruct_rules` file ships with the mod.

## Save compatibility

Player data gains one new, additive NBT compound, `runicskills:tc_state`
(`common/capability/SkillCapability.java`), holding the Tinkers' Power cooldown debt as remaining
ticks. It is written on every save and read back on every load; a save from 2.0.6 simply has none
of it and loads with an empty debt. Unknown keys in general — including a perk or Power id from an
add-on that is not currently installed — are round-tripped through `orphanTags` rather than
dropped, so removing an add-on and reinstalling it later restores the player's saved selections
instead of erasing them.

**A dormant `tc_` selection costs no budget and is not auto-activated.** A saved `tc_` perk id this
build's registry does not resolve counts as zero against the active-perk budget — it cannot lock a
player out of their other perks just because an add-on is missing. A saved Power with no native
integration behind it is refused at the eligibility gate and stays refused on re-equip: nothing in
the load path activates content that no longer has support behind it.

## Tinkers' Construct: absent, stable, or 3.12

With Tinkers' Construct absent, every Tinkers'-specific Power is still registered (so a save naming
one still resolves) but inert; each `tc_` perk, by contrast, is not registered at all when Tinkers'
is absent. Classification/repair/lock behaviour that depends on Tinkers' simply does not run. With the compiled-against stable release (1.20.1-3.11.2.166), the full
integration is available. With Tinkers' Construct 3.12, the mod runs in **conservative mode**:
classification and repair make the same raw API calls, but neither is marked supported for that
profile (classification and repair report `VERSION_UNVERIFIED`, not `SUPPORTED`), so any perk or
Power gated on either capability is inert — as native wear reduction and workmanship stamping
already are, since no Tinkers'-targeting mixin is applied for that profile.

## A paid Keystone slot survives disabling the service, but not the integration

The Keystone service's extra upgrade slot is stored in the tool's own Tinkers' modifier data
(`runicskills:keystone`), computed from the tool's own modifier list rather than from anything
Runic Skills tracks per player. Turning off `enableTConstructPerks` (the Keystone service itself)
stops new sales; it does not reach back into a tool that already paid for one and invalidate the
modifiers sitting in that slot. Turning off `enableTConstructIntegration` (or uninstalling Tinkers'
Construct entirely) is different: the Keystone modifier is registered only inside that flag's
enabled branch, so a tool's paid slot is not granted while the integration is off.
