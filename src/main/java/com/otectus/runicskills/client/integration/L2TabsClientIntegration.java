package com.otectus.runicskills.client.integration;

import com.otectus.runicskills.client.gui.InventoryTabLayout.Rect;
import com.otectus.runicskills.client.gui.TabRunicSkills;
import com.otectus.runicskills.client.integration.NativeInventoryTabs.Destination;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.common.util.LogOnce;
import com.otectus.runicskills.registry.RegistryItems;
import dev.xkmc.l2tabs.init.data.L2TabsConfig;
import dev.xkmc.l2tabs.tabs.core.BaseTab;
import dev.xkmc.l2tabs.tabs.core.FloatingButton;
import dev.xkmc.l2tabs.tabs.core.TabManager;
import dev.xkmc.l2tabs.tabs.core.TabRegistry;
import dev.xkmc.l2tabs.tabs.core.TabToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Optional, client-only L2 adapter. No class outside this adapter links the two tab APIs. */
public final class L2TabsClientIntegration {
    private static TabToken<TabRunicSkills> skills;
    private static final Map<String, TabToken<DestinationTab>> BRIDGED = new LinkedHashMap<>();
    private static final Map<TabToken<?>, String> EXPORTED_IDS = new IdentityHashMap<>();
    private static final Set<Class<?>> SHARED_SCREENS = new LinkedHashSet<>();
    private static Field previousPage;
    private static Field nextPage;
    private static boolean pagingFieldsFailed;

    private L2TabsClientIntegration() {}

    public static void registerTab() {
        skills = TabRegistry.registerTab(3500, TabRunicSkills::new,
                RegistryItems.LEVELING_BOOK, Component.translatable("screen.skill.title"));
        int priority = 3600;
        for (Destination destination : NativeInventoryTabs.destinations()) {
            // Do not feed exported L2 destinations back into the registry on later reloads.
            if (!destination.id().startsWith("customnpcs:") || !destination.enabled().getAsBoolean()) continue;
            TabToken<DestinationTab> token = TabRegistry.registerTab(priority++,
                    (value, manager, icon, title) -> new DestinationTab(value, manager, destination),
                    () -> destination.icon().get().getItem(), destination.title());
            BRIDGED.put(destination.id(), token);
        }
        SHARED_SCREENS.add(InventoryScreen.class);
        SHARED_SCREENS.add(RunicSkillsScreen.class);
        addScreen("dev.xkmc.l2tabs.tabs.contents.AttributeScreen");
        addScreen("dev.xkmc.l2tabs.compat.CuriosListScreen");
        addScreen("top.theillusivec4.curios.client.gui.CuriosScreen");
        registerTextScreenPanel();
        NativeInventoryTabs.registerStrip("l2tabs", L2TabsClientIntegration::hasNativeStrip,
                L2TabsClientIntegration::controls);
        MinecraftForge.EVENT_BUS.register(L2TabsClientIntegration.class);
    }

