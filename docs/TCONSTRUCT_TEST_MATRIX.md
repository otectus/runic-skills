# Tinkers' Construct test profiles (2.1.0)

This file describes executable runtime profiles and the limits of their coverage. The previous counts (M0 116, M1 202, combined add-ons 210) were measured before the stabilization regressions and are historical, not current release totals. Current run evidence is recorded in the [stabilization verification summary](STABILIZATION_2.1.0.md) and the Gradle logs under `build/`.

## Runtime profiles

The compile dependency remains Tinkers' Construct 1.20.1-3.11.2.166 and Mantle 1.20.1-1.11.97, preserving the minimum supported API. Runtime jars are selected explicitly by Gradle properties; the mod then detects their actual versions at startup. Any profile selecting the `jewelry` add-on uses Mantle 1.20.1-1.11.113; the stable profile without Jewelry retains Mantle 1.11.97.

| Profile | Selection | Runtime and assertions |
| --- | --- | --- |
| M0 | No Tinkers property | Tinkers absent. General capability, config, networking and dormant optional-content coverage; no native test classes load. |
| M1 | `-PtinkersProfile=stable` | Tinkers 3.11.2.166, Mantle 1.11.97, JEI 15.20.0.103. Full core native station, wear, repair, equipment, projectile, workshop, perk and Artifice suite. |
| M2 | `-PtinkersProfile=beta` | Tinkers 3.12.0.220, Mantle 1.11.113. Native API repair/classification, configured stack rules, add-on absence and explicit conservative capability/Power unavailability. Stable-only bytecode behavior is intentionally unavailable. |
| M3 | M1 + `-PtinkersAddons=tcintegrations` | TCIntegrations 2.0.25.19 with companion-dependent capabilities dormant when their companion is absent. |
| M4 | M1 + `-PtinkersAddons=levelling,delight` | Levelling 1.4.3, Tinkers' Delight 2.0.3, Farmer's Delight 1.3.4. Positive/capped/unknown awards, separate player/tool progression, rounding carry and culinary conditions. |
| M5 | M1 + `-PtinkersAddons=advanced` | Advanced Core 3.0.0-beta.5 and EtSTLib 3.0.0-beta.20. Actual incoming FE-cost seam and bounded discount. |
| M6 | M1 + `-PtinkersAddons=innovation` | Tinkersinnovation 1.20.1-3.0.0. Core-suite coexistence; no Runic perk is specifically attached to this add-on. |
| M7 | M1 + `-PtinkersAddons=tcintegrations,levelling,delight,advanced,innovation` | Combined optional adapters and native core suite. |
| M8 | M1 + `-PtinkersAddons=thinking` | Thinking 0.1.6.6.3. Death-save, consumed XP conversion and embellishment coverage. |
| M9 | M1 + `-PtinkersAddons=tcintegrations,botania,ars` | TCIntegrations with Botania 1.20.1-455-forge and Ars Nouveau 4.12.7, plus Patchouli, Curios and the pinned Forge GeckoLib artifact. API/seam availability and coexistence; additional tests are not implied by presence alone. |
| M10 | M1 + `-PtinkersAddons=jewelry` | Tinkers 3.11.2.166 with Mantle 1.11.113, exact Tinkers' Jewelry 1.2.0, Apothic Attributes 1.3.7 and Placebo 8.6.3, using the existing Curios 5.9.1 runtime. Presence-gated Jewelry perk tests and native core coexistence. |

Stable Tinkers artifact SHA-256: `653b49d73481a1325ba78adc7273dee6c765a51bafdf264a7510576d6ef91c43`.
Stable Mantle artifact SHA-256: `67c79f7cbea8f4d2be8987a7ee83d7a341b841de89c3c09acd2edbf7e539e61e`.
`build.gradle` is authoritative for every version and dependency coordinate.

