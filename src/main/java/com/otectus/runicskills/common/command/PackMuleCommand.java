package com.otectus.runicskills.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.otectus.runicskills.common.inventory.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Deliberate, lossless preparation for disabling/removing oversized-stack support. */
public final class PackMuleCommand {
    private PackMuleCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("packmule")
                .then(Commands.literal("status").executes(c -> status(c.getSource(), c.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("normalize").executes(c -> status(c.getSource(), c.getSource().getPlayerOrException(), true))
                        .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(c -> status(c.getSource(), EntityArgument.getPlayer(c, "player"), true))));
    }
    private static int status(CommandSourceStack source, ServerPlayer player, boolean normalize) {
        if (normalize) {
            InventoryReconciliation.restore(player, true);
            InventoryReconciliation.normalize(player, true);
            player.containerMenu.broadcastFullState(); player.getInventory().setChanged();
        }
        int remaining = InventoryReconciliation.nativeExcess(player);
        source.sendSuccess(() -> Component.translatable("message.runicskills.pack_mule.status", PlayerStackPolicy.rank(player), remaining), false);
        if (remaining > 0) source.sendFailure(Component.translatable("message.runicskills.pack_mule.make_room"));
        return remaining == 0 ? 1 : 0;
    }
}
