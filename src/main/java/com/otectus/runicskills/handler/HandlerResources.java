package com.otectus.runicskills.handler;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;

public class HandlerResources {
    public static final ResourceLocation[] PERK_PAGE = new ResourceLocation[]{
            create("textures/gui/container/skill_page_1.png"),
            create("textures/gui/container/skill_page_2.png"),
            create("textures/gui/container/skill_page_3.png")
    };

    public static final ResourceLocation[] SKILL_PANEL = new ResourceLocation[]{
            create("textures/gui/container/skill_panel_1.png"),
            create("textures/gui/container/skill_panel_2.png"),
            create("textures/gui/container/skill_panel_3.png")
    };

    public static final ResourceLocation SKILL_CARD_HOVER = create("textures/gui/container/skill_card_hover.png");

    public static final ResourceLocation[] STRENGTH_LOCKED_ICON = new ResourceLocation[]{
            create("textures/skill/strength/locked_0.png"),
            create("textures/skill/strength/locked_8.png"),
            create("textures/skill/strength/locked_16.png"),
            create("textures/skill/strength/locked_24.png")
    };

    public static final ResourceLocation[] CONSTITUTION_LOCKED_ICON = new ResourceLocation[]{
            create("textures/skill/constitution/locked_0.png"),
            create("textures/skill/constitution/locked_8.png"),
            create("textures/skill/constitution/locked_16.png"),
            create("textures/skill/constitution/locked_24.png")
    };

    public static final ResourceLocation[] DEXTERITY_LOCKED_ICON = new ResourceLocation[]{
            create("textures/skill/dexterity/locked_0.png"),
            create("textures/skill/dexterity/locked_8.png"),
            create("textures/skill/dexterity/locked_16.png"),
            create("textures/skill/dexterity/locked_24.png")
    };

    public static final ResourceLocation[] ENDURANCE_LOCKED_ICON = new ResourceLocation[]{
            create("textures/skill/endurance/locked_0.png"),
            create("textures/skill/endurance/locked_8.png"),
            create("textures/skill/endurance/locked_16.png"),
            create("textures/skill/endurance/locked_24.png")
    };

    public static final ResourceLocation[] INTELLIGENCE_LOCKED_ICON = new ResourceLocation[]{
            create("textures/skill/intelligence/locked_0.png"),
            create("textures/skill/intelligence/locked_8.png"),
            create("textures/skill/intelligence/locked_16.png"),
            create("textures/skill/intelligence/locked_24.png")
    };

    public static final ResourceLocation[] BUILDING_LOCKED_ICON = new ResourceLocation[]{
            create("textures/skill/building/locked_0.png"),
            create("textures/skill/building/locked_8.png"),
            create("textures/skill/building/locked_16.png"),
            create("textures/skill/building/locked_24.png")
    };

    public static final ResourceLocation[] WISDOM_LOCKED_ICON = new ResourceLocation[]{
            create("textures/skill/wisdom/locked_0.png"),
            create("textures/skill/wisdom/locked_8.png"),
            create("textures/skill/wisdom/locked_16.png"),
            create("textures/skill/wisdom/locked_24.png")
    };

    public static final ResourceLocation[] MAGIC_LOCKED_ICON = new ResourceLocation[]{
            create("textures/skill/magic/locked_0.png"),
            create("textures/skill/magic/locked_8.png"),
            create("textures/skill/magic/locked_16.png"),
            create("textures/skill/magic/locked_24.png")
    };

    public static final ResourceLocation[] FORTUNE_LOCKED_ICON = new ResourceLocation[]{
            create("textures/skill/fortune/locked_0.png"),
            create("textures/skill/fortune/locked_8.png"),
            create("textures/skill/fortune/locked_16.png"),
            create("textures/skill/fortune/locked_24.png")
    };

    public static final ResourceLocation[] TINKERING_LOCKED_ICON = new ResourceLocation[]{
            create("textures/skill/tinkering/locked_0.png"),
            create("textures/skill/tinkering/locked_8.png"),
            create("textures/skill/tinkering/locked_16.png"),
            create("textures/skill/tinkering/locked_24.png")
    };

    public static final ResourceLocation PERK_ICONS = create("textures/skill/icons.png");
    public static final ResourceLocation NULL_PERK = create("textures/skill/null_skill.png");

    // ========== STRENGTH Perks ==========
    public static final ResourceLocation ONE_HANDED_PERK = create("textures/skill/strength/one_handed.png");
    public static final ResourceLocation FIGHTING_SPIRIT_PERK = create("textures/skill/strength/fighting_spirit.png");
    public static final ResourceLocation BERSERKER_PERK = create("textures/skill/strength/berserker.png");
    public static final ResourceLocation ARMOR_PIERCING_PERK = create("textures/skill/strength/armor_piercing.png");
    public static final ResourceLocation HEAVY_STRIKES_PERK = create("textures/skill/strength/heavy_strikes.png");
    public static final ResourceLocation CLEAVE_PERK = create("textures/skill/strength/cleave.png");
    public static final ResourceLocation TITANS_GRIP_PERK = create("textures/skill/strength/titans_grip.png");
    public static final ResourceLocation SAMURAIS_EDGE_PERK = create("textures/skill/strength/samurais_edge.png");
    public static final ResourceLocation BRUTAL_SWING_PERK = create("textures/skill/strength/brutal_swing.png");
    public static final ResourceLocation POLEARM_MASTERY_PERK = create("textures/skill/strength/polearm_mastery.png");
    public static final ResourceLocation WARMONGER_PERK = create("textures/skill/strength/warmonger.png");
    public static final ResourceLocation EXECUTE_PERK = create("textures/skill/strength/execute.png");
    public static final ResourceLocation BLOODLUST_PERK = create("textures/skill/strength/bloodlust.png");
    public static final ResourceLocation DRAGON_BONE_MASTERY_PERK = create("textures/skill/strength/dragon_bone_mastery.png");
    public static final ResourceLocation NICHIRIN_BLADE_PERK = create("textures/skill/strength/nichirin_blade.png");
    public static final ResourceLocation SIEGE_BREAKER_PERK = create("textures/skill/strength/siege_breaker.png");
    public static final ResourceLocation MOWZIES_MIGHT_PERK = create("textures/skill/strength/mowzies_might.png");
    public static final ResourceLocation SPARTANS_DISCIPLINE_PERK = create("textures/skill/strength/spartans_discipline.png");
    public static final ResourceLocation POWER_ATTACK_PERK = create("textures/skill/strength/power_attack.png");
    public static final ResourceLocation UNSTOPPABLE_FORCE_PERK = create("textures/skill/strength/unstoppable_force.png");
    public static final ResourceLocation PRIMAL_FURY_PERK = create("textures/skill/strength/primal_fury.png");
    public static final ResourceLocation VENGEANCE_PERK = create("textures/skill/strength/vengeance.png");
    public static final ResourceLocation LAST_STAND_PERK = create("textures/skill/strength/last_stand.png");
    public static final ResourceLocation WARLORDS_PRESENCE_PERK = create("textures/skill/strength/warlords_presence.png");
    public static final ResourceLocation CHAIN_LIGHTNING_STRIKE_PERK = create("textures/skill/strength/chain_lightning_strike.png");
    public static final ResourceLocation BLADE_STORM_PERK = create("textures/skill/strength/blade_storm.png");
    public static final ResourceLocation DEVASTATING_BLOW_PERK = create("textures/skill/strength/devastating_blow.png");
    public static final ResourceLocation SACRED_FIRE_PERK = create("textures/skill/strength/sacred_fire.png");
    public static final ResourceLocation BLOOD_FURY_PERK = create("textures/skill/strength/blood_fury.png");
    public static final ResourceLocation CATACLYSMS_WRATH_PERK = create("textures/skill/strength/cataclysms_wrath.png");
    public static final ResourceLocation ANCIENT_STRENGTH_PERK = create("textures/skill/strength/ancient_strength.png");
    public static final ResourceLocation GLADIATOR_PERK = create("textures/skill/strength/gladiator.png");
    public static final ResourceLocation TROPHY_HUNTER_PERK = create("textures/skill/strength/trophy_hunter.png");
    public static final ResourceLocation DRACONIC_FURY_PERK = create("textures/skill/strength/draconic_fury.png");
    public static final ResourceLocation MYTHICAL_BERSERKER_PERK = create("textures/skill/strength/mythical_berserker.png");
    public static final ResourceLocation STALWART_STRIKER_PERK = create("textures/skill/strength/stalwart_striker.png");
    public static final ResourceLocation WEAPON_MASTER_PERK = create("textures/skill/strength/weapon_master.png");
    public static final ResourceLocation RUNIC_MIGHT_PERK = create("textures/skill/strength/runic_might.png");
    public static final ResourceLocation DRAGON_SLAYER_PERK = create("textures/skill/strength/dragon_slayer.png");
    public static final ResourceLocation BOSS_HUNTER_PERK = create("textures/skill/strength/boss_hunter.png");

