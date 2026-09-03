package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A configured "3s" is 60 ticks everywhere, and no config value can produce a negative duration. */
class DurationMathTest {

    @Test
    void secondsConvertAtTwentyTicks() {
        assertEquals(60, DurationMath.secondsToTicks(3));
        assertEquals(20, DurationMath.secondsToTicks(1));
        assertEquals(10, DurationMath.secondsToTicks(0.5));
    }

    @Test
    void nonPositiveInputProducesNoDuration() {
        assertEquals(0, DurationMath.secondsToTicks(0));
        assertEquals(0, DurationMath.secondsToTicks(-5));
        assertEquals(0, DurationMath.secondsToTicks(Double.NaN));
    }

    @Test
    void absurdInputSaturatesInsteadOfWrappingNegative() {
        assertEquals(Integer.MAX_VALUE, DurationMath.secondsToTicks(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, DurationMath.secondsToTicks(Double.POSITIVE_INFINITY));
    }

    @Test
    void fractionalSecondsFloorToWholeTicks() {
        assertEquals(70, DurationMath.secondsToTicks(3.5));
        assertEquals(1, DurationMath.secondsToTicks(0.099));
    }
}
