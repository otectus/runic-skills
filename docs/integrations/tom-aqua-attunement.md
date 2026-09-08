# T.O. companion and Aqua Attunement

Build `gradlew.bat tomCompatJar` to produce
`build/compat-libs/runicskills-tom-compat-2.1.1.jar`. Install it alongside the matching
Runic Skills core, T.O. 6.3.0-1.20.1 and Iron's Spellbooks 1.20.1-3.15.0 on both sides.
The normal core build remains independent. The companion contains its own entry point
and metadata, with no copied upstream classes, native jars, core classes or tests.
It carries no core mixin configuration or refmap; the core jar owns those resources.

The companion waits for mod loading to complete, verifies the pinned T.O. and ISS
artifact hashes, and reads the public `TravelopticsSchools.AQUA_RESOURCE`, `AQUA`,
`TravelopticsAttributes.AQUA_SPELL_POWER` and `AQUA_MAGIC_RESIST` fields. It checks
school registration and attribute identities before publishing `AQUA_ATTRIBUTE`.
Missing or mismatched bindings leave the perk unavailable with an operator explanation.
The core never loads the companion entry point or a T.O. class.

## Native numerical contract

The pinned public Aqua school key is `traveloptics:aqua`; its native power attribute
is `traveloptics:aqua_spell_power`, with neutral value 1. Iron's native
`SchoolType.getPowerFor` reads that attribute directly. Aqua Attunement applies one
transient `MULTIPLY_TOTAL` modifier of 0.05 by default. For example, a native Aqua
attribute value of 2.5 becomes 2.625. Native limits still apply. This is a relative
increase to that attribute, not a promise that every spell's final damage rises by 5%.
This perk introduces no second damage-event multiplier or resistance bonus. The companion
also registers the native Aqua school for the separate Endurance secondary-school coefficient
(`tomAquaSecondaryPerLevel`, default 0.001); it does not alias any foreign school.

The modifier UUID is registered in the shared Runic ownership inventory. Reconciliation
keeps the same object when its value is unchanged; tuning replaces only that UUID.
Rank loss, skill loss, disabled content, module `off`/`observe`, `tomPerks=false`,
`tomAquaMapping=false` and a zero percentage remove it. Login clears stale saved copies
before applying a transient value. Core cleanup still runs if the companion is removed,
without removing the saved perk rank or another mod's modifier. Logout and death remove
the modifier, and ordinary attribute reconciliation handles return to play.

## Progression and settings

`runicskills:tom_aqua_attunement` is a paid, single-rank Magic perk, reference level 8
at a skill cap of 32. Positive requirements scale with the existing `ScaledRequirement`.
Use the existing common configuration:

- `tomAquaAttunementRequiredLevel`: −1..32, default 8; nonpositive disables.
- `tomAquaAttunementPercent`: 0..10, default 5; zero disables.
- `tomIntegrationMode`, `tomPerks` and `tomAquaMapping` must permit the feature.

T.O. Powers retain their independent switch. The companion also binds the verified paid-cast,
owned-summon, counterspell and paid Mechanized armor hooks used by the other 13 entries.
See [the complete catalogue and supported boundaries](../FOUR_MOD_INTEGRATION_2.1.1.md).

## Verification

`TomAquaGameTest` checks native relative scaling, direct school reads, isolation from
resistance and Fire power, idempotence, tuning, eleven lifecycle cases and stale saved
modifier cleanup. A separate test checks that removing the companion leaves the saved
rank dormant and clears only Runic's modifier. The native test explicitly skips when
the companion is absent. See [the validation report](validation-2.1.1.json) for current
tested artifact hashes and profile results. Client presentation and remote multiplayer
remain separate validation work.
