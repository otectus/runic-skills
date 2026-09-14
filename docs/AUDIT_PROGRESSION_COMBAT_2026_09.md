# Progression, attributes, combat and Powers audit — September 2026

The source of truth for this review is `CLAUDE.md`, `MODMAP.md`, current registrations,
capability code, packet handlers, event subscribers and mixins. Historical audit comments
were leads, not evidence. [CONTENT_TRACE.md](CONTENT_TRACE.md) contains the complete
registration-to-consumer index; this document records the semantic review and changes.

## Priorities and implemented repairs

| Priority | Finding and evidence | Resolution |
|---|---|---|
| High | `ExperienceMath.sum` multiplied in `int` before dividing; legitimate high XP levels wrapped well before the final total exceeded an integer. `SkillLevelUpSP.addPlayerXP` also added two integers without saturation, so a positive grant could erase the balance. | Long intermediates, explicit saturation at the vanilla point-field limit, bounded binary-search inverse, finite progress handling, overflow-safe level-cost and signed adjustment arithmetic. Normal costs and curve thresholds stay unchanged. |
| High | `RegistryAttributes.modifierAttributes` used allocated passive ranks irrespective of current governing-skill requirements. Lowering a skill or raising pack gates preserved all those stats. | `Passive.getEffectiveLevel` evaluates the contiguous qualified ranks. Allocated ranks remain saved and reactivate when requirements are met again; attribute reconciliation uses effective ranks. The interface displays a retained/effective discrepancy. |
| High | The Long Note, Warmage's Covenant and The Still Mind registered as independent cross-category Crowns, but all their effects lived in the ISS-only dispatcher. They were selectable and charged points without ISS. | Their IDs still register, but declare `requiredModId=irons_spellbooks`. Standard availability handling now rejects selection, explains the dependency and excludes dormant selections from Power Point charges. Saved slots remain removable and recover when ISS returns. |
| High | Folded Space's shared pearl/chorus handler unconditionally granted an ender pearl on a second teleport. Chorus-fruit pairs manufactured pearls; a full inventory silently lost legitimate refunds. | Only a pearl teleport receives a pearl refund. Chorus fruit can still prime a mobility window. Vanilla inventory-return handling drops overflow. Already-canceled teleport events are ignored, and handling runs at lowest priority. |
| High | `EntityJoinLevelEvent` was treated as a new projectile launch, including chunk loads and repeated joins. Phase Recoil could multiply saved velocity again; Ricochet Primer could repeat pierce gains. | Ignore disk joins and stamp processed launches in projectile NBT. Channel capture also ignores disk joins. Trueshot's launch-time target is now saved on the projectile, preserving valid aim through chunk loads without a process-wide entity-ID map. |
| High | Arcanist's Barrage had separate ISS and generic projectile counters. Spell projectiles could trigger both. The generic nested `victim.hurt` was also swallowed by the already-started native hurt cooldown on a real impact. | One cross-projectile counter owns Barrage. The echo's configured damage share joins the triggering impact, so mitigation, absorption and health loss happen once and the bonus cannot disappear into native immunity frames. The actual `LivingEntity.hurt` path has a regression test. |
| High | Crimson Tithe healed directly from `SpellDamageEvent`, before armor/absorption and independently of committed health damage or overkill. | Heal from positive blood-school `LivingDamageEvent` only, bounded by the victim's remaining health and excluding Runic secondary damage. |
| Medium | Volley Memory never consumed its setup history. Every rapid hit after the first payoff gained the bonus. | Consume the setup on payoff; another three qualifying hits are needed for the next fourth-hit bonus. Configured hit requirements are bounded by the tracked-history capacity. |
| Medium | Blade Storm removed its attack-speed modifier only during a five-second housekeeping sweep, retained it after perk disable, and reapplied identical modifiers on every qualifying hit. | Check expiry/eligibility on the player's tick, remove on state change, and retain unchanged modifiers without dirtying attributes. |
| Medium | Combat/Power histories described as per-life persisted in static maps across respawn/dimension transitions; utility absorption history could mistake a new body's zero absorption for a broken barrier. | Clear appropriate combat histories on respawn, dimension transition and logout, and clear utility/projectile maps on server stop. Cooldown debt is unchanged. |

