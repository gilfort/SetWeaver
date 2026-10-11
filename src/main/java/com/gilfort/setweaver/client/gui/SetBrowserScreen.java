package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.client.ArmorSetTooltipHandler;
import com.gilfort.setweaver.network.DeleteSetPayload;
import com.gilfort.setweaver.network.ReloadRequestPayload;
import com.gilfort.setweaver.network.SavePackagesPayload;
import com.gilfort.setweaver.network.SaveSetPayload;
import com.gilfort.setweaver.network.SaveTagPayload;
import com.gilfort.setweaver.network.ValidateRequestPayload;
import com.gilfort.setweaver.seteffects.ArmorPieces;
import com.gilfort.setweaver.seteffects.ArmorSetData;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry.SetEntry;
import com.gilfort.setweaver.seteffects.AttributePackageManager;
import com.gilfort.setweaver.seteffects.AttributePackageManager.AttributePackage;
import com.gilfort.setweaver.seteffects.SetFiles;
import com.gilfort.setweaver.seteffects.SetIds;
import com.gilfort.setweaver.seteffects.SetWeaverReloadListener;
import com.gilfort.setweaver.seteffects.ValidationResult;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The SetWeaver editor, opened with {@code /setweaver editor}. Three areas from left to right:
 * <ol>
 * <li>the sets (one row per item tag) plus entries for attribute packages, item tags and validation,</li>
 * <li>the scopes of the selected set, the packages or the item tags,</li>
 * <li>the details: an editable form with the action buttons below it.</li>
 * </ol>
 * Selecting an entry collapses the columns left of it to narrow strips with an arrow, which expand them again, so the
 * form gets as much room as possible. Changes stay a local draft until "Save"; leaving an entry with unsaved changes
 * asks first. Saving, deleting and validating happen on the server.
 */
public class SetBrowserScreen extends Screen {

    private static final int PAD = 6;
    private static final int HEADER = 26;
    private static final int STRIP = 14;
    private static final int COLUMN1_WIDTH = 140;
    private static final int COLUMN2_WIDTH = 170;
    private static final int ROW = 12;
    private static final int SEARCH_HEIGHT = 16;
    private static final int FOOTER = 28;
    private static final int LOCK_SIZE = 9;
    private static final int SMALL = 16;
    /** Minimum width of the details panel before a list column is collapsed. */
    private static final int MIN_DETAILS = 300;
    private static final Identifier LOCK_SPRITE = Identifier.withDefaultNamespace("widget/locked_button");

    /** What the second column and the details show. */
    private enum Mode { SETS, PACKAGES, TAGS, VALIDATION }

    /** Role and level of a set. */
    private record Scope(String role, int level) {
    }

    /** The browser currently open (also while one of its dialogs is shown), or {@code null}. */
    private static @Nullable SetBrowserScreen current;

    private static Mode rememberedMode = Mode.SETS;
    private static @Nullable String rememberedTag;
    private static @Nullable Scope rememberedScope;
    private static int rememberedPackage = -1;
    private static @Nullable Identifier rememberedItemTag;

    private Mode mode;
    private @Nullable String tag;
    private @Nullable Scope scope;
    private int packageIndex;
    private @Nullable Identifier itemTag;
    private boolean column1Collapsed;
    private boolean column2Collapsed;
    private int column1Scroll;
    private int column2Scroll;
    private int detailScroll;
    private String setQuery = "";
    private String tagQuery = "";

    private @Nullable SetDraft setDraft;
    private @Nullable PackagesDraft packagesDraft;
    private @Nullable TagDraft tagDraft;
    private @Nullable List<ValidationResult> validation;
    private @Nullable Component status;
    private int statusColor = Theme.GREY;
    private List<TagKey<Item>> armorTags;

    private final List<Hit> hits = new ArrayList<>();
    private @Nullable FormList form;
    private @Nullable Button saveButton;
    private @Nullable Button discardButton;

    /** Creates the browser, restoring the selection of the last time it was open. */
    public SetBrowserScreen() {
        super(Component.translatable("gui.setweaver.browser.title"));
        this.mode = rememberedMode;
        this.tag = rememberedTag;
        this.scope = rememberedScope;
        this.packageIndex = rememberedPackage;
        this.itemTag = rememberedItemTag;
        this.armorTags = GuiUtil.armorItemTags();
        this.validateSelection();
        this.column1Collapsed = this.mode != Mode.SETS || this.tag != null;
        this.column2Collapsed = this.hasDetailSelection();
        current = this;
        ClientPacketDistributor.sendToServer(new ValidateRequestPayload());
    }

    /** @return the open browser (also while a dialog of it is shown), or {@code null} */
    public static @Nullable SetBrowserScreen current() {
        return current;
    }

    /** Forgets the open browser, e.g. when leaving a server. */
    public static void forget() {
        current = null;
    }

    // ---------------------------------------------------------------- server updates

    /** Called when the server sent new set definitions and packages; keeps the selection where possible. */
    public void onRegistrySynced() {
        this.armorTags = GuiUtil.armorItemTags();
        if (this.setDraft != null && (this.setDraft.saving() || !this.setDraft.dirty())) {
            if (this.setDraft.saving()) {
                this.setStatus(Component.translatable("gui.setweaver.browser.saved"), Theme.GREEN);
            }
            this.setDraft = null;
        }
        if (this.packagesDraft != null && (this.packagesDraft.saving() || !this.packagesDraft.dirty())) {
            if (this.packagesDraft.saving()) {
                this.setStatus(Component.translatable("gui.setweaver.browser.saved"), Theme.GREEN);
            }
            // The server sorts packages by priority; keep the selected one by its name
            String selected = this.packageIndex >= 0 && this.packageIndex < this.packagesDraft.packages().size()
                    ? this.packagesDraft.packages().get(this.packageIndex).getName() : null;
            List<AttributePackage> stored = AttributePackageManager.client().getAllPackages();
            this.packageIndex = -1;
            for (int i = 0; i < stored.size() && selected != null; i++) {
                if (selected.equals(stored.get(i).getName())) {
                    this.packageIndex = i;
                    break;
                }
            }
            this.packagesDraft = null;
        }
        this.validateSelection();
        ClientPacketDistributor.sendToServer(new ValidateRequestPayload());
        this.rebuildWidgets();
    }

    /**
     * Shows the result of checking the set files on the server.
     *
     * @param results one result per file
     */
    public void onValidationResults(List<ValidationResult> results) {
        this.validation = List.copyOf(results);
        this.rebuildWidgets();
    }

    private void validateSelection() {
        if (this.tag != null && this.entriesOf(this.tag).isEmpty() && !this.draftHasTag(this.tag)) {
            this.tag = null;
        }
        if (this.tag == null || (this.scope != null && this.entry(this.tag, this.scope) == null && !this.draftIs(this.tag, this.scope))) {
            this.scope = null;
        }
        if (this.packageIndex >= this.packages().size()) {
            this.packageIndex = -1;
        }
        if (this.itemTag != null && this.armorTags.stream().noneMatch(t -> t.location().equals(this.itemTag))) {
            this.itemTag = null;
        }
    }

    private boolean draftHasTag(String tag) {
        return this.setDraft != null && this.setDraft.isNew() && this.setDraft.data().getTag().equals(tag);
    }

    private boolean draftIs(String tag, Scope scope) {
        return this.setDraft != null && this.setDraft.is(tag, scope.role(), scope.level());
    }

    private boolean hasDetailSelection() {
        return switch (this.mode) {
            case SETS -> this.scope != null;
            case PACKAGES -> this.packageIndex >= 0;
            case TAGS -> this.itemTag != null || (this.tagDraft != null && this.tagDraft.isNew());
            case VALIDATION -> false;
        };
    }

    // ---------------------------------------------------------------- data

    private Map<String, List<SetEntry>> entriesByTag() {
        Map<String, List<SetEntry>> byTag = new LinkedHashMap<>();
        for (SetEntry entry : ArmorSetDataRegistry.client().getAllEntries()) {
            byTag.computeIfAbsent(entry.tag(), t -> new ArrayList<>()).add(entry);
        }
        return byTag;
    }

    private List<SetEntry> entriesOf(String tag) {
        return this.entriesByTag().getOrDefault(tag, List.of());
    }

    private @Nullable SetEntry entry(String tag, Scope scope) {
        return ArmorSetDataRegistry.client().getExact(tag, scope.role(), scope.level());
    }

    private List<AttributePackage> packages() {
        return this.packagesDraft != null ? this.packagesDraft.packages() : AttributePackageManager.client().getAllPackages();
    }

    private Map<String, ValidationResult> validationByPath() {
        Map<String, ValidationResult> byPath = new HashMap<>();
        if (this.validation != null) {
            this.validation.forEach(result -> byPath.put(result.filePath(), result));
        }
        return byPath;
    }

    private static String path(SetEntry entry) {
        return SetFiles.relativePath(entry.role(), entry.level(), entry.tag());
    }

    private static Component scopeLabel(String role, int level) {
        return ArmorSetDataRegistry.scopeLabel(role, level);
    }

