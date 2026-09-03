package com.otectus.runicskills.api.client;

import com.otectus.runicskills.client.gui.InventoryTabLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

/**
 * Fired on the Forge bus before the Runic Skills tab strip picks a position, so other mods can say
 * "do not put it here".
 *
 * <p>The strip's whole placement problem is that it cannot see the widgets other mods draw on the
 * inventory screen — the report that started this was CustomNPCs' own tab column, which Runic
 * Skills has no dependency on and no business knowing the pixels of. Rather than hard-code another
 * mod's geometry, anything drawn on the screen can add its own rectangle here and the layout will
 * route around it.
 *
 * <p>Not cancellable, and only ever fired on the client. Listeners should only <i>add</i> to
 * {@link #getReserved()}; the list already contains the built-in regions (recipe book, potion
 * effect panel) registered in {@code InventoryTabReservedRegions}.
 *
 * <p>Reserved regions only steer the automatic anchor. A player who has explicitly chosen an
 * anchor or dragged the strip has overruled every listener, by design.
 */
public class InventoryTabLayoutEvent extends Event {

    private final Screen screen;
    private final List<InventoryTabLayout.Rect> reserved;

    public InventoryTabLayoutEvent(Screen screen, List<InventoryTabLayout.Rect> reserved) {
        this.screen = screen;
        this.reserved = reserved;
    }

    /** The screen the strip is about to be laid out on. */
    public Screen getScreen() {
        return screen;
    }

    /** The mutable list of boxes the strip should avoid, in GUI-scaled pixels. */
    public List<InventoryTabLayout.Rect> getReserved() {
        return reserved;
    }
}
