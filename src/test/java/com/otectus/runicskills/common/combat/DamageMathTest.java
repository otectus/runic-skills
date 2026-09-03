package com.otectus.runicskills.common.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A damage figure a handler cannot use never reaches the event.
 *
 * <p>The values below are not hypothetical: every percentage in this mod is read from a
 * hand-editable config and divided by 100 before being multiplied onto a hit, so a blank field, a
 * reduction over 100 and a multiplier with too many zeroes each produce one of them. A {@code NaN}
 * that reaches {@code setAmount} becomes {@code NaN} health, which is an entity that cannot die
 * and a save that fails to load later — far away from the config value that caused it.
 */
class DamageMathTest {

    private static final float ORIGINAL = 7.5F;

    @Test
    void aFiniteNonNegativeResultIsUsed() {
        assertEquals(11.25F, DamageMath.safeAmount(ORIGINAL, 11.25D));
    }

    @Test
    void zeroIsAValidResult() {
        assertEquals(0.0F, DamageMath.safeAmount(ORIGINAL, 0.0D));
    }

    @Test
    void notANumberKeepsTheOriginal() {
        assertEquals(ORIGINAL, DamageMath.safeAmount(ORIGINAL, Double.NaN));
    }

    @Test
    void positiveInfinityKeepsTheOriginal() {
        assertEquals(ORIGINAL, DamageMath.safeAmount(ORIGINAL, Double.POSITIVE_INFINITY));
    }

    @Test
    void negativeInfinityKeepsTheOriginal() {
        assertEquals(ORIGINAL, DamageMath.safeAmount(ORIGINAL, Double.NEGATIVE_INFINITY));
    }

    @Test
    void aNegativeResultKeepsTheOriginal() {
        assertEquals(ORIGINAL, DamageMath.safeAmount(ORIGINAL, -1.0D));
    }

    /** Finite as a double, infinite as a float — the cast is checked, not just the input. */
    @Test
    void aValueTooLargeForAFloatKeepsTheOriginal() {
        assertEquals(ORIGINAL, DamageMath.safeAmount(ORIGINAL, 1.0E300D));
    }
}
