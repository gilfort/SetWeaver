package com.gilfort.setweaver.guis;

import com.gilfort.setweaver.seteffects.AttributePackageManager;
import com.gilfort.setweaver.seteffects.AttributePackageManager.AttributePackage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-game editor for attribute packages (config/setweaver/attribute_packages.json).
 *
 * <p>Left panel: scrollable list of packages with inline action icons.
 * Right panel: detail view of the selected package (name, priority, enabled, attributes).</p>
 *
 * <p>When an attribute belongs to multiple packages, the package with the
 * lowest priority value wins for display purposes.</p>
 */
@OnlyIn(Dist.CLIENT)
public class PackageEditorScreen extends Screen {

    // ─── Panel colors (consistent with SetsManagerScreen) ────────────────
    private static final int PANEL_BG      = 0xFFF5F0E0;
    private static final int PANEL_BORDER  = 0xFF8B7355;
    private static final int PANEL_DIVIDER = 0xFFB8A080;
    private static final int PANEL_PADDING = 10;
    private static final int PANEL_MARGIN  = 12;
    private static final int DIVIDER_WIDTH = 4;

    private static final int COLOR_HEADER    = 0xFF000000;
    private static final int COLOR_TEXT      = 0xFF000000;
    private static final int COLOR_GRAY      = 0xFF444444;
    private static final int COLOR_SELECTED  = 0x44FFCC00;
    private static final int COLOR_HOVER     = 0x22FFCC00;
    private static final int COLOR_ENABLED   = 0xFF338833;
    private static final int COLOR_DISABLED  = 0xFF883333;

    // ─── Layout ──────────────────────────────────────────────────────────
    private int leftX, leftY, leftW, leftH;
    private int rightX, rightY, rightW, rightH;
    private int lineHeight;
    private int maxLinesLeft, maxLinesRight;

    // ─── State (preserved across init() calls / screen transitions) ────
    private final Screen parent;
    private List<AttributePackage> packages;
    private int selectedIndex = -1;
    private int leftScrollOffset = 0;
    private int rightScrollOffset = 0;
    private boolean dirty = false;
    private boolean stateLoaded = false;

    // ─── Edit fields ─────────────────────────────────────────────────────
    private EditBox nameBox;
    private EditBox priorityBox;

    // ─── Inline icon bounds ──────────────────────────────────────────────
    private int[] inlineDeleteBounds = null;
    private int[] inlineToggleBounds = null;

    // ─── Attribute action bounds (right panel) ───────────────────────────
    private final List<int[]> attrDeleteBounds = new ArrayList<>();

    public PackageEditorScreen(Screen parent) {
        super(Component.literal("Attribute Package Editor"));
        this.parent = parent;
    }

    // ─── Init ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        // Only load from manager on first init — preserve state across screen transitions
        if (!stateLoaded) {
            packages = new ArrayList<>();
            for (AttributePackage src : AttributePackageManager.getAllPackages()) {
                AttributePackage copy = new AttributePackage();
                copy.setName(src.getName());
                copy.setEnabled(src.isEnabled());
                copy.setPriority(src.getPriority());
                copy.setAttributes(new ArrayList<>(src.getAttributes()));
                packages.add(copy);
            }
            stateLoaded = true;
            if (!packages.isEmpty()) {
                selectedIndex = 0;
            }
        }

        int margin = PANEL_MARGIN;
        int totalW = this.width - 2 * margin;
        int contentH = this.height - 2 * margin - 28; // space for buttons

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
        maxLinesLeft = leftH / lineHeight;
        maxLinesRight = rightH / lineHeight;

        // Row spacing for edit fields (1.5× lineHeight to prevent overlap)
        int rowSpacing = (int) (lineHeight * 1.8);

        // ── Name edit box (hidden until selection) ───────────────────────
        nameBox = new EditBox(this.font, rightX + 55, rightY + rowSpacing, rightW - 60, 14,
                Component.literal("Package name"));
        nameBox.setMaxLength(50);
        nameBox.setVisible(false);
        nameBox.setResponder(val -> {
            if (selectedIndex >= 0 && selectedIndex < packages.size()) {
                packages.get(selectedIndex).setName(val);
                dirty = true;
            }
        });
        addRenderableWidget(nameBox);

        // ── Priority edit box ────────────────────────────────────────────
        priorityBox = new EditBox(this.font, rightX + 55, rightY + rowSpacing * 2, 40, 14,
                Component.literal("Priority"));
        priorityBox.setMaxLength(4);
        priorityBox.setVisible(false);
        priorityBox.setResponder(val -> {
            if (selectedIndex >= 0 && selectedIndex < packages.size()) {
                try {
                    packages.get(selectedIndex).setPriority(Integer.parseInt(val));
                    dirty = true;
                } catch (NumberFormatException ignored) {}
            }
        });
        addRenderableWidget(priorityBox);

