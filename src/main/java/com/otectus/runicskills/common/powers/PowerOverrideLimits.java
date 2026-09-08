package com.otectus.runicskills.common.powers;

/**
 * The bounds a datapack's Power overrides must satisfy, in one place so the loader and the sync
 * packet cannot drift apart.
 *
 * <p>The decoder has always enforced these, because a hostile server could otherwise make a client
 * allocate an unbounded list. The loader did not, so a local datapack could push a payload past the
 * decoder's own limits and the client would reject the login packet it was sent (MEDIUM-07). The
 * limits are now applied where the data enters, and the decoder keeps checking them because a
 * packet does not have to come from this mod's loader.</p>
 *
 * <p>Pure and Minecraft-free so it is unit-testable alongside the other packet-bounds constants.</p>
 */
public final class PowerOverrideLimits {

    /** Most overrides a single payload may carry. */
    public static final int MAX_OVERRIDES = 8192;

    /** Most tuning values a single override may carry. */
    public static final int MAX_VALUES_PER_OVERRIDE = 1024;

    /** Shared by datapacks and packets; invalid numbers never enter gameplay arithmetic. */
    public static boolean isValidValue(String key, Double value) {
        return key != null && !key.isEmpty() && key.length() <= 128
                && value != null && Double.isFinite(value);
    }

    public static double boundValue(String key, double value) {
        if (key.equals("rewind_ticks") || key.equals("stationary_window_ticks"))
            return Math.max(0, Math.min(1_200, value));
        if (key.endsWith("_ticks")) return Math.max(0, Math.min(1_728_000, value));
        if (key.endsWith("_radius_blocks") || key.equals("radius_blocks"))
            return Math.max(0, Math.min(64, value));
        return Math.max(-1_000_000, Math.min(1_000_000, value));
    }

    private PowerOverrideLimits() {
    }

    /** True when a decoded override count is representable. */
    public static boolean isValidOverrideCount(int count) {
        return count >= 0 && count <= MAX_OVERRIDES;
    }

    /** True when a decoded per-override value count is representable. */
    public static boolean isValidValueCount(int count) {
        return count >= 0 && count <= MAX_VALUES_PER_OVERRIDE;
    }
}
