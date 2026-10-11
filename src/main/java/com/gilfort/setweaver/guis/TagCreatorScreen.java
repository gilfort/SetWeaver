package com.gilfort.setweaver.guis;

import com.gilfort.setweaver.network.SaveTagPayload;
import com.google.gson.JsonArray;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-game editor for creating custom item tags in the SetWeaver datapack.
 *
 * <p>Creates tags at: {@code <world>/datapacks/setweaver_custom/data/<namespace>/tags/item/<name>.json}</p>
 * <p>After saving, player must run {@code /reload} to make the tag available.</p>
 */
@OnlyIn(Dist.CLIENT)
public class TagCreatorScreen extends Screen {

    // ─── Panel colors (consistent with other SetWeaver screens) ──────────
    private static final int PANEL_BG      = 0xFFF5F0E0;
    private static final int PANEL_BORDER  = 0xFF8B7355;
    private static final int PANEL_DIVIDER = 0xFFB8A080;
    private static final int PANEL_PADDING = 10;
    private static final int PANEL_MARGIN  = 12;
    private static final int DIVIDER_WIDTH = 4;

    private static final int COLOR_HEADER  = 0xFF000000;
    private static final int COLOR_TEXT    = 0xFF000000;
    private static final int COLOR_GRAY    = 0xFF444444;
    private static final int COLOR_HOVER   = 0x22FFCC00;

    // ─── Layout ──────────────────────────────────────────────────────────
    private int leftX, leftY, leftW, leftH;
    private int rightX, rightY, rightW, rightH;
    private int lineHeight;

    // ─── State (preserved across screen transitions) ───────────────────
    private final Screen parent;
    private List<String> selectedItems;
    private String savedNamespace = "setweaver";
    private String savedTagName = "";
    private int scrollOffset = 0;
    private boolean stateLoaded = false;

    // ─── Edit fields ─────────────────────────────────────────────────────
    private EditBox namespaceBox;
    private EditBox tagNameBox;

    // ─── Item delete bounds ──────────────────────────────────────────────
    private final List<int[]> itemDeleteBounds = new ArrayList<>();

    /**
     * Create a new tag from scratch.
     */
    public TagCreatorScreen(Screen parent) {
        super(Component.literal("Create Custom Tag"));
        this.parent = parent;
    }

    /**
     * Edit an existing tag (pre-filled with namespace, name, and items).
     */
    public TagCreatorScreen(Screen parent, String namespace, String tagName, List<String> existingItems) {
        super(Component.literal("Edit Custom Tag"));
        this.parent = parent;
        this.savedNamespace = namespace;
        this.savedTagName = tagName;
        this.selectedItems = new ArrayList<>(existingItems);
        this.stateLoaded = true;
    }

