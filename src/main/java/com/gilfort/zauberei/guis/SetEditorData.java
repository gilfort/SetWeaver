package com.gilfort.zauberei.guis;

import com.gilfort.zauberei.item.armorbonus.ArmorSetData;
import com.gilfort.zauberei.item.armorbonus.ArmorSetDataRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Mutable editor state shared between SetWizardScreen (Step 1) and
 * SetEditorScreen (Step 2).
 *
 * <p>Holds all data the user configures in the wizard. Can be converted
 * to/from {@link ArmorSetData} for loading existing sets or saving new ones.</p>
 *
 * <p>This is a plain data holder — no Minecraft dependencies beyond
 * ResourceLocation. Safe to use on both client and server side.</p>
 *
 * @see ArmorSetData
 * @see ArmorSetDataRegistry
 */
public class SetEditorData {

    // ══════════════════════════════════════════════════════════════════
    //  Step 1 fields — Tag & Scope
    // ══════════════════════════════════════════════════════════════════

    /** The item tag this set applies to (e.g. "zauberei:magiccloth_armor"). */
    private String tag = "";

    /** Human-readable name shown in tooltips (e.g. "Diamond Armor"). */
    private String displayName = "";

    /**
     * The major (school/class) this set applies to.
     * Use {@code "*"} for all majors.
     */
    private String major = ArmorSetDataRegistry.WILDCARD_MAJOR;

    /**
     * The year this set applies to.
     * Use {@code -1} ({@link ArmorSetDataRegistry#WILDCARD_YEAR}) for all years.
     */
    private int year = ArmorSetDataRegistry.WILDCARD_YEAR;

    /** Whether this set already exists in the registry (edit mode). */
    private boolean existingSet = false;

    // ══════════════════════════════════════════════════════════════════
    //  Step 2 fields — Parts, Effects & Attributes
    // ══════════════════════════════════════════════════════════════════

    /**
     * Which part thresholds are active (index 0 = 1 Piece, index 3 = 4 Pieces).
     * Toggle buttons in the editor set these independently.
     */
    private final boolean[] partsEnabled = {false, false, false, false};

    /**
     * Per-part effect and attribute lists.
     * Index 0 = 1-Piece configuration, index 3 = 4-Piece configuration.
     * Always has exactly 4 entries (one per possible threshold).
     */
    private final List<PartConfig> partConfigs = new ArrayList<>();

    // ══════════════════════════════════════════════════════════════════
    //  Inner classes — mutable editor entries
    // ══════════════════════════════════════════════════════════════════

    /**
     * Configuration for a single part threshold.
     * Contains mutable lists of effects and attributes that the user
     * can add, remove, and reorder in the editor.
     */
    public static class PartConfig {
        private final List<EffectEntry> effects = new ArrayList<>();
        private final List<AttributeEntry> attributes = new ArrayList<>();

        public List<EffectEntry> getEffects() { return effects; }
        public List<AttributeEntry> getAttributes() { return attributes; }

        /**
         * Creates a deep copy of this PartConfig.
         * Used by the "Copy from Part..." feature.
         *
         * @param copyEffects    whether to copy effects
         * @param copyAttributes whether to copy attributes
         * @return a new PartConfig with independent copies of the selected data
         */
        public PartConfig deepCopy(boolean copyEffects, boolean copyAttributes) {
            PartConfig copy = new PartConfig();
            if (copyEffects) {
                for (EffectEntry e : effects) {
                    copy.effects.add(new EffectEntry(e.effectId, e.amplifier));
                }
            }
            if (copyAttributes) {
                for (AttributeEntry a : attributes) {
                    copy.attributes.add(new AttributeEntry(a.attributeId, a.operation, a.value));
                }
            }
            return copy;
        }
    }

    /**
     * A single effect entry in the editor.
     * Mutable — the user can change effect and amplifier.
     */
    public static class EffectEntry {
        private String effectId;   // e.g. "minecraft:speed"
        private int amplifier;     // 0 = Level I, 1 = Level II, etc.

        public EffectEntry(String effectId, int amplifier) {
            this.effectId = effectId;
            this.amplifier = amplifier;
        }

