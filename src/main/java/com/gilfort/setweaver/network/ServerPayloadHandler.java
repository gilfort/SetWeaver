package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.AttributePackageManager;
import com.gilfort.setweaver.seteffects.SetWeaverReloadListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles payloads received on the SERVER side.
 * All operations validate permissions and sanitize paths before executing.
 */
public class ServerPayloadHandler {

    private static final Path SET_ARMOR_DIR = FMLPaths.CONFIGDIR.get()
            .resolve("setweaver")
            .resolve("set_armor");

    /**
     * Handles a save-set request from a client.
     * Validates OP permission, sanitizes the path to prevent directory traversal,
     * writes the JSON to disk, reloads the registry, and broadcasts the update.
     */
    public static void handleSaveSet(final SaveSetPayload payload,
                                     final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }

            // Permission check: require OP level 2
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Permission denied."));
                SetWeaver.LOGGER.warn("Player {} tried to save a set without permission",
                        sender.getName().getString());
                return;
            }

            // Sanitize path: prevent directory traversal attacks
            String relativePath = payload.relativePath();
            if (relativePath.contains("..") || relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Invalid file path."));
                SetWeaver.LOGGER.warn("Player {} sent invalid save path: {}",
                        sender.getName().getString(), relativePath);
                return;
            }

            Path targetFile = SET_ARMOR_DIR.resolve(relativePath).normalize();

            // Double-check the resolved path is still within the set_armor directory
            if (!targetFile.startsWith(SET_ARMOR_DIR)) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Invalid file path."));
                SetWeaver.LOGGER.warn("Player {} tried path traversal: {}",
                        sender.getName().getString(), targetFile);
                return;
            }

            try {
                // Create parent directories if needed
                Files.createDirectories(targetFile.getParent());

                // Write the JSON file
                Files.writeString(targetFile, payload.jsonContent());

                SetWeaver.LOGGER.info("Player {} saved set definition to {}",
                        sender.getName().getString(), relativePath);

                // Reload all set definitions from disk
                SetWeaverReloadListener.loadAllEffects();

                // Broadcast updated registry to all connected clients
                String registryJson = ArmorSetDataRegistry.serializeToJson();
                String packagesJson = AttributePackageManager.serializeToJson();
                sender.getServer().getPlayerList().getPlayers().forEach(player ->
                        PacketDistributor.sendToPlayer(player, new RegistrySyncPayload(registryJson, packagesJson))
                );

                sender.sendSystemMessage(Component.literal("[SetWeaver] Set saved and synced successfully."));

            } catch (IOException e) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Save failed: " + e.getMessage()));
                SetWeaver.LOGGER.error("Failed to save set definition for player {}: {}",
                        sender.getName().getString(), e.getMessage());
            }
        });
    }

    // ─── Tag Creation ────────────────────────────────────────────────────

    private static final String DATAPACK_NAME = "setweaver_custom";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Handles a save-tag request from a client.
     * Creates a custom item tag inside a SetWeaver-managed datapack in the world save.
     *
     * <p>Datapack structure:<br>
     * {@code <world>/datapacks/setweaver_custom/data/<namespace>/tags/item/<tagName>.json}</p>
     *
     * <p>After saving, the player must run {@code /reload} to make the tag available.</p>
     */
    public static void handleSaveTag(final SaveTagPayload payload,
                                     final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;

            // Permission check
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Permission denied."));
                return;
            }

            String namespace = payload.namespace().trim().toLowerCase();
            String tagName = payload.tagName().trim().toLowerCase();

            // Validate namespace and tag name (only allow safe characters)
            if (!namespace.matches("[a-z0-9_.-]+") || !tagName.matches("[a-z0-9_./-]+")) {
                sender.sendSystemMessage(Component.literal(
                        "[SetWeaver] Invalid tag name. Use only lowercase letters, numbers, underscores, dots, hyphens."));
                return;
            }

            // Prevent directory traversal
            if (tagName.contains("..")) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Invalid tag name."));
                return;
            }

            try {
                // Parse items JSON array
                JsonArray itemsArray = JsonParser.parseString(payload.itemsJson()).getAsJsonArray();

                // Build tag JSON: {"replace": false, "values": [...]}
                JsonObject tagJson = new JsonObject();
                tagJson.addProperty("replace", false);
                tagJson.add("values", itemsArray);

                // Resolve world datapack path
                Path worldDir = sender.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                Path datapackDir = worldDir.resolve("datapacks").resolve(DATAPACK_NAME);

                // Ensure pack.mcmeta exists
                ensurePackMcmeta(datapackDir);

                // Write tag file
                Path tagFile = datapackDir
                        .resolve("data")
                        .resolve(namespace)
                        .resolve("tags")
                        .resolve("item")
                        .resolve(tagName + ".json");

                Files.createDirectories(tagFile.getParent());
                Files.writeString(tagFile, GSON.toJson(tagJson));

                SetWeaver.LOGGER.info("Player {} created custom tag {}:{} with {} items",
                        sender.getName().getString(), namespace, tagName, itemsArray.size());

                sender.sendSystemMessage(Component.literal(
                        "[SetWeaver] Tag '" + namespace + ":" + tagName + "' saved. Run /reload to apply."));

            } catch (Exception e) {
                sender.sendSystemMessage(Component.literal("[SetWeaver] Failed to save tag: " + e.getMessage()));
                SetWeaver.LOGGER.error("Failed to save custom tag for player {}: {}",
                        sender.getName().getString(), e.getMessage());
            }
        });
    }

    /**
     * Ensures the SetWeaver custom datapack has a valid pack.mcmeta file.
     */
    private static void ensurePackMcmeta(Path datapackDir) throws IOException {
        Path mcmeta = datapackDir.resolve("pack.mcmeta");
        if (!Files.exists(mcmeta)) {
            Files.createDirectories(datapackDir);
            JsonObject pack = new JsonObject();
            JsonObject packInfo = new JsonObject();
            packInfo.addProperty("description", "SetWeaver custom tags");
            packInfo.addProperty("pack_format", 48); // MC 1.21.x
            pack.add("pack", packInfo);
            Files.writeString(mcmeta, GSON.toJson(pack));
        }
    }
}
