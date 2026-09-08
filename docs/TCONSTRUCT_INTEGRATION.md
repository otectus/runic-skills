# Tinkers' Construct integration (2.1.0)

Runic Skills supports Tinkers' Construct 1.20.1-3.11.2.166 with Mantle 1.20.1-1.11.97 as its minimum API. The `stable` Gradle profile boots those versions unless the `jewelry` add-on is selected, which uses the recommended Mantle 1.20.1-1.11.113 runtime. The `beta` profile uses Tinkers 3.12.0.220 and Mantle 1.11.113 in conservative mode. Other versions are unverified. Consult `/skills tinkers compat` for the capabilities and hooks actually available on your installation.

## Optional dependencies and profiles

The bootstrap loads reflectively only when `tconstruct` is installed. Optional add-on adapters have their own presence and configuration gates. Tinkers-targeting mixins apply only to the stable 3.11 profile with the integration enabled; their optional injection counts and production remapping are checked by Gradle. Startup diagnostics verify a target call for every injector rather than treating a successful mixin merge as evidence that its optional hooks matched.

Conservative mode keeps native equipment classification, native repair routing and configured stack rules available through the stable API. It disables mixin-dependent capabilities and leaves corresponding perks and Powers dormant. Saved content IDs remain resolvable; unavailable content cannot be equipped or charged against the active perk budget. Classification/repair API availability does not mean every Tinkers perk is enabled on an unverified version.

`runicskills:workmanship`, `runicskills:keystone` and the keystone recipe serializer remain registered whenever Tinkers is installed, including when the master integration switch is off. This preserves saved modifier identities and previously paid upgrades. The switch prevents new integration effects and services; it does not remove permanent upgrades already stored on a tool.

## Configuration

Tinkers options are in the server-authoritative `runicskills-common.json5`. Gameplay values are synchronized to clients.

| Option | Default | Meaning |
| --- | --- | --- |
| `enableTConstructIntegration` | true | Master integration switch; restart required for adapter/mixin installation. |
| `enableTConstructPerks` | true | Enable core and add-on Tinkers perks. |
| `enableTConstructPowers` | true | Enable Artifice Powers, checked on every proc. |
| `enableTConstructLockItems` | false | Opt into generated material-tier requirements; explicit pack rules still work when off. |
| `tconstructCompatibilityDiagnostics` | true | Print startup capability and machine-readable hook summaries. Commands remain available when logging is off. |
| `tconstructRepairBonusCap` | 0.50 | Maximum extra paid-repair restoration as a share of native restoration. |
| `tconstructMaterialCostDiscountMode` | false | Reserved compatibility field; no material-discount implementation. |

Each add-on also has an `enable…Integration` switch. Installation switches require a restart when they control an adapter or mixin. The main damage, mining, action-speed, incoming-reduction and workshop caps apply to the combined contributions, not separately to each perk.

## Equipment and permanent modifiers

Equipment uses Tinkers' published tags, not Java item classes or registry-name guesses. `MODIFIABLE` identifies finished equipment. `DURABILITY`, `HARVEST`, `MELEE_WEAPON`, `RANGED`, `ARMOR` and `SHIELDS` determine Runic roles. These are Java tag constants; actual resource paths use the native `tconstruct:modifiable/...` hierarchy. Parts, casts and materials are not finished tools. Native durability, damage and broken/unbreakable state are read from a fresh `ToolStack`.

Tinker's Touch, Tool Smith and Weapon Smith use namespaced native persistent data. The workmanship modifier adds the durability percentage during native stat rebuilding; existing Runic mining and attack consumers read the other stamps once. Legacy root stamps migrate lazily, retain the larger percentage, preserve unrelated NBT and never accumulate across rebuilds.

Keystone Tinker fits one permanent upgrade slot using the native station recipe and its configured material cost. Eligibility is checked for the taker before consumption; the shared station preview does not authorize another player. `runicskills:keystone_eligible` delegates to `#tconstruct:modifiable/bonus_slots` and remains datapack-extensible. A second keystone is refused. The modifier's slot persists through rebuilds and service disable.

## Durability and repair

