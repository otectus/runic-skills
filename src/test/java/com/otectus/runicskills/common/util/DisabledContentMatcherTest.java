package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure name-matching for the disabled* config lists. The critical invariants: a config author may
 * write an entry as a bare path or a full "modId:path" id and match either form of the queried name,
 * matching is exact (no substring/prefix false positives), and null/empty inputs are safe no-ops.
 */
class DisabledContentMatcherTest {

    private static final String MOD = "runicskills";

    @Test
    void barePathInputMatchesBarePathEntry() {
        assertTrue(DisabledContentMatcher.matches("berserker", MOD, List.of("berserker")));
    }

    @Test
    void barePathInputMatchesFullIdEntry() {
        assertTrue(DisabledContentMatcher.matches("berserker", MOD, List.of("runicskills:berserker")));
    }

    @Test
    void fullIdInputMatchesBarePathEntry() {
        assertTrue(DisabledContentMatcher.matches("runicskills:berserker", MOD, List.of("berserker")));
    }

    @Test
    void fullIdInputMatchesFullIdEntry() {
        assertTrue(DisabledContentMatcher.matches("runicskills:berserker", MOD, List.of("runicskills:berserker")));
    }

    @Test
    void addonNamespaceFullIdMatches() {
        // An addon perk lives under a different namespace; a bare-path config entry still matches it.
        assertTrue(DisabledContentMatcher.matches("addonmod:limit_breaker", MOD, List.of("limit_breaker")));
        assertTrue(DisabledContentMatcher.matches("addonmod:limit_breaker", MOD, List.of("addonmod:limit_breaker")));
        // ...but a runicskills full-id entry must NOT match a different namespace's perk of the same path.
        assertFalse(DisabledContentMatcher.matches("addonmod:limit_breaker", MOD, List.of("runicskills:limit_breaker")));
    }

    @Test
    void unmatchedNameReturnsFalse() {
        assertFalse(DisabledContentMatcher.matches("berserker", MOD, List.of("fire_attunement")));
    }

    @Test
    void noPrefixOrSubstringFalsePositive() {
        // "fire" must not match "fire_mark" and vice versa — matching is whole-string equality.
        assertFalse(DisabledContentMatcher.matches("fire", MOD, List.of("fire_mark")));
        assertFalse(DisabledContentMatcher.matches("fire_mark", MOD, List.of("fire")));
    }

    @Test
    void nullAndEmptyInputsAreSafe() {
        assertFalse(DisabledContentMatcher.matches(null, MOD, List.of("berserker")));
        assertFalse(DisabledContentMatcher.matches("berserker", MOD, null));
        assertFalse(DisabledContentMatcher.matches("berserker", MOD, Collections.emptyList()));
    }

    @Test
    void nullAndEmptyEntriesAreSkipped() {
        assertFalse(DisabledContentMatcher.matches("berserker", MOD, Arrays.asList(null, "")));
        // A valid entry alongside junk entries still matches.
        assertTrue(DisabledContentMatcher.matches("berserker", MOD, Arrays.asList(null, "", "berserker")));
    }
}
