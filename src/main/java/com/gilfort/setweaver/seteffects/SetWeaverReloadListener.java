package com.gilfort.setweaver.seteffects;

import com.gilfort.setweaver.SetWeaver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Loads the set definitions from {@code config/setweaver/set_armor/} (see {@link SetFiles} for the layout) on
 * server start and on {@code /reload}, and validates them for the editor.
 */
public class SetWeaverReloadListener implements PreparableReloadListener {

    /** Highest accepted effect amplifier (level X). */
    public static final int MAX_AMPLIFIER = 9;

    private static final Type EFFECT_LIST = new TypeToken<List<ArmorSetData.EffectData>>() {}.getType();
    private static final Type ATTRIBUTE_LIST = new TypeToken<List<ArmorSetData.AttributeData>>() {}.getType();

    /**
     * Gson with a deserializer for parts that accepts both attribute formats: the old map
     * ({@code "Attributes": {"<id>": {"value": 4, "modifier": "addition"}}}) and the current list
     * ({@code "Attributes": [{"attribute": "<id>", "value": 4, "modifier": "addition"}]}).
     */
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ArmorSetData.PartData.class, (JsonDeserializer<ArmorSetData.PartData>) (json, type, context) -> {
                JsonObject obj = json.getAsJsonObject();
                ArmorSetData.PartData part = new ArmorSetData.PartData();
                if (obj.has("Effects") && obj.get("Effects").isJsonArray()) {
                    part.setEffects(context.deserialize(obj.get("Effects"), EFFECT_LIST));
                }
                JsonElement attributes = obj.get("Attributes");
                if (attributes != null && attributes.isJsonArray()) {
                    part.setAttributes(context.deserialize(attributes, ATTRIBUTE_LIST));
                } else if (attributes != null && attributes.isJsonObject()) {
                    List<ArmorSetData.AttributeData> list = new ArrayList<>();
                    for (Map.Entry<String, JsonElement> entry : attributes.getAsJsonObject().entrySet()) {
                        ArmorSetData.AttributeData data = context.deserialize(entry.getValue(), ArmorSetData.AttributeData.class);
                        data.setAttribute(entry.getKey());
                        list.add(data);
                    }
                    part.setAttributes(list);
                }
                return part;
            })
            .setPrettyPrinting()
            .create();

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                          ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.runAsync(() -> { }, backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenRunAsync(SetWeaverReloadListener::loadAllEffects, gameExecutor);
    }

    /**
     * Reads all set files and attribute packages into the server registries. Creates the folder with a how-to file
     * and an example on first start. Must run on the server thread.
     */
    public static void loadAllEffects() {
        Path base = SetFiles.baseDir();
        if (!Files.isDirectory(base)) {
            createDefaultFolder(base);
        }
        AttributePackageManager.loadFromConfig();

        List<ArmorSetDataRegistry.SetEntry> entries = new ArrayList<>();
        for (Path file : listSetFiles(base)) {
            Parsed parsed = parseFile(base, file);
            if (parsed.entry != null) {
                entries.add(parsed.entry);
            }
            for (Component message : parsed.result.messages()) {
                if (parsed.entry == null) {
                    SetWeaver.LOGGER.error("[SetWeaver] {} not loaded: {}", parsed.result.filePath(), message.getString());
                } else {
                    SetWeaver.LOGGER.warn("[SetWeaver] {}: {}", parsed.result.filePath(), message.getString());
                }
            }
        }
        ArmorSetDataRegistry.server().replaceAll(entries);
        SetWeaver.LOGGER.info("[SetWeaver] Loaded {} set definition(s)", entries.size());
    }

    /**
     * Checks all set files without changing the registry.
     *
     * @return one result per file
     */
    public static List<ValidationResult> validateAllFiles() {
        Path base = SetFiles.baseDir();
        if (!Files.isDirectory(base)) {
            return List.of(new ValidationResult(base.toString(), ValidationResult.Status.ERROR,
                    List.of(Component.translatable("message.setweaver.validation.no_folder"))));
        }
        List<ValidationResult> results = new ArrayList<>();
        for (Path file : listSetFiles(base)) {
            results.add(parseFile(base, file).result);
        }
        return results;
    }

    private static List<Path> listSetFiles(Path base) {
        try (Stream<Path> files = Files.walk(base)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(SetFiles.EXTENSION))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            SetWeaver.LOGGER.error("[SetWeaver] Could not list {}: {}", base, e.getMessage());
            return List.of();
        }
    }

    /** Parse outcome: the entry (null if not loadable) and the validation result. */
    private record Parsed(ArmorSetDataRegistry.SetEntry entry, ValidationResult result) {
    }

    private static Parsed parseFile(Path base, Path file) {
        Path relative = base.relativize(file);
        String relativePath = relative.toString().replace('\\', '/');
        List<Component> problems = new ArrayList<>();

        // Scope from the folder structure
        String role;
        int level;
        int segments = relative.getNameCount();
        if (segments == 3) {
            String roleDir = relative.getName(0).toString();
            role = SetFiles.ALL_ROLES_DIR.equalsIgnoreCase(roleDir) ? ArmorSetDataRegistry.WILDCARD_ROLE : roleDir;
            try {
                level = Integer.parseInt(relative.getName(1).toString());
            } catch (NumberFormatException e) {
                return error(relativePath, Component.translatable("message.setweaver.validation.level_not_number",
                        relative.getName(1).toString()));
            }
        } else if (segments == 2 && SetFiles.ALL_ROLES_ALL_LEVELS_DIR.equalsIgnoreCase(relative.getName(0).toString())) {
            role = ArmorSetDataRegistry.WILDCARD_ROLE;
            level = ArmorSetDataRegistry.WILDCARD_LEVEL;
        } else {
            return error(relativePath, Component.translatable("message.setweaver.validation.bad_location"));
        }

        ResourceLocation tag = SetFiles.tagFromFileName(file.getFileName().toString());
        if (tag == null) {
            return error(relativePath, Component.translatable("message.setweaver.validation.bad_file_name"));
        }

        ArmorSetData data;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonObject()) {
                return error(relativePath, Component.translatable("message.setweaver.validation.not_object"));
            }
            data = GSON.fromJson(json, ArmorSetData.class);
        } catch (JsonParseException | IllegalStateException e) {
            return error(relativePath, Component.translatable("message.setweaver.validation.syntax", String.valueOf(e.getMessage())));
        } catch (IOException e) {
            return error(relativePath, Component.translatable("message.setweaver.validation.unreadable", String.valueOf(e.getMessage())));
        }
        if (data == null || data.getParts() == null || data.getParts().isEmpty()) {
            return error(relativePath, Component.translatable("message.setweaver.validation.no_parts"));
        }

        sanitize(data, problems);
        if (!data.hasBonuses()) {
            problems.add(Component.translatable("message.setweaver.validation.no_bonuses"));
        }

        ArmorSetDataRegistry.SetEntry entry = ArmorSetDataRegistry.createEntry(role, level, tag.toString(), data);
        ValidationResult.Status status = problems.isEmpty() ? ValidationResult.Status.OK : ValidationResult.Status.WARNING;
        return new Parsed(entry, new ValidationResult(relativePath, status, problems));
    }

    /** Removes invalid parts, effects and attributes, recording a message for each. */
    private static void sanitize(ArmorSetData data, List<Component> problems) {
        Iterator<Map.Entry<String, ArmorSetData.PartData>> parts = data.getParts().entrySet().iterator();
        while (parts.hasNext()) {
            Map.Entry<String, ArmorSetData.PartData> part = parts.next();
            String key = part.getKey();
            int threshold = ArmorSetData.parseThreshold(key);
            if (threshold < 1 || threshold > ArmorSetData.MAX_PARTS || part.getValue() == null) {
                problems.add(Component.translatable("message.setweaver.validation.bad_part", key, ArmorSetData.MAX_PARTS));
                parts.remove();
                continue;
            }
            ArmorSetData.PartData partData = part.getValue();

            if (partData.getEffects() != null) {
                Iterator<ArmorSetData.EffectData> effects = partData.getEffects().iterator();
                while (effects.hasNext()) {
                    ArmorSetData.EffectData effect = effects.next();
                    if (SetIds.effect(effect.getEffect()).isEmpty()) {
                        problems.add(Component.translatable("message.setweaver.validation.unknown_effect", key, String.valueOf(effect.getEffect())));
                        effects.remove();
                    } else if (effect.getAmplifier() < 0 || effect.getAmplifier() > MAX_AMPLIFIER) {
                        int clamped = Math.clamp(effect.getAmplifier(), 0, MAX_AMPLIFIER);
                        problems.add(Component.translatable("message.setweaver.validation.amplifier", key, effect.getEffect(), clamped));
                        effect.setAmplifier(clamped);
                    }
                }
            }

            if (partData.getAttributes() != null) {
                Iterator<ArmorSetData.AttributeData> attributes = partData.getAttributes().iterator();
                while (attributes.hasNext()) {
                    ArmorSetData.AttributeData attribute = attributes.next();
                    if (SetIds.attribute(attribute.getAttribute()).isEmpty()) {
                        problems.add(Component.translatable("message.setweaver.validation.unknown_attribute", key, String.valueOf(attribute.getAttribute())));
                        attributes.remove();
                    } else if (SetIds.operation(attribute.getModifier()).isEmpty()) {
                        problems.add(Component.translatable("message.setweaver.validation.unknown_modifier", key, String.valueOf(attribute.getModifier())));
                        attributes.remove();
                    }
                }
            }
        }
    }

    private static Parsed error(String relativePath, Component message) {
        return new Parsed(null, new ValidationResult(relativePath, ValidationResult.Status.ERROR, List.of(message)));
    }

    // ─── First start ─────────────────────────────────────────────────────

    private static void createDefaultFolder(Path base) {
        try {
            Files.createDirectories(base.resolve(SetFiles.ALL_ROLES_DIR));
            Files.createDirectories(base.resolve(SetFiles.ALL_ROLES_ALL_LEVELS_DIR));
            Path exampleDir = Files.createDirectories(base.resolve("_example").resolve("1"));
            Files.writeString(base.resolve("HOW_TO_SET_EFFECTS.txt"), HOW_TO, StandardCharsets.UTF_8);
            Files.writeString(exampleDir.resolve("minecraft__example_armor.json.disabled"), EXAMPLE, StandardCharsets.UTF_8);
            SetWeaver.LOGGER.info("[SetWeaver] Created set folder with documentation: {}", base);
        } catch (IOException e) {
            SetWeaver.LOGGER.error("[SetWeaver] Could not create {}: {}", base, e.getMessage());
        }
    }

    private static final String HOW_TO = """
            SETWEAVER - SET EFFECT CONFIGURATION
            Full documentation: https://github.com/gilfort/SetWeaver/wiki

            FOLDER STRUCTURE
              config/setweaver/set_armor/
                {role}/{level}/          one role and level
                all_roles/{level}/       all roles of one level
                all_roles_all_levels/    everyone (no role or level needed)

              The most specific folder wins for the same tag; sets are not merged.

            FILE NAMES
              {namespace}__{tagpath}.json is the item tag the set applies to,
              e.g. c__iron_armors.json -> #c:iron_armors

            JSON FORMAT
              {
                "displayName": "Example Armor Set",
                "parts": {
                  "2Part": {
                    "Effects": [ { "Effect": "minecraft:speed", "Amplifier": 0 } ],
                    "Attributes": [ { "attribute": "minecraft:generic.armor", "value": 2.0, "modifier": "addition" } ]
                  },
                  "4Part": { ... }
                }
              }

              The highest threshold that is reached is active and replaces the lower ones.
              Modifiers: addition, multiply_base, multiply_total. Amplifier 0 = level I.

            COMMANDS (operator level 2)
              /setweaver editor
              /setweaver set <player> <role> [<level>]
              /setweaver setyear <players> <level>
              /setweaver check <player>
            """;

    private static final String EXAMPLE = """
            {
              "displayName": "Example Armor Set",
              "parts": {
                "2Part": {
                  "Effects": [ { "Effect": "minecraft:speed", "Amplifier": 0 } ],
                  "Attributes": [ { "attribute": "minecraft:generic.armor", "value": 2.0, "modifier": "addition" } ]
                },
                "4Part": {
                  "Effects": [
                    { "Effect": "minecraft:speed", "Amplifier": 1 },
                    { "Effect": "minecraft:night_vision", "Amplifier": 0 }
                  ],
                  "Attributes": [
                    { "attribute": "minecraft:generic.armor", "value": 5.0, "modifier": "addition" },
                    { "attribute": "minecraft:generic.movement_speed", "value": 0.1, "modifier": "multiply_base" }
                  ]
                }
              }
            }
            """;
}
