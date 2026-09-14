package sfiomn.legendarytabs.client.screens;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import sfiomn.legendarytabs.api.tabs_menu.TabBase;

/** Compile-only signature mirror, verified with javap against Legendary Tabs 1.20.1-2.0. */
public class TabButton extends Button {
    public int tabPositionIndex;
    public TabBase tabBase;
    public Player player;
    public Screen screen;
    public boolean isDisabled;

    public TabButton(TabBase tab, Player player, Screen screen, int index, int left, int top) {
        super(0, 0, 0, 0, Component.empty(), button -> {}, DEFAULT_NARRATION);
        throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
    }

    public void updatePosition(int left, int top) {
        throw new AssertionError("Legendary Tabs API stub — the real implementation loads at runtime");
    }
}
