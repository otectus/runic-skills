package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.config.models.LockItem;

import java.util.List;
import java.util.Locale;

/**
 * Pure classification rules for Overgeared item locks. Bespoke because LockGen's keyword table would
 * misfile Overgeared's core tools ({@code hammer} → melee weapon) and gate crafting components
 * ({@code iron_sword_blade} contains "sword", {@code copper_hammer_head} contains "hammer").
 * Minecraft-free so it is directly unit-testable; {@link OvergearedLockProvider} supplies the
 * registry scan and the generic-gear fallback.
 *
 * <p>Contract of {@link #classify}: an empty list = explicitly unlocked (components, casts, rocks,
 * ingots, arrows); a non-empty list = Overgeared-specific lock (smithing hammers, tongs, blueprints);
 * {@code null} = not special — the caller should fall through to {@link LockGen#classifyGear} with
 * {@link #materialBase} as the base level so finished copper gear gates earlier than steel.</p>
 */
public final class OvergearedLockRules {

    private OvergearedLockRules() {
    }

    /** Mirrors {@link LockGen#scaled} (kept local so this class stays free of Forge-linked types). */
    static int scaled(int base, float mult) {
        if (base <= 0) return 0;
        return Math.max(2, Math.round(base * mult));
    }

    /** See class contract. */
    public static List<LockItem.Skill> classify(String path, float mult) {
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);

        // Crafting components and consumables — never locked. "_head"/"_blade"/"_plate" are part
        // items (copper_hammer_head, iron_sword_blade, iron_plate), casts are molds, knappable_rock
        // is a gathering item, heated ingots/nuggets/shards/alloys are smelting intermediates, and
        // arrows plus smithing templates are ammo/upgrade consumables.
        if (p.contains("_head") || p.contains("_blade") || p.contains("_plate")
                || p.contains("cast") || p.contains("rock") || p.contains("nugget")
                || p.contains("shard") || p.startsWith("heated_") || p.contains("alloy")
                || p.contains("ingot") || p.contains("arrow") || p.contains("template")
                || p.contains("crude_steel")) {
            return List.of();
        }
        if (p.contains("blueprint")) {
            return skills(new String[]{"tinkering"}, new int[]{6}, mult);
        }
        if (p.contains("hammer")) { // all remaining hammers are smithing hammers (heads excluded above)
            return skills(new String[]{"tinkering", "strength"}, new int[]{8, 6}, mult);
        }
        if (p.contains("tong")) {
            return skills(new String[]{"tinkering"}, new int[]{8}, mult);
        }
        return null; // finished gear (swords/armor/tools) → generic classification
    }

    /** Base lock level for finished Overgeared gear, tiered by material keyword. */
    public static int materialBase(String path) {
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (p.contains("copper")) return 6;
        if (p.contains("iron")) return 8;
        if (p.contains("silver") || p.contains("golden") || p.contains("gold")) return 10;
        if (p.contains("steel")) return 12;
        if (p.contains("diamond")) return 14;
        if (p.contains("netherite")) return 16;
        return 10;
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