    private static int scopeColor(String role, int level) {
        boolean wildRole = ArmorSetDataRegistry.WILDCARD_ROLE.equals(role);
        if (wildRole && level == ArmorSetDataRegistry.WILDCARD_LEVEL) {
            return Theme.YELLOW;
        }
        return wildRole ? Theme.LINK : Theme.GREEN;
    }

    private static int scopeOrder(String role, int level) {
        boolean wildRole = ArmorSetDataRegistry.WILDCARD_ROLE.equals(role);
        if (wildRole && level == ArmorSetDataRegistry.WILDCARD_LEVEL) {
            return 0;
        }
        return wildRole ? 1 : 2;
    }

    // ---------------------------------------------------------------- layout

    private int column1X() {
        return PAD;
    }

    private int column1Width() {
        return this.column1Collapsed ? STRIP : COLUMN1_WIDTH;
    }

    private int column2X() {
        return this.column1X() + this.column1Width() + PAD;
    }

    private boolean column2Visible() {
        return switch (this.mode) {
            case SETS -> this.tag != null;
            case PACKAGES, TAGS -> true;
            case VALIDATION -> false;
        };
    }

    private int column2Width() {
        if (!this.column2Visible()) {
            return 0;
        }
        return this.column2Collapsed ? STRIP : COLUMN2_WIDTH;
    }

    private int detailsX() {
        return this.column2Visible() ? this.column2X() + this.column2Width() + PAD : this.column2X();
    }

    private int detailsWidth() {
        return this.width - PAD - this.detailsX();
    }

    private int top() {
        return HEADER;
    }

    private int bottom() {
        return this.height - PAD;
    }

    private int column1ListTop() {
        return this.top() + 16 + SEARCH_HEIGHT + 4;
    }

    private int column2ListTop() {
        return this.mode == Mode.TAGS ? this.top() + SEARCH_HEIGHT + 6 : this.top() + 16;
    }

    private boolean hasFooter() {
        return this.form != null;
    }

