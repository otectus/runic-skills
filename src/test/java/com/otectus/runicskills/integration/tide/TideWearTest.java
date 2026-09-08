package com.otectus.runicskills.integration.tide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TideWearTest {
    @Test void catchAndOrdinaryAvoidanceNeverExceedTheOverallBudget() {
        assertEquals(0,TideWear.additionalChance(.35,.90));
        assertEquals(.35,TideWear.additionalChance(.7,0));
        assertEquals(0,TideWear.additionalChance(Double.NaN,.2));
        assertEquals(0,TideWear.additionalChance(.2,Double.POSITIVE_INFINITY));
        for (int b=0;b<=90;b++) for (int c=0;c<=100;c++) {
            double ordinary=b/100.0,extra=TideWear.additionalChance(c/100.0,ordinary);
            assertTrue(extra>=0 && extra<=.35);
            assertTrue(extra+(1-extra)*ordinary<=.90+1e-12);
        }
    }
    @Test void oneRollCanOnlySpareOnePointAndCapsTheCombinedChance() {
        assertEquals(1, TideWear.reduce(2, .20, .19));
        assertEquals(2, TideWear.reduce(2, .20, .20));
        assertEquals(1, TideWear.reduce(2, .90, .349));
        assertEquals(2, TideWear.reduce(2, .90, .35));
        assertEquals(0, TideWear.reduce(0, .35, 0));
        assertEquals(-1, TideWear.reduce(-1, .35, 0));
    }
    @Test void InvalidRollsCannotGrantConservation() {
        for (double roll : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 1})
            assertEquals(2, TideWear.reduce(2, .35, roll));
        assertEquals(2, TideWear.reduce(2, Double.NaN, 0));
        assertEquals(2, TideWear.reduce(2, -.1, 0));
    }
}
