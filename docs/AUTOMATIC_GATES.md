# Automatic gates for unconfigured content

A deterministic content-analysis engine that fills the gaps your rules leave. When Runic Skills
meets an item, block or spell that no manual rule, curated profile, native adapter or existing
integration has an opinion about, this decides whether it can say anything useful — and, most of
the time, deliberately says nothing and records why.

It is on by default (`enableAutoGates = true`). There are no online calls, no runtime training and
no nondeterministic predictions: the same inputs produce the same catalog, with the same content
digest, on any machine in any locale.

> **A note on the word "confidence".** Every number this document calls confidence describes
> *evidence quality* — how much was observed, how similar the comparable content was, how much that
> content agreed. It is not a measured probability that a gate is objectively correct. A score of
> 0.90 does not mean "90% accurate", and nothing here will tell you that it does.

---

## What it does, in one paragraph

After registries, tags and recipes are ready, it walks the item and block registries in stable id
order, asks whether each entry is already spoken for, builds an immutable descriptor of cheap
native facts about the ones that are not, classifies a role from that structure, compares the entry
against reviewed anchors of the same role, and either proposes one primary skill requirement and at
most one secondary — or abstains. Every entry gets a recorded outcome. Most of them are
abstentions, and that is the intended result.

---

## Configuration

All of these live in `runicskills.common.json5` under the **Automatic Gates** group. Apply edits with
`/skillsreload`; LIVE catalogs are invalidated and rebuilt from the updated settings. FROZEN mode
keeps the accepted catalog, while current manual overrides still take precedence.

Protocol 18 sends the action-scoped enforcement rules and independent legacy defaults with the
configuration in one complete revision. Client interaction checks use those same rules. A scoped
rule affects only its named actions and registry domain; its flattened tooltip entry never becomes
an enforcement fallback. Manual exact-id rules win, and independent legacy defaults continue to
apply to actions a scoped rule does not cover.

| Setting | Default | Meaning |
| --- | --- | --- |
| `enableAutoGates` | `true` | Master switch for the inferred layer. Off removes **only** the inferred rules after the next successful reload. |
| `autoGateItems` | `true` | Discover relevant unconfigured equipment and items. |
| `autoGateBlocks` | `true` | Discover recognised workstations, and the harvest targets below if that switch is on too. |
| `autoGateSpells` | `true` | Infer through supported native spell adapters. |
| `autoGateCrafting` | `false` | Also gate **crafting** an inferred equipment item, at the same requirement as using it. Off means equipment can be crafted, traded and stored before it can be used. |
| `autoGatePlacement` | `false` | Add inferred block-placement requirements. Operation gates on the placed block remain available. |
| `autoGateHarvestBlocks` | `false` | Gate **harvesting** blocks to which vanilla itself assigns a tool tier. Gating the tool doing the harvesting remains available and is unaffected. |
| `autoGateMinimumConfidence` | `0.75` | Evidence threshold a neighbour estimate must reach to be enforced. |
| `autoGateUseRoleFallbacks` | `true` | Use the reviewed conservative low-tier profile when the role is well established but the tier evidence is not. |
| `autoGateRecipeEvidence` | `true` | Use bounded recipe and upgrade evidence. |
| `autoGateLearnFromManualRules` | `false` | Allow your own manual rules to become calibration examples. |
| `autoGateExcludedNamespaces` | `[]` | Namespaces inference must not touch. |
| `autoGateExcludedItems` | `[]` | Exact item ids inference must not touch. |
| `autoGateExcludedBlocks` | `[]` | Exact block ids inference must not touch. |
| `autoGateExcludedSpells` | `[]` | Exact spell ids inference must not touch. |
| `autoGateMode` | `LIVE` | `LIVE` rebuilds from current inputs; `FROZEN` keeps a catalog you accepted. |

Two tags do the same job from a datapack, so a pack can ship its exclusions rather than asking
every server owner to type them:

| Tag | Effect |
| --- | --- |
| `#runicskills:auto_gate/excluded` (items) | The item is never given an inferred gate. |
| `#runicskills:auto_gate/excluded` (blocks) | The block is never given an inferred gate. |
| `#runicskills:auto_gate/workstation` (blocks) | The block is a reviewed, operable workstation — see *Blocks* below. |
| `#runicskills:auto_gate/spell_focus` (items) | The item is a reviewed spellcasting focus. |

### How this interacts with the switches you already had

