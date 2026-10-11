package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.SetWeaver;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Local, unsaved edit of an item tag of the {@code setweaver_custom} datapack.
 */
public final class TagDraft {

    /** Field key of the id. */
    public static final String ID = "id";
    /** Field key of the item list. */
    public static final String ITEMS = "items";

    private final boolean isNew;
    private final List<String> originalItems;
    private final List<String> items;
    /** Namespace while editing a new tag. */
    public String namespace;
    /** Path while editing a new tag. */
    public String path;

    private TagDraft(boolean isNew, String namespace, String path, List<String> items) {
        this.isNew = isNew;
        this.namespace = namespace;
        this.path = path;
        this.originalItems = List.copyOf(items);
        this.items = new ArrayList<>(items);
    }

    /** @return a draft for a new tag in the mod's namespace */
    public static TagDraft create() {
        return new TagDraft(true, SetWeaver.MODID, "", List.of());
    }

    /**
     * Starts editing an existing tag.
     *
     * @param id    the tag id
     * @param items its item ids
     * @return the draft
     */
    public static TagDraft of(Identifier id, List<String> items) {
        return new TagDraft(false, id.getNamespace(), id.getPath(), items);
    }

    /** @return whether the tag does not exist yet */
    public boolean isNew() {
        return this.isNew;
    }

    /** @return the edited item ids (mutable) */
    public List<String> items() {
        return this.items;
    }

    /** @return the tag id, or {@code null} while namespace or path are invalid */
    public @Nullable Identifier id() {
        return this.path.isBlank() ? null
                : Identifier.tryBuild(this.namespace.trim().toLowerCase(Locale.ROOT), this.path.trim().toLowerCase(Locale.ROOT));
    }

    /** @return whether there is something to save */
    public boolean dirty() {
        return this.isNew || !this.originalItems.equals(this.items);
    }

    /** @return whether saving is possible */
    public boolean canSave() {
        return this.dirty() && this.id() != null && !this.items.isEmpty();
    }

    /** @return whether the item list differs from the stored tag */
    public boolean itemsChanged() {
        return this.isNew || !this.originalItems.equals(this.items);
    }
}
