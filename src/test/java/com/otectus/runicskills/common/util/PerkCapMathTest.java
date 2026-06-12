package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Headless tests for {@link PerkCapMath#computeEffectiveCap(int, int, float)} — the pure math behind
 * the combined flat ({@code maxActivePerks}) + scaled ({@code perksPerGlobalLevel}) active-perk cap.
 * Convention under test: {@code 0} = unlimited; when both caps are active the smaller non-zero wins;
 * a derived (scaled) cap of {@code 0} is "not applicable" and never overrides the flat cap.
 */
class PerkCapMathTest {

    @Test
    void bothDisabledIsUnlimited() {
        assertEquals(0, PerkCapMath.computeEffectiveCap(0, 100, 0.0f));
        assertEquals(0, PerkCapMath.computeEffectiveCap(0, 0, 0.0f));
        // negative inputs are treated as disabled too
        assertEquals(0, PerkCapMath.computeEffectiveCap(-5, 100, -1.0f));
    }

    @Test
    void onlyFlatCapActive() {
        assertEquals(5, PerkCapMath.computeEffectiveCap(5, 100, 0.0f));
        assertEquals(5, PerkCapMath.computeEffectiveCap(5, 0, 0.0f));
    }

    @Test
    void onlyScaledCapActive() {
        // floor(200 * 0.05) = 10
        assertEquals(10, PerkCapMath.computeEffectiveCap(0, 200, 0.05f));
        // floor(33 * 0.1) = 3
        assertEquals(3, PerkCapMath.computeEffectiveCap(0, 33, 0.1f));
    }

    @Test
    void derivedCapFloors() {
        // floor(19 * 0.1) = 1 (not 1.9)
        assertEquals(1, PerkCapMath.computeEffectiveCap(0, 19, 0.1f));
        // floor(9 * 0.1) = 0 -> scaled "not applicable", both disabled -> unlimited
        assertEquals(0, PerkCapMath.computeEffectiveCap(0, 9, 0.1f));
    }

    @Test
    void bothActiveSmallerNonZeroWins() {
        // flat 5, scaled floor(200*0.05)=10 -> min = 5
        assertEquals(5, PerkCapMath.computeEffectiveCap(5, 200, 0.05f));
        // flat 8, scaled floor(60*0.05)=3 -> min = 3
        assertEquals(3, PerkCapMath.computeEffectiveCap(8, 60, 0.05f));
        // flat 4, scaled equal floor(80*0.05)=4 -> 4
        assertEquals(4, PerkCapMath.computeEffectiveCap(4, 80, 0.05f));
    }

    @Test
    void scaledZeroNeverOverridesFlat() {
        // global level too low for a scaled slot -> flat cap still applies, not 0/unlimited
        assertEquals(6, PerkCapMath.computeEffectiveCap(6, 5, 0.05f));
    }
}
