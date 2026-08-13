package com.otectus.runicskills.common.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * One definition of "did this perk proc?" for every config-driven probability in the mod.
 *
 * <p>Perk probabilities are expressed as a 1-in-N chance and read straight from a hand-editable
 * config file. Rolling them inline produced three separate defects:
 *
 * <ul>
 *   <li>{@code ThreadLocalRandom.nextInt(bound)} throws {@link IllegalArgumentException} for
 *       {@code bound <= 0}, and {@code 0} is the natural way a pack author writes "never". Six
 *       call sites passed the raw config value, so a single {@code 0} turned every craft — or
 *       every container open, or every kill — into an exception thrown out of a Forge event
 *       handler (RS-029).</li>
 *   <li>The sites compared the roll against {@code 1} rather than {@code 0}, so {@code nextInt(1)},
 *       which can only ever return {@code 0}, meant a configured 1-in-1 chance never fired
 *       instead of always firing (RS-154).</li>
 *   <li>Several sites rolled <em>before</em> checking whether the player even had the perk, so
 *       the crash above was not limited to players who had taken it (RS-029).</li>
 * </ul>
 *
 * <p>Callers must still gate on {@code Perk#isEnabled} before calling this — the roll is cheap,
 * but the enablement check is what keeps the behaviour correct.
 *
 * <p>Forge-free and deterministic given a bound, so it is unit-testable headlessly, matching
 * {@link ExperienceMath} and {@link PerkCapMath}.
 */
public final class ProcRoll {

    private ProcRoll() {
    }

    /**
     * Normalises a configured 1-in-N probability to a bound that is always legal to roll.
     * Values below 1 collapse to 1 ("always"), which is the reading a pack author expects from
     * {@code 0} far more often than "crash".
     */
    public static int bound(double configuredProbability) {
        if (Double.isNaN(configuredProbability)) return 1;
        double clamped = Math.min(Integer.MAX_VALUE, configuredProbability);
        return (int) Math.max(1L, (long) clamped);
    }

    /**
     * Rolls a configured 1-in-N chance. A bound of {@code 1} always succeeds; anything less than
     * {@code 1} is treated as {@code 1} rather than throwing.
     */
    public static boolean rolls(double configuredProbability) {
        return ThreadLocalRandom.current().nextInt(bound(configuredProbability)) == 0;
    }

    /** Percentage roll on {@code [0, 100)}. {@code >= 100} always succeeds, {@code <= 0} never does. */
    public static boolean rollsPercent(double percent) {
        if (percent >= 100.0D) return true;
        if (percent <= 0.0D) return false;
        return ThreadLocalRandom.current().nextDouble() * 100.0D < percent;
    }
}
