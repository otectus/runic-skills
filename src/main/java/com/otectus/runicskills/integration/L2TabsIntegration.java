package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;

public class L2TabsIntegration {

    private static final String CLIENT_INTEGRATION_CLASS =
            "com.otectus.runicskills.client.integration.L2TabsClientIntegration";
    private static final String TAB_FACTORY_CLASS =
            "dev.xkmc.l2tabs.tabs.core.TabRegistry$TabFactory";

    private static volatile boolean nativeTabsActive;

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("l2tabs");
    }

    /**
     * Loads and invokes the client-only integration without putting L2 Tabs API types in an
     * always-loaded class's constant pool. This method is called only from client setup.
     *
     * <p>L2 Tabs changed its registration API between 0.3.1 and 0.3.3. Because it is an optional
     * integration, an incompatible version must disable only the native tab rather than crashing
     * Minecraft's deferred setup queue.</p>
     */
    public static void registerClientTab() {
        if (!isModLoaded()) {
            return;
        }

        try {
            // Probe before loading our typed adapter. Initialization is disabled because this is
            // only a capability check for the API against which the adapter was compiled.
            Class.forName(TAB_FACTORY_CLASS, false, L2TabsIntegration.class.getClassLoader());

            Class<?> integration = Class.forName(CLIENT_INTEGRATION_CLASS);
            integration.getMethod("registerTab").invoke(null);
            nativeTabsActive = true;
        } catch (InvocationTargetException e) {
            nativeTabsActive = false;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            RunicSkills.getLOGGER().warn(
                    "L2 Tabs is installed, but its Runic Skills tab could not be registered. " +
                            "Native L2 Tabs integration will be disabled; Minecraft will continue.",
                    cause
            );
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            nativeTabsActive = false;
            RunicSkills.getLOGGER().warn(
                    "L2 Tabs is installed, but its API is incompatible with Runic Skills " +
                            "(L2 Tabs 0.3.3 or newer is expected). Native integration will be " +
                            "disabled; Minecraft will continue.",
                    e
            );
        }
    }

    /** Returns true only after the native Skills tab was registered successfully. */
    public static boolean isNativeTabsActive() {
        return nativeTabsActive;
    }
}
