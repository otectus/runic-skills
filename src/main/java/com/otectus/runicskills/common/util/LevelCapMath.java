package com.otectus.runicskills.common.util;

public final class LevelCapMath {
    private LevelCapMath() {}
    public static int reachable(int skills, int perSkill) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, skills) * Math.max(0, perSkill));
    }
    public static int effective(String mode, int custom, int skills, int perSkill) {
        return "sum_of_skill_caps".equals(mode) ? reachable(skills, perSkill) : Math.max(32, Math.min(99999, custom));
    }
    public static int suggestedSkillCap(int budget, int skills) {
        return (int) Math.min(1000, Math.max(2L, ((long) budget + Math.max(1, skills) - 1) / Math.max(1, skills)));
    }
}
