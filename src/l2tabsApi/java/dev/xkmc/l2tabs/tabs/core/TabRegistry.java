package dev.xkmc.l2tabs.tabs.core;

import java.util.function.Supplier;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** Compile-only signature mirror. Both the factory owner and return descriptor are ABI. */
public class TabRegistry {
    public static <T extends BaseTab<T>> TabToken<T> registerTab(
            int priority, TabToken.TabFactory<T> factory, Supplier<Item> icon, Component title) {
        throw new UnsupportedOperationException("Compile-only API mirror");
    }

    public static List<TabToken<?>> getTabs() {
        throw new UnsupportedOperationException("Compile-only API mirror");
    }
}
