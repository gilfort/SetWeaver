package com.gilfort.setweaver.client;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.client.gui.PackageEditorScreen;
import com.gilfort.setweaver.client.gui.SetsManagerScreen;
import com.gilfort.setweaver.network.OpenSetsGuiPayload;
import com.gilfort.setweaver.network.PlayerDataPayload;
import com.gilfort.setweaver.network.RegistrySyncPayload;
import com.gilfort.setweaver.network.ValidationResultsPayload;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.AttributePackageManager;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-only entry points called from common code (packet handlers). Callers must only invoke these on the
 * client, so this class is never loaded on a dedicated server.
 */
public final class ClientHooks {

    private ClientHooks() {
    }

    /** Opens the Sets Manager. */
    public static void handleOpenSetsGui(OpenSetsGuiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new SetsManagerScreen()));
    }

    /** Stores the local player's role and level. */
    public static void handlePlayerData(PlayerDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPlayerDataCache.update(payload.role(), payload.level()));
    }

    /** Replaces the client copies of set definitions and packages and refreshes open editor screens. */
    public static void handleRegistrySync(RegistrySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ArmorSetDataRegistry.client().deserializeFromJson(payload.registryJson());
            AttributePackageManager.client().deserializeFromJson(payload.packagesJson());
            SetWeaver.LOGGER.debug("[SetWeaver] Received {} set definition(s)",
                    ArmorSetDataRegistry.client().getAllEntries().size());
            var screen = Minecraft.getInstance().screen;
            if (screen instanceof SetsManagerScreen manager) {
                manager.onRegistrySynced();
            } else if (screen instanceof PackageEditorScreen packages) {
                packages.onRegistrySynced();
            }
        });
    }

    /** Shows server validation results in an open Sets Manager. */
    public static void handleValidationResults(ValidationResultsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof SetsManagerScreen manager) {
                manager.showValidationResults(payload.results());
            }
        });
    }

    /** Clears all data received from a server. */
    public static void resetClientState() {
        ClientPlayerDataCache.reset();
        ArmorSetDataRegistry.client().clear();
        AttributePackageManager.client().setPackages(java.util.List.of());
    }
}
