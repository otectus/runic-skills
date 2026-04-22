package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.ESkill;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.StringUtils;

public class SkillCondition extends IntegerConditionImpl {

    public SkillCondition() {
        super("Skill");
    }

    @Override
    public void ProcessVariable(String value, ServerPlayer serverPlayer) {
        ESkill skill;
        try {
            skill = ESkill.valueOf(StringUtils.capitalize(value));
        } catch (IllegalArgumentException e) {
            RunicSkills.getLOGGER().error(">> Unknown skill '{}' in title condition. Valid skills: {}", value, java.util.Arrays.toString(ESkill.values()));
            setProcessedValue(0);
            return;
        }
        SkillCapability capability = SkillCapability.get(serverPlayer);
        if (capability == null) {
            setProcessedValue(0);
            return;
        }
        int skillLevel = capability.getSkillLevel(RegistrySkills.getSkill(skill.toString()));

        setProcessedValue(skillLevel);
    }

}
