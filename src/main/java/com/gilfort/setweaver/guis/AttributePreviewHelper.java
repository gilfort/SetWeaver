package com.gilfort.setweaver.guis;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.TooltipFlag;

/**
 * Utility class for generating human-readable attribute previews.
 *
 * <p>Centralizes preview logic so that both {@link SetEditorData.AttributeEntry}
 * and {@link SetEditorScreen} produce consistent output regardless of which
 * data model they use internally.</p>
 *
 * <h3>Apotheosis formula reference</h3>
 * <pre>
 * newValue = (base + add) + percent × (base + add)
 * </pre>
 * Where {@code percent} is the multiply_base value displayed as {@code [×N]}.
 */
public final class AttributePreviewHelper {

    private AttributePreviewHelper() {} // utility class

    // ══════════════════════════════════════════════════════════════════
    //  Human-readable preview
    // ══════════════════════════════════════════════════════════════════

    /**
     * Returns a human-readable preview string for an attribute modifier.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code ("minecraft:generic.max_health", 4.0, "addition")} → {@code "+2 Hearts"}</li>
     *   <li>{@code ("minecraft:generic.movement_speed", 0.2, "multiply_base")} → {@code "+20% Base [×1.2] Speed"}</li>
     *   <li>{@code ("minecraft:generic.armor", 0.1, "multiply_total")} → {@code "+10% Total [×1.1] Armor"}</li>
     *   <li>{@code ("minecraft:generic.attack_damage", 3.0, "addition")} → {@code "+3 Attack Damage"}</li>
     * </ul>
     *
     * @param attrId    full attribute id (e.g. "minecraft:generic.max_health")
     * @param value     the raw modifier value
     * @param operation one of "addition", "multiply_base", "multiply_total"
     * @return formatted preview string
     */
    public static String getPreviewText(String attrId, double value, String operation) {
        String attrShort = formatAttributeShort(attrId);
        String op = operation != null ? operation : "addition";

        // Map operation string to MC Operation enum
        AttributeModifier.Operation mcOp = switch (op) {
            case "multiply_base", "multiply" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "multiply_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };

        // Try to use the attribute's own formatting via toValueComponent
        String valueStr = null;
        String attrName = attrShort;
        if (attrId != null) {
            try {
                Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(ResourceLocation.parse(attrId));
                if (attribute != null) {
                    TooltipFlag flag = Minecraft.getInstance().options.advancedItemTooltips
                            ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
                    valueStr = attribute.toValueComponent(mcOp, value, flag).getString();
                    // toValueComponent returns bare values — prepend +/- sign
                    if (value >= 0 && !valueStr.startsWith("+") && !valueStr.startsWith("-")) {
                        valueStr = "+" + valueStr;
                    }
                    attrName = Component.translatable(attribute.getDescriptionId()).getString();
                }
            } catch (Exception ignored) {}
        }

        // Fallback if registry lookup failed
        if (valueStr == null) {
            if (mcOp == AttributeModifier.Operation.ADD_VALUE) {
                String sign = value >= 0 ? "+" : "";
                valueStr = (value == (int) value)
                        ? sign + (int) value
                        : sign + String.format(java.util.Locale.US, "%.2f", value);
            } else {
                long pct = Math.round(value * 100);
                valueStr = (pct >= 0 ? "+" : "") + pct + "%";
            }
        }

        return switch (op) {
            case "multiply_base" -> {
                double multiplier = 1.0 + value;
                String mulStr = formatMultiplier(multiplier);
                yield valueStr + " Base [×" + mulStr + "] " + attrName;
            }
            case "multiply_total" -> {
                double multiplier = 1.0 + value;
                String mulStr = formatMultiplier(multiplier);
                yield valueStr + " Total [×" + mulStr + "] " + attrName;
            }
            default -> valueStr + " " + attrName;
        };
    }

    // ══════════════════════════════════════════════════════════════════
    //  Apotheosis formula line
    // ══════════════════════════════════════════════════════════════════

    /**
     * Returns an Apotheosis-style formula explanation for percent modifiers.
     *
     * <p>Example for {@code value = 15.0} (multiply_base, i.e. +1500%):</p>
     * <pre>
     * =(base+add) + 16.0×(base+add)
     * </pre>
     *
     * @param value     the raw modifier value (e.g. 15.0 for +1500%)
     * @param operation the operation type
     * @return formula string, or {@code null} if not a percent modifier
     */
    public static String getFormulaText(double value, String operation) {
        if (operation == null) return null;

        return switch (operation) {
            case "multiply_base" -> {
                double multiplier = 1.0 + value;
                String mulStr = formatMultiplier(multiplier);
                yield "=(base+add) + " + mulStr + "×(base+add)";
            }
            case "multiply_total" -> {
                double multiplier = 1.0 + value;
                String mulStr = formatMultiplier(multiplier);
                yield "=total × " + mulStr;
            }
            default -> null;
        };
    }

    // ══════════════════════════════════════════════════════════════════
    //  Attribute name formatting
    // ══════════════════════════════════════════════════════════════════

    /**
     * Extracts a short readable name from an attribute ResourceLocation.
     * E.g. {@code "minecraft:generic.max_health"} → {@code "Max Health"}
     */
    public static String formatAttributeShort(String attrId) {
        if (attrId == null || attrId.isEmpty()) return "?";
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

    /**
     * Returns whether a value is considered negative for display purposes.
     */
    public static boolean isNegative(double value, String operation) {
        if ("addition".equals(operation) || operation == null) {
            return value < 0;
        }
        // For multiply operations, the percentage is value*100
        return value < 0;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ══════════════════════════════════════════════════════════════════

    /**
     * Formats a multiplier value, avoiding unnecessary decimals.
     * E.g. 1.0 → "1", 1.2 → "1.2", 16.0 → "16", 1.15 → "1.15"
     */
    private static String formatMultiplier(double multiplier) {
        if (multiplier == (long) multiplier) {
            return String.valueOf((long) multiplier);
        }
        // Use Locale.US to always produce '.' as decimal separator (avoids
        // NumberFormatException on systems with comma-based locales like German)
        String one = String.format(java.util.Locale.US, "%.1f", multiplier);
        if (Double.parseDouble(one) == multiplier) {
            return one;
        }
        return String.format(java.util.Locale.US, "%.2f", multiplier);
    }
}