An exclusion suppresses a *generated* gate. It is **not** a permission and does not lift an explicit
rule you wrote. Those are different operations and conflating them is how an "exclusion" silently
becomes a bypass.

- `enableItemLocks = false` still switches off the whole item/block lock domain. The engine may
  still produce a preview, but it enforces nothing there.
- `enableSpellLocks = false` switches off every Runic requirement on a cast. It does not waive a
  spellbook's separate equipment requirement.
- `ironsEnableSchoolGating = false` switches off generated Iron's spell requirements. The engine
  does not route around it.
- `enableAutoGates = false` switches off **this layer only**. Your manual rules, the curated
  profiles, the native adapters and every existing integration keep working exactly as configured.

`/skills locks coverage` prints the three master switches above (`enableItemLocks`,
`enableSpellLocks`, `enableAutoGates`), the mode and threshold, the domain and action toggles, and
the authored rule count, followed by the generated catalog's status and its
outcome counts. It is the fastest way to answer "which setting is causing this
restriction?". It does not print the per-integration toggles such as `ironsEnableSchoolGating`;
`/skills locks explain <target>` names the layer and provider that decided one particular target,
which is the question those toggles answer.

---

## How inference works

### 1. Nothing is inferred where something already decided

Before anything is measured, a candidate is dropped if a manual config rule, an authored
`runicskills/gates` rule, a curated profile, a native adapter or an existing per-mod generator has
already decided that target — or if a native adapter *owns* it. Inference fills gaps. It never
improves, overrides or competes with somebody else's answer.

### 2. The role comes from structure, never from the name

The descriptor is a typed record of native facts, not a bag of words:

| Field group | Examples |
| --- | --- |
| Identity | target kind (`item`/`block`/`spell`), namespace, registry path |
| Role | the established role, its subrole (armour slot, `axe`), and the confidence in it |
| Intrinsic stats | attack damage and speed, material tier, mining speed, armour/toughness/knockback attribute modifiers, durability, enchantment value |
| Tags | every registry tag the entry carries |
| Acquisition | cheapest supported recipe depth, and the id it is a verified smithing upgrade of |
| Presentation | vanilla rarity (a minor feature for generic equipment) |
| Bookkeeping | which expected features were **absent**, and which adapters contributed |

The role is decided in this order, and the order is the whole point:

1. **A native equipment type** — `ArmorItem`, `ShieldItem`, `FishingRodItem`/`BucketItem`,
   `SwordItem`/`TridentItem`, `AxeItem`, `DiggerItem`, `ProjectileWeaponItem`. Confidence **1.00**.
2. **A reviewed tag** — the spell-focus tag for items; for blocks, the workstation tag and then
   vanilla's own harvest-tier tags. Confidence **0.90**.
3. **A native non-equipment type** — food, then block item. Confidence **1.00**.
4. **A native attribute** — a real additive main-hand attack-damage modifier makes something a
   melee weapon whatever class it extends. Confidence **1.00**.
5. **The registry path** — recorded only, at confidence **0.45**.

The reviewed tag sits above food and block items on purpose: a pack that puts an edible or a
placeable item in the spell-focus tag has said something deliberate about it, and the item's being
edible is not a reason to ignore that. It stays *below* the native equipment types, because nothing
a tag says should turn a sword into a spellbook.

The enforcement floor is **0.85**, so step 5 can never produce a gate on its own. That is not a
blocklist; it is the structure of the decision. It is why:

- an ingredient named `diamond_sword_blade` is a **material**, not a sword;
- a decorative `magic_tome` is a **material**, not a spellbook — unless a pack puts it in the
  reviewed focus tag, which is exactly how you opt one in;
- an ordinary `fishing_rod` is a recognised **utility** item, not a magic rod. The old keyword
  generator needed a hand-written never-gear list for this one item and for every mod that copied
  vanilla's naming.

Roles that are never gated automatically: material, food, decoration and utility.

### 3. Comparable content, not a formula

Neighbours are restricted to the same target kind and the same role first; armour is additionally
compared within its own slot. A chestplate is never estimated from a pickaxe.

Similarity is a weighted sum of four components, with the weights fixed in the calibration file:

| Component | Weight | What it compares |
| --- | --- | --- |
| Structural | **0.45** | Same subrole, and distance on the material-tier ladder |
| Intrinsic | **0.35** | The role's own stats, each normalised against a **fixed** calibration range |
| Recipe/upgrade | **0.15** | Comparable acquisition depth, and whether both are verified upgrades |
| Id tokens | **0.05** | Jaccard overlap of the `_`-delimited path tokens |

Two details matter more than the numbers:

- The ranges are **fixed**, not recomputed from what is installed. Installing one unusually strong
  modded item does not renormalise your server's balance.
- A component whose evidence is missing on either side contributes **zero**, and the sum is **not**
  renormalised over the components that happened to be present. Two items described only by their
  path tokens therefore score as two items about which almost nothing is known — not as a perfect
  match.

At most **5** neighbours are selected, ties broken by anchor id so registry walk order cannot matter,
and at least **3** are required. Each skill's level is the **weighted median** of the neighbours'
reviewed levels, which resists one extreme sample. A secondary skill needs **60%** of the selected
neighbour weight behind it, and it is dropped if it lands at or above the primary. An inferred
vector is capped at one primary and one secondary; anything richer is a curated decision.

### 4. Confidence and abstention

```
candidateConfidence = min(roleConfidence,
      0.40 * featureCoverage
    + 0.35 * weightedNeighborSimilarity
    + 0.25 * neighborAgreement)
```

- `featureCoverage` — the fraction of the role's expected weighted features the candidate actually
  supplied, counting **absent recipe evidence as absent**.
- `weightedNeighborSimilarity` — the mean similarity of the selected neighbours.
- `neighborAgreement` — `1 - min(1, weightedMeanAbsoluteDeviation / 8)` over the primary
  requirement. The deviation itself is stored too.

| Result | Behaviour |
| --- | --- |
| Confidence ≥ threshold and role confidence ≥ 0.85 | Apply the inferred vector after the reachability check. Labelled `NEIGHBOR_ESTIMATE`. |
| Confident role, insufficient or disagreeing tier evidence | Apply the reviewed conservative profile if `autoGateUseRoleFallbacks` is on. Labelled `ROLE_FALLBACK`. |
| Role confidence below 0.85 | `BELOW_THRESHOLD`. No gate. |
| Fewer than three trusted anchors | `TOO_FEW_ANCHORS`. Falls back if enabled, otherwise no gate. |
| The nearest trusted content is itself ungated | `UNDETERMINED`. No gate — a wooden sword must not inherit the cheapest gated anchor's requirement. |
| Ingredient, food, decoration, utility, excluded, owned, already decided | Recorded and skipped. |

**Every discoverable entry gets an outcome; not every entry deserves a restriction.** On a
near-vanilla server this engine produces a handful of conservative fallbacks and several thousand
recorded abstentions, and that is the correct behaviour rather than a failure to find work.

### 5. Recipes are evidence, not a classifier

Recipes are read once per rules revision, never during an action, and never by crafting anything:

- The **cheapest** supported acquisition route decides depth. An expensive optional recipe does not
  make an item expensive when an easier legitimate one exists.
- Traversal is bounded by a fixed pass count over capped recipe, ingredient and alternative counts,
  so a recipe cycle terminates by construction.
- An item the passes never resolve has **no** recipe evidence and says so, which lowers its feature
  coverage. Loot, trading, world generation and scripts are real acquisition routes, and an
  incomplete recipe list is not proof of a mandatory one.
- Output quantity normalises material effort. Stack **capacity** is ignored entirely as a power
  signal — changing a stack's size or count can never change a requirement.
- Recipe evidence can nudge an estimate by at most **two** reference levels. It cannot turn a
  name-only classification into a high-tier gate, and it is not applied to a harvest gate at all —
  see *Blocks* below for why.

### 6. Scaling and reachability, once each

1. Produce the reference vector (reviewed anchors, reference cap 32, multiplier 1).
2. Apply the one optional cap conversion, if `scaleGeneratedLockRequirements` is on.
3. Remove no-op level-one requirements — every skill starts at one.
4. Check attainability against the real per-skill cap and global level budget.
5. If it does not fit: drop the generated **secondary** first, then lower the generated **primary**
   within the remaining budget, and if nothing meaningful survives, **add no gate** and record why.

An inferred rule never carries an integration multiplier, so there is no second multiplier to apply
twice. A manually configured requirement is never silently rewritten by step 5 — an impossible
manual rule is reported to you to resolve.

### Blocks

A block can reach the role-confidence floor two ways, and only two.

