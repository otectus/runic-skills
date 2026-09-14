# Runic Skills 2.2.0 implementation and validation

Implemented from [the progression/config/Pack Mule handoff](Runic_Skills_Progression_Config_and_Pack_Mule_Implementation.md).
The user explicitly selected **2.2.0**. This is an unpublished working build; no release was
uploaded and no user installation or existing play world was modified during validation.

## Baseline and scope

The checkout was `master` at `ee17728ea6eacb56d2651d825c9c656fb6d729cb`, with a substantial
pre-existing uncommitted 2.1.2 implementation. Those changes were preserved. The public
handoff's 2.1.1/protocol-14 baseline therefore differs from both this working tree and the
locally available 2.1.2/protocol-15 artifact. The session recorded status and a source archive
before editing in `/tmp/runicskills-220/`; the verification receipt records their hashes.

The retained pre-update build (SHA-256 `54d505772e11302874c9d6e72af5b1cb9144c3aff32a41d63a69543ed434b514`) does not boot on pinned Forge: its
`MixLivingEntity.runicskills$beginFinishingItem` injection names `completeUsingItem`, which
does not resolve in production. This prevents claiming an old-release gameplay reproduction
of issue #7. The offending recursive Added handlers were inspected in the preserved source.
The affected issue environment, Forge 47.4.10, was not reproduced; validation uses 47.4.23.

Toolchain: Minecraft 1.20.1, Forge 47.4.23, Java 17, Gradle 8.10, ForgeGradle 6.0.24,
Parchment 2023.09.03, YACL 3.5.0+1.20.1-forge. Stable Tinkers' is 3.11.2.166 with
Mantle 1.11.97 (1.11.113 for Jewelry); the conservative beta profile is 3.12.0.220 with
Mantle 1.11.113. Current network protocol is **16**.

## Implementation

| Workstream | Final behavior and main source |
| --- | --- |
| Effects / issue #7 | `MixLivingEntity` transforms the incoming two-argument `addEffect` once before Forge's normal event/merge. Recursive Temporal Wisdom, Blessing of Luck and Hearty Feast subscribers were removed. `IncomingEffectPolicy` sums percentages, floors once, then adds potion flat duration; finite duration saturates at `Integer.MAX_VALUE`. Harmful reductions retain their additive policy. |
| Food/potion origin | `EffectApplicationContext` scopes the exact native target/effect, including transformed identity, with finally restoration. `MixPotionItem`, native food application and `MixSuspiciousStewItem` distinguish genuine food/potion effects from unrelated effects during consumption. Hearty Feast extends beneficial food effects only. |
| State and sharing | Copies retain serialized hidden chains, factor/ambient/icon/particle state, custom curatives and the outer source entity. Unknown `MobEffectInstance` subclasses remain unchanged, with at most 16 distinct class diagnostics per process. Shared Flame starts from the already transformed source duration, shares half rounded down, then applies a recipient's own bonus once. Shared copies cannot redistribute; unrelated nested effects can share independently. Infinite and instant effects are not copied. |
| Configuration | `ConfigHolder.beginEdit/commit` provides detached drafts, revision conflict detection, validation, unknown-key preservation, atomic writes and structured outcomes. Opening/canceling/resizing does not write. The actual YACL screen uses this persistence owner and keeps failed drafts available. Both config entry points use the same transaction. |
| Authority | Integrated saves commit/apply on the server thread. Server readers use local authoritative storage even in a shared client/server JVM. Successful reloads refresh perk/passive/Power metadata, resolved rules, attributes and Pack Mule inventories, then synchronize players. Remote gameplay options are read-only. |
| Caps / transactions | `LevelCaps` and `LevelCapMath` derive totals from registered skills. Custom values remain literal; automatic mode preserves the stored custom budget. Presets and live previews cover caps through 1000. `ProgressionService` enforces ordinary global caps and guards nested mutations; the purchase packet guards XP spending across callbacks. Admin/respec exceptions remain explicit. |
| ID rules | Explicit `LockItem.Allow` survives discovery, merging, immutable snapshots and wire round trips. Manual duplicate skill requirements use max; manual item overrides replace automatic rules. Spartan zero-tier starters are emitted as explicit exemptions. Longest suffix recognizes `parrying_dagger` before `dagger`; discovery respects opt-outs. |
| Generic providers | Verified vanilla native tool/armor materials use reference tiers rather than one namespace base. Blocks, food and known component patterns are excluded from generic gear discovery. Unverified mod materials retain labeled conservative fallback requirements. Manual and curated utility policies are retained. |
| Synchronization | `HandlerSkill.Snapshot` publishes one immutable server revision with matching gameplay config. `ConfigSyncCP` sends at most 128 entries/chunk, 512 chunks and 262144 config bytes. Clients install only a complete validated revision and keep the previous one during assembly. No client-side provider regeneration occurs. Startup, config apply and completed datapack reload rebuild the server table. |
| Tinkers' | Missing/new automatic-lock settings default true; explicit false stays false. Actual ATTACK/USE/EQUIP/MINE/CRAFT/TAKE reaches the existing stack resolver. The optional locked-item ejection policy uses TAKE, so automatic wield locks do not eject stored tools. Native block destruction checks MINE before payment. Known high tiers saturate safely; mixed unknown parts retain known gates. Automatic CRAFT/TAKE exemptions follow applicable explicit pack rules. |
| Stack explanations | A bounded, rate-limited server query inspects only a real slot in the current valid menu. Replies carry request/menu/slot/state/fingerprint identity. Tooltips expire on material/config changes, use localized skill/action names, and show rule/uncertainty information. Inspection commands use the same resolver. |
| Pack Mule | Three normal Strength perk ranks give 128/192/256 to eligible natural-64 stacks in the explicit owner's inventory, offhand and cursor. Default reference unlocks are 8/16/24. Natural-1/16, damageable and capability-bearing/stateful items are excluded. Slots, wrappers, native menu transfers, inventory pickups, creative validation and drops enforce destination capacity. Global item maximums remain untouched. |
| Inventory recovery | Rank/config loss normalizes into available space, retaining unplaced excess without allowing growth. Native drops split to natural limits. Canceled whole-stack and single-item tosses and refused post-capture death spawns retain ownership; grave listeners that take ownership do not receive duplicated recovery. Exceptional recovery is visible, persistent and bounded under ordinary operation. |
| Art / localization | Pack Mule uses the existing generated icon workflow, with authored glyph/spec/manifest inputs. All three-digit counters remain native and are tested visually. New English labels have explicit translation fallback debt; existing translations were not fabricated. Full catalogue: 10 skills, 508 perks, 38 passives, 111 Powers. |

