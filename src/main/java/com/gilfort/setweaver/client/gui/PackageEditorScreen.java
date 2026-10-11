package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.network.SavePackagesPayload;
import com.gilfort.setweaver.seteffects.AttributePackageManager;
import com.gilfort.setweaver.seteffects.AttributePackageManager.AttributePackage;
import com.google.gson.Gson;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Editor for attribute packages. Left: package list (toggle and delete icons on the selected one). Right: name,
 * priority and attributes of the selected package. "Save" sends all packages to the server.
 */
public class PackageEditorScreen extends Screen {

    private static final int PANEL_PADDING = 10;
    private static final int PANEL_MARGIN = 12;
    private static final int DIVIDER_WIDTH = 4;
    private static final int COLOR_ENABLED = 0xFF338833;
    private static final int COLOR_DISABLED = 0xFF883333;
    private static final Gson GSON = new Gson();

    private final Screen parent;
    private final List<AttributePackage> packages = new ArrayList<>();
    private int selectedIndex = -1;
    private int leftScroll = 0;
    private int rightScroll = 0;
    private boolean dirty = false;

    private int leftX, leftY, leftW, leftH;
    private int rightX, rightY, rightW, rightH;
    private int lineHeight, rowSpacing, maxLinesLeft;

    private EditBox nameBox;
    private EditBox priorityBox;
    private int[] toggleBounds;
    private int[] deleteBounds;
    private final List<int[]> attrDeleteBounds = new ArrayList<>();

    /**
     * @param parent screen to return to
     */
    public PackageEditorScreen(Screen parent) {
        super(Component.translatable("gui.setweaver.packages.title"));
        this.parent = parent;
        loadFromClient();
    }

    private void loadFromClient() {
        packages.clear();
        AttributePackageManager.client().getAllPackages().forEach(p -> packages.add(p.copy()));
        selectedIndex = packages.isEmpty() ? -1 : Math.clamp(selectedIndex, 0, packages.size() - 1);
        dirty = false;
    }

