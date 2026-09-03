package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lookups always carry the fallback bucket, and the bucket is bounded rather than unbounded. */
class ReverseIndexTest {

    @Test
    void aLookupReturnsWhatWasIndexedUnderTheKey() {
        ReverseIndex<String, String> index = new ReverseIndex<>(8);
        index.add("stick", "torch");
        index.add("stick", "pickaxe");
        index.add("coal", "torch");

        assertEquals(List.of("torch", "pickaxe"), List.copyOf(index.candidates("stick")));
        assertEquals(List.of("torch"), List.copyOf(index.candidates("coal")));
        assertEquals(2, index.size());
    }

    @Test
    void anUnknownKeyFindsNothingWhenThereIsNoFallback() {
        ReverseIndex<String, String> index = new ReverseIndex<>(8);
        index.add("stick", "torch");
        assertTrue(index.candidates("diamond").isEmpty());
        assertTrue(index.candidates(null).isEmpty());
    }

    /** The point of the bucket: an un-indexable entry is still considered by every lookup. */
    @Test
    void fallbackEntriesAppearUnderEveryKey() {
        ReverseIndex<String, String> index = new ReverseIndex<>(8);
        index.add("stick", "torch");
        index.addFallback("mystery_recipe");

        assertEquals(List.of("torch", "mystery_recipe"), List.copyOf(index.candidates("stick")));
        assertEquals(List.of("mystery_recipe"), List.copyOf(index.candidates("diamond")));
    }

    @Test
    void duplicatesCollapseAndDoNotConsumeCapacity() {
        ReverseIndex<String, String> index = new ReverseIndex<>(1);
        index.add("stick", "torch");
        index.add("stick", "torch");
        assertEquals(List.of("torch"), List.copyOf(index.candidates("stick")));

        assertTrue(index.addFallback("a"));
        assertTrue(index.addFallback("a"), "re-offering a stored value is not an overflow");
        assertEquals(0, index.fallbackOverflow());
    }

    @Test
    void theFallbackBucketIsBoundedAndCountsWhatItRefuses() {
        ReverseIndex<String, String> index = new ReverseIndex<>(2);
        assertTrue(index.addFallback("a"));
        assertTrue(index.addFallback("b"));
        assertEquals(0, index.fallbackOverflow());

        assertFalse(index.addFallback("c"));
        assertFalse(index.addFallback("d"));
        assertEquals(2, index.fallbackOverflow());
        assertEquals(List.of("a", "b"), List.copyOf(index.candidates("anything")));
    }

    /** A budgeted caller takes the same slice on every run, so views must not be rewritable either. */
    @Test
    void viewsAreImmutableAndInsertionOrdered() {
        ReverseIndex<String, String> index = new ReverseIndex<>(4);
        index.add("k", "first");
        index.add("k", "second");
        index.addFallback("third");

        assertEquals(List.of("first", "second", "third"), List.copyOf(index.candidates("k")));
        assertThrows(UnsupportedOperationException.class, () -> index.candidates("k").add("nope"));
        assertThrows(UnsupportedOperationException.class, () -> index.candidates("other").add("nope"));
    }

    @Test
    void nullsAreIgnoredRatherThanStored() {
        ReverseIndex<String, String> index = new ReverseIndex<>(4);
        index.add(null, "value");
        index.add("key", null);
        assertFalse(index.addFallback(null));
        assertEquals(0, index.size());
        assertTrue(index.candidates("key").isEmpty());
    }
}