    /** Skills and CustomNPCs do not create an L2 manager themselves. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        TabToken<?> selected = selectedBridge(screen);
        boolean onlyCurios = ModList.get().isLoaded("curios") && L2TabsConfig.CLIENT.showTabsOnlyCurio.get();
        if (selected != null && !onlyCurios && tabs(screen).isEmpty()) {
            new TabManager(screen).init(event::addListener, selected);
        }
        anchorBridgeScreens(screen);
        if (!tabs(screen).isEmpty()) SHARED_SCREENS.add(screen.getClass());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        anchorBridgeScreens(event.getScreen());
        for (BaseTab<?> tab : tabs(event.getScreen())) {
            if (tab instanceof DestinationTab bridge) bridge.updateVisibility();
        }
    }

    /**
     * Supplies Legendary Tabs with filtered L2 destinations, including addons. Called explicitly
     * before Legendary builds its widgets, so event order cannot lose the first screen's bridge.
     * Token factories and navigation remain authoritative in L2.
     */
    public static void synchronizeDestinations(Screen screen) {
        if (screen == null) return;
        if (!tabs(screen).isEmpty()) SHARED_SCREENS.add(screen.getClass());
        Set<Class<?>> screens = new LinkedHashSet<>(SHARED_SCREENS);
        for (Destination destination : NativeInventoryTabs.destinations()) {
            if (!destination.id().startsWith("l2tabs:")) screens.addAll(destination.screens());
        }
        NativeInventoryTabs.unregisterPrefix("l2tabs:");
        for (TabToken<?> token : List.copyOf(TabRegistry.getTabs())) {
            if (token == skills || BRIDGED.containsValue(token)) continue;
            BaseTab<?> nativeTab = token.create(new TabManager(screen));
            // The primary provider already supplies Inventory. Keep one inventory button.
            if (nativeTab.getClass().getName().equals("dev.xkmc.l2tabs.tabs.contents.TabInventory")) continue;
            String id = EXPORTED_IDS.computeIfAbsent(token, value -> {
                String key = value.title.getContents() instanceof TranslatableContents text
                        ? text.getKey() : nativeTab.getClass().getName();
                return "l2tabs:" + key + ":" + EXPORTED_IDS.size();
            });
            ItemStack icon = nativeTab.stack.copy();
            // L2's Curios tab supplies AIR and draws a texture itself. The shared item-only
            // bridge needs an equipment icon rather than an empty clickable tab.
            if (icon.isEmpty()) icon = new ItemStack(Items.LEATHER_CHESTPLATE);
            ItemStack exportedIcon = icon;
            String nativeClass = nativeTab.getClass().getName();
            NativeInventoryTabs.register(new Destination(id, exportedIcon::copy, token.title,
                    () -> open(token), current -> selected(token, nativeClass, current),
                    () -> L2TabsConfig.CLIENT.showTabs.get() && TabRegistry.getTabs().contains(token), screens));
        }
    }

