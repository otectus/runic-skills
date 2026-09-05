package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;

/**
 * Compatibility layer for the Legendary Tabs mod (modid: "legendarytabs").
 * <p>
 * When this mod is loaded, Runic Skills registers a native Legendary Tabs tab
 * ({@code client.gui.LegendaryTabRunicSkills}) via {@code TabsMenu.register} during
 * {@code FMLClientSetupEvent} — the Skills tab then appears inside Legendary Tabs' own tab strip
 * (drawn, positioned, highlighted, and paginated by Legendary Tabs itself) exactly like the
 * built-in tabs for FTB Quests, Backpacked, etc. To avoid a double-render, Runic Skills' own strip
 * is suppressed while that tab is live — see {@link InventoryTabOwnership}.
 * <p>
 * <b>Suppression is keyed to {@link #isNativeTabsActive()}, not to {@link #isModLoaded()}.</b>
 * Until 2.0.6 the mere presence of Legendary Tabs turned the built-in strip off, so any failure to
 * register the tab — an API change, a Legendary Tabs 1.x/2.0 mismatch, another mod claiming the
 * same mod id — left the player with no Skills tab at all rather than with the built-in one. The
 * flag below is set only after {@code TabsMenu.register} has actually returned.
 * <p>
 * This class is dedicated-server-safe (no client imports). The typed adapter is named only as a
 * string and reached through {@link Class#forName}, so {@code sfiomn.*} never enters this class's
 * constant pool; client-side extensions — including the inventory-tabs-to-Skills-screen
 * propagation — live in {@code client.integration.LegendaryTabsClientIntegration}.
 */
public class LegendaryTabsIntegration {

    public static final String MOD_ID = "legendarytabs";

    private static final String CLIENT_INTEGRATION_CLASS =
            "com.otectus.runicskills.client.integration.LegendaryTabsClientIntegration";
    private static final String TAB_BASE_CLASS =
            "sfiomn.legendarytabs.api.tabs_menu.TabBase";

    private static volatile boolean nativeTabsActive;

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Probes Legendary Tabs' {@code TabBase} and, if it is there, loads and invokes the
     * client-only integration. This method is called only from client setup.
     *
     * <p>The probe and the surrounding catch are the drift guard, and they are why the call is
     * routed through this facade instead of straight to the client class as it was before 2.0.6.
     * Our tab extends a class we do not ship; a Legendary Tabs release that moves or reshapes
     * {@code TabBase} used to surface as a {@link LinkageError} thrown out of
     * {@code ParallelDispatchEvent#enqueueWork}, which fails mod loading outright. It now surfaces
     * as one WARN plus Runic Skills' own tab strip, matching what L2Tabs and CustomNPCs already do.
     */
    public static void registerClientTab() {
        if (!isModLoaded()) {
            return;
        }

        try {
            // Initialization disabled: this is only a capability check for the API the adapter
            // was compiled against.
            Class.forName(TAB_BASE_CLASS, false, LegendaryTabsIntegration.class.getClassLoader());

            Class<?> integration = Class.forName(CLIENT_INTEGRATION_CLASS);
            integration.getMethod("register").invoke(null);
            nativeTabsActive = true;
        } catch (InvocationTargetException e) {
            nativeTabsActive = false;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            RunicSkills.getLOGGER().warn(
                    "Legendary Tabs is installed, but its Runic Skills tab could not be " +
                            "registered. Runic Skills' own tab strip will be used instead; " +
                            "Minecraft will continue.",
                    cause
            );
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            nativeTabsActive = false;
            RunicSkills.getLOGGER().warn(
                    "Legendary Tabs is installed, but its API is incompatible with Runic Skills " +
                            "(Legendary Tabs 2.0 or newer is expected). Runic Skills' own tab " +
                            "strip will be used instead; Minecraft will continue.",
                    e
            );
        }
    }

    /** Returns true only after the native Legendary Tabs Skills tab was registered successfully. */
    public static boolean isNativeTabsActive() {
        return nativeTabsActive;
    }
}