## Progression and multiplayer trace

The ten skills register through `RegistrySkills`, are initialized to level 1 by
`SkillCapability`, and serialize under the existing `skill.<id>` keys. Server purchases
enter through `SkillLevelUpSP`: rate admission precedes scheduled work; the handler
validates live skill/global caps and the vanilla point balance. `ProgressionService`
owns command and purchase mutations, Forge/KubeJS vetoes, attribute reconciliation,
title evaluation, quest notification and capability synchronization. The new XP checks
repair arithmetic, without changing the currency, discount mechanics or default prices.

Passive adjustments enter through `AdjustPassiveSP` as one bounded signed adjustment.
The server walks prerequisite ranks, fires one cancellable event, applies the whole
change and reconciles once. Effective rank is now also checked when deriving attributes,
so purchase-time qualification is no longer the only enforcement point.

Perks retain their stored rank. `Perk.isEnabled`/active-value resolution supplies
disabled-content checks, governing-skill requirements and effective budget dormancy;
`TogglePerkSP` applies target-rank, swap cooldown, perk-group and global-budget checks.
The combat review followed these gates into outgoing damage, retaliation, critical
hits, defensive damage reduction, ranged impacts, timed modifiers and per-tick effects.
Existing secondary-damage contexts and finite damage guards were preserved.

All serverbound registrations specify their direction and derive player identity from
the connection. Content IDs, timer maps and configuration payloads are bounded. The
capability serializer retains unknown registry keys for removed optional content;
death clone copies earned progress and cooldown debt. The save schema and all existing
content IDs and attribute UUIDs remain unchanged in these repairs.

## Skill and stat routing

Every passive passes through the common allocation, effective-rank and transient
attribute reconciliation pipeline. Native attributes then feed vanilla/Forge gameplay;
Runic attributes require explicit consumers. Optional passive registrations remain
conditional on their owning integrations.

| Skill | Passive/stat routes inspected |
|---|---|
| Strength | Attack damage and knockback → native melee. Optional Apothic life steal and armor pierce → native provider attributes. |
| Constitution | Maximum health, knockback resistance and Forge swim speed → native health/movement. Optional Apothic healing received → provider heal handling. |
| Dexterity | Movement speed → native movement. Projectile damage → Runic arrow-impact modifier or Apothic arrow-damage attribute according to delegation. Optional draw speed/arrow velocity → Apothic. |
| Endurance | Armor/toughness → native damage mitigation. Optional Apothic dodge chance and Ars warding. Separate configured Endurance fire-resistance path remains in combat handling. |
| Intelligence | Attack speed and Forge entity reach → native attack cadence/reach. Optional ISS/Ars maximum mana and Apothic experience gain. |
| Building | Forge block reach and Runic break speed → interaction/mining; mining speed can delegate to Apothic, with inactive-provider modifier cleanup. |
| Wisdom | Enchanting power → enchanting-table paths; XP bonus → positive XP awards with fractional carry. Optional ISS cast-time reduction. |
| Magic | Beneficial-effect duration → effect application hooks; magic resistance → tagged damage mitigation. Optional ISS spell power, Ars spell damage and Apothic elemental damage. |
| Fortune | Native luck → luck-aware loot contexts. Critical damage → critical-hit handling or delegated Apothic critical damage; optional Apothic critical chance. |
| Tinkering | Repair efficiency → repair policies/anvil and native tool paths. Crafting luck → bounded eligible craft-reward probability. |

