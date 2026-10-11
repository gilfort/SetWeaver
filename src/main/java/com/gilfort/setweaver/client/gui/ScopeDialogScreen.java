package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.SetFiles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog that chooses the item tag and the scope (role and level) of a new set or of a copy.
 */
public class ScopeDialogScreen extends Screen {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 168;
    private static final int LABEL = 80;
    private static final int ROW = 24;
    private static final int MAX_LEVEL = 999;

    /**
     * The chosen values.
     *
     * @param tag         item tag id
     * @param role        role or {@link ArmorSetDataRegistry#WILDCARD_ROLE}
     * @param level       level or {@link ArmorSetDataRegistry#WILDCARD_LEVEL}
     * @param displayName display name, empty for none
     */
    public record Result(String tag, String role, int level, String displayName) {
    }

    private final Screen parent;
    private final Component confirmLabel;
    private final java.util.function.Consumer<Result> onConfirm;
    private @Nullable String tag;
    private String role;
    private int level;
    private boolean allLevels;
    private String displayName;
    private @Nullable Component error;
    private @Nullable EditBox levelBox;

    /**
     * @param parent       screen to return to
     * @param title        dialog title
     * @param confirmLabel label of the confirm button
     * @param start        initial values ({@code null} tag = none chosen)
     * @param onConfirm    called with the values after returning to the parent
     */
    public ScopeDialogScreen(Screen parent, Component title, Component confirmLabel, Result start, java.util.function.Consumer<Result> onConfirm) {
        super(title);
        this.parent = parent;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
        this.tag = start.tag().isEmpty() ? null : start.tag();
        this.role = start.role();
        this.allLevels = start.level() == ArmorSetDataRegistry.WILDCARD_LEVEL;
        this.level = this.allLevels ? 1 : start.level();
        this.displayName = start.displayName();
    }

    private int left() {
        return (this.width - WIDTH) / 2;
    }

    private int top() {
        return (this.height - HEIGHT) / 2;
    }

    private int rowY(int row) {
        return this.top() + 24 + row * ROW;
    }

