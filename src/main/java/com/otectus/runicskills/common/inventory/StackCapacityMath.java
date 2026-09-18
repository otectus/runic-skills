package com.otectus.runicskills.common.inventory;

/**
 * Count representation and transfer arithmetic never confer ownership or perk permission.
 *
 * <p>Three questions are kept apart here, because collapsing them is what turned a Runic format
 * limit into a universal "this item is corrupt" verdict (reference document §4.4):
 *
 * <ol>
 *   <li>what the selected {@link StackRepresentationProvider} can losslessly represent —
 *       {@link #maxRepresentableCount()} and {@link #checkedCount(int)};</li>
 *   <li>what a particular destination will accept right now — {@link #insertable(int, int, int)},
 *       whose limit comes from the slot or container being written to;</li>
 *   <li>what Pack Mule grants an eligible player — {@link #capacity(int, boolean, int)}.</li>
 * </ol>
 *
 * <p>Intermediates are {@code long} and are validated before they narrow, so a count near
 * {@link Integer#MAX_VALUE} coming out of a foreign provider cannot overflow a sum into a negative
 * number and delete somebody's stack.
 */
public final class StackCapacityMath {
    /**
     * The bound of <b>Runic Skills' own</b> extended representation, unchanged since it was
     * introduced. This is not a statement about what an item count may legally be: when another
     * provider owns the representation, {@link #maxRepresentableCount()} answers instead.
     */
    public static final int MAX_SERIALIZED_COUNT = 1_048_576;

    private StackCapacityMath() {}

    /** The largest count the selected representation can carry without losing information. */
    public static int maxRepresentableCount() {
        return StackRepresentationProvider.selected().maxRepresentableCount();
    }

    /** Whether the selected representation can carry this count. */
    public static boolean representable(long count) {
        return representable(count, StackRepresentationProvider.selected());
    }

    /** Whether the given representation can carry this count. */
    public static boolean representable(long count, StackRepresentationProvider provider) {
        return count >= 1L && count <= provider.maxRepresentableCount();
    }

    /**
     * Pack Mule's grant: only an ordinary 64-stacking item in a player slot grows, and only by the
     * rank the owner has actually earned. A natural limit that is not 64 — a foreign provider's
     * expanded limit, an enderpearl's 16, a sword's 1 — is returned untouched.
     */
    public static int capacity(int natural, boolean eligible, int rank) {
        if (!eligible || natural != 64) return natural;
        long granted = 64L * (1L + Math.max(0, Math.min(3, rank)));
        return (int) Math.min(granted, Integer.MAX_VALUE);
    }

    /** How much of {@code source} the destination accepts, given the destination's current limit. */
    public static int insertable(int source, int destination, int limit) {
        return (int) insertable((long) source, (long) destination, (long) limit);
    }

    /** The same acceptance question in {@code long}, for conservation assertions over big counts. */
    public static long insertable(long source, long destination, long limit) {
        return Math.max(0L, Math.min(Math.max(0L, source), limit - Math.max(0L, destination)));
    }

    /**
     * Rejects a count the <i>selected</i> representation cannot carry.
     *
     * <p>Only ever called from code that is serializing through Runic Skills' own format. When a
     * foreign provider owns the representation its bound applies, which for every provider this
     * release knows is {@link Integer#MAX_VALUE} — so a supported external count is never refused
     * merely for exceeding {@link #MAX_SERIALIZED_COUNT}.
     */
    public static int checkedCount(int count) {
        return checkedCount(count, StackRepresentationProvider.selected());
    }

    /** The same check against a named representation, so tests can exercise every profile. */
    public static int checkedCount(int count, StackRepresentationProvider provider) {
        if (!representable(count, provider))
            throw new IllegalArgumentException("Unsupported item count for the "
                    + provider + " stack representation: " + count);
        return count;
    }
}
