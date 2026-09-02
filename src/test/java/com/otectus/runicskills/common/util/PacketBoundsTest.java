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

    // -- Content ids (RS10-020) ----------------------------------------------------------------
    //
    // Six server-bound action packets decoded an id with readUtf()'s 32,767-character default, on
    // the network thread, before the rate limiter ran — so a client could make the server allocate
    // 32 KB per packet at will. These bound and validate what an id may be.

    @Test
    void acceptsTheIdShapesThisModActuallyUses() {
        assertTrue(PacketBounds.isContentIdValid("berserker"), "bare registry path");
        assertTrue(PacketBounds.isContentIdValid("runicskills:the_apocrypha_awakens"), "full id");
        assertTrue(PacketBounds.isContentIdValid("some_addon:powers/tier3/crown"), "slashes in path");
        assertTrue(PacketBounds.isContentIdValid("mod-1.2:thing.with-dots"));
    }

    @Test
    void rejectsBlankAndOversizedIds() {
        assertFalse(PacketBounds.isContentIdValid(null));
        assertFalse(PacketBounds.isContentIdValid(""));
        assertTrue(PacketBounds.isContentIdValid("a".repeat(PacketBounds.MAX_CONTENT_ID_CHARS)),
                "the cap itself is inclusive");
        assertFalse(PacketBounds.isContentIdValid("a".repeat(PacketBounds.MAX_CONTENT_ID_CHARS + 1)));
        assertFalse(PacketBounds.isContentIdValid("a".repeat(Short.MAX_VALUE)),
                "the old readUtf() default is exactly what this exists to refuse");
    }

    @Test
    void rejectsCharactersAResourceLocationWouldThrowOn() {
        assertFalse(PacketBounds.isContentIdValid("Berserker"), "uppercase");
        assertFalse(PacketBounds.isContentIdValid("berserker!"), "punctuation");
        assertFalse(PacketBounds.isContentIdValid("bers erker"), "whitespace");
        assertFalse(PacketBounds.isContentIdValid("bers\u0000erker"), "embedded null");
    }

    @Test
    void rejectsMalformedNamespaceSeparators() {
        assertFalse(PacketBounds.isContentIdValid(":berserker"), "empty namespace");
        assertFalse(PacketBounds.isContentIdValid("runicskills:"), "empty path");
        assertFalse(PacketBounds.isContentIdValid("a:b:c"), "two separators");
        assertFalse(PacketBounds.isContentIdValid(":"));
    }
}
