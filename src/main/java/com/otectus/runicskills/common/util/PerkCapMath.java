package com.otectus.runicskills.common.util;

/**
 * Pure, Forge-free math for the active-perk cap. Kept free of Minecraft/Forge imports so it can be
 * unit-tested directly (see PerkCapMath in src/test). The runtime wrapper is
 * {@code RegistryPerks.effectivePerkCap(SkillCapability)}.
 */
public final class PerkCapMath {

    private PerkCapMath() {
    }

    /**
     * Computes the effective active-perk cap.
     *
     * <p>Convention: {@code 0} (or negative) means <em>unlimited</em>, matching the existing
     * {@code maxActivePerks} semantics enforced in {@code TogglePerkSP}.</p>
     *
     * <ul>
     *   <li>{@code flat} — the {@code maxActivePerks} config value ({@code <= 0} = disabled/unlimited).</li>
     *   <li>{@code globalLevel} — the player's total skill level (sum of all skill levels).</li>
     *   <li>{@code perScale} — the {@code perksPerGlobalLevel} config value ({@code <= 0} = disabled).
     *       The derived cap is {@code floor(globalLevel * perScale)}.</li>
     * </ul>
     *
     * <p>When both caps are active (non-zero) the <strong>smaller non-zero</strong> cap wins. A derived
     * cap of {@code 0} (e.g. at very low global level) is treated as "not applicable" rather than
     * "lock the player out of every perk", so it never makes the cap stricter than the flat cap alone.</p>
     */
    public static int computeEffectiveCap(int flat, int globalLevel, float perScale) {
        return computeEffectiveCap(flat, globalLevel, perScale, 0);
    }

    /**
     * As {@link #computeEffectiveCap(int, int, float)}, additionally clamping the scaled cap to an
     * optional hard ceiling.
     *
     * @param maxScaledCap optional upper bound on the {@code perScale}-derived cap ({@code <= 0} =
     *                     no ceiling). It bounds only the scaled cap, never relaxes the flat cap, and
     *                     never turns an unlimited (0) result into a limited one.
     */
    public static int computeEffectiveCap(int flat, int globalLevel, float perScale, int maxScaledCap) {
        int scaled = perScale > 0f ? (int) Math.floor((double) globalLevel * (double) perScale) : 0;
        if (maxScaledCap > 0 && scaled > maxScaledCap) scaled = maxScaledCap;

        boolean flatActive = flat > 0;
        boolean scaledActive = scaled > 0;

        if (!flatActive && !scaledActive) return 0;   // both disabled -> unlimited
        if (!flatActive) return scaled;                // only the scaled cap is active
        if (!scaledActive) return flat;                // only the flat cap is active
        return Math.min(flat, scaled);                 // both active -> smaller non-zero wins
    }
}
