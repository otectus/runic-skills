package com.otectus.runicskills.integration.common;

/** New-catalog contributions only; native values and unrelated Runic contributions remain intact. */
public final class IntegrationLimits {
    private IntegrationLimits() {}
    public static double bounded(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
    public static int preparation(int nativeTicks, double reduction) {
        if (nativeTicks <= 6) return nativeTicks;
        return Math.max(6, (int) Math.ceil(nativeTicks * (1 - bounded(reduction, 0, .25))));
    }
    public static double normalWindow(double nativeWidth, double contribution) {
        if (!Double.isFinite(nativeWidth) || nativeWidth < 0 || nativeWidth >= .85) return nativeWidth;
        return nativeWidth + Math.min(.85 - nativeWidth, bounded(contribution, 0, .06));
    }
    public static int paidCost(int nativeCost, double discount) {
        if (nativeCost <= 0) return nativeCost;
        return Math.max(1, (int) Math.ceil(nativeCost * (1 - bounded(discount, 0, .10))));
    }
    public static double eligibleSpeciesWeight(double nativeWeight, boolean eligible, double multiplier) {
        if (!eligible || !Double.isFinite(nativeWeight) || nativeWeight <= 0) return nativeWeight;
        double weighted = nativeWeight * bounded(multiplier, 1, 1.15);
        return Double.isFinite(weighted) ? weighted : nativeWeight;
    }
    public static int additionalRepair(int actuallyRestored, int missing, double efficiency) {
        if (actuallyRestored <= 0 || missing <= 0) return 0;
        return Math.min(missing, (int) Math.floor(actuallyRestored * bounded(efficiency, 0, .10)));
    }
}
