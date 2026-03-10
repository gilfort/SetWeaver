package com.gilfort.zauberei.item.armorbonus;

import org.w3c.dom.Attr;

import java.util.List;
import java.util.Map;

public class ArmorSetData {

    private String displayName;  // ← NEU: optional, z.B. "Magiccloth"
    private Map<String, PartData> parts;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Map<String, PartData> getParts() {
        return parts;
    }

    public void setParts(Map<String, PartData> parts) {
        this.parts = parts;
    }

    //Inner class for Effects and Attributes for each Equipment Part
    public static class PartData {
        // Gson wird diese Felder per Namen matchen:
        private List<EffectData> Effects;
        private Map<String, AttributeData> Attributes;

        // Getter und Setter (oder public Felder, je nachdem was du bevorzugst)
        public List<EffectData> getEffects() {
            return Effects;
        }

        public void setEffects(List<EffectData> effects) {
            Effects = effects;
        }

        public Map<String, AttributeData> getAttributes() {
            return Attributes;
        }

        public void setAttributes(Map<String, AttributeData> attributes) {
            Attributes = attributes;
        }
    }

    public static class EffectData {
        private String Effect;
        private int Amplifier;

        public int getAmplifier() {
            return Amplifier;
        }

        public void setAmplifier(int amplifier) {
            Amplifier = amplifier;
        }

        public String getEffect() {
            return Effect;
        }

        public void setEffect(String effect) {
            Effect = effect;
        }
    }

    public static class AttributeData {
        private double value;
        private String modifier;

        public double getValue() { return value; }
        public String getModifier() { return modifier; }
        public void setValue(double value) { this.value = value; }
        public void setModifier(String modifier) { this.modifier = modifier; }
    }

    /**
     * Returns the PartData for the highest defined threshold that does not
     * exceed {@code wornParts}.
     *
     * <p>Example: if {@code 2Part} and {@code 4Part} are defined and
     * {@code wornParts} is 3, returns the {@code 2Part} data.</p>
     *
     * @param wornParts number of set pieces currently worn
     * @return the active PartData, or {@code null} if no threshold is reached
     */
    public PartData getActivePartData(int wornParts) {
        PartData best = null;
        int bestThreshold = 0;
        for (Map.Entry<String, PartData> entry : parts.entrySet()) {
            String numStr = entry.getKey().replace("Part", "");
            try {
                int threshold = Integer.parseInt(numStr);
                if (threshold <= wornParts && threshold > bestThreshold) {
                    bestThreshold = threshold;
                    best = entry.getValue();
                }
            } catch (NumberFormatException e) {
                // skip malformed keys
            }
        }
        return best;
    }


}