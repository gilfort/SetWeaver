package com.gilfort.setweaver.seteffects;

import com.gilfort.setweaver.SetWeaver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Attribute packages: named groups of attributes that tooltips collapse into one line (hold ALT to expand).
 * A package only collapses when at least two of its attributes are present; among several matching packages the
 * one with the lowest priority value wins.
 *
 * <p>Stored in {@code config/setweaver/attribute_packages.json} on the server and synced to clients. Like
 * {@link ArmorSetDataRegistry} there is one instance per logical side.</p>
 */
public final class AttributePackageManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<AttributePackage>>() {}.getType();

    private static final AttributePackageManager SERVER = new AttributePackageManager();
    private static final AttributePackageManager CLIENT = new AttributePackageManager();

    /** All packages, including disabled ones, sorted by priority. */
    private volatile List<AttributePackage> all = List.of();
    /** Enabled packages with at least two attributes, sorted by priority. */
    private volatile List<AttributePackage> active = List.of();

    private AttributePackageManager() {
    }

    /** @return the packages loaded from the server config */
    public static AttributePackageManager server() {
        return SERVER;
    }

    /** @return the packages received from the server */
    public static AttributePackageManager client() {
        return CLIENT;
    }

    /** A named group of attribute ids. */
    public static class AttributePackage {
        private String name;
        private boolean enabled = true;
        private int priority = 100;
        private List<String> attributes = new ArrayList<>();

        /** @return the display name */
        public String getName() {
            return name;
        }

        /** @return whether the package is used in tooltips */
        public boolean isEnabled() {
            return enabled;
        }

        /** @return the priority, lower values win */
        public int getPriority() {
            return priority;
        }

        /** @return the attribute ids, never {@code null} */
        public List<String> getAttributes() {
            return attributes == null ? List.of() : attributes;
        }

        /** @param name the display name */
        public void setName(String name) {
            this.name = name;
        }

        /** @param enabled whether the package is used in tooltips */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @param priority the priority, lower values win */
        public void setPriority(int priority) {
            this.priority = priority;
        }

        /** @param attributes the attribute ids */
        public void setAttributes(List<String> attributes) {
            this.attributes = attributes;
        }

        /** @return a deep copy with a mutable attribute list */
        public AttributePackage copy() {
            AttributePackage copy = new AttributePackage();
            copy.name = name;
            copy.enabled = enabled;
            copy.priority = priority;
            copy.attributes = new ArrayList<>(getAttributes());
            return copy;
        }
    }

    /** JSON root object of the config file. */
    private static class PackagesConfig {
        List<AttributePackage> packages = new ArrayList<>();
    }

    /**
     * Replaces all packages.
     *
     * @param packages the new packages (copied)
     */
    public void setPackages(List<AttributePackage> packages) {
        List<AttributePackage> sorted = new ArrayList<>();
        for (AttributePackage pkg : packages) {
            if (pkg != null) {
                AttributePackage copy = pkg.copy();
                // Old ids such as minecraft:generic.armor are matched under their current name
                copy.setAttributes(new ArrayList<>(copy.getAttributes().stream()
                        .map(id -> SetIds.canonicalAttributeId(id).orElse(id)).distinct().toList()));
                sorted.add(copy);
            }
        }
        sorted.sort(Comparator.comparingInt(AttributePackage::getPriority));
        all = List.copyOf(sorted);
        active = sorted.stream().filter(p -> p.isEnabled() && p.getAttributes().size() >= 2).toList();
    }

    /** @return all packages including disabled ones (read-only, copy before editing) */
    public List<AttributePackage> getAllPackages() {
        return all;
    }

    /**
     * Finds the package an attribute is shown in, given the other attributes of the same set bonus.
     *
     * @param attributeId       the attribute to look up
     * @param presentAttributes all attribute ids of the set bonus
     * @return the best matching package, or {@code null}
     */
    public AttributePackage findBestPackageFor(String attributeId, Set<String> presentAttributes) {
        for (AttributePackage pkg : active) {
            if (!pkg.getAttributes().contains(attributeId)) {
                continue;
            }
            long matchCount = pkg.getAttributes().stream().filter(presentAttributes::contains).count();
            if (matchCount >= 2) {
                // active is sorted by priority, so the first match wins
                return pkg;
            }
        }
        return null;
    }

    /** @return all packages as JSON for the client sync packet */
    public String serializeToJson() {
        return GSON.toJson(all);
    }

    /**
     * Replaces the packages with a JSON list. Malformed input is logged and clears the packages.
     *
     * @param json the JSON list
     */
    public void deserializeFromJson(String json) {
        setPackages(parseList(json));
    }

    /**
     * Parses a JSON list of packages.
     *
     * @param json the JSON list
     * @return the packages, empty if the input is blank or malformed
     */
    public static List<AttributePackage> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<AttributePackage> list = GSON.fromJson(json, LIST_TYPE);
            return list == null ? List.of() : list;
        } catch (RuntimeException e) {
            SetWeaver.LOGGER.error("[SetWeaver] Could not read attribute packages: {}", e.getMessage());
            return List.of();
        }
    }

    // ─── Server-side config file ─────────────────────────────────────────

    private static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("setweaver").resolve("attribute_packages.json");
    }

    /** Loads the config file into {@link #server()}, writing the default file first if it is missing. */
    public static void loadFromConfig() {
        Path file = configFile();
        if (!Files.exists(file)) {
            writeConfig(defaultPackages());
            SetWeaver.LOGGER.info("[SetWeaver] Created default attribute_packages.json");
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            PackagesConfig config = GSON.fromJson(reader, PackagesConfig.class);
            SERVER.setPackages(config == null || config.packages == null ? List.of() : config.packages);
            SetWeaver.LOGGER.info("[SetWeaver] Loaded {} attribute package(s)", SERVER.all.size());
        } catch (IOException | RuntimeException e) {
            SetWeaver.LOGGER.error("[SetWeaver] Failed to load attribute_packages.json: {}", e.getMessage());
            SERVER.setPackages(List.of());
        }
    }

    /**
     * Writes the packages to the config file and reloads {@link #server()}.
     *
     * @param packages the packages to store
     * @return whether writing succeeded
     */
    public static boolean saveAndReload(List<AttributePackage> packages) {
        boolean ok = writeConfig(packages);
        loadFromConfig();
        return ok;
    }

    private static boolean writeConfig(List<AttributePackage> packages) {
        PackagesConfig config = new PackagesConfig();
        config.packages = new ArrayList<>(packages);
        try {
            Path file = configFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(config), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            SetWeaver.LOGGER.error("[SetWeaver] Failed to save attribute_packages.json: {}", e.getMessage());
            return false;
        }
    }

    private static List<AttributePackage> defaultPackages() {
        AttributePackage defense = new AttributePackage();
        defense.setName("Defense");
        defense.setEnabled(false);
        defense.setAttributes(new ArrayList<>(List.of("minecraft:armor", "minecraft:armor_toughness")));
        return List.of(defense);
    }
}
