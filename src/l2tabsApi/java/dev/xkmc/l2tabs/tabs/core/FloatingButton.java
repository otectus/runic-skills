package dev.xkmc.l2tabs.tabs.core;

import java.util.function.IntSupplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Compile-only signature mirror verified against L2 Tabs 0.3.3. Never packaged. */
public class FloatingButton extends Button {
    protected FloatingButton(int width, int height, Component title, OnPress onPress) {
        super(0, 0, width, height, title, onPress, DEFAULT_NARRATION);
    }

    public void setXRef(IntSupplier origin, int offset) {}
    public void setYRef(IntSupplier origin, int offset) {}
}
