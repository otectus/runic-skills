package com.otectus.runicskills.client.gui;

import com.otectus.runicskills.client.gui.InventoryTabLayout.Anchor;
import com.otectus.runicskills.client.gui.InventoryTabLayout.Rect;
import com.otectus.runicskills.client.gui.InventoryTabLayout.TabLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The layout engine is the only part of the tab rework that can be tested without a client, and
 * the one regression that matters most is invisible in a screenshot: a conflict-free vanilla
 * inventory must land on exactly the pixels 2.0.4 used.
 */
class InventoryTabLayoutTest {

    /** A 854x480 window at GUI scale 1 with the vanilla inventory panel centred in it. */
    private static final Rect SCREEN = new Rect(0, 0, 854, 480);
    private static final Rect PANEL = new Rect((854 - 176) / 2, (480 - 166) / 2, 176, 166);

    private static TabLayout auto(List<Rect> reserved) {
        return InventoryTabLayout.compute(PANEL, SCREEN, 2, Anchor.AUTO, 0, 0, reserved);
    }

    @Test
    @DisplayName("nothing in the way reproduces the pre-2.0.5 position exactly")
    void legacyPositionWhenNothingReserved() {
        TabLayout layout = auto(List.of());

        assertEquals(Anchor.TOP_LEFT, layout.resolved());
        assertEquals(new Rect(PANEL.x(), PANEL.y() - 28, 26, 32), layout.tabs().get(0));
        assertEquals(new Rect(PANEL.x() + 27, PANEL.y() - 28, 26, 32), layout.tabs().get(1));
        assertEquals(PANEL.y() - 28, layout.strip().y());
    }

    @Test
    @DisplayName("something over the top-left band pushes AUTO to the next candidate")
    void reservedTopBandMovesStrip() {
        // A box covering the left half of the band above the panel, as an open recipe book does.
        Rect obstacle = new Rect(PANEL.x() - 40, PANEL.y() - 60, 120, 60);
        TabLayout layout = auto(List.of(obstacle));

        assertTrue(layout.resolved() == Anchor.TOP_RIGHT || layout.resolved() == Anchor.LEFT,
                "expected the next fitting candidate, got " + layout.resolved());
        for (Rect tab : layout.tabs()) {
            assertFalse(tab.intersects(obstacle), "tab " + tab + " still overlaps the reserved region");
        }
    }

    @Test
    @DisplayName("the whole top band blocked falls through to a side anchor")
    void fullTopBandFallsThrough() {
        Rect band = new Rect(0, PANEL.y() - 60, 854, 60);
        TabLayout layout = auto(List.of(band));

        assertEquals(Anchor.LEFT, layout.resolved());
        assertEquals(new Rect(PANEL.x() - 26 - 2, PANEL.y(), 26, 32), layout.tabs().get(0));
        // LEFT stacks downwards at a 33 px pitch.
        assertEquals(new Rect(PANEL.x() - 26 - 2, PANEL.y() + 33, 26, 32), layout.tabs().get(1));
    }

    @Test
    @DisplayName("offsets are applied to the resolved anchor, not the screen")
    void offsetsApplyAfterAnchor() {
        TabLayout base = InventoryTabLayout.compute(PANEL, SCREEN, 2, Anchor.TOP_RIGHT, 0, 0, List.of());
        TabLayout moved = InventoryTabLayout.compute(PANEL, SCREEN, 2, Anchor.TOP_RIGHT, -10, 7, List.of());

        assertEquals(Anchor.TOP_RIGHT, moved.resolved());
        assertEquals(base.strip().x() - 10, moved.strip().x());
        assertEquals(base.strip().y() + 7, moved.strip().y());
        assertEquals(base.tabs().get(1).x() - 10, moved.tabs().get(1).x());
    }

    @Test
    @DisplayName("an offset that would leave the screen is clamped back inside it")
    void clampKeepsStripOnScreen() {
        Rect small = new Rect(0, 0, 320, 240);
        Rect panel = new Rect((320 - 176) / 2, (240 - 166) / 2, 176, 166);
        TabLayout layout = InventoryTabLayout.compute(panel, small, 2, Anchor.TOP_LEFT, 500, -500, List.of());

        assertTrue(layout.strip().x() >= 0 && layout.strip().right() <= small.w(),
                "strip escaped horizontally: " + layout.strip());
        assertTrue(layout.strip().y() >= 0 && layout.strip().bottom() <= small.h(),
                "strip escaped vertically: " + layout.strip());
        assertEquals(layout.strip().x(), layout.tabs().get(0).x());
        assertEquals(layout.strip().y(), layout.tabs().get(0).y());
    }

    @Test
    @DisplayName("an unreadable anchor string falls back to AUTO instead of throwing")
    void unknownAnchorParsesToAuto() {
        assertEquals(Anchor.AUTO, Anchor.parse("bogus"));
        assertEquals(Anchor.AUTO, Anchor.parse(null));
        assertEquals(Anchor.BOTTOM_RIGHT, Anchor.parse(" bottom_right "));
    }

    @Test
    @DisplayName("an explicit anchor is honoured even when it collides")
    void explicitAnchorIgnoresReservedRegions() {
        Rect band = new Rect(0, PANEL.y() - 60, 854, 60);
        TabLayout layout = InventoryTabLayout.compute(PANEL, SCREEN, 2, Anchor.TOP_LEFT, 0, 0, List.of(band));

        assertEquals(Anchor.TOP_LEFT, layout.resolved());
        assertTrue(layout.tabs().get(0).intersects(band));
    }
}
