package com.gilfort.zauberei.network;

import com.gilfort.zauberei.Zauberei;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Zauberei.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ZaubereiNetwork {

    @SubscribeEvent
    public static void registerPayload(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                LetterButtonPayload.TYPE,
                LetterButtonPayload.STREAM_CODEC,
                ServerPayloadHandler::handleIntroductionLetter
        );

        registrar.playToClient(
                OpenSetsGuiPayload.TYPE,
                OpenSetsGuiPayload.STREAM_CODEC,
                ClientPayloadHandler::handleOpenSetsGui
        );

        System.out.println("ZaubereiNetwork: Registering payload handler for LetterButtonPayload");

    }
}