### Count format and recovery

Ordinary positive counts 1–127 keep their legacy byte wire representation. Counts 128–1,048,576
write byte `0x80` followed by a positive bounded VarInt, then the unchanged Forge share/full tag.
Empty-stack encoding and following fields are unchanged. Both directions use the same format,
regardless of whether Pack Mule is enabled. Creative input additionally checks the actual
destination, item eligibility and the player's authority; a new 257-item Pack Mule slot is denied.

NBT `Count` remains a byte through 127 and becomes an integer above it. Reading happens before
constructor initialization can treat an extended stack as empty. The reader remains installed
with Pack Mule disabled. Unsupported saved integer counts are quarantined as full compressed
NBT with an error containing the recovery path. If that preservation fails, loading refuses to
silently discard the original. Existing wrapped corrupt bytes are not guessed back into items.

Canceled transfers normally queue at most 128 records and replay at most 128 per second.
Overflow exports explicit owner/stack NBT for operator recovery. If disk export itself fails,
emergency ownership stays in player NBT instead of being deleted; replay remains bounded.
This exceptional storage failure can exceed the normal record limit and is logged. Review
the recovery directory and resolve the storage failure before removal/downgrade.

### Feedback-loop audit

| Candidate cycle | Stopping invariant / cleanup |
| --- | --- |
| Added → self addEffect | Removed for the three reported perks; a pure incoming transform precedes the single native event. |
| Added → ally addEffect → further allies | Exact target/effect SHARED scope suppresses only redistribution of that copy; finally restores an outer scope. A three-player test also proves unrelated nested buffs are not suppressed. |
| Skill callback → nested purchase/grant | Per-player mutation and purchase guards span veto/script callbacks, reconciliation and XP spending; finally clears both. Denied/no-op purchases do not charge. |
| Damage → retaliation/cleave/secondary damage | Existing `DamageContext.mayEmitSecondary` and scoped origins cover Limit Breaker, Bulwark, Spell Parry and spell/summon splash. Deferred counterattack commits once. Damage-context regressions remain in the base suite. |
| Healing → healing/conversion | Native healing listeners scale the event; periodic recovery has a tick boundary. Herald starts cooldown debt before healing/smite, and smite uses secondary damage scope. Existing healing/Power tests remain in their profiles. |
| XP/craft → rewards/refunds | Existing crafting execution/context guards, committed result ledgers and XP reward policies remain in place. Real 2×2/3×3, recipe-book and shift-craft tests exercise extended source stacks with payment conservation. |
| Wear → repair/avoidance → wear | Existing per-operation wear and repair budgets remain. Stable Tinkers' native hook tests verify payment and callback boundaries. |
| Inventory → canceled spawn → retry | Recovery restores through explicit native/player destination bounds. Full inventories retain ownership; retries cannot create a second world drop. Random native clicks assert conservation after every operation. |

