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
                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                .executes(GlobalLimitCommand::execute)
                        )
        ));
    }

    private static int execute(CommandContext<CommandSourceStack> command) {
        int globalLimitLevel = command.getArgument("level", Integer.class);

        // local(), not instance(): this edits the server's own config file, and instance() may be
        // serving a snapshot on an integrated server (RS10-005).
        HandlerCommonConfig.HANDLER.local().playersMaxGlobalLevel = globalLimitLevel;
        HandlerCommonConfig.HANDLER.save();

        GameplayConfigCP.sendToAllPlayers();
        command.getSource().sendSystemMessage(Component.literal(String.format("Updating playersMaxGlobalLevel, new level: %d", globalLimitLevel)));

        return Command.SINGLE_SUCCESS;
    }

}