    private static void open(TabToken<?> token) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null || !TabRegistry.getTabs().contains(token)) return;
        token.create(new TabManager(screen)).onTabClicked();
    }

    private static boolean selected(TabToken<?> token, String tabClass, Screen screen) {
        for (BaseTab<?> tab : tabs(screen)) {
            if (tab.manager.selected == token) return true;
        }
        // Legendary initializes before L2's Init.Post. Recognize the built-in target identities
        // so their very first widget already has the correct selected state.
        return (tabClass.equals("dev.xkmc.l2tabs.tabs.contents.TabAttributes")
                && screen.getClass().getName().equals("dev.xkmc.l2tabs.tabs.contents.AttributeScreen"))
                || (tabClass.equals("dev.xkmc.l2tabs.compat.TabCurios")
                && screen.getClass().getName().equals("dev.xkmc.l2tabs.compat.CuriosListScreen"));
    }

    private static TabToken<?> selectedBridge(Screen screen) {
        if (screen instanceof RunicSkillsScreen) return skills;
        for (Destination destination : NativeInventoryTabs.destinations()) {
            TabToken<?> token = BRIDGED.get(destination.id());
            if (token != null && destination.enabled().getAsBoolean()
                    && destination.selected().test(screen)) return token;
        }
        return null;
    }

    /** L2 assumes a centered 176x166 panel for unknown Screens; Skills is 194 pixels tall. */
    private static void anchorBridgeScreens(Screen screen) {
        if (selectedBridge(screen) == null) return;
        Rect panel = NativeInventoryTabs.panel(screen);
        if (panel == null) return;
        for (AbstractWidget widget : controls(screen)) {
            if (!(widget instanceof FloatingButton floating)) continue;
            int x;
            int y;
            if (widget instanceof BaseTab<?> tab) {
                int index = tab.token.getIndex();
                // The first L2 page has six tabs. Later pages reserve slot zero for Back.
                int slot = index < 6 ? index : 1 + (index - 6) % 5;
                x = slot * 26;
                y = -28;
            } else {
                x = widget.getMessage().getString().equals("<") ? 3 : 159;
                y = -23;
            }
            floating.setXRef(() -> currentPanel(screen, panel).x(), x);
            floating.setYRef(() -> currentPanel(screen, panel).y(), y);
            // BackgroundRendered may run before FloatingButton evaluates its suppliers. Input
            // before the first frame must also match the eventual painted position.
            floating.setX(panel.x() + x);
            floating.setY(panel.y() + y);
        }
    }

    private static Rect currentPanel(Screen screen, Rect fallback) {
        Rect panel = NativeInventoryTabs.panel(screen);
        return panel == null ? fallback : panel;
    }

    public static boolean hasNativeStrip(Screen screen) {
        if (skills == null) return false;
        // Skills may be on another page or explicitly hidden in L2's preferences. Demand an
        // actual visible manager strip instead of undoing that choice with a duplicate fallback.
        return tabs(screen).stream().anyMatch(tab -> NativeInventoryTabs.onScreen(tab, screen));
    }

    /** Includes precisely the manager's paging controls, without hiding unrelated widgets. */
    public static List<AbstractWidget> controls(Screen screen) {
        List<AbstractWidget> controls = new ArrayList<>();
        Set<TabManager> managers = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (BaseTab<?> tab : tabs(screen)) {
            controls.add(tab);
            managers.add(tab.manager);
        }
        if (managers.isEmpty() || pagingFieldsFailed) return controls;
        try {
            if (previousPage == null) {
                previousPage = TabManager.class.getDeclaredField("left");
                nextPage = TabManager.class.getDeclaredField("right");
                previousPage.setAccessible(true);
                nextPage.setAccessible(true);
            }
            for (TabManager manager : managers) {
                if (previousPage.get(manager) instanceof AbstractWidget previous) controls.add(previous);
                if (nextPage.get(manager) instanceof AbstractWidget next) controls.add(next);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            pagingFieldsFailed = true;
            LogOnce.warnOnce("l2tabs:paging-controls",
                    "Runic Skills could not inspect L2 Tabs paging controls: {}", exception.toString());
        }
        return controls;
    }

    private static List<BaseTab<?>> tabs(Screen screen) {
        List<BaseTab<?>> tabs = new ArrayList<>();
        for (var listener : screen.children()) {
            if (listener instanceof BaseTab<?> tab) tabs.add(tab);
        }
        return tabs;
    }

    private static void addScreen(String name) {
        try {
            SHARED_SCREENS.add(Class.forName(name, false, L2TabsClientIntegration.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Curios is optional even when L2 Tabs is installed.
        }
    }

    private static void registerTextScreenPanel() {
        try {
            Class<?> type = Class.forName("dev.xkmc.l2tabs.tabs.contents.BaseTextScreen", false,
                    L2TabsClientIntegration.class.getClassLoader());
            Field left = type.getField("leftPos");
            Field top = type.getField("topPos");
            Field width = type.getField("imageWidth");
            Field height = type.getField("imageHeight");
            NativeInventoryTabs.registerPanel(type, screen -> {
                try {
                    return new Rect(left.getInt(screen), top.getInt(screen),
                            width.getInt(screen), height.getInt(screen));
                } catch (IllegalAccessException exception) {
                    return null;
                }
            });
        } catch (ReflectiveOperationException | LinkageError exception) {
            LogOnce.warnOnce("l2tabs:text-panel",
                    "Runic Skills could not inspect L2 Tabs panel geometry: {}", exception.toString());
        }
    }

    private static final class DestinationTab extends BaseTab<DestinationTab> {
        private final Destination destination;

        private DestinationTab(TabToken<DestinationTab> token, TabManager manager, Destination destination) {
            super(token, manager, destination.icon().get(), destination.title());
            this.destination = destination;
        }

        @Override
        public void onTabClicked() {
            if (destination.enabled().getAsBoolean()) destination.open().run();
        }

        private void updateVisibility() {
            this.visible = this.page == this.manager.tabPage && destination.enabled().getAsBoolean();
            this.active = this.visible;
        }
    }
}