    // ─── Init ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        if (!stateLoaded) {
            selectedItems = new ArrayList<>();
            stateLoaded = true;
        }

        int margin = PANEL_MARGIN;
        int totalW = this.width - 2 * margin;
        int contentH = this.height - 2 * margin - 28;

        int leftPanelW = (int) (totalW * 0.35);
        int rightPanelW = totalW - leftPanelW - DIVIDER_WIDTH;

        leftX = margin + PANEL_PADDING;
        leftY = margin + PANEL_PADDING;
        leftW = leftPanelW - 2 * PANEL_PADDING;
        leftH = contentH - 2 * PANEL_PADDING;

        rightX = margin + leftPanelW + DIVIDER_WIDTH + PANEL_PADDING;
        rightY = margin + PANEL_PADDING;
        rightW = rightPanelW - 2 * PANEL_PADDING;
        rightH = contentH - 2 * PANEL_PADDING;

        lineHeight = Math.max(10, this.height / 40);
        int rowSpacing = (int) (lineHeight * 1.8);

        // ── Left panel: Tag definition ───────────────────────────────────
        // Save current values before recreating widgets (screen resize / popup return)
        if (namespaceBox != null) savedNamespace = namespaceBox.getValue();
        if (tagNameBox != null) savedTagName = tagNameBox.getValue();

        namespaceBox = new EditBox(this.font, leftX + 75, leftY + rowSpacing, leftW - 80, 14,
                Component.literal("Namespace"));
        namespaceBox.setMaxLength(50);
        namespaceBox.setValue(savedNamespace);
        addRenderableWidget(namespaceBox);

        tagNameBox = new EditBox(this.font, leftX + 75, leftY + rowSpacing * 2, leftW - 80, 14,
                Component.literal("Tag name"));
        tagNameBox.setMaxLength(100);
        tagNameBox.setValue(savedTagName);
        addRenderableWidget(tagNameBox);

        // ── Bottom buttons ───────────────────────────────────────────────
        int buttonW = 70;
        int buttonH = 20;
        int buttonY = margin + contentH + 4;
        int buttonSpacing = 8;
        int totalButtonsW = buttonW * 3 + buttonSpacing * 2;
        int buttonStartX = (this.width - totalButtonsW) / 2;

        addRenderableWidget(Button.builder(Component.literal("+ Item"), btn -> onAddItem())
                .bounds(buttonStartX, buttonY, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(Component.literal("Save Tag"), btn -> onSave())
                .bounds(buttonStartX + (buttonW + buttonSpacing), buttonY, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onBack())
                .bounds(buttonStartX + (buttonW + buttonSpacing) * 2, buttonY, buttonW, buttonH).build());
    }

    // ─── Background ──────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int margin = PANEL_MARGIN;
        int totalW = this.width - 2 * margin;
        int contentH = this.height - 2 * margin - 28;
        int leftPanelW = (int) (totalW * 0.35);
        int rightPanelW = totalW - leftPanelW - DIVIDER_WIDTH;

        // Left panel
        graphics.fill(margin, margin, margin + leftPanelW, margin + contentH, PANEL_BG);
        graphics.renderOutline(margin, margin, leftPanelW, contentH, PANEL_BORDER);

        // Divider
        int divX = margin + leftPanelW;
        graphics.fill(divX, margin, divX + DIVIDER_WIDTH, margin + contentH, PANEL_DIVIDER);

        // Right panel
        int rpX = divX + DIVIDER_WIDTH;
        graphics.fill(rpX, margin, rpX + rightPanelW, margin + contentH, PANEL_BG);
        graphics.renderOutline(rpX, margin, rightPanelW, contentH, PANEL_BORDER);
    }

    // ─── Rendering ───────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderLeftPanel(graphics, mouseX, mouseY);
        renderRightPanel(graphics, mouseX, mouseY);
    }

    private void renderLeftPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(leftX, leftY, leftX + leftW, leftY + leftH);

        int rowSpacing = (int) (lineHeight * 1.8);

        // Header
        graphics.drawString(this.font,
                Component.literal("Tag Definition").withStyle(s -> s.withBold(true)),
                leftX + 3, leftY, COLOR_HEADER, false);

        // Namespace label
        graphics.drawString(this.font, "Namespace:", leftX + 3, leftY + rowSpacing + 3, COLOR_TEXT, false);

        // Tag name label
        graphics.drawString(this.font, "Tag Name:", leftX + 3, leftY + rowSpacing * 2 + 3, COLOR_TEXT, false);

        // Preview
        String ns = namespaceBox != null ? namespaceBox.getValue().trim() : "";
        String name = tagNameBox != null ? tagNameBox.getValue().trim() : "";
        int previewY = leftY + rowSpacing * 3 + 6;
        graphics.drawString(this.font, "Full Tag ID:", leftX + 3, previewY, COLOR_TEXT, false);
        String fullId = ns.isEmpty() || name.isEmpty() ? "(incomplete)" : ns + ":" + name;
        graphics.drawString(this.font, fullId,
                leftX + 3, previewY + lineHeight, ns.isEmpty() || name.isEmpty() ? COLOR_GRAY : 0xFF006600, false);

        // Info
        int infoY = previewY + lineHeight * 3;
        graphics.drawString(this.font,
                Component.literal("After saving, run").withStyle(ChatFormatting.ITALIC),
                leftX + 3, infoY, COLOR_GRAY, false);
        graphics.drawString(this.font,
                Component.literal("/reload").withStyle(ChatFormatting.GOLD),
                leftX + 3, infoY + lineHeight, COLOR_TEXT, false);
        graphics.drawString(this.font,
                Component.literal("to apply the tag.").withStyle(ChatFormatting.ITALIC),
                leftX + 3, infoY + lineHeight * 2, COLOR_GRAY, false);

        // Item count
        int countY = infoY + lineHeight * 4;
        graphics.drawString(this.font, "Items: " + selectedItems.size(),
                leftX + 3, countY, COLOR_TEXT, false);

        graphics.disableScissor();
    }

    private void renderRightPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(rightX, rightY, rightX + rightW, rightY + rightH);
        itemDeleteBounds.clear();

        // Header
        graphics.drawString(this.font,
                Component.literal("Items in Tag").withStyle(s -> s.withBold(true)),
                rightX + 3, rightY, COLOR_HEADER, false);

        if (selectedItems.isEmpty()) {
            graphics.drawString(this.font,
                    Component.literal("(no items — use + Item to add)").withStyle(ChatFormatting.ITALIC),
                    rightX + 8, rightY + lineHeight + 3, COLOR_GRAY, false);
            graphics.disableScissor();
            return;
        }

        int startY = rightY + lineHeight + 3;
        int maxLines = (rightH - lineHeight - 3) / lineHeight;
        int visEnd = Math.min(selectedItems.size(), scrollOffset + maxLines);

        for (int i = scrollOffset; i < visEnd; i++) {
            int entryY = startY + (i - scrollOffset) * lineHeight;
            String itemId = selectedItems.get(i);

            // Hover highlight
            if (mouseX >= rightX && mouseX <= rightX + rightW
                    && mouseY >= entryY && mouseY < entryY + lineHeight) {
                graphics.fill(rightX, entryY - 1, rightX + rightW, entryY + lineHeight - 1, COLOR_HOVER);
            }

            // Item icon
            try {
                ResourceLocation loc = ResourceLocation.parse(itemId);
                Item item = BuiltInRegistries.ITEM.get(loc);
                if (item != null) {
                    graphics.renderItem(new ItemStack(item), rightX + 3, entryY - 2);
                }
            } catch (Exception ignored) {}

            // Item name + ID
            String displayName = resolveItemName(itemId);
            graphics.drawString(this.font, displayName, rightX + 22, entryY, COLOR_TEXT, false);

            // Delete ✕
            int delX = rightX + rightW - 12;
            boolean delHover = mouseX >= delX && mouseX < delX + 10
                    && mouseY >= entryY && mouseY < entryY + 10;
            graphics.drawString(this.font, "\u2716", delX, entryY,
                    delHover ? 0xFFFF0000 : 0xFF773333, false);
            itemDeleteBounds.add(new int[]{delX, entryY, 10, 10, i});
        }

        // Scrollbar
        if (selectedItems.size() > maxLines) {
            int scrollBarX = rightX + rightW - 3;
            float ratio = (float) scrollOffset / (selectedItems.size() - maxLines);
            int thumbHeight = Math.max(10, rightH * maxLines / selectedItems.size());
            int thumbY = rightY + (int) ((rightH - thumbHeight) * ratio);
            graphics.fill(scrollBarX, rightY, scrollBarX + 2, rightY + rightH, 0x33000000);
            graphics.fill(scrollBarX, thumbY, scrollBarX + 2, thumbY + thumbHeight, 0xAA553300);
        }

        graphics.disableScissor();
    }

    // ─── Input ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // Item delete
        for (int[] bounds : itemDeleteBounds) {
            if (mouseX >= bounds[0] && mouseX < bounds[0] + bounds[2]
                    && mouseY >= bounds[1] && mouseY < bounds[1] + bounds[3]) {
                int idx = bounds[4];
                if (idx >= 0 && idx < selectedItems.size()) {
                    selectedItems.remove(idx);
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= rightX && mouseX <= rightX + rightW) {
            int maxLines = (rightH - lineHeight - 3) / lineHeight;
            if (scrollY > 0) scrollOffset = Math.max(0, scrollOffset - 1);
            else scrollOffset = Math.min(Math.max(0, selectedItems.size() - maxLines), scrollOffset + 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (namespaceBox.isFocused() || tagNameBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 256) { // ESC
            onBack();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ─── Actions ─────────────────────────────────────────────────────────

    private void onAddItem() {
        // Build searchable list of armor items only
        List<SearchableListPopup.Entry<String>> entries =
                BuiltInRegistries.ITEM.keySet().stream()
                        .filter(loc -> {
                            Item item = BuiltInRegistries.ITEM.get(loc);
                            return item instanceof net.minecraft.world.item.ArmorItem;
                        })
                        .sorted(Comparator.comparing(ResourceLocation::toString))
                        .map(loc -> {
                            String id = loc.toString();
                            Item item = BuiltInRegistries.ITEM.get(loc);
                            String displayName = item != null
                                    ? new ItemStack(item).getHoverName().getString()
                                    : id;
                            return new SearchableListPopup.Entry<>(
                                    id,
                                    Component.literal(displayName + " (" + id + ")"),
                                    displayName + " " + id);
                        })
                        .collect(Collectors.toList());

        assert this.minecraft != null;
        this.minecraft.setScreen(new SearchableListPopup<>(
                Component.literal("Add Item to Tag"),
                this, entries,
                selected -> {
                    if (!selectedItems.contains(selected)) {
                        selectedItems.add(selected);
                    }
                }
        ));
    }

    private void onSave() {
        String namespace = namespaceBox.getValue().trim().toLowerCase();
        String tagName = tagNameBox.getValue().trim().toLowerCase();

        if (namespace.isEmpty() || tagName.isEmpty()) {
            assert this.minecraft != null && this.minecraft.player != null;
            this.minecraft.player.sendSystemMessage(
                    Component.literal("[SetWeaver] Namespace and tag name must not be empty.")
                            .withStyle(ChatFormatting.RED));
            return;
        }

        if (selectedItems.isEmpty()) {
            assert this.minecraft != null && this.minecraft.player != null;
            this.minecraft.player.sendSystemMessage(
                    Component.literal("[SetWeaver] Add at least one item to the tag.")
                            .withStyle(ChatFormatting.RED));
            return;
        }

        // Build items JSON array
        JsonArray itemsArray = new JsonArray();
        for (String id : selectedItems) {
            itemsArray.add(id);
        }

        // Send to server
        PacketDistributor.sendToServer(new SaveTagPayload(namespace, tagName, itemsArray.toString()));
    }

    private void onBack() {
        assert this.minecraft != null;
        this.minecraft.setScreen(parent);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private String resolveItemName(String itemId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(loc);
            if (item != null) {
                return new ItemStack(item).getHoverName().getString();
            }
        } catch (Exception ignored) {}
        return itemId;
    }
}
