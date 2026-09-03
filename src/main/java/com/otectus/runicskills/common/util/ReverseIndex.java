package com.otectus.runicskills.common.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A many-to-many lookup with a bucket for the entries that could not be indexed at all.
 *
 * <p>Written for Master Researcher, which wants "which recipes consume this item?" without walking
 * every recipe in the game on every craft. The part that makes it worth its own class is the
 * fallback bucket: a modded ingredient may refuse to enumerate its items (a tag that resolves at
 * match time, a predicate ingredient, an implementation that throws), and such an entry is not
 * indexable but also must not be dropped — dropping it would silently make a perk blind to whole
 * mods. Those entries go into a bounded bucket that is returned alongside every lookup, so they
 * are always checked, at a cost the caller can bound.
 *
 * <p>The bucket is capped because it is fed by third-party code. Past the cap the entry is
 * refused and {@link #fallbackOverflow()} counts it, which is a number worth logging once rather
 * than an unbounded set worth crashing over.
 *
 * <p>Iteration order is insertion order everywhere, so a lookup truncated to a budget takes the
 * same slice on every run instead of a hash-order-dependent one. Not synchronised: the intended
 * lifecycle is build-once-then-read-only, with publication handled by the owner.
 *
 * <p>Pure Java, so the candidate/fallback/overflow behaviour is unit-testable headlessly.
 */
public final class ReverseIndex<K, V> {

    private final Map<K, Set<V>> index = new LinkedHashMap<>();
    private final Set<V> fallback = new LinkedHashSet<>();
    private final int fallbackCapacity;
    private int overflow;

    public ReverseIndex(int fallbackCapacity) {
        this.fallbackCapacity = Math.max(0, fallbackCapacity);
    }

    /** Records that {@code value} is reachable from {@code key}. */
    public void add(K key, V value) {
        if (key == null || value == null) return;
        index.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(value);
    }

    /**
     * Records a value that no key can find, so every lookup must consider it.
     *
     * @return true if it was stored; false if the bucket is full, in which case
     *         {@link #fallbackOverflow()} has been incremented
     */
    public boolean addFallback(V value) {
        if (value == null) return false;
        if (fallback.contains(value)) return true;
        if (fallback.size() >= fallbackCapacity) {
            overflow++;
            return false;
        }
        return fallback.add(value);
    }

    /** Everything reachable from {@code key}, plus the whole fallback bucket. Immutable. */
    public Set<V> candidates(K key) {
        Set<V> direct = key == null ? null : index.get(key);
        if (direct == null || direct.isEmpty()) return Collections.unmodifiableSet(fallback);
        if (fallback.isEmpty()) return Collections.unmodifiableSet(direct);
        Set<V> merged = new LinkedHashSet<>(direct);
        merged.addAll(fallback);
        return Collections.unmodifiableSet(merged);
    }

    /** How many values were refused because the fallback bucket was full. */
    public int fallbackOverflow() {
        return overflow;
    }

    /** How many distinct keys are indexed. */
    public int size() {
        return index.size();
    }
}
