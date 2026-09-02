package com.otectus.runicskills.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.runicskills.config.snapshot.ConfigScope;
import com.otectus.runicskills.config.snapshot.GameplayConfigSnapshot;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.network.packet.client.ConfigSyncCP;
import com.otectus.runicskills.network.packet.client.GameplayConfigCP;
import com.otectus.runicskills.network.packet.client.PerkGroupsSyncCP;
import com.otectus.runicskills.registry.RegistryAttributes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class SkillsReloadCommand {

    public static LiteralCommandNode<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register((Commands.literal("skillsreload").requires((source) -> {
            return source.hasPermission(2);
        })).executes(SkillsReloadCommand::execute));
    }

    /**
     * Reloads every config file and reports what that did.
     *
     * <p>The command used to print "Forcing refresh of skills..." and nothing else, which was the
     * whole problem: an operator had no way to tell which of their edits had taken effect, which
     * needed a restart, and which had changed the server's behaviour while connected clients
     * carried on using their own values (RS10-005). It now names the counts and, critically, lists
     * the restart-required settings it read but could not apply.
     */
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
                GameplayConfigCP.sendToPlayer(sp);
                PerkGroupsSyncCP.sendToPlayer(sp);
                // Re-apply passive attribute modifiers so disabledPassives changes take effect
                // immediately without requiring a relog.
                RegistryAttributes.modifierAttributes(sp);
            }
        }

        int liveServer = GameplayConfigSnapshot.countWithScope(ConfigScope.LIVE_SERVER);
        int players = server == null ? 0 : server.getPlayerList().getPlayers().size();
        command.getSource().sendSuccess(() -> Component.literal(
                "Runic Skills: reloaded " + liveServer + " live setting(s); re-synced "
                + players + " player(s)."), true);

        List<String> restartRequired = GameplayConfigSnapshot.restartRequiredChangesSinceStartup();
        if (!restartRequired.isEmpty()) {
            command.getSource().sendFailure(Component.literal(
                    "These setting(s) changed but need a restart to take effect: "
                    + String.join(", ", restartRequired)));
        }

        return Command.SINGLE_SUCCESS;
    }
}