Curios compilation always uses the 5.9.1 public API. Runtime profiles use Curios 5.9.1 unless
the `ars` companion selects 5.14.1. GeckoLib uses the base 4.4.7 runtime unless `ars` or
`-PironsProfile=true` selects the exact Forge 1.20.1 4.8.4 artifact. Combining both companions
still supplies one Curios jar and one GeckoLib jar, including when Jewelry is also selected.

The publisher's [Jewelry 1.2.0 file page](https://www.curseforge.com/minecraft/mc-mods/tinkers-jewelry/files/6328912)
confirms Forge 1.20.1 and the immutable coordinate `curse.maven:tinkers-jewelry-1191965:6328912`.
The exact jar's metadata requires Forge 47.2.6+, TConstruct 3.9.1+, Curios 5.9.1+ and
Apothic Attributes 1.3.5+. The selected Apothic artifact requires Placebo 8.6.0+; all pins meet those
bounds. Curios is reused from the existing runtime, while Apothic/Placebo reuse their compile
coordinates to avoid duplicate mod jars. When updating the Jewelry pin, inspect the actual jar's
`META-INF/mods.toml` and nested `META-INF/jarjar/metadata.json` again, then rerun M10.

| Exact M10 artifact | SHA-256 |
| --- | --- |
| Mantle 1.20.1-1.11.113 | `c059d5dcaca940383d9c4ff31bc79473b30ac267ccc5922d0d6b0fe7d93c6f7c` |
| Jewelry 1.2.0, CurseForge file 6328912 | `f80f5f426ab6362f28b72a31b6ea903942a74f4882cdff5a955a825f3d76171c` |
| Apothic Attributes 1.3.7, file 5634071 | `68487b11c0d4e2f67a85f2b04bf65e99ac15294f2923f4391f83e9542293187a` |
| Placebo 8.6.3, file 6274231 | `1cdf906cfbcbb5e5be2ef1bb721f79a24b01bd2ac559c8cdbfb7693f62421571` |

Jewelry profiles recommend Mantle 1.11.113 because parallel mod construction can deadlock on
Mantle 1.11.97. The captured thread dump, `build/reports/jewelry-startup-threads.txt`, shows
TConstruct's constructor waiting for `Loadables` from `ResourceLocationLoadable.<clinit>` while
Jewelry's constructor waits for `ResourceLocationLoadable` from `Loadables.<clinit>`. Neither
blocked stack contains Runic code. Inspection of the exact Mantle 1.11.113 bytecode confirms
that its `ResourceLocationLoadable.DEFAULT` no longer reads `Loadables` during static
initialization, removing this cycle. The `mantle_jewelry_version` property pins the newer
runtime for both Jewelry alone and combined add-on profiles; compilation retains the 1.11.97 API.
Executed results, including historical runs on the older runtime, belong in the
[verification summary](STABILIZATION_2.1.0.md).

For an existing pack that must keep Mantle 1.11.97, setting `maxThreads = 1` in Forge's
`config/fml.toml` before restarting serializes mod loading and avoids the observed parallel
initialization cycle. This is a pack workaround, not a Runic config option; upgrading Mantle
is preferred. A JVM `-Dfml.maxThreads` argument does not configure this Forge setting.

## Run commands

Run profiles serially: they share the GameTest server working directory.

```bash
./gradlew build
./gradlew runGameTestServer
./gradlew -PtinkersProfile=stable runGameTestServer
./gradlew -PtinkersProfile=beta runGameTestServer
./gradlew -PtinkersProfile=stable -PtinkersAddons=levelling,delight,thinking,innovation runGameTestServer
./gradlew -PtinkersProfile=stable -PtinkersAddons=advanced runGameTestServer
./gradlew -PtinkersProfile=stable -PtinkersAddons=jewelry runGameTestServer
./gradlew -PtinkersProfile=stable -PtinkersAddons=tcintegrations,botania,ars runGameTestServer
```

`runGametest` is not a task. A beta run requires `-PtinkersProfile=beta`; runtime detection does not install its jars.

## Stabilization regressions