    private int detailContentHeight() {
        return this.bottom() - this.top() - (this.hasFooter() ? FOOTER : 0) - 4;
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.browser.reload"),
                        button -> this.leaveDraft(() -> ClientPacketDistributor.sendToServer(new ReloadRequestPayload())))
                .bounds(this.width - PAD - 70, 4, 70, 18).tooltip(Tooltip.create(Component.translatable("gui.setweaver.browser.reload.tooltip"))).build());

        if (!this.column1Collapsed) {
            EditBox search = new EditBox(this.font, this.column1X() + 3, this.top() + 15, this.column1Width() - 6, SEARCH_HEIGHT,
                    Component.translatable("gui.setweaver.search"));
            search.setHint(Component.translatable("gui.setweaver.search"));
            search.setValue(this.setQuery);
            search.setResponder(value -> {
                this.setQuery = value;
                this.column1Scroll = 0;
            });
            this.addRenderableWidget(search);
        }
        if (this.mode == Mode.TAGS && !this.column2Collapsed) {
            EditBox search = new EditBox(this.font, this.column2X() + 3, this.top() + 3, this.column2Width() - 6, SEARCH_HEIGHT,
                    Component.translatable("gui.setweaver.search"));
            search.setHint(Component.translatable("gui.setweaver.search"));
            search.setValue(this.tagQuery);
            search.setResponder(value -> {
                this.tagQuery = value;
                this.column2Scroll = 0;
            });
            this.addRenderableWidget(search);
        }

        double previousScroll = this.form == null ? 0.0 : this.form.scrollAmount();
        this.form = null;
        this.saveButton = null;
        this.discardButton = null;
        List<FormList.Row> rows = switch (this.mode) {
            case SETS -> this.setForm();
            case PACKAGES -> this.packageForm();
            case TAGS -> this.tagForm();
            case VALIDATION -> null;
        };
        if (rows != null && this.form != null) {
            this.form.setRows(rows);
            this.form.setScrollAmount(previousScroll);
            this.addRenderableWidget(this.form);
            this.addFooterButtons();
        }
    }

    private FormList newForm() {
        // The form always has a footer below it; the list must end above it or it swallows the footer's clicks
        return new FormList(this.minecraft, this.detailsX() + 1, this.top() + 2, this.detailsWidth() - 2,
                this.bottom() - this.top() - FOOTER - 4, this::changedFields);
    }

    private Set<String> changedFields() {
        return switch (this.mode) {
            case SETS -> this.setDraft == null ? Set.of() : this.setDraft.changed();
            case PACKAGES -> this.packagesDraft == null ? Set.of() : this.packagesDraft.changed(this.packageIndex);
            case TAGS -> this.tagDraft == null ? Set.of() : this.tagDraft.isNew() ? Set.of(TagDraft.ID, TagDraft.ITEMS)
                    : this.tagDraft.itemsChanged() ? Set.of(TagDraft.ITEMS) : Set.of();
            case VALIDATION -> Set.of();
        };
    }

    private void addFooterButtons() {
        int x = this.detailsX() + 4;
        int y = this.bottom() - FOOTER + 4;
        this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.browser.save"),
                button -> this.save()).bounds(x, y, 56, 20).build());
        this.discardButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.browser.discard"),
                button -> this.discard()).bounds(x + 58, y, 62, 20).build());
        x += 128;
        if (this.mode == Mode.SETS) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.browser.copy"), button -> this.copySet())
                    .bounds(x, y, 70, 20).tooltip(Tooltip.create(Component.translatable("gui.setweaver.browser.copy.tooltip"))).build());
            x += 72;
            this.addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.browser.delete"), button -> this.deleteSet())
                    .bounds(x, y, 56, 20).build());
        } else if (this.mode == Mode.PACKAGES) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.browser.delete"), button -> this.deletePackage())
                    .bounds(x, y, 56, 20).build());
        } else if (this.mode == Mode.TAGS && this.tagDraft != null && this.isEditable(this.tagDraft)) {
            this.saveButton.setTooltip(Tooltip.create(Component.translatable("gui.setweaver.browser.tag.save.tooltip")));
        }
        this.updateFooter();
    }

    private void updateFooter() {
        boolean canSave;
        boolean dirty;
        switch (this.mode) {
            case SETS -> {
                canSave = this.setDraft != null && this.setDraft.canSave();
                dirty = this.setDraft != null && this.setDraft.dirty();
            }
            case PACKAGES -> {
                canSave = this.packagesDraft != null && this.packagesDraft.dirty() && this.packagesDraft.valid() && !this.packagesDraft.saving();
                dirty = this.packagesDraft != null && this.packagesDraft.dirty();
            }
            case TAGS -> {
                canSave = this.tagDraft != null && this.isEditable(this.tagDraft) && this.tagDraft.canSave();
                dirty = this.tagDraft != null && this.tagDraft.dirty() && this.isEditable(this.tagDraft);
            }
            default -> {
                canSave = false;
                dirty = false;
            }
        }
        if (this.saveButton != null) {
            this.saveButton.active = canSave;
        }
        if (this.discardButton != null) {
            this.discardButton.active = dirty;
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.updateFooter();
    }

    private void setStatus(@Nullable Component message, int color) {
        this.status = message;
        this.statusColor = color;
    }

    private void addStatusRows(FormList list, List<FormList.Row> rows) {
        if (this.status != null) {
            for (FormattedCharSequence line : this.font.split(this.status, list.textWidth())) {
                rows.add(list.text(Component.literal(plain(line)), this.statusColor, 0));
            }
        }
    }

    // ---------------------------------------------------------------- set form

    private @Nullable List<FormList.Row> setForm() {
        if (this.tag == null || this.scope == null) {
            return null;
        }
        SetEntry entry = this.entry(this.tag, this.scope);
        if (this.setDraft == null || !this.setDraft.is(this.tag, this.scope.role(), this.scope.level())) {
            if (entry == null) {
                return null;
            }
            this.setDraft = SetDraft.of(entry);
        }
        SetDraft draft = this.setDraft;
        SetEditorData data = draft.data();
        FormList list = this.newForm();
        this.form = list;
        List<FormList.Row> rows = new ArrayList<>();
        this.addStatusRows(list, rows);

        rows.add(list.header(Component.translatable("gui.setweaver.browser.set")));
        EditBox name = this.textField(Math.min(200, list.partsWidth()), data.getDisplayName(), 64,
                Component.literal(ArmorSetTooltipHandler.setName(null, data.getTag())), data::setDisplayName);
        rows.add(list.field(Component.translatable("gui.setweaver.browser.field.name"), SetDraft.NAME, widget(name)));
        rows.add(list.text(Component.translatable("gui.setweaver.browser.field.tag", "#" + data.getTag()), Theme.GREY, 0));
        rows.add(list.text(Component.translatable("gui.setweaver.browser.field.scope", scopeLabel(data.getRole(), data.getLevel())), Theme.GREY, 0));
        rows.add(list.text(Component.translatable("gui.setweaver.browser.field.file", data.relativePath()), Theme.DARK_GREY, 0));
        if (draft.isNew()) {
            rows.add(list.text(Component.translatable("gui.setweaver.browser.set.new"), Theme.CHANGED, 0));
        }

        for (int i = 0; i < ArmorSetData.MAX_PARTS; i++) {
            this.partRows(list, rows, draft, i);
        }

        ValidationResult result = this.validationByPath().get(data.relativePath());
        if (result != null && !result.messages().isEmpty()) {
            rows.add(list.text(Component.empty(), Theme.WHITE, 0));
            rows.add(list.text(Component.translatable("gui.setweaver.browser.problems", result.messages().size()), Theme.RED, 0));
            for (Component message : result.messages()) {
                for (FormattedCharSequence line : this.font.split(message, list.textWidth() - 8)) {
                    rows.add(list.text(Component.literal(plain(line)), result.status() == ValidationResult.Status.ERROR ? Theme.RED : Theme.YELLOW, 8));
                }
            }
        }
        return rows;
    }

    private void partRows(FormList list, List<FormList.Row> rows, SetDraft draft, int index) {
        SetEditorData.PartConfig part = draft.data().getPart(index);
        String field = SetDraft.partKey(index);
        rows.add(list.header(Component.literal(GuiUtil.pieces(index + 1)),
                widget(Button.builder(Component.translatable("gui.setweaver.browser.add_effect"), button -> this.pickEffect(part))
                        .bounds(0, 0, 50, SMALL).build()),
                widget(Button.builder(Component.translatable("gui.setweaver.browser.add_attribute"), button -> this.pickAttribute(part))
                        .bounds(0, 0, 64, SMALL).build()),
                widget(Button.builder(Component.translatable("gui.setweaver.browser.copy_from"), button -> this.pickCopySource(draft, index))
                        .bounds(0, 0, 44, SMALL).tooltip(Tooltip.create(Component.translatable("gui.setweaver.browser.copy_from.tooltip"))).build())));
        if (part.isEmpty()) {
            rows.add(list.text(Component.translatable("gui.setweaver.browser.none"), Theme.DARK_GREY, 8));
            return;
        }
        for (ArmorSetData.EffectData effect : part.getEffects()) {
            Optional<Holder.Reference<net.minecraft.world.effect.MobEffect>> holder = SetIds.effect(effect.getEffect());
            List<FormList.Part> parts = new ArrayList<>();
            holder.ifPresent(h -> parts.add(new FormList.IconPart(Hud.getMobEffectSprite(h))));
            parts.add(widget(this.small(Component.translatable("gui.setweaver.browser.decrease"),
                    () -> effect.setAmplifier(Math.max(0, effect.getAmplifier() - 1)))));
            parts.add(text(() -> Component.literal(AttributePreviewHelper.roman(effect.getAmplifier() + 1)), Theme.WHITE, 26));
            parts.add(widget(this.small(Component.translatable("gui.setweaver.browser.increase"),
                    () -> effect.setAmplifier(Math.min(SetWeaverReloadListener.MAX_AMPLIFIER, effect.getAmplifier() + 1)))));
            parts.add(widget(this.remove(() -> part.getEffects().remove(effect))));
            rows.add(list.entry(Component.literal(AttributePreviewHelper.effectName(effect.getEffect())), field, Theme.EFFECT,
                    List.of(Component.literal(effect.getEffect())), parts.toArray(FormList.Part[]::new)));
        }
        for (ArmorSetData.AttributeData attribute : part.getAttributes()) {
            EditBox value = this.textField(52, formatNumber(attribute.getValue()), 12, Component.empty(), text -> {
                Optional<Double> parsed = parseDouble(text);
                parsed.ifPresent(attribute::setValue);
                draft.setValid(attribute, parsed.isPresent());
            });
            value.setTextColor(draft.isInvalid(attribute) ? Theme.RED : Theme.WHITE);
            Button operation = this.cycle(84, () -> Component.translatable("gui.setweaver.operation." + operationOf(attribute)), () -> {
                List<String> ops = List.of(SetIds.ADDITION, SetIds.MULTIPLY_BASE, SetIds.MULTIPLY_TOTAL);
                attribute.setModifier(ops.get((ops.indexOf(operationOf(attribute)) + 1) % ops.size()));
            });
            operation.setTooltip(Tooltip.create(Component.translatable("gui.setweaver.browser.operation.tooltip")));
            rows.add(list.entry(Component.literal(AttributePreviewHelper.attributeName(attribute.getAttribute())), field, Theme.ATTRIBUTE,
                    List.of(Component.literal(attribute.getAttribute())),
                    widget(this.decimalStep(value, attribute, -1)), widget(value), widget(this.decimalStep(value, attribute, 1)),
                    widget(operation), widget(this.remove(() -> {
                        part.getAttributes().remove(attribute);
                        draft.forget(attribute);
                    }))));
            rows.add(list.entry(Component.empty(), null, Theme.GREY, List.of(), text(() -> {
                MutableComponent hint = Component.literal(AttributePreviewHelper.getPreviewText(attribute.getAttribute(), attribute.getValue(), attribute.getModifier()));
                String formula = AttributePreviewHelper.getFormulaText(attribute.getValue(), attribute.getModifier());
                if (formula != null) {
                    hint.append("  " + formula);
                }
                return hint;
            }, Theme.GREY, list.partsWidth())));
        }
    }

    private static String operationOf(ArmorSetData.AttributeData attribute) {
        String op = attribute.getModifier() == null ? SetIds.ADDITION : attribute.getModifier().toLowerCase(Locale.ROOT);
        return SetIds.MULTIPLY.equals(op) ? SetIds.MULTIPLY_BASE : op;
    }

    private void pickEffect(SetEditorData.PartConfig part) {
        List<RegistryPickerScreen.Option<String>> options = BuiltInRegistries.MOB_EFFECT.listElements()
                .map(h -> new RegistryPickerScreen.Option<>(h.key().identifier().toString(), h.value().getDisplayName(),
                        h.key().identifier().toString(), Hud.getMobEffectSprite(h), (ItemStack) null))
                .toList();
        this.minecraft.gui.setScreen(RegistryPickerScreen.multi(this, Component.translatable("gui.setweaver.browser.pick_effect"), options,
                id -> part.getEffects().stream().anyMatch(effect -> id.equals(effect.getEffect())), ids -> {
                    ids.forEach(id -> part.getEffects().add(SetEditorData.effect(id, 0)));
                    this.rebuildWidgets();
                }));
    }

    private void pickAttribute(SetEditorData.PartConfig part) {
        // The same attribute may appear several times (e.g. addition and multiply_base), so nothing counts as present
        this.minecraft.gui.setScreen(RegistryPickerScreen.multi(this, Component.translatable("gui.setweaver.browser.pick_attribute"),
                attributeOptions(), id -> false, ids -> {
                    ids.forEach(id -> part.getAttributes().add(SetEditorData.attribute(id, 1.0, SetIds.ADDITION)));
                    this.rebuildWidgets();
                }));
    }

    private static List<RegistryPickerScreen.Option<String>> attributeOptions() {
        return BuiltInRegistries.ATTRIBUTE.listElements()
                .map(h -> RegistryPickerScreen.Option.of(h.key().identifier().toString(),
                        (Component) Component.translatable(h.value().getDescriptionId()), h.key().identifier().toString()))
                .toList();
    }

    private void pickCopySource(SetDraft draft, int target) {
        List<RegistryPickerScreen.Option<Integer>> options = new ArrayList<>();
        for (int i = 0; i < ArmorSetData.MAX_PARTS; i++) {
            if (i != target) {
                SetEditorData.PartConfig part = draft.data().getPart(i);
                options.add(RegistryPickerScreen.Option.of(i, Component.literal(GuiUtil.pieces(i + 1)),
                        GuiUtil.tr("gui.setweaver.browser.copy_from.detail", part.getEffects().size(), part.getAttributes().size())));
            }
        }
        this.minecraft.gui.setScreen(new RegistryPickerScreen<>(this, Component.translatable("gui.setweaver.browser.copy_from.title",
                GuiUtil.pieces(target + 1)), options, source -> {
                    draft.data().getPart(target).copyFrom(draft.data().getPart(source));
                    this.rebuildWidgets();
                }, null, false));
    }

    // ---------------------------------------------------------------- package form

    private @Nullable List<FormList.Row> packageForm() {
        if (this.packageIndex < 0) {
            return null;
        }
        if (this.packagesDraft == null) {
            this.packagesDraft = new PackagesDraft(AttributePackageManager.client().getAllPackages());
        }
        if (this.packageIndex >= this.packagesDraft.packages().size()) {
            return null;
        }
        AttributePackage pkg = this.packagesDraft.packages().get(this.packageIndex);
        FormList list = this.newForm();
        this.form = list;
        List<FormList.Row> rows = new ArrayList<>();
        this.addStatusRows(list, rows);
        rows.add(list.header(Component.translatable("gui.setweaver.browser.package")));
        EditBox name = this.textField(Math.min(160, list.partsWidth()), pkg.getName() == null ? "" : pkg.getName(), 50,
                Component.translatable("gui.setweaver.browser.package.name.hint"), pkg::setName);
        rows.add(list.field(Component.translatable("gui.setweaver.browser.package.name"), PackagesDraft.NAME, widget(name)));
        EditBox priority = this.textField(40, Integer.toString(pkg.getPriority()), 4, Component.empty(),
                text -> parseInt(text).ifPresent(pkg::setPriority));
        rows.add(list.field(Component.translatable("gui.setweaver.browser.package.priority"), PackagesDraft.PRIORITY,
                widget(this.step(priority, -1)), widget(priority), widget(this.step(priority, 1)),
                text(() -> Component.translatable("gui.setweaver.browser.package.priority.hint"), Theme.GREY, 120)));
        rows.add(list.field(Component.translatable("gui.setweaver.browser.package.enabled"), PackagesDraft.ENABLED,
                widget(this.cycle(80, () -> Component.translatable(pkg.isEnabled() ? "options.on" : "options.off"),
                        () -> pkg.setEnabled(!pkg.isEnabled())))));
        rows.add(list.header(Component.translatable("gui.setweaver.browser.package.attributes", pkg.getAttributes().size()),
                widget(Button.builder(Component.translatable("gui.setweaver.browser.add_attribute"), button -> this.minecraft.gui.setScreen(
                        RegistryPickerScreen.multi(this, Component.translatable("gui.setweaver.browser.pick_attribute"), attributeOptions(),
                                id -> pkg.getAttributes().contains(id), ids -> {
                                    List<String> attributes = new ArrayList<>(pkg.getAttributes());
                                    ids.stream().filter(id -> !attributes.contains(id)).forEach(attributes::add);
                                    pkg.setAttributes(attributes);
                                    this.rebuildWidgets();
                                }))).bounds(0, 0, 70, SMALL).build())));
        if (pkg.getAttributes().isEmpty()) {
            rows.add(list.text(Component.translatable("gui.setweaver.browser.none"), Theme.DARK_GREY, 8));
        }
        for (String id : pkg.getAttributes()) {
            rows.add(list.entry(Component.literal(AttributePreviewHelper.attributeName(id)), PackagesDraft.ATTRIBUTES, Theme.WHITE,
                    List.of(Component.literal(id)), widget(this.remove(() -> {
                        List<String> attributes = new ArrayList<>(pkg.getAttributes());
                        attributes.remove(id);
                        pkg.setAttributes(attributes);
                    }))));
        }
        rows.add(list.text(Component.empty(), Theme.WHITE, 0));
        for (FormattedCharSequence line : this.font.split(Component.translatable("gui.setweaver.browser.package.help"), list.textWidth())) {
            rows.add(list.text(Component.literal(plain(line)), Theme.DARK_GREY, 0));
        }
        return rows;
    }

    // ---------------------------------------------------------------- tag form

    private boolean isEditable(TagDraft draft) {
        return draft.isNew() || SetWeaver.MODID.equals(draft.namespace);
    }

    private @Nullable List<FormList.Row> tagForm() {
        if (this.itemTag != null && (this.tagDraft == null || this.tagDraft.isNew() || !this.itemTag.equals(this.tagDraft.id()))) {
            TagKey<Item> key = this.armorTags.stream().filter(t -> t.location().equals(this.itemTag)).findFirst().orElse(null);
            if (key == null) {
                return null;
            }
            this.tagDraft = TagDraft.of(this.itemTag, GuiUtil.itemsInTag(key).stream()
                    .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()).toList());
        }
        if (this.tagDraft == null) {
            return null;
        }
        TagDraft draft = this.tagDraft;
        boolean editable = this.isEditable(draft);
        FormList list = this.newForm();
        this.form = list;
        List<FormList.Row> rows = new ArrayList<>();
        this.addStatusRows(list, rows);
        rows.add(list.header(Component.translatable(draft.isNew() ? "gui.setweaver.browser.tag.new" : "gui.setweaver.browser.tag")));
        if (draft.isNew()) {
            rows.add(list.field(Component.translatable("gui.setweaver.browser.tag.namespace"), TagDraft.ID,
                    widget(this.textField(Math.min(140, list.partsWidth()), draft.namespace, 50, Component.empty(), v -> draft.namespace = v))));
            rows.add(list.field(Component.translatable("gui.setweaver.browser.tag.path"), TagDraft.ID,
                    widget(this.textField(Math.min(140, list.partsWidth()), draft.path, 100,
                            Component.translatable("gui.setweaver.browser.tag.path.hint"), v -> draft.path = v)),
                    text(() -> draft.id() == null ? Component.translatable("gui.setweaver.browser.tag.invalid_id")
                            : Component.literal("#" + draft.id()), Theme.GREY, 140)));
        } else {
            rows.add(list.text(Component.literal("#" + draft.id()), Theme.GREY, 0));
        }
        if (!editable) {
            rows.add(list.text(Component.translatable("gui.setweaver.browser.tag.read_only"), Theme.DARK_GREY, 0));
        } else {
            rows.add(list.text(Component.translatable("gui.setweaver.browser.tag.reload_hint"), Theme.DARK_GREY, 0));
        }
        List<FormList.Part> headerParts = new ArrayList<>();
        if (editable) {
            headerParts.add(widget(Button.builder(Component.translatable("gui.setweaver.browser.add_item"), button -> this.pickItem(draft))
                    .bounds(0, 0, 60, SMALL).build()));
        }
        rows.add(list.header(Component.translatable("gui.setweaver.browser.tag.items", draft.items().size()), headerParts.toArray(FormList.Part[]::new)));
        if (draft.items().isEmpty()) {
            rows.add(list.text(Component.translatable("gui.setweaver.browser.none"), Theme.DARK_GREY, 8));
        }
        for (String id : draft.items()) {
            Identifier itemId = Identifier.tryParse(id);
            Item item = itemId == null ? null : BuiltInRegistries.ITEM.getValue(itemId);
            List<FormList.Part> parts = new ArrayList<>();
            Component label = Component.literal(id);
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                parts.add(new FormList.ItemPart(stack));
                label = stack.getHoverName();
            }
            if (editable) {
                parts.add(widget(this.remove(() -> draft.items().remove(id))));
            }
            rows.add(list.entry(label, TagDraft.ITEMS, Theme.WHITE, List.of(Component.literal(id)), parts.toArray(FormList.Part[]::new)));
        }
        if (!draft.isNew()) {
            List<SetEntry> sets = this.entriesOf(draft.id().toString());
            rows.add(list.text(Component.empty(), Theme.WHITE, 0));
            rows.add(list.text(Component.translatable(sets.isEmpty() ? "gui.setweaver.browser.tag.no_sets" : "gui.setweaver.browser.tag.sets",
                    sets.size()), Theme.GREY, 0));
        }
        return rows;
    }

    private void pickItem(TagDraft draft) {
        List<RegistryPickerScreen.Option<String>> options = new ArrayList<>();
        BuiltInRegistries.ITEM.listElements().forEach(h -> {
            if (ArmorPieces.isArmor(h.value())) {
                ItemStack stack = new ItemStack(h.value());
                options.add(new RegistryPickerScreen.Option<>(h.key().identifier().toString(), stack.getHoverName(),
                        h.key().identifier().toString(), null, stack));
            }
        });
        this.minecraft.gui.setScreen(RegistryPickerScreen.multi(this, Component.translatable("gui.setweaver.browser.pick_item"), options,
                id -> draft.items().contains(id), ids -> {
                    ids.stream().filter(id -> !draft.items().contains(id)).forEach(draft.items()::add);
                    this.rebuildWidgets();
                }));
    }

    // ---------------------------------------------------------------- form widgets

    private EditBox textField(int width, String value, int maxLength, Component hint, Consumer<String> onChange) {
        EditBox box = new EditBox(this.font, 0, 0, width, 18, Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setHint(hint);
        box.setResponder(onChange);
        return box;
    }

    private Button small(Component label, Runnable action) {
        return Button.builder(label, button -> action.run()).bounds(0, 0, SMALL, SMALL).build();
    }

    private Button remove(Runnable action) {
        Button button = Button.builder(Component.translatable("gui.setweaver.browser.remove"), pressed -> {
            action.run();
            this.rebuildWidgets();
        }).bounds(0, 0, SMALL, SMALL).build();
        button.setTooltip(Tooltip.create(Component.translatable("gui.setweaver.browser.remove.tooltip")));
        return button;
    }

    private Button cycle(int width, Supplier<Component> label, Runnable next) {
        return Button.builder(label.get(), button -> {
            next.run();
            button.setMessage(label.get());
        }).bounds(0, 0, width, 18).build();
    }

    private Button step(EditBox box, int delta) {
        return this.small(Component.translatable(delta < 0 ? "gui.setweaver.browser.decrease" : "gui.setweaver.browser.increase"),
                () -> box.setValue(Integer.toString(Math.max(0, parseInt(box.getValue()).orElse(0) + delta))));
    }

    private Button decimalStep(EditBox box, ArmorSetData.AttributeData attribute, int direction) {
        return this.small(Component.translatable(direction < 0 ? "gui.setweaver.browser.decrease" : "gui.setweaver.browser.increase"), () -> {
            double value = parseDouble(box.getValue()).orElse(attribute.getValue());
            boolean flat = SetIds.ADDITION.equals(operationOf(attribute));
            double base = SetIds.attribute(attribute.getAttribute()).map(h -> h.value().getDefaultValue()).orElse(1.0);
            double step = flat ? (Math.abs(base) >= 1.0 ? 1.0 : 0.01) : 0.05;
            box.setValue(formatNumber(Math.round((value + direction * step) * 100.0) / 100.0));
        });
    }

    private static FormList.WidgetPart widget(net.minecraft.client.gui.components.AbstractWidget widget) {
        return new FormList.WidgetPart(widget);
    }

    private static FormList.TextPart text(Supplier<Component> text, int color, int width) {
        return new FormList.TextPart(text, color, width);
    }

    private static Optional<Double> parseDouble(String text) {
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> parseInt(String text) {
        try {
            return Optional.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "");
    }

    // ---------------------------------------------------------------- actions

    private void save() {
        switch (this.mode) {
            case SETS -> {
                if (this.setDraft == null || !this.setDraft.canSave()) {
                    return;
                }
                this.setDraft.setSaving(true);
                ClientPacketDistributor.sendToServer(new SaveSetPayload(this.setDraft.data().relativePath(),
                        SetWeaverReloadListener.GSON.toJson(this.setDraft.data().toArmorSetData())));
            }
            case PACKAGES -> {
                if (this.packagesDraft == null) {
                    return;
                }
                this.packagesDraft.setSaving(true);
                ClientPacketDistributor.sendToServer(new SavePackagesPayload(this.packagesDraft.toJson()));
            }
            case TAGS -> {
                if (this.tagDraft == null || !this.tagDraft.canSave()) {
                    return;
                }
                Identifier id = this.tagDraft.id();
                ClientPacketDistributor.sendToServer(new SaveTagPayload(id.getNamespace(), id.getPath(), List.copyOf(this.tagDraft.items())));
                // The tag only exists after /reload; until then the saved state is the new baseline
                this.tagDraft = TagDraft.of(id, this.tagDraft.items());
                this.setStatus(Component.translatable("gui.setweaver.browser.tag.saved"), Theme.GREEN);
                this.rebuildWidgets();
                return;
            }
            default -> {
                return;
            }
        }
        this.setStatus(Component.translatable("gui.setweaver.browser.saving"), Theme.GREY);
        this.rebuildWidgets();
    }

    private void discard() {
        switch (this.mode) {
            case SETS -> {
                if (this.setDraft != null && this.setDraft.isNew()) {
                    this.scope = null;
                    this.column2Collapsed = false;
                }
                this.setDraft = null;
            }
            case PACKAGES -> {
                this.packagesDraft = null;
                this.validateSelection();
            }
            case TAGS -> {
                if (this.tagDraft != null && this.tagDraft.isNew()) {
                    this.column2Collapsed = false;
                }
                this.tagDraft = null;
            }
            default -> {
            }
        }
        this.setStatus(null, Theme.GREY);
        this.validateSelection();
        this.remember();
    }

    /** Opens the dialog for a new set. */
    private void newSet() {
        this.leaveDraft(() -> this.minecraft.gui.setScreen(new ScopeDialogScreen(this, Component.translatable("gui.setweaver.browser.new_set"),
                Component.translatable("gui.setweaver.browser.create"),
                new ScopeDialogScreen.Result(this.tag == null ? "" : this.tag, ArmorSetDataRegistry.WILDCARD_ROLE, ArmorSetDataRegistry.WILDCARD_LEVEL, ""),
                result -> this.openScope(result, null))));
    }

    /** Opens the dialog that copies the selected set into another scope. */
    private void copySet() {
        if (this.setDraft == null) {
            return;
        }
        SetEditorData source = this.setDraft.data();
        this.minecraft.gui.setScreen(new ScopeDialogScreen(this, Component.translatable("gui.setweaver.browser.copy.title"),
                Component.translatable("gui.setweaver.browser.copy"),
                new ScopeDialogScreen.Result(source.getTag(), source.getRole(), source.getLevel(), source.getDisplayName()),
                result -> this.openScope(result, source)));
    }

    /**
     * Selects the set of a scope dialog result: an existing one, or a new draft.
     *
     * @param result the chosen tag and scope
     * @param source contents to copy into it, or {@code null}
     */
    private void openScope(ScopeDialogScreen.Result result, @Nullable SetEditorData source) {
        Scope target = new Scope(result.role(), result.level());
        SetEntry existing = this.entry(result.tag(), target);
        Runnable open = () -> {
            SetDraft draft;
            if (existing != null) {
                draft = source == null ? SetDraft.of(existing) : SetDraft.overwrite(existing, source);
            } else {
                SetEditorData data = new SetEditorData();
                data.setTag(result.tag());
                data.setRole(result.role());
                data.setLevel(result.level());
                if (source != null) {
                    data.copyPartsFrom(source);
                }
                draft = SetDraft.create(data);
            }
            if (!result.displayName().isEmpty()) {
                draft.data().setDisplayName(result.displayName());
            }
            this.setDraft = draft;
            this.mode = Mode.SETS;
            this.tag = result.tag();
            this.scope = target;
            this.column1Collapsed = true;
            this.column2Collapsed = true;
            this.setStatus(null, Theme.GREY);
            this.remember();
        };
        if (existing != null && source != null) {
            this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                this.minecraft.gui.setScreen(this);
                if (confirmed) {
                    open.run();
                }
            }, Component.translatable("gui.setweaver.browser.copy.replace.title"),
                    Component.translatable("gui.setweaver.browser.copy.replace.message", path(existing)),
                    Component.translatable("gui.setweaver.browser.copy.replace"), Component.translatable("gui.cancel")));
        } else {
            open.run();
        }
    }

    private void deleteSet() {
        if (this.setDraft == null) {
            return;
        }
        if (this.setDraft.isNew()) {
            this.discard();
            return;
        }
        String file = this.setDraft.data().relativePath();
        this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                this.setDraft = null;
                this.scope = null;
                this.column2Collapsed = false;
                ClientPacketDistributor.sendToServer(new DeleteSetPayload(file));
            }
            this.minecraft.gui.setScreen(this);
        }, Component.translatable("gui.setweaver.browser.delete.title"), Component.translatable("gui.setweaver.browser.delete.message", file),
                Component.translatable("gui.setweaver.browser.delete"), Component.translatable("gui.cancel")));
    }

    private void newPackage() {
        if (this.packagesDraft == null) {
            this.packagesDraft = new PackagesDraft(AttributePackageManager.client().getAllPackages());
        }
        AttributePackage pkg = new AttributePackage();
        pkg.setName(GuiUtil.tr("gui.setweaver.browser.package.new_name"));
        this.packagesDraft.packages().add(pkg);
        this.selectPackage(this.packagesDraft.packages().size() - 1);
    }

    private void deletePackage() {
        if (this.packagesDraft == null || this.packageIndex < 0 || this.packageIndex >= this.packagesDraft.packages().size()) {
            return;
        }
        this.packagesDraft.packages().remove(this.packageIndex);
        this.packageIndex = -1;
        this.column2Collapsed = false;
        this.setStatus(Component.translatable("gui.setweaver.browser.package.deleted"), Theme.CHANGED);
        this.remember();
    }

    private void newTag() {
        this.leaveDraft(() -> {
            this.tagDraft = TagDraft.create();
            this.itemTag = null;
            this.column2Collapsed = true;
            this.setStatus(null, Theme.GREY);
            this.remember();
        });
    }

    /**
     * Runs an action that leaves the current entry; with unsaved changes it asks first.
     *
     * @param action the action
     */
    private void leaveDraft(Runnable action) {
        boolean dirty = (this.setDraft != null && this.setDraft.dirty() && !this.setDraft.saving())
                || (this.packagesDraft != null && this.packagesDraft.dirty() && !this.packagesDraft.saving())
                || (this.tagDraft != null && this.tagDraft.dirty() && this.isEditable(this.tagDraft));
        if (!dirty) {
            this.dropDrafts();
            action.run();
            return;
        }
        this.minecraft.gui.setScreen(new ConfirmScreen(discard -> {
            this.minecraft.gui.setScreen(this);
            if (discard) {
                this.dropDrafts();
                this.validateSelection();
                action.run();
            }
        }, Component.translatable("gui.setweaver.browser.discard.title"), Component.translatable("gui.setweaver.browser.discard.message"),
                Component.translatable("gui.setweaver.browser.discard"), Component.translatable("gui.cancel")));
    }

    private void dropDrafts() {
        this.setDraft = null;
        this.packagesDraft = null;
        this.tagDraft = null;
        this.setStatus(null, Theme.GREY);
    }

    // ---------------------------------------------------------------- selection

    private void selectMode(Mode mode) {
        if (this.mode == mode && mode != Mode.SETS) {
            return;
        }
        this.leaveDraft(() -> {
            this.mode = mode;
            this.tag = null;
            this.scope = null;
            this.packageIndex = -1;
            this.itemTag = null;
            this.column1Collapsed = mode != Mode.SETS;
            this.column2Collapsed = false;
            this.column2Scroll = 0;
            this.detailScroll = 0;
            this.remember();
        });
    }

    private void selectTag(String tag) {
        if (this.mode == Mode.SETS && tag.equals(this.tag) && this.scope == null) {
            return;
        }
        this.leaveDraft(() -> {
            this.mode = Mode.SETS;
            this.tag = tag;
            this.scope = null;
            this.column1Collapsed = true;
            this.column2Collapsed = false;
            this.column2Scroll = 0;
            this.detailScroll = 0;
            this.remember();
        });
    }

    private void selectScope(String tag, Scope scope) {
        if (this.setDraft != null && this.setDraft.is(tag, scope.role(), scope.level())) {
            this.column2Collapsed = true;
            this.remember();
            return;
        }
        this.leaveDraft(() -> {
            this.mode = Mode.SETS;
            this.tag = tag;
            this.scope = scope;
            this.column1Collapsed = true;
            this.column2Collapsed = true;
            this.remember();
        });
    }

    private void selectPackage(int index) {
        this.packageIndex = index;
        this.column1Collapsed = true;
        this.column2Collapsed = true;
        this.remember();
    }

    private void selectItemTag(Identifier id) {
        if (this.tagDraft != null && !this.tagDraft.isNew() && id.equals(this.tagDraft.id())) {
            this.column2Collapsed = true;
            this.remember();
            return;
        }
        this.leaveDraft(() -> {
            this.itemTag = id;
            this.column1Collapsed = true;
            this.column2Collapsed = true;
            this.remember();
        });
    }

    private void remember() {
        rememberedMode = this.mode;
        rememberedTag = this.tag;
        rememberedScope = this.scope;
        rememberedPackage = this.packageIndex;
        rememberedItemTag = this.itemTag;
        this.rebuildWidgets();
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.hits.clear();
        graphics.text(this.font, this.title, PAD, 9, Theme.WHITE, true);
        this.renderColumn1(graphics, mouseX, mouseY);
        if (this.column2Visible()) {
            this.renderColumn2(graphics, mouseX, mouseY);
        }
        this.renderDetails(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        for (Hit hit : this.hits) {
            if (hit.contains(mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(this.font, hit.tooltip(), mouseX, mouseY);
            }
        }
    }

    private void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, Theme.PANEL);
        graphics.outline(x, y, w, h, Theme.BORDER);
    }

    /** Draws a collapsed column: a strip with an arrow that expands it again. */
    private void strip(GuiGraphicsExtractor graphics, int x, int mouseX, int mouseY, Component tooltip) {
        int y = this.top();
        int h = this.bottom() - y;
        this.panel(graphics, x, y, STRIP, h);
        if (mouseX >= x && mouseX < x + STRIP && mouseY >= y && mouseY < y + h) {
            graphics.fill(x + 1, y + 1, x + STRIP - 1, y + h - 1, Theme.HOVER);
        }
        graphics.centeredText(this.font, Component.translatable("gui.setweaver.browser.expand"), x + STRIP / 2, y + 4, Theme.WHITE);
        this.hits.add(new Hit(x, y, x + STRIP, y + h, List.of(tooltip)));
    }

    private void renderColumn1(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.column1X();
        if (this.column1Collapsed) {
            this.strip(graphics, x, mouseX, mouseY, Component.translatable("gui.setweaver.browser.sets"));
            return;
        }
        int y = this.top();
        this.panel(graphics, x, y, this.column1Width(), this.bottom() - y);
        graphics.text(this.font, Component.translatable("gui.setweaver.browser.sets"), x + 4, y + 4, Theme.GOLD, true);
        this.column1Scroll = this.renderRows(graphics, this.column1Rows(), x, this.column1ListTop(), this.column1Width(),
                this.bottom() - this.column1ListTop(), this.column1Scroll, mouseX, mouseY);
    }

    private List<ListRow> column1Rows() {
        String filter = this.setQuery.trim().toLowerCase(Locale.ROOT);
        Map<String, ValidationResult> results = this.validationByPath();
        Map<String, List<SetEntry>> byTag = this.entriesByTag();
        if (this.setDraft != null && this.setDraft.isNew()) {
            byTag.putIfAbsent(this.setDraft.data().getTag(), List.of());
        }
        List<ListRow> rows = new ArrayList<>();
        rows.add(new ListRow(Component.translatable("gui.setweaver.browser.new_set"), Theme.LINK, 0, false, false, 0, 0,
                Component.empty(), this::newSet, List.of(Component.translatable("gui.setweaver.browser.new_set.tooltip"))));
        byTag.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), ArmorSetTooltipHandler.setName(e.getValue().isEmpty() ? null : e.getValue().getFirst().data(), e.getKey())))
                .filter(e -> filter.isEmpty() || e.getValue().toLowerCase(Locale.ROOT).contains(filter) || e.getKey().contains(filter))
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> {
                    List<SetEntry> entries = byTag.get(e.getKey());
                    int errors = 0;
                    int warnings = 0;
                    for (SetEntry entry : entries) {
                        ValidationResult result = results.get(path(entry));
                        if (result != null && result.status() == ValidationResult.Status.WARNING) {
                            warnings++;
                        } else if (result != null && result.status() == ValidationResult.Status.ERROR) {
                            errors++;
                        }
                    }
                    rows.add(new ListRow(Component.literal(e.getValue()), Theme.WHITE, 0, this.mode == Mode.SETS && e.getKey().equals(this.tag),
                            false, errors + warnings, errors > 0 ? Theme.RED : Theme.YELLOW, Component.literal(Integer.toString(entries.size())),
                            () -> this.selectTag(e.getKey()), List.of(Component.literal("#" + e.getKey()))));
                });
        if (byTag.isEmpty()) {
            rows.add(new ListRow(Component.translatable("gui.setweaver.browser.no_sets"), Theme.GREY, 0, false, false, 0, 0,
                    Component.empty(), null, List.of()));
        }
        rows.add(ListRow.SEPARATOR);
        rows.add(new ListRow(Component.translatable("gui.setweaver.browser.packages"), Theme.WHITE, 0, this.mode == Mode.PACKAGES, false, 0, 0,
                Component.literal(Integer.toString(AttributePackageManager.client().getAllPackages().size())), () -> this.selectMode(Mode.PACKAGES),
                List.of(Component.translatable("gui.setweaver.browser.packages.tooltip"))));
        rows.add(new ListRow(Component.translatable("gui.setweaver.browser.tags"), Theme.WHITE, 0, this.mode == Mode.TAGS, false, 0, 0,
                Component.literal(Integer.toString(this.armorTags.size())), () -> this.selectMode(Mode.TAGS),
                List.of(Component.translatable("gui.setweaver.browser.tags.tooltip"))));
        long problems = this.validation == null ? 0 : this.validation.stream().filter(r -> r.status() != ValidationResult.Status.OK).count();
        boolean anyError = this.validation != null && this.validation.stream().anyMatch(r -> r.status() == ValidationResult.Status.ERROR);
        rows.add(new ListRow(Component.translatable("gui.setweaver.browser.validation"), Theme.WHITE, 0, this.mode == Mode.VALIDATION, false,
                (int) problems, anyError ? Theme.RED : Theme.YELLOW, Component.empty(), () -> this.selectMode(Mode.VALIDATION),
                List.of(Component.translatable("gui.setweaver.browser.validation.tooltip"))));
        return rows;
    }

    private void renderColumn2(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.column2X();
        Component title = switch (this.mode) {
            case PACKAGES -> Component.translatable("gui.setweaver.browser.packages");
            case TAGS -> Component.translatable("gui.setweaver.browser.tags");
            default -> Component.translatable("gui.setweaver.browser.scopes");
        };
        if (this.column2Collapsed) {
            this.strip(graphics, x, mouseX, mouseY, title);
            return;
        }
        int y = this.top();
        this.panel(graphics, x, y, this.column2Width(), this.bottom() - y);
        if (this.mode != Mode.TAGS) {
            graphics.text(this.font, title, x + 4, y + 4, Theme.GOLD, true);
        }
        this.column2Scroll = this.renderRows(graphics, this.column2Rows(), x, this.column2ListTop(), this.column2Width(),
                this.bottom() - this.column2ListTop(), this.column2Scroll, mouseX, mouseY);
    }

    private List<ListRow> column2Rows() {
        List<ListRow> rows = new ArrayList<>();
        switch (this.mode) {
            case SETS -> {
                if (this.tag == null) {
                    return rows;
                }
                String tag = this.tag;
                rows.add(new ListRow(Component.translatable("gui.setweaver.browser.add_scope"), Theme.LINK, 0, false, false, 0, 0, Component.empty(),
                        () -> this.leaveDraft(() -> this.minecraft.gui.setScreen(new ScopeDialogScreen(this,
                                Component.translatable("gui.setweaver.browser.new_set"), Component.translatable("gui.setweaver.browser.create"),
                                new ScopeDialogScreen.Result(tag, ArmorSetDataRegistry.WILDCARD_ROLE, ArmorSetDataRegistry.WILDCARD_LEVEL, ""),
                                result -> this.openScope(result, null)))), List.of()));
                Map<String, ValidationResult> results = this.validationByPath();
                List<Scope> scopes = new ArrayList<>(this.entriesOf(tag).stream().map(e -> new Scope(e.role(), e.level())).toList());
                if (this.setDraft != null && this.setDraft.isNew() && this.setDraft.data().getTag().equals(tag)) {
                    scopes.add(new Scope(this.setDraft.data().getRole(), this.setDraft.data().getLevel()));
                }
                scopes.sort(Comparator.comparingInt((Scope s) -> scopeOrder(s.role(), s.level())).thenComparing(Scope::role)
                        .thenComparingInt(Scope::level));
                for (Scope scope : scopes) {
                    SetEntry entry = this.entry(tag, scope);
                    ValidationResult result = entry == null ? null : results.get(path(entry));
                    int problems = result == null ? 0 : result.messages().size();
                    boolean unsaved = this.draftIs(tag, scope) && this.setDraft.dirty();
                    rows.add(new ListRow(Component.literal("● ").append(scopeLabel(scope.role(), scope.level())), scopeColor(scope.role(), scope.level()),
                            0, scope.equals(this.scope), false, problems,
                            result != null && result.status() == ValidationResult.Status.ERROR ? Theme.RED : Theme.YELLOW,
                            unsaved ? Component.translatable("gui.setweaver.browser.unsaved") : Component.empty(),
                            () -> this.selectScope(tag, scope), List.of(Component.literal(SetFiles.relativePath(scope.role(), scope.level(), tag)))));
                }
            }
            case PACKAGES -> {
                rows.add(new ListRow(Component.translatable("gui.setweaver.browser.package.add"), Theme.LINK, 0, false, false, 0, 0,
                        Component.empty(), this::newPackage, List.of()));
                List<AttributePackage> packages = this.packages();
                for (int i = 0; i < packages.size(); i++) {
                    AttributePackage pkg = packages.get(i);
                    int index = i;
                    String name = pkg.getName() == null || pkg.getName().isBlank() ? GuiUtil.tr("gui.setweaver.browser.package.unnamed") : pkg.getName();
                    rows.add(new ListRow(Component.literal(name), pkg.isEnabled() ? Theme.WHITE : Theme.DARK_GREY, 0, i == this.packageIndex, false,
                            0, 0, Component.literal("[" + pkg.getPriority() + "]"), () -> this.selectPackage(index),
                            List.of(Component.translatable(pkg.isEnabled() ? "gui.setweaver.browser.package.on" : "gui.setweaver.browser.package.off"))));
                }
            }
            case TAGS -> {
                rows.add(new ListRow(Component.translatable("gui.setweaver.browser.tag.add"), Theme.LINK, 0, false, false, 0, 0,
                        Component.empty(), this::newTag, List.of()));
                String filter = this.tagQuery.trim().toLowerCase(Locale.ROOT);
                for (TagKey<Item> key : this.armorTags) {
                    Identifier id = key.location();
                    if (!filter.isEmpty() && !id.toString().contains(filter)) {
                        continue;
                    }
                    boolean editable = SetWeaver.MODID.equals(id.getNamespace());
                    rows.add(new ListRow(Component.literal(id.toString()), Theme.WHITE, 0, id.equals(this.itemTag), !editable, 0, 0,
                            Component.empty(), () -> this.selectItemTag(id), List.of(Component.literal("#" + id))));
                }
            }
            default -> {
            }
        }
        return rows;
    }

    /**
     * Draws a scrollable list of rows and registers their tooltips.
     *
     * @return the scroll offset, clamped to the content
     */
    private int renderRows(GuiGraphicsExtractor graphics, List<ListRow> rows, int x, int y, int w, int h, int scroll, int mouseX, int mouseY) {
        int max = Math.max(0, rows.size() * ROW - h);
        scroll = Math.clamp(scroll, 0, max);
        graphics.enableScissor(x + 1, y, x + w - 1, y + h);
        for (int i = 0; i < rows.size(); i++) {
            ListRow row = rows.get(i);
            int rowY = y + i * ROW - scroll;
            if (rowY + ROW < y || rowY > y + h) {
                continue;
            }
            if (row == ListRow.SEPARATOR) {
                graphics.horizontalLine(x + 4, x + w - 5, rowY + ROW / 2, Theme.BORDER);
                continue;
            }
            boolean hovered = row.action() != null && mouseX >= x && mouseX < x + w && mouseY >= Math.max(y, rowY) && mouseY < Math.min(y + h, rowY + ROW);
            if (row.selected()) {
                graphics.fill(x + 1, rowY, x + w - 1, rowY + ROW, Theme.SELECTED);
            } else if (hovered) {
                graphics.fill(x + 1, rowY, x + w - 1, rowY + ROW, Theme.HOVER);
            }
            int right = x + w - 4;
            if (row.problems() > 0) {
                Component problems = Component.translatable("gui.setweaver.browser.problem_marker", row.problems());
                right -= this.font.width(problems);
                graphics.text(this.font, problems, right, rowY + 2, row.problemColor(), false);
                right -= 3;
            }
            if (row.locked()) {
                right -= LOCK_SIZE;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK_SPRITE, right, rowY + 1, LOCK_SIZE, LOCK_SIZE);
                right -= 3;
            }
            if (!row.extra().getString().isEmpty()) {
                right -= this.font.width(row.extra());
                graphics.text(this.font, row.extra(), right, rowY + 2, Theme.GREY, false);
                right -= 3;
            }
            int textX = x + 4 + row.indent();
            graphics.text(this.font, clip(this.font, row.text(), right - textX), textX, rowY + 2, row.color(), false);
            List<Component> tooltip = new ArrayList<>(row.tooltip());
            if (row.locked()) {
                tooltip.add(Component.translatable("gui.setweaver.browser.read_only"));
            }
            if (!tooltip.isEmpty()) {
                this.hits.add(new Hit(x, Math.max(y, rowY), x + w, Math.min(y + h, rowY + ROW), tooltip));
            }
        }
        graphics.disableScissor();
        return scroll;
    }

    private static FormattedCharSequence clip(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        List<FormattedCharSequence> lines = font.split(text, Math.max(10, width));
        return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.getFirst();
    }

    private void renderDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.detailsX();
        int y = this.top();
        int w = this.detailsWidth();
        this.panel(graphics, x, y, w, this.bottom() - y);
        if (this.form != null) {
            graphics.horizontalLine(x + 1, x + w - 2, this.bottom() - FOOTER, Theme.BORDER);
            return;
        }
        int contentHeight = this.detailContentHeight();
        List<WrappedLine> wrapped = this.detailWrapped();
        this.detailScroll = Math.clamp(this.detailScroll, 0, Math.max(0, wrapped.size() * (ROW - 1) - contentHeight));
        graphics.enableScissor(x + 1, y + 2, x + w - 1, y + 2 + contentHeight);
        for (int i = 0; i < wrapped.size(); i++) {
            WrappedLine line = wrapped.get(i);
            int lineY = this.detailLineY(i);
            if (lineY + ROW < y || lineY > y + contentHeight) {
                continue;
            }
            int lineX = x + 6 + line.line().indent();
            boolean hovered = line.line().action() != null && mouseX >= lineX && mouseX < lineX + line.width() && mouseY >= lineY && mouseY < lineY + ROW - 1;
            graphics.text(this.font, line.text(), lineX, lineY, hovered ? Theme.WHITE : line.line().color(), false);
        }
        graphics.disableScissor();
    }

    private int detailLineY(int index) {
        return this.top() + 6 + index * (ROW - 1) - this.detailScroll;
    }

    private List<WrappedLine> detailWrapped() {
        int w = this.detailsWidth();
        List<WrappedLine> wrapped = new ArrayList<>();
        for (Line line : this.detailLines()) {
            if (line.text() == null) {
                wrapped.add(new WrappedLine(FormattedCharSequence.EMPTY, line, 0));
                continue;
            }
            for (FormattedCharSequence part : this.font.split(line.text(), Math.max(20, w - 12 - line.indent()))) {
                wrapped.add(new WrappedLine(part, line, this.font.width(part)));
            }
        }
        return wrapped;
    }

    /** @return the text shown in the details when no form is open */
    private List<Line> detailLines() {
        List<Line> lines = new ArrayList<>();
        switch (this.mode) {
            case SETS -> {
                if (this.tag == null) {
                    lines.add(Line.of(Component.translatable("gui.setweaver.browser.sets"), Theme.GOLD));
                    lines.add(Line.of(Component.translatable("gui.setweaver.browser.sets.help"), Theme.GREY));
                    lines.add(Line.BLANK);
                    lines.add(Line.link(Component.translatable("gui.setweaver.browser.new_set"), this::newSet));
                } else {
                    this.tagOverview(lines, this.tag);
                }
            }
            case PACKAGES -> {
                lines.add(Line.of(Component.translatable("gui.setweaver.browser.packages"), Theme.GOLD));
                lines.add(Line.of(Component.translatable("gui.setweaver.browser.package.help"), Theme.GREY));
            }
            case TAGS -> {
                lines.add(Line.of(Component.translatable("gui.setweaver.browser.tags"), Theme.GOLD));
                lines.add(Line.of(Component.translatable("gui.setweaver.browser.tags.help"), Theme.GREY));
            }
            case VALIDATION -> this.validationLines(lines);
        }
        return lines;
    }

    private void tagOverview(List<Line> lines, String tag) {
        List<SetEntry> entries = this.entriesOf(tag);
        lines.add(Line.of(Component.literal(ArmorSetTooltipHandler.setName(entries.isEmpty() ? null : entries.getFirst().data(), tag))
                .withStyle(style -> style.withBold(true)), Theme.GOLD));
        lines.add(Line.of(Component.literal("#" + tag), Theme.GREY));
        lines.add(Line.BLANK);
        lines.add(Line.of(Component.translatable("gui.setweaver.browser.scopes"), Theme.GOLD));
        for (SetEntry entry : entries) {
            lines.add(Line.link(Component.literal("● ").append(entry.scopeLabel()), () -> this.selectScope(tag, new Scope(entry.role(), entry.level()))));
        }
        lines.add(Line.link(Component.translatable("gui.setweaver.browser.add_scope"), () -> this.minecraft.gui.setScreen(new ScopeDialogScreen(this,
                Component.translatable("gui.setweaver.browser.new_set"), Component.translatable("gui.setweaver.browser.create"),
                new ScopeDialogScreen.Result(tag, ArmorSetDataRegistry.WILDCARD_ROLE, ArmorSetDataRegistry.WILDCARD_LEVEL, ""),
                result -> this.openScope(result, null)))));
        lines.add(Line.BLANK);
        Identifier id = Identifier.tryParse(tag);
        TagKey<Item> key = id == null ? null : TagKey.create(net.minecraft.core.registries.Registries.ITEM, id);
        List<Item> items = key == null ? List.of() : GuiUtil.itemsInTag(key);
        lines.add(Line.of(Component.translatable("gui.setweaver.browser.tag.items", items.size()), Theme.GOLD));
        if (items.isEmpty()) {
            lines.add(Line.indented(Component.translatable("gui.setweaver.browser.tag.empty"), Theme.RED));
        }
        for (Item item : items) {
            lines.add(Line.indented(new ItemStack(item).getHoverName(), Theme.WHITE));
        }
    }

    private void validationLines(List<Line> lines) {
        lines.add(Line.of(Component.translatable("gui.setweaver.browser.validation"), Theme.GOLD));
        if (this.validation == null) {
            lines.add(Line.of(Component.translatable("gui.setweaver.browser.validation.waiting"), Theme.GREY));
            return;
        }
        long ok = this.validation.stream().filter(r -> r.status() == ValidationResult.Status.OK).count();
        long warnings = this.validation.stream().filter(r -> r.status() == ValidationResult.Status.WARNING).count();
        long errors = this.validation.stream().filter(r -> r.status() == ValidationResult.Status.ERROR).count();
        lines.add(Line.of(Component.translatable("gui.setweaver.browser.validation.summary", ok, warnings, errors), Theme.GREY));
        lines.add(Line.link(Component.translatable("gui.setweaver.browser.validation.refresh"),
                () -> ClientPacketDistributor.sendToServer(new ValidateRequestPayload())));
        lines.add(Line.BLANK);
        Map<String, SetEntry> byPath = new HashMap<>();
        ArmorSetDataRegistry.client().getAllEntries().forEach(e -> byPath.put(path(e), e));
        List<ValidationResult> sorted = new ArrayList<>(this.validation);
        sorted.sort(Comparator.comparingInt((ValidationResult r) -> -r.status().ordinal()).thenComparing(ValidationResult::filePath));
        for (ValidationResult result : sorted) {
            int color = switch (result.status()) {
                case OK -> Theme.GREEN;
                case WARNING -> Theme.YELLOW;
                case ERROR -> Theme.RED;
            };
            String icon = switch (result.status()) {
                case OK -> "✔ ";
                case WARNING -> "⚠ ";
                case ERROR -> "✘ ";
            };
            SetEntry entry = byPath.get(result.filePath());
            Component text = Component.literal(icon + result.filePath());
            lines.add(entry == null ? Line.of(text, color)
                    : new Line(text, color, 0, () -> this.selectScope(entry.tag(), new Scope(entry.role(), entry.level()))));
            for (Component message : result.messages()) {
                lines.add(Line.indented(message, Theme.GREY));
            }
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        Runnable action = this.actionAt(event.x(), event.y());
        if (action != null) {
            action.run();
            return true;
        }
        return false;
    }

    /** @return what a click at a position does, computed from the layout the screen is drawn with, or {@code null} */
    private @Nullable Runnable actionAt(double mouseX, double mouseY) {
        if (mouseY < this.top() || mouseY >= this.bottom()) {
            return null;
        }
        if (mouseX >= this.column1X() && mouseX < this.column1X() + this.column1Width()) {
            if (this.column1Collapsed) {
                return () -> {
                    this.column1Collapsed = false;
                    // On narrow screens only one list column is open at a time, so the details keep a usable width
                    if (this.detailsWidth() < MIN_DETAILS) {
                        this.column2Collapsed = true;
                    }
                    this.rebuildWidgets();
                };
            }
            return rowAction(this.column1Rows(), mouseY - this.column1ListTop() + this.column1Scroll);
        }
        if (this.column2Visible() && mouseX >= this.column2X() && mouseX < this.column2X() + this.column2Width()) {
            if (this.column2Collapsed) {
                return () -> {
                    this.column2Collapsed = false;
                    if (this.detailsWidth() < MIN_DETAILS) {
                        this.column1Collapsed = true;
                    }
                    this.rebuildWidgets();
                };
            }
            return rowAction(this.column2Rows(), mouseY - this.column2ListTop() + this.column2Scroll);
        }
        if (this.form == null && mouseX >= this.detailsX()) {
            List<WrappedLine> wrapped = this.detailWrapped();
            for (int i = 0; i < wrapped.size(); i++) {
                WrappedLine line = wrapped.get(i);
                int lineY = this.detailLineY(i);
                int lineX = this.detailsX() + 6 + line.line().indent();
                if (line.line().action() != null && mouseX >= lineX && mouseX < lineX + line.width() && mouseY >= lineY && mouseY < lineY + ROW - 1) {
                    return line.line().action();
                }
            }
        }
        return null;
    }

    private static @Nullable Runnable rowAction(List<ListRow> rows, double offset) {
        if (offset < 0) {
            return null;
        }
        int index = (int) (offset / ROW);
        return index < rows.size() ? rows.get(index).action() : null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = (int) (-scrollY * ROW * 2);
        if (!this.column1Collapsed && mouseX >= this.column1X() && mouseX < this.column1X() + this.column1Width()) {
            this.column1Scroll += delta;
        } else if (this.column2Visible() && !this.column2Collapsed && mouseX >= this.column2X() && mouseX < this.column2X() + this.column2Width()) {
            this.column2Scroll += delta;
        } else if (mouseX >= this.detailsX() && this.form != null) {
            return this.form.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        } else if (mouseX >= this.detailsX()) {
            this.detailScroll += delta;
        } else {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return true;
    }

    @Override
    public void onClose() {
        this.leaveDraft(() -> {
            current = null;
            super.onClose();
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String plain(FormattedCharSequence sequence) {
        StringBuilder builder = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

    // ---------------------------------------------------------------- helper records

    /** An area with a tooltip, registered while rendering. */
    private record Hit(double x0, double y0, double x1, double y1, List<Component> tooltip) {

        boolean contains(double x, double y) {
            return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
        }
    }

    /** One row of a list column. */
    private record ListRow(Component text, int color, int indent, boolean selected, boolean locked, int problems, int problemColor,
                           Component extra, @Nullable Runnable action, List<Component> tooltip) {

        static final ListRow SEPARATOR = new ListRow(Component.empty(), 0, 0, false, false, 0, 0, Component.empty(), null, List.of());
    }

    /** One logical line of the details, wrapped when drawn. A {@code null} text is an empty line. */
    private record Line(@Nullable Component text, int color, int indent, @Nullable Runnable action) {

        static final Line BLANK = new Line(null, 0, 0, null);

        static Line of(Component text, int color) {
            return new Line(text, color, 0, null);
        }

        static Line indented(Component text, int color) {
            return new Line(text, color, 8, null);
        }

        static Line link(Component text, Runnable action) {
            return new Line(text, Theme.LINK, 0, action);
        }
    }

    /** A wrapped part of a {@link Line}. */
    private record WrappedLine(FormattedCharSequence text, Line line, int width) {
    }
}
