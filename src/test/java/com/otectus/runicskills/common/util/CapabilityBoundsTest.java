package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bounds checks for the values loaded out of a player's save (RS10-017).
 *
 * <p>The failures these guard against are not hypothetical shapes of bad input — they are the
 * specific values that made deserialization dangerous: a negative skill level that reads as below
 * every requirement, an {@code Integer.MAX_VALUE} rank that bypasses rank limits, an overflowing
 * global-level sum, and a Power deadline so far out that the cooldown never expires.
 */
class CapabilityBoundsTest {

    @Test
    void skillLevelsAreClampedIntoTheLegalRange() {
        assertEquals(CapabilityBounds.MIN_SKILL_LEVEL, CapabilityBounds.clampSkillLevel(Integer.MIN_VALUE));
        assertEquals(CapabilityBounds.MIN_SKILL_LEVEL, CapabilityBounds.clampSkillLevel(-1));
        assertEquals(CapabilityBounds.MIN_SKILL_LEVEL, CapabilityBounds.clampSkillLevel(0));
        assertEquals(1, CapabilityBounds.clampSkillLevel(1));
        assertEquals(32, CapabilityBounds.clampSkillLevel(32));
        assertEquals(CapabilityBounds.MAX_SKILL_LEVEL, CapabilityBounds.clampSkillLevel(Integer.MAX_VALUE));
    }

    /**
     * A pack that lowers {@code skillMaxLevel} must not have its players' earned levels erased on
     * load. The bound is the config schema's own ceiling, so everything a legitimate configuration
     * could ever have granted passes through untouched, and capping to the *current* setting stays
     * a use-time decision.
     */
    @Test
    void legitimatelyEarnedLevelsAboveTheCurrentCapSurvive() {
        assertEquals(500, CapabilityBounds.clampSkillLevel(500));
        assertEquals(CapabilityBounds.MAX_SKILL_LEVEL,
                CapabilityBounds.clampSkillLevel(CapabilityBounds.MAX_SKILL_LEVEL));
    }

    @Test
    void passiveLevelsAndPerkRanksRejectNegativesAndOverflow() {
        assertEquals(0, CapabilityBounds.clampPassiveLevel(-7));
        assertEquals(0, CapabilityBounds.clampPassiveLevel(Integer.MIN_VALUE));
        assertEquals(CapabilityBounds.MAX_PASSIVE_LEVEL, CapabilityBounds.clampPassiveLevel(Integer.MAX_VALUE));

        assertEquals(0, CapabilityBounds.clampPerkRank(-1));
        assertEquals(3, CapabilityBounds.clampPerkRank(3));
        assertEquals(CapabilityBounds.MAX_PERK_RANK, CapabilityBounds.clampPerkRank(Integer.MAX_VALUE));
    }

    @Test
    void cooldownDurationsAreNonNegativeAndBounded() {
        assertEquals(0, CapabilityBounds.clampCooldownTicks(-100));
        assertEquals(200, CapabilityBounds.clampCooldownTicks(200));
        assertEquals(CapabilityBounds.MAX_COOLDOWN_TICKS,
                CapabilityBounds.clampCooldownTicks(Integer.MAX_VALUE));
    }

    /**
     * Power state is an absolute game-time deadline. A past deadline is simply expired, and an
     * absurd one is pulled back to the ceiling rather than dropped — so a stuck cooldown still
     * eventually expires instead of becoming permanent.
     */
    @Test
    void gameTimeDeadlinesCollapseToExpiredOrToTheCeiling() {
        assertEquals(0L, CapabilityBounds.clampGameTime(-1L));
        assertEquals(0L, CapabilityBounds.clampGameTime(Long.MIN_VALUE));
        assertEquals(123_456L, CapabilityBounds.clampGameTime(123_456L));
        assertEquals(CapabilityBounds.MAX_GAME_TIME, CapabilityBounds.clampGameTime(Long.MAX_VALUE));
    }