**As a workstation**, through the reviewed `#runicskills:auto_gate/workstation` tag. Having a block
entity is explicitly **not** enough: that would gate every chest, sign, bed and banner in the game.
A block that has one is recorded at confidence 0.45 so you can see the engine considered it and
declined. Workstation rules are about `INTERACT_BLOCK` (and `PLACE_BLOCK` if you enable it).

**As a harvest target**, when vanilla itself gives the block a tool tier: a `#minecraft:mineable/*`
tag *and* one of `#minecraft:needs_stone_tool`, `#minecraft:needs_iron_tool` or
`#minecraft:needs_diamond_tool`. That is not a guess about the block — it is the game stating which
tool tier is required to get anything out of it, which is precisely the progression fact a harvest
gate is about. Hardness, blast resistance, dimension and the word "ore" in a path decide nothing:
coal ore, stone and deepslate carry no tier tag and abstain.

Harvest candidates are compared within their own tier, and the reviewed anchors are not new balance.
Each one is the Building level of the cheapest vanilla pickaxe that can drop the block, and those
pickaxe levels have shipped as built-in defaults since the lock table existed:

| Vanilla tier tag | Cheapest pickaxe | Harvest requirement |
| --- | --- | --- |
| `needs_stone_tool` | stone — itself reviewed as ungated | none; these blocks abstain |
| `needs_iron_tool` | `minecraft:iron_pickaxe`, Building 8 | Building 8 |
| `needs_diamond_tool` | `minecraft:diamond_pickaxe`, Building 16 | Building 16 |

Recipe evidence is deliberately **not** applied to a harvest gate. A netherite block's nine-ingot
recipe is real and says nothing about how hard the block is to mine, and letting it nudge the
estimate put that block one level off the tier ladder every other harvest anchor sits on.

Inferred block rules are **typed and action-scoped**, so "you must be Tinkering 7 to *operate* this
workstation" does not also forbid breaking it, and "you must be Building 8 to *harvest* this ore"
does not forbid right-clicking it. That distinction cannot be expressed in the legacy id table at
all, which is why block rules live only in the typed layer.

### Crafting

`autoGateCrafting` is the one switch that *adds* an action rather than removing one. No role
proposes crafting on its own; when the switch is on, every inferred **equipment** rule — armour,
shields, melee weapons, mining tools, ranged weapons, spellcasting foci — gains `CRAFT` at the
requirement it already had. It never invents a second, separately estimated crafting requirement,
never gates a target that was not already gated, and never touches blocks or spells: crafting a
workstation is a different operation from crafting a sword.

Enforcement runs through the result-slot seam manual rules already use, so an inferred crafting gate
behaves exactly like one you wrote by hand.

### Spells

A spell domain belongs to the mod that registered it. Spell registries are enumerated by their own
adapters, independently of the item registries, so `autoGateSpells` controls a real discovery pass:
Iron's Spells registers an enumerator that walks its Forge registry, which means addon spells
registered into it are covered too.

What that pass produces is **coverage, not rules**. The adapter can read the spell's native level,
rarity and its own progression; a neighbour estimate over item statistics cannot, and a verified
native adapter sits above trusted comparable content in the evidence order. So every enumerated
spell gets an outcome naming the adapter that owns it — `OWNED` — or `ALREADY_DECIDED` if you wrote
a rule for it, or `EXCLUDED_BY_CONFIG`. The number itself comes from `ironsSpellGateModel` and
`ironsEnableSchoolGating`, exactly as it did before this engine existed.

Authored spell rules from the datapack schema below are enforced through the spell master switch
(`enableSpellLocks`) independently of `enableItemLocks`, and they are terminal: an authored rule for
a spell decides it, allow or deny, before any generator runs.

An adapter that throws while enumerating costs its own coverage rows and nothing else.

---

## Authoring your own rules: `data/<namespace>/runicskills/gates/*.json`

Explicit rules outrank every generated layer, including this one.

