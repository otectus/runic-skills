package dev.xkmc.l2tabs.tabs.core;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Compile-only signature mirror verified against L2 Tabs 0.3.3. Never packaged. */
public abstract class BaseTab<T extends BaseTab<T>> extends FloatingButton {
    public final ItemStack stack;
    public final TabToken<T> token;
    public final TabManager manager;
    public int page;

    public BaseTab(TabToken<T> token, TabManager manager, ItemStack stack, Component title) {
        super(26, 32, title, button -> {});
        this.token = token;
        this.manager = manager;
        this.stack = stack;
    }
    public abstract void onTabClicked();
}