        // ── Bottom buttons ───────────────────────────────────────────────
        int buttonW = 65;
        int buttonH = 20;
        int buttonY = margin + contentH + 4;
        int buttonSpacing = 8;
        int totalButtonsW = buttonW * 4 + buttonSpacing * 3;
        int buttonStartX = (this.width - totalButtonsW) / 2;

        addRenderableWidget(Button.builder(Component.literal("+ Package"), btn -> onAddPackage())
                .bounds(buttonStartX, buttonY, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(Component.literal("+ Attribute"), btn -> onAddAttribute())
                .bounds(buttonStartX + (buttonW + buttonSpacing), buttonY, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> onSave())
                .bounds(buttonStartX + (buttonW + buttonSpacing) * 2, buttonY, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onBack())
                .bounds(buttonStartX + (buttonW + buttonSpacing) * 3, buttonY, buttonW, buttonH).build());

        // Update edit fields to match current selection
        updateEditFields();
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

        inlineDeleteBounds = null;
        inlineToggleBounds = null;

        if (packages.isEmpty()) {
            graphics.drawString(this.font,
                    Component.literal("No packages.").withStyle(ChatFormatting.ITALIC),
                    leftX + 5, leftY + 5, COLOR_GRAY, false);
            graphics.disableScissor();
            return;
        }

        int y = leftY;
        int visibleEnd = Math.min(packages.size(), leftScrollOffset + maxLinesLeft);

        for (int i = leftScrollOffset; i < visibleEnd; i++) {
            AttributePackage pkg = packages.get(i);
            int entryY = y + (i - leftScrollOffset) * lineHeight;

            // Selection highlight
            if (i == selectedIndex) {
                graphics.fill(leftX, entryY - 1, leftX + leftW, entryY + lineHeight - 1, COLOR_SELECTED);
            } else if (mouseX >= leftX && mouseX <= leftX + leftW
                    && mouseY >= entryY && mouseY < entryY + lineHeight) {
                graphics.fill(leftX, entryY - 1, leftX + leftW, entryY + lineHeight - 1, COLOR_HOVER);
            }

            // Status indicator + name
            String indicator = pkg.isEnabled() ? "\u25CF " : "\u25CB ";
            int indicatorColor = pkg.isEnabled() ? COLOR_ENABLED : COLOR_DISABLED;
            graphics.drawString(this.font, indicator, leftX + 3, entryY, indicatorColor, false);
            int indW = this.font.width(indicator);

            String name = pkg.getName() != null ? pkg.getName() : "(unnamed)";
            String priorityStr = " [" + pkg.getPriority() + "]";
            graphics.drawString(this.font, name, leftX + 3 + indW, entryY, COLOR_TEXT, false);
            graphics.drawString(this.font, priorityStr, leftX + 3 + indW + this.font.width(name),
                    entryY, COLOR_GRAY, false);

            // Inline icons for selected entry
            if (i == selectedIndex) {
                int iconX = leftX + leftW - 24;

                // Toggle enabled ●/○
                boolean togHover = mouseX >= iconX && mouseX < iconX + 10
                        && mouseY >= entryY && mouseY < entryY + 10;
                String togIcon = pkg.isEnabled() ? "\u25CB" : "\u25CF"; // opposite of current
                graphics.drawString(this.font, togIcon, iconX, entryY,
                        togHover ? 0xFF0088CC : COLOR_GRAY, false);
                inlineToggleBounds = new int[]{iconX, entryY, 10, 10};

                // Delete ✕
                iconX += 12;
                boolean delHover = mouseX >= iconX && mouseX < iconX + 10
                        && mouseY >= entryY && mouseY < entryY + 10;
                graphics.drawString(this.font, "\u2716", iconX, entryY,
                        delHover ? 0xFFFF0000 : 0xFF773333, false);
                inlineDeleteBounds = new int[]{iconX, entryY, 10, 10};
            }
        }

        // Scrollbar
        if (packages.size() > maxLinesLeft) {
            int scrollBarX = leftX + leftW - 3;
            float ratio = (float) leftScrollOffset / (packages.size() - maxLinesLeft);
            int thumbHeight = Math.max(10, leftH * maxLinesLeft / packages.size());
            int thumbY = leftY + (int) ((leftH - thumbHeight) * ratio);
            graphics.fill(scrollBarX, leftY, scrollBarX + 2, leftY + leftH, 0x33000000);
            graphics.fill(scrollBarX, thumbY, scrollBarX + 2, thumbY + thumbHeight, 0xAA553300);
        }

        graphics.disableScissor();
    }

