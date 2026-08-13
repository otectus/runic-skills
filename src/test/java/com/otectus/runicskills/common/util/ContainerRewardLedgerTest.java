package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down the per-container reward cooldown behind the Locksmith perk.
 *
 * <p>Locksmith granted vanilla experience on every {@code PlayerContainerEvent.Open} with no
 * cooldown and no per-container state. Vanilla XP is the single authoritative currency for skill
 * level-ups, so holding right-click on one crafting table was a zero-cost progression bypass —
 * every other XP grant in the mod is funded by a consumed resource (RS-001).
 */
class ContainerRewardLedgerTest {

    private static final long COOLDOWN = 20L * 60L * 20L; // 20 minutes in ticks
    private static final long MIN_GAP = 20L * 5L;         // 5 seconds in ticks

    private UUID player;

    @BeforeEach
    void reset() {
        ContainerRewardLedger.clear();
        player = UUID.randomUUID();
    }

    @Test
    void theSameContainerPaysOnceThenGoesQuiet() {
        long chest = ContainerRewardLedger.key("minecraft:overworld", 12345L);
        assertTrue(ContainerRewardLedger.claim(player, chest, 0L, COOLDOWN, MIN_GAP), "first open pays");
        assertFalse(ContainerRewardLedger.claim(player, chest, 1L, COOLDOWN, MIN_GAP),
                "reopening immediately must not pay — this was the farm");
        assertFalse(ContainerRewardLedger.claim(player, chest, COOLDOWN - 1, COOLDOWN, MIN_GAP),
                "still inside the cooldown");
    }

    @Test
    void theSameContainerPaysAgainAfterTheCooldown() {
        long chest = ContainerRewardLedger.key("minecraft:overworld", 12345L);
        assertTrue(ContainerRewardLedger.claim(player, chest, 0L, COOLDOWN, MIN_GAP));
        assertTrue(ContainerRewardLedger.claim(player, chest, COOLDOWN, COOLDOWN, MIN_GAP));
    }

    @Test
    void distinctContainersStillPayButAreRateLimited() {
        long a = ContainerRewardLedger.key("minecraft:overworld", 1L);
        long b = ContainerRewardLedger.key("minecraft:overworld", 2L);
        assertTrue(ContainerRewardLedger.claim(player, a, 0L, COOLDOWN, MIN_GAP));
        assertFalse(ContainerRewardLedger.claim(player, b, 1L, COOLDOWN, MIN_GAP),
                "a wall of chests must not restore the original faucet");
        assertTrue(ContainerRewardLedger.claim(player, b, MIN_GAP, COOLDOWN, MIN_GAP),
                "once the floor has elapsed, a different container pays");
    }

    @Test
    void thesameBlockInAnotherDimensionIsADifferentContainer() {
        long overworld = ContainerRewardLedger.key("minecraft:overworld", 99L);
        long nether = ContainerRewardLedger.key("minecraft:the_nether", 99L);
        assertNotEquals(overworld, nether);
    }

    @Test
    void playersDoNotShareCooldowns() {
        UUID other = UUID.randomUUID();
        long chest = ContainerRewardLedger.key("minecraft:overworld", 7L);
        assertTrue(ContainerRewardLedger.claim(player, chest, 0L, COOLDOWN, MIN_GAP));
        assertTrue(ContainerRewardLedger.claim(other, chest, 0L, COOLDOWN, MIN_GAP),
                "one player's cooldown must not block another's");
    }

    @Test
    void aBackwardsWorldClockDoesNotLockThePlayerOut() {
        long chest = ContainerRewardLedger.key("minecraft:overworld", 3L);
        assertTrue(ContainerRewardLedger.claim(player, chest, 100_000L, COOLDOWN, MIN_GAP));
        // A restored backup rewinds getGameTime(); that must read as expired, not as "wait 4 days".
        assertTrue(ContainerRewardLedger.claim(player, chest, 10L, COOLDOWN, MIN_GAP));
    }

    @Test
    void forgettingAPlayerClearsTheirHistory() {
        long chest = ContainerRewardLedger.key("minecraft:overworld", 5L);
        assertTrue(ContainerRewardLedger.claim(player, chest, 0L, COOLDOWN, MIN_GAP));
        ContainerRewardLedger.forget(player);
        assertTrue(ContainerRewardLedger.claim(player, chest, 1L, COOLDOWN, MIN_GAP));
    }

    @Test
    void zeroCooldownsDisableTheLimit() {
        long chest = ContainerRewardLedger.key("minecraft:overworld", 8L);
        assertTrue(ContainerRewardLedger.claim(player, chest, 0L, 0L, 0L));
        assertTrue(ContainerRewardLedger.claim(player, chest, 0L, 0L, 0L),
                "an operator who sets both limits to 0 opts back into the old behaviour");
    }
}
