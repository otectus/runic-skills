package com.otectus.runicskills.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.command.arguments.SkillArgument;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SkillLevelCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                (Commands.literal("skills").requires(source -> source.hasPermission(2)))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(
                                Commands.argument("skill", SkillArgument.getArgument())
                                .then(Commands.literal("get")
                                        .executes(source -> getSkill(source, EntityArgument.getPlayer(source, "player"), source.getArgument("skill", String.class)))
                                )
                                .then(Commands.literal("set")
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, HandlerCommonConfig.HANDLER.instance().skillMaxLevel))
                                            .executes(source -> setSkill(source, EntityArgument.getPlayer(source, "player"), source.getArgument("skill", String.class), IntegerArgumentType.getInteger(source, "level")))
                                        )
                                )
                                .then(Commands.literal(("add"))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, HandlerCommonConfig.HANDLER.instance().skillMaxLevel))
                                                .executes(source -> addSkill(source, EntityArgument.getPlayer(source, "player"), source.getArgument("skill", String.class), IntegerArgumentType.getInteger(source, "level"))))
                                )
                                .then(Commands.literal(("subtract"))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, HandlerCommonConfig.HANDLER.instance().skillMaxLevel))
                                                .executes(source -> subtractSkill(source, EntityArgument.getPlayer(source, "player"), source.getArgument("skill", String.class), IntegerArgumentType.getInteger(source, "level")))
                                        )
                                )
                        )
                )
        );
    }


    public static int getSkill(CommandContext<CommandSourceStack> source, ServerPlayer player, String skillKey) {
        Skill skill = RegistrySkills.getSkill(skillKey);

        if (player != null && skill != null) {
            SkillCapability capability = SkillCapability.get(player);
            if (capability == null) {
                source.getSource().sendFailure(Component.translatable("commands.message.capability.not_found"));
                return 0;
            }

            source.getSource().sendSuccess(() -> Component.translatable("commands.message.skill.get", player.getName().copy().withStyle(ChatFormatting.BOLD), Component.literal(String.valueOf(capability.getSkillLevel(skill))).withStyle(ChatFormatting.BOLD), Component.translatable(skill.getKey()).withStyle(ChatFormatting.BOLD)), false);

            return Command.SINGLE_SUCCESS;
        }


        return 0;
    }

    public static int setSkill(CommandContext<CommandSourceStack> source, ServerPlayer player, String skillKey, int setLevel) {
        Skill skill = RegistrySkills.getSkill(skillKey);

        if (player != null && skill != null) {
            SkillCapability capability = SkillCapability.get(player);
            if (capability == null) {
                source.getSource().sendFailure(Component.translatable("commands.message.capability.not_found"));
                return 0;
            }
            capability.setSkillLevel(skill, setLevel);
            SyncSkillCapabilityCP.send(player);

            source.getSource().sendSuccess(() -> Component.translatable("commands.message.skill.set", player.getName().copy().withStyle(ChatFormatting.BOLD), Component.literal(String.valueOf(capability.getSkillLevel(skill))).withStyle(ChatFormatting.BOLD), Component.translatable(skill.getKey()).withStyle(ChatFormatting.BOLD)), false);


            return Command.SINGLE_SUCCESS;
        }

        return 0;
    }

    public static int addSkill(CommandContext<CommandSourceStack> source, ServerPlayer player, String skillKey, int addLevel) {
        Skill skill = RegistrySkills.getSkill(skillKey);

        if (player != null && skill != null) {
            SkillCapability capability = SkillCapability.get(player);
            if (capability == null) {
                source.getSource().sendFailure(Component.translatable("commands.message.capability.not_found"));
                return 0;
            }
            int actualLevel = capability.getSkillLevel(skill);

            capability.setSkillLevel(skill,
                    (actualLevel + addLevel) > HandlerCommonConfig.HANDLER.instance().skillMaxLevel
                    ? HandlerCommonConfig.HANDLER.instance().skillMaxLevel : (actualLevel + addLevel));

            SyncSkillCapabilityCP.send(player);

            source.getSource().sendSuccess(() -> Component.translatable("commands.message.skill.set", player.getName().copy().withStyle(ChatFormatting.BOLD), Component.literal(String.valueOf(capability.getSkillLevel(skill))).withStyle(ChatFormatting.BOLD), Component.translatable(skill.getKey()).withStyle(ChatFormatting.BOLD)), false);


            return Command.SINGLE_SUCCESS;
        }

        return 0;
    }

    public static int subtractSkill(CommandContext<CommandSourceStack> source, ServerPlayer player, String skillKey, int subtractLevel) {
        Skill skill = RegistrySkills.getSkill(skillKey);

        if (player != null && skill != null) {
            SkillCapability capability = SkillCapability.get(player);
            if (capability == null) {
                source.getSource().sendFailure(Component.translatable("commands.message.capability.not_found"));
                return 0;
            }
            int actualLevel = capability.getSkillLevel(skill);

            capability.setSkillLevel(skill,
                    Math.max((actualLevel - subtractLevel), 1));

            SyncSkillCapabilityCP.send(player);

            source.getSource().sendSuccess(() -> Component.translatable("commands.message.skill.set", player.getName().copy().withStyle(ChatFormatting.BOLD), Component.literal(String.valueOf(capability.getSkillLevel(skill))).withStyle(ChatFormatting.BOLD), Component.translatable(skill.getKey()).withStyle(ChatFormatting.BOLD)), false);

            return Command.SINGLE_SUCCESS;
        }

        return 0;
    }
}


