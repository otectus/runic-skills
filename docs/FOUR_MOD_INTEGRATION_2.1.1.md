# Four-mod integration development — 2.1.1

Reference: [four-mod integration specification](Runic_Skills_Four_Mod_Integration_Spec.md).
Baseline: `3fe056a01c348d5e5a1a98a639f2ce6a1efe49f7`, retaining the existing uncommitted
2.1.0 stabilization work. **All 32 perks and 24 Powers are implemented and registered.**
The [per-entry ledger](integrations/content-ledger.json) and
[native test mapping](integrations/content-native-tests.json) connect every entry to a
representative native server test. This completes the requested content implementation;
it does not certify every item/provider or the full specification's client and gate matrix.

## Completed catalogue

Each integration supplies eight perks and six Powers, with requirements, availability,
configuration, localization, original icons, runtime behavior and cooldown/lifecycle handling.
Missing native capabilities keep affected selections dormant and prevent point spending.

| Integration | Implemented behavior |
| --- | --- |
| Simply Swords | Charged primary damage and Draw; ordinary wear; read-only awakening/gem inspection; successful gem effects, manual gems and summons; paid/unlocked ability followups; owner-verified return; paid repair; all six Power sequences. |
| Simply More | Mounted travel and lance hits; shield-break followups and allies; aimed legal reach; same-attacker counters; charged attacks; native Mimicry form continuity; paid repair; all six Power sequences. |
| T.O. | Aqua attribute and Endurance secondary-school binding; paid Aqua/other-school rotations; native owned summons; functional talent slot; advanced relic combat; verified counterspell interruption; paid Mechanized armor activation; all six Power sequences. |
| Tide | Cast preparation; catch wear and bait conservation; native journal knowledge, comparison, milestones and favor; native success window; habitat/species sequences; legal lava/void charges; all six Power sequences. |

## Implemented

- Version **2.1.1**, protocol **14**. Client and server must update together.
- The optional [T.O. companion and Aqua Attunement](integrations/tom-aqua-attunement.md)
  bind the pinned public Aqua API. Aqua Attunement is a paid Magic 8 perk giving one
  5% relative native spell-power attribute modifier. Repeated reconciliation is idempotent;
  disabled or unavailable content removes only Runic's modifier and retains the saved rank.
  The native Aqua school also has its own Endurance coefficient (default 0.001 per level),
  separate from the eight existing Iron's school mappings. No extra Aqua damage-event multiplier is added.
- Independent `off`, `auto`, `observe` module modes use the existing common JSON5 file,
  reload process and server snapshot. Invalid/null modes become `off`. The snapshot codec
  supports bounded strings and preserves null versus empty values.
- Exact version and SHA-256 evidence for the four pinned artifacts. The Tide hotfix cannot
  be confused with another jar reporting 2.1.1. Simply More classification also requires
  the pinned Simply Swords pairing. Unknown/remapped artifacts receive no native hook.
- Read-only equipment adapters own their registry namespace; Simply More cannot be claimed
  by a Simply Swords superclass. Fishing rods have a distinct role. No awakening state,
  implicit, socket, spell container or rod attachment is initialized.
  Tide also owns the native rod class replacing `minecraft:fishing_rod`, confirmed in the
  pinned artifact; its casting benefit includes that wooden rod.
- Tide **Many Waters** (Endurance 18) prepares the next cast after a first catch in a new
  habitat family. Its [ten-minute session](integrations/tide-habitat-sessions.md) remembers
  at most four rewarded habitats and preserves that budget across lifecycle changes.
- Simply Swords **Patient Temper** (Tinkering 10) joins the shared wear roll at the native
  ordinary melee/mining boundary, conserving at most one point per root. Its
  [boundary contract](integrations/swords-wear-boundary.md) documents exclusions and tests.
- Simply Swords **Resonant Reading** (Wisdom 12) adds Shift-held native weapon details.
  It reads awakening/unlock state, existing gem slots and IDs, the native gem progression
  gate, and stored implicits without initializing components. Unknown or malformed state
  is explicit. Existing item requirements appear once; More items remain More-owned.
  The pinned SS/More/Tide production profile preserves pristine and decorated NBT across
  all 133 registered native SwordItem weapons, including dormant/awakened and gem cases.
- Tide **Measured Cast**: paid, single-rank Dexterity perk, reference level 6, scaled with
  `ScaledRequirement`. Native preparation becomes `ceil(duration × (1 − reduction))`,
  with a 6-tick floor. Native durations already below 6 remain unchanged. Default reduction
  is 10%, capped at 25%. Both native server release and client prediction use this hook
  and server settings. It changes no bite time, loot, XP or minigame outcome.
