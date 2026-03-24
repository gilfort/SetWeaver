package com.gilfort.setweaver.guis;

import com.gilfort.setweaver.seteffects.ArmorSetData;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Set Wizard — Step 1: Tag & Scope selection.
 *
 * <p>A centered form panel where the user selects:</p>
 * <ul>
 *   <li><b>Tag</b> — which item tag this set applies to (searchable popup)</li>
 *   <li><b>Display Name</b> — optional human-readable name for tooltips</li>
 *   <li><b>Role</b> — school/class scope (* = all, or specific role)</li>
 *   <li><b>Level</b> — Level scope (* = all, or 1–99)</li>
 * </ul>
 *
 * <p>On "Next →", performs an existence check against
 * {@link ArmorSetDataRegistry} and proceeds to Step 2 (SetEditorScreen)
 * with a pre-populated {@link SetEditorData}.</p>
 *
 * <p>Styled consistently with {@link SetsManagerScreen} (Beige/Parchment theme).</p>
 *
 * @see SetEditorData
 * @see SearchableListPopup
 * @see ArmorSetDataRegistry
 */
@OnlyIn(Dist.CLIENT)
public class SetWizardScreen extends Screen {

    // ──── Color Scheme (consistent with SetsManagerScreen) ──────────────
    private static final int PANEL_BG       = 0xFFF5F0E0; // Beige/Parchment
    private static final int PANEL_BORDER   = 0xFF8B7355; // Warm brown frame
    private static final int COLOR_TEXT     = 0xFF000000;  // Black text
    private static final int COLOR_GRAY     = 0xFF444444;  // Muted labels
    private static final int COLOR_ERROR    = 0xFFCC3333;  // Red error text
    private static final int COLOR_SUCCESS  = 0xFF338833;  // Green success text
    private static final int COLOR_INFO     = 0xFF3355AA;  // Blue info text
    private static final int STATUS_BG      = 0x22000000;  // Status area background

    // ──── Layout constants ──────────────────────────────────────────────
    private static final int PANEL_WIDTH    = 340;
    private static final int PANEL_HEIGHT   = 260;
    private static final int PADDING        = 14;
    private static final int LABEL_WIDTH    = 90;  // Width reserved for labels
    private static final int ROW_HEIGHT     = 26;  // Vertical spacing between rows
    private static final int FIELD_HEIGHT   = 20;  // Button/EditBox height

    // ──── References ───────────────────────────────────────────────────
    private final Screen parentScreen;

    /**
     * When non-null, the wizard is in "copy" mode: part configs from this source
     * are applied to the new set (unless the target scope already exists).
     */
    private final SetEditorData sourceData;

    // ──── Selected values ───────────────────────────────────────────────
    private String selectedTag   = null;   // e.g. "zauberei:magiccloth_armor"
    private String selectedRole = ArmorSetDataRegistry.WILDCARD_ROLE; // "*"
    private int    selectedLevel  = ArmorSetDataRegistry.WILDCARD_LEVEL;  // -1
    private String savedDisplayName = ""; // persists across init() calls

    // ──── Widgets ───────────────────────────────────────────────────────
    private Button tagButton;
    private EditBox displayNameBox;
    private Button roleButton;
    private Button levelButton;
    private Button nextButton;

    // ──── Panel position (computed in init) ────────────────────────────
    private int panelX, panelY;

    // ──── Status message ───────────────────────────────────────────────
    private String statusMessage  = null;
    private int    statusColor    = COLOR_GRAY;

    // ══════════════════════════════════════════════════════════════════
    //  Constructor
    // ══════════════════════════════════════════════════════════════════

    /**
     * Creates the wizard Step 1 screen for creating a new set from scratch.
     *
     * @param parentScreen the screen to return to on "Back" (typically SetsManagerScreen)
     */
    public SetWizardScreen(Screen parentScreen) {
        super(Component.literal("Set Wizard — Step 1"));
        this.parentScreen = parentScreen;
        this.sourceData = null;
    }

    /**
     * Creates the wizard Step 1 screen in "copy" mode.
     * The form is pre-filled with the source set's tag, display name, role and level.
     * The user can change scope (role/level) before proceeding; part configs are
     * copied from {@code sourceData} into the new set unless the target scope already exists.
     *
     * @param parentScreen the screen to return to on "Back"
     * @param sourceData   the existing set to use as a copy template
     */
    public SetWizardScreen(Screen parentScreen, SetEditorData sourceData) {
        super(Component.literal("Set Wizard — Copy Set"));
        this.parentScreen = parentScreen;
        this.sourceData = sourceData;
        // Pre-fill from source
        this.selectedTag = sourceData.getTag();
        this.selectedRole = sourceData.getRole();
        this.selectedLevel = sourceData.getLevel();
        this.savedDisplayName = sourceData.getDisplayName();
    }

