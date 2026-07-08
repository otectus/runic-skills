package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic coverage for the culinary integration's namespace rules (no Minecraft bootstrap). */
class CulinaryNamespacesTest {

    @Test
    void defaultsCoverFarmersDelightFamilyAndLetsDo() {
        List<String> defaults = CulinaryNamespaces.defaults();
        // Verified upstream mod ids — a typo here silently no-ops the whole integration.
        for (String ns : List.of(
                "farmersdelight", "dungeonsdelight", "fruitsdelight", "rusticdelight",
                "vintagedelight", "brewinandchewin",
                "vinery", "bakery", "brewery", "candlelight", "meadow",
                "farm_and_charm", "beachparty", "herbalbrews")) {
            assertTrue(defaults.contains(ns), "default culinary namespaces must contain " + ns);
        }
        assertEquals(14, defaults.size(), "unexpected extra/missing default namespace");
    }

    @Test
    void defaultsAreTheTwoFamiliesConcatenated() {
        assertEquals(CulinaryNamespaces.FARMERS_DELIGHT_FAMILY.size()
                        + CulinaryNamespaces.LETS_DO_FAMILY.size(),
                CulinaryNamespaces.defaults().size());
    }

    @Test
    void matchesIsCaseInsensitiveAndTrimmed() {
        List<String> configured = List.of(" FarmersDelight ", "vinery");
        assertTrue(CulinaryNamespaces.matches("farmersdelight", configured));
        assertTrue(CulinaryNamespaces.matches("VINERY", configured));
        assertTrue(CulinaryNamespaces.matches(" vinery ", configured));
        assertFalse(CulinaryNamespaces.matches("bakery", configured));
    }

    @Test
    void matchesRejectsNullBlankAndNullList() {
        List<String> configured = CulinaryNamespaces.defaults();
        assertFalse(CulinaryNamespaces.matches(null, configured));
        assertFalse(CulinaryNamespaces.matches("", configured));
        assertFalse(CulinaryNamespaces.matches("  ", configured));
        assertFalse(CulinaryNamespaces.matches("farmersdelight", null));
        assertFalse(CulinaryNamespaces.matches("minecraft", configured));
    }

    @Test
    void matchesToleratesNullEntriesInConfiguredList() {
        List<String> configured = java.util.Arrays.asList(null, "bakery");
        assertTrue(CulinaryNamespaces.matches("bakery", configured));
        assertFalse(CulinaryNamespaces.matches("vinery", configured));
    }
}
