package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

/**
 * Whether Tinker's Construct is installed, asked without naming a single one of its classes.
 *
 * <p>The other optional integrations answer this from their own adapter class, because that class
 * already exists in common code. Tinkers' is different: everything this mod knows about it lives
 * in {@code integration/tconstruct}, which {@code checkSidedImports} forbids common code from
 * touching, and the perk registry is common code that must nonetheless decide at registration time
 * whether the sixteen {@code tc_} perks exist at all.
 *
 * <p>So the probe is a mod id and nothing more. {@code ModList} is Forge's own answer to "is this
 * loaded", available long before any of the mod's classes would be, which is exactly the property
 * that lets {@link com.otectus.runicskills.registry.RegistryPerks} use it during static
 * initialisation.
 *
 * <p><b>Presence is not capability.</b> This says a jar is on the classpath; it says nothing about
 * whether the seam a given perk needs is available on that jar's version. Each perk's effect is
 * gated separately on {@code TConstructCompatibilityStatus.supports(...)} at the point of use.
 */
public final class TConstructPresence {

    /** The upstream mod id, as it appears in that mod's own {@code mods.toml}. */
    public static final String MOD_ID = "tconstruct";

    private TConstructPresence() {
    }

    /** Whether Tinker's Construct is loaded. */
    public static boolean isModLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }
}
