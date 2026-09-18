package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

/**
 * Whether Better Combat is installed, asked without naming one of its classes.
 *
 * <p>Same job as {@link TConstructPresence}, and for the same reason: {@code RegistryPerks} decides
 * during static initialisation whether Titan's Grip exists at all, and that decision must be
 * answerable before any Better Combat class could be loaded. {@code ModList} answers it from the
 * loading mod list.
 *
 * <p><b>Presence is not capability.</b> This says the jar is there. Whether a given stack is
 * two-handed to Better Combat is asked separately, through
 * {@link com.otectus.runicskills.common.combat.TwoHandedWielding}, of a source that is installed
 * only if the API it names actually resolved.
 */
public final class BetterCombatPresence {

    /** The upstream mod id, as it appears in that mod's own {@code mods.toml}. */
    public static final String MOD_ID = "bettercombat";

    private BetterCombatPresence() {
    }

    /** Whether Better Combat is loaded. */
    public static boolean isModLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }
}
