# Tinkers' Construct Runtime Compatibility — Test Profiles & Artifact Inventory

Profiles used in 2.0.7/2.1.0 testing, their exact dependencies, and gametest coverage.

Every count in this file was re-measured on 2026-09-06 against release 2.1.0; none is carried over
from 2.0.7. Each run was matched to its profile by the capabilities its `TCONSTRUCT_COMPAT` log line
reported SUPPORTED, so the profile-to-count mapping below is verified rather than assumed. Every
count is a whole-suite total for the profile, not a per-class subtotal.

## Terminology

- **M0 (baseline):** Tinkers' Construct is absent. No Tinkers'-specific code runs.
- **M1 (stable):** Tinkers' Construct 1.20.1-3.11.2.166 + Mantle 1.20.1-1.11.97. Compiled-against versions, all core Tinkers' mixins applied, and the add-on profiles (below) are available.
- **M2 (beta):** Tinkers' Construct 1.20.1-3.12.x + Mantle 1.20.1-1.11.113. Recognized but runs in conservative mode (no core or add-on mixins). Not tested for perks or powers.
- **M3–M7 (add-on profiles):** Built on M1 (stable Tinkers'), each combining one or more add-on jars per spec §17.
- **M8–M9 (2.1.0 add-on profiles):** Also built on M1. M8 is Tinkers' Thinking; M9 is TCIntegrations with its Botania and Ars Nouveau companions.

## M0 Baseline

**Runtime:** Tinkers' Construct absent.

**Gametest count (whole-suite total):** 116

**Run with:**
```bash
./gradlew runGametest
```

**Verified:**
- `TcAddonAbsenceGameTest.addonPerksExistOnlyWithTheirAddon()` — no add-on perk is registered
- `TcAddonAbsenceGameTest.addonCapabilitiesNameWhatIsMissing()` — every add-on capability reports ABSENT
- `TcAddonAbsenceGameTest.medallionConcordIsReservedAndUnregistered()` — tc_medallion_concord is reserved, not registered

## M1 Stable (Core Tinkers')

**Runtime:** Tinkers' Construct 1.20.1-3.11.2.166 + Mantle 1.20.1-1.11.97

**Artifacts:**
- tconstruct: 1.20.1-3.11.2.166 (SHA-256: 653b49d73481a1325ba78adc7273dee6c765a51bafdf264a7510576d6ef91c43)
- mantle: 1.20.1-1.11.97 (SHA-256: 67c79f7cbea8f4d2be8987a7ee83d7a341b841de89c3c09acd2edbf7e539e61e)
- jei: 15.20.0.103 (optional, for recipe list integration)

**Gametest count (whole-suite total):** 202 (up from 200 in 2.0.7: `TcAddonAbsenceGameTest` gained
`subspaceIsReservedAndUnregistered()` and `katanasIsDataOnlyAndNeedsNoAdapter()`, measured 2026-09-06)

**Run with:**
```bash
./gradlew -PtinkersProfile=stable runGametest
```

**Verified:**
- All 16 core Tinkers' perks function correctly
- All 12 Artifice Powers function correctly
- Profile detection and conservative-mode fallback work
- Workmanship stamping and lazy migration
- Wear avoidance, repair routing, and station operations
- All fourteen add-on perk ids (2.0.7 seven + 2.1.0 seven) are registered exactly when their add-on
  is, and every add-on capability reports the right status with none of the add-ons installed

## M3 (TCIntegrations Profile)

**Runtime:** M1 stable + TCIntegrations 2.0.25.19

**Coordinates:**
```gradle
implementation 'maven.modrinth:tcintegrations:1.20.1-2.0.25.19'
```

**Artifacts:**
- tcintegrations: 1.20.1-2.0.25.19

**Gametest count (whole-suite total):** 202, measured 2026-09-06.

This profile gates no additional gametest: the four add-on perks are dormant without their
companion mods, and that dormancy is what `TcAddonAbsenceGameTest` already asserts at the M1
stable baseline. The whole-suite total therefore matches the stable profile exactly, which is
the expected result: a profile whose add-on gates no additional test reports the same total as
plain stable. See M9 below for the counter-intuitive case where adding companions to this same
add-on set still does not move the count.

**Run with:**
```bash
./gradlew -PtinkersProfile=stable -PtinkersAddons=tcintegrations runGametest
```

**Verified:**
- TC_MANA_POLISHER is registered (no companion Botania, so ABSENT at capability check)
- TC_SOURCE_TEMPERING is registered (no companion Ars Nouveau, so ABSENT)
- TC_CLOCKWORK_ALTERNATION is registered (no companion Create, so ABSENT)
- TC_SOULSTEEL_RESOLVE is registered (no companion Malum, so ABSENT)
- Four add-on capabilities gate correctly on missing companions

**Unverified (live behavior):**
- Mana Polisher payout — requires Botania (not in classpath)
- Source Tempering payout — requires Ars Nouveau (not in classpath)
- Clockwork Alternation wear avoidance — requires Create (not in classpath)
- Soulsteel Resolve knockback modifier — requires Malum (not in classpath)

## M4 (Levelling + Delight Profile)

**Runtime:** M1 stable + Tinkers' Levelling Addon 1.4.3 + Tinkers' Delight 2.0.3 + Farmer's Delight 1.3.4

**Coordinates:**
```gradle
implementation 'maven.modrinth:tinkers-levelling-addon:1.4.3'
implementation 'maven.modrinth:tinkers-delight:2.0.3'
implementation 'maven.modrinth:farmers-delight:1.20.1-1.3.4'
```

**Artifacts:**
- tinkerslevellingaddon: 1.4.3
- tinkers_delight: 2.0.3
- farmersdelight: 1.20.1-1.3.4

**Gametest count (whole-suite total):** 209 (M1's 202 + 7), measured 2026-09-06.

**Test methods added by this profile** (`TcAddonPerksGameTest`: 3, `LevellingCoexistenceGameTest`: 4):
- `TcAddonPerksGameTest.seasonedHandsScalesOneAward()`
- `TcAddonPerksGameTest.seasonedHandsIgnoresCommandAndNegativeAwards()`
- `TcAddonPerksGameTest.banquetPaysOnlyWhileNourished()`
- `LevellingCoexistenceGameTest.toolExperienceIsNotPlayerExperience()`
- `LevellingCoexistenceGameTest.theCarryDoesNotTravel()`
- `LevellingCoexistenceGameTest.nothingIsGrantedPastTheAddonsCap()`
- `LevellingCoexistenceGameTest.theOnlyDifferenceIsTheIntendedOne()`

**Run with:**
```bash
./gradlew -PtinkersProfile=stable -PtinkersAddons=levelling,delight runGametest
```

**Verified:**
- TC_SEASONED_HANDS is registered and scales tool experience correctly
- TC_BANQUET_OF_CINDERS is registered and pays melee bonus only with Nourishment active
- Player progression and tool progression remain separate
- Fractional carry does not travel between players or tools
- Awards past the add-on cap receive no multiplier
- Perk off/on produces exactly the intended difference

## M5 (Advanced Profile)

**Runtime:** M1 stable + Tinkers' Advanced Core 3.0.0-beta.5 + EtSTLib 3.0.0-beta.20

**Coordinates:**
```gradle
implementation 'maven.modrinth:etstlib:3.0.0-beta.20'
implementation 'maven.modrinth:tinkers-advanced-core:3.0.0-beta.5'
```

(build.gradle declares EtSTLib explicitly in the `advanced` add-on catalogue entry; it is not pulled
in transitively. There is no `tinkers-advanced` (Tools) or `tinkers-advanced-materials` coordinate in
build.gradle — the Tools main project is the one that cannot boot a dedicated server, so this profile
uses only the split Core publication.)

**Artifacts:**
- etstlib: 3.0.0-beta.20
- tinkers-advanced-core: 3.0.0-beta.5

**Gametest count (whole-suite total):** 203 (M1's 202 + 1), measured 2026-09-06.

**Test methods added by this profile** (`TcChargedCraftGameTest`: 1):
- `TcChargedCraftGameTest.chargedCraftDiscountsWithAPositiveMinimum()`

**Run with:**
```bash
./gradlew -PtinkersProfile=stable -PtinkersAddons=advanced runGametest
```

**Verified:**
- TC_CHARGED_CRAFT is registered and discounts FE cost correctly
- Positive cost never falls below one FE
- Unattributed extraction is never discounted

**Known upstream issue:**
- Tinkers' Advanced main project (Tools) 3.0.0-beta.13 cannot boot a dedicated server: it declares a client-only EventBusSubscriber without the `CLIENT` dist gate. The split Core beta.5 publication is used instead for safe test execution. A live multiplay server using this profile will fail to load.

## M6 (Innovation Profile)

**Runtime:** M1 stable + Tinkersinnovation 1.20.1-3.0.0

**Coordinates:**
```gradle
implementation 'maven.modrinth:tinkersinnovation:1.20.1-3.0.0'
```

**Artifacts:**
- tinkersinnovation: 1.20.1-3.0.0

**Gametest count (whole-suite total):** 202, measured 2026-09-06.

This profile gates no perk; it exists as overlap-coverage only (Section 12.8 target). It adds no
gametest of its own, so the whole-suite total matches the stable profile exactly, which is the
expected result.

**Run with:**
```bash
./gradlew -PtinkersProfile=stable -PtinkersAddons=innovation runGametest
```

## M7 (All Add-ons Combined)

**Runtime:** M1 stable + TCIntegrations + Tinkers' Levelling Addon + Tinkers' Delight + Farmer's Delight + Tinkers' Advanced Core + EtSTLib + Tinkersinnovation.

**Gametest count (whole-suite total):** 210 (M1's 202 + the levelling+delight profile's +7 + the
advanced profile's +1: 202 + 7 + 1 = 210 — the highest total of any profile in this file, and a
figure a reader can check by hand from the two rows above), measured 2026-09-06.

**Run with:**
```bash
./gradlew -PtinkersProfile=stable -PtinkersAddons=tcintegrations,levelling,delight,advanced,innovation runGametest
```

**Verified:**
- All seven add-on perks coexist and gate correctly on their dependencies
- No cross-perk interference
- Capability diagnostics report exactly which add-ons are present and which are absent

## M8 (Tinkers' Thinking Profile, 2.1.0)

**Runtime:** M1 stable + Tinkers' Thinking 0.1.6.6.3 — the exact build the reference pack runs.

**Coordinates:**
```gradle
runtimeOnly 'maven.modrinth:tinkers-thinking:0.1.6.6.3'
```

**Gametest count (whole-suite total):** 205 (M1's 202 + the three `TinkersThinkingPerksGameTest`
methods), measured 2026-09-06.

**Test methods added by this profile** (`TinkersThinkingPerksGameTest`: 3):
- `TinkersThinkingPerksGameTest.lastThoughtReducesDeathWear()`
- `TinkersThinkingPerksGameTest.studiedRecallAddsXpShare()`
- `TinkersThinkingPerksGameTest.embellishedFocusAddsMeleeShare()`

**Run with:**
```bash
./gradlew -PtinkersProfile=stable -PtinkersAddons=thinking runGametest
```

**Verified:**
- Last Thought arms only on a death the add-on's own save refused, not on an ordinary death or a
  bare `last_effort` effect, and pays into the add-on wear-avoidance channel
- Studied Recall pays only while `sculk_power` is present, and never above a full share of the orb
- Embellished Focus reads the modifier off the weapon by registry id; a plain tool earns nothing

## M9 (TCIntegrations + Botania + Ars Nouveau Profile, 2.1.0)

**Runtime:** M1 stable + TCIntegrations 2.0.25.19 + Botania 1.20.1-455-forge (with Patchouli) + Ars
Nouveau 4.12.7 (with Curios, Patchouli, GeckoLib) — the versions the reference pack ships.

**Gametest count (whole-suite total):** 202 (matches M1 exactly), measured 2026-09-06.

This profile registers no gametest class of its own: `ADDON_BOTANIA_REPAIR_CHARGE` and
`ADDON_ARS_ARMOR_REPAIR` are exercised through the same `TcAddonAbsenceGameTest` methods that already
run in every profile, which now observe `SUPPORTED` for those two capabilities instead of `ABSENT`
because their companion mods are present. Mana Polisher's and Source Tempering's actual payout is
still not driven by a dedicated gametest in this profile; the companion presence assertion is what is
new here. This is the second, more counter-intuitive way a profile can hold at 202: unlike M3 and
M6 (which hold at 202 because nothing new is exercised), M9 holds at 202 because the *same* test
methods now exercise real behavior instead of asserting dormancy — the count does not move, but
what it proves does.

**Run with:**
```bash
./gradlew -PtinkersProfile=stable -PtinkersAddons=tcintegrations,botania,ars runGametest
```

## Tinkers' Jewelry has no live gametest profile (2.1.0)

Modrinth's newest 1.20.1 Forge build of Tinkers' Jewelry is 1.1.0; the reference pack this release
was read against runs 1.2.0, published on CurseForge only. Booting a profile against 1.1.0 would
produce a green run about a jar nobody is running, so `build.gradle`'s add-on catalogue has no
`jewelry` slug. `TinkersJewelryPerksGameTest` still registers whenever `tinkersjewelry` is loaded, so
a pack developer who supplies their own 1.2.0 build gets the coverage; in every profile this
repository can boot, the four Tinkers' Jewelry perks rest on `TcAddonAbsenceGameTest`'s dormancy
invariants instead.

## Unsupported Profiles (Descoped for 2.0.7)

The following were evaluated but not included in test coverage due to unavailable artifacts or upstream defects:

### Construct's Arsenal + Json Things

**Status:** No public 1.20.1 Forge artifacts found on Modrinth or accessible via CurseForge (network blocker in sandbox).

**Scope:** Low-code tools/armor coverage (M7 test target only, not a Sec 10.3 perk). Descoping removes M7 coverage for this project specifically.

**Resolution:** Re-check with authenticated CurseForge API before permanently descoping.

### Tinkers' Ingenuity

**Status:** No public 1.20.1 Forge artifact on Modrinth (CurseForge blocked).

**Scope:** tc_medallion_concord (Sec 10.3 perk, Wisdom 16). Descoping removes live behavior; perk id is reserved and marked UPSTREAM_UNAVAILABLE per spec §10.3.

**Resolution:** Re-check with authenticated CurseForge API before permanently descoping.

### Tinkers' Advanced Tools (beta.13) in Dedicated-Server Testing

**Status:** Client-side EventBusSubscriber without dist gate prevents dedicated-server boot.

**Scope:** tc_charged_craft testability. Mitigation: use split Core beta.5 for test environment, document the upstream issue.

**Impact:** M5 boots correctly in test server; live servers using this profile will crash on startup.

### L2 Complements

**Status:** Requires L2 Library ≥ 2.5.0; project pins 2.4.28.

**Scope:** Sec 12.8 Innovation overlap test target (M7 test coverage). Descoping does not affect any perk.

**Resolution:** Update L2 Library pin or defer until 2.0.8.

### Tinkers' Things

**Status:** Available but requires unpublished jsonthings dependency.

**Scope:** Sec 12.4 low-code tools/armor coverage (M7 test target only). Descoping removes M7 coverage.

**Resolution:** Wait for jsonthings publication.

## Running Tests Locally

All profiles use the same gametest template (`empty.snbt`):

```bash
# Clean build with all profiles
./gradlew clean -PtinkersProfile=stable -PtinkersAddons=tcintegrations,levelling,delight,advanced,innovation runGametest

# Individual profiles
./gradlew -PtinkersProfile=stable runGametest  # M1 only
./gradlew -PtinkersProfile=stable -PtinkersAddons=levelling,delight runGametest  # M4
./gradlew -PtinkersProfile=stable -PtinkersAddons=advanced runGametest  # M5
./gradlew -PtinkersProfile=stable -PtinkersAddons=innovation runGametest  # M6
```

No manual profile selection is required for M0 (absent) or M2 (beta); they are auto-detected at runtime.

## Gametest Organization

Tests are registered conditionally in `TConstructGameTests`:
- `TcAddonAbsenceGameTest` — registered when Tinkers' is present (all profiles); gained two methods
  in 2.1.0 (`subspaceIsReservedAndUnregistered()`, `katanasIsDataOnlyAndNeedsNoAdapter()`)
- `TcAddonPerksGameTest` — registered only when add-ons that bootable profiles require are loaded
- `LevellingCoexistenceGameTest` — registered only when Tinkers' Levelling is loaded
- `TcChargedCraftGameTest` — registered only when Tinkers' Advanced Core and EtSTLib are loaded
- `TinkersThinkingPerksGameTest` (2.1.0) — registered only when `tinkers_thinking` is loaded
- `TinkersJewelryPerksGameTest` (2.1.0) — registered only when `tinkersjewelry` is loaded

This keeps the test suite runnable on every profile without skipping unavailable tests.
