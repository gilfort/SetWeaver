package com.gilfort.setweaver.seteffects;

import com.gilfort.setweaver.SetWeaver;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loaded armor set definitions, keyed by role, level and item tag.
 *
 * <p>There is one instance per logical side: {@link #server()} is filled from the config folder, {@link #client()}
 * from the server's sync packet. In single player both live in the same JVM, so they must never share state.
 * Each instance holds an immutable snapshot that is swapped atomically, so readers on other threads never see a
 * half-built registry.</p>
 */
public final class ArmorSetDataRegistry {

    /** Role placeholder for sets that apply to every role. */
    public static final String WILDCARD_ROLE = "*";
    /** Level placeholder for sets that apply to every level. */
    public static final int WILDCARD_LEVEL = -1;

    private static final ArmorSetDataRegistry SERVER = new ArmorSetDataRegistry();
    private static final ArmorSetDataRegistry CLIENT = new ArmorSetDataRegistry();

    private static final Gson GSON = new Gson();
    private static final Type DATA_MAP_TYPE = new TypeToken<Map<String, ArmorSetData>>() {}.getType();

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    private ArmorSetDataRegistry() {
    }

    /** @return the registry used by server logic (set effects, commands) */
    public static ArmorSetDataRegistry server() {
        return SERVER;
    }

    /** @return the registry received from the server (tooltips, editor) */
    public static ArmorSetDataRegistry client() {
        return CLIENT;
    }

    /**
     * One loaded set definition.
     *
     * @param role   lower-case role, or {@link #WILDCARD_ROLE}
     * @param level  level, or {@link #WILDCARD_LEVEL}
     * @param tag    item tag id as string, e.g. {@code c:armors}
     * @param tagKey the parsed item tag
     * @param data   the set definition
     */
    public record SetEntry(String role, int level, String tag, TagKey<Item> tagKey, ArmorSetData data) {

        /** @return whether the entry applies to every role */
        public boolean isWildcardRole() {
            return WILDCARD_ROLE.equals(role);
        }

        /** @return whether the entry applies to every level */
        public boolean isWildcardLevel() {
            return level == WILDCARD_LEVEL;
        }

        /** @return a translatable description of role and level, e.g. "naturalist / Level 3" */
        public Component scopeLabel() {
            return ArmorSetDataRegistry.scopeLabel(role, level);
        }
    }

    /**
     * @param role  role or {@link #WILDCARD_ROLE}
     * @param level level or {@link #WILDCARD_LEVEL}
     * @return a translatable description of the scope, e.g. "naturalist / Level 3"
     */
    public static Component scopeLabel(String role, int level) {
        boolean wildRole = WILDCARD_ROLE.equals(role);
        if (wildRole && level == WILDCARD_LEVEL) {
            return Component.translatable("gui.setweaver.scope.universal");
        }
        if (wildRole) {
            return Component.translatable("gui.setweaver.scope.all_roles", level);
        }
        return Component.translatable("gui.setweaver.scope.specific", role, level);
    }

    /**
     * Creates an entry, parsing the tag.
     *
     * @return the entry, or {@code null} if {@code tag} is not a valid id
     */
    public static SetEntry createEntry(String role, int level, String tag, ArmorSetData data) {
        Identifier id = Identifier.tryParse(tag);
        if (id == null || data == null) {
            return null;
        }
        return new SetEntry(normalizeRole(role), level, id.toString(), TagKey.create(Registries.ITEM, id), data);
    }

    /**
     * Normalizes a role name for lookups.
     *
     * @param role the role as typed
     * @return the lower-case role, empty for {@code null}
     */
    public static String normalizeRole(String role) {
        return role == null ? "" : role.toLowerCase(Locale.ROOT);
    }

    /**
     * Replaces all definitions at once.
     *
     * @param entries the new definitions
     */
    public void replaceAll(Collection<SetEntry> entries) {
        Map<String, SetEntry> byKey = new LinkedHashMap<>();
        for (SetEntry entry : entries) {
            byKey.put(makeKey(entry.role(), entry.level(), entry.tag()), entry);
        }
        snapshot = new Snapshot(Collections.unmodifiableMap(byKey));
    }

    /** Removes all definitions. */
    public void clear() {
        snapshot = Snapshot.EMPTY;
    }

    /**
     * Returns the set for a role / level / tag with fallback: exact match, then all roles for the level, then all
     * roles and levels.
     *
     * @return the data, or {@code null} if no scope matches
     */
    public ArmorSetData getData(String role, int level, String tag) {
        SetEntry entry = findEntry(snapshot, normalizeRole(role), level, tag);
        return entry == null ? null : entry.data();
    }

    /**
     * Returns the entry with exactly this scope (no wildcard fallback).
     *
     * @return the entry, or {@code null}
     */
    public SetEntry getExact(String tag, String role, int level) {
        return snapshot.byKey.get(makeKey(normalizeRole(role), level, tag));
    }

    /**
     * Returns, for every tag, the most specific entry that applies to a player with the given role and level.
     *
     * @param role  the player's role (any case, may be empty)
     * @param level the player's level
     * @return one entry per tag
     */
    public List<SetEntry> getApplicableEntries(String role, int level) {
        Snapshot current = snapshot;
        String normalized = normalizeRole(role);
        Map<String, SetEntry> byTag = new LinkedHashMap<>();
        for (SetEntry entry : current.byKey.values()) {
            if (!byTag.containsKey(entry.tag())) {
                SetEntry best = findEntry(current, normalized, level, entry.tag());
                if (best != null) {
                    byTag.put(entry.tag(), best);
                }
            }
        }
        return new ArrayList<>(byTag.values());
    }

    /**
     * @param stack the item to check
     * @return whether the item is in the tag of any loaded set (regardless of role and level)
     */
    public boolean isItemInAnyRegisteredTag(ItemStack stack) {
        for (TagKey<Item> tag : snapshot.tagKeys) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** @return all real role names (without the wildcard), sorted */
    public Set<String> getRoles() {
        return snapshot.roles;
    }

    /** @return all loaded entries */
    public List<SetEntry> getAllEntries() {
        return new ArrayList<>(snapshot.byKey.values());
    }

    /** @return the registry as JSON for the client sync packet */
    public String serializeToJson() {
        Map<String, ArmorSetData> map = new LinkedHashMap<>();
        snapshot.byKey.forEach((key, entry) -> map.put(key, entry.data()));
        return GSON.toJson(map);
    }

    /**
     * Replaces the contents with a JSON string produced by {@link #serializeToJson()}. Malformed input is logged and
     * leaves the registry empty instead of throwing.
     *
     * @param json the received JSON
     */
    public void deserializeFromJson(String json) {
        List<SetEntry> entries = new ArrayList<>();
        try {
            Map<String, ArmorSetData> incoming = GSON.fromJson(json, DATA_MAP_TYPE);
            if (incoming != null) {
                incoming.forEach((key, data) -> {
                    SetEntry entry = parseKey(key, data);
                    if (entry != null) {
                        entries.add(entry);
                    }
                });
            }
        } catch (RuntimeException e) {
            SetWeaver.LOGGER.error("[SetWeaver] Could not read set definitions from the server: {}", e.getMessage());
        }
        replaceAll(entries);
    }

    private static SetEntry findEntry(Snapshot current, String role, int level, String tag) {
        SetEntry entry = current.byKey.get(makeKey(role, level, tag));
        if (entry == null) {
            entry = current.byKey.get(makeKey(WILDCARD_ROLE, level, tag));
        }
        if (entry == null) {
            entry = current.byKey.get(makeKey(WILDCARD_ROLE, WILDCARD_LEVEL, tag));
        }
        return entry;
    }

    // Key format: "role:level:namespace:path"
    private static String makeKey(String role, int level, String tag) {
        return role + ":" + level + ":" + tag;
    }

    private static SetEntry parseKey(String key, ArmorSetData data) {
        String[] parts = key.split(":", 3);
        if (parts.length < 3) {
            return null;
        }
        try {
            return createEntry(parts[0], Integer.parseInt(parts[1]), parts[2], data);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Immutable registry state with derived lookups. */
    private static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(Map.of());

        final Map<String, SetEntry> byKey;
        final List<TagKey<Item>> tagKeys;
        final Set<String> roles;

        Snapshot(Map<String, SetEntry> byKey) {
            this.byKey = byKey;
            Set<TagKey<Item>> tags = new java.util.LinkedHashSet<>();
            Set<String> roleNames = new TreeSet<>();
            for (SetEntry entry : byKey.values()) {
                tags.add(entry.tagKey());
                if (!entry.isWildcardRole()) {
                    roleNames.add(entry.role());
                }
            }
            this.tagKeys = List.copyOf(tags);
            this.roles = Collections.unmodifiableSet(roleNames);
        }
    }
}
