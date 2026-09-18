package com.otectus.runicskills.integration.lock;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Iron's spellbook equipment gate: the curated rows that ship, and the metadata profile that
 * covers everything they deliberately leave out.
 *
 * <p>What this is guarding is the defect the 2.2.1 work exists to fix. The old classifier tiered
 * books by matching words in their registry path and fell through to a flat 8, which was the
 * answer for nine of Iron's sixteen books — a 12-slot ice spellbook and a 5-slot copper one asked
 * a player for exactly the same thing. The replacement has to be ordered by capability, and the
 * cheap way to lose that property again is a well-meaning tweak to one weight, so the ordering is
 * asserted directly rather than the individual numbers only.
 *
 * <p>Chassis figures below are the ones read off the {@code irons_spellbooks 1.20.1-3.16.3}
 * artifact: slots, free slots and attribute-modifier counts, not proposals.
 */
class IronsBookGateTest {

    // Iron's books, as the 3.16.3 artifact declares them: (total slots, preset slots, modifiers).
    private static final IronsBookProfile WIMPY = new IronsBookProfile(0, 0, 0);
    private static final IronsBookProfile COPPER = new IronsBookProfile(5, 0, 0);
    private static final IronsBookProfile IRON = new IronsBookProfile(6, 0, 0);
    private static final IronsBookProfile GOLD = new IronsBookProfile(8, 0, 2);
    private static final IronsBookProfile DIAMOND = new IronsBookProfile(10, 0, 1);
    private static final IronsBookProfile ROTTEN = new IronsBookProfile(8, 0, 1);
    private static final IronsBookProfile BLAZE = new IronsBookProfile(10, 0, 2);
    private static final IronsBookProfile DRUIDIC = new IronsBookProfile(10, 0, 2);
    private static final IronsBookProfile VILLAGER = new IronsBookProfile(10, 0, 3);
    private static final IronsBookProfile ICE = new IronsBookProfile(12, 0, 2);
    private static final IronsBookProfile DRAGONSKIN = new IronsBookProfile(12, 0, 2);
    private static final IronsBookProfile NETHERITE = new IronsBookProfile(12, 0, 2);
    private static final IronsBookProfile LEGENDARY = new IronsBookProfile(12, 0, 0);
    private static final IronsBookProfile EVOKER = new IronsBookProfile(7, 3, 2);
    private static final IronsBookProfile NECRONOMICON = new IronsBookProfile(6, 4, 0);

    private static int magic(IronsBookProfile profile) {
        return IronsBookGateMath.magicLevel(profile);
    }

    @Test
    void curatedTableShipsExactlyTheVerifiedRows() {
        Map<String, IronsBookProfiles.CuratedProfile> table = IronsBookProfiles.all();
        Map<String, Integer> expected = Map.of(
                "irons_spellbooks:copper_spell_book", 8,
                "irons_spellbooks:iron_spell_book", 10,
                "irons_spellbooks:gold_spell_book", 12,
                "irons_spellbooks:diamond_spell_book", 16,
                "irons_spellbooks:blaze_spell_book", 20,
                "irons_spellbooks:druidic_spell_book", 20,
                "irons_spellbooks:villager_spell_book", 22,
                "irons_spellbooks:netherite_spell_book", 22,
                "irons_spellbooks:ice_spell_book", 24,
                // Not the proposed 26: dragonskin is ice's chassis with a different school, and a
                // reviewed row is the only way a sidegrade pair can share one number when one half
                // of it is reviewed and the other would otherwise fall to the metadata profile.
                "irons_spellbooks:dragonskin_spell_book", 24);
        assertEquals(expected.keySet(), table.keySet(),
                "the curated table must contain exactly the rows verified against the 3.16.3 artifact");
        expected.forEach((item, magic) ->
                assertEquals(magic, table.get(item).magic(), item + " curated Magic requirement"));
    }

    @Test
    void everyCuratedRowCarriesItsEvidence() {
        // Spec 5.3: the audit export prints the reason for every profile, so a row with no reason
        // is a number nobody can review.
        IronsBookProfiles.all().forEach((item, row) ->
                assertFalse(row.reason() == null || row.reason().isBlank(),
                        item + " ships without a recorded reason"));
    }

    @Test
    void theBooksWhoseProposedNumbersTheArtifactContradictedAreNotShipped() {
        // Each of these was in the proposed table and each was left out for a checked reason:
        // wimpy has no capacity at all, legendary has no attributes and no way to obtain it,
        // rotten's "drawback" is spell resistance (an upside), and evoker's proposed value counted
        // locked preset slots as usable capacity.
        for (String item : new String[]{
                "irons_spellbooks:wimpy_spell_book",
                "irons_spellbooks:legendary_spell_book",
                "irons_spellbooks:rotten_spell_book",
                "irons_spellbooks:evoker_spell_book"}) {
            assertTrue(IronsBookProfiles.find(item).isEmpty(),
                    item + " is shipping an unverified curated row");
        }
    }

