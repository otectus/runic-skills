package com.otectus.runicskills.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down what Efficient Crafting is allowed to put back.
 *
 * <p>The perk's previous implementation handed the player one extra copy of the crafted result
 * (RS-205-04), which for a recipe with a container item is a straight duplication: craft a cake and
 * you kept your buckets <em>and</em> got a second cake. The refund replacing it is only correct
 * because it refuses every ambiguous case, so most of these tests are negatives — a remainder, a
 * damaged tool, an unexplainable count change — and each of them is a slot that must be left
 * exactly as vanilla left it.
 *
 * <p>Keys here are plain strings standing in for item-plus-NBT identity, which is precisely the
 * abstraction {@code plan} is written against.
 */
class CraftingRefundTest {

    private static final String LOG = "minecraft:oak_log";
    private static final String MILK = "minecraft:milk_bucket";
    private static final String BUCKET = "minecraft:bucket";

    private static CraftingRefund.SlotView slot(String key, int count) {
        return new CraftingRefund.SlotView(key, count);
    }

    private static CraftingRefund.SlotView empty() {
        return new CraftingRefund.SlotView(null, 0);
    }

    // --- the two things a refund may do -----------------------------------------------------------

    @Test
    void aSingleIngredientConsumedWholeIsRestored() {
        List<CraftingRefund.SlotRefund> refunds =
                CraftingRefund.plan(List.of(slot(LOG, 1)), List.of(empty()));

        assertEquals(List.of(new CraftingRefund.SlotRefund(0, CraftingRefund.Action.RESTORE_FULL)),
                refunds, "the whole stack was eaten, so the snapshot goes back");
    }

    @Test
    void aStackThatLostExactlyOneGrowsBackByOne() {
        List<CraftingRefund.SlotRefund> refunds =
                CraftingRefund.plan(List.of(slot(LOG, 64)), List.of(slot(LOG, 63)));

        assertEquals(List.of(new CraftingRefund.SlotRefund(0, CraftingRefund.Action.GROW_ONE)), refunds);
    }

    @Test
    void everyConsumedSlotOfOneOperationIsRefundedAndOnlyOnce() {
        List<CraftingRefund.SlotView> before =
                List.of(slot(LOG, 1), slot(LOG, 3), empty(), slot(LOG, 1));
        List<CraftingRefund.SlotView> after =
                List.of(empty(), slot(LOG, 2), empty(), empty());

        assertEquals(List.of(
                        new CraftingRefund.SlotRefund(0, CraftingRefund.Action.RESTORE_FULL),
                        new CraftingRefund.SlotRefund(1, CraftingRefund.Action.GROW_ONE),
                        new CraftingRefund.SlotRefund(3, CraftingRefund.Action.RESTORE_FULL)),
                CraftingRefund.plan(before, after),
                "one call is one crafting operation, so it refunds at most one recipe set");
    }

    // --- everything that must NOT be refunded -----------------------------------------------------

    @Test
    void aBucketRemainderIsNeverDuplicated() {
        // Vanilla replaces the milk bucket in place with an empty bucket. Refunding the milk would
        // leave the player with both, which is the duplication this perk used to be.
        assertTrue(CraftingRefund.plan(List.of(slot(MILK, 1)), List.of(slot(BUCKET, 1))).isEmpty(),
                "a different item in the slot is a remainder, not a consumption");
    }

    @Test
    void aDamagedReusableToolIsNeverDuplicated() {
        // Damage lives in the stack's tags, so a tool that came back one use worse is a different
        // key. Refunding it would hand the player a second, undamaged tool.
        assertTrue(CraftingRefund.plan(
                        List.of(slot("minecraft:shears#damage=0", 1)),
                        List.of(slot("minecraft:shears#damage=1", 1))).isEmpty());
    }

    @Test
    void anUntouchedSlotIsNotRefunded() {
        assertTrue(CraftingRefund.plan(List.of(slot(LOG, 4)), List.of(slot(LOG, 4))).isEmpty(),
                "nothing was consumed there");
    }

    @Test
    void aSlotThatGrewIsNotRefunded() {
        assertTrue(CraftingRefund.plan(List.of(slot(LOG, 4)), List.of(slot(LOG, 5))).isEmpty(),
                "a slot that gained items cannot be explained as a consumption");
    }

    @Test
    void aCountThatMovedByMoreThanOneIsNotRefunded() {
        assertTrue(CraftingRefund.plan(List.of(slot(LOG, 8)), List.of(slot(LOG, 5))).isEmpty(),
                "vanilla consumes exactly one unit per slot per operation");
    }

    @Test
    void anEmptySlotIsNeverFilled() {
        assertTrue(CraftingRefund.plan(List.of(empty()), List.of(slot(BUCKET, 1))).isEmpty(),
                "a remainder placed into an empty slot is not something that was consumed");
    }

    // --- degenerate inputs ------------------------------------------------------------------------

    @Test
    void anEmptyGridPlansNothing() {
        assertTrue(CraftingRefund.plan(List.of(), List.of()).isEmpty());
        assertTrue(CraftingRefund.plan(List.of(empty(), empty()), List.of(empty(), empty())).isEmpty());
    }

    @Test
    void mismatchedListLengthsReadOnlyTheOverlap() {
        List<CraftingRefund.SlotView> before = List.of(slot(LOG, 1), slot(LOG, 1), slot(LOG, 1));
        List<CraftingRefund.SlotView> after = List.of(empty());

        assertEquals(List.of(new CraftingRefund.SlotRefund(0, CraftingRefund.Action.RESTORE_FULL)),
                CraftingRefund.plan(before, after),
                "a container that changed size is not something to guess about");
    }

    @Test
    void nullListsAndNullSlotsAreTolerated() {
        assertTrue(CraftingRefund.plan(null, List.of(empty())).isEmpty());
        assertTrue(CraftingRefund.plan(List.of(empty()), null).isEmpty());
        assertTrue(CraftingRefund.plan(Arrays.asList((CraftingRefund.SlotView) null),
                Arrays.asList((CraftingRefund.SlotView) null)).isEmpty());
    }
}
