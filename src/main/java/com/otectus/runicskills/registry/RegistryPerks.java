package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.core.Value;
import com.otectus.runicskills.client.core.ValueType;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.integration.*;
import com.otectus.runicskills.registry.skill.Skill;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class RegistryPerks {
    public static final ResourceKey<Registry<Perk>> PERKS_KEY = ResourceKey.createRegistryKey(new ResourceLocation(RunicSkills.MOD_ID, "perks"));
    public static final DeferredRegister<Perk> PERKS = DeferredRegister.create(PERKS_KEY, RunicSkills.MOD_ID);
    public static final Supplier<IForgeRegistry<Perk>> PERKS_REGISTRY = PERKS.makeRegistry(() -> new RegistryBuilder<Perk>().disableSaving());

    public static final RegistryObject<Perk> ONE_HANDED =
            HandlerCommonConfig.HANDLER.instance().oneHandedRequiredLevel < 0
            ? null : PERKS.register("one_handed", () -> register(
                    "one_handed",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().oneHandedRequiredLevel,
                    HandlerResources.ONE_HANDED_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().oneHandedAmplifier)
            ));

    public static final RegistryObject<Perk> FIGHTING_SPIRIT =
            HandlerCommonConfig.HANDLER.instance().fightingSpiritRequiredLevel < 0
            ? null : PERKS.register("fighting_spirit", () -> register(
                    "fighting_spirit",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().fightingSpiritRequiredLevel,
                    HandlerResources.FIGHTING_SPIRIT_PERK,
                    new Value(ValueType.BOOST, HandlerCommonConfig.HANDLER.instance().fightingSpiritBoost),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().fightingSpiritDuration)
            ));

    public static final RegistryObject<Perk> BERSERKER =
            HandlerCommonConfig.HANDLER.instance().berserkerRequiredLevel < 0
            ? null : PERKS.register("berserker", () -> register(
                    "berserker",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().berserkerRequiredLevel,
                    HandlerResources.BERSERKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().berserkerPercent)
            ));

    public static final RegistryObject<Perk> ATHLETICS =
            HandlerCommonConfig.HANDLER.instance().athleticsRequiredLevel < 0
            ? null : PERKS.register("athletics", () -> register(
                    "athletics",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().athleticsRequiredLevel,
                    HandlerResources.ATHLETICS_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().athleticsModifier)
            ));

    public static final RegistryObject<Perk> TURTLE_SHIELD =
            HandlerCommonConfig.HANDLER.instance().turtleShieldRequiredLevel < 0
            ? null : PERKS.register("turtle_shield", () -> register(
                    "turtle_shield",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().turtleShieldRequiredLevel,
                    HandlerResources.TURTLE_SHIELD_PERK
            ));

    public static final RegistryObject<Perk> LION_HEART =
            HandlerCommonConfig.HANDLER.instance().lionHeartRequiredLevel < 0
            ? null : PERKS.register("lion_heart", () -> register(
                    "lion_heart",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().lionHeartRequiredLevel,
                    HandlerResources.LION_HEART_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lionHeartPercent)
            ));

    public static final RegistryObject<Perk> QUICK_REPOSITION =
            HandlerCommonConfig.HANDLER.instance().quickRepositionRequiredLevel < 0
            ? null : PERKS.register("quick_reposition", () -> register(
                    "quick_reposition",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().quickRepositionRequiredLevel,
                    HandlerResources.QUICK_REPOSITION_PERK,
                    new Value(ValueType.BOOST, HandlerCommonConfig.HANDLER.instance().quickRepositionBoost),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().quickRepositionDuration)
            ));

    public static final RegistryObject<Perk> STEALTH_MASTERY =
            HandlerCommonConfig.HANDLER.instance().stealthMasteryRequiredLevel < 0
            ? null : PERKS.register("stealth_mastery", () -> register(
                    "stealth_mastery",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().stealthMasteryRequiredLevel,
                    HandlerResources.STEALTH_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().stealthMasteryUnSneakPercent),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().stealthMasterySneakPercent),
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().stealthMasteryModifier)
            ));

    public static final RegistryObject<Perk> CAT_EYES =
            HandlerCommonConfig.HANDLER.instance().catEyesRequiredLevel < 0
            ? null : PERKS.register("cat_eyes", () -> register(
                    "cat_eyes",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().catEyesRequiredLevel,
                    HandlerResources.CAT_EYES_PERK
            ));

    public static final RegistryObject<Perk> SNOW_WALKER =
            HandlerCommonConfig.HANDLER.instance().snowWalkerRequiredLevel < 0
            ? null : PERKS.register("snow_walker", () -> register(
                    "snow_walker",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().snowWalkerRequiredLevel,
                    HandlerResources.SNOW_WALKER_PERK
            ));

    public static final RegistryObject<Perk> COUNTER_ATTACK =
            HandlerCommonConfig.HANDLER.instance().counterattackRequiredLevel < 0
            ? null : PERKS.register("counter_attack", () -> register(
                    "counter_attack",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().counterattackRequiredLevel,
                    HandlerResources.COUNTER_ATTACK_PERK,
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().counterAttackDuration),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().counterAttackPercent)
            ));

    public static final RegistryObject<Perk> DIAMOND_SKIN =
            HandlerCommonConfig.HANDLER.instance().diamondSkinRequiredLevel < 0
            ? null : PERKS.register("diamond_skin", () -> register(
                    "diamond_skin",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().diamondSkinRequiredLevel,
                    HandlerResources.DIAMOND_SKIN_PERK,
                    new Value(ValueType.BOOST, HandlerCommonConfig.HANDLER.instance().diamondSkinBoost),
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().diamondSkinSneakAmplifier)
            ));

    public static final RegistryObject<Perk> SCHOLAR =
            HandlerCommonConfig.HANDLER.instance().scholarRequiredLevel < 0
            ? null : PERKS.register("scholar", () -> register(
                    "scholar",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().scholarRequiredLevel,
                    HandlerResources.SCHOLAR_PERK
            ));

    public static final RegistryObject<Perk> HAGGLER =
            HandlerCommonConfig.HANDLER.instance().hagglerRequiredLevel < 0
            ? null : PERKS.register("haggler", () -> register(
                    "haggler",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().hagglerRequiredLevel,
                    HandlerResources.HAGGLER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().hagglerPercent)
            ));

    public static final RegistryObject<Perk> ALCHEMY_MANIPULATION =
            HandlerCommonConfig.HANDLER.instance().alchemyManipulationRequiredLevel < 0
            ? null : PERKS.register("alchemy_manipulation", () -> register(
                    "alchemy_manipulation",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().alchemyManipulationRequiredLevel,
                    HandlerResources.ALCHEMY_MANIPULATION_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().alchemyManipulationAmplifier)
            ));

    // Building perks
    public static final RegistryObject<Perk> OBSIDIAN_SMASHER =
            HandlerCommonConfig.HANDLER.instance().obsidianSmasherRequiredLevel < 0
            ? null : PERKS.register("obsidian_smasher", () -> register(
                    "obsidian_smasher",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().obsidianSmasherRequiredLevel,
                    HandlerResources.OBSIDIAN_SMASHER_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().obsidianSmasherModifier)
            ));

    public static final RegistryObject<Perk> TREASURE_HUNTER =
            HandlerCommonConfig.HANDLER.instance().treasureHunterRequiredLevel < 0
            ? null : PERKS.register("treasure_hunter", () -> register(
                    "treasure_hunter",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().treasureHunterRequiredLevel,
                    HandlerResources.TREASURE_HUNTER_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().treasureHunterProbability)
            ));

    public static final RegistryObject<Perk> CONVERGENCE =
            HandlerCommonConfig.HANDLER.instance().convergenceRequiredLevel < 0
            ? null : PERKS.register("convergence", () -> register(
                    "convergence",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().convergenceRequiredLevel,
                    HandlerResources.CONVERGENCE_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().convergenceProbability)
            ));

    // Tinkering base perks
    public static final RegistryObject<Perk> LOCKSMITH =
            HandlerCommonConfig.HANDLER.instance().locksmithRequiredLevel < 0
            ? null : PERKS.register("locksmith", () -> register(
                    "locksmith",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().locksmithRequiredLevel,
                    HandlerResources.LOCKSMITH_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().locksmithProbability)
            ));

    public static final RegistryObject<Perk> SAFE_CRACKER =
            HandlerCommonConfig.HANDLER.instance().safeCrackerRequiredLevel < 0
            ? null : PERKS.register("safe_cracker", () -> register(
                    "safe_cracker",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().safeCrackerRequiredLevel,
                    HandlerResources.SAFE_CRACKER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().safeCrackerAmplifier)
            ));

    public static final RegistryObject<Perk> MASTER_TINKERER =
            HandlerCommonConfig.HANDLER.instance().masterTinkererRequiredLevel < 0
            ? null : PERKS.register("master_tinkerer", () -> register(
                    "master_tinkerer",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().masterTinkererRequiredLevel,
                    HandlerResources.MASTER_TINKERER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterTinkererPercent)
            ));

    // Wisdom base perks
    public static final RegistryObject<Perk> ENCHANTERS_INSIGHT =
            HandlerCommonConfig.HANDLER.instance().enchantersInsightRequiredLevel < 0
            ? null : PERKS.register("enchanters_insight", () -> register(
                    "enchanters_insight",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().enchantersInsightRequiredLevel,
                    HandlerResources.ENCHANTERS_INSIGHT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantersInsightPercent)
            ));

    public static final RegistryObject<Perk> LORE_MASTERY =
            HandlerCommonConfig.HANDLER.instance().loreMasteryRequiredLevel < 0
            ? null : PERKS.register("lore_mastery", () -> register(
                    "lore_mastery",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().loreMasteryRequiredLevel,
                    HandlerResources.LORE_MASTERY_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().loreMasteryModifier)
            ));

    public static final RegistryObject<Perk> SAFE_PORT =
            HandlerCommonConfig.HANDLER.instance().safePortRequiredLevel < 0
            ? null : PERKS.register("safe_port", () -> register(
                    "safe_port",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().safePortRequiredLevel,
                    HandlerResources.SAFE_PORT_PERK
            ));

    public static final RegistryObject<Perk> LIFE_EATER =
            HandlerCommonConfig.HANDLER.instance().lifeEaterRequiredLevel < 0
            ? null : PERKS.register("life_eater", () -> register(
                    "life_eater",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().lifeEaterRequiredLevel,
                    HandlerResources.LIFE_EATER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().lifeEaterModifier)
            ));

    public static final RegistryObject<Perk> WORMHOLE_STORAGE =
            HandlerCommonConfig.HANDLER.instance().wormholeStorageRequiredLevel < 0
            ? null : PERKS.register("wormhole_storage", () -> register(
                    "wormhole_storage",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().wormholeStorageRequiredLevel,
                    HandlerResources.WORMHOLE_STORAGE_PERK
            ));

    public static final RegistryObject<Perk> CRITICAL_ROLL =
            HandlerCommonConfig.HANDLER.instance().criticalRollRequiredLevel < 0
            ? null : PERKS.register("critical_roll", () -> register(
                    "critical_roll",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().criticalRollRequiredLevel,
                    HandlerResources.CRITICAL_ROLL_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().criticalRoll6Modifier),
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().criticalRoll1Probability)
            ));

    public static final RegistryObject<Perk> LUCKY_DROP =
             HandlerCommonConfig.HANDLER.instance().luckyDropRequiredLevel < 0
            ? null : PERKS.register("lucky_drop", () -> register(
                    "lucky_drop",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyDropRequiredLevel,
                    HandlerResources.LUCKY_DROP_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().luckyDropProbability),
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().luckyDropModifier)
            ));

    public static final RegistryObject<Perk> LIMIT_BREAKER =
            HandlerCommonConfig.HANDLER.instance().limitBreakerRequiredLevel < 0
            ? null : PERKS.register("limit_breaker", () -> register(
                    "limit_breaker",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().limitBreakerRequiredLevel,
                    HandlerResources.LIMIT_BREAKER_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().limitBreakerProbability),
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().limitBreakerAmplifier)
            ));

    // Iron's Spells 'n Spellbooks Integration - Conditional perks
    public static final RegistryObject<Perk> MANA_EFFICIENCY =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().manaEfficiencyRequiredLevel < 0
            ? null : PERKS.register("mana_efficiency", () -> register(
                    "mana_efficiency",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().manaEfficiencyRequiredLevel,
                    HandlerResources.MANA_EFFICIENCY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().manaEfficiencyPercent)
            ));
    public static final RegistryObject<Perk> SPELL_ECHO =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().spellEchoRequiredLevel < 0
            ? null : PERKS.register("spell_echo", () -> register(
                    "spell_echo",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().spellEchoRequiredLevel,
                    HandlerResources.SPELL_ECHO_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().spellEchoProbability)
            ));
    public static final RegistryObject<Perk> ARCANE_SHIELD =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().arcaneShieldRequiredLevel < 0
            ? null : PERKS.register("arcane_shield", () -> register(
                    "arcane_shield",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().arcaneShieldRequiredLevel,
                    HandlerResources.ARCANE_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arcaneShieldPercent)
            ));

    // Iron's Spells 'n Spellbooks - School Attunement perks
    public static final RegistryObject<Perk> FIRE_ATTUNEMENT =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().fireAttunementRequiredLevel < 0
            ? null : PERKS.register("fire_attunement", () -> register(
                    "fire_attunement",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().fireAttunementRequiredLevel,
                    HandlerResources.FIRE_ATTUNEMENT_PERK
            ));
    public static final RegistryObject<Perk> ICE_ATTUNEMENT =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().iceAttunementRequiredLevel < 0
            ? null : PERKS.register("ice_attunement", () -> register(
                    "ice_attunement",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().iceAttunementRequiredLevel,
                    HandlerResources.ICE_ATTUNEMENT_PERK
            ));
    public static final RegistryObject<Perk> LIGHTNING_ATTUNEMENT =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().lightningAttunementRequiredLevel < 0
            ? null : PERKS.register("lightning_attunement", () -> register(
                    "lightning_attunement",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().lightningAttunementRequiredLevel,
                    HandlerResources.LIGHTNING_ATTUNEMENT_PERK
            ));
    public static final RegistryObject<Perk> HOLY_ATTUNEMENT =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().holyAttunementRequiredLevel < 0
            ? null : PERKS.register("holy_attunement", () -> register(
                    "holy_attunement",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().holyAttunementRequiredLevel,
                    HandlerResources.HOLY_ATTUNEMENT_PERK
            ));
    public static final RegistryObject<Perk> NATURE_ATTUNEMENT =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().natureAttunementRequiredLevel < 0
            ? null : PERKS.register("nature_attunement", () -> register(
                    "nature_attunement",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().natureAttunementRequiredLevel,
                    HandlerResources.NATURE_ATTUNEMENT_PERK
            ));
    public static final RegistryObject<Perk> BLOOD_ATTUNEMENT =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().bloodAttunementRequiredLevel < 0
            ? null : PERKS.register("blood_attunement", () -> register(
                    "blood_attunement",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().bloodAttunementRequiredLevel,
                    HandlerResources.BLOOD_ATTUNEMENT_PERK
            ));
    public static final RegistryObject<Perk> ENDER_ATTUNEMENT =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().enderAttunementRequiredLevel < 0
            ? null : PERKS.register("ender_attunement", () -> register(
                    "ender_attunement",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().enderAttunementRequiredLevel,
                    HandlerResources.ENDER_ATTUNEMENT_PERK
            ));
    public static final RegistryObject<Perk> EVOCATION_ATTUNEMENT =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().evocationAttunementRequiredLevel < 0
            ? null : PERKS.register("evocation_attunement", () -> register(
                    "evocation_attunement",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().evocationAttunementRequiredLevel,
                    HandlerResources.EVOCATION_ATTUNEMENT_PERK
            ));

    // Ars Nouveau Integration - Conditional perks
    public static final RegistryObject<Perk> ARCANE_EFFICIENCY =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().arsArcaneEfficiencyRequiredLevel < 0
            ? null : PERKS.register("arcane_efficiency", () -> register(
                    "arcane_efficiency",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsArcaneEfficiencyRequiredLevel,
                    HandlerResources.ARCANE_EFFICIENCY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsArcaneEfficiencyPercent)
            ));
    public static final RegistryObject<Perk> GLYPH_MASTERY =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().arsGlyphMasteryRequiredLevel < 0
            ? null : PERKS.register("glyph_mastery", () -> register(
                    "glyph_mastery",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().arsGlyphMasteryRequiredLevel,
                    HandlerResources.GLYPH_MASTERY_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().arsGlyphMasteryAmplification)
            ));
    public static final RegistryObject<Perk> ARCANE_WARD =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().arsArcaneWardRequiredLevel < 0
            ? null : PERKS.register("arcane_ward", () -> register(
                    "arcane_ward",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().arsArcaneWardRequiredLevel,
                    HandlerResources.ARCANE_WARD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsArcaneWardPercent)
            ));

    // Blood Magic Integration - Conditional perks
    public static final RegistryObject<Perk> BLOOD_MASTERY =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().bloodMasteryRequiredLevel < 0
            ? null : PERKS.register("blood_mastery", () -> register(
                    "blood_mastery",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().bloodMasteryRequiredLevel,
                    HandlerResources.BLOOD_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodMasteryPercent)
            ));
    public static final RegistryObject<Perk> RITUAL_SAGE =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().ritualSageRequiredLevel < 0
            ? null : PERKS.register("ritual_sage", () -> register(
                    "ritual_sage",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().ritualSageRequiredLevel,
                    HandlerResources.RITUAL_SAGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ritualSagePercent)
            ));
    public static final RegistryObject<Perk> CRIMSON_BOND =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().crimsonBondRequiredLevel < 0
            ? null : PERKS.register("crimson_bond", () -> register(
                    "crimson_bond",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().crimsonBondRequiredLevel,
                    HandlerResources.CRIMSON_BOND_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().crimsonBondProbability)
            ));

    // Ice and Fire Integration - Conditional perks
    public static final RegistryObject<Perk> DRAGON_SLAYER =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().dragonSlayerRequiredLevel < 0
            ? null : PERKS.register("dragon_slayer", () -> register(
                    "dragon_slayer",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().dragonSlayerRequiredLevel,
                    HandlerResources.DRAGON_SLAYER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonSlayerPercent)
            ));
    public static final RegistryObject<Perk> BEAST_TAMER =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().beastTamerRequiredLevel < 0
            ? null : PERKS.register("beast_tamer", () -> register(
                    "beast_tamer",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().beastTamerRequiredLevel,
                    HandlerResources.BEAST_TAMER_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().beastTamerProbability)
            ));
    public static final RegistryObject<Perk> MYTHIC_FORTITUDE =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().mythicFortitudeRequiredLevel < 0
            ? null : PERKS.register("mythic_fortitude", () -> register(
                    "mythic_fortitude",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().mythicFortitudeRequiredLevel,
                    HandlerResources.MYTHIC_FORTITUDE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mythicFortitudePercent)
            ));

    // Cataclysm Integration - Conditional perks
    public static final RegistryObject<Perk> CATACLYSM_RESISTANCE =
            !CataclysmIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().cataclysmResistanceRequiredLevel < 0
            ? null : PERKS.register("cataclysm_resistance", () -> register(
                    "cataclysm_resistance",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().cataclysmResistanceRequiredLevel,
                    HandlerResources.CATACLYSM_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().cataclysmResistancePercent)
            ));

    // Enigmatic Legacy Integration - Conditional perks
    public static final RegistryObject<Perk> CURSE_WARD =
            !EnigmaticLegacyIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().curseWardRequiredLevel < 0
            ? null : PERKS.register("curse_ward", () -> register(
                    "curse_ward",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().curseWardRequiredLevel,
                    HandlerResources.CURSE_WARD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().curseWardPercent)
            ));

    // Mowzie's Mobs Integration - Conditional perks
    public static final RegistryObject<Perk> BOSS_HUNTER =
            !MowziesMobsIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().bossHunterRequiredLevel < 0
            ? null : PERKS.register("boss_hunter", () -> register(
                    "boss_hunter",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().bossHunterRequiredLevel,
                    HandlerResources.BOSS_HUNTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bossHunterPercent)
            ));

    // Nature's Aura Integration - Conditional perks
    public static final RegistryObject<Perk> AURA_ATTUNEMENT =
            !NaturesAuraIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().auraAttunementRequiredLevel < 0
            ? null : PERKS.register("aura_attunement", () -> register(
                    "aura_attunement",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().auraAttunementRequiredLevel,
                    HandlerResources.AURA_ATTUNEMENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().auraAttunementPercent)
            ));

    // Farmer's Delight Integration - Conditional perks
    public static final RegistryObject<Perk> MASTER_CHEF =
            !FarmersDelightIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().masterChefRequiredLevel < 0
            ? null : PERKS.register("master_chef", () -> register(
                    "master_chef",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().masterChefRequiredLevel,
                    HandlerResources.MASTER_CHEF_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterChefPercent)
            ));

    // Apotheosis Integration - Conditional perks
    public static final RegistryObject<Perk> RUNIC_SALVAGER =
            !ApotheosisIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().runicSalvagerRequiredLevel < 0
            ? null : PERKS.register("runic_salvager", () -> register(
                    "runic_salvager",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().runicSalvagerRequiredLevel,
                    HandlerResources.RUNIC_SALVAGER_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().runicSalvagerProbability),
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().runicSalvagerModifier)
            ));
    public static final RegistryObject<Perk> GEM_ATTUNEMENT =
            !ApotheosisIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().gemAttunementRequiredLevel < 0
            ? null : PERKS.register("gem_attunement", () -> register(
                    "gem_attunement",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().gemAttunementRequiredLevel,
                    HandlerResources.GEM_ATTUNEMENT_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().gemAttunementProbability)
            ));
    public static final RegistryObject<Perk> ARCANE_REFORGING =
            !ApotheosisIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().arcaneReforgingRequiredLevel < 0
            ? null : PERKS.register("arcane_reforging", () -> register(
                    "arcane_reforging",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arcaneReforgingRequiredLevel,
                    HandlerResources.ARCANE_REFORGING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arcaneReforgingPercent)
            ));

    // ========== NEW PERKS - STRENGTH ==========
    public static final RegistryObject<Perk> ARMOR_PIERCING =
            HandlerCommonConfig.HANDLER.instance().armorPiercingRequiredLevel < 0
            ? null : PERKS.register("armor_piercing", () -> register(
                    "armor_piercing",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().armorPiercingRequiredLevel,
                    HandlerResources.ARMOR_PIERCING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().armorPiercingPercent)
            ));
    public static final RegistryObject<Perk> HEAVY_STRIKES =
            HandlerCommonConfig.HANDLER.instance().heavyStrikesRequiredLevel < 0
            ? null : PERKS.register("heavy_strikes", () -> register(
                    "heavy_strikes",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().heavyStrikesRequiredLevel,
                    HandlerResources.HEAVY_STRIKES_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().heavyStrikesPercent)
            ));
    public static final RegistryObject<Perk> CLEAVE =
            HandlerCommonConfig.HANDLER.instance().cleaveRequiredLevel < 0
            ? null : PERKS.register("cleave", () -> register(
                    "cleave",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().cleaveRequiredLevel,
                    HandlerResources.CLEAVE_PERK
            ));
    public static final RegistryObject<Perk> TITANS_GRIP =
            !SpartanIntegration.isAnyLoaded() || HandlerCommonConfig.HANDLER.instance().titansGripRequiredLevel < 0
            ? null : PERKS.register("titans_grip", () -> register(
                    "titans_grip",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().titansGripRequiredLevel,
                    HandlerResources.TITANS_GRIP_PERK
            ));
    public static final RegistryObject<Perk> SAMURAIS_EDGE =
            !SamuraiDynastyIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().samuraisEdgeRequiredLevel < 0
            ? null : PERKS.register("samurais_edge", () -> register(
                    "samurais_edge",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().samuraisEdgeRequiredLevel,
                    HandlerResources.SAMURAIS_EDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().samuraisEdgePercent)
            ));
    public static final RegistryObject<Perk> BRUTAL_SWING =
            !SpartanIntegration.isAnyLoaded() || HandlerCommonConfig.HANDLER.instance().brutalSwingRequiredLevel < 0
            ? null : PERKS.register("brutal_swing", () -> register(
                    "brutal_swing",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().brutalSwingRequiredLevel,
                    HandlerResources.BRUTAL_SWING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().brutalSwingPercent)
            ));
    public static final RegistryObject<Perk> POLEARM_MASTERY =
            !SpartanIntegration.isAnyLoaded() || HandlerCommonConfig.HANDLER.instance().polearmMasteryRequiredLevel < 0
            ? null : PERKS.register("polearm_mastery", () -> register(
                    "polearm_mastery",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().polearmMasteryRequiredLevel,
                    HandlerResources.POLEARM_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().polearmMasteryPercent)
            ));
    public static final RegistryObject<Perk> WARMONGER =
            HandlerCommonConfig.HANDLER.instance().warmongerRequiredLevel < 0
            ? null : PERKS.register("warmonger", () -> register(
                    "warmonger",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().warmongerRequiredLevel,
                    HandlerResources.WARMONGER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().warmongerPercent)
            ));
    public static final RegistryObject<Perk> EXECUTE =
            HandlerCommonConfig.HANDLER.instance().executeRequiredLevel < 0
            ? null : PERKS.register("execute", () -> register(
                    "execute",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().executeRequiredLevel,
                    HandlerResources.EXECUTE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().executePercent)
            ));
    public static final RegistryObject<Perk> BLOODLUST =
            HandlerCommonConfig.HANDLER.instance().bloodlustRequiredLevel < 0
            ? null : PERKS.register("bloodlust", () -> register(
                    "bloodlust",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().bloodlustRequiredLevel,
                    HandlerResources.BLOODLUST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodlustPercent)
            ));
    public static final RegistryObject<Perk> DRAGON_BONE_MASTERY =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().dragonBoneMasteryRequiredLevel < 0
            ? null : PERKS.register("dragon_bone_mastery", () -> register(
                    "dragon_bone_mastery",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().dragonBoneMasteryRequiredLevel,
                    HandlerResources.DRAGON_BONE_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonBoneMasteryPercent)
            ));
    public static final RegistryObject<Perk> NICHIRIN_BLADE =
            !NichirinDynastyIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().nichirinBladeRequiredLevel < 0
            ? null : PERKS.register("nichirin_blade", () -> register(
                    "nichirin_blade",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().nichirinBladeRequiredLevel,
                    HandlerResources.NICHIRIN_BLADE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().nichirinBladePercent)
            ));
    public static final RegistryObject<Perk> SIEGE_BREAKER =
            !CataclysmIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().siegeBreakerRequiredLevel < 0
            ? null : PERKS.register("siege_breaker", () -> register(
                    "siege_breaker",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().siegeBreakerRequiredLevel,
                    HandlerResources.SIEGE_BREAKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().siegeBreakerPercent)
            ));
    public static final RegistryObject<Perk> MOWZIES_MIGHT =
            !MowziesMobsIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().mowziesMightRequiredLevel < 0
            ? null : PERKS.register("mowzies_might", () -> register(
                    "mowzies_might",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().mowziesMightRequiredLevel,
                    HandlerResources.MOWZIES_MIGHT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mowziesMightPercent)
            ));
    public static final RegistryObject<Perk> SPARTANS_DISCIPLINE =
            !SpartanIntegration.isAnyLoaded() || HandlerCommonConfig.HANDLER.instance().spartansDisciplineRequiredLevel < 0
            ? null : PERKS.register("spartans_discipline", () -> register(
                    "spartans_discipline",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().spartansDisciplineRequiredLevel,
                    HandlerResources.SPARTANS_DISCIPLINE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spartansDisciplinePercent)
            ));
    public static final RegistryObject<Perk> POWER_ATTACK =
            HandlerCommonConfig.HANDLER.instance().powerAttackRequiredLevel < 0
            ? null : PERKS.register("power_attack", () -> register(
                    "power_attack",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().powerAttackRequiredLevel,
                    HandlerResources.POWER_ATTACK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().powerAttackPercent)
            ));
    public static final RegistryObject<Perk> UNSTOPPABLE_FORCE =
            HandlerCommonConfig.HANDLER.instance().unstoppableForceRequiredLevel < 0
            ? null : PERKS.register("unstoppable_force", () -> register(
                    "unstoppable_force",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().unstoppableForceRequiredLevel,
                    HandlerResources.UNSTOPPABLE_FORCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().unstoppableForcePercent)
            ));
    public static final RegistryObject<Perk> PRIMAL_FURY =
            HandlerCommonConfig.HANDLER.instance().primalFuryRequiredLevel < 0
            ? null : PERKS.register("primal_fury", () -> register(
                    "primal_fury",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().primalFuryRequiredLevel,
                    HandlerResources.PRIMAL_FURY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().primalFuryPercent)
            ));
    public static final RegistryObject<Perk> VENGEANCE =
            HandlerCommonConfig.HANDLER.instance().vengeanceRequiredLevel < 0
            ? null : PERKS.register("vengeance", () -> register(
                    "vengeance",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().vengeanceRequiredLevel,
                    HandlerResources.VENGEANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().vengeancePercent)
            ));
    public static final RegistryObject<Perk> LAST_STAND =
            HandlerCommonConfig.HANDLER.instance().lastStandRequiredLevel < 0
            ? null : PERKS.register("last_stand", () -> register(
                    "last_stand",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().lastStandRequiredLevel,
                    HandlerResources.LAST_STAND_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lastStandPercent)
            ));
    public static final RegistryObject<Perk> WARLORDS_PRESENCE =
            HandlerCommonConfig.HANDLER.instance().warlordsPresenceRequiredLevel < 0
            ? null : PERKS.register("warlords_presence", () -> register(
                    "warlords_presence",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().warlordsPresenceRequiredLevel,
                    HandlerResources.WARLORDS_PRESENCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().warlordsPresencePercent)
            ));
    public static final RegistryObject<Perk> CHAIN_LIGHTNING_STRIKE =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().chainLightningStrikeRequiredLevel < 0
            ? null : PERKS.register("chain_lightning_strike", () -> register(
                    "chain_lightning_strike",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().chainLightningStrikeRequiredLevel,
                    HandlerResources.CHAIN_LIGHTNING_STRIKE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().chainLightningStrikePercent)
            ));
    public static final RegistryObject<Perk> BLADE_STORM =
            HandlerCommonConfig.HANDLER.instance().bladeStormRequiredLevel < 0
            ? null : PERKS.register("blade_storm", () -> register(
                    "blade_storm",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().bladeStormRequiredLevel,
                    HandlerResources.BLADE_STORM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bladeStormPercent)
            ));
    public static final RegistryObject<Perk> DEVASTATING_BLOW =
            HandlerCommonConfig.HANDLER.instance().devastatingBlowRequiredLevel < 0
            ? null : PERKS.register("devastating_blow", () -> register(
                    "devastating_blow",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().devastatingBlowRequiredLevel,
                    HandlerResources.DEVASTATING_BLOW_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().devastatingBlowPercent)
            ));
    public static final RegistryObject<Perk> SACRED_FIRE =
            HandlerCommonConfig.HANDLER.instance().sacredFireRequiredLevel < 0
            ? null : PERKS.register("sacred_fire", () -> register(
                    "sacred_fire",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().sacredFireRequiredLevel,
                    HandlerResources.SACRED_FIRE_PERK
            ));
    public static final RegistryObject<Perk> BLOOD_FURY =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().bloodFuryRequiredLevel < 0
            ? null : PERKS.register("blood_fury", () -> register(
                    "blood_fury",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().bloodFuryRequiredLevel,
                    HandlerResources.BLOOD_FURY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodFuryPercent)
            ));
    public static final RegistryObject<Perk> CATACLYSMS_WRATH =
            !CataclysmIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().cataclysmsWrathRequiredLevel < 0
            ? null : PERKS.register("cataclysms_wrath", () -> register(
                    "cataclysms_wrath",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().cataclysmsWrathRequiredLevel,
                    HandlerResources.CATACLYSMS_WRATH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().cataclysmsWrathPercent)
            ));
    public static final RegistryObject<Perk> ANCIENT_STRENGTH =
            !EnigmaticLegacyIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().ancientStrengthRequiredLevel < 0
            ? null : PERKS.register("ancient_strength", () -> register(
                    "ancient_strength",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().ancientStrengthRequiredLevel,
                    HandlerResources.ANCIENT_STRENGTH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ancientStrengthPercent)
            ));
    public static final RegistryObject<Perk> GLADIATOR =
            HandlerCommonConfig.HANDLER.instance().gladiatorRequiredLevel < 0
            ? null : PERKS.register("gladiator", () -> register(
                    "gladiator",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().gladiatorRequiredLevel,
                    HandlerResources.GLADIATOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().gladiatorPercent)
            ));
    public static final RegistryObject<Perk> TROPHY_HUNTER =
            HandlerCommonConfig.HANDLER.instance().trophyHunterRequiredLevel < 0
            ? null : PERKS.register("trophy_hunter", () -> register(
                    "trophy_hunter",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().trophyHunterRequiredLevel,
                    HandlerResources.TROPHY_HUNTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().trophyHunterPercent)
            ));
    public static final RegistryObject<Perk> DRACONIC_FURY =
            !SaintsDragonsIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().draconicFuryRequiredLevel < 0
            ? null : PERKS.register("draconic_fury", () -> register(
                    "draconic_fury",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().draconicFuryRequiredLevel,
                    HandlerResources.DRACONIC_FURY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().draconicFuryPercent)
            ));
    public static final RegistryObject<Perk> MYTHICAL_BERSERKER =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().mythicalBerserkerRequiredLevel < 0
            ? null : PERKS.register("mythical_berserker", () -> register(
                    "mythical_berserker",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().mythicalBerserkerRequiredLevel,
                    HandlerResources.MYTHICAL_BERSERKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mythicalBerserkerPercent)
            ));
    public static final RegistryObject<Perk> STALWART_STRIKER =
            !StalwartDungeonsIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().stalwartStrikerRequiredLevel < 0
            ? null : PERKS.register("stalwart_striker", () -> register(
                    "stalwart_striker",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().stalwartStrikerRequiredLevel,
                    HandlerResources.STALWART_STRIKER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().stalwartStrikerAmplifier)
            ));
    public static final RegistryObject<Perk> WEAPON_MASTER =
            HandlerCommonConfig.HANDLER.instance().weaponMasterRequiredLevel < 0
            ? null : PERKS.register("weapon_master", () -> register(
                    "weapon_master",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().weaponMasterRequiredLevel,
                    HandlerResources.WEAPON_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().weaponMasterPercent)
            ));
    public static final RegistryObject<Perk> RUNIC_MIGHT =
            HandlerCommonConfig.HANDLER.instance().runicMightRequiredLevel < 0
            ? null : PERKS.register("runic_might", () -> register(
                    "runic_might",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().runicMightRequiredLevel,
                    HandlerResources.RUNIC_MIGHT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicMightPercent)
            ));

    // ========== NEW PERKS - CONSTITUTION ==========
    public static final RegistryObject<Perk> IRON_STOMACH =
            HandlerCommonConfig.HANDLER.instance().ironStomachRequiredLevel < 0
            ? null : PERKS.register("iron_stomach", () -> register(
                    "iron_stomach",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().ironStomachRequiredLevel,
                    HandlerResources.IRON_STOMACH_PERK
            ));
    public static final RegistryObject<Perk> SECOND_WIND =
            HandlerCommonConfig.HANDLER.instance().secondWindRequiredLevel < 0
            ? null : PERKS.register("second_wind", () -> register(
                    "second_wind",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().secondWindRequiredLevel,
                    HandlerResources.SECOND_WIND_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().secondWindAmplifier)
            ));
    public static final RegistryObject<Perk> VITALITY =
            HandlerCommonConfig.HANDLER.instance().vitalityRequiredLevel < 0
            ? null : PERKS.register("vitality", () -> register(
                    "vitality",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().vitalityRequiredLevel,
                    HandlerResources.VITALITY_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().vitalityAmplifier)
            ));
    public static final RegistryObject<Perk> NATURAL_RECOVERY =
            HandlerCommonConfig.HANDLER.instance().naturalRecoveryRequiredLevel < 0
            ? null : PERKS.register("natural_recovery", () -> register(
                    "natural_recovery",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().naturalRecoveryRequiredLevel,
                    HandlerResources.NATURAL_RECOVERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().naturalRecoveryPercent)
            ));
    public static final RegistryObject<Perk> THICK_SKIN =
            HandlerCommonConfig.HANDLER.instance().thickSkinRequiredLevel < 0
            ? null : PERKS.register("thick_skin", () -> register(
                    "thick_skin",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().thickSkinRequiredLevel,
                    HandlerResources.THICK_SKIN_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().thickSkinAmplifier)
            ));
    public static final RegistryObject<Perk> POISON_IMMUNITY =
            HandlerCommonConfig.HANDLER.instance().poisonImmunityRequiredLevel < 0
            ? null : PERKS.register("poison_immunity", () -> register(
                    "poison_immunity",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().poisonImmunityRequiredLevel,
                    HandlerResources.POISON_IMMUNITY_PERK
            ));
    public static final RegistryObject<Perk> FIRE_RESISTANCE =
            HandlerCommonConfig.HANDLER.instance().fireResistanceRequiredLevel < 0
            ? null : PERKS.register("fire_resistance", () -> register(
                    "fire_resistance",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().fireResistanceRequiredLevel,
                    HandlerResources.FIRE_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fireResistancePercent)
            ));
    public static final RegistryObject<Perk> DRACONIC_CONSTITUTION =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().draconicConstitutionRequiredLevel < 0
            ? null : PERKS.register("draconic_constitution", () -> register(
                    "draconic_constitution",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().draconicConstitutionRequiredLevel,
                    HandlerResources.DRACONIC_CONSTITUTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().draconicConstitutionPercent)
            ));
    public static final RegistryObject<Perk> CULINARY_EXPERT =
            !FarmersDelightIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().culinaryExpertRequiredLevel < 0
            ? null : PERKS.register("culinary_expert", () -> register(
                    "culinary_expert",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().culinaryExpertRequiredLevel,
                    HandlerResources.CULINARY_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().culinaryExpertPercent)
            ));
    public static final RegistryObject<Perk> ANGLERS_BOUNTY =
            HandlerCommonConfig.HANDLER.instance().anglersBountyRequiredLevel < 0
            ? null : PERKS.register("anglers_bounty", () -> register(
                    "anglers_bounty",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().anglersBountyRequiredLevel,
                    HandlerResources.ANGLERS_BOUNTY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().anglersBountyPercent)
            ));
    public static final RegistryObject<Perk> BLOOD_SACRIFICE_RECOVERY =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().bloodSacrificeRecoveryRequiredLevel < 0
            ? null : PERKS.register("blood_sacrifice_recovery", () -> register(
                    "blood_sacrifice_recovery",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().bloodSacrificeRecoveryRequiredLevel,
                    HandlerResources.BLOOD_SACRIFICE_RECOVERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodSacrificeRecoveryPercent)
            ));
    public static final RegistryObject<Perk> SEARING_RESISTANCE =
            HandlerCommonConfig.HANDLER.instance().searingResistanceRequiredLevel < 0
            ? null : PERKS.register("searing_resistance", () -> register(
                    "searing_resistance",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().searingResistanceRequiredLevel,
                    HandlerResources.SEARING_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().searingResistancePercent)
            ));
    public static final RegistryObject<Perk> WITHER_RESISTANCE =
            HandlerCommonConfig.HANDLER.instance().witherResistanceRequiredLevel < 0
            ? null : PERKS.register("wither_resistance", () -> register(
                    "wither_resistance",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().witherResistanceRequiredLevel,
                    HandlerResources.WITHER_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().witherResistancePercent)
            ));
    public static final RegistryObject<Perk> UNDYING_WILL =
            HandlerCommonConfig.HANDLER.instance().undyingWillRequiredLevel < 0
            ? null : PERKS.register("undying_will", () -> register(
                    "undying_will",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().undyingWillRequiredLevel,
                    HandlerResources.UNDYING_WILL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().undyingWillPercent)
            ));
    public static final RegistryObject<Perk> HEARTY_FEAST =
            !FarmersDelightIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().heartyFeastRequiredLevel < 0
            ? null : PERKS.register("hearty_feast", () -> register(
                    "hearty_feast",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().heartyFeastRequiredLevel,
                    HandlerResources.HEARTY_FEAST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().heartyFeastPercent)
            ));
    public static final RegistryObject<Perk> DRAGON_HEART =
            !SaintsDragonsIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().dragonHeartRequiredLevel < 0
            ? null : PERKS.register("dragon_heart", () -> register(
                    "dragon_heart",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().dragonHeartRequiredLevel,
                    HandlerResources.DRAGON_HEART_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().dragonHeartAmplifier)
            ));
    public static final RegistryObject<Perk> SWIMMERS_ENDURANCE =
            HandlerCommonConfig.HANDLER.instance().swimmersEnduranceRequiredLevel < 0
            ? null : PERKS.register("swimmers_endurance", () -> register(
                    "swimmers_endurance",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().swimmersEnduranceRequiredLevel,
                    HandlerResources.SWIMMERS_ENDURANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().swimmersEndurancePercent)
            ));
    public static final RegistryObject<Perk> EXPLORERS_VIGOR =
            !StalwartDungeonsIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().explorersVigorRequiredLevel < 0
            ? null : PERKS.register("explorers_vigor", () -> register(
                    "explorers_vigor",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().explorersVigorRequiredLevel,
                    HandlerResources.EXPLORERS_VIGOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().explorersVigorPercent)
            ));
    public static final RegistryObject<Perk> AURA_OF_VITALITY =
            !NaturesAuraIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().auraOfVitalityRequiredLevel < 0
            ? null : PERKS.register("aura_of_vitality", () -> register(
                    "aura_of_vitality",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().auraOfVitalityRequiredLevel,
                    HandlerResources.AURA_OF_VITALITY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().auraOfVitalityPercent)
            ));
    public static final RegistryObject<Perk> BATTLE_RECOVERY =
            HandlerCommonConfig.HANDLER.instance().battleRecoveryRequiredLevel < 0
            ? null : PERKS.register("battle_recovery", () -> register(
                    "battle_recovery",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().battleRecoveryRequiredLevel,
                    HandlerResources.BATTLE_RECOVERY_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().battleRecoveryAmplifier)
            ));
    public static final RegistryObject<Perk> ARMOR_OF_FAITH =
            HandlerCommonConfig.HANDLER.instance().armorOfFaithRequiredLevel < 0
            ? null : PERKS.register("armor_of_faith", () -> register(
                    "armor_of_faith",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().armorOfFaithRequiredLevel,
                    HandlerResources.ARMOR_OF_FAITH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().armorOfFaithPercent)
            ));
    public static final RegistryObject<Perk> SOUL_SUSTENANCE =
            HandlerCommonConfig.HANDLER.instance().soulSustenanceRequiredLevel < 0
            ? null : PERKS.register("soul_sustenance", () -> register(
                    "soul_sustenance",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().soulSustenanceRequiredLevel,
                    HandlerResources.SOUL_SUSTENANCE_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().soulSustenanceAmplifier)
            ));
    public static final RegistryObject<Perk> ENIGMATIC_VITALITY =
            !EnigmaticLegacyIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().enigmaticVitalityRequiredLevel < 0
            ? null : PERKS.register("enigmatic_vitality", () -> register(
                    "enigmatic_vitality",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().enigmaticVitalityRequiredLevel,
                    HandlerResources.ENIGMATIC_VITALITY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enigmaticVitalityPercent)
            ));
    public static final RegistryObject<Perk> COLONIAL_NOURISHMENT =
            HandlerCommonConfig.HANDLER.instance().colonialNourishmentRequiredLevel < 0
            ? null : PERKS.register("colonial_nourishment", () -> register(
                    "colonial_nourishment",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().colonialNourishmentRequiredLevel,
                    HandlerResources.COLONIAL_NOURISHMENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().colonialNourishmentPercent)
            ));
    public static final RegistryObject<Perk> BLOOD_SHIELD =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().bloodShieldRequiredLevel < 0
            ? null : PERKS.register("blood_shield", () -> register(
                    "blood_shield",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().bloodShieldRequiredLevel,
                    HandlerResources.BLOOD_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodShieldPercent)
            ));
    public static final RegistryObject<Perk> OBSIDIAN_HEART =
            HandlerCommonConfig.HANDLER.instance().obsidianHeartRequiredLevel < 0
            ? null : PERKS.register("obsidian_heart", () -> register(
                    "obsidian_heart",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().obsidianHeartRequiredLevel,
                    HandlerResources.OBSIDIAN_HEART_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().obsidianHeartPercent)
            ));
    public static final RegistryObject<Perk> POTION_MASTERY =
            HandlerCommonConfig.HANDLER.instance().potionMasteryRequiredLevel < 0
            ? null : PERKS.register("potion_mastery", () -> register(
                    "potion_mastery",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().potionMasteryRequiredLevel,
                    HandlerResources.POTION_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().potionMasteryPercent)
            ));
    public static final RegistryObject<Perk> PHOENIX_RISING =
            HandlerCommonConfig.HANDLER.instance().phoenixRisingRequiredLevel < 0
            ? null : PERKS.register("phoenix_rising", () -> register(
                    "phoenix_rising",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().phoenixRisingRequiredLevel,
                    HandlerResources.PHOENIX_RISING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().phoenixRisingPercent)
            ));
    public static final RegistryObject<Perk> NATURES_BLESSING =
            HandlerCommonConfig.HANDLER.instance().naturesBlessingRequiredLevel < 0
            ? null : PERKS.register("natures_blessing", () -> register(
                    "natures_blessing",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().naturesBlessingRequiredLevel,
                    HandlerResources.NATURES_BLESSING_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().naturesBlessingAmplifier)
            ));
    public static final RegistryObject<Perk> RUNIC_FORTIFICATION =
            HandlerCommonConfig.HANDLER.instance().runicFortificationRequiredLevel < 0
            ? null : PERKS.register("runic_fortification", () -> register(
                    "runic_fortification",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().runicFortificationRequiredLevel,
                    HandlerResources.RUNIC_FORTIFICATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicFortificationPercent)
            ));
    public static final RegistryObject<Perk> GOURMET =
            HandlerCommonConfig.HANDLER.instance().gourmetRequiredLevel < 0
            ? null : PERKS.register("gourmet", () -> register(
                    "gourmet",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().gourmetRequiredLevel,
                    HandlerResources.GOURMET_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().gourmetPercent)
            ));
    public static final RegistryObject<Perk> FROST_WALKER_CONSTITUTION =
            HandlerCommonConfig.HANDLER.instance().frostWalkerConstitutionRequiredLevel < 0
            ? null : PERKS.register("frost_walker_constitution", () -> register(
                    "frost_walker_constitution",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().frostWalkerConstitutionRequiredLevel,
                    HandlerResources.FROST_WALKER_CONSTITUTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().frostWalkerConstitutionPercent)
            ));
    public static final RegistryObject<Perk> MYRMEX_CARAPACE =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().myrmexCarapaceRequiredLevel < 0
            ? null : PERKS.register("myrmex_carapace", () -> register(
                    "myrmex_carapace",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().myrmexCarapaceRequiredLevel,
                    HandlerResources.MYRMEX_CARAPACE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().myrmexCarapacePercent)
            ));
    public static final RegistryObject<Perk> ENDERIUM_RESILIENCE =
            HandlerCommonConfig.HANDLER.instance().enderiumResilienceRequiredLevel < 0
            ? null : PERKS.register("enderium_resilience", () -> register(
                    "enderium_resilience",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().enderiumResilienceRequiredLevel,
                    HandlerResources.ENDERIUM_RESILIENCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enderiumResiliencePercent)
            ));
    public static final RegistryObject<Perk> SURVIVAL_INSTINCT =
            HandlerCommonConfig.HANDLER.instance().survivalInstinctRequiredLevel < 0
            ? null : PERKS.register("survival_instinct", () -> register(
                    "survival_instinct",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().survivalInstinctRequiredLevel,
                    HandlerResources.SURVIVAL_INSTINCT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().survivalInstinctPercent)
            ));

    // ========== NEW PERKS - DEXTERITY ==========
    public static final RegistryObject<Perk> EAGLE_EYE =
            HandlerCommonConfig.HANDLER.instance().eagleEyeRequiredLevel < 0
            ? null : PERKS.register("eagle_eye", () -> register(
                    "eagle_eye",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().eagleEyeRequiredLevel,
                    HandlerResources.EAGLE_EYE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().eagleEyePercent)
            ));
    public static final RegistryObject<Perk> RAPID_FIRE =
            HandlerCommonConfig.HANDLER.instance().rapidFireRequiredLevel < 0
            ? null : PERKS.register("rapid_fire", () -> register(
                    "rapid_fire",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().rapidFireRequiredLevel,
                    HandlerResources.RAPID_FIRE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().rapidFirePercent)
            ));
    public static final RegistryObject<Perk> MULTISHOT_MASTERY =
            HandlerCommonConfig.HANDLER.instance().multishotMasteryRequiredLevel < 0
            ? null : PERKS.register("multishot_mastery", () -> register(
                    "multishot_mastery",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().multishotMasteryRequiredLevel,
                    HandlerResources.MULTISHOT_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().multishotMasteryPercent)
            ));
    public static final RegistryObject<Perk> ARROW_RECOVERY =
            HandlerCommonConfig.HANDLER.instance().arrowRecoveryRequiredLevel < 0
            ? null : PERKS.register("arrow_recovery", () -> register(
                    "arrow_recovery",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().arrowRecoveryRequiredLevel,
                    HandlerResources.ARROW_RECOVERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arrowRecoveryPercent)
            ));
    public static final RegistryObject<Perk> ACROBAT =
            HandlerCommonConfig.HANDLER.instance().acrobatRequiredLevel < 0
            ? null : PERKS.register("acrobat", () -> register(
                    "acrobat",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().acrobatRequiredLevel,
                    HandlerResources.ACROBAT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().acrobatPercent)
            ));
    public static final RegistryObject<Perk> DODGE_ROLL =
            HandlerCommonConfig.HANDLER.instance().dodgeRollRequiredLevel < 0
            ? null : PERKS.register("dodge_roll", () -> register(
                    "dodge_roll",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().dodgeRollRequiredLevel,
                    HandlerResources.DODGE_ROLL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dodgeRollPercent)
            ));
    public static final RegistryObject<Perk> SPRINT_MASTER =
            HandlerCommonConfig.HANDLER.instance().sprintMasterRequiredLevel < 0
            ? null : PERKS.register("sprint_master", () -> register(
                    "sprint_master",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().sprintMasterRequiredLevel,
                    HandlerResources.SPRINT_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sprintMasterPercent)
            ));
    public static final RegistryObject<Perk> SILENT_STEP =
            HandlerCommonConfig.HANDLER.instance().silentStepRequiredLevel < 0
            ? null : PERKS.register("silent_step", () -> register(
                    "silent_step",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().silentStepRequiredLevel,
                    HandlerResources.SILENT_STEP_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().silentStepPercent)
            ));
    public static final RegistryObject<Perk> PRECISION_SHOT =
            HandlerCommonConfig.HANDLER.instance().precisionShotRequiredLevel < 0
            ? null : PERKS.register("precision_shot", () -> register(
                    "precision_shot",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().precisionShotRequiredLevel,
                    HandlerResources.PRECISION_SHOT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().precisionShotPercent)
            ));
    public static final RegistryObject<Perk> ARCHERY_EXPANSION =
            HandlerCommonConfig.HANDLER.instance().archeryExpansionRequiredLevel < 0
            ? null : PERKS.register("archery_expansion", () -> register(
                    "archery_expansion",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().archeryExpansionRequiredLevel,
                    HandlerResources.ARCHERY_EXPANSION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().archeryExpansionPercent)
            ));
    public static final RegistryObject<Perk> CROSSBOW_EXPERT =
            HandlerCommonConfig.HANDLER.instance().crossbowExpertRequiredLevel < 0
            ? null : PERKS.register("crossbow_expert", () -> register(
                    "crossbow_expert",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().crossbowExpertRequiredLevel,
                    HandlerResources.CROSSBOW_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().crossbowExpertPercent)
            ));
    public static final RegistryObject<Perk> SPARTAN_MARKSMANSHIP =
            !SpartanIntegration.isAnyLoaded() || HandlerCommonConfig.HANDLER.instance().spartanMarksmanshipRequiredLevel < 0
            ? null : PERKS.register("spartan_marksmanship", () -> register(
                    "spartan_marksmanship",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().spartanMarksmanshipRequiredLevel,
                    HandlerResources.SPARTAN_MARKSMANSHIP_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spartanMarksmanshipPercent)
            ));
    public static final RegistryObject<Perk> POISON_ARROW =
            HandlerCommonConfig.HANDLER.instance().poisonArrowRequiredLevel < 0
            ? null : PERKS.register("poison_arrow", () -> register(
                    "poison_arrow",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().poisonArrowRequiredLevel,
                    HandlerResources.POISON_ARROW_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().poisonArrowPercent)
            ));
    public static final RegistryObject<Perk> WIND_RUNNER =
            HandlerCommonConfig.HANDLER.instance().windRunnerRequiredLevel < 0
            ? null : PERKS.register("wind_runner", () -> register(
                    "wind_runner",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().windRunnerRequiredLevel,
                    HandlerResources.WIND_RUNNER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().windRunnerPercent)
            ));
    public static final RegistryObject<Perk> NINJA_TRAINING =
            !SamuraiDynastyIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().ninjaTrainingRequiredLevel < 0
            ? null : PERKS.register("ninja_training", () -> register(
                    "ninja_training",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().ninjaTrainingRequiredLevel,
                    HandlerResources.NINJA_TRAINING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ninjaTrainingPercent)
            ));
    public static final RegistryObject<Perk> PARKOUR_MASTER =
            HandlerCommonConfig.HANDLER.instance().parkourMasterRequiredLevel < 0
            ? null : PERKS.register("parkour_master", () -> register(
                    "parkour_master",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().parkourMasterRequiredLevel,
                    HandlerResources.PARKOUR_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().parkourMasterPercent)
            ));

    public static final RegistryObject<Perk> SHARPSHOOTER =
            HandlerCommonConfig.HANDLER.instance().sharpshooterRequiredLevel < 0
            ? null : PERKS.register("sharpshooter", () -> register(
                    "sharpshooter",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().sharpshooterRequiredLevel,
                    HandlerResources.SHARPSHOOTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sharpshooterPercent)
            ));
    public static final RegistryObject<Perk> EVASION =
            HandlerCommonConfig.HANDLER.instance().evasionRequiredLevel < 0
            ? null : PERKS.register("evasion", () -> register(
                    "evasion",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().evasionRequiredLevel,
                    HandlerResources.EVASION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().evasionPercent)
            ));
    public static final RegistryObject<Perk> FLEET_FOOTED =
            HandlerCommonConfig.HANDLER.instance().fleetFootedRequiredLevel < 0
            ? null : PERKS.register("fleet_footed", () -> register(
                    "fleet_footed",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().fleetFootedRequiredLevel,
                    HandlerResources.FLEET_FOOTED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fleetFootedPercent)
            ));
    public static final RegistryObject<Perk> AMBUSH =
            HandlerCommonConfig.HANDLER.instance().ambushRequiredLevel < 0
            ? null : PERKS.register("ambush", () -> register(
                    "ambush",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().ambushRequiredLevel,
                    HandlerResources.AMBUSH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ambushPercent)
            ));
    public static final RegistryObject<Perk> QUICK_DRAW =
            HandlerCommonConfig.HANDLER.instance().quickDrawRequiredLevel < 0
            ? null : PERKS.register("quick_draw", () -> register(
                    "quick_draw",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().quickDrawRequiredLevel,
                    HandlerResources.QUICK_DRAW_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().quickDrawPercent)
            ));
    public static final RegistryObject<Perk> RICOCHET =
            HandlerCommonConfig.HANDLER.instance().ricochetRequiredLevel < 0
            ? null : PERKS.register("ricochet", () -> register(
                    "ricochet",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().ricochetRequiredLevel,
                    HandlerResources.RICOCHET_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ricochetPercent)
            ));
    public static final RegistryObject<Perk> PHANTOM_STRIKE =
            HandlerCommonConfig.HANDLER.instance().phantomStrikeRequiredLevel < 0
            ? null : PERKS.register("phantom_strike", () -> register(
                    "phantom_strike",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().phantomStrikeRequiredLevel,
                    HandlerResources.PHANTOM_STRIKE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().phantomStrikePercent)
            ));
    public static final RegistryObject<Perk> DRAGON_RIDER =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().dragonRiderRequiredLevel < 0
            ? null : PERKS.register("dragon_rider", () -> register(
                    "dragon_rider",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().dragonRiderRequiredLevel,
                    HandlerResources.DRAGON_RIDER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonRiderPercent)
            ));
    public static final RegistryObject<Perk> ICE_ARROWS =
            HandlerCommonConfig.HANDLER.instance().iceArrowsRequiredLevel < 0
            ? null : PERKS.register("ice_arrows", () -> register(
                    "ice_arrows",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().iceArrowsRequiredLevel,
                    HandlerResources.ICE_ARROWS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().iceArrowsPercent)
            ));
    public static final RegistryObject<Perk> SPELL_DODGE =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().spellDodgeRequiredLevel < 0
            ? null : PERKS.register("spell_dodge", () -> register(
                    "spell_dodge",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().spellDodgeRequiredLevel,
                    HandlerResources.SPELL_DODGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellDodgePercent)
            ));
    public static final RegistryObject<Perk> ZIPLINE_EXPERT =
            HandlerCommonConfig.HANDLER.instance().ziplineExpertRequiredLevel < 0
            ? null : PERKS.register("zipline_expert", () -> register(
                    "zipline_expert",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().ziplineExpertRequiredLevel,
                    HandlerResources.ZIPLINE_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ziplineExpertPercent)
            ));
    public static final RegistryObject<Perk> SNIPER =
            HandlerCommonConfig.HANDLER.instance().sniperRequiredLevel < 0
            ? null : PERKS.register("sniper", () -> register(
                    "sniper",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().sniperRequiredLevel,
                    HandlerResources.SNIPER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sniperPercent)
            ));
    public static final RegistryObject<Perk> SMOKE_BOMB =
            HandlerCommonConfig.HANDLER.instance().smokeBombRequiredLevel < 0
            ? null : PERKS.register("smoke_bomb", () -> register(
                    "smoke_bomb",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().smokeBombRequiredLevel,
                    HandlerResources.SMOKE_BOMB_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().smokeBombPercent)
            ));
    public static final RegistryObject<Perk> MOUNTED_COMBAT =
            HandlerCommonConfig.HANDLER.instance().mountedCombatRequiredLevel < 0
            ? null : PERKS.register("mounted_combat", () -> register(
                    "mounted_combat",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().mountedCombatRequiredLevel,
                    HandlerResources.MOUNTED_COMBAT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mountedCombatPercent)
            ));
    public static final RegistryObject<Perk> TRACKING =
            HandlerCommonConfig.HANDLER.instance().trackingRequiredLevel < 0
            ? null : PERKS.register("tracking", () -> register(
                    "tracking",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().trackingRequiredLevel,
                    HandlerResources.TRACKING_PERK
            ));
    public static final RegistryObject<Perk> WIND_WALKER =
            HandlerCommonConfig.HANDLER.instance().windWalkerRequiredLevel < 0
            ? null : PERKS.register("wind_walker", () -> register(
                    "wind_walker",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().windWalkerRequiredLevel,
                    HandlerResources.WIND_WALKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().windWalkerPercent)
            ));
    public static final RegistryObject<Perk> TRICK_SHOT =
            HandlerCommonConfig.HANDLER.instance().trickShotRequiredLevel < 0
            ? null : PERKS.register("trick_shot", () -> register(
                    "trick_shot",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().trickShotRequiredLevel,
                    HandlerResources.TRICK_SHOT_PERK
            ));
    public static final RegistryObject<Perk> BLADE_DANCER =
            HandlerCommonConfig.HANDLER.instance().bladeDancerRequiredLevel < 0
            ? null : PERKS.register("blade_dancer", () -> register(
                    "blade_dancer",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().bladeDancerRequiredLevel,
                    HandlerResources.BLADE_DANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bladeDancerPercent)
            ));
    public static final RegistryObject<Perk> SILENT_KILL =
            HandlerCommonConfig.HANDLER.instance().silentKillRequiredLevel < 0
            ? null : PERKS.register("silent_kill", () -> register(
                    "silent_kill",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().silentKillRequiredLevel,
                    HandlerResources.SILENT_KILL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().silentKillPercent)
            ));
    public static final RegistryObject<Perk> AGILE_CLIMBER =
            HandlerCommonConfig.HANDLER.instance().agileClimberRequiredLevel < 0
            ? null : PERKS.register("agile_climber", () -> register(
                    "agile_climber",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().agileClimberRequiredLevel,
                    HandlerResources.AGILE_CLIMBER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().agileClimberPercent)
            ));

    // ========== NEW PERKS - ENDURANCE ==========
    public static final RegistryObject<Perk> SHIELD_WALL =
            HandlerCommonConfig.HANDLER.instance().shieldWallRequiredLevel < 0
            ? null : PERKS.register("shield_wall", () -> register(
                    "shield_wall",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().shieldWallRequiredLevel,
                    HandlerResources.SHIELD_WALL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().shieldWallPercent)
            ));
    public static final RegistryObject<Perk> HEAVY_ARMOR_MASTERY =
            HandlerCommonConfig.HANDLER.instance().heavyArmorMasteryRequiredLevel < 0
            ? null : PERKS.register("heavy_armor_mastery", () -> register(
                    "heavy_armor_mastery",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().heavyArmorMasteryRequiredLevel,
                    HandlerResources.HEAVY_ARMOR_MASTERY_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().heavyArmorMasteryAmplifier)
            ));
    public static final RegistryObject<Perk> STEADFAST =
            HandlerCommonConfig.HANDLER.instance().steadfastRequiredLevel < 0
            ? null : PERKS.register("steadfast", () -> register(
                    "steadfast",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().steadfastRequiredLevel,
                    HandlerResources.STEADFAST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().steadfastPercent)
            ));
    public static final RegistryObject<Perk> TOUGHENED_HIDE =
            HandlerCommonConfig.HANDLER.instance().toughenedHideRequiredLevel < 0
            ? null : PERKS.register("toughened_hide", () -> register(
                    "toughened_hide",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().toughenedHideRequiredLevel,
                    HandlerResources.TOUGHENED_HIDE_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().toughenedHideAmplifier)
            ));
    public static final RegistryObject<Perk> FIRE_PROOF =
            HandlerCommonConfig.HANDLER.instance().fireProofRequiredLevel < 0
            ? null : PERKS.register("fire_proof", () -> register(
                    "fire_proof",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().fireProofRequiredLevel,
                    HandlerResources.FIRE_PROOF_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fireProofPercent)
            ));
    public static final RegistryObject<Perk> BLAST_RESISTANCE =
            HandlerCommonConfig.HANDLER.instance().blastResistanceRequiredLevel < 0
            ? null : PERKS.register("blast_resistance", () -> register(
                    "blast_resistance",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().blastResistanceRequiredLevel,
                    HandlerResources.BLAST_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().blastResistancePercent)
            ));
    public static final RegistryObject<Perk> WARDING_RUNE =
            HandlerCommonConfig.HANDLER.instance().wardingRuneRequiredLevel < 0
            ? null : PERKS.register("warding_rune", () -> register(
                    "warding_rune",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().wardingRuneRequiredLevel,
                    HandlerResources.WARDING_RUNE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().wardingRunePercent)
            ));
    public static final RegistryObject<Perk> DRAGON_SCALE_ARMOR =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().dragonScaleArmorRequiredLevel < 0
            ? null : PERKS.register("dragon_scale_armor", () -> register(
                    "dragon_scale_armor",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().dragonScaleArmorRequiredLevel,
                    HandlerResources.DRAGON_SCALE_ARMOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonScaleArmorPercent)
            ));
    public static final RegistryObject<Perk> BULWARK =
            HandlerCommonConfig.HANDLER.instance().bulwarkRequiredLevel < 0
            ? null : PERKS.register("bulwark", () -> register(
                    "bulwark",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().bulwarkRequiredLevel,
                    HandlerResources.BULWARK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bulwarkPercent)
            ));
    public static final RegistryObject<Perk> STONEFLESH =
            HandlerCommonConfig.HANDLER.instance().stonefleshRequiredLevel < 0
            ? null : PERKS.register("stoneflesh", () -> register(
                    "stoneflesh",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().stonefleshRequiredLevel,
                    HandlerResources.STONEFLESH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().stonefleshPercent)
            ));
    public static final RegistryObject<Perk> POISON_RESISTANCE =
            HandlerCommonConfig.HANDLER.instance().poisonResistanceRequiredLevel < 0
            ? null : PERKS.register("poison_resistance", () -> register(
                    "poison_resistance",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().poisonResistanceRequiredLevel,
                    HandlerResources.POISON_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().poisonResistancePercent)
            ));
    public static final RegistryObject<Perk> THORNS_MASTERY =
            HandlerCommonConfig.HANDLER.instance().thornsMasteryRequiredLevel < 0
            ? null : PERKS.register("thorns_mastery", () -> register(
                    "thorns_mastery",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().thornsMasteryRequiredLevel,
                    HandlerResources.THORNS_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().thornsMasteryPercent)
            ));
    public static final RegistryObject<Perk> SENTINEL =
            HandlerCommonConfig.HANDLER.instance().sentinelRequiredLevel < 0
            ? null : PERKS.register("sentinel", () -> register(
                    "sentinel",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().sentinelRequiredLevel,
                    HandlerResources.SENTINEL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sentinelPercent)
            ));
    public static final RegistryObject<Perk> DRAGONHIDE =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().dragonhideRequiredLevel < 0
            ? null : PERKS.register("dragonhide", () -> register(
                    "dragonhide",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().dragonhideRequiredLevel,
                    HandlerResources.DRAGONHIDE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonhidePercent)
            ));
    public static final RegistryObject<Perk> ENIGMATIC_PROTECTION =
            !EnigmaticLegacyIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().enigmaticProtectionRequiredLevel < 0
            ? null : PERKS.register("enigmatic_protection", () -> register(
                    "enigmatic_protection",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().enigmaticProtectionRequiredLevel,
                    HandlerResources.ENIGMATIC_PROTECTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enigmaticProtectionPercent)
            ));
    public static final RegistryObject<Perk> FANTASY_FORTITUDE =
            !FantasyArmorIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().fantasyFortitudeRequiredLevel < 0
            ? null : PERKS.register("fantasy_fortitude", () -> register(
                    "fantasy_fortitude",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().fantasyFortitudeRequiredLevel,
                    HandlerResources.FANTASY_FORTITUDE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fantasyFortitudePercent)
            ));
    public static final RegistryObject<Perk> COLONY_GUARDIAN =
            HandlerCommonConfig.HANDLER.instance().colonyGuardianRequiredLevel < 0
            ? null : PERKS.register("colony_guardian", () -> register(
                    "colony_guardian",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().colonyGuardianRequiredLevel,
                    HandlerResources.COLONY_GUARDIAN_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().colonyGuardianPercent)
            ));
    public static final RegistryObject<Perk> BLOOD_WARD =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().bloodWardRequiredLevel < 0
            ? null : PERKS.register("blood_ward", () -> register(
                    "blood_ward",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().bloodWardRequiredLevel,
                    HandlerResources.BLOOD_WARD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodWardPercent)
            ));
    public static final RegistryObject<Perk> FROST_ENDURANCE =
            HandlerCommonConfig.HANDLER.instance().frostEnduranceRequiredLevel < 0
            ? null : PERKS.register("frost_endurance", () -> register(
                    "frost_endurance",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().frostEnduranceRequiredLevel,
                    HandlerResources.FROST_ENDURANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().frostEndurancePercent)
            ));
    public static final RegistryObject<Perk> OBSIDIAN_SKIN =
            HandlerCommonConfig.HANDLER.instance().obsidianSkinRequiredLevel < 0
            ? null : PERKS.register("obsidian_skin", () -> register(
                    "obsidian_skin",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().obsidianSkinRequiredLevel,
                    HandlerResources.OBSIDIAN_SKIN_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().obsidianSkinPercent)
            ));
    public static final RegistryObject<Perk> LIGHTNING_ROD =
            HandlerCommonConfig.HANDLER.instance().lightningRodRequiredLevel < 0
            ? null : PERKS.register("lightning_rod", () -> register(
                    "lightning_rod",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().lightningRodRequiredLevel,
                    HandlerResources.LIGHTNING_ROD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lightningRodPercent)
            ));
    public static final RegistryObject<Perk> SAMURAI_RESOLVE =
            !SamuraiDynastyIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().samuraiResolveRequiredLevel < 0
            ? null : PERKS.register("samurai_resolve", () -> register(
                    "samurai_resolve",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().samuraiResolveRequiredLevel,
                    HandlerResources.SAMURAI_RESOLVE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().samuraiResolvePercent)
            ));
    public static final RegistryObject<Perk> DUNGEON_RESILIENCE =
            !StalwartDungeonsIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().dungeonResilienceRequiredLevel < 0
            ? null : PERKS.register("dungeon_resilience", () -> register(
                    "dungeon_resilience",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().dungeonResilienceRequiredLevel,
                    HandlerResources.DUNGEON_RESILIENCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dungeonResiliencePercent)
            ));
    public static final RegistryObject<Perk> PRISMARINE_SHIELD =
            HandlerCommonConfig.HANDLER.instance().prismarineShieldRequiredLevel < 0
            ? null : PERKS.register("prismarine_shield", () -> register(
                    "prismarine_shield",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().prismarineShieldRequiredLevel,
                    HandlerResources.PRISMARINE_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().prismarineShieldPercent)
            ));
    public static final RegistryObject<Perk> AURA_SHIELD =
            !NaturesAuraIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().auraShieldRequiredLevel < 0
            ? null : PERKS.register("aura_shield", () -> register(
                    "aura_shield",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().auraShieldRequiredLevel,
                    HandlerResources.AURA_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().auraShieldPercent)
            ));
    public static final RegistryObject<Perk> PAIN_SUPPRESSION =
            HandlerCommonConfig.HANDLER.instance().painSuppressionRequiredLevel < 0
            ? null : PERKS.register("pain_suppression", () -> register(
                    "pain_suppression",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().painSuppressionRequiredLevel,
                    HandlerResources.PAIN_SUPPRESSION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().painSuppressionPercent)
            ));
    public static final RegistryObject<Perk> SPELL_SHIELD =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().spellShieldRequiredLevel < 0
            ? null : PERKS.register("spell_shield", () -> register(
                    "spell_shield",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().spellShieldRequiredLevel,
                    HandlerResources.SPELL_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellShieldPercent)
            ));
    public static final RegistryObject<Perk> UNBREAKABLE =
            HandlerCommonConfig.HANDLER.instance().unbreakableRequiredLevel < 0
            ? null : PERKS.register("unbreakable", () -> register(
                    "unbreakable",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().unbreakableRequiredLevel,
                    HandlerResources.UNBREAKABLE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().unbreakablePercent)
            ));
    public static final RegistryObject<Perk> DRAGON_BREATH_SHIELD =
            !SaintsDragonsIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().dragonBreathShieldRequiredLevel < 0
            ? null : PERKS.register("dragon_breath_shield", () -> register(
                    "dragon_breath_shield",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().dragonBreathShieldRequiredLevel,
                    HandlerResources.DRAGON_BREATH_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonBreathShieldPercent)
            ));
    public static final RegistryObject<Perk> SIEGE_DEFENSE =
            HandlerCommonConfig.HANDLER.instance().siegeDefenseRequiredLevel < 0
            ? null : PERKS.register("siege_defense", () -> register(
                    "siege_defense",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().siegeDefenseRequiredLevel,
                    HandlerResources.SIEGE_DEFENSE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().siegeDefensePercent)
            ));
    public static final RegistryObject<Perk> ANCIENT_GUARDIAN =
            HandlerCommonConfig.HANDLER.instance().ancientGuardianRequiredLevel < 0
            ? null : PERKS.register("ancient_guardian", () -> register(
                    "ancient_guardian",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().ancientGuardianRequiredLevel,
                    HandlerResources.ANCIENT_GUARDIAN_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ancientGuardianPercent)
            ));
    public static final RegistryObject<Perk> RUNIC_WARD =
            HandlerCommonConfig.HANDLER.instance().runicWardRequiredLevel < 0
            ? null : PERKS.register("runic_ward", () -> register(
                    "runic_ward",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().runicWardRequiredLevel,
                    HandlerResources.RUNIC_WARD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicWardPercent)
            ));
    public static final RegistryObject<Perk> ADAPTATION =
            HandlerCommonConfig.HANDLER.instance().adaptationRequiredLevel < 0
            ? null : PERKS.register("adaptation", () -> register(
                    "adaptation",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().adaptationRequiredLevel,
                    HandlerResources.ADAPTATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().adaptationPercent)
            ));
    public static final RegistryObject<Perk> IMMOVABLE_OBJECT =
            HandlerCommonConfig.HANDLER.instance().immovableObjectRequiredLevel < 0
            ? null : PERKS.register("immovable_object", () -> register(
                    "immovable_object",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().immovableObjectRequiredLevel,
                    HandlerResources.IMMOVABLE_OBJECT_PERK
            ));

    // ========== NEW PERKS - INTELLIGENCE ==========
    public static final RegistryObject<Perk> BOOKWORM =
            HandlerCommonConfig.HANDLER.instance().bookwormRequiredLevel < 0
            ? null : PERKS.register("bookworm", () -> register(
                    "bookworm",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().bookwormRequiredLevel,
                    HandlerResources.BOOKWORM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bookwormPercent)
            ));
    public static final RegistryObject<Perk> QUICK_LEARNER =
            HandlerCommonConfig.HANDLER.instance().quickLearnerRequiredLevel < 0
            ? null : PERKS.register("quick_learner", () -> register(
                    "quick_learner",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().quickLearnerRequiredLevel,
                    HandlerResources.QUICK_LEARNER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().quickLearnerPercent)
            ));
    public static final RegistryObject<Perk> LINGUIST =
            HandlerCommonConfig.HANDLER.instance().linguistRequiredLevel < 0
            ? null : PERKS.register("linguist", () -> register(
                    "linguist",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().linguistRequiredLevel,
                    HandlerResources.LINGUIST_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().linguistAmplifier)
            ));
    public static final RegistryObject<Perk> CARTOGRAPHER =
            HandlerCommonConfig.HANDLER.instance().cartographerRequiredLevel < 0
            ? null : PERKS.register("cartographer", () -> register(
                    "cartographer",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().cartographerRequiredLevel,
                    HandlerResources.CARTOGRAPHER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().cartographerPercent)
            ));
    public static final RegistryObject<Perk> POTION_BREWING_EXPERT =
            HandlerCommonConfig.HANDLER.instance().potionBrewingExpertRequiredLevel < 0
            ? null : PERKS.register("potion_brewing_expert", () -> register(
                    "potion_brewing_expert",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().potionBrewingExpertRequiredLevel,
                    HandlerResources.POTION_BREWING_EXPERT_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().potionBrewingExpertAmplifier)
            ));
    public static final RegistryObject<Perk> LORE_KEEPER =
            HandlerCommonConfig.HANDLER.instance().loreKeeperRequiredLevel < 0
            ? null : PERKS.register("lore_keeper", () -> register(
                    "lore_keeper",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().loreKeeperRequiredLevel,
                    HandlerResources.LORE_KEEPER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().loreKeeperPercent)
            ));
    public static final RegistryObject<Perk> DRAGON_LORE =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().dragonLoreRequiredLevel < 0
            ? null : PERKS.register("dragon_lore", () -> register(
                    "dragon_lore",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().dragonLoreRequiredLevel,
                    HandlerResources.DRAGON_LORE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonLorePercent)
            ));
    public static final RegistryObject<Perk> SPELLCRAFT_KNOWLEDGE =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().spellcraftKnowledgeRequiredLevel < 0
            ? null : PERKS.register("spellcraft_knowledge", () -> register(
                    "spellcraft_knowledge",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().spellcraftKnowledgeRequiredLevel,
                    HandlerResources.SPELLCRAFT_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellcraftKnowledgePercent)
            ));
    public static final RegistryObject<Perk> ARCANE_SCHOLAR =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().arcaneScholarRequiredLevel < 0
            ? null : PERKS.register("arcane_scholar", () -> register(
                    "arcane_scholar",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().arcaneScholarRequiredLevel,
                    HandlerResources.ARCANE_SCHOLAR_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().arcaneScholarAmplifier)
            ));
    public static final RegistryObject<Perk> BLOOD_RITUALIST =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().bloodRitualistRequiredLevel < 0
            ? null : PERKS.register("blood_ritualist", () -> register(
                    "blood_ritualist",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().bloodRitualistRequiredLevel,
                    HandlerResources.BLOOD_RITUALIST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodRitualistPercent)
            ));
    public static final RegistryObject<Perk> COLONY_ADVISOR =
            HandlerCommonConfig.HANDLER.instance().colonyAdvisorRequiredLevel < 0
            ? null : PERKS.register("colony_advisor", () -> register(
                    "colony_advisor",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().colonyAdvisorRequiredLevel,
                    HandlerResources.COLONY_ADVISOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().colonyAdvisorPercent)
            ));
    public static final RegistryObject<Perk> APOTHECARY =
            HandlerCommonConfig.HANDLER.instance().apothecaryRequiredLevel < 0
            ? null : PERKS.register("apothecary", () -> register(
                    "apothecary",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().apothecaryRequiredLevel,
                    HandlerResources.APOTHECARY_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().apothecaryAmplifier)
            ));
    public static final RegistryObject<Perk> SIEGE_ENGINEER =
            HandlerCommonConfig.HANDLER.instance().siegeEngineerRequiredLevel < 0
            ? null : PERKS.register("siege_engineer", () -> register(
                    "siege_engineer",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().siegeEngineerRequiredLevel,
                    HandlerResources.SIEGE_ENGINEER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().siegeEngineerPercent)
            ));
    public static final RegistryObject<Perk> MONSTER_COMPENDIUM =
            HandlerCommonConfig.HANDLER.instance().monsterCompendiumRequiredLevel < 0
            ? null : PERKS.register("monster_compendium", () -> register(
                    "monster_compendium",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().monsterCompendiumRequiredLevel,
                    HandlerResources.MONSTER_COMPENDIUM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().monsterCompendiumPercent)
            ));
    public static final RegistryObject<Perk> TACTICAL_GENIUS =
            HandlerCommonConfig.HANDLER.instance().tacticalGeniusRequiredLevel < 0
            ? null : PERKS.register("tactical_genius", () -> register(
                    "tactical_genius",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().tacticalGeniusRequiredLevel,
                    HandlerResources.TACTICAL_GENIUS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().tacticalGeniusPercent)
            ));
    public static final RegistryObject<Perk> NATURES_WISDOM =
            !NaturesAuraIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().naturesWisdomRequiredLevel < 0
            ? null : PERKS.register("natures_wisdom", () -> register(
                    "natures_wisdom",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().naturesWisdomRequiredLevel,
                    HandlerResources.NATURES_WISDOM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().naturesWisdomPercent)
            ));
    public static final RegistryObject<Perk> ENCHANTMENT_INSIGHT =
            HandlerCommonConfig.HANDLER.instance().enchantmentInsightRequiredLevel < 0
            ? null : PERKS.register("enchantment_insight", () -> register(
                    "enchantment_insight",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().enchantmentInsightRequiredLevel,
                    HandlerResources.ENCHANTMENT_INSIGHT_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().enchantmentInsightAmplifier)
            ));
    public static final RegistryObject<Perk> EFFICIENT_CRAFTING =
            HandlerCommonConfig.HANDLER.instance().efficientCraftingRequiredLevel < 0
            ? null : PERKS.register("efficient_crafting", () -> register(
                    "efficient_crafting",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().efficientCraftingRequiredLevel,
                    HandlerResources.EFFICIENT_CRAFTING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().efficientCraftingPercent)
            ));
    public static final RegistryObject<Perk> RUNECRAFTER =
            HandlerCommonConfig.HANDLER.instance().runecrafterRequiredLevel < 0
            ? null : PERKS.register("runecrafter", () -> register(
                    "runecrafter",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().runecrafterRequiredLevel,
                    HandlerResources.RUNECRAFTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runecrafterPercent)
            ));
    public static final RegistryObject<Perk> AQUATIC_KNOWLEDGE =
            HandlerCommonConfig.HANDLER.instance().aquaticKnowledgeRequiredLevel < 0
            ? null : PERKS.register("aquatic_knowledge", () -> register(
                    "aquatic_knowledge",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().aquaticKnowledgeRequiredLevel,
                    HandlerResources.AQUATIC_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().aquaticKnowledgePercent)
            ));
    public static final RegistryObject<Perk> PROGRESSIVE_MASTERY =
            HandlerCommonConfig.HANDLER.instance().progressiveMasteryRequiredLevel < 0
            ? null : PERKS.register("progressive_mastery", () -> register(
                    "progressive_mastery",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().progressiveMasteryRequiredLevel,
                    HandlerResources.PROGRESSIVE_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().progressiveMasteryPercent)
            ));
    public static final RegistryObject<Perk> SCROLL_MASTERY =
            HandlerCommonConfig.HANDLER.instance().scrollMasteryRequiredLevel < 0
            ? null : PERKS.register("scroll_mastery", () -> register(
                    "scroll_mastery",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().scrollMasteryRequiredLevel,
                    HandlerResources.SCROLL_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().scrollMasteryPercent)
            ));
    public static final RegistryObject<Perk> FAMILIAR_BOND =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().familiarBondRequiredLevel < 0
            ? null : PERKS.register("familiar_bond", () -> register(
                    "familiar_bond",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().familiarBondRequiredLevel,
                    HandlerResources.FAMILIAR_BOND_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().familiarBondPercent)
            ));
    public static final RegistryObject<Perk> STRATEGIC_MIND =
            HandlerCommonConfig.HANDLER.instance().strategicMindRequiredLevel < 0
            ? null : PERKS.register("strategic_mind", () -> register(
                    "strategic_mind",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().strategicMindRequiredLevel,
                    HandlerResources.STRATEGIC_MIND_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().strategicMindPercent)
            ));
    public static final RegistryObject<Perk> BREWING_INNOVATION =
            HandlerCommonConfig.HANDLER.instance().brewingInnovationRequiredLevel < 0
            ? null : PERKS.register("brewing_innovation", () -> register(
                    "brewing_innovation",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().brewingInnovationRequiredLevel,
                    HandlerResources.BREWING_INNOVATION_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().brewingInnovationAmplifier)
            ));
    public static final RegistryObject<Perk> ANCIENT_LANGUAGES =
            HandlerCommonConfig.HANDLER.instance().ancientLanguagesRequiredLevel < 0
            ? null : PERKS.register("ancient_languages", () -> register(
                    "ancient_languages",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().ancientLanguagesRequiredLevel,
                    HandlerResources.ANCIENT_LANGUAGES_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ancientLanguagesPercent)
            ));
    public static final RegistryObject<Perk> MASTER_RESEARCHER =
            HandlerCommonConfig.HANDLER.instance().masterResearcherRequiredLevel < 0
            ? null : PERKS.register("master_researcher", () -> register(
                    "master_researcher",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().masterResearcherRequiredLevel,
                    HandlerResources.MASTER_RESEARCHER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterResearcherPercent)
            ));
    public static final RegistryObject<Perk> GOLEM_COMMANDER =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().golemCommanderRequiredLevel < 0
            ? null : PERKS.register("golem_commander", () -> register(
                    "golem_commander",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().golemCommanderRequiredLevel,
                    HandlerResources.GOLEM_COMMANDER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().golemCommanderPercent)
            ));
    public static final RegistryObject<Perk> DIMENSIONAL_SCHOLAR =
            HandlerCommonConfig.HANDLER.instance().dimensionalScholarRequiredLevel < 0
            ? null : PERKS.register("dimensional_scholar", () -> register(
                    "dimensional_scholar",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().dimensionalScholarRequiredLevel,
                    HandlerResources.DIMENSIONAL_SCHOLAR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dimensionalScholarPercent)
            ));
    public static final RegistryObject<Perk> WAR_TACTICIAN =
            HandlerCommonConfig.HANDLER.instance().warTacticianRequiredLevel < 0
            ? null : PERKS.register("war_tactician", () -> register(
                    "war_tactician",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().warTacticianRequiredLevel,
                    HandlerResources.WAR_TACTICIAN_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().warTacticianPercent)
            ));
    public static final RegistryObject<Perk> ALCHEMIC_TRANSMUTATION =
            HandlerCommonConfig.HANDLER.instance().alchemicTransmutationRequiredLevel < 0
            ? null : PERKS.register("alchemic_transmutation", () -> register(
                    "alchemic_transmutation",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().alchemicTransmutationRequiredLevel,
                    HandlerResources.ALCHEMIC_TRANSMUTATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().alchemicTransmutationPercent)
            ));
    public static final RegistryObject<Perk> MYSTIC_ANALYSIS =
            HandlerCommonConfig.HANDLER.instance().mysticAnalysisRequiredLevel < 0
            ? null : PERKS.register("mystic_analysis", () -> register(
                    "mystic_analysis",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().mysticAnalysisRequiredLevel,
                    HandlerResources.MYSTIC_ANALYSIS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mysticAnalysisPercent)
            ));
    public static final RegistryObject<Perk> SAGES_FOCUS =
            HandlerCommonConfig.HANDLER.instance().sagesFocusRequiredLevel < 0
            ? null : PERKS.register("sages_focus", () -> register(
                    "sages_focus",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().sagesFocusRequiredLevel,
                    HandlerResources.SAGES_FOCUS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sagesFocusPercent)
            ));
    public static final RegistryObject<Perk> ENIGMATIC_WISDOM =
            !EnigmaticLegacyIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().enigmaticWisdomRequiredLevel < 0
            ? null : PERKS.register("enigmatic_wisdom", () -> register(
                    "enigmatic_wisdom",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().enigmaticWisdomRequiredLevel,
                    HandlerResources.ENIGMATIC_WISDOM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enigmaticWisdomPercent)
            ));

    // ========== NEW PERKS - BUILDING ==========
    public static final RegistryObject<Perk> EFFICIENT_MINER =
            HandlerCommonConfig.HANDLER.instance().efficientMinerRequiredLevel < 0
            ? null : PERKS.register("efficient_miner", () -> register(
                    "efficient_miner",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().efficientMinerRequiredLevel,
                    HandlerResources.EFFICIENT_MINER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().efficientMinerPercent)
            ));
    public static final RegistryObject<Perk> VEIN_MINER =
            HandlerCommonConfig.HANDLER.instance().veinMinerRequiredLevel < 0
            ? null : PERKS.register("vein_miner", () -> register(
                    "vein_miner",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().veinMinerRequiredLevel,
                    HandlerResources.VEIN_MINER_PERK
            ));
    public static final RegistryObject<Perk> SILK_TOUCH_MASTERY =
            HandlerCommonConfig.HANDLER.instance().silkTouchMasteryRequiredLevel < 0
            ? null : PERKS.register("silk_touch_mastery", () -> register(
                    "silk_touch_mastery",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().silkTouchMasteryRequiredLevel,
                    HandlerResources.SILK_TOUCH_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().silkTouchMasteryPercent)
            ));
    public static final RegistryObject<Perk> FORTUNE_MINER =
            HandlerCommonConfig.HANDLER.instance().fortuneMinerRequiredLevel < 0
            ? null : PERKS.register("fortune_miner", () -> register(
                    "fortune_miner",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().fortuneMinerRequiredLevel,
                    HandlerResources.FORTUNE_MINER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fortuneMinerPercent)
            ));
    public static final RegistryObject<Perk> ARCHITECT =
            HandlerCommonConfig.HANDLER.instance().architectRequiredLevel < 0
            ? null : PERKS.register("architect", () -> register(
                    "architect",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().architectRequiredLevel,
                    HandlerResources.ARCHITECT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().architectPercent)
            ));
    public static final RegistryObject<Perk> MASTER_MASON =
            HandlerCommonConfig.HANDLER.instance().masterMasonRequiredLevel < 0
            ? null : PERKS.register("master_mason", () -> register(
                    "master_mason",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().masterMasonRequiredLevel,
                    HandlerResources.MASTER_MASON_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterMasonPercent)
            ));
    public static final RegistryObject<Perk> LUMBERJACK =
            HandlerCommonConfig.HANDLER.instance().lumberjackRequiredLevel < 0
            ? null : PERKS.register("lumberjack", () -> register(
                    "lumberjack",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().lumberjackRequiredLevel,
                    HandlerResources.LUMBERJACK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lumberjackPercent)
            ));
    public static final RegistryObject<Perk> SMELTER =
            HandlerCommonConfig.HANDLER.instance().smelterRequiredLevel < 0
            ? null : PERKS.register("smelter", () -> register(
                    "smelter",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().smelterRequiredLevel,
                    HandlerResources.SMELTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().smelterPercent)
            ));
    public static final RegistryObject<Perk> QUARRY_MASTER =
            HandlerCommonConfig.HANDLER.instance().quarryMasterRequiredLevel < 0
            ? null : PERKS.register("quarry_master", () -> register(
                    "quarry_master",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().quarryMasterRequiredLevel,
                    HandlerResources.QUARRY_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().quarryMasterPercent)
            ));
    public static final RegistryObject<Perk> COLONY_BUILDER =
            HandlerCommonConfig.HANDLER.instance().colonyBuilderRequiredLevel < 0
            ? null : PERKS.register("colony_builder", () -> register(
                    "colony_builder",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().colonyBuilderRequiredLevel,
                    HandlerResources.COLONY_BUILDER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().colonyBuilderPercent)
            ));
    public static final RegistryObject<Perk> RESOURCE_EFFICIENCY =
            HandlerCommonConfig.HANDLER.instance().resourceEfficiencyRequiredLevel < 0
            ? null : PERKS.register("resource_efficiency", () -> register(
                    "resource_efficiency",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().resourceEfficiencyRequiredLevel,
                    HandlerResources.RESOURCE_EFFICIENCY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().resourceEfficiencyPercent)
            ));
    public static final RegistryObject<Perk> REINFORCED_CONSTRUCTION =
            HandlerCommonConfig.HANDLER.instance().reinforcedConstructionRequiredLevel < 0
            ? null : PERKS.register("reinforced_construction", () -> register(
                    "reinforced_construction",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().reinforcedConstructionRequiredLevel,
                    HandlerResources.REINFORCED_CONSTRUCTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().reinforcedConstructionPercent)
            ));
    public static final RegistryObject<Perk> TERRAFORMER =
            HandlerCommonConfig.HANDLER.instance().terraformerRequiredLevel < 0
            ? null : PERKS.register("terraformer", () -> register(
                    "terraformer",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().terraformerRequiredLevel,
                    HandlerResources.TERRAFORMER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().terraformerPercent)
            ));
    public static final RegistryObject<Perk> ORE_DETECTOR =
            HandlerCommonConfig.HANDLER.instance().oreDetectorRequiredLevel < 0
            ? null : PERKS.register("ore_detector", () -> register(
                    "ore_detector",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().oreDetectorRequiredLevel,
                    HandlerResources.ORE_DETECTOR_PERK
            ));
    public static final RegistryObject<Perk> BLAST_MINING =
            HandlerCommonConfig.HANDLER.instance().blastMiningRequiredLevel < 0
            ? null : PERKS.register("blast_mining", () -> register(
                    "blast_mining",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().blastMiningRequiredLevel,
                    HandlerResources.BLAST_MINING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().blastMiningPercent)
            ));
    public static final RegistryObject<Perk> STONE_CUTTER_EFFICIENCY =
            HandlerCommonConfig.HANDLER.instance().stoneCutterEfficiencyRequiredLevel < 0
            ? null : PERKS.register("stone_cutter_efficiency", () -> register(
                    "stone_cutter_efficiency",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().stoneCutterEfficiencyRequiredLevel,
                    HandlerResources.STONE_CUTTER_EFFICIENCY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().stoneCutterEfficiencyPercent)
            ));
    public static final RegistryObject<Perk> MASTER_WOODWORKER =
            HandlerCommonConfig.HANDLER.instance().masterWoodworkerRequiredLevel < 0
            ? null : PERKS.register("master_woodworker", () -> register(
                    "master_woodworker",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().masterWoodworkerRequiredLevel,
                    HandlerResources.MASTER_WOODWORKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterWoodworkerPercent)
            ));
    public static final RegistryObject<Perk> SCAFFOLD_MASTER =
            HandlerCommonConfig.HANDLER.instance().scaffoldMasterRequiredLevel < 0
            ? null : PERKS.register("scaffold_master", () -> register(
                    "scaffold_master",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().scaffoldMasterRequiredLevel,
                    HandlerResources.SCAFFOLD_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().scaffoldMasterPercent)
            ));
    public static final RegistryObject<Perk> DEEP_CORE_MINING =
            HandlerCommonConfig.HANDLER.instance().deepCoreMiningRequiredLevel < 0
            ? null : PERKS.register("deep_core_mining", () -> register(
                    "deep_core_mining",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().deepCoreMiningRequiredLevel,
                    HandlerResources.DEEP_CORE_MINING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().deepCoreMiningPercent)
            ));
    public static final RegistryObject<Perk> BRIDGE_BUILDER =
            HandlerCommonConfig.HANDLER.instance().bridgeBuilderRequiredLevel < 0
            ? null : PERKS.register("bridge_builder", () -> register(
                    "bridge_builder",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().bridgeBuilderRequiredLevel,
                    HandlerResources.BRIDGE_BUILDER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().bridgeBuilderAmplifier)
            ));
    public static final RegistryObject<Perk> RUNIC_MINING =
            HandlerCommonConfig.HANDLER.instance().runicMiningRequiredLevel < 0
            ? null : PERKS.register("runic_mining", () -> register(
                    "runic_mining",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().runicMiningRequiredLevel,
                    HandlerResources.RUNIC_MINING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicMiningPercent)
            ));
    public static final RegistryObject<Perk> MEDIEVAL_ARCHITECTURE =
            HandlerCommonConfig.HANDLER.instance().medievalArchitectureRequiredLevel < 0
            ? null : PERKS.register("medieval_architecture", () -> register(
                    "medieval_architecture",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().medievalArchitectureRequiredLevel,
                    HandlerResources.MEDIEVAL_ARCHITECTURE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().medievalArchitecturePercent)
            ));
    public static final RegistryObject<Perk> EXPLOSIVE_EXPERT =
            HandlerCommonConfig.HANDLER.instance().explosiveExpertRequiredLevel < 0
            ? null : PERKS.register("explosive_expert", () -> register(
                    "explosive_expert",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().explosiveExpertRequiredLevel,
                    HandlerResources.EXPLOSIVE_EXPERT_PERK
            ));
    public static final RegistryObject<Perk> FOUNDATION_LAYER =
            HandlerCommonConfig.HANDLER.instance().foundationLayerRequiredLevel < 0
            ? null : PERKS.register("foundation_layer", () -> register(
                    "foundation_layer",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().foundationLayerRequiredLevel,
                    HandlerResources.FOUNDATION_LAYER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().foundationLayerPercent)
            ));
    public static final RegistryObject<Perk> STRUCTURAL_ENGINEER =
            HandlerCommonConfig.HANDLER.instance().structuralEngineerRequiredLevel < 0
            ? null : PERKS.register("structural_engineer", () -> register(
                    "structural_engineer",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().structuralEngineerRequiredLevel,
                    HandlerResources.STRUCTURAL_ENGINEER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().structuralEngineerPercent)
            ));
    public static final RegistryObject<Perk> FARMERS_HAND =
            !FarmersDelightIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().farmersHandRequiredLevel < 0
            ? null : PERKS.register("farmers_hand", () -> register(
                    "farmers_hand",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().farmersHandRequiredLevel,
                    HandlerResources.FARMERS_HAND_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().farmersHandPercent)
            ));
    public static final RegistryObject<Perk> IRRIGATION_EXPERT =
            HandlerCommonConfig.HANDLER.instance().irrigationExpertRequiredLevel < 0
            ? null : PERKS.register("irrigation_expert", () -> register(
                    "irrigation_expert",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().irrigationExpertRequiredLevel,
                    HandlerResources.IRRIGATION_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().irrigationExpertPercent)
            ));
    public static final RegistryObject<Perk> DIMENSIONAL_BUILDER =
            HandlerCommonConfig.HANDLER.instance().dimensionalBuilderRequiredLevel < 0
            ? null : PERKS.register("dimensional_builder", () -> register(
                    "dimensional_builder",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().dimensionalBuilderRequiredLevel,
                    HandlerResources.DIMENSIONAL_BUILDER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dimensionalBuilderPercent)
            ));
    public static final RegistryObject<Perk> MASTER_BREAKER =
            HandlerCommonConfig.HANDLER.instance().masterBreakerRequiredLevel < 0
            ? null : PERKS.register("master_breaker", () -> register(
                    "master_breaker",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().masterBreakerRequiredLevel,
                    HandlerResources.MASTER_BREAKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterBreakerPercent)
            ));
    public static final RegistryObject<Perk> GLOWSTONE_SIGHT =
            HandlerCommonConfig.HANDLER.instance().glowstoneSightRequiredLevel < 0
            ? null : PERKS.register("glowstone_sight", () -> register(
                    "glowstone_sight",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().glowstoneSightRequiredLevel,
                    HandlerResources.GLOWSTONE_SIGHT_PERK
            ));
    public static final RegistryObject<Perk> SALVAGE_EXPERT =
            HandlerCommonConfig.HANDLER.instance().salvageExpertRequiredLevel < 0
            ? null : PERKS.register("salvage_expert", () -> register(
                    "salvage_expert",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().salvageExpertRequiredLevel,
                    HandlerResources.SALVAGE_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().salvageExpertPercent)
            ));
    public static final RegistryObject<Perk> PROSPECTOR =
            HandlerCommonConfig.HANDLER.instance().prospectorRequiredLevel < 0
            ? null : PERKS.register("prospector", () -> register(
                    "prospector",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().prospectorRequiredLevel,
                    HandlerResources.PROSPECTOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().prospectorPercent)
            ));
    public static final RegistryObject<Perk> CONSTRUCTION_HASTE =
            HandlerCommonConfig.HANDLER.instance().constructionHasteRequiredLevel < 0
            ? null : PERKS.register("construction_haste", () -> register(
                    "construction_haste",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().constructionHasteRequiredLevel,
                    HandlerResources.CONSTRUCTION_HASTE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().constructionHastePercent)
            ));
    public static final RegistryObject<Perk> UNDERGROUND_EXPLORER =
            HandlerCommonConfig.HANDLER.instance().undergroundExplorerRequiredLevel < 0
            ? null : PERKS.register("underground_explorer", () -> register(
                    "underground_explorer",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().undergroundExplorerRequiredLevel,
                    HandlerResources.UNDERGROUND_EXPLORER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().undergroundExplorerPercent)
            ));
    public static final RegistryObject<Perk> MASS_PRODUCTION =
            HandlerCommonConfig.HANDLER.instance().massProductionRequiredLevel < 0
            ? null : PERKS.register("mass_production", () -> register(
                    "mass_production",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().massProductionRequiredLevel,
                    HandlerResources.MASS_PRODUCTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().massProductionPercent)
            ));
    public static final RegistryObject<Perk> HERITAGE_BUILDER =
            HandlerCommonConfig.HANDLER.instance().heritageBuilderRequiredLevel < 0
            ? null : PERKS.register("heritage_builder", () -> register(
                    "heritage_builder",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().heritageBuilderRequiredLevel,
                    HandlerResources.HERITAGE_BUILDER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().heritageBuilderPercent)
            ));

    // ========== NEW PERKS - WISDOM ==========
    public static final RegistryObject<Perk> ENCHANTMENT_PRESERVATION =
            HandlerCommonConfig.HANDLER.instance().enchantmentPreservationRequiredLevel < 0
            ? null : PERKS.register("enchantment_preservation", () -> register(
                    "enchantment_preservation",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().enchantmentPreservationRequiredLevel,
                    HandlerResources.ENCHANTMENT_PRESERVATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantmentPreservationPercent)
            ));
    public static final RegistryObject<Perk> DISENCHANT_MASTERY =
            HandlerCommonConfig.HANDLER.instance().disenchantMasteryRequiredLevel < 0
            ? null : PERKS.register("disenchant_mastery", () -> register(
                    "disenchant_mastery",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().disenchantMasteryRequiredLevel,
                    HandlerResources.DISENCHANT_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().disenchantMasteryPercent)
            ));
    public static final RegistryObject<Perk> MENDING_BOOST =
            HandlerCommonConfig.HANDLER.instance().mendingBoostRequiredLevel < 0
            ? null : PERKS.register("mending_boost", () -> register(
                    "mending_boost",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().mendingBoostRequiredLevel,
                    HandlerResources.MENDING_BOOST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mendingBoostPercent)
            ));
    public static final RegistryObject<Perk> UNBREAKING_MASTERY =
            HandlerCommonConfig.HANDLER.instance().unbreakingMasteryRequiredLevel < 0
            ? null : PERKS.register("unbreaking_mastery", () -> register(
                    "unbreaking_mastery",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().unbreakingMasteryRequiredLevel,
                    HandlerResources.UNBREAKING_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().unbreakingMasteryPercent)
            ));
    public static final RegistryObject<Perk> ENCHANTMENT_STACKING =
            HandlerCommonConfig.HANDLER.instance().enchantmentStackingRequiredLevel < 0
            ? null : PERKS.register("enchantment_stacking", () -> register(
                    "enchantment_stacking",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().enchantmentStackingRequiredLevel,
                    HandlerResources.ENCHANTMENT_STACKING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantmentStackingPercent)
            ));
    public static final RegistryObject<Perk> WISDOM_OF_AGES =
            HandlerCommonConfig.HANDLER.instance().wisdomOfAgesRequiredLevel < 0
            ? null : PERKS.register("wisdom_of_ages", () -> register(
                    "wisdom_of_ages",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().wisdomOfAgesRequiredLevel,
                    HandlerResources.WISDOM_OF_AGES_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().wisdomOfAgesPercent)
            ));
    public static final RegistryObject<Perk> TOME_OF_KNOWLEDGE =
            HandlerCommonConfig.HANDLER.instance().tomeOfKnowledgeRequiredLevel < 0
            ? null : PERKS.register("tome_of_knowledge", () -> register(
                    "tome_of_knowledge",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().tomeOfKnowledgeRequiredLevel,
                    HandlerResources.TOME_OF_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().tomeOfKnowledgePercent)
            ));
    public static final RegistryObject<Perk> RUNIC_ENCHANTMENT =
            HandlerCommonConfig.HANDLER.instance().runicEnchantmentRequiredLevel < 0
            ? null : PERKS.register("runic_enchantment", () -> register(
                    "runic_enchantment",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().runicEnchantmentRequiredLevel,
                    HandlerResources.RUNIC_ENCHANTMENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicEnchantmentPercent)
            ));
    public static final RegistryObject<Perk> APOTHEOSIS_WISDOM =
            !ApotheosisIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().apotheosisWisdomRequiredLevel < 0
            ? null : PERKS.register("apotheosis_wisdom", () -> register(
                    "apotheosis_wisdom",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().apotheosisWisdomRequiredLevel,
                    HandlerResources.APOTHEOSIS_WISDOM_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().apotheosisWisdomAmplifier)
            ));
    public static final RegistryObject<Perk> SCROLL_SCRIBE =
            HandlerCommonConfig.HANDLER.instance().scrollScribeRequiredLevel < 0
            ? null : PERKS.register("scroll_scribe", () -> register(
                    "scroll_scribe",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().scrollScribeRequiredLevel,
                    HandlerResources.SCROLL_SCRIBE_PERK
            ));
    public static final RegistryObject<Perk> MYSTIC_ATTUNEMENT =
            HandlerCommonConfig.HANDLER.instance().mysticAttunementRequiredLevel < 0
            ? null : PERKS.register("mystic_attunement", () -> register(
                    "mystic_attunement",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().mysticAttunementRequiredLevel,
                    HandlerResources.MYSTIC_ATTUNEMENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mysticAttunementPercent)
            ));
    public static final RegistryObject<Perk> SOUL_BINDING =
            HandlerCommonConfig.HANDLER.instance().soulBindingRequiredLevel < 0
            ? null : PERKS.register("soul_binding", () -> register(
                    "soul_binding",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().soulBindingRequiredLevel,
                    HandlerResources.SOUL_BINDING_PERK
            ));
    public static final RegistryObject<Perk> EXPERIENCED_ENCHANTER =
            HandlerCommonConfig.HANDLER.instance().experiencedEnchanterRequiredLevel < 0
            ? null : PERKS.register("experienced_enchanter", () -> register(
                    "experienced_enchanter",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().experiencedEnchanterRequiredLevel,
                    HandlerResources.EXPERIENCED_ENCHANTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().experiencedEnchanterPercent)
            ));
    public static final RegistryObject<Perk> BLOOD_INSCRIPTION =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().bloodInscriptionRequiredLevel < 0
            ? null : PERKS.register("blood_inscription", () -> register(
                    "blood_inscription",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().bloodInscriptionRequiredLevel,
                    HandlerResources.BLOOD_INSCRIPTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodInscriptionPercent)
            ));
    public static final RegistryObject<Perk> ARCANE_LINGUIST =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().arcaneLinguistRequiredLevel < 0
            ? null : PERKS.register("arcane_linguist", () -> register(
                    "arcane_linguist",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().arcaneLinguistRequiredLevel,
                    HandlerResources.ARCANE_LINGUIST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arcaneLinguistPercent)
            ));
    public static final RegistryObject<Perk> WARD_MASTER =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().wardMasterRequiredLevel < 0
            ? null : PERKS.register("ward_master", () -> register(
                    "ward_master",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().wardMasterRequiredLevel,
                    HandlerResources.WARD_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().wardMasterPercent)
            ));
    public static final RegistryObject<Perk> DIMENSIONAL_WISDOM =
            HandlerCommonConfig.HANDLER.instance().dimensionalWisdomRequiredLevel < 0
            ? null : PERKS.register("dimensional_wisdom", () -> register(
                    "dimensional_wisdom",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().dimensionalWisdomRequiredLevel,
                    HandlerResources.DIMENSIONAL_WISDOM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dimensionalWisdomPercent)
            ));
    public static final RegistryObject<Perk> ANCIENT_INSCRIPTIONS =
            HandlerCommonConfig.HANDLER.instance().ancientInscriptionsRequiredLevel < 0
            ? null : PERKS.register("ancient_inscriptions", () -> register(
                    "ancient_inscriptions",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().ancientInscriptionsRequiredLevel,
                    HandlerResources.ANCIENT_INSCRIPTIONS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ancientInscriptionsPercent)
            ));
    public static final RegistryObject<Perk> ARS_SAVANT =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().arsSavantRequiredLevel < 0
            ? null : PERKS.register("ars_savant", () -> register(
                    "ars_savant",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().arsSavantRequiredLevel,
                    HandlerResources.ARS_SAVANT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsSavantPercent)
            ));
    public static final RegistryObject<Perk> NATURE_SAGE =
            !NaturesAuraIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().natureSageRequiredLevel < 0
            ? null : PERKS.register("nature_sage", () -> register(
                    "nature_sage",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().natureSageRequiredLevel,
                    HandlerResources.NATURE_SAGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().natureSagePercent)
            ));
    public static final RegistryObject<Perk> ENIGMATIC_UNDERSTANDING =
            !EnigmaticLegacyIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().enigmaticUnderstandingRequiredLevel < 0
            ? null : PERKS.register("enigmatic_understanding", () -> register(
                    "enigmatic_understanding",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().enigmaticUnderstandingRequiredLevel,
                    HandlerResources.ENIGMATIC_UNDERSTANDING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enigmaticUnderstandingPercent)
            ));
    public static final RegistryObject<Perk> SPELL_INSCRIPTION =
            HandlerCommonConfig.HANDLER.instance().spellInscriptionRequiredLevel < 0
            ? null : PERKS.register("spell_inscription", () -> register(
                    "spell_inscription",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().spellInscriptionRequiredLevel,
                    HandlerResources.SPELL_INSCRIPTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellInscriptionPercent)
            ));
    public static final RegistryObject<Perk> ELDER_KNOWLEDGE =
            HandlerCommonConfig.HANDLER.instance().elderKnowledgeRequiredLevel < 0
            ? null : PERKS.register("elder_knowledge", () -> register(
                    "elder_knowledge",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().elderKnowledgeRequiredLevel,
                    HandlerResources.ELDER_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().elderKnowledgePercent)
            ));
    public static final RegistryObject<Perk> SACRED_GEOMETRY =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().sacredGeometryRequiredLevel < 0
            ? null : PERKS.register("sacred_geometry", () -> register(
                    "sacred_geometry",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().sacredGeometryRequiredLevel,
                    HandlerResources.SACRED_GEOMETRY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sacredGeometryPercent)
            ));
    public static final RegistryObject<Perk> BOOKCRAFT =
            HandlerCommonConfig.HANDLER.instance().bookcraftRequiredLevel < 0
            ? null : PERKS.register("bookcraft", () -> register(
                    "bookcraft",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().bookcraftRequiredLevel,
                    HandlerResources.BOOKCRAFT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bookcraftPercent)
            ));
    public static final RegistryObject<Perk> MYSTIC_SIGHT =
            HandlerCommonConfig.HANDLER.instance().mysticSightRequiredLevel < 0
            ? null : PERKS.register("mystic_sight", () -> register(
                    "mystic_sight",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().mysticSightRequiredLevel,
                    HandlerResources.MYSTIC_SIGHT_PERK
            ));
    public static final RegistryObject<Perk> LAPIS_CONSERVATION =
            HandlerCommonConfig.HANDLER.instance().lapisConservationRequiredLevel < 0
            ? null : PERKS.register("lapis_conservation", () -> register(
                    "lapis_conservation",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().lapisConservationRequiredLevel,
                    HandlerResources.LAPIS_CONSERVATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lapisConservationPercent)
            ));
    public static final RegistryObject<Perk> ENLIGHTENMENT =
            HandlerCommonConfig.HANDLER.instance().enlightenmentRequiredLevel < 0
            ? null : PERKS.register("enlightenment", () -> register(
                    "enlightenment",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().enlightenmentRequiredLevel,
                    HandlerResources.ENLIGHTENMENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enlightenmentPercent)
            ));
    public static final RegistryObject<Perk> CURSE_BREAKER =
            HandlerCommonConfig.HANDLER.instance().curseBreakerRequiredLevel < 0
            ? null : PERKS.register("curse_breaker", () -> register(
                    "curse_breaker",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().curseBreakerRequiredLevel,
                    HandlerResources.CURSE_BREAKER_PERK
            ));
    public static final RegistryObject<Perk> ENCHANTMENT_AMPLIFIER =
            HandlerCommonConfig.HANDLER.instance().enchantmentAmplifierRequiredLevel < 0
            ? null : PERKS.register("enchantment_amplifier", () -> register(
                    "enchantment_amplifier",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().enchantmentAmplifierRequiredLevel,
                    HandlerResources.ENCHANTMENT_AMPLIFIER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantmentAmplifierPercent)
            ));
    public static final RegistryObject<Perk> RUNE_MASTERY =
            HandlerCommonConfig.HANDLER.instance().runeMasteryRequiredLevel < 0
            ? null : PERKS.register("rune_mastery", () -> register(
                    "rune_mastery",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().runeMasteryRequiredLevel,
                    HandlerResources.RUNE_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runeMasteryPercent)
            ));
    public static final RegistryObject<Perk> DRUIDIC_KNOWLEDGE =
            HandlerCommonConfig.HANDLER.instance().druidicKnowledgeRequiredLevel < 0
            ? null : PERKS.register("druidic_knowledge", () -> register(
                    "druidic_knowledge",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().druidicKnowledgeRequiredLevel,
                    HandlerResources.DRUIDIC_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().druidicKnowledgePercent)
            ));
    public static final RegistryObject<Perk> TEMPORAL_WISDOM =
            HandlerCommonConfig.HANDLER.instance().temporalWisdomRequiredLevel < 0
            ? null : PERKS.register("temporal_wisdom", () -> register(
                    "temporal_wisdom",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().temporalWisdomRequiredLevel,
                    HandlerResources.TEMPORAL_WISDOM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().temporalWisdomPercent)
            ));
    public static final RegistryObject<Perk> GRAND_SAGE =
            HandlerCommonConfig.HANDLER.instance().grandSageRequiredLevel < 0
            ? null : PERKS.register("grand_sage", () -> register(
                    "grand_sage",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().grandSageRequiredLevel,
                    HandlerResources.GRAND_SAGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().grandSagePercent)
            ));

    // ========== NEW PERKS - MAGIC ==========
    public static final RegistryObject<Perk> MANA_REGENERATION =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().manaRegenerationRequiredLevel < 0
            ? null : PERKS.register("mana_regeneration", () -> register(
                    "mana_regeneration",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().manaRegenerationRequiredLevel,
                    HandlerResources.MANA_REGENERATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().manaRegenerationPercent)
            ));
    public static final RegistryObject<Perk> SPELL_AMPLIFIER =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().spellAmplifierRequiredLevel < 0
            ? null : PERKS.register("spell_amplifier", () -> register(
                    "spell_amplifier",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().spellAmplifierRequiredLevel,
                    HandlerResources.SPELL_AMPLIFIER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellAmplifierPercent)
            ));
    public static final RegistryObject<Perk> SOURCE_WELL =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().sourceWellRequiredLevel < 0
            ? null : PERKS.register("source_well", () -> register(
                    "source_well",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().sourceWellRequiredLevel,
                    HandlerResources.SOURCE_WELL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sourceWellPercent)
            ));
    public static final RegistryObject<Perk> BLOOD_CHANNEL =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().bloodChannelRequiredLevel < 0
            ? null : PERKS.register("blood_channel", () -> register(
                    "blood_channel",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().bloodChannelRequiredLevel,
                    HandlerResources.BLOOD_CHANNEL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodChannelPercent)
            ));
    public static final RegistryObject<Perk> POTION_SPLASH =
            HandlerCommonConfig.HANDLER.instance().potionSplashRequiredLevel < 0
            ? null : PERKS.register("potion_splash", () -> register(
                    "potion_splash",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().potionSplashRequiredLevel,
                    HandlerResources.POTION_SPLASH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().potionSplashPercent)
            ));
    public static final RegistryObject<Perk> TELEKINESIS =
            HandlerCommonConfig.HANDLER.instance().telekinesisRequiredLevel < 0
            ? null : PERKS.register("telekinesis", () -> register(
                    "telekinesis",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().telekinesisRequiredLevel,
                    HandlerResources.TELEKINESIS_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().telekinesisAmplifier)
            ));
    public static final RegistryObject<Perk> ELEMENTAL_MASTER =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().elementalMasterRequiredLevel < 0
            ? null : PERKS.register("elemental_master", () -> register(
                    "elemental_master",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().elementalMasterRequiredLevel,
                    HandlerResources.ELEMENTAL_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().elementalMasterPercent)
            ));
    public static final RegistryObject<Perk> ARCANE_BARRIER =
            HandlerCommonConfig.HANDLER.instance().arcaneBarrierRequiredLevel < 0
            ? null : PERKS.register("arcane_barrier", () -> register(
                    "arcane_barrier",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arcaneBarrierRequiredLevel,
                    HandlerResources.ARCANE_BARRIER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().arcaneBarrierAmplifier)
            ));
    public static final RegistryObject<Perk> SPELL_QUICKENING =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().spellQuickeningRequiredLevel < 0
            ? null : PERKS.register("spell_quickening", () -> register(
                    "spell_quickening",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().spellQuickeningRequiredLevel,
                    HandlerResources.SPELL_QUICKENING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellQuickeningPercent)
            ));
    public static final RegistryObject<Perk> SOURCE_ATTUNEMENT =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().sourceAttunementRequiredLevel < 0
            ? null : PERKS.register("source_attunement", () -> register(
                    "source_attunement",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().sourceAttunementRequiredLevel,
                    HandlerResources.SOURCE_ATTUNEMENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sourceAttunementPercent)
            ));
    public static final RegistryObject<Perk> BLOOD_EMPOWER =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().bloodEmpowerRequiredLevel < 0
            ? null : PERKS.register("blood_empower", () -> register(
                    "blood_empower",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().bloodEmpowerRequiredLevel,
                    HandlerResources.BLOOD_EMPOWER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodEmpowerPercent)
            ));
    public static final RegistryObject<Perk> RITUAL_EFFICIENCY =
            !BloodMagicIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().ritualEfficiencyRequiredLevel < 0
            ? null : PERKS.register("ritual_efficiency", () -> register(
                    "ritual_efficiency",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().ritualEfficiencyRequiredLevel,
                    HandlerResources.RITUAL_EFFICIENCY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ritualEfficiencyPercent)
            ));
    public static final RegistryObject<Perk> SUMMONER =
            !ArsNouveauIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().summonerRequiredLevel < 0
            ? null : PERKS.register("summoner", () -> register(
                    "summoner",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().summonerRequiredLevel,
                    HandlerResources.SUMMONER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().summonerPercent)
            ));
    public static final RegistryObject<Perk> MYSTIC_SHIELD =
            HandlerCommonConfig.HANDLER.instance().mysticShieldRequiredLevel < 0
            ? null : PERKS.register("mystic_shield", () -> register(
                    "mystic_shield",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().mysticShieldRequiredLevel,
                    HandlerResources.MYSTIC_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mysticShieldPercent)
            ));
    public static final RegistryObject<Perk> ASTRAL_PROJECTION =
            HandlerCommonConfig.HANDLER.instance().astralProjectionRequiredLevel < 0
            ? null : PERKS.register("astral_projection", () -> register(
                    "astral_projection",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().astralProjectionRequiredLevel,
                    HandlerResources.ASTRAL_PROJECTION_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().astralProjectionAmplifier)
            ));
    public static final RegistryObject<Perk> PHILOSOPHERS_STONE =
            HandlerCommonConfig.HANDLER.instance().philosophersStoneRequiredLevel < 0
            ? null : PERKS.register("philosophers_stone", () -> register(
                    "philosophers_stone",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().philosophersStoneRequiredLevel,
                    HandlerResources.PHILOSOPHERS_STONE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().philosophersStonePercent)
            ));
    public static final RegistryObject<Perk> MANA_SHIELD =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().manaShieldRequiredLevel < 0
            ? null : PERKS.register("mana_shield", () -> register(
                    "mana_shield",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().manaShieldRequiredLevel,
                    HandlerResources.MANA_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().manaShieldPercent)
            ));
    public static final RegistryObject<Perk> DRAGON_MAGIC =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().dragonMagicRequiredLevel < 0
            ? null : PERKS.register("dragon_magic", () -> register(
                    "dragon_magic",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().dragonMagicRequiredLevel,
                    HandlerResources.DRAGON_MAGIC_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonMagicPercent)
            ));
    public static final RegistryObject<Perk> ELDRITCH_POWER =
            !IronsSpellbooksIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().eldritchPowerRequiredLevel < 0
            ? null : PERKS.register("eldritch_power", () -> register(
                    "eldritch_power",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().eldritchPowerRequiredLevel,
                    HandlerResources.ELDRITCH_POWER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().eldritchPowerPercent)
            ));
    public static final RegistryObject<Perk> SOUL_MAGIC =
            HandlerCommonConfig.HANDLER.instance().soulMagicRequiredLevel < 0
            ? null : PERKS.register("soul_magic", () -> register(
                    "soul_magic",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().soulMagicRequiredLevel,
                    HandlerResources.SOUL_MAGIC_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().soulMagicPercent)
            ));
    public static final RegistryObject<Perk> DUAL_CASTING =
            HandlerCommonConfig.HANDLER.instance().dualCastingRequiredLevel < 0
            ? null : PERKS.register("dual_casting", () -> register(
                    "dual_casting",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().dualCastingRequiredLevel,
                    HandlerResources.DUAL_CASTING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dualCastingPercent)
            ));
    public static final RegistryObject<Perk> ENCHANTED_MISSILES =
            HandlerCommonConfig.HANDLER.instance().enchantedMissilesRequiredLevel < 0
            ? null : PERKS.register("enchanted_missiles", () -> register(
                    "enchanted_missiles",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().enchantedMissilesRequiredLevel,
                    HandlerResources.ENCHANTED_MISSILES_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantedMissilesPercent)
            ));
    public static final RegistryObject<Perk> AURA_MANIPULATION =
            !NaturesAuraIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().auraManipulationRequiredLevel < 0
            ? null : PERKS.register("aura_manipulation", () -> register(
                    "aura_manipulation",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().auraManipulationRequiredLevel,
                    HandlerResources.AURA_MANIPULATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().auraManipulationPercent)
            ));
    public static final RegistryObject<Perk> VOID_MAGIC =
            HandlerCommonConfig.HANDLER.instance().voidMagicRequiredLevel < 0
            ? null : PERKS.register("void_magic", () -> register(
                    "void_magic",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().voidMagicRequiredLevel,
                    HandlerResources.VOID_MAGIC_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().voidMagicPercent)
            ));

    // ========== NEW PERKS - FORTUNE ==========
    public static final RegistryObject<Perk> TREASURE_SENSE =
            HandlerCommonConfig.HANDLER.instance().treasureSenseRequiredLevel < 0
            ? null : PERKS.register("treasure_sense", () -> register(
                    "treasure_sense",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().treasureSenseRequiredLevel,
                    HandlerResources.TREASURE_SENSE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().treasureSensePercent)
            ));
    public static final RegistryObject<Perk> DOUBLE_DOWN =
            HandlerCommonConfig.HANDLER.instance().doubleDownRequiredLevel < 0
            ? null : PERKS.register("double_down", () -> register(
                    "double_down",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().doubleDownRequiredLevel,
                    HandlerResources.DOUBLE_DOWN_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().doubleDownPercent)
            ));
    public static final RegistryObject<Perk> GOLDEN_TOUCH =
            HandlerCommonConfig.HANDLER.instance().goldenTouchRequiredLevel < 0
            ? null : PERKS.register("golden_touch", () -> register(
                    "golden_touch",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().goldenTouchRequiredLevel,
                    HandlerResources.GOLDEN_TOUCH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().goldenTouchPercent)
            ));
    public static final RegistryObject<Perk> FORTUNES_FAVOR =
            HandlerCommonConfig.HANDLER.instance().fortunesFavorRequiredLevel < 0
            ? null : PERKS.register("fortunes_favor", () -> register(
                    "fortunes_favor",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().fortunesFavorRequiredLevel,
                    HandlerResources.FORTUNES_FAVOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fortunesFavorPercent)
            ));
    public static final RegistryObject<Perk> LUCKY_FISHING =
            HandlerCommonConfig.HANDLER.instance().luckyFishingRequiredLevel < 0
            ? null : PERKS.register("lucky_fishing", () -> register(
                    "lucky_fishing",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyFishingRequiredLevel,
                    HandlerResources.LUCKY_FISHING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().luckyFishingPercent)
            ));
    public static final RegistryObject<Perk> PROSPECTORS_LUCK =
            HandlerCommonConfig.HANDLER.instance().prospectorsLuckRequiredLevel < 0
            ? null : PERKS.register("prospectors_luck", () -> register(
                    "prospectors_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().prospectorsLuckRequiredLevel,
                    HandlerResources.PROSPECTORS_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().prospectorsLuckPercent)
            ));
    public static final RegistryObject<Perk> SCAVENGER =
            HandlerCommonConfig.HANDLER.instance().scavengerRequiredLevel < 0
            ? null : PERKS.register("scavenger", () -> register(
                    "scavenger",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().scavengerRequiredLevel,
                    HandlerResources.SCAVENGER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().scavengerPercent)
            ));
    public static final RegistryObject<Perk> CRITICAL_MASTERY =
            HandlerCommonConfig.HANDLER.instance().criticalMasteryRequiredLevel < 0
            ? null : PERKS.register("critical_mastery", () -> register(
                    "critical_mastery",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().criticalMasteryRequiredLevel,
                    HandlerResources.CRITICAL_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().criticalMasteryPercent)
            ));
    public static final RegistryObject<Perk> LOOTER =
            HandlerCommonConfig.HANDLER.instance().looterRequiredLevel < 0
            ? null : PERKS.register("looter", () -> register(
                    "looter",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().looterRequiredLevel,
                    HandlerResources.LOOTER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().looterAmplifier)
            ));
    public static final RegistryObject<Perk> JACKPOT =
            HandlerCommonConfig.HANDLER.instance().jackpotRequiredLevel < 0
            ? null : PERKS.register("jackpot", () -> register(
                    "jackpot",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().jackpotRequiredLevel,
                    HandlerResources.JACKPOT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().jackpotPercent)
            ));
    public static final RegistryObject<Perk> ENCHANTED_FORTUNE =
            HandlerCommonConfig.HANDLER.instance().enchantedFortuneRequiredLevel < 0
            ? null : PERKS.register("enchanted_fortune", () -> register(
                    "enchanted_fortune",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().enchantedFortuneRequiredLevel,
                    HandlerResources.ENCHANTED_FORTUNE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantedFortunePercent)
            ));
    public static final RegistryObject<Perk> DRAGON_HOARD =
            !IceAndFireIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().dragonHoardRequiredLevel < 0
            ? null : PERKS.register("dragon_hoard", () -> register(
                    "dragon_hoard",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().dragonHoardRequiredLevel,
                    HandlerResources.DRAGON_HOARD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonHoardPercent)
            ));
    public static final RegistryObject<Perk> CATACLYSM_SPOILS =
            !CataclysmIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().cataclysmSpoilsRequiredLevel < 0
            ? null : PERKS.register("cataclysm_spoils", () -> register(
                    "cataclysm_spoils",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().cataclysmSpoilsRequiredLevel,
                    HandlerResources.CATACLYSM_SPOILS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().cataclysmSpoilsPercent)
            ));
    public static final RegistryObject<Perk> RUNIC_FORTUNE =
            HandlerCommonConfig.HANDLER.instance().runicFortuneRequiredLevel < 0
            ? null : PERKS.register("runic_fortune", () -> register(
                    "runic_fortune",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().runicFortuneRequiredLevel,
                    HandlerResources.RUNIC_FORTUNE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicFortunePercent)
            ));
    public static final RegistryObject<Perk> APOTHEOSIS_GEMS =
            !ApotheosisIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().apotheosisGemsRequiredLevel < 0
            ? null : PERKS.register("apotheosis_gems", () -> register(
                    "apotheosis_gems",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().apotheosisGemsRequiredLevel,
                    HandlerResources.APOTHEOSIS_GEMS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().apotheosisGemsPercent)
            ));
    public static final RegistryObject<Perk> LUCKY_CHARM =
            HandlerCommonConfig.HANDLER.instance().luckyCharmRequiredLevel < 0
            ? null : PERKS.register("lucky_charm", () -> register(
                    "lucky_charm",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyCharmRequiredLevel,
                    HandlerResources.LUCKY_CHARM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().luckyCharmPercent)
            ));
    public static final RegistryObject<Perk> COIN_FLIP =
            HandlerCommonConfig.HANDLER.instance().coinFlipRequiredLevel < 0
            ? null : PERKS.register("coin_flip", () -> register(
                    "coin_flip",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().coinFlipRequiredLevel,
                    HandlerResources.COIN_FLIP_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().coinFlipPercent)
            ));
    public static final RegistryObject<Perk> SALVAGE_LUCK =
            HandlerCommonConfig.HANDLER.instance().salvageLuckRequiredLevel < 0
            ? null : PERKS.register("salvage_luck", () -> register(
                    "salvage_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().salvageLuckRequiredLevel,
                    HandlerResources.SALVAGE_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().salvageLuckPercent)
            ));
    public static final RegistryObject<Perk> ADVENTURERS_LUCK =
            !StalwartDungeonsIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().adventurersLuckRequiredLevel < 0
            ? null : PERKS.register("adventurers_luck", () -> register(
                    "adventurers_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().adventurersLuckRequiredLevel,
                    HandlerResources.ADVENTURERS_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().adventurersLuckPercent)
            ));
    public static final RegistryObject<Perk> MIDAS_TOUCH =
            HandlerCommonConfig.HANDLER.instance().midasTouchRequiredLevel < 0
            ? null : PERKS.register("midas_touch", () -> register(
                    "midas_touch",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().midasTouchRequiredLevel,
                    HandlerResources.MIDAS_TOUCH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().midasTouchPercent)
            ));
    public static final RegistryObject<Perk> LUCKY_BREAK =
            HandlerCommonConfig.HANDLER.instance().luckyBreakRequiredLevel < 0
            ? null : PERKS.register("lucky_break", () -> register(
                    "lucky_break",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyBreakRequiredLevel,
                    HandlerResources.LUCKY_BREAK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().luckyBreakPercent)
            ));
    public static final RegistryObject<Perk> JEWELERS_EYE =
            HandlerCommonConfig.HANDLER.instance().jewelersEyeRequiredLevel < 0
            ? null : PERKS.register("jewelers_eye", () -> register(
                    "jewelers_eye",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().jewelersEyeRequiredLevel,
                    HandlerResources.JEWELERS_EYE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().jewelersEyePercent)
            ));
    public static final RegistryObject<Perk> FORTUNE_COOKIE =
            !FarmersDelightIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().fortuneCookieRequiredLevel < 0
            ? null : PERKS.register("fortune_cookie", () -> register(
                    "fortune_cookie",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().fortuneCookieRequiredLevel,
                    HandlerResources.FORTUNE_COOKIE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fortuneCookiePercent)
            ));
    public static final RegistryObject<Perk> ETHEREAL_LUCK =
            HandlerCommonConfig.HANDLER.instance().etherealLuckRequiredLevel < 0
            ? null : PERKS.register("ethereal_luck", () -> register(
                    "ethereal_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().etherealLuckRequiredLevel,
                    HandlerResources.ETHEREAL_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().etherealLuckPercent)
            ));
    public static final RegistryObject<Perk> RARE_FIND =
            HandlerCommonConfig.HANDLER.instance().rareFindRequiredLevel < 0
            ? null : PERKS.register("rare_find", () -> register(
                    "rare_find",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().rareFindRequiredLevel,
                    HandlerResources.RARE_FIND_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().rareFindPercent)
            ));
    public static final RegistryObject<Perk> LUCKY_STAR =
            HandlerCommonConfig.HANDLER.instance().luckyStarRequiredLevel < 0
            ? null : PERKS.register("lucky_star", () -> register(
                    "lucky_star",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyStarRequiredLevel,
                    HandlerResources.LUCKY_STAR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().luckyStarPercent)
            ));
    public static final RegistryObject<Perk> SERENDIPITY =
            HandlerCommonConfig.HANDLER.instance().serendipityRequiredLevel < 0
            ? null : PERKS.register("serendipity", () -> register(
                    "serendipity",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().serendipityRequiredLevel,
                    HandlerResources.SERENDIPITY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().serendipityPercent)
            ));
    public static final RegistryObject<Perk> GREED =
            HandlerCommonConfig.HANDLER.instance().greedRequiredLevel < 0
            ? null : PERKS.register("greed", () -> register(
                    "greed",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().greedRequiredLevel,
                    HandlerResources.GREED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().greedPercent)
            ));
    public static final RegistryObject<Perk> RAINBOW_LOOT =
            HandlerCommonConfig.HANDLER.instance().rainbowLootRequiredLevel < 0
            ? null : PERKS.register("rainbow_loot", () -> register(
                    "rainbow_loot",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().rainbowLootRequiredLevel,
                    HandlerResources.RAINBOW_LOOT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().rainbowLootPercent)
            ));
    public static final RegistryObject<Perk> FISHERMANS_LUCK =
            HandlerCommonConfig.HANDLER.instance().fishermansLuckRequiredLevel < 0
            ? null : PERKS.register("fishermans_luck", () -> register(
                    "fishermans_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().fishermansLuckRequiredLevel,
                    HandlerResources.FISHERMANS_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fishermansLuckPercent)
            ));
    public static final RegistryObject<Perk> LUCKY_EXPLORER =
            HandlerCommonConfig.HANDLER.instance().luckyExplorerRequiredLevel < 0
            ? null : PERKS.register("lucky_explorer", () -> register(
                    "lucky_explorer",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyExplorerRequiredLevel,
                    HandlerResources.LUCKY_EXPLORER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().luckyExplorerPercent)
            ));
    public static final RegistryObject<Perk> CHAOS_ROLL =
            HandlerCommonConfig.HANDLER.instance().chaosRollRequiredLevel < 0
            ? null : PERKS.register("chaos_roll", () -> register(
                    "chaos_roll",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().chaosRollRequiredLevel,
                    HandlerResources.CHAOS_ROLL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().chaosRollPercent)
            ));
    public static final RegistryObject<Perk> CRITICAL_FORTUNE =
            HandlerCommonConfig.HANDLER.instance().criticalFortuneRequiredLevel < 0
            ? null : PERKS.register("critical_fortune", () -> register(
                    "critical_fortune",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().criticalFortuneRequiredLevel,
                    HandlerResources.CRITICAL_FORTUNE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().criticalFortunePercent)
            ));
    public static final RegistryObject<Perk> MASTER_LOOTER =
            HandlerCommonConfig.HANDLER.instance().masterLooterRequiredLevel < 0
            ? null : PERKS.register("master_looter", () -> register(
                    "master_looter",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().masterLooterRequiredLevel,
                    HandlerResources.MASTER_LOOTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterLooterPercent)
            ));
    public static final RegistryObject<Perk> ARTIFACT_HUNTER =
            !EnigmaticLegacyIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().artifactHunterRequiredLevel < 0
            ? null : PERKS.register("artifact_hunter", () -> register(
                    "artifact_hunter",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().artifactHunterRequiredLevel,
                    HandlerResources.ARTIFACT_HUNTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().artifactHunterPercent)
            ));
    public static final RegistryObject<Perk> BLESSING_OF_LUCK =
            HandlerCommonConfig.HANDLER.instance().blessingOfLuckRequiredLevel < 0
            ? null : PERKS.register("blessing_of_luck", () -> register(
                    "blessing_of_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().blessingOfLuckRequiredLevel,
                    HandlerResources.BLESSING_OF_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().blessingOfLuckPercent)
            ));

    // ========== NEW PERKS - TINKERING ==========
    public static final RegistryObject<Perk> REPAIR_EXPERT =
            HandlerCommonConfig.HANDLER.instance().repairExpertRequiredLevel < 0
            ? null : PERKS.register("repair_expert", () -> register(
                    "repair_expert",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().repairExpertRequiredLevel,
                    HandlerResources.REPAIR_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().repairExpertPercent)
            ));
    public static final RegistryObject<Perk> DISASSEMBLER =
            HandlerCommonConfig.HANDLER.instance().disassemblerRequiredLevel < 0
            ? null : PERKS.register("disassembler", () -> register(
                    "disassembler",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().disassemblerRequiredLevel,
                    HandlerResources.DISASSEMBLER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().disassemblerPercent)
            ));
    public static final RegistryObject<Perk> AUTO_REPAIR =
            HandlerCommonConfig.HANDLER.instance().autoRepairRequiredLevel < 0
            ? null : PERKS.register("auto_repair", () -> register(
                    "auto_repair",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().autoRepairRequiredLevel,
                    HandlerResources.AUTO_REPAIR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().autoRepairPercent)
            ));
    public static final RegistryObject<Perk> GADGETEER =
            HandlerCommonConfig.HANDLER.instance().gadgeteerRequiredLevel < 0
            ? null : PERKS.register("gadgeteer", () -> register(
                    "gadgeteer",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().gadgeteerRequiredLevel,
                    HandlerResources.GADGETEER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().gadgeteerPercent)
            ));
    public static final RegistryObject<Perk> TRAP_MAKER =
            HandlerCommonConfig.HANDLER.instance().trapMakerRequiredLevel < 0
            ? null : PERKS.register("trap_maker", () -> register(
                    "trap_maker",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().trapMakerRequiredLevel,
                    HandlerResources.TRAP_MAKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().trapMakerPercent)
            ));
    public static final RegistryObject<Perk> LOCK_EXPERT =
            HandlerCommonConfig.HANDLER.instance().lockExpertRequiredLevel < 0
            ? null : PERKS.register("lock_expert", () -> register(
                    "lock_expert",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().lockExpertRequiredLevel,
                    HandlerResources.LOCK_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lockExpertPercent)
            ));
    public static final RegistryObject<Perk> KEY_FORGE =
            HandlerCommonConfig.HANDLER.instance().keyForgeRequiredLevel < 0
            ? null : PERKS.register("key_forge", () -> register(
                    "key_forge",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().keyForgeRequiredLevel,
                    HandlerResources.KEY_FORGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().keyForgePercent)
            ));
    public static final RegistryObject<Perk> MECHANICAL_KNOWLEDGE =
            HandlerCommonConfig.HANDLER.instance().mechanicalKnowledgeRequiredLevel < 0
            ? null : PERKS.register("mechanical_knowledge", () -> register(
                    "mechanical_knowledge",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().mechanicalKnowledgeRequiredLevel,
                    HandlerResources.MECHANICAL_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mechanicalKnowledgePercent)
            ));
    public static final RegistryObject<Perk> SIEGE_MECHANIC =
            HandlerCommonConfig.HANDLER.instance().siegeMechanicRequiredLevel < 0
            ? null : PERKS.register("siege_mechanic", () -> register(
                    "siege_mechanic",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().siegeMechanicRequiredLevel,
                    HandlerResources.SIEGE_MECHANIC_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().siegeMechanicPercent)
            ));
    public static final RegistryObject<Perk> WEAPON_SMITH =
            HandlerCommonConfig.HANDLER.instance().weaponSmithRequiredLevel < 0
            ? null : PERKS.register("weapon_smith", () -> register(
                    "weapon_smith",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().weaponSmithRequiredLevel,
                    HandlerResources.WEAPON_SMITH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().weaponSmithPercent)
            ));
    public static final RegistryObject<Perk> ARMOR_SMITH =
            HandlerCommonConfig.HANDLER.instance().armorSmithRequiredLevel < 0
            ? null : PERKS.register("armor_smith", () -> register(
                    "armor_smith",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().armorSmithRequiredLevel,
                    HandlerResources.ARMOR_SMITH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().armorSmithPercent)
            ));
    public static final RegistryObject<Perk> TOOL_SMITH =
            HandlerCommonConfig.HANDLER.instance().toolSmithRequiredLevel < 0
            ? null : PERKS.register("tool_smith", () -> register(
                    "tool_smith",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().toolSmithRequiredLevel,
                    HandlerResources.TOOL_SMITH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().toolSmithPercent)
            ));
    public static final RegistryObject<Perk> SALVAGE_MASTER =
            HandlerCommonConfig.HANDLER.instance().salvageMasterRequiredLevel < 0
            ? null : PERKS.register("salvage_master", () -> register(
                    "salvage_master",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().salvageMasterRequiredLevel,
                    HandlerResources.SALVAGE_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().salvageMasterPercent)
            ));
    public static final RegistryObject<Perk> ENCHANTMENT_TRANSFER =
            HandlerCommonConfig.HANDLER.instance().enchantmentTransferRequiredLevel < 0
            ? null : PERKS.register("enchantment_transfer", () -> register(
                    "enchantment_transfer",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().enchantmentTransferRequiredLevel,
                    HandlerResources.ENCHANTMENT_TRANSFER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantmentTransferPercent)
            ));
    public static final RegistryObject<Perk> GADGET_UPGRADE =
            HandlerCommonConfig.HANDLER.instance().gadgetUpgradeRequiredLevel < 0
            ? null : PERKS.register("gadget_upgrade", () -> register(
                    "gadget_upgrade",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().gadgetUpgradeRequiredLevel,
                    HandlerResources.GADGET_UPGRADE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().gadgetUpgradePercent)
            ));
    public static final RegistryObject<Perk> OVERCLOCK =
            HandlerCommonConfig.HANDLER.instance().overclockRequiredLevel < 0
            ? null : PERKS.register("overclock", () -> register(
                    "overclock",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().overclockRequiredLevel,
                    HandlerResources.OVERCLOCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().overclockPercent)
            ));
    public static final RegistryObject<Perk> RUNIC_ENGINEERING =
            HandlerCommonConfig.HANDLER.instance().runicEngineeringRequiredLevel < 0
            ? null : PERKS.register("runic_engineering", () -> register(
                    "runic_engineering",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().runicEngineeringRequiredLevel,
                    HandlerResources.RUNIC_ENGINEERING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicEngineeringPercent)
            ));
    public static final RegistryObject<Perk> BREWING_APPARATUS =
            HandlerCommonConfig.HANDLER.instance().brewingApparatusRequiredLevel < 0
            ? null : PERKS.register("brewing_apparatus", () -> register(
                    "brewing_apparatus",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().brewingApparatusRequiredLevel,
                    HandlerResources.BREWING_APPARATUS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().brewingApparatusPercent)
            ));
    public static final RegistryObject<Perk> MECHANICAL_ARM =
            HandlerCommonConfig.HANDLER.instance().mechanicalArmRequiredLevel < 0
            ? null : PERKS.register("mechanical_arm", () -> register(
                    "mechanical_arm",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().mechanicalArmRequiredLevel,
                    HandlerResources.MECHANICAL_ARM_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().mechanicalArmAmplifier)
            ));
    public static final RegistryObject<Perk> PRECISION_TOOLS =
            HandlerCommonConfig.HANDLER.instance().precisionToolsRequiredLevel < 0
            ? null : PERKS.register("precision_tools", () -> register(
                    "precision_tools",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().precisionToolsRequiredLevel,
                    HandlerResources.PRECISION_TOOLS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().precisionToolsPercent)
            ));
    public static final RegistryObject<Perk> ASSEMBLY_LINE =
            HandlerCommonConfig.HANDLER.instance().assemblyLineRequiredLevel < 0
            ? null : PERKS.register("assembly_line", () -> register(
                    "assembly_line",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().assemblyLineRequiredLevel,
                    HandlerResources.ASSEMBLY_LINE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().assemblyLinePercent)
            ));
    public static final RegistryObject<Perk> EXPLOSIVE_ORDINANCE =
            HandlerCommonConfig.HANDLER.instance().explosiveOrdinanceRequiredLevel < 0
            ? null : PERKS.register("explosive_ordinance", () -> register(
                    "explosive_ordinance",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().explosiveOrdinanceRequiredLevel,
                    HandlerResources.EXPLOSIVE_ORDINANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().explosiveOrdinancePercent)
            ));
    public static final RegistryObject<Perk> CIRCUIT_BREAKER =
            HandlerCommonConfig.HANDLER.instance().circuitBreakerRequiredLevel < 0
            ? null : PERKS.register("circuit_breaker", () -> register(
                    "circuit_breaker",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().circuitBreakerRequiredLevel,
                    HandlerResources.CIRCUIT_BREAKER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().circuitBreakerAmplifier)
            ));
    public static final RegistryObject<Perk> MODULAR_EQUIPMENT =
            HandlerCommonConfig.HANDLER.instance().modularEquipmentRequiredLevel < 0
            ? null : PERKS.register("modular_equipment", () -> register(
                    "modular_equipment",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().modularEquipmentRequiredLevel,
                    HandlerResources.MODULAR_EQUIPMENT_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().modularEquipmentAmplifier)
            ));
    public static final RegistryObject<Perk> CLOCKWORK_MASTERY =
            HandlerCommonConfig.HANDLER.instance().clockworkMasteryRequiredLevel < 0
            ? null : PERKS.register("clockwork_mastery", () -> register(
                    "clockwork_mastery",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().clockworkMasteryRequiredLevel,
                    HandlerResources.CLOCKWORK_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().clockworkMasteryPercent)
            ));
    public static final RegistryObject<Perk> FORGE_MASTER =
            HandlerCommonConfig.HANDLER.instance().forgeMasterRequiredLevel < 0
            ? null : PERKS.register("forge_master", () -> register(
                    "forge_master",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().forgeMasterRequiredLevel,
                    HandlerResources.FORGE_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().forgeMasterPercent)
            ));
    public static final RegistryObject<Perk> INVENTOR =
            HandlerCommonConfig.HANDLER.instance().inventorRequiredLevel < 0
            ? null : PERKS.register("inventor", () -> register(
                    "inventor",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().inventorRequiredLevel,
                    HandlerResources.INVENTOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().inventorPercent)
            ));
    public static final RegistryObject<Perk> SPRING_LOADED =
            HandlerCommonConfig.HANDLER.instance().springLoadedRequiredLevel < 0
            ? null : PERKS.register("spring_loaded", () -> register(
                    "spring_loaded",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().springLoadedRequiredLevel,
                    HandlerResources.SPRING_LOADED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().springLoadedPercent)
            ));
    public static final RegistryObject<Perk> BALLISTIC_EXPERT =
            HandlerCommonConfig.HANDLER.instance().ballisticExpertRequiredLevel < 0
            ? null : PERKS.register("ballistic_expert", () -> register(
                    "ballistic_expert",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().ballisticExpertRequiredLevel,
                    HandlerResources.BALLISTIC_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ballisticExpertPercent)
            ));
    public static final RegistryObject<Perk> SAFE_BUILDER =
            HandlerCommonConfig.HANDLER.instance().safeBuilderRequiredLevel < 0
            ? null : PERKS.register("safe_builder", () -> register(
                    "safe_builder",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().safeBuilderRequiredLevel,
                    HandlerResources.SAFE_BUILDER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().safeBuilderPercent)
            ));
    public static final RegistryObject<Perk> TINKERS_TOUCH =
            HandlerCommonConfig.HANDLER.instance().tinkersTouchRequiredLevel < 0
            ? null : PERKS.register("tinkers_touch", () -> register(
                    "tinkers_touch",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().tinkersTouchRequiredLevel,
                    HandlerResources.TINKERS_TOUCH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().tinkersTouchPercent)
            ));
    public static final RegistryObject<Perk> ALLOY_MASTER =
            HandlerCommonConfig.HANDLER.instance().alloyMasterRequiredLevel < 0
            ? null : PERKS.register("alloy_master", () -> register(
                    "alloy_master",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().alloyMasterRequiredLevel,
                    HandlerResources.ALLOY_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().alloyMasterPercent)
            ));
    public static final RegistryObject<Perk> MECHANISM_MASTERY =
            HandlerCommonConfig.HANDLER.instance().mechanismMasteryRequiredLevel < 0
            ? null : PERKS.register("mechanism_mastery", () -> register(
                    "mechanism_mastery",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().mechanismMasteryRequiredLevel,
                    HandlerResources.MECHANISM_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mechanismMasteryPercent)
            ));
    public static final RegistryObject<Perk> POWER_TOOLS =
            HandlerCommonConfig.HANDLER.instance().powerToolsRequiredLevel < 0
            ? null : PERKS.register("power_tools", () -> register(
                    "power_tools",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().powerToolsRequiredLevel,
                    HandlerResources.POWER_TOOLS_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().powerToolsAmplifier)
            ));
    public static final RegistryObject<Perk> BACKPACK_ENGINEER =
            HandlerCommonConfig.HANDLER.instance().backpackEngineerRequiredLevel < 0
            ? null : PERKS.register("backpack_engineer", () -> register(
                    "backpack_engineer",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().backpackEngineerRequiredLevel,
                    HandlerResources.BACKPACK_ENGINEER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().backpackEngineerAmplifier)
            ));
    public static final RegistryObject<Perk> WAYSTONE_TINKER =
            HandlerCommonConfig.HANDLER.instance().waystoneTinkerRequiredLevel < 0
            ? null : PERKS.register("waystone_tinker", () -> register(
                    "waystone_tinker",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().waystoneTinkerRequiredLevel,
                    HandlerResources.WAYSTONE_TINKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().waystoneTinkerPercent)
            ));
    public static final RegistryObject<Perk> MASTER_ARTIFICER =
            HandlerCommonConfig.HANDLER.instance().masterArtificerRequiredLevel < 0
            ? null : PERKS.register("master_artificer", () -> register(
                    "master_artificer",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().masterArtificerRequiredLevel,
                    HandlerResources.MASTER_ARTIFICER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterArtificerPercent)
            ));

    // ═════════════════════════════════════════════════════════════════════════
    //  Botania Integration — Perks (all conditionally null when Botania absent)
    // ═════════════════════════════════════════════════════════════════════════

    // ── WISDOM tree — Low tier (Elemental / Rune-of-Mana entries) ──
    public static final RegistryObject<Perk> BOTANIA_PETAL_READER =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaPetalReaderRequiredLevel < 0
            ? null : PERKS.register("botania_petal_reader", () -> register(
                    "botania_petal_reader",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaPetalReaderRequiredLevel,
                    HandlerResources.BOTANIA_PETAL_READER_PERK
            ));

    public static final RegistryObject<Perk> RUNE_OF_MANA_RESONANCE =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaResonanceRequiredLevel < 0
            ? null : PERKS.register("botania_rune_of_mana_resonance", () -> register(
                    "botania_rune_of_mana_resonance",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaResonanceRequiredLevel,
                    HandlerResources.BOTANIA_RESONANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().botaniaResonancePercent)
            ));

    public static final RegistryObject<Perk> BOTANIA_SPARKLE_SENSE =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaSparkleSenseRequiredLevel < 0
            ? null : PERKS.register("botania_sparkle_sense", () -> register(
                    "botania_sparkle_sense",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaSparkleSenseRequiredLevel,
                    HandlerResources.BOTANIA_SPARKLE_SENSE_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaSparkleSenseRadius)
            ));

    public static final RegistryObject<Perk> BOTANIA_DOWSERS_TWIG =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaDowsersTwigRequiredLevel < 0
            ? null : PERKS.register("botania_dowsers_twig", () -> register(
                    "botania_dowsers_twig",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaDowsersTwigRequiredLevel,
                    HandlerResources.BOTANIA_DOWSERS_TWIG_PERK,
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().botaniaDowsersTwigSeconds),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().botaniaDowsersTwigCooldownSeconds)
            ));

    public static final RegistryObject<Perk> BOTANIA_GREEN_THUMB =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaGreenThumbRequiredLevel < 0
            ? null : PERKS.register("botania_green_thumb", () -> register(
                    "botania_green_thumb",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaGreenThumbRequiredLevel,
                    HandlerResources.BOTANIA_GREEN_THUMB_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().botaniaGreenThumbOneInN)
            ));

    public static final RegistryObject<Perk> BOTANIA_LIVINGBARK_STUDENT =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaLivingbarkStudentRequiredLevel < 0
            ? null : PERKS.register("botania_livingbark_student", () -> register(
                    "botania_livingbark_student",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaLivingbarkStudentRequiredLevel,
                    HandlerResources.BOTANIA_LIVINGBARK_STUDENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().botaniaLivingbarkStudentPercent)
            ));

    // ── WISDOM tree — Mid tier (Seasonal specializations) ──
    public static final RegistryObject<Perk> BOTANIA_AGRICULTORS_EYE =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaAgricultorsEyeRequiredLevel < 0
            ? null : PERKS.register("botania_agricultors_eye", () -> register(
                    "botania_agricultors_eye",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaAgricultorsEyeRequiredLevel,
                    HandlerResources.BOTANIA_AGRICULTORS_EYE_PERK
            ));

    public static final RegistryObject<Perk> BOTANIA_FORAGERS_PALATE =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaForagersPalateRequiredLevel < 0
            ? null : PERKS.register("botania_foragers_palate", () -> register(
                    "botania_foragers_palate",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaForagersPalateRequiredLevel,
                    HandlerResources.BOTANIA_FORAGERS_PALATE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().botaniaForagersPalatePercent),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().botaniaForagersPalateSeconds)
            ));

    public static final RegistryObject<Perk> BOTANIA_LOOT_HUNTERS_INTUITION =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaLootHuntersIntuitionRequiredLevel < 0
            ? null : PERKS.register("botania_loot_hunters_intuition", () -> register(
                    "botania_loot_hunters_intuition",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaLootHuntersIntuitionRequiredLevel,
                    HandlerResources.BOTANIA_LOOT_HUNTERS_INTUITION_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaLootHuntersIntuitionRadius),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().botaniaLootHuntersIntuitionSeconds)
            ));

    public static final RegistryObject<Perk> BOTANIA_STILL_LISTENER =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaStillListenerRequiredLevel < 0
            ? null : PERKS.register("botania_still_listener", () -> register(
                    "botania_still_listener",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaStillListenerRequiredLevel,
                    HandlerResources.BOTANIA_STILL_LISTENER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaStillListenerRadius)
            ));

    public static final RegistryObject<Perk> BOTANIA_MANASEERS_LENS =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaManaseersLensRequiredLevel < 0
            ? null : PERKS.register("botania_manaseers_lens", () -> register(
                    "botania_manaseers_lens",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaManaseersLensRequiredLevel,
                    HandlerResources.BOTANIA_MANASEERS_LENS_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaManaseersLensRadius)
            ));

    public static final RegistryObject<Perk> BOTANIA_CORPOREA_QUERY =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaCorporeaQueryRequiredLevel < 0
            ? null : PERKS.register("botania_corporea_query", () -> register(
                    "botania_corporea_query",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaCorporeaQueryRequiredLevel,
                    HandlerResources.BOTANIA_CORPOREA_QUERY_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaCorporeaQueryRadius)
            ));

    // ── WISDOM tree — High tier (Sin / Gaia / Elven capstones) ──
    public static final RegistryObject<Perk> BOTANIA_CARTOGRAPHER =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaCartographerRequiredLevel < 0
            ? null : PERKS.register("botania_cartographer", () -> register(
                    "botania_cartographer",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaCartographerRequiredLevel,
                    HandlerResources.BOTANIA_CARTOGRAPHER_PERK,
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().botaniaCartographerSeconds)
            ));

    public static final RegistryObject<Perk> BOTANIA_FAR_REACH =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaFarReachRequiredLevel < 0
            ? null : PERKS.register("botania_far_reach", () -> register(
                    "botania_far_reach",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaFarReachRequiredLevel,
                    HandlerResources.BOTANIA_FAR_REACH_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().botaniaFarReachBonusBlocks)
            ));

    public static final RegistryObject<Perk> BOTANIA_LAZY_SWAP =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaLazySwapRequiredLevel < 0
            ? null : PERKS.register("botania_lazy_swap", () -> register(
                    "botania_lazy_swap",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaLazySwapRequiredLevel,
                    HandlerResources.BOTANIA_LAZY_SWAP_PERK
            ));

    public static final RegistryObject<Perk> BOTANIA_MIRRORS_READ =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaMirrorsReadRequiredLevel < 0
            ? null : PERKS.register("botania_mirrors_read", () -> register(
                    "botania_mirrors_read",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaMirrorsReadRequiredLevel,
                    HandlerResources.BOTANIA_MIRRORS_READ_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaMirrorsReadRadius)
            ));

    public static final RegistryObject<Perk> BOTANIA_ELVEN_KNOWLEDGE =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaElvenKnowledgeRequiredLevel < 0
            ? null : PERKS.register("botania_elven_knowledge", () -> register(
                    "botania_elven_knowledge",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaElvenKnowledgeRequiredLevel,
                    HandlerResources.BOTANIA_ELVEN_KNOWLEDGE_PERK
            ));

    public static final RegistryObject<Perk> BOTANIA_GAIAS_WITNESS =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaGaiasWitnessRequiredLevel < 0
            ? null : PERKS.register("botania_gaias_witness", () -> register(
                    "botania_gaias_witness",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaGaiasWitnessRequiredLevel,
                    HandlerResources.BOTANIA_GAIAS_WITNESS_PERK
            ));

    public static final RegistryObject<Perk> BOTANIA_ORACLE_NINE_RUNES =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaOracleNineRunesRequiredLevel < 0
            ? null : PERKS.register("botania_oracle_nine_runes", () -> register(
                    "botania_oracle_nine_runes",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().botaniaOracleNineRunesRequiredLevel,
                    HandlerResources.BOTANIA_ORACLE_NINE_RUNES_PERK
            ));

    // ── MAGIC tree — Low tier (Elemental / Rune foundation) ──
    public static final RegistryObject<Perk> INNER_WELLSPRING =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaInnerWellspringRequiredLevel < 0
            ? null : PERKS.register("botania_inner_wellspring", () -> register(
                    "botania_inner_wellspring",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaInnerWellspringRequiredLevel,
                    HandlerResources.BOTANIA_INNER_WELLSPRING_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaInnerWellspringManaPerTick)
            ));

    public static final RegistryObject<Perk> RUNE_OF_WATER_TIDEWOVEN =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaTidewovenRequiredLevel < 0
            ? null : PERKS.register("botania_tidewoven", () -> register(
                    "botania_tidewoven",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaTidewovenRequiredLevel,
                    HandlerResources.BOTANIA_TIDEWOVEN_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().botaniaTidewovenDiscount)
            ));

    public static final RegistryObject<Perk> RUNE_OF_FIRE_EMBERHEART =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaEmberheartRequiredLevel < 0
            ? null : PERKS.register("botania_emberheart", () -> register(
                    "botania_emberheart",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaEmberheartRequiredLevel,
                    HandlerResources.BOTANIA_EMBERHEART_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaEmberheartFireDamage)
            ));

    public static final RegistryObject<Perk> RUNE_OF_EARTH_STONE_ROOTED =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaStoneRootedRequiredLevel < 0
            ? null : PERKS.register("botania_stone_rooted", () -> register(
                    "botania_stone_rooted",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaStoneRootedRequiredLevel,
                    HandlerResources.BOTANIA_STONE_ROOTED_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaStoneRootedArmor)
            ));

    public static final RegistryObject<Perk> RUNE_OF_AIR_FEATHERSTEP =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaFeatherstepRequiredLevel < 0
            ? null : PERKS.register("botania_featherstep", () -> register(
                    "botania_featherstep",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaFeatherstepRequiredLevel,
                    HandlerResources.BOTANIA_FEATHERSTEP_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().botaniaFeatherstepMultiplier)
            ));

    public static final RegistryObject<Perk> BAND_OF_AURA =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaBandOfAuraRequiredLevel < 0
            ? null : PERKS.register("botania_band_of_aura", () -> register(
                    "botania_band_of_aura",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaBandOfAuraRequiredLevel,
                    HandlerResources.BOTANIA_BAND_OF_AURA_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaBandOfAuraManaPerTick)
            ));

    // ── MAGIC tree — Mid tier (Seasonal / Lens specializations) ──
    public static final RegistryObject<Perk> VERDANT_PULSE =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaVerdantPulseRequiredLevel < 0
            ? null : PERKS.register("botania_verdant_pulse", () -> register(
                    "botania_verdant_pulse",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaVerdantPulseRequiredLevel,
                    HandlerResources.BOTANIA_VERDANT_PULSE_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaVerdantPulseRadius),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().botaniaVerdantPulseCooldownSeconds)
            ));

    public static final RegistryObject<Perk> SOLAR_CONDUIT =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaSolarConduitRequiredLevel < 0
            ? null : PERKS.register("botania_solar_conduit", () -> register(
                    "botania_solar_conduit",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaSolarConduitRequiredLevel,
                    HandlerResources.BOTANIA_SOLAR_CONDUIT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().botaniaSolarConduitPercent)
            ));

    public static final RegistryObject<Perk> HARVEST_TITHE =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaHarvestTitheRequiredLevel < 0
            ? null : PERKS.register("botania_harvest_tithe", () -> register(
                    "botania_harvest_tithe",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaHarvestTitheRequiredLevel,
                    HandlerResources.BOTANIA_HARVEST_TITHE_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().botaniaHarvestTithePercent)
            ));

    public static final RegistryObject<Perk> FROSTBOUND =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaFrostboundRequiredLevel < 0
            ? null : PERKS.register("botania_frostbound", () -> register(
                    "botania_frostbound",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaFrostboundRequiredLevel,
                    HandlerResources.BOTANIA_FROSTBOUND_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaFrostboundDamage),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().botaniaFrostboundSlowSeconds)
            ));

    public static final RegistryObject<Perk> LENS_VELOCITY =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaLensVelocityRequiredLevel < 0
            ? null : PERKS.register("botania_lens_velocity", () -> register(
                    "botania_lens_velocity",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaLensVelocityRequiredLevel,
                    HandlerResources.BOTANIA_LENS_VELOCITY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().botaniaLensVelocityPercent)
            ));

    public static final RegistryObject<Perk> LENS_POTENCY =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaLensPotencyRequiredLevel < 0
            ? null : PERKS.register("botania_lens_potency", () -> register(
                    "botania_lens_potency",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaLensPotencyRequiredLevel,
                    HandlerResources.BOTANIA_LENS_POTENCY_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().botaniaLensPotencyMultiplier),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().botaniaLensPotencyCooldownSeconds)
            ));

    // ── MAGIC tree — High tier (Sin / Gaia / relic capstones) ──
    public static final RegistryObject<Perk> PIXIE_AFFINITY =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaPixieAffinityRequiredLevel < 0
            ? null : PERKS.register("botania_pixie_affinity", () -> register(
                    "botania_pixie_affinity",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaPixieAffinityRequiredLevel,
                    HandlerResources.BOTANIA_PIXIE_AFFINITY_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().botaniaPixieAffinityPercent)
            ));

    public static final RegistryObject<Perk> CAKE_COMBUSTION =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaCakeCombustionRequiredLevel < 0
            ? null : PERKS.register("botania_cake_combustion", () -> register(
                    "botania_cake_combustion",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaCakeCombustionRequiredLevel,
                    HandlerResources.BOTANIA_CAKE_COMBUSTION_PERK,
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().botaniaCakeCombustionSeconds)
            ));

    public static final RegistryObject<Perk> MAGNETITE =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaMagnetiteRequiredLevel < 0
            ? null : PERKS.register("botania_magnetite", () -> register(
                    "botania_magnetite",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaMagnetiteRequiredLevel,
                    HandlerResources.BOTANIA_MAGNETITE_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaMagnetiteRadius)
            ));

    public static final RegistryObject<Perk> UNBOUND_STEP =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaUnboundStepRequiredLevel < 0
            ? null : PERKS.register("botania_unbound_step", () -> register(
                    "botania_unbound_step",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaUnboundStepRequiredLevel,
                    HandlerResources.BOTANIA_UNBOUND_STEP_PERK
            ));

    public static final RegistryObject<Perk> ENVY_MIRRORED_WRATH =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaMirroredWrathRequiredLevel < 0
            ? null : PERKS.register("botania_mirrored_wrath", () -> register(
                    "botania_mirrored_wrath",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaMirroredWrathRequiredLevel,
                    HandlerResources.BOTANIA_MIRRORED_WRATH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().botaniaMirroredWrathPercent)
            ));

    public static final RegistryObject<Perk> CROWN_OF_REACH =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaCrownOfReachRequiredLevel < 0
            ? null : PERKS.register("botania_crown_of_reach", () -> register(
                    "botania_crown_of_reach",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaCrownOfReachRequiredLevel,
                    HandlerResources.BOTANIA_CROWN_OF_REACH_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().botaniaCrownOfReachBonusBlocks)
            ));

    public static final RegistryObject<Perk> THUNDERCALL =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaThundercallRequiredLevel < 0
            ? null : PERKS.register("botania_thundercall", () -> register(
                    "botania_thundercall",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaThundercallRequiredLevel,
                    HandlerResources.BOTANIA_THUNDERCALL_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().botaniaThundercallPercent)
            ));

    public static final RegistryObject<Perk> RELIC_ATTUNEMENT =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaRelicAttunementRequiredLevel < 0
            ? null : PERKS.register("botania_relic_attunement", () -> register(
                    "botania_relic_attunement",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaRelicAttunementRequiredLevel,
                    HandlerResources.BOTANIA_RELIC_ATTUNEMENT_PERK
            ));

    public static final RegistryObject<Perk> TERRASTEEL_ASCENSION =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaTerrasteelAscensionRequiredLevel < 0
            ? null : PERKS.register("botania_terrasteel_ascension", () -> register(
                    "botania_terrasteel_ascension",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaTerrasteelAscensionRequiredLevel,
                    HandlerResources.BOTANIA_TERRASTEEL_ASCENSION_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaTerrasteelAscensionMaxHp)
            ));

    public static final RegistryObject<Perk> FLUGELS_GRACE =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaFlugelsGraceRequiredLevel < 0
            ? null : PERKS.register("botania_flugels_grace", () -> register(
                    "botania_flugels_grace",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaFlugelsGraceRequiredLevel,
                    HandlerResources.BOTANIA_FLUGELS_GRACE_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().botaniaFlugelsGraceJumps)
            ));

    public static final RegistryObject<Perk> MANASTORM =
            !BotaniaIntegration.isModLoaded() || HandlerCommonConfig.HANDLER.instance().botaniaManastormRequiredLevel < 0
            ? null : PERKS.register("botania_manastorm", () -> register(
                    "botania_manastorm",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().botaniaManastormRequiredLevel,
                    HandlerResources.BOTANIA_MANASTORM_PERK,
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().botaniaManastormCooldownSeconds),
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().botaniaManastormDamageMultiplier)
            ));

    private static Perk register(String name, Supplier<Skill> skillSupplier, int requiredLvl, ResourceLocation texture, Value... configValues) {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, name);
        return new Perk(key, skillSupplier, requiredLvl, texture, configValues);
    }

    public static void load(IEventBus eventBus) {
        PERKS.register(eventBus);
    }

    private static volatile List<Perk> cachedValues;
    private static volatile Map<String, Perk> cachedByName;

    public static List<Perk> getCachedValues() {
        if (cachedValues == null) {
            cachedValues = List.copyOf(PERKS_REGISTRY.get().getValues());
        }
        return cachedValues;
    }

    public static Perk getPerk(String perkName) {
        if (cachedByName == null) {
            cachedByName = getCachedValues().stream()
                    .collect(Collectors.toUnmodifiableMap(Perk::getName, Perk::get));
        }
        return cachedByName.get(perkName);
    }

    // School Attunement helpers
    private static final Set<String> SCHOOL_PERK_NAMES = Set.of(
            "fire_attunement", "ice_attunement", "lightning_attunement", "holy_attunement",
            "nature_attunement", "blood_attunement", "ender_attunement", "evocation_attunement"
    );

    public static boolean isSchoolAttunementPerk(String perkName) {
        return SCHOOL_PERK_NAMES.contains(perkName);
    }

    public static int countEnabledSchoolPerks(com.otectus.runicskills.common.capability.SkillCapability capability) {
        int count = 0;
        for (String name : SCHOOL_PERK_NAMES) {
            Perk perk = getPerk(name);
            if (perk != null && capability.isPerkActive(perk)) {
                count++;
            }
        }
        return count;
    }

    public static int countEnabledPerks(com.otectus.runicskills.common.capability.SkillCapability capability) {
        int count = 0;
        for (Integer rank : capability.perkRank.values()) {
            if (rank != null && rank >= 1) count++;
        }
        return count;
    }

    // Disabled-via-config support. Accepts either a bare registry path ("berserker") or a
    // full id ("runicskills:berserker"); matches both against the disabledPerks list.
    public static boolean isDisabled(String perkName) {
        if (perkName == null) return false;
        List<String> list = HandlerCommonConfig.HANDLER.instance().disabledPerks;
        if (list == null || list.isEmpty()) return false;
        String fullId = perkName.contains(":") ? perkName : (RunicSkills.MOD_ID + ":" + perkName);
        String path = perkName.contains(":") ? perkName.substring(perkName.indexOf(':') + 1) : perkName;
        for (String entry : list) {
            if (entry == null || entry.isEmpty()) continue;
            if (entry.equals(path) || entry.equals(fullId)) return true;
        }
        return false;
    }

    public static boolean isDisabled(Perk perk) {
        if (perk == null) return false;
        if (isDisabled(perk.getName())) return true;
        return isDisabled(perk.getMod() + ":" + perk.getName());
    }

    public static List<Perk> getSchoolAttunementPerks() {
        List<Perk> perks = new ArrayList<>();
        for (String name : SCHOOL_PERK_NAMES) {
            Perk perk = getPerk(name);
            if (perk != null) perks.add(perk);
        }
        return perks;
    }
}


