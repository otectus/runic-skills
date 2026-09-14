# Progression provider audit — 2.2.0

The isolated production profile contains **16,045 registered items**, 147 loaded mod IDs
(including bundled libraries/test harness), and every current ID provider. The export examines
4,837 gear, utility or explicitly configured entries and their 4,776 loaded output recipes.
This is an actual registry/recipe export after datapacks, not a list inferred from mod names.

Machine-readable evidence lives under [`progression-2.2.0/`](progression-2.2.0/): the complete
compressed runtime export, historical provider vectors, comparison, dependency/version/hash
manifest and verification receipt. `tools/audit_progression.py` reproduces vector comparisons
and recipe warning lists from these exports. It does not modify recipes or player inventories.

## Baseline qualification

The original `runicskills-2.1.2.jar` has SHA-256
`54d505772e11302874c9d6e72af5b1cb9144c3aff32a41d63a69543ed434b514`. Its complete dedicated-server
boot fails on the old `completeUsingItem` mixin target. An isolated child-first classloader
instead executes `HandlerSkill`, lock providers and curated integration bytecode from the separately
retained 2.1.2 pack jar (SHA-256
`0e08b9c9b1474a41e05056ad3b38c4af932063444554b4f247f99a917f1758f8`)
against the same current Forge registry and relevant config defaults. That comparison succeeded
and exported 3,161 historical ID rules. It is **not** a successful old-release gameplay test.

There are 505 changed registered-item vectors relative to that artifact. This delta includes
pre-existing uncommitted 2.1.2 work preserved at session start, notably newer integration
profiles; it must not be attributed entirely to this update's edits. The session source archive
and change inventory distinguish that working-tree baseline from the old release artifact.

## Confirmed results

Comparing identical functional families through verified native or curated material chains
produces **188 comparable upgrade pairs**. Gold, magic materials and role-changing sidegrades
are excluded from this linear ordering. Each comparison checks every skill component.

| Metric | Old provider bytecode | 2.2.0 |
| --- | --- | --- |
| Component-wise requirement inversions | 24 | 0 |
| Nonempty skill-family mismatches | 13 | 0 |
| Requirements above skill cap 32 | — | 0 |
| Requirements impossible under custom budget 256 | — | 0 |
| Overlapping automatic-provider candidates | — | 0 |

The absence of warnings in these checks is scoped to the exported defaults and verified
material pairs; it does not prove every special material in every mod has correct balance.

| Actual registered item | Before | After |
| --- | --- | --- |
| `spartanweaponry:wooden_rapier` | Strength 8, Dexterity 6 | Explicitly unrestricted |
| `spartanweaponry:stone_rapier` | Dexterity 2, Intelligence 2 | Unchanged |
| `spartanweaponry:wooden_dagger` | Strength 8, Dexterity 6 | Explicitly unrestricted |
| `spartanweaponry:stone_dagger` | Dexterity 2 | Unchanged |
| `spartanshields:wooden_basic_shield` | Endurance 8, Constitution 6 | Explicitly unrestricted |
| `spartanshields:stone_basic_shield` | Endurance 2, Constitution 2 | Unchanged |
| `spartanshields:wooden_tower_shield` | Endurance 8, Constitution 6 | Explicitly unrestricted |
| `spartanshields:stone_tower_shield` | Endurance 4, Constitution 3 | Unchanged |

Spartan Weaponry 3.2.1 and Shields 3.1.1 are installed, alongside Spartan Fire 2.1.0,
Cataclysm 1.1.2 and Toolkit 1.6.1. Twenty-six Spartan vectors change. The existing positive
wooden-club requirement remains intentional. The loaded basic stone shield recipe includes
the wooden shield; the stone rapier recipe uses cobblestone and a handle. No upstream recipe
was rewritten to manufacture a missing predecessor edge.

Five actual non-gear generic false positives become unhandled/unrestricted:

- Nature's Aura `conversion_catalyst`, `crushing_catalyst`, and `furnace_heater` are blocks,
  rather than the magic or armor gear their substrings previously suggested.
- Bosses of Mass Destruction `gauntlet_blackstone` is a block, rather than an equipped gauntlet.
- Cataclysm `sandstone_poison_dart_trap` is a trap block, rather than ranged equipment.

Native material identities also correct Aquaculture, Mowzie's and relevant Let's Do gear.
Fantasy Armor supplies vanilla armor material identities for its 116 generated entries;
the export retains that evidence rather than guessing from durability or rarity.

## Every current provider

Counts are candidate rules generated at default config; manual/default rules can already
cover a namespace without a generic candidate. Full item IDs, candidates, winners, multipliers,
scaling and dependency versions/hashes are in the runtime export.

