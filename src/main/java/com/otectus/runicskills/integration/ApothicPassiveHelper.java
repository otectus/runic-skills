package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.passive.Passive;
import dev.shadowsoffire.attributeslib.api.ALObjects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * Isolates Apothic Attributes class references for safe conditional loading.
 * This class must ONLY be loaded when Apothic Attributes (attributeslib) is present.
 */
public class ApothicPassiveHelper {

    // --- Attribute Delegation Getters ---

    public static Attribute getCritDamage() {
        return ALObjects.Attributes.CRIT_DAMAGE.get();
    }

    public static Attribute getMiningSpeed() {
        return ALObjects.Attributes.MINING_SPEED.get();
    }

    public static Attribute getArrowDamage() {
        return ALObjects.Attributes.ARROW_DAMAGE.get();
    }

    // --- New Conditional Passive Factory Methods ---

    public static Passive createLifeStealPassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "life_steal");
        return new Passive(key, RegistrySkills.STRENGTH::get,
                HandlerResources.create("textures/skill/strength/passive_life_steal.png"),
                ALObjects.Attributes.LIFE_STEAL.get(),
                "96a891fe-5919-418d-8205-f50464391530",
                HandlerCommonConfig.HANDLER.instance().apothicLifeStealValue,
                HandlerCommonConfig.HANDLER.instance().apothicLifeStealPassiveLevels);
    }

    public static Passive createHealingReceivedPassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "healing_received");
        return new Passive(key, RegistrySkills.CONSTITUTION::get,
                HandlerResources.create("textures/skill/constitution/passive_healing_received.png"),
                ALObjects.Attributes.HEALING_RECEIVED.get(),
                "96a891fe-5919-418d-8205-f50464391531",
                HandlerCommonConfig.HANDLER.instance().apothicHealingReceivedValue,
                HandlerCommonConfig.HANDLER.instance().apothicHealingReceivedPassiveLevels);
    }

    public static Passive createDrawSpeedPassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "draw_speed");
        return new Passive(key, RegistrySkills.DEXTERITY::get,
                HandlerResources.create("textures/skill/dexterity/passive_draw_speed.png"),
                ALObjects.Attributes.DRAW_SPEED.get(),
                "96a891fe-5919-418d-8205-f50464391532",
                HandlerCommonConfig.HANDLER.instance().apothicDrawSpeedValue,
                HandlerCommonConfig.HANDLER.instance().apothicDrawSpeedPassiveLevels);
    }

    public static Passive createDodgeChancePassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "dodge_chance");
        return new Passive(key, RegistrySkills.ENDURANCE::get,
                HandlerResources.create("textures/skill/endurance/passive_dodge_chance.png"),
                ALObjects.Attributes.DODGE_CHANCE.get(),
                "96a891fe-5919-418d-8205-f50464391533",
                HandlerCommonConfig.HANDLER.instance().apothicDodgeChanceValue,
                HandlerCommonConfig.HANDLER.instance().apothicDodgeChancePassiveLevels);
    }

    public static Passive createExperienceGainedPassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "experience_gained");
        return new Passive(key, RegistrySkills.INTELLIGENCE::get,
                HandlerResources.create("textures/skill/intelligence/passive_experience_gained.png"),
                ALObjects.Attributes.EXPERIENCE_GAINED.get(),
                "96a891fe-5919-418d-8205-f50464391534",
                HandlerCommonConfig.HANDLER.instance().apothicExperienceGainedValue,
                HandlerCommonConfig.HANDLER.instance().apothicExperienceGainedPassiveLevels);
    }

    public static Passive createMiningSpeedPassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "mining_speed");
        return new Passive(key, RegistrySkills.BUILDING::get,
                HandlerResources.create("textures/skill/building/passive_mining_speed.png"),
                ALObjects.Attributes.MINING_SPEED.get(),
                "96a891fe-5919-418d-8205-f50464391535",
                HandlerCommonConfig.HANDLER.instance().apothicMiningSpeedValue,
                HandlerCommonConfig.HANDLER.instance().apothicMiningSpeedPassiveLevels);
    }

    public static Passive createColdDamagePassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "cold_damage");
        return new Passive(key, RegistrySkills.MAGIC::get,
                HandlerResources.create("textures/skill/magic/passive_cold_damage.png"),
                ALObjects.Attributes.COLD_DAMAGE.get(),
                "96a891fe-5919-418d-8205-f50464391536",
                HandlerCommonConfig.HANDLER.instance().apothicColdDamageValue,
                HandlerCommonConfig.HANDLER.instance().apothicColdDamagePassiveLevels);
    }

    public static Passive createCritChancePassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "crit_chance");
        return new Passive(key, RegistrySkills.FORTUNE::get,
                HandlerResources.create("textures/skill/fortune/passive_crit_chance.png"),
                ALObjects.Attributes.CRIT_CHANCE.get(),
                "96a891fe-5919-418d-8205-f50464391537",
                HandlerCommonConfig.HANDLER.instance().apothicCritChanceValue,
                HandlerCommonConfig.HANDLER.instance().apothicCritChancePassiveLevels);
    }

    public static Passive createFireDamagePassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "fire_damage");
        return new Passive(key, RegistrySkills.MAGIC::get,
                HandlerResources.create("textures/skill/magic/passive_fire_damage.png"),
                ALObjects.Attributes.FIRE_DAMAGE.get(),
                "96a891fe-5919-418d-8205-f50464391550",
                HandlerCommonConfig.HANDLER.instance().apothicFireDamageValue,
                HandlerCommonConfig.HANDLER.instance().apothicFireDamagePassiveLevels);
    }

    public static Passive createArrowVelocityPassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "arrow_velocity");
        return new Passive(key, RegistrySkills.DEXTERITY::get,
                HandlerResources.create("textures/skill/dexterity/passive_arrow_velocity.png"),
                ALObjects.Attributes.ARROW_VELOCITY.get(),
                "96a891fe-5919-418d-8205-f50464391551",
                HandlerCommonConfig.HANDLER.instance().apothicArrowVelocityValue,
                HandlerCommonConfig.HANDLER.instance().apothicArrowVelocityPassiveLevels);
    }

    public static Passive createArmorPiercePassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "armor_pierce");
        return new Passive(key, RegistrySkills.STRENGTH::get,
                HandlerResources.create("textures/skill/strength/passive_armor_pierce.png"),
                ALObjects.Attributes.ARMOR_PIERCE.get(),
                "96a891fe-5919-418d-8205-f50464391552",
                HandlerCommonConfig.HANDLER.instance().apothicArmorPierceValue,
                HandlerCommonConfig.HANDLER.instance().apothicArmorPiercePassiveLevels);
    }
}
