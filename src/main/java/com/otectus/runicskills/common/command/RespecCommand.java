package com.otectus.runicskills.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPassives;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.passive.Passive;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RespecCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                (Commands.literal("respec").requires(source -> source.hasPermission(2)))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(source -> respec(source, EntityArgument.getPlayer(source, "player")))
                )
        );
    }

    public static int respec(CommandContext<CommandSourceStack> source, ServerPlayer player) {
        if (player != null) {
            SkillCapability capability = SkillCapability.get(player);
            if (capability == null) {
                source.getSource().sendFailure(Component.translatable("commands.message.capability.not_found"));
                return 0;
            }

            for (Skill skill : RegistrySkills.getCachedValues()) {
                capability.setSkillLevel(skill, 1);
            }

            for (Passive passive : RegistryPassives.getCachedValues()) {
                capability.subPassiveLevel(passive, capability.getPassiveLevel(passive));
            }

            for (Perk perk : RegistryPerks.getCachedValues()) {
                capability.setPerkRank(perk, 0);
            }

            RegistryAttributes.modifierAttributes(player);
            SyncSkillCapabilityCP.send(player);

            source.getSource().sendSuccess(() -> Component.translatable("commands.message.respec.success", player.getName().copy().withStyle(ChatFormatting.BOLD)), false);

            return Command.SINGLE_SUCCESS;
        }

        return 0;
    }
}
