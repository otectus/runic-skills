package com.otectus.runicskills.integration.tconstruct;

/** The boundary between a native repair offer and already paid restoration. */
public final class TConstructRepairMath {
    private TConstructRepairMath() {}

    public static int offeredRepair(int points, int damage, double factor) {
        if (points <= 0 || damage <= 0 || !Double.isFinite(factor) || factor <= 0.0) return 0;
        return (int) Math.min(damage, Math.floor(points * factor));
    }

    public static int paidBonus(int restored, int damage, double share) {
        if (restored <= 0 || damage <= 0 || !Double.isFinite(share) || share <= 0.0) return 0;
        return (int) Math.min(damage, Math.floor(restored * share));
    }
}