```json
{
  "schema_version": 1,
  "requires_mods": ["irons_spellbooks"],
  "rules": [
    {
      "id": "examplepack:dragonskin_override",
      "target": {"kind": "item", "id": "irons_spellbooks:dragonskin_spell_book"},
      "actions": ["equip", "use"],
      "result": {"type": "requirements", "skills": {"magic": 24}, "scaling": "absolute"}
    },
    {
      "id": "examplepack:guidebook_exemption",
      "target": {"kind": "item", "id": "examplemod:guidebook"},
      "result": {"type": "allow"}
    },
    {
      "id": "examplepack:workbench_operation",
      "target": {"kind": "block", "id": "examplemod:spellcaster_workbench"},
      "actions": ["interact_block"],
      "result": {"type": "requirements", "skills": {"tinkering": 10, "magic": 8}}
    },
    {
      "id": "examplepack:leave_this_alone",
      "target": {"kind": "item", "id": "examplemod:ritual_dagger"},
      "result": {"type": "exclude_from_inference"}
    }
  ]
}
```

| Field | Required | Notes |
| --- | --- | --- |
| `schema_version` | yes | Must be `1`. |
| `requires_mods` | no | If any named mod is absent, the whole file is skipped — not failed. |
| `rules[].id` | yes | A resource id. The merge key; declaring it twice rejects **both** copies. |
| `rules[].target.kind` | yes | `item`, `block`, `entity` or `spell`. |
| `rules[].target.id` | yes | A resource id in that registry. |
| `rules[].actions` | no | `LockAction` names, lower case. Absent means every action in the target's domain. |
| `rules[].result.type` | yes | `requirements`, `allow` or `exclude_from_inference`. |
| `rules[].result.skills` | for `requirements` | Skill id → level. Must name real skills and be non-empty. |
| `rules[].result.scaling` | no | `absolute` (default), `reference_32` or `native_cap_relative`. |

Rules of the road, each of which exists because the alternative is a silent failure:

- **Unknown fields are refused by name.** A rule that loads with a typo in it is a rule you believe
  is protecting you.
- **`{"type": "allow"}` must be spelled out.** An empty or invalid requirement map is *not* a
  permission.
- **An exclusion is not a permission.** `exclude_from_inference` stops a generated gate; it does not
  permit the action against lower-priority sources.
- **A rule for an absent mod stays dormant** and is logged by id at `INFO` on every reload. It is
  not carried into the JSON export, which lists the rules that are in force and the inference
  exclusions. A rule naming something an *installed* mod does not register is a typo and
  invalidates that file.
- **A reload containing any unreadable file is refused while there are rules to keep**, and the
  previously loaded rules stay in force. The half you cannot see is the half that has stopped
  protecting you. The one exception is a server with nothing to keep: on first load the readable
  files are installed and the failures are logged, because refusing there would leave no rules at
  all — the same outcome with less of the pack working.
- Limits per reload: 2,048 rules, 256 KiB per document, 64 values per selector.

Explicit rules are also action-scoped literally: a rule about `attack` and `use` does not silently
answer `equip`.

---

## Commands

All under the existing `/skills locks` tree. The operator commands need permission level 2.

| Command | Shows |
| --- | --- |
| `/skills locks inspect [action]` | The held stack's requirements, provenance and whether the action is allowed. |
| `/skills locks inspect block` | The block you are looking at — separately from the held item. |
| `/skills locks inspect block <pos>` | The block at a position. |
| `/skills locks inspect block <id>` | A block by registry id. |
| `/skills locks inspect spell <id> [level]` | The spell's requirement, the model that produced it, and what the metadata model says. |
| `/skills locks explain <target>` | Winning layer, typed rules, inference evidence, rejected alternatives, and every suppressed candidate. Accepts `item:minecraft:iron_sword` or a bare id. |
| `/skills locks preview` | Builds a candidate catalog and reports added/removed/changed against the published one. **Publishes nothing.** |
| `/skills locks apply-preview <token>` | Publishes the unchanged preview and writes it as the accepted catalog. Rejects a stale preview. |
| `/skills locks coverage` | Active gate sources, domain and action toggles, authored rule count, catalog status and outcome counts, then the **inferred rules** broken down by namespace and by role. |
| `/skills locks audit` | Writes the full export to `debug/runicskills-locks.json`. |
| `/skills locks representation` | Stack-count representation diagnostics. |

The audit export carries, per item entry: the winning layer, the typed rules, every candidate that
was suppressed, and — for anything inference looked at — the outcome, all four confidence terms, the
neighbour deviation, the chosen anchors and the rejected alternatives.