    // ══════════════════════════════════════════════════════════════════
    //  Init
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();

        // Center panel on screen
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        // Content area inside panel
        int contentX = panelX + PADDING;
        int contentW = PANEL_WIDTH - 2 * PADDING;
        int fieldX   = contentX + LABEL_WIDTH;  // Fields start after labels
        int fieldW   = contentW - LABEL_WIDTH;  // Remaining width for fields

        // Row positions (below title)
        int titleH = 16;
        int row1Y = panelY + PADDING + titleH + 6;   // Tag
        int row2Y = row1Y + ROW_HEIGHT;                // Display Name
        int row3Y = row2Y + ROW_HEIGHT;                // role
        int row4Y = row3Y + ROW_HEIGHT;                // Level

        // ---- Row 1: Tag selector button ----
        tagButton = Button.builder(
                        Component.literal(selectedTag != null ? selectedTag : "Click to select..."),
                        btn -> onSelectTag())
                .bounds(fieldX, row1Y, fieldW, FIELD_HEIGHT)
                .build();
        addRenderableWidget(tagButton);

        // ---- Row 2: Display Name text field ----
        displayNameBox = new EditBox(this.font,
                fieldX, row2Y, fieldW, FIELD_HEIGHT,
                Component.literal("Display Name"));
        displayNameBox.setMaxLength(64);
        displayNameBox.setHint(
                Component.literal("Optional (e.g. Magic Robes)").withStyle(ChatFormatting.GRAY));
        displayNameBox.setValue(savedDisplayName);
        displayNameBox.setResponder(value -> savedDisplayName = value);
        addRenderableWidget(displayNameBox);

        // ---- Row 3: role selector button ----
        roleButton = Button.builder(
                        Component.literal(formatRoleLabel(selectedRole)),
                        btn -> onSelectRole())
                .bounds(fieldX, row3Y, fieldW, FIELD_HEIGHT)
                .build();
        addRenderableWidget(roleButton);

        // ---- Row 4: Level selector button ----
        levelButton = Button.builder(
                        Component.literal(formatLevelLabel(selectedLevel)),
                        btn -> onSelectLevel())
                .bounds(fieldX, row4Y, fieldW, FIELD_HEIGHT)
                .build();
        addRenderableWidget(levelButton);

        // ---- Bottom buttons: Back / Next ----
        int btnW = 80;
        int btnH = 20;
        int btnY = panelY + PANEL_HEIGHT - PADDING - btnH;
        int btnSpacing = 12;

        addRenderableWidget(Button.builder(
                        Component.literal("← Back"), btn -> onBack())
                .bounds(panelX + PADDING, btnY, btnW, btnH)
                .build());

        nextButton = Button.builder(
                        Component.literal("Next →"), btn -> onNext())
                .bounds(panelX + PANEL_WIDTH - PADDING - btnW, btnY, btnW, btnH)
                .build();
        addRenderableWidget(nextButton);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Rendering
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Vanilla blur + dim
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        // Draw centered panel
        drawPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int contentX = panelX + PADDING;
        int titleH = 16;
        int row1Y = panelY + PADDING + titleH + 6;
        int row2Y = row1Y + ROW_HEIGHT;
        int row3Y = row2Y + ROW_HEIGHT;
        int row4Y = row3Y + ROW_HEIGHT;

        // Title
        String titleText = sourceData != null
                ? "Copy Set — Step 1: New Scope"
                : "Set Wizard — Step 1: Tag & Scope";
        graphics.drawString(this.font,
                Component.literal(titleText).withStyle(s -> s.withBold(true)),
                panelX + PADDING, panelY + PADDING, COLOR_TEXT, false);

        // Row labels
        graphics.drawString(this.font, "Tag:",          contentX, row1Y + 6, COLOR_TEXT, false);
        graphics.drawString(this.font, "Display Name:", contentX, row2Y + 6, COLOR_TEXT, false);
        graphics.drawString(this.font, "Role:",          contentX, row3Y + 6, COLOR_TEXT, false);
        graphics.drawString(this.font, "Level:",         contentX, row4Y + 6, COLOR_TEXT, false);

