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
import net.minecraft.world.entity.player.Player;

public class UpdateSkillLevelCommand {

    public static LiteralCommandNode<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register((
                Commands.literal("updateskilllevel")
                        .requires((source) -> source.hasPermission(2))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                .executes(UpdateSkillLevelCommand::execute)
                        )

        ));
    }

    private static int execute(CommandContext<CommandSourceStack> command) {
        if(command.getSource().getEntity() != null
                && command.getSource().getEntity() instanceof Player){
            command.getSource().sendFailure(Component.literal("This command can only be run from the server console or a command block, not by a player."));
            return 0; // rejected — report failure, not SINGLE_SUCCESS
        }

        int levelLimit = command.getArgument("level", Integer.class);

        // local(), not instance(): this edits the server's own config file, and instance() may be
        // serving a snapshot on an integrated server (RS10-005).
        HandlerCommonConfig.HANDLER.local().skillMaxLevel = levelLimit;
        HandlerCommonConfig.HANDLER.save();

        GameplayConfigCP.sendToAllPlayers();
        command.getSource().sendSystemMessage(Component.literal(String.format("Updating skillMaxLevel, new level: %d", levelLimit)));

        return Command.SINGLE_SUCCESS;
    }
}
