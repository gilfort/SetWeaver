package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = SetWeaver.MODID)
public class SetWeaverNetwork {

    @SubscribeEvent
    public static void registerPayload(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Use lambdas instead of method references to avoid eager class-loading
        // of ClientPayloadHandler (which imports client-only classes like Screen)
        // on the dedicated server during payload registration.
        registrar.playToClient(
                OpenSetsGuiPayload.TYPE,
                OpenSetsGuiPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleOpenSetsGui(payload, context)
        );

        registrar.playToClient(
                PlayerDataPayload.TYPE,
                PlayerDataPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handlePlayerData(payload, context)
        );

        registrar.playToClient(
                RegistrySyncPayload.TYPE,
                RegistrySyncPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleRegistrySync(payload, context)
        );

        // Client → Server: save a set definition
        registrar.playToServer(
                SaveSetPayload.TYPE,
                SaveSetPayload.STREAM_CODEC,
                ServerPayloadHandler::handleSaveSet
        );
    }
}
