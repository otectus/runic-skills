package com.otectus.runicskills.integration.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The metadata spell gate's ordering guarantees.
 *
 * <p>Spec §10.2 requires the requirement to be monotonically nondecreasing across a spell's native
 * levels: a level a player could legitimately cast must never become unavailable because they
 * learned the next one. It also requires progression inside a rarity band to stay inside that band,
 * so a maxed Common spell cannot end up priced as an Uncommon one.
 */
class IronsSpellGateMathTest {

    @Test
    void anchorsAscendAndTheTopBandStopsAtTheCap() {
        for (int band = 1; band < IronsSpellGateMath.bandCount(); band++) {
            assertTrue(IronsSpellGateMath.bandAnchor(band - 1) < IronsSpellGateMath.bandAnchor(band),
                    "band " + band + " must ask more than the one below it");
        }
        assertEquals(IronsSpellGateMath.REFERENCE_CAP,
                IronsSpellGateMath.bandCeiling(IronsSpellGateMath.BAND_LEGENDARY));
    }

    @Test
    void progressionWithinABandNeverReachesTheBandAbove() {
        for (int band = 0; band < IronsSpellGateMath.bandCount() - 1; band++) {
            int ceiling = IronsSpellGateMath.bandCeiling(band);
            for (int levels = 1; levels <= 10; levels++) {
                for (int index = 0; index < levels; index++) {
                    int magic = IronsSpellGateMath.magicLevel(band, index, levels);
                    assertTrue(magic < ceiling,
                            "band " + band + " level " + index + "/" + levels + " reached " + magic
                                    + ", which is the next band's anchor " + ceiling);
                }
            }
        }
    }

    @Test
    void requirementIsNonDecreasingAcrossALevelRange() {
        for (int band = 0; band < IronsSpellGateMath.bandCount(); band++) {
            int previous = 0;
            for (int index = 0; index < 5; index++) {
                int magic = IronsSpellGateMath.magicLevel(band, index, 5);
                assertTrue(magic >= previous, "band " + band + " went backwards at level " + index);
                previous = magic;
            }
        }
    }

    @Test
    void aSingleLevelSpellSitsExactlyOnItsAnchor() {
        for (int band = 0; band < IronsSpellGateMath.bandCount(); band++) {
            assertEquals(IronsSpellGateMath.bandAnchor(band),
                    IronsSpellGateMath.magicLevel(band, 0, 1));
        }
    }

    @Test
    void anUnmappedRarityAbstainsRatherThanFallingThroughToAnExtreme() {
        // Spec 10.2: a rarity this build does not know must arrive as "cannot rank it". Reading an
        // ordinal would file a newly appended enum constant under the most punishing band.
        assertEquals(IronsSpellGateMath.UNDETERMINED, IronsSpellGateMath.magicLevel(-1, 0, 1));
        assertEquals(IronsSpellGateMath.UNDETERMINED,
                IronsSpellGateMath.magicLevel(IronsSpellGateMath.bandCount(), 0, 1));
        assertEquals(IronsSpellGateMath.UNDETERMINED, IronsSpellGateMath.bandAnchor(99));
    }

    @Test
    void anOutOfRangeIndexIsBoundedRatherThanExtrapolated() {
        int top = IronsSpellGateMath.magicLevel(IronsSpellGateMath.BAND_RARE, 4, 5);
        assertEquals(top, IronsSpellGateMath.magicLevel(IronsSpellGateMath.BAND_RARE, 99, 5));
        assertEquals(IronsSpellGateMath.bandAnchor(IronsSpellGateMath.BAND_RARE),
                IronsSpellGateMath.magicLevel(IronsSpellGateMath.BAND_RARE, -3, 5));
    }

    @Test
    void ararerSpellNeverCostsLessAtTheSamePositionInItsProgression() {
        for (int band = 1; band < IronsSpellGateMath.bandCount(); band++) {
            for (int index = 0; index < 4; index++) {
                assertTrue(IronsSpellGateMath.magicLevel(band, index, 4)
                                > IronsSpellGateMath.magicLevel(band - 1, index, 4),
                        "band " + band + " undercut band " + (band - 1) + " at position " + index);
            }
        }
    }
}
