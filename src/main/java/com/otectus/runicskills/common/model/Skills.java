
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

    /** Where this lock came from: "manual" config, or a generating provider id (e.g. "iceandfire"). */
    private final String source;

    public String getSource() {
        return source;
    }

    public Skills(String key, String getResource, boolean isDroppable, Skill getSkill, int skillLvl) {
        this(key, getResource, isDroppable, getSkill, skillLvl, "manual");
    }

    public Skills(String key, String getResource, boolean isDroppable, Skill getSkill, int skillLvl, String source) {
        this.key = key;
        this.getResource = getResource;
        this.isDroppable = isDroppable;
        this.getSkill = getSkill;
        this.skillLvl = skillLvl;
        this.source = (source == null || source.isBlank()) ? "manual" : source;
    }
}
