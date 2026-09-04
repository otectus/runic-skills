package com.otectus.runicskills.client.event;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.gui.DrawTabs;
import com.otectus.runicskills.client.gui.InventoryTabLayout;
import com.otectus.runicskills.client.gui.InventoryTabLayout.Anchor;
import com.otectus.runicskills.client.gui.InventoryTabLayout.Rect;
import com.otectus.runicskills.client.gui.InventoryTabLayout.TabLayout;
import com.otectus.runicskills.client.gui.InventoryTabReservedRegions;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.integration.InventoryTabOwnership;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Everything about the inventory tab strip that is not "paint the bodies": the tooltip, the click,
 * the Shift-drag that moves it, and the close-time latch reset.
 *
 * <p><b>Why these are Forge screen events rather than mixin injections.</b> The bodies have to be
 * drawn from {@code renderBg} so items and their tooltips sit on top of them, but everything else
 * is better off outside the mixin. {@code ScreenEvent.Render.Post} fires after the screen has
 * drawn its own tooltips, which is exactly where our tooltip belongs and nowhere the mixin can
 * reach. The mouse events compose with other mods instead of racing them for the same method, and
 * {@code Closing} is the single funnel every close path reaches — see the note below on why the
 * close reset cannot live in {@code MixInventoryScreen} at all.
 *
 * <p>Every handler returns immediately when an external tab mod owns the strip or when the player
 * has turned it off, so none of this runs for players who are not using it.
 */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, value = Dist.CLIENT)
public final class InventoryTabsScreenHandler {

    /** In-progress Shift-drag. Null whenever the strip is not being moved. */
    private static Drag drag;

    private InventoryTabsScreenHandler() {
    }

    /**
     * @param startMouseX mouse position when the drag began, in GUI pixels
     * @param startOffsetX the configured offset at that moment; the drag is applied relative to it
     *                     so the strip does not jump to the cursor on the first pixel of movement
     */
    private record Drag(double startMouseX, double startMouseY, int startOffsetX, int startOffsetY) {
    }

    /**
     * L2Tabs, Legendary Tabs and CustomNPCs render the Skills tab natively via their own tab
     * strips (see {@code RunicSkillsClient#clientSetup}); ours stays out of the way entirely when
     * any of them is active — see {@link InventoryTabOwnership#externalTabsActive()} for how that
     * is decided.
     */
    public static boolean suppressed() {
        return InventoryTabOwnership.externalTabsActive()
                || !HandlerConfigClient.inventoryTabsEnabled.get();
    }

    /**
     * Computes where the strip goes on the vanilla inventory.
     *
     * <p>Called once per frame from the {@code renderBg} injection and again by the listeners
     * below; it is a handful of integer comparisons plus two rectangle lookups, so recomputing is
     * cheaper than the staleness bugs a cache would introduce when the recipe book is toggled or
     * an effect expires mid-frame.
     */
    public static TabLayout layoutFor(InventoryScreen screen) {
        Rect panel = new Rect(screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(), screen.getYSize());
        Rect bounds = new Rect(0, 0, screen.width, screen.height);
        List<Rect> reserved = InventoryTabReservedRegions.collect(screen);
        return InventoryTabLayout.compute(panel, bounds, DrawTabs.tabCount(),
                Anchor.parse(HandlerConfigClient.inventoryTabsAnchor.get()),
                HandlerConfigClient.inventoryTabsOffsetX.get(),
                HandlerConfigClient.inventoryTabsOffsetY.get(),
                reserved);
    }

    /** The layout the bodies were actually drawn with this frame, so input matches paint. */
    private static TabLayout layoutOf(InventoryScreen screen) {
        TabLayout drawn = DrawTabs.currentLayout(screen);
        return drawn != null ? drawn : layoutFor(screen);
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (suppressed() || !(event.getScreen() instanceof InventoryScreen screen)) return;

        TabLayout layout = layoutOf(screen);
        GuiGraphics graphics = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        // While dragging, the cursor is the handle -- a tooltip following it would obscure the
        // thing the player is trying to position.
        if (drag == null) {
            DrawTabs.renderTooltip(graphics, mouseX, mouseY, layout);
        }

        // The only affordance for a feature that is otherwise invisible: hold Shift over the strip
        // and it outlines itself to say "this can be moved".
        if (HandlerConfigClient.inventoryTabsDragToMove.get() && Screen.hasShiftDown()
                && (drag != null || layout.strip().contains(mouseX, mouseY))) {
            outline(graphics, layout.strip());
        }
    }