        // Status message area
        if (statusMessage != null) {
            int statusY = row4Y + ROW_HEIGHT + 8;
            int statusW = PANEL_WIDTH - 2 * PADDING;
            int statusH = 28;

            // Background tint
            graphics.fill(contentX, statusY, contentX + statusW, statusY + statusH, STATUS_BG);

            // Word-wrap the status message
            List<String> lines = wrapText(statusMessage, statusW - 8);
            int lineY = statusY + 4;
            for (String line : lines) {
                graphics.drawString(this.font, line, contentX + 4, lineY, statusColor, false);
                lineY += 10;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Selector actions (open SearchableListPopup)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Opens a SearchableListPopup with item tags that contain at least one armor piece.
     */
    private void onSelectTag() {
        List<SearchableListPopup.Entry<String>> entries =
                BuiltInRegistries.ITEM.getTagNames()
                        .filter(tagKey -> {
                            // Only include tags that contain at least one ArmorItem
                            var tag = BuiltInRegistries.ITEM.getOrCreateTag(tagKey);
                            return tag.stream().anyMatch(h ->
                                    h.value() instanceof net.minecraft.world.item.ArmorItem);
                        })
                        .sorted(Comparator.comparing(t -> t.location().toString()))
                        .map(tagKey -> {
                            String loc = tagKey.location().toString();
                            return new SearchableListPopup.Entry<>(
                                    loc,
                                    Component.literal(loc),
                                    loc);
                        })
                        .collect(Collectors.toList());

        this.minecraft.setScreen(new SearchableListPopup<>(
                Component.literal("Select Item Tag"),
                this, entries,
                selected -> {
                    selectedTag = selected;
                    tagButton.setMessage(Component.literal(selected));
                    clearStatus();
                }
        ));
    }

    /**
     * Opens a SearchableListPopup with "* (All Roles)" + all known roles.
     */
    private void onSelectRole() {
        List<SearchableListPopup.Entry<String>> entries = new ArrayList<>();

        // First entry: wildcard
        entries.add(new SearchableListPopup.Entry<>(
                ArmorSetDataRegistry.WILDCARD_ROLE,
                Component.literal("★ All Roles (*)"),
                "all roles * wildcard"));

        // All registered roles, sorted alphabetically
        ArmorSetDataRegistry.getRoles().stream()
                .sorted()
                .forEach(m -> entries.add(new SearchableListPopup.Entry<>(
                        m,
                        Component.literal(capitalizeFirst(m)),
                        m)));

        this.minecraft.setScreen(new SearchableListPopup<>(
                Component.literal("Select Role"),
                this, entries,
                selected -> {
                    selectedRole = selected;
                    roleButton.setMessage(Component.literal(formatRoleLabel(selected)));
                    clearStatus();
                },
                true
        ));

    }

    /**
     * Opens a SearchableListPopup with "* (All Levels)" + Levels 1–99.
     */
    private void onSelectLevel() {
        List<SearchableListPopup.Entry<Integer>> entries = new ArrayList<>();

        // First entry: wildcard
        entries.add(new SearchableListPopup.Entry<>(
                ArmorSetDataRegistry.WILDCARD_LEVEL,
                Component.literal("★ All Levels (*)"),
                "all Levels * wildcard"));

        // Levels 1–99
        for (int y = 1; y <= 99; y++) {
            String label = "Level " + y;
            entries.add(new SearchableListPopup.Entry<>(
                    y,
                    Component.literal(label),
                    label + " " + y));
        }

        this.minecraft.setScreen(new SearchableListPopup<>(
                Component.literal("Select Level"),
                this, entries,
                selected -> {
                    selectedLevel = selected;
                    levelButton.setMessage(Component.literal(formatLevelLabel(selected)));
                    clearStatus();
                }
        ));
    }

    // ══════════════════════════════════════════════════════════════════
    //  Navigation actions
    // ══════════════════════════════════════════════════════════════════

    /**
     * Validates inputs, checks for existing set, builds SetEditorData,
     * and proceeds to Step 2.
     */
    private void onNext() {
        // ---- 1. Validate required fields ----
        if (selectedTag == null || selectedTag.isBlank()) {
            setStatus("Please select a Tag first.", COLOR_ERROR);
            return;
        }

        // Role/Level rule: if Role is specific, Level must also be specific
        boolean wildRole = ArmorSetDataRegistry.WILDCARD_ROLE.equals(selectedRole);
        boolean wildLevel  = (selectedLevel == ArmorSetDataRegistry.WILDCARD_LEVEL);
        if (!wildRole && wildLevel) {
            setStatus("If a Role is selected, Level must also be specified.", COLOR_ERROR);
            return;
        }

        // ---- 2. Build SetEditorData ----
        SetEditorData editorData = new SetEditorData();
        editorData.setTag(selectedTag);
        editorData.setDisplayName(displayNameBox.getValue().trim());
        editorData.setRole(selectedRole);
        editorData.setLevel(selectedLevel);

        // ---- 3. Existence check (exact match only) ----
        ArmorSetData existing = findExactMatch(selectedTag, selectedRole, selectedLevel);

        if (existing != null) {
            // Load existing data into editor (overrides any copy template)
            editorData.loadFrom(selectedTag, selectedRole, selectedLevel, existing);
            setStatus("Set already exists — loading for editing.", COLOR_INFO);
        } else if (sourceData != null) {
            // Copy mode: apply part configs from source set into the new scope
            editorData.copyPartsFrom(sourceData);
        }

        // ---- 4. Proceed to Step 2 ----
        proceedToStep2(editorData);
    }

    /**
     * Navigates back to the parent screen.
     */
    private void onBack() {
        this.minecraft.setScreen(parentScreen);
    }

    /**
     * Proceeds to Step 2 (SetEditorScreen) with the given editor data.
     *
     * <p>TODO: Phase 4 — Replace placeholder with:
     * {@code this.minecraft.setScreen(new SetEditorScreen(this, editorData));}</p>
     */
    private void proceedToStep2(SetEditorData editorData) {
        assert this.minecraft != null;
        // Pass parentScreen as mainScreen so "Save & Exit" skips the wizard and goes straight to the manager.
        this.minecraft.setScreen(new SetEditorScreen(this, parentScreen, editorData));
    }

    // ══════════════════════════════════════════════════════════════════
    //  Existence check — exact match against registry
    // ══════════════════════════════════════════════════════════════════

    /**
     * Checks for an EXACT match in the registry (no fallback to wildcards).
     * This is different from {@link ArmorSetDataRegistry#getData} which does
     * priority-based fallback.
     *
     * @return the existing ArmorSetData, or null if no exact match
     */
    private static ArmorSetData findExactMatch(String tag, String role, int level) {
        for (ArmorSetDataRegistry.SetEntry entry : ArmorSetDataRegistry.getAllEntries()) {
            if (entry.tag().equals(tag)
                    && entry.role().equals(role)
                    && entry.level() == level) {
                return entry.data();
            }
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Helper: panel drawing (matches SetsManagerScreen)
    // ══════════════════════════════════════════════════════════════════

    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, PANEL_BG);
        graphics.fill(x,         y,         x + w,     y + 1,     PANEL_BORDER); // top
        graphics.fill(x,         y + h - 1, x + w,     y + h,     PANEL_BORDER); // bottom
        graphics.fill(x,         y,         x + 1,     y + h,     PANEL_BORDER); // left
        graphics.fill(x + w - 1, y,         x + w,     y + h,     PANEL_BORDER); // right
    }

    // ══════════════════════════════════════════════════════════════════
    //  Helper: formatting
    // ══════════════════════════════════════════════════════════════════

    /**
     * Formats the Role value for display on the button.
     */
    private static String formatRoleLabel(String role) {
        if (ArmorSetDataRegistry.WILDCARD_ROLE.equals(role)) {
            return "★ All Roles (*)";
        }
        return capitalizeFirst(role);
    }

    /**
     * Formats the level value for display on the button.
     */
    private static String formatLevelLabel(int level) {
        if (level == ArmorSetDataRegistry.WILDCARD_LEVEL) {
            return "★ All Levels (*)";
        }
        return "Level " + level;
    }

    /**
     * Capitalizes the first letter of a string.
     */
    private static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Helper: status messages
    // ══════════════════════════════════════════════════════════════════

    private void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    private void clearStatus() {
        this.statusMessage = null;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Helper: text wrapping
    // ══════════════════════════════════════════════════════════════════

    /**
     * Simple word-wrap for the status area.
     */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.length() > 0
                    && this.font.width(current + " " + word) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                if (current.length() > 0) current.append(" ");
                current.append(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());

        return lines;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Input handling
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape → back
        if (keyCode == 256) {
            onBack();
            return true;
        }
        // Enter → next (if not typing in EditBox)
        if (keyCode == 257 && !displayNameBox.isFocused()) {
            onNext();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}