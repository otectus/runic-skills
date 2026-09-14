package com.otectus.runicskills.client.integration;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import sfiomn.legendarytabs.api.tabs_menu.TabBase;
import sfiomn.legendarytabs.api.tabs_menu.TabsMenu;

/** A foreign inventory action hosted by Legendary Tabs' own buttons and pagination. */
final class LegendaryTabDestination extends TabBase {
    private NativeInventoryTabs.Destination destination;
    private final int priority;

    LegendaryTabDestination(NativeInventoryTabs.Destination destination, int priority) {
        this.destination = destination;
        this.priority = priority;
    }

    void update(NativeInventoryTabs.Destination destination) {
        this.destination = destination;
    }

    @Override
    public String getId() {
        return destination.id();
    }

    @Override
    public void openTargetScreen(Player player) {
        if (isEnabled(player)) destination.open().run();
    }

    @Override
    public boolean isEnabled(Player player) {
        // A server/config filter can remove an exported L2 action between screen inits.
        // Do not let an older native page keep its captured action enabled afterward.
        return player != null && NativeInventoryTabs.destinations().stream()
                .anyMatch(current -> current.id().equals(destination.id()) && current.enabled().getAsBoolean());
    }

    @Override
    public boolean isCurrentlyUsed(Screen currentScreen) {
        return destination.selected().test(currentScreen);
    }

    @Override
    public Component getTooltip() {
        return destination.title();
    }

    @Override
    public ResourceLocation getIconTexture() {
        // The item renderer below supplies the icon; the API still requires this method.
        return DEFAULT_BUTTONS_TEXTURE;
    }

    @Override
    public void initTabOnScreens() {
        TabsMenu.addTabToScreen(this, InventoryScreen.class, player -> 176, player -> 166, priority);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, boolean selected,
                       ResourceLocation buttonSkin, int iconOffsetX, int iconOffsetY) {
        // Exact TabBase 2.0 frame coordinates, verified against the installed jar. Unlike
        // texture icons, foreign tabs have real ItemStacks (including their model/NBT).
        graphics.blit(buttonSkin == null ? DEFAULT_BUTTONS_TEXTURE : buttonSkin,
                x, y, selected ? 27 : 0, 0, 26, 22, 64, 64);
        ItemStack icon = destination.icon().get();
        if (!icon.isEmpty()) graphics.renderItem(icon, x + 5 + iconOffsetX, y + 5 + iconOffsetY);
    }
}
