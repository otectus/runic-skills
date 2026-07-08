package com.otectus.runicskills.common.util;

import java.util.List;

/**
 * Pure name-matching for the {@code disabledPerks} / {@code disabledPassives} / {@code disabledPowers}
 * config lists. Kept Minecraft/Forge-free so the matching rules are unit-testable; the registries
 * (RegistryPerks/RegistryPassives/RegistryPowers) own reading the config value and pass the list in.
 *
 * <p>A registry name may arrive as a bare path ("berserker") or a full id ("runicskills:berserker").
 * An entry matches if it equals either the bare path or the "modId:path" full id of the queried name,
 * so a config author can write it either way.</p>
 */
public final class DisabledContentMatcher {

    private DisabledContentMatcher() {
    }

    /**
     * @param name         the registry name to test — bare path ("berserker") or full id ("runicskills:berserker").
     * @param modId        the mod id used to build the full id when {@code name} is a bare path.
     * @param disabledList the configured disabled entries; {@code null}/empty means nothing is disabled.
     * @return {@code true} if any non-empty entry equals the queried name's bare path or its "modId:path" full id.
     */
    public static boolean matches(String name, String modId, List<String> disabledList) {
        if (name == null || disabledList == null || disabledList.isEmpty()) return false;
        String fullId = name.contains(":") ? name : (modId + ":" + name);
        String path = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        for (String entry : disabledList) {
            if (entry == null || entry.isEmpty()) continue;
            if (entry.equals(path) || entry.equals(fullId)) return true;
        }
        return false;
    }
}
