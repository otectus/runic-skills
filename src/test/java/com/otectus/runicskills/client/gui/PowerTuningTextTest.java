package com.otectus.runicskills.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PowerTuningTextTest {
    @Test
    void gameTicksBecomeSecondsWithoutChangingTheDisplayedMechanic() {
        var line = PowerTuningText.format("window_ticks", 45);
        assertEquals("Window", line.label());
        assertEquals("2.25", line.number());
        assertTrue(line.unitKey().endsWith(".seconds"));
    }

    @Test
    void distinguishesPercentagesMultipliersAndFlatAmounts() {
        assertEquals("25", PowerTuningText.format("damage_bonus", 0.25).number());
        assertTrue(PowerTuningText.format("echo_damage_share", 0.4).unitKey().endsWith(".percent"));
        assertEquals("1.25", PowerTuningText.format("damage_multiplier", 1.25).number());
        assertTrue(PowerTuningText.format("mana_restore", 15).unitKey().endsWith(".number"));
        assertTrue(PowerTuningText.format("radius_blocks", 4).unitKey().endsWith(".blocks"));
    }

    @Test
    void serverAuthoredLabelsCannotInsertTooltipLinesOrUnboundedText() {
        var line = PowerTuningText.format("duration\n\t" + "long_".repeat(25), 0.123456789);
        assertFalse(line.label().contains("\n"));
        assertTrue(line.label().length() <= 40);
        assertEquals("0.123", line.number());
    }
}
