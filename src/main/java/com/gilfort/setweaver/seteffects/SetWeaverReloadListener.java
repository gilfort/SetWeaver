package com.gilfort.setweaver.seteffects;

import com.gilfort.setweaver.SetWeaver;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Loads all JSON files from config/setweaver/set_armor/.
 *
 * Folder structure:
 *   config/setweaver/set_armor/{role}/{level}/{namespace}__{tagpath}.json
 *
 * Examples:
 *   config/setweaver/set_armor/naturalist/3/setweaver__magiccloth_armor.json
 *   config/setweaver/set_armor/naturalist/3/arsnouveau__tier2armor.json
 *
 * Filename is interpreted as an item tag:
 *   "setweaver__magiccloth_armor" -> "setweaver:magiccloth_armor"
 *
 * Tag-only (no armor material compatibility).
 */
public class SetWeaverReloadListener implements PreparableReloadListener {

    private static final File BASE_DIR = new File(
            FMLPaths.CONFIGDIR.get().toFile(),
            "setweaver" + File.separator + "set_armor"
    );

    /**
     * Custom Gson instance with backward-compatible deserializer for PartData.
     * Handles both old Map format and new List format for Attributes.
     *
     * <p>Old format: {@code "Attributes": {"minecraft:generic.max_health": {"value": 4, "modifier": "addition"}}}</p>
     * <p>New format: {@code "Attributes": [{"attribute": "minecraft:generic.max_health", "value": 4, "modifier": "addition"}]}</p>
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ArmorSetData.PartData.class, (JsonDeserializer<ArmorSetData.PartData>) (json, typeOfT, context) -> {
                JsonObject obj = json.getAsJsonObject();
                ArmorSetData.PartData pd = new ArmorSetData.PartData();

                // Deserialize Effects normally
                if (obj.has("Effects") && obj.get("Effects").isJsonArray()) {
                    java.lang.reflect.Type effectListType = new com.google.gson.reflect.TypeToken<List<ArmorSetData.EffectData>>(){}.getType();
                    pd.setEffects(context.deserialize(obj.get("Effects"), effectListType));
                }

                // Deserialize Attributes — handle both Map (old) and List (new)
                if (obj.has("Attributes")) {
                    JsonElement attrElem = obj.get("Attributes");
                    if (attrElem.isJsonArray()) {
                        // New List format
                        java.lang.reflect.Type attrListType = new com.google.gson.reflect.TypeToken<List<ArmorSetData.AttributeData>>(){}.getType();
                        pd.setAttributes(context.deserialize(attrElem, attrListType));
                    } else if (attrElem.isJsonObject()) {
                        // Old Map format: convert to List
                        List<ArmorSetData.AttributeData> attrList = new ArrayList<>();
                        for (Map.Entry<String, JsonElement> entry : attrElem.getAsJsonObject().entrySet()) {
                            ArmorSetData.AttributeData ad = context.deserialize(entry.getValue(), ArmorSetData.AttributeData.class);
                            ad.setAttribute(entry.getKey());
                            attrList.add(ad);
                        }
                        pd.setAttributes(attrList);
                    }
                }

                return pd;
            })
            .create();

    /**
     * Called by vanilla/NeoForge when {@code /reload} is executed.
     * Reloads all set definitions from the config directory.
     * The actual client sync is handled by {@link SetWeaver#onDatapackSync}.
     */
    @Override
    public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier,
                                          ResourceManager resourceManager,
                                          ProfilerFiller preparationsProfiler,
                                          ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor,
                                          Executor gameExecutor) {
        return CompletableFuture.runAsync(() -> {
            // Nothing to prepare
        }, backgroundExecutor).thenCompose(preparationBarrier::wait).thenRunAsync(() -> {
            loadAllEffects();
            SetWeaver.LOGGER.info("[SetWeaver] Reloaded set definitions via /reload");
        }, gameExecutor);
    }

    public static void loadAllEffects() {
        boolean firstRun = !BASE_DIR.exists();

        if (firstRun) {
            BASE_DIR.mkdirs();
            // Create wildcard directories
            new File(BASE_DIR, "all_roles").mkdirs();
            new File(BASE_DIR, "all_roles_all_levels").mkdirs();
            // Create HOW-TO file
            writeHowToFile();
            // Create example file
            writeExampleFile();
            AttributePackageManager.loadFromConfig();
            SetWeaver.LOGGER.info("[SetWeaver] Created config directory with documentation: {}",
                    BASE_DIR.getAbsolutePath());
            return;
        }

        ArmorSetDataRegistry.clear();
        AttributePackageManager.loadFromConfig();
        walkDirectory(BASE_DIR);
    }

    private static void writeHowToFile() {
        File howTo = new File(BASE_DIR, "HOW_TO_SET_EFFECTS.txt");
        if (howTo.exists()) return;

        String content = """
            ╔══════════════════════════════════════════════════════════════════╗
            ║              SETWEAVER — SET EFFECT CONFIGURATION               ║
            ╚══════════════════════════════════════════════════════════════════╝

            ── FOLDER STRUCTURE ─────────────────────────────────────────────

              config/setweaver/set_armor/
              ├── {role}/{level}/          Standard: applies to specific role + level
              ├── all_roles/{level}/       Wildcard: applies to ALL roles for a level
              └── all_roles_all_levels/    Universal: applies ALWAYS (no role/level needed)

              Priority (highest wins):
                1. {role}/{level}/         ← most specific
                2. all_roles/{level}/      ← level-specific wildcard
                3. all_roles_all_levels/   ← universal fallback

              If the SAME tag is defined at multiple levels, the most specific wins.
              There is NO merging between levels.

            ── FILE NAMING ──────────────────────────────────────────────────

              Filename format: {namespace}__{tagpath}.json
              The double underscore (__) becomes a colon (:) → item tag reference

              Examples:
                setweaver__magiccloth_armor.json  → tag "setweaver:magiccloth_armor"
                c__iron_armors.json              → tag "c:iron_armors"
                minecraft__gold_armor.json       → tag "minecraft:gold_armor"

            ── JSON FORMAT ──────────────────────────────────────────────────

              {
                "displayName": "Magiccloth Robes",       ← optional, shown in tooltip
                "parts": {
                  "2Part": {                              ← activates at 2+ pieces
                    "Effects": [
                      { "Effect": "minecraft:speed", "Amplifier": 0 }
                    ],
                    "Attributes": {
                      "minecraft:generic.armor": { "value": 2.0, "modifier": "addition" }
                    }
                  },
                  "4Part": {                              ← activates at 4 pieces (replaces 2Part!)
                    "Effects": [
                      { "Effect": "minecraft:speed", "Amplifier": 1 },
                      { "Effect": "minecraft:night_vision", "Amplifier": 0 }
                    ],
                    "Attributes": {
                      "minecraft:generic.armor": { "value": 5.0, "modifier": "addition" }
                    }
                  }
                }
              }

            ── THRESHOLD SYSTEM ─────────────────────────────────────────────

              Set bonuses use a THRESHOLD system, not exact matching:
              The highest defined threshold ≤ worn pieces is active.

              Example: Only "2Part" and "4Part" defined:
                1 piece  → no bonus
                2 pieces → 2Part active
                3 pieces → 2Part STILL active (no 3Part defined)
                4 pieces → 4Part active (REPLACES 2Part entirely)

              Each threshold defines the COMPLETE effect set — no stacking between levels.
              This means you can add AND remove effects between thresholds!

            ── MODIFIER TYPES ───────────────────────────────────────────────

              "addition"        → flat bonus (e.g. +2.0 armor)
              "multiply_base"   → percentage of base (e.g. 0.1 = +10%)
              "multiply_total"  → percentage of total (after all additions)

            ── EFFECT IDS ───────────────────────────────────────────────────

              Use Minecraft effect IDs. Common ones:
                minecraft:speed             minecraft:haste
                minecraft:strength          minecraft:regeneration
                minecraft:resistance        minecraft:fire_resistance
                minecraft:night_vision      minecraft:water_breathing
                minecraft:jump_boost        minecraft:slow_falling
                minecraft:invisibility      minecraft:absorption
                minecraft:slowness          minecraft:mining_fatigue

              Amplifier: 0 = Level I, 1 = Level II, 2 = Level III, etc.

            ── ATTRIBUTE IDS ────────────────────────────────────────────────

              minecraft:generic.max_health          minecraft:generic.armor
              minecraft:generic.armor_toughness     minecraft:generic.attack_damage
              minecraft:generic.attack_speed        minecraft:generic.movement_speed
              minecraft:generic.knockback_resistance
              minecraft:generic.luck

            ── INGAME COMMANDS ──────────────────────────────────────────────

              /setweaver reload                      Reload all set definitions
              /setweaver debug sets                  Show all loaded sets for your role/level
              /setweaver debug tag <tag>             Debug a specific tag match
              /setweaver setrole <role>            Set your role
              /setweaver setlevel <level>              Set your level
              /setweaver checkrole <player>         Check a player's role
              /setweaver checklevel <player>           Check a player's level
              /setweaver sets list                   List all loaded set definitions
              /setweaver sets create <tag>           Generate a template JSON for a tag
              /setweaver sets validate               Check all JSON files for errors
              /setweaver sets info <tag>             Show full details of a set definition

            ── TIPS ─────────────────────────────────────────────────────────

              • Use /setweaver sets create to generate template files quickly
              • Use /setweaver sets validate after editing to catch errors
              • Use /setweaver reload to apply changes without restart
              • The tooltip shows [SHIFT] to reveal set bonuses on any armor piece
              • If an item belongs to multiple sets, SHIFT+Scroll to browse them
              • "displayName" is optional — without it, the tag path is auto-formatted

            ══════════════════════════════════════════════════════════════════
            """;

        try (var writer = new java.io.FileWriter(howTo)) {
            writer.write(content);
        } catch (Exception e) {
            SetWeaver.LOGGER.error("[SetWeaver] Could not write HOW_TO file: {}", e.getMessage());
        }
    }

    private static void writeExampleFile() {
        File exampleDir = new File(BASE_DIR, "_example" + File.separator + "1");
        exampleDir.mkdirs();
        File exampleFile = new File(exampleDir, "setweaver__example_armor.json.disabled");
        if (exampleFile.exists()) return;

        String content = """
            {
              "displayName": "Example Armor Set",
              "parts": {
                "2Part": {
                  "Effects": [
                    { "Effect": "minecraft:speed", "Amplifier": 0 }
                  ],
                  "Attributes": {
                    "minecraft:generic.armor": { "value": 2.0, "modifier": "addition" }
                  }
                },
                "4Part": {
                  "Effects": [
                    { "Effect": "minecraft:speed", "Amplifier": 1 },
                    { "Effect": "minecraft:night_vision", "Amplifier": 0 }
                  ],
                  "Attributes": {
                    "minecraft:generic.armor": { "value": 5.0, "modifier": "addition" },
                    "minecraft:generic.movement_speed": { "value": 0.1, "modifier": "multiply_base" }
                  }
                }
              }
            }
            """;

        try (var writer = new java.io.FileWriter(exampleFile)) {
            writer.write(content);
        } catch (Exception e) {
            SetWeaver.LOGGER.error("[SetWeaver] Could not write example file: {}", e.getMessage());
        }
    }

    // ─── Validation Results ──────────────────────────────────────────────

    public record ValidationResult(String filePath, Status status, String message) {
        public enum Status { OK, WARNING, ERROR }
    }

    /**
     * Validates all JSON files in the set_armor directory WITHOUT loading them
     * into the registry. Returns structured results for chat display.
     */
    public static List<ValidationResult> validateAllFiles() {
        List<ValidationResult> results = new ArrayList<>();
        if (!BASE_DIR.exists()) {
            results.add(new ValidationResult(
                    BASE_DIR.getPath(), ValidationResult.Status.ERROR,
                    "Config directory does not exist"));
            return results;
        }
        collectValidationResults(BASE_DIR, results);
        return results;
    }

    private static void collectValidationResults(File dir, List<ValidationResult> results) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectValidationResults(file, results);
            } else if (file.getName().endsWith(".json")) {
                validateSingleFile(file, results);
            }
            // .disabled files are silently skipped (intentional)
        }
    }

    private static void validateSingleFile(File file, List<ValidationResult> results) {
        // Relative path for readable output
        String relativePath = BASE_DIR.toPath().relativize(file.toPath()).toString();

        // ── Structure check: path depth ──────────────────────────────────
        java.nio.file.Path relPath = BASE_DIR.toPath().relativize(file.toPath());
        int segments = relPath.getNameCount();

        if (segments == 3) {
            String dirName = relPath.getName(0).toString();
            String levelName = relPath.getName(1).toString();
            if (!"all_roles".equalsIgnoreCase(dirName)) {
                // Must be {role}/{level}/file.json — check level is numeric
                try {
                    Integer.parseInt(levelName);
                } catch (NumberFormatException e) {
                    results.add(new ValidationResult(relativePath,
                            ValidationResult.Status.ERROR,
                            "Level folder '" + levelName + "' is not a number"));
                    return;
                }
            } else {
                try {
                    Integer.parseInt(levelName);
                } catch (NumberFormatException e) {
                    results.add(new ValidationResult(relativePath,
                            ValidationResult.Status.ERROR,
                            "Level folder '" + levelName + "' under all_roles is not a number"));
                    return;
                }
            }
        } else if (segments == 2) {
            String dirName = relPath.getName(0).toString();
            if (!"all_roles_all_levels".equalsIgnoreCase(dirName) && !"_example".equalsIgnoreCase(dirName)) {
                results.add(new ValidationResult(relativePath,
                        ValidationResult.Status.ERROR,
                        "2-level path must be in 'all_roles_all_levels/' directory"));
                return;
            }
        } else {
            results.add(new ValidationResult(relativePath,
                    ValidationResult.Status.ERROR,
                    "Invalid directory depth (" + segments + " segments)"));
            return;
        }

        // ── Filename check ───────────────────────────────────────────────
        String fileName = file.getName().replace(".json", "");
        if (!fileName.contains("__")) {
            results.add(new ValidationResult(relativePath,
                    ValidationResult.Status.ERROR,
                    "Filename must contain '__' (e.g. namespace__tagpath.json)"));
            return;
        }

        String tagString = fileName.replaceFirst("__", ":");
        ResourceLocation tagLoc = ResourceLocation.tryParse(tagString);
        if (tagLoc == null) {
            results.add(new ValidationResult(relativePath,
                    ValidationResult.Status.ERROR,
                    "'" + tagString + "' is not a valid ResourceLocation"));
            return;
        }

        // ── JSON parse check ─────────────────────────────────────────────
        try (FileReader reader = new FileReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);

            if (!json.isJsonObject()) {
                results.add(new ValidationResult(relativePath,
                        ValidationResult.Status.ERROR, "Root element is not a JSON object"));
                return;
            }

            ArmorSetData rawData = GSON.fromJson(json, ArmorSetData.class);
            if (rawData == null || rawData.getParts() == null || rawData.getParts().isEmpty()) {
                results.add(new ValidationResult(relativePath,
                        ValidationResult.Status.ERROR, "Missing or empty 'parts' object"));
                return;
            }

            // ── Content validation (effects & attributes) ────────────────
            List<String> warnings = new ArrayList<>();

            for (Map.Entry<String, ArmorSetData.PartData> partEntry : rawData.getParts().entrySet()) {
                String partKey = partEntry.getKey();

                // Check part key format
                String numStr = partKey.replace("Part", "");
                try {
                    int n = Integer.parseInt(numStr);
                    if (n < 1 || n > 4) {
                        warnings.add(partKey + ": threshold " + n + " outside range 1-4");
                    }
                } catch (NumberFormatException e) {
                    results.add(new ValidationResult(relativePath,
                            ValidationResult.Status.ERROR,
                            "Invalid part key '" + partKey + "' (expected format: '2Part')"));
                    return;
                }

                ArmorSetData.PartData pd = partEntry.getValue();

                // Validate effects
                if (pd.getEffects() != null) {
                    for (ArmorSetData.EffectData ed : pd.getEffects()) {
                        ResourceLocation eLoc = tryMakeResourceLocation(ed.getEffect());
                        MobEffect mob = eLoc == null ? null : BuiltInRegistries.MOB_EFFECT.get(eLoc);
                        if (mob == null) {
                            warnings.add(partKey + ": unknown effect '" + ed.getEffect() + "'");
                        }
                    }
                }

                // Validate attributes
                if (pd.getAttributes() != null) {
                    for (ArmorSetData.AttributeData ae : pd.getAttributes()) {
                        ResourceLocation aLoc = tryMakeResourceLocation(ae.getAttribute());
                        Attribute attr = aLoc == null ? null : BuiltInRegistries.ATTRIBUTE.get(aLoc);
                        if (attr == null) {
                            warnings.add(partKey + ": unknown attribute '" + ae.getAttribute() + "'");
                        }
                        // Check modifier type
                        String mod = ae.getModifier();
                        if (mod == null || (!mod.equalsIgnoreCase("addition")
                                && !mod.equalsIgnoreCase("multiply_base")
                                && !mod.equalsIgnoreCase("multiply")
                                && !mod.equalsIgnoreCase("multiply_total"))) {
                            warnings.add(partKey + ": invalid modifier '" + mod
                                    + "' (use: addition, multiply_base, multiply_total)");
                        }
                    }
                }
            }

            if (!warnings.isEmpty()) {
                results.add(new ValidationResult(relativePath,
                        ValidationResult.Status.WARNING,
                        String.join("; ", warnings)));
            } else {
                results.add(new ValidationResult(relativePath,
                        ValidationResult.Status.OK, "Valid"));
            }

        } catch (JsonSyntaxException e) {
            results.add(new ValidationResult(relativePath,
                    ValidationResult.Status.ERROR,
                    "JSON syntax error: " + e.getMessage()));
        } catch (IOException e) {
            results.add(new ValidationResult(relativePath,
                    ValidationResult.Status.ERROR,
                    "Could not read file: " + e.getMessage()));
        }
    }


    private static void walkDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                walkDirectory(file);
            } else if (file.getName().endsWith(".json")) {
                try {
                    handleJsonFile(file);
                } catch (Exception e) {
                    SetWeaver.LOGGER.error("[SetWeaver] Error loading file {}: {}",
                            file.getAbsolutePath(), e.getMessage());
                }
            }
        }
    }

    private static void handleJsonFile(File file) throws IOException {
        // ── Determine role & level from relative path depth ──────────────
        // 3 segments: {role}/{level}/file.json          → standard
        // 2 segments: all_roles_all_levels/file.json    → universal wildcard
        java.nio.file.Path relativePath = BASE_DIR.toPath().relativize(file.toPath());
        int segmentCount = relativePath.getNameCount(); // includes filename

        String role;
        int level;

        if (segmentCount == 3) {
            // Standard:    {role}/{level}/file.json
            // Or:          all_roles/{level}/file.json
            String roleName = relativePath.getName(0).toString();
            String levelName  = relativePath.getName(1).toString();

            if ("all_roles".equalsIgnoreCase(roleName)) {
                role = ArmorSetDataRegistry.WILDCARD_ROLE;
            } else {
                role = roleName;
            }

            try {
                level = Integer.parseInt(levelName);
            } catch (NumberFormatException e) {
                SetWeaver.LOGGER.error(
                        "[SetWeaver] Invalid level folder '{}' in path: {}. Skipping.",
                        levelName, file.getAbsolutePath());
                return;
            }

        } else if (segmentCount == 2) {
            // Universal:   all_roles_all_levels/file.json
            String dirName = relativePath.getName(0).toString();
            if (!"all_roles_all_levels".equalsIgnoreCase(dirName)) {
                SetWeaver.LOGGER.error(
                        "[SetWeaver] Unexpected 2-level path: {}. " +
                                "Expected 'all_roles_all_levels/' or '{role}/{level}/'. Skipping.",
                        file.getAbsolutePath());
                return;
            }
            role = ArmorSetDataRegistry.WILDCARD_ROLE;
            level  = ArmorSetDataRegistry.WILDCARD_LEVEL;

        } else {
            SetWeaver.LOGGER.error(
                    "[SetWeaver] Invalid directory depth ({} segments) for file: {}. " +
                            "Expected: {role}/{level}/file.json or all_roles_all_levels/file.json",
                    segmentCount, file.getAbsolutePath());
            return;
        }

        // ── Parse filename as tag ────────────────────────────────────────
        String fileName = file.getName().replace(".json", "");

        if (!fileName.contains("__")) {
            SetWeaver.LOGGER.error(
                    "[SetWeaver] Invalid filename '{}' – expected format: " +
                            "namespace__tagpath.json (e.g. 'setweaver__magiccloth_armor.json'). Skipping.",
                    file.getName());
            return;
        }

        String tagString = fileName.replaceFirst("__", ":");
        ResourceLocation tagLoc = ResourceLocation.tryParse(tagString);
        if (tagLoc == null) {
            SetWeaver.LOGGER.error(
                    "[SetWeaver] '{}' is not a valid ResourceLocation (from file '{}'). Skipping.",
                    tagString, file.getName());
            return;
        }

        // ── Parse & validate JSON ────────────────────────────────────────
        try (FileReader reader = new FileReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);

            if (!json.isJsonObject()) {
                SetWeaver.LOGGER.error("[SetWeaver] Invalid JSON format in file: {}",
                        file.getAbsolutePath());
                return;
            }

            ArmorSetData rawData = GSON.fromJson(json, ArmorSetData.class);
            if (rawData == null || rawData.getParts() == null || rawData.getParts().isEmpty()) {
                SetWeaver.LOGGER.error(
                        "[SetWeaver] No 'parts' found in file: {} (tag {}). Skipping.",
                        file.getAbsolutePath(), tagString);
                return;
            }

            ArmorSetData validatedData = validateData(rawData, file);
            ArmorSetDataRegistry.put(role.toLowerCase(), level, tagString, validatedData);

            // ── Descriptive log message ──────────────────────────────────
            String scope;
            if (ArmorSetDataRegistry.WILDCARD_ROLE.equals(role)
                    && level == ArmorSetDataRegistry.WILDCARD_LEVEL) {
                scope = "ALL roles, ALL levels";
            } else if (ArmorSetDataRegistry.WILDCARD_ROLE.equals(role)) {
                scope = "ALL roles, level=" + level;
            } else {
                scope = "role=" + role + ", level=" + level;
            }
            SetWeaver.LOGGER.info("[SetWeaver] Loaded set definition: {} → tag={}",
                    scope, tagString);
        }
    }


    private static ArmorSetData validateData(ArmorSetData data, File file) {
        data.getParts().forEach((partName, partData) -> {

            // Validate effects
            if (partData.getEffects() != null) {
                var it = partData.getEffects().iterator();
                while (it.hasNext()) {
                    ArmorSetData.EffectData ed = it.next();
                    ResourceLocation id = tryMakeResourceLocation(ed.getEffect());
                    MobEffect mob = id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id);
                    if (mob == null) {
                        SetWeaver.LOGGER.error("[SetWeaver] Unknown effect '{}' in {} – skipped", ed.getEffect(), file);
                        it.remove();
                        continue;
                    }

                    // Clamp invalid roman level keys (existing behavior)
                    int lvl = ed.getAmplifier() + 1;
                    String key = "enchantment.level." + lvl;
                    if (!Language.getInstance().has(key)) {
                        int max = 5;
                        SetWeaver.LOGGER.warn("[SetWeaver] Level {} for effect '{}' in {} invalid, clamped to {}",
                                lvl, ed.getEffect(), file, max);
                        ed.setAmplifier(max - 1);
                    }
                }
            }

            // Validate attributes
            if (partData.getAttributes() != null) {
                var attrIt = partData.getAttributes().iterator();
                while (attrIt.hasNext()) {
                    var ad = attrIt.next();
                    ResourceLocation aid = tryMakeResourceLocation(ad.getAttribute());
                    Attribute attr = aid == null ? null : BuiltInRegistries.ATTRIBUTE.get(aid);
                    if (attr == null) {
                        SetWeaver.LOGGER.error("[SetWeaver] Unknown attribute '{}' in {} – removed",
                                ad.getAttribute(), file);
                        attrIt.remove();
                    }
                }
            }
        });

        return data;
    }

    private static ResourceLocation tryMakeResourceLocation(String raw) {
        String s = raw.contains(":") ? raw : "minecraft:" + raw;
        try {
            return ResourceLocation.tryParse(s);
        } catch (Exception e) {
            SetWeaver.LOGGER.error("[SetWeaver] Invalid ResourceLocation '{}' – {}", raw, e.getMessage());
            return null;
        }
    }
}
