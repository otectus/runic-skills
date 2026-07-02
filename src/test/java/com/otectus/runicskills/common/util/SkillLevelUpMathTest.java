package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
