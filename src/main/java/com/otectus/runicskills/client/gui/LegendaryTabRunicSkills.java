package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.handler.HandlerConfigClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import sfiomn.legendarytabs.api.tabs_menu.TabBase;
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;

/**
 * Native Legendary Tabs tab for Runic Skills. When Legendary Tabs is installed, this tab
 * is registered via {@link TabsMenu#register(TabBase)} so it appears inside Legendary Tabs'
 * own tab strip — drawn, positioned, highlighted, paginated, and "current" tracked by
 * Legendary Tabs itself, exactly like the built-in tabs for FTB Quests, Backpacked, etc.
 * <p>
 * To stay pixel-identical to neighbouring tabs in the strip, this tab reuses the same
 * {@code legendarytabs:textures/gui/tab_menu_buttons.png} atlas the built-in tabs blit
 * from. The sword tile lives at {@code (u=27, v=92)} — a plain silver sword on the
 * standard 26×22 frame; no built-in tab class claims this slot, so reusing it avoids
 * any visual collision with an existing integration. Using the shared atlas means our
 * frame shape, shading, and hover-state transition are byte-for-byte identical to every
 * other tab — there is no custom texture to maintain in the Runic Skills resources.
 */
public class LegendaryTabRunicSkills extends TabBase {

    // Vanilla InventoryScreen panel is 176×166 (the standard). Runic Skills' own screen uses
    // the same width but a taller 194-pixel panel (PANEL_HEIGHT in RunicSkillsScreen).
    // Passing the wrong height to TabsMenu#addTabToScreen makes Legendary Tabs compute the
    // wrong topScreenPos and draw its strip *inside* the panel instead of above it.
    private static final int VANILLA_GUI_WIDTH = 176;
    private static final int VANILLA_GUI_HEIGHT = 166;
    private static final int RUNIC_SKILLS_GUI_HEIGHT = 194;

    // Legendary Tabs' shared atlas (256×256). Layout convention across all its built-in tab
    // classes: the normal variant sits at (TAB_ICON_TEX_X, TAB_ICON_TEX_Y); the hover variant
    // is at (TAB_ICON_TEX_X + 54, TAB_ICON_TEX_Y). The TabButton passes hover=true both on
    // mouse-over *and* when the tab is the currently-used one (via TabButton.isDisabled), so
    // the same +54 U shift serves as both hover and "selected" appearance — exactly as every
    // other built-in tab handles it.
    private static final ResourceLocation TAB_ICONS =
            new ResourceLocation("legendarytabs", "textures/gui/tab_menu_buttons.png");
    private static final int TAB_W = 26;
    private static final int TAB_H = 22;
    private static final int TAB_ICON_TEX_X = 27;   // Plain silver sword — unused by any built-in tab.
    private static final int TAB_ICON_TEX_Y = 92;
    private static final int HOVER_U_SHIFT  = 54;

    @Override
    public void openTargetScreen(Player player) {
        Utils.playSound();
        Minecraft.getInstance().setScreen(new RunicSkillsScreen());
    }

    @Override
    public boolean isEnabled(Player player) {
        return true;
    }

    @Override
    public void render(GuiGraphics gfx, int x, int y, boolean hover) {
        int u = TAB_ICON_TEX_X + (hover ? HOVER_U_SHIFT : 0);
        gfx.blit(TAB_ICONS, x, y, u, TAB_ICON_TEX_Y, TAB_W, TAB_H);
    }

    @Override
    public boolean isCurrentlyUsed(Screen currentScreen) {
        return currentScreen instanceof RunicSkillsScreen;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("screen.skill.title");
    }

    @Override
    public void initTabOnScreens() {
        // Appear on the vanilla inventory screen…
        TabsMenu.addTabToScreen(
                this,
                InventoryScreen.class,
                player -> VANILLA_GUI_WIDTH,
                player -> VANILLA_GUI_HEIGHT,
                HandlerConfigClient.legendaryTabsPriority.get()
        );
        // …and on our own Skills screen, so Legendary Tabs' strip remains visible while
        // Skills is open and the user can jump straight back to any other tab. The Skills
        // panel is taller than vanilla inventory (194 vs 166), so use the correct height
        // here — otherwise the strip lands inside the panel and is hidden by the background.
        TabsMenu.addTabToScreen(
                this,
                RunicSkillsScreen.class,
                player -> VANILLA_GUI_WIDTH,
                player -> RUNIC_SKILLS_GUI_HEIGHT,
                HandlerConfigClient.legendaryTabsPriority.get()
        );
    }
}