Ordinary native wear joins the Runic avoidance sum after native modifier processing, before `directDamage`. Attribution must identify the actual player action. Unknown costs retain native behavior. Avoidance is capped at 90%; native overslime, unbreakability and modifier costs retain their own semantics. Repair Memory spends one charge per root action, including every AoE child of its final charge.

Runic-origin repair offers are scaled by the tool's native repair factor, then bounded by remaining damage. A non-finite, zero or negative factor refuses repair. A half-rate repair offered ten points can finish a tool missing four points. The API reports the committed restoration.

A paid station/repair-kit bonus is calculated from restoration already committed by the native recipe. Its native factor has already been applied and is not multiplied a second time. This keeps both reduced and enhanced repair modifiers inside the shared bonus cap and prevents restoring past full durability.

Station previews only calculate output. They do not grant charges, spend cooldowns or increment sequences. Those commit at the accepted native take, using the original native restoration for thresholds and script reports. A full-inventory shift-click grants nothing. On successful shift-click, temporary tool benefits bind to the real new inventory stack, not Mantle's intermediate copy or another identical tool. Per-click scratch data clears on return or exception and is never written to saves.

Private station quotes include repair bonuses and refresh when temporary benefits change with unchanged station inputs. A script-denied Runic operation delivers the native result without its previewed Runic bonus; it spends no Power preparation. Nested or throwing station callbacks cannot strand action or container scopes.

## Core perks

| Perk | Mechanic and boundary |
| --- | --- |
| Repair Memory | A substantial paid station repair arms tool-bound ordinary-use avoidance charges. One charge covers one root action. |
| Slime Steward | Avoidance only on a tool with an overslime modifier whose shield is depleted; never refills it. |
| Tempered Edge | Bonus on a ready primary native melee hit, subject to cooldown and shared damage cap. Readiness is captured before vanilla resets its attack ticker. |
| Precision Footing | Native effective mining while grounded and not sprinting. |
| Adaptive Grip | Alternating melee and mining with the same hybrid tool prepares a bonus. Repeated speed queries do not spend it; a committed action does. |
| Counterweight | Transient attack speed while holding a usable native broad melee tool. |
| Plate Discipline | One knockback-resistance modifier for at least three unbroken native armor pieces in armor slots. |
| Ember Guard | Fire reduction while focused on a genuinely heated workshop. |
| Material Harmony | Paid station repair bonus for at least three distinct functional materials; cosmetic variants count once. |
| Field Service | Bonus restoration through the native crafting-table repair-kit recipe. |
| Thermal Rhythm | Melting progress within the shared workshop cap. |
| Workshop Cadence | A same-recipe casting streak prepares cooling progress; bounded window and cooldown. |
| Cast Keeper | Chance to return one consumed cast; no additional output or fluid. |
| Measured Draw | Scales fully charged native launch inaccuracy; zero remains zero. |
| Returning Hand | One faster thrown-tool charge after a genuine native return, never above full charge. |
| Keystone Tinker | Validated station service for one permanent upgrade slot. |

Temporary state clears on logout, clone/death, dimension change, respec and server shutdown. Add-on state clears even on installations without TCIntegrations. Runtime attribute reconciliation removes obsolete modifiers.

## Artifice Powers

| Tier | Powers |
| --- | --- |
| Mark | First Heat, Plumb Line, Quench, Working Memory |
| Seal | Hammer and Tongs, Temper Reserve, Resonant Return, Workshop Aegis |
| Crown | The Great Work, Last Temper, Heart of the Foundry, Many Hands, One Forge |

The last entry is one Power named “Many Hands, One Forge,” so there are twelve Artifice Powers. `TConstructPowers` defines their magnitudes, thresholds and windows; the same validated override values feed descriptions and execution. Ordinary Power eligibility, prerequisites, slots and server cooldown debt apply.

First Heat counts ready accepted primary hits. Plumb Line counts roots rather than AoE children. Quench and Temper Reserve require substantial native restoration; Temper Reserve belongs to the actual repaired stack. Working Memory matches the changed tool's native data at the original station, ignoring damage alone, so another pickaxe of the same item ID cannot spend it. Resonant Return snapshots one prepared launch onto its projectile. Last Temper preserves one durability only when its clamp actually prevents a break.