    // ========== CONSTITUTION Perks ==========
    public static final ResourceLocation ATHLETICS_PERK = create("textures/skill/constitution/athletics.png");
    public static final ResourceLocation TURTLE_SHIELD_PERK = create("textures/skill/constitution/turtle_shield.png");
    public static final ResourceLocation LION_HEART_PERK = create("textures/skill/constitution/lion_heart.png");
    public static final ResourceLocation IRON_STOMACH_PERK = create("textures/skill/constitution/iron_stomach.png");
    public static final ResourceLocation SECOND_WIND_PERK = create("textures/skill/constitution/second_wind.png");
    public static final ResourceLocation VITALITY_PERK = create("textures/skill/constitution/vitality.png");
    public static final ResourceLocation NATURAL_RECOVERY_PERK = create("textures/skill/constitution/natural_recovery.png");
    public static final ResourceLocation THICK_SKIN_PERK = create("textures/skill/constitution/thick_skin.png");
    public static final ResourceLocation POISON_IMMUNITY_PERK = create("textures/skill/constitution/poison_immunity.png");
    public static final ResourceLocation FIRE_RESISTANCE_PERK = create("textures/skill/constitution/fire_resistance.png");
    public static final ResourceLocation DRACONIC_CONSTITUTION_PERK = create("textures/skill/constitution/draconic_constitution.png");
    public static final ResourceLocation CULINARY_EXPERT_PERK = create("textures/skill/constitution/culinary_expert.png");
    public static final ResourceLocation ANGLERS_BOUNTY_PERK = create("textures/skill/constitution/anglers_bounty.png");
    public static final ResourceLocation BLOOD_SACRIFICE_RECOVERY_PERK = create("textures/skill/constitution/blood_sacrifice_recovery.png");
    public static final ResourceLocation SEARING_RESISTANCE_PERK = create("textures/skill/constitution/searing_resistance.png");
    public static final ResourceLocation WITHER_RESISTANCE_PERK = create("textures/skill/constitution/wither_resistance.png");
    public static final ResourceLocation UNDYING_WILL_PERK = create("textures/skill/constitution/undying_will.png");
    public static final ResourceLocation HEARTY_FEAST_PERK = create("textures/skill/constitution/hearty_feast.png");
    public static final ResourceLocation DRAGON_HEART_PERK = create("textures/skill/constitution/dragon_heart.png");
    public static final ResourceLocation SWIMMERS_ENDURANCE_PERK = create("textures/skill/constitution/swimmers_endurance.png");
    public static final ResourceLocation EXPLORERS_VIGOR_PERK = create("textures/skill/constitution/explorers_vigor.png");
    public static final ResourceLocation AURA_OF_VITALITY_PERK = create("textures/skill/constitution/aura_of_vitality.png");
    public static final ResourceLocation BATTLE_RECOVERY_PERK = create("textures/skill/constitution/battle_recovery.png");
    public static final ResourceLocation ARMOR_OF_FAITH_PERK = create("textures/skill/constitution/armor_of_faith.png");
    public static final ResourceLocation SOUL_SUSTENANCE_PERK = create("textures/skill/constitution/soul_sustenance.png");
    public static final ResourceLocation ENIGMATIC_VITALITY_PERK = create("textures/skill/constitution/enigmatic_vitality.png");
    public static final ResourceLocation COLONIAL_NOURISHMENT_PERK = create("textures/skill/constitution/colonial_nourishment.png");
    public static final ResourceLocation BLOOD_SHIELD_PERK = create("textures/skill/constitution/blood_shield.png");
    public static final ResourceLocation OBSIDIAN_HEART_PERK = create("textures/skill/constitution/obsidian_heart.png");
    public static final ResourceLocation POTION_MASTERY_PERK = create("textures/skill/constitution/potion_mastery.png");
    public static final ResourceLocation PHOENIX_RISING_PERK = create("textures/skill/constitution/phoenix_rising.png");
    public static final ResourceLocation NATURES_BLESSING_PERK = create("textures/skill/constitution/natures_blessing.png");
    public static final ResourceLocation RUNIC_FORTIFICATION_PERK = create("textures/skill/constitution/runic_fortification.png");
    public static final ResourceLocation GOURMET_PERK = create("textures/skill/constitution/gourmet.png");
    public static final ResourceLocation FROST_WALKER_CONSTITUTION_PERK = create("textures/skill/constitution/frost_walker_constitution.png");
    public static final ResourceLocation MYRMEX_CARAPACE_PERK = create("textures/skill/constitution/myrmex_carapace.png");
    public static final ResourceLocation ENDERIUM_RESILIENCE_PERK = create("textures/skill/constitution/enderium_resilience.png");
    public static final ResourceLocation SURVIVAL_INSTINCT_PERK = create("textures/skill/constitution/survival_instinct.png");
    public static final ResourceLocation BLOOD_MASTERY_PERK = create("textures/skill/constitution/blood_mastery.png");
    public static final ResourceLocation MASTER_CHEF_PERK = create("textures/skill/constitution/master_chef.png");

    // ========== DEXTERITY Perks ==========
    public static final ResourceLocation QUICK_REPOSITION_PERK = create("textures/skill/dexterity/quick_reposition.png");
    public static final ResourceLocation STEALTH_MASTERY_PERK = create("textures/skill/dexterity/stealth_mastery.png");
    public static final ResourceLocation CAT_EYES_PERK = create("textures/skill/dexterity/cat_eyes.png");
    public static final ResourceLocation EAGLE_EYE_PERK = create("textures/skill/dexterity/eagle_eye.png");
    public static final ResourceLocation RAPID_FIRE_PERK = create("textures/skill/dexterity/rapid_fire.png");
    public static final ResourceLocation MULTISHOT_MASTERY_PERK = create("textures/skill/dexterity/multishot_mastery.png");
    public static final ResourceLocation ARROW_RECOVERY_PERK = create("textures/skill/dexterity/arrow_recovery.png");
    public static final ResourceLocation ACROBAT_PERK = create("textures/skill/dexterity/acrobat.png");
    public static final ResourceLocation DODGE_ROLL_PERK = create("textures/skill/dexterity/dodge_roll.png");
    public static final ResourceLocation SPRINT_MASTER_PERK = create("textures/skill/dexterity/sprint_master.png");
    public static final ResourceLocation SILENT_STEP_PERK = create("textures/skill/dexterity/silent_step.png");
    public static final ResourceLocation PRECISION_SHOT_PERK = create("textures/skill/dexterity/precision_shot.png");
    public static final ResourceLocation ARCHERY_EXPANSION_PERK = create("textures/skill/dexterity/archery_expansion.png");
    public static final ResourceLocation CROSSBOW_EXPERT_PERK = create("textures/skill/dexterity/crossbow_expert.png");
    public static final ResourceLocation SPARTAN_MARKSMANSHIP_PERK = create("textures/skill/dexterity/spartan_marksmanship.png");
    public static final ResourceLocation POISON_ARROW_PERK = create("textures/skill/dexterity/poison_arrow.png");
    public static final ResourceLocation WIND_RUNNER_PERK = create("textures/skill/dexterity/wind_runner.png");
    public static final ResourceLocation NINJA_TRAINING_PERK = create("textures/skill/dexterity/ninja_training.png");
    public static final ResourceLocation PARKOUR_MASTER_PERK = create("textures/skill/dexterity/parkour_master.png");

