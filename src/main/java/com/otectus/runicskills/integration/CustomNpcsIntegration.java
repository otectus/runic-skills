package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerConfigClient;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;

/**
 * Server-safe bridge to CustomNPCs' inventory tab strip.
 *
 * <p>With CustomNPCs installed the player already has a tab strip on the inventory screen
 * (Inventory, Factions, Quests). Drawing Runic Skills' own strip alongside it gives two strips and
 * two inventory icons, so instead we insert a Skills tab of CustomNPCs' own type into theirs. The
 * typed code that does that lives in the client-only integration class named below, reached by
 * {@link Class#forName} so no {@code noppes.*} type ever enters this class's constant pool.
 */
public class CustomNpcsIntegration {

    private static final String CLIENT_INTEGRATION_CLASS =
            "com.otectus.runicskills.client.integration.CustomNpcsTabsClientIntegration";
    private static final String ABSTRACT_TAB_CLASS =
            "noppes.npcs.client.gui.player.tabs.AbstractTab";
    private static final String SCREEN_CLASS =
            "net.minecraft.client.gui.screens.Screen";

    private static volatile boolean registered;

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("customnpcs");
    }

    /**
     * Probes the real {@code AbstractTab} and, if it matches what the compile-time stub in
     * {@code src/customnpcsApi/java} describes, loads and invokes the client-only integration.
     * This method is called only from client setup.
     *
     * <p>The probe is the drift guard. Our tab subclasses a class we do not compile against for
     * real, so a CustomNPCs update that renames the constructor, {@code init(Screen)}, {@code id}
     * or {@code onTabClicked} would otherwise surface as a {@code NoSuchMethodError} the first
     * time a player opened their inventory. Checking first turns that into one WARN and Runic
     * Skills' own tab strip, which is the pre-existing behaviour anyway.
     */
    public static void registerClientTabs() {
        if (!isModLoaded()) {
            return;
        }

        try {
            ClassLoader loader = CustomNpcsIntegration.class.getClassLoader();
            Class<?> abstractTab = Class.forName(ABSTRACT_TAB_CLASS, false, loader);
            abstractTab.getConstructor(int.class, int.class, int.class,
                    Class.forName("net.minecraft.world.item.ItemStack", false, loader));
            abstractTab.getMethod("init", Class.forName(SCREEN_CLASS, false, loader));
            abstractTab.getMethod("onTabClicked");
            abstractTab.getField("id");

            Class<?> integration = Class.forName(CLIENT_INTEGRATION_CLASS);
            integration.getMethod("register").invoke(null);
            registered = true;
        } catch (InvocationTargetException e) {
            registered = false;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            RunicSkills.getLOGGER().warn(
                    "CustomNPCs is installed, but the native Runic Skills tab could not be " +
                            "registered. Runic Skills' own tab strip will be used instead; " +
                            "Minecraft will continue.",
                    cause
            );
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            registered = false;
            RunicSkills.getLOGGER().warn(
                    "CustomNPCs is installed, but its tab API is incompatible with Runic Skills " +
                            "(CustomNPCs 1.20.1 GBPort 1.20.1.20260711 or a build with the same " +
                            "AbstractTab surface is expected). Runic Skills' own tab strip will be " +
                            "used instead; Minecraft will continue.",
                    e
            );
        }
    }

    /**
     * Returns true only when the native Skills tab was registered successfully and the player has
     * not turned it off. False restores the separate Runic Skills strip.
     */
    public static boolean isNativeTabsActive() {
        return registered && HandlerConfigClient.customNpcsNativeTabs.get();
    }
}
