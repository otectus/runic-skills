package com.otectus.runicskills.integration.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MountedTravelTest {
    @Test void sixForwardBlocksAndSingleConsumption() {
        var travel = new MountedTravel();
        for (int i = 0; i < 5; i++) travel.sample(0, 1, 0, 1, 1, true);
        assertFalse(travel.ready()); travel.sample(0, 1, 0, 1, 1, true);
        assertTrue(travel.consume()); assertFalse(travel.consume());
    }
    @Test void teleportReverseMissingTicksAndUnseparatedTravelReset() {
        for (int scenario = 0; scenario < 4; scenario++) {
            var travel = new MountedTravel();
            travel.sample(0, 3, 0, 1, 1, true);
            travel.sample(0, scenario == 0 ? 8 : scenario == 1 ? -1 : 1, 0, 1, scenario == 2 ? 2 : 1, scenario != 3);
            travel.sample(0, 3, 0, 1, 1, true);
            assertFalse(travel.ready());
        }
    }
    @Test void invalidNumbersCannotArmACharge() {
        var travel = new MountedTravel();
        travel.sample(Double.NaN, 6, 0, 1, 1, true);
        travel.sample(0, 3, Double.POSITIVE_INFINITY, 1, 1, true);
        assertFalse(travel.ready());
    }
}