    public static final ResourceLocation SHARPSHOOTER_PERK = create("textures/skill/dexterity/sharpshooter.png");
    public static final ResourceLocation EVASION_PERK = create("textures/skill/dexterity/evasion.png");
    public static final ResourceLocation FLEET_FOOTED_PERK = create("textures/skill/dexterity/fleet_footed.png");
    public static final ResourceLocation AMBUSH_PERK = create("textures/skill/dexterity/ambush.png");
    public static final ResourceLocation QUICK_DRAW_PERK = create("textures/skill/dexterity/quick_draw.png");
    public static final ResourceLocation RICOCHET_PERK = create("textures/skill/dexterity/ricochet.png");
    public static final ResourceLocation PHANTOM_STRIKE_PERK = create("textures/skill/dexterity/phantom_strike.png");
    public static final ResourceLocation DRAGON_RIDER_PERK = create("textures/skill/dexterity/dragon_rider.png");
    public static final ResourceLocation ICE_ARROWS_PERK = create("textures/skill/dexterity/ice_arrows.png");
    public static final ResourceLocation SPELL_DODGE_PERK = create("textures/skill/dexterity/spell_dodge.png");
    public static final ResourceLocation ZIPLINE_EXPERT_PERK = create("textures/skill/dexterity/zipline_expert.png");
    public static final ResourceLocation SNIPER_PERK = create("textures/skill/dexterity/sniper.png");
    public static final ResourceLocation SMOKE_BOMB_PERK = create("textures/skill/dexterity/smoke_bomb.png");
    public static final ResourceLocation MOUNTED_COMBAT_PERK = create("textures/skill/dexterity/mounted_combat.png");
    public static final ResourceLocation TRACKING_PERK = create("textures/skill/dexterity/tracking.png");
    public static final ResourceLocation WIND_WALKER_PERK = create("textures/skill/dexterity/wind_walker.png");
    public static final ResourceLocation TRICK_SHOT_PERK = create("textures/skill/dexterity/trick_shot.png");
    public static final ResourceLocation BLADE_DANCER_PERK = create("textures/skill/dexterity/blade_dancer.png");
    public static final ResourceLocation SILENT_KILL_PERK = create("textures/skill/dexterity/silent_kill.png");
    public static final ResourceLocation AGILE_CLIMBER_PERK = create("textures/skill/dexterity/agile_climber.png");

    // ========== ENDURANCE Perks ==========
    public static final ResourceLocation SNOW_WALKER_PERK = create("textures/skill/endurance/snow_walker.png");
    public static final ResourceLocation COUNTER_ATTACK_PERK = create("textures/skill/endurance/counter_attack.png");
    public static final ResourceLocation DIAMOND_SKIN_PERK = create("textures/skill/endurance/diamond_skin.png");
    public static final ResourceLocation SHIELD_WALL_PERK = create("textures/skill/endurance/shield_wall.png");
    public static final ResourceLocation HEAVY_ARMOR_MASTERY_PERK = create("textures/skill/endurance/heavy_armor_mastery.png");
    public static final ResourceLocation STEADFAST_PERK = create("textures/skill/endurance/steadfast.png");
    public static final ResourceLocation TOUGHENED_HIDE_PERK = create("textures/skill/endurance/toughened_hide.png");
    public static final ResourceLocation FIRE_PROOF_PERK = create("textures/skill/endurance/fire_proof.png");
    public static final ResourceLocation BLAST_RESISTANCE_PERK = create("textures/skill/endurance/blast_resistance.png");
    public static final ResourceLocation WARDING_RUNE_PERK = create("textures/skill/endurance/warding_rune.png");
    public static final ResourceLocation DRAGON_SCALE_ARMOR_PERK = create("textures/skill/endurance/dragon_scale_armor.png");
    public static final ResourceLocation BULWARK_PERK = create("textures/skill/endurance/bulwark.png");
    public static final ResourceLocation STONEFLESH_PERK = create("textures/skill/endurance/stoneflesh.png");
    public static final ResourceLocation POISON_RESISTANCE_PERK = create("textures/skill/endurance/poison_resistance.png");
    public static final ResourceLocation THORNS_MASTERY_PERK = create("textures/skill/endurance/thorns_mastery.png");
    public static final ResourceLocation SENTINEL_PERK = create("textures/skill/endurance/sentinel.png");
    public static final ResourceLocation DRAGONHIDE_PERK = create("textures/skill/endurance/dragonhide.png");
    public static final ResourceLocation ENIGMATIC_PROTECTION_PERK = create("textures/skill/endurance/enigmatic_protection.png");
    public static final ResourceLocation FANTASY_FORTITUDE_PERK = create("textures/skill/endurance/fantasy_fortitude.png");
    public static final ResourceLocation COLONY_GUARDIAN_PERK = create("textures/skill/endurance/colony_guardian.png");
    public static final ResourceLocation BLOOD_WARD_PERK = create("textures/skill/endurance/blood_ward.png");
    public static final ResourceLocation FROST_ENDURANCE_PERK = create("textures/skill/endurance/frost_endurance.png");
    public static final ResourceLocation OBSIDIAN_SKIN_PERK = create("textures/skill/endurance/obsidian_skin.png");
    public static final ResourceLocation LIGHTNING_ROD_PERK = create("textures/skill/endurance/lightning_rod.png");
    public static final ResourceLocation SAMURAI_RESOLVE_PERK = create("textures/skill/endurance/samurai_resolve.png");
    public static final ResourceLocation DUNGEON_RESILIENCE_PERK = create("textures/skill/endurance/dungeon_resilience.png");
    public static final ResourceLocation PRISMARINE_SHIELD_PERK = create("textures/skill/endurance/prismarine_shield.png");
    public static final ResourceLocation AURA_SHIELD_PERK = create("textures/skill/endurance/aura_shield.png");
    public static final ResourceLocation PAIN_SUPPRESSION_PERK = create("textures/skill/endurance/pain_suppression.png");
    public static final ResourceLocation SPELL_SHIELD_PERK = create("textures/skill/endurance/spell_shield.png");
    public static final ResourceLocation UNBREAKABLE_PERK = create("textures/skill/endurance/unbreakable.png");
    public static final ResourceLocation DRAGON_BREATH_SHIELD_PERK = create("textures/skill/endurance/dragon_breath_shield.png");
    public static final ResourceLocation SIEGE_DEFENSE_PERK = create("textures/skill/endurance/siege_defense.png");
    public static final ResourceLocation ANCIENT_GUARDIAN_PERK = create("textures/skill/endurance/ancient_guardian.png");
    public static final ResourceLocation RUNIC_WARD_PERK = create("textures/skill/endurance/runic_ward.png");
    public static final ResourceLocation ADAPTATION_PERK = create("textures/skill/endurance/adaptation.png");
    public static final ResourceLocation IMMOVABLE_OBJECT_PERK = create("textures/skill/endurance/immovable_object.png");
    public static final ResourceLocation ARCANE_SHIELD_PERK = create("textures/skill/endurance/arcane_shield.png");
    public static final ResourceLocation MYTHIC_FORTITUDE_PERK = create("textures/skill/endurance/mythic_fortitude.png");
    public static final ResourceLocation CATACLYSM_RESISTANCE_PERK = create("textures/skill/endurance/cataclysm_resistance.png");

