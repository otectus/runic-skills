package com.otectus.runicskills.integration.lock.auto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The false-positive fixtures §7.3 requires, as assertions about the function that decides.
 *
 * <p>The plan names four of them: an ingredient called {@code diamond_sword_blade}, a decorative
 * {@code magic_tome}, an ordinary {@code fishing_rod} and a temporary spell-created light. Each is a
 * case where the path reads exactly like equipment and the structure says it is not, and each is
 * the shape of mistake the old keyword generator made and had to blocklist its way out of one mod
 * at a time.
 */
class RoleClassifierTest {

    private static RoleClassifier.Classification classify(RoleClassifier.ItemStructure item) {
        return RoleClassifier.classify(item);
    }

    @Test
    void anIngredientNamedLikeAWeaponIsAMaterialAndCannotReachTheEnforcementFloor() {
        var blade = classify(new RoleClassifier.ItemStructure("diamond_sword_blade", false, "",
                false, false, false, false, false, false, false, false, false, 0, true));
        assertEquals(ContentRole.MATERIAL, blade.role(),
                "a crafting component that happens to contain 'sword' must not become a weapon");
        assertFalse(blade.role().gateEligible(), "a material is never gated automatically");
        assertTrue(blade.roleConfidence() < 0.85, "a name-only classification must stay below the "
                + "0.85 enforcement floor; it reached " + blade.roleConfidence());
        assertEquals("path_tokens_only", blade.provenance(),
                "the abstention must say it was the path that suggested the role, so an operator "
                        + "reading the export can see the engine noticed and declined");
    }

    @Test
    void aDecorativeTomeIsDecorationOrMaterialAndNotASpellFocus() {
        var tome = classify(new RoleClassifier.ItemStructure("magic_tome", false, "", false, false,
                false, false, false, false, false, false, false, 0, true));
        assertEquals(ContentRole.MATERIAL, tome.role(),
                "'tome' is a magic keyword; without the reviewed focus tag it decides nothing");
        assertTrue(tome.roleConfidence() < 0.85);
    }

    @Test
    void aTaggedFocusIsAFocusAndOutranksTheSameNameWithoutTheTag() {
        var tagged = classify(new RoleClassifier.ItemStructure("magic_tome", false, "", false, false,
                false, false, false, false, false, false, true, 0, true));
        assertEquals(ContentRole.SPELL_FOCUS, tagged.role());
        assertEquals(0.9, tagged.roleConfidence(), 1e-9,
                "a reviewed tag is strong evidence but still below a native type");
        assertTrue(tagged.roleConfidence() >= 0.85, "a reviewed tag must be able to produce a gate");
    }

    @Test
    void anOrdinaryFishingRodIsUtilityBecauseStructureOutranksTheRodKeyword() {
        var rod = classify(new RoleClassifier.ItemStructure("fishing_rod", false, "", false, true,
                false, false, false, false, false, false, false, 0, true));
        assertEquals(ContentRole.UTILITY, rod.role());
        assertFalse(rod.role().gateEligible(),
                "the legacy generator needed a hand-written never-gear list for exactly this item");
        assertEquals("native_type", rod.provenance());
    }

    @Test
    void aTemporarySpellLightIsNotEquipment() {
        // A spell-created light has no item form at all in most systems; where it has one it is a
        // block item. Either way it must not be read as gear because its path says "light".
        var light = classify(new RoleClassifier.ItemStructure("magic_light", false, "", false, false,
                false, false, false, false, false, true, false, 0, false));
        assertEquals(ContentRole.DECORATION, light.role());
        assertFalse(light.role().gateEligible());
    }

    @Test
    void nativeTypesDecideRolesAtFullConfidence() {
        assertEquals(ContentRole.ARMOR, classify(new RoleClassifier.ItemStructure("mystery", true,
                "chest", false, false, false, false, false, false, false, false, false, 0, false)).role());
        assertEquals("chest", classify(new RoleClassifier.ItemStructure("mystery", true, "chest",
                false, false, false, false, false, false, false, false, false, 0, false)).subrole());
        assertEquals(ContentRole.MINING_TOOL, classify(new RoleClassifier.ItemStructure("mystery",
                false, "", false, false, false, false, true, false, false, false, false, 0, false)).role());
        assertEquals(ContentRole.RANGED_WEAPON, classify(new RoleClassifier.ItemStructure("mystery",
                false, "", false, false, false, false, false, true, false, false, false, 0, false)).role());
    }

