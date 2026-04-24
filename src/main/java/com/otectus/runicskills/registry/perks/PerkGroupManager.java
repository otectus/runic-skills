package com.otectus.runicskills.registry.perks;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * In-memory store for data-driven perk groups. Populated by {@link PerkGroupsReloadListener}
 * on server resource reload, replaced on client when the server broadcasts the sync packet.
 * <p>
 * This is additive to the hardcoded Iron's Spells school-attunement cap — both checks run
 * independently in {@code TogglePerkSP}.
 */
public final class PerkGroupManager {

    private static volatile Map<ResourceLocation, PerkGroup> groups = Collections.emptyMap();

    private PerkGroupManager() {}

    public static Collection<PerkGroup> all() {
        return groups.values();
    }

    public static void replaceAll(Map<ResourceLocation, PerkGroup> next) {
        groups = Map.copyOf(next);
    }

    public static void clear() {
        groups = Collections.emptyMap();
    }

    /**
     * Count of currently-enabled perks in the given group for this player.
     */
    public static int countEnabledInGroup(SkillCapability capability, PerkGroup group) {
        if (capability == null || group == null) return 0;
        int count = 0;
        for (String entry : group.perks()) {
            if (entry == null) continue;
            String path = entry.contains(":") ? entry.substring(entry.indexOf(':') + 1) : entry;
            var perk = RegistryPerks.getPerk(path);
            if (perk != null && capability.isPerkActive(perk)) count++;
        }
        return count;
    }

    /**
     * First group whose cap would be exceeded if the given perk were enabled, or null.
     * Groups containing {@code perkName} are checked; caller is expected to invoke this only
     * when transitioning from rank 0 to >=1 (rank-ups bypass group caps).
     */
    public static PerkGroup firstBlockingGroup(SkillCapability capability, String perkName) {
        if (capability == null || perkName == null) return null;
        Map<ResourceLocation, PerkGroup> snapshot = groups;
        if (snapshot.isEmpty()) return null;
        for (PerkGroup group : snapshot.values()) {
            if (!group.contains(perkName)) continue;
            if (countEnabledInGroup(capability, group) >= group.maxActive()) return group;
        }
        return null;
    }

}