The nine custom attributes are all consumed: break speed, critical damage, projectile
damage, beneficial effect duration, magic resistance, enchanting power, XP bonus,
repair efficiency and crafting luck. The optional attribute providers and their UUID
migration remain in place. Disabling delegation reconciles the inactive provider so one
passive cannot occupy both attributes after reload.

## Powers: definition to trigger families

Every Power definition carries tier, governing skill, school/category, default cooldown
and icon. `PowerEligibility` applies the configured tier percentages, Seal secondary
gate, Crown total-skill gate, prerequisite chain, slots and point budget. Runtime
dispatch checks availability and eligibility again; config/rule reload and loss of a
required skill therefore affect gameplay without discarding the saved choice.

The per-ID index is in [CONTENT_TRACE.md](CONTENT_TRACE.md). The behavior routes reviewed
are:

| Family | Runtime pipeline and behavior |
|---|---|
| Projectile | `VanillaPowerEventDispatcher`: launch aim and resting pierce, qualifying target hits, consumed Volley setup, airborne damage, shared Barrage cycle. Works with ordinary and spell projectiles. |
| Channel | `ChannelPowerHandler`: continuous item-use history, interruption by health damage, saved projectile bonus/splash and capped Siphon healing. The Long Note uses ISS continuous casts and sustained spell-damage chaining. |
| Summon | `SummonPowerHandler`: owned-summon focus fire, committed damage bank stored on the summon, leash mitigation, death echoes/bursts, and Conductor target commands. ISS helpers supply native summon behavior where available. |
| Mobility | Vanilla pearl/chorus events and ISS spell teleports open recoil/strike windows, shed impairments, clear nearby target locks and feed Folded Space's cost/refund behavior. |
| Weapon/caster | `WeaponCasterPowerHandler`: melee/shot alternation, bow draw credit, shield-reflected ranged damage and cooldown refunds. Warmage's Covenant consumes the crit-to-spell window in ISS. |
| Utility | `UtilityPowerHandler`: allied effect sharing, expiring-buff cooldown refund, debuff-removal bank and barrier-loss protection. The Still Mind uses ISS cast-time HP/mana qualification and school-based spell debuffs. |
| Fire / Ice | Two ISS dispatchers route fire spell hits, immolate, field ownership, projectile block hits, freeze/chill, bear summon state and death/area effects. |
| Lightning / Holy | Native spell events, counters/tags, marked follow-up targets, timed mitigation and lightning/holy bonus paths. |
| Ender / Evocation | Teleports/counterspells, saved rewind debt/history, marked projectile echoes, summon/spell entity seams, shield panels and invisibility windows. |
| Nature / Blood / Eldritch | Effect tags, stationary history, debuff-based suppression, bounded health damage healing, health/mana costs, low-health kills, reveal/hold and resistance calculations. |
| Artifice and added integration families | Availability is supplied by the Tinkers', Tide, weapon and T.O. capability facades; the complete per-ID routes are indexed in CONTENT_TRACE and assessed in the integration/crafting reports. Core eligibility never makes those external capabilities mandatory for unrelated Powers. |

Current status metadata explicitly describes ten approximate Powers. These remain
implemented substitutions, rather than being silently represented as exact replicas
of the design document: Reforge the Shadow, Sacrifice Cascade, Counterspell Riposte,
Thunder Lord, Warmage's Covenant, Step Between, Folded Space, Herald of Dawn,
Forbidden Knowledge and Glacial Sovereign. The runtime provider gate now also exposes
the three cross-category Crowns whose actual implementation requires ISS.

## Compatibility and tuning

- Existing skill, passive, perk, Power and title IDs and player NBT schema are retained.
- Dormant passive ranks are stored exactly as before. They can be lowered normally or
  recovered by restoring their prerequisite skill/gates.
- Projectile NBT additions are optional and additive. Old saved projectiles do not
  replay launch bonuses when loaded; new projectiles preserve Trueshot aim.
