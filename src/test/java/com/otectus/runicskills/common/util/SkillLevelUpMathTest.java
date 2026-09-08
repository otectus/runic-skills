package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks down the skill-level-cap gate used by {@code SkillLevelUpSP}. Regression for the 1.5.3
 * review finding: at-cap level-up packets consumed XP and fired events even though the storage
 * clamp made the level-up itself a no-op. The gate applies to everyone — creative mode bypasses
 * the XP cost, never the cap.
 */
class SkillLevelUpMathTest {

    @Test
    void belowCapAllows() {
        assertTrue(SkillLevelUpMath.canLevelUp(0, 32));
        assertTrue(SkillLevelUpMath.canLevelUp(31, 32));
    }

    @Test
    void atCapRejects() {
        assertFalse(SkillLevelUpMath.canLevelUp(32, 32));
    }

    @Test
    void aboveCapRejects() {
        // A cap lowered after levels were earned (config edit / /globallimit) must freeze, not grow.
        assertFalse(SkillLevelUpMath.canLevelUp(40, 32));
    }

    @Test
    void nonPositiveCapRejectsEverything() {
        // Matches the storage clamp Math.min(level, maxLevel): cap <= 0 already made leveling a
        // no-op, so the gate rejects rather than treating 0 as "unlimited".
        assertFalse(SkillLevelUpMath.canLevelUp(0, 0));
        assertFalse(SkillLevelUpMath.canLevelUp(5, -1));
    }

    @Test
    void smallCapsAndNonMultiplesDoNotAwardMasterRankEarly() {
        assertEquals(2, SkillLevelUpMath.rankIndex(1, 3));
        assertEquals(5, SkillLevelUpMath.rankIndex(2, 3));
        assertEquals(8, SkillLevelUpMath.rankIndex(3, 3));
        assertEquals(7, SkillLevelUpMath.rankIndex(32, 33));
        assertEquals(8, SkillLevelUpMath.rankIndex(33, 33));
    }

    @Test
    void rankBandsRemainBoundedForChangedOrInvalidCaps() {
        assertEquals(0, SkillLevelUpMath.rankIndex(-1, 32));
        assertEquals(0, SkillLevelUpMath.rankIndex(1, 0));
        assertEquals(8, SkillLevelUpMath.rankIndex(33, 32));
        assertEquals(8, SkillLevelUpMath.rankIndex(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