    // ========== INTELLIGENCE Perks ==========
    public static final ResourceLocation SCHOLAR_PERK = create("textures/skill/intelligence/scholar.png");
    public static final ResourceLocation HAGGLER_PERK = create("textures/skill/intelligence/haggler.png");
    public static final ResourceLocation ALCHEMY_MANIPULATION_PERK = create("textures/skill/intelligence/alchemy_manipulation.png");
    public static final ResourceLocation BOOKWORM_PERK = create("textures/skill/intelligence/bookworm.png");
    public static final ResourceLocation QUICK_LEARNER_PERK = create("textures/skill/intelligence/quick_learner.png");
    public static final ResourceLocation LINGUIST_PERK = create("textures/skill/intelligence/linguist.png");
    public static final ResourceLocation CARTOGRAPHER_PERK = create("textures/skill/intelligence/cartographer.png");
    public static final ResourceLocation POTION_BREWING_EXPERT_PERK = create("textures/skill/intelligence/potion_brewing_expert.png");
    public static final ResourceLocation LORE_KEEPER_PERK = create("textures/skill/intelligence/lore_keeper.png");
    public static final ResourceLocation DRAGON_LORE_PERK = create("textures/skill/intelligence/dragon_lore.png");
    public static final ResourceLocation SPELLCRAFT_KNOWLEDGE_PERK = create("textures/skill/intelligence/spellcraft_knowledge.png");
    public static final ResourceLocation ARCANE_SCHOLAR_PERK = create("textures/skill/intelligence/arcane_scholar.png");
    public static final ResourceLocation BLOOD_RITUALIST_PERK = create("textures/skill/intelligence/blood_ritualist.png");
    public static final ResourceLocation COLONY_ADVISOR_PERK = create("textures/skill/intelligence/colony_advisor.png");
    public static final ResourceLocation APOTHECARY_PERK = create("textures/skill/intelligence/apothecary.png");
    public static final ResourceLocation SIEGE_ENGINEER_PERK = create("textures/skill/intelligence/siege_engineer.png");
    public static final ResourceLocation MONSTER_COMPENDIUM_PERK = create("textures/skill/intelligence/monster_compendium.png");
    public static final ResourceLocation TACTICAL_GENIUS_PERK = create("textures/skill/intelligence/tactical_genius.png");
    public static final ResourceLocation NATURES_WISDOM_PERK = create("textures/skill/intelligence/natures_wisdom.png");
    public static final ResourceLocation ENCHANTMENT_INSIGHT_PERK = create("textures/skill/intelligence/enchantment_insight.png");
    public static final ResourceLocation EFFICIENT_CRAFTING_PERK = create("textures/skill/intelligence/efficient_crafting.png");
    public static final ResourceLocation RUNECRAFTER_PERK = create("textures/skill/intelligence/runecrafter.png");
    public static final ResourceLocation AQUATIC_KNOWLEDGE_PERK = create("textures/skill/intelligence/aquatic_knowledge.png");
    public static final ResourceLocation PROGRESSIVE_MASTERY_PERK = create("textures/skill/intelligence/progressive_mastery.png");
    public static final ResourceLocation SCROLL_MASTERY_PERK = create("textures/skill/intelligence/scroll_mastery.png");
    public static final ResourceLocation FAMILIAR_BOND_PERK = create("textures/skill/intelligence/familiar_bond.png");
    public static final ResourceLocation STRATEGIC_MIND_PERK = create("textures/skill/intelligence/strategic_mind.png");
    public static final ResourceLocation BREWING_INNOVATION_PERK = create("textures/skill/intelligence/brewing_innovation.png");
    public static final ResourceLocation ANCIENT_LANGUAGES_PERK = create("textures/skill/intelligence/ancient_languages.png");
    public static final ResourceLocation MASTER_RESEARCHER_PERK = create("textures/skill/intelligence/master_researcher.png");
    public static final ResourceLocation GOLEM_COMMANDER_PERK = create("textures/skill/intelligence/golem_commander.png");
    public static final ResourceLocation DIMENSIONAL_SCHOLAR_PERK = create("textures/skill/intelligence/dimensional_scholar.png");
    public static final ResourceLocation WAR_TACTICIAN_PERK = create("textures/skill/intelligence/war_tactician.png");
    public static final ResourceLocation ALCHEMIC_TRANSMUTATION_PERK = create("textures/skill/intelligence/alchemic_transmutation.png");
    public static final ResourceLocation MYSTIC_ANALYSIS_PERK = create("textures/skill/intelligence/mystic_analysis.png");
    public static final ResourceLocation SAGES_FOCUS_PERK = create("textures/skill/intelligence/sages_focus.png");
    public static final ResourceLocation ENIGMATIC_WISDOM_PERK = create("textures/skill/intelligence/enigmatic_wisdom.png");
    public static final ResourceLocation SPELL_ECHO_PERK = create("textures/skill/intelligence/spell_echo.png");
    public static final ResourceLocation GLYPH_MASTERY_PERK = create("textures/skill/intelligence/glyph_mastery.png");
    public static final ResourceLocation BEAST_TAMER_PERK = create("textures/skill/intelligence/beast_tamer.png");

    // ========== BUILDING Perks ==========
    public static final ResourceLocation OBSIDIAN_SMASHER_PERK = create("textures/skill/building/obsidian_smasher.png");
    public static final ResourceLocation TREASURE_HUNTER_PERK = create("textures/skill/building/treasure_hunter.png");
    public static final ResourceLocation CONVERGENCE_PERK = create("textures/skill/building/convergence.png");
    public static final ResourceLocation EFFICIENT_MINER_PERK = create("textures/skill/building/efficient_miner.png");
    public static final ResourceLocation VEIN_MINER_PERK = create("textures/skill/building/vein_miner.png");
    public static final ResourceLocation SILK_TOUCH_MASTERY_PERK = create("textures/skill/building/silk_touch_mastery.png");
    public static final ResourceLocation FORTUNE_MINER_PERK = create("textures/skill/building/fortune_miner.png");
    public static final ResourceLocation ARCHITECT_PERK = create("textures/skill/building/architect.png");
    public static final ResourceLocation MASTER_MASON_PERK = create("textures/skill/building/master_mason.png");
    public static final ResourceLocation LUMBERJACK_PERK = create("textures/skill/building/lumberjack.png");
    public static final ResourceLocation SMELTER_PERK = create("textures/skill/building/smelter.png");
    public static final ResourceLocation QUARRY_MASTER_PERK = create("textures/skill/building/quarry_master.png");
    public static final ResourceLocation COLONY_BUILDER_PERK = create("textures/skill/building/colony_builder.png");
    public static final ResourceLocation RESOURCE_EFFICIENCY_PERK = create("textures/skill/building/resource_efficiency.png");
    public static final ResourceLocation REINFORCED_CONSTRUCTION_PERK = create("textures/skill/building/reinforced_construction.png");
    public static final ResourceLocation TERRAFORMER_PERK = create("textures/skill/building/terraformer.png");
    public static final ResourceLocation ORE_DETECTOR_PERK = create("textures/skill/building/ore_detector.png");
    public static final ResourceLocation BLAST_MINING_PERK = create("textures/skill/building/blast_mining.png");
    public static final ResourceLocation STONE_CUTTER_EFFICIENCY_PERK = create("textures/skill/building/stone_cutter_efficiency.png");
    public static final ResourceLocation MASTER_WOODWORKER_PERK = create("textures/skill/building/master_woodworker.png");
    public static final ResourceLocation SCAFFOLD_MASTER_PERK = create("textures/skill/building/scaffold_master.png");
    public static final ResourceLocation DEEP_CORE_MINING_PERK = create("textures/skill/building/deep_core_mining.png");
    public static final ResourceLocation BRIDGE_BUILDER_PERK = create("textures/skill/building/bridge_builder.png");
    public static final ResourceLocation RUNIC_MINING_PERK = create("textures/skill/building/runic_mining.png");
    public static final ResourceLocation MEDIEVAL_ARCHITECTURE_PERK = create("textures/skill/building/medieval_architecture.png");
    public static final ResourceLocation EXPLOSIVE_EXPERT_PERK = create("textures/skill/building/explosive_expert.png");
    public static final ResourceLocation FOUNDATION_LAYER_PERK = create("textures/skill/building/foundation_layer.png");
    public static final ResourceLocation STRUCTURAL_ENGINEER_PERK = create("textures/skill/building/structural_engineer.png");
    public static final ResourceLocation FARMERS_HAND_PERK = create("textures/skill/building/farmers_hand.png");
    public static final ResourceLocation IRRIGATION_EXPERT_PERK = create("textures/skill/building/irrigation_expert.png");
    public static final ResourceLocation DIMENSIONAL_BUILDER_PERK = create("textures/skill/building/dimensional_builder.png");
    public static final ResourceLocation MASTER_BREAKER_PERK = create("textures/skill/building/master_breaker.png");
    public static final ResourceLocation GLOWSTONE_SIGHT_PERK = create("textures/skill/building/glowstone_sight.png");
    public static final ResourceLocation SALVAGE_EXPERT_PERK = create("textures/skill/building/salvage_expert.png");
    public static final ResourceLocation PROSPECTOR_PERK = create("textures/skill/building/prospector.png");
    public static final ResourceLocation CONSTRUCTION_HASTE_PERK = create("textures/skill/building/construction_haste.png");
    public static final ResourceLocation UNDERGROUND_EXPLORER_PERK = create("textures/skill/building/underground_explorer.png");
    public static final ResourceLocation MASS_PRODUCTION_PERK = create("textures/skill/building/mass_production.png");
    public static final ResourceLocation HERITAGE_BUILDER_PERK = create("textures/skill/building/heritage_builder.png");
    public static final ResourceLocation RUNIC_SALVAGER_PERK = create("textures/skill/building/runic_salvager.png");

