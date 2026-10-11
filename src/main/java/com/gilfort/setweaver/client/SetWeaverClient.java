package com.gilfort.setweaver.client;

import com.gilfort.setweaver.SetWeaver;
import com.gilfort.setweaver.network.OpenSetsGuiPayload;
import com.gilfort.setweaver.network.PlayerDataPayload;
import com.gilfort.setweaver.network.RegistrySyncPayload;
import com.gilfort.setweaver.network.ValidationResultsPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Client-only registrations. Only loaded on the physical client.
 */
@EventBusSubscriber(modid = SetWeaver.MODID, value = Dist.CLIENT)
public final class SetWeaverClient {

    private SetWeaverClient() {
    }

    /**
     * Registers the handlers of the client-bound packets declared in {@code SetWeaverNetwork}.
     *
     * @param event the registration event
     */
    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(OpenSetsGuiPayload.TYPE, ClientHooks::handleOpenSetsGui);
        event.register(PlayerDataPayload.TYPE, ClientHooks::handlePlayerData);
        event.register(RegistrySyncPayload.TYPE, ClientHooks::handleRegistrySync);
        event.register(ValidationResultsPayload.TYPE, ClientHooks::handleValidationResults);
    }
}
