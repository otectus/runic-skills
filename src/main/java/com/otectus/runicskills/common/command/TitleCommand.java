package com.otectus.runicskills.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.command.arguments.TitleArgument;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryTitles;
import com.otectus.runicskills.registry.title.Title;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


public class TitleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((Commands.literal("titles")
                .requires(source -> source.hasPermission(2)))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("title", TitleArgument.getArgument())
                                .then((Commands.literal("set")
                                        .then(Commands.literal("true")
                                                .executes(source -> setTitle(source, EntityArgument.getPlayer(source, "player"), TitleArgument.getTitle(source, "title"), true))))
                                        .then(Commands.literal("false")
                                                .executes(source -> setTitle(source, EntityArgument.getPlayer(source, "player"), TitleArgument.getTitle(source, "title"), false)))))));
    }


    public static int setTitle(CommandContext<CommandSourceStack> source, ServerPlayer player, ResourceLocation titleKey, boolean set) {
        Title title = RegistryTitles.TITLES_REGISTRY.get().getValue(titleKey);
        if (player != null && title != null) {
            if (set) {
                title.setRequirement(player, true);
                source.getSource().sendSuccess(() -> Component.translatable("commands.message.title.set", player.getName().copy().withStyle(ChatFormatting.BOLD), Component.translatable(title.getKey()).withStyle(ChatFormatting.BOLD)), false);

            } else {

                SkillCapability capability = SkillCapability.get(player);
                if (capability == null) return 0;
                capability.setUnlockTitle(title, false);
                SyncSkillCapabilityCP.send(player);
                source.getSource().sendSuccess(() -> Component.translatable("commands.message.title.unset", player.getName().copy().withStyle(ChatFormatting.BOLD), Component.translatable(title.getKey()).withStyle(ChatFormatting.BOLD)), false);
            }


            return 1;
        }

        return 0;
    }
}


