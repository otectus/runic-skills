# Runic Skills × Iron's Spells 'n Spellbooks — Powers System Design Specification

## 1. Executive Summary

**Powers are conditional, triggered, state-based abilities — not stat bonuses.** They are the missing middle layer between Runic Skills' existing **Perks** (passive `minecraft:attribute` modifiers applied at skill thresholds) and actively-cast Iron's Spells. Every Power listed in this document activates **for free (zero mana cost)** in response to a gameplay condition — a spell cast finishing, a specific damage type landing, a status effect ticking, a threshold of HP/mana being crossed, a summon dying, a teleport resolving. They reinforce school identity by making the act of *casting X* produce an interesting *secondary Y*, without a second mana bill.

**Tier structure and selection limits.** Three tiers, with deliberately constrained selection budgets so that a character concept is defined by its Powers rather than drowning in them:

| Tier | Name | Per-character limit | Design weight |
|---|---|---|---|
| T1 | **Runic Marks** (Minor Powers) | 5 | Early-game, low-impact conditional effects. One line of flavor per cast. |
| T2 | **Runic Seals** (Major Powers) | 3 | Mid-game, build-defining. Reshape a kit's tempo or combo loop. |
| T3 | **Runic Crowns** (Keystone Powers) | 1 | Capstone. Identity-defining. Sometimes comes with a drawback. |

Total: **9 Powers equipped per character** out of a pool of **6 per school × 9 schools + 6 per cross-cutting category × 6 categories = 90 Powers** in the v1 design.

**Design philosophy, non-negotiable:**

1. **No mana cost to trigger.** Powers are reactive; the mana bill was already paid when the player cast the triggering spell.
2. **No generic stat buffs.** Nothing that reads "+10% fire spell power" or "-15% cooldown" is a Power — those belong in the existing Perk tree.
3. **Every Power must consume a condition** that is legible to the player in combat (a school, a status effect, an action, a threshold).
4. **School identity is sacred.** Fire Powers must *feel* like fire; Eldritch Powers must *feel* forbidden. No reskins across schools.
5. **Every Power is implementable today** using the concrete Iron's Spells API (`SpellOnCastEvent`, `SpellOnHitEvent`, `ChangeManaEvent`, `SpellTeleportEvent`, `MagicData`, `SchoolRegistry`, `MobEffect` registry), standard Forge/NeoForge events (`LivingDamageEvent`, `LivingHurtEvent`, `LivingDeathEvent`, `PlayerTickEvent`), and the Runic Skills attachment (`PlayerSkills`, `PlayerPowers`).

