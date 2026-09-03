package com.otectus.runicskills.common.crafting;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which crafting-grid slots Efficient Crafting may put back, by comparing the grid as it
 * was before vanilla consumed the recipe with the grid vanilla left behind.
 *
 * <p>The perk promises "Crafting has a X% chance to not consume materials". Until 2.0.4 it was
 * implemented as a chance to insert one extra copy of the <em>result</em> (RS-205-04), which is a
 * different mechanic and, for any recipe whose ingredients are worth more than one output — or
 * whose output count is greater than one — a duplication bug rather than a saving. The honest
 * version can only be expressed as a diff, because the only thing that knows what a recipe really
 * consumed is vanilla, after it has run.
 *
 * <p>The rule per slot is deliberately narrow: refund <em>only</em> what is unambiguously one unit
 * of a consumed ingredient.
 *
 * <ul>
 *   <li>Slot was empty before → nothing. Nothing was consumed there.</li>
 *   <li>Slot is empty now → {@link Action#RESTORE_FULL}: the whole stack (a single unit) was eaten,
 *       so the snapshot goes back verbatim.</li>
 *   <li>Same key and exactly one fewer → {@link Action#GROW_ONE}: one unit came off a stack.</li>
 *   <li>Anything else → nothing.</li>
 * </ul>
 *
 * <p>That last case is what keeps this from becoming the duplication bug it replaces. A milk bucket
 * becomes an empty bucket, a water bottle becomes a glass bottle, a damaged reusable tool comes back
 * with a new damage value, and a modded recipe can leave any custom container item it likes — in
 * every one of those the slot's <em>key</em> (item plus NBT, damage included) differs from the
 * snapshot, so no refund is planned and the player keeps exactly the one remainder vanilla gave
 * them. A count that changed by something other than one, or grew, is equally unexplainable and is
 * equally left alone. Guessing here is how you hand a player two buckets (spec §8.4.4).
 *
 * <p>One call describes one crafting operation. {@code ResultSlot#onTake} consumes exactly one
 * recipe set, and a shift-click loops it once per crafted operation, so no arithmetic on "how many
 * crafts happened" is needed or wanted: at most one recipe set is ever refunded per call
 * (spec §8.4.5, §8.4.6).
 *
 * <p>Pure Java by design — the caller reduces {@code ItemStack}s to opaque keys — so the decision
 * is unit-testable headlessly, like {@code ProcRoll} and {@code ExperienceMath}.
 */
public final class CraftingRefund {

    private CraftingRefund() {
    }

    /** What a refund does to one slot. */
    public enum Action {
        /** Put the snapshot back whole; vanilla emptied the slot. */
        RESTORE_FULL,
        /** Add back the single unit taken off a surviving stack. */
        GROW_ONE
    }

    /**
     * One grid slot, reduced to what the decision actually depends on.
     *
     * @param key   identity of the item including its NBT and damage; {@code null} means empty
     * @param count stack size; {@code 0} also means empty
     */
    public record SlotView(Object key, int count) {

        public boolean isEmpty() {
            return key == null || count <= 0;
        }
    }

    /** A refund to apply to slot {@code slot} of the crafting grid. */
    public record SlotRefund(int slot, Action action) {
    }

    /**
     * Plans the refund for one crafting operation.
     *
     * <p>The lists are read up to the shorter of the two: a container that changed size across the
     * call is a situation this cannot reason about, and the extra slots are simply not refunded.
     *
     * @param before the grid as snapshotted before vanilla consumed the recipe
     * @param after  the grid vanilla left behind, including any remainders it placed
     * @return the slots to refund, possibly empty; never null
     */
    public static List<SlotRefund> plan(List<SlotView> before, List<SlotView> after) {
        if (before == null || after == null) return List.of();

        int size = Math.min(before.size(), after.size());
        List<SlotRefund> refunds = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            SlotView was = before.get(i);
            SlotView now = after.get(i);
            if (was == null || was.isEmpty()) continue;

            if (now == null || now.isEmpty()) {
                refunds.add(new SlotRefund(i, Action.RESTORE_FULL));
            } else if (was.key().equals(now.key()) && now.count() == was.count() - 1) {
                refunds.add(new SlotRefund(i, Action.GROW_ONE));
            }
            // Anything else — remainder placed, tool damaged, count moved by more than one, count
            // grew — is not a consumption this can identify, so the slot is left as vanilla left it.
        }
        return refunds;
    }
}