    @Test
    void keysMustBeNonBlankAndWithinTheSharedIdLimit() {
        assertFalse(CapabilityBounds.isStorableKey(null));
        assertFalse(CapabilityBounds.isStorableKey(""));
        assertTrue(CapabilityBounds.isStorableKey("runicskills:kindle"));
        assertTrue(CapabilityBounds.isStorableKey("x".repeat(CapabilityBounds.MAX_KEY_CHARS)));
        assertFalse(CapabilityBounds.isStorableKey("x".repeat(CapabilityBounds.MAX_KEY_CHARS + 1)));
    }

    @Test
    void globalLevelSumSaturatesInsteadOfWrapping() {
        assertEquals(Integer.MAX_VALUE,
                CapabilityBounds.addSaturating(Integer.MAX_VALUE, 1));
        assertEquals(Integer.MAX_VALUE,
                CapabilityBounds.addSaturating(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(Integer.MIN_VALUE,
                CapabilityBounds.addSaturating(Integer.MIN_VALUE, -1));
        assertEquals(30, CapabilityBounds.addSaturating(10, 20));
    }

    /**
     * The property that actually matters at the call sites: whatever a save contains, the value
     * handed to the rest of the mod is inside the declared range. Fixed seed so a failure is
     * reproducible from the report alone.
     */
    @Test
    void everyClampIsTotalOverArbitraryInput() {
        Random random = new Random(20250826L);
        for (int i = 0; i < 20_000; i++) {
            int anyInt = random.nextInt();
            long anyLong = random.nextLong();

            int skill = CapabilityBounds.clampSkillLevel(anyInt);
            assertTrue(skill >= CapabilityBounds.MIN_SKILL_LEVEL && skill <= CapabilityBounds.MAX_SKILL_LEVEL,
                    "skill level out of range for input " + anyInt + ": " + skill);

            int passive = CapabilityBounds.clampPassiveLevel(anyInt);
            assertTrue(passive >= 0 && passive <= CapabilityBounds.MAX_PASSIVE_LEVEL,
                    "passive level out of range for input " + anyInt + ": " + passive);

            int rank = CapabilityBounds.clampPerkRank(anyInt);
            assertTrue(rank >= 0 && rank <= CapabilityBounds.MAX_PERK_RANK,
                    "perk rank out of range for input " + anyInt + ": " + rank);

            int cooldown = CapabilityBounds.clampCooldownTicks(anyInt);
            assertTrue(cooldown >= 0 && cooldown <= CapabilityBounds.MAX_COOLDOWN_TICKS,
                    "cooldown out of range for input " + anyInt + ": " + cooldown);

            long deadline = CapabilityBounds.clampGameTime(anyLong);
            assertTrue(deadline >= 0L && deadline <= CapabilityBounds.MAX_GAME_TIME,
                    "game time out of range for input " + anyLong + ": " + deadline);
        }
    }

    /** Clamping already-clean data must be a no-op, so repeated loads converge instead of drifting. */
    @Test
    void clampingIsIdempotent() {
        Random random = new Random(4242L);
        for (int i = 0; i < 5_000; i++) {
            int anyInt = random.nextInt();
            long anyLong = random.nextLong();
            assertEquals(CapabilityBounds.clampSkillLevel(anyInt),
                    CapabilityBounds.clampSkillLevel(CapabilityBounds.clampSkillLevel(anyInt)));
            assertEquals(CapabilityBounds.clampPassiveLevel(anyInt),
                    CapabilityBounds.clampPassiveLevel(CapabilityBounds.clampPassiveLevel(anyInt)));
            assertEquals(CapabilityBounds.clampPerkRank(anyInt),
                    CapabilityBounds.clampPerkRank(CapabilityBounds.clampPerkRank(anyInt)));
            assertEquals(CapabilityBounds.clampCooldownTicks(anyInt),
                    CapabilityBounds.clampCooldownTicks(CapabilityBounds.clampCooldownTicks(anyInt)));
            assertEquals(CapabilityBounds.clampGameTime(anyLong),
                    CapabilityBounds.clampGameTime(CapabilityBounds.clampGameTime(anyLong)));
        }
    }
}
