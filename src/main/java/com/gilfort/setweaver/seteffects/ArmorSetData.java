package com.gilfort.setweaver.seteffects;

import java.util.List;
import java.util.Map;

/**
 * One armor set definition as stored in a set JSON file.
 *
 * <p>The JSON field names ({@code Effects}, {@code Attributes}, {@code Effect}, {@code Amplifier}) are part of the
 * file format and must not be renamed.</p>
 */
public class ArmorSetData {

    /** Suffix of the part keys, e.g. {@code "2Part"}. */
    public static final String PART_SUFFIX = "Part";

    /** Highest supported threshold (one piece per armor slot). */
    public static final int MAX_PARTS = 4;

    /** Optional name shown in tooltips and the editor; the tag path is used when absent. */
    private String displayName;

    /** Bonuses per threshold, keyed by {@code "<n>Part"}. */
    private Map<String, PartData> parts;

    /** @return the optional display name, may be {@code null} */
    public String getDisplayName() {
        return displayName;
    }

    /** @param displayName the display name, {@code null} for none */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** @return the bonuses per threshold key, may be {@code null} */
    public Map<String, PartData> getParts() {
        return parts;
    }

    /** @param parts the bonuses per threshold key */
    public void setParts(Map<String, PartData> parts) {
        this.parts = parts;
    }

    /** Effects and attribute modifiers granted at one threshold. */
    public static class PartData {
        private List<EffectData> Effects;
        private List<AttributeData> Attributes;

        /** @return the mob effects, may be {@code null} */
        public List<EffectData> getEffects() {
            return Effects;
        }

        /** @param effects the mob effects */
        public void setEffects(List<EffectData> effects) {
            Effects = effects;
        }

        /** @return the attribute modifiers, may be {@code null} */
        public List<AttributeData> getAttributes() {
            return Attributes;
        }

        /** @param attributes the attribute modifiers */
        public void setAttributes(List<AttributeData> attributes) {
            Attributes = attributes;
        }

        /** @return whether this threshold grants neither effects nor attributes */
        public boolean isEmpty() {
            return (Effects == null || Effects.isEmpty()) && (Attributes == null || Attributes.isEmpty());
        }
    }

    /** A mob effect granted by a set. */
    public static class EffectData {
        private String Effect;
        private int Amplifier;

        /** @return the amplifier (0 = level I) */
        public int getAmplifier() {
            return Amplifier;
        }

        /** @param amplifier the amplifier (0 = level I) */
        public void setAmplifier(int amplifier) {
            Amplifier = amplifier;
        }

        /** @return the effect id, e.g. {@code minecraft:speed} */
        public String getEffect() {
            return Effect;
        }

        /** @param effect the effect id */
        public void setEffect(String effect) {
            Effect = effect;
        }
    }

    /** An attribute modifier granted by a set. */
    public static class AttributeData {
        private String attribute;
        private double value;
        private String modifier;

        /** @return the attribute id */
        public String getAttribute() {
            return attribute;
        }

        /** @param attribute the attribute id */
        public void setAttribute(String attribute) {
            this.attribute = attribute;
        }

        /** @return the modifier amount */
        public double getValue() {
            return value;
        }

        /** @param value the modifier amount */
        public void setValue(double value) {
            this.value = value;
        }

        /** @return the operation name ({@code addition}, {@code multiply_base}, {@code multiply_total}) */
        public String getModifier() {
            return modifier;
        }

        /** @param modifier the operation name */
        public void setModifier(String modifier) {
            this.modifier = modifier;
        }
    }

    /**
     * Parses a part key such as {@code "2Part"}.
     *
     * @param key the part key
     * @return the threshold, or {@code -1} if the key is malformed
     */
    public static int parseThreshold(String key) {
        if (key == null || !key.endsWith(PART_SUFFIX)) {
            return -1;
        }
        try {
            return Integer.parseInt(key.substring(0, key.length() - PART_SUFFIX.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Returns the part for the highest threshold that does not exceed {@code wornParts}.
     * Example: with {@code 2Part} and {@code 4Part} defined, 3 worn pieces activate {@code 2Part}.
     *
     * @param wornParts number of set pieces currently worn
     * @return the active part, or {@code null} if no threshold is reached
     */
    public PartData getActivePartData(int wornParts) {
        if (parts == null) {
            return null;
        }
        PartData best = null;
        int bestThreshold = 0;
        for (Map.Entry<String, PartData> entry : parts.entrySet()) {
            int threshold = parseThreshold(entry.getKey());
            if (threshold > bestThreshold && threshold <= wornParts) {
                bestThreshold = threshold;
                best = entry.getValue();
            }
        }
        return best;
    }

    /** @return the lowest defined threshold, or {@code -1} if none */
    public int getFirstThreshold() {
        int min = Integer.MAX_VALUE;
        if (parts != null) {
            for (String key : parts.keySet()) {
                int t = parseThreshold(key);
                if (t > 0) {
                    min = Math.min(min, t);
                }
            }
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    /** @return the highest defined threshold, or {@code 0} if none */
    public int getMaxThreshold() {
        int max = 0;
        if (parts != null) {
            for (String key : parts.keySet()) {
                max = Math.max(max, parseThreshold(key));
            }
        }
        return max;
    }

    /** @return whether at least one threshold grants an effect or attribute */
    public boolean hasBonuses() {
        if (parts == null) {
            return false;
        }
        for (PartData part : parts.values()) {
            if (part != null && !part.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
