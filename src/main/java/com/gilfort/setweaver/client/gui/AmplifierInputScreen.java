package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.seteffects.SetWeaverReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

/**
 * Popup asking for the amplifier (0–9) of a new effect.
 */
public class AmplifierInputScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 100;
    private static final int PADDING = 12;

    private final Screen parentScreen;
    private final String effectId;
    private final IntConsumer onConfirm;

    private EditBox amplifierBox;
    private Component errorMessage = null;
    private int panelX, panelY;

    /**
     * @param parentScreen screen to return to
     * @param effectId     the effect id, shown in the title
     * @param onConfirm    called with the amplifier after returning to the parent
     */
    public AmplifierInputScreen(Screen parentScreen, String effectId, IntConsumer onConfirm) {
        super(Component.translatable("gui.setweaver.amplifier.title"));
        this.parentScreen = parentScreen;
        this.effectId = effectId;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        amplifierBox = new EditBox(this.font, panelX + PADDING, panelY + PADDING + 22, PANEL_W - 2 * PADDING, 20,
                Component.translatable("gui.setweaver.amplifier.title"));
        amplifierBox.setMaxLength(1);
        amplifierBox.setValue("0");
        amplifierBox.setFilter(s -> s.matches("\\d?"));
        amplifierBox.setHint(Component.translatable("gui.setweaver.amplifier.hint"));
        addRenderableWidget(amplifierBox);
        setInitialFocus(amplifierBox);

        int btnY = panelY + PANEL_H - PADDING - 20;
        int centerX = panelX + PANEL_W / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> confirm())
                .bounds(centerX - 74, btnY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
                .bounds(centerX + 4, btnY, 70, 20).build());
    }

    private void confirm() {
        int amplifier;
        try {
            amplifier = Integer.parseInt(amplifierBox.getValue().trim());
        } catch (NumberFormatException e) {
            amplifier = -1;
        }
        if (amplifier < 0 || amplifier > SetWeaverReloadListener.MAX_AMPLIFIER) {
            errorMessage = Component.translatable("gui.setweaver.amplifier.invalid", SetWeaverReloadListener.MAX_AMPLIFIER);
            return;
        }
        this.minecraft.setScreen(parentScreen);
        onConfirm.accept(amplifier);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GuiUtil.drawPanel(graphics, panelX, panelY, PANEL_W, PANEL_H);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, GuiUtil.tr("gui.setweaver.amplifier.for", AttributePreviewHelper.shortName(effectId)),
                panelX + PADDING, panelY + PADDING, GuiUtil.COLOR_TEXT, false);
        if (errorMessage != null) {
            graphics.drawString(this.font, errorMessage, panelX + PADDING, panelY + PANEL_H - PADDING - 32, GuiUtil.COLOR_ERROR, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
