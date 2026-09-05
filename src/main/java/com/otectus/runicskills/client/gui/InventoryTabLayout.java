package com.otectus.runicskills.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where the tab strip goes, expressed as plain geometry.
 *
 * <p>Placement used to be three arithmetic expressions inlined into {@link DrawTabs} and repeated
 * — with different inputs — in the mixin and in {@code RunicSkillsScreen}, so the drawn box, the
 * hover box and the click box could disagree, and nothing could avoid another mod's widgets. This
 * class computes the rectangles once; render, hover, tooltip and click all read the same
 * {@link TabLayout}.
 *
 * <p>Deliberately free of Minecraft imports: the interesting part is arithmetic, and arithmetic is
 * the part worth having unit tests for (JUnit in {@code src/test/java} cannot load Minecraft
 * classes). Callers pass in the panel and screen rectangles they already know.
 *
 * <p>{@link Anchor#TOP_LEFT} reproduces the pre-2.0.5 position exactly, and {@link Anchor#AUTO}
 * tries it first, so a vanilla inventory with nothing in the way is pixel-identical to 2.0.4.
 */
public final class InventoryTabLayout {

    /** Tab body width, matching the 26x32 cells in {@code textures/gui/container/tabs.png}. */
    public static final int TAB_W = 26;
    /** Tab body height. */
    public static final int TAB_H = 32;
    /** Horizontal step between tabs. One pixel wider than a tab, as the legacy strip was. */
    public static final int PITCH = 27;
    /** Vertical step for the side anchors: a tab plus a one-pixel seam. */
    public static final int V_PITCH = 33;
    /** Distance the top strip sits above the panel. Legacy value; do not change casually. */
    public static final int TOP_GAP = 28;
    /** Breathing room between the panel edge and a side/bottom strip, and against screen edges. */
    public static final int SPACING = 2;

    private InventoryTabLayout() {
    }

    /** An axis-aligned box in GUI-scaled pixels. */
    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        /** Half-open on both axes, matching {@code Utils.checkMouse} so hover and layout agree. */
        public boolean contains(int px, int py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        /** True when the two boxes share at least one pixel. Touching edges do not count. */
        public boolean intersects(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
    }

    /**
     * Where the player wants the strip. {@link #AUTO} is a search over the rest, in the order they
     * are declared here.
     */
    public enum Anchor {
        AUTO,
        TOP_LEFT,
        TOP_RIGHT,
        LEFT,
        RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        /**
         * Lenient parse for the config string. Anything unrecognised — including {@code null} and
         * a hand-edited typo — resolves to {@link #AUTO} rather than throwing, because a bad TOML
         * value must not be able to break the inventory screen.
         */
        public static Anchor parse(String raw) {
            if (raw == null) return AUTO;
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return AUTO;
            }
        }
    }

    /**
     * The result: one rect per tab, the bounding {@code strip} they occupy (used for the drag
     * handle and the Shift outline), and the anchor that was actually used after an {@link
     * Anchor#AUTO} search.
     */
    public record TabLayout(List<Rect> tabs, Rect strip, Anchor resolved) {
    }

    /**
     * Resolves the anchor, positions the strip, applies the player's manual offset and clamps the
     * result on-screen.
     *
     * <p>Order matters: the collision search runs on the un-offset candidates, so a manual nudge
     * moves the strip the player is looking at instead of silently selecting a different anchor.
     * The clamp runs last so no combination of offset and anchor can push the strip out of reach.
     *
     * @param reserved boxes the strip should not overlap (recipe book, effect panel, other mods'
     *                 widgets). Only consulted for {@link Anchor#AUTO}; an explicit anchor is the
     *                 player's decision and is honoured even when it collides.
     */
    public static TabLayout compute(Rect panel, Rect screen, int tabCount, Anchor requested,
                                    int offsetX, int offsetY, List<Rect> reserved) {
        Anchor resolved = requested == null ? Anchor.AUTO : requested;
        if (tabCount <= 0) {
            return new TabLayout(List.of(), new Rect(panel.x(), panel.y() - TOP_GAP, 0, 0),
                    resolved == Anchor.AUTO ? Anchor.TOP_LEFT : resolved);
        }

        if (resolved == Anchor.AUTO) {
            resolved = search(panel, screen, tabCount, reserved);
        }

        Rect strip = stripFor(resolved, panel, tabCount);
        strip = new Rect(strip.x() + offsetX, strip.y() + offsetY, strip.w(), strip.h());
        strip = clamp(strip, screen);

        return new TabLayout(tabsIn(strip, resolved, tabCount), strip, resolved);
    }

    /**
     * First candidate that is fully on-screen (with {@link #SPACING} to spare) and clear of every
     * reserved box wins.
     *
     * <p>When every candidate collides, the least-covered one is used rather than
     * {@link Anchor#TOP_LEFT}. The strip is painted from {@code renderBg}, underneath the widget
     * layer, so a reserved box the strip sits on is a box drawn <em>over</em> the strip: with a
     * crowded inventory screen the old unconditional fall back to TOP_LEFT could put the strip
     * entirely behind another mod's widgets, which is indistinguishable from the tabs not being
     * there at all. Minimising the covered area keeps as much of the strip clickable and visible
     * as the screen allows. TOP_LEFT is still the answer when nothing fits on-screen, because an
     * overlapping strip is worse than the legacy one but a missing strip is worse than both.
     *
     * <p>The screen test uses the inflated strip so a candidate is not chosen flush against the
     * window edge, while the obstacle test uses the real strip: sitting immediately beside the
     * recipe book is a perfectly good answer, and inflating there would reject positions that
     * look right.
     */
    private static Anchor search(Rect panel, Rect screen, int tabCount, List<Rect> reserved) {
        Anchor best = Anchor.TOP_LEFT;
        long leastCovered = Long.MAX_VALUE;

        for (Anchor candidate : Anchor.values()) {
            if (candidate == Anchor.AUTO) continue;
            Rect strip = stripFor(candidate, panel, tabCount);
            if (!inside(inflate(strip, SPACING), screen)) continue;

            long covered = coveredArea(strip, reserved);
            if (covered == 0L) return candidate;
            if (covered < leastCovered) {
                leastCovered = covered;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * How many square pixels of {@code probe} the reserved boxes cover, counting a pixel once per
     * box that covers it. Overlapping obstacles therefore double-count, which is the behaviour we
     * want from a tie-breaker: a spot two mods both want is worse than a spot only one wants.
     */
    private static long coveredArea(Rect probe, List<Rect> reserved) {
        if (reserved == null) return 0L;
        long total = 0L;
        for (Rect r : reserved) {
            if (r == null) continue;
            long w = Math.min(probe.right(), r.right()) - Math.max(probe.x(), r.x());
            long h = Math.min(probe.bottom(), r.bottom()) - Math.max(probe.y(), r.y());
            if (w > 0 && h > 0) total += w * h;
        }
        return total;
    }

    private static boolean inside(Rect inner, Rect outer) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }

    private static Rect inflate(Rect r, int by) {
        return new Rect(r.x() - by, r.y() - by, r.w() + by * 2, r.h() + by * 2);
    }

    /** True for the anchors whose tabs run left-to-right rather than top-to-bottom. */
    private static boolean horizontal(Anchor anchor) {
        return anchor != Anchor.LEFT && anchor != Anchor.RIGHT;
    }

    private static int stripWidth(Anchor anchor, int tabCount) {
        return horizontal(anchor) ? (tabCount - 1) * PITCH + TAB_W : TAB_W;
    }

    private static int stripHeight(Anchor anchor, int tabCount) {
        return horizontal(anchor) ? TAB_H : (tabCount - 1) * V_PITCH + TAB_H;
    }

    private static Rect stripFor(Anchor anchor, Rect panel, int tabCount) {
        int w = stripWidth(anchor, tabCount);
        int h = stripHeight(anchor, tabCount);
        return switch (anchor) {
            // The legacy strip: first tab flush with the panel's left edge, 28 px above it.
            case TOP_LEFT, AUTO -> new Rect(panel.x(), panel.y() - TOP_GAP, w, h);
            case TOP_RIGHT -> new Rect(panel.right() - w, panel.y() - TOP_GAP, w, h);
            case LEFT -> new Rect(panel.x() - TAB_W - SPACING, panel.y(), w, h);
            case RIGHT -> new Rect(panel.right() + SPACING, panel.y(), w, h);
            case BOTTOM_LEFT -> new Rect(panel.x(), panel.bottom() + SPACING, w, h);
            case BOTTOM_RIGHT -> new Rect(panel.right() - w, panel.bottom() + SPACING, w, h);
        };
    }

    /**
     * Pushes the strip back inside the screen. When the strip is larger than the screen (tiny
     * window, huge GUI scale) the top-left corner wins, so the first tab stays clickable.
     */
    private static Rect clamp(Rect strip, Rect screen) {
        int x = Math.min(strip.x(), screen.right() - strip.w());
        int y = Math.min(strip.y(), screen.bottom() - strip.h());
        x = Math.max(x, screen.x());
        y = Math.max(y, screen.y());
        return new Rect(x, y, strip.w(), strip.h());
    }

    private static List<Rect> tabsIn(Rect strip, Anchor anchor, int tabCount) {
        List<Rect> tabs = new ArrayList<>(tabCount);
        for (int i = 0; i < tabCount; i++) {
            if (horizontal(anchor)) {
                tabs.add(new Rect(strip.x() + i * PITCH, strip.y(), TAB_W, TAB_H));
            } else {
                tabs.add(new Rect(strip.x(), strip.y() + i * V_PITCH, TAB_W, TAB_H));
            }
        }
        return List.copyOf(tabs);
    }
}