        public String getEffectId() { return effectId; }
        public void setEffectId(String effectId) { this.effectId = effectId; }
        public int getAmplifier() { return amplifier; }
        public void setAmplifier(int amplifier) { this.amplifier = amplifier; }
    }

    /**
     * A single attribute entry in the editor.
     * Mutable — the user can change attribute, operation, and value.
     */
    public static class AttributeEntry {
        private String attributeId;  // e.g. "minecraft:generic.max_health"
        private String operation;    // "addition", "multiply_base", "multiply_total"
        private double value;        // e.g. 4.0, 0.2

        public AttributeEntry(String attributeId, String operation, double value) {
            this.attributeId = attributeId;
            this.operation = operation;
            this.value = value;
        }

        public String getAttributeId() { return attributeId; }
        public void setAttributeId(String attributeId) { this.attributeId = attributeId; }
        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }

        /**
         * Returns a human-readable preview of this attribute modifier.
         * Examples:
         * <ul>
         *   <li>Max Health +4 (addition) → "+2 Hearts"</li>
         *   <li>Movement Speed +0.2 (multiply_base) → "+20% Base Speed"</li>
         *   <li>Armor +0.1 (multiply_total) → "+10% Total Armor"</li>
         *   <li>Attack Damage +3 (addition) → "+3 Damage"</li>
         * </ul>
         */
        public String getPreview() {
            String attrShort = formatAttributeShort(attributeId);

            return switch (operation) {
                case "multiply_base" -> {
                    String sign = value >= 0 ? "+" : "";
                    yield sign + Math.round(value * 100) + "% Base " + attrShort;
                }
                case "multiply_total" -> {
                    String sign = value >= 0 ? "+" : "";
                    yield sign + Math.round(value * 100) + "% Total " + attrShort;
                }
                default -> { // "addition"
                    // Special case: Max Health displays in Hearts (1 Heart = 2 HP)
                    if (attributeId.contains("max_health")) {
                        double hearts = value / 2.0;
                        String sign = hearts >= 0 ? "+" : "";
                        // Format: avoid .0 for whole numbers
                        String heartsStr = (hearts == (int) hearts)
                                ? String.valueOf((int) hearts)
                                : String.format("%.1f", hearts);
                        yield sign + heartsStr + " Heart" + (Math.abs(hearts) != 1 ? "s" : "");
                    }
                    String sign = value >= 0 ? "+" : "";
                    // Format: avoid .0 for whole numbers
                    String valStr = (value == (int) value)
                            ? String.valueOf((int) value)
                            : String.format("%.2f", value);
                    yield sign + valStr + " " + attrShort;
                }
            };
        }

