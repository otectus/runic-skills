package com.otectus.runicskills.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.otectus.runicskills.common.equipment.EquipmentProfile;
import com.otectus.runicskills.common.equipment.EquipmentProfileService;
import com.otectus.runicskills.common.equipment.RequirementDecision;
import com.otectus.runicskills.common.workshop.WorkshopFocusService;
import com.otectus.runicskills.common.workshop.WorkshopFocusService.Focus;
import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.integration.lock.LockProviderRegistry;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Map;
import java.util.Optional;

/**
 * {@code /skills tinkers …} — what this server thinks of your equipment, your workshop and its own
 * compatibility.
 *
 * <p>A subtree rather than a root, per §14.4, and attached to the existing {@code /skills} literal.
 * Three of its four forms are usable by an ordinary player on purpose: a player who cannot see why
 * their tool is refused, why their workshop stopped helping, or whether the integration is even
 * working on this server has no other way to find out, and every one of those answers is about
 * their own equipment. Only inspecting somebody <em>else</em> is operator-gated, at the same
 * permission level the rest of the {@code /skills} tree uses.
 *
 * <p><b>Inspection never writes.</b> §14.4 is explicit that read-only inspection must not repair,
 * stamp, assign identity, add a modifier or mark an item dirty — so this reads the classification
 * and the requirement verdict, which are the two things already computed for every other purpose,
 * and touches the stack in no other way.
 */
public final class TinkersCommand {

    /** How far the workshop subcommand will look for a controller: ordinary interaction reach. */
    private static final double PICK_DISTANCE = 6.0;

    private TinkersCommand() {
    }

    /**
     * The {@code tinkers} subtree, for {@code SkillLevelCommand} to hang on the {@code skills} root.
     *
     * <p>Returned rather than registered so it lands under the existing literal instead of taking a
     * second top-level name; §14.4 asks for the existing tree to be extended, and a new root would
     * be one more generic word for a large pack to collide with.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("tinkers")
                .then(Commands.literal("inspect")
                        .executes(context -> inspect(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> inspect(context,
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("workshop")
                        .then(Commands.literal("focus").executes(TinkersCommand::focus))
                        .then(Commands.literal("release").executes(TinkersCommand::release)))
                .then(Commands.literal("compat").executes(TinkersCommand::compat));
    }

    /**
     * Prints how the held item classifies and what it would require.
     *
     * <p>The equipment profile answers "what is this", the stack resolver answers "may you use it",
     * and both are the same calls the game itself makes — an inspection that computed its own
     * answer could disagree with the one being enforced, which is the one failure a diagnostic must
     * not have.
     */
    private static int inspect(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        CommandSourceStack source = context.getSource();
        ItemStack held = target.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.translatable("commands.runicskills.tinkers.inspect.empty"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.runicskills.tinkers.inspect.header",
                target.getName().copy().withStyle(ChatFormatting.BOLD), held.getHoverName()), false);

