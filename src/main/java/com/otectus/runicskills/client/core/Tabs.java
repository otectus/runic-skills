
package com.otectus.runicskills.client.core;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class Tabs {
    private final String name;
    public String getName() { return name; }
    private final ItemStack itemStack;
    public ItemStack getItemStack() { return itemStack; }

    /**
     * Built on demand rather than held as an instance.
     *
     * <p>The tab strip stored a fully constructed {@link Screen} per tab and rebuilt the strip on
     * every frame, so simply having the inventory open allocated a whole {@code InventoryScreen}
     * — and with it a {@code RecipeBookComponent} — plus a second screen, 60+ times a second, to
     * render two 26x32 icons. Only the tab that is actually clicked ever needs a screen (RS-025).
     */
    private final java.util.function.Supplier<Screen> screen;
    public Screen getScreen() { return screen.get(); }
    private final Boolean isScreen;
    public boolean isScreen() { return isScreen; }

    private final Component getName;
    public Component getComponentName() { return getName; }

    public Tabs(String name, ItemStack itemStack, java.util.function.Supplier<Screen> screen,
                Boolean isScreen, Component getName) {
        this.name = name;
        this.itemStack = itemStack;
        this.screen = screen;
        this.isScreen = isScreen;
        this.getName = getName;
    }
}


