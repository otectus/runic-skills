package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for {@link LockCheck#meetsRequirements(Map, ToIntFunction)} — the pure decision
 * behind item gating. The {@code enableItemLocks} master toggle and creative/FakePlayer exemptions
 * live in {@code SkillCapability.canUse} (they read Forge state) and are covered by manual testing.
 */
class LockCheckTest {

    /** Mimics SkillCapability.safeLevel: unknown skills default to level 1, never throw. */
    private static ToIntFunction<String> levels(Map<String, Integer> player) {
        return name -> player.getOrDefault(name, 1);
    }

    private static Map<String, Integer> map(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    @Test
    void levelBelowRequirementIsLocked() {
        assertFalse(LockCheck.meetsRequirements(map("strength", 8), levels(map("strength", 1))));
    }

    @Test
    void levelAtRequirementIsAllowed() {
        // Boundary: requirement is met at exactly the required level (the comparison is `<`, not `<=`).
        assertTrue(LockCheck.meetsRequirements(map("strength", 8), levels(map("strength", 8))));
    }

    @Test
    void levelAboveRequirementIsAllowed() {
        assertTrue(LockCheck.meetsRequirements(map("strength", 8), levels(map("strength", 10))));
    }

    @Test
    void noRequirementsIsAllowed() {
        assertTrue(LockCheck.meetsRequirements(null, levels(map("strength", 1))));
        assertTrue(LockCheck.meetsRequirements(map(), levels(map("strength", 1))));
    }

    @Test
    void multiSkillAllMetIsAllowed() {
        assertTrue(LockCheck.meetsRequirements(
                map("strength", 8, "dexterity", 5),
                levels(map("strength", 8, "dexterity", 6))));
    }

    @Test
    void multiSkillOneUnmetIsLocked() {
        assertFalse(LockCheck.meetsRequirements(
                map("strength", 8, "dexterity", 5),
                levels(map("strength", 20, "dexterity", 4))));
    }

    @Test
    void unknownSkillDefaultsToOneWithoutThrowing() {
        // levelLookup returns the default (1) for a skill the player map doesn't contain.
        // A requirement above 1 is therefore unmet; a requirement of 1 is met.
        assertFalse(LockCheck.meetsRequirements(map("magic", 4), levels(map("strength", 32))));
        assertTrue(LockCheck.meetsRequirements(map("magic", 1), levels(map("strength", 32))));
    }
}
