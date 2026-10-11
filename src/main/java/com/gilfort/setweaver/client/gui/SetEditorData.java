package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.seteffects.ArmorSetData;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.SetFiles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable state of a set while it is edited: scope (tag, role, level), display name and the effects and attributes
 * of the four thresholds. Shared by the Set Wizard and the Set Editor.
 */
public class SetEditorData {

    private String tag = "";
    private String displayName = "";
    private String role = ArmorSetDataRegistry.WILDCARD_ROLE;
    private int level = ArmorSetDataRegistry.WILDCARD_LEVEL;
    private final List<PartConfig> parts = new ArrayList<>();

    /** Effects and attributes of one threshold. */
    public static class PartConfig {
        private final List<ArmorSetData.EffectData> effects = new ArrayList<>();
        private final List<ArmorSetData.AttributeData> attributes = new ArrayList<>();

        /** @return the mutable effect list */
        public List<ArmorSetData.EffectData> getEffects() {
            return effects;
        }

        /** @return the mutable attribute list */
        public List<ArmorSetData.AttributeData> getAttributes() {
            return attributes;
        }

        /** @return whether the threshold grants nothing */
        public boolean isEmpty() {
            return effects.isEmpty() && attributes.isEmpty();
        }

        /** Replaces this part's contents with deep copies of another part's contents. */
        public void copyFrom(PartConfig source) {
            effects.clear();
            attributes.clear();
            source.effects.forEach(e -> effects.add(effect(e.getEffect(), e.getAmplifier())));
            source.attributes.forEach(a -> attributes.add(attribute(a.getAttribute(), a.getValue(), a.getModifier())));
        }
    }

    /** Creates empty editor data with all wildcards. */
    public SetEditorData() {
        for (int i = 0; i < ArmorSetData.MAX_PARTS; i++) {
            parts.add(new PartConfig());
        }
    }

    /**
     * Creates editor data from a loaded set.
     *
     * @param entry the loaded set
     * @return the editor data
     */
    public static SetEditorData of(ArmorSetDataRegistry.SetEntry entry) {
        SetEditorData editorData = new SetEditorData();
        editorData.tag = entry.tag();
        editorData.role = entry.role();
        editorData.level = entry.level();
        editorData.loadParts(entry.data());
        return editorData;
    }

    /**
     * Replaces display name and parts with those of a set definition.
     *
     * @param data the set definition
     */
    public void loadParts(ArmorSetData data) {
        displayName = data.getDisplayName() == null ? "" : data.getDisplayName();
        parts.forEach(p -> p.copyFrom(new PartConfig()));
        if (data.getParts() == null) {
            return;
        }
        data.getParts().forEach((key, part) -> {
            int threshold = ArmorSetData.parseThreshold(key);
            if (threshold < 1 || threshold > ArmorSetData.MAX_PARTS || part == null) {
                return;
            }
            PartConfig config = parts.get(threshold - 1);
            if (part.getEffects() != null) {
                part.getEffects().forEach(e -> config.effects.add(effect(e.getEffect(), e.getAmplifier())));
            }
            if (part.getAttributes() != null) {
                part.getAttributes().forEach(a -> config.attributes.add(attribute(a.getAttribute(), a.getValue(), a.getModifier())));
            }
        });
    }

    /**
     * Copies all parts of another editor data object (deep copy).
     *
     * @param source the source
     */
    public void copyPartsFrom(SetEditorData source) {
        for (int i = 0; i < ArmorSetData.MAX_PARTS; i++) {
            parts.get(i).copyFrom(source.parts.get(i));
        }
    }

    /** @return the set definition for saving; empty thresholds are left out */
    public ArmorSetData toArmorSetData() {
        ArmorSetData data = new ArmorSetData();
        data.setDisplayName(displayName.isBlank() ? null : displayName);
        Map<String, ArmorSetData.PartData> map = new LinkedHashMap<>();
        for (int i = 0; i < ArmorSetData.MAX_PARTS; i++) {
            PartConfig config = parts.get(i);
            if (config.isEmpty()) {
                continue;
            }
            ArmorSetData.PartData part = new ArmorSetData.PartData();
            part.setEffects(new ArrayList<>(config.effects));
            part.setAttributes(new ArrayList<>(config.attributes));
            map.put((i + 1) + ArmorSetData.PART_SUFFIX, part);
        }
        data.setParts(map);
        return data;
    }

    /** @return the file path relative to the set folder */
    public String relativePath() {
        return SetFiles.relativePath(role, level, tag);
    }

    /** @return the item tag id */
    public String getTag() {
        return tag;
    }

    /** @param tag the item tag id */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /** @return the display name, empty if none */
    public String getDisplayName() {
        return displayName;
    }

    /** @param displayName the display name, empty for none */
    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName;
    }

    /** @return the role or {@link ArmorSetDataRegistry#WILDCARD_ROLE} */
    public String getRole() {
        return role;
    }

    /** @param role the role or {@link ArmorSetDataRegistry#WILDCARD_ROLE} */
    public void setRole(String role) {
        this.role = role;
    }

    /** @return the level or {@link ArmorSetDataRegistry#WILDCARD_LEVEL} */
    public int getLevel() {
        return level;
    }

    /** @param level the level or {@link ArmorSetDataRegistry#WILDCARD_LEVEL} */
    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * @param index threshold index, 0 = one piece
     * @return the part
     */
    public PartConfig getPart(int index) {
        return parts.get(index);
    }

    /** Creates an effect entry. */
    public static ArmorSetData.EffectData effect(String id, int amplifier) {
        ArmorSetData.EffectData data = new ArmorSetData.EffectData();
        data.setEffect(id);
        data.setAmplifier(amplifier);
        return data;
    }

    /** Creates an attribute entry. */
    public static ArmorSetData.AttributeData attribute(String id, double value, String operation) {
        ArmorSetData.AttributeData data = new ArmorSetData.AttributeData();
        data.setAttribute(id);
        data.setValue(value);
        data.setModifier(operation);
        return data;
    }
}
