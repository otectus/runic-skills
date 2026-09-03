package com.otectus.runicskills.common.util;

/**
 * The one conversion from a configured duration in seconds to game ticks, kept Minecraft-free so it
 * can be unit-tested (see {@code DurationMathTest}).
 *
 * <p>It exists because the same conversion was written four different ways at four call sites: a
 * bare {@code * 20}, a {@code * 40} that doubled the window, and two copies of
 * {@code 10 + 20 * seconds} that padded it by half a second. A perk tooltip saying "3s" therefore
 * meant 6s, 3.5s or 3s depending on which handler read it (MEDIUM-08). {@code DurationUnitConsistencyTest}
 * keeps every {@code ValueType.DURATION} field routed through here.</p>
 */
public final class DurationMath {

    /** Ticks per second. */
    public static final int TICKS_PER_SECOND = 20;

    private DurationMath() {
    }

    /**
     * {@code seconds} as whole ticks, floored. Never negative, and saturates at
     * {@link Integer#MAX_VALUE} rather than wrapping — a config holding an absurd number must
     * produce a very long effect, not a negative-duration one that expires instantly.
     */
    public static int secondsToTicks(double seconds) {
        if (Double.isNaN(seconds) || seconds <= 0.0) return 0;
        double ticks = seconds * TICKS_PER_SECOND;
        if (ticks >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) ticks;
    }
}
