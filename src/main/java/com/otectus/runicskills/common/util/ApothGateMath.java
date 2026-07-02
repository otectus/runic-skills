package com.otectus.runicskills.common.util;

/**
 * Pure, Forge-free decision logic for the Apotheosis affix-rarity gate. Kept free of
 * Minecraft/Apotheosis imports so it can be unit-tested directly (see ApothGateMath test in
 * src/test) without Apotheosis on the classpath. The runtime caller is
 * {@code ApotheosisIntegration}.
 *
 * <p>The required-level input encodes three cases:</p>
 * <ul>
 *   <li>{@code <= 0} — no gate (common/ungated rarity, no affixes, or gating disabled). Must never
 *       surface as a "Fortune 0" requirement.</li>
 *   <li>{@link #UNMAPPED} — the item's rarity is not present in the {@code apothRarity*Level} config.
 *       Default-deny (B3 safety) but reported as an "unconfigured rarity", never as a numeric level.</li>
 *   <li>any other positive value — gate at exactly that Fortune level.</li>
 * </ul>
 */
public final class ApothGateMath {

    /** Sentinel returned for an Apotheosis rarity with no configured level (default-deny). */
    public static final int UNMAPPED = Integer.MAX_VALUE;

    private ApothGateMath() {
    }

    public enum Outcome {
        /** Player may equip/use the item — no gate, or Fortune requirement met. */
        ALLOW,
        /** Blocked: a real, configured Fortune level the player has not reached. */
        GATED,
        /** Blocked: the item's rarity is not mapped in config (default-deny). */
        UNMAPPED_RARITY
    }

    public record Decision(Outcome outcome, int requiredLevel) {
        public boolean blocks() {
            return outcome != Outcome.ALLOW;
        }
    }

    /**
     * Decides whether an affixed item is usable.
     *
     * @param requiredLevel {@code <= 0} = no gate, {@link #UNMAPPED} = unconfigured rarity,
     *                      otherwise the Fortune level required.
     * @param fortuneLevel  the player's current Fortune skill level.
     */
    public static Decision decide(int requiredLevel, int fortuneLevel) {
        if (requiredLevel <= 0) return new Decision(Outcome.ALLOW, 0);
        if (requiredLevel == UNMAPPED) return new Decision(Outcome.UNMAPPED_RARITY, UNMAPPED);
        if (fortuneLevel >= requiredLevel) return new Decision(Outcome.ALLOW, requiredLevel);
        return new Decision(Outcome.GATED, requiredLevel);
    }
}
