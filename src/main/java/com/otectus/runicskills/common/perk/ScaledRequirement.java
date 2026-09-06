package com.otectus.runicskills.common.perk;

import com.otectus.runicskills.handler.HandlerCommonConfig;

/**
 * A perk's level requirement, expressed against the skill cap the server actually runs.
 *
 * <p>Every level in the integration spec's perk table was written against the stock cap of 32, and
 * a pack that halves the cap would otherwise find the level-32 perk permanently unreachable while
 * the level-4 one arrives at the same moment as before. §10.1 states the conversion:
 * {@code max(1, min(skillCap, ceil(baseLevel * skillCap / 32)))}, which keeps a requirement inside
 * the cap, keeps it reachable, and keeps the relative ordering of the sixteen perks intact.
 *
 * <p><b>A non-positive base passes through untouched.</b> {@code Perk} already reads a requirement
 * below one as "disabled", and that is how a pack turns a perk off — so scaling {@code -1} up to
 * {@code 1} would silently re-enable something an operator switched off.
 *
 * <p>Only the sixteen Tinker's Construct perks use this. The 470 older perks name a raw level in
 * config and always have; converting them would change what every existing pack's numbers mean.
 */
public final class ScaledRequirement {

    /** The cap the spec's tables were written against. */
    public static final int REFERENCE_CAP = 32;

    private ScaledRequirement() {
    }

    /** {@code baseLevel} converted to the configured cap, or {@code baseLevel} when disabled. */
    public static int forConfiguredCap(int baseLevel) {
        return forCap(baseLevel, HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
    }

    /**
     * {@code baseLevel} converted to {@code skillCap}. Pure, so the arithmetic is unit-testable.
     *
     * <p>Long arithmetic and a ceiling without floating point, for the same reason
     * {@code PowerEligibility} does it that way: a configured cap is an operator-supplied integer
     * and the product of two of them is not guaranteed to fit in an {@code int}.
     */
    public static int forCap(int baseLevel, int skillCap) {
        if (baseLevel <= 0) return baseLevel;
        if (skillCap <= 0) return baseLevel;
        long scaled = ((long) baseLevel * skillCap + REFERENCE_CAP - 1) / REFERENCE_CAP;
        return (int) Math.max(1L, Math.min(skillCap, scaled));
    }
}
