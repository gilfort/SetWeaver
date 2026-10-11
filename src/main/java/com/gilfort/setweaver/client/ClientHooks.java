package com.gilfort.setweaver.client;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.client.gui.SetBrowserScreen;
import com.gilfort.setweaver.network.OpenSetsGuiPayload;
import com.gilfort.setweaver.network.PlayerDataPayload;
import com.gilfort.setweaver.network.RegistrySyncPayload;
import com.gilfort.setweaver.network.ValidationResultsPayload;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.AttributePackageManager;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client-only entry points called from common code (packet handlers). Callers must only invoke these on the
 * client, so this class is never loaded on a dedicated server.
 */
public final class ClientHooks {

    private ClientHooks() {
    }

    /** Opens the set browser. */
    public static void handleOpenSetsGui(OpenSetsGuiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().gui.setScreen(new SetBrowserScreen()));
    }

    /** Stores the local player's role and level. */
    public static void handlePlayerData(PlayerDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPlayerDataCache.update(payload.role(), payload.level()));
    }

    /** Replaces the client copies of set definitions and packages and refreshes an open browser. */
    public static void handleRegistrySync(RegistrySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ArmorSetDataRegistry.client().deserializeFromJson(payload.registryJson());
            AttributePackageManager.client().deserializeFromJson(payload.packagesJson());
            SetWeaver.LOGGER.debug("[SetWeaver] Received {} set definition(s)", ArmorSetDataRegistry.client().getAllEntries().size());
            SetBrowserScreen browser = SetBrowserScreen.current();
            if (browser != null) {
                browser.onRegistrySynced();
            }
        });
    }

    /** Shows server validation results in an open browser. */
    public static void handleValidationResults(ValidationResultsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            SetBrowserScreen browser = SetBrowserScreen.current();
            if (browser != null) {
                browser.onValidationResults(payload.results());
            }
        });
    }

    /** Clears all data received from a server. */
    public static void resetClientState() {
        ClientPlayerDataCache.reset();
        ArmorSetDataRegistry.client().clear();
        AttributePackageManager.client().setPackages(List.of());
        SetBrowserScreen.forget();
    }
}
