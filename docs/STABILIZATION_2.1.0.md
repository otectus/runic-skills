# 2.1.0 stabilization and verification

Implemented on `feature/tinkers-integration-2.1.0`. The generated
[content trace](CONTENT_TRACE_2.1.0.md) lists every declared Skill, Perk, Passive and Power,
its registration/defaults/description and executable consumers. It covers the complete optional
catalogue: 10 Skills, 475 Perks, 38 Passives and 87 Powers. Source coverage is distinct from runtime
coverage; this report records the profiles actually executed.

## Behavior and compatibility

- Combat: Counter Attack survives the swing event until a landed melee hit; forced critical
  damage and crit ownership agree; Blood Fury heals from committed damage; Mana Shield rounds its
  XP payment without rounding its damage protection upward. Last Stand uses post-mitigation fatal
  damage and grants its two-second protection. Phoenix Rising now rescues a fatal hit at the
  configured health fraction. Survival cooldowns and Chaos Roll persist; piercing and spectral
  arrows receive their damage multiplier once. Death rewards run after survival cancellation,
  and Siphon Bond cannot convert overkill into healing. Mana Shield's description identifies XP
  as its resource cost.
- Progression: small and uneven skill caps produce valid ranks, configured disabled Perks stay
  disabled, Power factories refresh current tier gates, custom Power gates remain authoritative,
  and prerequisite Powers must still qualify. Gem refund detection excludes compression cycles.
- Powers: cooldown debt and refunds survive reconnect/restart; damage history cannot cross
  dimensions; rewind needs the complete history window, a safe destination and successful
  acquisition of its persisted cooldown. Grove healing
  suppression tracks its attacker and remaining debuffs, Rooted grants knockback resistance,
  and Harvest requires a genuinely low-health victim before a blood-spell killing hit and grants
  one HP per stack. Timed health bonuses clamp health when removed. Channel preparation follows
  the projectile through NBT; summon damage banking is bounded and persistent. Absorbed and
  recursive damage cannot charge these effects. Spell-field lifetime extension is idempotent.
- Tinkers: native action and station scopes unwind on nested calls and exceptions, repair
  previews do not spend benefits, and accepted station transactions bind effects to the delivered
  tool. Repair factors and shared caps apply once; final AoE charges survive for the full action.
  Mining prediction receives bounded, change-only server snapshots. Modifier registration
  preserves previously purchased slots while configuration gates behavior. Workshop focus,
  tool identity, jewelry ownership and add-on cleanup are covered by the same authoritative rules.
  Hook diagnostics inspect actual injected call sites rather than assuming a merged mixin fired.
- Jewelry startup: the selected runtime uses Mantle 1.11.113, which removes the circular static
  initialization between `Loadables` and `ResourceLocationLoadable` observed when TConstruct and
  Jewelry constructed concurrently on 1.11.97. Compilation and stable profiles without Jewelry
  retain Mantle 1.11.97. This uses the upstream fix without serializing the test loader; the captured
  diagnosis is retained in `build/reports/jewelry-startup-threads.txt`.
- Attribute delegation: owned neutral attributes stay registered, and reload reconciles passive
  modifiers off the inactive provider before applying the active one. Existing players can switch
  between Runic and Apothic providers without losing or duplicating their earned bonuses.

Existing registry IDs, capability version, public event types, configuration keys and datapack
directories remain intact. Ordinary cooldowns add the `powerCooldownDebt` NBT compound alongside
the existing fields; Artifice retains its separate server-tick debt. Missing add-on selections
remain saved and can be removed in the Powers screen. Protocol **13** requires clients and servers
to update together for the new mining snapshot.

Power overrides reject non-finite values; duration fields are bounded to 24 hours and block-radius
fields to 64 blocks. Position-history windows are bounded to one minute and allocate only the
requested history while the relevant Power is equipped. Required levels are bounded to 0–1000
before numeric narrowing. Unknown valid
tuning keys remain available to add-ons, with a finite numeric ceiling. `/reload` sends current
Power overrides and perk groups to connected clients. Tooltips show effective requirements and
readable override values. Corrected mechanics use explicit English fallback where old translations
would describe the wrong behavior; the fallback manifest records that translation debt.

## Art and interface

All 641 shipped content/control icons use the same authored 16×16 RGBA pixel-art system:
475 Perks, 38 Passives, 40 Skill rank variants, 87 Powers and one Powers menu emblem. Their decoded
pixel hashes are distinct. Explicit mechanic silhouettes and badges replace generated random
Power markings. Seventeen retired authoring entries remain recorded without shipping unused art.
The [art source guide](../tools/icongen/README.md) documents regeneration and the integrity check.

The main overview anchors a 20×20 icon-only Powers button in its upper-right corner, with native
16×16 art, tooltip, keyboard focus, narration, hover and disabled styling. Detail-grid icons also
render at native size. Powers return to the parent Skills screen, refresh after authoritative
selection/config updates, wrap tuning tooltips and provide recovery for unavailable saved IDs.

