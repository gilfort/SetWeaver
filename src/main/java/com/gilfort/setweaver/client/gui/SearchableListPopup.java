package com.gilfort.setweaver.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Modal list with a search field, used to pick effects, attributes, tags, roles and levels. Type to filter, arrow
 * keys to move, Enter or double-click to select, Escape to cancel.
 *
 * @param <T> type of the selected value
 */
public class SearchableListPopup<T> extends Screen {

    private static final int POPUP_WIDTH = 260;
    private static final int POPUP_HEIGHT = 280;
    private static final int ITEM_HEIGHT = 14;
    private static final int SEARCH_HEIGHT = 16;
    private static final int PADDING = 8;
    private static final int SCROLLBAR_W = 4;

    private final Screen parentScreen;
    private final List<Entry<T>> allEntries;
    private final List<Entry<T>> filteredEntries;
    private final Consumer<T> onSelect;
    /** Creates a value from free text; {@code null} if free text is not allowed. */
    private final Function<String, T> customEntryFactory;

    private EditBox searchBox;
    private int popupX, popupY;
    private int listX, listY, listW, listH;
    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private int maxVisibleItems;

    /**
     * One selectable row.
     *
     * @param value          the value passed to the callback
     * @param displayName    the shown text
     * @param searchableText lower-case text matched by the filter
     * @param <T>            type of the value
     */
    public record Entry<T>(T value, Component displayName, String searchableText) {

        /** Creates an entry; the search text is lower-cased. */
        public Entry(T value, Component displayName, String searchableText) {
            this.value = value;
            this.displayName = displayName;
            this.searchableText = searchableText.toLowerCase(Locale.ROOT);
        }

        /** Creates an entry that is searched by its display name. */
        public Entry(T value, Component displayName) {
            this(value, displayName, displayName.getString());
        }
    }

    /**
     * Creates a popup that only allows listed values.
     *
     * @param title        popup title
     * @param parentScreen screen to return to
     * @param entries      selectable entries
     * @param onSelect     called with the selected value after returning to the parent
     */
    public SearchableListPopup(Component title, Screen parentScreen, List<Entry<T>> entries, Consumer<T> onSelect) {
        this(title, parentScreen, entries, onSelect, null);
    }

    /**
     * Creates a popup that also accepts free text.
     *
     * @param title              popup title
     * @param parentScreen       screen to return to
     * @param entries            selectable entries
     * @param onSelect           called with the selected value after returning to the parent
     * @param customEntryFactory turns typed text into a value, {@code null} to disallow free text
     */
    public SearchableListPopup(Component title, Screen parentScreen, List<Entry<T>> entries, Consumer<T> onSelect,
                               Function<String, T> customEntryFactory) {
        super(title);
        this.parentScreen = parentScreen;
        this.allEntries = new ArrayList<>(entries);
        this.filteredEntries = new ArrayList<>(entries);
        this.onSelect = onSelect;
        this.customEntryFactory = customEntryFactory;
    }

    @Override
    protected void init() {
        popupX = (this.width - POPUP_WIDTH) / 2;
        popupY = (this.height - POPUP_HEIGHT) / 2;

        int searchY = popupY + PADDING + 12;
        searchBox = new EditBox(this.font, popupX + PADDING, searchY, POPUP_WIDTH - 2 * PADDING, SEARCH_HEIGHT,
                Component.translatable("gui.setweaver.search"));
        searchBox.setMaxLength(100);
        searchBox.setHint(Component.translatable("gui.setweaver.search.hint").withStyle(ChatFormatting.GRAY));
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        listX = popupX + PADDING;
        listY = searchY + SEARCH_HEIGHT + 4;
        listW = POPUP_WIDTH - 2 * PADDING;
        listH = POPUP_HEIGHT - PADDING - 12 - SEARCH_HEIGHT - 4 - 24 - PADDING;
        maxVisibleItems = listH / ITEM_HEIGHT;

        int btnW = 80;
        int btnY = popupY + POPUP_HEIGHT - PADDING - 20;
        int btnStartX = popupX + (POPUP_WIDTH - (btnW * 2 + 12)) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.select"), btn -> onConfirm())
                .bounds(btnStartX, btnY, btnW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onCancel())
                .bounds(btnStartX + btnW + 12, btnY, btnW, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, GuiUtil.OVERLAY_DIM);
        GuiUtil.drawPanel(graphics, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                popupX + PADDING, popupY + PADDING, GuiUtil.COLOR_TEXT, false);
        String count = filteredEntries.size() + "/" + allEntries.size();
        graphics.text(this.font, count, popupX + POPUP_WIDTH - PADDING - this.font.width(count),
                popupY + PADDING + 1, GuiUtil.COLOR_GRAY, false);
        renderList(graphics, mouseX, mouseY);
    }

