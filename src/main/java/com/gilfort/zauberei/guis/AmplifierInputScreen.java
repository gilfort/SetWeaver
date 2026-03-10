package com.gilfort.zauberei.guis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.IntConsumer;

/**
 * Small popup screen to enter an amplifier value (0–9) for a mob effect.
 *
 * <p>Opened by {@link SetEditorScreen} after the user selects an effect
 * via {@link SearchableListPopup}. On confirm, calls the provided
 * {@link IntConsumer} with the parsed amplifier value.</p>
 */
@OnlyIn(Dist.CLIENT)
public class AmplifierInputScreen extends Screen {

    // ── Layout ──────────────────────────────────────────────────────────
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 100;
    private static final int PADDING = 12;

    // ── Colors (consistent with SetsManagerScreen / SetEditorScreen) ────
    private static final int PANEL_BG     = 0xFFF5F0E0;
    private static final int PANEL_BORDER = 0xFF8B7355;
    private static final int COLOR_TEXT   = 0xFF000000;
    private static final int COLOR_ERROR  = 0xFFCC3333;

    // ── State ────────────────────────────────────────────────────────────
    private final Screen parentScreen;
    private final String effectId;
    private final IntConsumer onConfirm;

    private EditBox amplifierBox;
    private String errorMessage = null;

    // ── Panel position (computed in init) ────────────────────────────────
    private int panelX, panelY;

    /**
     * @param parentScreen the screen to return to on cancel / confirm
     * @param effectId     the effect ResourceLocation string (e.g. "minecraft:speed")
     * @param onConfirm    called with the amplifier int (0–9) when the user confirms
     */
    public AmplifierInputScreen(Screen parentScreen, String effectId, IntConsumer onConfirm) {
        super(Component.literal("Set Amplifier"));
        this.parentScreen = parentScreen;
        this.effectId     = effectId;
        this.onConfirm    = onConfirm;
    }

    @Override
    protected void init() {
        super.init();

        panelX = (this.width  - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        int fieldY = panelY + PADDING + 22; // below title + effect label

        // Amplifier input field
        amplifierBox = new EditBox(
                this.font,
                panelX + PADDING, fieldY,
                PANEL_W - 2 * PADDING, 20,
                Component.literal("Amplifier (0–9)"));
        amplifierBox.setMaxLength(2);
        amplifierBox.setValue("0");
        amplifierBox.setHint(Component.literal("0 = Level I, 1 = Level II …"));
        addRenderableWidget(amplifierBox);

        int btnY   = panelY + PANEL_H - PADDING - 20;
        int btnW   = 70;
        int centerX = panelX + PANEL_W / 2;

        // Confirm button
        addRenderableWidget(Button.builder(
                        Component.literal("✔ Confirm"),
                        btn -> onConfirm())
                .bounds(centerX - btnW - 4, btnY, btnW, 20)
                .build());

        // Cancel button
        addRenderableWidget(Button.builder(
                        Component.literal("Cancel"),
                        btn -> onCancel())
                .bounds(centerX + 4, btnY, btnW, 20)
                .build());
    }

    // ── Actions ──────────────────────────────────────────────────────────

    private void onConfirm() {
        String raw = amplifierBox.getValue().trim();
        int amplifier;
        try {
            amplifier = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            errorMessage = "Please enter a number (0–9).";
            return;
        }
        if (amplifier < 0 || amplifier > 9) {
            errorMessage = "Amplifier must be between 0 and 9.";
            return;
        }
        // Return to parent first, then fire callback
        assert this.minecraft != null;
        this.minecraft.setScreen(parentScreen);
        onConfirm.accept(amplifier);
    }

    private void onCancel() {
        assert this.minecraft != null;
        this.minecraft.setScreen(parentScreen);
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        // Panel background
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, PANEL_BG);
        // Border
        graphics.fill(panelX,              panelY,              panelX + PANEL_W, panelY + 1,              PANEL_BORDER);
        graphics.fill(panelX,              panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H,       PANEL_BORDER);
        graphics.fill(panelX,              panelY,              panelX + 1,        panelY + PANEL_H,       PANEL_BORDER);
        graphics.fill(panelX + PANEL_W - 1, panelY,            panelX + PANEL_W,  panelY + PANEL_H,       PANEL_BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Title
        graphics.drawString(this.font,
                "Amplifier for: " + shortName(effectId),
                panelX + PADDING, panelY + PADDING,
                COLOR_TEXT, false);

        // Error message
        if (errorMessage != null) {
            graphics.drawString(this.font, errorMessage,
                    panelX + PADDING,
                    panelY + PANEL_H - PADDING - 32,
                    COLOR_ERROR, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onCancel(); return true; }  // Escape
        if (keyCode == 257) { onConfirm(); return true; } // Enter
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Helper ───────────────────────────────────────────────────────────

    private static String shortName(String resourceLocation) {
        if (resourceLocation == null) return "?";
        int colon = resourceLocation.indexOf(':');
        return colon >= 0 ? resourceLocation.substring(colon + 1) : resourceLocation;
    }
}