- Tide **Patient Hands** (Tinkering 8) and **Careful Landing** (Endurance 24) each
  contribute a default 10% chance to spare one native fish-retrieval wear point. Careful
  Landing requires legal native lava/void access. All catch contributions share one roll,
  a 35% catch cap, and the remaining budget under Runic's overall 90% wear-avoidance cap.
- Tide **The Stillwater Oath**, a Dexterity Mark in Angling, prepares one 15% faster cast
  after a verified fish catch; the charge lasts 60 seconds and its cooldown is 20 seconds.
- Tide **Baitkeeper** (Fortune 16) gives a default 10% chance to preserve one actual native
  bait decrement. One roll per cast, capped at 25%, spans all occupied slots and outputs.
  It requires an accepted, canonical fish output and the matching native consumption object;
  unrelated consumption and rejected delivery receive no benefit. The native loop can still
  spend a preserved last unit on a later output. There is no copied-stack refund or new bait.
- Tide **Unbroken Thread**, a Tinkering Mark in Angling, prepares one 20% wear-conservation
  chance after two distinct fish casts. Only a subsequent successful fish retrieval spends
  it. The charge lasts 90 seconds and its cooldown is 30 seconds. Duplicate retrievals do
  not advance the sequence. Perks and Powers have independent switches.
- [Keeper of the Banks](integrations/tide-keeper-of-the-banks.md), a Wisdom Seal in Angling, rewards three distinct fish species across
  two habitat families within five minutes. It prepares one 20% bait-preservation roll and
  one 15% faster cast, expiring after 90 seconds with a 120-second cooldown. Native delivery
  supplies species and habitat evidence. Baitkeeper and Keeper share one roll, a 25% cap
  and at most one conserved unit per cast. A failed roll spends the bait charge; empty or
  rejected delivery preserves it. An accepted cast spends only preparation.
- All 24 Powers use normal selection, skill and point eligibility. Unequip, death, logout,
  dimension change and loss of eligibility clear charges/sequences while retaining cooldown
  debt. Native cast success spends preparation; reading the preparation bar cannot spend it.
- Measured Cast requires an actual transformed injector call site; a merged method alone
  is insufficient. Missing hooks disable purchase and execution while retaining saved ranks.
- Tide's native `HookAccessor` takes precedence over output namespaces in fishing-event
  ownership. Its pre-delivery Forge events bypass Runic's Starcatcher bonus loot/XP handler
  even when Tide benefits are off. Native events and outputs are not cancelled or mutated.
- `PowerAvailability` is shared by activation and point accounting. Missing capabilities
  and disabled Powers cost no points. Returning selections reserve the current budget in
  stable tier/slot order, without deleting dormant IDs or exceeding available points.
- Iron's secondary-school bonuses use fully qualified IDs and preserve the eight existing
  mappings. Foreign schools named `ice`, for example, receive neither its mapping nor
  its catalyst. A descriptor registry supports later companion bindings.
- Root actions support 96 effect claims: seven existing claim sites plus all 56 integration
  entries total 63, leaving bounded bookkeeping capacity. Exhaustion is counted once per
  root and visible to operators. Different actors' nested actions cannot share claims.
  Projectile snapshots retain their independent existing serialization bound.

The [Guard foundation](integrations/guard-boundary.md) is implemented: a separate, transient pool capped at four points,
refreshed to the greater balance, with a maximum eight-second expiry. Its damage seam
follows all Forge damage callbacks, armor and native absorption. Grants during the
current damage callback cannot cover that hit. Native invulnerability/effect-bypass tags
and the extensible `runicskills:bypasses_guard` damage tag bypass this pool. Server state
synchronizes a balance/expiry display for self, mount and the looked-at recipient.
No Guard-dependent catalog entry is registered before its own native trigger is verified.

## Configuration and commands

Settings remain in `config/RunicSkills/runicskills.common.json5`, not a second gameplay
configuration. Prefixes are `simplySwords`, `simplyMore`, `tom`, `tide`. Each has
`IntegrationMode`, `AutomaticEquipmentGates`, `NativeAbilityGates`, `Perks`, and `Powers`.
Automatic gates default **false**. Specialized switches request workshop, Mimicry, Aqua,
native activation, journal, minigame and weighting capabilities.

