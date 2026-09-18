package com.otectus.runicskills.common.inventory;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class StackCapacityMathTest {
    /** The boundaries reference document §4.7 names, plus one above the Runic representation limit. */
    private static final int[] BOUNDARIES = {1, 16, 63, 64, 65, 127, 128, 192, 255, 256, 257, 1_048_576, 1_048_577};

    @Test void randomizedTransfersConserveIncludingOverLimitDestinations() {
        Random random = new Random(220);
        for (int trial = 0; trial < 10000; trial++) {
            int source = random.nextInt(1025), target = random.nextInt(300), limit = 64 * (1 + random.nextInt(4));
            int moved = StackCapacityMath.insertable(source, target, limit);
            assertEquals(source + target, source - moved + target + moved);
            assertTrue(moved >= 0 && moved <= source);
            assertTrue(target >= limit ? moved == 0 : target + moved <= limit);
        }
        assertEquals(0, StackCapacityMath.insertable(256, 192, 64));
        assertEquals(1, StackCapacityMath.insertable(Integer.MAX_VALUE, 255, 256));
    }

    /**
     * A foreign provider's counts can sit close to {@link Integer#MAX_VALUE}. The arithmetic runs in
     * {@code long}, so a source plus a destination that would overflow an int still answers with a
     * transfer that conserves items rather than a negative one that destroys them.
     */
    @Test void largeExternalCountsDoNotOverflowTheTransferArithmetic() {
        long huge = Integer.MAX_VALUE;
        assertEquals(0L, StackCapacityMath.insertable(huge, huge, huge));
        assertEquals(1L, StackCapacityMath.insertable(huge, huge - 1L, huge));
        assertEquals(huge - 64L, StackCapacityMath.insertable(huge, 64L, huge));
        for (int trial = 0; trial < 1000; trial++) {
            long source = Integer.MAX_VALUE - trial, destination = Integer.MAX_VALUE - 2L * trial;
            long moved = StackCapacityMath.insertable(source, destination, (long) Integer.MAX_VALUE);
            assertTrue(moved >= 0 && moved <= source, "moved " + moved);
            assertTrue(destination + moved <= Integer.MAX_VALUE);
            assertEquals(source + destination, (source - moved) + (destination + moved));
        }
    }

    @Test void specialItemsAndForeignCapacitiesRemainNative() {
        assertEquals(128, StackCapacityMath.capacity(64, true, 1));
        assertEquals(192, StackCapacityMath.capacity(64, true, 2));
        assertEquals(256, StackCapacityMath.capacity(64, true, 3));
        for (int natural : new int[]{1,16,512}) assertEquals(natural, StackCapacityMath.capacity(natural, true, 3));
        assertEquals(64, StackCapacityMath.capacity(64, false, 3));
    }

    /** A rank beyond the perk's three tiers grants a fourth tier's worth and no more. */
    @Test void packMuleGrantIsBoundedByTheRanksThatExist() {
        assertEquals(256, StackCapacityMath.capacity(64, true, 99));
        assertEquals(64, StackCapacityMath.capacity(64, true, 0));
        assertEquals(64, StackCapacityMath.capacity(64, true, -5));
        // An externally expanded limit is a foreign decision and is never multiplied again.
        assertEquals(1_000_000, StackCapacityMath.capacity(1_000_000, true, 3));
    }

    /** The Runic representation keeps its own historical bound, and rejects one count above it. */
    @Test void runicRepresentationBoundIsUnchanged() {
        assertEquals(1_048_576, StackCapacityMath.MAX_SERIALIZED_COUNT);
        assertEquals(1_048_576, StackRepresentationProvider.RUNIC.maxRepresentableCount());
        for (int count : BOUNDARIES) {
            boolean legal = count <= StackCapacityMath.MAX_SERIALIZED_COUNT;
            assertEquals(legal, StackCapacityMath.representable(count, StackRepresentationProvider.RUNIC),
                    "RUNIC representable " + count);
            if (legal) assertEquals(count, StackCapacityMath.checkedCount(count, StackRepresentationProvider.RUNIC));
            else assertThrows(IllegalArgumentException.class,
                    () -> StackCapacityMath.checkedCount(count, StackRepresentationProvider.RUNIC));
        }
        assertTrue(StackRepresentationProvider.RUNIC.ownsNbtCount());
        assertTrue(StackRepresentationProvider.RUNIC.ownsNetworkCount());
        assertTrue(StackRepresentationProvider.RUNIC.grantsPackMuleCapacity());
    }

    /**
     * Reference document §4.4: a supported external count must not be rejected merely for exceeding
     * the Runic limit, and an unclassifiable one must not be rejected either — "unknown" is not
     * "invalid", because the record still belongs to somebody.
     */
    @Test void foreignProvidersAreNotJudgedByTheRunicLimit() {
        for (StackRepresentationProvider provider : new StackRepresentationProvider[]{
                StackRepresentationProvider.EXTERNAL_DELEGATED, StackRepresentationProvider.UNSUPPORTED_OVERLAP}) {
            assertEquals(Integer.MAX_VALUE, provider.maxRepresentableCount(), provider + " bound");
            for (int count : BOUNDARIES) {
                assertTrue(StackCapacityMath.representable(count, provider), provider + " rejected " + count);
                assertEquals(count, StackCapacityMath.checkedCount(count, provider));
            }
            assertEquals(Integer.MAX_VALUE, StackCapacityMath.checkedCount(Integer.MAX_VALUE, provider));
            assertFalse(provider.ownsNbtCount(), provider + " must install no NBT codec");
            assertFalse(provider.ownsNetworkCount(), provider + " must install no packet codec");
            assertFalse(provider.grantsPackMuleCapacity(), provider + " must defer Pack Mule");
        }
    }

    /** Zero and negative counts are not items under any provider. */
    @Test void nonPositiveCountsAreInvalidEverywhere() {
        for (StackRepresentationProvider provider : StackRepresentationProvider.values()) {
            for (int count : new int[]{0, -1, Integer.MIN_VALUE}) {
                assertFalse(StackCapacityMath.representable(count, provider), provider + " accepted " + count);
                assertThrows(IllegalArgumentException.class,
                        () -> StackCapacityMath.checkedCount(count, provider));
            }
        }
    }

    /** The no-argument entry points read the selected provider rather than a hard-coded constant. */
    @Test void selectedProviderDrivesTheDefaultBound() {
        StackRepresentationProvider selected = StackRepresentationProvider.selected();
        assertNotNull(selected);
        assertEquals(selected.maxRepresentableCount(), StackCapacityMath.maxRepresentableCount());
        assertFalse(StackRepresentationProvider.selectionDetail().isBlank());
        for (int count : BOUNDARIES) {
            assertEquals(StackCapacityMath.representable(count, selected), StackCapacityMath.representable(count));
        }
        // No mod list outside a game, so nothing else can claim the representation.
        assertEquals(StackRepresentationProvider.RUNIC, selected);
    }
}
