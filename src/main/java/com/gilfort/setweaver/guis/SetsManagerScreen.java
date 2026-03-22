package com.gilfort.setweaver.guis;

import com.gilfort.setweaver.seteffects.SetWeaverReloadListener;
import com.gilfort.setweaver.seteffects.ArmorSetData;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sets Manager GUI — a visual interface for browsing, inspecting,
 * validating and managing armor set effect definitions.
 *
 * <p>Uses a configbook.png background (1600×1024 original resolution)
 * displayed at 75% of the player's screen, dynamically scaled to
 * maintain aspect ratio regardless of GUI scale setting.</p>
 *
 * <p>Left page: scrollable list of all loaded set definitions.
 * Right page: detail view of the currently selected set.</p>
 *
 * @see ArmorSetDataRegistry
 * @see SetWeaverReloadListener
 */
@OnlyIn(Dist.CLIENT)
public class SetsManagerScreen extends Screen {

    // ─── Texture ─────────────────────────────────────────────────────────
    // ─── Panel colors ─────────────────────────────────────────────────────
    private static final int PANEL_BG        = 0xFFF5F0E0; // Beige/Pergament
    private static final int PANEL_BORDER    = 0xFF8B7355; // Warmes Braun (Rahmen)
    private static final int PANEL_DIVIDER   = 0xFFB8A080; // Helle Trennlinie Mitte
    private static final int PANEL_PADDING   = 10;         // Innenabstand
    private static final int PANEL_MARGIN    = 12;         // Außenabstand zum Bildschirmrand
    private static final int DIVIDER_WIDTH   = 4;          // Breite der Mitteltrennlinie

    // Panel layout (computed in init())
    private int leftPanelX, leftPanelW;
    private int rightPanelX, rightPanelW;
    private int panelH;

    // ─── Tag Browser state ───────────────────────────────────────────────
    private boolean showingTagBrowser = false;
    private EditBox tagSearchBox;
    private List<TagKey<Item>> allTags = new ArrayList<>();
    private List<TagKey<Item>> filteredTags = new ArrayList<>();
    private int tagSelectedIndex = -1;
    private int tagLeftScrollOffset = 0;
    private int tagRightScrollOffset = 0;
    private List<Item> selectedTagItems = new ArrayList<>();


    // Text colors — alles schwarz
    private static final int COLOR_HEADER    = 0xFF000000; // Schwarz (war: Dark brown)
    private static final int COLOR_TEXT      = 0xFF000000; // Schwarz (war: Dark brown text)
    private static final int COLOR_GRAY      = 0xFF444444; // Dunkles Grau für Hints

    // Icon/Scope-Farben — bleiben bunt
    private static final int COLOR_SPECIFIC  = 0xFF55FF55; // Grün       ●
    private static final int COLOR_ALL_ROLE = 0xFF5555FF; // Blau       ●
    private static final int COLOR_UNIVERSAL = 0xFFFFFF55; // Gelb       ●
    private static final int COLOR_EFFECT    = 0xFF7733AA; // Lila       (Effekte)
    private static final int COLOR_ATTRIBUTE = 0xFF338833; // Grün       (Attribute)
    private static final int COLOR_SELECTED  = 0x44FFCC00; // Gold       (Highlight)
    private static final int COLOR_HOVER     = 0x22FFCC00; // Gold-Hover


    // ─── Dynamic layout (computed in init()) ─────────────────────────────
    private int renderWidth, renderHeight;
    private int guiLeft, guiTop;
    private int leftX, leftY, leftW, leftH;
    private int rightX, rightY, rightW, rightH;
    private int lineHeight;
    private int maxLinesLeft, maxLinesRight;

    // ─── Data ────────────────────────────────────────────────────────────
    private List<ListEntry> listEntries = new ArrayList<>();
    private int selectedIndex = -1;
    private int leftScrollOffset = 0;
    private int rightScrollOffset = 0;

    // ─── Validation state ────────────────────────────────────────────────
    private boolean showingValidation = false;
    private List<SetWeaverReloadListener.ValidationResult> validationResults = null;

    // ─── Inner types ─────────────────────────────────────────────────────

