# Apprentice's Codex integration

What Runic Skills does with [Apprentice's Codex](https://www.curseforge.com/minecraft/mc-mods/apprentices-codex)
`0.9.7.1`, and the complete content ledger behind it.

The table at the bottom is not a summary. Reference document §6.6 asks for an outcome on **every**
registered item and block, exemptions included, and that is what it lists: 186 rows covering 166
items and 20 blocks, each with the role it was classified as, the requirement it produces, the
actions that requirement is about, and why. The counts and the ids in this document were exported
from a real run (`runGameTestServer -PcodexProfile=true`), not written by hand.

## At a glance

| | |
| --- | --- |
| Artifact read | `apprentice_codex-0.9.7.1+mc1.20.1.jar`, SHA-256 `4685141bdd7fe0319270167ebffd658b6c2d91c6eaef5a22adb72c427b9cf4da` |
| Compile surface | `maven.modrinth:apprentices-codex:lcCKcjxL` (compile-only) |
| Native dependencies | Iron's Spells `1.20.1-3.16.3`, `irons_lib 1.20.1-2.1.0`, Curios `5.14.1+1.20.1`, GeckoLib `4.8.4` |
| Settings | `enableApprenticeCodexIntegration = true`, `codexAutomationGatePolicy = "DEVICE"` |
| Ledger | 186 entries: 114 curated, 1 native, 1 undetermined, 70 exempt |
| Gates published | 116 typed rules (109 item rules, 7 block rules) |

Nothing here is active unless Codex is installed. The content ledger and its rules are produced by
`integration/lock/ApprenticeCodexLockProvider`, which names no `jp.aquafactory`
type at all, so it is safe to load on a server without Codex; the classes that do name it
live under `integration/apprenticecodex/` and `mixin/apprenticecodex/`, reached only through
`RunicSkills.tryLoadIntegration` and `RunicSkillsMixinPlugin` respectively. The `checkSidedImports`
task fails the build on a `jp.aquafactory` import outside those two packages (and the
conditionally registered `gametest/codex/` tests).

## How a requirement is decided

Rules are **typed and action-scoped**. An item rule names `EQUIP`/`USE` (and `ATTACK` for a hybrid
weapon); a block rule names `INTERACT_BLOCK` and nothing else, so a player who cannot operate a
`spellcaster_workbench` can still break and move it. That distinction cannot be expressed in the
untyped id table at all, which is why these are published through the typed layer.

The role is decided first, from the most specific token in the id, and the role decides where the
number comes from:

1. **Spell containers** — books, tablets, grimoires and manifests — are ranked by the capacity they
   actually have, through the same function Iron's own spellbooks go through
   (`IronsBookGateMath`: `round((3·free + preset + 2·modifiers) / 2)`). A Codex grimoire and an
   Iron's spellbook of the same chassis therefore ask the same thing. The read goes through
   `ISpellContainer` on a detached inspection stack, never through `ISpellbook` and never through
   the class hierarchy: of Codex's six container class shapes, three do not implement `ISpellbook`
   at all, and one of the three that does implements it over a plain `Item` rather than over
   `SpellBook`. `ISpellbook` is in any case an empty marker interface in 3.16.3 and carries no data.
2. **Everything else** uses a reviewed role anchor at the reference cap of 32, chosen to match the
   anchor the equivalent Iron's or vanilla content already uses. Where such an item also carries a
   spell container, the container can only *raise* the Magic half, never lower it.
3. A container whose chassis cannot be read at all falls back to the lowest reviewed book anchor
   (Magic 8) and is recorded as **undetermined**, exactly as the Iron's provider does — the audit
   says the number is a fallback rather than a measurement.

Two floors exist for a reason:

- A requirement below 2 is dropped. Every player starts at level 1, so a rule asking for 1 gates
  nobody and would only clutter the table.
- A book whose measured chassis scores below the reviewed entry-utility band is raised to Magic 4.
  Three books land there: `isekai_travel_guidebook` (2 preset slots, 0 free, 0 modifiers) is the
  case §6.3 names, alongside `explorers_codex` (4 slots, 0 free, 1 modifier) and
  `grimoire_manifest` (1 slot, 0 free, 0 modifiers). Each is a functional unique spellbook with a
  low-entry profile, not an ungated one.

`enableApprenticeCodexIntegration = false` leaves Codex content **ungated**, not handed to the
universal estimator. The namespace is claimed through `LockOwnership` whenever the mod is installed,
which is what stops the inference engine recreating the same restrictions under another name
(§6.3). That claim is deliberately independent of the setting.

## Spell casting

Casting a spell from a Codex book, spellgun or swingcast weapon goes through Iron's
`attemptInitiateCast`, so it reaches `SpellPreCastEvent` and is gated by the ordinary spell rules —
`enableSpellLocks`, an explicit rule, then the configured generator. The item's own requirement and
the spell's are separate rules about separate things: an allowed spellgun containing one spell the
player cannot cast still fires everything else in it.

### A refused spellgun cast costs nothing

A Codex spellgun lends the player whatever mana they are short of, calls `attemptInitiateCast`, and
subtracts exactly what it lent if that returns false. Cancelling `SpellPreCastEvent` is what makes
it return false, so **the native rollback is the one that runs**. `reserveBorrowedMana` is only
reached after a cast has started, so a refusal leaves no reservation behind either, and ammunition
is checked before the cast and consumed after it. Runic Skills adds no refund of its own: §12.3 asks
for the commitment to be prevented rather than refunded, and this path already prevents it.

`CodexSpellgunGameTest` asserts this against the real item — mana unchanged, ammunition unchanged,
weapon unchanged, player not left casting — and then fires the same weapon from a fully levelled
player to prove the refusal was the gate rather than something else failing.

## Spell dispensers and automation

`SpellDispenserCastHelper` does not call `attemptInitiateCast`. It reimplements the cast loop, so
**no Iron's event and no Forge event is posted for an autonomous cast**: the ordinary pre-cast
subscription is coverage of every casting path except this one. That is why this integration
contains exactly one mixin.

| Policy | Behaviour |
| --- | --- |
| `DEVICE` (default) | The player actions on the device carry the gate — placing it, opening it, configuring it, all ordinary block interactions covered by the reviewed `INTERACT_BLOCK` rule on `spell_dispenser`. The machine's own casting rules then run untouched and each execution is recorded as `AUTOMATION_EXEMPT`. No skill reward or combat credit is granted for a machine's work, and an empty FakePlayer capability is never read as the owner's skill record. |
| `ONLINE_OWNER` | The cast is attributed to a verified owner who must be online, and is checked through the same resolver a hand cast uses. An owner who cannot be resolved — including Codex's placeholder profile for an unowned device — or who is offline **pauses** the device with its native failure diagnostic. Nothing loads offline player data to answer the question. |

The refusal is taken at the head of the widest `tryCast` and `tryStartContinuousCast` overloads,
which every narrower overload funnels into (confirmed with `javap -c` against 0.9.7.1). At that
point no caster proxy exists, no `MagicData` has been touched and nothing has been consumed, so a
refusal restores nothing because nothing was spent. It returns Codex's own
`CastResult.serverAllowlistBlocked(...)`, so the device reports a native message to nearby players
and keeps its contents. A continuous cast is decided once, at its start, which is the authorization
boundary §6.5 names.

The mixin is `@Pseudo`, string-targeted, `remap = false`, `require = 0, expect = 1`, and gated in
`RunicSkillsMixinPlugin` on Codex being installed **and** `enableApprenticeCodexIntegration` being
on. That flag is therefore restart-required: no config toggle can hot-unload a mixin.

## Content and action ledger

Exported from `runGameTestServer -PcodexProfile=true`; `/skills locks audit` writes the same rows
to `debug/runicskills-locks.json` under `apprentice_codex`, and every row is also printed at
`DEBUG` on each rule build.
Requirements are reference values at the per-skill cap of 32 before any cap-relative scaling.

### Gated (116 entries)

| Role | Requirement | Actions | Outcome | Entries |
| --- | --- | --- | --- | --- |
| `storage_book` (2) | Magic 16, Intelligence 10 | `EQUIP`, `USE` | curated | `archivists_grimoire`, `ender_grimoire` |
| `spell_container` (5) | from its own chassis, subject to the entry floor | `EQUIP`, `USE` | mixed | `spellstained_runic_tablet` (native, Magic 12 / Int 7), `explorers_codex`, `grimoire_manifest` and `isekai_travel_guidebook` (all three raised to the curated entry floor, Magic 4 / Int 2), `chargecast_catalystbook` (undetermined, Magic 8 / Int 5) |
| `spellgun` (16) | Magic 12, Dexterity 10 | `EQUIP`, `USE`, `ATTACK` | curated | `artisan_smash_launcher`, `breaching_enemy_shotgun`, `commence_fire_rifle`, `copper_spellcaster_gun`, `diamond_spellcaster_gun`, `dual_acrobat_smg`, `fly_swatter_launcher`, `focus_staffbow`, `gold_spellcaster_gun`, `iron_spellcaster_gun`, `lethal_assault_rifle`, `malignant_spellcaster_gun`, `multipurpose_staffrifle`, `quick_arms_handgun`, `silent_assassin_rifle`, `thermal_process_thrower` |
| `swingcast_weapon` (15) | Magic 12, Strength 10 | `EQUIP`, `USE`, `ATTACK` | curated | `bound_sword`, `charged_twin_blade_staff`, `copper_swingcast_staff`, `crystal_bladed_staff`, `diamond_swingcast_staff`, `gold_swingcast_staff`, `iron_swingcast_staff`, `mana_force_blade`, `mana_force_blade_sheath`, `netherite_swingcast_staff`, `silver_swingcast_staff`, `sky_edge_sword`, `smashcast_scepter`, `soulstained_steel_swingcast_staff`, `spellcharged_greatsword` |
| `staff` (9) | Magic 14, Intelligence 8 | `EQUIP`, `USE`, `ATTACK` | curated | `circuit_heat_staff`, `illuminate_stellar_staff`, `mithril_freecast_staff`, `multicast_echo_staff`, `pastel_staff`, `revolvercast_staff`, `unite_luna_staff`, `wooden_wand`, `zenith_staff` |
| `bow` (2) | Magic 12, Dexterity 8 | `EQUIP`, `USE`, `ATTACK` | curated | `bound_bow`, `elemental_bow` |
| `shield` (3) | Magic 10, Endurance 8 | `EQUIP`, `USE`, `ATTACK` | curated | `bulwark_greatshield`, `parrycast_buckler`, `reflectcast_shield` |
| `armor` (28) | Magic 14, Endurance 8 | `EQUIP`, `USE` | curated | the `apprentice_mage`, `chromatic_magia_dress`, `element_maiden_robe`, `enchantress`, `magi_agent_suit`, `soulcollector` and `stealth_rune_armor` sets |
| `curio` (15) | Magic 12 | `EQUIP`, `USE` | curated | `absorption_amplify_amulet`, `ashen_circlet`, `attackcast_ring`, `autocast_amulet`, `enchanted_circlet`, `instant_search_brazier`, `jumpcast_charm`, `luminous_device`, `magi_compressor_gadget`, `mana_shield_charm`, `satellite_followcast_amulet`, `scrollcaster_gauntlet`, `spell_cast_parrying_ring`, `spellcaster_ammo_pouch`, `spellcaster_quiver` |
| `amplifier` (11) | Magic 12 | `EQUIP`, `USE` | curated | `copper_spell_amplifier`, `diamond_spell_amplifier`, `gold_spell_amplifier`, `iron_spell_amplifier`, `netherite_spell_amplifier`, `silver_spell_amplifier`, `soulstained_steel_spell_amplifier`, `photon_siphon`, `protection_spell_supporter`, `spell_autonomy_card`, `spell_invoke_card` |
| `broom` (2) | Magic 14, Dexterity 8 | `EQUIP`, `USE` | curated | `floatmount_broom`, `hoverride_broom` |
| `exploration_tool` (1) | Magic 10, Intelligence 6 | `EQUIP`, `USE` | curated | `explorers_cane` |
| `workstation` (6) | see below | `INTERACT_BLOCK` | curated | `spellcaster_workbench` (Building 12, Magic 12), `spell_calibration_bench` (Tinkering 12, Magic 12), `alchemy_brewer` (Magic 12, Intelligence 12), `atelier_station` (Building 8, Magic 8), `apprentice_desk` (Intelligence 6, Magic 8), `essence_smoker` (Building 6, Magic 8) |
| `automation_device` (1) | Tinkering 12, Magic 12 | `INTERACT_BLOCK` | curated | `spell_dispenser` |

Workstation anchors are placed against vanilla blocks the calibration corpus already reviews: the
smithing table and enchanting table for the workbench, the brewing stand for the alchemy brewer, the
loom for the atelier station, the lectern for the apprentice desk, the smoker for the essence
smoker.

### Exempt (70 entries)

| Group | Why | Entries |
| --- | --- | --- |
| `component` (44) | Ingredient, ammunition, casing, mould, ink, weave, plate, flask or food. §6.2: classify and normally exempt; do not lock by namespace alone. | every `*_round`, `empty_*_casing`, `*_ink`, `*_mold`, `*_shard`, `*_ingot`, `*_plate`, `*_weave*`, `*_flask`, plus `anti_mana_arrow`, `arcane_cinder`, `arcane_propellant_charge`, `bullet_rune`, `comfort_berries`, `comfort_sandwich`, `craftsmans_delight`, `overdrive_broom_engine`, `scrollwoven_parchment`, `spell_bullet_head`, `spell_side_edge`, `spell_side_edge_mirror`, `spellstained_diamond`, `storage_stabilizer`, `wisdom_shard` |
| `block_item` (11) | The placed block carries the operation gate; placement has its own setting and is off by default. | the item forms of `alchemy_brewer`, `apprentice_desk`, `arcanum_in_a_jar`, `atelier_station`, `creative_spell_dispenser`, `essence_smoker`, `magnetic_stability_anchor`, `spell_calibration_bench`, `spell_dispenser`, `spellcaster_accessory_case`, `spellcaster_workbench` |
| `utility_block` (10) | Not a reviewed workstation: lights, traps, decoration and spell-created blocks inherit the gate of whatever created them (§6.2). | `arcanum_in_a_jar`, `comfort_berry_bush`, `frost_rune_trap`, `healing_bloom_light`, `mage_light_torch`, `magnetic_stability_anchor`, `otherworld_lens_lens`, `potted_comfort_berry_bush`, `rift_hole`, `wizardlamp_lantern` |
| `exempt_block` (3) | Creative content, and storage: §12.1 adds no new restriction on retrieving or storing items. | `creative_spell_dispenser`, `personal_shelf_chest`, `spellcaster_accessory_case` |
| `unclassified` (2) | No reviewed role matches the id, and namespace membership alone is not a reason to lock it. | `mana_thruster`, `scarlet_thirst` |

## Known limits

- `revolvercast_staff` is classified as a staff rather than as a spellgun. Its id carries no gun
  token and its behaviour was not inspected; the staff anchor is the more conservative of the two
  for its Magic half and asks no physical skill.
- `mana_force_blade_sheath` is classified with the weapon it sheathes.
- `instant_search_brazier` and `luminous_device` are curios rather than blocks: they are not
  registered as blocks in 0.9.7.1.
- Codex's own spells are not separately profiled. They are registered into Iron's spell registry, so
  the Iron's spell adapter owns them and the `METADATA` model ranks them by their native rarity and
  progression like any other spell; the automatic gate engine records them as owned rather than
  producing a competing estimate.
- The `ONLINE_OWNER` policy evaluates only online owners by design. Persistent owner authorization —
  trusted ownership changes, revocation, expiry, offline evaluation — is explicitly out of scope
  until those are defined (§6.5).

## Verification

| Claim | Where |
| --- | --- |
| Every registered item and block has a ledger outcome with a recorded reason | `CodexLedgerGameTest.everyCodexEntryHasALedgerOutcome` |
| Reviewed roles land where they were reviewed, and storage books never gate `TAKE` | `CodexLedgerGameTest.reviewedRolesLandWhereTheyWereReviewed` |
| The integration switched off leaves Codex content ungated | `CodexLedgerGameTest.switchingTheIntegrationOffLeavesCodexContentUngated` |
| A container is gated by its capacity, allows a qualified player, and never blocks retrieval | `CodexEquipmentGameTest.aCodexContainerIsGatedByItsCapacity` |
| A spellgun carries both a physical and a magic requirement | `CodexEquipmentGameTest.aSpellgunCarriesBothItsPhysicalAndItsMagicRole` |
| Operating a workstation is gated; breaking and placing it are not | `CodexWorkstationGameTest.operatingIsGatedAndBreakingIsNot` |
| A refused spellgun cast restores borrowed mana and consumes no ammunition | `CodexSpellgunGameTest` |
| The strict policy refuses an unqualified owner and spends nothing | `CodexDispenserGameTest.strictPolicyRefusesAnUnqualifiedOwnerAndSpendsNothing` |
| The strict policy pauses for an offline or absent owner | `CodexDispenserGameTest.strictPolicyPausesForAnOfflineOrAbsentOwner` |
| The default policy lets the machine decide | `CodexDispenserGameTest.theDefaultPolicyLetsTheMachineDecide` |

All ten run only when Codex is installed: they are registered by `CodexGameTests` from
`RegisterGameTestsEvent` and carry no `@GameTestHolder`, so an installation without the mod never
loads them.
