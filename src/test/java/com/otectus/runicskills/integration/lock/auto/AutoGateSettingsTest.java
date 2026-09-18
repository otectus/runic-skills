package com.otectus.runicskills.integration.lock.auto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped defaults are a product decision, and the exclusion lists are the pack author's only
 * hard override short of writing a rule. Both are asserted here because both are easy to change by
 * accident and expensive to notice: a flipped default silently gates a server's whole recipe book.
 */
class AutoGateSettingsTest {

    @Test
    void theShippedDefaultsAreTheOnesTheSpecificationMandates() {
        AutoGateSettings defaults = AutoGateSettings.defaults();
        assertTrue(defaults.enabled(), "enableAutoGates defaults to true");
        assertTrue(defaults.items());
        assertTrue(defaults.blocks());
        assertTrue(defaults.spells());
        assertFalse(defaults.crafting(), "equipment can be crafted before it can be used");
        assertFalse(defaults.placement());
        assertFalse(defaults.harvestBlocks());
        assertEquals(0.75, defaults.minimumConfidence(), 1e-9);
        assertTrue(defaults.roleFallbacks());
        assertTrue(defaults.recipeEvidence());
        assertFalse(defaults.learnFromManualRules(),
                "one item deliberately set to level 100 is a decision, not a reference point");
        assertEquals("LIVE", defaults.mode());
        assertFalse(defaults.frozen());
    }

    @Test
    void anUnknownModeFallsBackToLiveRatherThanDisablingTheLayer() {
        assertEquals("LIVE", settings("nonsense").mode());
        assertEquals("FROZEN", settings("frozen").mode(), "the value is read case-insensitively");
        assertTrue(settings("FROZEN").frozen());
    }

    @Test
    void exclusionsMatchByNamespaceAndByExactIdPerDomain() {
        AutoGateSettings settings = new AutoGateSettings(true, true, true, true, false, false, false,
                0.75, true, true, false, Set.of("create"), Set.of("examplemod:ritual_dagger"),
                Set.of("examplemod:altar"), Set.of("examplemod:fireball"), "LIVE");
        assertTrue(settings.excludes("item", "create:anything"));
        assertTrue(settings.excludes("block", "create:anything"));
        assertTrue(settings.excludes("item", "examplemod:ritual_dagger"));
        assertTrue(settings.excludes("item", "ExampleMod:Ritual_Dagger"),
                "an id typed with different case in the config must still exclude");
        assertFalse(settings.excludes("block", "examplemod:ritual_dagger"),
                "the item list is not the block list; the same path in two registries is two things");
        assertTrue(settings.excludes("block", "examplemod:altar"));
        assertTrue(settings.excludes("spell", "examplemod:fireball"));
        assertFalse(settings.excludes("item", "examplemod:other"));
    }

    @Test
    void theFingerprintChangesWithEverySettingThatCouldChangeACatalog() {
        AutoGateSettings base = AutoGateSettings.defaults();
        assertNotEquals(base.fingerprint(), settings("FROZEN").fingerprint());
        AutoGateSettings stricter = new AutoGateSettings(true, true, true, true, false, false, false,
                0.9, true, true, false, Set.of(), Set.of(), Set.of(), Set.of(), "LIVE");
        assertNotEquals(base.fingerprint(), stricter.fingerprint());
        assertEquals(base.fingerprint(), AutoGateSettings.defaults().fingerprint());
    }

    @Test
    void theFingerprintDoesNotDependOnTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            String turkish = AutoGateSettings.defaults().fingerprint();
            Locale.setDefault(Locale.US);
            assertEquals(turkish, AutoGateSettings.defaults().fingerprint());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void listBackedExclusionsArriveSortedSoTheFingerprintIsStable() {
        Set<String> a = AutoGateSettings.setOf(List.of("b", "a", "c"));
        Set<String> b = AutoGateSettings.setOf(List.of("c", "b", "a"));
        assertEquals(a.toString(), b.toString());
    }

    private static AutoGateSettings settings(String mode) {
        return new AutoGateSettings(true, true, true, true, false, false, false, 0.75, true, true,
                false, Set.of(), Set.of(), Set.of(), Set.of(), mode);
    }
}