    /**
     * Represents one row in the left-side list.
     * Can be either a TAG header (grouping) or a SCOPE entry (clickable).
     */
    private record ListEntry(EntryType type, String tag, String scopeLabel,
                             String role, int level, ArmorSetData data) {
        enum EntryType { TAG_HEADER, SCOPE_ENTRY }

        int getColor() {
            if (type == EntryType.TAG_HEADER) return COLOR_HEADER;
            boolean wildRole = ArmorSetDataRegistry.WILDCARD_ROLE.equals(role);
            boolean wildLevel = level == ArmorSetDataRegistry.WILDCARD_LEVEL;
            if (wildRole && wildLevel) return COLOR_UNIVERSAL;
            if (wildRole) return COLOR_ALL_ROLE;
            return COLOR_SPECIFIC;
        }

        String getDisplayText() {
            if (type == EntryType.TAG_HEADER) {
                // Format tag as readable name
                return formatTagName(tag);
            }
            return "  \u25CF " + scopeLabel; // ● bullet + scope
        }

        private static String formatTagName(String tagString) {
            try {
                ResourceLocation loc = ResourceLocation.parse(tagString);
                String path = loc.getPath();
                path = path.replaceAll("_(armou?rs?|set|equipment|gear)$", "");
                String[] words = path.split("_");
                StringBuilder sb = new StringBuilder();
                for (String word : words) {
                    if (!word.isEmpty()) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(Character.toUpperCase(word.charAt(0)));
                        if (word.length() > 1) sb.append(word.substring(1));
                    }
                }
                return sb.toString();
            } catch (Exception e) {
                return tagString;
            }
        }
    }

    // ─── Constructor ─────────────────────────────────────────────────────

    public SetsManagerScreen() {
        super(Component.literal("Sets Manager"));
    }

    // ─── Init ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        int margin = PANEL_MARGIN;
        int divider = DIVIDER_WIDTH;
        int totalW = this.width - 2 * margin;
        int totalH = this.height - 2 * margin;

        // Buttons am unteren Rand reservieren
        int buttonAreaH = 28;
        int contentH = totalH - buttonAreaH - 8;

        // Linke Seite: 35% der Breite
        int leftPanelW = (int)(totalW * 0.35f) - divider / 2;
        int leftPanelX = margin;
        int leftPanelY = margin;

        // Rechte Seite: 65% der Breite
        int rightPanelX = margin + leftPanelW + divider;
        int rightPanelW = totalW - leftPanelW - divider;
        int rightPanelY = margin;

        // Speichern für renderBackground
        guiLeft   = leftPanelX;
        guiTop    = leftPanelY;
        renderWidth  = totalW;
        renderHeight = contentH;

        // Content-Bereiche (mit Padding)
        leftX = leftPanelX + PANEL_PADDING;
        leftY = leftPanelY + PANEL_PADDING;
        leftW = leftPanelW - 2 * PANEL_PADDING;
        leftH = contentH - 2 * PANEL_PADDING;

        rightX = rightPanelX + PANEL_PADDING;
        rightY = rightPanelY + PANEL_PADDING;
        rightW = rightPanelW - 2 * PANEL_PADDING;
        rightH = contentH - 2 * PANEL_PADDING;

        // Für renderBackground speichern
        this.leftPanelX  = leftPanelX;
        this.leftPanelW  = leftPanelW;
        this.rightPanelX = rightPanelX;
        this.rightPanelW = rightPanelW;
        this.panelH      = contentH;

        lineHeight = Math.max(10, this.height / 40);
        maxLinesLeft  = leftH  / lineHeight;
        maxLinesRight = rightH / lineHeight;

        buildListEntries();

        // ── Buttons ──────────────────────────────────────────────────────
        int buttonW = 55;
        int buttonH = 20;
        int buttonY = margin + contentH + 4;
        int buttonSpacing = 8;
        int totalButtonsW = buttonW * 7 + buttonSpacing * 6;
        int buttonStartX = (this.width - totalButtonsW) / 2;

        addRenderableWidget(Button.builder(Component.literal("Reload"), btn -> onReload())
                .bounds(buttonStartX, buttonY, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(Component.literal("Validate"), btn -> onValidate())
                .bounds(buttonStartX + (buttonW + buttonSpacing), buttonY, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(Component.literal("Tags"), btn -> onToggleTagBrowser())
                .bounds(buttonStartX + (buttonW + buttonSpacing) * 2, buttonY, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(Component.literal("Edit"), btn -> onEditSet())
                .bounds(buttonStartX + (buttonW + buttonSpacing) * 3, buttonY, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(Component.literal("Copy"), btn -> onCopySet())
                .bounds(buttonStartX + (buttonW + buttonSpacing) * 4, buttonY, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(Component.literal("Create"), btn -> onCreateSet())
                .bounds(buttonStartX + (buttonW + buttonSpacing) * 5, buttonY, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(buttonStartX + (buttonW + buttonSpacing) * 6, buttonY, buttonW, buttonH).build());


// ── Tag Search Box (unsichtbar bis Tag-Browser aktiv) ────────────
        tagSearchBox = new EditBox(this.font, leftX, leftY, leftW, 14, Component.literal("Search tags..."));
        tagSearchBox.setMaxLength(100);
        tagSearchBox.setHint(Component.literal("Filter tags...").withStyle(ChatFormatting.GRAY));
        tagSearchBox.setResponder(this::onTagFilterChanged);
        tagSearchBox.setVisible(false);
        addRenderableWidget(tagSearchBox);

// ── Tag-Daten vorladen ───────────────────────────────────────────
        allTags = BuiltInRegistries.ITEM.getTagNames()
                .sorted(Comparator.comparing(t -> t.location().toString()))
                .collect(Collectors.toCollection(ArrayList::new));
        filteredTags = new ArrayList<>(allTags);

    }


    // ─── Data building ───────────────────────────────────────────────────

    private void buildListEntries() {
        listEntries.clear();
        selectedIndex = -1;
        leftScrollOffset = 0;
        rightScrollOffset = 0;
        showingValidation = false;
        validationResults = null;

        List<ArmorSetDataRegistry.SetEntry> allEntries = ArmorSetDataRegistry.getAllEntries();

        // Group by tag, then sort entries within each group
        Map<String, List<ArmorSetDataRegistry.SetEntry>> grouped = allEntries.stream()
                .collect(Collectors.groupingBy(ArmorSetDataRegistry.SetEntry::tag,
                        LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));

        for (Map.Entry<String, List<ArmorSetDataRegistry.SetEntry>> group : grouped.entrySet()) {
            String tag = group.getKey();

            // Add tag header
            listEntries.add(new ListEntry(
                    ListEntry.EntryType.TAG_HEADER, tag, "", "", 0, null));

            // Add scope entries, sorted: universal → all_roles → specific
            List<ArmorSetDataRegistry.SetEntry> sorted = group.getValue().stream()
                    .sorted(Comparator
                            .comparingInt(SetsManagerScreen::scopePriority)
                            .thenComparing(ArmorSetDataRegistry.SetEntry::role)
                            .thenComparingInt(ArmorSetDataRegistry.SetEntry::level))
                    .collect(Collectors.toCollection(ArrayList::new));

            for (ArmorSetDataRegistry.SetEntry se : sorted) {
                listEntries.add(new ListEntry(
                        ListEntry.EntryType.SCOPE_ENTRY, tag,
                        se.scopeLabel(), se.role(), se.level(), se.data()));
            }
        }

        // Auto-select first scope entry
        for (int i = 0; i < listEntries.size(); i++) {
            if (listEntries.get(i).type() == ListEntry.EntryType.SCOPE_ENTRY) {
                selectedIndex = i;
                break;
            }
        }
    }

    private static int scopePriority(ArmorSetDataRegistry.SetEntry e) {
        boolean wildRole = ArmorSetDataRegistry.WILDCARD_ROLE.equals(e.role());
        boolean wildLevel = e.level() == ArmorSetDataRegistry.WILDCARD_LEVEL;
        if (wildRole && wildLevel) return 0; // universal first
        if (wildRole) return 1;             // all_roles second
        return 2;                            // specific last
    }

    // ─── Rendering ───────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        if (showingTagBrowser) {
            renderTagList(graphics, mouseX, mouseY);
            renderTagItems(graphics);
        } else {
            renderLeftPage(graphics, mouseX, mouseY);
            if (showingValidation && validationResults != null) {
                renderValidationPage(graphics);
            } else {
                renderRightPage(graphics);
            }
        }
    }




    // ─── Left page: Set list ─────────────────────────────────────────────

    // ─── Tag Browser: left panel (tag list) ──────────────────────────────

    private void renderTagList(GuiGraphics graphics, int mouseX, int mouseY) {
        // Bereich unterhalb der SearchBox
        int listY = leftY + 18; // SearchBox ist 14px hoch + 4px Abstand
        int listH = leftH - 18;
        int maxLines = listH / lineHeight;

        graphics.enableScissor(leftX, listY, leftX + leftW, listY + listH);

        if (filteredTags.isEmpty()) {
            graphics.drawString(this.font,
                    Component.literal("No tags found.").withStyle(ChatFormatting.ITALIC),
                    leftX + 3, listY + 3, COLOR_GRAY, false);
            graphics.disableScissor();
            return;
        }

        int visibleEnd = Math.min(filteredTags.size(), tagLeftScrollOffset + maxLines);
        for (int i = tagLeftScrollOffset; i < visibleEnd; i++) {
            int entryY = listY + (i - tagLeftScrollOffset) * lineHeight;
            TagKey<Item> tag = filteredTags.get(i);

            // Selection / Hover highlight
            if (i == tagSelectedIndex) {
                graphics.fill(leftX, entryY - 1, leftX + leftW, entryY + lineHeight - 1, COLOR_SELECTED);
            } else if (mouseX >= leftX && mouseX <= leftX + leftW
                    && mouseY >= entryY && mouseY < entryY + lineHeight) {
                graphics.fill(leftX, entryY - 1, leftX + leftW, entryY + lineHeight - 1, COLOR_HOVER);
            }

            // Tag name: namespace in grau, path in schwarz
            String ns = tag.location().getNamespace();
            String path = tag.location().getPath();

            graphics.drawString(this.font, ns + ":", leftX + 3, entryY, COLOR_GRAY, false);
            int nsWidth = this.font.width(ns + ":");

            String displayPath = path;
            if (this.font.width(displayPath) > leftW - nsWidth - 10) {
                while (this.font.width(displayPath + "...") > leftW - nsWidth - 10 && displayPath.length() > 3) {
                    displayPath = displayPath.substring(0, displayPath.length() - 1);
                }
                displayPath += "...";
            }
            graphics.drawString(this.font, displayPath, leftX + 3 + nsWidth, entryY, COLOR_TEXT, false);
        }

        // Scrollbar
        if (filteredTags.size() > maxLines) {
            int scrollBarX = leftX + leftW - 3;
            float ratio = (float) tagLeftScrollOffset / Math.max(1, filteredTags.size() - maxLines);
            int thumbHeight = Math.max(10, listH * maxLines / filteredTags.size());
            int thumbY = listY + (int) ((listH - thumbHeight) * ratio);
            graphics.fill(scrollBarX, listY, scrollBarX + 2, listY + listH, 0x33000000);
            graphics.fill(scrollBarX, thumbY, scrollBarX + 2, thumbY + thumbHeight, 0xAA553300);
        }

        graphics.disableScissor();
    }

// ─── Tag Browser: right panel (items in tag) ─────────────────────────

    private void renderTagItems(GuiGraphics graphics) {
        graphics.enableScissor(rightX, rightY, rightX + rightW, rightY + rightH);

        if (tagSelectedIndex < 0 || tagSelectedIndex >= filteredTags.size()) {
            graphics.drawString(this.font,
                    Component.literal("Select a tag on the left.").withStyle(ChatFormatting.ITALIC),
                    rightX + 5, rightY + 5, COLOR_GRAY, false);
            graphics.disableScissor();
            return;
        }

        TagKey<Item> tag = filteredTags.get(tagSelectedIndex);

        // Header
        int y = rightY - tagRightScrollOffset * lineHeight;

        // Title
        if (isVisible(y)) {
            graphics.drawString(this.font,
                    Component.literal("#" + tag.location()).withStyle(s -> s.withBold(true)),
                    rightX + 3, y, COLOR_HEADER, false);
        }
        y += lineHeight;

        // Count
        if (isVisible(y)) {
            graphics.drawString(this.font,
                    selectedTagItems.size() + " item(s)",
                    rightX + 3, y, COLOR_TEXT, false);
        }
        y += lineHeight;
        y += lineHeight; // blank line

        // Items – Icon + Name + ID
        // Jedes Item braucht 2 Zeilen: Name-Zeile (mit Icon) + ID-Zeile
        List<int[]> iconPositions = new ArrayList<>(); // [x, y, itemIndex]

        for (int i = 0; i < selectedTagItems.size(); i++) {
            Item item = selectedTagItems.get(i);
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            String displayName = new ItemStack(item).getHoverName().getString();

            // Zeile 1: Icon-Platz + Name
            if (isVisible(y)) {
                graphics.drawString(this.font, displayName,
                        rightX + 22, y, COLOR_TEXT, false);
                // Icon-Position merken (rendern wir gleich)
                iconPositions.add(new int[]{rightX + 3, y - 1, i});
            }
            y += lineHeight;

            // Zeile 2: Registry-ID eingerückt
            if (isVisible(y)) {
                graphics.drawString(this.font, itemId.toString(),
                        rightX + 22, y, COLOR_GRAY, false);
            }
            y += lineHeight;
        }

        // Scrollbar
        int totalLines = 3 + selectedTagItems.size() * 2;
        if (totalLines > maxLinesRight) {
            int scrollBarX = rightX + rightW - 3;
            float ratio = (float) tagRightScrollOffset / Math.max(1, totalLines - maxLinesRight);
            int thumbHeight = Math.max(10, rightH * maxLinesRight / totalLines);
            int thumbY = rightY + (int) ((rightH - thumbHeight) * ratio);
            graphics.fill(scrollBarX, rightY, scrollBarX + 2, rightY + rightH, 0x33000000);
            graphics.fill(scrollBarX, thumbY, scrollBarX + 2, thumbY + thumbHeight, 0xAA553300);
        }

        graphics.disableScissor();

        // Icons NACH disableScissor rendern (renderItem hat eigenen Scissor/Blend)
        graphics.enableScissor(rightX, rightY, rightX + rightW, rightY + rightH);
        for (int[] pos : iconPositions) {
            ItemStack stack = new ItemStack(selectedTagItems.get(pos[2]));
            graphics.renderItem(stack, pos[0], pos[1]);
        }
        graphics.disableScissor();
    }

    /** Prüft ob eine y-Position im sichtbaren Bereich liegt. */
    private boolean isVisible(int y) {
        return y >= rightY - lineHeight && y < rightY + rightH;
    }



    private void renderLeftPage(GuiGraphics graphics, int mouseX, int mouseY) {
        if (listEntries.isEmpty()) {
            graphics.drawString(this.font,
                    Component.literal("No sets loaded.").withStyle(ChatFormatting.ITALIC),
                    leftX + 5, leftY + 5, COLOR_GRAY, false);
            graphics.drawString(this.font,
                    Component.literal("Use /setweaver sets create"),
                    leftX + 5, leftY + 5 + lineHeight, COLOR_GRAY, false);
            return;
        }

        // Scissor to left page area
        graphics.enableScissor(leftX, leftY, leftX + leftW, leftY + leftH);

        int y = leftY;
        int visibleStart = leftScrollOffset;
        int visibleEnd = Math.min(listEntries.size(), leftScrollOffset + maxLinesLeft);

        for (int i = visibleStart; i < visibleEnd; i++) {
            ListEntry entry = listEntries.get(i);
            int entryY = y + (i - visibleStart) * lineHeight;

            // Selection highlight
            if (i == selectedIndex) {
                graphics.fill(leftX, entryY - 1, leftX + leftW, entryY + lineHeight - 1, COLOR_SELECTED);
            }
            // Hover highlight (only for clickable entries)
            else if (entry.type() == ListEntry.EntryType.SCOPE_ENTRY
                    && mouseX >= leftX && mouseX <= leftX + leftW
                    && mouseY >= entryY && mouseY < entryY + lineHeight) {
                graphics.fill(leftX, entryY - 1, leftX + leftW, entryY + lineHeight - 1, COLOR_HOVER);
            }

            // Draw text
            String text = entry.getDisplayText();
            // Truncate if too wide
            if (this.font.width(text) > leftW - 10) {
                while (this.font.width(text + "...") > leftW - 10 && text.length() > 3) {
                    text = text.substring(0, text.length() - 1);
                }
                text += "...";
            }

            if (entry.type() == ListEntry.EntryType.TAG_HEADER) {
                // Tag-Header: schwarz + bold
                graphics.drawString(this.font,
                        Component.literal(text).withStyle(s -> s.withBold(true)),
                        leftX + 3, entryY, COLOR_HEADER, false);
            } else {
                // Scope-Entry: farbiger Bullet ● + schwarzer Text dahinter
                String bullet = "\u25CF ";
                String label = entry.scopeLabel(); // direkt aus dem Record, kein String-Fummel
                graphics.drawString(this.font, bullet, leftX + 8, entryY, entry.getColor(), false);
                int bulletWidth = this.font.width(bullet);
                graphics.drawString(this.font, label, leftX + 8 + bulletWidth, entryY, COLOR_TEXT, false);
            }
        }

        // ── Scrollbar indicator ──────────────────────────────────────────
        if (listEntries.size() > maxLinesLeft) {
            int scrollBarX = leftX + leftW - 3;
            int scrollBarHeight = leftH;
            float ratio = (float) leftScrollOffset / (listEntries.size() - maxLinesLeft);
            int thumbHeight = Math.max(10, scrollBarHeight * maxLinesLeft / listEntries.size());
            int thumbY = leftY + (int) ((scrollBarHeight - thumbHeight) * ratio);

            graphics.fill(scrollBarX, leftY, scrollBarX + 2, leftY + scrollBarHeight, 0x33000000);
            graphics.fill(scrollBarX, thumbY, scrollBarX + 2, thumbY + thumbHeight, 0xAA553300);
        }

        graphics.disableScissor();
    }

    // ─── Right page: Set details ─────────────────────────────────────────

    private void renderRightPage(GuiGraphics graphics) {
        graphics.enableScissor(rightX, rightY, rightX + rightW, rightY + rightH);

        if (selectedIndex < 0 || selectedIndex >= listEntries.size()) {
            graphics.drawString(this.font,
                    Component.literal("Select a set on the left.").withStyle(ChatFormatting.ITALIC),
                    rightX + 5, rightY + 5, COLOR_GRAY, false);
            graphics.disableScissor();
            return;
        }

        ListEntry selected = listEntries.get(selectedIndex);
        if (selected.data() == null) {
            graphics.drawString(this.font,
                    Component.literal("(Header — select an entry below)"),
                    rightX + 5, rightY + 5, COLOR_GRAY, false);
            graphics.disableScissor();
            return;
        }

        // Build detail lines
        List<DetailLine> detailLines = buildDetailLines(selected);

        int y = rightY - rightScrollOffset * lineHeight;
        for (DetailLine line : detailLines) {
            if (y >= rightY - lineHeight && y < rightY + rightH) {
                if (line.bold) {
                    graphics.drawString(this.font,
                            Component.literal(line.text).withStyle(s -> s.withBold(true)),
                            rightX + line.indent + 3, y, line.color, false);
                } else {
                    graphics.drawString(this.font, line.text,
                            rightX + line.indent + 3, y, line.color, false);
                }
            }
            y += lineHeight;
        }

        // Scrollbar
        if (detailLines.size() > maxLinesRight) {
            int scrollBarX = rightX + rightW - 3;
            float ratio = (float) rightScrollOffset / (detailLines.size() - maxLinesRight);
            int thumbHeight = Math.max(10, rightH * maxLinesRight / detailLines.size());
            int thumbY = rightY + (int) ((rightH - thumbHeight) * ratio);

            graphics.fill(scrollBarX, rightY, scrollBarX + 2, rightY + rightH, 0x33000000);
            graphics.fill(scrollBarX, thumbY, scrollBarX + 2, thumbY + thumbHeight, 0xAA553300);
        }

        graphics.disableScissor();
    }

    // ─── Detail line builder ─────────────────────────────────────────────

    private record DetailLine(String text, int color, int indent, boolean bold) {
        static DetailLine header(String text) {
            return new DetailLine(text, COLOR_HEADER, 0, true);     // schwarz + bold
        }
        static DetailLine text(String text) {
            return new DetailLine(text, COLOR_TEXT, 0, false);       // schwarz
        }
        static DetailLine text(String text, int iconColor) {
            return new DetailLine(text, iconColor, 0, false);        // für Icons mit Farbe
        }
        static DetailLine indented(String text) {
            return new DetailLine(text, COLOR_TEXT, 8, false);       // schwarz
        }
        static DetailLine indented(String text, int color) {
            return new DetailLine(text, color, 8, false);            // beibehalten für farbige Icons
        }
        static DetailLine blank() {
            return new DetailLine("", 0, 0, false);
        }
    }


    private List<DetailLine> buildDetailLines(ListEntry entry) {
        List<DetailLine> lines = new ArrayList<>();
        ArmorSetData data = entry.data();

        // Set name
        String displayName = data.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = ListEntry.formatTagName(entry.tag());
        }
        lines.add(DetailLine.header(displayName));
        lines.add(DetailLine.blank());

        // Tag & scope — alles schwarz, nur Scope-Icon farbig
        lines.add(DetailLine.text("Tag: " + entry.tag(), COLOR_TEXT));
        lines.add(DetailLine.text("Scope: " + entry.scopeLabel(), COLOR_TEXT));
        lines.add(DetailLine.blank());

        // Thresholds
        if (data.getParts() == null || data.getParts().isEmpty()) {
            lines.add(DetailLine.text("(No thresholds defined)", COLOR_GRAY));
            return lines;
        }

        // Sort part keys numerically
        List<Map.Entry<String, ArmorSetData.PartData>> sortedParts = data.getParts().entrySet()
                .stream()
                .sorted(Comparator.comparingInt(e -> {
                    try { return Integer.parseInt(e.getKey().replace("Part", "")); }
                    catch (NumberFormatException ex) { return 99; }
                }))
                .collect(Collectors.toCollection(ArrayList::new));

        for (Map.Entry<String, ArmorSetData.PartData> partEntry : sortedParts) {
            String partKey = partEntry.getKey();
            ArmorSetData.PartData partData = partEntry.getValue();

            // Threshold header — schwarz + bold
            String num = partKey.replace("Part", "");
            lines.add(DetailLine.header("\u2550\u2550 " + num + " Piece" +
                    (Integer.parseInt(num) > 1 ? "s" : "") + " \u2550\u2550"));

            // Effects — Icon (✦) in Lila, Name+Level in Schwarz
            if (partData.getEffects() != null && !partData.getEffects().isEmpty()) {
                for (ArmorSetData.EffectData effect : partData.getEffects()) {
                    String effectName = resolveEffectName(effect.getEffect());
                    int level = effect.getAmplifier() + 1;
                    String roman = toRoman(level);
                    lines.add(DetailLine.indented("\u2726 " + effectName + " " + roman, COLOR_EFFECT));
                }
            }

            // Attributes — Icon (▸) in Grün, Name+Wert in Schwarz
            if (partData.getAttributes() != null && !partData.getAttributes().isEmpty()) {
                for (Map.Entry<String, ArmorSetData.AttributeData> attr : partData.getAttributes().entrySet()) {
                    String attrName = resolveAttributeName(attr.getKey());
                    ArmorSetData.AttributeData attrData = attr.getValue();
                    String valueStr = formatAttributeValue(attrData);
                    lines.add(DetailLine.indented("\u25B8 " + attrName + " " + valueStr, COLOR_ATTRIBUTE));
                }
            }

            // No effects and no attributes?
            if ((partData.getEffects() == null || partData.getEffects().isEmpty())
                    && (partData.getAttributes() == null || partData.getAttributes().isEmpty())) {
                lines.add(DetailLine.indented("(no bonuses)", COLOR_GRAY));
            }

            lines.add(DetailLine.blank());
        }

        return lines;
    }


    // ─── Validation page ─────────────────────────────────────────────────

    private void renderValidationPage(GuiGraphics graphics) {
        graphics.enableScissor(rightX, rightY, rightX + rightW, rightY + rightH);

        List<DetailLine> lines = new ArrayList<>();
        lines.add(DetailLine.header("Validation Results"));
        lines.add(DetailLine.blank());

        int ok = 0, warn = 0, err = 0;
        for (SetWeaverReloadListener.ValidationResult result : validationResults) {
            int iconColor;
            String icon;
            switch (result.status()) {
                case OK:
                    iconColor = COLOR_SPECIFIC;  // Grün
                    icon = "\u2714 ";            // ✔
                    ok++;
                    break;
                case WARNING:
                    iconColor = COLOR_UNIVERSAL; // Gelb
                    icon = "\u26A0 ";            // ⚠
                    warn++;
                    break;
                default:
                    iconColor = 0xFFFF5555;      // Rot
                    icon = "\u2718 ";            // ✘
                    err++;
                    break;
            }

            // Icon farbig, Dateiname schwarz — als zwei separate drawString-Aufrufe
            lines.add(DetailLine.text(icon, iconColor));           // Icon in Farbe
            lines.add(DetailLine.indented(result.filePath()));     // Pfad schwarz, eingerückt

            if (result.status() != SetWeaverReloadListener.ValidationResult.Status.OK) {
                String msg = result.message();
                List<String> wrapped = wrapText(msg, rightW - 20);
                for (String line : wrapped) {
                    lines.add(DetailLine.indented(line));          // Fehlermeldung schwarz
                }
            }
        }

        lines.add(DetailLine.blank());
        lines.add(DetailLine.header("Summary: " + ok + " OK, " + warn + " Warning(s), " + err + " Error(s)"));

        // Render with scroll
        int y = rightY - rightScrollOffset * lineHeight;
        for (DetailLine line : lines) {
            if (y >= rightY - lineHeight && y < rightY + rightH) {
                if (line.bold) {
                    graphics.drawString(this.font,
                            Component.literal(line.text).withStyle(s -> s.withBold(true)),
                            rightX + line.indent + 3, y, line.color, false);
                } else {
                    graphics.drawString(this.font, line.text,
                            rightX + line.indent + 3, y, line.color, false);
                }
            }
            y += lineHeight;
        }

        // Scrollbar
        if (lines.size() > maxLinesRight) {
            int scrollBarX = rightX + rightW - 3;
            float ratio = (float) rightScrollOffset / Math.max(1, lines.size() - maxLinesRight);
            int thumbHeight = Math.max(10, rightH * maxLinesRight / lines.size());
            int thumbY = rightY + (int) ((rightH - thumbHeight) * ratio);

            graphics.fill(scrollBarX, rightY, scrollBarX + 2, rightY + rightH, 0x33000000);
            graphics.fill(scrollBarX, thumbY, scrollBarX + 2, thumbY + thumbHeight, 0xAA553300);
        }

        graphics.disableScissor();
    }

    // ─── Input handling ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // Tag Browser: click on tag list
        if (showingTagBrowser) {
            int listY = leftY + 18;
            int listH = leftH - 18;
            if (mouseX >= leftX && mouseX <= leftX + leftW
                    && mouseY >= listY && mouseY <= listY + listH) {
                int clickedLine = (int) ((mouseY - listY) / lineHeight) + tagLeftScrollOffset;
                selectTag(clickedLine);
                return true;
            }
            return false;
        }

        // Click on left page → select entry
        if (mouseX >= leftX && mouseX <= leftX + leftW
                && mouseY >= leftY && mouseY <= leftY + leftH) {

            int clickedLine = (int) ((mouseY - leftY) / lineHeight) + leftScrollOffset;
            if (clickedLine >= 0 && clickedLine < listEntries.size()) {
                ListEntry entry = listEntries.get(clickedLine);
                if (entry.type() == ListEntry.EntryType.SCOPE_ENTRY) {
                    selectedIndex = clickedLine;
                    rightScrollOffset = 0;
                    showingValidation = false;
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll left page
        if (showingTagBrowser) {
            int listY = leftY + 18;
            int listH = leftH - 18;
            int maxLines = listH / lineHeight;

            // Scroll tag list (links)
            if (mouseX >= leftX && mouseX <= leftX + leftW
                    && mouseY >= listY && mouseY <= listY + listH) {
                if (scrollY > 0) tagLeftScrollOffset = Math.max(0, tagLeftScrollOffset - 1);
                else tagLeftScrollOffset = Math.min(Math.max(0, filteredTags.size() - maxLines), tagLeftScrollOffset + 1);
                return true;
            }

            // Scroll tag items (rechts)
            if (mouseX >= rightX && mouseX <= rightX + rightW
                    && mouseY >= rightY && mouseY <= rightY + rightH) {
                if (scrollY > 0) tagRightScrollOffset = Math.max(0, tagRightScrollOffset - 1);
                else tagRightScrollOffset++;
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        if (mouseX >= leftX && mouseX <= leftX + leftW
                && mouseY >= leftY && mouseY <= leftY + leftH) {
            if (scrollY > 0) {
                leftScrollOffset = Math.max(0, leftScrollOffset - 1);
            } else if (scrollY < 0) {
                leftScrollOffset = Math.min(
                        Math.max(0, listEntries.size() - maxLinesLeft),
                        leftScrollOffset + 1);
            }
            return true;
        }

        // Scroll right page
        if (mouseX >= rightX && mouseX <= rightX + rightW
                && mouseY >= rightY && mouseY <= rightY + rightH) {
            if (scrollY > 0) {
                rightScrollOffset = Math.max(0, rightScrollOffset - 1);
            } else if (scrollY < 0) {
                rightScrollOffset = rightScrollOffset + 1;
                // Max will be clamped naturally in render (no content = no visual)
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Arrow keys for list navigation
        if (keyCode == 265) { // UP
            navigateList(-1);
            return true;
        }
        if (keyCode == 264) { // DOWN
            navigateList(1);
            return true;
        }

        // ESC to close
        if (keyCode == 256) {
            onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void navigateList(int direction) {
        if (listEntries.isEmpty()) return;

        int newIndex = selectedIndex;
        do {
            newIndex += direction;
            if (newIndex < 0) newIndex = listEntries.size() - 1;
            if (newIndex >= listEntries.size()) newIndex = 0;
            // Skip headers
            if (listEntries.get(newIndex).type() == ListEntry.EntryType.SCOPE_ENTRY) {
                selectedIndex = newIndex;
                rightScrollOffset = 0;
                showingValidation = false;

                // Auto-scroll left list to keep selection visible
                if (selectedIndex < leftScrollOffset) {
                    leftScrollOffset = selectedIndex;
                } else if (selectedIndex >= leftScrollOffset + maxLinesLeft) {
                    leftScrollOffset = selectedIndex - maxLinesLeft + 1;
                }
                return;
            }
        } while (newIndex != selectedIndex); // prevent infinite loop if all headers
    }

    // ─── Button actions ──────────────────────────────────────────────────

    /**
     * Opens the Set Wizard in "copy" mode for the currently selected set.
     * The wizard is pre-filled with the source set's data; the user can change
     * role/level (and tag/display name) before proceeding to the editor.
     */
    private void onCopySet() {
        if (selectedIndex < 0 || selectedIndex >= listEntries.size()) return;
        ListEntry entry = listEntries.get(selectedIndex);
        if (entry.type() != ListEntry.EntryType.SCOPE_ENTRY || entry.data() == null) return;

        assert this.minecraft != null;

        SetEditorData sourceData = new SetEditorData();
        sourceData.loadFrom(entry.tag(), entry.role(), entry.level(), entry.data());

        this.minecraft.setScreen(new SetWizardScreen(this, sourceData));
    }

    /** Opens the Set Wizard (Step 1) to create or edit a set definition. */
    private void onCreateSet() {
        assert this.minecraft != null;
        this.minecraft.setScreen(new SetWizardScreen(this));
    }

    /** Opens the SetEditorScreen for the currently selected set. */
    private void onEditSet() {
        if (selectedIndex < 0 || selectedIndex >= listEntries.size()) return;
        ListEntry entry = listEntries.get(selectedIndex);
        if (entry.type() != ListEntry.EntryType.SCOPE_ENTRY || entry.data() == null) return;

        assert this.minecraft != null;

        // SetEditorData aus bestehendem Set aufbauen (inkl. role/Level für korrekten Speicherpfad)
        SetEditorData editorData = new SetEditorData();
        editorData.loadFrom(entry.tag(), entry.role(), entry.level(), entry.data());

        // Wizard-Konstruktor nutzen → editorData bleibt erhalten → Unterordner stimmen beim Speichern
        this.minecraft.setScreen(new SetEditorScreen(this, editorData));
    }

    private void onReload() {
        // Reload set definitions from config files
        SetWeaverReloadListener.loadAllEffects();
        buildListEntries();
    }

    private void onValidate() {
        validationResults = SetWeaverReloadListener.validateAllFiles();
        showingValidation = true;
        rightScrollOffset = 0;
    }

    // ─── Helper methods ──────────────────────────────────────────────────

    private String resolveEffectName(String effectId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(
                    effectId.contains(":") ? effectId : "minecraft:" + effectId);
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(loc);
            if (effect != null) {
                return effect.getDisplayName().getString();
            }
        } catch (Exception ignored) {}
        return effectId;
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
        // Fallback: format the path nicely
        String path = attributeId.contains(":") ? attributeId.split(":")[1] : attributeId;
        return path.replace("generic.", "").replace("_", " ");
    }

    private String formatAttributeValue(ArmorSetData.AttributeData data) {
        double val = data.getValue();
        String mod = data.getModifier();
        if (mod != null && (mod.equalsIgnoreCase("multiply")
                || mod.equalsIgnoreCase("multiply_base")
                || mod.equalsIgnoreCase("multiply_total"))) {
            return String.format("+%.0f%%", val * 100);
        }
        if (val == (long) val) {
            return String.format("+%d", (long) val);
        }
        return String.format("+%.2f", val);
    }

    private static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.length() > 0
                    && this.font.width(current.toString() + " " + word) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                if (current.length() > 0) current.append(" ");
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }

        return lines;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Vanilla Blur + Dim
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        // Linkes Panel
        drawPanel(graphics, leftPanelX, guiTop, leftPanelW, panelH);

        // Rechtes Panel
        drawPanel(graphics, rightPanelX, guiTop, rightPanelW, panelH);

        // Trennlinie zwischen den Panels
        int divX = leftPanelX + leftPanelW + 1;
        graphics.fill(divX, guiTop + 4, divX + DIVIDER_WIDTH - 2, guiTop + panelH - 4, PANEL_DIVIDER);
    }

    /** Zeichnet ein Panel mit Hintergrund und Rahmen. */
    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        // Hintergrund (Beige/Pergament)
        graphics.fill(x, y, x + w, y + h, PANEL_BG);
        // Rahmen (1px, warmes Braun)
        graphics.fill(x,         y,         x + w,     y + 1,     PANEL_BORDER); // oben
        graphics.fill(x,         y + h - 1, x + w,     y + h,     PANEL_BORDER); // unten
        graphics.fill(x,         y,         x + 1,     y + h,     PANEL_BORDER); // links
        graphics.fill(x + w - 1, y,         x + w,     y + h,     PANEL_BORDER); // rechts
    }

    // ─── Tag Browser actions ─────────────────────────────────────────────

    private void onToggleTagBrowser() {
        showingTagBrowser = !showingTagBrowser;
        showingValidation = false;
        tagSearchBox.setVisible(showingTagBrowser);
        tagLeftScrollOffset = 0;
        tagRightScrollOffset = 0;

        if (showingTagBrowser) {
            tagSearchBox.setFocused(true);
            setFocused(tagSearchBox);
            onTagFilterChanged(tagSearchBox.getValue()); // apply current filter
        }
    }

    private void onTagFilterChanged(String filter) {
        String lower = filter.toLowerCase();
        filteredTags = lower.isEmpty()
                ? new ArrayList<>(allTags)
                : allTags.stream()
                .filter(t -> t.location().toString().contains(lower))
                .collect(Collectors.toCollection(ArrayList::new));
        tagSelectedIndex = -1;
        tagLeftScrollOffset = 0;
        tagRightScrollOffset = 0;
        selectedTagItems.clear();
    }

    private void selectTag(int index) {
        if (index < 0 || index >= filteredTags.size()) return;
        tagSelectedIndex = index;
        tagRightScrollOffset = 0;

        TagKey<Item> tag = filteredTags.get(index);
        selectedTagItems = BuiltInRegistries.ITEM.getOrCreateTag(tag)
                .stream()
                .map(Holder::value)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}

