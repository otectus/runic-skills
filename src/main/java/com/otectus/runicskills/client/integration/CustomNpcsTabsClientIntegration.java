package com.otectus.runicskills.client.integration;

import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.integration.CustomNpcsIntegration;
import com.otectus.runicskills.integration.L2TabsIntegration;
import com.otectus.runicskills.integration.LegendaryTabsIntegration;
import com.otectus.runicskills.registry.RegistryItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.client.gui.player.tabs.AbstractTab;
import noppes.npcs.client.gui.player.tabs.InventoryTabFactions;
import noppes.npcs.client.gui.player.tabs.InventoryTabQuests;
import noppes.npcs.client.gui.player.tabs.InventoryTabVanilla;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side companion to {@link CustomNpcsIntegration}: inserts a Skills tab of CustomNPCs' own
 * type into CustomNPCs' tab strip, so the player sees one strip
 * (Inventory, Skills, Factions, Quests) rather than two.
 *
 * <p>Lives under {@code client/} because it touches {@code net.minecraft.client.*} and
 * {@code noppes.npcs.client.*}, which the {@code :checkSidedImports} lint forbids in the shared
 * {@code integration/} package. It is named only as a string from {@link CustomNpcsIntegration},
 * and only after that class's reflective probe has passed — a direct reference from anywhere else
 * would put {@code AbstractTab} in an always-loaded constant pool and throw
 * {@link NoClassDefFoundError} for every player without CustomNPCs.
 *
 * <p><b>Why {@link EventPriority#LOWEST}.</b> CustomNPCs adds its three tabs from its own
 * default-priority {@code ScreenEvent.Init.Post} handler. Ours has to run after that one, or the
 * list it scans is still empty on the vanilla inventory and there is nothing to insert into.
 *
 * <p><b>Why widgets are scanned rather than screen types.</b> The strip appears on the vanilla
 * inventory (added by that handler) and on CustomNPCs' own Faction and Quest screens (added by
 * those screens' {@code init()} via {@code addRenderableWidget}, so already present when
 * {@code Init.Post} fires). Keying off the screen classes would mean stubbing and naming
 * {@code GuiFaction} and {@code GuiQuestLog} as well, and would silently miss any screen a future
 * CustomNPCs release puts the strip on. "Does this screen already have {@code AbstractTab}
 * widgets?" is the question we actually mean, and it answers itself for screens we have never
 * heard of. Re-running upstream's {@code init(screen)} after renumbering keeps CustomNPCs' own
 * per-screen anchors authoritative, so we never hard-code their offsets.
 *
 * <p><b>The {@code +8 / +1} geometry.</b> The Skills screen is ours, so CustomNPCs never puts a
 * strip on it and there is nothing to insert into — the full four-tab strip is built here instead.
 * Upstream's inventory anchor works out to {@code guiLeft + 8, guiTop + 1} against the 176-wide
 * vanilla panel; the Skills panel is also 176 wide, so reusing those two numbers lands the strip on
 * exactly the spot the player just clicked it from on the inventory. {@code y} is then
 * {@code panelTop + 1 - 28} because a tab hangs its 28px above the panel.
 */
public final class CustomNpcsTabsClientIntegration {

    /** How far apart CustomNPCs spaces its tabs, and the width of one. */
    private static final int TAB_PITCH = 28;

    /** Upstream's inventory-screen anchor, expressed relative to the panel. */
    private static final int STRIP_INSET_X = 8;
    private static final int STRIP_INSET_Y = 1;

    /**
     * The tab currently on screen, and the screen it was built for. Only used by the tooltip
     * fallback below; a stale pair is harmless because the identity check against the event's
     * screen fails.
     */
    private static RunicSkillsCustomNpcsTab activeTab;
    private static Screen activeScreen;

    private CustomNpcsTabsClientIntegration() {
    }

