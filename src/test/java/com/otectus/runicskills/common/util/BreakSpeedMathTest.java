package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The break-speed delta reads as the multiplier the tooltip promises once added to the original
 * speed — the double-count in MEDIUM-01 was invisible at attribute 0 and only diverged above it.
 */
class BreakSpeedMathTest {

    @Test
    void attributeReadsAsAMultiplierOfTheOriginalSpeed() {
        float original = 4.0F;
        assertEquals(1.00F * original, original + BreakSpeedMath.delta(original, 0.0), 1e-5F);
        assertEquals(1.25F * original, original + BreakSpeedMath.delta(original, 0.25), 1e-5F);
        assertEquals(1.50F * original, original + BreakSpeedMath.delta(original, 0.5), 1e-5F);
    }

    @Test
    void negativeOrNonFiniteAttributeNeverSlowsThePlayer() {
        assertEquals(0.0F, BreakSpeedMath.delta(4.0F, -1.0), 1e-5F);
        assertEquals(0.0F, BreakSpeedMath.delta(4.0F, Double.NaN), 1e-5F);
    }

    @Test
    void zeroOriginalSpeedStaysZero() {
        assertEquals(0.0F, BreakSpeedMath.delta(0.0F, 5.0), 1e-5F);
    }
}
