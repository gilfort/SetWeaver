package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.client.ArmorSetTooltipHandler;
import com.gilfort.setweaver.network.DeleteSetPayload;
import com.gilfort.setweaver.network.ReloadRequestPayload;
import com.gilfort.setweaver.network.ValidateRequestPayload;
import com.gilfort.setweaver.seteffects.ArmorSetData;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry.SetEntry;
import com.gilfort.setweaver.seteffects.SetFiles;
import com.gilfort.setweaver.seteffects.ValidationResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sets Manager: lists all loaded sets (left) with their details (right), validates the set files on the server,
 * browses item tags and opens the wizard, editor, tag creator and package editor.
 */
public class SetsManagerScreen extends Screen {

    private static final int PANEL_PADDING = 10;
    private static final int PANEL_MARGIN = 12;
    private static final int DIVIDER_WIDTH = 4;
    private static final int ICON_SIZE = 10;
    private static final int ICON_GAP = 2;

    private static final int COLOR_SPECIFIC = 0xFF55FF55;
    private static final int COLOR_ALL_ROLES = 0xFF5555FF;
    private static final int COLOR_UNIVERSAL = 0xFFFFFF55;
    private static final int COLOR_EFFECT = 0xFF7733AA;
    private static final int COLOR_ATTRIBUTE = 0xFF338833;

    /** One row of the set list: a section header, a tag header or a selectable scope entry. */
    private record ListRow(Kind kind, String text, SetEntry entry) {
        enum Kind { SECTION, TAG, SCOPE }
    }

    /** One line of the right panel. */
    private record DetailLine(String text, int color, int indent, boolean bold) {
        static DetailLine header(String text) {
            return new DetailLine(text, GuiUtil.COLOR_TEXT, 0, true);
        }

        static DetailLine text(String text, int color) {
            return new DetailLine(text, color, 0, false);
        }

        static DetailLine indented(String text, int color) {
            return new DetailLine(text, color, 8, false);
        }

        static DetailLine blank() {
            return new DetailLine("", 0, 0, false);
        }
    }

    // Layout
    private int leftPanelX, leftPanelW, rightPanelX, rightPanelW, panelH;
    private int leftX, leftY, leftW, leftH;
    private int rightX, rightY, rightW, rightH;
    private int lineHeight, maxLinesLeft, maxLinesRight;

    // Set list
    private final List<ListRow> rows = new ArrayList<>();
    private int selectedIndex = -1;
    private int leftScroll = 0;
    private int rightScroll = 0;
    private int[] editBounds, copyBounds, deleteBounds;

    // Validation
    private List<ValidationResult> validationResults;
    private boolean showingValidation = false;

    // Tag browser
    private boolean showingTagBrowser = false;
    private EditBox tagSearchBox;
    private Button createTagButton;
    private List<TagKey<Item>> allTags = List.of();
    private List<TagKey<Item>> filteredTags = List.of();
    private int tagSelectedIndex = -1;
    private int tagLeftScroll = 0;
    private int tagRightScroll = 0;
    private List<Item> selectedTagItems = List.of();
    private int[] editTagBounds;

    /** Creates the manager. */
    public SetsManagerScreen() {
        super(Component.translatable("gui.setweaver.manager.title"));
    }

