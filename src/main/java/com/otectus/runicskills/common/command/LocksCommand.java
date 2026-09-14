package com.otectus.runicskills.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.*;
import com.otectus.runicskills.integration.lock.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;

public final class LocksCommand {
    private LocksCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("locks")
                .then(Commands.literal("inspect").executes(c -> inspect(c.getSource(), LockAction.USE))
                        .then(Commands.argument("action", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(LockAction.values())
                                        .map(a -> a.name().toLowerCase(java.util.Locale.ROOT)), b))
                                .executes(c -> {
                                    try { return inspect(c.getSource(), LockAction.valueOf(StringArgumentType.getString(c, "action").toUpperCase(java.util.Locale.ROOT))); }
                                    catch (IllegalArgumentException e) { c.getSource().sendFailure(Component.literal("Unknown lock action.")); return 0; }
                                })))
                .then(Commands.literal("audit").requires(s -> s.hasPermission(2)).executes(c -> {
                    try { LockAudit.write(c.getSource().getServer()); }
                    catch (java.io.IOException e) { c.getSource().sendFailure(Component.literal("Could not write lock audit: " + e.getMessage())); return 0; }
                    c.getSource().sendSuccess(() -> Component.literal("Wrote debug/runicskills-locks.json (loaded registry and recipes, no player data)."), false);
                    return 1;
                }));
    }
    private static int inspect(CommandSourceStack source, LockAction action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException(); var stack = player.getMainHandItem();
        String id = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        var snapshot = HandlerSkill.snapshot();
        source.sendSuccess(() -> Component.literal(id + " / " + action + ": locks "
                + (HandlerCommonConfig.HANDLER.instance().enableItemLocks ? "enabled" : "disabled")
                + ", revision " + snapshot.revision() + ", source " + snapshot.sources().getOrDefault(id, "unhandled")
                + ", requirements " + LockAudit.requirements(id)), false);
        var decision = LockProviderRegistry.resolveStack(player, stack, action);
        decision.ifPresent(d -> source.sendSuccess(() -> Component.literal("Stack rules " + d.matchedRuleIds()
                + ": " + d.requirements() + "; uncertainty: " + d.unsupportedFacts()), false));
        var cap = SkillCapability.get(player);
        source.sendSuccess(() -> Component.literal("Action allowed by Runic Skills: " + (cap == null || cap.canUseItemSilent(player, stack, action))), false);
        return 1;
    }
}
