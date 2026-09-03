package com.otectus.runicskills.common.durability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The three conversions in {@link DurabilityMath}, and in particular the identity the Precision
 * Tools one exists for.
 *
 * <p>"Avoid X% of durability points" and "gain X% durability" are not the same statement, and the
 * six perks this class was written for spent 2.0.4 implementing neither of them. The identity test
 * below is the one that matters: whatever avoidance probability the conversion returns must make
 * the expected lifetime {@code 1/(1-p)} come out at exactly {@code 1 + X/100}.
 */
class DurabilityMathTest {

    // --- avoidance conversion --------------------------------------------------------------------

    @Test
    void avoidanceIsPercentOverOneHundredPlusPercent() {
        assertEquals(15.0 / 115.0, DurabilityMath.bonusDurabilityToAvoidance(15), 1e-12);
        assertEquals(0.5, DurabilityMath.bonusDurabilityToAvoidance(100), 1e-12);
        assertEquals(10.0 / 110.0, DurabilityMath.bonusDurabilityToAvoidance(10), 1e-12);
    }

    @Test
    void nonPositiveAndNonFiniteConfigurationsAvoidNothing() {
        assertEquals(0.0, DurabilityMath.bonusDurabilityToAvoidance(0));
        assertEquals(0.0, DurabilityMath.bonusDurabilityToAvoidance(-15));
        assertEquals(0.0, DurabilityMath.bonusDurabilityToAvoidance(Double.NaN));
        assertEquals(0.0, DurabilityMath.bonusDurabilityToAvoidance(Double.POSITIVE_INFINITY));
    }

    /** The whole point of the conversion: expected lifetime 1/(1-p) is exactly 1 + X/100. */
    @Test
    void avoidanceYieldsExactlyThePromisedExtraDurability() {
        for (int percent : new int[]{5, 10, 15, 25, 50, 100, 300}) {
            double p = DurabilityMath.bonusDurabilityToAvoidance(percent);
            assertEquals(1.0 + percent / 100.0, 1.0 / (1.0 - p), 1e-12,
                    "avoidance for +" + percent + "% must give that much extra lifetime");
        }
    }

    // --- scaled maximum damage -------------------------------------------------------------------

    @Test
    void scaledMaxDamageFloorsTheProduct() {
        // A diamond pickaxe (1561) stamped at the default 15%: floor(1561 * 1.15) = 1795.
        assertEquals(1795, DurabilityMath.scaledMaxDamage(1561, 15));
        assertEquals(3122, DurabilityMath.scaledMaxDamage(1561, 100));
        assertEquals(64, DurabilityMath.scaledMaxDamage(59, 10)); // floor(64.9)
    }

    @Test
    void aMaximumOfZeroStaysZeroAndNeverShrinks() {
        assertEquals(0, DurabilityMath.scaledMaxDamage(0, 15));
        assertEquals(1561, DurabilityMath.scaledMaxDamage(1561, 0));
        assertEquals(1561, DurabilityMath.scaledMaxDamage(1561, -50));
    }

    @Test
    void anEnormousPercentageSaturatesInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE,
                DurabilityMath.scaledMaxDamage(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    // --- stamping ---------------------------------------------------------------------------------

    @Test
    void stampingTakesTheMaximumAndNeverAdds() {
        assertEquals(10, DurabilityMath.stampValue(0, 10));
        assertEquals(10, DurabilityMath.stampValue(10, 10));  // re-repairing is a no-op
        assertEquals(15, DurabilityMath.stampValue(10, 15));  // a raised config value applies
        assertEquals(15, DurabilityMath.stampValue(15, 10));  // a lowered one does not remove
    }
}
