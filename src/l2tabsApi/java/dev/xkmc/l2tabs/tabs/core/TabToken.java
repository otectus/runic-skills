package dev.xkmc.l2tabs.tabs.core;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Compile-only signature mirror of L2 Tabs 0.3.3. */
public class TabToken<T extends BaseTab<T>> {
    public final Component title = null;

    public int getIndex() { throw new UnsupportedOperationException("Compile-only API mirror"); }
    public T create(TabManager manager) { throw new UnsupportedOperationException("Compile-only API mirror"); }

    @FunctionalInterface
    public interface TabFactory<T extends BaseTab<T>> {
        T create(TabToken<T> token, TabManager manager, ItemStack stack, Component title);
    }
}