    // ========== WISDOM Perks ==========
    public static final ResourceLocation ENCHANTERS_INSIGHT_PERK = create("textures/skill/wisdom/enchanters_insight.png");
    public static final ResourceLocation LORE_MASTERY_PERK = create("textures/skill/wisdom/lore_mastery.png");
    public static final ResourceLocation ENCHANTMENT_PRESERVATION_PERK = create("textures/skill/wisdom/enchantment_preservation.png");
    public static final ResourceLocation DISENCHANT_MASTERY_PERK = create("textures/skill/wisdom/disenchant_mastery.png");
    public static final ResourceLocation MENDING_BOOST_PERK = create("textures/skill/wisdom/mending_boost.png");
    public static final ResourceLocation UNBREAKING_MASTERY_PERK = create("textures/skill/wisdom/unbreaking_mastery.png");
    public static final ResourceLocation ENCHANTMENT_STACKING_PERK = create("textures/skill/wisdom/enchantment_stacking.png");
    public static final ResourceLocation WISDOM_OF_AGES_PERK = create("textures/skill/wisdom/wisdom_of_ages.png");
    public static final ResourceLocation TOME_OF_KNOWLEDGE_PERK = create("textures/skill/wisdom/tome_of_knowledge.png");
    public static final ResourceLocation RUNIC_ENCHANTMENT_PERK = create("textures/skill/wisdom/runic_enchantment.png");
    public static final ResourceLocation APOTHEOSIS_WISDOM_PERK = create("textures/skill/wisdom/apotheosis_wisdom.png");
    public static final ResourceLocation SCROLL_SCRIBE_PERK = create("textures/skill/wisdom/scroll_scribe.png");
    public static final ResourceLocation MYSTIC_ATTUNEMENT_PERK = create("textures/skill/wisdom/mystic_attunement.png");
    public static final ResourceLocation SOUL_BINDING_PERK = create("textures/skill/wisdom/soul_binding.png");
    public static final ResourceLocation EXPERIENCED_ENCHANTER_PERK = create("textures/skill/wisdom/experienced_enchanter.png");
    public static final ResourceLocation BLOOD_INSCRIPTION_PERK = create("textures/skill/wisdom/blood_inscription.png");
    public static final ResourceLocation ARCANE_LINGUIST_PERK = create("textures/skill/wisdom/arcane_linguist.png");
    public static final ResourceLocation WARD_MASTER_PERK = create("textures/skill/wisdom/ward_master.png");
    public static final ResourceLocation DIMENSIONAL_WISDOM_PERK = create("textures/skill/wisdom/dimensional_wisdom.png");
    public static final ResourceLocation ANCIENT_INSCRIPTIONS_PERK = create("textures/skill/wisdom/ancient_inscriptions.png");
    public static final ResourceLocation ARS_SAVANT_PERK = create("textures/skill/wisdom/ars_savant.png");
    public static final ResourceLocation NATURE_SAGE_PERK = create("textures/skill/wisdom/nature_sage.png");
    public static final ResourceLocation ENIGMATIC_UNDERSTANDING_PERK = create("textures/skill/wisdom/enigmatic_understanding.png");
    public static final ResourceLocation SPELL_INSCRIPTION_PERK = create("textures/skill/wisdom/spell_inscription.png");
    public static final ResourceLocation ELDER_KNOWLEDGE_PERK = create("textures/skill/wisdom/elder_knowledge.png");
    public static final ResourceLocation SACRED_GEOMETRY_PERK = create("textures/skill/wisdom/sacred_geometry.png");
    public static final ResourceLocation BOOKCRAFT_PERK = create("textures/skill/wisdom/bookcraft.png");
    public static final ResourceLocation MYSTIC_SIGHT_PERK = create("textures/skill/wisdom/mystic_sight.png");
    public static final ResourceLocation LAPIS_CONSERVATION_PERK = create("textures/skill/wisdom/lapis_conservation.png");
    public static final ResourceLocation ENLIGHTENMENT_PERK = create("textures/skill/wisdom/enlightenment.png");
    public static final ResourceLocation CURSE_BREAKER_PERK = create("textures/skill/wisdom/curse_breaker.png");
    public static final ResourceLocation ENCHANTMENT_AMPLIFIER_PERK = create("textures/skill/wisdom/enchantment_amplifier.png");
    public static final ResourceLocation RUNE_MASTERY_PERK = create("textures/skill/wisdom/rune_mastery.png");
    public static final ResourceLocation DRUIDIC_KNOWLEDGE_PERK = create("textures/skill/wisdom/druidic_knowledge.png");
    public static final ResourceLocation TEMPORAL_WISDOM_PERK = create("textures/skill/wisdom/temporal_wisdom.png");
    public static final ResourceLocation GRAND_SAGE_PERK = create("textures/skill/wisdom/grand_sage.png");
    public static final ResourceLocation ARCANE_WARD_PERK = create("textures/skill/wisdom/arcane_ward.png");
    public static final ResourceLocation RITUAL_SAGE_PERK = create("textures/skill/wisdom/ritual_sage.png");
    public static final ResourceLocation CURSE_WARD_PERK = create("textures/skill/wisdom/curse_ward.png");
    public static final ResourceLocation AURA_ATTUNEMENT_PERK = create("textures/skill/wisdom/aura_attunement.png");

    // ========== MAGIC Perks ==========
    public static final ResourceLocation SAFE_PORT_PERK = create("textures/skill/magic/safe_port.png");
    public static final ResourceLocation LIFE_EATER_PERK = create("textures/skill/magic/life_eater.png");
    public static final ResourceLocation WORMHOLE_STORAGE_PERK = create("textures/skill/magic/wormhole_storage.png");
    public static final ResourceLocation MANA_REGENERATION_PERK = create("textures/skill/magic/mana_regeneration.png");
    public static final ResourceLocation SPELL_AMPLIFIER_PERK = create("textures/skill/magic/spell_amplifier.png");
    public static final ResourceLocation SOURCE_WELL_PERK = create("textures/skill/magic/source_well.png");
    public static final ResourceLocation BLOOD_CHANNEL_PERK = create("textures/skill/magic/blood_channel.png");
    public static final ResourceLocation POTION_SPLASH_PERK = create("textures/skill/magic/potion_splash.png");
    public static final ResourceLocation TELEKINESIS_PERK = create("textures/skill/magic/telekinesis.png");
    public static final ResourceLocation ELEMENTAL_MASTER_PERK = create("textures/skill/magic/elemental_master.png");
    public static final ResourceLocation ARCANE_BARRIER_PERK = create("textures/skill/magic/arcane_barrier.png");
    public static final ResourceLocation SPELL_QUICKENING_PERK = create("textures/skill/magic/spell_quickening.png");
    public static final ResourceLocation SOURCE_ATTUNEMENT_PERK = create("textures/skill/magic/source_attunement.png");
    public static final ResourceLocation BLOOD_EMPOWER_PERK = create("textures/skill/magic/blood_empower.png");
    public static final ResourceLocation RITUAL_EFFICIENCY_PERK = create("textures/skill/magic/ritual_efficiency.png");
    public static final ResourceLocation SUMMONER_PERK = create("textures/skill/magic/summoner.png");
    public static final ResourceLocation MYSTIC_SHIELD_PERK = create("textures/skill/magic/mystic_shield.png");
    public static final ResourceLocation ASTRAL_PROJECTION_PERK = create("textures/skill/magic/astral_projection.png");
    public static final ResourceLocation PHILOSOPHERS_STONE_PERK = create("textures/skill/magic/philosophers_stone.png");
    public static final ResourceLocation MANA_SHIELD_PERK = create("textures/skill/magic/mana_shield.png");
    public static final ResourceLocation DRAGON_MAGIC_PERK = create("textures/skill/magic/dragon_magic.png");
    public static final ResourceLocation ELDRITCH_POWER_PERK = create("textures/skill/magic/eldritch_power.png");
    public static final ResourceLocation SOUL_MAGIC_PERK = create("textures/skill/magic/soul_magic.png");
    public static final ResourceLocation DUAL_CASTING_PERK = create("textures/skill/magic/dual_casting.png");
    public static final ResourceLocation ENCHANTED_MISSILES_PERK = create("textures/skill/magic/enchanted_missiles.png");
    public static final ResourceLocation AURA_MANIPULATION_PERK = create("textures/skill/magic/aura_manipulation.png");
    public static final ResourceLocation VOID_MAGIC_PERK = create("textures/skill/magic/void_magic.png");
    public static final ResourceLocation MANA_EFFICIENCY_PERK = create("textures/skill/magic/mana_efficiency.png");
    public static final ResourceLocation FIRE_ATTUNEMENT_PERK = create("textures/skill/magic/fire_attunement.png");
    public static final ResourceLocation ICE_ATTUNEMENT_PERK = create("textures/skill/magic/ice_attunement.png");
    public static final ResourceLocation LIGHTNING_ATTUNEMENT_PERK = create("textures/skill/magic/lightning_attunement.png");
    public static final ResourceLocation HOLY_ATTUNEMENT_PERK = create("textures/skill/magic/holy_attunement.png");
    public static final ResourceLocation NATURE_ATTUNEMENT_PERK = create("textures/skill/magic/nature_attunement.png");
    public static final ResourceLocation BLOOD_ATTUNEMENT_PERK = create("textures/skill/magic/blood_attunement.png");
    public static final ResourceLocation ENDER_ATTUNEMENT_PERK = create("textures/skill/magic/ender_attunement.png");
    public static final ResourceLocation EVOCATION_ATTUNEMENT_PERK = create("textures/skill/magic/evocation_attunement.png");
    public static final ResourceLocation ARCANE_EFFICIENCY_PERK = create("textures/skill/magic/arcane_efficiency.png");
    public static final ResourceLocation ARCANE_REFORGING_PERK = create("textures/skill/magic/arcane_reforging.png");
    public static final ResourceLocation CRIMSON_BOND_PERK = create("textures/skill/magic/crimson_bond.png");

