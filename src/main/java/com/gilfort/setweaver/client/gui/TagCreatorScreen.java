package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.network.SaveTagPayload;
import com.gilfort.setweaver.seteffects.ArmorPieces;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Creates or edits an item tag in the world datapack {@code setweaver_custom}. The tag becomes available after
 * {@code /reload}.
 */
public class TagCreatorScreen extends Screen {

    private static final int PANEL_PADDING = 10;
    private static final int PANEL_MARGIN = 12;
    private static final int DIVIDER_WIDTH = 4;

    private final Screen parent;
    private final List<String> selectedItems;
    private String namespace = SetWeaver.MODID;
    private String tagName = "";
    private int scrollOffset = 0;
    private Component status;
    private boolean statusIsError;

    private int leftX, leftY, leftW, leftH;
    private int rightX, rightY, rightW, rightH;
    private int lineHeight, rowSpacing;
    private EditBox namespaceBox;
    private EditBox tagNameBox;
    private final List<int[]> itemDeleteBounds = new ArrayList<>();

    /**
     * Creates a new tag.
     *
     * @param parent screen to return to
     */
    public TagCreatorScreen(Screen parent) {
        super(Component.translatable("gui.setweaver.tag_creator.create_title"));
        this.parent = parent;
        this.selectedItems = new ArrayList<>();
    }

    /**
     * Edits an existing tag.
     *
     * @param parent        screen to return to
     * @param namespace     tag namespace
     * @param tagName       tag path
     * @param existingItems item ids currently in the tag
     */
    public TagCreatorScreen(Screen parent, String namespace, String tagName, List<String> existingItems) {
        super(Component.translatable("gui.setweaver.tag_creator.edit_title"));
        this.parent = parent;
        this.namespace = namespace;
        this.tagName = tagName;
        this.selectedItems = new ArrayList<>(existingItems);
    }

