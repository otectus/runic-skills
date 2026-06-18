package com.otectus.runicskills.common.util;

import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Pure, Forge-free decision logic for item locks: does a player meet every skill requirement
 * of a locked item? Kept free of Minecraft/Forge imports so it can be unit-tested directly
 * (see LockCheckTest in src/test). The runtime wrapper that adapts {@code List<Skills>} and
 * applies the {@code enableItemLocks} master toggle is {@code SkillCapability.canUse(...)}.
 */
public final class LockCheck {

    private LockCheck() {
    }

    /**
     * Returns true if the player meets every requirement.
     *
     * <ul>
     *   <li>{@code required} — skill name → minimum level. A {@code null} or empty map means the
     *       item carries no lock, so it is always usable.</li>
     *   <li>{@code levelLookup} — resolves the player's current level for a skill name. Must be
     *       null-safe (return a sensible default, never throw) for unknown skill names.</li>
     * </ul>
     *
     * <p>The comparison is strict: a requirement is met when the player's level is
     * {@code >=} the required level (i.e. it is unmet only when {@code level < required}).</p>
     */
    public static boolean meetsRequirements(Map<String, Integer> required, ToIntFunction<String> levelLookup) {
        if (required == null || required.isEmpty()) return true;
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int requiredLevel = entry.getValue() == null ? 0 : entry.getValue();
            if (levelLookup.applyAsInt(entry.getKey()) < requiredLevel) {
                return false;
            }
        }
        return true;
    }
}
