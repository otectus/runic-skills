package com.otectus.runicskills.integration.lock;

import org.junit.jupiter.api.Test;
import static com.otectus.runicskills.integration.lock.RecentEquipmentLockRules.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class RecentEquipmentLockRulesTest {
    @Test void fishingHasAStarterAndProgressesWithoutMagicRequirements() {
        assertTrue(RecentEquipmentLockRules.requirements("tide", "wooden_fishing_rod", FISHING, 32).isEmpty());
        var iron = RecentEquipmentLockRules.requirements("tide", "iron_fishing_rod", FISHING, 32);
        var diamond = RecentEquipmentLockRules.requirements("tide", "diamond_fishing_rod", FISHING, 32);
        assertEquals(8, iron.get("fortune"));
        assertEquals(16, diamond.get("fortune"));
        assertFalse(diamond.containsKey("magic"));
    }

    @Test void namedRelicsAndMaterialWeaponsAreGatedAndScaleToTheCap() {
        assertEquals(24, RecentEquipmentLockRules.requirements("simplyswords", "bramblethorn", MELEE, 32).get("strength"));
        assertEquals(12, RecentEquipmentLockRules.requirements("simplymore", "mimicry_katana", MELEE, 16).get("strength"));
        assertEquals(8, RecentEquipmentLockRules.requirements("simplyswords", "iron_longsword", MELEE, 32).get("strength"));
        assertEquals(16, RecentEquipmentLockRules.requirements("simplymore", "diamond_lance", MELEE, 32).get("strength"));
    }

    @Test void tomEquipmentIncludesMagicWhileBooksUseIntelligence() {
        var armor = RecentEquipmentLockRules.requirements("traveloptics", "mechanized_exoskeleton_helmet", ARMOR, 32);
        assertEquals(24, armor.get("endurance"));
        assertEquals(12, armor.get("magic"));
        var book = RecentEquipmentLockRules.requirements("traveloptics", "abyssal_spellbook", MAGIC, 32);
        assertEquals(24, book.get("magic"));
        assertEquals(12, book.get("intelligence"));
        assertFalse(book.containsKey("strength"));
    }
}
