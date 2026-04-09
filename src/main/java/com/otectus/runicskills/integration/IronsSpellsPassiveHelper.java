package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.passive.Passive;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * Isolates Iron's Spellbooks class references for safe conditional loading.
 * This class must ONLY be loaded when Iron's Spellbooks is present.
 */
public class IronsSpellsPassiveHelper {

    public static Passive createSpellPowerPassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "spell_power");
        return new Passive(key, RegistrySkills.MAGIC::get,
                HandlerResources.create("textures/skill/magic/passive_spell_power.png"),
                AttributeRegistry.SPELL_POWER.get(),
                "96a891fe-5919-418d-8205-f50464391520",
                HandlerCommonConfig.HANDLER.instance().ironsSpellPowerValue,
                HandlerCommonConfig.HANDLER.instance().ironsSpellPowerPassiveLevels);
    }

    public static Passive createMaxManaPassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "max_mana");
        return new Passive(key, RegistrySkills.INTELLIGENCE::get,
                HandlerResources.create("textures/skill/intelligence/passive_max_mana.png"),
                AttributeRegistry.MAX_MANA.get(),
                "96a891fe-5919-418d-8205-f50464391521",
                HandlerCommonConfig.HANDLER.instance().ironsMaxManaValue,
                HandlerCommonConfig.HANDLER.instance().ironsMaxManaPassiveLevels);
    }

    public static Passive createCastTimeReductionPassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "cast_time_reduction");
        return new Passive(key, RegistrySkills.WISDOM::get,
                HandlerResources.create("textures/skill/wisdom/passive_cast_time_reduction.png"),
                AttributeRegistry.CAST_TIME_REDUCTION.get(),
                "96a891fe-5919-418d-8205-f50464391522",
                HandlerCommonConfig.HANDLER.instance().ironsCastTimeReductionValue,
                HandlerCommonConfig.HANDLER.instance().ironsCastTimeReductionPassiveLevels);
    }
}
