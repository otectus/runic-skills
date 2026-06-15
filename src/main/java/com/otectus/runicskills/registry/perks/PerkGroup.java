package com.otectus.runicskills.registry.perks;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * A mutual-exclusion or cap group for perks, loaded from data/runicskills/perk_groups/*.json.
 * <p>
 * Perks are identified by their registry path (e.g. "berserker") OR full id
 * ("runicskills:berserker") — both forms are accepted when matching.
 *
 * @param id        the datapack file id (e.g. runicskills:berserker_or_juggernaut)
 * @param maxActive max number of perks in this group that may be enabled at once (>=1)
 * @param perks     set of perk identifiers (path or full id form)
 * @param messageKey optional i18n key sent to clients when the cap is hit; may be null
 */
public record PerkGroup(ResourceLocation id, int maxActive, Set<String> perks, @Nullable String messageKey) {

    /** True if the given perk registry path (no namespace) is a member of this group. */
    public boolean contains(String perkPath) {
        if (perkPath == null) return false;
        if (perks.contains(perkPath)) return true;
        // accept full ids of the form "<mod>:<path>" too
        for (String entry : perks) {
            if (entry == null) continue;
            int idx = entry.indexOf(':');
            if (idx >= 0 && idx + 1 < entry.length() && entry.substring(idx + 1).equals(perkPath)) return true;
        }
        return false;
    }
}