    @Test
    void anAxeIsAMeleeWeaponWithItsOwnSubroleRatherThanAPlainDigger() {
        var axe = classify(new RoleClassifier.ItemStructure("copper_axe", false, "", false, false,
                false, true, true, false, false, false, false, 5, false));
        assertEquals(ContentRole.MELEE_WEAPON, axe.role());
        assertEquals("axe", axe.subrole(),
                "an axe must be compared against other axes, not against swords");
    }

    @Test
    void realAttackDamageEstablishesAWeaponWithoutANativeWeaponType() {
        var modded = classify(new RoleClassifier.ItemStructure("thing", false, "", false, false,
                false, false, false, false, false, false, false, 7.5, false));
        assertEquals(ContentRole.MELEE_WEAPON, modded.role());
        assertEquals(1.0, modded.roleConfidence(), 1e-9,
                "an actual attack attribute is native evidence, not a guess");
    }

    @Test
    void aBlockEntityAloneCannotProduceAWorkstationGate() {
        var entity = RoleClassifier.classify(
                RoleClassifier.BlockStructure.plain("odd_machine", false, true));
        assertEquals(ContentRole.WORKSTATION_BLOCK, entity.role(),
                "the role it suggests is still recorded, so the abstention is visible");
        assertTrue(entity.roleConfidence() < 0.85,
                "'merely being a BlockEntity' is a weak signal and must not gate anything");
        var tagged = RoleClassifier.classify(
                RoleClassifier.BlockStructure.plain("odd_machine", true, true));
        assertTrue(tagged.roleConfidence() >= 0.85,
                "the reviewed workstation tag is how a pack opts a block in");
    }

    @Test
    void aHarvestTierTagEstablishesAHarvestBlockAndItsOwnTierIsItsSubrole() {
        var ore = RoleClassifier.classify(
                new RoleClassifier.BlockStructure("diamond_ore", false, false, true, 2));
        assertEquals(ContentRole.HARVEST_BLOCK, ore.role());
        assertEquals("tier_2", ore.subrole(),
                "a harvest candidate must be compared against blocks of its own tier");
        assertTrue(ore.roleConfidence() >= 0.85,
                "vanilla's own needs_*_tool tag is evidence, not a guess");
        assertEquals("harvest_tier_tag", ore.provenance());
    }

    @Test
    void aMineableBlockWithNoTierTagIsNotAHarvestTarget() {
        // §7.3: hardness, blast resistance and the word "ore" are weak signals that cannot decide.
        // Coal ore and plain stone are mineable and declare no tier, so there is nothing to rank
        // them by and the engine must not invent one.
        var coal = RoleClassifier.classify(
                new RoleClassifier.BlockStructure("coal_ore", false, false, true, -1));
        assertEquals(ContentRole.DECORATION, coal.role());
        assertFalse(coal.role().gateEligible());
        var named = RoleClassifier.classify(
                new RoleClassifier.BlockStructure("mysterious_ore_of_power", false, false, true, -1));
        assertEquals(ContentRole.DECORATION, named.role(),
                "the word 'ore' in a path cannot create a harvest role");
    }

    @Test
    void aTierTagWithoutAMineableTagDecidesNothing() {
        var odd = RoleClassifier.classify(
                new RoleClassifier.BlockStructure("odd", false, false, false, 3));
        assertEquals(ContentRole.DECORATION, odd.role(),
                "both halves of the harvest evidence are required");
    }

    @Test
    void aWorkstationThatIsAlsoMineableStaysAWorkstation() {
        var anvil = RoleClassifier.classify(
                new RoleClassifier.BlockStructure("anvil", true, false, true, 1));
        assertEquals(ContentRole.WORKSTATION_BLOCK, anvil.role(),
                "operating a workstation and harvesting it are different rules, and the reviewed "
                        + "workstation tag is the more specific statement");
    }

    @Test
    void onlyEquipmentRolesAreSubjectToTheCraftingSwitch() {
        for (ContentRole role : List.of(ContentRole.ARMOR, ContentRole.SHIELD,
                ContentRole.MELEE_WEAPON, ContentRole.MINING_TOOL, ContentRole.RANGED_WEAPON,
                ContentRole.SPELL_FOCUS)) {
            assertTrue(role.equipment(), role.key() + " is equipment a player wields or wears");
        }
        for (ContentRole role : List.of(ContentRole.WORKSTATION_BLOCK, ContentRole.HARVEST_BLOCK,
                ContentRole.SPELL, ContentRole.MATERIAL, ContentRole.FOOD, ContentRole.DECORATION,
                ContentRole.UTILITY, ContentRole.UNKNOWN)) {
            assertFalse(role.equipment(),
                    role.key() + " is not equipment; crafting one is a different operation");
        }
    }
}
