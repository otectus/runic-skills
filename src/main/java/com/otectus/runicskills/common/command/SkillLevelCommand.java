package com.otectus.runicskills.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.command.arguments.SkillArgument;
import com.otectus.runicskills.common.progression.ProgressionService;
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
    public static LiteralCommandNode<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(
                (Commands.literal("skills").requires(source -> source.hasPermission(2)))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(
                                Commands.argument("skill", SkillArgument.getArgument())
                                .then(Commands.literal("get")
                                        .executes(source -> getSkill(source, EntityArgument.getPlayer(source, "player"), source.getArgument("skill", String.class)))
                                )
                                // The argument ranges are deliberately open at the top. They used
                                // to be IntegerArgumentType.integer(1, skillMaxLevel), evaluated
                                // once when the command tree was built and never again — so after
                                // /skillsreload changed the cap, the command was validating against
                                // a number the server no longer used (RS10-013). The real bound is
                                // applied inside execution by ProgressionService, against the
                                // configuration as it is at that moment, and the operator is told
                                // when their request was clamped.
                                .then(Commands.literal("set")
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                            .executes(source -> setSkill(source, EntityArgument.getPlayer(source, "player"), source.getArgument("skill", String.class), IntegerArgumentType.getInteger(source, "level")))
                                        )
                                )
                                .then(Commands.literal(("add"))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                .executes(source -> addSkill(source, EntityArgument.getPlayer(source, "player"), source.getArgument("skill", String.class), IntegerArgumentType.getInteger(source, "level"))))
                                )
                                .then(Commands.literal(("subtract"))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
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
        return apply(source, player, skillKey,
                skill -> ProgressionService.setSkillLevel(player, skill, setLevel, ProgressionService.Cause.COMMAND));
    }

    public static int addSkill(CommandContext<CommandSourceStack> source, ServerPlayer player, String skillKey, int addLevel) {
        return apply(source, player, skillKey,
                skill -> ProgressionService.addSkillLevels(player, skill, addLevel, ProgressionService.Cause.COMMAND));
    }

    public static int subtractSkill(CommandContext<CommandSourceStack> source, ServerPlayer player, String skillKey, int subtractLevel) {
        return apply(source, player, skillKey,
                skill -> ProgressionService.addSkillLevels(player, skill, -subtractLevel, ProgressionService.Cause.COMMAND));
    }

    /**
     * Runs one progression change and reports it.
     *
     * <p>All three forms share this: they differ only in what they ask for. Previously each wrote
     * into the capability itself with its own clamping — {@code set} had none at all beyond the
     * stale Brigadier range, {@code add} could overflow, and none of the three reconciled attributes,
     * titles or quests (RS10-013).
     *
     * <p>The result reports the level the player actually ended up at, which is not necessarily the
     * one requested: a request above the configured maximum is clamped rather than refused, and
     * saying so is what stops an operator believing they set a level they did not.
     */
    private static int apply(CommandContext<CommandSourceStack> source, ServerPlayer player, String skillKey,
                             java.util.function.Function<Skill, ProgressionService.Outcome> change) {
        Skill skill = RegistrySkills.getSkill(skillKey);
        if (player == null || skill == null) {
            source.getSource().sendFailure(Component.translatable("commands.message.skill.unknown"));
            return 0;
        }

        ProgressionService.Outcome outcome = change.apply(skill);
        if (outcome.denial() == ProgressionService.Denial.NO_CAPABILITY) {
            source.getSource().sendFailure(Component.translatable("commands.message.capability.not_found"));
            return 0;
        }
        if (outcome.denial() == ProgressionService.Denial.CANCELLED) {
            source.getSource().sendFailure(Component.translatable("commands.message.skill.cancelled"));
            return 0;
        }

        // NO_CHANGE is reported as a success with the current level rather than as a failure: the
        // player is at the level the operator asked for, which is what was wanted.
        source.getSource().sendSuccess(() -> Component.translatable("commands.message.skill.set",
                player.getName().copy().withStyle(ChatFormatting.BOLD),
                Component.literal(String.valueOf(outcome.current())).withStyle(ChatFormatting.BOLD),
                Component.translatable(skill.getKey()).withStyle(ChatFormatting.BOLD)), false);
        return Command.SINGLE_SUCCESS;
    }
}