    private void renderRightPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(rightX, rightY, rightX + rightW, rightY + rightH);
        attrDeleteBounds.clear();

        if (selectedIndex < 0 || selectedIndex >= packages.size()) {
            graphics.drawString(this.font,
                    Component.literal("Select a package on the left.").withStyle(ChatFormatting.ITALIC),
                    rightX + 5, rightY + 5, COLOR_GRAY, false);
            graphics.disableScissor();
            return;
        }

        AttributePackage pkg = packages.get(selectedIndex);
        int rowSpacing = (int) (lineHeight * 1.8);

        // Row 0: Header
        graphics.drawString(this.font,
                Component.literal("Edit Package").withStyle(s -> s.withBold(true)),
                rightX + 3, rightY, COLOR_HEADER, false);

        // Row 1: Name label (EditBox is rendered by widget system)
        graphics.drawString(this.font, "Name:", rightX + 3, rightY + rowSpacing + 3, COLOR_TEXT, false);

        // Row 2: Priority label
        graphics.drawString(this.font, "Priority:", rightX + 3, rightY + rowSpacing * 2 + 3, COLOR_TEXT, false);
        graphics.drawString(this.font, "(lower = higher priority)",
                rightX + 100, rightY + rowSpacing * 2 + 3, COLOR_GRAY, false);

        // Row 3: Status
        String statusText = pkg.isEnabled() ? "Enabled" : "Disabled";
        int statusColor = pkg.isEnabled() ? COLOR_ENABLED : COLOR_DISABLED;
        graphics.drawString(this.font, "Status: ", rightX + 3, rightY + rowSpacing * 3 + 3, COLOR_TEXT, false);
        graphics.drawString(this.font, statusText,
                rightX + 3 + this.font.width("Status: "), rightY + rowSpacing * 3 + 3, statusColor, false);

        // Row 4: Attributes header
        int attrHeaderY = rightY + rowSpacing * 4;
        graphics.drawString(this.font,
                Component.literal("Attributes:").withStyle(s -> s.withBold(true)),
                rightX + 3, attrHeaderY, COLOR_HEADER, false);

        // Attribute list
        List<String> attrs = pkg.getAttributes();
        int attrStartY = attrHeaderY + lineHeight;
        int visStart = rightScrollOffset;
        int maxAttrLines = Math.max(1, (rightY + rightH - attrStartY) / lineHeight);

        for (int i = visStart; i < attrs.size() && (i - visStart) < maxAttrLines; i++) {
            int attrY = attrStartY + (i - visStart) * lineHeight;
            String attrId = attrs.get(i);

            // Resolve human-readable name
            String displayName = resolveAttributeName(attrId);
            graphics.drawString(this.font, "\u25B8 " + displayName, rightX + 8, attrY, COLOR_TEXT, false);

            // Show full ID in gray below/after
            String shortId = attrId.length() > 30 ? "..." + attrId.substring(attrId.length() - 27) : attrId;
            int nameWidth = this.font.width("\u25B8 " + displayName + " ");
            graphics.drawString(this.font, "(" + shortId + ")",
                    rightX + 8 + nameWidth, attrY, COLOR_GRAY, false);

            // Delete icon ✕
            int delX = rightX + rightW - 12;
            boolean delHover = mouseX >= delX && mouseX < delX + 10
                    && mouseY >= attrY && mouseY < attrY + 10;
            graphics.drawString(this.font, "\u2716", delX, attrY,
                    delHover ? 0xFFFF0000 : 0xFF773333, false);
            attrDeleteBounds.add(new int[]{delX, attrY, 10, 10, i});
        }

        if (attrs.isEmpty()) {
            graphics.drawString(this.font,
                    Component.literal("(no attributes — use + Attribute to add)").withStyle(ChatFormatting.ITALIC),
                    rightX + 8, attrStartY, COLOR_GRAY, false);
        }

