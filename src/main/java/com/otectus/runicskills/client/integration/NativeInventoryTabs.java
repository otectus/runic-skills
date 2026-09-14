package com.otectus.runicskills.client.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.gui.InventoryTabLayout.Rect;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.common.util.LogOnce;
import com.otectus.runicskills.integration.InventoryTabOwnership;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shares destinations between optional tab providers without linking their classes together.
 * Only one provider paints a screen: Legendary Tabs, then L2 Tabs, then CustomNPCs.
 * Providers keep their own navigation, filtering, paging, skins and button hit testing.
 */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, value = Dist.CLIENT)
public final class NativeInventoryTabs {
    public record Destination(String id, Supplier<ItemStack> icon, Component title,
                              Runnable open, Predicate<Screen> selected,
                              BooleanSupplier enabled, Set<Class<?>> screens) {
        public Destination {
            screens = Set.copyOf(screens);
        }
    }

    private record Strip(Predicate<Screen> present, Function<Screen, List<AbstractWidget>> controls) {}
    private record Visibility(boolean visible, boolean active) {}
    private static final Map<String, Destination> DESTINATIONS = new LinkedHashMap<>();
    private static final Map<Class<?>, Function<Screen, Rect>> PANELS = new LinkedHashMap<>();
    private static final Map<String, Strip> STRIPS = new LinkedHashMap<>();
    private static final Map<AbstractWidget, Visibility> HIDDEN = new IdentityHashMap<>();
    private static Screen hiddenScreen;

    private NativeInventoryTabs() {}

    public static void register(Destination destination) {
        DESTINATIONS.put(destination.id(), destination);
    }

    public static List<Destination> destinations() {
        return List.copyOf(DESTINATIONS.values());
    }

    public static void unregisterPrefix(String prefix) {
        DESTINATIONS.keySet().removeIf(id -> id.startsWith(prefix));
    }

    public static void registerPanel(Class<?> type, Function<Screen, Rect> panel) {
        PANELS.put(type, panel);
    }

    public static void registerStrip(String id, Predicate<Screen> present,
                                     Function<Screen, List<AbstractWidget>> controls) {
        STRIPS.put(id, new Strip(present, controls));
        InventoryTabOwnership.setVisibilityProbe(() -> {
            Screen screen = Minecraft.getInstance().screen;
            return screen != null && (hasStrip("legendarytabs", screen)
                    || hasStrip("l2tabs", screen) || hasStrip("customnpcs", screen));
        });
    }

    public static boolean hasStrip(String id, Screen screen) {
        Strip strip = STRIPS.get(id);
        try {
            return strip != null && strip.present().test(screen);
        } catch (RuntimeException | LinkageError e) {
            warn(id, e);
            return false;
        }
    }

    /** Actual panel bounds, including vanilla recipe-book displacement. Null means unknown. */
    public static Rect panel(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container) {
            return new Rect(container.getGuiLeft(), container.getGuiTop(),
                    container.getXSize(), container.getYSize());
        }
        if (screen instanceof RunicSkillsScreen skills) {
            return new Rect(skills.panelLeft(), skills.panelTop(), 176, 194);
        }
        for (var entry : PANELS.entrySet()) {
            if (entry.getKey().isInstance(screen)) {
                try {
                    return entry.getValue().apply(screen);
                } catch (RuntimeException | LinkageError e) {
                    warn(entry.getKey().getName(), e);
                    return null;
                }
            }
        }
        return null;
    }

    public static boolean onScreen(AbstractWidget widget, Screen screen) {
        return widget.visible && widget.getX() < screen.width && widget.getY() < screen.height
                && widget.getX() + widget.getWidth() > 0 && widget.getY() + widget.getHeight() > 0;
    }

    /** Restore only what we hid, before the providers refresh their own visibility. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        restore();
        hiddenScreen = event.getScreen();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void reconcile(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        if (hasStrip("legendarytabs", screen)) {
            hide("l2tabs", screen);
            hide("customnpcs", screen);
        } else if (hasStrip("l2tabs", screen)) {
            hide("legendarytabs", screen);
            hide("customnpcs", screen);
        } else if (hasStrip("customnpcs", screen)) {
            hide("legendarytabs", screen);
            hide("l2tabs", screen);
        }
    }

    private static void hide(String id, Screen screen) {
        Strip strip = STRIPS.get(id);
        if (strip == null) return;
        try {
            for (AbstractWidget widget : new ArrayList<>(strip.controls().apply(screen))) {
                HIDDEN.putIfAbsent(widget, new Visibility(widget.visible, widget.active));
                widget.visible = false;
                widget.active = false;
                if (screen.getFocused() == widget) screen.setFocused(null);
            }
        } catch (RuntimeException | LinkageError e) {
            warn(id, e);
        }
    }

    private static void warn(String id, Throwable e) {
        LogOnce.warnOnce("native-tabs:" + id,
                "Runic Skills could not coordinate {} inventory tabs: {}", id, e.toString());
    }

    private static void restore() {
        HIDDEN.forEach((widget, state) -> {
            widget.visible = state.visible();
            widget.active = state.active();
        });
        HIDDEN.clear();
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (event.getScreen() == hiddenScreen) {
            restore();
            hiddenScreen = null;
        }
    }
}
