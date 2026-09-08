package com.otectus.runicskills.integration.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuardBudgetTest {
    @Test void refreshUsesTheGreaterBalanceAndBoundedExpiry() {
        var pool = new GuardBudget();
        assertTrue(pool.refresh(3, 60, 100));
        assertEquals(0, pool.consume(1, pool.grantId(), 101));
        pool.refresh(1, 40, 110);
        assertEquals(2, pool.remaining(110));
        assertEquals(50, pool.ticksLeft(110));
        pool.refresh(50, 99999, 120);
        assertEquals(4, pool.remaining(120));
        assertEquals(160, pool.ticksLeft(120));
        assertEquals(0, pool.remaining(280));
        pool.refresh(1, 20, 300);
        assertEquals(1, pool.remaining(300));
    }
    @Test void consumptionConservesDamageAndNeverOverdraws() {
        for (int d = 0; d <= 100; d++) {
            var pool = new GuardBudget();
            pool.refresh(4, 60, 0);
            float damage = d / 10f;
            float result = pool.consume(damage, pool.grantId(), 1);
            assertEquals(damage, result + 4 - pool.remaining(1), .00001f);
            assertTrue(result >= 0 && pool.remaining(1) >= 0);
            assertEquals(10 - pool.remaining(1), pool.consume(10, pool.grantId(), 2), .00001f);
        }
    }
    @Test void callbackRefreshDoesNotProtectTheTriggeringHit() {
        var pool = new GuardBudget();
        pool.refresh(2, 60, 0);
        long incoming = pool.grantId();
        pool.refresh(4, 60, 1);
        assertEquals(5, pool.consume(5, incoming, 1));
        assertEquals(4, pool.remaining(1));
        assertEquals(1, pool.consume(5, pool.grantId(), 2));
    }
    @Test void invalidOrExpiredInputCannotSpendOrGrant() {
        var pool = new GuardBudget();
        for (float points : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1, 0})
            assertFalse(pool.refresh(points, 60, 0));
        assertFalse(pool.refresh(1, 0, 0));
        pool.refresh(3, 60, 0);
        assertEquals(-1, pool.consume(-1, pool.grantId(), 1));
        assertTrue(Float.isNaN(pool.consume(Float.NaN, pool.grantId(), 1)));
        assertEquals(3, pool.remaining(1));
        assertEquals(2, pool.consume(2, pool.grantId(), 60));
        assertEquals(0, pool.ticksLeft(60));
    }
}
