package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.integration.ApothicAttributesIntegration;
import com.otectus.runicskills.integration.ApothicPassiveHelper;
import com.otectus.runicskills.integration.ArsNouveauIntegration;
import com.otectus.runicskills.integration.ArsNouveauPassiveHelper;
import com.otectus.runicskills.integration.IronsSpellbooksIntegration;
import com.otectus.runicskills.integration.IronsSpellsPassiveHelper;
import com.otectus.runicskills.registry.skill.Skill;
import com.otectus.runicskills.registry.passive.Passive;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class RegistryPassives {
    public static final ResourceKey<Registry<Passive>> PASSIVES_KEY = ResourceKey.createRegistryKey(new ResourceLocation(RunicSkills.MOD_ID, "passives"));
    public static final DeferredRegister<Passive> PASSIVES = DeferredRegister.create(PASSIVES_KEY, RunicSkills.MOD_ID);
    public static final Supplier<IForgeRegistry<Passive>> PASSIVES_REGISTRY = PASSIVES.makeRegistry(() -> new RegistryBuilder<Passive>().disableSaving());

    public static final RegistryObject<Passive> ATTACK_DAMAGE = PASSIVES.register("attack_damage", () -> register("attack_damage", RegistrySkills.STRENGTH::get, HandlerResources.create("textures/skill/strength/passive_attack_damage.png"), Attributes.ATTACK_DAMAGE, "96a891fe-5919-418d-8205-f50464391500", HandlerCommonConfig.HANDLER.instance().attackDamageValue, HandlerCommonConfig.HANDLER.instance().attackPassiveLevels));

    public static final RegistryObject<Passive> ATTACK_KNOCKBACK = PASSIVES.register("attack_knockback", () -> register("attack_knockback", RegistrySkills.STRENGTH::get, HandlerResources.create("textures/skill/strength/passive_attack_knockback.png"), Attributes.ATTACK_KNOCKBACK, "96a891fe-5919-418d-8205-f50464391501", HandlerCommonConfig.HANDLER.instance().attackKnockbackValue, HandlerCommonConfig.HANDLER.instance().attackKnockbackPassiveLevels));

    public static final RegistryObject<Passive> MAX_HEALTH = PASSIVES.register("max_health", () -> register("max_health", RegistrySkills.CONSTITUTION::get, HandlerResources.create("textures/skill/constitution/passive_max_health.png"), Attributes.MAX_HEALTH, "96a891fe-5919-418d-8205-f50464391502", HandlerCommonConfig.HANDLER.instance().maxHealthValue, HandlerCommonConfig.HANDLER.instance().maxHealthPassiveLevels));

    public static final RegistryObject<Passive> KNOCKBACK_RESISTANCE = PASSIVES.register("knockback_resistance", () -> register("knockback_resistance", RegistrySkills.CONSTITUTION::get, HandlerResources.create("textures/skill/constitution/passive_knockback_resistance.png"), Attributes.KNOCKBACK_RESISTANCE, "96a891fe-5919-418d-8205-f50464391503", HandlerCommonConfig.HANDLER.instance().knockbackResistanceValue, HandlerCommonConfig.HANDLER.instance().knockbackResistancePassiveLevels));

    public static final RegistryObject<Passive> MOVEMENT_SPEED = PASSIVES.register("movement_speed", () -> register("movement_speed", RegistrySkills.DEXTERITY::get, HandlerResources.create("textures/skill/dexterity/passive_movement_speed.png"), Attributes.MOVEMENT_SPEED, "96a891fe-5919-418d-8205-f50464391504", HandlerCommonConfig.HANDLER.instance().movementSpeedValue, HandlerCommonConfig.HANDLER.instance().movementSpeedPassiveLevels));

    public static final RegistryObject<Passive> PROJECTILE_DAMAGE = PASSIVES.register("projectile_damage", () -> register("projectile_damage", RegistrySkills.DEXTERITY::get, HandlerResources.create("textures/skill/dexterity/passive_projectile_damage.png"), RegistryAttributes.getEffectiveProjectileDamage(), "96a891fe-5919-418d-8205-f50464391505", HandlerCommonConfig.HANDLER.instance().projectileDamageValue, HandlerCommonConfig.HANDLER.instance().projectileDamagePassiveLevels));

    public static final RegistryObject<Passive> ARMOR = PASSIVES.register("armor", () -> register("armor", RegistrySkills.ENDURANCE::get, HandlerResources.create("textures/skill/endurance/passive_armor.png"), Attributes.ARMOR, "96a891fe-5919-418d-8205-f50464391506", HandlerCommonConfig.HANDLER.instance().armorValue, HandlerCommonConfig.HANDLER.instance().armorPassiveLevels));

    public static final RegistryObject<Passive> ARMOR_TOUGHNESS = PASSIVES.register("armor_toughness", () -> register("armor_toughness", RegistrySkills.ENDURANCE::get, HandlerResources.create("textures/skill/endurance/passive_armor_toughness.png"), Attributes.ARMOR_TOUGHNESS, "96a891fe-5919-418d-8205-f50464391507", HandlerCommonConfig.HANDLER.instance().armorToughnessValue, HandlerCommonConfig.HANDLER.instance().armorToughnessPassiveLevels));

    public static final RegistryObject<Passive> ATTACK_SPEED = PASSIVES.register("attack_speed", () -> register("attack_speed", RegistrySkills.INTELLIGENCE::get, HandlerResources.create("textures/skill/intelligence/passive_attack_speed.png"), Attributes.ATTACK_SPEED, "96a891fe-5919-418d-8205-f50464391508", HandlerCommonConfig.HANDLER.instance().attackSpeedValue, HandlerCommonConfig.HANDLER.instance().attackSpeedPassiveLevels));

    public static final RegistryObject<Passive> ENTITY_REACH = PASSIVES.register("entity_reach", () -> register("entity_reach", RegistrySkills.INTELLIGENCE::get, HandlerResources.create("textures/skill/intelligence/passive_entity_reach.png"), ForgeMod.ENTITY_REACH.get(), "96a891fe-5919-418d-8205-f50464391509", HandlerCommonConfig.HANDLER.instance().entityReachValue, HandlerCommonConfig.HANDLER.instance().entityReachPassiveLevels));

    // Building passives
    public static final RegistryObject<Passive> BLOCK_REACH = PASSIVES.register("block_reach", () -> register("block_reach", RegistrySkills.BUILDING::get, HandlerResources.create("textures/skill/building/passive_block_reach.png"), ForgeMod.BLOCK_REACH.get(), "96a891fe-5919-418d-8205-f50464391510", HandlerCommonConfig.HANDLER.instance().blockReachValue, HandlerCommonConfig.HANDLER.instance().blockReachPassiveLevels));

    public static final RegistryObject<Passive> BREAK_SPEED = PASSIVES.register("break_speed", () -> register("break_speed", RegistrySkills.BUILDING::get, HandlerResources.create("textures/skill/building/passive_break_speed.png"), RegistryAttributes.getEffectiveBreakSpeed(), "96a891fe-5919-418d-8205-f50464391511", HandlerCommonConfig.HANDLER.instance().breakSpeedValue, HandlerCommonConfig.HANDLER.instance().breakSpeedPassiveLevels));

    // Wisdom base passives
    public static final RegistryObject<Passive> ENCHANTING_POWER = PASSIVES.register("enchanting_power", () -> register("enchanting_power", RegistrySkills.WISDOM::get, HandlerResources.create("textures/skill/wisdom/passive_enchanting_power.png"), RegistryAttributes.ENCHANTING_POWER.get(), "96a891fe-5919-418d-8205-f50464391540", HandlerCommonConfig.HANDLER.instance().enchantingPowerValue, HandlerCommonConfig.HANDLER.instance().enchantingPowerPassiveLevels));

    public static final RegistryObject<Passive> XP_BONUS = PASSIVES.register("xp_bonus", () -> register("xp_bonus", RegistrySkills.WISDOM::get, HandlerResources.create("textures/skill/wisdom/passive_xp_bonus.png"), RegistryAttributes.XP_BONUS.get(), "96a891fe-5919-418d-8205-f50464391541", HandlerCommonConfig.HANDLER.instance().xpBonusValue, HandlerCommonConfig.HANDLER.instance().xpBonusPassiveLevels));

    public static final RegistryObject<Passive> BENEFICIAL_EFFECT = PASSIVES.register("beneficial_effect", () -> register("beneficial_effect", RegistrySkills.MAGIC::get, HandlerResources.create("textures/skill/magic/passive_beneficial_effect.png"), RegistryAttributes.BENEFICIAL_EFFECT.get(), "96a891fe-5919-418d-8205-f50464391512", HandlerCommonConfig.HANDLER.instance().beneficialEffectValue, HandlerCommonConfig.HANDLER.instance().beneficialEffectPassiveLevels));

    public static final RegistryObject<Passive> MAGIC_RESIST = PASSIVES.register("magic_resist", () -> register("magic_resist", RegistrySkills.MAGIC::get, HandlerResources.create("textures/skill/magic/passive_magic_resist.png"), RegistryAttributes.MAGIC_RESIST.get(), "96a891fe-5919-418d-8205-f50464391513", HandlerCommonConfig.HANDLER.instance().magicResistValue, HandlerCommonConfig.HANDLER.instance().magicResistPassiveLevels));

    public static final RegistryObject<Passive> CRITICAL_DAMAGE = PASSIVES.register("critical_damage", () -> register("critical_damage", RegistrySkills.FORTUNE::get, HandlerResources.create("textures/skill/fortune/passive_critical_damage.png"), RegistryAttributes.getEffectiveCritDamage(), "96a891fe-5919-418d-8205-f50464391515", HandlerCommonConfig.HANDLER.instance().criticalDamageValue, HandlerCommonConfig.HANDLER.instance().criticalDamagePassiveLevels));

    public static final RegistryObject<Passive> FORTUNE = PASSIVES.register("fortune", () -> register("fortune", RegistrySkills.FORTUNE::get, HandlerResources.create("textures/skill/fortune/passive_luck.png"), Attributes.LUCK, "96a891fe-5919-418d-8205-f50464391514", HandlerCommonConfig.HANDLER.instance().luckValue, HandlerCommonConfig.HANDLER.instance().luckPassiveLevels));

    // Iron's Spells 'n Spellbooks Integration - Conditional passives
    public static final RegistryObject<Passive> SPELL_POWER = !IronsSpellbooksIntegration.isModLoaded() ? null : PASSIVES.register("spell_power", IronsSpellsPassiveHelper::createSpellPowerPassive);
    public static final RegistryObject<Passive> MAX_MANA = !IronsSpellbooksIntegration.isModLoaded() ? null : PASSIVES.register("max_mana", IronsSpellsPassiveHelper::createMaxManaPassive);
    public static final RegistryObject<Passive> CAST_TIME_REDUCTION = !IronsSpellbooksIntegration.isModLoaded() ? null : PASSIVES.register("cast_time_reduction", IronsSpellsPassiveHelper::createCastTimeReductionPassive);

    // Ars Nouveau Integration - Conditional passives
    public static final RegistryObject<Passive> ARS_SPELL_DAMAGE = !ArsNouveauIntegration.isModLoaded() ? null : PASSIVES.register("ars_spell_damage", ArsNouveauPassiveHelper::createArsSpellDamagePassive);
    public static final RegistryObject<Passive> ARS_FLAT_MANA = !ArsNouveauIntegration.isModLoaded() ? null : PASSIVES.register("ars_flat_mana", ArsNouveauPassiveHelper::createArsFlatManaPassive);
    public static final RegistryObject<Passive> ARS_WARDING = !ArsNouveauIntegration.isModLoaded() ? null : PASSIVES.register("ars_warding", ArsNouveauPassiveHelper::createArsWardingPassive);

    // Apothic Attributes Integration - Conditional passives
    public static final RegistryObject<Passive> APOTHIC_LIFE_STEAL = !ApothicAttributesIntegration.isModLoaded() ? null : PASSIVES.register("life_steal", ApothicPassiveHelper::createLifeStealPassive);
    public static final RegistryObject<Passive> APOTHIC_HEALING_RECEIVED = !ApothicAttributesIntegration.isModLoaded() ? null : PASSIVES.register("healing_received", ApothicPassiveHelper::createHealingReceivedPassive);
    public static final RegistryObject<Passive> APOTHIC_DRAW_SPEED = !ApothicAttributesIntegration.isModLoaded() ? null : PASSIVES.register("draw_speed", ApothicPassiveHelper::createDrawSpeedPassive);
    public static final RegistryObject<Passive> APOTHIC_DODGE_CHANCE = !ApothicAttributesIntegration.isModLoaded() ? null : PASSIVES.register("dodge_chance", ApothicPassiveHelper::createDodgeChancePassive);
    public static final RegistryObject<Passive> APOTHIC_EXPERIENCE_GAINED = !ApothicAttributesIntegration.isModLoaded() ? null : PASSIVES.register("experience_gained", ApothicPassiveHelper::createExperienceGainedPassive);
    public static final RegistryObject<Passive> APOTHIC_MINING_SPEED = !ApothicAttributesIntegration.isModLoaded() ? null : PASSIVES.register("mining_speed", ApothicPassiveHelper::createMiningSpeedPassive);
    public static final RegistryObject<Passive> APOTHIC_COLD_DAMAGE = !ApothicAttributesIntegration.isModLoaded() ? null : PASSIVES.register("cold_damage", ApothicPassiveHelper::createColdDamagePassive);
    public static final RegistryObject<Passive> APOTHIC_CRIT_CHANCE = !ApothicAttributesIntegration.isModLoaded() ? null : PASSIVES.register("crit_chance", ApothicPassiveHelper::createCritChancePassive);
    public static final RegistryObject<Passive> APOTHIC_FIRE_DAMAGE = !ApothicAttributesIntegration.isModLoaded() ? null : PASSIVES.register("fire_damage", ApothicPassiveHelper::createFireDamagePassive);
    public static final RegistryObject<Passive> APOTHIC_ARROW_VELOCITY = !ApothicAttributesIntegration.isModLoaded() ? null : PASSIVES.register("arrow_velocity", ApothicPassiveHelper::createArrowVelocityPassive);
    public static final RegistryObject<Passive> APOTHIC_ARMOR_PIERCE = !ApothicAttributesIntegration.isModLoaded() ? null : PASSIVES.register("armor_pierce", ApothicPassiveHelper::createArmorPiercePassive);

    // Forge native attributes
    public static final RegistryObject<Passive> SWIM_SPEED = PASSIVES.register("swim_speed", () -> register("swim_speed", RegistrySkills.CONSTITUTION::get, HandlerResources.create("textures/skill/constitution/passive_swim_speed.png"), ForgeMod.SWIM_SPEED.get(), "96a891fe-5919-418d-8205-f50464391553", HandlerCommonConfig.HANDLER.instance().swimSpeedValue, HandlerCommonConfig.HANDLER.instance().swimSpeedPassiveLevels));

    // Tinkering skill passives
    public static final RegistryObject<Passive> REPAIR_EFFICIENCY = PASSIVES.register("repair_efficiency", () -> register("repair_efficiency", RegistrySkills.TINKERING::get, HandlerResources.create("textures/skill/tinkering/passive_repair_efficiency.png"), RegistryAttributes.REPAIR_EFFICIENCY.get(), "96a891fe-5919-418d-8205-f50464391560", HandlerCommonConfig.HANDLER.instance().repairEfficiencyValue, HandlerCommonConfig.HANDLER.instance().repairEfficiencyPassiveLevels));
    public static final RegistryObject<Passive> CRAFTING_LUCK = PASSIVES.register("crafting_luck", () -> register("crafting_luck", RegistrySkills.TINKERING::get, HandlerResources.create("textures/skill/tinkering/passive_crafting_luck.png"), RegistryAttributes.CRAFTING_LUCK.get(), "96a891fe-5919-418d-8205-f50464391561", HandlerCommonConfig.HANDLER.instance().craftingLuckValue, HandlerCommonConfig.HANDLER.instance().craftingLuckPassiveLevels));

    private static Passive register(String name, Supplier<Skill> skillSupplier, ResourceLocation texture, Attribute attribute, String attributeUuid, Object attributeValue, int... levelsRequired) {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, name);
        return new Passive(key, skillSupplier, texture, attribute, attributeUuid, attributeValue, levelsRequired);
    }

    public static void load(IEventBus eventBus) {
        PASSIVES.register(eventBus);
    }

    private static volatile List<Passive> cachedValues;
    private static volatile Map<String, Passive> cachedByName;

    public static List<Passive> getCachedValues() {
        if (cachedValues == null) {
            cachedValues = List.copyOf(PASSIVES_REGISTRY.get().getValues());
        }
        return cachedValues;
    }

    public static Passive getPassive(String passiveName) {
        if (cachedByName == null) {
            cachedByName = getCachedValues().stream()
                    .collect(Collectors.toUnmodifiableMap(Passive::getName, Passive::get));
        }
        return cachedByName.get(passiveName);
    }
}


