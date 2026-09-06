package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

/**
 * Which Tinker's Construct add-ons are installed, asked without naming a single one of their classes.
 *
 * <p>The same reasoning as {@link TConstructPresence}, one layer further out. An add-on is optional
 * on top of an optional mod, so its classes are absent from strictly more installs than Tinkers'
 * own — and the perk registry, which is common code, still has to decide during static
 * initialisation whether the seven add-on perks exist at all. A mod id is the only thing that can
 * be asked that early and that safely.
 *
 * <p><b>Two different questions live here.</b> The <em>add-on</em> ids decide whether a perk is
 * registered: a player with Tinkers' Levelling installed can spend a point on Seasoned Hands. The
 * <em>companion</em> ids — Botania, Create, Malum, Ars Nouveau — decide whether the perk can do
 * anything, which is a capability question answered later by
 * {@code TConstructCompatibilityStatus}. Registering on the add-on and gating the effect on the
 * companion is what §10.3 means by "retain the acquired perk ID but mark it unavailable".
 *
 * <p><b>Tinkers' Ingenuity has no constant.</b> Its perk, {@code tc_medallion_concord}, is a
 * reserved id in 2.0.7 rather than a registered perk: no 1.20.1 artifact for that add-on could be
 * resolved from any maven this build reaches, so there is nothing to compile against and nothing to
 * test. The reservation is recorded in the compatibility diagnostic instead, which is where a
 * player can see why the id exists and does nothing.
 */
public final class TcAddonPresence {

    /** TCIntegrations — the §12.2 first-class adapter, mod id from its own {@code mods.toml}. */
    public static final String TCINTEGRATIONS = "tcintegrations";

    /** Tinkers' Levelling Addon (§12.3). */
    public static final String TINKERS_LEVELLING = "tinkerslevellingaddon";

    /** Tinkers' Delight (§12.6). Note the underscore: the display name is not the id. */
    public static final String TINKERS_DELIGHT = "tinkers_delight";

    /** Tinkers' Advanced (§12.7). Core, Tools and Materials all publish under this one id. */
    public static final String TINKERS_ADVANCED = "tinkers_advanced";

    /** EtSTLib, the energy library Tinkers' Advanced requires and charges tools through. */
    public static final String ETSTLIB = "etstlib";

    /** Farmer's Delight, a mandatory dependency of Tinkers' Delight and the owner of its effect. */
    public static final String FARMERS_DELIGHT = "farmersdelight";

    /** Botania, whose mana pays for TCIntegrations' repair charge. */
    public static final String BOTANIA = "botania";

    /** Ars Nouveau, whose source pays for TCIntegrations' armour repair. */
    public static final String ARS_NOUVEAU = "ars_nouveau";

    /** Create, whose Mechanical Arm modifier grants TCIntegrations' offhand attacks. */
    public static final String CREATE = "create";

    /** Malum, whose Soul Stained steel TCIntegrations turns into a modifier. */
    public static final String MALUM = "malum";

    /** Tinkers' Thinking. Mod id read from its own {@code mods.toml}; note the underscore. */
    public static final String TINKERS_THINKING = "tinkers_thinking";

    /**
     * Tinkers' Jewelry. The id has no separator even though the display name does.
     *
     * <p>Read out of {@code tinkersjewelry-1.2.0.jar}'s {@code mods.toml} rather than guessed from
     * the file name: the two happen to agree here and routinely do not.
     */
    public static final String TINKERS_JEWELRY = "tinkersjewelry";

    /**
     * Tinkers' Katanas, which has no adapter and needs none.
     *
     * <p>The jar ships zero Java classes: its two tools are JsonThings definitions that register
     * into Tinkers' own {@code tconstruct:modifiable/*} item tags, so every classification, repair
     * and wear seam this mod already owns reaches them unchanged. The constant exists so the
     * diagnostic can say "installed, covered by the core seams" rather than leaving a mod id a
     * player can see in their pack unexplained.
     */
    public static final String TINKERS_KATANAS = "tinkers_katanas";

    /** Curios, a mandatory dependency of Tinkers' Jewelry and where its rings are worn. */
    public static final String CURIOS = "curios";

    private TcAddonPresence() {
    }

    /** Whether {@code modId} is loaded. Safe before any of that mod's classes could resolve. */
    public static boolean isLoaded(String modId) {
        return modId != null && ModList.get() != null && ModList.get().isLoaded(modId);
    }

    /** Whether Tinkers' Construct <em>and</em> {@code modId} are both loaded. */
    public static boolean isAddonLoaded(String modId) {
        return TConstructPresence.isModLoaded() && isLoaded(modId);
    }
}
