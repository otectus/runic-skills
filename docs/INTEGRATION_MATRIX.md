# Integration & Lock-Provider Matrix

> **Generated from the code, not maintained by hand.** Every row below is
> [`LockProviderRegistry`](../src/main/java/com/otectus/runicskills/integration/lock/LockProviderRegistry.java)
> read back out. The previous revision of this document was titled "(1.5.0)", listed a provider whose
> integration had been deleted, and omitted eight that existed — which is what happens to a table
> that describes code but is edited separately from it. Regenerate it when the registry changes.

Source of truth for which mods get item-lock generation and how it is wired. The
[`checkLockProviders`](../build.gradle) Gradle task and
[`LockProviderRegistryTest`](../src/test/java/com/otectus/runicskills/integration/lock/LockProviderRegistryTest.java)
fail the build if an integration class exposing `generateLockItems()` is not registered, so the
*code* cannot silently drift; this table is the human-readable view of it.

## How lock generation is wired

All lock providers are registered in `LockProviderRegistry` and iterated by
`HandlerSkill.injectIntegrationItems()`. Manual config locks still win via `putIfAbsent`.

- **Curated adapters** wrap the existing static `generateLockItems()` on a Forge-only integration
  class (hand-tuned material/weapon tables).
- **Standalone providers** are Forge-only classes with no upstream API imports — the matching
  event-handler class hard-references that mod's API and must not be eagerly resolved from the
  registry.
- **`GenericNamespaceLockProvider`** scans a mod's item namespace and classifies gear by keyword via
  [`LockGen`](../src/main/java/com/otectus/runicskills/integration/lock/LockGen.java).

### Opting out

Three levels, coarsest first:

| Setting | Scope |
| --- | --- |
| `disabledDiscoveredLockMods` | a whole namespace or provider id |
| `disabledDiscoveredLockItems` | one item id, for when classification gets a single item wrong |
| `discoveredLockLevelMultiplier` | scales every discovered lock's level |

Keyword classification matches on `_`-delimited path segments, and a short never-gear list covers the
collisions a segment match cannot resolve on its own — `fishing_rod` and `lightning_rod` really do
end in the magic-implement keyword `rod`, and a mushroom cap really is a `cap`. It was a bare
substring match until 2.0.0, which meant `bow` claimed `bowl`, `axe` claimed every `waxed_*` block,
and the only remedy was disabling the mod's locks entirely. See `LockGenTest`.

## Curated adapters (6)

| Provider id | Wiring |
| --- | --- |
| `spartan` | curated tables + a `spartan*` discovery net |
| `iceandfire` | curated family tables + namespace scan |
| `locks` | curated |
| `samurai_dynasty` | curated |
| `more_vanilla` | curated |
| `jewelcraft` | curated |

## Standalone providers (7 registrations)

| Provider | Namespace | Why standalone |
| --- | --- | --- |
| `IronsSpellbooksLockProvider` | `irons_spellbooks` | registry scan; spellbooks resolve through the reviewed table then the chassis metadata profile, other gear (staves/scrolls/armor/rings/orbs) still by keyword. No ISS API imports, so the registry can hold it when ISS is absent |
| `StarcatcherLockProvider` | `starcatcher` | rods and reusable tackle; ordinary bait and catches stay unlocked |
| `OvergearedLockProvider` | `overgeared` | hammers, tongs, blueprints and forged gear; crafting components stay unlocked |
| `RecentEquipmentLockProvider(SIMPLY_SWORDS)` | `simplyswords` | finished weapon classes, including named relics |
| `RecentEquipmentLockProvider(SIMPLY_MORE)` | `simplymore` | finished weapon classes, including named relics |
| `RecentEquipmentLockProvider(TOM)` | `traveloptics` | weapons, armor and complete spellcasting implements |
| `RecentEquipmentLockProvider(TIDE)` | `tide` | native fishing rods; bait, fish and materials stay unlocked |

The recent providers are registered once per `IntegrationModule` and default on. Each honors
its `AutomaticEquipmentGates` flag, integration mode and discovered mod/item opt-outs. Their
reference levels scale to the configured skill cap; see [progression settings](PROGRESSION_GATING.md).

## Iron's spellbook gates (2.2.1)

Baseline artifact: `irons_spellbooks` **1.20.1-3.16.3** (CurseForge file `8680180`). The 3.15
pin is gone; the runtime test profile also pulls `irons_lib 1.20.1-2.1.0` and Curios `5.14.1`,
which 3.16.3 declares mandatory.

A spellbook's **equipment** gate and a spell's **cast** gate are separate rules. Inscribing or
erasing an ordinary spell does not move the book's gate, and an allowed book holding one spell the
player cannot cast still casts everything else in it.

Book gates resolve in this order, first answer winning:

1. **Reviewed table** — `data/runicskills/irons/book_profiles.json`. Ten rows, each checked against
   the 3.16.3 recipes and item registrations, each carrying the evidence it was decided on. The
   audit export prints that evidence as `profile_reason`.
