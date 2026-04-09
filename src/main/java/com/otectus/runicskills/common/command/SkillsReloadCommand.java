package com.otectus.runicskills.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.runicskills.handler.HandlerSkill;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class SkillsReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((Commands.literal("skillsreload").requires((source) -> {
            return source.hasPermission(2);
        })).executes(SkillsReloadCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> command){
        HandlerSkill.ForceRefresh();

        if(command.getSource().getEntity() instanceof Player player) {
            player.sendSystemMessage(Component.literal("Forcing refresh of skills..."));
        }

        return Command.SINGLE_SUCCESS;
    }
}
