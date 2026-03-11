package com.gilfort.setweaver.network;

import com.gilfort.setweaver.SetWeaver;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = SetWeaver.MODID)
public class ZaubereiNetwork {

    @SubscribeEvent
    public static void registerPayload(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                OpenSetsGuiPayload.TYPE,
                OpenSetsGuiPayload.STREAM_CODEC,
                ClientPayloadHandler::handleOpenSetsGui
        );
    }
}