2. **Chassis metadata** — `3 * freeSlots + presetSlots + 2 * modifiers`, halved onto the reviewed
   scale. Read from a detached inspection stack, never from a player's book. Covers every book the
   table leaves out, every addon book, and anything Iron's adds later.
3. **Conservative anchor** — Magic 8, the reviewed copper value, when the chassis cannot be read.
   Published with an `:undetermined` source so the audit reports it as a guess.
4. **Abstain** — a book with no capacity and no attributes gets no rule at all.

Reviewed rows at cap 32 and multiplier 1 (Intelligence trails Magic at 0.6):

| Book | Magic | Why |
| --- | --- | --- |
| `copper_spell_book` | 8 | 5 slots, no modifiers; the anchor |
| `iron_spell_book` | 10 | 6 slots, no modifiers |
| `gold_spell_book` | 12 | 8 slots, +50 mana, +15% cast time — the cast-time cost is why it sits below diamond |
| `diamond_spell_book` | 16 | 10 slots, +100 mana, no cast-time penalty |
| `blaze_spell_book` | 20 | 10 slots, fire power +0.1, +200 mana |
| `druidic_spell_book` | 20 | blaze's chassis, nature school — deliberately equal |
| `villager_spell_book` | 22 | 10 slots, three modifiers, priest trade |
| `netherite_spell_book` | 22 | 12 slots, +20% cooldown reduction, +200 mana |
| `ice_spell_book` | 24 | 12 slots, ice power +0.1, +200 mana; hardest verified acquisition |
| `dragonskin_spell_book` | 24 | ice's chassis, ender school — equal, not the proposed 26 |

Five books the earlier proposal covered are deliberately **not** in the table and use the metadata
profile instead: `wimpy` (no capacity — inert, so no rule), `legendary` (12 slots but no attributes
and no recipe or loot entry; it is unobtainable in survival and stat-inferior), `rotten` (its spell
resistance is an upside, not the drawback the proposal priced, and it is an ingredient of the
druidic book), and `evoker` (7 slots of which only 4 are free). `necronomicon` and `cursed_doll`
were never in the proposal and use the metadata profile too.

## Iron's spell-cast gates (2.2.1)

| Setting | Default | Effect |
| --- | --- | --- |
| `enableSpellLocks` | `true` | Master switch for every requirement on a cast, explicit rules included. Never waives a book's equipment gate. Absent in pre-2.2.1 files, where it defaults on. |
| `ironsSpellGateModel` | `METADATA` | Which generator proposes a requirement. `METADATA` uses the spell's native rarity and its position within its own level range; `LEGACY_LEVEL` uses `ironsBaseSpellGatingLevel + (level - 1) * ironsSpellLevelScaleFactor`. |
| `ironsEnableSchoolGating` | `true` | Whether the selected generator runs at all. Explicit spell rules still apply when it is off. |

The two models are alternatives, never combined: the maximum or sum of two independent estimates
is a requirement neither proposed. `METADATA` anchors Magic at Common 4, Uncommon 8, Rare 14,
Epic 20, Legendary 26 and rises within a band across the levels sharing that rarity, stopping one
short of the next band. A rarity this build cannot map abstains rather than falling through to an
extreme.

Two ordering fixes ship with it. An **explicit rule for a spell now resolves before the generated
formula**, so a pack that deliberately permits a spell is no longer refused by the formula the
permission was written to override. And the gate reads the **selected** spell level rather than the
effective one: Iron's passes `getLevelFor(...)` into the cast, so gear and perk bonuses were
already folded in, and a perk that granted +2 spell levels made previously castable spells refuse.

A denied cast costs nothing. `SpellPreCastEvent` fires before mana is deducted, before a cooldown
starts and before a scroll is consumed, so cancelling there leaves all three — and the book's
contents — untouched.

## Typed gate providers (2.2.1)

A third kind of provider, beside the id-only and stack-aware ones. A `TypedGateProvider` publishes
**action-scoped** rules: `EQUIP`/`USE`/`ATTACK` on an item, `INTERACT_BLOCK` on a block. That is the
only way to gate *operating* a workstation without also forbidding breaking it, which one entry in
the action-blind id table can never express (§12.1). Its rules sit above the keyword generators and
below anything a person authored, and item rules also populate the untyped table so the client
tooltips and synchronisation still see them.

| Provider | Namespace | What it publishes |
| --- | --- | --- |
| `ApprenticeCodexLockProvider` | `apprenticecodex` | 116 typed rules over 186 reviewed entries: spell containers ranked by their real capacity through the Iron's chassis reader, reviewed role anchors for gear, and `INTERACT_BLOCK` gates on seven workstations. Names no Codex or Iron's type, so it loads on a server that has neither. See [`APPRENTICE_CODEX.md`](APPRENTICE_CODEX.md) |

