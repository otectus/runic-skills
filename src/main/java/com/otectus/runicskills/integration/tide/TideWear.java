package com.otectus.runicskills.integration.tide;

/** One combined roll, one point per committed cast; no repair or individual stacking rolls. */
public final class TideWear {
    private TideWear() {}
    /** Native catch reduction precedes the ordinary Runic wear roll. Bound their combined probability. */
    public static double additionalChance(double requested, double ordinary) {
        if (!Double.isFinite(requested) || !Double.isFinite(ordinary)) return 0;
        double base=Math.max(0,Math.min(.90,ordinary));
        // Total avoidance is extra + (1 - extra) * base, rather than the sum of two caps.
        return Math.max(0,Math.min(Math.min(.35,requested),(.90-base)/(1-base)));
    }
    public static int reduce(int nativeWear, double combinedChance, double roll) {
        if (nativeWear <= 0 || !Double.isFinite(combinedChance) || !Double.isFinite(roll) || roll < 0 || roll >= 1) return nativeWear;
        return roll < Math.max(0, Math.min(.35, combinedChance)) ? nativeWear - 1 : nativeWear;
    }
}
