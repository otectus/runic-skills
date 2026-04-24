package com.otectus.runicskills.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.network.packet.client.CommonConfigSyncCP;
import com.otectus.runicskills.network.packet.client.ConfigSyncCP;
import com.otectus.runicskills.network.packet.client.DynamicConfigSyncCP;
import com.otectus.runicskills.network.packet.client.PerkGroupsSyncCP;
import com.otectus.runicskills.registry.RegistryAttributes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class SkillsReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((Commands.literal("skillsreload").requires((source) -> {
            return source.hasPermission(2);
        })).executes(SkillsReloadCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> command){
        HandlerSkill.ForceRefresh();

        // Re-sync to every connected client. Without this, the lock-items list
        // is only refreshed server-side; clients keep their stale cache (which
        // InteractionEventHandler also consults, since events fire on both sides)
        // until they relog.
        MinecraftServer server = command.getSource().getServer();
        if (server != null) {
            ConfigSyncCP.sendToAllPlayers();
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                CommonConfigSyncCP.sendToPlayer(sp);
                DynamicConfigSyncCP.sendToPlayer(sp);
                PerkGroupsSyncCP.sendToPlayer(sp);
                // Re-apply passive attribute modifiers so disabledPassives changes take effect
                // immediately without requiring a relog.
                RegistryAttributes.modifierAttributes(sp);
            }
        }

        if(command.getSource().getEntity() instanceof Player player) {
            player.sendSystemMessage(Component.literal("Forcing refresh of skills..."));
        }

        return Command.SINGLE_SUCCESS;
    }
}