## Validation

Commands use JDK 17
and the checked-in Gradle wrapper. `build` includes all JUnit tests, sided-import, lock-provider,
version, translation, mixin inventory, YACL, remapping and shipped-refmap checks.
GameTest worlds are isolated by runtime profile under `run/gametest-*`; removing an optional mod
from the next profile therefore cannot strand the suite on that mod's saved dimension type.

| Runtime profile | Required GameTests | Result |
| --- | ---: | --- |
| Default: Tinkers and Irons' Spellbooks absent | 141 | PASS |
| Tinkers 3.11.2.166 / Mantle 1.11.97 + Irons' Spellbooks 3.15.0 | 240 | PASS |
| Tinkers 3.12.0.220 / Mantle 1.11.113, conservative mode | 159 | PASS |
| Tinkers 3.11.2.166 / Mantle 1.11.113 + Jewelry 1.2.0 / Apothic 1.3.7 | 239 | PASS |
| Stable Tinkers / Mantle 1.11.113 + all nine add-on selections + Irons' Spellbooks | 258 | PASS |

All five profiles passed: **1,037 required GameTest executions**, including shared tests repeated
across profiles. The Jewelry and combined runs used normal parallel mod loading with the updated
Mantle pin. Logs, completion times, runtime versions and counts are recorded in
`build/reports/stabilization-profile-results.json`; full builds also passed in the default,
Jewelry and combined profiles.

Reproduce the matrix serially from the repository root:

```sh
./gradlew build runGameTestServer -PtinkersProfile=stable -PironsProfile=true -PtinkersAddons=levelling,delight,thinking,innovation,advanced,tcintegrations,botania,ars,jewelry
./gradlew runGameTestServer -PtinkersProfile=stable -PironsProfile=true
./gradlew build runGameTestServer
./gradlew runGameTestServer -PtinkersProfile=beta
./gradlew runGameTestServer -PtinkersProfile=stable -PtinkersAddons=jewelry
python tools/icongen/build.py --check
python tools/content_trace.py
python tools/verify_against_pack.py --jar build/libs/runicskills-2.1.0.jar --mods <production-mods-directory>
```

The ordinary Gradle build passed **313 JUnit tests**, with no failures, errors or skipped tests.
Mixin validation checked **81 injectors and 96 member references**; the shipped bundle contains
the generated refmap and MixinExtras. Icon generation verification passed all **641** unique native
images. The source trace's **86** relative source links resolve. Version checks also require the
current README compatibility row's protocol and the updater's release note to match the release.

Production-name validation uses 19 original dependency jars from the Gradle cache, with each
artifact's SHA-1 checked against its cache content directory and its SHA-256 recorded in
`build/reports/production-validation-provenance.json`. The sample contains stable Tinkers/Mantle,
the combined add-ons and their named companions. It is a curated dependency sample, not a claim
that a particular user's complete pack was launched. All **35 references across 15 target classes**
resolve with **zero failures and zero skipped targets**. The detailed result and checked artifact
hash are recorded in `build/reports/production-reference-final.txt` and
`build/reports/production-reference-artifact.json`.

The distributable is `build/libs/runicskills-2.1.0.jar` (2,326,600 bytes), SHA-256
`2bfa43279b6a82b09b86cfbb9ce9dbd627a731a41a8695ff886a876697c21fb2`.

The optional spell profile is `-PironsProfile=true`: the same Irons' Spellbooks **3.15.0** artifact
used for compilation (CurseForge file 7402504), with Forge GeckoLib **4.8.4**. The default profile
does not load Irons' Spellbooks or Tinkers, so absence remains independently testable.

The CI workflow retains the default absence job and adds named stable, beta, stable-with-spells,
and combined add-on matrix entries. Each retains its own test log and distributable. CI was
configured here; the results in this report come from local runs, not a remote CI execution.

## Verification limits

Headless Forge tests verify server gameplay and conditional registration; they do not replace an
interactive client pass for keyboard navigation, narration audio, GUI scale combinations or
rendering with third-party resource packs, or a two-client latency/prediction session.
Native-resolution contact sheets were visually reviewed
and image integrity is automated. The manual [smoke checklist](SMOKE_TESTS.md) remains applicable.

Successful builds still report existing Forge API/Gradle deprecations and upstream diagnostics.
The optional Botania target is absent from the compile-only validation classpath; the independent
production-jar check resolves it against the actual Botania jar.

The ten documented Power design substitutions in [Content Status](CONTENT_STATUS.md) retain their
accurate shipped descriptions. They are implemented behavior, not claims of exact equivalence to
every item in the older design document. Optional integrations outside the executed runtime matrix
are source-reviewed; no claim is made that every possible pack combination has been played.