    /** Called when the server sent new packages; refreshes the list unless there are unsaved changes. */
    public void onRegistrySynced() {
        if (!dirty) {
            loadFromClient();
            updateEditFields();
        }
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
        maxLinesLeft = leftH / lineHeight;

        nameBox = new EditBox(this.font, rightX + 55, rightY + rowSpacing, rightW - 60, 14, Component.translatable("gui.setweaver.packages.name"));
        nameBox.setMaxLength(50);
        nameBox.setResponder(value -> {
            if (selected() != null && !value.equals(selected().getName())) {
                selected().setName(value);
                dirty = true;
            }
        });
        addRenderableWidget(nameBox);

        priorityBox = new EditBox(this.font, rightX + 55, rightY + rowSpacing * 2, 40, 14, Component.translatable("gui.setweaver.packages.priority"));
        priorityBox.setMaxLength(4);
        priorityBox.setFilter(s -> s.matches("\\d*"));
        priorityBox.setResponder(value -> {
            if (selected() != null && !value.isEmpty() && Integer.parseInt(value) != selected().getPriority()) {
                selected().setPriority(Integer.parseInt(value));
                dirty = true;
            }
        });
        addRenderableWidget(priorityBox);

        int buttonW = 65;
        int buttonY = PANEL_MARGIN + contentH + 4;
        int startX = (this.width - (buttonW * 4 + 8 * 3)) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.packages.add_package"), btn -> addPackage())
                .bounds(startX, buttonY, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.packages.add_attribute"), btn -> addAttribute())
                .bounds(startX + (buttonW + 8), buttonY, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.save"), btn -> save())
                .bounds(startX + (buttonW + 8) * 2, buttonY, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.back"), btn -> onClose())
                .bounds(startX + (buttonW + 8) * 3, buttonY, buttonW, 20).build());

        updateEditFields();
    }

    private AttributePackage selected() {
        return selectedIndex >= 0 && selectedIndex < packages.size() ? packages.get(selectedIndex) : null;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int totalW = this.width - 2 * PANEL_MARGIN;
        int contentH = this.height - 2 * PANEL_MARGIN - 28;
        int leftPanelW = (int) (totalW * 0.35);
        GuiUtil.drawPanel(graphics, PANEL_MARGIN, PANEL_MARGIN, leftPanelW, contentH);
        int divX = PANEL_MARGIN + leftPanelW;
        graphics.fill(divX, PANEL_MARGIN, divX + DIVIDER_WIDTH, PANEL_MARGIN + contentH, GuiUtil.PANEL_DIVIDER);
        GuiUtil.drawPanel(graphics, divX + DIVIDER_WIDTH, PANEL_MARGIN, totalW - leftPanelW - DIVIDER_WIDTH, contentH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderLeftPanel(graphics, mouseX, mouseY);
        renderRightPanel(graphics, mouseX, mouseY);
    }

    private void renderLeftPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        toggleBounds = null;
        deleteBounds = null;
        graphics.enableScissor(leftX, leftY, leftX + leftW, leftY + leftH);
        if (packages.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.setweaver.packages.none").withStyle(ChatFormatting.ITALIC),
                    leftX + 5, leftY + 5, GuiUtil.COLOR_GRAY, false);
        }
        int end = Math.min(packages.size(), leftScroll + maxLinesLeft);
        for (int i = leftScroll; i < end; i++) {
            AttributePackage pkg = packages.get(i);
            int y = leftY + (i - leftScroll) * lineHeight;
            if (i == selectedIndex) {
                graphics.fill(leftX, y - 1, leftX + leftW, y + lineHeight - 1, GuiUtil.COLOR_SELECTED);
            } else if (GuiUtil.inside(mouseX, mouseY, leftX, y, leftW, lineHeight)) {
                graphics.fill(leftX, y - 1, leftX + leftW, y + lineHeight - 1, GuiUtil.COLOR_HOVER);
            }
            String indicator = pkg.isEnabled() ? "● " : "○ ";
            graphics.drawString(this.font, indicator, leftX + 3, y, pkg.isEnabled() ? COLOR_ENABLED : COLOR_DISABLED, false);
            int x = leftX + 3 + this.font.width(indicator);
            String name = GuiUtil.truncate(this.font, pkg.getName() == null || pkg.getName().isBlank()
                    ? GuiUtil.tr("gui.setweaver.packages.unnamed") : pkg.getName(), leftW - 60);
            graphics.drawString(this.font, name, x, y, GuiUtil.COLOR_TEXT, false);
            graphics.drawString(this.font, " [" + pkg.getPriority() + "]", x + this.font.width(name), y, GuiUtil.COLOR_GRAY, false);

            if (i == selectedIndex) {
                int iconX = leftX + leftW - 24;
                toggleBounds = new int[]{iconX, y, 10, 10};
                graphics.drawString(this.font, pkg.isEnabled() ? "○" : "●", iconX, y,
                        GuiUtil.hit(toggleBounds, mouseX, mouseY) ? 0xFF0088CC : GuiUtil.COLOR_GRAY, false);
                deleteBounds = new int[]{iconX + 12, y, 10, 10};
                graphics.drawString(this.font, "✖", iconX + 12, y, GuiUtil.hit(deleteBounds, mouseX, mouseY) ? 0xFFFF0000 : 0xFF773333, false);
            }
        }
        graphics.disableScissor();
        GuiUtil.drawScrollbar(graphics, leftX + leftW - 3, leftY, leftH, packages.size(), maxLinesLeft, leftScroll);
    }

    private void renderRightPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        attrDeleteBounds.clear();
        graphics.enableScissor(rightX, rightY, rightX + rightW, rightY + rightH);
        AttributePackage pkg = selected();
        if (pkg == null) {
            graphics.drawString(this.font, Component.translatable("gui.setweaver.packages.select").withStyle(ChatFormatting.ITALIC),
                    rightX + 5, rightY + 5, GuiUtil.COLOR_GRAY, false);
            graphics.disableScissor();
            return;
        }
        graphics.drawString(this.font, Component.translatable("gui.setweaver.packages.edit").withStyle(ChatFormatting.BOLD),
                rightX + 3, rightY, GuiUtil.COLOR_TEXT, false);
        graphics.drawString(this.font, GuiUtil.tr("gui.setweaver.packages.name"), rightX + 3, rightY + rowSpacing + 3, GuiUtil.COLOR_TEXT, false);
        graphics.drawString(this.font, GuiUtil.tr("gui.setweaver.packages.priority"), rightX + 3, rightY + rowSpacing * 2 + 3, GuiUtil.COLOR_TEXT, false);
        graphics.drawString(this.font, GuiUtil.tr("gui.setweaver.packages.priority_hint"), rightX + 100, rightY + rowSpacing * 2 + 3, GuiUtil.COLOR_GRAY, false);
        String statusLabel = GuiUtil.tr("gui.setweaver.packages.status") + " ";
        graphics.drawString(this.font, statusLabel, rightX + 3, rightY + rowSpacing * 3 + 3, GuiUtil.COLOR_TEXT, false);
        graphics.drawString(this.font, GuiUtil.tr(pkg.isEnabled() ? "gui.setweaver.packages.enabled" : "gui.setweaver.packages.disabled"),
                rightX + 3 + this.font.width(statusLabel), rightY + rowSpacing * 3 + 3, pkg.isEnabled() ? COLOR_ENABLED : COLOR_DISABLED, false);

        int headerY = rightY + rowSpacing * 4;
        graphics.drawString(this.font, Component.translatable("gui.setweaver.packages.attributes").withStyle(ChatFormatting.BOLD),
                rightX + 3, headerY, GuiUtil.COLOR_TEXT, false);
        List<String> attributes = pkg.getAttributes();
        int startY = headerY + lineHeight;
        int maxLines = Math.max(1, (rightY + rightH - startY) / lineHeight);
        rightScroll = Math.clamp(rightScroll, 0, Math.max(0, attributes.size() - maxLines));
        if (attributes.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.setweaver.packages.no_attributes").withStyle(ChatFormatting.ITALIC),
                    rightX + 8, startY, GuiUtil.COLOR_GRAY, false);
        }
        for (int i = rightScroll; i < attributes.size() && i - rightScroll < maxLines; i++) {
            int y = startY + (i - rightScroll) * lineHeight;
            String id = attributes.get(i);
            String name = "▸ " + AttributePreviewHelper.attributeName(id) + " ";
            graphics.drawString(this.font, name, rightX + 8, y, GuiUtil.COLOR_TEXT, false);
            graphics.drawString(this.font, GuiUtil.truncate(this.font, "(" + id + ")", rightW - this.font.width(name) - 30),
                    rightX + 8 + this.font.width(name), y, GuiUtil.COLOR_GRAY, false);
            int[] bounds = {rightX + rightW - 12, y, 10, 10, i};
            graphics.drawString(this.font, "✖", bounds[0], y, GuiUtil.hit(bounds, mouseX, mouseY) ? 0xFFFF0000 : 0xFF773333, false);
            attrDeleteBounds.add(bounds);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        AttributePackage pkg = selected();
        if (pkg != null && GuiUtil.hit(toggleBounds, mouseX, mouseY)) {
            pkg.setEnabled(!pkg.isEnabled());
            dirty = true;
            return true;
        }
        if (pkg != null && GuiUtil.hit(deleteBounds, mouseX, mouseY)) {
            packages.remove(selectedIndex);
            selectedIndex = Math.min(selectedIndex, packages.size() - 1);
            updateEditFields();
            dirty = true;
            return true;
        }
        for (int[] bounds : attrDeleteBounds) {
            if (pkg != null && GuiUtil.hit(bounds, mouseX, mouseY)) {
                List<String> attributes = new ArrayList<>(pkg.getAttributes());
                attributes.remove(bounds[4]);
                pkg.setAttributes(attributes);
                dirty = true;
                return true;
            }
        }
        if (GuiUtil.inside(mouseX, mouseY, leftX, leftY, leftW, leftH)) {
            int line = (int) ((mouseY - leftY) / lineHeight) + leftScroll;
            if (line < packages.size()) {
                selectedIndex = line;
                rightScroll = 0;
                updateEditFields();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : 1;
        if (GuiUtil.inside(mouseX, mouseY, leftX, leftY, leftW, leftH)) {
            leftScroll = Math.clamp(leftScroll + step, 0, Math.max(0, packages.size() - maxLinesLeft));
            return true;
        }
        if (GuiUtil.inside(mouseX, mouseY, rightX, rightY, rightW, rightH)) {
            rightScroll = Math.max(0, rightScroll + step);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void addPackage() {
        AttributePackage pkg = new AttributePackage();
        pkg.setName(GuiUtil.tr("gui.setweaver.packages.new_name"));
        packages.add(pkg);
        selectedIndex = packages.size() - 1;
        updateEditFields();
        dirty = true;
    }

    private void addAttribute() {
        AttributePackage pkg = selected();
        if (pkg == null) {
            return;
        }
        List<SearchableListPopup.Entry<String>> entries = new ArrayList<>();
        BuiltInRegistries.ATTRIBUTE.entrySet().forEach(e -> {
            String id = e.getKey().location().toString();
            String name = Component.translatable(e.getValue().getDescriptionId()).getString();
            entries.add(new SearchableListPopup.Entry<>(id, Component.literal(name + " (" + id + ")"), name + " " + id));
        });
        entries.sort(Comparator.comparing(e -> e.displayName().getString()));
        this.minecraft.setScreen(new SearchableListPopup<>(Component.translatable("gui.setweaver.packages.add_attribute"), this, entries, id -> {
            if (!pkg.getAttributes().contains(id)) {
                List<String> attributes = new ArrayList<>(pkg.getAttributes());
                attributes.add(id);
                pkg.setAttributes(attributes);
                dirty = true;
            }
        }));
    }

    private void save() {
        PacketDistributor.sendToServer(new SavePackagesPayload(GSON.toJson(packages)));
        dirty = false;
    }

    private void updateEditFields() {
        if (nameBox == null) {
            return;
        }
        AttributePackage pkg = selected();
        nameBox.setVisible(pkg != null);
        priorityBox.setVisible(pkg != null);
        if (pkg != null) {
            nameBox.setValue(pkg.getName() == null ? "" : pkg.getName());
            priorityBox.setValue(String.valueOf(pkg.getPriority()));
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