    // ── Iron's Spells 'n Spellbooks — Phase 1a generic mana/casting perks ──
    // Textures pulled from ISS's own item sheet (verified present in 3.15.x). If
    // a path doesn't resolve at runtime the sprite falls back to missing-texture,
    // which is harmless — the perk still functions.
    public static final ResourceLocation ISS_WELLSPRING_PERK        = ironsItem("upgrade_orb_mana");
    public static final ResourceLocation ISS_QUICKENING_PERK        = ironsItem("cast_time_ring");
    public static final ResourceLocation ISS_RESERVOIR_PERK         = ironsItem("mana_ring");
    public static final ResourceLocation ISS_TEMPO_PERK             = ironsItem("upgrade_orb_cooldown");
    public static final ResourceLocation ISS_ARCANE_RECOVERY_PERK   = ironsItem("blood_vial");
    public static final ResourceLocation ISS_FOCUS_PERK             = ironsItem("concentration_amulet");
    public static final ResourceLocation ISS_MANA_BULWARK_PERK      = ironsItem("enchanted_ward_amulet");
    public static final ResourceLocation ISS_ARCANE_REPRIEVE_PERK   = ironsItem("greater_healing_potion");
    public static final ResourceLocation ISS_MANA_SURGE_PERK        = ironsItem("cinder_essence");
    public static final ResourceLocation ISS_SPELLWEAVER_PERK       = ironsItem("blank_rune");
    public static final ResourceLocation ISS_RESONANT_CASTING_PERK  = ironsItem("chronicle");
    public static final ResourceLocation ISS_IMBUED_FOCUS_PERK      = ironsItem("arcane_rune");
    public static final ResourceLocation ISS_QUICKCAST_PERK         = ironsItem("scroll");
    public static final ResourceLocation ISS_LONG_CHANNEL_PERK      = ironsItem("chronicle_old");
    public static final ResourceLocation ISS_CONTINUOUS_FLOW_PERK   = ironsItem("arcane_essence");
    public static final ResourceLocation ISS_CHARGE_MASTERY_PERK    = ironsItem("upgrade_orb_swirl");

    // ── Iron's Spells 'n Spellbooks — Phase 1b school specialist triplets ──
    // Per-school X-mancer (power) / X-Warded (resist) / X-Catalyst (signature effect).
    // Textures: upgrade_orb_<school> for mancer, <school>_rune for warded,
    // scroll_<school> for catalyst — all confirmed present in 3.15.x. Eldritch
    // school has no upgrade_orb_eldritch, so it uses eldritch_manuscript /
    // netherward_tincture / scroll_eldritch instead.
    public static final ResourceLocation ISS_FIRE_MANCER_PERK       = ironsItem("upgrade_orb_fire");
    public static final ResourceLocation ISS_FIRE_WARDED_PERK       = ironsItem("fire_rune");
    public static final ResourceLocation ISS_FIRE_CATALYST_PERK     = ironsItem("scroll_fire");
    public static final ResourceLocation ISS_ICE_MANCER_PERK        = ironsItem("upgrade_orb_ice");
    public static final ResourceLocation ISS_ICE_WARDED_PERK        = ironsItem("ice_rune");
    public static final ResourceLocation ISS_ICE_CATALYST_PERK      = ironsItem("scroll_ice");
    public static final ResourceLocation ISS_LIGHTNING_MANCER_PERK  = ironsItem("upgrade_orb_lightning");
    public static final ResourceLocation ISS_LIGHTNING_WARDED_PERK  = ironsItem("lightning_rune");
    public static final ResourceLocation ISS_LIGHTNING_CATALYST_PERK= ironsItem("scroll_lightning");
    public static final ResourceLocation ISS_HOLY_MANCER_PERK       = ironsItem("upgrade_orb_holy");
    public static final ResourceLocation ISS_HOLY_WARDED_PERK       = ironsItem("holy_rune");
    public static final ResourceLocation ISS_HOLY_CATALYST_PERK     = ironsItem("scroll_holy");
    public static final ResourceLocation ISS_ENDER_MANCER_PERK      = ironsItem("upgrade_orb_ender");
    public static final ResourceLocation ISS_ENDER_WARDED_PERK      = ironsItem("ender_rune");
    public static final ResourceLocation ISS_ENDER_CATALYST_PERK    = ironsItem("scroll_ender");
    public static final ResourceLocation ISS_BLOOD_MANCER_PERK      = ironsItem("upgrade_orb_blood");
    public static final ResourceLocation ISS_BLOOD_WARDED_PERK      = ironsItem("blood_rune");
    public static final ResourceLocation ISS_BLOOD_CATALYST_PERK    = ironsItem("scroll_blood");
    public static final ResourceLocation ISS_EVOCATION_MANCER_PERK  = ironsItem("upgrade_orb_evocation");
    public static final ResourceLocation ISS_EVOCATION_WARDED_PERK  = ironsItem("evocation_rune");
    public static final ResourceLocation ISS_EVOCATION_CATALYST_PERK= ironsItem("scroll_evocation");
    public static final ResourceLocation ISS_NATURE_MANCER_PERK     = ironsItem("upgrade_orb_nature");
    public static final ResourceLocation ISS_NATURE_WARDED_PERK     = ironsItem("nature_rune");
    public static final ResourceLocation ISS_NATURE_CATALYST_PERK   = ironsItem("scroll_nature");
    public static final ResourceLocation ISS_ELDRITCH_MANCER_PERK   = ironsItem("eldritch_manuscript");
    public static final ResourceLocation ISS_ELDRITCH_WARDED_PERK   = ironsItem("netherward_tincture");
    public static final ResourceLocation ISS_ELDRITCH_CATALYST_PERK = ironsItem("scroll_eldritch");
    public static final ResourceLocation ISS_ELDRITCH_ATTUNEMENT_PERK = ironsItem("affinity_ring_eldritch");

    // ── Iron's Spells 'n Spellbooks — Phase 1c summon/utility perks ──
    public static final ResourceLocation ISS_LORD_OF_THE_DEAD_PERK  = ironsItem("necronomicon_spell_book");
    public static final ResourceLocation ISS_LIFE_LEECH_BOUND_PERK  = ironsItem("blood_rune");