    @Override
    protected void init() {
        int totalW = this.width - 2 * PANEL_MARGIN;
        int contentH = this.height - 2 * PANEL_MARGIN - 28 - 8;
        leftPanelX = PANEL_MARGIN;
        leftPanelW = (int) (totalW * 0.35f) - DIVIDER_WIDTH / 2;
        rightPanelX = PANEL_MARGIN + leftPanelW + DIVIDER_WIDTH;
        rightPanelW = totalW - leftPanelW - DIVIDER_WIDTH;
        panelH = contentH;

        leftX = leftPanelX + PANEL_PADDING;
        leftY = PANEL_MARGIN + PANEL_PADDING;
        leftW = leftPanelW - 2 * PANEL_PADDING;
        leftH = contentH - 2 * PANEL_PADDING;
        rightX = rightPanelX + PANEL_PADDING;
        rightY = leftY;
        rightW = rightPanelW - 2 * PANEL_PADDING;
        rightH = leftH;
        lineHeight = Math.max(10, this.height / 40);
        maxLinesLeft = leftH / lineHeight;
        maxLinesRight = rightH / lineHeight;

        rebuildRows(null);

        String[] keys = {"reload", "validate", "tags", "packages", "create", "close"};
        Runnable[] actions = {this::reload, this::validate, this::toggleTagBrowser,
                () -> this.minecraft.gui.setScreen(new PackageEditorScreen(this)),
                () -> this.minecraft.gui.setScreen(new SetWizardScreen(this)), this::onClose};
        int buttonW = 55;
        int startX = (this.width - (buttonW * keys.length + 8 * (keys.length - 1))) / 2;
        int buttonY = PANEL_MARGIN + contentH + 4;
        for (int i = 0; i < keys.length; i++) {
            Runnable action = actions[i];
            addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.manager." + keys[i]), btn -> action.run())
                    .bounds(startX + i * (buttonW + 8), buttonY, buttonW, 20).build());
        }

        tagSearchBox = new EditBox(this.font, leftX, leftY, leftW, 14, Component.translatable("gui.setweaver.search"));
        tagSearchBox.setMaxLength(100);
        tagSearchBox.setHint(Component.translatable("gui.setweaver.manager.filter_tags").withStyle(ChatFormatting.GRAY));
        tagSearchBox.setResponder(this::filterTags);
        tagSearchBox.setVisible(showingTagBrowser);
        addRenderableWidget(tagSearchBox);

        createTagButton = Button.builder(Component.translatable("gui.setweaver.manager.create_tag"),
                btn -> this.minecraft.gui.setScreen(new TagCreatorScreen(this))).bounds(leftX, leftY + leftH - 20, leftW, 20).build();
        createTagButton.visible = showingTagBrowser;
        addRenderableWidget(createTagButton);

