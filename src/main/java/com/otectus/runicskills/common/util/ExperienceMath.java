package com.otectus.runicskills.common.util;

/**
 * Pure, Forge-free reimplementation of the vanilla Minecraft XP curve plus the skill level-up cost
 * formula, kept free of Minecraft/Forge imports so it can be unit-tested directly (see
 * {@code ExperienceMathTest} in src/test). The runtime callers are {@code SkillLevelUpSP} (server
 * validation + XP deduction) and {@code client.core.Utils}/{@code client.screen.RunicSkillsScreen}
 * (client affordability + tooltip). Before this class the curve math was copy-pasted verbatim in
 * three places and untested.
 *
 * <p><strong>Regression this locks down (the 0.1-multiplier over-spend bug):</strong> the level-up
 * affordability gate used to be an OR across two <em>different</em> currencies — XP <em>points</em>
 * OR XP <em>levels</em> — while the deduction always removed points. A player with enough displayed
 * levels but far fewer points passed the check, then got charged the full point cost and went
 * negative. XP points are now the single authoritative currency: {@link #spendableXp} computes the
 * player's real balance and {@link #requiredPoints} the point cost, compared by
 * {@link SkillLevelUpMath#canAfford}. {@link #getLevelForExperience} clamps to 0 so a total XP value
 * can never resolve to a negative level.</p>
 */
public final class ExperienceMath {

    private ExperienceMath() {
    }

    /**
     * XP points needed to advance from {@code level} to {@code level + 1}. Mirrors vanilla
     * {@code Player.getXpNeededForNextLevel()} exactly, so it can stand in for it without a player.
     */
    public static int xpBarCap(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    /** Total XP points accumulated to reach {@code level} (0 progress into it). Vanilla curve. */
    public static int getExperienceForLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 15) return sum(level, 7, 2);
        if (level <= 30) return 315 + sum(level - 15, 37, 5);
        return 1395 + sum(level - 30, 112, 9);
    }

    /**
     * Inverse of {@link #getExperienceForLevel}: the experience level a player holding {@code totalXp}
     * points sits at. A non-positive {@code totalXp} clamps to level 0 — a total XP value must never
     * resolve to a negative level (see class regression note).
     */
    public static int getLevelForExperience(int totalXp) {
        if (totalXp <= 0) return 0;
        int level = 0;
        while (true) {
            final int xpToNextLevel = xpBarCap(level);
            if (totalXp < xpToNextLevel) return level;
            level++;
            totalXp -= xpToNextLevel;
        }
    }

    /**
     * The player's current spendable XP-point balance, derived from vanilla {@code experienceLevel}
     * and {@code experienceProgress}. This is the authoritative currency for skill level-up costs.
     * Progress is clamped to {@code [0,1]} so a transiently inconsistent player state can't inflate
     * or negate the balance.
     */
    public static int spendableXp(int experienceLevel, float experienceProgress) {
        int level = Math.max(0, experienceLevel);
        float progress = experienceProgress < 0f ? 0f : (experienceProgress > 1f ? 1f : experienceProgress);
        return getExperienceForLevel(level) + (int) (progress * xpBarCap(level));
    }

    /**
     * Fractional progress into {@code level} for a player holding {@code total} points, clamped to
     * {@code [0,1)}. Never returns a negative value or NaN ({@link #xpBarCap} is always {@code > 0}).
     */
    public static float progressForTotal(int total, int level) {
        int clampedTotal = Math.max(0, total);
        int base = getExperienceForLevel(level);
        int cap = xpBarCap(level);
        float progress = (clampedTotal - base) / (float) cap;
        if (progress < 0f) return 0f;
        if (progress >= 1f) return 0f;
        return progress;
    }

    /**
     * The XP-point cost to raise a skill currently at {@code skillLevel} by one, in the authoritative
     * points currency the server deducts.
     *
     * <p>Base cost = {@code getExperienceForLevel(skillLevel + firstCostLevel - 1)} scaled by
     * {@code mult} ({@code skillLevelUpCostMultiplier}) and rounded. The result is clamped to at least
     * {@code minCost} ({@code skillLevelUpMinCost}) so low multipliers stay cheap without rounding a
     * real level-up down to a free one. {@code minCost} is itself floored at 0.</p>
     */
    public static int requiredPoints(int skillLevel, int firstCostLevel, float mult, int minCost) {
        int base = getExperienceForLevel(skillLevel + firstCostLevel - 1);
        int cost = Math.max(0, Math.round(base * mult));
        return Math.max(Math.max(0, minCost), cost);
    }

    private static int sum(int n, int a0, int d) {
        return n * (2 * a0 + (n - 1) * d) / 2;
    }
}
