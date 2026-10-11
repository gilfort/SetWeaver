package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.seteffects.SetIds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Popup asking for the value and operation of a new attribute modifier, with a live preview.
 */
public class AttributeValueScreen extends Screen {

    private static final int W = 220;
    private static final int H = 170;

    /** Selectable operations in cycle order. */
    public static final List<String> OPERATIONS = List.of(SetIds.ADDITION, SetIds.MULTIPLY_BASE, SetIds.MULTIPLY_TOTAL);

    private final Screen parent;
    private final String attrId;
    private final BiConsumer<Double, String> onConfirm;

    private EditBox valueBox;
    private int selectedOperation = 0;
    private int px, py;

    /**
     * @param parent    screen to return to
     * @param attrId    the attribute id
     * @param onConfirm called with value and operation after returning to the parent
     */
    public AttributeValueScreen(Screen parent, String attrId, BiConsumer<Double, String> onConfirm) {
        super(Component.translatable("gui.setweaver.attribute_value.title"));
        this.parent = parent;
        this.attrId = attrId;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        px = (this.width - W) / 2;
        py = (this.height - H) / 2;

        valueBox = new EditBox(this.font, px + 8, py + 38, W - 16, 16, Component.translatable("gui.setweaver.value"));
        valueBox.setValue("1.0");
        valueBox.setMaxLength(12);
        addRenderableWidget(valueBox);
        setInitialFocus(valueBox);

        addRenderableWidget(Button.builder(operationLabel(), btn -> {
            selectedOperation = (selectedOperation + 1) % OPERATIONS.size();
            btn.setMessage(operationLabel());
        }).bounds(px + 8, py + 74, W - 16, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> confirm())
                .bounds(px + W / 2 - 52, py + H - 24, 48, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
                .bounds(px + W / 2 + 4, py + H - 24, 48, 16).build());
    }

    private Component operationLabel() {
        return Component.literal("◆ ").append(Component.translatable("gui.setweaver.operation." + OPERATIONS.get(selectedOperation))).append(" ◆");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, GuiUtil.OVERLAY_DIM);
        GuiUtil.drawPanel(g, px, py, W, H);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(this.font, GuiUtil.tr("gui.setweaver.attribute_value.for"), px + 8, py + 8, GuiUtil.COLOR_TEXT, false);
        g.text(this.font, AttributePreviewHelper.attributeName(attrId), px + 8, py + 20, 0xFF555555, false);
        g.text(this.font, GuiUtil.tr("gui.setweaver.attribute_value.operation"), px + 8, py + 62, GuiUtil.COLOR_TEXT, false);

        double value = parseOr(valueBox.getValue(), 0);
        String operation = OPERATIONS.get(selectedOperation);
        String label = GuiUtil.tr("gui.setweaver.preview") + " ";
        g.text(this.font, label, px + 8, py + 96, GuiUtil.COLOR_TEXT, false);
        g.text(this.font, AttributePreviewHelper.getPreviewText(attrId, value, operation),
                px + 8 + this.font.width(label), py + 96, value < 0 ? GuiUtil.COLOR_ERROR : 0xFF226622, false);
        String formula = AttributePreviewHelper.getFormulaText(value, operation);
        if (formula != null) {
            g.text(this.font, formula, px + 8, py + 108, 0xFF888888, false);
        }
    }

    private void confirm() {
        try {
            double value = Double.parseDouble(valueBox.getValue().trim());
            this.minecraft.gui.setScreen(parent);
            onConfirm.accept(value, OPERATIONS.get(selectedOperation));
        } catch (NumberFormatException e) {
            valueBox.setValue("1.0");
        }
    }

    private static double parseOr(String text, double fallback) {
        try {
            return text.isBlank() ? fallback : Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation()) {
            confirm();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