    // ========== FORTUNE Perks ==========
    public static final ResourceLocation CRITICAL_ROLL_PERK = create("textures/skill/fortune/critical_roll.png");
    public static final ResourceLocation LUCKY_DROP_PERK = create("textures/skill/fortune/lucky_drop.png");
    public static final ResourceLocation LIMIT_BREAKER_PERK = create("textures/skill/fortune/limit_breaker.png");
    public static final ResourceLocation TREASURE_SENSE_PERK = create("textures/skill/fortune/treasure_sense.png");
    public static final ResourceLocation DOUBLE_DOWN_PERK = create("textures/skill/fortune/double_down.png");
    public static final ResourceLocation GOLDEN_TOUCH_PERK = create("textures/skill/fortune/golden_touch.png");
    public static final ResourceLocation FORTUNES_FAVOR_PERK = create("textures/skill/fortune/fortunes_favor.png");
    public static final ResourceLocation LUCKY_FISHING_PERK = create("textures/skill/fortune/lucky_fishing.png");
    public static final ResourceLocation PROSPECTORS_LUCK_PERK = create("textures/skill/fortune/prospectors_luck.png");
    public static final ResourceLocation SCAVENGER_PERK = create("textures/skill/fortune/scavenger.png");
    public static final ResourceLocation CRITICAL_MASTERY_PERK = create("textures/skill/fortune/critical_mastery.png");
    public static final ResourceLocation LOOTER_PERK = create("textures/skill/fortune/looter.png");
    public static final ResourceLocation JACKPOT_PERK = create("textures/skill/fortune/jackpot.png");
    public static final ResourceLocation ENCHANTED_FORTUNE_PERK = create("textures/skill/fortune/enchanted_fortune.png");
    public static final ResourceLocation DRAGON_HOARD_PERK = create("textures/skill/fortune/dragon_hoard.png");
    public static final ResourceLocation CATACLYSM_SPOILS_PERK = create("textures/skill/fortune/cataclysm_spoils.png");
    public static final ResourceLocation RUNIC_FORTUNE_PERK = create("textures/skill/fortune/runic_fortune.png");
    public static final ResourceLocation APOTHEOSIS_GEMS_PERK = create("textures/skill/fortune/apotheosis_gems.png");
    public static final ResourceLocation LUCKY_CHARM_PERK = create("textures/skill/fortune/lucky_charm.png");
    public static final ResourceLocation COIN_FLIP_PERK = create("textures/skill/fortune/coin_flip.png");
    public static final ResourceLocation SALVAGE_LUCK_PERK = create("textures/skill/fortune/salvage_luck.png");
    public static final ResourceLocation ADVENTURERS_LUCK_PERK = create("textures/skill/fortune/adventurers_luck.png");
    public static final ResourceLocation MIDAS_TOUCH_PERK = create("textures/skill/fortune/midas_touch.png");
    public static final ResourceLocation LUCKY_BREAK_PERK = create("textures/skill/fortune/lucky_break.png");
    public static final ResourceLocation JEWELERS_EYE_PERK = create("textures/skill/fortune/jewelers_eye.png");
    public static final ResourceLocation FORTUNE_COOKIE_PERK = create("textures/skill/fortune/fortune_cookie.png");
    public static final ResourceLocation ETHEREAL_LUCK_PERK = create("textures/skill/fortune/ethereal_luck.png");
    public static final ResourceLocation RARE_FIND_PERK = create("textures/skill/fortune/rare_find.png");
    public static final ResourceLocation LUCKY_STAR_PERK = create("textures/skill/fortune/lucky_star.png");
    public static final ResourceLocation SERENDIPITY_PERK = create("textures/skill/fortune/serendipity.png");
    public static final ResourceLocation GREED_PERK = create("textures/skill/fortune/greed.png");
    public static final ResourceLocation RAINBOW_LOOT_PERK = create("textures/skill/fortune/rainbow_loot.png");
    public static final ResourceLocation FISHERMANS_LUCK_PERK = create("textures/skill/fortune/fishermans_luck.png");
    public static final ResourceLocation LUCKY_EXPLORER_PERK = create("textures/skill/fortune/lucky_explorer.png");
    public static final ResourceLocation CHAOS_ROLL_PERK = create("textures/skill/fortune/chaos_roll.png");
    public static final ResourceLocation CRITICAL_FORTUNE_PERK = create("textures/skill/fortune/critical_fortune.png");
    public static final ResourceLocation MASTER_LOOTER_PERK = create("textures/skill/fortune/master_looter.png");
    public static final ResourceLocation ARTIFACT_HUNTER_PERK = create("textures/skill/fortune/artifact_hunter.png");
    public static final ResourceLocation BLESSING_OF_LUCK_PERK = create("textures/skill/fortune/blessing_of_luck.png");
    public static final ResourceLocation GEM_ATTUNEMENT_PERK = create("textures/skill/fortune/gem_attunement.png");

    // ========== TINKERING Perks ==========
    public static final ResourceLocation LOCKSMITH_PERK = create("textures/skill/tinkering/locksmith.png");
    public static final ResourceLocation SAFE_CRACKER_PERK = create("textures/skill/tinkering/safe_cracker.png");
    public static final ResourceLocation MASTER_TINKERER_PERK = create("textures/skill/tinkering/master_tinkerer.png");
    public static final ResourceLocation REPAIR_EXPERT_PERK = create("textures/skill/tinkering/repair_expert.png");
    public static final ResourceLocation DISASSEMBLER_PERK = create("textures/skill/tinkering/disassembler.png");
    public static final ResourceLocation AUTO_REPAIR_PERK = create("textures/skill/tinkering/auto_repair.png");
    public static final ResourceLocation GADGETEER_PERK = create("textures/skill/tinkering/gadgeteer.png");
    public static final ResourceLocation TRAP_MAKER_PERK = create("textures/skill/tinkering/trap_maker.png");
    public static final ResourceLocation LOCK_EXPERT_PERK = create("textures/skill/tinkering/lock_expert.png");
    public static final ResourceLocation KEY_FORGE_PERK = create("textures/skill/tinkering/key_forge.png");
    public static final ResourceLocation MECHANICAL_KNOWLEDGE_PERK = create("textures/skill/tinkering/mechanical_knowledge.png");
    public static final ResourceLocation SIEGE_MECHANIC_PERK = create("textures/skill/tinkering/siege_mechanic.png");
    public static final ResourceLocation WEAPON_SMITH_PERK = create("textures/skill/tinkering/weapon_smith.png");
    public static final ResourceLocation ARMOR_SMITH_PERK = create("textures/skill/tinkering/armor_smith.png");
    public static final ResourceLocation TOOL_SMITH_PERK = create("textures/skill/tinkering/tool_smith.png");
    public static final ResourceLocation SALVAGE_MASTER_PERK = create("textures/skill/tinkering/salvage_master.png");
    public static final ResourceLocation ENCHANTMENT_TRANSFER_PERK = create("textures/skill/tinkering/enchantment_transfer.png");
    public static final ResourceLocation GADGET_UPGRADE_PERK = create("textures/skill/tinkering/gadget_upgrade.png");
    public static final ResourceLocation OVERCLOCK_PERK = create("textures/skill/tinkering/overclock.png");
    public static final ResourceLocation RUNIC_ENGINEERING_PERK = create("textures/skill/tinkering/runic_engineering.png");
    public static final ResourceLocation BREWING_APPARATUS_PERK = create("textures/skill/tinkering/brewing_apparatus.png");
    public static final ResourceLocation MECHANICAL_ARM_PERK = create("textures/skill/tinkering/mechanical_arm.png");
    public static final ResourceLocation PRECISION_TOOLS_PERK = create("textures/skill/tinkering/precision_tools.png");
    public static final ResourceLocation ASSEMBLY_LINE_PERK = create("textures/skill/tinkering/assembly_line.png");
    public static final ResourceLocation EXPLOSIVE_ORDINANCE_PERK = create("textures/skill/tinkering/explosive_ordinance.png");
    public static final ResourceLocation CIRCUIT_BREAKER_PERK = create("textures/skill/tinkering/circuit_breaker.png");
    public static final ResourceLocation MODULAR_EQUIPMENT_PERK = create("textures/skill/tinkering/modular_equipment.png");
    public static final ResourceLocation CLOCKWORK_MASTERY_PERK = create("textures/skill/tinkering/clockwork_mastery.png");
    public static final ResourceLocation FORGE_MASTER_PERK = create("textures/skill/tinkering/forge_master.png");
    public static final ResourceLocation INVENTOR_PERK = create("textures/skill/tinkering/inventor.png");
    public static final ResourceLocation SPRING_LOADED_PERK = create("textures/skill/tinkering/spring_loaded.png");
    public static final ResourceLocation BALLISTIC_EXPERT_PERK = create("textures/skill/tinkering/ballistic_expert.png");
    public static final ResourceLocation SAFE_BUILDER_PERK = create("textures/skill/tinkering/safe_builder.png");
    public static final ResourceLocation TINKERS_TOUCH_PERK = create("textures/skill/tinkering/tinkers_touch.png");
    public static final ResourceLocation ALLOY_MASTER_PERK = create("textures/skill/tinkering/alloy_master.png");
    public static final ResourceLocation MECHANISM_MASTERY_PERK = create("textures/skill/tinkering/mechanism_mastery.png");
    public static final ResourceLocation POWER_TOOLS_PERK = create("textures/skill/tinkering/power_tools.png");
    public static final ResourceLocation BACKPACK_ENGINEER_PERK = create("textures/skill/tinkering/backpack_engineer.png");
    public static final ResourceLocation WAYSTONE_TINKER_PERK = create("textures/skill/tinkering/waystone_tinker.png");
    public static final ResourceLocation MASTER_ARTIFICER_PERK = create("textures/skill/tinkering/master_artificer.png");

