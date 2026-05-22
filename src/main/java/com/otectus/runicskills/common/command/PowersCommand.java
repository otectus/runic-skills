package com.otectus.runicskills.common.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Admin command for the Powers system. Same op-2 gate as TitleCommand. Subcommands:
 * <ul>
 *   <li>{@code /powers list [tier]} — print every registered Power, marking equipped ones.</li>
 *   <li>{@code /powers equip <power>} — equip a Power into the appropriate tier slot.</li>
 *   <li>{@code /powers unequip <power>} — unequip a Power.</li>
 *   <li>{@code /powers view} — print the caller's current equipped Marks/Seals/Crown.</li>
 * </ul>
 * The equip path bypasses the {@link com.otectus.runicskills.network.packet.common.PowerEquipSP}
 * skill-level gate (admins should be able to test any Power) but still respects the tier slot
 * caps and {@code disabledPowers} kill-switch — those are catalog invariants, not gates.
 */
public class PowersCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("powers")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(ctx -> listAll(ctx, null))
                        .then(Commands.literal("mark") .executes(ctx -> listAll(ctx, PowerTier.MARK)))
                        .then(Commands.literal("seal") .executes(ctx -> listAll(ctx, PowerTier.SEAL)))
                        .then(Commands.literal("crown").executes(ctx -> listAll(ctx, PowerTier.CROWN))))
                .then(Commands.literal("view")
                        .executes(PowersCommand::view))
                .then(Commands.literal("equip")
                        .then(Commands.argument("power", StringArgumentType.word())
                                .suggests(POWER_SUGGESTIONS)
                                .executes(ctx -> equip(ctx, true))))
                .then(Commands.literal("unequip")
                        .then(Commands.argument("power", StringArgumentType.word())
                                .suggests(EQUIPPED_SUGGESTIONS)
                                .executes(ctx -> equip(ctx, false))))
        );
    }

    // ── Suggestion providers ────────────────────────────────────────────────────────

    private static final SuggestionProvider<CommandSourceStack> POWER_SUGGESTIONS =
            (ctx, builder) -> {
                String input = builder.getRemaining().toLowerCase();
                for (Power p : RegistryPowers.getCachedValues()) {
                    if (p.getName().startsWith(input)) builder.suggest(p.getName());
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> EQUIPPED_SUGGESTIONS =
            (ctx, builder) -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                    return Suggestions.empty();
                }
                SkillCapability cap = SkillCapability.get(player);
                if (cap == null) return Suggestions.empty();
                String input = builder.getRemaining().toLowerCase();
                for (PowerTier tier : PowerTier.values()) {
                    for (String name : cap.getEquippedPowers(tier)) {
                        if (name.startsWith(input)) builder.suggest(name);
                    }
                }
                return builder.buildFuture();
            };

    // ── Subcommand implementations ──────────────────────────────────────────────────

    private static int listAll(CommandContext<CommandSourceStack> ctx, PowerTier tierFilter) {
        ServerPlayer player = (ctx.getSource().getEntity() instanceof ServerPlayer p) ? p : null;
        SkillCapability cap = player == null ? null : SkillCapability.get(player);
        for (PowerTier tier : PowerTier.values()) {
            if (tierFilter != null && tierFilter != tier) continue;
            ctx.getSource().sendSuccess(() -> Component.translatable(tier.getKey())
                    .withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD), false);
            for (Power p : RegistryPowers.getByTier(tier)) {
                boolean equipped = cap != null && cap.isPowerEquipped(p);
                boolean disabled = RegistryPowers.isDisabled(p);
                ChatFormatting color = disabled ? ChatFormatting.DARK_GRAY
                        : equipped ? ChatFormatting.GREEN
                        : ChatFormatting.GRAY;
                MutableComponent line = Component.literal("  " + p.getName() + " ").withStyle(color)
                        .append(Component.translatable(p.getKey()).withStyle(color))
                        .append(Component.literal(disabled ? " [disabled]" : equipped ? " [equipped]" : "")
                                .withStyle(disabled ? ChatFormatting.RED : ChatFormatting.GREEN));
                ctx.getSource().sendSuccess(() -> line, false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int view(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Run as a player."));
            return 0;
        }
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return 0;

        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Marks %d/%d · Seals %d/%d · Crown %d/%d",
                cap.equippedMarks.size(), PowerTier.MARK.maxEquipped,
                cap.equippedSeals.size(), PowerTier.SEAL.maxEquipped,
                cap.equippedCrown.isEmpty() ? 0 : 1, PowerTier.CROWN.maxEquipped))
                .withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD), false);

        printRow(ctx, "Marks", cap.equippedMarks);
        printRow(ctx, "Seals", cap.equippedSeals);
        printRow(ctx, "Crown", cap.equippedCrown.isEmpty() ? List.of() : List.of(cap.equippedCrown));

        return Command.SINGLE_SUCCESS;
    }

    private static void printRow(CommandContext<CommandSourceStack> ctx, String label, List<String> names) {
        if (names.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + label + ": (none)")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return;
        }
        for (String n : names) {
            Power p = RegistryPowers.getPower(n);
            String disp = p == null ? n : Component.translatable(p.getKey()).getString();
            ctx.getSource().sendSuccess(() -> Component.literal("  " + label + ": " + disp + " (" + n + ")")
                    .withStyle(ChatFormatting.GREEN), false);
        }
    }

    private static int equip(CommandContext<CommandSourceStack> ctx, boolean equipFlag) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Run as a player."));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "power");
        Power p = RegistryPowers.getPower(name);
        if (p == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown power: " + name));
            return 0;
        }
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return 0;

        if (equipFlag) {
            if (RegistryPowers.isDisabled(p)) {
                ctx.getSource().sendFailure(Component.literal(name + " is disabled in config."));
                return 0;
            }
            if (!cap.equipPower(p)) {
                ctx.getSource().sendFailure(Component.literal(
                        "Cannot equip " + name + " — slot full or already equipped."));
                return 0;
            }
            int icd = PowerOverridesManager.icdTicksOr(p, p.defaultIcdTicks);
            ctx.getSource().sendSuccess(() -> Component.literal("Equipped " + name + " ("
                    + p.getTier().name().toLowerCase() + ", default ICD " + icd + "t)")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            if (!cap.unequipPower(p)) {
                ctx.getSource().sendFailure(Component.literal(name + " was not equipped."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Unequipped " + name)
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        SyncSkillCapabilityCP.send(player);
        return Command.SINGLE_SUCCESS;
    }
}