    @Test
    void metadataProfileIsMonotoneAlongTheVerifiedUpgradePath() {
        // Copper -> iron -> gold -> diamond is a real upgrade chain in Iron's recipes. Note that
        // gold has MORE attribute modifiers than diamond and must still rank below it: capacity
        // has to outweigh the modifier term or the ordering is decided by bonus count.
        assertTrue(magic(COPPER) < magic(IRON), "copper must rank below iron");
        assertTrue(magic(IRON) < magic(GOLD), "iron must rank below gold");
        assertTrue(magic(GOLD) < magic(DIAMOND), "gold must rank below diamond");
    }

    @Test
    void theMetadataScaleAgreesWithTheReviewedScale() {
        // The two layers have to be on one ruler or a book the table covers and a book it does not
        // are ranked against different things. Copper's chassis is the calibration point: the
        // metadata profile must return exactly its reviewed value.
        assertEquals(IronsBookProfiles.find("irons_spellbooks:copper_spell_book").orElseThrow().magic(),
                magic(COPPER), "the metadata profile drifted off the reviewed scale");
        // And the check that made the drift visible: legendary is the biggest plain chassis in the
        // game and must still rank below the reviewed specialised books rather than level with them.
        assertTrue(magic(LEGENDARY)
                        < IronsBookProfiles.find("irons_spellbooks:ice_spell_book").orElseThrow().magic(),
                "legendary's metadata profile reached the reviewed ice requirement");
    }

    @Test
    void sidegradesRankEqual() {
        // Spec 5.3: allow equal gates for legitimate sidegrades; require an increase only along a
        // verified upgrade. Ice and dragonskin are one chassis with a different school attached,
        // as are blaze and druidic.
        assertEquals(magic(ICE), magic(DRAGONSKIN), "ice and dragonskin are the same chassis");
        assertEquals(magic(BLAZE), magic(DRUIDIC), "blaze and druidic are the same chassis");
    }

    @Test
    void capacityOutranksVocabulary() {
        // legendary_spell_book has the largest chassis of the plain books and no attributes at all.
        // The keyword classifier put it at the very top on the strength of its name; the metadata
        // profile must place it below the same-sized books that actually grant something.
        assertTrue(magic(LEGENDARY) < magic(ICE), "legendary must not outrank ice on its name");
        assertTrue(magic(LEGENDARY) < magic(NETHERITE), "legendary must not outrank netherite on its name");
        assertTrue(magic(LEGENDARY) > magic(DIAMOND), "twelve slots still beat ten");
    }

    @Test
    void lockedPresetSlotsAreWorthLessThanFreeOnes() {
        // The proposed evoker row read the constructor's capacity and ignored that three of its
        // seven slots hold fixed spells. A chassis with the same total capacity but all of it free
        // must ask for more.
        IronsBookProfile allFree = new IronsBookProfile(7, 0, 2);
        assertTrue(magic(EVOKER) < magic(allFree),
                "four free slots must not price the same as seven");
        assertTrue(magic(NECRONOMICON) < magic(COPPER) + IronsBookGateMath.REFERENCE_CAP,
                "a small preset book must stay in a sane range");
    }

    @Test
    void anInertChassisAsksForNothing() {
        // wimpy_spell_book has zero slots and no modifiers: a requirement on it would be decorative.
        assertTrue(WIMPY.isInert());
        assertEquals(0, magic(WIMPY));
    }

    @Test
    void requirementsNeverExceedTheReferenceCap() {
        // An addon book with sixty slots is still only asking for everything a player can have.
        IronsBookProfile absurd = new IronsBookProfile(60, 0, 12);
        assertEquals(IronsBookGateMath.REFERENCE_CAP, magic(absurd));
    }

    @Test
    void intelligenceTrailsMagicAtTheDocumentedRatio() {
        assertEquals(Math.round(24 * 0.6f), IronsBookGateMath.intelligenceLevel(24));
        assertEquals(0, IronsBookGateMath.intelligenceLevel(0));
    }

    @Test
    void rottenAndGoldShareACapacityButNotARequirement() {
        // Both hold eight spells; gold carries two modifiers and rotten one, so gold ranks higher.
        // The proposed table had rotten ABOVE gold on a drawback the artifact does not have.
        assertTrue(magic(ROTTEN) < magic(GOLD), "rotten must not outrank gold");
    }

    @Test
    void bookDetectionCoversTheChassisNamesIronsAndItsAddonsUse() {
        assertTrue(IronsSpellbooksLockProvider.isBook("copper_spell_book"));
        assertTrue(IronsSpellbooksLockProvider.isBook("archivists_grimoire"));
        assertTrue(IronsSpellbooksLockProvider.isBook("explorers_codex"));
        assertFalse(IronsSpellbooksLockProvider.isBook("wizard_boots"));
        assertFalse(IronsSpellbooksLockProvider.isBook("blank_rune"));
    }
}
