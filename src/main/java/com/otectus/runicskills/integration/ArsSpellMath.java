package com.otectus.runicskills.integration;

/** Arithmetic shared by native Ars payment, school bridges and discount handlers. */
public final class ArsSpellMath {
    private ArsSpellMath() {}

    /** Iron's school power is a multiplier: 1.0 is neutral and 1.2 supplies a 20% bonus. */
    public static double schoolBridgeMultiplier(double schoolPower, double percent) {
        if (!Double.isFinite(schoolPower) || !Double.isFinite(percent)) return 1.0;
        return 1.0 + Math.max(0.0, schoolPower - 1.0) * Math.max(0.0, percent) / 100.0;
    }

    /** A discount cannot charge for an already-free spell or increase a positive cost. */
    public static int discountedCost(int current, double percent, int minimum) {
        if (current <= 0) return 0;
        if (!Double.isFinite(percent) || percent <= 0) return current;
        int reduced = (int) (current * (1.0 - Math.min(100.0, percent) / 100.0));
        return Math.min(current, Math.max(reduced, minimum));
    }

    public static int scholarlyCost(int current, int extraGlyphs, int perGlyph) {
        if (current <= 0) return 0;
        long reduction = (long) Math.max(0, extraGlyphs) * Math.max(0, perGlyph);
        return (int) Math.max(1L, current - reduction);
    }

    /** Convert only committed mana loss, never a quoted cost or a hit/resolve count. */
    public static float paidRefund(double before, double after, double percent) {
        if (!Double.isFinite(before) || !Double.isFinite(after) || !Double.isFinite(percent)
                || before <= 0 || after < 0 || percent <= 0) return 0;
        double paid = Math.max(0.0, before - after);
        double refund = paid * Math.min(100.0, percent) / 100.0;
        return refund <= Float.MAX_VALUE ? (float) refund : 0;
    }
}
