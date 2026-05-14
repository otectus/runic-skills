package com.otectus.runicskills.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Tooltip width-clamping helper (since 1.2.0). Long tooltip lines built from
 * translated strings can overflow the screen at GUI scale 4 / 4K resolution.
 * {@link #wrap(List, int)} walks each input component, splits any line that
 * exceeds {@code maxWidthPx} via {@link Font#split(Component, int)}, and
 * reconstructs the split as a sequence of {@link Component#literal} lines
 * carrying the original line's style — preserving color and formatting at the
 * cost of nested translation-key structure beyond the first wrapped line.
 *
 * <p>Lines that already fit within {@code maxWidthPx} pass through unchanged,
 * so translation keys and nested style nodes are preserved in the common case.
 */
public final class TooltipWrap {

    private TooltipWrap() {}

    /**
     * Returns a wrapped copy of {@code lines} where any component wider than
     * {@code maxWidthPx} is broken at word boundaries. The empty separator lines
     * already in the input are preserved verbatim.
     */
    public static List<Component> wrap(List<Component> lines, int maxWidthPx) {
        Font font = Minecraft.getInstance().font;
        List<Component> out = new ArrayList<>(lines.size());
        for (Component line : lines) {
            if (font.width(line) <= maxWidthPx) {
                out.add(line);
                continue;
            }
            Style style = line.getStyle();
            List<FormattedCharSequence> split = font.split(line, maxWidthPx);
            for (FormattedCharSequence seq : split) {
                MutableComponent piece = Component.literal(charSeqToString(seq));
                if (style != Style.EMPTY) piece.setStyle(style);
                out.add(piece);
            }
        }
        return out;
    }

    private static String charSeqToString(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}