**UI treatment.** Powers sit in a dedicated **Powers panel** adjacent to the Perks tree — different chrome (runestone slab aesthetic vs. Perks' parchment), an always-visible counter ("2/5 Marks, 1/3 Seals, 0/1 Crown"), a glowing rune border animation when a Power fires in combat, and a floating rune icon above the player's hotbar for the 0.5s a Power procs. This is the primary signal that keeps Powers **readable**.

---

## 2. Iron's Spells 'n Spellbooks — Mechanical Analysis

### 2.1 Spell system architecture (what we integrate with)

Iron's Spells organizes every spell as a subclass of `AbstractSpell` in package `io.redspace.ironsspellbooks.api.spells`. Each spell declares: a `CastType` (`INSTANT`, `LONG`, `CONTINUOUS`, `CHARGE`, `NONE`), a `SchoolType` drawn from `SchoolRegistry`, a `SpellRarity` (Common → Legendary), a mana cost curve, a cooldown, and a `DefaultConfig`. Spells are invoked through any `ISpellContainer` (spellbook, scroll, `MagicSwordItem`, `StaffItem`, or any curio tagged `irons_spellbooks:spell_imbued_curio`). Per-player magic state lives in `MagicData`, retrieved via `MagicData.getPlayerMagicData(LivingEntity)`; this object tracks `getMana()`, `getPlayerCooldowns()`, `getPlayerRecasts()`, the currently casting spell, and the active `ICastDataSerializable`. Mana persists in a Forge Capability (1.20.1) or NeoForge data attachment (1.21). Summons go through `SummonManager` since v3.14.0 — a second cast of a summon spell unsummons the previous batch, so Powers that care about "your summons" should read the active `SummonedEntitiesCastData`, not scan the world.

### 2.2 The nine schools and their identities

| School | Focus item | Combat fantasy | Dominant mechanic | Best Power design space |
|---|---|---|---|---|
| **Fire** | Blaze Rod | Burn, spread, lingering fields | `Ignited` DoT + zone denial (Wall of Fire, Magma Bomb, Scorch, Heat Surge's Rended armor shred) | Ignition propagation, fire-field reactions, armor-shred triggers |
| **Ice** | Frozen Bone | Chill → Freeze → Shatter | Stacking `Chilled` into full freeze; Ice Shadows as decoys | Shatter bursts, frozen-kill conversions, decoy plays |
| **Lightning** | Bottle o' Lightning | Burst and chain | Highest spike damage; `Charged` self-buff; chain jumps; vertical mobility | Chain arc triggers, airborne state rewards, on-Charged procs |
| **Holy** | Divine Pearl | Support + smite | Heal/Fortify/Haste + `Guided` homing tag; bonus to undead | Ally-adjacency procs, undead-slayer triggers, post-heal follow-ups |
| **Ender** | Ender Pearl | Arcane toolkit, displacement | Teleport, Counterspell, Echoing Strikes, Black Hole | Post-teleport windows, counterspell procs, echo/delay mechanics |
| **Blood** | Blood Vial | HP conversion, necromancy | Lifesteal (100% on Ray of Siphoning), Sacrifice detonates summons, Heartstop defers damage | Below-threshold triggers, summon-sacrifice procs, lifesteal cascades |
| **Evocation** | Emerald | Conjuration, trickery | Vex summons, fangs, creeper chains, shield, invisibility | On-summon procs, invisibility breaks, conjured-object echoes |
| **Nature** | Poisonous Potato | Attrition, debuff stacking | Poison, Acid armor-shred, Root, Oakskin, Blight | Stacked-debuff triggers, poison-on-kill spread, defensive-stance swaps |
| **Eldritch** | Echo Shard | Forbidden, reality-warping | Legendary-only, high mana cost, kinetic/sonic/blindness | Rare-but-huge procs, sanity/knowledge mechanics, wall-piercing effects |

### 2.3 Patterns in the mod we can lean on

Iron's already ships a handful of perk-like mechanisms that serve as templates. **Affinity Rings** fire `ModifySpellLevelEvent` to grant +1 effective level to one spell — exactly the pattern "a passive modifying a specific cast." **Infernal Sorcerer Chestplate** uses `ServerPlayerEvents.onBeforeDamageTaken` to push `ImmolateEffect` stacks onto attackers — exactly the pattern "reactive retaliation." **Echoing Strikes** schedules delayed damage echoes — exactly the pattern "this action produces a secondary event on a timer." Every Power in this document emulates one of those three archetypes (event modifier, reactive retaliation, or delayed echo) because those patterns are known to work and the mod's networking/rendering already supports them.

### 2.4 API surface we will use (confirmed)

- **Events (game bus, `NeoForge.EVENT_BUS` / `MinecraftForge.EVENT_BUS`):**
  - `SpellPreCastEvent` (cancelable) — veto casts.
  - `SpellOnCastEvent` — react on successful cast. Read `getSpell().getSchoolType()`, `getCastSource()`.
  - `SpellOnHitEvent` / `SpellDamageEvent` — react to spell impact; modify damage.
  - `ChangeManaEvent` — observe mana gain/loss.
  - `SpellTeleportEvent` — fires for every spell-driven teleport (Teleport, Blink, Blood Step, Frost Step, Evasion, Abyssal Shroud's dodge).
  - `ModifySpellLevelEvent` — cleanest way to say "this Power makes your X spells cast as if one level higher."
  - `SpellSelectionManager.SpellSelectionEvent` — inject a spell into the cast wheel without giving the player an item.
- **Vanilla events we pair with:** `LivingDamageEvent`, `LivingHurtEvent`, `LivingDeathEvent`, `MobEffectEvent.Added`, `MobEffectEvent.Expired`, `PlayerTickEvent`, `EntityJoinLevelEvent` (for detecting summons via `Owner` field).
- **Reads:** `SchoolRegistry.getSchool(ResourceLocation)`; `MagicData.getPlayerMagicData(e).getPlayerCooldowns().getCooldownPercent(spellId)`; active `MobEffect`s via `LivingEntity.getActiveEffects()`; damage source via `((SpellDamageSource) src).getSchool()` when applicable.
- **Writes:** `MobEffectInstance` application, `AttributeModifier` add/remove on the Runic Skills attachment-scoped `AttributeMap`, `SummonManager.initSummon`, `player.teleportTo`, `level.explode`, `level.addFreshEntity`.
- **Known gaps:** no client-side cast event (issue #955) — any client-only VFX must be sent via a `CustomPacketPayload`. `SpellOnCastEvent.cancel()` is a no-op (issue #388) — use `SpellPreCastEvent` for veto.

---

## 3. Powers System Framework

### 3.1 Tier structure & selection limits

- **Runic Marks (T1 / Minor):** 5 equipped. Each costs 1 Power Point. Unlocked at **any skill ≥ 30** in the Power's governing skill (Magery for spellcaster schools; Tactics/Anatomy for Weapon-Caster Hybrid; Animal Taming for Summon Powers; Meditation for Utility/Buff). No prerequisite Powers.
- **Runic Seals (T2 / Major):** 3 equipped. Each costs 2 Power Points. Unlocked at **governing skill ≥ 60 AND Evaluating Intelligence ≥ 40** (UO "Eval Int" gates power magnitude). Requires one Mark of the same school already slotted.
- **Runic Crowns (T3 / Keystone):** 1 equipped. Costs 3 Power Points. Unlocked at **governing skill ≥ 90 AND total-skill ≥ 500** (assuming a 700 cap). Requires one Seal of the same school already slotted, OR, for cross-cutting Crowns, one Seal of the matching category.

Power Points scale 1:1 with character level (assuming Runic Skills has a level or equivalent milestone — if it doesn't, grant +1 PP per 50 total skill, for a budget of 14 PP at the 700 cap, consumed by 5 Marks (5) + 3 Seals (6) + 1 Crown (3) = 14 exactly).

### 3.2 UI / indication treatment

Powers are visually distinct from Perks in four ways:

1. **Panel chrome** — the Powers panel uses a hexagonal runestone slab motif, rendered via `GuiGraphics.blitSprite` against a granite texture; Perks use the existing parchment.
2. **Proc feedback** — when any Power fires, a server→client `PowerProcPacket(powerId, sourcePos)` spawns a short-lived orbiting rune particle above the triggering entity (using `ParticleTypes.ENCHANT` recolored to the school's registered color from `SchoolType.displayName.getStyle().getColor()`) and emits a per-school sound from `SchoolType.getDefaultCastSound()` at 0.4 volume. The ISS HUD mod's overlay pattern is a good reference.
3. **Tooltip color coding** — Mark tooltips have a grey border, Seal tooltips gold, Crown tooltips purple (matching the tier), plus a second border tinted to school color.
4. **Always-on counter** — top-left of the screen when the Powers panel is open: "Marks 3/5 · Seals 2/3 · Crown 1/1".

### 3.3 Activation model

Every Power is **passive in install, reactive in trigger**. There is no bind-to-key activation, no button press. The trigger fires when the condition the Power specifies is met. Crown-tier Powers may have an internal cooldown (ICD) stored in the `PlayerPowers` attachment as `Map<ResourceLocation, Long>` of `powerId → gameTimeAvailable`. Marks and Seals generally do not have ICDs, but procs that scale with damage have a soft cap (per-tick cap or per-second cap).

### 3.4 Interaction with the 21 skills and 6 stats

Each school maps to a **governing skill**. Under the best inference of the Runic Skills / UO mapping:

| School(s) | Primary governing skill | Secondary (unlocks Seals) |
|---|---|---|
| All spellcaster schools | **Magery** | **Evaluating Intelligence** |
| Holy (healing half) | **Healing** also qualifies | Spirit Speak |
| Blood / Eldritch | **Spirit Speak** (necromancy/occult lens) | Magery |
| Nature | **Alchemy** co-governs (poisons) | Magery |
| Weapon-Caster Hybrid | **Tactics + Anatomy** | Magery |
| Summon cross-cutting | **Animal Taming** | Spirit Speak |
| Mobility cross-cutting | **Stealth** co-governs | Magery |
| Utility/Buff cross-cutting | **Meditation** | Magery |
| Channel/Beam | **Magery + Focus** (if present; else Meditation) | Eval Int |

The **6 core stats** (Str, Dex, Int, Con, Wis, Cha — best inference) scale Power magnitudes through attribute reads rather than new attributes:

- **Intelligence** scales Power magnitudes that produce damage/healing/mana.
- **Wisdom** scales proc chances and ICD reductions.
- **Dexterity** scales mobility/teleport Powers' distances and windows.
- **Constitution** scales defensive triggers (threshold HP%, barrier HP).
- **Strength** scales Weapon-Caster Hybrid Powers.
- **Charisma** scales Summon Powers (count bonus, summon damage window).

### 3.5 Gating

```
require_skill: { magery: 30 }          # Mark
require_skill: { magery: 60, eval_int: 40 }     # Seal
require_skill: { magery: 90 }, require_total_skill: 500   # Crown
require_power: runic_skills:fire/mark_01        # chained prerequisite
```

All gates are JSON in `data/runic_skills/powers/<id>.json`, codec-driven, reload-safe.

---

## 4. Proposed Powers — By Spell School

Every Power below specifies Name, Tier, Intent, Trigger, Effect, Why It Fits, Implementation Notes, and Balance Notes. To keep this document usable as a spec, implementation notes reference exact event classes and API calls.

### 4.1 Fire

#### Fire — Marks (T1)

**Ember Trail** — *Mark.* **Intent:** reward movement while burning things. **Trigger:** after you successfully land any `SchoolRegistry.FIRE` spell, for the next 3 seconds your footfalls leave a brief flame particle trail that ignites any non-player entity that touches it. **Effect:** entities walking the trail gain 3 seconds of `Ignited` (1 stack). **Why it fits:** Fire's identity is *spread* — this takes the "one thing is on fire, now everything is" fantasy into movement. **Implementation:** subscribe to `SpellOnCastEvent`; on cast-hit, write a timestamp `fireTrailUntil = level.getGameTime() + 60` on the attachment; in `PlayerTickEvent.Post`, if `level.getGameTime() < fireTrailUntil && player.getDeltaMovement().horizontalDistanceSqr() > 0.01`, spawn a small AABB proxy entity (1-tick lifetime, 0.8-block radius) that on collision applies `MobEffectRegistry.IGNITED`. **Balance:** trail does not damage the caster or allies (tag check `#runic_skills:allies_of` + owner UUID); ignition duration is fixed low so stacking doesn't scale with spell level.

**Kindle** — *Mark.* **Intent:** give Firebolt a combo reward. **Trigger:** when your Firebolt or Blaze Storm damage applies `Ignited` to a target that was not `Ignited` the previous tick. **Effect:** the next fire-school damage instance this target receives within 4 seconds deals +20% damage (flat-added, not multiplicative). **Why it fits:** Fire's ignite→reignite setup is the main combo loop; this pays out the first hit explicitly. **Implementation:** `SpellOnHitEvent` filtered to fire-school sources; check target's `getActiveEffect(IGNITED)` null before, set an NBT attachment marker `kindleReadyUntil` on the *target* entity; next `SpellDamageEvent` on that target reads the marker and applies `event.setDamage(event.getDamage() * 1.2)`. **Balance:** per-target, single-consume; does not stack with itself.

#### Fire — Seals (T2)

**Heat Haze** — *Seal.* **Intent:** Fire caster's self-peel. **Trigger:** when a melee attack deals physical damage to you and you have `Ignited` on at least one entity within 8 blocks. **Effect:** project a 2-block radius pulse from you that deals 4 fire damage, applies 2s `Ignited`, and knocks back attackers. 8-second ICD. **Why it fits:** Fire mages need close-range denial; this reuses the "you're lit, I'm lit, we all get lit" fantasy. **Implementation:** `LivingHurtEvent` where `event.getSource().getDirectEntity() instanceof LivingEntity attacker` and `level.getEntitiesOfClass(LivingEntity, aabb(8)).stream().anyMatch(e -> e.hasEffect(IGNITED))`; call `level.explode(null, pos, 0f, ExplosionInteraction.NONE)` with fire damage custom, or manually apply to entities in AABB. **Balance:** ICD prevents spam under fire-DoT tick storms; physical-damage-only filter prevents chain procs with fire-retaliation.

**Scorched Earth** — *Seal.* **Intent:** lingering-field mage identity. **Trigger:** whenever you cast Wall of Fire, Magma Bomb, Scorch, or Heat Surge. **Effect:** that field's lifetime is extended by 40%, and entities inside it have their armor reduced by 15% (stacks separately from Heat Surge's Rended). **Why it fits:** doubles down on zone denial — *you* become the zone-control specialist. **Implementation:** `SpellOnCastEvent` with `ResourceLocation` match set; for Wall of Fire, tap into the wall entity's `tickCount` limit via a data attachment applied on entity join (`EntityJoinLevelEvent` filter by owner); for Magma Bomb/Scorch fire fields, apply a debuff `MobEffectRegistry.ARMOR_BURN` (register a new MobEffect in Runic Skills) to entities on intersection with the fields, detected via `PlayerTickEvent` scanning owned field entities. **Balance:** armor shred is capped at −15% and does not stack with itself; only owned fields are affected.

#### Fire — Crown (T3)

**Pyroclasm** — *Crown.* **Intent:** define a build around stacking `Ignited` across the battlefield. **Trigger:** when any entity with `Ignited` dies within 16 blocks of you. **Effect:** detonate the corpse for 6 fire damage in a 3-block radius, applying 4s `Ignited` to everything hit — including other entities with `Ignited`, which can chain-proc Pyroclasm if they die from the detonation. Caps at 6 detonations in any 2-second window. **Why it fits:** this is the Chain Creeper fantasy applied to *kills you didn't even see happen*. **Implementation:** `LivingDeathEvent` with owner-UUID-distance check and `IGNITED` check; apply damage through a `SpellDamageSource` of school FIRE (reuse mod's `DamageSources.getFireSpellDamage(owner)`); track a rolling counter `detonationsThisSecond` in the attachment. **Balance:** cap prevents chain-collapse in horde fights; self-immune check; allies immune via tag.

### 4.2 Ice

#### Ice — Marks (T1)

**Brittle** — *Mark.* **Intent:** reward setup. **Trigger:** when you damage a target with any physical or spell damage while that target is `Chilled` (not yet full-frozen). **Effect:** that hit deals +15% damage. **Why it fits:** Chilled → Frozen → Shatter is Ice's identity; this rewards engaging the chilled state before freeze. **Implementation:** `LivingDamageEvent` with target.hasEffect(CHILLED) and !target.hasEffect(FROZEN); `event.setAmount(event.getAmount() * 1.15f)`. **Balance:** one multiplier only; does not stack with Shatter.

**Frost Echo** — *Mark.* **Intent:** give Icicle/Ray of Frost a lingering bite. **Trigger:** when an Ice-school projectile you fired hits terrain (not an entity). **Effect:** a 1-block radius ice patch remains for 3 seconds; entities stepping on it gain 1 stack of `Chilled`. **Why it fits:** Ice as a **terrain shaper** (cf. Snowball field). **Implementation:** `ProjectileImpactEvent` with owner check and `BlockHitResult`; spawn a tiny custom `IceEchoEntity` with the effect. **Balance:** one patch per second per player; patches don't overlap in effect.

#### Ice — Seals (T2)

**Shatter** — *Seal.* **Intent:** pay out the Freeze state. **Trigger:** when a fully-frozen entity takes damage from you. **Effect:** the freeze ends immediately, dealing bonus cold damage equal to 30% of the missing HP of the entity (capped at 40 damage, capped at 15 vs players). **Why it fits:** *every* Ice player fantasizes about shattering frozen things; currently the base mod only has Frostbite's shatter dmg tied to kills. **Implementation:** `LivingDamageEvent` with `target.hasEffect(FROZEN)` and `event.getSource().getEntity() == player`; calculate bonus and `event.setAmount(event.getAmount() + bonus)`; then `target.removeEffect(FROZEN)`. **Balance:** PvP cap critical to prevent one-shots; missing-HP formula rewards burst but doesn't oneshot tanks.

**Reforge the Shadow** — *Seal.* **Intent:** build around Ice Shadows. **Trigger:** when you cast Frost Step or when a Frostbite-induced Ice Shadow spawns. **Effect:** that Ice Shadow has +40% HP and, on death, emits a 3-block Chilling burst (2s Chilled to all enemies). **Why it fits:** Ice Shadows are criminally underused; this makes decoys genuinely meaningful. **Implementation:** `EntityJoinLevelEvent` filter for `FrozenHumanoid` with owner UUID; modify `getAttribute(MAX_HEALTH)` via a dedicated `AttributeModifier`; on `LivingDeathEvent` of that entity, apply `CHILLED` in AABB. **Balance:** one shadow's burst doesn't chain into another's; death-burst can't kill.

#### Ice — Crown (T3)

**Glacial Sovereign** — *Crown.* **Intent:** total CC archetype. **Trigger:** you have cast at least 3 Ice-school spells in the last 15 seconds. **Effect:** every enemy within 12 blocks has their Chilled accumulation rate doubled, and any full freeze on any entity within 12 blocks lasts 30% longer. Additionally, the first time per combat an enemy would die while Frozen, they instead are entombed in an Ice Tomb for 4 seconds and drop their target to you (not a revive — just a buffer that resets aggro). **Why it fits:** Ice = the control queen; this makes the caster a literal center-of-gravity for frost. **Implementation:** per-player sliding-window cast counter; active state injected as a data attachment flag; tick-scan nearby entities and extend their `CHILLED` / `FROZEN` duration each tick (`effect.update(effect.getDuration()+2)` every 40 ticks while eligible). Entombment via `LivingDeathEvent` with `target.hasEffect(FROZEN)`; cancel the event once per kill, apply invuln tag + visual ice, then re-kill after 4s. **Balance:** the death-denial is gated to once per combat (combat-timer attachment) and the entombed entity cannot take damage during it, preventing double-hit exploits.

### 4.3 Lightning

#### Lightning — Marks (T1)

**Static Cling** — *Mark.* **Intent:** reward chaining. **Trigger:** when Chain Lightning or Ball Lightning's second-or-later target hit happens. **Effect:** that target becomes `Shocked` for 4 seconds. **Why it fits:** chain spread is Lightning's signature; this makes the chain do *more*. **Implementation:** Chain Lightning fires one `SpellOnHitEvent` per jump (verify by logging); add a counter on the MagicData sync packet to know jump index, or use a `WeakHashMap<LivingEntity, Integer>` keyed by the chain's cast ID. If single-cast-scope can't be inferred, simpler fallback: any `SpellOnHitEvent` from `LIGHTNING` where the target is within a chain-eligible range triggers, with 1-second ICD per target. **Balance:** `Shocked` already exists as a debuff; no new effect needed.

**Crackle Arc** — *Mark.* **Intent:** give Ball Lightning a melee-range reward. **Trigger:** when you melee-attack an enemy that is `Charged` (you have the Charge buff). **Effect:** your melee attack chains a small bolt to the nearest other enemy within 4 blocks for 4 lightning damage. **Why it fits:** Lightning has Charge as the melee-mage buff; this makes the combo legible. **Implementation:** `LivingHurtEvent` if `player.hasEffect(CHARGED)` and direct-entity attacker; raycast `level.getNearestEntity(LivingEntity.class, ...)`; apply damage with `DamageSources.getLightningSpellDamage(player)`. **Balance:** can't chain to the same target, per-tick cap.

#### Lightning — Seals (T2)

**Skybreaker** — *Seal.* **Intent:** airborne playstyle. **Trigger:** you cast any Lightning-school damage spell while airborne (`!player.onGround() && player.getDeltaMovement().y != 0`) or within 2 seconds of using Ascension. **Effect:** that spell's damage is +25% and, on kill, resets Ascension's cooldown. **Why it fits:** Ascension is underused because after you jump there's nothing strong to do in the air — this fixes the loop. **Implementation:** `SpellOnCastEvent` airborne check, buffer a "nextSpellBoost" flag; next `SpellDamageEvent` from that player applies +25%; on `LivingDeathEvent` with the same caster, call `magicData.getPlayerCooldowns().removeCooldown(AscensionSpell.ID)`. **Balance:** once per cast; does not stack with on-crit bonuses.

**Conduit Mark** — *Seal.* **Intent:** set up burst. **Trigger:** when Chain Lightning hits a target. **Effect:** the first target becomes a "Conduit" for 5 seconds; the next Lightning Bolt / Lightning Lance / Ball Lightning hit on them detonates the conduit, dealing 6 lightning damage to all entities within 5 blocks of the conduit. **Why it fits:** mark-and-detonate pattern applied to Lightning's natural setup spell. **Implementation:** MobEffect `runic_skills:conduit_mark` applied on Chain Lightning hit (detect via `SpellOnHitEvent` with spellId match); subsequent `SpellOnHitEvent` matching the three detonators checks for effect presence, removes it, and emits the AoE. **Balance:** one conduit per target; detonation damage flat, does not scale with spell power beyond caster's intelligence.

#### Lightning — Crown (T3)

**Thunder Lord** — *Crown.* **Intent:** become the storm. **Trigger:** you have dealt lightning damage in the last 4 seconds. **Effect:** every 2 seconds while this state is active, a weak (3 damage) lightning bolt strikes the nearest hostile enemy within 10 blocks. Additionally, your Lightning Bolt, Lightning Lance, and Thunderstorm have 20% reduced cooldown. **Why it fits:** Thunderstorm already exists as a spell; Thunder Lord makes *you* a mini-Thunderstorm that tags along with every fight. **Implementation:** `SpellOnHitEvent` with lightning school sets `lightningWindowUntil`; `PlayerTickEvent.Post` every 40 ticks while in window calls `LightningBolt.create(level)` with reduced damage (see Iron's existing `LightningBoltEffect`); cooldown reduction via `ModifySpellLevelEvent`-adjacent `ChangeManaEvent`-style cooldown hook (in practice: listen to `SpellPreCastEvent`, if spellId matches, apply a one-shot `magicData.getPlayerCooldowns().decreaseCooldown(0.2f)` after cast). **Balance:** the auto-strike is weak (3 dmg) so it doesn't replace actual casting; cooldown reduction is flat-20%, does not stack multiplicatively with CDR attribute.

### 4.4 Holy

#### Holy — Marks (T1)

**Sanctified Strike** — *Mark.* **Intent:** undead-slayer identity. **Trigger:** you deal holy damage to an entity tagged `minecraft:undead` (or the mod's equivalent). **Effect:** that hit deals +25% damage. **Why it fits:** D&D Cleric/Paladin staple; matches the mod's existing undead bonus to holy sources. **Implementation:** `SpellDamageEvent` with `school == HOLY` and `target.getType().is(EntityTypeTags.UNDEAD)`; scale amount. **Balance:** stacks additively with the mod's innate undead bonus to avoid double-multiplier blowup.

**Fortifying Bond** — *Mark.* **Intent:** reward party-play. **Trigger:** you cast Heal, Greater Heal, Blessing of Life, or Healing Circle on an ally (not self). **Effect:** you gain 3 seconds of 20% damage reduction. **Why it fits:** healers in Iron's Spells eat damage; this is the "selfless healer gets a guardian angel" pattern. **Implementation:** `SpellOnCastEvent` with spellId match and target-entity-not-self check (for Heal, target is self, so exclude that); apply a short `MobEffectRegistry.FORTIFY` (existing) at 1 amplifier for 60 ticks. **Balance:** once every 5 seconds via ICD; self-heal Heal/Greater Heal don't trigger it (Fortify is the self-peel; this rewards altruism).

#### Holy — Seals (T2)

**Guided Fate** — *Seal.* **Intent:** turn the Guided effect into a proper build-around. **Trigger:** when any projectile (not just holy) hits a target with the `Guided` effect. **Effect:** the projectile's damage is +20% and its cooldown is refunded by 20% on the caster. **Why it fits:** Guided is a brilliant but under-leveraged mechanic — this makes marking a priority target *worth* it for any follow-up. **Implementation:** `SpellOnHitEvent` and `LivingHurtEvent` with projectile source; check `target.hasEffect(GUIDED)`; scale damage; `magicData.getPlayerCooldowns().decreaseCooldown(lastCastSpellId, 0.2f)`. **Balance:** Guided is on a 25s duration so it's a planned-combo window, not a spam-and-pray.

**Wings of Judgment** — *Seal.* **Intent:** air-support archetype. **Trigger:** while you have Angel Wing's flight buff active, every time you cast Sunbeam or Guiding Bolt. **Effect:** that spell deals +15% damage and briefly (1s) applies Slowness IV to hit enemies. **Why it fits:** Angel Wing is cool but has nothing to do in the air except fly — this gives the flight state a combat loop. **Implementation:** check `player.hasEffect(ANGEL_WINGS)` on `SpellOnCastEvent`; buffer the bonus; `SpellDamageEvent` applies damage; `SpellOnHitEvent` applies the slow. **Balance:** Angel Wing's short duration + long cooldown naturally gates this.

#### Holy — Crown (T3)

**Herald of Dawn** — *Crown.* **Intent:** fully-committed support identity. **Trigger:** when any ally within 16 blocks drops below 30% HP. **Effect:** emit a burst of holy light that heals all allies (including self) within 12 blocks for 8 HP, applies 8s `Fortify` (4 temp HP), and deals 10 holy damage to all hostile entities in that radius. 30-second ICD. **Why it fits:** "the cleric stabilizes the party at the worst moment" — pure support fantasy finally made automatic. **Implementation:** `LivingDamageEvent` and `LivingHurtEvent.Post` tick-scan of nearby allies (attachment flag `alliedTeamUUID` read from a simple "grouped players" system; fallback: any non-hostile entity). On trigger apply `HealEffect` / `FORTIFY` / `holy_spell_damage`. **Balance:** 30s ICD prevents chain-healing during sustained DoTs; healing flat to prevent scaling abuse.

### 4.5 Ender

#### Ender — Marks (T1)

**Step Between** — *Mark.* **Intent:** reward skillful teleporting. **Trigger:** you resolve a `SpellTeleportEvent` (Teleport, Blink, Blood Step, Frost Step, Evasion-dodge). **Effect:** for 1.5 seconds after, your next non-channel spell has its cast time halved. **Why it fits:** Ender is the "displacement mage" — translating the teleport into a faster follow-up makes each blink a decision about what comes next. **Implementation:** `SpellTeleportEvent` sets `postTeleportUntil` timestamp; `SpellPreCastEvent` reads it and if active, overrides cast time via `magicData.setCastDurationRemaining(duration/2)` (or similar — confirm the exact mutator on `MagicData`). **Balance:** affects only the very next cast; no stacking.

**Arcane Echo** — *Mark.* **Intent:** reward Magic Missile / Magic Arrow spam. **Trigger:** when Magic Missile or Magic Arrow hits an entity. **Effect:** 25% chance to fire a phantom copy of the projectile from your position, dealing 40% damage. **Why it fits:** arcane-missile fantasy. **Implementation:** `SpellOnHitEvent` spellId match → RNG roll → spawn the same projectile entity type with halved config damage; mark as echo in data to prevent self-triggering recursion. **Balance:** echoes cannot trigger Arcane Echo; per-second cap of 3.

#### Ender — Seals (T2)

**Counterspell Riposte** — *Seal.* **Intent:** make anti-caster play rewarding. **Trigger:** Counterspell successfully interrupts an enemy's cast OR banishes a summoned object. **Effect:** refund 50% of Counterspell's cooldown and grant you 4 seconds of +20% damage vs that specific entity. **Why it fits:** Counterspell is the mod's only anti-cast tool; this makes it a build centerpiece. **Implementation:** subscribe to `SpellPreCastEvent` on *other* entities (to detect interrupts by hooking into Counterspell's own effect call; easier: listen to `SpellOnCastEvent` with `spellId == Counterspell` and scan `level.getEntitiesOfClass` for entities whose `magicData.isCasting()` just became false). Apply a "marked" data attachment on the target entity; `SpellDamageEvent` reads the mark. **Balance:** per-target, 4s window; refund does not stack.

**Black Hole Resonance** — *Seal.* **Intent:** synergy with the mod's premier CC. **Trigger:** you have cast Black Hole in the last 8 seconds. **Effect:** all your other spells gain 10% increased damage vs entities currently being pulled, and on kill-within-Black-Hole the Black Hole's duration is extended by 1 second up to a +5s cap. **Why it fits:** Black Hole is a commitment spell; this rewards building the rest of your kit around it. **Implementation:** Black Hole spawns a known entity class; detect it via a registered type check; mark all entities within its pull radius with a short-duration "pulled" effect each tick; `SpellDamageEvent` scaling; `LivingDeathEvent` for duration extension via setting the entity's `tickCount` backward. **Balance:** boss pull resist already limits Black Hole; this does not bypass.

#### Ender — Crown (T3)

**Unraveled** — *Crown.* **Intent:** the "time-and-space mage" archetype. **Trigger:** you take lethal damage (damage that would bring you to 0 HP). **Effect:** cancel the death, teleport you back to the location you occupied 3 seconds ago, restore 50% of your mana and 30% of your HP, and cleanse all debuffs. 10-minute real-time ICD (not combat-ICD — this is a panic button). **Why it fits:** Ender is displacement; this is the ultimate displacement — in time. **Implementation:** a ring buffer on the player's attachment `lastPositions: Deque<(pos, tick)>` capped at 60 entries, pushed in `PlayerTickEvent.Post`. `LivingDeathEvent` (cancelable in Forge) cancels, teleports (`player.teleportTo`), restores mana, calls `player.removeAllEffects()`. ICD stored in attachment as absolute game time. **Balance:** 10-minute ICD is long enough to prevent chain-cheese; the position rollback can be exploited into unreachable spots, so clamp destination to a `level.clip(NOT_WATER)` sanity check and fall back to current position if blocked; PvP servers may want to disable via config tag.

### 4.6 Evocation

#### Evocation — Marks (T1)

**Fang Follow-Through** — *Mark.* **Intent:** tie Fang Strike into melee. **Trigger:** you cast Fang Strike or Fang Ward, then make a melee attack within 3 seconds. **Effect:** that melee attack deals +30% damage and applies a 1-second root to its target. **Why it fits:** Evocation's fangs are ground-based; this extends them to your hand. **Implementation:** `SpellOnCastEvent` sets `fangFollowUntil`; `LivingHurtEvent` with direct-entity attacker checks window, scales damage, applies `ROOT` (existing MobEffect). **Balance:** one attack per cast; root is brief to prevent chain-CC with real Root spell.

**Vex Taunt** — *Mark.* **Intent:** reward active summon play. **Trigger:** when a Summoned Vex under your ownership damages an enemy. **Effect:** that enemy has a 30% chance to retarget aggro to the vex for 3 seconds. **Why it fits:** Evocation summons are supposed to be distractions; this makes them actually distracting. **Implementation:** `LivingDamageEvent` filter where `event.getSource().getEntity()` is a SummonedVex whose owner matches; RNG roll; set target's brain target via `Brain.setMemoryWithExpiry(ATTACK_TARGET, vex, 60)` or fallback to mob.setTarget. **Balance:** non-boss-only; boss aggro is notoriously finicky and we don't want to break encounters.

#### Evocation — Seals (T2)

**Creeper Cascade Mastery** — *Seal.* **Intent:** lean into the chain-detonate spectacle. **Trigger:** you cast Chain Creeper. **Effect:** the chain gets one additional hop (+1 generation) and each detonation has a 25% chance to spawn one extra creeper head that flies to the nearest enemy. **Why it fits:** Chain Creeper is the mod's chaos engine — pulling it further is peak Evocation. **Implementation:** `SpellOnCastEvent` spellId match; either modify `ModifySpellLevelEvent` to bump level by 1 (easy, crude), or hook into the Chain Creeper's own internal recursion count via reflection on the cast data. **Balance:** caps total generations at 5 to prevent infinite chains in horde spawners.

**Shield Wall** — *Seal.* **Intent:** defensive utility build. **Trigger:** you cast Shield. **Effect:** a second shield orthogonal to the first spawns, OR if you look at an ally within 6 blocks, the second shield spawns between them and their nearest enemy. 20-second ICD. **Why it fits:** Shield is Evocation's bunker tool; giving it a second panel makes it the actual "wall" its name implies. **Implementation:** `SpellOnCastEvent` → spawn a second ShieldEntity at rotated position relative to caster view. **Balance:** ICD separate from Shield's own cooldown; second shield has 50% HP of first.

#### Evocation — Crown (T3)

**Trickster's Aria** — *Crown.* **Intent:** invisibility-based rogue-mage. **Trigger:** you cast Invisibility. **Effect:** during invisibility, your projectile spells (Magic Missile, Firecracker, Wither Skull, Blood Slash, Firebolt, Icicle, Ball Lightning) can be cast without breaking invisibility (they do *not* count as "dealing damage" for the vanishing rule — but the invisibility still ends on melee damage). Additionally, the first projectile after entering invisibility deals +50% damage. **Why it fits:** the assassin mage fantasy is missing from the base mod; this defines it. **Implementation:** `SpellPreCastEvent` sets a flag `suppressInvisBreak`; override Iron's invisibility-break logic via a mixin on the `InvisibilityEffect`'s damage listener that checks the flag. **Balance:** melee damage still breaks (preserves counterplay); +50% first-shot applies once per invisibility window; visual sparkle on the caster gives opponents a *chance* to notice the projectile origin.

### 4.7 Nature

#### Nature — Marks (T1)

**Poisoner's Thumb** — *Mark.* **Intent:** reward stacking poison sources. **Trigger:** you damage an entity that already has the Poison effect. **Effect:** +15% damage on that hit. **Why it fits:** Nature's core loop is "poison them then hit them" — Spider Aspect already exists, this is its little brother. **Implementation:** `LivingDamageEvent` with `target.hasEffect(MobEffects.POISON)`. **Balance:** stacks additively with Spider Aspect, not multiplicatively.

**Rooted** — *Mark.* **Intent:** reward Oakskin commitment. **Trigger:** while you have Oakskin active and you have not moved more than 2 blocks in the last 3 seconds. **Effect:** gain +20% max HP regen rate and immunity to knockback. **Why it fits:** Oakskin trades mobility for tankiness — Rooted doubles down when you actually stay put. **Implementation:** track `lastPositionSample` on `PlayerTickEvent` every 60 ticks; if `hasEffect(OAKSKIN) && distance < 2`, apply a non-persistent `AttributeModifier` to MAX_HEALTH via `Attributes.KNOCKBACK_RESISTANCE`. Remove when conditions break. **Balance:** easy to interrupt (any movement cancels); doesn't make up for the base Oakskin slow.

#### Nature — Seals (T2)

**Blight Spread** — *Seal.* **Intent:** plague-mage archetype. **Trigger:** an entity with your Blight debuff dies. **Effect:** Blight transfers to up to 3 nearby (8 blocks) enemies at 70% duration. **Why it fits:** Blight is a single-target debuff; this makes it *the* reason to play Nature as a multi-target shutdown mage. **Implementation:** `LivingDeathEvent` check for `BLIGHT` effect (register `blight` MobEffect if not in the mod; if it uses an existing debuff like `MobEffects.WITHER` for representation, adapt); scan AABB(8) for eligible targets; apply effect. **Balance:** 70% decay prevents infinite propagation in dense crowds; max 3 targets per death.

**Venomous Harvest** — *Seal.* **Intent:** mana sustain through poison kills. **Trigger:** a poisoned (Poison effect) enemy dies. **Effect:** you gain 10 mana and 2s of +15% poison damage. **Why it fits:** Gluttony already converts food to mana; this makes *enemies* the food source, reinforcing the "nature feeds the mage" fantasy. **Implementation:** `LivingDeathEvent` filter; `ChangeManaEvent.post` via `magicData.addMana(10)`; apply a custom short MobEffect `venomous_harvest` that scales nature damage via `SpellDamageEvent`. **Balance:** mana gain is flat to prevent scaling abuse; note: this violates "no mana cost on Powers" only in a *positive* direction (Powers giving mana for free is fine, the rule forbids *costing* mana).

#### Nature — Crown (T3)

**The Grove Remembers** — *Crown.* **Intent:** attrition supremacy. **Trigger:** you have applied 3 or more distinct debuffs (Poison, Slowness, Blight, Root, Armor-shred, Weakness, etc.) to the same target. **Effect:** that target takes an additional +30% damage from all sources and has its healing reduced by 50%. One "marked" target at a time; marking a new one clears the old. **Why it fits:** Nature's entire premise is that the debuff *is* the win condition; this cashes the check for it. **Implementation:** scan target's `getActiveEffects()` on `LivingHurtEvent`; count negative effects; if ≥3 and from this player, apply a `grove_remembers` MobEffect (custom, BAD category) which the `SpellDamageEvent` reads. **Balance:** debuff count gating means you have to commit 2–3 spells to set up; single-target focus keeps it from nuking crowds.

### 4.8 Blood

#### Blood — Marks (T1)

**Crimson Tithe** — *Mark.* **Intent:** small lifesteal everywhere. **Trigger:** you deal any Blood-school damage. **Effect:** heal for 10% of damage dealt. **Why it fits:** Blood already has strong lifesteal on specific spells; this universalizes the "small tax" across all blood casts. **Implementation:** `SpellDamageEvent.Post` (or `LivingHurtEvent.Post`) filter by blood school; `player.heal(amount * 0.1f)`. **Balance:** does not stack with per-spell lifesteal (Ray of Siphoning stays at 100% and is unaffected; Blood Slash stays 15%; this Power adds 10% *only if the spell itself has 0% innate lifesteal*).

**Marrow Sense** — *Mark.* **Intent:** reward low-HP play. **Trigger:** you are below 50% HP. **Effect:** your Blood-school spells cast 15% faster (cast-time reduction, applied on cast-start only). **Why it fits:** Blood mages traditionally trade HP for power; this rewards *being* wounded. **Implementation:** `SpellPreCastEvent` with `player.getHealth() / player.getMaxHealth() < 0.5` and school BLOOD; reduce cast time. **Balance:** flat 15%, not stacking with CTR attribute multiplicatively.

#### Blood — Seals (T2)

**Sacrifice Cascade** — *Seal.* **Intent:** necromancer detonation loop. **Trigger:** you cast Sacrifice on your summon. **Effect:** the explosion also applies `BLEEDING` (existing MobEffect) for 6 seconds to all hit enemies AND restores 15 mana per summon remaining after the sacrifice. **Why it fits:** Sacrifice is already amazing; Cascade turns it into a *strategic* choice — sacrifice early for more mana, late for more damage. **Implementation:** `SpellOnCastEvent` with Sacrifice spell; read Iron's `SummonedEntitiesCastData` before the cast resolves; post-detonation `ChangeManaEvent` addition; AABB apply of BLEEDING. **Balance:** mana refund capped at 45 total (3 summons); bleeding is real damage so it stacks oddly with poison — tested and fine.

**Harvest the Weak** — *Seal.* **Intent:** Devour build-around. **Trigger:** you kill an enemy with Devour (or any Blood spell that kills a target below 30% HP). **Effect:** you gain +1 temporary max HP for 60 seconds, stacking up to +10. **Why it fits:** Devour already grants max-HP-on-kill; this generalizes it to any execution via Blood. **Implementation:** `LivingDeathEvent` from blood-school damage, target below 30% HP threshold check; apply a `MobEffectInstance` granting `Attributes.MAX_HEALTH` modifier stacking via amplifier. **Balance:** stack cap +10; duration resets don't stack beyond +10.

#### Blood — Crown (T3)

**The Heart's Toll** — *Crown.* **Intent:** reckless-caster archetype. **Trigger:** passively active. **Effect:** Blood-school spell damage is increased by 30%. HOWEVER, every Blood spell you cast costs 5% of your current HP (not mana — the mana cost stays the same; this is an *additional* HP tax). At 10% HP or below, Blood spells no longer cost HP but deal −50% damage. **Why it fits:** this is the *true* Blood mage — the one who pays in flesh. The dual-cost design emulates Heartstop's deferred-risk pattern across an entire school. **Implementation:** `SpellPreCastEvent` with blood school; `player.hurt(DamageSources.genericKill/bloodTithe, player.getHealth() * 0.05)`; `SpellDamageEvent` scales up damage. Below-10% clamp via the same hook. **Balance:** this violates "no drawback Powers" as a principle — but Crowns are allowed drawbacks because they're identity-defining. HP-cost means no infinite spell cheese; the 10% floor prevents self-kill. Note: the HP cost is from the Power, not a mana cost — Powers still activate *for free*.

### 4.9 Eldritch

#### Eldritch — Marks (T1)

**Forbidden Knowledge** — *Mark.* **Intent:** research-gated identity. **Trigger:** you cast any Eldritch spell. **Effect:** for 10 seconds after, your Ancient Knowledge Fragment drop rate from hostile mobs doubles. **Why it fits:** Eldritch is unlocked through research; this integrates the research loop into combat progression. **Implementation:** `SpellOnCastEvent` schoolId match → set `fragmentBonusUntil`; `LivingDropsEvent` reads it and adds extra drops via the mod's own item. **Balance:** purely economic, no combat power.

**Blind Witness** — *Mark.* **Intent:** reward Sculk Tentacles. **Trigger:** you damage an entity affected by Blindness. **Effect:** +20% damage on that hit. **Why it fits:** Blindness is Eldritch's signature debuff; this makes Sculk Tentacles a proper combo setup. **Implementation:** `LivingDamageEvent` with `target.hasEffect(BLINDNESS)`. **Balance:** simple, legible.

#### Eldritch — Seals (T2)

**Kinetic Affinity** — *Seal.* **Intent:** build around Telekinesis. **Trigger:** while channeling Telekinesis. **Effect:** the held target takes +50% fall damage AND +50% damage from physical sources while held. Additionally, dropping them from height triggers a small kinetic pulse on impact. **Why it fits:** Telekinesis is a tool without a builder — Kinetic Affinity is the environmentalist's kit. **Implementation:** `SpellOnCastEvent` with Telekinesis; attach a per-entity `kinetic_susceptibility` tag while channeled; `LivingDamageEvent` scales fall/physical. **Balance:** caps at +50%; one target at a time (Telekinesis itself enforces this).

**Piercing Insight** — *Seal.* **Intent:** wall-bypass mage. **Trigger:** you cast Sonic Boom or Eldritch Blast. **Effect:** that spell reveals all hostile entities within 24 blocks of its path for 15 seconds (Glowing effect + wall-vision, similar to Planar Sight but only on tagged entities). Additionally, the next spell you cast within 5 seconds ignores 50% of spell resistance on those revealed targets. **Why it fits:** Eldritch is the "forbidden sight" school; this operationalizes that fantasy into combat utility. **Implementation:** `SpellOnCastEvent` → raycast path, apply `MobEffects.GLOWING` to entities in a cylinder around it; tag them with `revealed_by_eldritch`; `SpellDamageEvent` applies resist pierce. **Balance:** glowing is a massive PvP advantage — for PvP servers, allow config toggle to disable the player-visibility portion.

#### Eldritch — Crown (T3)

**The Apocrypha Awakens** — *Crown.* **Intent:** capstone power-cost inversion. **Trigger:** your mana is below 20%. **Effect:** your Eldritch spells cost 50% less mana AND deal +40% damage. When your mana is above 80%, your Eldritch spells deal −20% damage (the knowledge only speaks when you are nearly silent). **Why it fits:** thematically perfect — the forbidden tome whispers loudest when you are empty. Mechanically, inverts the mana-hoarding pattern Resonance (from Ars 'n Spells) encourages. **Implementation:** `SpellPreCastEvent` (mana cost modification via `event.setManaCost` if available; else intercept `ChangeManaEvent` during the cast sequence); `SpellDamageEvent` for the damage scaling. **Balance:** the low-mana discount creates a real kiting-the-player window; the high-mana penalty prevents nova-first-strike.

---

## 5. Proposed Powers — Cross-Cutting Categories

Six categories × 6 Powers each = 36 additional Powers. These overlap school membership — a Mark here might be taken by a Fire mage who casts mostly projectiles — and cost Power Points from the same budget.

### 5.1 Projectile Spells

**Marks:**
- **Trueshot** — projectile hits while aim-centered on the target (raycast match within 2° at cast time) deal +15% damage. Implementation: `SpellOnCastEvent` records aim; `SpellOnHitEvent` compares initial aim to hit position.
- **Ricochet Primer** — the first projectile you fire after 3 seconds of not casting has a 25% chance to pierce one extra entity. Implementation: idle timer + `SpellOnHitEvent` counter reset.

**Seals:**
- **Volley Memory** — if you hit the same target with 3 projectiles in 5 seconds, the fourth deals +40%. Implementation: sliding-window hit counter on target's data attachment.
- **Gravity Well** — projectile hits on airborne enemies deal +25% and apply Slow Falling for 3s (to keep them exposed). Implementation: `target.onGround()` check in `SpellOnHitEvent`; apply vanilla `SLOW_FALLING`.

**Crown:**
- **Arcanist's Barrage** — every 10th projectile you land in a single combat fires a free echo of itself at the same target for 75% damage. Implementation: per-combat counter on attachment; spawn projectile via mod's spawnProjectile helpers.

### 5.2 Channel/Beam Spells

**Marks:**
- **Unbroken Focus** — for every 1 second you sustain a channel without taking damage, that channel's damage +4% (capped at +20%). Implementation: `SpellOnCastEvent` start timer; `LivingHurtEvent` resets; `SpellDamageEvent` scales.
- **Tidal Draw** — channels pull enemies within 6 blocks slowly toward your aim (0.1 block/tick velocity bump). Implementation: `PlayerTickEvent` while channeling; small `Vec3` push on AABB-contained entities.

**Seals:**
- **Harmonic Resonance** — every 2s of sustained channel, the channel's AoE/cone widens by 1 block (capped +3). Implementation: dynamic hitbox scaling on the beam entity or cone raycast width parameter.
- **Siphon Bond** — Ray of Siphoning / Ray of Frost / Poison Breath channels also heal you at 25% of the lifesteal rate of the channel. Implementation: per-spell additive heal on `SpellDamageEvent.Post` during channel.

**Crown:**
- **The Long Note** — sustaining a single channel for 4+ consecutive seconds causes its damage to spread to a second target within 5 blocks at 70%. Implementation: duration check; secondary raycast for chain target; spell damage source replication.

### 5.3 Summon Spells

**Marks:**
- **Master's Bond** — your summons have +15% max HP. Wait — *this looks like a stat buff and violates the rule.* Replacement: **Pack Tactics** — when two or more of your summons attack the same enemy within 2 seconds, the second attack deals +25%. Implementation: `LivingHurtEvent` with summon-owner check + sliding attack log.
- **Fallen Echo** — when one of your summons dies, you gain 3 seconds of +15% spell damage. Implementation: `LivingDeathEvent` owner filter; short MobEffect.

**Seals:**
- **Soul Tether** — summons within 8 blocks of you take 30% less damage; beyond 8 blocks they take 30% more. Implementation: `LivingHurtEvent` with distance check; scale incoming damage. Creates positional tension.
- **Lingering Binding** — when you unsummon (recast) summons, their deaths release the stored damage they would have dealt as a burst AoE at their positions. Implementation: hook `SummonManager.recastFinishedHelper`; read accumulated damage (track via per-summon data attachment that increments on each attack).

**Crown:**
- **The Conductor** — you can maintain summon batches from two different summon spells concurrently (normally the SummonManager caps you at one active batch). Implementation: wrap or replace the SummonManager check for this specific Power via a hook into the recast resolution; store two concurrent `SummonedEntitiesCastData` instances in the attachment.

### 5.4 Mobility/Teleport Spells

**Marks:**
- **Phase Recoil** — right after `SpellTeleportEvent`, projectiles fired within 1s have +15% speed. Implementation: post-teleport window; modify projectile velocity in `ProjectileImpactEvent.onSpawn`-equivalent.
- **Vanishing Trail** — teleporting leaves a 0.5s after-image at your previous location that draws attacker aggro for 2 seconds. Implementation: spawn a marker entity at old position with taunt aura (reuse vex-aggro logic).

**Seals:**
- **Clean Exit** — teleports cleanse Slowness, Root, and Chilled from you. Implementation: `SpellTeleportEvent` post-resolution; `player.removeEffect(...)`.
- **Blink Strike** — the first attack or spell after a teleport within 2 seconds deals +30%. Implementation: post-teleport flag; scale first damage event.

**Crown:**
- **Folded Space** — you can teleport again for 50% normal mana cost within 3 seconds of the first teleport (ignores cooldown on the second cast). Implementation: hook `SpellPreCastEvent` for teleport school of spells; within window, reduce mana cost via the mod's cost modification path and bypass cooldown check. Note: this adds a cost reduction but NOT on the Power itself — the triggered *spell* still costs mana; the Power activation is free.

### 5.5 Weapon-Caster Hybrid

**Marks:**
- **Staff Strike** — melee attacks with a StaffItem reduce your next spell's cast time by 20% (2s window). Implementation: `AttackEntityEvent` → set `staffBuffUntil`; `SpellPreCastEvent` reads.
- **Spell Parry** — blocking with a shield while holding a MagicSwordItem reflects 20% of spell damage back at the caster. Implementation: `LivingAttackEvent` with blocking check + item check; second damage event on attacker.

**Seals:**
- **Imbued Rhythm** — alternating melee hits and spell casts (M-S-M-S or S-M-S-M) adds +8% damage per alternation up to +32% (reset on repeat). Implementation: state machine on attachment: last-action enum, counter.
- **Arcane Riposte** — when your MagicSwordItem's preset spell fires on-hit, that specific spell's cooldown is reduced by 30%. Implementation: `SpellOnCastEvent` with `CastSource.SWORD`; cooldown adjust.

**Crown:**
- **Warmage's Covenant** — casting a spell immediately after a critical melee hit (within 0.5s) makes that spell ignore 25% of target's spell resist. Implementation: `CriticalHitEvent` buffers `critUntil`; `SpellDamageEvent` consumes and applies resist pierce.

### 5.6 Utility/Buff Spells

**Marks:**
- **Lingering Grace** — self-buffs (Charge, Oakskin, Frostbite, Spider Aspect, Haste) applied to yourself last 15% longer. This borders on stat-buff territory — refined: **Lingering Grace** instead: *when any self-buff on you expires, the next spell you cast within 3 seconds has its cooldown refunded by 25%*. Implementation: `MobEffectEvent.Expired` filter on buff list; next `SpellOnCastEvent` triggers refund.
- **Shared Flame** — utility spells cast on self also apply the buff (at 50% duration) to allies within 4 blocks. Implementation: `SpellOnCastEvent` buff spells; scan nearby allies; apply MobEffectInstance.

**Seals:**
- **Shield Break Counter** — when a Shield or Fortify barrier on you is destroyed, you gain 3 seconds of 40% damage reduction. Implementation: track barrier HP via FortifyEffect removal; `MobEffectEvent.Expired` or custom listener; apply reduction effect.
- **Empowered Dispel** — when Cleanse removes a debuff from you, the next spell you cast inherits 10% of that debuff's remaining duration as bonus damage per second. Implementation: intercept Cleanse's effect removal; read removed effects' durations; buffer bonus.

**Crown:**
- **The Still Mind** — when you are at full mana and full HP simultaneously, your next spell has +30% damage and applies one random school-appropriate debuff. Implementation: state check on `SpellPreCastEvent`; RNG from a per-school debuff table; apply effect on hit.

---

## 6. Design Philosophy & Constraints Recap

**Rejected categorically from Powers:**
- "+X% mana / -Y% cost / +Z% spell power / +W% CDR" — these are the Perk tree's domain.
- Flat-damage boosts untied to any condition — every Power must consume a condition.
- Reskinned Powers across schools (no "Fire Kindle" + "Ice Kindle" + "Lightning Kindle" copy-paste). Each school's Powers reinforce that school's *unique* loop: Fire spreads, Ice sets up shatter, Lightning chains and verticals, Holy rewards altruism, Ender rewards displacement, Blood rewards self-cost, Evocation rewards trickery/summons, Nature rewards debuff-stacking, Eldritch rewards forbidden-knowledge commitment.

**Emphasized instead:**
- **Timing windows** — many Powers create short 1–5 second windows that reward combo execution (Step Between, Blink Strike, Kindle, Warmage's Covenant).
- **Positioning** — Soul Tether, Heat Haze, Herald of Dawn, Glacial Sovereign all care about *where* you are.
- **Commitment rewards** — The Heart's Toll, Thunder Lord, Glacial Sovereign, The Apocrypha Awakens make specific playstyles measurably stronger.
- **Emergent cross-school synergies** — Guided Fate (Holy) boosts *any* projectile, including Fire/Ender/Blood ones; Trickster's Aria (Evocation) enables Fire/Ender spam during invis; Blind Witness (Eldritch) rewards anyone who dipped into Sculk Tentacles.

**Bridge-mod (Ars 'n Spells) synergy:**
- Cross-school Powers that trigger on "any projectile" or "any spell cast" automatically work with Ars Nouveau spells routed through Ars 'n Spells, as long as Ars's spell-resolve fires through its own equivalent events OR is wrapped into Iron's event flow (the bridge's `BridgeManager` handles mana; the Powers system independently subscribes to both `SpellOnCastEvent` and Ars's `SpellResolveEvent` for coverage).

**No mana cost reminder:** every Power activates free. Powers that grant mana (Venomous Harvest, Sacrifice Cascade) are positive; no Power costs mana to trigger. Powers that cost HP (The Heart's Toll) are explicitly flagged as Crown-tier drawback commitments, not a mana proxy.

**How Powers differ from Iron's Spells upgrade gems:** upgrade gems grant numeric attribute bonuses (max mana, spell power, per-school power, etc.) on a per-item basis and are equivalent to Perks. Powers are trigger→effect behavioral rules attached to the *player*, not an item. An equipped Power fires regardless of which spellbook you're holding.

---

## 7. Final Evaluation

**Easiest to implement (ship first, low-risk):**
1. Sanctified Strike (Holy Mark) — trivial `SpellDamageEvent` + entity tag check.
2. Poisoner's Thumb (Nature Mark) — trivial MobEffect check.
3. Brittle / Blind Witness / Crimson Tithe — all simple effect-gated damage scalars.
4. Marrow Sense — single HP threshold check.

**Most novel / design-risky (signature Powers):**
1. **Unraveled** (Ender Crown) — time-rewind death save; requires position buffer, careful PvP considerations.
2. **The Heart's Toll** (Blood Crown) — inverted cost structure; requires player buy-in.
3. **The Apocrypha Awakens** (Eldritch Crown) — inverted mana relationship.
4. **Glacial Sovereign** (Ice Crown) — runtime effect-duration manipulation on nearby entities.
5. **The Conductor** (Summon Crown) — requires wrapping SummonManager's single-batch rule.

**Most likely to feel great in gameplay:**
1. **Pyroclasm** (Fire Crown) — chain-explosion visual spectacle; satisfying audio/visual feedback.
2. **Shatter** (Ice Seal) — burst-damage release of a frozen target is *already* satisfying; this just quantifies it.
3. **Conduit Mark** (Lightning Seal) — mark-and-detonate is universally fun.
4. **Trickster's Aria** (Evocation Crown) — creates an entirely new archetype.
5. **Herald of Dawn** (Holy Crown) — support players will build around this.

**Requires careful tuning (abuse vectors):**
1. **Pyroclasm** — horde/spawner chains must be cap-limited (already spec'd).
2. **Unraveled** — destination clamping; PvP config toggle.
3. **The Conductor** — don't let summons double-scale via both `SUMMON_DAMAGE` sources.
4. **Guided Fate** — +20% dmg + 20% CDR is a big stack; verify additive vs multiplicative behavior with gear CDR.
5. **Empowered Dispel** — debuff durations can be abusively long from certain mods; cap at a fixed max duration.

**Recommended rollout order:**
- **Phase 1 (beta):** Fire, Ice, Lightning, Holy Marks + Seals (not Crowns). Cross-cutting Projectile and Channel/Beam categories. Lowest implementation risk, highest new-build density, covers the most common player choices.
- **Phase 2:** Ender, Evocation, Nature Marks + Seals + all previously shipped Crowns. By now the event dispatcher is proven; Crowns are the natural capstone once players have leveled into them.
- **Phase 3:** Blood + Eldritch full tiers, plus Summon, Mobility, Weapon-Caster, Utility cross-cutting Crowns. These are the highest-complexity Powers; ship them last with the most playtesting data.

---

## 8. Implementation Roadmap Summary

### 8.1 Event listener architecture

A single `PowerEventDispatcher` class (singleton on the game bus) subscribes to the event list below. Each event fires `dispatcher.handle(EventType, player, payload)` which looks up equipped Powers for that player from the `PlayerPowers` attachment, filters by `Power.respondsTo(EventType)`, and calls each Power's `onTrigger(context)`.

Subscribed events (NeoForge 1.21):
- `SpellPreCastEvent`, `SpellOnCastEvent`, `SpellOnHitEvent`, `SpellDamageEvent`, `ChangeManaEvent`, `SpellTeleportEvent`, `ModifySpellLevelEvent`, `SpellSelectionManager.SpellSelectionEvent` (all Iron's Spells game bus).
- `LivingHurtEvent`, `LivingDamageEvent`, `LivingDeathEvent`, `LivingHealEvent`, `LivingKnockBackEvent`, `LivingEquipmentChangeEvent`, `MobEffectEvent.Added`, `MobEffectEvent.Expired`, `PlayerTickEvent.Post`, `AttackEntityEvent`, `CriticalHitEvent`, `ProjectileImpactEvent`, `EntityJoinLevelEvent`, `LivingDropsEvent`.

### 8.2 Shared utility systems

Create these once, reuse across Powers:

1. **SpellHistoryTracker** — `Deque<(spellId, tick, school)>` per player, capped at 32, pushed on `SpellOnCastEvent`. Used by Thunder Lord, Glacial Sovereign, Skybreaker, Imbued Rhythm.
2. **DamageTypeMemory** — per-player `EnumMap<School, long lastTickHit>` and `Map<UUID, School> lastHitEntity`. Used by Conduit Mark, Guided Fate, Venomous Harvest.
3. **ProcWindowManager** — generic rolling-window timer system. Per player+powerId → `windowEndsAt` ticks. Powers call `windows.open(powerId, duration)` and `windows.active(powerId)`. Used by nearly every Mark.
4. **InternalCooldownManager** — per player+powerId → `availableAt`. Powers call `icd.check(powerId, durationTicks)`. Used by all Seal/Crown Powers with ICDs.
5. **TargetTagger** — writes short-lived tags (via `MobEffectInstance` or a dedicated per-entity data attachment) for "marked targets." Used by Conduit Mark, Grove Remembers, Counterspell Riposte.
6. **AllyDetector** — resolves "is this entity my ally?" via a team/party system (if Runecraft has one) or by owner-UUID/tamed-by-UUID fallback. Used by Fortifying Bond, Herald of Dawn, Shared Flame.
7. **PositionBuffer** — rolling per-player position history. Used exclusively by Unraveled (and potentially future rewind Powers).
8. **SummonRegistry** — a running per-player list of active summons, tracked via `EntityJoinLevelEvent` owner-UUID filter. Used by Conductor, Lingering Binding, Vex Taunt, Fallen Echo, Soul Tether.

### 8.3 Integration points with Iron's Spells API

- **Reading spells:** `SchoolRegistry.getSchool(spellId)` to classify; cache in `SpellHistoryTracker`.
- **Reading MagicData:** `MagicData.getPlayerMagicData(player).getPlayerCooldowns()` for cooldown refunds; `.getMana()` for threshold Powers.
- **Granting spell access:** `SpellSelectionManager.SpellSelectionEvent` to inject a bonus spell (unused in Phase 1 but kept as design-option for future Powers like "your Evocation Crown grants Spectral Hammer").
- **Modifying damage:** `SpellDamageEvent.setDamage(...)` for all school-damage scaling.
- **Applying Iron's effects:** reference `MobEffectRegistry.CHILLED`, `IGNITED`, `FORTIFY`, `GUIDED`, `CHARGED`, etc. Do *not* duplicate these — use the mod's registered effects.
- **School color for UI:** `SchoolType.displayName.getStyle().getColor()` gives hex for particles and tooltip borders.
- **Cast sources:** filter by `CastSource.SWORD` for Weapon-Caster Hybrid Powers.

### 8.4 Data-driven JSON schema (datapack-tweakable)

```json
{
  "type": "runic_skills:power",
  "id": "runic_skills:fire/kindle",
  "tier": "mark",
  "school": "irons_spellbooks:fire",
  "governing_skill": "magery",
  "gates": {
    "skill_requirements": {"magery": 30},
    "total_skill_minimum": 0,
    "prerequisite_powers": []
  },
  "trigger": {
    "type": "runic_skills:spell_on_hit",
    "school": "irons_spellbooks:fire",
    "applied_effect": "irons_spellbooks:ignited",
    "first_application_only": true
  },
  "effect": [
    { "type": "runic_skills:mark_target", "mark_id": "kindle", "duration_ticks": 80 },
    { "type": "runic_skills:consume_mark_on_damage",
      "mark_id": "kindle",
      "damage_multiplier": 1.20,
      "school_filter": "irons_spellbooks:fire" }
  ],
  "icd_ticks": 0,
  "description": { "translate": "power.runic_skills.fire.kindle.desc" }
}
```

All Triggers and Effects are `MapCodec`-dispatched polymorphic types, registered in their own `DeferredRegister<TriggerType>` and `DeferredRegister<EffectType>`. New kinds can be added by addons without Java edits to Runic Skills. `SimpleJsonResourceReloadListener` at path `data/<ns>/runic_skills_powers/` loads and refreshes on `/reload`.

### 8.5 Per-player data

Two NeoForge data attachments:
- `PlayerPowers` — `List<ResourceLocation> equippedMarks (max 5), equippedSeals (max 3), Optional<ResourceLocation> equippedCrown`. Serialized via Codec; `copyOnDeath()`.
- `PowerRuntimeState` — transient state per Power (ICDs, windows, sliding counters). Not persisted across server restarts; rebuilt on login.

### 8.6 Testing milestones

1. Unit: register one Power (Sanctified Strike), verify `SpellDamageEvent` scaling works with a Holy spellbook against a zombie.
2. Integration: five concurrent Powers equipped; verify no event-handler ordering bugs; verify MagicData reads remain consistent during `PlayerTickEvent`.
3. Datapack: reload a modified `kindle.json` with a different multiplier; confirm live update without restart.
4. Cross-mod: Ars 'n Spells active; cast an Ars spell; verify Guided Fate fires if target is Guided (or confirm intentional gap and document).
5. PvP: verify Unraveled clamping on an arena server; verify Piercing Insight's glow effect is togglable.

---

## Closing note

This specification is deliberately opinionated. It takes Iron's Spells 'n Spellbooks' existing, underused mechanical seams — Guided, Ice Shadows, Charge, Echoing Strikes, Sacrifice, Heat Surge's Rended, Counterspell's banish, Blight, Telekinesis's kinetic susceptibility — and turns each into a Power that *lives on the character, not the spellbook*. No Power costs mana to trigger; none is a flat stat buff; each reinforces its school's identity; each has a concrete implementation path via documented Forge/NeoForge events and the published `io.redspace.ironsspellbooks.api.*` surface. Ninety Powers across nine schools and six cross-cutting categories, tiered 5/3/1, gated by Runic Skills' skill thresholds, serialized as datapack JSON, dispatched through a single event router, and designed to create **legible, combo-driven, emergent gameplay** that rewards commitment over optimization.

Ship Phase 1 (Fire/Ice/Lightning/Holy Marks and Seals + Projectile and Channel/Beam cross-cutters) first; iterate on Crowns after live telemetry shows which loops players actually build around.