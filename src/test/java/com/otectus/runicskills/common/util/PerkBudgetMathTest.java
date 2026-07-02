package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the earned-global-level perk budget math ({@link PerkCapMath#computeEffectiveCap}).
 *
 * <p>The runtime feeds {@code computeEffectiveCap} the player's <em>earned</em> global level
 * (total skill levels above the starting baseline), so these tests use earned levels directly:
 * a fresh player is earned-level 0, and {@code 0.5} perks/level grants the first slot at earned 2
 * and three slots at earned 6 — matching the documented example.</p>
 */
class PerkBudgetMathTest {

    private static int scaled(int earnedLevel, float perScale) {
        // flat disabled (0), no ceiling (0) -> isolates the scaled cap.
        return PerkCapMath.computeEffectiveCap(0, earnedLevel, perScale, 0);
    }

    @Test
    void ratioZeroIsLegacyUnlimited() {
        // perksPerGlobalLevel = 0 -> scaled cap disabled. With no flat cap either, 0 = unlimited.
        assertEquals(0, scaled(0, 0.0f));
        assertEquals(0, scaled(100, 0.0f));
    }

    @Test
    void halfPerEarnedLevelMatchesDocumentedExample() {
        assertEquals(0, scaled(0, 0.5f),  "fresh player (earned 0) -> 0 perks");
        assertEquals(0, scaled(1, 0.5f),  "floor(1*0.5)=0");
        assertEquals(1, scaled(2, 0.5f),  "floor(2*0.5)=1 -> first slot at earned 2");
        assertEquals(3, scaled(6, 0.5f),  "floor(6*0.5)=3 -> matches the example");
        assertEquals(50, scaled(100, 0.5f));
    }

    @Test
    void quarterPerEarnedLevelFloors() {
        assertEquals(0, scaled(0, 0.25f));
        assertEquals(0, scaled(1, 0.25f));   // floor(0.25)
        assertEquals(0, scaled(2, 0.25f));   // floor(0.5)
        assertEquals(1, scaled(6, 0.25f));   // floor(1.5)
        assertEquals(25, scaled(100, 0.25f));
    }

    @Test
    void onePerEarnedLevelIsLinear() {
        assertEquals(1, scaled(1, 1.0f));
        assertEquals(2, scaled(2, 1.0f));
        assertEquals(6, scaled(6, 1.0f));
        assertEquals(100, scaled(100, 1.0f));
    }

    @Test
    void smallerNonZeroCapWinsWhenBothActive() {
        // flat=2, scaled at earned 100 * 0.5 = 50 -> flat is smaller and binds.
        assertEquals(2, PerkCapMath.computeEffectiveCap(2, 100, 0.5f, 0));
        // flat=10, scaled at earned 6 * 0.5 = 3 -> scaled is smaller and binds.
        assertEquals(3, PerkCapMath.computeEffectiveCap(10, 6, 0.5f, 0));
    }

    @Test
    void derivedZeroNeverOverridesFlatCap() {
        // earned 1 * 0.5 floors to 0; the flat cap must still apply rather than locking out all perks.
        assertEquals(5, PerkCapMath.computeEffectiveCap(5, 1, 0.5f, 0));
    }

    @Test
    void maxCapClampsScaledButNotFlat() {
        // scaled at earned 100 * 0.5 = 50, clamped to 10.
        assertEquals(10, PerkCapMath.computeEffectiveCap(0, 100, 0.5f, 10));
        // ceiling does not relax a smaller flat cap.
        assertEquals(4, PerkCapMath.computeEffectiveCap(4, 100, 0.5f, 10));
        // below the ceiling the scaled value passes through unchanged.
        assertEquals(3, PerkCapMath.computeEffectiveCap(0, 6, 0.5f, 10));
    }

    @Test
    void maxCapOfZeroMeansNoCeiling() {
        assertEquals(50, PerkCapMath.computeEffectiveCap(0, 100, 0.5f, 0));
    }

    @Test
    void negativeEarnedLevelIsHarmless() {
        // The runtime clamps earned level at 0, but guard the math anyway.
        assertEquals(0, scaled(-5, 0.5f));
    }
}