## Validation and reproducibility

Executed results, exact artifacts and retained evidence are in
[`progression-2.2.0/verification.json`](progression-2.2.0/verification.json).
The registry/provider comparison and warning dispositions are documented in
[the progression audit](PROGRESSION_AUDIT_2.2.0.md).

Executed checks: **370 JUnit**, **234 base GameTests**, **352 combined add-on GameTests**,
**256 beta GameTests**, **31 multi-integration production tests**, and **22 production tests each**
for core-only, Spartan Weaponry-only and Spartan Shields-only. Earlier checkpoints and the
last diagnostics-only change are identified by exact artifact hashes in the receipt.

Core checks include JUnit, native Forge GameTests, packaged mixin/refmap validation and
reobfuscated production-jar tests. Native tests cover the specified count boundary matrix,
NBT/full/share-tag round trips, malformed counts/packets, owner-specific capacity, Forge
insert simulation, partial transfers, all click families, 2000 randomized native menu
transactions with per-operation conservation, real crafting payments, respec, canceled
toss/death spawn and grave ownership.

The real client harness exercises both config entry points, Save/Done, Cancel, resize,
external conflicts, actual I/O failure and retry, integrated-server authority, reopening,
restart, real network counts, native split/merge and remote read-only values distinct from
local defaults. Two simultaneous real clients with ranks I/III and local caps 64/100 verified
128/256 counts, read-only server cap 32, and native split/merge over a dedicated connection.
A second paired session exercises the published integrated LAN server with synthetic offline
identities; only the test fixture disables account authentication. It also captures native
counts at GUI scales 1–4. Screenshots and receipts
are retained with the verification evidence.

Useful commands:

```sh
./gradlew build productionValidationJar runGameTestServer --offline --console=plain
./gradlew runGameTestServer -PtinkersProfile=stable -PtinkersAddons=delight --offline --console=plain
./gradlew runGameTestServer -PtinkersProfile=beta --console=plain
./gradlew runClient -PprogressionClientValidation=true --offline --console=plain
./gradlew runClient -PprogressionClientValidation=true -PprogressionClientPhase=restart --offline --console=plain
./gradlew runClient -PprogressionClientValidation=true -PprogressionClientPhase=io --offline --console=plain
./gradlew runClient -PprogressionClientValidation=true -PprogressionClientPhase=scales --offline --console=plain
python3 tools/audit_progression.py CURRENT.json BASELINE.json --output COMPARISON.json
```

Client fixtures use disposable `build/update-220-*` directories; create its `saves/validation-220` from
a disposable test world before the first launch. The remote check uses a separate dedicated
server in `build/update-220-remote` and client phase `remote` with
`-PprogressionClientRemote=127.0.0.1:25585`. Test harness classes are excluded from release jars.

## Boundaries and independent findings

- **Historical reproduction:** the available 2.1.2 jar fails before world startup on its old
  mixin target. Historical provider bytecode can run against the same current production
  registry/config and supplies genuine old requirement vectors, but that is not a successful
  2.1.2 gameplay boot. The release delta also includes the user's pre-existing 2.1.2 work.
- **Roaring beta 0.3 (P1 upstream):** loading the supplied full dependency set on a dedicated
  server fails because Roaring loads `LocalPlayer`. It was moved out only in the isolated
  validation copy. Its optional Bielgg link remains absent; this is not a full untouched-pack
  startup pass. Other upstream tag/client-reflection warnings are retained in the logs.
- **Disposable Epic Knights add-on config:** one rerun failed in `EpicKnightsAddon.<clinit>`
  after Cloth Config reported an unterminated string and its fallback threw a null-pointer
  error. The failed log and resulting config files were preserved; regeneration in the test
  instance allowed all 31 production checks to pass. The file corruption origin is unverified.
- **Generic material uncertainty:** the audit labels unknown mod material tiers. Tested
  fallback mechanics do not certify the intended balance of every special native material.
- **Custom effects:** arbitrary instance-subclass fields have no general serialization adapter;
  those instances remain unchanged with bounded diagnostics. Normal Forge custom MobEffects using the standard instance
  class retain serialized state. No third-party event-bus/logging patch ships.
- **External inventories/codecs:** native containers, Forge wrappers and the actual tested pack
  boundaries are covered. Unexercised sorting/recipe-transfer mods, arbitrary graves and other
  extended-count codecs are not claimed universally compatible. Another incompatible codec
  requires a verified adapter. The existing 256-count protocol must not be disabled mid-session.
- **Downgrade:** normalization covers online player inventory/cursor/recovery state. Offline
  saves and third-party copied payloads require the explicit process in
  [the migration guide](MIGRATING_TO_2.2.0.md).