        allTags = GuiUtil.armorItemTags();
        filterTags(tagSearchBox.getValue());
    }

    // ─── Data ────────────────────────────────────────────────────────────

    /** Called when the server sent new set definitions; rebuilds the list and keeps the selection. */
    public void onRegistrySynced() {
        SetEntry selected = selectedEntry();
        rebuildRows(selected == null ? null : SetFiles.relativePath(selected.role(), selected.level(), selected.tag()));
    }

    /**
     * Shows validation results from the server in the right panel.
     *
     * @param results one result per set file
     */
    public void showValidationResults(List<ValidationResult> results) {
        validationResults = results;
        showingValidation = true;
        showingTagBrowser = false;
        updateTagBrowserWidgets();
        rightScroll = 0;
    }

    private SetEntry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < rows.size() ? rows.get(selectedIndex).entry() : null;
    }

    private void rebuildRows(String keepPath) {
        rows.clear();
        List<SetEntry> active = new ArrayList<>();
        List<SetEntry> inactive = new ArrayList<>();
        for (SetEntry entry : ArmorSetDataRegistry.client().getAllEntries()) {
            (entry.data().hasBonuses() ? active : inactive).add(entry);
        }
        addGrouped(active);
        if (!inactive.isEmpty()) {
            rows.add(new ListRow(ListRow.Kind.SECTION, "── " + GuiUtil.tr("gui.setweaver.manager.not_active") + " ──", null));
            addGrouped(inactive);
        }

        selectedIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            SetEntry entry = rows.get(i).entry();
            if (entry != null && (keepPath == null || keepPath.equals(SetFiles.relativePath(entry.role(), entry.level(), entry.tag())))) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex < 0 && keepPath != null) {
            rebuildRows(null);
            return;
        }
        leftScroll = Math.clamp(leftScroll, 0, Math.max(0, rows.size() - maxLinesLeft));
        rightScroll = 0;
    }

    private void addGrouped(List<SetEntry> entries) {
        Map<String, List<SetEntry>> byTag = new LinkedHashMap<>();
        entries.stream().sorted(Comparator.comparing(SetEntry::tag))
                .forEach(e -> byTag.computeIfAbsent(e.tag(), k -> new ArrayList<>()).add(e));
        byTag.forEach((tag, group) -> {
            rows.add(new ListRow(ListRow.Kind.TAG, ArmorSetTooltipHandler.setName(null, tag), null));
            group.stream()
                    .sorted(Comparator.comparingInt(SetsManagerScreen::scopePriority).thenComparing(SetEntry::role).thenComparingInt(SetEntry::level))
                    .forEach(e -> rows.add(new ListRow(ListRow.Kind.SCOPE, e.scopeLabel().getString(), e)));
        });
    }

    private static int scopePriority(SetEntry entry) {
        if (entry.isWildcardRole() && entry.isWildcardLevel()) {
            return 0;
        }
        return entry.isWildcardRole() ? 1 : 2;
    }

    private static int scopeColor(SetEntry entry) {
        if (entry.isWildcardRole() && entry.isWildcardLevel()) {
            return COLOR_UNIVERSAL;
        }
        return entry.isWildcardRole() ? COLOR_ALL_ROLES : COLOR_SPECIFIC;
    }

    // ─── Rendering ───────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiUtil.drawPanel(graphics, leftPanelX, PANEL_MARGIN, leftPanelW, panelH);
        GuiUtil.drawPanel(graphics, rightPanelX, PANEL_MARGIN, rightPanelW, panelH);
        int divX = leftPanelX + leftPanelW + 1;
        graphics.fill(divX, PANEL_MARGIN + 4, divX + DIVIDER_WIDTH - 2, PANEL_MARGIN + panelH - 4, GuiUtil.PANEL_DIVIDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (showingTagBrowser) {
            renderTagList(graphics, mouseX, mouseY);
            renderTagItems(graphics, mouseX, mouseY);
        } else {
            renderSetList(graphics, mouseX, mouseY);
            renderDetailLines(graphics, showingValidation && validationResults != null ? validationLines() : selectedSetLines());
        }
    }

    private void renderSetList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        editBounds = copyBounds = deleteBounds = null;
        if (rows.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.setweaver.manager.no_sets").withStyle(ChatFormatting.ITALIC),
                    leftX + 5, leftY + 5, GuiUtil.COLOR_GRAY, false);
            graphics.text(this.font, GuiUtil.tr("gui.setweaver.manager.no_sets_hint"), leftX + 5, leftY + 5 + lineHeight, GuiUtil.COLOR_GRAY, false);
            return;
        }
        graphics.enableScissor(leftX, leftY, leftX + leftW, leftY + leftH);
        int end = Math.min(rows.size(), leftScroll + maxLinesLeft);
        for (int i = leftScroll; i < end; i++) {
            ListRow row = rows.get(i);
            int y = leftY + (i - leftScroll) * lineHeight;
            if (i == selectedIndex) {
                graphics.fill(leftX, y - 1, leftX + leftW, y + lineHeight - 1, GuiUtil.COLOR_SELECTED);
            } else if (row.kind() == ListRow.Kind.SCOPE && GuiUtil.inside(mouseX, mouseY, leftX, y, leftW, lineHeight)) {
                graphics.fill(leftX, y - 1, leftX + leftW, y + lineHeight - 1, GuiUtil.COLOR_HOVER);
            }
            switch (row.kind()) {
                case SECTION -> graphics.text(this.font, Component.literal(GuiUtil.truncate(this.font, row.text(), leftW - 10))
                        .withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC), leftX + 3, y, GuiUtil.COLOR_GRAY, false);
                case TAG -> graphics.text(this.font, Component.literal(GuiUtil.truncate(this.font, row.text(), leftW - 10))
                        .withStyle(ChatFormatting.BOLD), leftX + 3, y, GuiUtil.COLOR_TEXT, false);
                case SCOPE -> {
                    graphics.text(this.font, "● ", leftX + 8, y, scopeColor(row.entry()), false);
                    int x = leftX + 8 + this.font.width("● ");
                    graphics.text(this.font, GuiUtil.truncate(this.font, row.text(), leftW - (x - leftX) - 40), x, y, GuiUtil.COLOR_TEXT, false);
                }
            }
            if (i == selectedIndex && row.kind() == ListRow.Kind.SCOPE) {
                int iconX = leftX + leftW - 3 * (ICON_SIZE + ICON_GAP) - 6;
                editBounds = drawIcon(graphics, "✎", iconX, y, 0xFF00AA00, 0xFF336633, mouseX, mouseY);
                copyBounds = drawIcon(graphics, "❏", iconX + ICON_SIZE + ICON_GAP, y, 0xFF0055CC, 0xFF335577, mouseX, mouseY);
                deleteBounds = drawIcon(graphics, "✖", iconX + 2 * (ICON_SIZE + ICON_GAP), y, 0xFFFF0000, 0xFF773333, mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        GuiUtil.drawScrollbar(graphics, leftX + leftW - 3, leftY, leftH, rows.size(), maxLinesLeft, leftScroll);
    }

    private int[] drawIcon(GuiGraphicsExtractor graphics, String icon, int x, int y, int hoverColor, int color, int mouseX, int mouseY) {
        int[] bounds = {x, y, ICON_SIZE, ICON_SIZE};
        graphics.text(this.font, icon, x, y, GuiUtil.hit(bounds, mouseX, mouseY) ? hoverColor : color, false);
        return bounds;
    }

    private List<DetailLine> selectedSetLines() {
        List<DetailLine> lines = new ArrayList<>();
        SetEntry entry = selectedEntry();
        if (entry == null) {
            lines.add(DetailLine.text(GuiUtil.tr("gui.setweaver.manager.select_set"), GuiUtil.COLOR_GRAY));
            return lines;
        }
        ArmorSetData data = entry.data();
        lines.add(DetailLine.header(ArmorSetTooltipHandler.setName(data, entry.tag())));
        lines.add(DetailLine.blank());
        lines.add(DetailLine.text(GuiUtil.tr("gui.setweaver.manager.tag", entry.tag()), GuiUtil.COLOR_TEXT));
        lines.add(DetailLine.text(GuiUtil.tr("gui.setweaver.manager.scope", entry.scopeLabel().getString()), GuiUtil.COLOR_TEXT));
        lines.add(DetailLine.blank());
        if (data.getParts() == null || data.getParts().isEmpty()) {
            lines.add(DetailLine.text(GuiUtil.tr("gui.setweaver.manager.no_thresholds"), GuiUtil.COLOR_GRAY));
            return lines;
        }
        data.getParts().entrySet().stream()
                .sorted(Comparator.comparingInt(e -> ArmorSetData.parseThreshold(e.getKey())))
                .forEach(e -> {
                    ArmorSetData.PartData part = e.getValue();
                    lines.add(DetailLine.header("══ " + GuiUtil.pieces(ArmorSetData.parseThreshold(e.getKey())) + " ══"));
                    if (part.getEffects() != null) {
                        part.getEffects().forEach(effect -> lines.add(DetailLine.indented("✦ " + AttributePreviewHelper.effectName(effect.getEffect())
                                + " " + AttributePreviewHelper.roman(effect.getAmplifier() + 1), COLOR_EFFECT)));
                    }
                    if (part.getAttributes() != null) {
                        part.getAttributes().forEach(a -> lines.add(DetailLine.indented("▸ "
                                + AttributePreviewHelper.getPreviewText(a.getAttribute(), a.getValue(), a.getModifier()), COLOR_ATTRIBUTE)));
                    }
                    if (part.isEmpty()) {
                        lines.add(DetailLine.indented(GuiUtil.tr("gui.setweaver.manager.no_bonuses"), GuiUtil.COLOR_GRAY));
                    }
                    lines.add(DetailLine.blank());
                });
        return lines;
    }

    private List<DetailLine> validationLines() {
        List<DetailLine> lines = new ArrayList<>();
        lines.add(DetailLine.header(GuiUtil.tr("gui.setweaver.manager.validation_title")));
        lines.add(DetailLine.blank());
        int ok = 0, warnings = 0, errors = 0;
        for (ValidationResult result : validationResults) {
            int color;
            String icon;
            switch (result.status()) {
                case OK -> {
                    color = COLOR_SPECIFIC;
                    icon = "✔ ";
                    ok++;
                }
                case WARNING -> {
                    color = 0xFFCC9900;
                    icon = "⚠ ";
                    warnings++;
                }
                default -> {
                    color = 0xFFFF5555;
                    icon = "✘ ";
                    errors++;
                }
            }
            lines.add(DetailLine.text(icon + result.filePath(), color));
            for (Component message : result.messages()) {
                GuiUtil.wrap(this.font, message.getString(), rightW - 20).forEach(l -> lines.add(DetailLine.indented(l, GuiUtil.COLOR_TEXT)));
            }
        }
        lines.add(DetailLine.blank());
        lines.add(DetailLine.header(GuiUtil.tr("gui.setweaver.manager.validation_summary", ok, warnings, errors)));
        return lines;
    }

    private void renderDetailLines(GuiGraphicsExtractor graphics, List<DetailLine> lines) {
        rightScroll = Math.clamp(rightScroll, 0, Math.max(0, lines.size() - maxLinesRight));
        graphics.enableScissor(rightX, rightY, rightX + rightW, rightY + rightH);
        int y = rightY - rightScroll * lineHeight;
        for (DetailLine line : lines) {
            if (y >= rightY - lineHeight && y < rightY + rightH && !line.text().isEmpty()) {
                Component text = line.bold() ? Component.literal(line.text()).withStyle(ChatFormatting.BOLD) : Component.literal(line.text());
                graphics.text(this.font, text, rightX + line.indent() + 3, y, line.color(), false);
            }
            y += lineHeight;
        }
        graphics.disableScissor();
        GuiUtil.drawScrollbar(graphics, rightX + rightW - 3, rightY, rightH, lines.size(), maxLinesRight, rightScroll);
    }

    // ─── Tag browser ─────────────────────────────────────────────────────

    private int tagListY() {
        return leftY + 18;
    }

    private int tagListH() {
        return leftH - 18 - 24;
    }

    private void renderTagList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int listY = tagListY();
        int listH = tagListH();
        int maxLines = listH / lineHeight;
        graphics.enableScissor(leftX, listY, leftX + leftW, listY + listH);
        if (filteredTags.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.setweaver.manager.no_tags").withStyle(ChatFormatting.ITALIC),
                    leftX + 3, listY + 3, GuiUtil.COLOR_GRAY, false);
        }
        int end = Math.min(filteredTags.size(), tagLeftScroll + maxLines);
        for (int i = tagLeftScroll; i < end; i++) {
            int y = listY + (i - tagLeftScroll) * lineHeight;
            if (i == tagSelectedIndex) {
                graphics.fill(leftX, y - 1, leftX + leftW, y + lineHeight - 1, GuiUtil.COLOR_SELECTED);
            } else if (GuiUtil.inside(mouseX, mouseY, leftX, y, leftW, lineHeight)) {
                graphics.fill(leftX, y - 1, leftX + leftW, y + lineHeight - 1, GuiUtil.COLOR_HOVER);
            }
            TagKey<Item> tag = filteredTags.get(i);
            String ns = tag.location().getNamespace() + ":";
            graphics.text(this.font, ns, leftX + 3, y, GuiUtil.COLOR_GRAY, false);
            int nsW = this.font.width(ns);
            graphics.text(this.font, GuiUtil.truncate(this.font, tag.location().getPath(), leftW - nsW - 10), leftX + 3 + nsW, y, GuiUtil.COLOR_TEXT, false);
        }
        graphics.disableScissor();
        GuiUtil.drawScrollbar(graphics, leftX + leftW - 3, listY, listH, filteredTags.size(), maxLines, tagLeftScroll);
    }

    private void renderTagItems(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        editTagBounds = null;
        graphics.enableScissor(rightX, rightY, rightX + rightW, rightY + rightH);
        if (tagSelectedIndex < 0 || tagSelectedIndex >= filteredTags.size()) {
            graphics.text(this.font, Component.translatable("gui.setweaver.manager.select_tag").withStyle(ChatFormatting.ITALIC),
                    rightX + 5, rightY + 5, GuiUtil.COLOR_GRAY, false);
            graphics.disableScissor();
            return;
        }
        int totalLines = 3 + selectedTagItems.size() * 2;
        tagRightScroll = Math.clamp(tagRightScroll, 0, Math.max(0, totalLines - maxLinesRight));
        TagKey<Item> tag = filteredTags.get(tagSelectedIndex);
        int y = rightY - tagRightScroll * lineHeight;
        graphics.text(this.font, Component.literal("#" + tag.location()).withStyle(ChatFormatting.BOLD), rightX + 3, y, GuiUtil.COLOR_TEXT, false);
        y += lineHeight;
        graphics.text(this.font, GuiUtil.tr("gui.setweaver.manager.item_count", selectedTagItems.size()), rightX + 3, y, GuiUtil.COLOR_TEXT, false);
        // Tags of this mod's namespace are usually created in the editor and can be edited there
        if (SetWeaver.MODID.equals(tag.location().getNamespace())) {
            String label = GuiUtil.tr("gui.setweaver.manager.edit_tag");
            int x = rightX + rightW - this.font.width(label) - 5;
            editTagBounds = new int[]{x, y, this.font.width(label), lineHeight};
            graphics.text(this.font, label, x, y, GuiUtil.hit(editTagBounds, mouseX, mouseY) ? 0xFF0088CC : 0xFF336699, false);
        }
        y += lineHeight * 2;
        for (Item item : selectedTagItems) {
            if (y >= rightY - lineHeight && y < rightY + rightH) {
                graphics.item(new ItemStack(item), rightX + 3, y - 1);
                graphics.text(this.font, new ItemStack(item).getHoverName().getString(), rightX + 22, y, GuiUtil.COLOR_TEXT, false);
            }
            y += lineHeight;
            if (y >= rightY - lineHeight && y < rightY + rightH) {
                graphics.text(this.font, BuiltInRegistries.ITEM.getKey(item).toString(), rightX + 22, y, GuiUtil.COLOR_GRAY, false);
            }
            y += lineHeight;
        }
        graphics.disableScissor();
        GuiUtil.drawScrollbar(graphics, rightX + rightW - 3, rightY, rightH, totalLines, maxLinesRight, tagRightScroll);
    }

    private void toggleTagBrowser() {
        showingTagBrowser = !showingTagBrowser;
        showingValidation = false;
        updateTagBrowserWidgets();
        if (showingTagBrowser) {
            setFocused(tagSearchBox);
        }
    }

    private void updateTagBrowserWidgets() {
        if (tagSearchBox != null) {
            tagSearchBox.setVisible(showingTagBrowser);
            createTagButton.visible = showingTagBrowser;
        }
    }

    private void filterTags(String filter) {
        String lower = filter.toLowerCase(Locale.ROOT);
        filteredTags = allTags.stream().filter(t -> t.location().toString().contains(lower)).toList();
        tagSelectedIndex = -1;
        tagLeftScroll = 0;
        tagRightScroll = 0;
        selectedTagItems = List.of();
    }

    private void selectTag(int index) {
        if (index >= 0 && index < filteredTags.size()) {
            tagSelectedIndex = index;
            tagRightScroll = 0;
            selectedTagItems = GuiUtil.itemsInTag(filteredTags.get(index));
        }
    }

    // ─── Actions ─────────────────────────────────────────────────────────

    private void reload() {
        ClientPacketDistributor.sendToServer(new ReloadRequestPayload());
    }

    private void validate() {
        ClientPacketDistributor.sendToServer(new ValidateRequestPayload());
    }

    private void editSelected() {
        SetEntry entry = selectedEntry();
        if (entry != null) {
            this.minecraft.gui.setScreen(new SetEditorScreen(this, this, SetEditorData.of(entry)));
        }
    }

    private void copySelected() {
        SetEntry entry = selectedEntry();
        if (entry != null) {
            this.minecraft.gui.setScreen(new SetWizardScreen(this, SetEditorData.of(entry)));
        }
    }

    private void deleteSelected() {
        SetEntry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        String path = SetFiles.relativePath(entry.role(), entry.level(), entry.tag());
        this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ClientPacketDistributor.sendToServer(new DeleteSetPayload(path));
            }
            this.minecraft.gui.setScreen(this);
        }, Component.translatable("gui.setweaver.manager.delete_title"), Component.translatable("gui.setweaver.manager.delete_question", path)));
    }

    private void editSelectedTag() {
        if (tagSelectedIndex < 0 || tagSelectedIndex >= filteredTags.size()) {
            return;
        }
        TagKey<Item> tag = filteredTags.get(tagSelectedIndex);
        List<String> ids = selectedTagItems.stream().map(i -> BuiltInRegistries.ITEM.getKey(i).toString()).toList();
        this.minecraft.gui.setScreen(new TagCreatorScreen(this, tag.location().getNamespace(), tag.location().getPath(), ids));
    }

    // ─── Input ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (showingTagBrowser) {
            if (GuiUtil.hit(editTagBounds, mouseX, mouseY)) {
                editSelectedTag();
                return true;
            }
            if (GuiUtil.inside(mouseX, mouseY, leftX, tagListY(), leftW, tagListH())) {
                selectTag((int) ((mouseY - tagListY()) / lineHeight) + tagLeftScroll);
                return true;
            }
            return false;
        }
        if (GuiUtil.hit(editBounds, mouseX, mouseY)) {
            editSelected();
            return true;
        }
        if (GuiUtil.hit(copyBounds, mouseX, mouseY)) {
            copySelected();
            return true;
        }
        if (GuiUtil.hit(deleteBounds, mouseX, mouseY)) {
            deleteSelected();
            return true;
        }
        if (GuiUtil.inside(mouseX, mouseY, leftX, leftY, leftW, leftH)) {
            int line = (int) ((mouseY - leftY) / lineHeight) + leftScroll;
            if (line < rows.size() && rows.get(line).kind() == ListRow.Kind.SCOPE) {
                selectedIndex = line;
                rightScroll = 0;
                showingValidation = false;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : 1;
        boolean overLeft = GuiUtil.inside(mouseX, mouseY, leftX, leftY, leftW, leftH);
        boolean overRight = GuiUtil.inside(mouseX, mouseY, rightX, rightY, rightW, rightH);
        if (showingTagBrowser) {
            if (overLeft) {
                tagLeftScroll = Math.clamp(tagLeftScroll + step, 0, Math.max(0, filteredTags.size() - tagListH() / lineHeight));
                return true;
            }
            if (overRight) {
                tagRightScroll = Math.max(0, tagRightScroll + step);
                return true;
            }
        } else if (overLeft) {
            leftScroll = Math.clamp(leftScroll + step, 0, Math.max(0, rows.size() - maxLinesLeft));
            return true;
        } else if (overRight) {
            rightScroll = Math.max(0, rightScroll + step);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!showingTagBrowser && (event.isUp() || event.isDown())) {
            moveSelection(event.isUp() ? -1 : 1);
            return true;
        }
        return super.keyPressed(event);
    }

    private void moveSelection(int direction) {
        if (rows.isEmpty()) {
            return;
        }
        int index = selectedIndex;
        for (int step = 0; step < rows.size(); step++) {
            index = Math.floorMod(index + direction, rows.size());
            if (rows.get(index).kind() == ListRow.Kind.SCOPE) {
                selectedIndex = index;
                rightScroll = 0;
                showingValidation = false;
                if (index < leftScroll) {
                    leftScroll = index;
                } else if (index >= leftScroll + maxLinesLeft) {
                    leftScroll = index - maxLinesLeft + 1;
                }
                return;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
