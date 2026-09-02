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

## Standalone providers (3)

| Provider | Namespace | Why standalone |
| --- | --- | --- |
| `IronsSpellbooksLockProvider` | `irons_spellbooks` | registry scan (books/staves/scrolls/armor/rings/orbs); no ISS API imports, so the registry can hold it when ISS is absent |
| `StarcatcherLockProvider` | `starcatcher` | rods and reusable tackle; ordinary bait and catches stay unlocked |
| `OvergearedLockProvider` | `overgeared` | hammers, tongs, blueprints and forged gear; crafting components stay unlocked |

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
