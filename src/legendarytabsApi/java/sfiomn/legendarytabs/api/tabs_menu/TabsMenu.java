package sfiomn.legendarytabs.api.tabs_menu;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * COMPILE-ONLY STUB of Sfiomn's Legendary Tabs {@code TabsMenu} (1.20.1-2.0).
 * See {@link TabBase} for why this exists and what the rules are.
 *
 * <p>Only the members Runic Skills actually calls are mirrored here. Adding a call to a
 * Legendary Tabs method that is absent from this stub is a compile error by design: it forces
 * the new dependency to be declared here and re-checked against the real jar.
 */
public class TabsMenu {

    private TabsMenu() {
    }

    public static void register(TabBase tab) {
        throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
    }

    public static void addTabToScreen(TabBase tab,
                                      Class<?> screenClass,
                                      Function<Player, Integer> width,
                                      Function<Player, Integer> height,
                                      int priority) {
        throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
    }

    public static Class<?> resolveScreenIdentity(Screen screen) {
        throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
    }

    public static Set<Class<?>> getRegisteredScreens() {
        throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
    }

    public static ScreenInfo getScreenInfo(Class<?> screenClass) {
        throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
    }

    public static class ScreenInfo {
        public Function<Player, Integer> width;
        public Function<Player, Integer> height;
        public Map<Integer, List<TabBase>> tabs;
        public ResourceLocation buttonSkin;
        public int iconOffsetX;
        public int iconOffsetY;

        public ScreenInfo(Function<Player, Integer> width, Function<Player, Integer> height) {
            throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
        }

        public ScreenInfo(Function<Player, Integer> width, Function<Player, Integer> height,
                          TabBase tab, int priority) {
            throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
        }

        public void addTab(int priority, TabBase tab) {
            throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
        }
    }
}
