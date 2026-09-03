package com.otectus.runicskills.common.util;

/**
 * The one reading of "how long ago was that?", kept Minecraft-free so it can be unit-tested (see
 * {@code GameTimeWindowTest}).
 *
 * <p>Timers across the mod stamp {@code level.getGameTime()} and later subtract. Two things went
 * wrong every time that was written by hand. A missing stamp was faked with {@code Long.MIN_VALUE},
 * and {@code now - Long.MIN_VALUE} overflows into a negative number, so "never used" read as "used
 * a moment ago" and cooldowns refused to fire. And a stamp from a longer-running world loaded next
 * to a shorter one leaves {@code now} behind {@code last}, which reads as a window that has not
 * opened yet and never will.
 *
 * <p>Both cases mean the same thing — no usable stamp — so both answer {@link Long#MAX_VALUE}:
 * infinitely long ago, cooldown ready, combat window closed.
 */
public final class GameTimeWindow {

    private GameTimeWindow() {
    }

    /**
     * Ticks between {@code last} and {@code now}, or {@link Long#MAX_VALUE} when {@code last} is
     * absent or sits in the future.
     */
    public static long elapsed(long now, Long last) {
        if (last == null || now < last) return Long.MAX_VALUE;
        long difference = now - last;
        // A leftover Long.MIN_VALUE sentinel still wraps the subtraction negative; that is the same
        // "no usable stamp" answer, so it saturates rather than reading as a moment ago.
        return difference < 0 ? Long.MAX_VALUE : difference;
    }

    /** Whether a cooldown of {@code cooldownTicks} started at {@code last} has run out. */
    public static boolean ready(long now, Long last, long cooldownTicks) {
        return elapsed(now, last) >= cooldownTicks;
    }

    /** Whether {@code last} is recent enough to still be inside a {@code windowTicks} window. */
    public static boolean within(long now, Long last, long windowTicks) {
        return elapsed(now, last) <= windowTicks;
    }
}
