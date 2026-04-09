package com.otectus.runicskills.client.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilsTest {

    @Test
    void intToRoman_basicDigits() {
        assertEquals("I",    Utils.intToRoman(1));
        assertEquals("IV",   Utils.intToRoman(4));
        assertEquals("V",    Utils.intToRoman(5));
        assertEquals("IX",   Utils.intToRoman(9));
    }

    @Test
    void intToRoman_twoDigits() {
        assertEquals("XIV",  Utils.intToRoman(14));
        assertEquals("XLIX", Utils.intToRoman(49));
        assertEquals("XCIX", Utils.intToRoman(99));
    }

    @Test
    void intToRoman_largeNumbers() {
        assertEquals("CD",     Utils.intToRoman(400));
        assertEquals("MCMXCIV", Utils.intToRoman(1994));
    }
}
