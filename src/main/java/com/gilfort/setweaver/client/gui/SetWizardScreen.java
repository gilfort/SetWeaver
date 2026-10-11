package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.SetFiles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Set Wizard, step 1: choose tag, display name, role and level of a new set (or of a copy of an existing one), then
 * continue to the {@link SetEditorScreen}. An existing set with the same scope is opened for editing instead.
 */
public class SetWizardScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 260;
    private static final int PADDING = 14;
    private static final int LABEL_WIDTH = 90;
    private static final int ROW_HEIGHT = 26;
    private static final int FIELD_HEIGHT = 20;
    private static final int TITLE_HEIGHT = 16;
    private static final int MAX_LEVEL = 99;

    private final Screen parentScreen;
    /** Source of a copy, {@code null} when creating a new set. */
    private final SetEditorData sourceData;

    private String selectedTag;
    private String selectedRole = ArmorSetDataRegistry.WILDCARD_ROLE;
    private int selectedLevel = ArmorSetDataRegistry.WILDCARD_LEVEL;
    private String displayName = "";

    private Button tagButton;
    private Button roleButton;
    private Button levelButton;
    private EditBox displayNameBox;
    private int panelX, panelY;
    private Component statusMessage;
    private int statusColor = GuiUtil.COLOR_GRAY;

    /**
     * Creates the wizard for a new set.
     *
     * @param parentScreen screen to return to
     */
    public SetWizardScreen(Screen parentScreen) {
        this(parentScreen, null);
    }

    /**
     * Creates the wizard for a copy of an existing set; the form starts with the source's values.
     *
     * @param parentScreen screen to return to
     * @param sourceData   the set to copy, {@code null} for a new set
     */
    public SetWizardScreen(Screen parentScreen, SetEditorData sourceData) {
        super(Component.translatable(sourceData == null ? "gui.setweaver.wizard.title" : "gui.setweaver.wizard.copy_title"));
        this.parentScreen = parentScreen;
        this.sourceData = sourceData;
        if (sourceData != null) {
            selectedTag = sourceData.getTag();
            selectedRole = sourceData.getRole();
            selectedLevel = sourceData.getLevel();
            displayName = sourceData.getDisplayName();
        }
    }

    private int rowY(int row) {
        return panelY + PADDING + TITLE_HEIGHT + 6 + row * ROW_HEIGHT;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;
        int fieldX = panelX + PADDING + LABEL_WIDTH;
        int fieldW = PANEL_WIDTH - 2 * PADDING - LABEL_WIDTH;

        tagButton = addRenderableWidget(Button.builder(tagLabel(), btn -> selectTag())
                .bounds(fieldX, rowY(0), fieldW, FIELD_HEIGHT).build());

        displayNameBox = new EditBox(this.font, fieldX, rowY(1), fieldW, FIELD_HEIGHT,
                Component.translatable("gui.setweaver.wizard.display_name"));
        displayNameBox.setMaxLength(64);
        displayNameBox.setHint(Component.translatable("gui.setweaver.wizard.display_name.hint").withStyle(ChatFormatting.GRAY));
        displayNameBox.setValue(displayName);
        displayNameBox.setResponder(value -> displayName = value);
        addRenderableWidget(displayNameBox);

        roleButton = addRenderableWidget(Button.builder(roleLabel(selectedRole), btn -> selectRole())
                .bounds(fieldX, rowY(2), fieldW, FIELD_HEIGHT).build());
        levelButton = addRenderableWidget(Button.builder(levelLabel(selectedLevel), btn -> selectLevel())
                .bounds(fieldX, rowY(3), fieldW, FIELD_HEIGHT).build());

        int btnY = panelY + PANEL_HEIGHT - PADDING - 20;
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.back"), btn -> onClose())
                .bounds(panelX + PADDING, btnY, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.next"), btn -> next())
                .bounds(panelX + PANEL_WIDTH - PADDING - 80, btnY, 80, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GuiUtil.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = panelX + PADDING;
        graphics.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x, panelY + PADDING, GuiUtil.COLOR_TEXT, false);
        String[] labels = {"gui.setweaver.wizard.tag", "gui.setweaver.wizard.display_name", "gui.setweaver.wizard.role", "gui.setweaver.wizard.level"};
        for (int i = 0; i < labels.length; i++) {
            graphics.drawString(this.font, GuiUtil.tr(labels[i]), x, rowY(i) + 6, GuiUtil.COLOR_TEXT, false);
        }
        if (statusMessage != null) {
            int statusY = rowY(4) + 8;
            int statusW = PANEL_WIDTH - 2 * PADDING;
            graphics.fill(x, statusY, x + statusW, statusY + 28, 0x22000000);
            int lineY = statusY + 4;
            for (String line : GuiUtil.wrap(this.font, statusMessage.getString(), statusW - 8)) {
                graphics.drawString(this.font, line, x + 4, lineY, statusColor, false);
                lineY += 10;
            }
        }
    }

    private void selectTag() {
        List<SearchableListPopup.Entry<String>> entries = GuiUtil.armorItemTags().stream()
                .map(tag -> new SearchableListPopup.Entry<>(tag.location().toString(), Component.literal(tag.location().toString())))
                .toList();
        this.minecraft.setScreen(new SearchableListPopup<>(Component.translatable("gui.setweaver.wizard.select_tag"), this, entries,
                selected -> {
                    selectedTag = selected;
                    tagButton.setMessage(tagLabel());
                    statusMessage = null;
                }));
    }

    private void selectRole() {
        List<SearchableListPopup.Entry<String>> entries = new ArrayList<>();
        entries.add(new SearchableListPopup.Entry<>(ArmorSetDataRegistry.WILDCARD_ROLE, roleLabel(ArmorSetDataRegistry.WILDCARD_ROLE), "*"));
        for (String role : ArmorSetDataRegistry.client().getRoles()) {
            entries.add(new SearchableListPopup.Entry<>(role, Component.literal(role), role));
        }
        this.minecraft.setScreen(new SearchableListPopup<>(Component.translatable("gui.setweaver.wizard.select_role"), this, entries,
                selected -> {
                    selectedRole = selected;
                    roleButton.setMessage(roleLabel(selected));
                    statusMessage = null;
                },
                typed -> {
                    String role = ArmorSetDataRegistry.normalizeRole(typed);
                    return SetFiles.isValidRole(role) ? role : null;
                }));
    }

    private void selectLevel() {
        List<SearchableListPopup.Entry<Integer>> entries = new ArrayList<>();
        entries.add(new SearchableListPopup.Entry<>(ArmorSetDataRegistry.WILDCARD_LEVEL, levelLabel(ArmorSetDataRegistry.WILDCARD_LEVEL), "*"));
        for (int level = 1; level <= MAX_LEVEL; level++) {
            entries.add(new SearchableListPopup.Entry<>(level, levelLabel(level), String.valueOf(level)));
        }
        this.minecraft.setScreen(new SearchableListPopup<>(Component.translatable("gui.setweaver.wizard.select_level"), this, entries,
                selected -> {
                    selectedLevel = selected;
                    levelButton.setMessage(levelLabel(selected));
                    statusMessage = null;
                }));
    }

    /** Validates the form and opens the editor. */
    private void next() {
        if (selectedTag == null || selectedTag.isBlank()) {
            setStatus(Component.translatable("gui.setweaver.wizard.error.no_tag"), GuiUtil.COLOR_ERROR);
            return;
        }
        boolean wildRole = ArmorSetDataRegistry.WILDCARD_ROLE.equals(selectedRole);
        if (!wildRole && selectedLevel == ArmorSetDataRegistry.WILDCARD_LEVEL) {
            setStatus(Component.translatable("gui.setweaver.wizard.error.role_needs_level"), GuiUtil.COLOR_ERROR);
            return;
        }

        SetEditorData editorData = new SetEditorData();
        editorData.setTag(selectedTag);
        editorData.setRole(selectedRole);
        editorData.setLevel(selectedLevel);

        var existing = ArmorSetDataRegistry.client().getExact(selectedTag, selectedRole, selectedLevel);
        if (existing != null) {
            // Editing an existing scope; a copy template does not overwrite it
            editorData.loadParts(existing.data());
        } else if (sourceData != null) {
            editorData.copyPartsFrom(sourceData);
        }
        if (!displayName.isBlank()) {
            editorData.setDisplayName(displayName.trim());
        }
        // "Save & Exit" in the editor returns straight to the manager, "Back" to this wizard
        this.minecraft.setScreen(new SetEditorScreen(this, parentScreen, editorData));
    }

    private Component tagLabel() {
        return selectedTag != null ? Component.literal(selectedTag) : Component.translatable("gui.setweaver.wizard.click_to_select");
    }

    private static Component roleLabel(String role) {
        if (ArmorSetDataRegistry.WILDCARD_ROLE.equals(role)) {
            return Component.translatable("gui.setweaver.wizard.all_roles");
        }
        return Component.literal(role.isEmpty() ? role : role.substring(0, 1).toUpperCase(Locale.ROOT) + role.substring(1));
    }

    private static Component levelLabel(int level) {
        return level == ArmorSetDataRegistry.WILDCARD_LEVEL
                ? Component.translatable("gui.setweaver.wizard.all_levels")
                : Component.translatable("gui.setweaver.level", level);
    }

    private void setStatus(Component message, int color) {
        statusMessage = message;
        statusColor = color;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && !displayNameBox.isFocused()) {
            next();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
