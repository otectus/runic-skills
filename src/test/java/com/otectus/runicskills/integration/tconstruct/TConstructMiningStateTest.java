package com.otectus.runicskills.integration.tconstruct;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TConstructMiningStateTest {
    @Test
    void miningPredictionFollowsTheSelectedSlotAndClearsAcrossServers() {
        try {
            TConstructMiningState.accept(0.25F, 2);
            assertEquals(0.25F, TConstructMiningState.bonus(2));
            assertEquals(0.0F, TConstructMiningState.bonus(3));
            TConstructMiningState.clear();
            assertEquals(0.0F, TConstructMiningState.bonus(2));
            TConstructMiningState.accept(Float.NaN, 2);
            assertEquals(0.0F, TConstructMiningState.bonus(2));
        } finally {
            TConstructMiningState.clear();
        }
    }
}
