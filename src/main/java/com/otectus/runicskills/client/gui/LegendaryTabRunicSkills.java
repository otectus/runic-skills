package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.client.core.Utils;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.registry.RegistryItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import sfiomn.legendarytabs.api.tabs_menu.TabBase;
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;

/**
 * Native Legendary Tabs tab for Runic Skills. When Legendary Tabs is installed, this tab
 * is registered via {@link TabsMenu#register(TabBase)} so it appears inside Legendary Tabs'
 * own tab strip — drawn, positioned, highlighted, paginated, and "current" tracked by
 * Legendary Tabs itself, exactly like the built-in tabs for FTB Quests, Backpacked, etc.
 * <p>
 * Tab icon is the Runic Skills leveling book item rendered as an {@link ItemStack}; this
 * matches the icon players already associate with the Skills screen and avoids needing a
 * separate texture atlas entry in Legendary Tabs' sprite sheet.
 */
public class LegendaryTabRunicSkills extends TabBase {

    // Matches the vanilla inventory panel dimensions used by most other Legendary Tabs.
    private static final int VANILLA_GUI_WIDTH = 176;
    private static final int VANILLA_GUI_HEIGHT = 166;

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
        // Legendary Tabs invokes this with the icon-origin coordinates inside the tab button
        // (the button background/frame is drawn by Legendary Tabs itself). Draw our 16x16
        // leveling-book icon at that origin.
        ItemStack icon = RegistryItems.LEVELING_BOOK.get().getDefaultInstance();
        gfx.renderItem(icon, x, y);
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
        // Skills is open and the user can jump straight back to any other tab.
        TabsMenu.addTabToScreen(
                this,
                RunicSkillsScreen.class,
                player -> VANILLA_GUI_WIDTH,
                player -> VANILLA_GUI_HEIGHT,
                HandlerConfigClient.legendaryTabsPriority.get()
        );
    }
}
