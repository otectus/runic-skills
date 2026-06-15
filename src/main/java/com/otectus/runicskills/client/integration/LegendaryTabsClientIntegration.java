package com.otectus.runicskills.client.integration;

import com.mojang.logging.LogUtils;
import com.otectus.runicskills.client.gui.LegendaryTabRunicSkills;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.integration.LegendaryTabsIntegration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import sfiomn.legendarytabs.api.tabs_menu.TabBase;
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Client-side companion to {@link LegendaryTabsIntegration}. Lives under {@code client/}
 * because it touches {@code net.minecraft.client.*} (Screen, InventoryScreen) which the
 * {@code :checkSidedImports} lint forbids in the shared {@code integration/} package.
 */
public final class LegendaryTabsClientIntegration {

    private static final String INVENTORY_TAB_CLASS = "sfiomn.legendarytabs.client.tabs_menu.InventoryTab";
    private static final String OUR_TAB_CLASS = "com.otectus.runicskills.client.gui.LegendaryTabRunicSkills";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean synchronized_ = false;

    private LegendaryTabsClientIntegration() {}

    /**
     * Registers the Runic Skills tab with Legendary Tabs' {@code TabsMenu}.
     * <p>
     * This method body contains direct bytecode references to {@code sfiomn.*} types and
     * to {@link LegendaryTabRunicSkills} (which extends {@code TabBase}). It MUST only be
     * invoked when {@link LegendaryTabsIntegration#isModLoaded()} is {@code true} — calling
     * it (or even loading this class via a method reference to it) when Legendary Tabs is
     * absent will throw {@link NoClassDefFoundError} on {@code TabBase}.
     * <p>
     * Isolating this call into a dedicated method — rather than inlining a lambda inside
     * {@code ClientProxy.clientSetup} — keeps ClientProxy's bytecode free of any direct
     * reference to optional-mod types. Forge's {@code AutomaticEventSubscriber} loads every
     * {@code @EventBusSubscriber} class with {@code Class.forName(..., true, loader)} at
     * mod construction; the JVM verifier then checks assignability for every method body,
     * and a lambda body like {@code TabsMenu.register(new LegendaryTabRunicSkills())}
     * triggers eager resolution of {@code TabBase} even though the lambda is never invoked.
     * Moving the call here defers class loading until {@code isModLoaded()} is true.
     */
    public static void registerTab() {
        TabsMenu.register(new LegendaryTabRunicSkills());
    }

