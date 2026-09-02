package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.config.models.LockItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keyword classification for discovered (registry-scanned) item locks — pure logic, no registry.
 *
 * <p>{@link LockGen} runs across <em>every</em> item of a namespace it has never seen, so a keyword
 * that also occurs inside an unrelated word gates that item too, silently, in whichever of the
 * discovered mods happens to name something that way. The matcher used to be a bare
 * {@code String#contains}: {@code bow} claimed {@code bowl} and {@code rainbow}, {@code axe}
 * claimed every {@code waxed_*}, {@code cap} claimed {@code mushroom_cap} (RS-049). These are the
 * regression cases for the word-boundary matcher and the never-gear list that replaced it.
 */
class LockGenTest {

    private static List<LockItem.Skill> classify(String path) {
        return LockGen.classifyGear(path, 10, 1.0f);
    }

    private static boolean locked(String path) {
        return !classify(path).isEmpty();
    }

    private static LockItem.Skill skill(List<LockItem.Skill> skills, String name) {
        return skills.stream().filter(s -> name.equalsIgnoreCase(s.Skill.toString())).findFirst().orElse(null);
    }

    @Test
    void keywordsInsideUnrelatedWordsDoNotLock() {
        // "bow" inside bowl/rainbow, "axe" inside waxed, "cap" inside a mushroom cap,
        // "orb" inside orbit, "club" inside a sandwich. Every one of these was gear before.
        for (String path : List.of(
                "bowl", "rainbow_wool", "waxed_copper_block", "waxed_cut_copper_stairs",
                "orbital_beacon", "scrollwork_block", "bowline_rope")) {
            assertFalse(locked(path), path + " must not be classified as gear");
        }
    }

    @Test
    void vanillaStyleRodsAreNeverMagicImplements() {
        // "rod" is a real magic-implement keyword, and also names four vanilla non-gear items.
        // Segment matching alone cannot separate them: fishing_rod's last segment IS "rod".
        // "cap" is genuinely a helmet keyword and genuinely the top of a mushroom; a whole-segment
        // match cannot tell them apart, so the collision is listed rather than guessed at.
        for (String path : List.of("fishing_rod", "lightning_rod", "blaze_rod", "end_rod",
                "iron_fishing_rod", "diamond_fishing_rod", "brown_mushroom_cap")) {
            assertFalse(locked(path), path + " must not be classified as gear");
            assertTrue(LockGen.isNeverGear(path), path + " must be on the never-gear list");
        }
    }

    @Test
    void genuineGearStillClassifies() {
        assertEquals("strength", skill(classify("iron_sword"), "strength").Skill.toString().toLowerCase());
        assertTrue(locked("steel_greatsword"));
        assertTrue(locked("dwarven_battleaxe"));
        assertTrue(locked("diamond_pickaxe"));
        assertTrue(locked("oak_crossbow"));
        assertTrue(locked("netherite_helmet"));
        assertTrue(locked("plate_armor"));
        assertTrue(locked("tower_shield"));
        assertTrue(locked("arcane_rod"));
        assertTrue(locked("elder_spell_book"));
    }

    @Test
    void categorySplitIsUnchanged() {
        // Order of evaluation still matters: pickaxe is a tool, a bare axe is a weapon,
        // and a quarterstaff is melee rather than a magic implement.
        assertEquals(10, skill(classify("iron_pickaxe"), "building").Level);
        assertEquals(10, skill(classify("iron_axe"), "strength").Level);
        assertEquals(10, skill(classify("oak_quarterstaff"), "strength").Level);
        assertEquals(10, skill(classify("apprentice_staff"), "magic").Level);
        assertEquals(10, skill(classify("iron_helmet"), "endurance").Level);
        assertEquals(10, skill(classify("yew_longbow"), "dexterity").Level);
    }

    @Test
    void nonGearIsStillLeftAlone() {
        for (String path : List.of("iron_ingot", "bread", "oak_planks", "wheat_seeds", "red_dye")) {
            assertFalse(locked(path), path + " must not be classified as gear");
        }
    }
}
