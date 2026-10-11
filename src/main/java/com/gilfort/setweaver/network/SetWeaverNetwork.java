package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all packets. Client-bound packets get their handlers in {@code client/SetWeaverClient}, so a dedicated
 * server never loads client classes.
 */
@EventBusSubscriber(modid = SetWeaver.MODID)
public final class SetWeaverNetwork {

    /** Protocol version; bump when packets change incompatibly. */
    public static final String PROTOCOL_VERSION = "3";

    private SetWeaverNetwork() {
    }

    /**
     * Registers the packets.
     *
     * @param event the registration event
     */
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // Server → client
        registrar.playToClient(OpenSetsGuiPayload.TYPE, OpenSetsGuiPayload.STREAM_CODEC);
        registrar.playToClient(PlayerDataPayload.TYPE, PlayerDataPayload.STREAM_CODEC);
        registrar.playToClient(RegistrySyncPayload.TYPE, RegistrySyncPayload.STREAM_CODEC);
        registrar.playToClient(ValidationResultsPayload.TYPE, ValidationResultsPayload.STREAM_CODEC);

        // Client → server
        registrar.playToServer(SaveSetPayload.TYPE, SaveSetPayload.STREAM_CODEC, ServerPayloadHandler::handleSaveSet);
        registrar.playToServer(DeleteSetPayload.TYPE, DeleteSetPayload.STREAM_CODEC, ServerPayloadHandler::handleDeleteSet);
        registrar.playToServer(SaveTagPayload.TYPE, SaveTagPayload.STREAM_CODEC, ServerPayloadHandler::handleSaveTag);
        registrar.playToServer(SavePackagesPayload.TYPE, SavePackagesPayload.STREAM_CODEC, ServerPayloadHandler::handleSavePackages);
        registrar.playToServer(ValidateRequestPayload.TYPE, ValidateRequestPayload.STREAM_CODEC, ServerPayloadHandler::handleValidateRequest);
        registrar.playToServer(ReloadRequestPayload.TYPE, ReloadRequestPayload.STREAM_CODEC, ServerPayloadHandler::handleReloadRequest);
    }
}
