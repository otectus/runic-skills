package com.otectus.runicskills.integration.tide;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static com.otectus.runicskills.integration.tide.TideCatchLedger.*;

class TideCatchLedgerTest {
    private Cast cast(UUID actor, UUID hook, long generation) {
        return new Cast(actor, hook, generation, "minecraft:overworld", "tide:iron_fishing_rod", true, "water", "tide", 1, 100, 300);
    }
    @Test void ownerHookAndGenerationAreRequiredAndEachCommitIsSingleUse() {
        var ledger = new TideCatchLedger();
        UUID actor = UUID.randomUUID(), hook = UUID.randomUUID();
        assertTrue(ledger.begin(cast(actor, hook, 1)));
        assertTrue(ledger.commit(UUID.randomUUID(), hook, 1, "minecraft:overworld", 150, true, Outcome.FISH, "minecraft:cod", false).isEmpty());
        assertTrue(ledger.commit(actor, UUID.randomUUID(), 1, "minecraft:overworld", 150, true, Outcome.FISH, "minecraft:cod", false).isEmpty());
        assertTrue(ledger.commit(actor, hook, 2, "minecraft:overworld", 150, true, Outcome.FISH, "minecraft:cod", false).isEmpty());
        // Foreign fish with a real native external-delivery handoff are still Tide catches.
        var result = ledger.commit(actor, hook, 1, "minecraft:overworld", 150, true, Outcome.FISH, "foreign:fish", true).orElseThrow();
        assertTrue(result.fish()); assertTrue(result.externalDelivery());
        assertTrue(ledger.commit(actor, hook, 1, "minecraft:overworld", 150, true, Outcome.FISH, "foreign:fish", true).isEmpty());
        assertFalse(ledger.begin(cast(actor, hook, 2)));
    }
    @Test void invalidOrExpiredCastCannotMintBenefitsAndCleanupRemainsPossible() {
        var ledger = new TideCatchLedger();
        UUID actor = UUID.randomUUID(), hook = UUID.randomUUID();
        ledger.begin(cast(actor, hook, 1));
        assertTrue(ledger.commit(actor, hook, 1, "minecraft:the_nether", 150, true, Outcome.FISH, "minecraft:cod", false).isEmpty());
        assertEquals(0, ledger.activeCount());
        hook = UUID.randomUUID(); ledger.begin(cast(actor, hook, 2));
        assertTrue(ledger.commit(actor, hook, 2, "minecraft:overworld", 300, true, Outcome.FISH, "minecraft:cod", false).isEmpty());
        hook = UUID.randomUUID(); ledger.begin(cast(actor, hook, 3));
        assertTrue(ledger.commit(actor, hook, 3, "minecraft:overworld", 200, false, Outcome.INELIGIBLE, null, false).isEmpty());
        ledger.clear(actor); assertEquals(0, ledger.activeCount());
    }
    @Test void nonfishOutcomesDoNotBecomeFishAndRecentHistoryIsBounded() {
        var ledger = new TideCatchLedger(); UUID actor = UUID.randomUUID();
        for (int i = 1; i <= 100; i++) {
            UUID hook = UUID.randomUUID(); assertTrue(ledger.begin(cast(actor, hook, i)));
            assertFalse(ledger.commit(actor, hook, i, "minecraft:overworld", 150, true, Outcome.PULLED_ENTITY, null, false).orElseThrow().fish());
        }
        assertEquals(0, ledger.activeCount());
    }
}
