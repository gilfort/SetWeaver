package com.gilfort.setweaver.seteffects;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class ArmorSetDataRegistry {

    // Key format: "role:level:namespace:tagpath"
    // Example:   "naturalist:3:zauberei:magiccloth_armor"
    private static final Map<String, ArmorSetData> DATA_MAP = new HashMap<>();

    public static void clear() {
        DATA_MAP.clear();
    }

    public static void put(String role, int level, String tag, ArmorSetData data) {
        String key = makeKey(role, level, tag);
        DATA_MAP.put(key, data);
    }

    // ─── Sentinel values for wildcards ───────────────────────────────────
    public static final String WILDCARD_ROLE = "*";
    public static final int    WILDCARD_LEVEL  = -1;

    /**
     * Returns set data for the given role/level/tag with priority fallback:
     * <ol>
     *   <li>Exact match: {@code role + level + tag}</li>
     *   <li>Wildcard role: {@code all_roles + level + tag}</li>
     *   <li>Wildcard both: {@code all_roles + all_levels + tag}</li>
     * </ol>
     */
    public static ArmorSetData getData(String role, int level, String tag) {
        // Priority 1: exact match (e.g. naturalist/3/magiccloth_armor)
        ArmorSetData data = DATA_MAP.get(makeKey(role, level, tag));
        if (data != null) return data;

        // Priority 2: all_roles for this level (e.g. all_roles/3/magiccloth_armor)
        data = DATA_MAP.get(makeKey(WILDCARD_ROLE, level, tag));
        if (data != null) return data;

        // Priority 3: all_roles_all_levels (e.g. all_roles_all_levels/magiccloth_armor)
        return DATA_MAP.get(makeKey(WILDCARD_ROLE, WILDCARD_LEVEL, tag));
    }

    /**
     * Returns all tag strings relevant for a given role/level,
     * including wildcard entries. Specific entries take priority
     * (the same tag won't appear twice).
     */
    public static Set<String> getRegisteredTags(String role, int level) {
        // Use a map to track priority: tag → already found at higher priority?
        Map<String, Boolean> tagMap = new HashMap<>();

        // Priority 1: exact match
        String exactPrefix = role + ":" + level + ":";
        // Priority 2: all_roles for this level
        String wildcardRolePrefix = WILDCARD_ROLE + ":" + level + ":";
        // Priority 3: all_roles_all_levels
        String wildcardBothPrefix = WILDCARD_ROLE + ":" + WILDCARD_LEVEL + ":";

        for (String key : DATA_MAP.keySet()) {
            String tag = null;
            if (key.startsWith(exactPrefix)) {
                tag = key.substring(exactPrefix.length());
            } else if (key.startsWith(wildcardRolePrefix)) {
                tag = key.substring(wildcardRolePrefix.length());
            } else if (key.startsWith(wildcardBothPrefix)) {
                tag = key.substring(wildcardBothPrefix.length());
            }

            if (tag != null) {
                tagMap.putIfAbsent(tag, true);
            }
        }

        return Collections.unmodifiableSet(tagMap.keySet());
    }


    public static boolean isItemInAnyRegisteredTag(ItemStack stack) {
        for (String key : DATA_MAP.keySet()) {
            // Key format: "role:level:namespace:tagpath"
            // Tag ist alles ab dem 3. Doppelpunkt
            String[] parts = key.split(":", 3);
            if (parts.length < 3) continue;
            String tagString = parts[2]; // z.B. "zauberei:magiccloth_armor"
            try {
                ResourceLocation tagLoc = ResourceLocation.parse(tagString);
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);
                if (stack.is(tagKey)) return true;
            } catch (Exception e) {
                continue;
            }
        }
        return false;
    }
    /**
     * Returns all real role names registered in the data map.
     * Excludes the wildcard sentinel ({@code "*"}) so that command
     * auto-completion only suggests actual roles.
     */
    public static Set<String> getRoles() {
        return Collections.unmodifiableSet(
                DATA_MAP.keySet().stream()
                        .map(key -> key.split(":", 2)[0])
                        .filter(m -> !WILDCARD_ROLE.equals(m))
                        .collect(Collectors.toSet())
        );
    }

    // ─── Record for structured iteration ─────────────────────────────────

    /**
     * Represents one loaded set entry with parsed key components.
     * Used by commands like {@code /zauberei sets list} and {@code info}.
     */
    public record SetEntry(String role, int level, String tag, ArmorSetData data) {

        /**
         * Human-readable scope description for chat output.
         * @return e.g. "naturalist / level 3", "ALL roles / level 1", "ALL roles / ALL levels"
         */
        public String scopeLabel() {
            boolean wildRole = WILDCARD_ROLE.equals(role);
            boolean wildLevel  = level == WILDCARD_LEVEL;
            if (wildRole && wildLevel) return "ALL roles / ALL levels";
            if (wildRole)             return "ALL roles / level " + level;
            return role + " / level " + level;
        }
    }

    /**
     * Returns ALL loaded set entries as structured records.
     * Useful for listing, validation, and debug commands.
     */
    public static List<SetEntry> getAllEntries() {
        List<SetEntry> entries = new ArrayList<>();
        for (Map.Entry<String, ArmorSetData> e : DATA_MAP.entrySet()) {
            String[] parts = e.getKey().split(":", 3);
            if (parts.length < 3) continue;
            String role = parts[0];
            int level;
            try { level = Integer.parseInt(parts[1]); } catch (NumberFormatException ex) { continue; }
            String tag = parts[2];
            entries.add(new SetEntry(role, level, tag, e.getValue()));
        }
        return entries;
    }

    /**
     * Returns all unique tag strings across ALL entries (regardless of role/level).
     * Used for command auto-completion of tag arguments.
     */
    public static Set<String> getAllTags() {
        return Collections.unmodifiableSet(
                DATA_MAP.keySet().stream()
                        .map(key -> {
                            String[] parts = key.split(":", 3);
                            return parts.length >= 3 ? parts[2] : null;
                        })
                        .filter(t -> t != null)
                        .collect(Collectors.toSet())
        );
    }



    private static String makeKey(String role, int level, String tag) {
        return role + ":" + level + ":" + tag;
    }
}
