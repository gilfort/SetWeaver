package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.seteffects.ArmorSetData;
import com.gilfort.setweaver.seteffects.AttributePackageManager;
import com.gilfort.setweaver.seteffects.SetFiles;
import com.gilfort.setweaver.seteffects.SetWeaverReloadListener;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Handles client → server packets of the editor. Every handler runs on the server thread, checks the game master
 * permission (operator level 2)
 * and treats all input as untrusted.
 */
public final class ServerPayloadHandler {

    /** Name of the world datapack that receives tags created in the editor. */
    public static final String CUSTOM_DATAPACK = "setweaver_custom";

    private ServerPayloadHandler() {
    }

    /** Handles {@link SaveSetPayload}. */
    public static void handleSaveSet(SaveSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = authorized(context);
            if (sender == null) {
                return;
            }
            Optional<Path> target = SetFiles.resolveClientPath(payload.relativePath());
            if (target.isEmpty()) {
                fail(sender, "message.setweaver.invalid_path");
                return;
            }
            ArmorSetData data;
            try {
                data = SetWeaverReloadListener.GSON.fromJson(payload.jsonContent(), ArmorSetData.class);
            } catch (JsonParseException | IllegalStateException e) {
                fail(sender, "message.setweaver.invalid_set");
                return;
            }
            if (data == null || data.getParts() == null || data.getParts().isEmpty()) {
                fail(sender, "message.setweaver.invalid_set");
                return;
            }
            try {
                Files.createDirectories(target.get().getParent());
                Files.writeString(target.get(), SetWeaverReloadListener.GSON.toJson(data), StandardCharsets.UTF_8);
            } catch (IOException e) {
                SetWeaver.LOGGER.error("[SetWeaver] Failed to save set {}: {}", target.get(), e.getMessage());
                fail(sender, "message.setweaver.save_failed");
                return;
            }
            reloadAndSync(sender);
            success(sender, "message.setweaver.set_saved", payload.relativePath());
        });
    }

    /** Handles {@link DeleteSetPayload}. */
    public static void handleDeleteSet(DeleteSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = authorized(context);
            if (sender == null) {
                return;
            }
            Optional<Path> target = SetFiles.resolveClientPath(payload.relativePath());
            if (target.isEmpty()) {
                fail(sender, "message.setweaver.invalid_path");
                return;
            }
            try {
                if (!Files.deleteIfExists(target.get())) {
                    fail(sender, "message.setweaver.set_not_found");
                    return;
                }
            } catch (IOException e) {
                SetWeaver.LOGGER.error("[SetWeaver] Failed to delete set {}: {}", target.get(), e.getMessage());
                fail(sender, "message.setweaver.delete_failed");
                return;
            }
            reloadAndSync(sender);
            success(sender, "message.setweaver.set_deleted", payload.relativePath());
        });
    }

    /** Handles {@link SaveTagPayload}. */
    public static void handleSaveTag(SaveTagPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = authorized(context);
            if (sender == null) {
                return;
            }
            Identifier tagId = Identifier.tryBuild(payload.namespace(), payload.tagName());
            if (tagId == null || tagId.getPath().contains("..")) {
                fail(sender, "message.setweaver.invalid_tag");
                return;
            }
            Set<String> items = new LinkedHashSet<>();
            for (String raw : payload.items()) {
                Identifier itemId = Identifier.tryParse(raw);
                if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                    items.add(itemId.toString());
                }
            }
            if (items.isEmpty()) {
                fail(sender, "message.setweaver.tag_empty");
                return;
            }

            Path datapack = sender.level().getServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve(CUSTOM_DATAPACK).normalize();
            Path tagFile = datapack.resolve("data").resolve(tagId.getNamespace()).resolve("tags").resolve("item")
                    .resolve(tagId.getPath() + ".json").normalize();
            if (!tagFile.startsWith(datapack)) {
                fail(sender, "message.setweaver.invalid_tag");
                return;
            }
            try {
                Files.createDirectories(tagFile.getParent());
                Files.writeString(datapack.resolve("pack.mcmeta"), packMcmeta(), StandardCharsets.UTF_8);
                JsonObject tag = new JsonObject();
                tag.addProperty("replace", false);
                JsonArray values = new JsonArray();
                items.forEach(values::add);
                tag.add("values", values);
                Files.writeString(tagFile, tag.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                SetWeaver.LOGGER.error("[SetWeaver] Failed to save tag {}: {}", tagId, e.getMessage());
                fail(sender, "message.setweaver.tag_save_failed");
                return;
            }
            success(sender, "message.setweaver.tag_saved", tagId.toString());
        });
    }

    /** Handles {@link SavePackagesPayload}. */
    public static void handleSavePackages(SavePackagesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = authorized(context);
            if (sender == null) {
                return;
            }
            List<AttributePackageManager.AttributePackage> packages = new ArrayList<>();
            for (AttributePackageManager.AttributePackage pkg : AttributePackageManager.parseList(payload.packagesJson())) {
                if (pkg == null || pkg.getName() == null || pkg.getName().isBlank()) {
                    continue;
                }
                AttributePackageManager.AttributePackage clean = pkg.copy();
                clean.setName(pkg.getName().strip());
                clean.setAttributes(new ArrayList<>(pkg.getAttributes().stream()
                        .filter(id -> Identifier.tryParse(id) != null).distinct().toList()));
                packages.add(clean);
            }
            if (!AttributePackageManager.saveAndReload(packages)) {
                fail(sender, "message.setweaver.save_failed");
                return;
            }
            SetWeaverSync.broadcastRegistry(sender.level().getServer());
            success(sender, "message.setweaver.packages_saved", packages.size());
        });
    }

    /** Handles {@link ValidateRequestPayload}. */
    public static void handleValidateRequest(ValidateRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = authorized(context);
            if (sender == null) {
                return;
            }
            var results = SetWeaverReloadListener.validateAllFiles();
            PacketDistributor.sendToPlayer(sender, new ValidationResultsPayload(
                    results.subList(0, Math.min(results.size(), ValidationResultsPayload.MAX_RESULTS))));
        });
    }

    /** Handles {@link ReloadRequestPayload}. */
    public static void handleReloadRequest(ReloadRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = authorized(context);
            if (sender == null) {
                return;
            }
            reloadAndSync(sender);
            success(sender, "message.setweaver.reloaded");
        });
    }

    private static ServerPlayer authorized(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return player;
        }
        if (context.player() instanceof ServerPlayer player) {
            fail(player, "message.setweaver.permission_denied");
        }
        return null;
    }

    private static void reloadAndSync(ServerPlayer sender) {
        SetWeaverReloadListener.loadAllEffects();
        SetWeaverSync.broadcastRegistry(sender.level().getServer());
    }

    private static String packMcmeta() {
        PackFormat format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        JsonArray version = new JsonArray();
        version.add(format.major());
        version.add(format.minor());
        JsonObject pack = new JsonObject();
        pack.add("min_format", version);
        pack.add("max_format", version);
        pack.addProperty("description", "SetWeaver custom tags");
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root.toString();
    }

    private static void success(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.GREEN));
    }

    private static void fail(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
    }
}
