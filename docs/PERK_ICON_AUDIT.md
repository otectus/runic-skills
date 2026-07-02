# Perk Icon Audit — 1.5.x Icon Overhaul

Every registered perk icon was replaced with an original, generated 16x16 pixel-art
texture in a consistent Runic Skills visual style (shared outline color, 3-tone cel
shading, per-theme palettes, corner badge overlays encoding the effect modifier:
`up` = increase, `plus` = flat bonus, `sparkle` = chance/proc, `down` = reduction,
`X` = immunity/never, shield = protection, clock = duration/speed, coin = drops/loot,
elemental minis = magic school).

- **461 perk icons** generated (379 replaced existing PNGs, 82 new PNGs replacing
  borrowed Iron's Spellbooks / Ars Nouveau / Apotheosis item sprites).
- All perk textures now live at `assets/runicskills/textures/skill/<skill>/<perk_id>.png`,
  named exactly after the perk registry id.
- `HandlerResources` no longer references any foreign mod namespace for perk icons;
  the `ironsItem`/`arsItem`/`apothItem` helpers were removed.
- 21 orphaned PNGs from removed perk sets (old Blood Magic / Enigmatic Legacy content
  and two stale `passive_*` duplicates under `wisdom/`) were deleted.
- Passive-attribute icons (`passive_*.png`), locked-tier icons (`locked_*.png`),
  `null_skill.png` and `icons.png` are not perk icons and were left untouched.
- Guarded by `PerkTextureResolutionTest` (every referenced texture resolves on disk,
  no foreign namespaces, every registered perk id has a matching icon).

Regeneration source (glyph library + per-perk specs + generator) lives in
`tools/icongen/`; see its README for how to regenerate or restyle the set.

## Passive icons (38)

The Skills UI shows Passives ahead of perks in each skill page's grid; all 38
`passive_*.png` textures were regenerated in the same style (specs in
`tools/icongen/specs_passives.tsv`, filenames unchanged — including the two
irregular names `passive_luck.png` (id `fortune`) and `passive_ars_mana.png`
(id `ars_flat_mana`)).

| Passive ID | Skill | Name | Icon concept | Texture path | Status |
|---|---|---|---|---|---|
| `block_reach` | building | Block Reach | Builder's hand — block reach | `textures/skill/building/passive_block_reach.png` | ✅ generated, resolves |
| `break_speed` | building | Break Speed | Worn pick — block break speed | `textures/skill/building/passive_break_speed.png` | ✅ generated, resolves |
| `mining_speed` | building | Mining Speed | Powered pick — mining speed | `textures/skill/building/passive_mining_speed.png` | ✅ generated, resolves |
| `healing_received` | constitution | Healing Received | Mended heart — healing received | `textures/skill/constitution/passive_healing_received.png` | ✅ generated, resolves |
| `knockback_resistance` | constitution | Knockback Resistance | Braced anchor — resist knockback | `textures/skill/constitution/passive_knockback_resistance.png` | ✅ generated, resolves |
| `max_health` | constitution | Max Health | Full heart — maximum health | `textures/skill/constitution/passive_max_health.png` | ✅ generated, resolves |
| `swim_speed` | constitution | Swim Speed | Swift fish — swim speed | `textures/skill/constitution/passive_swim_speed.png` | ✅ generated, resolves |
| `arrow_velocity` | dexterity | Arrow Velocity | Swift volley — arrow velocity | `textures/skill/dexterity/passive_arrow_velocity.png` | ✅ generated, resolves |
| `draw_speed` | dexterity | Draw Speed | Limber bow — draw speed | `textures/skill/dexterity/passive_draw_speed.png` | ✅ generated, resolves |
| `movement_speed` | dexterity | Movement Speed | Runner's boot — movement speed | `textures/skill/dexterity/passive_movement_speed.png` | ✅ generated, resolves |
| `projectile_damage` | dexterity | Projectile Damage | Sharp arrow — projectile damage | `textures/skill/dexterity/passive_projectile_damage.png` | ✅ generated, resolves |
| `armor` | endurance | Armor | Plate armor — damage nullified | `textures/skill/endurance/passive_armor.png` | ✅ generated, resolves |
| `armor_toughness` | endurance | Armor Toughness | Tough helm — mitigate strong attacks | `textures/skill/endurance/passive_armor_toughness.png` | ✅ generated, resolves |
| `ars_warding` | endurance | Arcane Warding | Source ward — magical protection | `textures/skill/endurance/passive_ars_warding.png` | ✅ generated, resolves |
| `dodge_chance` | endurance | Dodge Chance | Fleet wing — dodge chance | `textures/skill/endurance/passive_dodge_chance.png` | ✅ generated, resolves |
| `crit_chance` | fortune | Critical Chance | Lucky mark — critical chance | `textures/skill/fortune/passive_crit_chance.png` | ✅ generated, resolves |
| `critical_damage` | fortune | Critical Damage | Marked target — critical damage | `textures/skill/fortune/passive_critical_damage.png` | ✅ generated, resolves |
| `fortune` | fortune | Fortune | Gilded clover — luck | `textures/skill/fortune/passive_luck.png` | ✅ generated, resolves |
| `ars_flat_mana` | intelligence | Mana Reserve | Mana flask — Ars mana reserve | `textures/skill/intelligence/passive_ars_mana.png` | ✅ generated, resolves |
| `attack_speed` | intelligence | Attack Speed | Quick blades — hand cooldown | `textures/skill/intelligence/passive_attack_speed.png` | ✅ generated, resolves |
| `entity_reach` | intelligence | Entity Reach | Extended hand — attack reach | `textures/skill/intelligence/passive_entity_reach.png` | ✅ generated, resolves |
| `experience_gained` | intelligence | Experience Gained | Gilded book — experience gained | `textures/skill/intelligence/passive_experience_gained.png` | ✅ generated, resolves |
| `max_mana` | intelligence | Max Mana | Brimming drop — maximum mana | `textures/skill/intelligence/passive_max_mana.png` | ✅ generated, resolves |
| `ars_spell_damage` | magic | Spell Damage Bonus | Source sigil — Ars spell damage | `textures/skill/magic/passive_ars_spell_damage.png` | ✅ generated, resolves |
| `beneficial_effect` | magic | Beneficial Effect | Tonic — positive effects last longer | `textures/skill/magic/passive_beneficial_effect.png` | ✅ generated, resolves |
| `cold_damage` | magic | Cold Damage | Biting frost — bonus cold damage | `textures/skill/magic/passive_cold_damage.png` | ✅ generated, resolves |
| `fire_damage` | magic | Fire Damage | Searing flame — bonus fire damage | `textures/skill/magic/passive_fire_damage.png` | ✅ generated, resolves |
| `magic_resist` | magic | Magic Resist | Arcane buckler — magic damage down | `textures/skill/magic/passive_magic_resist.png` | ✅ generated, resolves |
| `spell_power` | magic | Spell Power | Charged wand — spell power | `textures/skill/magic/passive_spell_power.png` | ✅ generated, resolves |
| `armor_pierce` | strength | Armor Pierce | Split plate — armor pierced | `textures/skill/strength/passive_armor_pierce.png` | ✅ generated, resolves |
| `attack_damage` | strength | Attack Damage | Bared blade — physical damage dealt | `textures/skill/strength/passive_attack_damage.png` | ✅ generated, resolves |
| `attack_knockback` | strength | Attack Knockback | Heavy fist — knockback dealt | `textures/skill/strength/passive_attack_knockback.png` | ✅ generated, resolves |
| `life_steal` | strength | Life Steal | Draining fangs — lifesteal | `textures/skill/strength/passive_life_steal.png` | ✅ generated, resolves |
| `crafting_luck` | tinkering | Crafting Luck | Lucky gear — bonus craft output | `textures/skill/tinkering/passive_crafting_luck.png` | ✅ generated, resolves |
| `repair_efficiency` | tinkering | Repair Efficiency | Efficient anvil — cheaper repairs | `textures/skill/tinkering/passive_repair_efficiency.png` | ✅ generated, resolves |
| `cast_time_reduction` | wisdom | Cast Time Reduction | Draining glass — cast time down | `textures/skill/wisdom/passive_cast_time_reduction.png` | ✅ generated, resolves |
| `enchanting_power` | wisdom | Enchanting Power | Charged spellbook — enchanting power | `textures/skill/wisdom/passive_enchanting_power.png` | ✅ generated, resolves |
| `xp_bonus` | wisdom | XP Bonus | Bright orb — bonus XP | `textures/skill/wisdom/passive_xp_bonus.png` | ✅ generated, resolves |

## Perk icons (461)

| Perk ID | Skill | Source | Gameplay summary | Icon concept | Texture path | Status |
|---|---|---|---|---|---|---|
| `architect` | building | base | Placed blocks gain N bonus hardness. | Reinforced house — placed blocks gain hardness | `textures/skill/building/architect.png` | ✅ generated, resolves |
| `blast_mining` | building | base | TNT mining yields N more drops. | TNT with loot badge — TNT mining yields more drops | `textures/skill/building/blast_mining.png` | ✅ generated, resolves |
| `bridge_builder` | building | base | Block placement reach increased by N blocks. | Bridge with reach-up arrow — placement reach | `textures/skill/building/bridge_builder.png` | ✅ generated, resolves |
| `colony_builder` | building | base | Colony build speed increased by N. | Timber house with clock — colony build speed | `textures/skill/building/colony_builder.png` | ✅ generated, resolves |
| `construction_haste` | building | base | Block placement speed increased by N. | Builder's hammer speeding up placement | `textures/skill/building/construction_haste.png` | ✅ generated, resolves |
| `convergence` | building | base | Crafting some items will return part of the materials spent, the probability is N | Crafting gear refunding materials | `textures/skill/building/convergence.png` | ✅ generated, resolves |
| `deep_core_mining` | building | base | Mining below Y=0 yields N more resources. | Dark deepslate pick with bonus drops | `textures/skill/building/deep_core_mining.png` | ✅ generated, resolves |
| `dimensional_builder` | building | base | Building in other dimensions is N faster. | Ender-hued house — faster building in other dimensions | `textures/skill/building/dimensional_builder.png` | ✅ generated, resolves |
| `earthbreaker` | building | Apotheosis/Apothic | Stone parts before you. +N mining speed. | Stone-parting pick — bonus mining speed | `textures/skill/building/earthbreaker.png` | ✅ generated, resolves |
| `efficient_miner` | building | base | Mining speed increased by N. | Classic pick with speed-up arrow | `textures/skill/building/efficient_miner.png` | ✅ generated, resolves |
| `explosive_expert` | building | base | Your controlled explosions deal no terrain damage. | Bomb with no-terrain-damage mark | `textures/skill/building/explosive_expert.png` | ✅ generated, resolves |
| `farmers_hand` | building | base | Crop growth speed increased by N. | Golden wheat growing faster | `textures/skill/building/farmers_hand.png` | ✅ generated, resolves |
| `fortune_miner` | building | base | Mining has a N chance for bonus ore drops. | Gold-flecked ore with lucky sparkle | `textures/skill/building/fortune_miner.png` | ✅ generated, resolves |
| `foundation_layer` | building | base | Blocks placed on bedrock level gain N hardness. | Bedrock-dark wall hardened | `textures/skill/building/foundation_layer.png` | ✅ generated, resolves |
| `glowstone_sight` | building | base | Mining generates temporary light around you. | Radiant torch — mining light aura | `textures/skill/building/glowstone_sight.png` | ✅ generated, resolves |
| `heritage_builder` | building | base | Colony structures gain N bonus durability. | Sandstone tower with durability shield | `textures/skill/building/heritage_builder.png` | ✅ generated, resolves |
| `irrigation_expert` | building | base | Water-adjacent crops grow N faster. | Watered crops with droplet | `textures/skill/building/irrigation_expert.png` | ✅ generated, resolves |
| `lumberjack` | building | base | Tree chopping speed increased by N. | Felling axe with speed arrow | `textures/skill/building/lumberjack.png` | ✅ generated, resolves |
| `mass_production` | building | base | Bulk crafting operations are N more efficient. | Industrial gear with bonus | `textures/skill/building/mass_production.png` | ✅ generated, resolves |
| `master_breaker` | building | base | All block breaking speed increased by N. | Heavy maul breaking all blocks faster | `textures/skill/building/master_breaker.png` | ✅ generated, resolves |
| `master_mason` | building | base | Stone blocks are placed N faster. | Stone wall placed faster | `textures/skill/building/master_mason.png` | ✅ generated, resolves |
| `master_woodworker` | building | base | Wood planks yield N more from logs. | Planks with bonus yield | `textures/skill/building/master_woodworker.png` | ✅ generated, resolves |
| `medieval_architecture` | building | base | Medieval building blocks crafted yield N bonus. | Castle tower with bonus yield | `textures/skill/building/medieval_architecture.png` | ✅ generated, resolves |
| `obsidian_smasher` | building | base | Even the hardest block now does not compare to your mining ability, now you will be N times faster when cru... | Obsidian-crushing maul | `textures/skill/building/obsidian_smasher.png` | ✅ generated, resolves |
| `ore_detector` | building | base | Nearby ores glow through walls, revealing their location. | Ore with watchful eye — ores glow through walls | `textures/skill/building/ore_detector.png` | ✅ generated, resolves |
| `prospector` | building | base | You have a N chance to find bonus minerals while mining. | Copper pick finding bonus minerals | `textures/skill/building/prospector.png` | ✅ generated, resolves |
| `quarry_master` | building | base | Mining in deep layers yields N more drops. | Quarry pick with deep-layer drops | `textures/skill/building/quarry_master.png` | ✅ generated, resolves |
| `reinforced_construction` | building | base | Buildings take N less explosion damage. | Steel-braced wall vs explosions | `textures/skill/building/reinforced_construction.png` | ✅ generated, resolves |
| `resource_efficiency` | building | base | Breaking crafting blocks returns N of materials. | Planks returning materials | `textures/skill/building/resource_efficiency.png` | ✅ generated, resolves |
| `runic_mining` | building | base | Runic ore drops increased by N. | Runic ore with bonus drops | `textures/skill/building/runic_mining.png` | ✅ generated, resolves |
| `runic_salvager` | building | base | Chance for bonus materials when salvaging items at the Apotheosis Salvaging Table. | Arcane salvaging anvil with bonus chance | `textures/skill/building/runic_salvager.png` | ✅ generated, resolves |
| `salvage_expert` | building | base | Breaking crafted blocks returns N of materials. | Grindstone returning materials | `textures/skill/building/salvage_expert.png` | ✅ generated, resolves |
| `scaffold_master` | building | base | Scaffolding placement speed increased by N. | Scaffolding placed faster | `textures/skill/building/scaffold_master.png` | ✅ generated, resolves |
| `silk_touch_mastery` | building | base | You have a N chance for silk touch drops without the enchantment. | Gentle hand — silk-touch chance | `textures/skill/building/silk_touch_mastery.png` | ✅ generated, resolves |
| `smelter` | building | base | Smelting speed increased by N. | Furnace smelting faster | `textures/skill/building/smelter.png` | ✅ generated, resolves |
| `stone_cutter_efficiency` | building | base | Stonecutter recipes yield N bonus output. | Stonecutter wheel with bonus output | `textures/skill/building/stone_cutter_efficiency.png` | ✅ generated, resolves |
| `structural_engineer` | building | base | Multi-block structures build N faster. | Blueprint — multiblock builds faster | `textures/skill/building/structural_engineer.png` | ✅ generated, resolves |
| `terraformer` | building | base | Shovel and hoe speed increased by N. | Shovel/hoe working faster | `textures/skill/building/terraformer.png` | ✅ generated, resolves |
| `treasure_hunter` | building | base | Find buried treasure, the probability is N when dig in any type of dirt. | Buried chest with lucky sparkle | `textures/skill/building/treasure_hunter.png` | ✅ generated, resolves |
| `underground_explorer` | building | base | Move N faster in caves below Y=30. | Cave boot under a dark moon — faster below Y=30 | `textures/skill/building/underground_explorer.png` | ✅ generated, resolves |
| `vein_miner` | building | base | Mine entire veins of connected ore at once. | Connected ore vein mined at once | `textures/skill/building/vein_miner.png` | ✅ generated, resolves |
| `anglers_bounty` | constitution | base | Fish you consume provide N more nourishment. | Hearty fish — more nourishment | `textures/skill/constitution/anglers_bounty.png` | ✅ generated, resolves |
| `armor_of_faith` | constitution | base | When below N health, gain Resistance I. | Blessed armor — low-health resistance | `textures/skill/constitution/armor_of_faith.png` | ✅ generated, resolves |
| `athletics` | constitution | base | Your breathing will increase by N, now you will be able to breathe underwater longer. | Air bubbles — longer breath underwater | `textures/skill/constitution/athletics.png` | ✅ generated, resolves |
| `aura_of_vitality` | constitution | base | Nature's Aura healing effects are N stronger near you. | Nature-touched heart — aura healing stronger | `textures/skill/constitution/aura_of_vitality.png` | ✅ generated, resolves |
| `battle_recovery` | constitution | base | After 5 seconds without taking damage, regenerate N HP per second. | Heart regenerating after combat lull | `textures/skill/constitution/battle_recovery.png` | ✅ generated, resolves |
| `colonial_nourishment` | constitution | base | Colony food items provide N bonus effects. | Colony meal with bonus effects | `textures/skill/constitution/colonial_nourishment.png` | ✅ generated, resolves |
| `culinary_expert` | constitution | base | Farmer's Delight food provides N bonus saturation. | Roast with bonus saturation | `textures/skill/constitution/culinary_expert.png` | ✅ generated, resolves |
| `draconic_constitution` | constitution | base | Dragon blood flowing through you grants N resistance to elemental damage. | Dragon-blood ward vs elements | `textures/skill/constitution/draconic_constitution.png` | ✅ generated, resolves |
| `dragon_heart` | constitution | base | Dragon hearts restore N additional HP when consumed. | Fiery dragon heart restoring HP | `textures/skill/constitution/dragon_heart.png` | ✅ generated, resolves |
| `enderium_resilience` | constitution | base | Enderium items grant N magic resistance. | Enderium plate with magic ward | `textures/skill/constitution/enderium_resilience.png` | ✅ generated, resolves |
| `explorers_vigor` | constitution | base | Take N less damage while inside dungeons. | Dungeon map with damage ward | `textures/skill/constitution/explorers_vigor.png` | ✅ generated, resolves |
| `fire_resistance` | constitution | base | Your constitution reduces fire damage taken by N. | Flame with damage-down mark | `textures/skill/constitution/fire_resistance.png` | ✅ generated, resolves |
| `frost_walker_constitution` | constitution | base | Cold and freezing damage is reduced by N. | Snowflake with cold-damage-down | `textures/skill/constitution/frost_walker_constitution.png` | ✅ generated, resolves |
| `ghostbound` | constitution | Apotheosis/Apothic | A spirit-layer clings to your frame. +N ghost health. | Spirit layer granting ghost health | `textures/skill/constitution/ghostbound.png` | ✅ generated, resolves |
| `gourmet` | constitution | base | Cooked meals restore N more hunger. | Gourmet roast restoring more hunger | `textures/skill/constitution/gourmet.png` | ✅ generated, resolves |
| `heart_of_the_healer` | constitution | Apotheosis/Apothic | Mend yourself faster than the world can cut you. +N healing received and +N overheal. | Radiant heart — healing received and overheal | `textures/skill/constitution/heart_of_the_healer.png` | ✅ generated, resolves |
| `hearty_feast` | constitution | base | Food effects last N longer. | Feast pot — food effects last longer | `textures/skill/constitution/hearty_feast.png` | ✅ generated, resolves |
| `iron_stomach` | constitution | base | Your hardened constitution renders you immune to food poisoning. | Iron apple — immune to food poisoning | `textures/skill/constitution/iron_stomach.png` | ✅ generated, resolves |
| `lion_heart` | constitution | base | Reduces the time of all negative effects by N. This will only apply to effects applied to you by enemies. | Lion-gold heart shrugging off debuffs | `textures/skill/constitution/lion_heart.png` | ✅ generated, resolves |
| `master_chef` | constitution | base | Your constitution allows you to extract more from food, increasing effect durations by N. | Chef's pot extending effect durations | `textures/skill/constitution/master_chef.png` | ✅ generated, resolves |
| `myrmex_carapace` | constitution | base | Myrmex armor provides N bonus poison resistance. | Chitin carapace vs poison | `textures/skill/constitution/myrmex_carapace.png` | ✅ generated, resolves |
| `natural_recovery` | constitution | base | Your natural health regeneration is increased by N. | Verdant heart with natural regen | `textures/skill/constitution/natural_recovery.png` | ✅ generated, resolves |
| `natures_blessing` | constitution | base | Standing on natural blocks heals N HP per second. | Blessed leaf healing on natural ground | `textures/skill/constitution/natures_blessing.png` | ✅ generated, resolves |
| `obsidian_heart` | constitution | base | Explosion damage is reduced by N. | Obsidian heart vs explosions | `textures/skill/constitution/obsidian_heart.png` | ✅ generated, resolves |
| `phoenix_rising` | constitution | base | Respawn with N HP instead of the default amount. | Phoenix rising with respawn health | `textures/skill/constitution/phoenix_rising.png` | ✅ generated, resolves |
| `poison_immunity` | constitution | base | Your body is completely immune to poison damage. | Venom drop crossed out — poison immune | `textures/skill/constitution/poison_immunity.png` | ✅ generated, resolves |
| `potion_mastery` | constitution | base | Dave's Potioneering effects last N longer. | Potion with extended duration | `textures/skill/constitution/potion_mastery.png` | ✅ generated, resolves |
| `runic_fortification` | constitution | base | Runic ore armor provides N additional max health. | Runic armor granting max health | `textures/skill/constitution/runic_fortification.png` | ✅ generated, resolves |
| `searing_resistance` | constitution | base | Reduce lava and magma damage by N. | Lava drop with damage-down | `textures/skill/constitution/searing_resistance.png` | ✅ generated, resolves |
| `second_wind` | constitution | base | When dropping below 25%% health, regenerate N HP. | Wing of second wind — regen when low | `textures/skill/constitution/second_wind.png` | ✅ generated, resolves |
| `soul_sustenance` | constitution | base | XP orbs restore N HP when collected. | XP orb restoring health | `textures/skill/constitution/soul_sustenance.png` | ✅ generated, resolves |
| `survival_instinct` | constitution | base | Gain N movement speed when below 30%% health. | Adrenaline boot — speed when hurt | `textures/skill/constitution/survival_instinct.png` | ✅ generated, resolves |
| `swimmers_endurance` | constitution | base | Your swimming speed is increased by N. | Swift fish — swim speed | `textures/skill/constitution/swimmers_endurance.png` | ✅ generated, resolves |
| `thick_skin` | constitution | base | Your toughened body reduces all physical damage by N flat points. | Leathered hide — flat damage reduction | `textures/skill/constitution/thick_skin.png` | ✅ generated, resolves |
| `turtle_shield` | constitution | base | Shulker projectiles will no longer affect you with the levitation effect. | Shell negating levitation bolts | `textures/skill/constitution/turtle_shield.png` | ✅ generated, resolves |
| `undying_will` | constitution | base | You have a N chance to survive a killing blow at 1 HP. | Defiant skull — chance to survive death | `textures/skill/constitution/undying_will.png` | ✅ generated, resolves |
| `vitality` | constitution | base | Your maximum health increases by N hearts. | Big heart with extra hearts | `textures/skill/constitution/vitality.png` | ✅ generated, resolves |
| `wither_resistance` | constitution | base | Reduce wither damage by N. | Wither skull with damage-down | `textures/skill/constitution/wither_resistance.png` | ✅ generated, resolves |
| `acrobat` | dexterity | base | Fall damage reduced by N. | Feather-fall — less fall damage | `textures/skill/dexterity/acrobat.png` | ✅ generated, resolves |
| `agile_climber` | dexterity | base | Climbing speed on ladders and vines increased by N. | Ladder climbed faster | `textures/skill/dexterity/agile_climber.png` | ✅ generated, resolves |
| `ambush` | dexterity | base | First attack from stealth deals N bonus damage. | Shadow dagger — stealth opener crit | `textures/skill/dexterity/ambush.png` | ✅ generated, resolves |
| `apothic_critical_mastery` | dexterity | Apotheosis/Apothic | You see the opening before it's shown. +N critical strike chance and +N critical damage. | Bullseye — crit chance and damage | `textures/skill/dexterity/apothic_critical_mastery.png` | ✅ generated, resolves |
| `archery_expansion` | dexterity | base | Advanced bows deal N bonus damage. | Advanced bow with bonus damage | `textures/skill/dexterity/archery_expansion.png` | ✅ generated, resolves |
| `arrow_mastery` | dexterity | Apotheosis/Apothic | Your shafts fly true and fast. +N arrow damage and +N arrow velocity. | Gilded arrow — damage and velocity | `textures/skill/dexterity/arrow_mastery.png` | ✅ generated, resolves |
| `arrow_recovery` | dexterity | base | You have a N chance to recover arrows from killed mobs. | Recovered arrow — retrieval chance | `textures/skill/dexterity/arrow_recovery.png` | ✅ generated, resolves |
| `blade_dancer` | dexterity | base | Sword attack speed increased by N. | Dancing blades — sword attack speed | `textures/skill/dexterity/blade_dancer.png` | ✅ generated, resolves |
| `cat_eyes` | dexterity | base | Now you have permanent Night Vision, the darkness is nothing to you anymore. | Cat eye under the moon — night vision | `textures/skill/dexterity/cat_eyes.png` | ✅ generated, resolves |
| `crossbow_expert` | dexterity | base | Crossbow reload speed increased by N. | Crossbow reloaded faster | `textures/skill/dexterity/crossbow_expert.png` | ✅ generated, resolves |
| `dodge_roll` | dexterity | base | You have a N chance to dodge incoming melee attacks. | Rolling boot — melee dodge chance | `textures/skill/dexterity/dodge_roll.png` | ✅ generated, resolves |
| `dragon_rider` | dexterity | base | Mount speed increased by N. | Crimson mount — ride speed | `textures/skill/dexterity/dragon_rider.png` | ✅ generated, resolves |
| `eagle_eye` | dexterity | base | Arrow damage at long range increased by N. | Eagle eye — long-range arrow damage | `textures/skill/dexterity/eagle_eye.png` | ✅ generated, resolves |
| `evasion` | dexterity | base | Dodge chance increased by N when not wearing heavy armor. | Light wing — dodge unarmored | `textures/skill/dexterity/evasion.png` | ✅ generated, resolves |
| `evasive` | dexterity | Apotheosis/Apothic | Blades pass by empty air. +N dodge chance against melee and projectile attacks. | Spectral wing — apothic dodge | `textures/skill/dexterity/evasive.png` | ✅ generated, resolves |
| `fleet_footed` | dexterity | base | Walking speed in water increased by N. | Water-striding boot | `textures/skill/dexterity/fleet_footed.png` | ✅ generated, resolves |
| `ice_arrows` | dexterity | base | Arrows have a N chance to slow targets. | Frost arrow slowing targets | `textures/skill/dexterity/ice_arrows.png` | ✅ generated, resolves |
| `mounted_combat` | dexterity | base | Damage dealt while mounted increased by N. | Warhorse — mounted damage | `textures/skill/dexterity/mounted_combat.png` | ✅ generated, resolves |
| `multishot_mastery` | dexterity | base | You have a N chance to fire an additional arrow. | Arrow fan — extra arrow chance | `textures/skill/dexterity/multishot_mastery.png` | ✅ generated, resolves |
| `ninja_training` | dexterity | base | Ninja armor provides N more stealth effectiveness. | Ninja mask — stealth gear stronger | `textures/skill/dexterity/ninja_training.png` | ✅ generated, resolves |
| `parkour_master` | dexterity | base | Parkour moves cost N less stamina. | Free-runner boot — less stamina | `textures/skill/dexterity/parkour_master.png` | ✅ generated, resolves |
| `phantom_strike` | dexterity | base | After dodging, your next attack deals N bonus damage. | Phantom blade — post-dodge strike | `textures/skill/dexterity/phantom_strike.png` | ✅ generated, resolves |
| `poison_arrow` | dexterity | base | Your arrows have a N chance to poison targets. | Venom-tipped arrow | `textures/skill/dexterity/poison_arrow.png` | ✅ generated, resolves |
| `precision_shot` | dexterity | base | Critical bow shots deal N more damage. | Steady aim — crit bow damage | `textures/skill/dexterity/precision_shot.png` | ✅ generated, resolves |
| `quick_draw` | dexterity | base | Weapon switch speed increased by N. | Fast-swap blade | `textures/skill/dexterity/quick_draw.png` | ✅ generated, resolves |
| `quick_reposition` | dexterity | base | Damaging an enemy with an arrow will cause you to gain Speed N for N. | Charged boot — speed on arrow hit | `textures/skill/dexterity/quick_reposition.png` | ✅ generated, resolves |
| `rapid_fire` | dexterity | base | Bow draw speed increased by N. | Bow drawn faster | `textures/skill/dexterity/rapid_fire.png` | ✅ generated, resolves |
| `ricochet` | dexterity | base | Arrows have a N chance to bounce to a nearby target. | Bouncing arrow | `textures/skill/dexterity/ricochet.png` | ✅ generated, resolves |
| `sharpshooter` | dexterity | base | Headshots deal N bonus damage. | Headshot reticle | `textures/skill/dexterity/sharpshooter.png` | ✅ generated, resolves |
| `silent_kill` | dexterity | base | Stealth kills have a N chance to not alert nearby mobs. | Silenced blade — no alert | `textures/skill/dexterity/silent_kill.png` | ✅ generated, resolves |
| `silent_step` | dexterity | base | Mob detection range reduced by N when sneaking. | Muffled boot — detection reduced | `textures/skill/dexterity/silent_step.png` | ✅ generated, resolves |
| `smoke_bomb` | dexterity | base | When hit below N health, gain brief invisibility. | Smoke puff — emergency invisibility | `textures/skill/dexterity/smoke_bomb.png` | ✅ generated, resolves |
| `sniper` | dexterity | base | Bow damage increased by N beyond 30 blocks. | Long-range scope reticle | `textures/skill/dexterity/sniper.png` | ✅ generated, resolves |
| `spartan_marksmanship` | dexterity | base | Spartan throwing weapons deal N more damage. | Thrown spear — spartan weapons | `textures/skill/dexterity/spartan_marksmanship.png` | ✅ generated, resolves |
| `spell_dodge` | dexterity | base | You have a N chance to dodge incoming spells. | Arcane wing — dodge spells | `textures/skill/dexterity/spell_dodge.png` | ✅ generated, resolves |
| `sprint_master` | dexterity | base | Sprint speed increased by N. | Golden sprinter boot | `textures/skill/dexterity/sprint_master.png` | ✅ generated, resolves |
| `stealth_mastery` | dexterity | base | Now you can hide from your enemies, the vision is reduced by N, crouching will reduce it further to N. Shoo... | Night mask — reduced visibility | `textures/skill/dexterity/stealth_mastery.png` | ✅ generated, resolves |
| `tracking` | dexterity | base | Damaged enemies glow briefly, revealing their position. | Tracked pawprint — marked prey glows | `textures/skill/dexterity/tracking.png` | ✅ generated, resolves |
| `trick_shot` | dexterity | base | Arrows that pass through fire gain fire damage. | Arrow igniting through fire | `textures/skill/dexterity/trick_shot.png` | ✅ generated, resolves |
| `wind_runner` | dexterity | base | Movement speed on paths increased by N. | Wind boot — path speed | `textures/skill/dexterity/wind_runner.png` | ✅ generated, resolves |
| `wind_walker` | dexterity | base | Air movement speed increased by N. | Storm wing — air speed | `textures/skill/dexterity/wind_walker.png` | ✅ generated, resolves |
| `zipline_expert` | dexterity | base | Zipline travel speed increased by N. | Zipline pulley speeding travel | `textures/skill/dexterity/zipline_expert.png` | ✅ generated, resolves |
| `adaptation` | endurance | base | Consecutive hits from the same source deal N less damage. | Adaptive shield — repeat hits weaker | `textures/skill/endurance/adaptation.png` | ✅ generated, resolves |
| `ancient_guardian` | endurance | base | Boss attacks deal N less damage to you. | Ancient bastion vs bosses | `textures/skill/endurance/ancient_guardian.png` | ✅ generated, resolves |
| `arcane_shield` | endurance | base | Your endurance protects you from arcane forces, reducing incoming spell damage by N. | Arcane shield — spell damage down | `textures/skill/endurance/arcane_shield.png` | ✅ generated, resolves |
| `aura_shield` | endurance | base | Nearby aura reduces incoming damage by N. | Aura buckler — ambient damage reduction | `textures/skill/endurance/aura_shield.png` | ✅ generated, resolves |
| `blast_resistance` | endurance | base | Explosion damage reduced by N. | Dud bomb — explosion damage down | `textures/skill/endurance/blast_resistance.png` | ✅ generated, resolves |
| `blood_warded` | endurance | Iron's Spellbooks | The crimson ward holds. Incoming blood magic damage reduced by N. | Crimson ward vs blood magic | `textures/skill/endurance/blood_warded.png` | ✅ generated, resolves |
| `bulwark` | endurance | base | While blocking, reflect N of damage back to attackers. | Bulwark reflecting damage | `textures/skill/endurance/bulwark.png` | ✅ generated, resolves |
| `cataclysm_resistance` | endurance | base | Your endurance hardens you against cataclysmic forces, reducing boss damage by N. | Obsidian shield vs cataclysm bosses | `textures/skill/endurance/cataclysm_resistance.png` | ✅ generated, resolves |
| `colony_guardian` | endurance | base | Take N less damage while in colony territories. | Guarded homestead — safe in colonies | `textures/skill/endurance/colony_guardian.png` | ✅ generated, resolves |
| `counter_attack` | endurance | base | When an enemy damages you, for N your next attack will increase by N of the enemy's damage taken. | Riposte blades | `textures/skill/endurance/counter_attack.png` | ✅ generated, resolves |
| `diamond_skin` | endurance | base | Now you have permanent Resistance N, crouching will give you N extra armor. | Diamond facet skin — permanent resistance | `textures/skill/endurance/diamond_skin.png` | ✅ generated, resolves |
| `dragon_breath_shield` | endurance | base | Dragon breath attacks deal N less damage. | Ward vs dragon breath | `textures/skill/endurance/dragon_breath_shield.png` | ✅ generated, resolves |
| `dragon_scale_armor` | endurance | base | Dragon scale armor provides N additional protection. | Dragon-scale plate protection | `textures/skill/endurance/dragon_scale_armor.png` | ✅ generated, resolves |
| `dragonhide` | endurance | base | Dragonsteel armor provides N additional fire and ice resistance. | Dragonsteel hide vs fire and ice | `textures/skill/endurance/dragonhide.png` | ✅ generated, resolves |
| `dungeon_resilience` | endurance | base | Dungeon trap damage reduced by N. | Dungeon wall — trap damage down | `textures/skill/endurance/dungeon_resilience.png` | ✅ generated, resolves |
| `eldritch_warded` | endurance | Iron's Spellbooks | You have seen what should not be, and remain. Incoming eldritch magic damage reduced by N. | Unblinking ward vs eldritch magic | `textures/skill/endurance/eldritch_warded.png` | ✅ generated, resolves |
| `ender_warded` | endurance | Iron's Spellbooks | The Void turns aside its own. Incoming ender magic damage reduced by N. | Void ward vs ender magic | `textures/skill/endurance/ender_warded.png` | ✅ generated, resolves |
| `evocation_warded` | endurance | Iron's Spellbooks | Evoked forces slide off your guard. Incoming evocation magic damage reduced by N. | Sigil ward vs evocation | `textures/skill/endurance/evocation_warded.png` | ✅ generated, resolves |
| `fantasy_fortitude` | endurance | base | Fantasy armor sets provide N bonus protection. | Fantasy armor bonus protection | `textures/skill/endurance/fantasy_fortitude.png` | ✅ generated, resolves |
| `fire_proof` | endurance | base | Fire duration reduced by N. | Snuffed flame — burn time reduced | `textures/skill/endurance/fire_proof.png` | ✅ generated, resolves |
| `fire_warded` | endurance | Iron's Spellbooks | Embers fail to find your skin. Incoming fire magic damage reduced by N. | Ember ward vs fire magic | `textures/skill/endurance/fire_warded.png` | ✅ generated, resolves |
| `frost_endurance` | endurance | base | Cold and freezing damage reduced by N. | Hardened frost — cold protection | `textures/skill/endurance/frost_endurance.png` | ✅ generated, resolves |
| `gem_threaded_armor` | endurance | Apotheosis/Apothic | Your gems shore up your guard. +N armor per equipped socket across all affixed gear. | Socketed plating — armor per gem | `textures/skill/endurance/gem_threaded_armor.png` | ✅ generated, resolves |
| `heavy_armor_mastery` | endurance | base | Heavy armor provides N additional armor points. | Heavy plate with bonus armor | `textures/skill/endurance/heavy_armor_mastery.png` | ✅ generated, resolves |
| `holy_warded` | endurance | Iron's Spellbooks | Sacred light shields you. Incoming holy magic damage reduced by N. | Sacred ward vs holy magic | `textures/skill/endurance/holy_warded.png` | ✅ generated, resolves |
| `ice_warded` | endurance | Iron's Spellbooks | Frost cannot bite you. Incoming ice magic damage reduced by N. | Frost ward vs ice magic | `textures/skill/endurance/ice_warded.png` | ✅ generated, resolves |
| `immovable_object` | endurance | base | You cannot be knocked back while blocking. | Anchored — cannot be knocked back | `textures/skill/endurance/immovable_object.png` | ✅ generated, resolves |
| `lightning_rod` | endurance | base | Lightning damage reduced by N. | Grounded bolt — lightning damage down | `textures/skill/endurance/lightning_rod.png` | ✅ generated, resolves |
| `lightning_warded` | endurance | Iron's Spellbooks | The storm passes around you. Incoming lightning magic damage reduced by N. | Storm ward vs lightning magic | `textures/skill/endurance/lightning_warded.png` | ✅ generated, resolves |
| `mana_bulwark` | endurance | Iron's Spellbooks | Weave a shield of pure mana. Redirect N of incoming damage to your mana pool at a rate of N mana per HP. | Mana shield absorbing damage | `textures/skill/endurance/mana_bulwark.png` | ✅ generated, resolves |
| `mythic_fortitude` | endurance | base | Your endurance shields you from dragon elemental attacks, reducing fire, ice, and lightning breath damage b... | Gilded scales vs dragon breath elements | `textures/skill/endurance/mythic_fortitude.png` | ✅ generated, resolves |
| `nature_warded` | endurance | Iron's Spellbooks | The wild spares its kin. Incoming nature magic damage reduced by N. | Wild ward vs nature magic | `textures/skill/endurance/nature_warded.png` | ✅ generated, resolves |
| `obsidian_skin` | endurance | base | Obsidian armor provides N bonus damage reduction. | Obsidian armor damage reduction | `textures/skill/endurance/obsidian_skin.png` | ✅ generated, resolves |
| `pain_suppression` | endurance | base | Damage over time effects deal N less damage. | Numbed heart — DoT reduced | `textures/skill/endurance/pain_suppression.png` | ✅ generated, resolves |
| `poison_resistance` | endurance | base | Poison damage reduced by N. | Venom drop weakened | `textures/skill/endurance/poison_resistance.png` | ✅ generated, resolves |
| `prismarine_shield` | endurance | base | Prismarine armor provides N bonus ranged damage reduction. | Prismarine ward vs ranged | `textures/skill/endurance/prismarine_shield.png` | ✅ generated, resolves |
| `runic_ward` | endurance | base | Runic ore armor provides N bonus magic resistance. | Runic armor magic resistance | `textures/skill/endurance/runic_ward.png` | ✅ generated, resolves |
| `samurai_resolve` | endurance | base | After taking damage, gain N damage reduction briefly. | Tempered resolve after taking a hit | `textures/skill/endurance/samurai_resolve.png` | ✅ generated, resolves |
| `sentinel` | endurance | base | Gain N damage reduction when below 50%% health. | Sentinel shield when bloodied | `textures/skill/endurance/sentinel.png` | ✅ generated, resolves |
| `shield_wall` | endurance | base | Shield blocking reduces N more incoming damage. | Braced shield blocking more | `textures/skill/endurance/shield_wall.png` | ✅ generated, resolves |
| `siege_defense` | endurance | base | While in a structure, gain N damage reduction. | Fortified keep — safe in structures | `textures/skill/endurance/siege_defense.png` | ✅ generated, resolves |
| `snow_walker` | endurance | base | Walk over Powder Snow without falling. | Snowshoe over powder snow | `textures/skill/endurance/snow_walker.png` | ✅ generated, resolves |
| `spell_shield` | endurance | base | Ars Nouveau spell damage reduced by N. | Ward vs Ars spell damage | `textures/skill/endurance/spell_shield.png` | ✅ generated, resolves |
| `steadfast` | endurance | base | Knockback received reduced by N. | Braced anchor — knockback reduced | `textures/skill/endurance/steadfast.png` | ✅ generated, resolves |
| `stoneflesh` | endurance | base | Standing still grants N damage reduction. | Stone skin while standing still | `textures/skill/endurance/stoneflesh.png` | ✅ generated, resolves |
| `thorns_mastery` | endurance | base | Thorns enchantment deals N more damage. | Sharpened thorns strike back harder | `textures/skill/endurance/thorns_mastery.png` | ✅ generated, resolves |
| `toughened_hide` | endurance | base | Armor toughness increased by N. | Toughened hide — armor toughness | `textures/skill/endurance/toughened_hide.png` | ✅ generated, resolves |
| `unbreakable` | endurance | base | Armor durability loss reduced by N. | Unbreaking armor — durability protected | `textures/skill/endurance/unbreakable.png` | ✅ generated, resolves |
| `warding_rune` | endurance | base | Magic damage reduced by N. | Warding rune — magic damage down | `textures/skill/endurance/warding_rune.png` | ✅ generated, resolves |
| `adventurers_luck` | fortune | base | Dungeon loot quality increased by N. | Treasure map — better dungeon loot | `textures/skill/fortune/adventurers_luck.png` | ✅ generated, resolves |
| `affix_affinity` | fortune | Apotheosis/Apothic | Enchanted gear answers to your bloodline. Each Rare-or-better affix item equipped grants N damage and N dam... | Affix gear resonance — damage and DR | `textures/skill/fortune/affix_affinity.png` | ✅ generated, resolves |
| `apotheosis_gems` | fortune | base | Apotheosis gem quality chance increased by N. | Purer gems from Apotheosis | `textures/skill/fortune/apotheosis_gems.png` | ✅ generated, resolves |
| `apothic_apprentice` | fortune | Apotheosis/Apothic | Mastery of the lapidary's art. Equipped items gain +N effective socket on top of Socket Virtuoso. | Lapidary socket — extra effective socket | `textures/skill/fortune/apothic_apprentice.png` | ✅ generated, resolves |
| `blessing_of_luck` | fortune | base | Lucky effect duration increased by N. | Blessed clover — luck lasts longer | `textures/skill/fortune/blessing_of_luck.png` | ✅ generated, resolves |
| `cataclysm_spoils` | fortune | base | Cataclysm boss drops yield N more loot. | Boss skull yielding spoils | `textures/skill/fortune/cataclysm_spoils.png` | ✅ generated, resolves |
| `chaos_roll` | fortune | base | Any action has a N chance for a bonus effect. | Chaos die — random bonus effects | `textures/skill/fortune/chaos_roll.png` | ✅ generated, resolves |
| `coin_flip` | fortune | base | Attacked mobs have a N chance to drop bonus XP. | Flipped coin — bonus XP chance | `textures/skill/fortune/coin_flip.png` | ✅ generated, resolves |
| `critical_fortune` | fortune | base | Critical kills yield N more drops. | Lucky crit — kills drop more | `textures/skill/fortune/critical_fortune.png` | ✅ generated, resolves |
| `critical_mastery` | fortune | base | Critical hit chance increased by N. | Honed aim — crit chance | `textures/skill/fortune/critical_mastery.png` | ✅ generated, resolves |
| `critical_roll` | fortune | base | When you make a critical attack you will roll a dice, rolling a 6 will increase your critical damage by N, ... | Crit dice gamble | `textures/skill/fortune/critical_roll.png` | ✅ generated, resolves |
| `double_down` | fortune | base | You have a N chance for double drops from all sources. | Doubled die — double drops | `textures/skill/fortune/double_down.png` | ✅ generated, resolves |
| `dragon_hoard` | fortune | base | Dragon loot tables yield N more. | Dragon's hoard chest | `textures/skill/fortune/dragon_hoard.png` | ✅ generated, resolves |
| `enchanted_fortune` | fortune | base | Enchanted mob drops increased by N. | Enchanted drops increased | `textures/skill/fortune/enchanted_fortune.png` | ✅ generated, resolves |
| `ethereal_luck` | fortune | base | Magical loot sources yield N more. | Spirit luck — magical loot | `textures/skill/fortune/ethereal_luck.png` | ✅ generated, resolves |
| `fishermans_luck` | fortune | base | Aquaculture fish drops increased by N. | Prize catch — more fish drops | `textures/skill/fortune/fishermans_luck.png` | ✅ generated, resolves |
| `fortune_cookie` | fortune | base | Food has a N chance to give a random buff. | Fortune cookie — random food buff | `textures/skill/fortune/fortune_cookie.png` | ✅ generated, resolves |
| `fortunes_favor` | fortune | base | Fortune enchantment effectiveness increased by N. | Fortune enchant favored | `textures/skill/fortune/fortunes_favor.png` | ✅ generated, resolves |
| `gem_attunement` | fortune | base | Chance that a gem is not consumed when socketing into equipment. | Gem kept when socketing | `textures/skill/fortune/gem_attunement.png` | ✅ generated, resolves |
| `golden_touch` | fortune | base | You have a N chance for gold drops from any mob. | Gilded hand — gold from any kill | `textures/skill/fortune/golden_touch.png` | ✅ generated, resolves |
| `greed` | fortune | base | Gold drops from all sources increased by N. | Coin stack — more gold everywhere | `textures/skill/fortune/greed.png` | ✅ generated, resolves |
| `jackpot` | fortune | base | You have a N chance for triple loot from chests. | Jackpot chest — triple loot | `textures/skill/fortune/jackpot.png` | ✅ generated, resolves |
| `jewelers_eye` | fortune | base | Jewelry crafting has a N chance for a bonus gem. | Jeweler's appraisal — bonus gems | `textures/skill/fortune/jewelers_eye.png` | ✅ generated, resolves |
| `limit_breaker` | fortune | base | You have a N chance to deals N when you attack a entity. | Limit break burst damage | `textures/skill/fortune/limit_breaker.png` | ✅ generated, resolves |
| `looter` | fortune | base | Boss mob drops increased by N items. | Trophy skull — boss drops | `textures/skill/fortune/looter.png` | ✅ generated, resolves |
| `lucky_break` | fortune | base | Tool durability loss has a N chance to be ignored. | Charmed tool — durability spared | `textures/skill/fortune/lucky_break.png` | ✅ generated, resolves |
| `lucky_charm` | fortune | base | All negative effects have N shorter duration. | Lucky horseshoe — debuffs shortened | `textures/skill/fortune/lucky_charm.png` | ✅ generated, resolves |
| `lucky_drop` | fortune | base | You have a N chance of getting N mob drops every time you kill a mob. | Clover — bonus mob drops | `textures/skill/fortune/lucky_drop.png` | ✅ generated, resolves |
| `lucky_explorer` | fortune | base | Structure chest loot is N better. | Explorer compass — richer chests | `textures/skill/fortune/lucky_explorer.png` | ✅ generated, resolves |
| `lucky_fishing` | fortune | base | Fishing treasure rate increased by N. | Golden fish — treasure bites | `textures/skill/fortune/lucky_fishing.png` | ✅ generated, resolves |
| `lucky_star` | fortune | base | At nighttime, all luck bonuses increased by N. | Night star — luck after dark | `textures/skill/fortune/lucky_star.png` | ✅ generated, resolves |
| `master_looter` | fortune | base | All loot tables have N increased quality. | Master's chest — loot quality up | `textures/skill/fortune/master_looter.png` | ✅ generated, resolves |
| `midas_touch` | fortune | base | Mob drops have a N chance to become gold variants. | Midas hand — drops turn gold | `textures/skill/fortune/midas_touch.png` | ✅ generated, resolves |
| `prospectors_luck` | fortune | base | Ore mining yields N more drops. | Rich vein — more ore drops | `textures/skill/fortune/prospectors_luck.png` | ✅ generated, resolves |
| `rainbow_loot` | fortune | base | Mob drops have a N chance to be enchanted. | Prismatic star — enchanted drops | `textures/skill/fortune/rainbow_loot.png` | ✅ generated, resolves |
| `rare_find` | fortune | base | Chance for rare and epic drops increased by N. | Rare gem discovery | `textures/skill/fortune/rare_find.png` | ✅ generated, resolves |
| `runic_fortune` | fortune | base | Runic ore drops increased by N. | Runic luck — runic ore drops | `textures/skill/fortune/runic_fortune.png` | ✅ generated, resolves |
| `salvage_luck` | fortune | base | Salvaging items yields N more materials. | Lucky salvage — extra materials | `textures/skill/fortune/salvage_luck.png` | ✅ generated, resolves |
| `scavenger` | fortune | base | You have a N chance to find extra items in loot chests. | Scavenger pack — extra chest finds | `textures/skill/fortune/scavenger.png` | ✅ generated, resolves |
| `serendipity` | fortune | base | You have a N chance to find rare items while mining. | Serendipitous strike while mining | `textures/skill/fortune/serendipity.png` | ✅ generated, resolves |
| `socket_virtuoso` | fortune | Apotheosis/Apothic | Your eye finds every flaw. Equipped items gain +N effective socket. | Virtuoso socket — extra effective socket | `textures/skill/fortune/socket_virtuoso.png` | ✅ generated, resolves |
| `treasure_sense` | fortune | base | Chance for rare loot increased by N. | Treasure sense — rare loot nearby | `textures/skill/fortune/treasure_sense.png` | ✅ generated, resolves |
| `alchemic_transmutation` | intelligence | base | Brewing yields N bonus ingredients. | Transmuting flask — bonus ingredients | `textures/skill/intelligence/alchemic_transmutation.png` | ✅ generated, resolves |
| `alchemy_manipulation` | intelligence | base | The power of alchemy is now in your hands, all the potions you drink will amplifier their power by N. | Amplified potion effects | `textures/skill/intelligence/alchemy_manipulation.png` | ✅ generated, resolves |
| `ancient_languages` | intelligence | base | Reading ancient texts grants N bonus XP. | Ancient script — bonus XP | `textures/skill/intelligence/ancient_languages.png` | ✅ generated, resolves |
| `apothecary` | intelligence | base | Dave's Potioneering effects amplified by N levels. | Herbal potion amplification | `textures/skill/intelligence/apothecary.png` | ✅ generated, resolves |
| `apothic_scholar` | intelligence | Apotheosis/Apothic | Knowledge compounds with every triumph. +N experience gained from all sources. | Scholar's tome — XP gained | `textures/skill/intelligence/apothic_scholar.png` | ✅ generated, resolves |
| `aquatic_knowledge` | intelligence | base | Fish provide N more nourishment and unique buffs. | Studied fish — nourishment and buffs | `textures/skill/intelligence/aquatic_knowledge.png` | ✅ generated, resolves |
| `arcane_scholar` | intelligence | base | Ars Nouveau spell complexity limit increased by N. | Spell complexity expanded | `textures/skill/intelligence/arcane_scholar.png` | ✅ generated, resolves |
| `beast_tamer` | intelligence | base | Your intelligence aids in taming mythical creatures, granting a 1 in N chance for easier taming. | Tamed paw — easier taming | `textures/skill/intelligence/beast_tamer.png` | ✅ generated, resolves |
| `bookworm` | intelligence | base | XP gain from all sources increased by N. | Bookworm — XP gain up | `textures/skill/intelligence/bookworm.png` | ✅ generated, resolves |
| `brewing_innovation` | intelligence | base | Potions can stack N additional effects. | Innovative brew — extra effects | `textures/skill/intelligence/brewing_innovation.png` | ✅ generated, resolves |
| `cartographer` | intelligence | base | Maps reveal N more of the surrounding area. | Wider map reveal | `textures/skill/intelligence/cartographer.png` | ✅ generated, resolves |
| `colony_advisor` | intelligence | base | Colony buildings work N faster. | Advised colony works faster | `textures/skill/intelligence/colony_advisor.png` | ✅ generated, resolves |
| `continuous_flow` | intelligence | Iron's Spellbooks | Sustain the arcane stream with less strain. Continuous-cast spells drain N less mana per tick. | Steady mana stream — less drain | `textures/skill/intelligence/continuous_flow.png` | ✅ generated, resolves |
| `dimensional_scholar` | intelligence | base | XP gained in other dimensions increased by N. | Otherworld study — dimension XP | `textures/skill/intelligence/dimensional_scholar.png` | ✅ generated, resolves |
| `dragon_lore` | intelligence | base | Your knowledge increases dragon taming success by N. | Dragon lore — taming success | `textures/skill/intelligence/dragon_lore.png` | ✅ generated, resolves |
| `efficient_crafting` | intelligence | base | Crafting has a N chance to not consume materials. | Efficient craft — materials spared | `textures/skill/intelligence/efficient_crafting.png` | ✅ generated, resolves |
| `enchantment_insight` | intelligence | base | Enchanting table shows N extra enchantment options. | Insight lenses — extra enchant options | `textures/skill/intelligence/enchantment_insight.png` | ✅ generated, resolves |
| `familiar_bond` | intelligence | base | Familiar and pet damage increased by N. | Bonded familiar damage | `textures/skill/intelligence/familiar_bond.png` | ✅ generated, resolves |
| `glyph_mastery` | intelligence | base | Your deep understanding of glyphs adds N amplification to all spell effects. | Mastered glyph amplification | `textures/skill/intelligence/glyph_mastery.png` | ✅ generated, resolves |
| `golem_commander` | intelligence | base | Golem familiars deal N more damage. | Golem core — construct damage | `textures/skill/intelligence/golem_commander.png` | ✅ generated, resolves |
| `haggler` | intelligence | base | Haggling is an art that you now possess, the villagers will give you a N discount on all their trades. | Haggled emerald — trade discount | `textures/skill/intelligence/haggler.png` | ✅ generated, resolves |
| `linguist` | intelligence | base | Villager trade options increased by N. | Polyglot scroll — more trades | `textures/skill/intelligence/linguist.png` | ✅ generated, resolves |
| `lore_keeper` | intelligence | base | Patchouli guide books provide N bonus XP. | Guidebook lore XP | `textures/skill/intelligence/lore_keeper.png` | ✅ generated, resolves |
| `master_researcher` | intelligence | base | Recipe discovery rate increased by N. | Research sheet — recipe discovery | `textures/skill/intelligence/master_researcher.png` | ✅ generated, resolves |
| `monster_compendium` | intelligence | base | Studied mobs take N more damage. | Studied monster weak points | `textures/skill/intelligence/monster_compendium.png` | ✅ generated, resolves |
| `mystic_analysis` | intelligence | base | Identify enemy weaknesses for N bonus type damage. | Analytic eye — typed bonus damage | `textures/skill/intelligence/mystic_analysis.png` | ✅ generated, resolves |
| `natures_wisdom` | intelligence | base | Nature's Aura generation increased by N. | Aura generation increased | `textures/skill/intelligence/natures_wisdom.png` | ✅ generated, resolves |
| `potion_brewing_expert` | intelligence | base | Brewed potions are N levels stronger. | Stronger brewed potions | `textures/skill/intelligence/potion_brewing_expert.png` | ✅ generated, resolves |
| `progressive_mastery` | intelligence | base | Each difficulty level grants N more XP. | Difficulty star — scaling XP | `textures/skill/intelligence/progressive_mastery.png` | ✅ generated, resolves |
| `quick_learner` | intelligence | base | Skill XP gain increased by N. | Fast-learning XP orb | `textures/skill/intelligence/quick_learner.png` | ✅ generated, resolves |
| `runecrafter` | intelligence | base | Runic items gain N bonus stats. | Crafted rune — runic item stats | `textures/skill/intelligence/runecrafter.png` | ✅ generated, resolves |
| `sages_focus` | intelligence | base | Channeled abilities are N faster. | Focused channel — faster abilities | `textures/skill/intelligence/sages_focus.png` | ✅ generated, resolves |
| `scholar` | intelligence | base | You can now read the enchantments of the ancient inhabitants of this world. You wonder how these enchantmen... | Scholar's lenses — read ancient enchants | `textures/skill/intelligence/scholar.png` | ✅ generated, resolves |
| `scroll_mastery` | intelligence | base | Scrolls have a N chance to not be consumed. | Scroll preserved on use | `textures/skill/intelligence/scroll_mastery.png` | ✅ generated, resolves |
| `siege_engineer` | intelligence | base | Siege machine damage increased by N. | Siege works — machine damage | `textures/skill/intelligence/siege_engineer.png` | ✅ generated, resolves |
| `spell_echo` | intelligence | base | You have a N chance to refund half of a spell's mana cost when casting. | Echoing cast — mana refund chance | `textures/skill/intelligence/spell_echo.png` | ✅ generated, resolves |
| `spellcraft_knowledge` | intelligence | base | Spell casting speed increased by N. | Practiced wand — cast speed | `textures/skill/intelligence/spellcraft_knowledge.png` | ✅ generated, resolves |
| `spellweaver` | intelligence | Iron's Spellbooks | Chain your casts into a rhythm. Every N consecutive cast within N costs zero mana. | Woven casts — combo free spell | `textures/skill/intelligence/spellweaver.png` | ✅ generated, resolves |
| `strategic_mind` | intelligence | base | Discover enemy weaknesses, dealing N bonus damage. | Battle plan — exploit weaknesses | `textures/skill/intelligence/strategic_mind.png` | ✅ generated, resolves |
| `tactical_genius` | intelligence | base | Nearby allies gain N bonus damage. | Rally banner — ally damage | `textures/skill/intelligence/tactical_genius.png` | ✅ generated, resolves |
| `war_tactician` | intelligence | base | Allies in range gain N bonus attack speed. | War banner — ally attack speed | `textures/skill/intelligence/war_tactician.png` | ✅ generated, resolves |
| `affix_focus` | magic | Apotheosis/Apothic | Rare gear attunes to your casting. Equipping N or more Rare-or-better Apotheosis items grants +N effective ... | Rare gear attunes casting | `textures/skill/magic/affix_focus.png` | ✅ generated, resolves |
| `arcane_barrier` | magic | base | Create a magical barrier that absorbs N damage. | Conjured barrier absorbing damage | `textures/skill/magic/arcane_barrier.png` | ✅ generated, resolves |
| `arcane_efficiency` | magic | base | Your mastery of source manipulation allows more efficient spellcasting. Ars Nouveau spells cost N less mana. | Efficient sigil — Ars mana cost down | `textures/skill/magic/arcane_efficiency.png` | ✅ generated, resolves |
| `arcane_recovery` | magic | Iron's Spellbooks | Draw mana from the dying. Killing a foe restores mana equal to N of their maximum health, up to N. | Mana drawn from the dying | `textures/skill/magic/arcane_recovery.png` | ✅ generated, resolves |
| `arcane_reforging` | magic | base | Chance for the reforging result rarity to be upgraded by one tier. | Reforge rarity upgrade chance | `textures/skill/magic/arcane_reforging.png` | ✅ generated, resolves |
| `arcane_reprieve` | magic | Iron's Spellbooks | When your mana runs dry, the arcane surges back. Hitting zero mana instantly restores N of your max mana. N... | Emergency mana surge | `textures/skill/magic/arcane_reprieve.png` | ✅ generated, resolves |
| `ars_abjurer` | magic | Ars Nouveau | Protection and healing both answer louder. Abjuration-school glyphs are N stronger. | Abjurer ward — protection stronger | `textures/skill/magic/ars_abjurer.png` | ✅ generated, resolves |
| `ars_arcane_weaver` | magic | Ars Nouveau | Manipulation bends further under your weave. Manipulation-school glyphs deal N more damage. | Woven manipulation damage | `textures/skill/magic/ars_arcane_weaver.png` | ✅ generated, resolves |
| `ars_conjurer` | magic | Ars Nouveau | Your summons come at reduced toll. Conjuration-school glyphs cost N less mana. | Conjured spirit — summon cost down | `textures/skill/magic/ars_conjurer.png` | ✅ generated, resolves |
| `ars_emberforged` | magic | Ars Nouveau | Flame shapes to your will. Fire-school glyphs deal N more damage. | Emberforged flame — fire glyphs | `textures/skill/magic/ars_emberforged.png` | ✅ generated, resolves |
| `ars_form_projectile` | magic | Ars Nouveau | The arrow of will costs you less. Projectile-form glyph spells cost N less mana. | Spell arrow — projectile cost down | `textures/skill/magic/ars_form_projectile.png` | ✅ generated, resolves |
| `ars_form_self` | magic | Ars Nouveau | Inward-cast spells flow easily. Self-form glyph spells cost N less mana. | Inward focus — self spells cheaper | `textures/skill/magic/ars_form_self.png` | ✅ generated, resolves |
| `ars_form_touch` | magic | Ars Nouveau | At arm's reach you strike truest. Touch-form glyph spells deal N more damage. | Touch focus — contact spells stronger | `textures/skill/magic/ars_form_touch.png` | ✅ generated, resolves |
| `ars_geomancer` | magic | Ars Nouveau | The ground moves for you. Earth-school glyphs deal N more damage. | Geomancer stone — earth glyphs | `textures/skill/magic/ars_geomancer.png` | ✅ generated, resolves |
| `ars_hedgewitch` | magic | Ars Nouveau | Tides run through your casting. Water-school glyphs cost N less and deal N more damage. | Tidal drop — water glyphs stronger | `textures/skill/magic/ars_hedgewitch.png` | ✅ generated, resolves |
| `ars_stormcaller` | magic | Ars Nouveau | The wind speaks your name. Air-school glyphs deal N more damage. | Stormcaller bolt — air glyphs | `textures/skill/magic/ars_stormcaller.png` | ✅ generated, resolves |
| `ars_wild_manipulation` | magic | Ars Nouveau | Manipulation glyphs answer your pull. Manipulation-school glyph spells cost N less mana. | Wild weave — manipulation cost down | `textures/skill/magic/ars_wild_manipulation.png` | ✅ generated, resolves |
| `astral_projection` | magic | base | View the area around you in a N block radius. | Astral eye — remote viewing | `textures/skill/magic/astral_projection.png` | ✅ generated, resolves |
| `aura_manipulation` | magic | base | Nature's Aura effects are N stronger. | Amplified nature aura | `textures/skill/magic/aura_manipulation.png` | ✅ generated, resolves |
| `blood_attunement` | magic | base | Attune to the Blood school, allowing you to cast Blood spells. | Blood school runestone | `textures/skill/magic/blood_attunement.png` | ✅ generated, resolves |
| `blood_catalyst` | magic | Iron's Spellbooks | Your blood spells tear open wounds. N chance to apply Rend to victims for N. | Rending proc drop | `textures/skill/magic/blood_catalyst.png` | ✅ generated, resolves |
| `blood_mancer` | magic | Iron's Spellbooks | Blood is your ink. Blood spell damage increased by N. | Hemomancer drop — blood damage | `textures/skill/magic/blood_mancer.png` | ✅ generated, resolves |
| `charge_mastery` | magic | Iron's Spellbooks | Master the charge. Iron's Spells held-cast (Long) spells deal N more damage, modelling a fully-released cha... | Full charge — held casts hit harder | `textures/skill/magic/charge_mastery.png` | ✅ generated, resolves |
| `dragon_magic` | magic | base | Dragon-based magical items are N stronger. | Dragon magic items stronger | `textures/skill/magic/dragon_magic.png` | ✅ generated, resolves |
| `dual_casting` | magic | base | You have a N chance to cast a spell twice. | Twin cast chance | `textures/skill/magic/dual_casting.png` | ✅ generated, resolves |
| `eldritch_attunement` | magic | Iron's Spellbooks | Attune to the Eldritch school, allowing you to cast Eldritch spells. | Eldritch school runestone | `textures/skill/magic/eldritch_attunement.png` | ✅ generated, resolves |
| `eldritch_catalyst` | magic | Iron's Spellbooks | Each incantation veils you in unreality. N chance to gain Abyssal Shroud for N ticks when you cast. | Shrouding proc eye | `textures/skill/magic/eldritch_catalyst.png` | ✅ generated, resolves |
| `eldritch_mancer` | magic | Iron's Spellbooks | Unknowable glyphs bend at your reading. Eldritch spell damage increased by N. | Unknowable eye — eldritch damage | `textures/skill/magic/eldritch_mancer.png` | ✅ generated, resolves |
| `eldritch_power` | magic | base | Eldritch spells deal N more damage. | Eldritch spells empowered | `textures/skill/magic/eldritch_power.png` | ✅ generated, resolves |
| `elemental_master` | magic | base | Elemental spell damage increased by N. | Tri-element burst damage | `textures/skill/magic/elemental_master.png` | ✅ generated, resolves |
| `enchanted_missiles` | magic | base | Magic missile damage increased by N. | Missile volley damage | `textures/skill/magic/enchanted_missiles.png` | ✅ generated, resolves |
| `ender_attunement` | magic | base | Attune to the Ender school, allowing you to cast Ender spells. | Ender school runestone | `textures/skill/magic/ender_attunement.png` | ✅ generated, resolves |
| `ender_catalyst` | magic | Iron's Spellbooks | Casting opens a tear in perception. N chance to gain Planar Sight for N when you cast. | Planar proc pearl | `textures/skill/magic/ender_catalyst.png` | ✅ generated, resolves |
| `ender_mancer` | magic | Iron's Spellbooks | The Void listens when you speak. Ender spell damage increased by N. | Void pearl — ender spell damage | `textures/skill/magic/ender_mancer.png` | ✅ generated, resolves |
| `evocation_attunement` | magic | base | Attune to the Evocation school, allowing you to cast Evocation spells. | Evocation school runestone | `textures/skill/magic/evocation_attunement.png` | ✅ generated, resolves |
| `evocation_catalyst` | magic | Iron's Spellbooks | Your evoker's touch echoes through your blows. N chance to gain Echoing Strikes for N when you cast. | Echoing proc star | `textures/skill/magic/evocation_catalyst.png` | ✅ generated, resolves |
| `evocation_mancer` | magic | Iron's Spellbooks | You command arcane threads with precision. Evocation spell damage increased by N. | Evoker star — evocation damage | `textures/skill/magic/evocation_mancer.png` | ✅ generated, resolves |
| `fire_attunement` | magic | base | Attune to the Fire school, allowing you to cast Fire spells. | Fire school runestone | `textures/skill/magic/fire_attunement.png` | ✅ generated, resolves |
| `fire_catalyst` | magic | Iron's Spellbooks | Your fire spells carry lingering fury. N chance to set victims alight (Immolate) for N. | Immolating proc flame | `textures/skill/magic/fire_catalyst.png` | ✅ generated, resolves |
| `fire_mancer` | magic | Iron's Spellbooks | You hurl flame with practiced ease. Fire spell damage increased by N. | Pyromancer flame — fire spell damage | `textures/skill/magic/fire_mancer.png` | ✅ generated, resolves |
| `holy_attunement` | magic | base | Attune to the Holy school, allowing you to cast Holy spells. | Holy school runestone | `textures/skill/magic/holy_attunement.png` | ✅ generated, resolves |
| `holy_catalyst` | magic | Iron's Spellbooks | Each incantation steels your soul. N chance to gain Fortify for N when you cast. | Fortifying proc light | `textures/skill/magic/holy_catalyst.png` | ✅ generated, resolves |
| `holy_mancer` | magic | Iron's Spellbooks | Divine light flows through you. Holy spell damage and healing increased by N. | Divine radiance — holy power | `textures/skill/magic/holy_mancer.png` | ✅ generated, resolves |
| `ice_attunement` | magic | base | Attune to the Ice school, allowing you to cast Ice spells. | Ice school runestone | `textures/skill/magic/ice_attunement.png` | ✅ generated, resolves |
| `ice_catalyst` | magic | Iron's Spellbooks | Your ice spells shudder through muscle. N chance to Chill victims for N. | Chilling proc crystal | `textures/skill/magic/ice_catalyst.png` | ✅ generated, resolves |
| `ice_mancer` | magic | Iron's Spellbooks | The chill bends to your will. Ice spell damage increased by N. | Cryomancer crystal — ice spell damage | `textures/skill/magic/ice_mancer.png` | ✅ generated, resolves |
| `life_eater` | magic | base | Killing an entity causes you to absorb part of its health, you will gain N half heart. | Stolen life on kill | `textures/skill/magic/life_eater.png` | ✅ generated, resolves |
| `life_leech_bound` | magic | Iron's Spellbooks | Your minions pay tribute in mana. N of damage your summons deal returns to you as mana. | Minions tithe mana | `textures/skill/magic/life_leech_bound.png` | ✅ generated, resolves |
| `lightning_attunement` | magic | base | Attune to the Lightning school, allowing you to cast Lightning spells. | Lightning school runestone | `textures/skill/magic/lightning_attunement.png` | ✅ generated, resolves |
| `lightning_catalyst` | magic | Iron's Spellbooks | Your casts crackle with latent voltage. N chance to gain Charged for N when you cast. | Charged proc bolt | `textures/skill/magic/lightning_catalyst.png` | ✅ generated, resolves |
| `lightning_mancer` | magic | Iron's Spellbooks | Thunder answers your call. Lightning spell damage increased by N. | Stormwrought bolt — lightning damage | `textures/skill/magic/lightning_mancer.png` | ✅ generated, resolves |
| `long_channel` | magic | Iron's Spellbooks | Pour your focus into extended rituals. Long-cast spells deal N more damage. | Extended ritual — long casts stronger | `textures/skill/magic/long_channel.png` | ✅ generated, resolves |
| `lord_of_the_dead` | magic | Iron's Spellbooks | Your summons hit harder and hold longer. Summon damage +N and summon max health +N. | Undead lord — summons empowered | `textures/skill/magic/lord_of_the_dead.png` | ✅ generated, resolves |
| `mana_efficiency` | magic | base | Your mastery of mana allows more efficient spellcasting. Spells cost N less mana. | Lean mana drop — spells cost less | `textures/skill/magic/mana_efficiency.png` | ✅ generated, resolves |
| `mana_regeneration` | magic | base | Mana regeneration rate increased by N. | Mana regen up | `textures/skill/magic/mana_regeneration.png` | ✅ generated, resolves |
| `mana_shield` | magic | base | N of damage taken is absorbed by mana instead of health. | Mana absorbing damage | `textures/skill/magic/mana_shield.png` | ✅ generated, resolves |
| `mana_surge` | magic | Iron's Spellbooks | Desperation fuels the arcane. Below N HP, your spell power and mana regeneration rise by N and N respectively. | Blue surge — desperate power | `textures/skill/magic/mana_surge.png` | ✅ generated, resolves |
| `mystic_shield` | magic | base | Magical projectiles deal N less damage to you. | Ward vs magic projectiles | `textures/skill/magic/mystic_shield.png` | ✅ generated, resolves |
| `nature_attunement` | magic | base | Attune to the Nature school, allowing you to cast Nature spells. | Nature school runestone | `textures/skill/magic/nature_attunement.png` | ✅ generated, resolves |
| `nature_catalyst` | magic | Iron's Spellbooks | Casting grows a protective bark across your skin. N chance to gain Oakskin for N when you cast. | Oakskin proc leaf | `textures/skill/magic/nature_catalyst.png` | ✅ generated, resolves |
| `nature_mancer` | magic | Iron's Spellbooks | Roots and thorns answer you. Nature spell damage increased by N. | Druid leaf — nature damage | `textures/skill/magic/nature_mancer.png` | ✅ generated, resolves |
| `philosophers_stone` | magic | base | Killed mobs have a N chance to drop transmuted items. | Transmutation stone drops | `textures/skill/magic/philosophers_stone.png` | ✅ generated, resolves |
| `potion_splash` | magic | base | Splash and lingering potion area increased by N. | Wider splash radius | `textures/skill/magic/potion_splash.png` | ✅ generated, resolves |
| `quickcast` | magic | Iron's Spellbooks | Your instant spells flow in rapid succession. Instant-type spells have their cooldowns reduced by N. | Instant spells cycle faster | `textures/skill/magic/quickcast.png` | ✅ generated, resolves |
| `quickening` | magic | Iron's Spellbooks | Your spells leap from your fingers. Cast times reduced by N. | Cast time reduced | `textures/skill/magic/quickening.png` | ✅ generated, resolves |
| `reservoir` | magic | Iron's Spellbooks | Mana flows more freely. Regeneration rate increased by N. | Deep mana reservoir regen | `textures/skill/magic/reservoir.png` | ✅ generated, resolves |
| `resonant_affixes` | magic | Apotheosis/Apothic | Mythic threads amplify the weave. Each Rare-or-better affix item equipped grants +N spell damage. | Affix resonance — spell damage | `textures/skill/magic/resonant_affixes.png` | ✅ generated, resolves |
| `resonant_casting` | magic | Iron's Spellbooks | Overflowing mana sharpens your spells. While above N mana, gain N bonus spell damage. | Overflow resonance — bonus damage | `textures/skill/magic/resonant_casting.png` | ✅ generated, resolves |
| `safe_port` | magic | base | Teleporting with a Ender Pearl will no longer hurt you. | Painless pearl teleport | `textures/skill/magic/safe_port.png` | ✅ generated, resolves |
| `schoolbridge_abjuration` | magic | Iron's Spellbooks | Sacred words protect both worlds. Ars Abjuration glyphs scale with N of your Iron's holy spell power. | Sacred bridge — abjuration from holy | `textures/skill/magic/schoolbridge_abjuration.png` | ✅ generated, resolves |
| `schoolbridge_air` | magic | Iron's Spellbooks | Every storm is yours to conduct. Ars Air glyphs scale with N of your Iron's lightning spell power. | Storm bridge — air from lightning power | `textures/skill/magic/schoolbridge_air.png` | ✅ generated, resolves |
| `schoolbridge_earth` | magic | Iron's Spellbooks | Stone and root grow from the same seed. Ars Earth glyphs scale with N of your Iron's nature spell power. | Root bridge — earth from nature power | `textures/skill/magic/schoolbridge_earth.png` | ✅ generated, resolves |
| `schoolbridge_fire` | magic | Iron's Spellbooks | Your two flames are one. Ars Fire glyphs scale with N of your Iron's fire spell power. | Bridged flames — Ars fire scales with Iron's | `textures/skill/magic/schoolbridge_fire.png` | ✅ generated, resolves |
| `schoolbridge_manipulation` | magic | Iron's Spellbooks | The Void obeys a shared grammar. Ars Manipulation glyphs scale with N of your Iron's ender spell power. | Void bridge — manipulation from ender | `textures/skill/magic/schoolbridge_manipulation.png` | ✅ generated, resolves |
| `schoolbridge_water` | magic | Iron's Spellbooks | Frost answers the tide. Ars Water glyphs scale with N of your Iron's ice spell power. | Frost-tide bridge — water from ice power | `textures/skill/magic/schoolbridge_water.png` | ✅ generated, resolves |
| `soul_magic` | magic | base | Soul-based abilities are N more effective. | Soul abilities empowered | `textures/skill/magic/soul_magic.png` | ✅ generated, resolves |
| `source_attunement` | magic | base | Ars Nouveau source pool increased by N. | Expanded source pool | `textures/skill/magic/source_attunement.png` | ✅ generated, resolves |
| `source_well` | magic | base | Ars Nouveau source generation increased by N. | Source generation up | `textures/skill/magic/source_well.png` | ✅ generated, resolves |
| `spell_amplifier` | magic | base | All spell damage increased by N. | All spell damage amplified | `textures/skill/magic/spell_amplifier.png` | ✅ generated, resolves |
| `spell_quickening` | magic | base | Spell cast time reduced by N. | Spell cast time down | `textures/skill/magic/spell_quickening.png` | ✅ generated, resolves |
| `spellsocket` | magic | Apotheosis/Apothic | Channel resonates through every facet. Every N equipped sockets grants +1 effective spell level (capped at ... | Sockets grant spell level | `textures/skill/magic/spellsocket.png` | ✅ generated, resolves |
| `summoner` | magic | base | Summoned creatures are N stronger. | Strengthened summons | `textures/skill/magic/summoner.png` | ✅ generated, resolves |
| `telekinesis` | magic | base | Interact with blocks at a range of N blocks. | Telekinetic reach | `textures/skill/magic/telekinesis.png` | ✅ generated, resolves |
| `tempo` | magic | Iron's Spellbooks | The arcane rhythm answers you. Spell cooldowns reduced by N. | Spell cooldowns reduced | `textures/skill/magic/tempo.png` | ✅ generated, resolves |
| `triple_threat` | magic | Apotheosis/Apothic | You wield three traditions in concert. While Iron's, Ars, and Apotheosis are all loaded, gain N to max mana... | Three traditions in concert | `textures/skill/magic/triple_threat.png` | ✅ generated, resolves |
| `unified_arcana` | magic | Ars Nouveau | Cross-caster backflow. N of the Source cost of a successful Ars cast is refunded to your Iron's mana pool. | Source refunds Iron's mana | `textures/skill/magic/unified_arcana.png` | ✅ generated, resolves |
| `void_magic` | magic | base | Ender-based damage increased by N. | Void-charged ender damage | `textures/skill/magic/void_magic.png` | ✅ generated, resolves |
| `wellspring` | magic | Iron's Spellbooks | Your arcane reserves deepen. Gain N maximum mana. | Deepened mana wellspring | `textures/skill/magic/wellspring.png` | ✅ generated, resolves |
| `wormhole_storage` | magic | base | You can access to ender chest from inventory. | Wormhole ender chest access | `textures/skill/magic/wormhole_storage.png` | ✅ generated, resolves |
| `armor_piercing` | strength | base | Your strikes find the weak points in armor, ignoring N of the enemy's armor. | Pierced plate — armor ignored | `textures/skill/strength/armor_piercing.png` | ✅ generated, resolves |
| `berserker` | strength | base | If you have less than or equal to N of your maximum health, all your attacks will become critical damage. | Berserk axe — low-health crits | `textures/skill/strength/berserker.png` | ✅ generated, resolves |
| `blade_storm` | strength | base | Attack speed increases by N when striking multiple enemies. | Whirling blades vs crowds | `textures/skill/strength/blade_storm.png` | ✅ generated, resolves |
| `blood_fury` | strength | base | Critical hits steal N of the damage dealt as health. | Crit lifesteal drop | `textures/skill/strength/blood_fury.png` | ✅ generated, resolves |
| `bloodlust` | strength | base | Each kill grants you N bonus attack speed for a short duration. | Bloodied fangs — kill frenzy speed | `textures/skill/strength/bloodlust.png` | ✅ generated, resolves |
| `boss_hunter` | strength | base | Your strength gives you an edge against powerful bosses, dealing N bonus damage. | Crowned prey — boss damage | `textures/skill/strength/boss_hunter.png` | ✅ generated, resolves |
| `brutal_swing` | strength | base | Two-handed weapons deal N bonus damage in your powerful hands. | Two-handed brutal swing | `textures/skill/strength/brutal_swing.png` | ✅ generated, resolves |
| `cataclysms_wrath` | strength | base | Cataclysm weapons deal N bonus damage in your hands. | Cataclysm blade empowered | `textures/skill/strength/cataclysms_wrath.png` | ✅ generated, resolves |
| `chain_lightning_strike` | strength | base | Your melee attacks have a N chance to chain lightning to nearby enemies. | Melee chains lightning | `textures/skill/strength/chain_lightning_strike.png` | ✅ generated, resolves |
| `cleave` | strength | base | Your melee attacks cleave through enemies, hitting additional nearby targets. | Cleaving strike hits nearby foes | `textures/skill/strength/cleave.png` | ✅ generated, resolves |
| `devastating_blow` | strength | base | Attacks against enemies at full health deal N bonus damage. | Opening blow vs full health | `textures/skill/strength/devastating_blow.png` | ✅ generated, resolves |
| `draconic_fury` | strength | base | Dragon-type weapon attacks gain N bonus fire damage. | Dragon weapon fire damage | `textures/skill/strength/draconic_fury.png` | ✅ generated, resolves |
| `dragon_bone_mastery` | strength | base | Your knowledge of dragon bone weapons grants N bonus damage when wielding them. | Dragon bone weapon damage | `textures/skill/strength/dragon_bone_mastery.png` | ✅ generated, resolves |
| `dragon_slayer` | strength | base | Your strength allows you to deal N bonus damage to dragons and mythical creatures. | Slayer's edge vs dragons | `textures/skill/strength/dragon_slayer.png` | ✅ generated, resolves |
| `execute` | strength | base | Deal N bonus damage to enemies below 25%% health. | Execution — finish the weakened | `textures/skill/strength/execute.png` | ✅ generated, resolves |
| `fighting_spirit` | strength | base | When you kill an enemy you will gain Strength N for N. | Kill-fueled strength | `textures/skill/strength/fighting_spirit.png` | ✅ generated, resolves |
| `gladiator` | strength | base | Your shield bash damage is increased by N. | Shield bash damage | `textures/skill/strength/gladiator.png` | ✅ generated, resolves |
| `heavy_strikes` | strength | base | Your powerful blows deal N bonus damage to enemies who are blocking with a shield. | Guard-crushing strikes | `textures/skill/strength/heavy_strikes.png` | ✅ generated, resolves |
| `last_stand` | strength | base | At 1 HP, gain brief invulnerability and N bonus damage. | Last banner at 1 HP | `textures/skill/strength/last_stand.png` | ✅ generated, resolves |
| `mowzies_might` | strength | base | Mowzie's Mobs weapon abilities deal N more damage. | Mowzie ability might | `textures/skill/strength/mowzies_might.png` | ✅ generated, resolves |
| `mythical_berserker` | strength | base | Survive a killing blow at 1 HP and gain N bonus damage for a short time. | Deathless rage | `textures/skill/strength/mythical_berserker.png` | ✅ generated, resolves |
| `nichirin_blade` | strength | base | Nichirin katanas deal N bonus damage in your skilled hands. | Nichirin katana damage | `textures/skill/strength/nichirin_blade.png` | ✅ generated, resolves |
| `one_handed` | strength | base | Weapon damage will increase by N if you only use one hand. | One-handed finesse damage | `textures/skill/strength/one_handed.png` | ✅ generated, resolves |
| `polearm_mastery` | strength | base | Your mastery of polearms grants N bonus damage with halberds and lances. | Halberd and lance mastery | `textures/skill/strength/polearm_mastery.png` | ✅ generated, resolves |
| `power_attack` | strength | base | Your critical hits deal an additional N bonus damage. | Crit power surge | `textures/skill/strength/power_attack.png` | ✅ generated, resolves |
| `primal_fury` | strength | base | When below 50%% health, your desperation grants N bonus attack damage. | Primal fury when wounded | `textures/skill/strength/primal_fury.png` | ✅ generated, resolves |
| `reapers_edge` | strength | Apotheosis/Apothic | Even giants bleed. Fully-charged strikes also deal N of the target's current HP as armor-piercing damage. | Reaper's cut — current-HP damage | `textures/skill/strength/reapers_edge.png` | ✅ generated, resolves |
| `runic_might` | strength | base | Runic ore weapons deal N bonus damage. | Runic weapon damage | `textures/skill/strength/runic_might.png` | ✅ generated, resolves |
| `sacred_fire` | strength | base | Your attacks set enemies ablaze with sacred flame. | Sacred flame ignites foes | `textures/skill/strength/sacred_fire.png` | ✅ generated, resolves |
| `samurais_edge` | strength | base | Your training with the katana grants N bonus damage with katana weapons. | Katana training damage | `textures/skill/strength/samurais_edge.png` | ✅ generated, resolves |
| `siege_breaker` | strength | base | Your strength lets you deal N bonus damage to boss entities. | Siege maul vs bosses | `textures/skill/strength/siege_breaker.png` | ✅ generated, resolves |
| `spartans_discipline` | strength | base | Your mastery of Spartan weapons grants N bonus damage with all Spartan weaponry. | Spartan weapon discipline | `textures/skill/strength/spartans_discipline.png` | ✅ generated, resolves |
| `spectral_ward` | strength | Apotheosis/Apothic | Your blows ignore the trappings of defense. +N protection pierce and +N protection shred. | Blows ignore defenses | `textures/skill/strength/spectral_ward.png` | ✅ generated, resolves |
| `stalwart_striker` | strength | base | Killing dungeon mobs restores N health. | Dungeon kills restore health | `textures/skill/strength/stalwart_striker.png` | ✅ generated, resolves |
| `titans_grip` | strength | base | Your immense strength allows you to wield two-handed weapons alongside a shield. | Titan grip — 2H with shield | `textures/skill/strength/titans_grip.png` | ✅ generated, resolves |
| `trophy_hunter` | strength | base | Deal N bonus damage to elite and boss mobs. | Elite trophy damage | `textures/skill/strength/trophy_hunter.png` | ✅ generated, resolves |
| `unstoppable_force` | strength | base | Your strikes have a N chance to briefly stun enemies. | Stunning impact chance | `textures/skill/strength/unstoppable_force.png` | ✅ generated, resolves |
| `vampiric_fangs` | strength | Apotheosis/Apothic | Every wound feeds you back. N of physical damage dealt is restored as health. | Vampiric lifesteal fangs | `textures/skill/strength/vampiric_fangs.png` | ✅ generated, resolves |
| `vengeance` | strength | base | Deal N bonus damage to the last entity that struck you. | Vengeful strike at your attacker | `textures/skill/strength/vengeance.png` | ✅ generated, resolves |
| `warlords_presence` | strength | base | Nearby allies deal N bonus damage inspired by your presence. | Warlord banner — allies inspired | `textures/skill/strength/warlords_presence.png` | ✅ generated, resolves |
| `warmonger` | strength | base | You deal N bonus damage to enemies wearing armor. | Armor-breaker damage | `textures/skill/strength/warmonger.png` | ✅ generated, resolves |
| `weapon_master` | strength | base | All weapon types deal N bonus damage. | All-weapon mastery | `textures/skill/strength/weapon_master.png` | ✅ generated, resolves |
| `alloy_master` | tinkering | base | Alloying produces N more ingots. | Alloy furnace — extra ingots | `textures/skill/tinkering/alloy_master.png` | ✅ generated, resolves |
| `armor_smith` | tinkering | base | Repaired armor gains N bonus protection. | Smithed armor bonus protection | `textures/skill/tinkering/armor_smith.png` | ✅ generated, resolves |
| `assembly_line` | tinkering | base | Batch crafting produces N more items. | Assembly gear — batch output | `textures/skill/tinkering/assembly_line.png` | ✅ generated, resolves |
| `auto_repair` | tinkering | base | Equipped items passively repair at N rate. | Self-repairing gear | `textures/skill/tinkering/auto_repair.png` | ✅ generated, resolves |
| `backpack_engineer` | tinkering | base | Backpack capacity increased by N slots. | Expanded backpack slots | `textures/skill/tinkering/backpack_engineer.png` | ✅ generated, resolves |
| `ballistic_expert` | tinkering | base | Ranged mechanical weapons deal N more damage. | Mechanical ranged damage | `textures/skill/tinkering/ballistic_expert.png` | ✅ generated, resolves |
| `brewing_apparatus` | tinkering | base | Brewing stand speed increased by N. | Tuned brewing stand speed | `textures/skill/tinkering/brewing_apparatus.png` | ✅ generated, resolves |
| `circuit_breaker` | tinkering | base | Redstone signal range increased by N blocks. | Extended redstone signal | `textures/skill/tinkering/circuit_breaker.png` | ✅ generated, resolves |
| `clockwork_mastery` | tinkering | base | Timed mechanisms are N more accurate. | Precise clockwork timing | `textures/skill/tinkering/clockwork_mastery.png` | ✅ generated, resolves |
| `disassembler` | tinkering | base | You have a N chance to recover components from items. | Disassembled component recovery | `textures/skill/tinkering/disassembler.png` | ✅ generated, resolves |
| `enchantment_transfer` | tinkering | base | You have a N chance to transfer enchantments between items. | Enchant transfer chance | `textures/skill/tinkering/enchantment_transfer.png` | ✅ generated, resolves |
| `explosive_ordinance` | tinkering | base | TNT blast radius increased by N. | Bigger TNT blast radius | `textures/skill/tinkering/explosive_ordinance.png` | ✅ generated, resolves |
| `forge_master` | tinkering | base | Smelting yields N more output. | Forge yields more output | `textures/skill/tinkering/forge_master.png` | ✅ generated, resolves |
| `gadget_upgrade` | tinkering | base | Backpack upgrades are N more effective. | Upgraded pack modules | `textures/skill/tinkering/gadget_upgrade.png` | ✅ generated, resolves |
| `gadgeteer` | tinkering | base | Mechanical items are N more effective. | Gadgets more effective | `textures/skill/tinkering/gadgeteer.png` | ✅ generated, resolves |
| `inventor` | tinkering | base | You have a N chance to discover improved recipe variants. | Inventive recipe variants | `textures/skill/tinkering/inventor.png` | ✅ generated, resolves |
| `key_forge` | tinkering | base | Crafted keys have a N chance to be master keys. | Forged master key chance | `textures/skill/tinkering/key_forge.png` | ✅ generated, resolves |
| `lock_expert` | tinkering | base | All locks take N less time to pick. | Locks picked faster | `textures/skill/tinkering/lock_expert.png` | ✅ generated, resolves |
| `locksmith` | tinkering | base | Your tinkering skill grants a 1 in N chance for lock picks to not break. | Lockpicks spared from breaking | `textures/skill/tinkering/locksmith.png` | ✅ generated, resolves |
| `master_artificer` | tinkering | base | Crafted items have a N chance for a bonus enchantment. | Artificer bonus enchantment | `textures/skill/tinkering/master_artificer.png` | ✅ generated, resolves |
| `master_tinkerer` | tinkering | base | Items you craft gain N bonus durability from your tinkering mastery. | Tinkered durability bonus | `textures/skill/tinkering/master_tinkerer.png` | ✅ generated, resolves |
| `mechanical_arm` | tinkering | base | Block interaction range increased by N blocks. | Extended interaction reach | `textures/skill/tinkering/mechanical_arm.png` | ✅ generated, resolves |
| `mechanical_knowledge` | tinkering | base | Redstone devices work N faster. | Faster redstone devices | `textures/skill/tinkering/mechanical_knowledge.png` | ✅ generated, resolves |
| `mechanism_mastery` | tinkering | base | Complex mechanisms activate N faster. | Mechanisms trigger faster | `textures/skill/tinkering/mechanism_mastery.png` | ✅ generated, resolves |
| `modular_equipment` | tinkering | base | Equipment modification slots increased by N. | Extra modification slots | `textures/skill/tinkering/modular_equipment.png` | ✅ generated, resolves |
| `overclock` | tinkering | base | All crafting stations work N faster. | Overclocked crafting stations | `textures/skill/tinkering/overclock.png` | ✅ generated, resolves |
| `power_tools` | tinkering | base | Tool efficiency enchantment level effectively increased by N. | Powered tool efficiency | `textures/skill/tinkering/power_tools.png` | ✅ generated, resolves |
| `precision_tools` | tinkering | base | Tool durability increased by N. | Precision-built tool durability | `textures/skill/tinkering/precision_tools.png` | ✅ generated, resolves |
| `repair_expert` | tinkering | base | Anvil repair costs N less materials. | Cheaper anvil repairs | `textures/skill/tinkering/repair_expert.png` | ✅ generated, resolves |
| `runic_engineering` | tinkering | base | Runic items gain N bonus effects when repaired. | Runic repair bonus effects | `textures/skill/tinkering/runic_engineering.png` | ✅ generated, resolves |
| `safe_builder` | tinkering | base | Built locks are N more secure. | Hardened built locks | `textures/skill/tinkering/safe_builder.png` | ✅ generated, resolves |
| `safe_cracker` | tinkering | base | Your expertise reduces lock Complexity enchantment effectiveness by N levels. | Cracked lock complexity | `textures/skill/tinkering/safe_cracker.png` | ✅ generated, resolves |
| `salvage_master` | tinkering | base | Salvaging items yields N more materials. | Master salvage yields | `textures/skill/tinkering/salvage_master.png` | ✅ generated, resolves |
| `siege_mechanic` | tinkering | base | Siege machines reload N faster. | Siege engines reload faster | `textures/skill/tinkering/siege_mechanic.png` | ✅ generated, resolves |
| `spring_loaded` | tinkering | base | Jump boost from tinker items increased by N. | Spring-boosted jumps | `textures/skill/tinkering/spring_loaded.png` | ✅ generated, resolves |
| `tinkers_touch` | tinkering | base | Items you craft gain N bonus durability. | Tinker's crafted durability | `textures/skill/tinkering/tinkers_touch.png` | ✅ generated, resolves |
| `tool_smith` | tinkering | base | Repaired tools gain N bonus efficiency. | Repaired tool efficiency | `textures/skill/tinkering/tool_smith.png` | ✅ generated, resolves |
| `trap_maker` | tinkering | base | Placed traps deal N more damage. | Deadlier placed traps | `textures/skill/tinkering/trap_maker.png` | ✅ generated, resolves |
| `waystone_tinker` | tinkering | base | Waystone teleportation cost reduced by N. | Cheaper waystone travel | `textures/skill/tinkering/waystone_tinker.png` | ✅ generated, resolves |
| `weapon_smith` | tinkering | base | Repaired weapons gain N bonus damage. | Repaired weapon damage | `textures/skill/tinkering/weapon_smith.png` | ✅ generated, resolves |
| `ancient_inscriptions` | wisdom | base | Ancient artifacts provide N bonus effects. | Ancient artifact inscriptions | `textures/skill/wisdom/ancient_inscriptions.png` | ✅ generated, resolves |
| `apotheosis_wisdom` | wisdom | base | Apotheosis enchantment cap increased by N. | Raised enchantment cap | `textures/skill/wisdom/apotheosis_wisdom.png` | ✅ generated, resolves |
| `arcane_linguist` | wisdom | base | Reading spell types grants N bonus effectiveness. | Spell text fluency | `textures/skill/wisdom/arcane_linguist.png` | ✅ generated, resolves |
| `arcane_ward` | wisdom | base | Your knowledge of arcane constructs provides protection, reducing incoming Ars Nouveau spell damage by N. | Ward vs Ars constructs | `textures/skill/wisdom/arcane_ward.png` | ✅ generated, resolves |
| `ars_savant` | wisdom | base | Ars Nouveau familiar abilities improved by N. | Savant familiar abilities | `textures/skill/wisdom/ars_savant.png` | ✅ generated, resolves |
| `aura_attunement` | wisdom | base | Your attunement to nature increases the efficiency of aura effects near you by N. | Attuned aura efficiency | `textures/skill/wisdom/aura_attunement.png` | ✅ generated, resolves |
| `bookcraft` | wisdom | base | Enchanted books sell for N more to villagers. | Enchanted books sell higher | `textures/skill/wisdom/bookcraft.png` | ✅ generated, resolves |
| `curse_breaker` | wisdom | base | You can remove curses from items at the grindstone. | Curses ground away | `textures/skill/wisdom/curse_breaker.png` | ✅ generated, resolves |
| `dimensional_wisdom` | wisdom | base | Enchantments work N better in other dimensions. | Enchants stronger off-world | `textures/skill/wisdom/dimensional_wisdom.png` | ✅ generated, resolves |
| `disenchant_mastery` | wisdom | base | The grindstone returns N more XP. | Grindstone returns more XP | `textures/skill/wisdom/disenchant_mastery.png` | ✅ generated, resolves |
| `druidic_knowledge` | wisdom | base | Nature enchantments are N stronger. | Nature enchantments stronger | `textures/skill/wisdom/druidic_knowledge.png` | ✅ generated, resolves |
| `elder_knowledge` | wisdom | base | XP from enchanting is returned by N on completion. | Enchanting XP returned | `textures/skill/wisdom/elder_knowledge.png` | ✅ generated, resolves |
| `enchanters_insight` | wisdom | base | Your deep understanding of enchantments reduces the XP cost of enchanting by N. | Cheaper enchanting XP | `textures/skill/wisdom/enchanters_insight.png` | ✅ generated, resolves |
| `enchantment_amplifier` | wisdom | base | Anvil enchantments have a N chance for a bonus level. | Anvil bonus level chance | `textures/skill/wisdom/enchantment_amplifier.png` | ✅ generated, resolves |
| `enchantment_preservation` | wisdom | base | You have a N chance for enchantments to be kept on death. | Enchants kept on death | `textures/skill/wisdom/enchantment_preservation.png` | ✅ generated, resolves |
| `enchantment_stacking` | wisdom | base | You have a N chance to add an additional enchantment. | Additional enchantment chance | `textures/skill/wisdom/enchantment_stacking.png` | ✅ generated, resolves |
| `enlightenment` | wisdom | base | All skill XP gain increased by N. | Enlightened skill XP | `textures/skill/wisdom/enlightenment.png` | ✅ generated, resolves |
| `experienced_enchanter` | wisdom | base | Enchanting costs N less XP. | Enchanting costs less XP | `textures/skill/wisdom/experienced_enchanter.png` | ✅ generated, resolves |
| `focus` | wisdom | Iron's Spellbooks | Your concentration is iron. Taking damage during a long or charged cast has a N chance to not interrupt you. | Iron concentration — casts uninterrupted | `textures/skill/wisdom/focus.png` | ✅ generated, resolves |
| `grand_sage` | wisdom | base | All wisdom-based bonuses are amplified by N. | Grand sage amplification | `textures/skill/wisdom/grand_sage.png` | ✅ generated, resolves |
| `imbued_focus` | wisdom | Iron's Spellbooks | Channel deeper arcane pathways. All your spells cast at +N effective level. | Spells cast at higher level | `textures/skill/wisdom/imbued_focus.png` | ✅ generated, resolves |
| `lapis_conservation` | wisdom | base | Lapis has a N chance to not be consumed when enchanting. | Lapis spared when enchanting | `textures/skill/wisdom/lapis_conservation.png` | ✅ generated, resolves |
| `lore_mastery` | wisdom | base | Your mastery of arcane lore causes the grindstone to return N times more XP. | Lore-mastered grindstone XP | `textures/skill/wisdom/lore_mastery.png` | ✅ generated, resolves |
| `mending_boost` | wisdom | base | Mending repair rate increased by N. | Faster mending repair | `textures/skill/wisdom/mending_boost.png` | ✅ generated, resolves |
| `mystic_attunement` | wisdom | base | All magical items gain N effectiveness. | Magical items attuned | `textures/skill/wisdom/mystic_attunement.png` | ✅ generated, resolves |
| `mystic_sight` | wisdom | base | You can see enchantments on items within a short radius. | See enchantments at a glance | `textures/skill/wisdom/mystic_sight.png` | ✅ generated, resolves |
| `nature_sage` | wisdom | base | Nature's Aura infusions last N longer. | Longer aura infusions | `textures/skill/wisdom/nature_sage.png` | ✅ generated, resolves |
| `rune_mastery` | wisdom | base | Rune-based enchantments are N more effective. | Rune enchantments effective | `textures/skill/wisdom/rune_mastery.png` | ✅ generated, resolves |
| `runic_enchantment` | wisdom | base | Enchantments on runic gear are N stronger. | Runic gear enchants stronger | `textures/skill/wisdom/runic_enchantment.png` | ✅ generated, resolves |
| `scroll_scribe` | wisdom | base | You can create scrolls that replicate enchantments. | Scribe enchantment scrolls | `textures/skill/wisdom/scroll_scribe.png` | ✅ generated, resolves |
| `soul_binding` | wisdom | base | Items with Soul Bound enchantment never drop on death. | Soulbound items never drop | `textures/skill/wisdom/soul_binding.png` | ✅ generated, resolves |
| `spell_inscription` | wisdom | base | Inscribed spells cost N less mana. | Inscribed spells cost less | `textures/skill/wisdom/spell_inscription.png` | ✅ generated, resolves |
| `temporal_wisdom` | wisdom | base | Enchantment effects last N longer in combat. | Enchant effects linger in combat | `textures/skill/wisdom/temporal_wisdom.png` | ✅ generated, resolves |
| `tome_of_knowledge` | wisdom | base | Books store N more enchantment levels. | Books hold more levels | `textures/skill/wisdom/tome_of_knowledge.png` | ✅ generated, resolves |
| `unbreaking_mastery` | wisdom | base | Unbreaking enchantment chance increased by N. | Unbreaking chance improved | `textures/skill/wisdom/unbreaking_mastery.png` | ✅ generated, resolves |
| `ward_master` | wisdom | base | Protective wards last N longer. | Wards last longer | `textures/skill/wisdom/ward_master.png` | ✅ generated, resolves |
| `wisdom_of_ages` | wisdom | base | Anvil repair cost reduced by N. | Aged wisdom — cheap repairs | `textures/skill/wisdom/wisdom_of_ages.png` | ✅ generated, resolves |
