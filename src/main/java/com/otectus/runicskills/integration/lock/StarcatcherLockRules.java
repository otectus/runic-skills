package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.config.models.LockItem;

import java.util.List;
import java.util.Locale;

/**
 * Pure classification rules for Starcatcher item locks. A bespoke ruleset (instead of
 * {@link LockGen#classifyGear}) because LockGen's {@code rod} keyword classifies as MAGIC — a
 * fishing rod gated behind the Magic skill would be nonsense. Minecraft-free so it is directly
 * unit-testable; {@link StarcatcherLockProvider} supplies the registry scan.
 *
 * <p>Locked: fishing rods ({@code *_rod}) as the core progression gear, plus reusable tackle
 * (hooks/bobbers) at a lower tier. Everything else — fish, bait (consumable), bottles, trophies,
 * decoration, the tackle box — is deliberately left unlocked.</p>
 */
public final class StarcatcherLockRules {

    private StarcatcherLockRules() {
    }

    /** Mirrors {@link LockGen#scaled} (kept local so this class stays free of Forge-linked types). */
    static int scaled(int base, float mult) {
        if (base <= 0) return 0;
        return Math.max(2, Math.round(base * mult));
    }

    /** Skill requirements for a starcatcher item path, or an empty list to leave it unlocked. */
    public static List<LockItem.Skill> classify(String path, float mult) {
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (p.contains("trophy")) return List.of();
        if (p.endsWith("_rod") || p.equals("rod")) {
            return skills(new String[]{"fortune", "dexterity"}, new int[]{8, 6}, mult);
        }
        if (p.contains("hook") || p.contains("bobber")) {
            return skills(new String[]{"fortune"}, new int[]{6}, mult);
        }
        return List.of();
    }

    private static List<LockItem.Skill> skills(String[] names, int[] bases, float mult) {
        java.util.ArrayList<LockItem.Skill> out = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            int level = scaled(bases[i], mult);
            if (level >= 2) out.add(new LockItem.Skill(names[i], level));
        }
        return out;
    }
}
