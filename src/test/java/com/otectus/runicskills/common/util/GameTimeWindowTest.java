package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A missing or impossible stamp reads as "infinitely long ago", never as "just now". */
class GameTimeWindowTest {

    @Test
    void absentStampIsInfinitelyLongAgo() {
        assertEquals(Long.MAX_VALUE, GameTimeWindow.elapsed(1000L, null));
        assertTrue(GameTimeWindow.ready(1000L, null, 200L));
        assertFalse(GameTimeWindow.within(1000L, null, 200L));
    }

    @Test
    void stampFromTheFutureIsTreatedAsAbsent() {
        // A world loaded with a shorter game time than the one the stamp was taken in.
        assertEquals(Long.MAX_VALUE, GameTimeWindow.elapsed(0L, 72000L));
        assertTrue(GameTimeWindow.ready(0L, 72000L, 200L));
        assertFalse(GameTimeWindow.within(0L, 72000L, 200L));
    }

    @Test
    void elapsedIsPlainSubtractionWhenTheStampIsUsable() {
        assertEquals(0L, GameTimeWindow.elapsed(500L, 500L));
        assertEquals(300L, GameTimeWindow.elapsed(500L, 200L));
    }

    @Test
    void boundariesAreInclusiveOnBothSides() {
        assertTrue(GameTimeWindow.ready(300L, 100L, 200L));
        assertFalse(GameTimeWindow.ready(299L, 100L, 200L));
        assertTrue(GameTimeWindow.within(300L, 100L, 200L));
        assertFalse(GameTimeWindow.within(301L, 100L, 200L));
    }

    @Test
    void theOldMinValueSentinelNoLongerOverflowsIntoRecent() {
        assertEquals(Long.MAX_VALUE, GameTimeWindow.elapsed(0L, Long.MIN_VALUE));
        assertTrue(GameTimeWindow.ready(0L, Long.MIN_VALUE, 200L));
    }
}
