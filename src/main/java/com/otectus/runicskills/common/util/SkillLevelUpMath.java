package com.otectus.runicskills.common.util;

/**
 * Pure, Forge-free decision logic for skill level-ups, unit-testable without a Minecraft
 * classpath (same pattern as {@link PerkCapMath} / {@link ApothGateMath}). The runtime caller is
 * {@code SkillLevelUpSP}.
 *
 * <p>{@code SkillCapability.addSkillLevel} already clamps stored levels to {@code skillMaxLevel},
 * so a level-up request at cap could never raise the level — but before 1.5.3 the packet handler
 * still consumed the player's XP, fired {@code SkillLevelUpEvent}, and notified the quest bridge
 * for a level-up that never happened. The handler must therefore reject at-cap requests up front.
 */
public final class SkillLevelUpMath {

    private SkillLevelUpMath() {
    }

    /**
     * Whether a skill at {@code currentLevel} may gain a level under {@code maxLevel}.
     *
     * <p>{@code maxLevel <= 0} rejects all level-ups — consistent with the storage clamp
     * ({@code Math.min(level, maxLevel)}), under which such a cap already made leveling a no-op.</p>
     */
    public static boolean canLevelUp(int currentLevel, int maxLevel) {
        return currentLevel < maxLevel;
    }
}