| Provider | Candidates | Review boundary |
| --- | ---: | --- |
| Spartan | 909 | Curated starters/materials/families and add-on discovery; explicit exemptions verified |
| Ice & Fire | 226 | Curated material/dragon armor roles and discovery retained |
| Locks | 23 | Existing lock/tool tiers retained |
| Samurai Dynasty | 177 | Native catalogue and explicit family/material profiles retained |
| More Vanilla Tools/Armor | 135 | Authored material tables and tool roles retained |
| Jewelcraft | 55 | Equipment rows and existing component exemptions retained |
| Iron's Spellbooks | 106 | Authored book/staff/armor/spell/utility rules retained |
| Epic Knights family | 570 | Actual namespace inventory; unknown materials remain labeled inference |
| Aquaculture | 16 | Native vanilla tool tiers override namespace base; starter exemption |
| Call of Yucutan | 19 | Conservative material fallback |
| Galosphere | 6 | Conservative material fallback |
| Undergarden | 40 | Conservative material fallback |
| Deeper and Darker | 19 | Conservative material fallback |
| Dragonsteel | 0 | Six registered material/component items; no inferred equipment gates |
| Cataclysm | 25 | Generic trap-block false positive removed; authored special gear retained |
| Mowzie's Mobs | 10 | Native vanilla materials where exposed; special fallback retained |
| Farmer's Delight | 5 | Native knife materials; food excluded from generic gear scan |
| Siege Machines | 0 | Machines/workbench already have built-in default rules; entity placers are not inferred hand gear |
| Fantasy Armor | 116 | Verified vanilla material API identity |
| Nature's Aura | 19 | Three generic block false positives removed; custom material uncertainty remains |
| Bosses of Mass Destruction | 1 | Gauntlet block excluded from generic gear classification |
| Jet and Elia's | 175 | Correct `jet_and_elias_armors` item namespace; conservative materials |
| Nichirin Dynasty | 0 | Sixteen named weapons are already covered by built-in defaults; twenty namespace entries total |
| Saints Dragons | 21 | Conservative material fallback |
| Stalwart Dungeons | 35 | Conservative material fallback |
| Dungeons Delight | 5 | Food/components excluded; finished equipment retained |
| Fruits Delight | 1 | Food/components excluded |
| Rustic Delight | 0 | 154 registered entries, no additional generic gear candidates |
| Vintage Delight | 0 | 148 registered entries, no additional generic gear candidates |
| Brewin' and Chewin' | 0 | 47 registered entries, no additional generic gear candidates |
| Let's Do family | 14 | Actual enabled namespaces; native material identity where available |
| Starcatcher | 39 | Dedicated rod/reusable-tackle policy; bait/catch exemptions retained |
| Overgeared | 27 | Finished equipment policy; hammer/tongs/blueprint/parts distinctions retained |
| Simply Swords | 133 | Existing native adapter/reference scaling; pre-existing working-tree changes retained |
| Simply More | 110 | Existing native adapter/reference scaling |
| T.O. Magic / Travel Optics | 88 | Existing native spell/equipment profiles and reference scaling |
| Tide | 18 | Native fishing/equipment profiles; ordinary bait/catches exempt |
| Tinkers' stack provider | Per stack/action | Stable, conservative beta and native add-on tests; no permanent item-ID material rule |

Generic fallback multipliers retain their legacy minimum positive gate of 2; zero material
reference means an explicit exemption. A multiplier is not an off switch. Use the master,
integration, namespace or exact-item switches to disable automatic policy. Optional reference-32
cap scaling is applied once; manual levels remain literal. Tinkers'/newer reference-scaled
providers bypass that additional multiplier stage.

## Warning review and remaining uncertainty

- **961 unverified material fallbacks:** the runtime did not expose a supported vanilla material
  identity. These retain their existing conservative namespace requirements and say UNDETERMINED.
  They are not reported as verified native material adapters. Pack authors can provide explicit
  rules for their intended special-material progression.
- **154 utility/component review warnings:** 140 are deliberate built-in utility/default rules.
  Remaining rows are six Simply More backhand blades, Simply Swords' Twisted Blade, three
  Ice & Fire dragon armor heads, Iron's scroll forge and Tide's three swordfish. These are
  intentional finished gear/utility roles. Tide's swordfish are actual native `SwordItem`
  subclasses with IRON tier, despite also being edible. No generic component gate was retained
  solely because a handle/head/blade substring matched.
- **249 recipe use-gate warnings:** a loaded recipe has an ingredient for which every alternative
  has a higher use gate than the output. These are separate from actual CRAFT rules. The export
  preserves all ingredient alternatives and does not claim that holding/consuming such an item
  requires wielding it. Automatic Tinkers' material rules explicitly exempt crafting/removal.
- **32 mandatory recipe-cycle warnings:** sapling reproduction, template duplication, native
  repair/upgrade/conversion recipes and mod-specific reusable inputs create cycles. A recipe
  graph alone omits loot, trades and world acquisition. These are review findings, not proven
  progression deadlocks, and their recipes were not flattened.
- **12 dormant configured IDs:** six old Ars aliases, Cataclysm `final_fractal`/`zweiender` and
  four removed Enigmatic Legacy entries are absent in this dependency set. They remain dormant
  for config compatibility, are listed explicitly, and are not counted as registered coverage.
- **Dedicated-server material getter:** Nature's Aura 39.4's armor-material presentation getter
  is unavailable on the dedicated runtime. The audit now records safe class/enum identity instead
  of invoking a client presentation method. This was reproduced during production validation.

The unfiltered pack is blocked by Roaring beta 0.3 loading `LocalPlayer` on a dedicated server.
Only that jar was moved out of the isolated validation copy. The exact filtered dependency list,
upstream warnings and successful/failed test receipts are retained; no untouched-pack success
or universal compatibility claim is implied.
