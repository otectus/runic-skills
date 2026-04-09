package com.otectus.runicskills.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class ListSkillsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                (Commands.literal("listskills").requires(source -> source.hasPermission(2)))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(source -> listSkills(source, EntityArgument.getPlayer(source, "player")))
                )
        );
    }

    public static int listSkills(CommandContext<CommandSourceStack> source, ServerPlayer player) {
        if (player != null) {
            SkillCapability capability = SkillCapability.get(player);
            if (capability == null) {
                source.getSource().sendFailure(Component.translatable("commands.message.capability.not_found"));
                return 0;
            }

            List<Skill> skills = RegistrySkills.getCachedValues();
            MutableComponent skillList = Component.empty();

            for (int i = 0; i < skills.size(); i++) {
                Skill skill = skills.get(i);
                int level = capability.getSkillLevel(skill);

                skillList.append(Component.translatable(skill.getKey()).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.valueOf(level)).withStyle(ChatFormatting.WHITE));

                if (i < skills.size() - 1) {
                    skillList.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
            }

            int globalLevel = capability.getGlobalLevel();

            source.getSource().sendSuccess(() -> Component.translatable("commands.message.listskills.header", player.getName().copy().withStyle(ChatFormatting.BOLD)), false);
            source.getSource().sendSuccess(() -> skillList, false);
            source.getSource().sendSuccess(() -> Component.translatable("commands.message.listskills.global", Component.literal(String.valueOf(globalLevel)).withStyle(ChatFormatting.BOLD)), false);

            return Command.SINGLE_SUCCESS;
        }

        return 0;
    }
}
