package com.gilfort.setweaver.guis;


import com.gilfort.setweaver.seteffects.ArmorSetData;
import com.gilfort.setweaver.seteffects.ArmorSetData.AttributeData;
import com.gilfort.setweaver.seteffects.ArmorSetData.EffectData;
import com.gilfort.setweaver.seteffects.ArmorSetData.PartData;
import com.gilfort.setweaver.network.SaveSetPayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;

/**
 * SetEditorScreen — Edit or create an ArmorSet JSON.
 *
 * <p>Layout:
 * <ul>
 *   <li>Top: Set name display + Part-tabs (1Part … 4Part)</li>
 *   <li>Left panel: Effects list for the active part</li>
 *   <li>Right panel: Attributes list for the active part</li>
 *   <li>Bottom: Save + Cancel buttons</li>
 * </ul>
 *
 * <p>Saves directly to {@code config/setweaver/set-effects/<setId>.json}.
 *
 * @see ArmorSetData
 * @see SetsManagerScreen
 */
@OnlyIn(Dist.CLIENT)
public class SetEditorScreen extends Screen {

    // ──── Color Scheme (consistent with SetsManagerScreen) ──────────────
    private static final int PANEL_BG       = 0xFFF5F0E0;
    private static final int PANEL_BORDER   = 0xFF8B7355;
    private static final int COLOR_TEXT     = 0xFF000000;
    private static final int COLOR_GRAY     = 0xFF666666;
    private static final int COLOR_SELECTED = 0x44FFCC00;
    private static final int COLOR_HOVER    = 0x22FFCC00;
    private static final int TAB_ACTIVE_BG  = 0xFFDDD0A0;
    private static final int TAB_INACTIVE_BG= 0xFFBBAA88;
    private static final int COLOR_RED_BTN  = 0xFFCC3333;
    private static final int COLOR_GREEN    = 0xFF226622;

    // ──── Layout ────────────────────────────────────────────────────────
    private static final int PADDING        = 8;
    private static final int TAB_HEIGHT     = 20;
    private static final int TAB_WIDTH      = 60;
    private static final int ITEM_HEIGHT    = 14;
    private static final int SECTION_TITLE_H= 12;
    private static final int PARTS          = 4; // 1Part … 4Part

    // ── Inline-Editing State ──────────────────────────────────────────
    private enum InlineEditTarget { NONE, EFFECT_AMP, ATTR_VALUE, ATTR_MODIFIER }
    private InlineEditTarget inlineEditTarget = InlineEditTarget.NONE;
    private int inlineEditIndex = -1;
    private EditBox inlineEditBox = null;       // nur für AMP und VALUE
    private String inlineOldValue = "";         // Anzeige des alten Werts
    private int overlayX, overlayY, overlayW;   // Overlay-Position (für Klick-Erkennung)
    private int editBoxX;                       // X-Position des Edit-Bereichs (rechts vom Pfeil)



    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ──── Data ──────────────────────────────────────────────────────────
    private final Screen parentScreen;
    /** The top-level manager screen — "Save & Exit" always returns here. */
    private final Screen mainScreen;
    private final String setId;
    private final String displayName;
    private final SetEditorData editorData;

    /**
     * Working copy of the set data — one PartData per part index (0=1Part … 3=4Part).
     * We use ArrayList<EffectData> and LinkedHashMap<String,AttributeData> so order is stable.
     */
    private final List<EffectData>[]              partEffects;
    private final Map<String, AttributeData>[]    partAttributes;

    // ──── UI State ───────────────────────────────────────────────────────
    private int activeTab = 0;  // 0 = 1Part, 1 = 2Part, …
    private int effectScrollOffset   = 0;
    private int attributeScrollOffset= 0;
    private int selectedEffectIndex   = -1;
    private int selectedAttributeIndex= -1;

    // ──── Derived layout (computed in init) ─────────────────────────────
    private int contentTop;   // y below tabs
    private int contentBottom;// y above buttons
    private int leftPanelX, leftPanelW;
    private int rightPanelX, rightPanelW;
    private int panelListTop, panelListH;
    private int maxEffectVisible, maxAttrVisible;
    // ──── Attribute column layout (computed in init) ───────────────────
    private int attrModColW, attrValColW, attrNameColW;


    // ──── Status message ────────────────────────────────────────────────
    private String statusMessage = "";
    private boolean statusIsError = false;

    // ════════════════════════════════════════════════════════════════════
    //  Constructors
    // ════════════════════════════════════════════════════════════════════

    /**
     * Create-mode: starts with empty data.
     */
    @SuppressWarnings("unchecked")
    public SetEditorScreen(Screen parent, String setId, String displayName) {
        super(Component.literal("Edit Set: " + displayName));
        this.parentScreen = parent;
        this.mainScreen   = parent;
        this.setId        = setId;
        this.displayName  = displayName;
        this.partEffects    = new ArrayList[PARTS];
        this.partAttributes = new LinkedHashMap[PARTS];
        this.editorData = null;
        for (int i = 0; i < PARTS; i++) {
            partEffects[i]    = new ArrayList<>();
            partAttributes[i] = new LinkedHashMap<>();
        }
    }

    /**
     * Edit-mode: pre-loads existing {@link ArmorSetData}.
     */
    @SuppressWarnings("unchecked")
    public SetEditorScreen(Screen parent, String setId, ArmorSetData existing) {
        super(Component.literal("Edit Set: " +
                (existing.getDisplayName() != null ? existing.getDisplayName() : setId)));
        this.parentScreen = parent;
        this.mainScreen   = parent;
        this.setId        = setId;
        this.displayName  = existing.getDisplayName() != null ? existing.getDisplayName() : setId;
        this.partEffects    = new ArrayList[PARTS];
        this.partAttributes = new LinkedHashMap[PARTS];
        this.editorData = null;
        for (int i = 0; i < PARTS; i++) {
            partEffects[i]    = new ArrayList<>();
            partAttributes[i] = new LinkedHashMap<>();
        }

        // Load existing data into working copies
        if (existing.getParts() != null) {
            for (int i = 0; i < PARTS; i++) {
                String key = (i + 1) + "Part";
                PartData pd = existing.getParts().get(key);
                if (pd != null) {
                    if (pd.getEffects() != null) {
                        partEffects[i].addAll(pd.getEffects());
                    }
                    if (pd.getAttributes() != null) {
                        partAttributes[i].putAll(pd.getAttributes());
                    }
                }
            }
        }
    }

