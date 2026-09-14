package com.otectus.runicskills.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.client.GameplayConfigCP;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class GlobalLimitCommand {

    public static LiteralCommandNode<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register((
                Commands.literal("globallimit")
                        .requires((source) -> source.hasPermission(2))
                        .then(Commands.argument("level", IntegerArgumentType.integer(32, 99999))
                                .executes(GlobalLimitCommand::execute)
                        )
        ));
    }

    private static int execute(CommandContext<CommandSourceStack> command) {
        int globalLimitLevel = command.getArgument("level", Integer.class);

        // local(), not instance(): this edits the server's own config file, and instance() may be
        // serving a snapshot on an integrated server (RS10-005).
        var holder = HandlerCommonConfig.HANDLER;
        try {
            var session = holder.beginEdit();
            session.draft().playersMaxGlobalLevel = globalLimitLevel;
            session.draft().globalLevelCapMode = "custom";
            var result = holder.commit(session, session.draft());
            if (!result.success()) {
                command.getSource().sendFailure(Component.literal(result.message()));
                return 0;
            }
        } catch (com.otectus.runicskills.config.storage.ConfigHolder.EditException failure) {
            command.getSource().sendFailure(Component.literal(failure.getMessage())); return 0;
        }
        SkillsReloadCommand.reload(command.getSource().getServer());
        command.getSource().sendSystemMessage(Component.literal(String.format("Updating playersMaxGlobalLevel, new level: %d", globalLimitLevel)));

        return Command.SINGLE_SUCCESS;
    }

}
