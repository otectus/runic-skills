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

    /**
     * Whether a player can afford a skill level-up whose cost is {@code requiredPoints} XP points,
     * given their current spendable balance {@code spendableXp} (see
     * {@link ExperienceMath#spendableXp}). Creative players always afford it.
     *
     * <p>XP points are the single authoritative currency. Before 1.5.5 the packet handler also
     * accepted an alternate "enough experience <em>levels</em>" branch, which let a player with few
     * XP points but a high level number pass the gate and then be charged the full point cost —
     * driving their XP negative. This helper collapses affordability to a single points comparison so
     * validation (server) and the button/tooltip state (client) can never diverge.</p>
     */
    public static boolean canAfford(boolean creative, int spendableXp, int requiredPoints) {
        return creative || requiredPoints <= spendableXp;
    }
}
