package sfiomn.legendarytabs.api.tabs_menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * COMPILE-ONLY STUB of Sfiomn's Legendary Tabs {@code TabBase} (1.20.1-2.0).
 *
 * <p>This is not upstream code. It is a hand-written signature-only mirror of the public API
 * surface Runic Skills compiles against, so a fresh clone builds with no third-party jar
 * present. Signatures were taken from {@code javap} on the 2.0 release and must stay
 * byte-compatible with it; the bodies are unreachable because this source set is
 * {@code compileOnly} and never ships in the mod jar. At runtime the real Legendary Tabs
 * classes are the ones loaded.
 *
 * <p>If Legendary Tabs changes this API, update this file and the {@code versionRange} for
 * {@code legendarytabs} in {@code mods.toml} together.
 */
public abstract class TabBase {

    public static final int TAB_HEIGHT = 0;
    public static final int TAB_WIDTH = 0;
    public static final int ICON_SIZE = 0;
    public static final int ICON_OFFSET_X = 0;
    public static final int ICON_OFFSET_Y = 0;
    public static final ResourceLocation DEFAULT_BUTTONS_TEXTURE = null;

    public TabBase() {
    }

    public abstract String getId();

    public abstract void openTargetScreen(Player player);

    public abstract boolean isEnabled(Player player);

    public abstract void initTabOnScreens();

    public void render(GuiGraphics graphics, int x, int y, boolean selected,
                       ResourceLocation buttonSkin, int iconOffsetX, int iconOffsetY) {
        throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
    }

    public abstract boolean isCurrentlyUsed(Screen currentScreen);

    public abstract Component getTooltip();

    public abstract ResourceLocation getIconTexture();

    public int getIconTexX() {
        throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
    }

    public int getIconTexY() {
        throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
    }
}
