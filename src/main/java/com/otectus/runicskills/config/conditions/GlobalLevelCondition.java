package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.server.level.ServerPlayer;

public class GlobalLevelCondition extends IntegerConditionImpl {

    public GlobalLevelCondition() {
        super("GlobalLevel");
    }

    @Override
    public void ProcessVariable(String value, ServerPlayer serverPlayer) {
        SkillCapability cap = SkillCapability.get(serverPlayer);
        int total = 0;
        if (cap != null) {
            for (Skill skill : RegistrySkills.SKILLS_REGISTRY.get().getValues()) {
                total += cap.getSkillLevel(skill);
            }
        }
        setProcessedValue(total);
    }

}
