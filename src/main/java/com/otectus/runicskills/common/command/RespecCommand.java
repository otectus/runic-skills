package com.otectus.runicskills.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.integration.quests.RunicQuestBridge;
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
    public static LiteralCommandNode<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(
                Commands.literal("respec")
                // Respeccing SOMEONE ELSE stays operator-only; the permission check moved off the
                // root literal onto this branch so the self-service form below is reachable.
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(2))
                        .executes(source -> respec(source, EntityArgument.getPlayer(source, "player")))
                )
                // Self-service form, available to any player.
                //
                // Lowering maxActivePerks or perksPerGlobalLevel puts everyone already above the
                // new budget into a frozen state that TogglePerkSP will not let them spend out of,
                // and a respec was the only exit — behind permission level 2. On a public server
                // that meant a single config edit could strand the entire playerbase until an
                // operator ran the command once per affected player, by hand (RS-052). Resetting
                // your own progression is not a privileged action; it only ever costs the caller.
                .executes(source -> respecSelf(source))
        );
    }

    /** Resets the calling player's own progression. Requires no permission level. */
    private static int respecSelf(CommandContext<CommandSourceStack> source) {
        ServerPlayer self = source.getSource().getPlayer();
        if (self == null) {
            source.getSource().sendFailure(Component.translatable("commands.message.respec.player_only"));
            return 0;
        }
        return respec(source, self);
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

            // A respec resets every skill to 1, which puts every Power below its level gate — but
            // equipped Powers were never cleared, so a player kept a full endgame loadout across
            // a reset that took away everything that qualified them for it. The same gap let
            // Powers survive skill loss and level rollback (RS-020).
            capability.equippedMarks.clear();
            capability.equippedSeals.clear();
            capability.equippedCrown = "";
            capability.powerWindows.clear();

            // Residue that used to survive a "full reset": in-flight perk cooldowns (including the
            // perk-swap lock that could otherwise keep a just-respecced player frozen), the
            // Counter Attack retaliation window, and the transient Powers runtime state keyed on
            // this player (RS-128).
            capability.perkCooldowns.clear();
            com.otectus.runicskills.common.powers.PowerRuntime.clearPlayer(player.getUUID());
            com.otectus.runicskills.common.powers.PowerCooldownDebt.restore(player);
            com.otectus.runicskills.common.util.ContainerRewardLedger.forget(player.getUUID());

            RegistryAttributes.modifierAttributes(player);
            SyncSkillCapabilityCP.send(player);
            // Re-evaluate every active FTB Quests task after a respec. Sticky tasks
            // (default) hold their completed state; non-sticky tasks reset to 0
            // because the player's progression dropped below their thresholds.
            RunicQuestBridge.refreshAll(player);

            source.getSource().sendSuccess(() -> Component.translatable("commands.message.respec.success", player.getName().copy().withStyle(ChatFormatting.BOLD)), false);

            return Command.SINGLE_SUCCESS;
        }

        return 0;
    }
}
