package com.gilfort.setweaver.command;

import com.gilfort.setweaver.SetWeaver;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers the {@code /setweaver} command.
 */
@EventBusSubscriber(modid = SetWeaver.MODID)
public final class CommandRegistry {

    private CommandRegistry() {
    }

    /**
     * Registers the commands.
     *
     * @param event the registration event
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandHandler.registerCommands(event.getDispatcher());
    }
}
