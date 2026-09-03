package com.otectus.runicskills.common.util;

/**
 * The block-break-speed contribution of the {@code break_speed} attribute, kept Minecraft-free so
 * it can be unit-tested (see {@code BreakSpeedMathTest}).
 *
 * <p>The call site adds this delta to {@code getNewSpeed()}, so what belongs here is the delta and
 * not the multiplied speed. Writing it as {@code original * (1 + attr)} and then adding produced
 * {@code 2.0x} at attribute 1.0 where the tooltip promises {@code 2.0x} of the <em>original</em>
 * only once — a silent double count that grew with every tool branch that added it (MEDIUM-01).</p>
 */
public final class BreakSpeedMath {

    private BreakSpeedMath() {
    }

    /**
     * The speed to add to the current break speed so a {@code +attribute} fraction reads as the
     * multiplier the tooltip states: 0 → no change, 0.25 → 1.25x, 0.5 → 1.5x.
     *
     * <p>A negative attribute contributes nothing rather than slowing the player down: the passive
     * is a bonus, and another mod's modifier must not be able to turn it into a penalty.</p>
     */
    public static float delta(float original, double attribute) {
        if (Double.isNaN(attribute) || attribute <= 0.0) return 0.0F;
        return original * (float) attribute;
    }
}
