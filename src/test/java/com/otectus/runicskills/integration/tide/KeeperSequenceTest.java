package com.otectus.runicskills.integration.tide;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class KeeperSequenceTest {
    @Test void requiresSpeciesAndHabitatsTogether() {
        var sequence = new KeeperSequence(10);
        assertFalse(sequence.caught(10, Set.of("tide:a"), 0));
        assertFalse(sequence.caught(11, Set.of("tide:a"), 1));
        assertFalse(sequence.caught(12, Set.of("tide:b"), 0));
        assertTrue(sequence.caught(13, Set.of("tide:c"), 0));
    }
    @Test void multipleOutputsCannotInventASecondHabitat() {
        var sequence = new KeeperSequence(0);
        assertFalse(sequence.caught(0, Set.of("tide:a", "tide:b", "tide:c", "tide:d"), 0));
        assertFalse(sequence.caught(1, Set.of(), 1));
        assertTrue(sequence.caught(2, Set.of("tide:d"), 1));
    }
    @Test void exactExpiryAndClockRollbackRejectProgress() {
        var sequence = new KeeperSequence(20);
        assertFalse(sequence.caught(20, Set.of("tide:a", "tide:b", "tide:c"), 0));
        assertFalse(sequence.caught(19, Set.of("tide:a"), 1));
        assertFalse(sequence.caught(20 + KeeperSequence.TICKS, Set.of("tide:a"), 1));
        assertTrue(sequence.caught(19 + KeeperSequence.TICKS, Set.of("tide:a"), 1));
    }
    @Test void invalidObservationsDoNotAdvanceHabitats() {
        var sequence = new KeeperSequence(0);
        assertFalse(sequence.caught(0, Set.of("tide:a", "tide:b", "tide:c"), -1));
        assertFalse(sequence.caught(0, Set.of("tide:a", "tide:b", "tide:c"), 16));
        assertFalse(sequence.caught(0, Set.of(" ", "x".repeat(257)), 1));
        assertFalse(sequence.caught(0, Set.of("tide:a", "tide:b", "tide:c"), 0));
    }
}
