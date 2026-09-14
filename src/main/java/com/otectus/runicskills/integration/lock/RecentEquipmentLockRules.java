package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.common.perk.ScaledRequirement;
import java.util.Map;
import java.util.TreeMap;

/** Reference requirements for finished equipment only; callers establish the native item category. */
public final class RecentEquipmentLockRules {
    public enum Kind { MELEE, ARMOR, RANGED, FISHING, MAGIC, TOOL }
    private RecentEquipmentLockRules() {}

    public static Map<String, Integer> requirements(String namespace, String path, Kind kind, int cap) {
        int level = referenceLevel(path, kind);
        Map<String, Integer> reference = switch (kind) {
            case FISHING -> Map.of("fortune", level, "dexterity", level / 2);
            case MELEE -> Map.of("strength", level, "dexterity", level / 2);
            case ARMOR -> Map.of("endurance", level, "constitution", level / 2);
            case RANGED -> Map.of("dexterity", level);
            case MAGIC -> Map.of("magic", level, "intelligence", level / 2);
            case TOOL -> Map.of("building", level);
        };
        Map<String, Integer> result = new TreeMap<>();
        reference.forEach((skill, base) -> {
            if (base > 1) result.put(skill, ScaledRequirement.forCap(base, cap));
        });
        // T.O. equipment combines martial/armor progression with its spellcasting identity.
        if (namespace.equals("traveloptics") && kind != Kind.MAGIC)
            result.put("magic", ScaledRequirement.forCap(Math.max(8, level / 2), cap));
        return Map.copyOf(result);
    }

    private static int referenceLevel(String path, Kind kind) {
        if (path.startsWith("wooden_") || path.startsWith("wood_")) return 1;
        if (path.startsWith("stone_") || path.startsWith("copper_")) return 4;
        if (path.startsWith("golden_") || path.startsWith("gold_")) return 6;
        if (path.startsWith("iron_")) return 8;
        if (path.startsWith("diamond_")) return 16;
        if (path.startsWith("netherite_") || path.startsWith("runic_")) return 24;
        return kind == Kind.FISHING ? 12 : 24;
    }
}
