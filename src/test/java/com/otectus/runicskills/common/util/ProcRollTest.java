package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down the shared perk-proc roll.
 *
 * <p>Six call sites passed a raw config value to {@code ThreadLocalRandom.nextInt(bound)}, which
 * throws for {@code bound <= 0}. A pack author writing {@code 0} to mean "never" therefore turned
 * every craft — or container open, or kill — into an exception thrown out of a Forge event
 * handler, breaking crafting server-wide for players who had never taken the perk (RS-029).
 * Separately, the sites compared the roll against {@code 1}, so a configured 1-in-1 chance could
 * never fire, because {@code nextInt(1)} only ever returns {@code 0} (RS-154).
 */
class ProcRollTest {

    // --- RS-029: a non-positive bound must never throw -------------------------------------------

    @Test
    void nonPositiveBoundsCollapseToOne() {
        assertEquals(1, ProcRoll.bound(0), "0 is the natural way to write 'always', not a crash");
        assertEquals(1, ProcRoll.bound(-1));
        assertEquals(1, ProcRoll.bound(-9999));
        assertEquals(1, ProcRoll.bound(Double.NaN));
    }

    @Test
    void rollingANonPositiveBoundDoesNotThrow() {
        for (int i = 0; i < 100; i++) {
            assertTrue(ProcRoll.rolls(0), "bound 0 normalises to 1, which always succeeds");
            assertTrue(ProcRoll.rolls(-5));
        }
    }

    // --- RS-154: 1-in-1 must mean "always", not "never" ------------------------------------------

    @Test
    void oneInOneAlwaysProcs() {
        for (int i = 0; i < 100; i++) {
            assertTrue(ProcRoll.rolls(1));
        }
    }

    @Test
    void largeBoundsAreHonouredAndClamped() {
        assertEquals(500, ProcRoll.bound(500));
        assertEquals(Integer.MAX_VALUE, ProcRoll.bound(Double.MAX_VALUE), "no overflow to a negative bound");
    }

    @Test
    void aWideBoundProcsSometimesButNotAlways() {
        int hits = 0;
        for (int i = 0; i < 5000; i++) {
            if (ProcRoll.rolls(4)) hits++;
        }
        assertTrue(hits > 0, "a 1-in-4 chance must fire sometimes");
        assertTrue(hits < 5000, "a 1-in-4 chance must not fire every time");
    }

    // --- percentage rolls ------------------------------------------------------------------------

    @Test
    void chance01NormalisesAPercentageToAProbability() {
        assertEquals(0.10D, ProcRoll.chance01(10.0D), 1.0e-9, "10% is 0.10, not 10");
        assertEquals(0.155D, ProcRoll.chance01(15.5D), 1.0e-9);
    }

    @Test
    void chance01ClampsAtBothEnds() {
        assertEquals(1.0D, ProcRoll.chance01(150.0D), 0.0D, "no perk composition may exceed certainty");
        assertEquals(0.0D, ProcRoll.chance01(-5.0D), 0.0D, "a negative percentage is not a negative chance");
        assertEquals(0.0D, ProcRoll.chance01(0.0D), 0.0D);
    }

    @Test
    void chance01RejectsNonFiniteConfiguration() {
        assertEquals(0.0D, ProcRoll.chance01(Double.NaN), 0.0D);
        assertEquals(0.0D, ProcRoll.chance01(Double.POSITIVE_INFINITY), 0.0D);
        assertEquals(0.0D, ProcRoll.chance01(Double.NEGATIVE_INFINITY), 0.0D);
    }

    @Test
    void percentEndpointsAreAbsolute() {
        for (int i = 0; i < 100; i++) {
            assertTrue(ProcRoll.rollsPercent(100.0), "100% always");
            assertTrue(ProcRoll.rollsPercent(250.0), "above 100% always");
            assertFalse(ProcRoll.rollsPercent(0.0), "0% never");
            assertFalse(ProcRoll.rollsPercent(-10.0), "negative never");
        }
    }
}
