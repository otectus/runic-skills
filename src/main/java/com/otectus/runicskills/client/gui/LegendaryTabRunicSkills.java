package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.handler.HandlerConfigClient;
import net.minecraft.client.Minecraft;
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
 * To stay pixel-identical to neighbouring tabs in the strip, this tab reuses Legendary
 * Tabs' own icon asset rather than shipping one. Legendary Tabs 2.0 draws the button
 * chrome itself — from the skin configured for the screen the strip is on — and asks the
 * tab only for an 18×18 icon, so {@code legendarytabs:textures/gui/skills.png} is all we
 * supply and the frame shape, shading and hover transition come out identical to every
 * other tab for free.
 * <p>
 * Before 2.0 the API worked the other way round: each tab blitted its own 26×22 cell —
 * chrome and icon baked together — out of a single {@code tab_menu_buttons.png} atlas.
 * That atlas no longer exists, and a 26×22 cell cannot be remapped onto an 18×18 icon
 * slot, which is why this class no longer overrides {@code render} at all.
 */
public class LegendaryTabRunicSkills extends TabBase {

    // Vanilla InventoryScreen panel is 176×166 (the standard). Runic Skills' own screen uses
    // the same width but a taller 194-pixel panel (PANEL_HEIGHT in RunicSkillsScreen).
    // Passing the wrong height to TabsMenu#addTabToScreen makes Legendary Tabs compute the
    // wrong topScreenPos and draw its strip *inside* the panel instead of above it.
    private static final int VANILLA_GUI_WIDTH = 176;
    private static final int VANILLA_GUI_HEIGHT = 166;
    private static final int RUNIC_SKILLS_GUI_HEIGHT = 194;

    // Legendary Tabs 2.0 ships one bare 18×18 PNG per tab. TabBase.render blits this whole
    // file at (getIconTexX(), getIconTexY()), which default to 0,0 — correct for a bare icon
    // rather than a region cut from a sheet.
    private static final ResourceLocation ICON =
            new ResourceLocation("legendarytabs", "textures/gui/skills.png");

    // Ids are global across every Legendary Tabs addon, so namespace ours.
    @Override
    public String getId() {
        return "runicskills_skills";
    }

    @Override
    public ResourceLocation getIconTexture() {
        return ICON;
    }

    @Override
    public void openTargetScreen(Player player) {
        Utils.playSound();
        Minecraft.getInstance().setScreen(new RunicSkillsScreen());
    }

    @Override
    public boolean isEnabled(Player player) {
        return true;
    }

    // No render() override: TabBase draws the chrome for the screen the strip is on and
    // blits our 18×18 icon over it, including the hover/selected state.

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
