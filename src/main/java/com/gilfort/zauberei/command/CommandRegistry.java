package com.gilfort.zauberei.command;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber()
public class CommandRegistry {

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event){
        CommandHandler.registerCommands(event.getDispatcher());
    }

}
