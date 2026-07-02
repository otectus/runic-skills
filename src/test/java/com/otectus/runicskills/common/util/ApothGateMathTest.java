package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Apotheosis affix-gate decision ({@link ApothGateMath#decide}).
 *
 * <p>Regression for the "Requires Fortune 0" bug: a required level of 0 (common/ungated rarity,
 * no affixes, or gating disabled) must NEVER block or surface as a requirement, and an unmapped
 * rarity must report as "unconfigured" rather than as a bogus numeric level.</p>
 */
class ApothGateMathTest {

    @Test
    void zeroRequirementNeverBlocks() {
        ApothGateMath.Decision d = ApothGateMath.decide(0, 0);
        assertFalse(d.blocks(), "required 0 must allow (no 'Fortune 0' lock)");
        assertEquals(ApothGateMath.Outcome.ALLOW, d.outcome());
    }

    @Test
    void negativeRequirementNeverBlocks() {
        assertFalse(ApothGateMath.decide(-1, 0).blocks());
    }

    @Test
    void gatedWhenFortuneBelowRequirement() {
        ApothGateMath.Decision d = ApothGateMath.decide(10, 5);
        assertTrue(d.blocks());
        assertEquals(ApothGateMath.Outcome.GATED, d.outcome());
        assertEquals(10, d.requiredLevel(), "message must show the real required level, not 0");
    }

    @Test
    void allowedWhenFortuneMeetsRequirement() {
        assertFalse(ApothGateMath.decide(10, 10).blocks());
        assertFalse(ApothGateMath.decide(10, 12).blocks());
    }

    @Test
    void unmappedRarityIsReportedDistinctly() {
        ApothGateMath.Decision d = ApothGateMath.decide(ApothGateMath.UNMAPPED, 5);
        assertTrue(d.blocks(), "unmapped rarity default-denies");
        assertEquals(ApothGateMath.Outcome.UNMAPPED_RARITY, d.outcome(),
                "unmapped must not masquerade as a numeric Fortune requirement");
    }

    @Test
    void unmappedRarityIsNotBypassedByHugeFortune() {
        // Even an (impossible) maxed Fortune does not satisfy an unconfigured rarity.
        ApothGateMath.Decision d = ApothGateMath.decide(ApothGateMath.UNMAPPED, Integer.MAX_VALUE);
        assertTrue(d.blocks());
        assertEquals(ApothGateMath.Outcome.UNMAPPED_RARITY, d.outcome());
    }
}
