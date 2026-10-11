package com.gilfort.setweaver.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Picks one entry from a list (effects, attributes, item tags, items, roles, levels) with a search field. Each row
 * shows an optional icon, the name and a second line in small print; a click picks the entry and returns to the
 * parent screen. Optionally the typed text can be used as a new value.
 *
 * @param <T> the value type
 */
public class RegistryPickerScreen<T> extends Screen {

    private static final int ROW = 22;
    private static final int WIDTH = 280;
    private static final int ICON = 18;

    /**
     * An entry of the picker.
     *
     * @param value  the value handed to the callback
     * @param name   the shown name
     * @param detail the second line (e.g. the id), may be empty
     * @param sprite an icon sprite, or {@code null}
     * @param item   an item icon, or {@code null}
     * @param <T>    the value type
     */
    public record Option<T>(T value, Component name, String detail, @Nullable Identifier sprite, @Nullable ItemStack item) {

        /** Creates an option without icon. */
        public static <T> Option<T> of(T value, Component name, String detail) {
            return new Option<>(value, name, detail, null, null);
        }
    }

    private final Screen parent;
    private final List<Option<T>> options;
    private final Consumer<T> onPick;
    private final @Nullable Function<String, T> custom;
    private List<Option<T>> filtered;
    private @Nullable Option<T> customOption;
    private int scroll;
    private @Nullable EditBox search;

    /**
     * Creates a picker that only allows listed entries, sorted by name.
     *
     * @param parent  the screen to return to
     * @param title   the title
     * @param options the entries
     * @param onPick  called with the picked value after returning to the parent
     */
    public RegistryPickerScreen(Screen parent, Component title, List<Option<T>> options, Consumer<T> onPick) {
        this(parent, title, options, onPick, null, true);
    }

    /**
     * Creates a picker.
     *
     * @param parent  the screen to return to
     * @param title   the title
     * @param options the entries
     * @param onPick  called with the picked value after returning to the parent
     * @param custom  turns typed text into a value ({@code null} result = not allowed), or {@code null}
     * @param sort    whether to sort the entries by name
     */
    public RegistryPickerScreen(Screen parent, Component title, List<Option<T>> options, Consumer<T> onPick,
                                @Nullable Function<String, T> custom, boolean sort) {
        super(title);
        this.parent = parent;
        List<Option<T>> list = new ArrayList<>(options);
        if (sort) {
            list.sort(Comparator.comparing(option -> option.name().getString().toLowerCase(Locale.ROOT)));
        }
        this.options = List.copyOf(list);
        this.filtered = this.options;
        this.onPick = onPick;
        this.custom = custom;
    }

    private int left() {
        return (this.width - WIDTH) / 2;
    }

    private int listTop() {
        return 52;
    }

    private int listBottom() {
        return this.height - 34;
    }

    private List<Option<T>> rows() {
        if (this.customOption == null) {
            return this.filtered;
        }
        List<Option<T>> rows = new ArrayList<>();
        rows.add(this.customOption);
        rows.addAll(this.filtered);
        return rows;
    }

    @Override
    protected void init() {
        String query = this.search == null ? "" : this.search.getValue();
        this.search = new EditBox(this.font, this.left(), 28, WIDTH, 18, Component.translatable("gui.setweaver.search"));
        this.search.setHint(Component.translatable("gui.setweaver.search"));
        this.search.setValue(query);
        this.search.setResponder(this::filter);
        this.addRenderableWidget(this.search);
        this.setInitialFocus(this.search);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20).build());
        this.filter(query);
    }

    private void filter(String query) {
        String filter = query.trim().toLowerCase(Locale.ROOT);
        this.filtered = filter.isEmpty() ? this.options : this.options.stream()
                .filter(option -> option.name().getString().toLowerCase(Locale.ROOT).contains(filter)
                        || option.detail().toLowerCase(Locale.ROOT).contains(filter))
                .toList();
        this.customOption = null;
        if (this.custom != null && !filter.isEmpty()
                && this.options.stream().noneMatch(option -> option.name().getString().equalsIgnoreCase(query.trim()))) {
            T value = this.custom.apply(query.trim());
            if (value != null) {
                this.customOption = Option.of(value, Component.translatable("gui.setweaver.picker.use", query.trim()),
                        Component.translatable("gui.setweaver.picker.use.detail").getString());
            }
        }
        this.scroll = 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(this.font, this.title, this.width / 2, 12, Theme.WHITE);
        int left = this.left();
        int top = this.listTop();
        int bottom = this.listBottom();
        graphics.fill(left, top, left + WIDTH, bottom, Theme.DIALOG);
        graphics.outline(left, top, WIDTH, bottom - top, Theme.BORDER);
        List<Option<T>> rows = this.rows();
        this.scroll = Math.clamp(this.scroll, 0, Math.max(0, rows.size() * ROW - (bottom - top)));
        graphics.enableScissor(left + 1, top + 1, left + WIDTH - 1, bottom - 1);
        for (int i = 0; i < rows.size(); i++) {
            int y = top + i * ROW - this.scroll;
            if (y + ROW < top || y > bottom) {
                continue;
            }
            Option<T> option = rows.get(i);
            if (mouseX >= left && mouseX < left + WIDTH && mouseY >= Math.max(top, y) && mouseY < Math.min(bottom, y + ROW)) {
                graphics.fill(left + 1, y, left + WIDTH - 1, y + ROW, Theme.HOVER);
            }
            int textX = left + 4;
            if (option.sprite() != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, option.sprite(), left + 3, y + 2, ICON, ICON);
                textX += ICON + 3;
            } else if (option.item() != null) {
                graphics.item(option.item(), left + 4, y + 3);
                textX += ICON + 3;
            }
            graphics.text(this.font, option.name(), textX, y + 2, option == this.customOption ? Theme.GOLD : Theme.WHITE, false);
            graphics.text(this.font, option.detail(), textX, y + 12, Theme.DARK_GREY, false);
        }
        graphics.disableScissor();
        if (rows.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.setweaver.picker.nothing"), this.width / 2, top + 8, Theme.DARK_GREY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int left = this.left();
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || event.x() < left || event.x() >= left + WIDTH
                || event.y() < this.listTop() || event.y() >= this.listBottom()) {
            return false;
        }
        int index = (int) ((event.y() - this.listTop() + this.scroll) / ROW);
        List<Option<T>> rows = this.rows();
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        this.pick(rows.get(index));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && this.rows().size() == 1) {
            this.pick(this.rows().getFirst());
            return true;
        }
        return super.keyPressed(event);
    }

    private void pick(Option<T> option) {
        this.onClose();
        this.onPick.accept(option.value());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll -= (int) (scrollY * ROW * 2);
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
