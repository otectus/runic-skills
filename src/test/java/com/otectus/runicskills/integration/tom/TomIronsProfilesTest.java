package com.otectus.runicskills.integration.tom;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TomIronsProfilesTest {
    @Test void acceptsAuditedPairsAndRejectsRepackedOrMismatchedArtifacts() {
        String legacy = "92c046383b4960c655f840d8846732a481edcf7c5ed89028b3d7b2cc2910b224";
        String current = "54b5aaa52887c38f570fbd820288171234d8541c17a5e953f9271189fdd480fb";
        assertTrue(TomIronsProfiles.supports("1.20.1-3.15.0", legacy));
        assertTrue(TomIronsProfiles.supports("1.20.1-3.16.3", current));
        assertFalse(TomIronsProfiles.supports("1.20.1-3.16.3", legacy));
        assertFalse(TomIronsProfiles.supports("1.20.1-3.16.4", current));
        assertFalse(TomIronsProfiles.supports("1.20.1-3.16.3", "repacked"));
        assertFalse(TomIronsProfiles.supports("1.20.1-3.16.3", null));
    }
}