        Optional<EquipmentProfile> profile = EquipmentProfileService.profile(held);
        if (profile.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.runicskills.tinkers.inspect.unclassified").withStyle(ChatFormatting.GRAY),
                    false);
        } else {
            EquipmentProfile found = profile.get();
            source.sendSuccess(() -> Component.translatable(
                    "commands.runicskills.tinkers.inspect.profile", found.providerId(),
                    found.roles().toString(), found.remaining(), found.maxDurability())
                    .withStyle(ChatFormatting.GRAY), false);
        }

        Optional<RequirementDecision> decision =
                LockProviderRegistry.resolveStack(target, held, LockAction.USE);
        if (decision.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.runicskills.tinkers.inspect.no_requirement")
                    .withStyle(ChatFormatting.GREEN), false);
            return Command.SINGLE_SUCCESS;
        }

        RequirementDecision verdict = decision.get();
        StringBuilder requirements = new StringBuilder();
        for (Map.Entry<String, Integer> requirement : verdict.requirements().entrySet()) {
            if (requirements.length() > 0) requirements.append(", ");
            requirements.append(requirement.getKey()).append(' ').append(requirement.getValue());
        }
        String summary = requirements.length() == 0 ? "-" : requirements.toString();
        source.sendSuccess(() -> Component.translatable(
                "commands.runicskills.tinkers.inspect.decision",
                Component.translatable(verdict.allowed()
                        ? "commands.runicskills.tinkers.allowed"
                        : "commands.runicskills.tinkers.denied"),
                summary, verdict.matchedRuleIds().toString())
                .withStyle(verdict.allowed() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        if (!verdict.unsupportedFacts().isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.runicskills.tinkers.inspect.unsupported",
                    verdict.unsupportedFacts().toString()).withStyle(ChatFormatting.YELLOW), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Claims the controller the caller is looking at.
     *
     * <p>The accessible equivalent of the panel's Focus button, and deliberately identical to it:
     * the same service call, the same validation, the same refusal messages. What the command does
     * <em>not</em> do is accept a coordinate — the target is whatever the player is actually looking
     * at, so there is no way to spell a workshop across the world into this form either.
     */
    private static int focus(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.runicskills.tinkers.workshop.player_only"));
            return 0;
        }
        if (!TConstructCompatibilityStatus.current().supports(Capability.WORKSHOP)) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.runicskills.tinkers.workshop.unavailable"));
            return 0;
        }
        HitResult looking = player.pick(PICK_DISTANCE, 0.0f, false);
        if (!(looking instanceof BlockHitResult hit) || looking.getType() != HitResult.Type.BLOCK) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.runicskills.tinkers.workshop.no_target"));
            return 0;
        }
        BlockPos pos = hit.getBlockPos();
        WorkshopFocusService.Outcome outcome =
                WorkshopFocusService.focus(player, pos, WorkshopFocusService.revisionOf(player.getUUID()));
        // A controller was not what they were looking at? Offer the same position as an association,
        // because a casting table is the other thing a player stands in front of in a workshop.
        if (outcome == WorkshopFocusService.Outcome.NO_TARGET) {
            outcome = WorkshopFocusService.associate(player, pos,
                    WorkshopFocusService.revisionOf(player.getUUID()));
        }
        return report(context, outcome, player);
    }

    /** Drops the caller's own claim. */
    private static int release(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.runicskills.tinkers.workshop.player_only"));
            return 0;
        }
        return report(context, WorkshopFocusService.release(player,
                WorkshopFocusService.revisionOf(player.getUUID())), player);
    }

    /** Says what happened and, when a claim is held, what it currently is. */
    private static int report(CommandContext<CommandSourceStack> context,
                              WorkshopFocusService.Outcome outcome, ServerPlayer player) {
        CommandSourceStack source = context.getSource();
        Component message = Component.translatable(outcome.messageKey());
        if (!outcome.succeeded()) {
            source.sendFailure(message);
            return 0;
        }
        source.sendSuccess(() -> message.copy().withStyle(ChatFormatting.GREEN), false);
        Focus focus = WorkshopFocusService.activeFocus(player);
        if (focus != null) {
            long remaining = WorkshopFocusService.remainingTicks(focus, player.getServer()) / 20L;
            source.sendSuccess(() -> Component.translatable(
                    "commands.runicskills.tinkers.workshop.held",
                    focus.controller().toShortString(), focus.associations().size(), remaining)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        WorkshopFocusService.publishStatus(player);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Prints the per-capability compatibility summary.
     *
     * <p>Every player gets the statuses, because "is this working on this server" is a fair
     * question to be able to answer. Operators additionally get the reason strings, which name the
     * version and the specific hook that did not apply — the artifact-level detail §14.4 reserves
     * for them.
     */
    private static int compat(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TConstructCompatibilityStatus status = TConstructCompatibilityStatus.current();
        boolean operator = source.hasPermission(2);
        source.sendSuccess(() -> Component.translatable(
                "commands.runicskills.tinkers.compat.header", status.version(),
                status.profile().toString()).withStyle(ChatFormatting.BOLD), false);
        for (Capability capability : Capability.values()) {
            Entry entry = status.entry(capability);
            Component line = operator
                    ? Component.literal(capability + ": " + entry.status() + " — " + entry.reason())
                    : Component.literal(capability + ": " + entry.status());
            // Three colours, not two. A hook that did not apply is a broken install and reads red;
            // ABSENT and DISABLED_BY_CONFIG are somebody's choice or somebody's missing mod and read
            // grey. Colouring them alike is what made "the Tinkers' perks are not working" one
            // question with several fixes, which is the thing this diagnostic exists to stop.
            ChatFormatting colour = switch (entry.status()) {
                case SUPPORTED -> ChatFormatting.GREEN;
                case HOOK_UNAVAILABLE, UPSTREAM_INCOMPATIBLE -> ChatFormatting.RED;
                default -> ChatFormatting.GRAY;
            };
            source.sendSuccess(() -> line.copy().withStyle(colour), false);
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.runicskills.tinkers.compat.focuses", WorkshopFocusService.size())
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return Command.SINGLE_SUCCESS;
    }
}
