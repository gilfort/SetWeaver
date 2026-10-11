package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.seteffects.ArmorSetData;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Local, unsaved edit of one set (one tag + scope) in the set browser. Tracks which fields differ from the stored set
 * and which values cannot be parsed.
 */
public final class SetDraft {

    /** Field key of the display name. */
    public static final String NAME = "name";
    private static final Gson GSON = new Gson();

    private final SetEditorData data;
    private final boolean isNew;
    private final String originalName;
    private final List<String> originalParts = new ArrayList<>();
    /** Attribute entries whose value text cannot be parsed. */
    private final Set<ArmorSetData.AttributeData> invalid = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean saving;

    private SetDraft(SetEditorData data, boolean isNew) {
        this.data = data;
        this.isNew = isNew;
        this.originalName = data.getDisplayName();
        for (int i = 0; i < ArmorSetData.MAX_PARTS; i++) {
            this.originalParts.add(partJson(i));
        }
    }

    /**
     * Starts editing a stored set.
     *
     * @param entry the stored set
     * @return the draft
     */
    public static SetDraft of(ArmorSetDataRegistry.SetEntry entry) {
        return new SetDraft(SetEditorData.of(entry), false);
    }

    /**
     * Starts a set that does not exist yet.
     *
     * @param data its scope, name and (possibly copied) contents
     * @return the draft
     */
    public static SetDraft create(SetEditorData data) {
        return new SetDraft(data, true);
    }

    /**
     * Replaces the contents of a stored set with copied ones (copy onto an existing scope).
     *
     * @param entry  the stored set
     * @param source the copied contents
     * @return the draft
     */
    public static SetDraft overwrite(ArmorSetDataRegistry.SetEntry entry, SetEditorData source) {
        SetDraft draft = of(entry);
        draft.data.copyPartsFrom(source);
        return draft;
    }

    /** @return the edited data */
    public SetEditorData data() {
        return this.data;
    }

    /** @return whether the set is not stored yet */
    public boolean isNew() {
        return this.isNew;
    }

    /**
     * @param tag   item tag id
     * @param role  role
     * @param level level
     * @return whether this draft edits that set
     */
    public boolean is(String tag, String role, int level) {
        return this.data.getTag().equals(tag) && this.data.getRole().equals(role) && this.data.getLevel() == level;
    }

    private String partJson(int index) {
        SetEditorData.PartConfig part = this.data.getPart(index);
        return GSON.toJson(List.of(part.getEffects(), part.getAttributes()));
    }

    /** @return field keys that differ from the stored set ({@link #NAME}, {@link #partKey(int)}) */
    public Set<String> changed() {
        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(this.originalName, this.data.getDisplayName())) {
            changed.add(NAME);
        }
        for (int i = 0; i < ArmorSetData.MAX_PARTS; i++) {
            if (!this.originalParts.get(i).equals(partJson(i))) {
                changed.add(partKey(i));
            }
        }
        return changed;
    }

    /**
     * @param index threshold index, 0 = one piece
     * @return the field key of a threshold
     */
    public static String partKey(int index) {
        return "part." + index;
    }

    /** @return whether there is something to save */
    public boolean dirty() {
        return this.isNew || !this.changed().isEmpty();
    }

    /** @return whether saving is possible: changed, all values valid and at least one bonus */
    public boolean canSave() {
        return this.dirty() && this.invalid.isEmpty() && !this.saving && this.data.toArmorSetData().hasBonuses();
    }

    /**
     * Marks an attribute value as (in)valid.
     *
     * @param attribute the attribute entry
     * @param valid     whether its text parses
     */
    public void setValid(ArmorSetData.AttributeData attribute, boolean valid) {
        if (valid) {
            this.invalid.remove(attribute);
        } else {
            this.invalid.add(attribute);
        }
    }

    /**
     * @param attribute an attribute entry
     * @return whether its value text cannot be parsed
     */
    public boolean isInvalid(ArmorSetData.AttributeData attribute) {
        return this.invalid.contains(attribute);
    }

    /** Forgets an attribute entry that was removed. */
    public void forget(ArmorSetData.AttributeData attribute) {
        this.invalid.remove(attribute);
    }

    /** @return whether a save is on its way */
    public boolean saving() {
        return this.saving;
    }

    /** @param saving whether a save is on its way */
    public void setSaving(boolean saving) {
        this.saving = saving;
    }
}
