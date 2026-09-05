package com.otectus.runicskills.client.integration;

import com.mojang.logging.LogUtils;
import com.otectus.runicskills.client.gui.LegendaryTabRunicSkills;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.integration.LegendaryTabsIntegration;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import sfiomn.legendarytabs.api.tabs_menu.TabBase;
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;

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

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Skills panel geometry: same width as vanilla inventory, taller panel. */
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 194;

    /** The single registered tab instance, captured at {@link #registerTab()}. */
    private static LegendaryTabRunicSkills tabInstance;

    /** Latches the "internals changed" warning so a broken upstream cannot flood the log. */
    private static boolean warned = false;

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
        LegendaryTabRunicSkills tab = new LegendaryTabRunicSkills();
        tabInstance = tab;
        TabsMenu.register(tab);
    }

    /**
     * Entry point called reflectively by {@link LegendaryTabsIntegration#registerClientTab()}:
     * registers the tab and, only if that succeeded, subscribes the per-screen handler below.
     *
     * <p>Both steps live here rather than in {@code RunicSkillsClient} so a Legendary Tabs API
     * change is caught by the facade's try/catch and downgraded to the built-in strip, instead of
     * escaping {@code enqueueWork} and failing mod loading. Subscribing after registration also
     * means the handler never runs without a {@link #tabInstance} to place.
     */
    public static void register() {
        registerTab();
        MinecraftForge.EVENT_BUS.register(LegendaryTabsClientIntegration.class);
    }

    /**
     * Full sweep across every screen Legendary Tabs currently knows about. Called once at
     * {@code FMLLoadCompleteEvent} so the strip is already correct for anything opened
     * before a world is joined.
     *
     * <p>This is no longer sufficient on its own — see {@link #onScreenInitPre} — but it is
     * kept so the pre-world state matches the post-world state, and so the summary line
     * below still appears once per launch.
     */
    public static void synchronizeTabStripAcrossScreens() {
        if (!LegendaryTabsIntegration.isModLoaded()) return;

        try {
            int forward = mirrorInventoryStripOntoSkillsScreen();

            int reverse = 0;
            // Snapshot: addTabToScreen mutates the underlying map.
            for (Class<?> screenClass : new ArrayList<>(TabsMenu.getRegisteredScreens())) {
                if (ensureSkillsTabOn(screenClass)) reverse++;
            }

            LOGGER.info("[runicskills] Legendary Tabs sync — forward: {} tab(s) onto Skills screen; reverse: Skills tab onto {} screen(s).",
                    forward, reverse);
        } catch (RuntimeException | LinkageError e) {
            warnOnce(e);
        }
    }

    /**
     * Keeps the strip correct as screens are opened.
     *
     * <p>The load-complete sweep alone used to be the whole mechanism, justified by "every
     * mod's client setup has finished, so {@code TabsMenu.tabsScreens} is fully populated".
     * That held for Legendary Tabs 1.x and is false in 2.0: data-driven tabs are loaded from
     * a datapack and pushed to the client by {@code SyncTabsPacket}, so
     * {@code TabRegistry.reloadTabs()} — which seeds new screen classes and calls
     * {@code TabsMenu.fanOutInventoryTab()} — first runs on <em>world join</em>, long after
     * {@code FMLLoadCompleteEvent}, and again on every {@code /reload}. Screens that only
     * enter the registry at that point (Sophisticated Backpacks' {@code BackpackScreen}
     * among them) were never visited, so the Skills tab silently never appeared on them.
     *
     * <p>{@code Init.Pre} rather than {@code Init.Post}: Legendary Tabs builds its
     * {@code TabButton} widgets from an {@code Init.Post} listener, so a tab added here is
     * picked up on the same frame the screen opens.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenInitPre(ScreenEvent.Init.Pre event) {
        try {
            Class<?> screenClass = TabsMenu.resolveScreenIdentity(event.getScreen());
            if (screenClass == RunicSkillsScreen.class) {
                mirrorInventoryStripOntoSkillsScreen();
            } else {
                ensureSkillsTabOn(screenClass);
            }
        } catch (RuntimeException | LinkageError e) {
            warnOnce(e);
        }
    }

    /**
     * Forward pass — copy every tab registered on {@link InventoryScreen} onto
     * {@link RunicSkillsScreen} at the same priority, so the Skills page shows the same
     * strip the player sees on the vanilla inventory. Idempotent: {@code ScreenInfo.addTab}
     * dedupes by instance across all priority buckets.
     *
     * @return how many tabs were offered (not necessarily how many were newly added)
     */
    private static int mirrorInventoryStripOntoSkillsScreen() {
        TabsMenu.ScreenInfo inventoryScreenInfo = TabsMenu.getScreenInfo(InventoryScreen.class);
        if (inventoryScreenInfo == null || inventoryScreenInfo.tabs == null) return 0;

        Function<Player, Integer> width = player -> PANEL_WIDTH;
        Function<Player, Integer> height = player -> PANEL_HEIGHT;

        int forward = 0;
        for (Map.Entry<Integer, List<TabBase>> entry : inventoryScreenInfo.tabs.entrySet()) {
            int priority = entry.getKey();
            // Snapshot the bucket: addTabToScreen mutates these lists.
            for (TabBase tab : new ArrayList<>(entry.getValue())) {
                if (tab == tabInstance) continue;
                TabsMenu.addTabToScreen(tab, RunicSkillsScreen.class, width, height, priority);
                forward++;
            }
        }
        return forward;
    }

    /**
     * Reverse pass for one screen — put the Skills tab on it if Legendary Tabs already draws
     * a strip there.
     *
     * <p>The gate is {@code getScreenInfo(...) != null}, i.e. Legendary Tabs has already
     * registered this screen. That is deliberate, and load-bearing in two directions.
     * {@code addTabToScreen} routes through {@code ensureScreenInfo}, a
     * {@code computeIfAbsent} — calling it for an unregistered screen would <em>create</em>
     * the entry and grow a tab strip on a screen that is not meant to have one (every chest,
     * say). And the previous gate — "does this screen already contain Legendary Tabs'
     * built-in {@code InventoryTab}?" — is no longer safe to rely on, because addons
     * legitimately drop that tab from screens where they supply their own equivalent
     * (Sophisticated Tab does exactly this on {@code BackpackScreen}); keying off it would
     * make the Skills tab vanish from any screen another mod curates.
     *
     * @return true if the tab was newly added to this screen
     */
    private static boolean ensureSkillsTabOn(Class<?> screenClass) {
        if (tabInstance == null) return false;
        if (screenClass == InventoryScreen.class) return false;   // registered by the tab itself
        if (screenClass == RunicSkillsScreen.class) return false; // our own screen

        TabsMenu.ScreenInfo info = TabsMenu.getScreenInfo(screenClass);
        if (info == null || info.tabs == null) return false;

        for (List<TabBase> bucket : info.tabs.values()) {
            if (bucket.contains(tabInstance)) return false;
        }

        TabsMenu.addTabToScreen(
                tabInstance,
                screenClass,
                player -> PANEL_WIDTH,
                player -> PANEL_HEIGHT,
                HandlerConfigClient.legendaryTabsPriority.get());
        return true;
    }

    /**
     * Degrade rather than crash: this is a cosmetic convenience reaching into another mod's
     * state, and a hard failure here would take the client down the way the 1.x/2.0
     * {@code TabBase} mismatch did. Latched, because this now runs per screen init.
     */
    private static void warnOnce(Throwable e) {
        if (warned) return;
        warned = true;
        LOGGER.warn("[runicskills] Could not synchronize Legendary Tabs strip across screens (internals changed?) — {}: {}",
                e.getClass().getSimpleName(), e.getMessage());
    }
}