Workshop sequences require attributed native operations. Many Hands groups casting associations and nearby native stations by the focused controller; a real allied station assembly can complete the cooperative award. Expired contributors are removed before the four-player capacity check and ally/dimension membership is rechecked at payout. Heart of the Foundry tracks at most ten personally inserted melting slots; automation creates no insertion credit.

Temporary mining bonuses (Adaptive Grip, Plumb Line, Inspired) are sent only when they change, with their hotbar slot, to keep client break prediction aligned with server execution. Precision Footing's movement/effectiveness condition is evaluated on both sides. Client state clears on disconnect. The new packet raises the channel protocol to **13**, requiring matching client/server releases without changing save or config schemas.

## Add-on integrations

TCIntegrations capabilities distinguish companion Botania, Ars Nouveau, Create and Malum presence. Native mana/energy/XP costs and charges retain their own attribution and caps. Tinkers' Levelling, Delight, Advanced, Thinking and Jewelry adapters are loaded independently by mod ID.

Seasoned Hands saturates large scaled awards at the add-on's integer limit and keeps the fractional carry below one point. Its rounding carry remains per player and item type because the upstream award does not supply the original stack; identical tools may share less than one point of rounding, never a duplicated award or a carry between players.

Thinking's Studied Recall restores a bounded share only after a canceled pickup has actually removed its XP orb; an unrelated canceled pickup cannot repeatedly mint XP from a live orb. Last Thought observes the add-on's death-save effect; Embellished Focus reads the weapon's modifier IDs.

Jewelry's Gem Attunement reads equipped Curios and armor slots, not a ring held in hand or stored in inventory. Curios API calls live behind a presence-gated nested class. Undying Lustre reaches the native durability cost inside the death-resolution bracket even without an ordinary melee/mining action; only that save-cost perk applies there. Jeweler's Setting binds to the actually delivered piece; Polished Facet uses the shared paid-repair channel. Subspace Reserve and Medallion Concord remain reserved, unregistered IDs where no implemented supported mechanic exists. Katanas' data-driven definitions use the existing native tags and hooks.

Use Mantle 1.11.113 with Jewelry 1.2.0. A captured startup hang on Mantle 1.11.97 showed TConstruct and Jewelry constructing in parallel and waiting on each other's initialization of Mantle's `ResourceLocationLoadable` and `Loadables` classes. Mantle 1.11.113 removes that static initialization cycle; the Jewelry Gradle profile selects it without raising Runic's compile-time API requirement. Packs that must retain 1.11.97 can set `maxThreads = 1` in Forge's `config/fml.toml` and restart to serialize mod loading. See the [test matrix](TCONSTRUCT_TEST_MATRIX.md) for evidence and exact artifact pins, and the [verification summary](STABILIZATION_2.1.0.md) for executed outcomes.

## Requirements, scripts and verification

Automatic locks are off by default to preserve existing worlds. Precedence is explicit definition/material/action pack rule, configured item-ID override, optional automatic material-tier profile, then allow. Unknown materials do not create maximum-level locks. Tier 0 asks nothing; tiers 1–4 scale the stock Tinkering thresholds 1/8/16/24 and functional thresholds 1/4/8/16 to the configured skill cap. Tier 5+ needs an explicit rule. Functional requirements follow the action and primary native tags. Storage/removal is not denied by the automatic stack provider.

See [pack rules](TCONSTRUCT_PACK_RULES.md) for datapack formats and scripting hooks, [hook ledger](TCONSTRUCT_HOOKS.md) for injection targets, and [test matrix](TCONSTRUCT_TEST_MATRIX.md) for profiles and limitations. Production SRG remapping is checked statically; a Mojang-mapped GameTest alone cannot prove a production client hook. The Jewelry profile selects the exact Forge 1.20.1 Jewelry 1.2.0 artifact from CurseForge, with Mantle 1.11.113 and its required Apothic Attributes and Placebo dependencies. Executed results are recorded in the verification report; client behavior and unsupported add-on combinations still require live acceptance testing.
