package com.otectus.runicskills.integration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArsSpellMathTest {
    @Test void bridgesTransferOnlyEarnedSchoolBonus() {
        assertEquals(1.0, ArsSpellMath.schoolBridgeMultiplier(1.0, 50));
        assertEquals(1.1, ArsSpellMath.schoolBridgeMultiplier(1.2, 50), 1e-10);
        assertEquals(1.0, ArsSpellMath.schoolBridgeMultiplier(.5, 50));
        assertEquals(1.0, ArsSpellMath.schoolBridgeMultiplier(Double.NaN, 50));
    }

    @Test void reductionsDoNotChargeFreeSpellsAndScholarCannotOverflow() {
        assertEquals(0, ArsSpellMath.discountedCost(0, 20, 1));
        assertEquals(0, ArsSpellMath.discountedCost(-10, 20, 1));
        assertEquals(80, ArsSpellMath.discountedCost(100, 20, 1));
        assertEquals(1, ArsSpellMath.discountedCost(1, 99, 1));
        assertEquals(0, ArsSpellMath.discountedCost(100, 100, 0));
        assertEquals(0, ArsSpellMath.scholarlyCost(0, 10, 1));
        assertEquals(1, ArsSpellMath.scholarlyCost(100, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(80, ArsSpellMath.scholarlyCost(100, 4, 5));
    }

    @Test void backflowUsesActualCommittedLossAndNoGainOrFreePayment() {
        assertEquals(2f, ArsSpellMath.paidRefund(100, 80, 10));
        assertEquals(.2f, ArsSpellMath.paidRefund(2, 0, 10));
        assertEquals(0f, ArsSpellMath.paidRefund(100, 100, 10));
        assertEquals(0f, ArsSpellMath.paidRefund(100, 120, 10));
        assertEquals(0f, ArsSpellMath.paidRefund(100, -10, 10));
        assertEquals(0f, ArsSpellMath.paidRefund(Double.NaN, 0, 10));
        assertEquals(0f, ArsSpellMath.paidRefund(100, 0, Double.POSITIVE_INFINITY));
        assertEquals(20f, ArsSpellMath.paidRefund(100, 80, 500));
    }
}
