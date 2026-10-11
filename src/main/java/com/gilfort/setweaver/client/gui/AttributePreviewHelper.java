package com.gilfort.setweaver.client.gui;

import com.gilfort.setweaver.seteffects.SetIds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.TooltipFlag;

import java.util.Locale;

/**
 * Human-readable texts for effects, attributes and modifier values in the editor screens.
 */
public final class AttributePreviewHelper {

    private AttributePreviewHelper() {
    }

    /**
     * Describes a modifier, e.g. {@code "+2 Max Health"} or {@code "+20% Base [×1.2] Speed"}.
     *
     * @param attrId    the attribute id
     * @param value     the modifier amount
     * @param operation the operation name
     * @return the preview text
     */
    public static String getPreviewText(String attrId, double value, String operation) {
        AttributeModifier.Operation op = SetIds.operation(operation).orElse(AttributeModifier.Operation.ADD_VALUE);
        String valueText = formatValue(attrId, value, op);
        String name = attributeName(attrId);
        return switch (op) {
            case ADD_MULTIPLIED_BASE -> GuiUtil.tr("gui.setweaver.preview.base", valueText, formatMultiplier(1 + value), name);
            case ADD_MULTIPLIED_TOTAL -> GuiUtil.tr("gui.setweaver.preview.total", valueText, formatMultiplier(1 + value), name);
            default -> valueText + " " + name;
        };
    }

    /**
     * Explains how a percentage modifier is calculated.
     *
     * @param value     the modifier amount
     * @param operation the operation name
     * @return the formula, or {@code null} for flat modifiers
     */
    public static String getFormulaText(double value, String operation) {
        AttributeModifier.Operation op = SetIds.operation(operation).orElse(AttributeModifier.Operation.ADD_VALUE);
        return switch (op) {
            case ADD_MULTIPLIED_BASE -> GuiUtil.tr("gui.setweaver.formula.base", formatMultiplier(1 + value));
            case ADD_MULTIPLIED_TOTAL -> GuiUtil.tr("gui.setweaver.formula.total", formatMultiplier(1 + value));
            default -> null;
        };
    }

    /**
     * @param attrId an attribute id
     * @return the translated attribute name, or a title-cased id path if the attribute is unknown
     */
    public static String attributeName(String attrId) {
        return SetIds.attribute(attrId)
                .map(h -> Component.translatable(h.value().getDescriptionId()).getString())
                .orElseGet(() -> titleCase(shortName(attrId).replace("generic.", "").replace("player.", "")));
    }

    /**
     * @param effectId an effect id
     * @return the translated effect name, or the id if the effect is unknown
     */
    public static String effectName(String effectId) {
        return SetIds.effect(effectId).map(h -> h.value().getDisplayName().getString()).orElse(String.valueOf(effectId));
    }

    /**
     * @param id a namespaced id
     * @return the path part of the id
     */
    public static String shortName(String id) {
        if (id == null) {
            return "?";
        }
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /**
     * @param number 1 to 10
     * @return the roman numeral, or the number itself outside that range
     */
    public static String roman(int number) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return number >= 1 && number <= numerals.length ? numerals[number - 1] : String.valueOf(number);
    }

    private static String formatValue(String attrId, double value, AttributeModifier.Operation op) {
        var holder = SetIds.attribute(attrId);
        String text;
        if (holder.isPresent()) {
            TooltipFlag flag = Minecraft.getInstance().options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
            text = holder.get().value().toValueComponent(op, value, flag).getString();
        } else if (op == AttributeModifier.Operation.ADD_VALUE) {
            text = value == (long) value ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value);
        } else {
            text = Math.round(value * 100) + "%";
        }
        return value >= 0 && !text.startsWith("+") && !text.startsWith("-") ? "+" + text : text;
    }

    /** Formats a multiplier without unneeded decimals: 1.0 → "1", 1.2 → "1.2", 1.15 → "1.15". */
    private static String formatMultiplier(double multiplier) {
        if (multiplier == (long) multiplier) {
            return String.valueOf((long) multiplier);
        }
        String one = String.format(Locale.ROOT, "%.1f", multiplier);
        return Double.parseDouble(one) == multiplier ? one : String.format(Locale.ROOT, "%.2f", multiplier);
    }

    private static String titleCase(String snake) {
        StringBuilder sb = new StringBuilder();
        for (String word : snake.split("[_.]")) {
            if (!word.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
