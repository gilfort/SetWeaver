package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.network.SaveSetPayload;
import com.gilfort.setweaver.seteffects.ArmorSetData;
import com.gilfort.setweaver.seteffects.ArmorSetData.AttributeData;
import com.gilfort.setweaver.seteffects.ArmorSetData.EffectData;
import com.gilfort.setweaver.seteffects.SetIds;
import com.gilfort.setweaver.seteffects.SetWeaverReloadListener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Set Editor, step 2: edit the effects and attributes of the four thresholds (tabs "1 Piece" … "4 Pieces") and
 * send the result to the server. Click an amplifier, value or operation to edit it in place.
 */
public class SetEditorScreen extends Screen {

    private static final int TAB_ACTIVE_BG = 0xFFDDD0A0;
    private static final int TAB_INACTIVE_BG = 0xFFBBAA88;
    private static final int COLOR_POSITIVE = 0xFF226622;
    private static final int COLOR_GOLD = 0xFFDAA520;

    private static final int PADDING = 8;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_WIDTH = 60;
    private static final int ITEM_HEIGHT = 14;
    private static final int SECTION_TITLE_H = 12;
    private static final int AMP_ZONE_W = 45;
    private static final int TABS_TOP = PADDING + 14;

    /** What is being edited in place. */
    private enum InlineTarget { NONE, EFFECT_AMP, ATTR_VALUE, ATTR_OPERATION }

    private final Screen parentScreen;
    /** Screen that "Save & Exit" returns to. */
    private final Screen mainScreen;
    private final SetEditorData editorData;

    private int activeTab = 0;
    private int effectScroll = 0;
    private int attributeScroll = 0;
    private int selectedEffect = -1;
    private int selectedAttribute = -1;

    private InlineTarget inlineTarget = InlineTarget.NONE;
    private int inlineIndex = -1;
    private EditBox inlineBox;
    private String inlineOldValue = "";
    private int overlayX, overlayY, overlayW, editBoxX;

    private int contentTop, contentBottom;
    private int leftPanelX, leftPanelW, rightPanelX, rightPanelW;
    private int listTop, listH, maxVisible;
    private int opColW, valColW, nameColW;

    private Component statusMessage = Component.empty();
    private boolean statusIsError = false;

    /**
     * @param parent     screen for "Back"
     * @param mainScreen screen for "Save &amp; Exit"
     * @param editorData the set being edited
     */
    public SetEditorScreen(Screen parent, Screen mainScreen, SetEditorData editorData) {
        super(Component.translatable("gui.setweaver.editor.title", displayName(editorData)));
        this.parentScreen = parent;
        this.mainScreen = mainScreen;
        this.editorData = editorData;
    }

    private static String displayName(SetEditorData data) {
        return data.getDisplayName().isBlank() ? data.getTag() : data.getDisplayName();
    }

    private SetEditorData.PartConfig part() {
        return editorData.getPart(activeTab);
    }

