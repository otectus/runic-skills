# Runic Skills perk design — a technical deep-dive into ISS, Apotheosis/Apothic, and Ars Nouveau for Forge 1.20.1

**Bottom line up front.** Runic Skills can hook cleanly into all three target mods by attaching perk effects to vanilla `AttributeModifier`s on well-documented ResourceLocation attribute IDs (`irons_spellbooks:*`, `attributeslib:*`, `ars_nouveau` `PerkAttributes`), by subscribing to published Forge events (`SpellPreCastEvent`/`SpellOnCastEvent`/`ChangeManaEvent` for ISS; `SpellCastEvent`/`SpellResolveEvent`/`SpellDamageEvent` for Ars; `GetItemSocketsEvent`/`AffixModifierEvent` for Apotheosis), and by treating **Iron's Spells 'n Spellbooks mana as the single source of truth**, because the existing bridge mod *Ars 'n Spells* (by Otectus) already routes Ars Nouveau Source/personal mana through the ISS pool in its default `iss_primary` mode. The design space is unusually clean: ISS owns the caster attributes, Apothic Attributes owns the combat attributes, and Ars Nouveau's Thread Perks occupy the armor-bound passive slot — leaving **active/cooldown, school-specialist, cross-mod synergy, and affix-scaling perks** as the natural niche for Runic Skills. Below is a detailed mechanics reference for each mod followed by roughly 70 concrete perk designs split across single-mod and cross-mod categories, with ResourceLocations, numbers, and suggested hooks.

> **Author note on "Joshua" vs. "Otectus"**: every mod named in the brief (Ars 'n Spells, Effective Instruments, Locks Reforged, Iron's Botany, Runecraft Core, Runic Structures) is published on CurseForge and GitHub under the handle **Otectus**. "Joshua" is presumably the real name behind that handle; it is not a public attribution anywhere searchable. Also note: **"Runic Gods", "Runic Races", and "Runic Skills" do not currently exist as published CurseForge/Modrinth/GitHub projects under Otectus as of April 2026** — only *Runecraft Core* and *Runic Structures* are public. The Runecraft: Conquest modpack (project ID 1244037) delivers race/skill progression through third-party mods (Passive Skill Tree, Attributizer, Origins-like). This report assumes Runic Skills is in active development and will ship alongside the others in the Runecraft ecosystem.

---

## 1. Iron's Spells 'n Spellbooks — mechanics reference

**Modid / namespace**: `irons_spellbooks`, package `io.redspace.ironsspellbooks`. Stable API lives in `io.redspace.ironsspellbooks.api.*`. Repo: `github.com/iron431/irons-spells-n-spellbooks`.

