package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.config.models.LockItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Classification rules for Starcatcher item locks (pure logic — no registry scan). */
class StarcatcherLockRulesTest {

    private static LockItem.Skill skill(List<LockItem.Skill> skills, String name) {
        return skills.stream().filter(s -> name.equalsIgnoreCase(s.Skill.toString())).findFirst().orElse(null);
    }

    @Test
    void rodsLockBehindFortuneWithDexteritySecondary() {
        for (String rod : List.of("starcatcher_rod", "bamboo_rod", "obsidian_rod", "azure_crystal_rod")) {
            List<LockItem.Skill> skills = StarcatcherLockRules.classify(rod, 1.0f);
            assertEquals(2, skills.size(), rod);
            assertEquals(8, skill(skills, "fortune").Level, rod);
            assertEquals(6, skill(skills, "dexterity").Level, rod);
        }
    }

    @Test
    void reusableTackleLocksLightly() {
        for (String tackle : List.of("gold_hook", "mossy_hook", "vanilla_bobber")) {
            List<LockItem.Skill> skills = StarcatcherLockRules.classify(tackle, 1.0f);
            assertEquals(1, skills.size(), tackle);
            assertEquals(6, skill(skills, "fortune").Level, tackle);
        }
    }

    @Test
    void consumablesFishAndDecorationStayUnlocked() {
        for (String path : List.of(
                "worm", "legendary_bait", "sculk_bait",                 // bait (consumable)
                "black_eel", "cooked_starcaught_fish", "lava_crab",     // fish/food
                "message_in_a_bottle", "starcatcher_guide", "pearl",    // misc
                "trophy_of_the_older_angler",                           // trophy
                "starcaught_bucket", "fish_bones")) {
            assertTrue(StarcatcherLockRules.classify(path, 1.0f).isEmpty(), path + " must stay unlocked");
        }
    }

    @Test
    void multiplierScalesWithFloorOfTwo() {
        List<LockItem.Skill> doubled = StarcatcherLockRules.classify("bamboo_rod", 2.0f);
        assertEquals(16, skill(doubled, "fortune").Level);
        assertEquals(12, skill(doubled, "dexterity").Level);
        // At a tiny multiplier the scaled() floor of 2 applies (mirrors LockGen convention).
        List<LockItem.Skill> tiny = StarcatcherLockRules.classify("bamboo_rod", 0.1f);
        assertEquals(2, skill(tiny, "fortune").Level);
    }
}