    @Override
    protected void init() {
        int x = this.left() + 8 + LABEL;
        int w = WIDTH - 16 - LABEL;
        this.addRenderableWidget(Button.builder(this.tag == null ? Component.translatable("gui.setweaver.browser.scope.pick_tag")
                : Component.literal("#" + this.tag), button -> this.pickTag()).bounds(x, this.rowY(0), w, 20).build());
        this.addRenderableWidget(Button.builder(roleLabel(this.role), button -> this.pickRole()).bounds(x, this.rowY(1), w, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.browser.decrease"), button -> this.stepLevel(-1))
                .bounds(x, this.rowY(2) + 2, 16, 16).build()).active = !this.allLevels;
        this.levelBox = new EditBox(this.font, x + 19, this.rowY(2) + 1, 40, 18, Component.translatable("gui.setweaver.browser.scope.level"));
        this.levelBox.setMaxLength(3);
        this.levelBox.setValue(Integer.toString(this.level));
        this.levelBox.setEditable(!this.allLevels);
        this.levelBox.active = !this.allLevels;
        this.levelBox.setResponder(value -> {
            try {
                this.level = Math.clamp(Integer.parseInt(value.trim()), 1, MAX_LEVEL);
            } catch (NumberFormatException e) {
                // keep the last valid level
            }
        });
        this.addRenderableWidget(this.levelBox);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.setweaver.browser.increase"), button -> this.stepLevel(1))
                .bounds(x + 62, this.rowY(2) + 2, 16, 16).build()).active = !this.allLevels;
        this.addRenderableWidget(Button.builder(Component.translatable(this.allLevels ? "gui.setweaver.browser.scope.all_levels.on"
                : "gui.setweaver.browser.scope.all_levels.off"), button -> {
                    this.allLevels = !this.allLevels;
                    this.rebuildWidgets();
                }).bounds(x + 82, this.rowY(2), w - 82, 20).build());

        EditBox name = new EditBox(this.font, x, this.rowY(3), w, 20, Component.translatable("gui.setweaver.browser.field.name"));
        name.setMaxLength(64);
        name.setHint(Component.translatable("gui.setweaver.browser.field.name.hint"));
        name.setValue(this.displayName);
        name.setResponder(value -> this.displayName = value);
        this.addRenderableWidget(name);

        int buttonY = this.top() + HEIGHT - 26;
        this.addRenderableWidget(Button.builder(this.confirmLabel, button -> this.confirm()).bounds(this.left() + WIDTH / 2 - 104, buttonY, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(this.left() + WIDTH / 2 + 4, buttonY, 100, 20).build());
    }

    private void stepLevel(int delta) {
        this.level = Math.clamp(this.level + delta, 1, MAX_LEVEL);
        if (this.levelBox != null) {
            this.levelBox.setValue(Integer.toString(this.level));
        }
    }

    private void pickTag() {
        List<RegistryPickerScreen.Option<String>> options = new ArrayList<>();
        GuiUtil.armorItemTags().forEach(tagKey -> {
            List<net.minecraft.world.item.Item> items = GuiUtil.itemsInTag(tagKey);
            options.add(new RegistryPickerScreen.Option<>(tagKey.location().toString(), Component.literal("#" + tagKey.location()),
                    GuiUtil.tr("gui.setweaver.browser.item_count", items.size()), null,
                    items.isEmpty() ? null : new ItemStack(items.getFirst())));
        });
        this.minecraft.gui.setScreen(new RegistryPickerScreen<>(this, Component.translatable("gui.setweaver.browser.scope.pick_tag"), options,
                picked -> {
                    this.tag = picked;
                    this.error = null;
                }));
    }

    private void pickRole() {
        List<RegistryPickerScreen.Option<String>> options = new ArrayList<>();
        options.add(RegistryPickerScreen.Option.of(ArmorSetDataRegistry.WILDCARD_ROLE, roleLabel(ArmorSetDataRegistry.WILDCARD_ROLE), "*"));
        ArmorSetDataRegistry.client().getRoles().forEach(r -> options.add(RegistryPickerScreen.Option.of(r, Component.literal(r), "")));
        this.minecraft.gui.setScreen(new RegistryPickerScreen<>(this, Component.translatable("gui.setweaver.browser.scope.pick_role"), options,
                picked -> {
                    this.role = picked;
                    this.error = null;
                }, typed -> {
                    String normalized = ArmorSetDataRegistry.normalizeRole(typed);
                    return SetFiles.isValidRole(normalized) ? normalized : null;
                }, false));
    }

    private void confirm() {
        if (this.tag == null) {
            this.error = Component.translatable("gui.setweaver.browser.scope.error.no_tag");
            return;
        }
        boolean wildRole = ArmorSetDataRegistry.WILDCARD_ROLE.equals(this.role);
        if (!wildRole && this.allLevels) {
            this.error = Component.translatable("gui.setweaver.browser.scope.error.role_needs_level");
            return;
        }
        Result result = new Result(this.tag, this.role, this.allLevels ? ArmorSetDataRegistry.WILDCARD_LEVEL : this.level, this.displayName.trim());
        this.onClose();
        this.onConfirm.accept(result);
    }

    private static Component roleLabel(String role) {
        return ArmorSetDataRegistry.WILDCARD_ROLE.equals(role) ? Component.translatable("gui.setweaver.browser.scope.all_roles") : Component.literal(role);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.left();
        int top = this.top();
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, Theme.DIALOG);
        graphics.outline(left, top, WIDTH, HEIGHT, Theme.BORDER);
        graphics.text(this.font, this.title, left + 8, top + 8, Theme.GOLD, true);
        String[] labels = {"tag", "role", "level", "name"};
        for (int i = 0; i < labels.length; i++) {
            graphics.text(this.font, Component.translatable("gui.setweaver.browser.scope." + labels[i]), left + 8, this.rowY(i) + 6, Theme.WHITE, false);
        }
        if (this.error != null) {
            graphics.text(this.font, this.error, left + 8, this.rowY(4) + 2, Theme.RED, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && this.getFocused() == null) {
            this.confirm();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
