package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One diagnostic per offender, and a full key set never means silence. */
class LogOnceTest {

    private static LogOnce fresh() {
        return LogOnce.with(LoggerFactory.getLogger(LogOnceTest.class));
    }

    @Test
    void theFirstCallForAKeyLogsAndLaterOnesDoNot() {
        LogOnce log = fresh();
        assertTrue(log.shouldLog("minecraft:oak_planks"));
        assertFalse(log.shouldLog("minecraft:oak_planks"));
        assertFalse(log.shouldLog("minecraft:oak_planks"));
    }

    @Test
    void keysAreIndependent() {
        LogOnce log = fresh();
        assertTrue(log.shouldLog("a"));
        assertTrue(log.shouldLog("b"));
        assertFalse(log.shouldLog("a"));
    }

    @Test
    void instancesDoNotShareTheirKeySet() {
        assertTrue(fresh().shouldLog("same"));
        assertTrue(fresh().shouldLog("same"));
    }

    /**
     * The bound stops the set from growing, not the logging. A server that has produced this many
     * distinct warnings has a real problem, and hiding the next one would be the wrong trade.
     */
    @Test
    void aFullKeySetStillLogsButStopsRemembering() {
        LogOnce log = fresh();
        for (int i = 0; i < LogOnce.MAX_KEYS; i++) {
            assertTrue(log.shouldLog("key" + i), "key" + i + " was not the first of its kind");
        }
        assertFalse(log.shouldLog("key0"), "a remembered key must stay suppressed");
        assertTrue(log.shouldLog("overflow"));
        assertTrue(log.shouldLog("overflow"), "an unremembered key cannot be suppressed");
    }

    @Test
    void aNullKeyAlwaysLogs() {
        LogOnce log = fresh();
        assertTrue(log.shouldLog(null));
        assertTrue(log.shouldLog(null));
    }
}