**A reserved feature switch does not implement its missing hook.** There are currently no
new automatic equipment locks or native ability gates. Existing manual
locks and upstream restrictions retain their behavior.

Measured Cast tunables: `tideMeasuredCastRequiredLevel` (−1 through 32; nonpositive disables)
and `tideMeasuredCastPercent` (0 through 25; zero disables purchasing a no-effect perk).
Patient Hands and Careful Landing have corresponding `RequiredLevel` and `Percent` fields,
with reference levels −1..32 and chances 0..35. Baitkeeper has `tideBaitkeeperRequiredLevel`
(−1..32, default 16) and `tideBaitkeeperPercent` (0..25, default 10). All implemented entries have original icons
from the existing pixel-art generator and explicit English fallback for untranslated keys.

Power overrides use the existing `data/<namespace>/powers/<id>.json` surface. Stillwater
Oath reads `values.preparation_percent` (0..25, default 15) and `values.charge_seconds`
(1..60, default 60). Unbroken Thread reads `values.avoidance_percent` (0..35, default 20)
and `values.charge_seconds` (1..90, default 90). The two Marks accept `icd_ticks` (1..72000; zero is
clamped to one tick), with defaults 400 and 600 respectively. Non-finite values are rejected
or use the documented default. Their descriptions use the same bounded numbers as execution.

- `/skills integrations status`: module classification status.
- `/skills integrations inspect hand`: read-only held-item ownership.
- `/skills integrations validate`: permission level 2; hashes, known capabilities,
  unavailable-hook explanations and claim-exhaustion count. Reports evidence, not certification.

- `/skills integrations explain <player> <action>`: operator inspection of the held item,
  resolved rule candidate, gate capability and equipped Angling Power eligibility.
- `/skills integrations dump`: operator export to `debug/runicskills-integrations.json`;
  includes artifact, config, capability and rule evidence without inventories or player IDs.

Keeper overrides read `values.bait_percent` (0..25, default 20),
`values.preparation_percent` (0..25, default 15), `values.charge_seconds` (1..90, default 90),
and `icd_ticks` (1..72000, default 2400). Zeroing both benefits disables the Power.
Its five-minute sequence stores at most three species and a bounded habitat bitmask.

All entries use the existing perk and Power screens. Tide journal comparison and favor use
a server-authoritative screen; visual client QA remains pending. Inspection recognizes Tide's replacement wooden rod.

## Contracts awaiting native adapters

`IntegrationRuleIndex` supplies immutable bounded rules, replacement/priority/resource-ID
ordering, same-priority conflict diagnostics, maximum-per-skill merging, exclusions and
reference scaling. The reload listener reads
`data/<namespace>/runicskills/integrations/*.json` using the specification's schema. A whole
candidate publishes atomically with a revision only after every required resource validates.
Malformed JSON, unknown required items/tags, duplicate rule IDs, unknown fields/actions,
non-finite/fractional numbers and bounds violations retain the previous index. Files are
bounded to 64 KiB each, with at most 512 resources/rules. `optional: true` at the root permits
an absent module to remain dormant; its syntax is still validated. Required item tags must
exist as resources in the candidate pack, so stale bindings from a prior reload cannot pass.

**Rule resolution is connected to diagnostics, but gate enforcement and client rule sync
remain pending.** No family locks are generated from raw artifact tags.

`TideCatchLedger` supplies one live cast per player, actor/hook/generation/dimension identity,
expiry, explicit result types and single-use commits. History is bounded to 32 hooks per
player and 1,024 players, with explicit lifecycle cleanup. The native bridge now
observes actual casts, accepted item delivery, canonical fish processing and final retrieval
wear. It verifies the real hook owner, hand, unchanged rod/attachment snapshot, current manual
requirements and legal native medium. Failed delivery, duplicate retrieval, pulled entities,
items and empty catches do not grant benefits. Native cleanup always proceeds.

Fishing Real/Hybrid Aquatic conversion profiles remain unavailable until their handoff is
verified. The native Tide journal UI, legal-species weighting and native success-window hooks
are implemented; external minigame-provider timing adapters remain unavailable.
The bridge creates no fish, XP, refunds or additional journal entries.

General registered-Power cooldown debt was already present in the starting working tree
and is retained, including Artifice's separate clock. All integration Powers use that debt
and clear their transient charges/sequences on loss of eligibility and lifecycle transitions.

## Evidence and validation

[Compatibility manifest](integrations/compat-manifest.json) contains the downloaded hashes
and actual dependency manifests, including T.O.'s unusual Cataclysm range verbatim.
`docs/integrations/artifact-tags/` contains raw tag inventories from the jars, not reviewed
per-item progression rules or proof that every tagged item is installed.