### Mana and cast mechanics
Mana is stored in a Forge capability **`MagicData`** (`io.redspace.ironsspellbooks.capabilities.magic.MagicData`) attached to every living entity. `MagicData` holds current mana, the cooldown map (spell-id → tick count), the synced spell data (status-effect flags like `HEARTSTOP`, `ABYSSAL_SHROUD`, `ANGEL_WINGS`, `TRUE_INVIS`, `CHARGED`, `PLANAR_SIGHT`), the active cast source enum (**`CastSource`**: `SPELLBOOK`, `SCROLL`, `SWORD`, `STAFF`, `LOOT_CHEST`, `SPELL_BOOK`, `RUNE`), and the `SpellSelectionManager`. Max mana is read from `AttributeRegistry.MAX_MANA` (default 100, stored as `irons_spellbooks:max_mana`). Mana regen is tick-based and **notoriously tied to max mana**: regen per tick is approximately `(max_mana / 100) * mana_regen_attribute`, so *boosting max mana boosts regen* and *reducing max mana below 100 can zero out regen* (issue #425). Cast time is modified by `cast_time_reduction`; cooldowns by `cooldown_reduction`. Both are percentage-based, each configurable to cap around 75%.

### Schools
The **`SchoolRegistry`** (data-driven via the registry events `irons_spellbooks:schools`) registers nine base schools, each with a Power attribute and a Resistance attribute:

| School | Focus item | Power attribute | Resistance attribute |
|---|---|---|---|
| Fire | Blaze Rod | `irons_spellbooks:fire_spell_power` | `irons_spellbooks:fire_magic_resist` |
| Ice | Frozen Bone | `irons_spellbooks:ice_spell_power` | `irons_spellbooks:ice_magic_resist` |
| Lightning | Bottle o' Lightning | `irons_spellbooks:lightning_spell_power` | `irons_spellbooks:lightning_magic_resist` |
| Holy | Divine Pearl | `irons_spellbooks:holy_spell_power` | `irons_spellbooks:holy_magic_resist` |
| Ender | Ender Pearl | `irons_spellbooks:ender_spell_power` | `irons_spellbooks:ender_magic_resist` |
| Blood | Blood Vial | `irons_spellbooks:blood_spell_power` | `irons_spellbooks:blood_magic_resist` |
| Evocation | Emerald | `irons_spellbooks:evocation_spell_power` | `irons_spellbooks:evocation_magic_resist` |
| Nature | Poisonous Potato | `irons_spellbooks:nature_spell_power` | `irons_spellbooks:nature_magic_resist` |
| Eldritch | (research-gated) | `irons_spellbooks:eldritch_spell_power` | `irons_spellbooks:eldritch_magic_resist` |

Schools are **fully data-driven in 3.x** — addons (including Iron's Botany, Cataclysm: Spellbooks) register new schools via `SchoolRegistry.register(id, focusTag, powerAttr, resistAttr, damageType, defaultCastSound)`. That means **Runic Skills should reference school power/resist attributes by ResourceLocation, not by hard-coded references**, because the set is extensible.

### Cast types (`io.redspace.ironsspellbooks.api.spells.CastType`)
`INSTANT`, `LONG`, `CONTINUOUS`, `CHARGE`. Cast time is in ticks; `cast_time_reduction` modifies Long and Charge casts. Continuous casts drain mana per tick and honour `mana_regen` as a background tick-rate. Instant casts are interruption-proof; Long can be cancelled by taking damage unless mitigated by the Antique Amulet or equivalent.

### Full attribute list (from `AttributeRegistry`)
All IDs are under `irons_spellbooks:` unless noted. Verified via `/attribute` commands, source registry, and changelog entries.

- `max_mana` — flat mana pool, default 100.
- `mana_regen` — regen multiplier (default 1.0; can be 0, not negative).
- `spell_power` — global spell damage multiplier.
- `fire_spell_power`, `ice_spell_power`, `lightning_spell_power`, `holy_spell_power`, `ender_spell_power`, `blood_spell_power`, `evocation_spell_power`, `nature_spell_power`, `eldritch_spell_power` — per-school multipliers.
- `spell_resist` — global incoming-spell mitigation.
- `fire_magic_resist`, `ice_magic_resist`, `lightning_magic_resist`, `holy_magic_resist`, `ender_magic_resist`, `blood_magic_resist`, `evocation_magic_resist`, `nature_magic_resist`, `eldritch_magic_resist` — per-school mitigation.
- `cooldown_reduction` — percent CDR (default 0.0).
- `cast_time_reduction` — percent cast-time reduction.
- `summon_damage` — multiplier applied to damage dealt by *summons* (distinct from summoner spell power).
- **Specific-spell resist attributes** like `blood_slash_resist` — ISS registers granular resistances per heavily-used spell; search `AttributeRegistry` for the full list.

Recent changes: Eldritch Spell Power and Resistance were added (changelog: "Added Eldritch Spell Power and Resistance attributes"); most attribute caps were increased; mana regen floor became 0.

### Status effects (`MobEffectRegistry`, `irons_spellbooks:`)
Every effect the mod layers onto ISS mechanics:
- **Ember** — burning magic damage-over-time (fire school signature).
- **Chilled** / **Frozen** — slow → ice tomb escalation. When fully frozen, enemies now encase in an **Ice Tomb** that doubles damage taken when shattered.
- **Abyssal Shroud** — Eldritch invulnerability during duration.
- **Heartstop** — blood school: temporary invulnerability, then 50% accumulated damage on expiry.
- **Ascension** — holy mobility.
- **Angel Wings** — timed glide.
- **True Invisibility** — hides armor-derived visibility from mobs.
- **Charged** — lightning school buff.
- **Planar Sight** — see entities through walls.
- **Oakskin** — nature school DR buff.
- **Fortify** — holy DR.
- **Spider Aspect** — nature buff.
- **Evasion** — dodge.
- **Blooded / Bleeding** — blood DoT.
- **Root**, **Slow**, **Blight**, **Withering**, **Unholy** — debuffs (several are spell-specific).
- **Echoing Strikes**, **Chromatic Strikes** — cleave/AoE self-buffs on imbued weapons.
- **Guiding Bolt** — holy mark that amplifies next hit.
- **Raise Dead**, summon-specific timers (deprecated in 3.14+; summons now use the `SummonManager` and recast-to-unsummon).
- **Truesight** — reveal invisibility.

### Damage sources (`io.redspace.ironsspellbooks.damage.DamageSources`)
Each school has a custom `SpellDamageSource` that tags into `irons_spellbooks:is_magic_damage` (tag) plus its school. Resistance is applied in the spell-damage event handler by reading the target's school-specific `*_magic_resist` attribute. Global `spell_resist` is applied on top.

### Cast items and upgrade system
- **Spellbooks** (tier-ordered, partial list): Flimsy Journal, Ironbound Tome, Rotten Spellbook (−15% spell resist, cheap 8 slots), Apprentice Spellbook (+50 max mana), Blaze Instruction Manual (+10% Fire Spell Power, +200 max mana), Villager Bible (Holy), Enchanted Spellbook (+100 max mana), Dragon Skin Spellbook (Ender), Ancient Codex (end-game generalist).
- **Scrolls** — craft at Scroll Forge with Ink (Common, Uncommon, Rare, Epic, Legendary) + Focus + paper. Consumed on cast unless inscribed via Inscription Table.
- **Imbued weapons / staves** — every vanilla tool tier plus mod-tier staves (Graybeard, Ice, Pyrium, Blood, Artificer's Cane). Imbue slot: typically 1; spellbooks have variable slots (scales with tier; Lesser Spell Slot Improvement bumps Novice up to 12 max).
- **Upgrade Orbs** (crafted with Arcane Debris + Cinder Essence; default 3 upgrade slots per item, gated by `irons_spellbooks:upgrade_whitelist` tag):
  - +5% Spell Power (generic) per orb; +5% Cooldown Reduction; +5% Spell Resistance; +50 Max Mana; +5% per school Spell Power (Fire, Ice, Lightning, Holy, Ender, Blood, Evocation, Nature orbs); **+Cast Time Reduction** and **+Mana Regen** orbs (added in the recent rune/orb update).
- **Affinity Rings** — combine a ring with a scroll at the Arcane Anvil to attune it to a specific spell, granting bonus level. Supports multiple attunements on one ring via new map-NBT `bonuses` field.
- **Runes** — Blank Runestone is the base crafting material, dropped from every magical structure's school-appropriate mob. Rune types mirror the orb types.

### Events and API (all under `io.redspace.ironsspellbooks.api.events`)
- **`SpellPreCastEvent`** — fired before cast validation; cancellable; can modify mana cost, cooldown, spell level.
- **`SpellOnCastEvent`** — fired after the spell resolves; intentionally not-cancellable (see issue #388).
- **`ChangeManaEvent`** — fired whenever mana is added/removed; exposes `getOldMana`/`setNewMana`/`getMagicData`. The canonical place to hook "mana restoration on kill" or "mana conversion" effects.
- **`SpellDamageEvent`** — modifiable `damage` field; fires before ISS applies school resistance.
- **`ModifySpellLevelEvent`** — lets perks/mods buff a spell's effective level (used by Affinity Rings).
- **`SpellCooldownEvent`** — fires when a cooldown begins.
- **`SpellTeleportEvent`** — fires on blink/teleport effects, with config switches.
- **`SpellSelectionManager.SpellSelectionEvent`** — inject entries into the spell wheel without needing a spellbook/scroll (useful for "this perk teaches you one free spell" effects).
- **`RegisterConfigParametersEvent` / `ModifyDefaultConfigValuesEvent`** — data-driven spell config overrides.

The spell itself is `AbstractSpell` with fields `school`, `castType`, `minRarity`, `maxLevel`, `defaultConfig` (cast time, cooldown seconds, mana cost per level, base/level-scaled damage). Spell IDs are registry-keyed, e.g. `irons_spellbooks:fireball`, `irons_spellbooks:blood_slash`, `irons_spellbooks:teleport`.

### Notable bosses and entities
Dead King Boris (drops Blood Staff, 5 imbued spells, end-game), Priest (trades spellbooks), Archevoker, Keeper, Necromancer, Cryomancer, Pyromancer, Apothecarist (friendly trader), Archer Walker. Summoned mobs (`SummonedZombie`, `SummonedSkeleton`, `SummonedVex`, `SummonedPolarBear`) use `summon_damage` and now route through `SummonManager`.

---

## 2. Apotheosis / Apothic family — mechanics reference

**1.20.1 dependency chain**: Placebo → Apothic Attributes (modid `attributeslib` on 1.20.1; package `dev.shadowsoffire.attributeslib`) → Apothic Spawners (`apothic_spawners`) → Apotheosis (`apotheosis`; bundles Adventure + Enchantment + Potion + Village modules). Apothic Curios bridges affixes/sockets to Curios slots. **Apothic Enchanting as a separate jar is 1.20.4+ only** — on 1.20.1 the enchanting content still ships inside Apotheosis itself.

### Adventure module
- **Affix NBT** lives at `stack.tag.affix_data`: `{ name, sockets:int, affixes:{id→float}, rarity:"apotheosis:<id>", uuids:int[] }`.
- **Rarity tiers (1.20.1)**: Common → Uncommon → Rare → Epic → Mythic. Ancient/Artifact/Heirloom/Esoteric are **Apotheotic Additions** (third-party), not base.
- **Loot categories** (`LootCategory` enum): `SWORD`, `TRIDENT`, `HEAVY_WEAPON`, `BOW`, `CROSSBOW`, `PICKAXE`, `SHOVEL`, `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`, `SHIELD`, plus Curios-slot categories via Apothic Curios (`curios:ring`, `curios:necklace`, `curios:belt`, `curios:spellbook`).
- **Affix subtypes** (data-driven JSON under `data/apotheosis/affixes/<category>/<type>/`): `attribute/`, `mob_effect/`, `damage_reduction/`, `effect/`, `socket/`, `durable/`.
- **Sockets and Gems**: Sigil of Socketing adds up to 3 sockets; Sigil of Withdrawal extracts; Sigil of Rebirth fuels Reforging; Sigil of Enhancement fuels Augmenting. Gem Cutting Table (late 1.20.1) upgrades gem purity.
- **Tables**: Salvaging Table (affix → rarity materials), Simple Reforging Table (up to Rare), Reforging Table (up to Mythic), Augmenting Table (re-roll one affix at a time with Sigil of Enhancement + XP).
- **Bosses / Invaders / Elites / Rogue Spawners** are datapack-registered; the Adventure module fires `boss_dungeon` worldgen features.
- **World Tiers** (CTRL+T): Haven, Frontier, Ascent, Summit, Pinnacle — mob strength and invader rates scale with the tier; requires milestone gear + boss kills.

### Enchantment module (inside Apotheosis jar on 1.20.1)
- Enchanting Table scales with **Eterna / Quanta / Arcana** from nearby bookshelves (5×5×2). Eterna sets level cap (up to 100), Quanta controls variance, Arcana controls quantity/rarity.
- **Enchantment Library** block stores many books with filtering.
- Anvil: no prior-work cap; anvil enchants: **Splitting** (split books), **Obliteration** (inverse-combine), **Unbreaking**, **Stable Footing**.
- **Altar of the Sea** multiblock: spend XP + enchanted items → single powerful book.
- 15+ new enchants in `apotheosis:` namespace: Bane of Illagers, Berserker's Fury, Hell Infusion, Icy Thorns, Knowledge of the Ages, Life Mending, Occult Aversion, Rebounding, Reflective Defenses, Shield Bash, Spearfishing, Scavenger, Natures Blessing, Tempting, Depth Miner, True Infinity, Miner's Fervor, Chromatic, Sparkling, Earth's Boon, Soulbound, Capturing.
- **Tiers**: Masterwork (high table level only), Twisted (drawbacks), Corrupted (life-cost).

### Spawner module (Apothic Spawners)
Silk Touch picks spawners up (-100 durability). Modifier items: Sugar, Clock, Fermented Spider Eye, Blaze Rod, Prismarine Crystals, Ghast Tear, Nether Star, Dragon Breath, Redstone Block, Glowstone, Iron Block, Wool, Poppy, Lava Bucket, Echo Shard. Quartz in off-hand inverts the modifier. Adds NBT fields `initial_health`, `ignore_players`, `ignore_conditions`, `redstone_control`, `ignore_light`, `no_ai`, `silent`, `youthful`, `burning`, `echoing`. **Capturing** enchant: 0.4%/level chance to drop spawn egg on kill.

### Potion module (inside Apotheosis jar on 1.20.1; split into Apothic Attributes in 1.21+)
**Potion Charms**: 3 identical potions → charm giving effect while in inventory/Curios. **Effects**: Sundering (inverse Resistance), Knowledge (+XP multiplier), Bursting Vitality (+healing received), Grievous (−healing received), Bleeding (armor-piercing DoT), Detonation (consumes fire on expiry), Flying (creative flight). **New brewing recipes**: Resistance, Absorption, Haste, Mining Fatigue, Wither, Luck. **True Infinity** enchant lives here.

### Apothic Attributes — the critical attribute registry
Registered under the **`attributeslib:` namespace on 1.20.1** (renamed to `apothic_attributes:` in 1.21+). Key attributes (resource location shown; default / min / max in parentheses):

| Attribute | ID | Default/Min/Max | Notes |
|---|---|---|---|
| Armor Pierce | `attributeslib:armor_pierce` | 0 / 0 / 1000 | Flat armor points bypassed. |
| Armor Shred | `attributeslib:armor_shred` | 0 / 0 / 2 | % of target armor bypassed. |
| Arrow Damage | `attributeslib:arrow_damage` | 1.0 / 0 / 10 | Multiplier; affects tridents after 1.20 patch. |
| Arrow Velocity | `attributeslib:arrow_velocity` | 1.0 / 0 / 10 | Multiplier. |
| Cold Damage | `attributeslib:cold_damage` | 0 / 0 / 1000 | Flat bonus magic damage + slow. |
| Crit Chance | `attributeslib:crit_chance` | 0.05 / 0 / 10 | Multi-crit is additive above 100%. |
| Crit Damage | `attributeslib:crit_damage` | 1.5 / 1 / 100 | Applies to vanilla and Apoth crits. |
| Current HP Damage | `attributeslib:current_hp_damage` | 0 / 0 / 1 | % of target current HP; armor-piercing physical. Requires ≥75% attack charge. |
| Dodge Chance | `attributeslib:dodge_chance` | 0 / 0 / 1 | Melee/projectile dodge. |
| Draw Speed | `attributeslib:draw_speed` | 1.0 / 0 / 4 | Bow/crossbow charge. |
| Experience Gained | `attributeslib:experience_gained` | 1.0 / 0 / 1000 | XP multiplier. |
| Fire Damage | `attributeslib:fire_damage` | 0 / 0 / 1000 | Flat magic damage + burn. |
| Ghost Health | `attributeslib:ghost_health` | 0 / 0 / 1000 | Regenerating pool (not absorption). |
| Healing Received | `attributeslib:healing_received` | 1.0 / 0 / 1000 | Multiplier. |
| Life Steal | `attributeslib:life_steal` | 0 / 0 / 10 | % of physical damage → HP. |
| Mining Speed | `attributeslib:mining_speed` | 1.0 / 0 / 10 | Multiplier. |
| Overheal | `attributeslib:overheal` | 0 / 0 / 10 | Physical damage → absorption. |
| Protection Pierce | `attributeslib:prot_pierce` | 0 / 0 / 34 | Flat protection bypassed. |
| Protection Shred | `attributeslib:prot_shred` | 0 / 0 / 1 | % protection bypassed. |
| Elytra Flight | `attributeslib:elytra_flight` | false | Boolean; ≥0.5 true. |
| Creative Flight | `attributeslib:creative_flight` | false | Boolean; ≥0.5 true. |

**Notes**: Armor DR is rewritten as `50/(50+armor)` (configurable). Armor Toughness reduces Pierce/Shred effectiveness by 2%/point up to 60%. Tag `attributeslib:is_non_physical` opts a damage type out of LIFE_STEAL/OVERHEAL. Damage types: `attributeslib:bleeding`, `attributeslib:detonation`, `attributeslib:current_hp_damage`, `attributeslib:fire_damage`, `attributeslib:cold_damage`. Helper classes: `ALObjects.Attributes`, `ALCombatRules`, `AffixHelper`, `SocketHelper`, `LootCategory`, `LootRarity`, `RarityRegistry`. Events: `GetItemSocketsEvent`, `GetItemGemsEvent`, `ItemSocketingEvent`, `AffixModifierEvent`, `GetAffixModifiersEvent`, `AffixRerollEvent`, `AffixSalvageEvent`, `ApothAttackEvent`, `AttributeChangedValueEvent`.

**Interaction with ISS**: Base Apotheosis 1.20.1 adds **no** spell-power attributes. The third-party addon **Fallen Gems & Affixes** (K4yne, requires RunicLib) backports the NeoForge-only "Apotheosis × Iron's Spellbooks Compat" mod to 1.20.1, adding the `AdaptiveSpellPowerAffix`, spell-school gems, staff/heavy-weapon categories, and `curios:spellbook` as a loot category. **Runic Skills should NOT assume these affixes exist on base Apotheosis** — gate with `ModList.isLoaded("fallen_gems_affixes")` or with a capability check.

---

## 3. Ars Nouveau — mechanics reference

**Modid / namespace**: `ars_nouveau`, package `com.hollingsworth.arsnouveau`. Repo: `baileyholl/Ars-Nouveau`. Deps: Curios, Patchouli, GeckoLib.

### Glyphs and spell composition
Three glyph types — **Forms** (`projectile`, `touch`, `self`, `rune`, `orbit`, `ray`, `underfoot`), **Effects** (`harm`, `heal`, `break`, `place_block`, `ignite`, `freeze`, `launch`, `knockback`, `summon_undead`, `blink`, `lightning`, `fangs`, `windshear`, `linger`, `summon_vex`, etc.), and **Augments** (`amplify`+20, `dampen`−10, `aoe`+20, `pierce`+15, `accelerate`+15, `decelerate`−10, `extend_time`+10, `duration_down`−10, `sensitive`+10, `split`, `fortune`, `randomize`, `life_link`). Spells are ordered `Form → Effect (+Augs) → Effect (+Augs)…`. Augments attach to the preceding effect.

### Source and mana
Two parallel pools:
- **Personal (caster) mana** — Forge capability `IManaCap` (`com.hollingsworth.arsnouveau.api.mana.IManaCap`). Max mana backed by `PerkAttributes.MAX_MANA` + `FLAT_MANA_BONUS` + unlocked-glyph bonus; base ~100. Regen backed by `PerkAttributes.MANA_REGEN_BONUS`. Mana Boost enchantment +25/level max; Mana Regen enchantment -1/-2/-3 ticks.
- **Source (world mana)** — stored in Source Jars (~10,000 cap). Generated by Sourcelinks (Volcanic, Agronomic, Mycelial, Mire, Alchemical, Vitalic). Transferred by Source Relays (10-block default range, configured with the Dominion Wand).

### Spell schools (`SpellSchools`)
`MANIPULATION`, `CONJURATION`, `NECROMANCY`, `ABJURATION`, `ELEMENTAL_FIRE`, `ELEMENTAL_WATER`, `ELEMENTAL_EARTH`, `ELEMENTAL_AIR`. Each `AbstractSpellPart` declares `getSchools()`; **schools are class-level hard-coded in base Ars** (not JSON-data-driven; addons hook them). Schools have no built-in resistance tags in base — Ars Elemental adds per-school damage/DR.

### Thread / Perk system (CRITICAL for Runic Skills non-duplication)
Threads are armor-slotted passive perks. Craft: Blank Thread (magebloom fiber + gold) + Glyph at Scribes Table → Perk Thread. Apply at Alteration Table. **Per-set stacking: a single thread ID can only be slotted once across the whole armor set** (enforced in `PerkUtil#canPutPerkInSlot`). Slot level scales the effect (1 / 2 / 3).

Base perks: **Thread of Spell Power** (Potency — boosts `PerkAttributes.SPELL_DAMAGE_BONUS`), **Magic Capacity** (+max mana), **Mana Regen**, **Repairing**, **Feather**, **Gliding** (T3), **Heights**, **High Step**, **Depths** (water), **Chilling**, **Kindling**, **Immolation**, **Life Drain**, **Undying** (totem revive), **Warding** (DR via `PerkAttributes.WARDING`), **Thread of the Starbuncle** (+speed), **Thread of the Whirlisprig**, **Thread of the Drygmy** (loot), **Thread of the Wixie** (chance free cast), **Thread of the Amethyst Golem**, **Reactive** (reflect), **XP Boost / Brilliance**, **Looting**.

Armor tiers (per piece): T1 = +30 max mana, +1 regen, 1 slot (L1); T2 = +30/+1/+1 L2 slot cumulative; T3 = +30/+1/+1 L3 slot cumulative. Full T3 Archmage: +360 max mana, +12 regen, 12 slots. Archmage set bonus ≈ −15–25% spell cost.

### Spell cost
`SpellCost = max(0, round(Σ glyph.manaCost × (1 − setDiscount)) − flatDiscounts)`. **Potency ≠ cost reduction** — Potency is damage. Cost reduction comes from Archmage set bonus, Lesser/Greater Discount Rings, Belt of Levitation. Cost **can reach 0** with full Archmage + both Discount Rings on high-cost spells; this is an intentional endgame and a balance trap.

### Events and API
`SpellCastEvent` (pre-resolve, cancellable), `SpellResolveEvent.Pre/Post` (per-glyph), `SpellDamageEvent` (mutable damage), `EffectResolveEvent`, `SpellProjectileHitEvent`, `BookCastedEvent`, `RitualEvent.Pre/Post`, `SourceEvent`. Core classes: `SpellResolver`, `SpellContext`, `ISpellCaster`, `PerkRegistry` (register perks at common setup), `GlyphRegistry`, `ArsNouveauRegistries.PERK_REGISTRY` (1.20.1 DeferredRegister-compatible).

### Familiars
Starbuncle (+speed), Whirlisprig/Sylph (fall-dmg reduction + crop aura), Drygmy (passive loot), Wixie (chance free cast), Bookwyrm (regen), Amethyst Golem (amethyst automation). Unlocked via **Ritual of Binding**.

---

## 4. Ars 'n Spells bridge — synergy reference

**Author**: Otectus. Modid: `ars_n_spells` (NBT sometimes `arsnspells:`). Deps: Ars Nouveau 4.12.7, Iron's Spellbooks 3.15.5.1.

### Core bridging behavior
`mana_unification_mode` has five values: **`iss_primary`** (Ars drains ISS mana — the canonical Runecraft default), `ars_primary` (ISS drains Ars), `hybrid` (shared pool, pick HUD bar), `separate` (independent with split-cost percentages), `off`. Cross-mod casts use `conversion_rate_ars_to_iron` / `conversion_rate_iron_to_ars`.

**Attribute routing**: In `iss_primary`/`hybrid`, Ars armor perks (Magic Capacity, Mana Regen, Spell Power thread) route their bonuses into ISS attributes (max_mana, mana_regen, spell_power). In `ars_primary`, ISS gear perks feed Ars calculations. A `spell_power_cap` (default 3.0) prevents runaway stacking.

**Spell Transcription ritual** (`ars_n_spells:spell_transcription`): drop an Ars parchment/spellbook/focus OR an ISS scroll near a brazier plus a target item → target gets `arsnspells:cross_spells` NBT, right-clicks to cast, sneak-right-click cycles stacked inscriptions. Any item can become a caster.

**School translation**: `SpellAnalysis` utility reclassifies Ars glyphs by their first element tag into ISS schools (Fire, Ice, Lightning, Holy, Ender, Blood, Evocation, Nature, Eldritch, plus Aqua/Geo/Wind/Dormant for addon compat). When the Ars spell's first glyph is "fire-typed" it draws from `irons_spellbooks:fire_spell_power` when computing damage.

**Extras**: Resonance (mana > threshold → ISS spell damage bonus), unified cooldown categories (opt-in), Source Jar proximity multiplier on ISS mana regen (`source_jar_synergy_multiplier` default 5.0), cross-mod school XP/affinity, optional Covenant of the Seven integration (Ring of Seven Curses/Virtues, 13 Blasphemy curios with 15%+10% school-match discount).

### Design implication for Runic Skills
- **Treat ISS mana as canonical.** Perks that touch mana should read/write `MagicData` and the `irons_spellbooks:max_mana`/`mana_regen` attributes; Ars 'n Spells handles the Ars side automatically.
- **Do not register duplicate Ars armor perks** — they would stack with Ars 'n Spells' routing and over-buff.
- **Reference school power attributes by ResourceLocation** so Ars 'n Spells' `SpellAnalysis` classification picks them up transparently.

---

## 5. Iron's Botany — synergy reference

**Author**: Otectus. Slug `irons-botany`. Released late March 2026 (~700 DL as of April 2026). Very new. Public description: *"Botania mana power spells while spells can also interact with Botania systems. New school, new spells, new armor."*

**Confirmed**: Botania mana is a valid ISS cast resource (bridged analogously to how Ars 'n Spells bridges Source). New ISS magic **school** (likely "Flora" or equivalent — the name and attribute IDs are unverified from public sources but will follow the pattern `irons_spellbooks:<school>_spell_power` / `<school>_magic_resist`). New armor set follows the standard ISS school-armor pattern (+school_power, +max_mana, +cooldown_reduction or +cast_time_reduction per piece).

**Probable hooks** (by analogy to Ars 'n Spells): Botania `IManaItem`/`IManaPool`/`IManaReceiver` capability hooks to tap tablets/pools; a manager that routes cast costs through `ChangeManaEvent`; config-driven behavior toggles.

**Runic Skills should**: gate Iron's Botany-specific perks with `ModList.isLoaded("irons_botany")`; reference the new school by ResourceLocation rather than hard-coding the name; treat Botania mana as an optional upstream feed for ISS mana.

---

## 6. Perk brainstorm for Runic Skills

Conventions used below: **Attribute ID / event** is the cleanest integration point. "Per rank" assumes 3–5 ranks is standard; feel free to scale. All numbers are design starting points, not final balance. Prereqs are suggested tree positions, not hard requirements.

### A. Single-mod perks

#### A1. ISS — mana, casting, and cooldowns (generic)

| Perk | Effect | Hook | Notes |
|---|---|---|---|
| **Wellspring** | +25 max mana per rank (5 ranks) | Permanent modifier on `irons_spellbooks:max_mana` | Root node; linear scaling. |
| **Quickening** | +5% `cast_time_reduction` per rank (3) | Modifier on `irons_spellbooks:cast_time_reduction` | Cap design space around vanilla 75% floor. |
| **Reservoir** | +10% mana regen per rank (3) | `irons_spellbooks:mana_regen` | Watch the regen-below-100 bug: make sure `max_mana` stays ≥ 100 with this perk slotted. |
| **Tempo** | +5% `cooldown_reduction` per rank (3) | `irons_spellbooks:cooldown_reduction` | |
| **Arcane Recovery** | Killing an entity restores mana equal to 2% of its max HP per rank (3) | `ChangeManaEvent` or `LivingDeathEvent` → `MagicData.addMana` | Scales with boss fights; consider a 50-mana cap per kill. |
| **Focus** | Taking damage during a Long/Charge cast has a 10% chance per rank (3) to not interrupt | `LivingAttackEvent` + `MagicData.getCastingSpellId()` | Competes with Antique Amulet — allow stacking or gate. |
| **Mana Shield** | 10% of incoming damage per rank (2) redirected to mana at 2:1 mana:HP conversion | `LivingHurtEvent` → drain mana | Archetype-defining tank-caster perk. |
| **Second Wind** | When mana hits 0, instantly restore 40% max mana. 120-second cooldown. | `ChangeManaEvent` when new ≤ 0 | Capstone perk. |
| **Mana Surge** | Below 25% HP: +20% spell power and +30% regen | `LivingTickEvent` attribute swap via transient modifiers | Berserker theme. |
| **Spellweaver** | Every 5th cast within 10 seconds costs 0 mana | `SpellPreCastEvent` → set mana cost 0 | Combo-play enabler. |
| **Resonant Casting** | While above 95% mana: +10% spell power (stacks with Ars 'n Spells Resonance) | `SpellDamageEvent` | Already partly exists via Ars 'n Spells; this perk deepens it. |
| **Imbued Focus** | +1 effective spell level on the last spell in the spell wheel | `ModifySpellLevelEvent` | |
| **Quickcast** | Instant-type spells have their cooldown reduced by 15% per rank (3) | `SpellCooldownEvent` → filter by `CastType.INSTANT` | Type-specific CDR. |
| **Long Channel** | Long-cast spells deal +10% damage per rank (3) | `SpellDamageEvent` → filter by `CastType.LONG` | |
| **Continuous Flow** | Continuous-cast spells have -20% mana-per-tick cost | `ChangeManaEvent` → check `MagicData.getCastType()` | |
| **Charge Mastery** | Charge spells always release at full power regardless of actual charge time | `SpellPreCastEvent` → override charge value | Capstone. |

#### A2. ISS — per-school specialist perks (identical template across 9 schools)

For each school `X` in {Fire, Ice, Lightning, Holy, Ender, Blood, Evocation, Nature, Eldritch}, offer three perks:

| Perk template | Effect | Hook |
|---|---|---|
| **X-mancer** | +10%/+20%/+30% `irons_spellbooks:X_spell_power` (3 ranks) | Attribute modifier |
| **X-Warded** | +10%/+20%/+30% `irons_spellbooks:X_magic_resist` (3 ranks) | Attribute modifier |
| **X Catalyst** | Spells of school X have 15% chance to apply the school's signature effect for +2 seconds | `SpellOnCastEvent` with school check |

Signature-effect table for the X Catalyst perk:
- Fire → Ember
- Ice → Chilled (push to Frozen at 2 stacks)
- Lightning → Charged
- Holy → Fortify
- Ender → Planar Sight
- Blood → Blooded (2 HP/s bleed for 2s)
- Evocation → Summon buff (+10% summon damage for 10s)
- Nature → Oakskin (2s)
- Eldritch → Abyssal Shroud mini-ward (0.5s invuln)

Implementation tip: keep a **`Map<ResourceLocation, PerkBinding>` in datapack-driven JSON** keyed by school ID so Iron's Botany's new school automatically gets these three perks via JSON generation.

#### A3. ISS — summon and utility perks

| Perk | Effect | Hook |
|---|---|---|
| **Lord of the Dead** | +15%/+30% `irons_spellbooks:summon_damage`; summons gain +20% HP | Attribute + `EntityJoinLevelEvent` for health |
| **Pack Caller** | Max concurrent summon count +1 (vanilla is 1 after 3.14 recast system) | `SummonManager` — requires internal access or reflection; flag as risky |
| **Life Leech Bound** | Your summons return 5% of damage dealt as mana to you | Summon damage event (check `SummonedEntitiesCastData.getSummoner()`) |
| **Eldritch Apprentice** | −50% Eldritch research XP cost | Hook into the Eldritch research system |

#### A4. Apotheosis/Apothic family — affix, gem, and combat perks

| Perk | Effect | Hook / attribute | Notes |
|---|---|---|---|
| **Gemsmith** | Each socketed gem grants +10% of its bonus rolled value | `GetAffixModifiersEvent` or `AttributeChangedValueEvent`; multiply gem modifier on equip | Data-driven: iterate `SocketHelper.getGems(stack)`. |
| **Affix Affinity** | Each Rare+ affix item equipped grants +2% damage and +2% damage reduction | `LivingTickEvent` → count via `AffixHelper.getRarity(stack)` per slot; apply transient modifier | Straightforward "set bonus" style. |
| **Socket Virtuoso** | +1 effective socket on every equipped item (read-only bonus) | `GetItemSocketsEvent` → add(1) | Pure event hook; minimal coupling. |
| **Reforger's Touch** | −30% salvaging material cost for reforging | `AffixSalvageEvent` / `AffixRerollEvent` with player-context check | Economy perk. |
| **Lucky Loot** | +25% chance for dropped loot to roll as affix items; +1 rarity tier on your kills | Wrap the global loot modifier `apotheosis:affix_loot` | Risky: global loot modifiers don't easily take player context — use an intermediary capability flag. |
| **Spawner Mage** | Spawner modifier items cost 50% less quantity when you apply them | `PlayerInteractEvent` on spawner + custom crafting hook | Niche. |
| **Critical Mastery** | +5% `attributeslib:crit_chance` and +10% `attributeslib:crit_damage` per rank (3) | Attribute modifier | Vanilla-flavored. |
| **Vampiric Fangs** | +5% `attributeslib:life_steal` per rank (3) | Attribute | |
| **Reaper's Edge** | +3% `attributeslib:current_hp_damage` per rank (3) | Attribute | Scales hard against bosses; consider capping at 15%. |
| **Evasive** | +5% `attributeslib:dodge_chance` per rank (3) | Attribute | |
| **Arrow Mastery** | +15% `attributeslib:arrow_damage` and +10% `attributeslib:arrow_velocity` per rank (3) | Attributes | Archer branch. |
| **Earthbreaker** | +20% `attributeslib:mining_speed` per rank (3) | Attribute | Utility. |
| **Scholar** | +15% `attributeslib:experience_gained` per rank (3) | Attribute | |
| **Spectral Ward** | +2 `attributeslib:prot_pierce` and +0.05 `attributeslib:prot_shred` per rank (3) | Attributes | For offense-focused builds. |
| **Ghostbound** | +4 max `attributeslib:ghost_health` per rank (5) | Attribute | Layered survival. |
| **Heart of the Healer** | +20% `attributeslib:healing_received`, +5% `attributeslib:overheal` | Attributes | Support. |
| **Enchanter's Insight** | Eterna/Arcana generated by adjacent bookshelves +5% per rank (3) (max 100 stays the cap) | `GetEnchantingPowerEvent` (Forge) | Interacts with Apothic Enchanting stat system. |
| **Library Dedication** | Enchantment Library pulls always succeed and consume 50% fewer levels | Hook into enchantment library use | Fork-specific; verify event exists. |

#### A5. Ars Nouveau — glyph, Source, and ritual perks (designed to NOT duplicate Threads)

| Perk | Effect | Hook | Notes |
|---|---|---|---|
| **Glyphsmith** | Crafting a new glyph at the Scribes Table costs 25% less XP and materials | `ScribesTableBlockEntity` recipe event — or a crafting event | Purely economy; no stat overlap. |
| **Ley Line** | +20% Source generation from Sourcelinks within 16 blocks of you per rank (3) | `SourceEvent` or tick scan around player | Position-cached every 20 ticks to avoid performance cost. |
| **Source Savant** | Source Jars within 16 blocks passively contribute +10% of your max mana as regen while standing near them (compounds with Ars 'n Spells' `source_jar_synergy_multiplier`) | Periodic tick; apply transient `irons_spellbooks:mana_regen` modifier | Makes bases meaningful without needing the Archmage set. |
| **Form Focus: Projectile** | Projectile-form spells cost 10% less Source | `SpellCastEvent` → inspect `spell.recipe.get(0)` | Distinct from Threads (which don't filter by form). |
| **Form Focus: Touch** | Touch-form spells deal +15% damage | `SpellDamageEvent` | |
| **Form Focus: Self** | Self-form buff spells last +20% longer | `SpellResolveEvent.Post` → extend effect duration | |
| **Split-Caster** | `augment_split` glyphs add +1 extra replication at no Source cost | Hook `SpellResolveEvent.Pre` on `form_projectile` + split augment | Capstone — strong! |
| **Ritualist** | Ritual brazier radius +50% per rank (2) | Modify `AbstractRitual.getRange()` via ASM/coremod OR event if one exists | Flag as implementation-risky; may need a mixin. |
| **Bookwyrm's Apprentice** | Familiar buffs remain at full strength even without the Curio equipped (passive unlock via ritual progression) | Familiar passive tick | Distinct from Threads (which boost familiars, not replace worn slot). |
| **Ars Scholar** | +1 glyph slot on the currently held Spell Book | Modify `SpellBook.getMaxSpells()` via event/mixin, or swap stack's NBT | Risky coupling; flag for Joshua. |
| **Mythical Scribe** | Drop rate of Blank Threads / Mythical Clay from Drygmys +50% | Drygmy loot generation hook | Strictly progression-speed. |
| **Enchanter-Arms** | Enchanter's Sword/Bow/Staff durability loss -50% | `ItemAttributeModifierEvent` or item damage event | |
| **Apparatus Synergy** | Enchanting Apparatus recipes cost 25% less Source | Recipe Source-cost hook | |
| **Wild Manipulation** | Manipulation-school glyphs cost 20% less Source | School-filtered `SpellCastEvent` | Elemental-school variants below. |

Per-school Ars perks (template across 8 Ars schools — mirrors ISS school structure):

| Perk template | Effect |
|---|---|
| **Hedgewitch: Water** | Ars Water (`ELEMENTAL_WATER`) glyphs cost 15% less Source and deal +10% damage |
| **Emberforged: Fire** | Ars Fire glyphs +10% damage, 20% chance to apply Ignite for +2s |
| **Stormcaller: Air** | Ars Air glyphs +15% projectile speed, +10% knockback |
| **Geomancer: Earth** | Ars Earth glyphs +25% AOE radius |
| **Necromant** | Necromancy-school glyphs deal +15% damage to undead targets — oh wait, undead are often the minions, invert this: "Necromancy summons gain +15% HP" |
| **Conjurer** | Conjuration-school glyphs cost 10% less Source and last 30% longer |
| **Abjurer** | Abjuration (healing/protection) glyphs +20% effect magnitude |
| **Arcane Weaver** | Manipulation-school glyphs chain to 1 additional target |

### B. Cross-mod synergy perks

These perks require ≥2 target mods installed; Runic Skills should gate them with `ModList.isLoaded` checks. The payoff: **perks that only work when the Runecraft ecosystem is fully assembled feel rewarding and deepen the identity of the pack.**

| Perk | Required mods | Effect | Hook |
|---|---|---|---|
| **Unified Arcana** | ISS + Ars (+ Ars 'n Spells) | While Ars 'n Spells is in `iss_primary` mode, Ars glyph casts refund 15% of the converted ISS mana | Hook `ChangeManaEvent` when `MagicData.getCastSource() == RUNE` or check an Ars 'n Spells capability flag |
| **Schoolbridge: Fire** | ISS + Ars (+ Ars 'n Spells) | Ars Fire glyphs gain the full bonus of `irons_spellbooks:fire_spell_power` (Ars 'n Spells already does this partially; perk raises the cap above `spell_power_cap`) | `SpellDamageEvent` (Ars) reading ISS attribute |
| **Schoolbridge: Ice → Water** | ISS + Ars | Ars Water/Freeze glyphs scale with `irons_spellbooks:ice_spell_power` | Same |
| **Schoolbridge: Air → Lightning** | ISS + Ars | Ars Air glyphs (especially `effect_lightning`) scale with `irons_spellbooks:lightning_spell_power` | Same |
| **Schoolbridge: Earth → Nature** | ISS + Ars | Ars Earth glyphs scale with `irons_spellbooks:nature_spell_power` | Same |
| **Schoolbridge: Necromancy → Blood** | ISS + Ars | Ars Necromancy glyphs scale with `irons_spellbooks:blood_spell_power` and summoned undead gain +10% HP from `summon_damage` | Same |
| **Schoolbridge: Abjuration → Holy** | ISS + Ars | Ars Abjuration (heal) glyphs scale with `irons_spellbooks:holy_spell_power` | Same |
| **Schoolbridge: Manipulation → Ender** | ISS + Ars | Ars Manipulation glyphs (blink, launch) scale with `irons_spellbooks:ender_spell_power` | Same |
| **Resonant Affixes** | ISS + Apotheosis | Any Apotheosis affix granting `attribute.generic.attack_damage` also contributes 50% of its % value to `irons_spellbooks:spell_power` | Iterate equipped items on tick; if `AffixHelper.hasAffixes(stack)` and the applied modifiers include attack_damage, add transient spell_power modifier |
| **Gem-Fueled Casting** | ISS + Apotheosis | Each socketed gem in armor grants +20 `max_mana`; each socketed gem in main-hand weapon grants +3% `spell_power` | Tick scan via `SocketHelper.getGems(stack)` |
| **Spellsocket** | ISS + Apotheosis + Apothic Curios | Sockets in Curios slots (ring/necklace/spellbook) grant an additional rolled school-power effect based on gem purity | `GetItemGemsEvent` + Curios inventory scan |
| **Affix Focus** | ISS + Apotheosis | Equipping 4+ Rare-or-higher affix items grants +1 effective spell level on all held spellbook spells | `ModifySpellLevelEvent` |
| **Adaptive Caster** | ISS + Apotheosis + Fallen Gems & Affixes | The highest-rolled school-power affix on equipped gear also contributes 50% of its value to every other school | `LivingEquipmentChangeEvent`; re-balance transient modifiers |
| **Botanical Conduit** | ISS + Iron's Botany | While standing on grass/leaves/Botania flora tag blocks: +20% mana regen | Tick scan of block below player |
| **Mana Tablet Reserve** | ISS + Iron's Botany (+ Botania) | A filled Botania mana tablet in inventory acts as an ISS mana battery: drains tablet to restore ISS mana at 1000 Botania mana : 10 ISS mana ratio | `ChangeManaEvent` when mana hits 0 → attempt to pull from `IManaItem` in inventory |
| **Petal Affinity** | ISS + Iron's Botany | Iron's Botany's new "flora" school spells deal +15% damage while standing near Botania's Mystical Flowers (4-block radius scan) | Tick scan; filter spells by school ID |
| **Apothic Apprentice** | Ars + Apotheosis | Ars Spell Books roll Apotheosis affixes at the Scribes Table (chance-based) | `ScribesTableBlockEntity` result hook |
| **Glyph-Imbued Gem** | Ars + Apotheosis | Socketing a gem also bestows a random Ars glyph on the spellbook in your off-hand (consumes the gem on first cast) | `ItemSocketingEvent` |
| **Triple Threat** | ISS + Ars + Apotheosis | For each of the three mods you have an active-effect equipped item from: +5% spell power, +5% mana regen, +5% max mana (so 3 mods = +15% across the board) | Tick scan — count distinct mod-origin flags on equipped ItemStacks |
| **Sourcelink Affix** | Ars + Apotheosis | Apotheosis bosses drop a Source Jar with random starting charge (1000–5000 Source) as a bonus drop at Mythic rarity | `LivingDropsEvent` filtered by `AffixHelper.getRarity` |
| **Dead King's Debt** | ISS + Apotheosis | Defeating the Dead King grants a guaranteed Mythic-rarity affix reroll token | `LivingDeathEvent` on ISS Dead King entity |
| **Spawner Sanctuary** | ISS + Apotheosis | An Apothic-captured spawner placed within 8 blocks of you grants +20% summon count (Evocation/Blood schools summon one extra minion) | Spawner block-entity proximity scan |
| **Ritualized Reforge** | Ars + Apotheosis | Performing an Ars ritual while holding an affix item adds +1 random affix (consumes 500 Source) | `RitualEvent.Post` |
| **Gem-Threaded Armor** | Ars + Apotheosis | Gems socketed in Ars armor pieces also count their bonus value toward `PerkAttributes.SPELL_DAMAGE_BONUS` | Custom event subscription on equipment change |
| **Arcane Syncretism** | All 3 + Ars 'n Spells | Capstone. Ars glyphs, ISS spells, and Apotheosis affix triggers all feed a shared "Arcane Momentum" resource (max 100). At 100, your next cast is a free Amplify+AOE guaranteed crit. | Custom capability; fire on `SpellOnCastEvent`, `SpellResolveEvent.Post`, `ApothAttackEvent`. |

---

## 7. Implementation notes, coupling warnings, and design philosophy

**Use data-driven registration by default.** Every school, attribute, and damage type mentioned above is a ResourceLocation. Runic Skills should store perk-to-attribute bindings in datapack JSON (under `data/runic_skills/perks/*.json`) rather than hard-coding class references, because:
- Iron's Botany adds a new ISS school with a new power attribute — hard-coding the 9 schools breaks.
- Apothic family modid changed from `attributeslib` (1.20.1) to `apothic_attributes` (1.21+) — datapack-driven means a single JSON edit fixes a port.
- Ars Nouveau schools (8 base) may be expanded by Ars Elemental addons.

**Clean integration hooks**:
- **ISS**: subscribe to `SpellPreCastEvent` (for cost/level tweaks), `SpellOnCastEvent` (for post-cast triggers), `ChangeManaEvent` (for mana redistribution), `SpellDamageEvent` (for damage multipliers), `ModifySpellLevelEvent` (for level boosts). Use `MagicData.getPlayerMagicData(player)` to read current state. The stable API is in `io.redspace.ironsspellbooks.api.*` — anything under there is guaranteed not to break between 3.x patches.
- **Apotheosis**: subscribe to `GetItemSocketsEvent`, `GetItemGemsEvent`, `AffixModifierEvent`, `AffixRerollEvent`, `AffixSalvageEvent`, `AttributeChangedValueEvent`. Read affix data with `AffixHelper.hasAffixes(stack)`, `AffixHelper.getAffixes(stack)`, `AffixHelper.getRarity(stack)`. **Avoid calling these from inside `Item#getAttributeModifiers`** — it causes a StackOverflow via the EnchantmentHelper mixin (issue #1157). Apply perk bonuses in `LivingEquipmentChangeEvent` instead.
- **Ars Nouveau**: subscribe to `SpellCastEvent`, `SpellResolveEvent.Pre/Post`, `SpellDamageEvent`, `RitualEvent`, `SourceEvent`, `BookCastedEvent`. Register new perks through `ArsNouveauRegistries.PERK_REGISTRY` (1.20.1 Forge DeferredRegister) if you want them slottable in Ars armor; otherwise keep them in your own skill-tree capability for cleaner UX.
- **Ars 'n Spells**: does not expose a public API beyond its config. Detect with `ModList.isLoaded("ars_n_spells")` and key off its effect (Ars spells consuming ISS mana) via `ChangeManaEvent` — no direct API hook is needed.

**Risky couplings — flag for Joshua**:
1. **Summon count limits** (ISS 3.14+ one-batch-per-spell via `SummonManager`): perks that extend concurrent summons need internal-package access. Treat as brittle.
2. **Ars perk-registry migration** between 1.19 and 1.20.1 (Map-backed → DeferredRegister): ensure Runic Skills' Ars integration targets the 1.20.1 API explicitly.
3. **Apotheosis Gem Facets removal in 7.x**: older tutorials still show the `facets` NBT tag, which is gone. Use single-facet purity-driven reads only.
4. **Modid rename across MC versions**: `attributeslib` (1.20.1) → `apothic_attributes` (1.21+). Runic Skills' datapack bindings should prefix the 1.20.1 ID in JSON.
5. **Spell book slot modification** ("Ars Scholar" perk above): `SpellBook.getMaxSpells()` isn't a simple event; needs a mixin or NBT trickery. Consider scrapping in favor of an orthogonal perk.
6. **Ars cost can reach 0** with full Archmage + Discount Rings. Cost-reduction perks in Runic Skills should be flat-cap-floored (minimum 1) or use damage-side multipliers instead.

**Perks that could double-dip with existing systems** (cap, gate, or scale down):
- Any `spell_power` / `max_mana` / `mana_regen` boost overlaps with Archmage set bonus, ISS upgrade orbs, Ars Thread of Spell Power/Magic Capacity/Mana Regen, and (if Ars 'n Spells is in `iss_primary`) Ars's routing of its own perks into ISS attributes. **Recommended**: cap each stat at +100% from perks, enforce additive stacking with all sources via `Operation.MULTIPLY_TOTAL` or a transient cap clamp.
- Warding vs. school-resist perks: Ars's Thread of Warding is generic DR; Runic Skills' school resists are scoped. Fine to stack.
- Feather/Depths/Heights/High Step/Gliding: **do not duplicate**. Players get those from Ars Threads already; Runic Skills should not offer them. If Joshua wants movement perks, reach for something unique (e.g., spell-triggered teleport proc, spell-on-sprint effect).
- Life Steal: Apothic `life_steal` and ISS Ray of Siphoning both provide this. Runic Skills' Vampiric Fangs uses the Apothic attribute, so it stacks naturally.

**Philosophical direction** (based on the Otectus catalog):
1. **JSON-first.** Otectus's mods (Locks Reforged, RPG Lore, Ars 'n Spells) all lean on datapack/TOML configuration and ResourceLocation-keyed registries. Runic Skills perks should be JSON files under `data/runic_skills/perks/` with fields: `id`, `display_name`, `description`, `prerequisite_perks[]`, `max_rank`, `cost_per_rank`, `effects[]` (each effect: `type: "attribute_modifier" | "event_listener" | "capability_flag"`, `target`, `operation`, `value_per_rank`).
2. **Mode toggles.** Otectus consistently uses enum-selector configs (`mana_unification_mode`, `lp_source_mode`). Runic Skills should expose a few master toggles: `cross_mod_perks_enabled`, `double_dip_cap_mode` (none/soft/hard), `school_attribute_source` (iss/bridged/both).
3. **Graceful fallback.** Every mod in the Otectus catalog degrades cleanly when a dependency is missing. Runic Skills must hide or no-op cross-mod perks when the target mod isn't loaded, not crash.
4. **Complement, don't duplicate.** The Ars Thread system and ISS Upgrade Orbs already cover passive stat sticks. Runic Skills' unique niche is **active/proc abilities, cross-mod synergies, skill-tree prerequisites, and school specialization** — precisely the three categories where existing mods are thin.

**Priority perk categories for initial implementation** (if Joshua ships in slices):
1. **Phase 1**: The 9-school specialist triplet for ISS (27 perks via JSON templates) + 6 global ISS casting perks (Wellspring, Reservoir, Tempo, Quickening, Arcane Recovery, Spellweaver) = ~33 perks, one JSON generator, minimal custom code.
2. **Phase 2**: Apothic Attributes combat perks (13 perks, all pure attribute modifiers) + Ars form/school perks (12 perks, mostly event-filtered) = ~25 more perks.
3. **Phase 3**: Cross-mod synergy perks (the 8 Schoolbridge perks + 10 big synergy perks), which require the most testing and gating logic.
4. **Phase 4**: Iron's Botany-specific perks (wait until Iron's Botany is stable and docs exist).

---

## Conclusion — what this research changes about the design

Three findings shift the design envelope. **First**, the cleanest, most future-proof implementation treats all perks as data-driven JSON bindings to ResourceLocation-keyed attributes and events, not as hard-coded Java effects — because every target mod has extensible registries (schools, affixes, glyphs) and at least one has already been renamed across MC versions. **Second**, Ars Nouveau's existing Thread Perk system already occupies the "passive armor-bound stat buff" niche, so Runic Skills maximizes design leverage by pushing into active/conditional/tree-gated space and into cross-mod synergies where no existing system operates — the Schoolbridge perks and the Gem-Fueled Casting / Resonant Affixes family are particularly high-value because no published mod does this today. **Third**, because Ars 'n Spells already routes Ars Source and armor perks into ISS attributes in its default mode, Runic Skills can safely assume ISS mana is canonical and build every cross-system perk around `irons_spellbooks:*` attribute reads — making the perk system degrade to "ISS-only" gracefully when Ars/Apotheosis/Botany aren't installed. The combined effect: a perk catalog of ~70 designs that feels integrated, is mostly pure-attribute or pure-event code, and plugs into the Runecraft ecosystem without stepping on any existing progression system.