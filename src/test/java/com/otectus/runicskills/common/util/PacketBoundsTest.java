package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down the collection-count validation behind NoticeOverlayCP and CommonConfigSyncCP
 * decoding. Regression for the 1.5.3 review findings: an unchecked {@code readVarInt} count fed
 * straight into an array/list allocation crashes the client decode thread on a negative count
 * and is an allocation DoS on a huge one.
 */
class PacketBoundsTest {

    @Test
    void acceptsZeroAndTypicalCounts() {
        assertTrue(PacketBounds.isCountValid(0, 16));
        assertTrue(PacketBounds.isCountValid(2, 16));
        assertTrue(PacketBounds.isCountValid(16, 16), "cap itself is inclusive");
    }

    @Test
    void rejectsNegativeCounts() {
        assertFalse(PacketBounds.isCountValid(-1, 16), "would throw NegativeArraySizeException");
        assertFalse(PacketBounds.isCountValid(Integer.MIN_VALUE, 16));
    }

    @Test
    void rejectsOversizedCounts() {
        assertFalse(PacketBounds.isCountValid(17, 16));
        assertFalse(PacketBounds.isCountValid(Integer.MAX_VALUE, 65536), "allocation DoS");
    }
}
