# Integration & Lock-Provider Matrix (1.5.0)

> **Audit provenance (1.5.0):** the lock-provider namespaces were re-verified against the
> Runecraft modpack — both the `20260615` packmaster item-registry dump and the `assets/<namespace>/`
> folders of the actual mod jars. Confirmed correction: Epic Knights **Antique Legacy** registers items
> under `antiquelegacy` (not `epic_knights_antique_legacy`), and **Call of the Yucatan**
> (`call_of_yucutan`) gear was previously uncovered — both are now wired. The item-registry dump was
> complete only through namespaces ≤ `e`, so "Installed in Runecraft" flags for later-alphabet mods
> are carried from the prior audit and not re-confirmed this pass.

Source of truth for which mods get item-lock generation and how it is wired. The
[`checkLockProviders`](../build.gradle) Gradle task and
[`LockProviderRegistryTest`](../src/test/java/com/otectus/runicskills/integration/lock/LockProviderRegistryTest.java)
fail the build if an integration class exposing `generateLockItems()` is not registered, so this table
cannot silently drift from the code.

## How lock generation is wired

All lock providers are registered in
[`LockProviderRegistry`](../src/main/java/com/otectus/runicskills/integration/lock/LockProviderRegistry.java)
and iterated by `HandlerSkill.injectIntegrationItems()`. Manual config locks still win via `putIfAbsent`.

- **Curated adapters** wrap the existing static `generateLockItems()` on a Forge-only integration class
  (hand-tuned material/weapon tables).
- **`IronsSpellbooksLockProvider`** is a standalone Forge-only provider (no ISS API imports) — the ISS
  event-handler class hard-references the ISS API and must not be eagerly resolved from the registry.
- **`GenericNamespaceLockProvider`** scans a mod's item namespace and classifies gear by keyword via
  [`LockGen`](../src/main/java/com/otectus/runicskills/integration/lock/LockGen.java). Opt a mod out with
  `disabledDiscoveredLockMods`; scale all discovered locks with `discoveredLockLevelMultiplier`.

## Lock providers

| Provider id | Namespace(s) | Wiring | Installed in Runecraft | Base/approach |
| --- | --- | --- | --- | --- |
| spartan | spartanweaponry/shields/cataclysm/fire (+ any `spartan*`) | curated + discovery net | ✅ | material tables + `spartan*` scan |
| iceandfire | iceandfire | curated + discovery net | ✅ | family tables + namespace scan |
| irons_spellbooks | irons_spellbooks | standalone provider | ✅ | registry scan (books/staves/scrolls/armor/rings/orbs) |
| locks | locks | curated adapter | ❌ | curated |
| samurai_dynasty | samurai_dynasty | curated adapter | ❌ | curated |
| more_vanilla | morevanilla* | curated adapter | ❌ | curated |
| jewelcraft | jewelcraft | curated adapter | ❌ | curated |
| epic_knights | magistuarmory, magistuarmoryaddon, darkagesarmory, epic_knights__japanese_armory, epic_knights_ice_and_fire, antiquelegacy | discovered | ✅ | base 12 |
| aquaculture | aquaculture | discovered | ✅ | base 10 |
| call_of_yucutan | call_of_yucutan | discovered | ✅ | base 8 |
| galosphere | galosphere | discovered | ✅ | base 8 |
| undergarden | undergarden | discovered | ✅ | base 10 |
| deeperdarker | deeperdarker | discovered | ✅ | base 12 |
| dragonsteel | dragonsteel | discovered | ✅ | base 16 |
| cataclysm | cataclysm | discovered | ✅ (L_Ender's) | base 20 |
| mowziesmobs | mowziesmobs | discovered | ✅ | base 14 |
| farmersdelight | farmersdelight | discovered | ✅ | base 4 |
| siegemachines | siegemachines | discovered | ✅ | base 12 |
| fantasy_armor | fantasy_armor | discovered | ❌ | base 8 |
| naturesaura | naturesaura | discovered | ❌ | base 8 |
| bosses_of_mass_destruction | bosses_of_mass_destruction | discovered | ✅ | base 18 |
| jet_and_elias | jet_and_elias_armors | discovered | ❌ | base 10 |
| nichirin_dynasty | nichirin_dynasty | discovered | ❌ | base 12 |
| saintsdragons | saintsdragons | discovered | ❌ | base 14 |
| stalwart_dungeons | stalwart_dungeons | discovered | ❌ | base 12 |

A provider is inactive (no-op, no crash) when its target mod is absent, so providers for
not-installed mods ship safely and activate automatically if the mod is added.

## Non-lock integrations (perks / events only)

These mods integrate through perk hooks or combat events rather than item locks:
Iron's Spells (school attunement, spell gating, summon hooks), Ars Nouveau, Apotheosis / Apothic
Attributes (affix/gem/socket perks, Apotheosis Wisdom enchantment-cap boost, gem rarity gating),
Cataclysm/Mowzie's/Farmers Delight (combat-event perks), the gun mods (TacZ, CGM,
Scorched Guns), and the tab integrations (L2Tabs, Legendary Tabs) + FTB Quests task types.

## Guards

- `checkLockProviders` (Gradle, in `check`) — every `integration/*Integration.java` with
  `generateLockItems()` must be referenced by `LockProviderRegistry`.
- `LockProviderRegistryTest` (JUnit) — same invariant under `test`, plus unique/non-blank provider ids.
