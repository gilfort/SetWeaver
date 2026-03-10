package com.gilfort.setweaver.guis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A reusable modal popup overlay for selecting from large lists with search.
 * Used for MobEffect selection, Attribute selection, Copy-source part selection, etc.
 *
 * <p>Renders on top of the parent screen with a dimmed background.
 * Keyboard-navigable: type to filter, Arrow keys to navigate, Enter to select, Escape to cancel.</p>
 *
 * <p>Styled consistently with SetsManagerScreen (Beige/Parchment theme).</p>
 *
 * @param <T> The type of value being selected (e.g. ResourceLocation, String, Integer)
 * @see SetsManagerScreen
 */
@OnlyIn(Dist.CLIENT)
public class SearchableListPopup<T> extends Screen {

    // ──── Color Scheme (consistent with SetsManagerScreen) ──────────────
    private static final int PANEL_BG       = 0xFFF5F0E0; // Beige/Parchment
    private static final int PANEL_BORDER   = 0xFF8B7355; // Warm brown frame
    private static final int COLOR_TEXT     = 0xFF000000;  // Black text
    private static final int COLOR_GRAY     = 0xFF444444;  // Muted text
    private static final int COLOR_SELECTED = 0x44FFCC00;  // Gold highlight
    private static final int COLOR_HOVER    = 0x22FFCC00;  // Gold hover
    private static final int OVERLAY_DIM    = 0x88000000;  // Dimmed background

    // ──── Layout constants ──────────────────────────────────────────────
    private static final int POPUP_WIDTH    = 260;
    private static final int POPUP_HEIGHT   = 280;
    private static final int ITEM_HEIGHT    = 14;
    private static final int SEARCH_HEIGHT  = 16;
    private static final int PADDING        = 8;
    private static final int SCROLLBAR_W    = 4;

    // ──── Data ──────────────────────────────────────────────────────────
    private final Screen parentScreen;
    private final List<Entry<T>> allEntries;
    private final List<Entry<T>> filteredEntries;
    private final Consumer<T> onSelect;
    private final boolean allowCustomEntry;
    private final Component popupTitle;

    // ──── Widgets ───────────────────────────────────────────────────────
    private EditBox searchBox;

    // ──── State ─────────────────────────────────────────────────────────
    private int popupX, popupY;
    private int listX, listY, listW, listH;
    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private int maxVisibleItems;

    // ════════════════════════════════════════════════════════════════════
    //  Entry record — one selectable item in the list
    // ════════════════════════════════════════════════════════════════════

    /**
     * Represents a single selectable entry in the popup list.
     *
     * @param <T> The type of the underlying value
     */
    public record Entry<T>(T value, Component displayName, String searchableText) {

        /**
         * Primary constructor — provide explicit search text.
         */
        public Entry(T value, Component displayName, String searchableText) {
            this.value = value;
            this.displayName = displayName;
            this.searchableText = searchableText.toLowerCase();
        }

        /**
         * Convenience constructor — uses display name as search text.
         */
        public Entry(T value, Component displayName) {
            this(value, displayName, displayName.getString());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Constructor
    // ════════════════════════════════════════════════════════════════════

    /**
     * Creates a new SearchableListPopup.
     *
     * @param popupTitle   Title shown at the top of the popup
     * @param parentScreen The screen to return to on cancel
     * @param entries      All selectable entries
     * @param onSelect     Callback invoked with the selected value
     */
    public SearchableListPopup(Component popupTitle, Screen parentScreen,
                               List<Entry<T>> entries, Consumer<T> onSelect) {
        super(popupTitle);
        this.popupTitle = popupTitle;
        this.parentScreen = parentScreen;
        this.allEntries = new ArrayList<>(entries);
        this.filteredEntries = new ArrayList<>(entries);
        this.onSelect = onSelect;
        this.allowCustomEntry = false;
    }
    /**
     * Creates a SearchableListPopup with optional custom-entry support.
     *
     * @param allowCustomEntry if true, the user can type any text and select it
     *                         as a custom value (only works when T = String)
     */
    public SearchableListPopup(Component popupTitle, Screen parentScreen,
                               List<Entry<T>> entries, Consumer<T> onSelect,
                               boolean allowCustomEntry) {
        super(popupTitle);
        this.popupTitle    = popupTitle;
        this.parentScreen  = parentScreen;
        this.allEntries    = new ArrayList<>(entries);
        this.filteredEntries = new ArrayList<>(entries);
        this.onSelect      = onSelect;
        this.allowCustomEntry = allowCustomEntry;
    }
    // ════════════════════════════════════════════════════════════════════
    //  Init
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();

        // Center popup on screen
        popupX = (this.width - POPUP_WIDTH) / 2;
        popupY = (this.height - POPUP_HEIGHT) / 2;

        // Search box at top of popup (below title)
        int searchY = popupY + PADDING + 12; // 12px for title
        searchBox = new EditBox(this.font,
                popupX + PADDING, searchY, POPUP_WIDTH - 2 * PADDING, SEARCH_HEIGHT,
                Component.literal("Search..."));
        searchBox.setMaxLength(100);
        searchBox.setHint(Component.literal("Type to filter...").withStyle(ChatFormatting.GRAY));
        searchBox.setResponder(this::onSearchChanged);
        searchBox.setFocused(true);
        addRenderableWidget(searchBox);
        setFocused(searchBox);

        // List area
        listX = popupX + PADDING;
        listY = searchY + SEARCH_HEIGHT + 4;
        listW = POPUP_WIDTH - 2 * PADDING;
        listH = POPUP_HEIGHT - PADDING - 12 - SEARCH_HEIGHT - 4 - 24 - PADDING; // minus buttons
        maxVisibleItems = listH / ITEM_HEIGHT;

        // Buttons at bottom
        int btnW = 80;
        int btnH = 20;
        int btnY = popupY + POPUP_HEIGHT - PADDING - btnH;
        int btnSpacing = 12;
        int totalBtnW = btnW * 2 + btnSpacing;
        int btnStartX = popupX + (POPUP_WIDTH - totalBtnW) / 2;

        addRenderableWidget(Button.builder(Component.literal("Select"), btn -> onConfirm())
                .bounds(btnStartX, btnY, btnW, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onCancel())
                .bounds(btnStartX + btnW + btnSpacing, btnY, btnW, btnH).build());
    }

