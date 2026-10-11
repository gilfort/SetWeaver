package com.gilfort.setweaver.seteffects;

import com.gilfort.setweaver.SetWeaver;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Manages "attribute packages" — named groups of attributes that are displayed
 * as a single collapsed line in armor set tooltips.
 *
 * <p>When a set bonus grants multiple attributes that all belong to the same package,
 * the tooltip shows the package name instead of listing each attribute individually.
 * The player can hold ALT to expand the package and see the individual attributes.</p>
 *
 * <p>If only ONE attribute from a package is present in a set bonus, it is shown
 * directly (no collapsing).</p>
 *
 * <h3>Config location</h3>
 * {@code config/setweaver/attribute_packages.json}
 *
 * @see ArmorSetData.AttributeData
 */
public class AttributePackageManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(
            FMLPaths.CONFIGDIR.get().toFile(),
            "setweaver" + File.separator + "attribute_packages.json"
    );

    /** All loaded packages, keyed by package name. */
    private static final List<AttributePackage> PACKAGES = new ArrayList<>();

    /**
     * A named group of attribute IDs that should be displayed together.
     *
     * <p>When an attribute belongs to multiple packages, the package with the
     * <b>lowest priority value</b> wins (0 = highest priority).</p>
     */
    public static class AttributePackage {
        private String name;
        private boolean enabled = true;
        private int priority = 100;
        private List<String> attributes;

        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public int getPriority() { return priority; }
        public List<String> getAttributes() { return attributes; }

        public void setName(String name) { this.name = name; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setPriority(int priority) { this.priority = priority; }
        public void setAttributes(List<String> attributes) { this.attributes = attributes; }
    }

    /** Wrapper for JSON root object. */
    private static class PackagesConfig {
        List<AttributePackage> packages = new ArrayList<>();
    }

    // ─── Loading ─────────────────────────────────────────────────────────

    /**
     * Loads attribute packages from the config file.
     * Creates a default file with a disabled "Defense" example if it doesn't exist.
     */
    public static void loadFromConfig() {
        PACKAGES.clear();
        ALL_PACKAGES.clear();

        if (!CONFIG_FILE.exists()) {
            writeDefaultConfig();
            SetWeaver.LOGGER.info("[SetWeaver] Created default attribute_packages.json");
            return; // default has only disabled packages
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            PackagesConfig config = GSON.fromJson(reader, PackagesConfig.class);
            if (config != null && config.packages != null) {
                for (AttributePackage pkg : config.packages) {
                    if (pkg.getAttributes() == null) pkg.setAttributes(new ArrayList<>());
                    ALL_PACKAGES.add(pkg);
                    if (pkg.isEnabled() && pkg.getAttributes().size() >= 2) {
                        PACKAGES.add(pkg);
                    }
                }
            }
            // Sort active packages by priority (lowest first = highest priority)
            PACKAGES.sort(Comparator.comparingInt(AttributePackage::getPriority));
            ALL_PACKAGES.sort(Comparator.comparingInt(AttributePackage::getPriority));
            SetWeaver.LOGGER.info("[SetWeaver] Loaded {} active attribute package(s)", PACKAGES.size());
        } catch (Exception e) {
            SetWeaver.LOGGER.error("[SetWeaver] Failed to load attribute_packages.json: {}", e.getMessage());
        }
    }

    // ─── Querying ────────────────────────────────────────────────────────

    /**
     * Returns the list of currently loaded (enabled) packages.
     */
    public static List<AttributePackage> getPackages() {
        return Collections.unmodifiableList(PACKAGES);
    }

    /**
     * Finds the package that contains the given attribute ID, or {@code null}
     * if the attribute doesn't belong to any package.
     */
    public static AttributePackage findPackageFor(String attributeId) {
        for (AttributePackage pkg : PACKAGES) {
            if (pkg.getAttributes().contains(attributeId)) {
                return pkg;
            }
        }
        return null;
    }

    /**
     * Context-aware package resolution: finds the best package for an attribute
     * considering which other attributes are actually present in the set bonus.
     *
     * <p>A package is only a valid match if at least 2 of its attributes are present
     * in {@code presentAttributes}. Among valid packages, the one with the lowest
     * priority value (= highest priority) wins.</p>
     *
     * @param attributeId       the attribute to find a package for
     * @param presentAttributes all attribute IDs present in the current set bonus tier
     * @return the best matching package, or {@code null}
     */
    public static AttributePackage findBestPackageFor(String attributeId, Set<String> presentAttributes) {
        AttributePackage best = null;
        for (AttributePackage pkg : PACKAGES) {
            if (!pkg.getAttributes().contains(attributeId)) continue;

            // Count how many of this package's attributes are actually present
            long matchCount = pkg.getAttributes().stream()
                    .filter(presentAttributes::contains)
                    .count();
            if (matchCount < 2) continue; // not enough to form a group

            if (best == null || pkg.getPriority() < best.getPriority()) {
                best = pkg;
            }
        }
        return best;
    }

    // ─── Serialization (for network sync) ────────────────────────────────

    /**
     * Serializes the currently loaded packages to a JSON string for network sync.
     */
    public static String serializeToJson() {
        return GSON.toJson(PACKAGES);
    }

    /**
     * Deserializes packages from a JSON string (received from server).
     * Replaces the current in-memory packages.
     */
    public static void deserializeFromJson(String json) {
        PACKAGES.clear();
        if (json == null || json.isBlank()) return;

        try {
            Type listType = new TypeToken<List<AttributePackage>>() {}.getType();
            List<AttributePackage> list = GSON.fromJson(json, listType);
            if (list != null) {
                PACKAGES.addAll(list);
            }
        } catch (Exception e) {
            SetWeaver.LOGGER.error("[SetWeaver] Failed to deserialize attribute packages: {}", e.getMessage());
        }
    }

    // ─── Editing (for in-game Package Editor) ──────────────────────────

    /** All packages from config (including disabled), for the editor GUI. */
    private static final List<AttributePackage> ALL_PACKAGES = new ArrayList<>();

    /**
     * Returns ALL packages (including disabled), for the editor GUI.
     */
    public static List<AttributePackage> getAllPackages() {
        return ALL_PACKAGES;
    }

    /**
     * Saves the given list of packages to the config file and reloads.
     */
    public static void saveAndReload(List<AttributePackage> packages) {
        CONFIG_FILE.getParentFile().mkdirs();

        PackagesConfig config = new PackagesConfig();
        config.packages = new ArrayList<>(packages);

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
            SetWeaver.LOGGER.info("[SetWeaver] Saved {} attribute package(s) to config", packages.size());
        } catch (IOException e) {
            SetWeaver.LOGGER.error("[SetWeaver] Failed to save attribute_packages.json: {}", e.getMessage());
        }

        // Reload active packages into memory
        loadFromConfig();
    }

    // ─── Default Config ──────────────────────────────────────────────────

    private static void writeDefaultConfig() {
        // Ensure parent directory exists
        CONFIG_FILE.getParentFile().mkdirs();

        PackagesConfig defaultConfig = new PackagesConfig();

        // Add the disabled "Defense" example
        AttributePackage defense = new AttributePackage();
        defense.setName("Defense");
        defense.setEnabled(false);
        defense.setAttributes(List.of(
                "minecraft:generic.armor",
                "minecraft:generic.armor_toughness"
        ));
        defaultConfig.packages.add(defense);
        ALL_PACKAGES.add(defense);

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(defaultConfig, writer);
        } catch (IOException e) {
            SetWeaver.LOGGER.error("[SetWeaver] Could not write default attribute_packages.json: {}", e.getMessage());
        }
    }
}
