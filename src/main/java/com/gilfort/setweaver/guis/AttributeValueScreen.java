package com.gilfort.setweaver.guis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Tiny modal screen: asks for attribute value + modifier type.
 */
@OnlyIn(Dist.CLIENT)
public class AttributeValueScreen extends Screen {

    private static final int PANEL_BG     = 0xFFF5F0E0;
    private static final int PANEL_BORDER = 0xFF8B7355;
    private static final int W = 220, H = 170;

    // NeoForge 1.21.1 attribute modifier operations
    private static final List<String> MODIFIERS = List.of(
            "addition", "multiply_base", "multiply_total");

    private final Screen parent;
    private final String attrId;
    private final BiConsumer<Double, String> onConfirm;

    private EditBox valueBox;
    private int selectedModifier = 0; // index into MODIFIERS

    public AttributeValueScreen(Screen parent, String attrId,
                                BiConsumer<Double, String> onConfirm) {
        super(Component.literal("Set Attribute Value"));
        this.parent    = parent;
        this.attrId    = attrId;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int px = (this.width - W) / 2;
        int py = (this.height - H) / 2;

        valueBox = new EditBox(this.font, px + 8, py + 38, W - 16, 16,
                Component.literal("Value"));
        valueBox.setValue("1.0");
        valueBox.setMaxLength(12);
        valueBox.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
        addRenderableWidget(valueBox);
        setFocused(valueBox);

        // Modifier cycle button
        addRenderableWidget(Button.builder(
                        Component.literal("◆ " + MODIFIERS.get(selectedModifier) + " ◆"),
                        btn -> {
                            selectedModifier = (selectedModifier + 1) % MODIFIERS.size();
                            btn.setMessage(Component.literal("◆ " + MODIFIERS.get(selectedModifier) + " ◆"));
                        })
                .bounds(px + 8, py + 74, W - 16, 16).build());

        addRenderableWidget(Button.builder(Component.literal("OK"), btn -> confirm())
                .bounds(px + W / 2 - 42, py + H - 24, 38, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> cancel())
                .bounds(px + W / 2 + 4, py + H - 24, 38, 16).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x88000000);
        // Panel background + border
        int px = (this.width - W) / 2, py = (this.height - H) / 2;
        g.fill(px, py, px + W, py + H, PANEL_BG);
        g.fill(px, py, px + W, py + 1, PANEL_BORDER);
        g.fill(px, py + H - 1, px + W, py + H, PANEL_BORDER);
        g.fill(px, py, px + 1, py + H, PANEL_BORDER);
        g.fill(px + W - 1, py, px + W, py + H, PANEL_BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // 1) super.render() ZUERST — ruft renderBackground() + Widgets auf
        super.render(g, mouseX, mouseY, pt);

        // 2) Text DANACH — liegt über dem Panel, sichtbar
        int px = (this.width - W) / 2, py = (this.height - H) / 2;
        g.drawString(this.font, "Value for:", px + 8, py + 8, 0xFF000000, false);
        g.drawString(this.font, AttributePreviewHelper.formatAttributeShort(attrId),
                px + 8, py + 20, 0xFF555555, false);
        g.drawString(this.font, "Modifier type:", px + 8, py + 62, 0xFF000000, false);

        // 3) Live preview — updates as user types value or cycles modifier
        String text = valueBox.getValue().trim();
        double previewVal = 0;
        try {
            if (!text.isEmpty()) previewVal = Double.parseDouble(text);
        } catch (NumberFormatException ignored) {}

        String operation = MODIFIERS.get(selectedModifier);
        String preview = AttributePreviewHelper.getPreviewText(attrId, previewVal, operation);
        int previewColor = AttributePreviewHelper.isNegative(previewVal, operation)
                ? 0xFFCC3333 : 0xFF226622;

        // Preview label + value
        g.drawString(this.font, "Preview:", px + 8, py + 96, 0xFF000000, false);
        g.drawString(this.font, preview, px + 8 + this.font.width("Preview: "),
                py + 96, previewColor, false);

        // Formula for percent modifiers
        String formula = AttributePreviewHelper.getFormulaText(previewVal, operation);
        if (formula != null) {
            g.drawString(this.font, formula, px + 8, py + 108, 0xFF888888, false);
        }
    }


    private void confirm() {
        try {
            double val = Double.parseDouble(valueBox.getValue().trim());
            assert this.minecraft != null;
            this.minecraft.setScreen(parent);
            onConfirm.accept(val, MODIFIERS.get(selectedModifier));
        } catch (NumberFormatException e) {
            valueBox.setValue("1.0");
        }
    }

    private void cancel() {
        assert this.minecraft != null;
        this.minecraft.setScreen(parent); }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257) { confirm(); return true; }
        if (key == 256) { cancel();  return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
