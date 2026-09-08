package com.otectus.runicskills.integration.tconstruct;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TConstructRepairMathTest {
    @Test
    void halfRateRepairCanFinishAToolWhenTheOfferIsLargeEnough() {
        assertEquals(4, TConstructRepairMath.offeredRepair(10, 4, 0.5));
        assertEquals(2, TConstructRepairMath.offeredRepair(4, 10, 0.5));
    }

    @Test
    void offeredRepairRejectsInvalidFactorsAndSaturatesLargeInputs() {
        assertEquals(0, TConstructRepairMath.offeredRepair(10, 10, Double.NaN));
        assertEquals(0, TConstructRepairMath.offeredRepair(10, 10, Double.POSITIVE_INFINITY));
        assertEquals(0, TConstructRepairMath.offeredRepair(10, 10, 0));
        assertEquals(10, TConstructRepairMath.offeredRepair(Integer.MAX_VALUE, 10, Double.MAX_VALUE));
    }

    @Test
    void paidShareIsBoundedByNativeRestorationAndRemainingDamage() {
        assertEquals(5, TConstructRepairMath.paidBonus(10, 100, 0.5));
        assertEquals(3, TConstructRepairMath.paidBonus(10, 3, 0.5));
        assertEquals(0, TConstructRepairMath.paidBonus(0, 100, 0.5));
        assertEquals(0, TConstructRepairMath.paidBonus(10, 10, Double.NaN));
    }
}
