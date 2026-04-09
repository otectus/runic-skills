package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.passive.Passive;
import com.hollingsworth.arsnouveau.api.perk.PerkAttributes;
import net.minecraft.resources.ResourceLocation;

/**
 * Isolates Ars Nouveau class references for safe conditional loading.
 * This class must ONLY be loaded when Ars Nouveau is present.
 */
public class ArsNouveauPassiveHelper {

    public static Passive createArsSpellDamagePassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "ars_spell_damage");
        return new Passive(key, RegistrySkills.MAGIC::get,
                HandlerResources.create("textures/skill/magic/passive_ars_spell_damage.png"),
                PerkAttributes.SPELL_DAMAGE_BONUS.get(),
                "96a891fe-5919-418d-8205-f50464391530",
                HandlerCommonConfig.HANDLER.instance().arsSpellDamageBonusValue,
                HandlerCommonConfig.HANDLER.instance().arsSpellDamageBonusPassiveLevels);
    }

    public static Passive createArsFlatManaPassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "ars_flat_mana");
        return new Passive(key, RegistrySkills.INTELLIGENCE::get,
                HandlerResources.create("textures/skill/intelligence/passive_ars_mana.png"),
                PerkAttributes.FLAT_MANA_BONUS.get(),
                "96a891fe-5919-418d-8205-f50464391531",
                HandlerCommonConfig.HANDLER.instance().arsFlatManaBonusValue,
                HandlerCommonConfig.HANDLER.instance().arsFlatManaBonusPassiveLevels);
    }

    public static Passive createArsWardingPassive() {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, "ars_warding");
        return new Passive(key, RegistrySkills.ENDURANCE::get,
                HandlerResources.create("textures/skill/endurance/passive_ars_warding.png"),
                PerkAttributes.WARDING.get(),
                "96a891fe-5919-418d-8205-f50464391532",
                HandlerCommonConfig.HANDLER.instance().arsWardingValue,
                HandlerCommonConfig.HANDLER.instance().arsWardingPassiveLevels);
    }
}