    @Override
    protected void init() {
        cancelInlineEdit();
        contentTop = TABS_TOP + TAB_HEIGHT + 4;
        contentBottom = this.height - PADDING - 20 - 4 - 14 - 2;

        int totalW = this.width - 2 * PADDING;
        leftPanelX = PADDING;
        leftPanelW = (totalW - PADDING) / 2;
        rightPanelX = leftPanelX + leftPanelW + PADDING;
        rightPanelW = totalW - leftPanelW - PADDING;

        listTop = contentTop + PADDING + SECTION_TITLE_H + 4 + 20 + 4;
        listH = contentBottom - listTop - PADDING;
        maxVisible = Math.max(1, listH / ITEM_HEIGHT);

        opColW = this.font.width(GuiUtil.tr("gui.setweaver.operation.multiply_total")) + 8;
        valColW = this.font.width("+100% Base [×2.0] Speed") + 8;
        nameColW = (rightPanelW - 2 * PADDING) - valColW - opColW;

        int addY = contentTop + PADDING + SECTION_TITLE_H + 4;
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.editor.add_effect").withStyle(ChatFormatting.DARK_GREEN),
                btn -> openAddEffect()).bounds(leftPanelX, addY, leftPanelW / 2 - 2, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.editor.remove").withStyle(ChatFormatting.DARK_RED),
                btn -> removeSelectedEffect()).bounds(leftPanelX + leftPanelW / 2 + 2, addY, leftPanelW / 2 - 2, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.editor.add_attribute").withStyle(ChatFormatting.DARK_GREEN),
                btn -> openAddAttribute()).bounds(rightPanelX, addY, rightPanelW / 2 - 2, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.editor.remove").withStyle(ChatFormatting.DARK_RED),
                btn -> removeSelectedAttribute()).bounds(rightPanelX + rightPanelW / 2 + 2, addY, rightPanelW / 2 - 2, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.editor.copy_from"), btn -> openCopyFromPart())
                .bounds(PADDING + ArmorSetData.MAX_PARTS * (TAB_WIDTH + 2) + 8, TABS_TOP + 2, 80, TAB_HEIGHT - 4).build());