- Pure Java tests cover native repair scaling before clamping, invalid factors, bounded paid-repair arithmetic, attack readiness scope nesting/cleanup, selected-slot mining-cache isolation, and detection of merged but uncalled optional injector methods.
- Native station tests cover ordinary/shift takes, per-player shared-preview isolation, locked/refused results, one reward, no repair duplication, full inventories, real delivered-stack binding, script-denied native-only repair, temporary repair quote refresh, and scope cleanup after a throwing callback.
- Core perk tests cover the final Repair Memory charge across multiple children of one action, Adaptive Grip query purity versus committed consumption, actual vanilla attack readiness capture, and broken weapon/armor exclusion.
- Artifice tests cover each of the twelve Powers, substantial native restoration thresholds, identity/part-change matching, cooldown debt, launch snapshots, focus/owner attribution, and an allied native station contribution to Many Hands.
- Levelling coverage includes saturation of a large positive award without negative overflow or an unbounded remainder.
- General regressions run alongside the integration suite to catch cross-integration changes in skill gates, persistence, cooldowns, crafting and event ordering.
- Jewelry coverage includes a real Curios ring slot, held/inventory exclusion and the native save-cost durability seam without a normal tool-action frame. The accompanying Apothic runtime checks delegation toggles on existing players, attribute-provider refresh and removal of the inactive provider's old bonus.

GameTests mix actual native menu/repair/attack paths with controlled Forge event and dispatcher inputs. A green suite is evidence for these asserted behaviors, not proof of every third-party pack combination or interactive client rendering.

## Conditional registration

`TConstructGameTests` loads native test classes by name only after Tinkers is present. None carries an unconditional `@GameTestHolder`.

Every installed Tinkers profile runs `NativeRepairGameTest`, `StackRequirementGameTest`, `CompatibilityStatusGameTest` and `TcAddonAbsenceGameTest`. Stable profiles additionally load all core seam tests and the add-on classes whose required mod IDs are present. Beta tests assert conservative behavior instead of attempting to equip Powers whose required hooks are intentionally unavailable.

`TcAddonPerksGameTest` requires Levelling, Tinkers' Delight and Farmer's Delight together. `LevellingCoexistenceGameTest`, `TcChargedCraftGameTest`, `TinkersThinkingPerksGameTest` and `TinkersJewelryPerksGameTest` have their own presence gates. Add-on absence tests do not run in M0; M0's ordinary optional-content tests cover the absent integration boundary.

## Pack validation limits

- **Tinkers' Jewelry 1.2.0:** M10 now selects the publisher's exact artifact and registers Jewelry tests. The verification report records executed results. Ring rendering, client interaction and additional pack combinations still require interactive acceptance checks.
- **Advanced monolith 3.0.0-beta.13:** declares a client event subscriber on dedicated servers and fails before Runic gameplay. Use the tested Core profile; this integration does not patch another mod's boot failure.
- **L2 Complements:** the existing L2 Library pin and the add-on's required version differ; there is no profile claiming this overlap is validated.
- **JsonThings/Tinkers' Things/Katanas:** the available Katanas content is data-driven and uses native tags. No executable low-code runtime combination is selected by these profiles; registration/tag review is distinct from running the real content pack.
- **Create/Malum companion paths:** registry/API checks and dormant paths are covered by available profiles. A live companion runtime is required to validate offhand alternation and Soulsteel effects end to end.
- **Multiplayer/client:** native GameTests use separate server players and real menu delivery. Protocol bounds, state clearing and server authority have automated coverage; visual mining prediction, focus UI, GUI scale, mouse/keyboard narration and disconnect/reconnect need client acceptance testing.

`checkMixinRemapping` verifies production member naming independently of Mojang-mapped GameTests. The runtime ledger probes approved core/add-on targets and verifies actual handler call sites, so a `require=0` injector that silently misses is reported unavailable. `tools/verify_against_pack.py` checks the built jar against supplied obfuscated pack artifacts; see [pack verification](PACK_VERIFICATION.md).
