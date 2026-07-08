package com.otectus.runicskills.common.util;

import java.util.Locale;

/**
 * Pure tier math for Overgeared's {@code "ForgingQuality"} item-NBT string
 * ({@code poor/well/expert/perfect/master/none}). Kept Minecraft-free so the upgrade rules are
 * unit-testable; {@code OvergearedIntegration} owns the NBT read/write half.
 *
 * <p>Both helpers return {@code null} for "no change" so callers can no-op safely on unknown or
 * un-upgradable values (a future Overgeared version adding tiers degrades to doing nothing, never
 * to corrupting the tag).</p>
 */
public final class ForgingQualityMath {

    public static final String POOR = "poor";
    public static final String WELL = "well";
    public static final String EXPERT = "expert";
    public static final String PERFECT = "perfect";
    public static final String MASTER = "master";

    private ForgingQualityMath() {
    }

    /**
     * One tier up, hard-capped at {@code perfect}: {@code poor→well→expert→perfect}. Returns
     * {@code null} for {@code perfect}/{@code master}/{@code none}/unknown — {@code master} stays
     * exclusive to Overgeared's own PERFECT-plus-conditions path.
     */
    public static String nextQuality(String quality) {
        if (quality == null) return null;
        return switch (quality.trim().toLowerCase(Locale.ROOT)) {
            case POOR -> WELL;
            case WELL -> EXPERT;
            case EXPERT -> PERFECT;
            default -> null;
        };
    }

    /** {@code poor→well} only (the steady_hammer "mitigate a bad forging outcome" rule), else null. */
    public static String mitigatePoor(String quality) {
        return quality != null && POOR.equals(quality.trim().toLowerCase(Locale.ROOT)) ? WELL : null;
    }
}