    private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);
        if (filteredEntries.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.setweaver.no_results").withStyle(ChatFormatting.ITALIC),
                    listX + 3, listY + 3, GuiUtil.COLOR_GRAY, false);
        }
        int rowW = listW - SCROLLBAR_W - 2;
        int end = Math.min(filteredEntries.size(), scrollOffset + maxVisibleItems);
        for (int i = scrollOffset; i < end; i++) {
            int y = listY + (i - scrollOffset) * ITEM_HEIGHT;
            if (i == selectedIndex) {
                graphics.fill(listX, y, listX + rowW, y + ITEM_HEIGHT, GuiUtil.COLOR_SELECTED);
            } else if (GuiUtil.inside(mouseX, mouseY, listX, y, rowW, ITEM_HEIGHT)) {
                graphics.fill(listX, y, listX + rowW, y + ITEM_HEIGHT, GuiUtil.COLOR_HOVER);
            }
            String text = GuiUtil.truncate(this.font, filteredEntries.get(i).displayName().getString(), rowW - 6);
            graphics.text(this.font, text, listX + 3, y + 3, GuiUtil.COLOR_TEXT, false);
        }
        graphics.disableScissor();
        GuiUtil.drawScrollbar(graphics, listX + listW - SCROLLBAR_W, listY, listH, filteredEntries.size(), maxVisibleItems, scrollOffset);
    }

    private void onSearchChanged(String filter) {
        String lower = filter.toLowerCase(Locale.ROOT).trim();
        filteredEntries.clear();
        for (Entry<T> entry : allEntries) {
            if (lower.isEmpty() || entry.searchableText().contains(lower)) {
                filteredEntries.add(entry);
            }
        }
        // Offer the typed text as a new value when free text is allowed and nothing matches exactly
        String trimmed = filter.trim();
        if (customEntryFactory != null && !trimmed.isEmpty()
                && allEntries.stream().noneMatch(e -> e.searchableText().equals(lower))) {
            T value = customEntryFactory.apply(trimmed);
            if (value != null) {
                filteredEntries.addFirst(new Entry<>(value,
                        Component.translatable("gui.setweaver.use_custom", trimmed).withStyle(ChatFormatting.YELLOW), trimmed));
            }
        }
        scrollOffset = 0;
        selectedIndex = -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (GuiUtil.inside(mouseX, mouseY, listX, listY, listW - SCROLLBAR_W - 2, listH)) {
            int index = (int) ((mouseY - listY) / ITEM_HEIGHT) + scrollOffset;
            if (index >= 0 && index < filteredEntries.size()) {
                boolean sameRow = index == selectedIndex;
                selectedIndex = index;
                if (doubleClick && sameRow) {
                    onConfirm();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (GuiUtil.inside(mouseX, mouseY, listX, listY, listW, listH)) {
            int max = Math.max(0, filteredEntries.size() - maxVisibleItems);
            scrollOffset = Math.clamp(scrollOffset + (scrollY > 0 ? -1 : 1), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onCancel();
        } else if (event.isConfirmation()) {
            onConfirm();
        } else if (event.isUp()) {
            moveSelection(-1);
        } else if (event.isDown()) {
            moveSelection(1);
        } else {
            return super.keyPressed(event);
        }
        return true;
    }

    private void moveSelection(int direction) {
        if (filteredEntries.isEmpty()) {
            return;
        }
        selectedIndex = selectedIndex < 0 && direction < 0
                ? filteredEntries.size() - 1
                : Math.floorMod(selectedIndex + direction, filteredEntries.size());
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + maxVisibleItems) {
            scrollOffset = selectedIndex - maxVisibleItems + 1;
        }
    }

    private void onConfirm() {
        if (selectedIndex >= 0 && selectedIndex < filteredEntries.size()) {
            T selected = filteredEntries.get(selectedIndex).value();
            this.minecraft.gui.setScreen(parentScreen);
            onSelect.accept(selected);
        }
    }

    private void onCancel() {
        this.minecraft.gui.setScreen(parentScreen);
    }

    @Override
    public void onClose() {
        onCancel();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