    private static void outline(GuiGraphics graphics, Rect strip) {
        int colour = 0xFFFFFFFF;
        graphics.fill(strip.x() - 1, strip.y() - 1, strip.right() + 1, strip.y(), colour);
        graphics.fill(strip.x() - 1, strip.bottom(), strip.right() + 1, strip.bottom() + 1, colour);
        graphics.fill(strip.x() - 1, strip.y(), strip.x(), strip.bottom(), colour);
        graphics.fill(strip.right(), strip.y(), strip.right() + 1, strip.bottom(), colour);
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (suppressed() || event.getButton() != 0) return;
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;

        TabLayout layout = layoutOf(screen);
        int mouseX = (int) event.getMouseX();
        int mouseY = (int) event.getMouseY();

        if (HandlerConfigClient.inventoryTabsDragToMove.get() && Screen.hasShiftDown()
                && layout.strip().contains(mouseX, mouseY)) {
            drag = new Drag(event.getMouseX(), event.getMouseY(),
                    HandlerConfigClient.inventoryTabsOffsetX.get(),
                    HandlerConfigClient.inventoryTabsOffsetY.get());
            // Cancelled so the Shift-click does not also reach the slot underneath and quick-move
            // an item while the player is rearranging their HUD.
            event.setCanceled(true);
            return;
        }

        // A plain click only arms the latch that the next render consumes. The event is left
        // uncancelled: the strip sits outside the panel, so the screen has nothing to do with the
        // click anyway, and cancelling would break any other mod listening after us.
        if (DrawTabs.tabIndexAt(layout, mouseX, mouseY) >= 0) {
            DrawTabs.mouseClicked(0);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (drag == null) return;
        if (suppressed() || !(event.getScreen() instanceof InventoryScreen)) {
            drag = null;
            return;
        }

        // Written straight into the config values so the strip follows the cursor live; the TOML
        // is only written on release, so a drag is one file write rather than one per frame.
        HandlerConfigClient.inventoryTabsOffsetX.set(clampOffset(
                drag.startOffsetX() + (int) Math.round(event.getMouseX() - drag.startMouseX())));
        HandlerConfigClient.inventoryTabsOffsetY.set(clampOffset(
                drag.startOffsetY() + (int) Math.round(event.getMouseY() - drag.startMouseY())));
        event.setCanceled(true);
    }

    /** The config range is -500..500; {@code set} outside it would be rejected on reload. */
    private static int clampOffset(int value) {
        return Math.max(-500, Math.min(500, value));
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (drag == null || event.getButton() != 0) return;
        drag = null;
        HandlerConfigClient.SPEC.save();
    }

    /**
     * Clears {@link DrawTabs}' click latch when the inventory closes, and abandons any drag.
     *
     * <p>{@code DrawTabs.checkMouse} is set by the click handler and consumed by the next
     * {@code DrawTabs.render}. If the click that armed it is also the click that closed the
     * screen, the latch survives into the next screen and fires a tab switch nobody asked for.
     *
     * <p><b>Why this is not in {@code MixInventoryScreen}.</b> That is where the reset used to
     * live, as a plain {@code public void onClose()} on the mixin. {@code InventoryScreen} does not
     * declare {@code onClose} — it inherits it — so the mixin method was an <i>implicit
     * overwrite</i> rather than an injection, and any other mod doing the same thing to the same
     * class wins. Highlighter does exactly that, and Mixin resolved the tie against us on every
     * start: {@code Method overwrite conflict for m_7379_ in
     * runicskills.mixins.json:MixInventoryScreen, previously written by
     * com.anthonyhilyard.highlighter.mixin.InventoryScreenMixin. Skipping method.} The reset simply
     * never ran. An {@code @Inject} was not an option either, for the same reason the overwrite
     * happened: there is no {@code onClose} in the target class to inject into.
     *
     * <p>Forge's {@link ScreenEvent.Closing} is fired from {@code Minecraft#setScreen} for the
     * outgoing screen, which is the single funnel every close path reaches — Escape, the inventory
     * key, and opening a different screen all end in {@code setScreen}, and {@code Screen#onClose}
     * itself routes through {@code popGuiLayer}, which delegates to {@code setScreen(null)} when no
     * Forge GUI layer is stacked. Listening there composes with every other mod instead of
     * competing with it.
     */
    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof InventoryScreen) {
            drag = null;
            DrawTabs.onClose();
        }
    }
}
