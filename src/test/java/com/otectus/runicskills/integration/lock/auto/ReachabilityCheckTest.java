package com.otectus.runicskills.integration.lock.auto;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A generated requirement has to be something a player on this server can actually reach.
 *
 * <p>§9 step 7 names the order — drop the generated secondary, then lower the generated primary,
 * then add no gate at all — and the reason it is not simply "clamp to the cap": clamping reports
 * nothing, so an impossible rule and a rule that quietly became a different rule look identical
 * afterwards. Every adjustment here carries the sentence that explains it.
 */
class ReachabilityCheckTest {

    private static Map<String, Integer> vector(Object... pairs) {
        Map<String, Integer> result = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], (Integer) pairs[i + 1]);
        return result;
    }

    @Test
    void minimumGlobalCountsTheStartingLevelEveryPlayerAlreadyHas() {
        // Ten skills that all start at one: a player holds ten levels before spending anything, and
        // Strength 8 costs the seven above its first.
        assertEquals(17, ReachabilityCheck.minimumGlobal(vector("strength", 8), 10));
        assertEquals(10, ReachabilityCheck.minimumGlobal(Map.of(), 10));
        assertEquals(24, ReachabilityCheck.minimumGlobal(vector("strength", 8, "dexterity", 8), 10));
    }

    @Test
    void aLevelOneRequirementIsRemovedBecauseItRestrictsNobody() {
        var fitted = ReachabilityCheck.fit(vector("strength", 8, "dexterity", 1), 32, 320, 10);
        assertEquals(vector("strength", 8), fitted.vector(),
                "skills start at one, so a level-one requirement is a tooltip claiming a gate that "
                        + "does not exist");
        assertTrue(fitted.changed());
    }

    @Test
    void anAttainableVectorIsLeftExactlyAsItIs() {
        var fitted = ReachabilityCheck.fit(vector("strength", 16, "building", 8), 32, 320, 10);
        assertEquals(vector("strength", 16, "building", 8), fitted.vector());
        assertFalse(fitted.changed());
        assertEquals("", fitted.reason());
    }

    @Test
    void aLevelAboveThePerSkillCapIsLoweredAndSaidSo() {
        var fitted = ReachabilityCheck.fit(vector("strength", 40), 20, 400, 10);
        assertEquals(vector("strength", 20), fitted.vector());
        assertTrue(fitted.changed());
        assertTrue(fitted.reason().contains("per-skill cap"), fitted.reason());
    }

    @Test
    void theGeneratedSecondaryIsDroppedBeforeThePrimaryIsTouched() {
        // A tight global budget: ten skills, so 10 levels are free and only 18 more are available.
        var fitted = ReachabilityCheck.fit(vector("strength", 16, "dexterity", 12), 32, 28, 10);
        assertEquals(vector("strength", 16), fitted.vector(),
                "the primary survives intact and the generated secondary goes first");
        assertTrue(fitted.reason().contains("secondary"), fitted.reason());
    }

    @Test
    void thePrimaryIsLoweredOnlyWhenDroppingTheSecondaryWasNotEnough() {
        var fitted = ReachabilityCheck.fit(vector("strength", 30, "dexterity", 12), 32, 20, 10);
        assertEquals(1, fitted.vector().size());
        assertEquals(11, fitted.vector().get("strength"),
                "a budget of 20 across 10 skills leaves eleven levels in one of them");
        assertEquals(10L + 10L, ReachabilityCheck.minimumGlobal(fitted.vector(), 10));
        assertTrue(fitted.reason().contains("global budget"), fitted.reason());
    }

    @Test
    void aBudgetThatLeavesNothingAttainableProducesNoGateAndExplainsWhy() {
        // A server with ten skills and a global cap of ten has no spare levels at all.
        var fitted = ReachabilityCheck.fit(vector("strength", 30), 32, 10, 10);
        assertTrue(fitted.empty(), "an unreachable requirement must not be clamped into a real one");
        assertTrue(fitted.reason().contains("no gate was added"), fitted.reason());
    }

    @Test
    void anUncappedGlobalBudgetOnlyAppliesThePerSkillCap() {
        var fitted = ReachabilityCheck.fit(vector("strength", 30, "dexterity", 20), 32, 0, 10);
        assertEquals(vector("strength", 30, "dexterity", 20), fitted.vector());
    }

    @Test
    void theAdjustedVectorIsImmutable() {
        var fitted = ReachabilityCheck.fit(vector("strength", 8), 32, 320, 10);
        try {
            fitted.vector().put("dexterity", 30);
            throw new AssertionError("a published adjustment must not be mutable");
        } catch (UnsupportedOperationException expected) {
            // The point of the assertion.
        }
    }
}
