package com.gilfort.setweaver.command;

import com.gilfort.setweaver.network.OpenSetsGuiPayload;
import com.gilfort.setweaver.network.ServerPayloadHandler;
import com.gilfort.setweaver.network.SetWeaverSync;
import com.gilfort.setweaver.seteffects.ArmorSetDataRegistry;
import com.gilfort.setweaver.seteffects.SetFiles;
import com.gilfort.setweaver.util.PlayerDataHelper;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;

/**
 * The {@code /setweaver} command tree (all subcommands need operator level 2):
 * <pre>
 * /setweaver editor                          open the Sets Manager
 * /setweaver set &lt;player&gt; &lt;role&gt; [&lt;level&gt;]   set role (and level)
 * /setweaver setyear &lt;players&gt; &lt;level&gt;      set only the level
 * /setweaver check &lt;player&gt;                  show role and level
 * </pre>
 */
public final class CommandHandler {

    /** Suggests the roles used by loaded sets (without the wildcard). */
    public static final SuggestionProvider<CommandSourceStack> ROLE_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ArmorSetDataRegistry.server().getRoles(), builder);

    private static final DynamicCommandExceptionType INVALID_ROLE = new DynamicCommandExceptionType(
            role -> Component.translatable("message.setweaver.command.invalid_role", role));

    private CommandHandler() {
    }

    /**
     * Registers the command tree.
     *
     * @param dispatcher the command dispatcher
     */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setweaver")
                .requires(src -> src.hasPermission(ServerPayloadHandler.PERMISSION_LEVEL))
                .then(Commands.literal("editor")
                        .executes(ctx -> openEditor(ctx.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests(ROLE_SUGGESTIONS)
                                        .executes(ctx -> setRole(ctx, null))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                .executes(ctx -> setRole(ctx, IntegerArgumentType.getInteger(ctx, "level")))))))
                .then(Commands.literal("setyear")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(CommandHandler::setLevel))))
                .then(Commands.literal("check")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(CommandHandler::check))));
    }

    private static int openEditor(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PacketDistributor.sendToPlayer(player, new OpenSetsGuiPayload());
        return Command.SINGLE_SUCCESS;
    }

    private static int setRole(CommandContext<CommandSourceStack> context, Integer level) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        String role = ArmorSetDataRegistry.normalizeRole(StringArgumentType.getString(context, "role"));
        if (!SetFiles.isValidRole(role)) {
            throw INVALID_ROLE.create(role);
        }
        PlayerDataHelper.setRole(target, role);
        if (level != null) {
            PlayerDataHelper.setLevel(target, level);
        }
        SetWeaverSync.sendPlayerData(target);

        int newLevel = PlayerDataHelper.getLevel(target);
        context.getSource().sendSuccess(() -> Component.translatable("message.setweaver.command.set",
                target.getDisplayName(), role, newLevel).withStyle(ChatFormatting.AQUA), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int level = IntegerArgumentType.getInteger(context, "level");
        for (ServerPlayer target : targets) {
            PlayerDataHelper.setLevel(target, level);
            SetWeaverSync.sendPlayerData(target);
            context.getSource().sendSuccess(() -> Component.translatable("message.setweaver.command.setyear",
                    target.getDisplayName(), level).withStyle(ChatFormatting.AQUA), true);
        }
        return targets.size();
    }

    private static int check(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        String role = PlayerDataHelper.getRole(target);
        int level = PlayerDataHelper.getLevel(target);
        Component roleText = role.isEmpty() ? Component.translatable("message.setweaver.command.not_set") : Component.literal(role);
        Component levelText = level == 0 ? Component.translatable("message.setweaver.command.not_set") : Component.literal(String.valueOf(level));
        context.getSource().sendSuccess(() -> Component.translatable("message.setweaver.command.check",
                target.getDisplayName(), roleText, levelText).withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }
}
