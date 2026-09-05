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

    /**
     * Whether the Skills tab is actually on the screen being drawn right now.
     *
     * <p>Written once per frame by the client integration's {@code Render.Pre} pass, which is the
     * only code that knows the answer. See {@link #isNativeTabsActive()} for why the suppression
     * test needs it.
     */
    private static volatile boolean nativeTabPresent;

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
     * Whether the inline CustomNPCs tab is the arrangement we should be attempting: the probe
     * passed, the client integration is subscribed, and the player has not turned it off.
     *
     * <p>This is the question the client integration asks itself before trying to insert the tab.
     * It is <em>not</em> the question the tab strip asks — see {@link #isNativeTabsActive()}.
     */
    public static boolean isNativeTabsPreferred() {
        return registered && HandlerConfigClient.customNpcsNativeTabs.get();
    }

    /**
     * Records whether the Skills tab is present on the screen currently being drawn. Called every
     * frame from the client integration; {@code false} is as important as {@code true}, because it
     * is what hands the screen back to Runic Skills' own strip.
     */
    public static void setNativeTabPresent(boolean present) {
        nativeTabPresent = present;
    }

    /**
     * Returns true only when CustomNPCs is really drawing the Skills tab on the current screen.
     * False restores the separate Runic Skills strip.
     *
     * <p>Until 2.0.6 this returned {@link #isNativeTabsPreferred()}, i.e. "the integration loaded
     * without throwing". That is not the same claim. The integration inserts its tab into a strip
     * CustomNPCs builds on the inventory screen, and there are ordinary reasons for that strip not
     * to be there — a CustomNPCs build or server configuration that does not add it, a screen a
     * future release stops decorating, or our own {@code addRenderableWidget} reflection failing.
     * In every one of those cases the old test suppressed Runic Skills' strip in favour of a tab
     * that was never added, and the player was left with no Skills tab at all. Asking about the
     * screen in front of the player instead makes the fallback automatic and per-screen.
     */
    public static boolean isNativeTabsActive() {
        return isNativeTabsPreferred() && nativeTabPresent;
    }
}