    // ════════════════════════════════════════════════════════════════════
    //  Rendering
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim overlay (nicht super aufrufen — wir wollen keinen Vanilla-BG)
        graphics.fill(0, 0, this.width, this.height, OVERLAY_DIM);
        // Panel background + border
        drawPanel(graphics, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1) super.render() ZUERST — ruft renderBackground() + Widgets auf
        super.render(graphics, mouseX, mouseY, partialTick);

        // 2) Title (NACH super, damit es über dem Panel liegt)
        graphics.drawString(this.font,
                popupTitle.copy().withStyle(s -> s.withBold(true)),
                popupX + PADDING, popupY + PADDING, COLOR_TEXT, false);

        // 3) Render list entries
        renderList(graphics, mouseX, mouseY);

        // 4) Result count
        String countText = filteredEntries.size() + "/" + allEntries.size();
        int countX = popupX + POPUP_WIDTH - PADDING - this.font.width(countText);
        graphics.drawString(this.font, countText, countX, popupY + PADDING + 1, COLOR_GRAY, false);
    }


    /**
     * Renders the scrollable list of filtered entries.
     */
    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);

        if (filteredEntries.isEmpty()) {
            graphics.drawString(this.font,
                    Component.literal("No results found.").withStyle(ChatFormatting.ITALIC),
                    listX + 3, listY + 3, COLOR_GRAY, false);
            graphics.disableScissor();
            return;
        }

        int visibleEnd = Math.min(filteredEntries.size(), scrollOffset + maxVisibleItems);

        for (int i = scrollOffset; i < visibleEnd; i++) {
            int entryY = listY + (i - scrollOffset) * ITEM_HEIGHT;
            Entry<T> entry = filteredEntries.get(i);

            // Selection highlight
            if (i == selectedIndex) {
                graphics.fill(listX, entryY, listX + listW - SCROLLBAR_W - 2, entryY + ITEM_HEIGHT, COLOR_SELECTED);
            }
            // Hover highlight
            else if (mouseX >= listX && mouseX <= listX + listW - SCROLLBAR_W - 2
                    && mouseY >= entryY && mouseY < entryY + ITEM_HEIGHT) {
                graphics.fill(listX, entryY, listX + listW - SCROLLBAR_W - 2, entryY + ITEM_HEIGHT, COLOR_HOVER);
            }

            // Entry text (truncate if needed)
            String text = entry.displayName().getString();
            int maxTextW = listW - SCROLLBAR_W - 8;
            if (this.font.width(text) > maxTextW) {
                while (this.font.width(text + "...") > maxTextW && text.length() > 3) {
                    text = text.substring(0, text.length() - 1);
                }
                text += "...";
            }
            graphics.drawString(this.font, text, listX + 3, entryY + 3, COLOR_TEXT, false);
        }

        // Scrollbar
        if (filteredEntries.size() > maxVisibleItems) {
            int scrollBarX = listX + listW - SCROLLBAR_W;
            float ratio = (float) scrollOffset / Math.max(1, filteredEntries.size() - maxVisibleItems);
            int thumbHeight = Math.max(10, listH * maxVisibleItems / filteredEntries.size());
            int thumbY = listY + (int) ((listH - thumbHeight) * ratio);

            // Track
            graphics.fill(scrollBarX, listY, scrollBarX + SCROLLBAR_W, listY + listH, 0x33000000);
            // Thumb
            graphics.fill(scrollBarX, thumbY, scrollBarX + SCROLLBAR_W, thumbY + thumbHeight, 0xAA553300);
        }

        graphics.disableScissor();
    }

    /**
     * Draws a panel with background and 1px border (matches SetsManagerScreen style).
     */
    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        // Background
        graphics.fill(x, y, x + w, y + h, PANEL_BG);
        // Border (1px warm brown)
        graphics.fill(x,         y,         x + w,     y + 1,     PANEL_BORDER); // top
        graphics.fill(x,         y + h - 1, x + w,     y + h,     PANEL_BORDER); // bottom
        graphics.fill(x,         y,         x + 1,     y + h,     PANEL_BORDER); // left
        graphics.fill(x + w - 1, y,         x + w,     y + h,     PANEL_BORDER); // right
    }

    // ════════════════════════════════════════════════════════════════════
    //  Search filtering
    // ════════════════════════════════════════════════════════════════════

    private void onSearchChanged(String filter) {
        String lower = filter.toLowerCase().trim();
        filteredEntries.clear();

        if (lower.isEmpty()) {
            filteredEntries.addAll(allEntries);
        } else {
            for (Entry<T> entry : allEntries) {
                if (entry.searchableText().contains(lower)) {
                    filteredEntries.add(entry);
                }
            }
        }

        // Custom-entry: show "✎ Use 'xyz' (new)" at top if:
        // - allowCustomEntry is enabled
        // - search text is non-empty
        // - no entry matches EXACTLY (case-insensitive)
        if (allowCustomEntry && !filter.trim().isEmpty()) {
            String trimmed = filter.trim();
            boolean exactMatch = allEntries.stream()
                    .anyMatch(e -> e.searchableText().equalsIgnoreCase(trimmed));
            if (!exactMatch) {
                @SuppressWarnings("unchecked")
                Entry<T> customEntry = (Entry<T>) new Entry<>(
                        trimmed,
                        Component.literal("✎ Use \"" + trimmed + "\" (new)")
                                .withStyle(ChatFormatting.YELLOW),
                        trimmed);
                filteredEntries.addFirst(customEntry);
            }
        }

        scrollOffset = 0;
        selectedIndex = -1;
    }


    // ════════════════════════════════════════════════════════════════════
    //  Input handling
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // Click inside list area
        if (mouseX >= listX && mouseX <= listX + listW - SCROLLBAR_W - 2
                && mouseY >= listY && mouseY <= listY + listH) {
            int clickedIndex = (int) ((mouseY - listY) / ITEM_HEIGHT) + scrollOffset;
            if (clickedIndex >= 0 && clickedIndex < filteredEntries.size()) {
                selectedIndex = clickedIndex;
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll within the list area
        if (mouseX >= listX && mouseX <= listX + listW
                && mouseY >= listY && mouseY <= listY + listH) {
            if (scrollY > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (scrollY < 0) {
                scrollOffset = Math.min(
                        Math.max(0, filteredEntries.size() - maxVisibleItems),
                        scrollOffset + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape → cancel
        if (keyCode == 256) {
            onCancel();
            return true;
        }

        // Enter → confirm selection
        if (keyCode == 257) {
            onConfirm();
            return true;
        }

        // Arrow Up → move selection up
        if (keyCode == 265) {
            if (filteredEntries.isEmpty()) return true;
            if (selectedIndex <= 0) {
                selectedIndex = filteredEntries.size() - 1; // wrap to bottom
            } else {
                selectedIndex--;
            }
            ensureSelectedVisible();
            return true;
        }

        // Arrow Down → move selection down
        if (keyCode == 264) {
            if (filteredEntries.isEmpty()) return true;
            if (selectedIndex >= filteredEntries.size() - 1) {
                selectedIndex = 0; // wrap to top
            } else {
                selectedIndex++;
            }
            ensureSelectedVisible();
            return true;
        }

        // Let the search box handle other keys (typing)
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Ensures the currently selected item is within the visible scroll area.
     */
    private void ensureSelectedVisible() {
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + maxVisibleItems) {
            scrollOffset = selectedIndex - maxVisibleItems + 1;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Actions
    // ════════════════════════════════════════════════════════════════════

    private void onConfirm() {
        if (selectedIndex >= 0 && selectedIndex < filteredEntries.size()) {
            T selected = filteredEntries.get(selectedIndex).value();
            // Return to parent first, then invoke callback
            this.minecraft.setScreen(parentScreen);
            onSelect.accept(selected);
        }
    }

    private void onCancel() {
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Double-click support
    // ════════════════════════════════════════════════════════════════════

    private long lastClickTime = 0;
    private int lastClickIndex = -1;

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Detect double-click on list entries
        if (button == 0 && mouseX >= listX && mouseX <= listX + listW - SCROLLBAR_W - 2
                && mouseY >= listY && mouseY <= listY + listH) {
            int clickedIndex = (int) ((mouseY - listY) / ITEM_HEIGHT) + scrollOffset;
            if (clickedIndex >= 0 && clickedIndex < filteredEntries.size()) {
                long now = System.currentTimeMillis();
                if (clickedIndex == lastClickIndex && (now - lastClickTime) < 400) {
                    // Double-click → confirm immediately
                    selectedIndex = clickedIndex;
                    onConfirm();
                    return true;
                }
                lastClickTime = now;
                lastClickIndex = clickedIndex;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}