```text
gradlew.bat build compileGametestJava
gradlew.bat runGameTestServer
gradlew.bat productionValidationJar
gradlew.bat tomCompatJar
python tools/icongen/build.py --check
python tools/verify_four_mod_artifacts.py --mods <directory> --require-all --output <report.json>
```

The static checker verifies four artifact hashes, 17 Tide and four T.O. member descriptors; it does
not establish behavior. `checkMixinRemapping` has no Tide compile dependency and reports
that owner unresolved; the binary checker covers this literal Tide-declared member.
The existing build also retains an unrelated unresolved Botania-reference warning.

`productionValidationJar` is an explicit, reobfuscated test-only mod. Neither release jar
contains its entry point, GameTests or structure. Its output is under `build/validation-libs/`,
outside the release CI's jar upload glob. Put it alongside the release jar in an
isolated, disposable Forge server and enable `-Drunicskills.productionValidation=true`.
The test-only launcher places forty structures at (0,160,0) and runs automatically after
server startup, reporting each result as `FOUR_MOD_PRODUCTION PASS` or `FAIL`.
Forge's normal GameTest launcher is intentionally disabled in production, regardless of
its `forge.enableGameTest` flag. This harness does not change that production flag.
Never distribute the validation jar to players. The optional T.O. companion is built separately
under `build/compat-libs/`; install it with the matching core and pinned T.O./ISS profile on both sides.

Validated scope (the machine-readable report records current counts and artifact hashes):

- Normal release build: unit tests, language parity, common-side imports, Mixin inventory,
  icon integrity and packaged-refmap verification.
- Forty targeted production checks in each of five profiles: all four integrations,
  all four without the T.O. companion, SS/More/Tide, Tide only, and all integrations absent.
  Native tests explicitly skip absent dependencies; those skips are not native behavior evidence.
- All 56 entries have representative positive native evidence in the combined profile, plus
  rejection, source ownership, conservation, shared caps or lifecycle checks at their shared seams.
  The gem tests include rejected effects, manual Ward, canceled summons and successful summons.
- Shared Guard covers native damage ordering, armor/absorption, shields, cancellation, nested
  damage, callback grants, bypass, lifecycle and wire bounds.
- All 698 authored icons pass completeness, unique pixels and alpha checks. Native class/member
  signature checks and exact four-artifact hashes pass; runtime hooks are verified after transformation.
- Release jars exclude validation entry points, GameTests and upstream native classes.
  The separate harness logs the actual release and companion hashes. No release was published.

[Machine-readable results and jar hashes](integrations/validation-2.1.1.json) record the
current checks. Prior atomic rule-reload validation is retained as foundation evidence;
it is not presented as a new test of the final artifact.

## Supported boundaries and remaining release work

The content is implemented on verified native paths. These boundaries are deliberate:

- Reach uses Forge's public server resolver and the aimed hitbox intersection. Better Combat
  disables these two entries until a verified server reach adapter exists.
- Relic Care covers paid manual anvil extraction; unverified workshop routes stay unchanged.
- Simply Swords ability rewards require a successful native activation API result. Paid rewards
  also require actual native mana debit. Unverified release-only completion paths are not inferred.
- Mimicry continuity follows native replacements after damage in the admitted manual ability
  timeline. Copied stacks and unverified delayed transitions cannot fabricate continuity.
- Pressure Reader verifies interruption of a casting native T.O. `IMagicEntity` by a normal paid
  Counterspell. Attempted, canceled, idle-target and unsupported dispel paths give no reward.
- Artificer receipts cover authenticated Mechanized Exoskeleton key activations with actual Plasma
  Fuel debit and committed thrust or projectile output. Passive drains, toggles and other unverified
  equipment paths cannot arm them. Native armor state and costs are never changed by the adapter.
- Confluence recognizes Hydroshot, Aqua Missiles and Tsunami as offensive Aqua casts. Utility
  casts preserve the charge; arbitrary spell/entity names cannot claim the shared damage budget.
- Tide external minigame providers and fish-delivery conversions need separate verified adapters.

Client rendering/prediction, remote multiplayer, exhaustive per-item/provider acceptance and
balance profiling remain untested. Automatic equipment/native ability gate enforcement,
reviewed default gate rules and client rule synchronization remain separate specification work.
Whitespace checking uses Git's `cr-at-eol` setting for this Windows working tree.
