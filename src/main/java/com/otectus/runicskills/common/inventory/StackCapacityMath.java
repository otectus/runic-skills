package com.otectus.runicskills.common.inventory;

/** Count representation and transfer arithmetic never confer ownership or perk permission. */
public final class StackCapacityMath {
    public static final int MAX_SERIALIZED_COUNT = 1_048_576;
    private StackCapacityMath() {}
    public static int capacity(int natural, boolean eligible, int rank) {
        return eligible && natural == 64 ? 64 * (1 + Math.max(0, Math.min(3, rank))) : natural;
    }
    public static int insertable(int source, int destination, int limit) {
        return (int) Math.max(0L, Math.min(Math.max(0L, source), (long) limit - Math.max(0, destination)));
    }
    public static int checkedCount(int count) {
        if (count < 1 || count > MAX_SERIALIZED_COUNT) throw new IllegalArgumentException("Unsupported item count: " + count);
        return count;
    }
}
