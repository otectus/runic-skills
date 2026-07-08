package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down the vanilla XP-curve math and the skill level-up cost formula extracted into
 * {@link ExperienceMath}. Regression for the {@code skillLevelUpCostMultiplier = 0.1} over-spend
 * bug: leveling a skill could deduct more XP points than the player had (because the affordability
 * gate compared displayed <em>levels</em> while the deduction removed <em>points</em>), driving XP
 * negative. XP points are now the single authoritative currency, validated by
 * {@link SkillLevelUpMath#canAfford} against {@link ExperienceMath#spendableXp}.
 *
 * <p>Expected values below are the vanilla curve: getExperienceForLevel(5)=55, (15)=315, (19)=493,
 * (34)=1897, (35)=2045, (36)=2202.</p>
 */
class ExperienceMathTest {

    // --- vanilla curve sanity -------------------------------------------------------------------

    @Test
    void experienceForLevelMatchesVanillaCurve() {
        assertEquals(0, ExperienceMath.getExperienceForLevel(0));
        assertEquals(55, ExperienceMath.getExperienceForLevel(5));
        assertEquals(315, ExperienceMath.getExperienceForLevel(15));
        assertEquals(493, ExperienceMath.getExperienceForLevel(19));
        assertEquals(1897, ExperienceMath.getExperienceForLevel(34));
        assertEquals(2202, ExperienceMath.getExperienceForLevel(36));
    }

    @Test
    void getLevelForExperienceInvertsCurveAndClampsNegatives() {
        assertEquals(0, ExperienceMath.getLevelForExperience(0));
        assertEquals(0, ExperienceMath.getLevelForExperience(-163)); // no negative levels
        assertEquals(0, ExperienceMath.getLevelForExperience(6));    // still level 0 (needs 7)
        assertEquals(1, ExperienceMath.getLevelForExperience(7));    // exactly level 1
        assertEquals(15, ExperienceMath.getLevelForExperience(315));
    }

    @Test
    void spendableXpDerivesBalanceFromLevelAndProgress() {
        assertEquals(27, ExperienceMath.spendableXp(3, 0f));   // getExperienceForLevel(3) == 27
        assertEquals(33, ExperienceMath.spendableXp(3, 0.5f)); // + (int)(0.5 * xpBarCap(3)=13)
        assertEquals(0, ExperienceMath.spendableXp(0, 0f));
        // Transiently inconsistent progress is clamped, never inflates/negates the balance.
        assertEquals(27, ExperienceMath.spendableXp(3, -5f));
        assertEquals(40, ExperienceMath.spendableXp(3, 2f)); // clamp progress to 1 -> 27 + 13
    }

    @Test
    void progressForTotalIsNeverNegativeOrNaN() {
        assertEquals(0f, ExperienceMath.progressForTotal(-50, 0)); // negative total clamps
        assertEquals(0f, ExperienceMath.progressForTotal(0, 0));
        assertTrue(ExperienceMath.progressForTotal(3, 0) > 0f && ExperienceMath.progressForTotal(3, 0) < 1f);
    }

    // --- cost formula ---------------------------------------------------------------------------

    @Test
    void requiredPointsAtMultiplierOnePointOneAcrossSkillLevels() {
        // firstCostLevel = 5, minCost = 1 (never binds here — costs are well above 1).
        assertEquals(6, ExperienceMath.requiredPoints(1, 5, 0.1f, 1));   // round(55 * 0.1 = 5.5) = 6
        assertEquals(49, ExperienceMath.requiredPoints(15, 5, 0.1f, 1)); // round(493 * 0.1)=49
        assertEquals(190, ExperienceMath.requiredPoints(30, 5, 0.1f, 1));// round(1897 * 0.1)=190
        assertEquals(205, ExperienceMath.requiredPoints(31, 5, 0.1f, 1));// round(2045 * 0.1)=205
        assertEquals(220, ExperienceMath.requiredPoints(32, 5, 0.1f, 1));// round(2202 * 0.1)=220
    }

    @Test
    void requiredPointsAtMultiplierOneEqualsVanillaCost() {
        assertEquals(55, ExperienceMath.requiredPoints(1, 5, 1.0f, 1));
        assertEquals(493, ExperienceMath.requiredPoints(15, 5, 1.0f, 1));
        assertEquals(1897, ExperienceMath.requiredPoints(30, 5, 1.0f, 1));
    }

    @Test
    void requiredPointsScalesWithMultiplier() {
        assertEquals(28, ExperienceMath.requiredPoints(1, 5, 0.5f, 1));  // round(27.5)=28
        assertEquals(110, ExperienceMath.requiredPoints(1, 5, 2.0f, 1)); // 55 * 2
    }

    @Test
    void requiredPointsClampsToMinCost() {
        // base getExperienceForLevel(1)=7; round(0.7)=1 which is below minCost 5 -> clamp to 5.
        assertEquals(5, ExperienceMath.requiredPoints(1, 1, 0.1f, 5));
        // minCost 0 permits the raw rounded value, including a genuinely free (0) level-up.
        assertEquals(0, ExperienceMath.requiredPoints(0, 1, 0.1f, 0));
        // A negative minCost is floored at 0, never produces a negative cost.
        assertEquals(0, ExperienceMath.requiredPoints(0, 1, 0.1f, -3));
    }

    // --- affordability decision (the exploit lock) ----------------------------------------------

    @Test
    void enoughDisplayedLevelsButInsufficientPointsIsRejected() {
        // The reported case: skill 30 @ mult 0.1 costs 190 points. A player at experience level 3
        // has only 27 spendable points — even though 3 >= the old "required levels" (3), they cannot
        // afford it. This is the branch that used to pass and drive XP negative.
        int required = ExperienceMath.requiredPoints(30, 5, 0.1f, 1);
        int spendable = ExperienceMath.spendableXp(3, 0f);
        assertEquals(190, required);
        assertEquals(27, spendable);
        assertFalse(SkillLevelUpMath.canAfford(false, spendable, required));
    }

    @Test
    void exactBoundarySucceedsAndOnePointShortFails() {
        assertTrue(SkillLevelUpMath.canAfford(false, 190, 190));
        assertFalse(SkillLevelUpMath.canAfford(false, 189, 190));
    }

    @Test
    void creativeAlwaysAffordsCost() {
        assertTrue(SkillLevelUpMath.canAfford(true, 0, 9999));
    }

    @Test
    void spendingCannotDriveXpNegative() {
        // Deduction path: new total = max(0, spendable - cost). Even a (guarded-against) over-charge
        // resolves to level 0 / 0 progress rather than negative XP.
        int newTotal = Math.max(0, ExperienceMath.spendableXp(3, 0f) - 190);
        assertEquals(0, newTotal);
        assertEquals(0, ExperienceMath.getLevelForExperience(newTotal));
        assertEquals(0f, ExperienceMath.progressForTotal(newTotal, 0));
    }
}
