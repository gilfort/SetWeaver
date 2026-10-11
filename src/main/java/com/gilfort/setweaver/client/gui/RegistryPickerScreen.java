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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Picks entries from a list (effects, attributes, item tags, items, roles) with a search field. Each row shows an
 * optional icon, the name and a second line in small print.
 * <ul>
 * <li>Single mode: a click picks the entry and returns to the parent screen; optionally the typed text can be used as
 * a new value.</li>
 * <li>Multi mode: clicks tick entries (entries already present are greyed out); "Select all shown" ticks every
 * visible entry, so e.g. typing "leather" and selecting all adds a whole armor set at once.</li>
 * </ul>
 *
 * @param <T> the value type
 */
public class RegistryPickerScreen<T> extends Screen {

    private static final int ROW = 22;
    private static final int WIDTH = 280;
    private static final int ICON = 18;
    private static final int CHECK = 9;
    private static final int BUTTON_WIDTH = 92;

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
    private final @Nullable Consumer<T> onPick;
    private final @Nullable Consumer<List<T>> onPickMany;
    private final @Nullable Function<String, T> custom;
    private final Predicate<T> present;
    private final Set<T> chosen = new LinkedHashSet<>();
    private List<Option<T>> filtered;
    private @Nullable Option<T> customOption;
    private int scroll;
    private @Nullable EditBox search;
    private @Nullable Button confirmButton;

    /**
     * Creates a single-choice picker that only allows listed entries, sorted by name.
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
     * Creates a single-choice picker.
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
        this(parent, title, options, onPick, null, custom, value -> false, sort);
    }

    private RegistryPickerScreen(Screen parent, Component title, List<Option<T>> options, @Nullable Consumer<T> onPick,
                                 @Nullable Consumer<List<T>> onPickMany, @Nullable Function<String, T> custom, Predicate<T> present, boolean sort) {
        super(title);
        this.parent = parent;
        List<Option<T>> list = new ArrayList<>(options);
        if (sort) {
            list.sort(Comparator.comparing(option -> option.name().getString().toLowerCase(Locale.ROOT)));
        }
        this.options = List.copyOf(list);
        this.filtered = this.options;
        this.onPick = onPick;
        this.onPickMany = onPickMany;
        this.custom = custom;
        this.present = present;
    }

    /**
     * Creates a multi-choice picker, sorted by name.
     *
     * @param parent     the screen to return to
     * @param title      the title
     * @param options    the entries
     * @param present    entries that are already added (shown greyed out, not selectable)
     * @param onPickMany called with the ticked values, in list order, after returning to the parent
     * @param <T>        the value type
     * @return the picker
     */
    public static <T> RegistryPickerScreen<T> multi(Screen parent, Component title, List<Option<T>> options, Predicate<T> present,
                                                    Consumer<List<T>> onPickMany) {
        return new RegistryPickerScreen<>(parent, title, options, null, onPickMany, null, present, true);
    }

    private boolean multi() {
        return this.onPickMany != null;
    }

    private int left() {
        return (this.width - WIDTH) / 2;
    }

    private int listTop() {
        return 52;
    }

    private int listBottom() {
        return this.height - (this.multi() ? 44 : 34);
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
        int y = this.height - 26;
        if (this.multi()) {
            int x = this.width / 2 - (BUTTON_WIDTH * 3 + 8) / 2;
            this.addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.picker.select_all"), button -> this.selectAllShown())
                    .bounds(x, y, BUTTON_WIDTH, 20).build());
            this.confirmButton = this.addRenderableWidget(Button.builder(this.confirmLabel(), button -> this.confirmMany())
                    .bounds(x + BUTTON_WIDTH + 4, y, BUTTON_WIDTH, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                    .bounds(x + 2 * (BUTTON_WIDTH + 4), y, BUTTON_WIDTH, 20).build());
            this.updateConfirm();
        } else {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                    .bounds(this.width / 2 - 50, y, 100, 20).build());
        }
        this.filter(query);
    }

    private Component confirmLabel() {
        return Component.translatable("gui.setweaver.picker.add", this.chosen.size());
    }

    private void updateConfirm() {
        if (this.confirmButton != null) {
            this.confirmButton.setMessage(this.confirmLabel());
            this.confirmButton.active = !this.chosen.isEmpty();
        }
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

    private void selectAllShown() {
        for (Option<T> option : this.filtered) {
            if (!this.present.test(option.value())) {
                this.chosen.add(option.value());
            }
        }
        this.updateConfirm();
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
            boolean isPresent = this.multi() && this.present.test(option.value());
            boolean isChosen = this.chosen.contains(option.value());
            if (isChosen) {
                graphics.fill(left + 1, y, left + WIDTH - 1, y + ROW, Theme.SELECTED);
            } else if (!isPresent && mouseX >= left && mouseX < left + WIDTH && mouseY >= Math.max(top, y) && mouseY < Math.min(bottom, y + ROW)) {
                graphics.fill(left + 1, y, left + WIDTH - 1, y + ROW, Theme.HOVER);
            }
            int textX = left + 4;
            if (this.multi()) {
                int boxY = y + (ROW - CHECK) / 2;
                graphics.outline(textX, boxY, CHECK, CHECK, isPresent ? Theme.DARK_GREY : Theme.GREY);
                if (isChosen || isPresent) {
                    graphics.text(this.font, "✔", textX + 1, boxY, isPresent ? Theme.DARK_GREY : Theme.GREEN, false);
                }
                textX += CHECK + 4;
            }
            if (option.sprite() != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, option.sprite(), textX - 1, y + 2, ICON, ICON);
                textX += ICON + 3;
            } else if (option.item() != null) {
                graphics.item(option.item(), textX, y + 3);
                textX += ICON + 3;
            }
            int color = option == this.customOption ? Theme.GOLD : isPresent ? Theme.DARK_GREY : Theme.WHITE;
            graphics.text(this.font, option.name(), textX, y + 2, color, false);
            String detail = isPresent ? Component.translatable("gui.setweaver.picker.present").getString() : option.detail();
            graphics.text(this.font, detail, textX, y + 12, Theme.DARK_GREY, false);
        }
        graphics.disableScissor();
        if (rows.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.setweaver.picker.nothing"), this.width / 2, top + 8, Theme.DARK_GREY);
        }
        if (this.multi()) {
            graphics.centeredText(this.font, Component.translatable("gui.setweaver.picker.multi_hint"), this.width / 2, bottom + 3, Theme.DARK_GREY);
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
        Option<T> option = rows.get(index);
        if (!this.multi()) {
            this.pick(option);
        } else if (!this.present.test(option.value())) {
            if (!this.chosen.remove(option.value())) {
                this.chosen.add(option.value());
            }
            this.updateConfirm();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation()) {
            if (this.multi() && !this.chosen.isEmpty()) {
                this.confirmMany();
                return true;
            }
            if (!this.multi() && this.rows().size() == 1) {
                this.pick(this.rows().getFirst());
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void pick(Option<T> option) {
        this.onClose();
        if (this.onPick != null) {
            this.onPick.accept(option.value());
        }
    }

    private void confirmMany() {
        // Keep the order of the list, not the order of the clicks
        List<T> picked = this.options.stream().map(Option::value).filter(this.chosen::contains).toList();
        this.onClose();
        if (this.onPickMany != null && !picked.isEmpty()) {
            this.onPickMany.accept(picked);
        }
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