- Barrage now shares one target/reset counter for ordinary and spell projectiles. Its
  default cross-projectile combat gap is 200 ticks. Existing ISS keys
  `echo_count_threshold`, `combat_timeout_ticks`, `echo_damage_multiplier` remain aliases;
  `projectiles_per_echo`, `combat_reset_ticks`, `echo_damage_share` take precedence when
  both are supplied. Pack authors who relied on the former ISS-only 600-tick default
  can explicitly set `combat_reset_ticks: 600`.
- Barrage's echo share is part of the triggering damage event. It receives that impact's
  mitigation and does not create a second native damage event or bypass hurt cooldowns.
- The three spell-only category Crowns remain saved when ISS is absent, cost no points
  while unavailable, and can be removed through the ordinary saved-slot interface.

## Validation and practical limits

Added `ProgressionCombatAuditGameTest`: nine base-profile cases cover passive dormancy
and NBT restoration, XP saturation/spending, the Volley setup/payoff cycle, actual
native Barrage health loss, projectile reload suppression, saved Trueshot aim,
chorus/pearl refund separation including full inventory, Blade Storm disable/respawn,
and spell-only Crown dependency/slot preservation. `SpellPowerGameTest` adds native
Crimson Tithe absorption/overkill checks and a dual-event spell-projectile Barrage
counter test. Its provider inventory now includes 45 school Powers plus three
spell-only category Crowns.

`ExperienceMathTest` adds exhaustive curve/inverse boundary checks through level 21,863,
large signed adjustments, saturation, invalid progress and overflowed cost inputs.
Build and profile execution are coordinated by the parent audit; final test totals and
results belong in the main release validation report.

Remaining in-game checks: multiplayer prediction/tooltips after skill loss and reload;
real arrows and native spell projectiles crossing unloaded chunks; combined armor,
absorption, rescue, Guard and external damage modifiers; actual bow/trident channel
release and sustained ISS casts; and optional integration combinations unavailable in
the build matrix. Forge pre-teleport and damage events do not guarantee they are the
last possible mod subscriber: a later subscriber at the same priority can still
change/cancel the transaction. The changes improve the existing event boundaries;
they do not claim a general commit API for arbitrary third-party mods.

Two design approximations remain worth future work: ranged distance perks measure
shooter-to-impact distance rather than a launch-position snapshot, and selected school
Powers intentionally use damage/effect substitutions already labeled in status metadata.
Neither was rewritten as part of this stability pass.

## Final reload-boundary review

The inherited Minecraft `SimpleJsonResourceReloadListener.prepare` skipped syntax-invalid
files before the existing schema validators could see them. That made an apparently
successful reload silently remove a perk exclusion, Tinkers rule, or skill visual when
its JSON developed a syntax error. All three loaders now share
`AtomicJsonReloadListener`: preparation either supplies the complete directory or logs
the failing resource and retains the previous snapshot. Missing files still clear on a
successful reload, and resource namespaces, nested paths, and normal pack stacking are
preserved. Existing `apply(Map)` and Tinkers `parse(Map)` schema entry points remain.

The preparation reader consumes at most the per-file limit plus one detection byte,
closes the stream, and rejects oversized input before parsing. Perk groups and Tinkers
rules permit 256 KiB per UTF-8 document; visuals permit 64 KiB. File-count limits are
4,096 groups, 2,048 Tinkers rule files, and 128 visual files. Syntax/read failures, empty
or null JSON documents, and excess parser nesting retain the snapshot without aborting
the global server reload. The existing integration-rules custom preparation and
recycling's intentional per-file skip policy are unchanged.

`AtomicJsonReloadGameTest` adds three full `PreparableReloadListener.reload` tests using
in-memory `ResourceManager` resources and actual input streams. They verify retention
for mixed valid/broken candidates in every loader, strict consumption of an endless
whitespace stream at the byte bound, intentional empty-directory replacement, nested
resource-ID mapping, empty/null/trailing JSON rejection, and I/O failure retention.
These tests complement the existing already-parsed schema and packet regressions.
