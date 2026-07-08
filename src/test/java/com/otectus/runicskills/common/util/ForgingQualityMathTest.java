package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tier math for Overgeared's "ForgingQuality" NBT string. The critical invariants: upgrades are
 * hard-capped at perfect (Masterwork stays exclusive to Overgeared's own path), and unknown values
 * from a future Overgeared version mean "no change", never tag corruption.
 */
class ForgingQualityMathTest {

    @Test
    void nextQualityClimbsOneTier() {
        assertEquals("well", ForgingQualityMath.nextQuality("poor"));
        assertEquals("expert", ForgingQualityMath.nextQuality("well"));
        assertEquals("perfect", ForgingQualityMath.nextQuality("expert"));
    }

    @Test
    void nextQualityNeverGrantsMasterOrChangesTerminalTiers() {
        assertNull(ForgingQualityMath.nextQuality("perfect"), "perfect must not upgrade to master");
        assertNull(ForgingQualityMath.nextQuality("master"));
        assertNull(ForgingQualityMath.nextQuality("none"));
    }

    @Test
    void nextQualityIsSafeOnUnknownInput() {
        assertNull(ForgingQualityMath.nextQuality(null));
        assertNull(ForgingQualityMath.nextQuality(""));
        assertNull(ForgingQualityMath.nextQuality("legendary")); // hypothetical future tier
    }

    @Test
    void nextQualityNormalizesCaseAndWhitespace() {
        assertEquals("well", ForgingQualityMath.nextQuality(" Poor "));
        assertEquals("perfect", ForgingQualityMath.nextQuality("EXPERT"));
    }

    @Test
    void mitigatePoorOnlyTouchesPoor() {
        assertEquals("well", ForgingQualityMath.mitigatePoor("poor"));
        assertEquals("well", ForgingQualityMath.mitigatePoor(" POOR "));
        assertNull(ForgingQualityMath.mitigatePoor("well"));
        assertNull(ForgingQualityMath.mitigatePoor("expert"));
        assertNull(ForgingQualityMath.mitigatePoor("perfect"));
        assertNull(ForgingQualityMath.mitigatePoor("master"));
        assertNull(ForgingQualityMath.mitigatePoor(null));
    }
}
