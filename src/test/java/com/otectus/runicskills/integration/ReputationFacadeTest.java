package com.otectus.runicskills.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The MCA: Reputation facade's absent default. Every install without Reputation runs with no
 * provider, and that state must read as the bottom tier — never throw and never report standing a
 * player cannot have — because the StandingTier title condition calls into it on every title scan.
 */
class ReputationFacadeTest {

    @Test
    void noProviderIsInstalledByDefault() {
        assertFalse(ReputationFacade.isActive(),
                "the facade must stay dormant until McaReputationIntegration installs itself");
    }

    @Test
    void theAbsentDefaultIsTheBottomTier() {
        assertEquals(0, ReputationFacade.ABSENT_TIER_INDEX);
        assertEquals(0, ReputationFacade.bestTierIndex(null));
        assertEquals(0, ReputationFacade.tierIndexHere(null));
    }
}
