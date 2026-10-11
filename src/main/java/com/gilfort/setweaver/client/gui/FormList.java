package com.gilfort.setweaver.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Scrollable form in the details area of the set browser. Each row has a label on the left and widgets, icons or live
 * text on the right; rows of changed fields show a marker. Plain text rows span the whole width.
 */
public class FormList extends ContainerObjectSelectionList<FormList.Row> {

    /** Height of a row with widgets. */
    public static final int ROW_HEIGHT = 22;
    /** Height of a plain text row. */
    public static final int TEXT_HEIGHT = 12;
    /** Default width of the label column. */
    public static final int LABEL_WIDTH = 110;
    private static final int GAP = 3;

    private final Supplier<Set<String>> changed;
    private final int labelWidth;

    /**
     * Creates the list.
     *
     * @param minecraft the client
     * @param x         the left edge
     * @param y         the top edge
     * @param width     the width
     * @param height    the height
     * @param changed   keys of changed fields
     */
    public FormList(Minecraft minecraft, int x, int y, int width, int height, Supplier<Set<String>> changed) {
        super(minecraft, width, height, y, ROW_HEIGHT);
        this.setX(x);
        this.changed = changed;
        // Narrow panels get a narrower label column so the widgets still fit
        this.labelWidth = Math.clamp(width / 3, 60, LABEL_WIDTH);
    }

    /**
     * Replaces all rows, keeping the scroll position.
     *
     * @param rows the rows
     */
    public void setRows(List<Row> rows) {
        double scroll = this.scrollAmount();
        this.clearEntries();
        rows.forEach(row -> this.addEntry(row, row.parts.isEmpty() ? TEXT_HEIGHT : ROW_HEIGHT));
        this.setScrollAmount(scroll);
    }

    /** @return the width available for a full-width text row */
    public int textWidth() {
        return this.getRowWidth() - 8;
    }

    /** @return the width right of the label column */
    public int partsWidth() {
        return this.getRowWidth() - this.labelWidth;
    }

    @Override
    public int getRowWidth() {
        return this.getWidth() - 14;
    }

    @Override
    public int getRowLeft() {
        return this.getX() + 4;
    }

    @Override
    protected int scrollBarX() {
        return this.getRight() - 6;
    }

    @Override
    protected void extractListBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
    }

    /** A part on the right side of a row. */
    public sealed interface Part {
    }

    /**
     * A widget; its width is kept.
     *
     * @param widget the widget
     */
    public record WidgetPart(AbstractWidget widget) implements Part {
    }

    /**
     * Live text.
     *
     * @param text  the text, read every frame
     * @param color the color
     * @param width the reserved width
     */
    public record TextPart(Supplier<Component> text, int color, int width) implements Part {
    }

    /**
     * A sprite icon (18 × 18).
     *
     * @param sprite the sprite
     */
    public record IconPart(Identifier sprite) implements Part {
    }

    /**
     * An item icon (16 × 16).
     *
     * @param stack the item
     */
    public record ItemPart(ItemStack stack) implements Part {
    }

    /** One row: a label plus parts, or a full-width text line. */
    public final class Row extends ContainerObjectSelectionList.Entry<Row> {

        private final @Nullable Component label;
        private final @Nullable String field;
        private final List<Part> parts;
        private final int color;
        private final int indent;
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private final List<Component> tooltip;

        /**
         * Creates a row.
         *
         * @param label   the label, or {@code null}
         * @param field   the field key for the change marker, or {@code null}
         * @param color   the label color
         * @param indent  the indent of the label
         * @param parts   the parts on the right
         * @param tooltip tooltip of the label
         */
        public Row(@Nullable Component label, @Nullable String field, int color, int indent, List<Part> parts, List<Component> tooltip) {
            this.label = label;
            this.field = field;
            this.color = color;
            this.indent = indent;
            this.parts = List.copyOf(parts);
            this.tooltip = List.copyOf(tooltip);
            for (Part part : parts) {
                if (part instanceof WidgetPart(AbstractWidget widget)) {
                    this.widgets.add(widget);
                }
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = FormList.this.minecraft.font;
            int x = this.getContentX() + this.indent;
            int middle = this.getContentYMiddle() - font.lineHeight / 2;
            boolean changedField = this.field != null && FormList.this.changed.get().contains(this.field);
            int labelWidth = this.parts.isEmpty() ? this.getContentWidth() - this.indent : FormList.this.labelWidth - this.indent;
            if (this.label != null) {
                int labelX = x;
                if (changedField) {
                    graphics.text(font, "●", x, middle, Theme.CHANGED, false);
                    labelX += 7;
                }
                List<FormattedCharSequence> lines = font.split(this.label, Math.max(20, labelWidth - (labelX - x) - 2));
                graphics.text(font, lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.getFirst(), labelX, middle, this.color, false);
                if (!this.tooltip.isEmpty() && mouseX >= x && mouseX < x + labelWidth && mouseY >= this.getContentY()
                        && mouseY < this.getContentY() + this.getContentHeight()) {
                    graphics.setComponentTooltipForNextFrame(font, this.tooltip, mouseX, mouseY);
                }
            }
            int partX = this.getContentX() + FormList.this.labelWidth;
            for (Part part : this.parts) {
                switch (part) {
                    case WidgetPart(AbstractWidget widget) -> {
                        widget.setPosition(partX, this.getContentYMiddle() - widget.getHeight() / 2);
                        widget.extractRenderState(graphics, mouseX, mouseY, a);
                        partX += widget.getWidth() + GAP;
                    }
                    case TextPart(Supplier<Component> text, int textColor, int width) -> {
                        graphics.text(font, text.get(), partX, middle, textColor, false);
                        partX += width + GAP;
                    }
                    case IconPart(Identifier sprite) -> {
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, partX, this.getContentYMiddle() - 9, 18, 18);
                        partX += 18 + GAP;
                    }
                    case ItemPart(ItemStack stack) -> {
                        graphics.item(stack, partX + 1, this.getContentYMiddle() - 8);
                        partX += 18 + GAP;
                    }
                }
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.widgets;
        }
    }

    /** Creates a section header row, optionally with widgets (e.g. add buttons). */
    public Row header(Component title, Part... parts) {
        return new Row(title, null, Theme.GOLD, 0, List.of(parts), List.of());
    }

    /** Creates a field row with change marker. */
    public Row field(Component label, String field, Part... parts) {
        return new Row(label, field, Theme.WHITE, 0, List.of(parts), List.of());
    }

    /** Creates an indented entry row of a list (one effect, one attribute, one item). */
    public Row entry(Component label, @Nullable String field, int color, List<Component> tooltip, Part... parts) {
        return new Row(label, field, color, 8, List.of(parts), tooltip);
    }

    /** Creates a text line spanning the whole row. */
    public Row text(Component text, int color, int indent) {
        return new Row(text, null, color, indent, List.of(), List.of());
    }
}