    // ══════════════════════════════════════════════════════════════════════════
    //  Botania Integration — perk icons reuse Botania's own 16×16 item textures
    //  via the botania: namespace. These paths only resolve at runtime when
    //  Botania is loaded; every BOTANIA_* perk is null-guarded in RegistryPerks,
    //  so a missing texture cannot be requested without Botania present.
    // ══════════════════════════════════════════════════════════════════════════

    // ── WISDOM perks ──
    public static final ResourceLocation BOTANIA_PETAL_READER_PERK             = botaniaItem("lexicon");
    public static final ResourceLocation BOTANIA_RESONANCE_PERK                = botaniaItem("rune_mana");
    public static final ResourceLocation BOTANIA_SPARKLE_SENSE_PERK            = botaniaItem("third_eye_0");
    public static final ResourceLocation BOTANIA_DOWSERS_TWIG_PERK             = botaniaItem("divining_rod");
    public static final ResourceLocation BOTANIA_GREEN_THUMB_PERK              = botaniaItem("overgrowth_seed");
    public static final ResourceLocation BOTANIA_LIVINGBARK_STUDENT_PERK       = botaniaItem("livingwood_twig");
    public static final ResourceLocation BOTANIA_AGRICULTORS_EYE_PERK          = botaniaItem("infused_seeds");
    public static final ResourceLocation BOTANIA_FORAGERS_PALATE_PERK          = botaniaItem("mana_cookie");
    public static final ResourceLocation BOTANIA_LOOT_HUNTERS_INTUITION_PERK   = botaniaItem("itemfinder");
    public static final ResourceLocation BOTANIA_STILL_LISTENER_PERK           = botaniaItem("third_eye_2");
    public static final ResourceLocation BOTANIA_MANASEERS_LENS_PERK           = botaniaItem("monocle");
    public static final ResourceLocation BOTANIA_CORPOREA_QUERY_PERK           = botaniaItem("corporea_spark");
    public static final ResourceLocation BOTANIA_CARTOGRAPHER_PERK             = botaniaItem("sextant");
    public static final ResourceLocation BOTANIA_FAR_REACH_PERK                = botaniaItem("reach_ring");
    public static final ResourceLocation BOTANIA_LAZY_SWAP_PERK                = botaniaItem("swap_ring");
    public static final ResourceLocation BOTANIA_MIRRORS_READ_PERK             = botaniaItem("mana_mirror");
    public static final ResourceLocation BOTANIA_ELVEN_KNOWLEDGE_PERK          = botaniaItem("lexicon_elven");
    public static final ResourceLocation BOTANIA_GAIAS_WITNESS_PERK            = botaniaItem("gaia_head");
    public static final ResourceLocation BOTANIA_ORACLE_NINE_RUNES_PERK        = botaniaItem("twig_wand");

    // ── MAGIC perks ──
    public static final ResourceLocation BOTANIA_INNER_WELLSPRING_PERK         = botaniaItem("mana_tablet");
    public static final ResourceLocation BOTANIA_TIDEWOVEN_PERK                = botaniaItem("rune_water");
    public static final ResourceLocation BOTANIA_EMBERHEART_PERK               = botaniaItem("rune_fire");
    public static final ResourceLocation BOTANIA_STONE_ROOTED_PERK             = botaniaItem("rune_earth");
    public static final ResourceLocation BOTANIA_FEATHERSTEP_PERK              = botaniaItem("rune_air");
    public static final ResourceLocation BOTANIA_BAND_OF_AURA_PERK             = botaniaItem("aura_ring");
    public static final ResourceLocation BOTANIA_VERDANT_PULSE_PERK            = botaniaItem("rune_spring");
    public static final ResourceLocation BOTANIA_SOLAR_CONDUIT_PERK            = botaniaItem("rune_summer");
    public static final ResourceLocation BOTANIA_HARVEST_TITHE_PERK            = botaniaItem("rune_autumn");
    public static final ResourceLocation BOTANIA_FROSTBOUND_PERK               = botaniaItem("rune_winter");
    public static final ResourceLocation BOTANIA_LENS_VELOCITY_PERK            = botaniaItem("lens_speed");
    public static final ResourceLocation BOTANIA_LENS_POTENCY_PERK             = botaniaItem("lens_power");
    public static final ResourceLocation BOTANIA_PIXIE_AFFINITY_PERK           = botaniaItem("rune_lust");
    public static final ResourceLocation BOTANIA_CAKE_COMBUSTION_PERK          = botaniaItem("rune_gluttony");
    public static final ResourceLocation BOTANIA_MAGNETITE_PERK                = botaniaItem("magnet_ring");
    public static final ResourceLocation BOTANIA_UNBOUND_STEP_PERK             = botaniaItem("rune_sloth");
    public static final ResourceLocation BOTANIA_MIRRORED_WRATH_PERK           = botaniaItem("rune_envy");
    public static final ResourceLocation BOTANIA_CROWN_OF_REACH_PERK           = botaniaItem("rune_pride");
    public static final ResourceLocation BOTANIA_THUNDERCALL_PERK              = botaniaItem("rune_wrath");
    public static final ResourceLocation BOTANIA_RELIC_ATTUNEMENT_PERK         = botaniaItem("king_key");
    public static final ResourceLocation BOTANIA_TERRASTEEL_ASCENSION_PERK     = botaniaItem("terrasteel_ingot");
    public static final ResourceLocation BOTANIA_FLUGELS_GRACE_PERK            = botaniaItem("flight_tiara");
    public static final ResourceLocation BOTANIA_MANASTORM_PERK                = botaniaItem("lens_storm");

    private static ResourceLocation botaniaItem(String name) {
        return new ResourceLocation("botania", "textures/item/" + name + ".png");
    }

    // Foreign-mod item-texture helpers. These point the perk-icon renderer at the
    // source mod's own item sprites, so perk art matches the theme without us
    // needing to ship our own PNGs. If the source mod isn't installed the perk is
    // null-registered (see RegistryPerks), so the unresolved path never renders.
    private static ResourceLocation ironsItem(String name) {
        return new ResourceLocation("irons_spellbooks", "textures/item/" + name + ".png");
    }

    private static ResourceLocation arsItem(String name) {
        return new ResourceLocation("ars_nouveau", "textures/item/" + name + ".png");
    }

    private static ResourceLocation apothItem(String name) {
        return new ResourceLocation("apotheosis", "textures/item/" + name + ".png");
    }

    private static ResourceLocation attribItem(String name) {
        return new ResourceLocation("attributeslib", "textures/item/" + name + ".png");
    }

    public static ResourceLocation create(String path) {
        return new ResourceLocation(RunicSkills.MOD_ID, path);
    }
}
