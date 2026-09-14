package com.otectus.runicskills.common.inventory;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class StackCapacityMathTest {
    @Test void randomizedTransfersConserveIncludingOverLimitDestinations() {
        Random random = new Random(220);
        for (int trial = 0; trial < 10000; trial++) {
            int source = random.nextInt(1025), target = random.nextInt(300), limit = 64 * (1 + random.nextInt(4));
            int moved = StackCapacityMath.insertable(source, target, limit);
            assertEquals(source + target, source - moved + target + moved);
            assertTrue(moved >= 0 && moved <= source);
            assertTrue(target >= limit ? moved == 0 : target + moved <= limit);
        }
        assertEquals(0, StackCapacityMath.insertable(256, 192, 64));
        assertEquals(1, StackCapacityMath.insertable(Integer.MAX_VALUE, 255, 256));
    }
    @Test void specialItemsAndForeignCapacitiesRemainNative() {
        assertEquals(128, StackCapacityMath.capacity(64, true, 1));
        assertEquals(192, StackCapacityMath.capacity(64, true, 2));
        assertEquals(256, StackCapacityMath.capacity(64, true, 3));
        for (int natural : new int[]{1,16,512}) assertEquals(natural, StackCapacityMath.capacity(natural, true, 3));
        assertEquals(64, StackCapacityMath.capacity(64, false, 3));
    }
}