    /**
     * Wizard-mode: receives data from {@link SetWizardScreen} via {@link SetEditorData}.
     *
     * <p>Bridges the new wizard flow (SetEditorData) with the existing
     * internal data structures (List<EffectData>, Map<String,AttributeData>).</p>
     */
    @SuppressWarnings("unchecked")
    public SetEditorScreen(Screen parent, SetEditorData editorData) {
        this(parent, parent, editorData);
    }

    /**
     * Wizard-mode with explicit main screen: "Save & Exit" returns to {@code mainScreen}
     * while "Back" returns to {@code parent} (e.g. the SetWizardScreen).
     */
    public SetEditorScreen(Screen parent, Screen mainScreen, SetEditorData editorData) {
        super(Component.literal("Edit Set: " +
                (editorData.getDisplayName().isBlank() ? editorData.getTag() : editorData.getDisplayName())));
        this.parentScreen = parent;
        this.mainScreen   = mainScreen;
        // Build a setId from the tag (e.g. "zauberei:magiccloth_armor" → "zauberei__magiccloth_armor")
        this.setId       = editorData.getTag().replace(":", "__");
        this.displayName = editorData.getDisplayName().isBlank()
                ? editorData.getTag()
                : editorData.getDisplayName();

        this.partEffects    = new ArrayList[PARTS];
        this.partAttributes = new LinkedHashMap[PARTS];
        this.editorData = editorData;
        for (int i = 0; i < PARTS; i++) {
            partEffects[i]    = new ArrayList<>();
            partAttributes[i] = new LinkedHashMap<>();
        }

        // Transfer effects and attributes from SetEditorData → internal structures
        for (int i = 0; i < PARTS; i++) {
            if (!editorData.isPartEnabled(i)) continue;

            SetEditorData.PartConfig config = editorData.getPartConfig(i);

            for (SetEditorData.EffectEntry ee : config.getEffects()) {
                EffectData ed = new EffectData();
                ed.setEffect(ee.getEffectId());
                ed.setAmplifier(ee.getAmplifier());
                partEffects[i].add(ed);
            }

            for (SetEditorData.AttributeEntry ae : config.getAttributes()) {
                AttributeData ad = new AttributeData();
                ad.setValue(ae.getValue());
                ad.setModifier(ae.getOperation());
                partAttributes[i].put(ae.getAttributeId(), ad);
            }
        }
    }


    // ════════════════════════════════════════════════════════════════════
    //  Init
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();

        int tabsTop    = PADDING + 14;
        contentTop     = tabsTop + TAB_HEIGHT + 4;
        int btnH       = 20;
        int statusH    = 14;
        contentBottom  = this.height - PADDING - btnH - 4 - statusH - 2;

        // Two panels side by side
        int totalW     = this.width - 2 * PADDING;
        leftPanelX     = PADDING;
        leftPanelW     = (totalW - PADDING) / 2;
        rightPanelX    = leftPanelX + leftPanelW + PADDING;
        rightPanelW    = totalW - leftPanelW - PADDING;

        // List area inside each panel (below section title + add button)
        panelListTop   = contentTop + PADDING + SECTION_TITLE_H + 4 + 20 + 4;
        panelListH     = contentBottom - panelListTop - PADDING;
        maxEffectVisible = panelListH / ITEM_HEIGHT;
        maxAttrVisible   = panelListH / ITEM_HEIGHT;
        // Attribute column widths (fixed, so click zones don't jump)
        attrModColW = this.font.width("multiply_total") + 8;
        attrValColW = this.font.width("+100% Base [×2.0] Speed") + 8;
        attrNameColW = (rightPanelW - 2 * PADDING) - attrValColW - attrModColW;


        // ── Buttons ──────────────────────────────────────────────────────
        int btnY = contentBottom + 4;

        // Add Effect
        addRenderableWidget(Button.builder(
                        Component.literal("+ Effect").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD),
                        btn -> openAddEffectPopup())
                .bounds(leftPanelX, contentTop + PADDING + SECTION_TITLE_H + 4, leftPanelW / 2 - 2, 18)
                .build());

// Remove Effect
        addRenderableWidget(Button.builder(
                        Component.literal("- Remove").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        btn -> removeSelectedEffect())
                .bounds(leftPanelX + leftPanelW / 2 + 2, contentTop + PADDING + SECTION_TITLE_H + 4, leftPanelW / 2 - 2, 18)
                .build());

// Add Attribute
        addRenderableWidget(Button.builder(
                        Component.literal("+ Attribute").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD),
                        btn -> openAddAttributePopup())
                .bounds(rightPanelX, contentTop + PADDING + SECTION_TITLE_H + 4, rightPanelW / 2 - 2, 18)
                .build());

