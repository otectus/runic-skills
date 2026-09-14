package dev.xkmc.l2tabs.tabs.core;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/** Compile-only signature mirror verified against L2 Tabs 0.3.3. Never packaged. */
public class TabManager {
    public TabToken<?> selected;
    public int tabPage;
    public TabManager(Screen screen) {}
    public void init(Consumer<AbstractWidget> add, TabToken<?> selected) {}
    public Screen getScreen() { throw new UnsupportedOperationException("Compile-only API mirror"); }
}
