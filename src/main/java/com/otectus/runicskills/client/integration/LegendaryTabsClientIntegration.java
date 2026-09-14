package com.otectus.runicskills.client.integration;

import com.mojang.logging.LogUtils;
import com.otectus.runicskills.client.gui.InventoryTabLayout;
import com.otectus.runicskills.client.gui.LegendaryTabRunicSkills;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.integration.L2TabsIntegration;
import com.otectus.runicskills.integration.LegendaryTabsIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import sfiomn.legendarytabs.api.tabs_menu.TabBase;
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;
import sfiomn.legendarytabs.client.screens.TabButton;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Optional, client-only adapter. All Legendary Tabs symbols stay behind the reflective
 * {@link LegendaryTabsIntegration} facade so installations without that mod still load.
 *
 * <p>Legendary Tabs owns the buttons, focus, selected state and pagination. Foreign inventory
 * destinations join that same strip before its Init.Post handler builds the actual widgets.
 * Suppression of the other strips is conditional on those replacement widgets being usable.
 */
public final class LegendaryTabsClientIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NEXT_BUTTON = "sfiomn.legendarytabs.client.screens.NextTabsButton";
    private static final Map<String, LegendaryTabDestination> DESTINATIONS = new LinkedHashMap<>();
    private static LegendaryTabRunicSkills tabInstance;
    private static Method l2Synchronize;
    private static boolean l2DestinationsReady;
    private static boolean warned;

    private LegendaryTabsClientIntegration() {}

    public static void registerTab() {
        if (tabInstance != null) return;
        LegendaryTabRunicSkills tab = new LegendaryTabRunicSkills();
        TabsMenu.register(tab);
        // Upstream register catches Exception internally: returning alone is not proof.
        if (!contains(TabsMenu.getScreenInfo(InventoryScreen.class), tab)) {
            throw new IllegalStateException("Legendary Tabs did not register the Skills destination");
        }
        tabInstance = tab;
    }

    public static void register() {
        registerTab();
        synchronizeDestinations();
        NativeInventoryTabs.registerStrip("legendarytabs", LegendaryTabsClientIntegration::isStripPresent,
                LegendaryTabsClientIntegration::controls);
        MinecraftForge.EVENT_BUS.register(LegendaryTabsClientIntegration.class);
    }

    /** Data-driven Legendary tabs arrive on world join and /reload, after client setup. */
    public static void synchronizeTabStripAcrossScreens() {
        if (!LegendaryTabsIntegration.isModLoaded()) return;
        try {
            synchronizeDestinations();
            LOGGER.info("[runicskills] Unified Legendary Tabs navigation across {} registered screens.",
                    TabsMenu.getRegisteredScreens().size());
        } catch (RuntimeException | LinkageError e) {
            warnOnce(e);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenInitPre(ScreenEvent.Init.Pre event) {
        synchronizeFor(event.getScreen());
    }

    /** L2 Tabs creates its manager during initialization; export it before Legendary's NORMAL handler. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void beforeNativeButtons(ScreenEvent.Init.Post event) {
        synchronizeFor(event.getScreen());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        updateLayout(event.getScreen());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        updateLayout(event.getScreen());
    }

    private static void synchronizeFor(Screen screen) {
        try {
            if (L2TabsIntegration.isNativeTabsActive() && Minecraft.getInstance().player != null) {
                if (l2Synchronize == null) {
                    l2Synchronize = Class.forName(
                            "com.otectus.runicskills.client.integration.L2TabsClientIntegration")
                            .getMethod("synchronizeDestinations", Screen.class);
                }
                l2Synchronize.invoke(null, screen);
                l2DestinationsReady = true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            l2DestinationsReady = false;
            warnOnce(e);
        }
        try {
            synchronizeDestinations();
        } catch (RuntimeException | LinkageError e) {
            warnOnce(e);
        }
    }

    private static void synchronizeDestinations() {
        if (tabInstance == null) return;
        Set<Class<?>> commonScreens = new LinkedHashSet<>();
        commonScreens.add(InventoryScreen.class);
        commonScreens.add(RunicSkillsScreen.class);
        List<NativeInventoryTabs.Destination> destinations = NativeInventoryTabs.destinations();
        Set<String> currentIds = new LinkedHashSet<>();
        for (NativeInventoryTabs.Destination destination : destinations) currentIds.add(destination.id());
        List<LegendaryTabDestination> obsolete = DESTINATIONS.entrySet().stream()
                .filter(entry -> !currentIds.contains(entry.getKey())).map(Map.Entry::getValue).toList();
        if (!obsolete.isEmpty()) {
            for (Class<?> screenClass : new ArrayList<>(TabsMenu.getRegisteredScreens())) {
                TabsMenu.ScreenInfo info = TabsMenu.getScreenInfo(screenClass);
                if (info != null && info.tabs != null) {
                    for (List<TabBase> bucket : info.tabs.values()) bucket.removeAll(obsolete);
                }
            }
            DESTINATIONS.keySet().retainAll(currentIds);
        }
        int priority = 100;
        for (NativeInventoryTabs.Destination destination : destinations) {
            commonScreens.addAll(destination.screens());
            LegendaryTabDestination tab = DESTINATIONS.get(destination.id());
            if (tab == null) {
                tab = new LegendaryTabDestination(destination, priority++);
                // Call directly: upstream register swallows exceptions and can claim success.
                tab.initTabOnScreens();
                DESTINATIONS.put(destination.id(), tab);
            } else {
                tab.update(destination);
                priority++;
            }
        }

        // Only these known player-navigation screens acquire a new strip. Existing addon
        // screens keep their own geometry/skin; chests and unrelated menus stay untouched.
        for (Class<?> screenClass : commonScreens) mirrorInventoryStrip(screenClass);
        for (Class<?> screenClass : new ArrayList<>(TabsMenu.getRegisteredScreens())) {
            TabsMenu.ScreenInfo info = TabsMenu.getScreenInfo(screenClass);
            addIfMissing(info, screenClass, tabInstance, HandlerConfigClient.legendaryTabsPriority.get());
            int foreignPriority = 100;
            for (LegendaryTabDestination tab : DESTINATIONS.values()) {
                addIfMissing(info, screenClass, tab, foreignPriority++);
            }
        }
    }

    private static void mirrorInventoryStrip(Class<?> screenClass) {
        if (screenClass == InventoryScreen.class) return;
        TabsMenu.ScreenInfo inventory = TabsMenu.getScreenInfo(InventoryScreen.class);
        if (inventory == null || inventory.tabs == null) return;
        for (Map.Entry<Integer, List<TabBase>> bucket : inventory.tabs.entrySet()) {
            for (TabBase tab : new ArrayList<>(bucket.getValue())) {
                addIfMissing(TabsMenu.getScreenInfo(screenClass), screenClass, tab, bucket.getKey());
            }
        }
    }

    private static void addIfMissing(TabsMenu.ScreenInfo info, Class<?> screenClass, TabBase tab, int priority) {
        if (contains(info, tab)) return;
        TabsMenu.addTabToScreen(tab, screenClass,
                player -> panelSize(screenClass, true), player -> panelSize(screenClass, false), priority);
    }

    private static int panelSize(Class<?> screenClass, boolean width) {
        Screen current = Minecraft.getInstance().screen;
        if (current != null && TabsMenu.resolveScreenIdentity(current) == screenClass) {
            InventoryTabLayout.Rect panel = NativeInventoryTabs.panel(current);
            if (panel != null) return width ? panel.w() : panel.h();
        }
        return width ? 176 : screenClass == RunicSkillsScreen.class ? 194 : 166;
    }

    private static boolean contains(TabsMenu.ScreenInfo info, TabBase tab) {
        return info != null && info.tabs != null && info.tabs.values().stream().anyMatch(list -> list.contains(tab));
    }

    /** Keep native hitboxes on the actual panel, including recipe-book shifts and CNPC geometry. */
    public static void updateLayout(Screen screen) {
        try {
            InventoryTabLayout.Rect panel = NativeInventoryTabs.panel(screen);
            if (panel != null && !controls(screen).isEmpty()) {
                TabsMenu.updateButtonsPosition(screen, panel.x(), panel.y());
            }
        } catch (RuntimeException | LinkageError e) {
            warnOnce(e);
        }
    }

    public static List<AbstractWidget> controls(Screen screen) {
        List<AbstractWidget> controls = new ArrayList<>();
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget
                    && (child instanceof TabButton || child.getClass().getName().equals(NEXT_BUTTON))) {
                controls.add(widget);
            }
        }
        return controls;
    }

    /** A registered mod is insufficient: every enabled replacement must actually be reachable. */
    public static boolean isStripPresent(Screen screen) {
        try {
            if (L2TabsIntegration.isNativeTabsActive() && !l2DestinationsReady) return false;
            if (!hasDestination(screen, "runicskills_skills")) return false;
            for (NativeInventoryTabs.Destination destination : NativeInventoryTabs.destinations()) {
                if (destination.enabled().getAsBoolean() && !hasDestination(screen, destination.id())) return false;
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            warnOnce(e);
            return false;
        }
    }

    /** Paginated destinations count only when a native page and its page control are visible. */
    public static boolean hasDestination(Screen screen, String id) {
        boolean visibleTab = false;
        boolean visiblePager = false;
        for (AbstractWidget widget : controls(screen)) {
            if (!usable(widget, screen)) continue;
            if (widget instanceof TabButton button) {
                visibleTab = true;
                if (button.tabBase != null && id.equals(button.tabBase.getId())) return true;
            } else {
                visiblePager = true;
            }
        }
        if (!visibleTab) return false;
        TabsMenu.ScreenInfo info = TabsMenu.getScreenInfo(TabsMenu.resolveScreenIdentity(screen));
        if (info == null || info.tabs == null) return false;
        for (List<TabBase> bucket : info.tabs.values()) {
            for (TabBase tab : bucket) {
                if (id.equals(tab.getId()) && tab.isEnabled(Minecraft.getInstance().player)
                        && (visiblePager || tab.isCurrentlyUsed(screen))) return true;
            }
        }
        return false;
    }

    private static boolean usable(AbstractWidget widget, Screen screen) {
        return widget.visible && widget.active && widget.getX() >= 0 && widget.getY() >= 0
                && widget.getX() + widget.getWidth() <= screen.width
                && widget.getY() + widget.getHeight() <= screen.height;
    }

    private static void warnOnce(Throwable e) {
        if (warned) return;
        warned = true;
        LOGGER.warn("[runicskills] Could not unify Legendary Tabs navigation; other native controls remain available.", e);
    }
}
