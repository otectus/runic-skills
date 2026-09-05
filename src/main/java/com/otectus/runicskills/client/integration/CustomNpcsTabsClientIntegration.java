package com.otectus.runicskills.client.integration;

import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.integration.CustomNpcsIntegration;
import com.otectus.runicskills.integration.L2TabsIntegration;
import com.otectus.runicskills.integration.LegendaryTabsIntegration;
import com.otectus.runicskills.common.util.LogOnce;
import com.otectus.runicskills.registry.RegistryItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import noppes.npcs.client.gui.player.tabs.AbstractTab;
import noppes.npcs.client.gui.player.tabs.InventoryTabFactions;
import noppes.npcs.client.gui.player.tabs.InventoryTabQuests;
import noppes.npcs.client.gui.player.tabs.InventoryTabVanilla;

import java.lang.reflect.Method;
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
 * <p><b>Why insertion happens at first render, not at {@code Init.Post}.</b> CustomNPCs adds its
 * three tabs from its own {@code ScreenEvent.Init.Post} handler, and that handler is itself
 * annotated {@code @SubscribeEvent(priority = EventPriority.LOWEST)} — confirmed with {@code javap}.
 * Two handlers at the same priority are dispatched in registration order, which nothing about our
 * mod can influence; in the reporting instance ours ran first, found no {@link AbstractTab} on the
 * inventory screen and returned, leaving the player with CustomNPCs' three tabs and no Skills tab.
 * {@code Init.Post} ordering therefore cannot be made reliable at any priority. Instead
 * {@link #ensure(Screen)} runs from {@code ScreenEvent.Render.Pre}, by which point every mod's
 * {@code init} work is done. It is idempotent and cheap — one pass over {@code screen.children()} —
 * and because a window resize re-runs {@code init()} and clears the widget list, the per-frame
 * check re-inserts the tab by itself. The {@code Init.Post} handler is kept only as a fast path
 * for the frame-zero case; it calls the same {@code ensure} and nothing depends on it running.
 *
 * <p>Adding at render time means {@code ScreenEvent.Init.Post#addListener} is no longer available,
 * so the widget goes in through the screen's own {@code addRenderableWidget}, reached by
 * {@link #addRenderableWidget(Screen, AbstractWidget)}.
 *
 * <p><b>Why widgets are scanned rather than screen types.</b> The strip appears on the vanilla
 * inventory (added by that handler) and on CustomNPCs' own Faction and Quest screens (added by
 * those screens' {@code init()} via {@code addRenderableWidget}, so already present when
 * {@code init()} runs). Keying off the screen classes would mean stubbing and naming
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

    /**
     * Set the first time a CustomNPCs strip is actually found on one of CustomNPCs' own screens.
     *
     * <p>{@link #buildFullStrip} draws the whole four-tab strip on the Skills screen, which only
     * makes sense as a continuation of a strip the player already has elsewhere. If CustomNPCs is
     * installed but never puts a strip on the inventory — a configuration or a build that does not
     * add one — then building one here would give the player a CustomNPCs strip on the Skills
     * screen and Runic Skills' own strip on the inventory, two different arrangements for the same
     * pair of screens. Waiting for evidence keeps both screens on whichever arrangement is
     * actually working.
     */
    private static boolean upstreamStripSeen;

    /**
     * {@code Screen.addRenderableWidget}, resolved on first use and then cached. {@code null} until
     * the first call, and left {@code null} if resolution ever fails.
     */
    private static Method addRenderableWidget;

    /** Set once resolution or invocation has failed, so we stop retrying every frame. */
    private static boolean addRenderableWidgetBroken;

    private CustomNpcsTabsClientIntegration() {
    }

    /**
     * Adds {@code widget} to {@code screen} through the screen's own {@code protected}
     * {@code addRenderableWidget}, returning whether it went in.
     *
     * <p><b>Why not a mixin accessor.</b> This used to be an {@code @Invoker} on {@code Screen}.
     * The target is generic — {@code <T extends GuiEventListener & Renderable & NarratableEntry>
     * T addRenderableWidget(T)} — and the Mixin annotation processor emitted no refmap entry for
     * it. In the development environment that is invisible, because the method is already named
     * {@code addRenderableWidget}; in production, where it is {@code m_142416_}, the invoker had
     * nothing to bind to and mixin application failed outright with
     * {@code InvalidAccessorException}, taking the whole game down for every user with CustomNPCs
     * installed.
     *
     * <p><b>Why SRG-name reflection is reliable.</b>
     * {@link ObfuscationReflectionHelper#findMethod} takes the SRG name and remaps it for whichever
     * naming the runtime is actually using, so one call covers both the mapped dev environment and
     * the obfuscated production one — no generated refmap involved. The parameter type is
     * {@link GuiEventListener} because that is the erasure of the generic method's single bound.
     *
     * <p>Failure is not fatal: the tab is simply not shown, and the reason is logged once.
     */
    private static boolean addRenderableWidget(Screen screen, AbstractWidget widget) {
        if (addRenderableWidgetBroken) return false;
        try {
            if (addRenderableWidget == null) {
                addRenderableWidget = ObfuscationReflectionHelper.findMethod(
                        Screen.class, "m_142416_", GuiEventListener.class);
            }
            addRenderableWidget.invoke(screen, widget);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            addRenderableWidgetBroken = true;
            LogOnce.warnOnce("customnpcs-tabs:add-widget",
                    "Runic Skills could not add its CustomNPCs tab to {}: {}. The tab will not be shown.",
                    screen.getClass().getName(), e.toString());
            return false;
        }
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
     * Fast path: usually the tab is in place before the screen's first frame is drawn. Harmless
     * when CustomNPCs has not run yet — {@link #ensure(Screen)} simply finds no strip, and the
     * render handler below picks it up on the next frame.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        ensure(event.getScreen());
    }

    /**
     * The authoritative insertion point. Runs every frame; all but the first return after a single
     * pass over the screen's listener list.
     */
    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        ensure(event.getScreen());
    }

    /**
     * Inserts the Skills tab into whatever strip this screen has, or builds the whole strip when
     * the screen is ours. Idempotent: if our tab is already on the screen, this only refreshes the
     * {@code activeTab}/{@code activeScreen} pair the tooltip handler reads.
     */
    private static void ensure(Screen screen) {
        if (!CustomNpcsIntegration.isNativeTabsPreferred()) {
            CustomNpcsIntegration.setNativeTabPresent(false);
            return;
        }
        // L2Tabs and Legendary Tabs already render a Skills tab natively. Adding a second copy
        // here would recreate exactly the duplication this integration exists to remove. Their
        // own suppression covers the strip, so this only has to stay out of the way.
        if (L2TabsIntegration.isNativeTabsActive() || LegendaryTabsIntegration.isNativeTabsActive()) {
            CustomNpcsIntegration.setNativeTabPresent(false);
            return;
        }

        List<AbstractTab> existing = new ArrayList<>();
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof RunicSkillsCustomNpcsTab ours) {
                activeTab = ours;
                activeScreen = screen;
                CustomNpcsIntegration.setNativeTabPresent(true);
                return;
            }
            if (listener instanceof AbstractTab tab) {
                existing.add(tab);
            }
        }

        boolean present;
        if (!existing.isEmpty()) {
            upstreamStripSeen = true;
            present = insertIntoExistingStrip(screen, existing);
        } else if (upstreamStripSeen && screen instanceof RunicSkillsScreen skillsScreen) {
            present = buildFullStrip(skillsScreen);
        } else {
            // No strip to join and no evidence there is one anywhere: this screen belongs to
            // Runic Skills' own tab strip, which draws itself as soon as this flag says so.
            present = false;
        }
        CustomNpcsIntegration.setNativeTabPresent(present);
    }

    /**
     * Inventory, Faction and Quest screens: make room at index 1 and drop our tab in.
     *
     * <p>Everything from CustomNPCs' Factions tab onwards shifts right by one slot, then each tab
     * re-runs {@code init(screen)} so its x is recomputed from upstream's own anchor for this
     * screen rather than from anything we assume about it.
     */
    private static boolean insertIntoExistingStrip(Screen screen, List<AbstractTab> existing) {
        RunicSkillsCustomNpcsTab ours = new RunicSkillsCustomNpcsTab();
        ours.id = 1;
        ours.init(screen);

        // Our tab goes in before CustomNPCs' are renumbered, so a failure leaves their strip
        // exactly as it was. The old order renumbered first and returned on failure, and because
        // this runs every frame the ids kept climbing: CustomNPCs' own tabs walked one slot
        // further right per frame until they left the screen, which is what "my tabs disappeared"
        // looked like from the player's side.
        if (!addRenderableWidget(screen, ours)) return false;

        for (AbstractTab tab : existing) {
            if (tab.id >= 1) tab.id++;
            tab.init(screen);
        }

        activeTab = ours;
        activeScreen = screen;
        return true;
    }

    /**
     * The Skills screen: CustomNPCs puts no strip here, so build all four tabs and place them by
     * hand at the panel-relative offsets documented on this class. Ours draws itself selected,
     * because its {@code screenClass} is this screen.
     */
    private static boolean buildFullStrip(RunicSkillsScreen screen) {
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
            if (!addRenderableWidget(screen, tab)) return false;
        }

        activeTab = ours;
        activeScreen = screen;
        return true;
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