Its `automatic_gates` block adds the catalog itself: status, digest, input fingerprints, the full
outcome counts, the inferred rules, the authored rules and the inference exclusions. The
*interesting* abstentions are listed there in full — `UNDETERMINED`, `BELOW_THRESHOLD`,
`TOO_FEW_ANCHORS`, `UNREACHABLE`, `BUDGET_EXHAUSTED`, `OWNED` and the config exclusions — because
"why is this **not** gated" is the harder question. The two bulk outcomes, `EXCLUDED_ROLE` and
`ALREADY_DECIDED`, appear in the counts only: on an ordinary server they are several thousand
ingredients and every rule you already wrote, and listing them would bury the rest. Any single one
of them is still reachable through `/skills locks explain <target>`.

Operator commands export counts, ids and profiles. They do not export player inventories, personal
identifiers or arbitrary NBT.

---

## Freezing a catalog

`autoGateMode = FROZEN` keeps a catalog you previously accepted, so content added afterwards stays
undetermined rather than receiving an unreviewed prediction. Explicit rules keep their precedence
either way.

```
/skills locks preview                 # builds a candidate, prints its digest, publishes nothing
/skills locks apply-preview a1b2c3d4  # publishes it and writes runicskills/gates/frozen.json
```

Then set `autoGateMode` to `FROZEN`. From that point the accepted file is what loads.

If the frozen file is missing or unreadable, you get a clear status and the **last valid catalog
stays in force**. It does not quietly fall back to `LIVE`: an operator who froze their rules should
not end up running the mode they deliberately turned off without being told.

Nothing here rewrites your configuration during a normal startup. Promoting a generated entry into
an explicit pack rule is your decision, and it produces a file you can read.

---

## The calibration corpus

`data/runicskills/gate_calibration/v1.json` in the mod jar. It is reviewed data, read once at
startup, and **not** a datapack file — a pack that disagrees with a number writes an ordinary lock
rule or a `runicskills/gates` rule, both of which outrank everything the corpus produces.

It deliberately sits in `gate_calibration/` rather than in `gates/`. The rule loader scans the
datapack folder `runicskills/gates`, which resolves to `data/<namespace>/runicskills/gates` — a
different place from `data/runicskills/gates`, so the two never collided. They were one level of
nesting apart and easy to read as the same folder, though, and a corpus inside the rule loader's
scope would not simply be ignored: it would be parsed as a malformed rule, and one unreadable file
refuses the whole reload. A GameTest now drives a real reload against the shipped resources and
asserts the folder contains nothing the rule loader cannot read.

Three kinds of row:

| Source | What it is |
| --- | --- |
| `runic_builtin_default` | A requirement this mod has shipped as a reviewed built-in default. |
| `irons_book_profile` | A reviewed Iron's spellbook gate, carried over from `data/runicskills/irons/book_profiles.json`. |
| `explicit_ungated` | Content that deliberately requires nothing: wooden and stone tools, leather armour, the crafting table, the furnace, chests. |

The ungated rows are not filler. Without them the only trusted rows would be gated ones, the
cheapest gate in the corpus would become the floor for every low-tier item, and a wooden sword
would inherit a golden sword's requirement. They also mean those entries are themselves never
gated: a reviewed "this requires nothing" is a statement about that entry, not only a reference
point for its neighbours.

**The corpus never learns from the engine's own output.** A row whose declared source is an
inference provenance string is dropped with a warning, so an exported catalog cannot be pasted back
in and become its own training data one generation later. `autoGateLearnFromManualRules` is a
separate, default-off option: when it is on, only valid opted-in manual rules are accepted, at most
16 per namespace, and engine-sourced rows are still refused.

---

## What it will not do

- Enumerate registries, inspect recipes, instantiate block entities or resolve optional classes
  during a tooltip, an inventory insertion, an attack or a cast. Runtime decisions are indexed
  lookups.
- Infer item pickup, storage withdrawal, dropping, safe unequip or ordinary transportation gates.
- Add crafting, placement or harvest restrictions unless you turn those on. Even then: crafting
  only follows an equipment gate the engine already proposed, and harvesting only follows vanilla's
  own tool-tier tags.
- Silently remove an intentional manual restriction to repair a progression cycle. It reports the
  blocked route and the rules responsible, and leaves the decision to you.
- Publish a partially assembled revision. A failed build keeps the last known good catalog and logs
  one actionable diagnostic; a generation that started earlier can never replace one that started
  later.
- Truncate the rule catalog silently. The per-revision budget is derived from the client
  synchronisation limits, and a candidate that hits it is recorded as `BUDGET_EXHAUSTED`.
