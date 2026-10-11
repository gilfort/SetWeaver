package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.seteffects.ArmorPieces;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Drawing helpers and the shared parchment color scheme of the SetWeaver screens.
 */
public final class GuiUtil {

    /** Panel background (parchment). */
    public static final int PANEL_BG = 0xFFF5F0E0;
    /** Panel border (warm brown). */
    public static final int PANEL_BORDER = 0xFF8B7355;
    /** Divider between two panels. */
    public static final int PANEL_DIVIDER = 0xFFB8A080;
    /** Main text color. */
    public static final int COLOR_TEXT = 0xFF000000;
    /** Muted text color for hints. */
    public static final int COLOR_GRAY = 0xFF444444;
    /** Error text color. */
    public static final int COLOR_ERROR = 0xFFCC3333;
    /** Success text color. */
    public static final int COLOR_SUCCESS = 0xFF338833;
    /** Highlight of the selected row. */
    public static final int COLOR_SELECTED = 0x44FFCC00;
    /** Highlight of the hovered row. */
    public static final int COLOR_HOVER = 0x22FFCC00;
    /** Dimmed background behind popups. */
    public static final int OVERLAY_DIM = 0x88000000;

    private GuiUtil() {
    }

    /**
     * Translates a key on the client.
     *
     * @param key  the lang key
     * @param args format arguments
     * @return the translated text
     */
    public static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    /** Draws a parchment panel with a 1 px border. */
    public static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, PANEL_BG);
        graphics.renderOutline(x, y, w, h, PANEL_BORDER);
    }

    /**
     * Draws a vertical scrollbar if the content does not fit.
     *
     * @param x       left edge of the 2 px wide bar
     * @param y       top of the track
     * @param height  track height
     * @param total   number of rows
     * @param visible number of visible rows
     * @param offset  first visible row
     */
    public static void drawScrollbar(GuiGraphics graphics, int x, int y, int height, int total, int visible, int offset) {
        if (total <= visible || visible <= 0) {
            return;
        }
        float ratio = (float) offset / Math.max(1, total - visible);
        int thumb = Math.max(10, height * visible / total);
        int thumbY = y + (int) ((height - thumb) * Math.min(1f, ratio));
        graphics.fill(x, y, x + 2, y + height, 0x33000000);
        graphics.fill(x, thumbY, x + 2, thumbY + thumb, 0xAA553300);
    }

    /**
     * Shortens a text with a trailing ellipsis until it fits.
     *
     * @return the text, shortened if needed
     */
    public static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && font.width(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    /**
     * Word-wraps a text.
     *
     * @return the lines
     */
    public static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!current.isEmpty() && font.width(current + " " + word) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                if (!current.isEmpty()) {
                    current.append(' ');
                }
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    /**
     * @param bounds {x, y, w, h}, may be {@code null}
     * @return whether the point lies inside the bounds
     */
    public static boolean hit(int[] bounds, double mouseX, double mouseY) {
        return bounds != null && mouseX >= bounds[0] && mouseX < bounds[0] + bounds[2]
                && mouseY >= bounds[1] && mouseY < bounds[1] + bounds[3];
    }

    /** @return whether the point lies inside the rectangle */
    public static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** @return all item tags that contain at least one armor piece, sorted by id */
    public static List<TagKey<Item>> armorItemTags() {
        return BuiltInRegistries.ITEM.getTags()
                .filter(pair -> pair.getSecond().stream().anyMatch(h -> ArmorPieces.isArmor(h.value())))
                .map(Pair::getFirst)
                .sorted(Comparator.comparing(t -> t.location().toString()))
                .toList();
    }

    /** @return all items of a tag */
    public static List<Item> itemsInTag(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.getTag(tag).map(set -> set.stream().map(Holder::value).toList()).orElse(List.of());
    }

    /**
     * Formats a number of armor pieces, e.g. "2 Pieces".
     *
     * @param count the number of pieces
     * @return the translated text
     */
    public static String pieces(int count) {
        return tr(count == 1 ? "gui.setweaver.piece" : "gui.setweaver.pieces", count);
    }
}