    @Override
    protected void init() {
        int totalW = this.width - 2 * PANEL_MARGIN;
        int contentH = this.height - 2 * PANEL_MARGIN - 28;
        int leftPanelW = (int) (totalW * 0.35);
        int rightPanelW = totalW - leftPanelW - DIVIDER_WIDTH;
        leftX = PANEL_MARGIN + PANEL_PADDING;
        leftY = PANEL_MARGIN + PANEL_PADDING;
        leftW = leftPanelW - 2 * PANEL_PADDING;
        leftH = contentH - 2 * PANEL_PADDING;
        rightX = PANEL_MARGIN + leftPanelW + DIVIDER_WIDTH + PANEL_PADDING;
        rightY = leftY;
        rightW = rightPanelW - 2 * PANEL_PADDING;
        rightH = leftH;
        lineHeight = Math.max(10, this.height / 40);
        rowSpacing = (int) (lineHeight * 1.8);

        namespaceBox = new EditBox(this.font, leftX + 75, leftY + rowSpacing, leftW - 80, 14, Component.translatable("gui.setweaver.tag_creator.namespace"));
        namespaceBox.setMaxLength(50);
        namespaceBox.setValue(namespace);
        namespaceBox.setResponder(v -> namespace = v);
        addRenderableWidget(namespaceBox);

        tagNameBox = new EditBox(this.font, leftX + 75, leftY + rowSpacing * 2, leftW - 80, 14, Component.translatable("gui.setweaver.tag_creator.tag_name"));
        tagNameBox.setMaxLength(100);
        tagNameBox.setValue(tagName);
        tagNameBox.setResponder(v -> tagName = v);
        addRenderableWidget(tagNameBox);

        int buttonW = 70;
        int buttonY = PANEL_MARGIN + contentH + 4;
        int startX = (this.width - (buttonW * 3 + 16)) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.tag_creator.add_item"), btn -> addItem())
                .bounds(startX, buttonY, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.tag_creator.save"), btn -> save())
                .bounds(startX + buttonW + 8, buttonY, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.back"), btn -> onClose())
                .bounds(startX + (buttonW + 8) * 2, buttonY, buttonW, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int totalW = this.width - 2 * PANEL_MARGIN;
        int contentH = this.height - 2 * PANEL_MARGIN - 28;
        int leftPanelW = (int) (totalW * 0.35);
        GuiUtil.drawPanel(graphics, PANEL_MARGIN, PANEL_MARGIN, leftPanelW, contentH);
        int divX = PANEL_MARGIN + leftPanelW;
        graphics.fill(divX, PANEL_MARGIN, divX + DIVIDER_WIDTH, PANEL_MARGIN + contentH, GuiUtil.PANEL_DIVIDER);
        GuiUtil.drawPanel(graphics, divX + DIVIDER_WIDTH, PANEL_MARGIN, totalW - leftPanelW - DIVIDER_WIDTH, contentH);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderLeftPanel(graphics);
        renderRightPanel(graphics, mouseX, mouseY);
    }

    private Identifier tagId() {
        return Identifier.tryBuild(namespace.trim().toLowerCase(Locale.ROOT), tagName.trim().toLowerCase(Locale.ROOT));
    }

    private void renderLeftPanel(GuiGraphicsExtractor graphics) {
        graphics.enableScissor(leftX, leftY, leftX + leftW, leftY + leftH);
        graphics.text(this.font, Component.translatable("gui.setweaver.tag_creator.definition").withStyle(ChatFormatting.BOLD),
                leftX + 3, leftY, GuiUtil.COLOR_TEXT, false);
        graphics.text(this.font, GuiUtil.tr("gui.setweaver.tag_creator.namespace"), leftX + 3, leftY + rowSpacing + 3, GuiUtil.COLOR_TEXT, false);
        graphics.text(this.font, GuiUtil.tr("gui.setweaver.tag_creator.tag_name"), leftX + 3, leftY + rowSpacing * 2 + 3, GuiUtil.COLOR_TEXT, false);

        int y = leftY + rowSpacing * 3 + 6;
        Identifier id = tagName.isBlank() ? null : tagId();
        graphics.text(this.font, GuiUtil.tr("gui.setweaver.tag_creator.full_id"), leftX + 3, y, GuiUtil.COLOR_TEXT, false);
        graphics.text(this.font, id == null ? GuiUtil.tr("gui.setweaver.tag_creator.incomplete") : "#" + id,
                leftX + 3, y + lineHeight, id == null ? GuiUtil.COLOR_GRAY : 0xFF006600, false);

        y += lineHeight * 3;
        for (String line : GuiUtil.wrap(this.font, GuiUtil.tr("gui.setweaver.tag_creator.reload_hint"), leftW - 6)) {
            graphics.text(this.font, Component.literal(line).withStyle(ChatFormatting.ITALIC), leftX + 3, y, GuiUtil.COLOR_GRAY, false);
            y += lineHeight;
        }
        y += lineHeight;
        graphics.text(this.font, GuiUtil.tr("gui.setweaver.tag_creator.item_count", selectedItems.size()), leftX + 3, y, GuiUtil.COLOR_TEXT, false);
        if (status != null) {
            y += lineHeight * 2;
            for (String line : GuiUtil.wrap(this.font, status.getString(), leftW - 6)) {
                graphics.text(this.font, line, leftX + 3, y, statusIsError ? GuiUtil.COLOR_ERROR : GuiUtil.COLOR_SUCCESS, false);
                y += lineHeight;
            }
        }
        graphics.disableScissor();
    }

    private void renderRightPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        itemDeleteBounds.clear();
        graphics.enableScissor(rightX, rightY, rightX + rightW, rightY + rightH);
        graphics.text(this.font, Component.translatable("gui.setweaver.tag_creator.items").withStyle(ChatFormatting.BOLD),
                rightX + 3, rightY, GuiUtil.COLOR_TEXT, false);
        int startY = rightY + lineHeight + 3;
        int maxLines = maxLines();
        if (selectedItems.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.setweaver.tag_creator.no_items").withStyle(ChatFormatting.ITALIC),
                    rightX + 8, startY, GuiUtil.COLOR_GRAY, false);
        }
        int end = Math.min(selectedItems.size(), scrollOffset + maxLines);
        for (int i = scrollOffset; i < end; i++) {
            int y = startY + (i - scrollOffset) * lineHeight;
            String itemId = selectedItems.get(i);
            if (GuiUtil.inside(mouseX, mouseY, rightX, y, rightW, lineHeight)) {
                graphics.fill(rightX, y - 1, rightX + rightW, y + lineHeight - 1, GuiUtil.COLOR_HOVER);
            }
            Item item = item(itemId);
            if (item != null) {
                graphics.item(new ItemStack(item), rightX + 3, y - 2);
            }
            String name = item != null ? new ItemStack(item).getHoverName().getString() : itemId;
            graphics.text(this.font, GuiUtil.truncate(this.font, name, rightW - 40), rightX + 22, y, GuiUtil.COLOR_TEXT, false);
            int[] bounds = {rightX + rightW - 12, y, 10, 10, i};
            graphics.text(this.font, "✖", bounds[0], y, GuiUtil.hit(bounds, mouseX, mouseY) ? 0xFFFF0000 : 0xFF773333, false);
            itemDeleteBounds.add(bounds);
        }
        graphics.disableScissor();
        GuiUtil.drawScrollbar(graphics, rightX + rightW - 3, rightY, rightH, selectedItems.size(), maxLines, scrollOffset);
    }

    private int maxLines() {
        return Math.max(1, (rightH - lineHeight - 3) / lineHeight);
    }

    private static Item item(String id) {
        Identifier loc = Identifier.tryParse(id);
        return loc != null && BuiltInRegistries.ITEM.containsKey(loc) ? BuiltInRegistries.ITEM.getValue(loc) : null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        for (int[] bounds : itemDeleteBounds) {
            if (GuiUtil.hit(bounds, mouseX, mouseY)) {
                selectedItems.remove(bounds[4]);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (GuiUtil.inside(mouseX, mouseY, rightX, rightY, rightW, rightH)) {
            scrollOffset = Math.clamp(scrollOffset + (scrollY > 0 ? -1 : 1), 0, Math.max(0, selectedItems.size() - maxLines()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void addItem() {
        List<SearchableListPopup.Entry<String>> entries = new ArrayList<>();
        BuiltInRegistries.ITEM.entrySet().forEach(e -> {
            if (ArmorPieces.isArmor(e.getValue())) {
                String id = e.getKey().identifier().toString();
                String name = new ItemStack(e.getValue()).getHoverName().getString();
                entries.add(new SearchableListPopup.Entry<>(id, Component.literal(name + " (" + id + ")"), name + " " + id));
            }
        });
        entries.sort(Comparator.comparing(SearchableListPopup.Entry::searchableText));
        this.minecraft.gui.setScreen(new SearchableListPopup<>(Component.translatable("gui.setweaver.tag_creator.add_item_title"), this, entries,
                id -> {
                    if (!selectedItems.contains(id)) {
                        selectedItems.add(id);
                    }
                }));
    }

    private void save() {
        Identifier id = tagName.isBlank() ? null : tagId();
        if (id == null) {
            setStatus(Component.translatable("gui.setweaver.tag_creator.error.invalid_id"), true);
            return;
        }
        if (selectedItems.isEmpty()) {
            setStatus(Component.translatable("gui.setweaver.tag_creator.error.no_items"), true);
            return;
        }
        ClientPacketDistributor.sendToServer(new SaveTagPayload(id.getNamespace(), id.getPath(), List.copyOf(selectedItems)));
        setStatus(Component.translatable("gui.setweaver.tag_creator.sent"), false);
    }

    private void setStatus(Component message, boolean isError) {
        status = message;
        statusIsError = isError;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