// Remove Attribute
        addRenderableWidget(Button.builder(
                        Component.literal("- Remove").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        btn -> removeSelectedAttribute())
                .bounds(rightPanelX + rightPanelW / 2 + 2, contentTop + PADDING + SECTION_TITLE_H + 4, rightPanelW / 2 - 2, 18)
                .build());

                // Copy from another Part
                int copyBtnX = PADDING + PARTS * (TAB_WIDTH + 2) + 8;
                int copyBtnY = PADDING + 14 + 2; // gleiche Höhe wie Tabs
                addRenderableWidget(Button.builder(
                        Component.literal("Copy from\u2026"),
                        btn -> openCopyFromPartPopup())
                .bounds(copyBtnX, copyBtnY, 70, TAB_HEIGHT - 4)
                .build());


        // Three bottom buttons: Save | Save & Exit | Cancel
        // Total width: 80 + 8 + 96 + 8 + 80 = 272 → centered
        int totalBtnW = 80 + 8 + 96 + 8 + 80;
        int btnStartX = (this.width - totalBtnW) / 2;

        // Save
        addRenderableWidget(Button.builder(
                        Component.literal("💾 Save"), btn -> saveToJson())
                .bounds(btnStartX, btnY, 80, btnH)
                .build());

        // Save & Exit
        addRenderableWidget(Button.builder(
                        Component.literal("💾 Save & Exit"), btn -> saveAndExit())
                .bounds(btnStartX + 80 + 8, btnY, 96, btnH)
                .build());

        // Back
        addRenderableWidget(Button.builder(
                        Component.literal("← Back"), btn -> onClose())
                .bounds(btnStartX + 80 + 8 + 96 + 8, btnY, 80, btnH)
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════
//  Inline Editing — Overlay-Based
// ═══════════════════════════════════════════════════════════════════

    private void startInlineEditEffectAmp(int index, int panelX, int panelW) {
        cancelInlineEdit();
        if (index < 0 || index >= partEffects[activeTab].size()) return;

        EffectData ed = partEffects[activeTab].get(index);
        inlineEditTarget = InlineEditTarget.EFFECT_AMP;
        inlineEditIndex = index;
        inlineOldValue = String.valueOf(ed.getAmplifier());

        // Overlay over the right 45px of the effect row
        int ampZoneW = 45;
        overlayX = panelX + panelW - ampZoneW;
        overlayY = panelListTop + (index - effectScrollOffset) * ITEM_HEIGHT;
        overlayW = ampZoneW;

        // Left half: old value | Right half: EditBox
        int halfW = overlayW / 2;
        editBoxX = overlayX + halfW;

        inlineEditBox = new EditBox(this.font, editBoxX + 1, overlayY, halfW - 2, ITEM_HEIGHT,
                Component.literal("amp"));
        inlineEditBox.setMaxLength(2);
        inlineEditBox.setValue("");  // start empty
        inlineEditBox.setTextColor(0xFF44FF44);
        inlineEditBox.setFocused(true);
        addRenderableWidget(inlineEditBox);
        setFocused(inlineEditBox);
    }

    private void startInlineEditAttrValue(int index, int colX, int colW) {
        cancelInlineEdit();
        List<Map.Entry<String, AttributeData>> entries =
                new ArrayList<>(partAttributes[activeTab].entrySet());
        if (index < 0 || index >= entries.size()) return;

        AttributeData ad = entries.get(index).getValue();
        inlineEditTarget = InlineEditTarget.ATTR_VALUE;
        inlineEditIndex = index;
        String sign = ad.getValue() >= 0 ? "+" : "";
        inlineOldValue = sign + String.format("%.2f", ad.getValue());

        // Overlay spans BOTH value + modifier columns
        overlayX = colX;
        overlayY = panelListTop + (index - attributeScrollOffset) * ITEM_HEIGHT;
        overlayW = colW + attrModColW;  // both columns

        // Left half: old value | Right half: EditBox
        int halfW = overlayW / 2;
        editBoxX = overlayX + halfW;

        inlineEditBox = new EditBox(this.font, editBoxX + 1, overlayY, halfW - 2, ITEM_HEIGHT,
                Component.literal("val"));
        inlineEditBox.setMaxLength(10);
        inlineEditBox.setValue("");  // start empty
        inlineEditBox.setTextColor(0xFF44FF44);
        inlineEditBox.setFocused(true);
        addRenderableWidget(inlineEditBox);
        setFocused(inlineEditBox);
    }

    private void startInlineEditAttrModifier(int index, int colX, int colW) {
        cancelInlineEdit();
        List<Map.Entry<String, AttributeData>> entries =
                new ArrayList<>(partAttributes[activeTab].entrySet());
        if (index < 0 || index >= entries.size()) return;

        AttributeData ad = entries.get(index).getValue();
        String mod = ad.getModifier() == null ? "addition" : ad.getModifier();
        inlineEditTarget = InlineEditTarget.ATTR_MODIFIER;
        inlineEditIndex = index;
        inlineOldValue = mod;

        // Overlay spans BOTH value + modifier columns (same as value edit)
        int valColX = colX - attrValColW;
        overlayX = valColX;
        overlayY = panelListTop + (index - attributeScrollOffset) * ITEM_HEIGHT;
        overlayW = attrValColW + colW;  // both columns

        // Left half: old value | Right half: clickable box
        int halfW = overlayW / 2;
        editBoxX = overlayX + halfW;

        // Cycle once on first click
        cycleAttributeModifier(index);
    }



    private void confirmInlineEdit() {
        if (inlineEditTarget == InlineEditTarget.NONE) return;

        if (inlineEditBox != null) {
            String text = inlineEditBox.getValue().trim();
            switch (inlineEditTarget) {
                case EFFECT_AMP -> {
                    try {
                        int amp = Math.max(0, Math.min(9, Integer.parseInt(text)));
                        if (inlineEditIndex < partEffects[activeTab].size()) {
                            partEffects[activeTab].get(inlineEditIndex).setAmplifier(amp);
                            setStatus("\u2714 Amplifier \u2192 " + amp, false);
                        }
                    } catch (NumberFormatException e) {
                        setStatus("Invalid number!", true);
                    }
                }
                case ATTR_VALUE -> {
                    try {
                        double val = Double.parseDouble(text);
                        List<Map.Entry<String, AttributeData>> entries =
                                new ArrayList<>(partAttributes[activeTab].entrySet());
                        if (inlineEditIndex < entries.size()) {
                            entries.get(inlineEditIndex).getValue().setValue(val);
                            setStatus("\u2714 Value \u2192 " + val, false);
                        }
                    } catch (NumberFormatException e) {
                        setStatus("Invalid number!", true);
                    }
                }
                default -> {}
            }
        }
        cancelInlineEdit();
    }

    private void cancelInlineEdit() {
        if (inlineEditBox != null) {
            removeWidget(inlineEditBox);
            inlineEditBox = null;
        }
        inlineEditTarget = InlineEditTarget.NONE;
        inlineEditIndex = -1;
        inlineOldValue = "";
    }

    private void cycleAttributeModifier(int index) {
        List<Map.Entry<String, AttributeData>> entries =
                new ArrayList<>(partAttributes[activeTab].entrySet());
        if (index < 0 || index >= entries.size()) return;

        AttributeData ad = entries.get(index).getValue();
        String current = ad.getModifier() == null ? "addition" : ad.getModifier().toLowerCase();
        String next = switch (current) {
            case "addition" -> "multiply_base";
            case "multiply_base" -> "multiply_total";
            default -> "addition";
        };
        ad.setModifier(next);
        setStatus("\u2714 Modifier \u2192 " + next, false);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Rendering
    // ════════════════════════════════════════════════════════════════════

    // ── Schritt 1: renderBackground() überschreiben für den Hintergrund ──
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Vanilla-Dimming (verdunkelt die Welt dahinter)
        super.renderBackground(g, mouseX, mouseY, partialTick);
        // Unser eigener Screen-Hintergrund
        g.fill(0, 0, this.width, this.height, 0xFF2A2A2A);
    }

    // ── Schritt 2: render() nur noch für Content + Status ────────────────
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1. Widgets (Buttons, EditBoxes) — ZUERST
        super.render(g, mouseX, mouseY, partialTick);

        // 2. Title
        g.drawString(this.font,
                Component.literal("✦ Set Editor: ").withStyle(ChatFormatting.BOLD)
                        .append(Component.literal(displayName)),
                PADDING, PADDING, 0xFFFFDD88, false);

        // 3. Tabs
        renderTabs(g, mouseX, mouseY);

        // 4. Panels
        renderPanel(g, leftPanelX,  contentTop, leftPanelW,  contentBottom - contentTop, "Effects",    true,  mouseX, mouseY);
        renderPanel(g, rightPanelX, contentTop, rightPanelW, contentBottom - contentTop, "Attributes", false, mouseX, mouseY);

        // 5. Inline edit overlay (on top of everything)
        renderInlineEditOverlay(g);


        if (!statusMessage.isEmpty()) {
            int color = statusIsError ? 0xFFFF4444 : 0xFF44CC44;
            int statusY = this.height - PADDING - 2;  // ganz unten
            int msgW = this.font.width(statusMessage) + 8;
            int msgX = this.width / 2 - msgW / 2;
            g.fill(msgX - 2, statusY - 10, msgX + msgW + 2, statusY + 2, 0xAA000000);
            g.drawString(this.font, statusMessage, msgX + 4, statusY - 8, color, false);
        }
    }


    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tabsTop = PADDING + 14;
        int tabsStartX = PADDING;

        for (int i = 0; i < PARTS; i++) {
            int tx = tabsStartX + i * (TAB_WIDTH + 2);
            int ty = tabsTop;
            boolean active = (i == activeTab);
            int bg = active ? TAB_ACTIVE_BG : TAB_INACTIVE_BG;

            g.fill(tx, ty, tx + TAB_WIDTH, ty + TAB_HEIGHT, bg);
            // Border
            g.fill(tx, ty, tx + TAB_WIDTH, ty + 1, PANEL_BORDER);
            g.fill(tx, ty, tx + 1, ty + TAB_HEIGHT, PANEL_BORDER);
            g.fill(tx + TAB_WIDTH - 1, ty, tx + TAB_WIDTH, ty + TAB_HEIGHT, PANEL_BORDER);
            if (!active) g.fill(tx, ty + TAB_HEIGHT - 1, tx + TAB_WIDTH, ty + TAB_HEIGHT, PANEL_BORDER);

            String label = (i + 1) + " Part" + (i + 1 > 1 ? "s" : "");
            int textX = tx + (TAB_WIDTH - this.font.width(label)) / 2;
            int textY = ty + (TAB_HEIGHT - 8) / 2;
            g.drawString(this.font, label, textX, textY, active ? COLOR_TEXT : 0xFF888877, false);

            // Badge: count of effects+attributes
            int count = partEffects[i].size() + partAttributes[i].size();
            if (count > 0) {
                String badge = String.valueOf(count);
                int bx = tx + TAB_WIDTH - this.font.width(badge) - 3;
                g.drawString(this.font, badge, bx, ty + 2, 0xFFFFAA00, false);
            }
        }
    }

    private void renderPanel(GuiGraphics g, int px, int py, int pw, int ph,
                             String title, boolean isEffects, int mouseX, int mouseY) {
        // Panel background + border
        g.fill(px, py, px + pw, py + ph, PANEL_BG);
        g.fill(px,          py,          px + pw,     py + 1,      PANEL_BORDER);
        g.fill(px,          py + ph - 1, px + pw,     py + ph,     PANEL_BORDER);
        g.fill(px,          py,          px + 1,       py + ph,     PANEL_BORDER);
        g.fill(px + pw - 1, py,          px + pw,     py + ph,     PANEL_BORDER);

        // Section title
        g.drawString(this.font,
                Component.literal(title).withStyle(ChatFormatting.BOLD),
                px + PADDING, py + PADDING, COLOR_TEXT, false);

        // List entries
        if (isEffects) {
            renderEffectList(g, mouseX, mouseY);
        } else {
            renderAttributeList(g, mouseX, mouseY);
        }
    }

    /**
     * Renders the dark inline-edit overlay bar on top of the active entry.
     * Shows: "old value" → [editable area]
     */
    private void renderInlineEditOverlay(GuiGraphics g) {
        if (inlineEditTarget == InlineEditTarget.NONE) return;

        // ── Dark overlay background with gold border ──
        g.fill(overlayX - 1, overlayY - 1, overlayX + overlayW + 1, overlayY + ITEM_HEIGHT + 1, 0xFFDAA520);
        g.fill(overlayX, overlayY, overlayX + overlayW, overlayY + ITEM_HEIGHT, 0xEE2A2A2A);

        int textY = overlayY + 3;
        int halfW = overlayW / 2;

        // ── Left half: original value in muted gray ──
        // Truncate if needed
        String oldDisplay = inlineOldValue;
        int maxOldW = halfW - 8;
        if (this.font.width(oldDisplay) > maxOldW) {
            while (this.font.width(oldDisplay + "..") > maxOldW && oldDisplay.length() > 2) {
                oldDisplay = oldDisplay.substring(0, oldDisplay.length() - 1);
            }
            oldDisplay += "..";
        }
        g.drawString(this.font, oldDisplay, overlayX + 4, textY, 0xFF999999, false);

        // ── Arrow " → " centered at the split ──
        String arrow = "\u2192";
        int arrowW = this.font.width(arrow);
        g.drawString(this.font, arrow, overlayX + halfW - arrowW / 2, textY, 0xFFDAA520, false);

        // ── Right half: for ATTR_MODIFIER draw clickable box ──
        if (inlineEditTarget == InlineEditTarget.ATTR_MODIFIER) {
            List<Map.Entry<String, AttributeData>> entries =
                    new ArrayList<>(partAttributes[activeTab].entrySet());
            if (inlineEditIndex < entries.size()) {
                AttributeData ad = entries.get(inlineEditIndex).getValue();
                String currentMod = ad.getModifier() == null ? "add" : ad.getModifier();

                int boxX = editBoxX;
                int boxW = halfW;

                // Box background
                g.fill(boxX, overlayY + 1, boxX + boxW - 1, overlayY + ITEM_HEIGHT - 1, 0xFF444444);

                // New modifier value in green, same textY
                g.drawString(this.font, currentMod, boxX + 4, textY, 0xFF44FF44, false);

                // Cycle hint right-aligned
                int hintX = boxX + boxW - this.font.width("\u21BB") - 5;
                g.drawString(this.font, "\u21BB", hintX, textY, 0xFF888888, false);
            }
        }
        // EditBox renders itself via widgets — no extra drawing needed for AMP/VALUE

        // ── Live preview line below overlay for ATTR_VALUE edits ──
        if (inlineEditTarget == InlineEditTarget.ATTR_VALUE && inlineEditBox != null) {
            List<Map.Entry<String, AttributeData>> entries =
                    new ArrayList<>(partAttributes[activeTab].entrySet());
            if (inlineEditIndex < entries.size()) {
                String attrId = entries.get(inlineEditIndex).getKey();
                AttributeData ad = entries.get(inlineEditIndex).getValue();
                String operation = ad.getModifier() != null ? ad.getModifier() : "addition";

                // Parse the current EditBox value for live preview
                String text = inlineEditBox.getValue().trim();
                double previewVal = ad.getValue(); // fallback to current
                try {
                    if (!text.isEmpty()) previewVal = Double.parseDouble(text);
                } catch (NumberFormatException ignored) {}

                String preview = AttributePreviewHelper.getPreviewText(attrId, previewVal, operation);
                int previewColor = AttributePreviewHelper.isNegative(previewVal, operation)
                        ? 0xFFFF6666 : 0xFF66FF66;

                // Draw preview below overlay
                int py = overlayY + ITEM_HEIGHT + 2;
                int pw = this.font.width(preview) + 8;
                int px = overlayX + (overlayW - pw) / 2;
                g.fill(px - 2, py - 1, px + pw + 2, py + 10, 0xDD000000);
                g.fill(px - 2, py - 1, px + pw + 2, py, 0xFFDAA520); // gold top border
                g.drawString(this.font, preview, px + 4, py + 1, previewColor, false);

                // Formula line for percent modifiers
                String formula = AttributePreviewHelper.getFormulaText(previewVal, operation);
                if (formula != null) {
                    int fy = py + 12;
                    int fw = this.font.width(formula) + 8;
                    int fx = overlayX + (overlayW - fw) / 2;
                    g.fill(fx - 2, fy - 1, fx + fw + 2, fy + 10, 0xCC000000);
                    g.drawString(this.font, formula, fx + 4, fy + 1, 0xFF999999, false);
                }
            }
        }

        // ── Live preview for ATTR_MODIFIER edits ──
        if (inlineEditTarget == InlineEditTarget.ATTR_MODIFIER) {
            List<Map.Entry<String, AttributeData>> entries =
                    new ArrayList<>(partAttributes[activeTab].entrySet());
            if (inlineEditIndex < entries.size()) {
                String attrId = entries.get(inlineEditIndex).getKey();
                AttributeData ad = entries.get(inlineEditIndex).getValue();
                String operation = ad.getModifier() != null ? ad.getModifier() : "addition";

                String preview = AttributePreviewHelper.getPreviewText(attrId, ad.getValue(), operation);
                int previewColor = AttributePreviewHelper.isNegative(ad.getValue(), operation)
                        ? 0xFFFF6666 : 0xFF66FF66;

                int py = overlayY + ITEM_HEIGHT + 2;
                int pw = this.font.width(preview) + 8;
                int px = overlayX + (overlayW - pw) / 2;
                g.fill(px - 2, py - 1, px + pw + 2, py + 10, 0xDD000000);
                g.fill(px - 2, py - 1, px + pw + 2, py, 0xFFDAA520);
                g.drawString(this.font, preview, px + 4, py + 1, previewColor, false);
            }
        }
    }




    private void renderEffectList(GuiGraphics g, int mouseX, int mouseY) {
        List<EffectData> effects = partEffects[activeTab];
        int lx = leftPanelX + PADDING;
        int ly = panelListTop;
        int lw = leftPanelW - 2 * PADDING;

        g.enableScissor(lx, ly, lx + lw, ly + panelListH);

        if (effects.isEmpty()) {
            g.drawString(this.font,
                    Component.literal("No effects.").withStyle(ChatFormatting.ITALIC),
                    lx + 2, ly + 3, COLOR_GRAY, false);
        } else {
            int visEnd = Math.min(effects.size(), effectScrollOffset + maxEffectVisible);
            for (int i = effectScrollOffset; i < visEnd; i++) {
                int ey = ly + (i - effectScrollOffset) * ITEM_HEIGHT;
                EffectData ed = effects.get(i);

                if (i == selectedEffectIndex) {
                    g.fill(lx, ey, lx + lw, ey + ITEM_HEIGHT, COLOR_SELECTED);
                } else if (mouseX >= lx && mouseX <= lx + lw && mouseY >= ey && mouseY < ey + ITEM_HEIGHT) {
                    g.fill(lx, ey, lx + lw, ey + ITEM_HEIGHT, COLOR_HOVER);
                }

                // Format: "minecraft:speed  Amp: 1"
                String effectName = shortName(ed.getEffect());
                String ampStr = "Amp: " + ed.getAmplifier();
                g.drawString(this.font, effectName, lx + 3, ey + 3, COLOR_TEXT, false);
                int ampX = lx + lw - this.font.width(ampStr) - 3;
                g.drawString(this.font, ampStr, ampX, ey + 3, COLOR_GRAY, false);
            }
        }

        g.disableScissor();
    }

    private void renderAttributeList(GuiGraphics g, int mouseX, int mouseY) {
        Map<String, AttributeData> attrs = partAttributes[activeTab];
        List<Map.Entry<String, AttributeData>> entries = new ArrayList<>(attrs.entrySet());
        int lx = rightPanelX + PADDING;
        int ly = panelListTop;
        int lw = rightPanelW - 2 * PADDING;

        int modColW = attrModColW;
        int valColW = attrValColW;
        int nameColW = attrNameColW;

        // Track hovered entry for formula tooltip
        int hoveredIndex = -1;

        g.enableScissor(lx, ly, lx + lw, ly + panelListH);

        if (entries.isEmpty()) {
            g.drawString(this.font,
                    Component.literal("No attributes.").withStyle(ChatFormatting.ITALIC),
                    lx + 2, ly + 3, COLOR_GRAY, false);
        } else {
            int visEnd = Math.min(entries.size(), attributeScrollOffset + maxAttrVisible);
            for (int i = attributeScrollOffset; i < visEnd; i++) {
                int ay = ly + (i - attributeScrollOffset) * ITEM_HEIGHT;
                Map.Entry<String, AttributeData> entry = entries.get(i);
                AttributeData ad = entry.getValue();
                String attrId = entry.getKey();
                String operation = ad.getModifier() != null ? ad.getModifier() : "addition";

                boolean isHovered = mouseX >= lx && mouseX <= lx + lw
                        && mouseY >= ay && mouseY < ay + ITEM_HEIGHT;

                if (i == selectedAttributeIndex) {
                    g.fill(lx, ay, lx + lw, ay + ITEM_HEIGHT, COLOR_SELECTED);
                } else if (isHovered) {
                    g.fill(lx, ay, lx + lw, ay + ITEM_HEIGHT, COLOR_HOVER);
                    hoveredIndex = i;
                }

                // Column 1: Formatted name (Title Case, left-aligned)
                String attrName = AttributePreviewHelper.formatAttributeShort(attrId);
                int maxNameW = nameColW - 6;
                if (this.font.width(attrName) > maxNameW) {
                    while (this.font.width(attrName + "..") > maxNameW && attrName.length() > 3) {
                        attrName = attrName.substring(0, attrName.length() - 1);
                    }
                    attrName += "..";
                }
                g.drawString(this.font, attrName, lx + 3, ay + 3, COLOR_TEXT, false);

                // Column 2: Human-readable preview (right-aligned in its column)
                String preview = AttributePreviewHelper.getPreviewText(attrId, ad.getValue(), operation);
                int previewColor = AttributePreviewHelper.isNegative(ad.getValue(), operation)
                        ? 0xFFCC3333 : COLOR_GREEN;
                int valColX = lx + nameColW;
                // Truncate preview if too wide for value column
                int maxPreviewW = valColW - 6;
                if (this.font.width(preview) > maxPreviewW) {
                    while (this.font.width(preview + "..") > maxPreviewW && preview.length() > 3) {
                        preview = preview.substring(0, preview.length() - 1);
                    }
                    preview += "..";
                }
                int previewTextX = valColX + valColW - this.font.width(preview) - 4;
                g.drawString(this.font, preview, previewTextX, ay + 3, previewColor, false);

                // Column 3: Modifier (left-aligned in its column)
                String modText = operation;
                int modColX = lx + nameColW + valColW;
                g.drawString(this.font, modText, modColX + 3, ay + 3, 0xFF887744, false);

                // Subtle column dividers
                g.fill(valColX, ay + 1, valColX + 1, ay + ITEM_HEIGHT - 1, 0x22000000);
                g.fill(modColX, ay + 1, modColX + 1, ay + ITEM_HEIGHT - 1, 0x22000000);
            }
        }

        g.disableScissor();

        // ── Formula tooltip when hovering a percent modifier ──
        int tooltipIndex = hoveredIndex >= 0 ? hoveredIndex : selectedAttributeIndex;
        if (tooltipIndex >= 0 && tooltipIndex < entries.size()) {
            Map.Entry<String, AttributeData> entry = entries.get(tooltipIndex);
            AttributeData ad = entry.getValue();
            String operation = ad.getModifier() != null ? ad.getModifier() : "addition";
            String formula = AttributePreviewHelper.getFormulaText(ad.getValue(), operation);

            if (formula != null) {
                // Draw formula line below the attribute panel area
                int formulaY = ly + panelListH + 2;
                int formulaW = this.font.width(formula) + 8;
                int formulaX = lx + (lw - formulaW) / 2;
                g.fill(formulaX - 2, formulaY - 1, formulaX + formulaW + 2, formulaY + 10, 0xCC000000);
                g.drawString(this.font, formula, formulaX + 4, formulaY + 1, 0xFF999999, false);
            }
        }
    }


    // ════════════════════════════════════════════════════════════════════
    //  Input
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ── Active inline edit? ──
        if (inlineEditTarget != InlineEditTarget.NONE) {
            // Check if click is inside the overlay
            boolean insideOverlay = mouseX >= overlayX && mouseX <= overlayX + overlayW
                    && mouseY >= overlayY - 1 && mouseY <= overlayY + ITEM_HEIGHT + 1;

            if (inlineEditTarget == InlineEditTarget.ATTR_MODIFIER) {
                if (insideOverlay && mouseX >= editBoxX) {
                    // Click on right half → cycle again
                    cycleAttributeModifier(inlineEditIndex);
                    return true;
                }
                // Click anywhere else → confirm and close
                confirmInlineEdit();
                // DON'T fall through — prevent accidental clicks
                return true;
            }

            // AMP / VALUE edits
            if (insideOverlay) {
                // Click inside overlay → let EditBox handle
                return super.mouseClicked(mouseX, mouseY, button);
            }
            // Click outside → CONFIRM (not cancel!)
            confirmInlineEdit();
            return true;
        }

        // ── Tab clicks ──
        int tabsTop = PADDING + 14;
        for (int i = 0; i < PARTS; i++) {
            int tx = PADDING + i * (TAB_WIDTH + 2);
            if (mouseX >= tx && mouseX <= tx + TAB_WIDTH
                    && mouseY >= tabsTop && mouseY <= tabsTop + TAB_HEIGHT) {
                cancelInlineEdit();
                activeTab = i;
                selectedEffectIndex = -1;
                selectedAttributeIndex = -1;
                effectScrollOffset = 0;
                attributeScrollOffset = 0;
                return true;
            }
        }

        // ── Effect list click ──
        int lx = leftPanelX + PADDING;
        int lw = leftPanelW - 2 * PADDING;
        if (mouseX >= lx && mouseX <= lx + lw
                && mouseY >= panelListTop && mouseY <= panelListTop + panelListH) {
            int idx = (int) ((mouseY - panelListTop) / ITEM_HEIGHT) + effectScrollOffset;
            if (idx >= 0 && idx < partEffects[activeTab].size()) {
                if (mouseX >= lx + lw - 45) {
                    startInlineEditEffectAmp(idx, lx, lw);
                } else {
                    selectedEffectIndex = idx;
                    selectedAttributeIndex = -1;
                }
            }
            return true;
        }

        // ── Attribute list click — fixed column zones ──
        int rx = rightPanelX + PADDING;
        int rw = rightPanelW - 2 * PADDING;
        if (mouseX >= rx && mouseX <= rx + rw
                && mouseY >= panelListTop && mouseY <= panelListTop + panelListH) {
            List<Map.Entry<String, AttributeData>> entries =
                    new ArrayList<>(partAttributes[activeTab].entrySet());
            int idx = (int) ((mouseY - panelListTop) / ITEM_HEIGHT) + attributeScrollOffset;
            if (idx >= 0 && idx < entries.size()) {
                int valColX = rx + attrNameColW;
                int modColX = rx + attrNameColW + attrValColW;

                if (mouseX >= modColX) {
                    startInlineEditAttrModifier(idx, modColX, attrModColW);
                } else if (mouseX >= valColX) {
                    startInlineEditAttrValue(idx, valColX, attrValColW);
                } else {
                    selectedAttributeIndex = idx;
                    selectedEffectIndex = -1;
                }
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }



    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int lx = leftPanelX + PADDING;
        int lw = leftPanelW - 2 * PADDING;
        if (mouseX >= lx && mouseX <= lx + lw
                && mouseY >= panelListTop && mouseY <= panelListTop + panelListH) {
            if (scrollY > 0) effectScrollOffset = Math.max(0, effectScrollOffset - 1);
            else effectScrollOffset = Math.min(
                    Math.max(0, partEffects[activeTab].size() - maxEffectVisible),
                    effectScrollOffset + 1);
            return true;
        }
        int rx = rightPanelX + PADDING;
        int rw = rightPanelW - 2 * PADDING;
        if (mouseX >= rx && mouseX <= rx + rw
                && mouseY >= panelListTop && mouseY <= panelListTop + panelListH) {
            List<Map.Entry<String, AttributeData>> entries =
                    new ArrayList<>(partAttributes[activeTab].entrySet());
            if (scrollY > 0) attributeScrollOffset = Math.max(0, attributeScrollOffset - 1);
            else attributeScrollOffset = Math.min(
                    Math.max(0, entries.size() - maxAttrVisible),
                    attributeScrollOffset + 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inlineEditTarget != InlineEditTarget.NONE) {
            if (keyCode == 257) { confirmInlineEdit(); return true; }  // Enter
            if (keyCode == 256) { cancelInlineEdit(); return true; }   // Escape
            // Modifier hat kein EditBox → Escape/Enter schließt
            if (inlineEditTarget == InlineEditTarget.ATTR_MODIFIER) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }



    // ════════════════════════════════════════════════════════════════════
    //  Popup flows — Add Effect
    // ════════════════════════════════════════════════════════════════════

    /**
     * Step 1: Pick a MobEffect via SearchableListPopup.
     * Step 2: Enter amplifier via a small inline EditBox popup.
     */
    private void openAddEffectPopup() {
        // Build effect entries from Minecraft's built-in registry
        List<SearchableListPopup.Entry<String>> effectEntries = new ArrayList<>();
        net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.forEach(effect -> {
            net.minecraft.resources.ResourceLocation rl =
                    net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (rl != null) {
                effectEntries.add(new SearchableListPopup.Entry<>(
                        rl.toString(),
                        Component.literal(rl.getPath().replace("_", " ")),
                        rl.toString()));
            }
        });
        effectEntries.sort(Comparator.comparing(e -> e.displayName().getString()));

        this.minecraft.setScreen(new SearchableListPopup<>(
                Component.literal("Select Effect"),
                this, effectEntries,
                effectId -> openAmplifierPopup(effectId)));
    }

    /**
     * Step 2: Ask for amplifier value (0–9).
     */
    private void openAmplifierPopup(String effectId) {
        this.minecraft.setScreen(new AmplifierInputScreen(this, effectId, amplifier -> {
            EffectData ed = new EffectData();
            ed.setEffect(effectId);
            ed.setAmplifier(amplifier);
            partEffects[activeTab].add(ed);
            setStatus("Effect added!", false);
        }));
    }

    // ════════════════════════════════════════════════════════════════════
    //  Popup flows — Add Attribute
    // ════════════════════════════════════════════════════════════════════

    private void openAddAttributePopup() {
        List<SearchableListPopup.Entry<String>> attrEntries = new ArrayList<>();
        net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.forEach(attr -> {
            net.minecraft.resources.ResourceLocation rl =
                    net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(attr);
            if (rl != null) {
                attrEntries.add(new SearchableListPopup.Entry<>(
                        rl.toString(),
                        Component.literal(rl.getPath().replace("_", " ")),
                        rl.toString()));
            }
        });
        attrEntries.sort(Comparator.comparing(e -> e.displayName().getString()));

        this.minecraft.setScreen(new SearchableListPopup<>(
                Component.literal("Select Attribute"),
                this, attrEntries,
                attrId -> openAttributeValuePopup(attrId)));
    }

    private void openAttributeValuePopup(String attrId) {
        this.minecraft.setScreen(new AttributeValueScreen(this, attrId, (value, modifier) -> {
            AttributeData ad = new AttributeData();
            ad.setValue(value);
            ad.setModifier(modifier);
            partAttributes[activeTab].put(attrId, ad);
            setStatus("Attribute added!", false);
        }));
    }

    /**
     * Opens a popup to select which part to copy effects & attributes FROM
     * into the currently active tab.
     */
    private void openCopyFromPartPopup() {
        List<SearchableListPopup.Entry<Integer>> entries = new ArrayList<>();

        for (int i = 0; i < PARTS; i++) {
            if (i == activeTab) continue;
            int effectCount = partEffects[i].size();
            int attrCount = partAttributes[i].size();
            String label = (i + 1) + " Part  ("
                    + effectCount + " effect" + (effectCount != 1 ? "s" : "") + ", "
                    + attrCount + " attr" + (attrCount != 1 ? "s" : "") + ")";
            entries.add(new SearchableListPopup.Entry<>(i, Component.literal(label)));
        }

        assert this.minecraft != null;
        this.minecraft.setScreen(new SearchableListPopup<>(
                Component.literal("Copy into " + (activeTab + 1) + " Part from:"),
                this,
                entries,
                source -> copyPartData(source, activeTab)
        ));
    }


    /**
     * Copies all effects and attributes from one part to another.
     * Replaces existing data in the target part.
     */
    private void copyPartData(int source, int target) {
        // Clear target
        partEffects[target].clear();
        partAttributes[target].clear();

        // Deep-copy effects
        for (EffectData ed : partEffects[source]) {
            EffectData copy = new EffectData();
            copy.setEffect(ed.getEffect());
            copy.setAmplifier(ed.getAmplifier());
            partEffects[target].add(copy);
        }

        // Deep-copy attributes
        for (Map.Entry<String, AttributeData> entry : partAttributes[source].entrySet()) {
            AttributeData ad = new AttributeData();
            ad.setValue(entry.getValue().getValue());
            ad.setModifier(entry.getValue().getModifier());
            partAttributes[target].put(entry.getKey(), ad);
        }

        // Reset selection
        selectedEffectIndex = -1;
        selectedAttributeIndex = -1;
        effectScrollOffset = 0;
        attributeScrollOffset = 0;

        setStatus("✔ Copied from " + (source + 1) + " Part → " + (target + 1) + " Part", false);
    }


    // ════════════════════════════════════════════════════════════════════
    //  Remove
    // ════════════════════════════════════════════════════════════════════

    private void removeSelectedEffect() {
        if (selectedEffectIndex >= 0 && selectedEffectIndex < partEffects[activeTab].size()) {
            partEffects[activeTab].remove(selectedEffectIndex);
            selectedEffectIndex = -1;
            setStatus("Effect removed.", false);
        }
    }

    private void removeSelectedAttribute() {
        List<String> keys = new ArrayList<>(partAttributes[activeTab].keySet());
        if (selectedAttributeIndex >= 0 && selectedAttributeIndex < keys.size()) {
            partAttributes[activeTab].remove(keys.get(selectedAttributeIndex));
            selectedAttributeIndex = -1;
            setStatus("Attribute removed.", false);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Save to JSON
    // ════════════════════════════════════════════════════════════════════

    private void saveToJson() {
        ArmorSetData data = new ArmorSetData();
        data.setDisplayName(displayName);

        Map<String, PartData> partsMap = new LinkedHashMap<>();
        for (int i = 0; i < PARTS; i++) {
            List<EffectData> effects = partEffects[i];
            Map<String, AttributeData> attrs = partAttributes[i];
            if (!effects.isEmpty() || !attrs.isEmpty()) {
                PartData pd = new PartData();
                pd.setEffects(new ArrayList<>(effects));
                pd.setAttributes(new LinkedHashMap<>(attrs));
                partsMap.put((i + 1) + "Part", pd);
            }
        }
        data.setParts(partsMap);

        // Resolve relative path within config/setweaver/set_armor/
        String relativePath;
        if (editorData != null) {
            // Wizard mode: resolveFilePath() returns e.g.
            //   "naturalist/3/zauberei__magiccloth_armor.json"
            //   "all_roles_all_Levels/zauberei__magiccloth_armor.json"
            relativePath = editorData.resolveFilePath();
        } else {
            // Legacy create/edit mode
            relativePath = setId + ".json";
        }

        String json = GSON.toJson(data);

        // Send to server for saving, reloading, and syncing to all clients
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new SaveSetPayload(relativePath, json)
        );

        setStatus("Saving to server: " + relativePath, false);
    }

    /** Saves the current set and returns directly to the main manager screen. */
    private void saveAndExit() {
        saveToJson();
        assert this.minecraft != null;
        this.minecraft.setScreen(mainScreen);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════

    private void setStatus(String msg, boolean isError) {
        this.statusMessage = msg;
        this.statusIsError = isError;
    }

    /** Shortens "minecraft:generic_max_health" → "generic_max_health" */
    private String shortName(String resourceLocation) {
        if (resourceLocation == null) return "?";
        int colon = resourceLocation.indexOf(':');
        return colon >= 0 ? resourceLocation.substring(colon + 1) : resourceLocation;
    }

    private String formatAttrValue(AttributeData ad) {
        String mod = ad.getModifier() != null ? ad.getModifier() : "add";
        String sign = ad.getValue() >= 0 ? "+" : "";
        return sign + String.format("%.2f", ad.getValue()) + " (" + mod + ")";
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