    /**
     * Two-way synchronization of the Legendary Tabs tab strip across every inventory-like
     * screen that any loaded mod has registered against.
     *
     * <ul>
     *   <li><b>Forward:</b> Copy every tab registered on {@link InventoryScreen} onto
     *       {@link RunicSkillsScreen} so the Skills page shows the same full strip a
     *       player sees on the vanilla inventory — same tabs, same order, same X anchor.</li>
     *   <li><b>Reverse:</b> Register our Skills tab on every <i>other</i> screen class
     *       that already has the built-in {@code InventoryTab}. This keeps the Skills
     *       tab visible when the player is on any companion inventory screen (First Aid
     *       medkit, Curios, Travelers Backpack, the Pufferfish / Reskillable skill pages,
     *       …). Without this, our tab vanishes the moment they switch to one of those
     *       screens and reappears only when they return to the vanilla inventory.</li>
     * </ul>
     *
     * <p>Legendary Tabs' {@code TabsMenu.initScreenButtons} keys off
     * {@code screen.getClass()} (exact match, not {@code instanceof}) and iterates only
     * tabs explicitly registered against that class. Built-in tabs each register against
     * a hard-coded list of screens that cannot be extended by us at library level, so we
     * extend it via reflection after every mod's {@code TabsMenu.register} call has run
     * — i.e. during {@code FMLLoadCompleteEvent}.
     *
     * <p>The {@code tabsScreens} map is a private static inside {@link TabsMenu}; the
     * {@code tabs} map inside each {@code ScreenInfo} is public. Any failure (library
     * internals renamed in a future version, cast fails, etc.) logs a single warning and
     * degrades gracefully — the rest of the mod keeps working.
     */
    public static synchronized void synchronizeTabStripAcrossScreens() {
        if (synchronized_) return;
        synchronized_ = true;
        if (!LegendaryTabsIntegration.isModLoaded()) return;

        try {
            Field tabsScreensField = TabsMenu.class.getDeclaredField("tabsScreens");
            tabsScreensField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Class<? extends Screen>, Object> tabsScreens =
                    (Map<Class<? extends Screen>, Object>) tabsScreensField.get(null);

            Object inventoryScreenInfo = tabsScreens.get(InventoryScreen.class);
            if (inventoryScreenInfo == null) {
                LOGGER.info("[runicskills] No Legendary Tabs registrations on InventoryScreen — skipping strip sync.");
                return;
            }

            Field tabsField = inventoryScreenInfo.getClass().getField("tabs");

            // Snapshot the InventoryScreen tab list. We reuse this below for forward
            // propagation onto RunicSkillsScreen.
            @SuppressWarnings("unchecked")
            Map<Integer, List<TabBase>> inventoryTabs =
                    (Map<Integer, List<TabBase>>) tabsField.get(inventoryScreenInfo);

            // --- Forward: mirror the Inventory strip onto RunicSkillsScreen ------------
            // Skills panel is 176 wide and 194 tall (same width as vanilla inventory, so
            // horizontal positioning matches pixel-for-pixel).
            Function<Player, Integer> width  = player -> 176;
            Function<Player, Integer> height = player -> 194;

            int forward = 0;
            for (Map.Entry<Integer, List<TabBase>> entry : inventoryTabs.entrySet()) {
                int priority = entry.getKey();
                for (TabBase tab : entry.getValue()) {
                    if (tab.getClass().getName().equals(OUR_TAB_CLASS)) continue;
                    TabsMenu.addTabToScreen(tab, RunicSkillsScreen.class, width, height, priority);
                    forward++;
                }
            }

            // --- Reverse: put our tab on every screen that already hosts InventoryTab --
            LegendaryTabRunicSkills ourTab = null;
            for (List<TabBase> bucket : inventoryTabs.values()) {
                for (TabBase t : bucket) {
                    if (t instanceof LegendaryTabRunicSkills) { ourTab = (LegendaryTabRunicSkills) t; break; }
                }
                if (ourTab != null) break;
            }
            if (ourTab == null) {
                LOGGER.info("[runicskills] Skills tab not yet registered on InventoryScreen — skipping reverse sync.");
                return;
            }

            int priority = HandlerConfigClient.legendaryTabsPriority.get();
            int reverse = 0;
            // Iterate a snapshot of entries because addTabToScreen mutates the underlying map.
            List<Map.Entry<Class<? extends Screen>, Object>> snapshot = new ArrayList<>(tabsScreens.entrySet());
            for (Map.Entry<Class<? extends Screen>, Object> entry : snapshot) {
                Class<? extends Screen> screenClass = entry.getKey();
                if (screenClass == InventoryScreen.class) continue;   // our tab already there
                if (screenClass == RunicSkillsScreen.class) continue; // our own screen

                @SuppressWarnings("unchecked")
                Map<Integer, List<TabBase>> tabs =
                        (Map<Integer, List<TabBase>>) tabsField.get(entry.getValue());

                // Only extend screens Legendary Tabs treats as inventory-like. The simplest
                // reliable signal is: does the built-in InventoryTab appear here? If yes,
                // this is an inventory-companion screen (Medkit, Curios, Backpack, …).
                boolean inventoryLike = false;
                boolean alreadyPresent = false;
                for (List<TabBase> bucket : tabs.values()) {
                    for (TabBase t : bucket) {
                        String name = t.getClass().getName();
                        if (name.equals(INVENTORY_TAB_CLASS)) inventoryLike = true;
                        if (name.equals(OUR_TAB_CLASS))       alreadyPresent = true;
                    }
                }
                if (!inventoryLike || alreadyPresent) continue;

                // Pass no-op width/height lambdas: addTabToScreen only stores them on the
                // first registration for a screen class, and every screen we're touching
                // here was already seeded by its owner mod with the correct dimensions.
                TabsMenu.addTabToScreen(ourTab, screenClass, width, height, priority);
                reverse++;
            }

            LOGGER.info("[runicskills] Legendary Tabs sync — forward: {} tab(s) onto Skills screen; reverse: Skills tab onto {} inventory-like screen(s).",
                    forward, reverse);
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
            LOGGER.warn("[runicskills] Could not synchronize Legendary Tabs strip across screens (internals changed?) — {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