        /**
         * Extracts a short readable name from an attribute ResourceLocation.
         * E.g. "minecraft:generic.max_health" → "Max Health"
         */
        private static String formatAttributeShort(String attrId) {
            try {
                String path = ResourceLocation.parse(attrId).getPath();
                // Remove common prefixes
                path = path.replace("generic.", "")
                        .replace("player.", "")
                        .replace("zombie.", "");
                // Convert snake_case to Title Case
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
                return attrId;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Constructor & initialization
    // ══════════════════════════════════════════════════════════════════

    public SetEditorData() {
        // Initialize 4 empty part configs (one per threshold)
        for (int i = 0; i < 4; i++) {
            partConfigs.add(new PartConfig());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Step 1 getters & setters
    // ══════════════════════════════════════════════════════════════════

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public boolean isExistingSet() { return existingSet; }
    public void setExistingSet(boolean existingSet) { this.existingSet = existingSet; }

    // ══════════════════════════════════════════════════════════════════
    //  Step 2 getters & setters
    // ══════════════════════════════════════════════════════════════════

    public boolean isPartEnabled(int partIndex) { return partsEnabled[partIndex]; }
    public void setPartEnabled(int partIndex, boolean enabled) { partsEnabled[partIndex] = enabled; }

    public PartConfig getPartConfig(int partIndex) { return partConfigs.get(partIndex); }

    /**
     * Returns the number of currently enabled parts.
     */
    public int getEnabledPartCount() {
        int count = 0;
        for (boolean b : partsEnabled) if (b) count++;
        return count;
    }

    /**
     * Copies effects and/or attributes from one part to another.
     * Existing data in the target is replaced (not merged).
     *
     * @param sourceIndex    the source part (0-3)
     * @param targetIndex    the target part (0-3)
     * @param copyEffects    whether to copy effects
     * @param copyAttributes whether to copy attributes
     */
    public void copyFromPart(int sourceIndex, int targetIndex,
                             boolean copyEffects, boolean copyAttributes) {
        PartConfig source = partConfigs.get(sourceIndex);
        PartConfig copied = source.deepCopy(copyEffects, copyAttributes);
        PartConfig target = partConfigs.get(targetIndex);

        if (copyEffects) {
            target.getEffects().clear();
            target.getEffects().addAll(copied.getEffects());
        }
        if (copyAttributes) {
            target.getAttributes().clear();
            target.getAttributes().addAll(copied.getAttributes());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Conversion: ArmorSetData → SetEditorData (load existing)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Populates this editor data from an existing {@link ArmorSetData}.
     * Used when editing a set that already exists in the registry.
     *
     * @param tag   the item tag string (e.g. "zauberei:magiccloth_armor")
     * @param major the major (or "*" for wildcard)
     * @param year  the year (or -1 for wildcard)
     * @param data  the existing set data to load into the editor
     * @return this instance for chaining
     */
    public SetEditorData loadFrom(String tag, String major, int year, ArmorSetData data) {
        this.tag = tag;
        this.major = major;
        this.year = year;
        this.existingSet = true;
        this.displayName = data.getDisplayName() != null ? data.getDisplayName() : "";

        // Reset all parts
        for (int i = 0; i < 4; i++) {
            partsEnabled[i] = false;
            partConfigs.get(i).getEffects().clear();
            partConfigs.get(i).getAttributes().clear();
        }

        // Load parts from ArmorSetData
        if (data.getParts() != null) {
            for (Map.Entry<String, ArmorSetData.PartData> entry : data.getParts().entrySet()) {
                // Parse key like "2Part" → index 1
                String key = entry.getKey();
                int partNum;
                try {
                    partNum = Integer.parseInt(key.replace("Part", ""));
                } catch (NumberFormatException e) {
                    continue; // skip invalid keys
                }
                if (partNum < 1 || partNum > 4) continue;

                int idx = partNum - 1;
                partsEnabled[idx] = true;
                PartConfig config = partConfigs.get(idx);

                // Load effects
                ArmorSetData.PartData pd = entry.getValue();
                if (pd.getEffects() != null) {
                    for (ArmorSetData.EffectData ed : pd.getEffects()) {
                        config.getEffects().add(
                                new EffectEntry(ed.getEffect(), ed.getAmplifier()));
                    }
                }

                // Load attributes
                if (pd.getAttributes() != null) {
                    for (Map.Entry<String, ArmorSetData.AttributeData> ae : pd.getAttributes().entrySet()) {
                        config.getAttributes().add(
                                new AttributeEntry(
                                        ae.getKey(),
                                        ae.getValue().getModifier(),
                                        ae.getValue().getValue()));
                    }
                }
            }
        }

        return this;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Conversion: SetEditorData → ArmorSetData (save)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Converts this editor state into an {@link ArmorSetData} object
     * ready for JSON serialization and saving.
     *
     * <p>Only enabled parts with at least one effect or attribute
     * are included in the output.</p>
     *
     * @return a new ArmorSetData populated from the editor state
     */
    public ArmorSetData toArmorSetData() {
        ArmorSetData data = new ArmorSetData();
        data.setDisplayName(displayName.isBlank() ? null : displayName);

        Map<String, ArmorSetData.PartData> parts = new LinkedHashMap<>();

        for (int i = 0; i < 4; i++) {
            if (!partsEnabled[i]) continue;

            PartConfig config = partConfigs.get(i);

            // Skip parts with no effects and no attributes
            if (config.getEffects().isEmpty() && config.getAttributes().isEmpty()) continue;

            ArmorSetData.PartData pd = new ArmorSetData.PartData();

            // Convert effects
            if (!config.getEffects().isEmpty()) {
                List<ArmorSetData.EffectData> effectList = new ArrayList<>();
                for (EffectEntry ee : config.getEffects()) {
                    ArmorSetData.EffectData ed = new ArmorSetData.EffectData();
                    ed.setEffect(ee.getEffectId());
                    ed.setAmplifier(ee.getAmplifier());
                    effectList.add(ed);
                }
                pd.setEffects(effectList);
            }

            // Convert attributes
            if (!config.getAttributes().isEmpty()) {
                Map<String, ArmorSetData.AttributeData> attrMap = new LinkedHashMap<>();
                for (AttributeEntry ae : config.getAttributes()) {
                    ArmorSetData.AttributeData ad = new ArmorSetData.AttributeData();
                    ad.setValue(ae.getValue());
                    ad.setModifier(ae.getOperation());
                    attrMap.put(ae.getAttributeId(), ad);
                }
                pd.setAttributes(attrMap);
            }

            String partKey = (i + 1) + "Part"; // 1Part, 2Part, 3Part, 4Part
            parts.put(partKey, pd);
        }

        data.setParts(parts);
        return data;
    }

    // ══════════════════════════════════════════════════════════════════
    //  File path resolution
    // ══════════════════════════════════════════════════════════════════

    /**
     * Computes the relative file path where this set should be saved.
     * Based on the major/year scope and tag.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Major=*, Year=* → {@code all_majors_all_years/zauberei__magiccloth_armor.json}</li>
     *   <li>Major=*, Year=3 → {@code all_majors/3/zauberei__magiccloth_armor.json}</li>
     *   <li>Major=naturalist, Year=3 → {@code naturalist/3/zauberei__magiccloth_armor.json}</li>
     * </ul>
     *
     * @return the relative path from the set_armor config directory
     */
    public String resolveFilePath() {
        // Convert tag "zauberei:magiccloth_armor" → filename "zauberei__magiccloth_armor.json"
        String fileName = tag.replace(":", "__") + ".json";

        boolean wildMajor = ArmorSetDataRegistry.WILDCARD_MAJOR.equals(major);
        boolean wildYear = (year == ArmorSetDataRegistry.WILDCARD_YEAR);

        if (wildMajor && wildYear) {
            return "all_majors_all_years" + "/" + fileName;
        } else if (wildMajor) {
            return "all_majors" + "/" + year + "/" + fileName;
        } else {
            return major + "/" + year + "/" + fileName;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Validation
    // ══════════════════════════════════════════════════════════════════

    /**
     * Validates the editor state and returns a list of error messages.
     * An empty list means the data is valid and ready to save.
     *
     * @return list of validation error messages (empty if valid)
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        // Step 1 validations
        if (tag == null || tag.isBlank()) {
            errors.add("Tag is required.");
        } else if (!tag.contains(":")) {
            errors.add("Tag must include namespace (e.g. 'zauberei:my_armor').");
        }

        // Major/Year rule: if major is specific, year must also be specific
        boolean wildMajor = ArmorSetDataRegistry.WILDCARD_MAJOR.equals(major);
        boolean wildYear = (year == ArmorSetDataRegistry.WILDCARD_YEAR);
        if (!wildMajor && wildYear) {
            errors.add("If a Major is selected, Year must also be specified.");
        }

        // Step 2 validations
        int enabledCount = getEnabledPartCount();
        if (enabledCount == 0) {
            errors.add("At least one Part must be enabled.");
        }

        // Check that enabled parts have at least one effect or attribute
        for (int i = 0; i < 4; i++) {
            if (!partsEnabled[i]) continue;
            PartConfig config = partConfigs.get(i);
            if (config.getEffects().isEmpty() && config.getAttributes().isEmpty()) {
                errors.add("Part " + (i + 1) + " is enabled but has no effects or attributes.");
            }

            // Check for incomplete entries
            for (EffectEntry e : config.getEffects()) {
                if (e.getEffectId() == null || e.getEffectId().isBlank()) {
                    errors.add("Part " + (i + 1) + ": Effect entry has no effect selected.");
                }
            }
            for (AttributeEntry a : config.getAttributes()) {
                if (a.getAttributeId() == null || a.getAttributeId().isBlank()) {
                    errors.add("Part " + (i + 1) + ": Attribute entry has no attribute selected.");
                }
                if (a.getOperation() == null || a.getOperation().isBlank()) {
                    errors.add("Part " + (i + 1) + ": Attribute entry has no operation selected.");
                }
            }
        }

        return errors;
    }
}