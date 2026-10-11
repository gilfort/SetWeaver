package com.gilfort.setweaver.seteffects;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves the effect, attribute and operation names used in set files.
 */
public final class SetIds {

    /** Operation name for flat bonuses. */
    public static final String ADDITION = "addition";
    /** Operation name for a percentage of the base value. */
    public static final String MULTIPLY_BASE = "multiply_base";
    /** Operation name for a percentage of the total value. */
    public static final String MULTIPLY_TOTAL = "multiply_total";
    /** Legacy alias of {@link #MULTIPLY_BASE}. */
    public static final String MULTIPLY = "multiply";

    /** Prefixes vanilla attribute ids had before Minecraft 1.21.2 (e.g. {@code minecraft:generic.armor}). */
    private static final List<String> LEGACY_ATTRIBUTE_PREFIXES = List.of("generic.", "player.", "zombie.");

    private SetIds() {
    }

    /**
     * Parses an id, defaulting to the {@code minecraft} namespace.
     *
     * @param name e.g. {@code speed} or {@code minecraft:speed}
     * @return the id, or {@code null} if it is not valid
     */
    public static Identifier parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
    }

    /**
     * @param name an effect id
     * @return the registered effect
     */
    public static Optional<Holder.Reference<MobEffect>> effect(String name) {
        Identifier id = parse(name);
        return id == null ? Optional.empty() : BuiltInRegistries.MOB_EFFECT.get(id);
    }

    /**
     * Looks up an attribute. Old vanilla ids such as {@code minecraft:generic.armor} resolve to their current name
     * ({@code minecraft:armor}), so set files from Minecraft 1.21.1 keep working.
     *
     * @param name an attribute id
     * @return the registered attribute
     */
    public static Optional<Holder.Reference<Attribute>> attribute(String name) {
        Identifier id = parse(name);
        if (id == null) {
            return Optional.empty();
        }
        Optional<Holder.Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(id);
        if (holder.isEmpty() && Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
            for (String prefix : LEGACY_ATTRIBUTE_PREFIXES) {
                if (id.getPath().startsWith(prefix)) {
                    return BuiltInRegistries.ATTRIBUTE.get(id.withPath(id.getPath().substring(prefix.length())));
                }
            }
        }
        return holder;
    }

    /**
     * @param name an attribute id, possibly a legacy one
     * @return the current id of the attribute, or empty if it is unknown
     */
    public static Optional<String> canonicalAttributeId(String name) {
        return attribute(name).map(h -> h.key().identifier().toString());
    }

    /**
     * @param name an operation name from a set file
     * @return the operation, or empty if the name is unknown
     */
    public static Optional<AttributeModifier.Operation> operation(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case ADDITION -> Optional.of(AttributeModifier.Operation.ADD_VALUE);
            case MULTIPLY_BASE, MULTIPLY -> Optional.of(AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            case MULTIPLY_TOTAL -> Optional.of(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            default -> Optional.empty();
        };
    }
}
