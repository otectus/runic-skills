package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.api.client.InventoryTabLayoutEvent;
import com.otectus.runicskills.client.gui.InventoryTabLayout.Rect;
import com.otectus.runicskills.handler.HandlerConfigClient;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The parts of a screen the tab strip must not sit on top of.
 *
 * <p>Two of them are vanilla's own and are known here: the recipe book, and the potion effect
 * panel that only appears once the player has an effect — which is why the strip could look
 * correct for an entire play session and then start overlapping. A third is generic: any widget
 * another mod has added to the inventory screen outside the panel, which covers the tab mods that
 * build their strips out of ordinary buttons (CustomNPCs is the known example) without naming any
 * of them. Anything that is not a widget — a mod that paints its tabs straight into a render event
 * — still has {@link #register} (for code that can see Runic Skills) and
 * {@link InventoryTabLayoutEvent} (for code that cannot, or does not want a hard dependency).
 *
 * <p>Every rectangle is recomputed from the live screen on every call rather than cached: the
 * effect panel changes size with the effect count and flips between its wide and compact layouts
 * with the window width, and the widget list is not final until every mod's {@code Init.Post}
 * listener has run, so a cached rect is a wrong rect one resize — or one listener — later.
 */
public final class InventoryTabReservedRegions {

    /** Insertion-ordered so built-ins are evaluated before anything a mod adds later. */
    private static final Map<Class<? extends Screen>, List<Function<Screen, List<Rect>>>> PROVIDERS =
            new LinkedHashMap<>();

    static {
        register(InventoryScreen.class, InventoryTabReservedRegions::recipeBookRegion);
        register(InventoryScreen.class, InventoryTabReservedRegions::effectPanelRegion);
        register(InventoryScreen.class, InventoryTabReservedRegions::foreignWidgetRegions);
    }

    private InventoryTabReservedRegions() {
    }

    /**
     * Registers a source of reserved rectangles for one screen type. The provider is called for
     * any screen that is an {@code instanceof} the given class, once per layout pass, and may
     * return an empty list when its widget is currently hidden.
     */
    public static void register(Class<? extends Screen> screenClass, Function<Screen, List<Rect>> provider) {
        PROVIDERS.computeIfAbsent(screenClass, k -> new ArrayList<>()).add(provider);
    }

    /** Runs every matching provider, then gives other mods a turn via the Forge event. */
    public static List<Rect> collect(Screen screen) {
        List<Rect> reserved = new ArrayList<>();
        if (screen == null) return reserved;

        for (Map.Entry<Class<? extends Screen>, List<Function<Screen, List<Rect>>>> entry : PROVIDERS.entrySet()) {
            if (!entry.getKey().isInstance(screen)) continue;
            for (Function<Screen, List<Rect>> provider : entry.getValue()) {
                List<Rect> rects = provider.apply(screen);
                if (rects != null) reserved.addAll(rects);
            }
        }

        MinecraftForge.EVENT_BUS.post(new InventoryTabLayoutEvent(screen, reserved));
        return reserved;
    }

    /**
     * The open recipe book.
     *
     * <p>Below 379 px of GUI-scaled width vanilla stops shifting the inventory panel and draws the
     * book in "too narrow" mode over the whole screen; there is nowhere to route around at that
     * point, so nothing is reserved and the clamp is left to do what it can.
     */
    private static List<Rect> recipeBookRegion(Screen screen) {
        if (!HandlerConfigClient.inventoryTabsAvoidRecipeBook.get()) return List.of();
        if (!(screen instanceof InventoryScreen inventory)) return List.of();

        RecipeBookComponent book = inventory.getRecipeBookComponent();
        if (book == null || !book.isVisible() || inventory.width < 379) return List.of();

        return List.of(new Rect((inventory.width - 147) / 2 - 86, (inventory.height - 166) / 2, 147, 166));
    }

    /**
     * The vanilla potion effect panel, mirroring {@code EffectRenderingInventoryScreen.renderEffects}:
     * it starts two pixels right of the panel, is skipped entirely below 32 px of remaining width,
     * is 120 px wide when at least that much is left and 32 otherwise, and packs its rows tighter
     * than the 33 px pitch once more than five effects are active.
     */
    private static List<Rect> effectPanelRegion(Screen screen) {
        if (!HandlerConfigClient.inventoryTabsAvoidEffects.get()) return List.of();
        if (!(screen instanceof EffectRenderingInventoryScreen<?> effectScreen)) return List.of();
        if (!effectScreen.canSeeEffects()) return List.of();

        AbstractContainerScreen<?> container = effectScreen;
        Collection<MobEffectInstance> effects = container.getMinecraft().player == null
                ? List.of()
                : container.getMinecraft().player.getActiveEffects();
        int count = effects.size();
        if (count == 0) return List.of();

        int x = container.getGuiLeft() + container.getXSize() + 2;
        int available = container.width - x;
        if (available < 32) return List.of();

        int w = available >= 120 ? 120 : 32;
        int pitch = count > 5 ? 132 / (count - 1) : 33;
        int h = (count - 1) * pitch + 32;
        return List.of(new Rect(x, container.getGuiTop(), w, h));
    }

    /**
     * Every visible widget on the screen that is not fully inside the inventory panel.
     *
     * <p>Mods that add inventory tabs most often do it the plain way: a vanilla {@code AbstractWidget}
     * per tab, added on {@code ScreenEvent.Init.Post}, sitting in the band above the panel — exactly
     * where our own strip wants to be. Reserving them by shape rather than by name means the search
     * routes around all of them without this mod knowing any of them exist, and without depending on
     * whose {@code Init.Post} listener runs first: the layout is recomputed every frame, so widgets
     * added after ours were is not a case that can arise.
     *
     * <p>The containment test is what keeps vanilla out of it. Vanilla's own inventory widgets — the
     * recipe book toggle — are drawn on the panel, so a widget fully inside the panel rectangle is
     * assumed to belong there and is ignored; the strip never overlaps the panel anyway. Anything
     * poking outside it is somebody's addition and is reserved whole.
     */
    private static List<Rect> foreignWidgetRegions(Screen screen) {
        if (!(screen instanceof InventoryScreen inventory)) return List.of();

        Rect panel = new Rect(inventory.getGuiLeft(), inventory.getGuiTop(),
                inventory.getXSize(), inventory.getYSize());
        List<Rect> rects = new ArrayList<>();

        for (GuiEventListener child : inventory.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
            if (widget.getWidth() <= 0 || widget.getHeight() <= 0) continue;

            Rect rect = new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
            if (contains(panel, rect)) continue;
            rects.add(rect);
        }
        return rects;
    }

    /** True when {@code inner} lies wholly within {@code outer}, edges included. */
    private static boolean contains(Rect outer, Rect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }
}
