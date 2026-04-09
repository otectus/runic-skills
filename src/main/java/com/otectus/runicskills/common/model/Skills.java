
package com.otectus.runicskills.common.model;

import com.otectus.runicskills.registry.skill.Skill;

public final class Skills {
    private final String key;

    public String getKey() {
        return key;
    }

    private final String getResource;

    public String getResource() {
        return getResource;
    }

    private final boolean isDroppable;

    public boolean isDroppable() {
        return isDroppable;
    }

    private final Skill getSkill;

    public Skill getSkill() {
        return getSkill;
    }

    private final int skillLvl;

    public int getSkillLvl() {
        return skillLvl;
    }

    public Skills(String key, String getResource, boolean isDroppable, Skill getSkill, int skillLvl) {
        this.key = key;
        this.getResource = getResource;
        this.isDroppable = isDroppable;
        this.getSkill = getSkill;
        this.skillLvl = skillLvl;
    }
}
