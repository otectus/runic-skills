package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.config.models.LockItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Classification rules for Overgeared item locks (pure logic — no registry scan). Item paths below
 * are real ids from Overgeared 1.6.x ModItems.
 */
class OvergearedLockRulesTest {

    private static LockItem.Skill skill(List<LockItem.Skill> skills, String name) {
        return skills.stream().filter(s -> name.equalsIgnoreCase(s.Skill.toString())).findFirst().orElse(null);
    }

    @Test
    void smithingHammersAreTinkeringGear() {
        for (String path : List.of("smithing_hammer", "copper_smithing_hammer")) {
            List<LockItem.Skill> skills = OvergearedLockRules.classify(path, 1.0f);
            assertEquals(8, skill(skills, "tinkering").Level, path);
            assertEquals(6, skill(skills, "strength").Level, path);
        }
    }

    @Test
    void tongsAndBlueprintsAreTinkeringGear() {
        List<LockItem.Skill> tongs = OvergearedLockRules.classify("iron_tongs", 1.0f);
        assertEquals(8, skill(tongs, "tinkering").Level);
        List<LockItem.Skill> tong = OvergearedLockRules.classify("iron_tong", 1.0f);
        assertEquals(8, skill(tong, "tinkering").Level);

        for (String bp : List.of("blueprint", "empty_blueprint")) {
            List<LockItem.Skill> skills = OvergearedLockRules.classify(bp, 1.0f);
            assertEquals(6, skill(skills, "tinkering").Level, bp);
        }
    }

    @Test
    void componentsAndConsumablesAreExplicitlyUnlocked() {
        for (String path : List.of(
                "copper_hammer_head", "steel_hammer_head",   // would match "hammer" otherwise
                "iron_sword_blade", "golden_sword_blade",    // would match "sword"/"blade"
                "iron_axe_head", "copper_pickaxe_head",      // would match "axe"/"pickaxe"
                "iron_plate", "copper_nugget", "diamond_shard",
                "clay_tool_cast", "nether_tool_cast", "knappable_rock",
                "heated_steel_ingot", "netherite_alloy", "crude_steel",
                "steel_arrow", "iron_arrow_head", "diamond_upgrade_smithing_template")) {
            List<LockItem.Skill> skills = OvergearedLockRules.classify(path, 1.0f);
            assertTrue(skills != null && skills.isEmpty(), path + " must be explicitly unlocked");
        }
    }

    @Test
    void finishedGearFallsThroughToGenericClassification() {
        // null = "not special": the provider then applies LockGen.classifyGear with materialBase.
        for (String path : List.of("copper_sword", "steel_chestplate", "copper_pickaxe", "steel_boots")) {
            assertNull(OvergearedLockRules.classify(path, 1.0f), path + " should fall through");
        }
    }

    @Test
    void materialBaseTiersGearByMetal() {
        assertEquals(6, OvergearedLockRules.materialBase("copper_sword"));
        assertEquals(8, OvergearedLockRules.materialBase("iron_chestplate"));
        assertEquals(12, OvergearedLockRules.materialBase("steel_axe"));
        assertEquals(16, OvergearedLockRules.materialBase("netherite_greatsword"));
        assertEquals(10, OvergearedLockRules.materialBase("mystery_maul")); // default tier
    }

    @Test
    void multiplierScalesSpecialGear() {
        List<LockItem.Skill> doubled = OvergearedLockRules.classify("smithing_hammer", 2.0f);
        assertEquals(16, skill(doubled, "tinkering").Level);
        assertEquals(12, skill(doubled, "strength").Level);
    }
}
