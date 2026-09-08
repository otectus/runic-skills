package com.otectus.runicskills.common.actions;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RunicActionContextTest {
    @Test
    void allNewEntriesFitAndExhaustionIsBoundedAndObservable() {
        long before = RunicActionContext.exhaustedRoots();
        try (var ignored = RunicActionContext.push(ActionOrigin.MELEE, UUID.randomUUID())) {
            for (int i = 0; i < RunicActionContext.MAX_CLAIMS; i++) assertTrue(RunicActionContext.claim("effect:" + i));
            assertFalse(RunicActionContext.claim("effect:overflow"));
            assertFalse(RunicActionContext.claim("effect:overflow-again"));
            assertEquals(RunicActionContext.MAX_CLAIMS, RunicActionContext.current().claims().size());
            assertEquals(before + 1, RunicActionContext.exhaustedRoots());
        }
    }

    @Test
    void nestedDifferentActorsNeverBorrowEachOthersClaimsOrRoot() {
        try (var outer = RunicActionContext.push(ActionOrigin.MELEE, UUID.randomUUID())) {
            long original = RunicActionContext.rootActionId();
            assertTrue(RunicActionContext.claim("effect:one"));
            try (var inner = RunicActionContext.push(ActionOrigin.MELEE, UUID.randomUUID())) {
                assertNotEquals(original, RunicActionContext.rootActionId());
                assertTrue(RunicActionContext.claim("effect:one"));
            }
            assertEquals(original, RunicActionContext.rootActionId());
            assertFalse(RunicActionContext.claim("effect:one"));
        }
    }
    @Test
    void capturedReadinessSurvivesTheTickerResetAndNeverCrossesActors() {
        UUID actor = UUID.randomUUID();
        try (var ignored = RunicActionContext.push(ActionOrigin.MELEE, actor)) {
            RunicActionContext.captureAttackStrength(actor, 1.0F);
            assertEquals(1.0F, RunicActionContext.attackStrength(actor, 0.0F));
            RunicActionContext.captureAttackStrength(actor, 0.0F);
            assertEquals(1.0F, RunicActionContext.attackStrength(actor, 0.0F));
            assertEquals(0.25F, RunicActionContext.attackStrength(UUID.randomUUID(), 0.25F));
        }
        assertEquals(0.0F, RunicActionContext.attackStrength(actor, 0.0F));
    }

    @Test
    void recursiveSwingKeepsItsOwnReadinessAndRestoresTheOuterSwing() {
        UUID actor = UUID.randomUUID();
        try (var outer = RunicActionContext.push(ActionOrigin.MELEE, actor)) {
            RunicActionContext.captureAttackStrength(actor, 1.0F);
            try (var inner = RunicActionContext.push(ActionOrigin.MELEE, actor)) {
                RunicActionContext.captureAttackStrength(actor, 0.2F);
                assertEquals(0.2F, RunicActionContext.attackStrength(actor, 0.0F));
            }
            assertEquals(1.0F, RunicActionContext.attackStrength(actor, 0.0F));
        }
        assertEquals(0, RunicActionContext.depth());
    }

    @Test
    void throwingAttackClosesItsScopeAndCapture() {
        UUID actor = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = RunicActionContext.push(ActionOrigin.MELEE, actor)) {
                RunicActionContext.captureAttackStrength(actor, 1.0F);
                throw new IllegalStateException("foreign attack handler failed");
            }
        });
        assertEquals(0, RunicActionContext.depth());
        assertEquals(0.0F, RunicActionContext.attackStrength(actor, 0.0F));
    }
}