        graphics.disableScissor();
    }

    // ─── Input handling ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // Inline toggle
        if (inlineToggleBounds != null && hitTest(inlineToggleBounds, mouseX, mouseY)) {
            if (selectedIndex >= 0 && selectedIndex < packages.size()) {
                packages.get(selectedIndex).setEnabled(!packages.get(selectedIndex).isEnabled());
                dirty = true;
            }
            return true;
        }

        // Inline delete package
        if (inlineDeleteBounds != null && hitTest(inlineDeleteBounds, mouseX, mouseY)) {
            if (selectedIndex >= 0 && selectedIndex < packages.size()) {
                packages.remove(selectedIndex);
                if (selectedIndex >= packages.size()) selectedIndex = packages.size() - 1;
                updateEditFields();
                dirty = true;
            }
            return true;
        }

        // Attribute delete icons (right panel)
        for (int[] bounds : attrDeleteBounds) {
            if (mouseX >= bounds[0] && mouseX < bounds[0] + bounds[2]
                    && mouseY >= bounds[1] && mouseY < bounds[1] + bounds[3]) {
                int attrIndex = bounds[4];
                if (selectedIndex >= 0 && selectedIndex < packages.size()) {
                    List<String> attrs = packages.get(selectedIndex).getAttributes();
                    if (attrIndex >= 0 && attrIndex < attrs.size()) {
                        attrs.remove(attrIndex);
                        dirty = true;
                    }
                }
                return true;
            }
        }

        // Click on left panel → select package
        if (mouseX >= leftX && mouseX <= leftX + leftW
                && mouseY >= leftY && mouseY <= leftY + leftH) {
            int clickedLine = (int) ((mouseY - leftY) / lineHeight) + leftScrollOffset;
            if (clickedLine >= 0 && clickedLine < packages.size()) {
                selectedIndex = clickedLine;
                rightScrollOffset = 0;
                updateEditFields();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftX && mouseX <= leftX + leftW
                && mouseY >= leftY && mouseY <= leftY + leftH) {
            if (scrollY > 0) leftScrollOffset = Math.max(0, leftScrollOffset - 1);
            else leftScrollOffset = Math.min(Math.max(0, packages.size() - maxLinesLeft), leftScrollOffset + 1);
            return true;
        }
        if (mouseX >= rightX && mouseX <= rightX + rightW
                && mouseY >= rightY && mouseY <= rightY + rightH) {
            if (scrollY > 0) rightScrollOffset = Math.max(0, rightScrollOffset - 1);
            else rightScrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let edit boxes handle input first
        if (nameBox.isFocused() || priorityBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == 256) { // ESC
            onBack();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ─── Actions ─────────────────────────────────────────────────────────

    private void onAddPackage() {
        AttributePackage pkg = new AttributePackage();
        pkg.setName("New Package");
        pkg.setEnabled(true);
        pkg.setPriority(100);
        pkg.setAttributes(new ArrayList<>());
        packages.add(pkg);
        selectedIndex = packages.size() - 1;
        updateEditFields();
        dirty = true;
    }

    private void onAddAttribute() {
        if (selectedIndex < 0 || selectedIndex >= packages.size()) return;

        // Build list of all registered attributes
        List<SearchableListPopup.Entry<String>> entries =
                BuiltInRegistries.ATTRIBUTE.keySet().stream()
                        .sorted(Comparator.comparing(ResourceLocation::toString))
                        .map(loc -> {
                            String id = loc.toString();
                            Attribute attr = BuiltInRegistries.ATTRIBUTE.get(loc);
                            String displayName = attr != null
                                    ? Component.translatable(attr.getDescriptionId()).getString()
                                    : id;
                            return new SearchableListPopup.Entry<>(
                                    id,
                                    Component.literal(displayName + " (" + id + ")"),
                                    displayName + " " + id);
                        })
                        .collect(Collectors.toList());

        assert this.minecraft != null;
        this.minecraft.setScreen(new SearchableListPopup<>(
                Component.literal("Add Attribute"),
                this, entries,
                selected -> {
                    if (selectedIndex >= 0 && selectedIndex < packages.size()) {
                        List<String> attrs = packages.get(selectedIndex).getAttributes();
                        if (!attrs.contains(selected)) {
                            attrs.add(selected);
                            dirty = true;
                        }
                    }
                }
        ));
    }

    private void onSave() {
        AttributePackageManager.saveAndReload(packages);
        dirty = false;
    }

    private void onBack() {
        assert this.minecraft != null;
        this.minecraft.setScreen(parent);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private void updateEditFields() {
        if (selectedIndex >= 0 && selectedIndex < packages.size()) {
            AttributePackage pkg = packages.get(selectedIndex);
            nameBox.setValue(pkg.getName() != null ? pkg.getName() : "");
            priorityBox.setValue(String.valueOf(pkg.getPriority()));
            nameBox.setVisible(true);
            priorityBox.setVisible(true);
        } else {
            nameBox.setVisible(false);
            priorityBox.setVisible(false);
        }
    }

    private String resolveAttributeName(String attributeId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(
                    attributeId.contains(":") ? attributeId : "minecraft:" + attributeId);
            Attribute attr = BuiltInRegistries.ATTRIBUTE.get(loc);
            if (attr != null) {
                return Component.translatable(attr.getDescriptionId()).getString();
            }
        } catch (Exception ignored) {}
        return attributeId;
    }

    private static boolean hitTest(int[] bounds, double mx, double my) {
        return mx >= bounds[0] && mx < bounds[0] + bounds[2]
                && my >= bounds[1] && my < bounds[1] + bounds[3];
    }
}