        int btnY = contentBottom + 4;
        int btnStartX = (this.width - (80 + 8 + 96 + 8 + 80)) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.editor.save"), btn -> save())
                .bounds(btnStartX, btnY, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.editor.save_exit"), btn -> {
            if (save()) {
                this.minecraft.setScreen(mainScreen);
            }
        }).bounds(btnStartX + 88, btnY, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.back"), btn -> onClose())
                .bounds(btnStartX + 192, btnY, 80, 20).build());
    }

    // ─── Rendering ───────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, this.width, this.height, 0xFF2A2A2A);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), PADDING, PADDING, 0xFFFFDD88, false);
        renderTabs(g);
        renderPanel(g, leftPanelX, leftPanelW, "gui.setweaver.editor.effects");
        renderPanel(g, rightPanelX, rightPanelW, "gui.setweaver.editor.attributes");
        renderEffects(g, mouseX, mouseY);
        renderAttributes(g, mouseX, mouseY);
        renderInlineOverlay(g);
        // Widgets were drawn under the panels; draw the inline edit box again on top
        if (inlineBox != null) {
            inlineBox.render(g, mouseX, mouseY, partialTick);
        }
        if (!statusMessage.getString().isEmpty()) {
            int statusY = this.height - PADDING - 2;
            int msgW = this.font.width(statusMessage) + 8;
            int msgX = this.width / 2 - msgW / 2;
            g.fill(msgX - 2, statusY - 10, msgX + msgW + 2, statusY + 2, 0xAA000000);
            g.drawString(this.font, statusMessage, msgX + 4, statusY - 8, statusIsError ? 0xFFFF4444 : 0xFF44CC44, false);
        }
    }

    private void renderTabs(GuiGraphics g) {
        for (int i = 0; i < ArmorSetData.MAX_PARTS; i++) {
            int tx = PADDING + i * (TAB_WIDTH + 2);
            boolean active = i == activeTab;
            g.fill(tx, TABS_TOP, tx + TAB_WIDTH, TABS_TOP + TAB_HEIGHT, active ? TAB_ACTIVE_BG : TAB_INACTIVE_BG);
            g.renderOutline(tx, TABS_TOP, TAB_WIDTH, TAB_HEIGHT, GuiUtil.PANEL_BORDER);
            String label = GuiUtil.pieces(i + 1);
            g.drawString(this.font, label, tx + (TAB_WIDTH - this.font.width(label)) / 2, TABS_TOP + (TAB_HEIGHT - 8) / 2,
                    active ? GuiUtil.COLOR_TEXT : 0xFF888877, false);
            SetEditorData.PartConfig config = editorData.getPart(i);
            int count = config.getEffects().size() + config.getAttributes().size();
            if (count > 0) {
                String badge = String.valueOf(count);
                g.drawString(this.font, badge, tx + TAB_WIDTH - this.font.width(badge) - 3, TABS_TOP + 2, 0xFFFFAA00, false);
            }
        }
    }

    private void renderPanel(GuiGraphics g, int px, int pw, String titleKey) {
        GuiUtil.drawPanel(g, px, contentTop, pw, contentBottom - contentTop);
        g.drawString(this.font, Component.translatable(titleKey).withStyle(ChatFormatting.BOLD), px + PADDING, contentTop + PADDING,
                GuiUtil.COLOR_TEXT, false);
    }

    private void renderEffects(GuiGraphics g, int mouseX, int mouseY) {
        List<EffectData> effects = part().getEffects();
        int lx = leftPanelX + PADDING;
        int lw = leftPanelW - 2 * PADDING;
        g.enableScissor(lx, listTop, lx + lw, listTop + listH);
        if (effects.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.setweaver.editor.no_effects").withStyle(ChatFormatting.ITALIC),
                    lx + 2, listTop + 3, GuiUtil.COLOR_GRAY, false);
        }
        int end = Math.min(effects.size(), effectScroll + maxVisible);
        for (int i = effectScroll; i < end; i++) {
            int y = listTop + (i - effectScroll) * ITEM_HEIGHT;
            EffectData effect = effects.get(i);
            highlight(g, i == selectedEffect, lx, y, lw, mouseX, mouseY);
            g.drawString(this.font, GuiUtil.truncate(this.font, AttributePreviewHelper.effectName(effect.getEffect()), lw - AMP_ZONE_W - 6),
                    lx + 3, y + 3, GuiUtil.COLOR_TEXT, false);
            String amp = AttributePreviewHelper.roman(effect.getAmplifier() + 1);
            g.drawString(this.font, amp, lx + lw - this.font.width(amp) - 3, y + 3, GuiUtil.COLOR_GRAY, false);
        }
        g.disableScissor();
    }

    private void renderAttributes(GuiGraphics g, int mouseX, int mouseY) {
        List<AttributeData> attributes = part().getAttributes();
        int lx = rightPanelX + PADDING;
        int lw = rightPanelW - 2 * PADDING;
        int hovered = -1;
        g.enableScissor(lx, listTop, lx + lw, listTop + listH);
        if (attributes.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.setweaver.editor.no_attributes").withStyle(ChatFormatting.ITALIC),
                    lx + 2, listTop + 3, GuiUtil.COLOR_GRAY, false);
        }
        int end = Math.min(attributes.size(), attributeScroll + maxVisible);
        for (int i = attributeScroll; i < end; i++) {
            int y = listTop + (i - attributeScroll) * ITEM_HEIGHT;
            AttributeData attribute = attributes.get(i);
            String operation = operationOf(attribute);
            if (GuiUtil.inside(mouseX, mouseY, lx, y, lw, ITEM_HEIGHT)) {
                hovered = i;
            }
            highlight(g, i == selectedAttribute, lx, y, lw, mouseX, mouseY);

            g.drawString(this.font, GuiUtil.truncate(this.font, AttributePreviewHelper.attributeName(attribute.getAttribute()), nameColW - 6),
                    lx + 3, y + 3, GuiUtil.COLOR_TEXT, false);
            int valX = lx + nameColW;
            String preview = GuiUtil.truncate(this.font,
                    AttributePreviewHelper.getPreviewText(attribute.getAttribute(), attribute.getValue(), operation), valColW - 6);
            g.drawString(this.font, preview, valX + valColW - this.font.width(preview) - 4, y + 3,
                    attribute.getValue() < 0 ? GuiUtil.COLOR_ERROR : COLOR_POSITIVE, false);
            int opX = valX + valColW;
            g.drawString(this.font, GuiUtil.tr("gui.setweaver.operation." + operation), opX + 3, y + 3, 0xFF887744, false);
            g.fill(valX, y + 1, valX + 1, y + ITEM_HEIGHT - 1, 0x22000000);
            g.fill(opX, y + 1, opX + 1, y + ITEM_HEIGHT - 1, 0x22000000);
        }
        g.disableScissor();

        int formulaIndex = hovered >= 0 ? hovered : selectedAttribute;
        if (formulaIndex >= 0 && formulaIndex < attributes.size() && inlineTarget == InlineTarget.NONE) {
            AttributeData attribute = attributes.get(formulaIndex);
            String formula = AttributePreviewHelper.getFormulaText(attribute.getValue(), operationOf(attribute));
            if (formula != null) {
                drawHintBox(g, formula, lx + lw / 2, listTop + listH + 2, 0xFF999999, false);
            }
        }
    }

    private void highlight(GuiGraphics g, boolean selected, int x, int y, int w, int mouseX, int mouseY) {
        if (selected) {
            g.fill(x, y, x + w, y + ITEM_HEIGHT, GuiUtil.COLOR_SELECTED);
        } else if (GuiUtil.inside(mouseX, mouseY, x, y, w, ITEM_HEIGHT)) {
            g.fill(x, y, x + w, y + ITEM_HEIGHT, GuiUtil.COLOR_HOVER);
        }
    }

    /** Draws the dark "old value → new value" bar over the row being edited, plus a live preview below it. */
    private void renderInlineOverlay(GuiGraphics g) {
        if (inlineTarget == InlineTarget.NONE) {
            return;
        }
        g.fill(overlayX - 1, overlayY - 1, overlayX + overlayW + 1, overlayY + ITEM_HEIGHT + 1, COLOR_GOLD);
        g.fill(overlayX, overlayY, overlayX + overlayW, overlayY + ITEM_HEIGHT, 0xEE2A2A2A);
        int halfW = overlayW / 2;
        int textY = overlayY + 3;
        g.drawString(this.font, GuiUtil.truncate(this.font, inlineOldValue, halfW - 8), overlayX + 4, textY, 0xFF999999, false);
        g.drawString(this.font, "→", overlayX + halfW - this.font.width("→") / 2, textY, COLOR_GOLD, false);

        if (inlineTarget == InlineTarget.EFFECT_AMP || inlineIndex >= part().getAttributes().size()) {
            return;
        }
        AttributeData attribute = part().getAttributes().get(inlineIndex);
        String operation = operationOf(attribute);
        double value = attribute.getValue();
        if (inlineTarget == InlineTarget.ATTR_OPERATION) {
            g.fill(editBoxX, overlayY + 1, editBoxX + halfW - 1, overlayY + ITEM_HEIGHT - 1, 0xFF444444);
            g.drawString(this.font, GuiUtil.tr("gui.setweaver.operation." + operation), editBoxX + 4, textY, 0xFF44FF44, false);
            g.drawString(this.font, "↻", editBoxX + halfW - this.font.width("↻") - 5, textY, 0xFF888888, false);
        } else if (inlineBox != null) {
            try {
                if (!inlineBox.getValue().isBlank()) {
                    value = Double.parseDouble(inlineBox.getValue().trim());
                }
            } catch (NumberFormatException ignored) {
                // keep the stored value for the preview
            }
        }
        int previewY = overlayY + ITEM_HEIGHT + 2;
        drawHintBox(g, AttributePreviewHelper.getPreviewText(attribute.getAttribute(), value, operation),
                overlayX + overlayW / 2, previewY, value < 0 ? 0xFFFF6666 : 0xFF66FF66, true);
        String formula = AttributePreviewHelper.getFormulaText(value, operation);
        if (formula != null && inlineTarget == InlineTarget.ATTR_VALUE) {
            drawHintBox(g, formula, overlayX + overlayW / 2, previewY + 12, 0xFF999999, false);
        }
    }

    private void drawHintBox(GuiGraphics g, String text, int centerX, int y, int color, boolean goldTop) {
        int w = this.font.width(text) + 8;
        int x = centerX - w / 2;
        g.fill(x - 2, y - 1, x + w + 2, y + 10, 0xDD000000);
        if (goldTop) {
            g.fill(x - 2, y - 1, x + w + 2, y, COLOR_GOLD);
        }
        g.drawString(this.font, text, x + 4, y + 1, color, false);
    }

    // ─── Inline editing ──────────────────────────────────────────────────

    private void startInlineEdit(InlineTarget target, int index, int x, int y, int width, String oldValue, int maxLength) {
        cancelInlineEdit();
        inlineTarget = target;
        inlineIndex = index;
        inlineOldValue = oldValue;
        overlayX = x;
        overlayY = y;
        overlayW = width;
        editBoxX = x + width / 2;
        if (maxLength > 0) {
            inlineBox = new EditBox(this.font, editBoxX + 1, y, width / 2 - 2, ITEM_HEIGHT, Component.translatable("gui.setweaver.value"));
            inlineBox.setMaxLength(maxLength);
            inlineBox.setTextColor(0xFF44FF44);
            addWidget(inlineBox);
            setFocused(inlineBox);
            inlineBox.setFocused(true);
        }
    }

    private void confirmInlineEdit() {
        if (inlineBox != null) {
            String text = inlineBox.getValue().trim();
            try {
                if (inlineTarget == InlineTarget.EFFECT_AMP && inlineIndex < part().getEffects().size()) {
                    int amp = Math.clamp(Integer.parseInt(text), 0, SetWeaverReloadListener.MAX_AMPLIFIER);
                    part().getEffects().get(inlineIndex).setAmplifier(amp);
                    setStatus(Component.translatable("gui.setweaver.editor.status.amplifier", AttributePreviewHelper.roman(amp + 1)), false);
                } else if (inlineTarget == InlineTarget.ATTR_VALUE && inlineIndex < part().getAttributes().size()) {
                    double value = Double.parseDouble(text);
                    part().getAttributes().get(inlineIndex).setValue(value);
                    setStatus(Component.translatable("gui.setweaver.editor.status.value", text), false);
                }
            } catch (NumberFormatException e) {
                if (!text.isEmpty()) {
                    setStatus(Component.translatable("gui.setweaver.editor.status.invalid_number"), true);
                }
            }
        }
        cancelInlineEdit();
    }

    private void cancelInlineEdit() {
        if (inlineBox != null) {
            removeWidget(inlineBox);
            inlineBox = null;
        }
        inlineTarget = InlineTarget.NONE;
        inlineIndex = -1;
    }

    private void cycleOperation(int index) {
        if (index < 0 || index >= part().getAttributes().size()) {
            return;
        }
        AttributeData attribute = part().getAttributes().get(index);
        List<String> ops = AttributeValueScreen.OPERATIONS;
        String next = ops.get((ops.indexOf(operationOf(attribute)) + 1) % ops.size());
        attribute.setModifier(next);
        setStatus(Component.translatable("gui.setweaver.editor.status.operation", GuiUtil.tr("gui.setweaver.operation." + next)), false);
    }

    private static String operationOf(AttributeData attribute) {
        String op = attribute.getModifier() == null ? SetIds.ADDITION : attribute.getModifier().toLowerCase(Locale.ROOT);
        return SetIds.MULTIPLY.equals(op) ? SetIds.MULTIPLY_BASE : op;
    }

    // ─── Input ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inlineTarget != InlineTarget.NONE) {
            boolean inOverlay = GuiUtil.inside(mouseX, mouseY, overlayX, overlayY - 1, overlayW, ITEM_HEIGHT + 2);
            if (inlineTarget == InlineTarget.ATTR_OPERATION && inOverlay && mouseX >= editBoxX) {
                cycleOperation(inlineIndex);
                return true;
            }
            if (inOverlay && inlineBox != null) {
                return inlineBox.mouseClicked(mouseX, mouseY, button);
            }
            confirmInlineEdit();
            return true;
        }

        for (int i = 0; i < ArmorSetData.MAX_PARTS; i++) {
            if (GuiUtil.inside(mouseX, mouseY, PADDING + i * (TAB_WIDTH + 2), TABS_TOP, TAB_WIDTH, TAB_HEIGHT)) {
                activeTab = i;
                selectedEffect = -1;
                selectedAttribute = -1;
                effectScroll = 0;
                attributeScroll = 0;
                return true;
            }
        }

        int lx = leftPanelX + PADDING;
        int lw = leftPanelW - 2 * PADDING;
        if (GuiUtil.inside(mouseX, mouseY, lx, listTop, lw, listH)) {
            int index = (int) ((mouseY - listTop) / ITEM_HEIGHT) + effectScroll;
            if (index < part().getEffects().size()) {
                if (mouseX >= lx + lw - AMP_ZONE_W) {
                    startInlineEdit(InlineTarget.EFFECT_AMP, index, lx + lw - AMP_ZONE_W, rowY(index, effectScroll), AMP_ZONE_W,
                            String.valueOf(part().getEffects().get(index).getAmplifier()), 1);
                } else {
                    selectedEffect = index;
                    selectedAttribute = -1;
                }
            }
            return true;
        }

        int rx = rightPanelX + PADDING;
        int rw = rightPanelW - 2 * PADDING;
        if (GuiUtil.inside(mouseX, mouseY, rx, listTop, rw, listH)) {
            int index = (int) ((mouseY - listTop) / ITEM_HEIGHT) + attributeScroll;
            if (index < part().getAttributes().size()) {
                AttributeData attribute = part().getAttributes().get(index);
                int valX = rx + nameColW;
                int opX = valX + valColW;
                if (mouseX >= opX) {
                    startInlineEdit(InlineTarget.ATTR_OPERATION, index, valX, rowY(index, attributeScroll), valColW + opColW,
                            GuiUtil.tr("gui.setweaver.operation." + operationOf(attribute)), 0);
                    cycleOperation(index);
                } else if (mouseX >= valX) {
                    startInlineEdit(InlineTarget.ATTR_VALUE, index, valX, rowY(index, attributeScroll), valColW + opColW,
                            String.format(Locale.ROOT, "%+.2f", attribute.getValue()), 10);
                } else {
                    selectedAttribute = index;
                    selectedEffect = -1;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int rowY(int index, int scroll) {
        return listTop + (index - scroll) * ITEM_HEIGHT;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : 1;
        if (GuiUtil.inside(mouseX, mouseY, leftPanelX, listTop, leftPanelW, listH)) {
            effectScroll = Math.clamp(effectScroll + step, 0, Math.max(0, part().getEffects().size() - maxVisible));
            return true;
        }
        if (GuiUtil.inside(mouseX, mouseY, rightPanelX, listTop, rightPanelW, listH)) {
            attributeScroll = Math.clamp(attributeScroll + step, 0, Math.max(0, part().getAttributes().size() - maxVisible));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inlineTarget != InlineTarget.NONE) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmInlineEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelInlineEdit();
                return true;
            }
            return inlineBox == null || inlineBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (inlineBox != null) {
            return inlineBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ─── Popups ──────────────────────────────────────────────────────────

    private void openAddEffect() {
        List<SearchableListPopup.Entry<String>> entries = new ArrayList<>();
        BuiltInRegistries.MOB_EFFECT.entrySet().forEach(e -> {
            String id = e.getKey().location().toString();
            String name = e.getValue().getDisplayName().getString();
            entries.add(new SearchableListPopup.Entry<>(id, Component.literal(name + " (" + id + ")"), name + " " + id));
        });
        entries.sort(Comparator.comparing(e -> e.displayName().getString()));
        this.minecraft.setScreen(new SearchableListPopup<>(Component.translatable("gui.setweaver.editor.select_effect"), this, entries,
                id -> this.minecraft.setScreen(new AmplifierInputScreen(this, id, amplifier -> {
                    part().getEffects().add(SetEditorData.effect(id, amplifier));
                    setStatus(Component.translatable("gui.setweaver.editor.status.effect_added"), false);
                }))));
    }

    private void openAddAttribute() {
        List<SearchableListPopup.Entry<String>> entries = new ArrayList<>();
        BuiltInRegistries.ATTRIBUTE.entrySet().forEach(e -> {
            String id = e.getKey().location().toString();
            String name = Component.translatable(e.getValue().getDescriptionId()).getString();
            entries.add(new SearchableListPopup.Entry<>(id, Component.literal(name + " (" + id + ")"), name + " " + id));
        });
        entries.sort(Comparator.comparing(e -> e.displayName().getString()));
        this.minecraft.setScreen(new SearchableListPopup<>(Component.translatable("gui.setweaver.editor.select_attribute"), this, entries,
                id -> this.minecraft.setScreen(new AttributeValueScreen(this, id, (value, operation) -> {
                    part().getAttributes().add(SetEditorData.attribute(id, value, operation));
                    setStatus(Component.translatable("gui.setweaver.editor.status.attribute_added"), false);
                }))));
    }

    private void openCopyFromPart() {
        List<SearchableListPopup.Entry<Integer>> entries = new ArrayList<>();
        for (int i = 0; i < ArmorSetData.MAX_PARTS; i++) {
            if (i != activeTab) {
                SetEditorData.PartConfig config = editorData.getPart(i);
                entries.add(new SearchableListPopup.Entry<>(i, Component.translatable("gui.setweaver.editor.copy_entry",
                        GuiUtil.pieces(i + 1), config.getEffects().size(), config.getAttributes().size())));
            }
        }
        int target = activeTab;
        this.minecraft.setScreen(new SearchableListPopup<>(
                Component.translatable("gui.setweaver.editor.copy_title", GuiUtil.pieces(target + 1)), this, entries, source -> {
                    editorData.getPart(target).copyFrom(editorData.getPart(source));
                    selectedEffect = -1;
                    selectedAttribute = -1;
                    effectScroll = 0;
                    attributeScroll = 0;
                    setStatus(Component.translatable("gui.setweaver.editor.status.copied", GuiUtil.pieces(source + 1), GuiUtil.pieces(target + 1)), false);
                }));
    }

    private void removeSelectedEffect() {
        if (selectedEffect >= 0 && selectedEffect < part().getEffects().size()) {
            part().getEffects().remove(selectedEffect);
            selectedEffect = -1;
            setStatus(Component.translatable("gui.setweaver.editor.status.effect_removed"), false);
        }
    }

    private void removeSelectedAttribute() {
        if (selectedAttribute >= 0 && selectedAttribute < part().getAttributes().size()) {
            part().getAttributes().remove(selectedAttribute);
            selectedAttribute = -1;
            setStatus(Component.translatable("gui.setweaver.editor.status.attribute_removed"), false);
        }
    }

    /** Sends the set to the server, which writes, reloads and syncs it. */
    private boolean save() {
        ArmorSetData data = editorData.toArmorSetData();
        if (!data.hasBonuses()) {
            setStatus(Component.translatable("gui.setweaver.editor.status.empty"), true);
            return false;
        }
        PacketDistributor.sendToServer(new SaveSetPayload(editorData.relativePath(), SetWeaverReloadListener.GSON.toJson(data)));
        setStatus(Component.translatable("gui.setweaver.editor.status.saving", editorData.relativePath()), false);
        return true;
    }

    private void setStatus(Component message, boolean isError) {
        statusMessage = message;
        statusIsError = isError;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
