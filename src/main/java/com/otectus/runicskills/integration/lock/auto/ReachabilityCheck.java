package com.otectus.runicskills.integration.lock.auto;

import java.util.Map;
import java.util.TreeMap;

/**
 * Whether a proposed requirement is something a player on this server can actually reach, and what
 * to do when it is not.
 *
 * <p>§9 steps 5 to 7. A generated vector is not simply clamped to the cap: clamping silently turns
 * "Endurance 40" into "Endurance 20" and reports nothing, which hides both the impossible rule and
 * the fact that two different items now demand the same thing. The order here is the documented one
 * — drop the generated secondary first, then lower the generated primary within the remaining
 * budget, and if nothing meaningful survives, add no gate and say why.
 *
 * <p>Manual rules are deliberately <em>not</em> passed through this. §9: an impossible manual
 * requirement is reported to the pack author, never quietly rewritten.
 *
 * <p>Pure arithmetic over a vector and three numbers, so the small-cap and starting-level cases are
 * unit-testable without a running progression service.
 */
public final class ReachabilityCheck {

    /** The outcome of fitting one vector into the server's actual progression budget. */
    public record Adjustment(Map<String, Integer> vector, boolean changed, String reason) {
        public Adjustment {
            vector = java.util.Collections.unmodifiableMap(new TreeMap<>(vector));
            reason = reason == null ? "" : reason;
        }

        /** Nothing attainable survived; the caller must add no gate. */
        public boolean empty() {
            return vector.isEmpty();
        }
    }

    private ReachabilityCheck() {
    }

    /**
     * The minimum global level a player needs to satisfy {@code vector}.
     *
     * <p>Every skill starts at one, so a player already holds {@code skillCount} levels before
     * spending anything; each requirement costs the levels above its first. This mirrors
     * {@code LockAudit.minimumGlobal} exactly — two different answers to "can this be reached"
     * would be worse than none.
     */
    public static long minimumGlobal(Map<String, Integer> vector, int skillCount) {
        long total = Math.max(0, skillCount);
        for (int level : vector.values()) total += Math.max(0, level - 1);
        return total;
    }

    /**
     * Fits {@code vector} into the per-skill cap and the global budget.
     *
     * @param vector      the proposed requirement
     * @param perSkillCap the configured per-skill maximum
     * @param globalCap   the effective global level budget
     * @param skillCount  how many skills are registered
     */
    public static Adjustment fit(Map<String, Integer> vector, int perSkillCap, int globalCap,
                                 int skillCount) {
        Map<String, Integer> result = new TreeMap<>();
        StringBuilder reason = new StringBuilder();
        boolean changed = false;

        for (Map.Entry<String, Integer> entry : vector.entrySet()) {
            int level = entry.getValue();
            if (level <= 1) {
                // §9 step 5: skills start at one, so a level-one requirement restricts nobody and
                // only makes a tooltip claim there is a gate where there is not.
                changed = true;
                continue;
            }
            if (perSkillCap > 0 && level > perSkillCap) {
                level = perSkillCap;
                changed = true;
                append(reason, entry.getKey() + " lowered to the per-skill cap " + perSkillCap);
            }
            if (level > 1) result.put(entry.getKey(), level);
        }
        if (result.isEmpty()) {
            return new Adjustment(Map.of(), true, "nothing above level one remained");
        }
        if (globalCap <= 0 || minimumGlobal(result, skillCount) <= globalCap) {
            return new Adjustment(result, changed, reason.toString());
        }

        // Step 7, first move: drop the generated secondary. The primary is the highest level; a tie
        // resolves to the alphabetically first name so the choice is reproducible.
        String primary = null;
        int highest = -1;
        for (Map.Entry<String, Integer> entry : result.entrySet()) {
            if (entry.getValue() > highest) {
                highest = entry.getValue();
                primary = entry.getKey();
            }
        }
        if (result.size() > 1) {
            Map<String, Integer> reduced = new TreeMap<>();
            reduced.put(primary, result.get(primary));
            append(reason, "generated secondary dropped to fit the global budget " + globalCap);
            result = reduced;
            changed = true;
        }
        if (minimumGlobal(result, skillCount) <= globalCap) {
            return new Adjustment(result, changed, reason.toString());
        }

        // Step 7, second move: lower the primary to what the budget leaves.
        long spare = globalCap - Math.max(0, skillCount);
        int affordable = (int) Math.max(0, Math.min(Integer.MAX_VALUE, spare + 1));
        if (affordable < 2) {
            return new Adjustment(Map.of(), true, "the global budget of " + globalCap
                    + " leaves no attainable requirement for " + primary + "; no gate was added");
        }
        result = new TreeMap<>(Map.of(primary, Math.min(result.get(primary), affordable)));
        append(reason, primary + " lowered to " + result.get(primary)
                + " to fit the global budget " + globalCap);
        return new Adjustment(result, true, reason.toString());
    }

    private static void append(StringBuilder text, String sentence) {
        if (text.length() > 0) text.append("; ");
        text.append(sentence);
    }
}
