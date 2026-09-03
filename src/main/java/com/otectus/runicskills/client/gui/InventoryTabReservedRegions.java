package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.api.client.InventoryTabLayoutEvent;
import com.otectus.runicskills.client.gui.InventoryTabLayout.Rect;
import com.otectus.runicskills.handler.HandlerConfigClient;
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
 * correct for an entire play session and then start overlapping. Everything else comes from
 * outside, either through {@link #register} (for code that can see Runic Skills) or through
 * {@link InventoryTabLayoutEvent} (for code that cannot, or does not want a hard dependency).
 *
 * <p>The vanilla rectangles are recomputed from the live screen on every call rather than cached:
 * the effect panel changes size with the effect count and flips between its wide and compact
 * layouts with the window width, so a cached rect is a wrong rect one resize later.
 */
public final class InventoryTabReservedRegions {

    /** Insertion-ordered so built-ins are evaluated before anything a mod adds later. */
    private static final Map<Class<? extends Screen>, List<Function<Screen, List<Rect>>>> PROVIDERS =
            new LinkedHashMap<>();

    static {
        register(InventoryScreen.class, InventoryTabReservedRegions::recipeBookRegion);
        register(InventoryScreen.class, InventoryTabReservedRegions::effectPanelRegion);
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
}