`ApprenticeCodexLockProvider` also claims the `apprenticecodex` namespace through `LockOwnership`
whenever the mod is installed — deliberately regardless of `enableApprenticeCodexIntegration`, so
switching the integration off leaves Codex content ungated instead of handing it to the universal
estimator (§6.3).

## Stack-aware providers

`TConstructBootstrap` registers `TConstructRequirementResolver` when Tinkers is enabled.
It reads actual material tiers for every native modifiable tool, including addon weapons,
armor and Jewelry, regardless of namespace. `enableTConstructLockItems` defaults to `true`;
explicit item-ID and datapack requirements retain their documented precedence. This provider
does not generate static item-ID entries because every material shares the same equipment ID.

## Discovered namespaces (24)

Base level is the gameplay weight of the mod; the multiplier scales all of them.

| Provider id | Namespace(s) | Base |
| --- | --- | --- |
| `epic_knights` | `magistuarmory, magistuarmoryaddon, darkagesarmory, epic_knights__japanese_armory, epic_knights_ice_and_fire, antiquelegacy` | 12 |
| `aquaculture` | `aquaculture` | 10 |
| `call_of_yucutan` | `call_of_yucutan` | 8 |
| `galosphere` | `galosphere` | 8 |
| `undergarden` | `undergarden` | 10 |
| `deeperdarker` | `deeperdarker` | 12 |
| `dragonsteel` | `dragonsteel` | 16 |
| `cataclysm` | `cataclysm` | 20 |
| `mowziesmobs` | `mowziesmobs` | 14 |
| `farmersdelight` | `farmersdelight` | 4 |
| `siegemachines` | `siegemachines` | 12 |
| `fantasy_armor` | `fantasy_armor` | 8 |
| `naturesaura` | `naturesaura` | 8 |
| `bosses_of_mass_destruction` | `bosses_of_mass_destruction` | 18 |
| `jet_and_elias` | `jet_and_elias_armors` | 10 |
| `nichirin_dynasty` | `nichirin_dynasty` | 12 |
| `saintsdragons` | `saintsdragons` | 14 |
| `stalwart_dungeons` | `stalwart_dungeons` | 12 |
| `dungeonsdelight` | `dungeonsdelight` | 6 |
| `fruitsdelight` | `fruitsdelight` | 4 |
| `rusticdelight` | `rusticdelight` | 4 |
| `vintagedelight` | `vintagedelight` | 4 |
| `brewinandchewin` | `brewinandchewin` | 4 |
| `letsdo` | `vinery, bakery, brewery, candlelight, meadow, farm_and_charm, beachparty, herbalbrews` | 5 |

A provider is inactive (no-op, no crash) when its target mod is absent, so providers for
not-installed mods ship safely and activate automatically if the mod is added. **`naturesaura` is
one of these**: the six Nature's Aura *perks* and `NaturesAuraIntegration` were removed in 2.0.0
because that mod is not a dependency at any scope and their behaviour could never have been executed
— but namespace-scanned lock generation needs no API, so the lock provider remains useful and is
kept. Likewise `farmersdelight` keeps its lock provider even though `FarmersDelightIntegration` was
folded into `CulinaryIntegration` in 1.6.0: locking a knife and boosting a meal are separate jobs.

An earlier note here recorded that "Installed in Runecraft" flags for later-alphabet mods were
carried from a prior audit and never re-confirmed. That column has been dropped rather than left
standing as unverified data — which pack happens to ship which mod is not a property of this code,
and a provider costs nothing when its mod is absent.

## Non-lock integrations (perks / events only)

These mods integrate through perk hooks or combat events rather than item locks: Iron's Spells
(school attunement, spell gating, summon hooks, and the Powers dispatcher), Ars Nouveau, Apotheosis /
Apothic Attributes (affix/gem/socket perks, the Apotheosis Wisdom enchantment-cap boost, gem rarity
gating), Cataclysm / Mowzie's (combat-event perks), the Culinary layer (Farmer's Delight and its
addons plus the Let's Do series — namespace-driven food perks), Starcatcher, Overgeared, the gun
mods (TacZ, CGM, Scorched Guns), the tab integrations (L2Tabs, Legendary Tabs) and FTB Quests task
types.

## Guards

- `checkLockProviders` (Gradle, in `check`) — every `integration/*Integration.java` with
  `generateLockItems()` must be referenced by `LockProviderRegistry`.
- `LockProviderRegistryTest` (JUnit) — the same invariant under `test`, plus unique/non-blank ids.
- `LockGenTest` (JUnit) — keyword classification, including the false positives that word-boundary
  matching and the never-gear list exist to prevent.
- `checkSidedImports` (Gradle, in `check`) — a `jp.aquafactory` import outside
  `integration/apprenticecodex/`, `mixin/apprenticecodex/` or `gametest/codex/` fails the build, on
  the same terms as the `slimeknights` guard: an optional mod's class named anywhere the JVM can
  reach without it installed is a `NoClassDefFoundError` waiting for the first pack that does not
  run it.
