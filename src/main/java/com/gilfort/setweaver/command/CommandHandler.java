package com.gilfort.setweaver.command;


import com.gilfort.setweaver.datagen.PlayerDataHelper;
import com.gilfort.setweaver.network.OpenSetsGuiPayload;
import com.gilfort.setweaver.network.PlayerDataPayload;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Command handler for the SetWeaver mod.
 *
 * <h3>Command Tree:</h3>
 * <pre>
 * /setweaver
 *   ├── editor                              Open the Sets Manager GUI (OP 2+)
 *   ├── set &lt;player&gt; &lt;role&gt; &lt;year&gt;        Set role + year for a player (OP 2+)
 *   └── check &lt;player&gt;                     Show role + year of a player (OP 2+)
 * </pre>
 */
public class CommandHandler {

    // ═══════════════════════════════════════════════════════════════════════
    //  SUGGESTION PROVIDERS
    // ═══════════════════════════════════════════════════════════════════════

    /** Suggests all real role names from the registry (excludes wildcard). */
    public static final SuggestionProvider<CommandSourceStack> ROLE_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ArmorSetDataRegistry.getRoles(), builder);

    /** Suggests all currently online player names. */
    public static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (ctx, builder) -> {
        List<String> names = ctx.getSource().getServer().getPlayerList().getPlayers()
                .stream().map(p -> p.getGameProfile().getName()).toList();
        return SharedSuggestionProvider.suggest(names, builder);
    };

    // ═══════════════════════════════════════════════════════════════════════
    //  COMMAND REGISTRATION
    // ═══════════════════════════════════════════════════════════════════════

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("setweaver")

                        // ── editor ───────────────────────────────────────────
                        .then(Commands.literal("editor")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> openSetsGui(ctx.getSource())))

                        // ── set <player> <role> <level> ───────────────────────
                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(PLAYER_SUGGESTIONS)
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .suggests(ROLE_SUGGESTIONS)
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                        .executes(CommandHandler::setPlayerDataCommand)))))

                        // ── check <player> ───────────────────────────────────
                        .then(Commands.literal("check")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(PLAYER_SUGGESTIONS)
                                        .executes(ctx -> checkPlayer(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "target")))))
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  COMMAND IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Opens the Sets Manager GUI for the executing player.
     * Sends a network packet to the client to trigger the screen.
     */
    private static int openSetsGui(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        PacketDistributor.sendToPlayer(player, new OpenSetsGuiPayload());

        source.sendSuccess(
                () -> Component.literal("[SetWeaver] Opening Sets Manager..."), false);
        return 1;
    }

    /**
     * Sets the role and level for a target player.
     * Usage: /setweaver set &lt;player&gt; &lt;role&gt; &lt;level&gt;
     */
    private static int setPlayerDataCommand(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "target");
        String role = StringArgumentType.getString(context, "role");
        int level = IntegerArgumentType.getInteger(context, "level");
        CommandSourceStack source = context.getSource();

        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player '" + targetName + "' not found or not online."));
            return 0;
        }

        PlayerDataHelper.setRole(target, role);
        PlayerDataHelper.setLevel(target, level);

        // Sync the updated role/level to the target player's client
        PacketDistributor.sendToPlayer(target, new PlayerDataPayload(role, level));

        source.sendSuccess(() -> Component.literal("[SetWeaver] ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(targetName).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Role: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(role).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  Level: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(level)).withStyle(ChatFormatting.YELLOW)), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Displays the current role and level of a target player.
     * Usage: /setweaver check &lt;player&gt;
     */
    private static int checkPlayer(CommandSourceStack source, String playerName) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Player '" + playerName + "' not found or not online."));
            return 0;
        }

        String role = PlayerDataHelper.getRole(target);
        int level = PlayerDataHelper.getLevel(target);

        source.sendSystemMessage(Component.literal("[SetWeaver] ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(playerName).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Role: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(role.isEmpty() ? "(not set)" : role).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  Level: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(level == 0 ? "(not set)" : String.valueOf(level)).withStyle(ChatFormatting.YELLOW)));
        return 1;
    }
}