    /**
     * Subscribes the handlers below. Called reflectively from
     * {@link CustomNpcsIntegration#registerClientTabs()} once the probe has confirmed the real
     * {@code AbstractTab} still matches the compile-time stub.
     */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(CustomNpcsTabsClientIntegration.class);
    }

    /**
     * Runic Skills' tab, drawn by CustomNPCs' own code.
     *
     * <p>It overrides nothing but the two abstract methods on purpose: inheriting
     * {@code AbstractTab}'s render is the whole point, and is what makes this tab
     * pixel-identical to the ones beside it in every state (idle, hovered, selected).
     */
    private static final class RunicSkillsCustomNpcsTab extends AbstractTab {

        RunicSkillsCustomNpcsTab() {
            super(1, 0, 0, new ItemStack(RegistryItems.LEVELING_BOOK.get()));
            // Upstream draws a tab as selected when this matches the open screen's class.
            this.screenClass = RunicSkillsScreen.class;
            this.setMessage(Component.translatable("screen.skill.title"));
            // Kept even though CustomNPCs' render never consumes it (see onScreenRenderPost): it
            // costs nothing, and it starts working by itself if upstream ever moves its drawing
            // into renderWidget.
            this.setTooltip(Tooltip.create(Component.translatable("screen.skill.title")));
        }

        @Override
        public void onTabClicked() {
            Minecraft.getInstance().setScreen(new RunicSkillsScreen());
        }

        @Override
        public boolean shouldAddToList() {
            return true;
        }

        /**
         * Whether the cursor is over the tab, as CustomNPCs itself computed it last frame.
         *
         * <p>{@code isHoveredOrFocused()} is not usable here: it reads {@code AbstractWidget}'s
         * {@code isHovered}, which is only ever assigned by {@code AbstractWidget#render} — and
         * upstream overrides {@code render} outright, so that field stays {@code false} forever.
         * The {@code hover} field upstream sets in its place is the one that is actually current.
         */
        boolean isHoveredNow() {
            return this.visible && this.hover;
        }
    }

    /**
     * Inserts the Skills tab into whatever strip this screen has, or builds the whole strip when
     * the screen is ours.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (!CustomNpcsIntegration.isNativeTabsActive()) return;
        // L2Tabs and Legendary Tabs already render a Skills tab natively. Adding a second copy
        // here would recreate exactly the duplication this integration exists to remove.
        if (L2TabsIntegration.isNativeTabsActive() || LegendaryTabsIntegration.isModLoaded()) return;

        Screen screen = event.getScreen();
        List<AbstractTab> existing = new ArrayList<>();
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof RunicSkillsCustomNpcsTab ours) {
                // Idempotent: Init.Post fires again on every resize, and screens can be re-inited.
                activeTab = ours;
                activeScreen = screen;
                return;
            }
            if (listener instanceof AbstractTab tab) {
                existing.add(tab);
            }
        }

        if (!existing.isEmpty()) {
            insertIntoExistingStrip(event, screen, existing);
        } else if (screen instanceof RunicSkillsScreen skillsScreen) {
            buildFullStrip(event, skillsScreen);
        }
    }

    /**
     * Inventory, Faction and Quest screens: make room at index 1 and drop our tab in.
     *
     * <p>Everything from CustomNPCs' Factions tab onwards shifts right by one slot, then each tab
     * re-runs {@code init(screen)} so its x is recomputed from upstream's own anchor for this
     * screen rather than from anything we assume about it.
     */
    private static void insertIntoExistingStrip(ScreenEvent.Init.Post event, Screen screen,
                                                List<AbstractTab> existing) {
        for (AbstractTab tab : existing) {
            if (tab.id >= 1) tab.id++;
            tab.init(screen);
        }

        RunicSkillsCustomNpcsTab ours = new RunicSkillsCustomNpcsTab();
        ours.id = 1;
        ours.init(screen);
        event.addListener(ours);

        activeTab = ours;
        activeScreen = screen;
    }

    /**
     * The Skills screen: CustomNPCs puts no strip here, so build all four tabs and place them by
     * hand at the panel-relative offsets documented on this class. Ours draws itself selected,
     * because its {@code screenClass} is this screen.
     */
    private static void buildFullStrip(ScreenEvent.Init.Post event, RunicSkillsScreen screen) {
        RunicSkillsCustomNpcsTab ours = new RunicSkillsCustomNpcsTab();
        List<AbstractTab> strip = List.of(
                new InventoryTabVanilla(), ours, new InventoryTabFactions(), new InventoryTabQuests());

        int left = screen.panelLeft();
        int top = screen.panelTop();
        for (int id = 0; id < strip.size(); id++) {
            AbstractTab tab = strip.get(id);
            tab.id = id;
            // init() first so anything else it sets is applied, then override the position: its
            // anchors only know about CustomNPCs' own screens, and this is not one of them.
            tab.init(screen);
            tab.setX(left + STRIP_INSET_X + id * TAB_PITCH);
            tab.setY(top + STRIP_INSET_Y - TAB_PITCH);
            event.addListener(tab);
        }

        activeTab = ours;
        activeScreen = screen;
    }

    /**
     * Draws the "Skills" tooltip that {@link Tooltip} cannot.
     *
     * <p>{@code AbstractWidget}'s tooltip is dispatched from {@code AbstractWidget#render}, which
     * CustomNPCs overrides in full — verified in the bytecode: the method that tests {@code
     * visible} first is {@code render} (SRG {@code m_88315_}), not {@code renderWidget}
     * ({@code m_87963_}), so nothing on the tab ever reaches {@code updateTooltip}. That is why
     * their own Faction and Quest tabs hand-draw their labels too. This handler does the same at
     * {@code Render.Post}, which is after the screen has drawn its items and their tooltips, so
     * ours is not painted over.
     */
    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (activeTab == null || event.getScreen() != activeScreen) return;
        if (!activeTab.isHoveredNow()) return;

        event.getGuiGraphics().renderTooltip(
                Minecraft.getInstance().font,
                Component.translatable("screen.skill.title"),
                event.getMouseX(),
                event.getMouseY());
    }
}
