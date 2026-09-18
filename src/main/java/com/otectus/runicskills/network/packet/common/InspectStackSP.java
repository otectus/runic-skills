package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.lock.*;
import com.otectus.runicskills.network.*;
import com.otectus.runicskills.network.packet.client.StackRequirementsCP;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Read only: the client names a visible menu slot, never an arbitrary tool/NBT or another player. */
public record InspectStackSP(int menu, int slot, long request) {
    public InspectStackSP(FriendlyByteBuf buffer) { this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong()); }
    public void toBytes(FriendlyByteBuf buffer) { buffer.writeVarInt(menu); buffer.writeVarInt(slot); buffer.writeVarLong(request); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); var player = context.getSender();
        if (player != null && PacketRateLimiter.allow(player, "inspect_stack", 5)) context.enqueueWork(() -> {
            StackRequirementsCP response = inspect(player); if (response != null) ServerNetworking.sendToPlayer(response, player);
        });
        context.setPacketHandled(true);
    }
    public StackRequirementsCP inspect(ServerPlayer player) {
        var container = player.containerMenu;
        if (container.containerId != menu || slot < 0 || slot >= container.slots.size() || !container.stillValid(player)) return null;
        var stack = container.getSlot(slot).getItem();
        var decisions = new EnumMap<LockAction, StackRequirementsCP.View>(LockAction.class);
        // Only the actions a stack can answer. The typed CAST/*_BLOCK actions added in 2.2.1 are
        // statements about a spell definition or the block being acted on, so resolving them here
        // would add four always-absent views per inspection and grow this packet by two thirds for
        // nothing (spec 14.2: review the byte budget, do not raise the counts).
        if (HandlerCommonConfig.HANDLER.instance().enableItemLocks) for (LockAction action : LockAction.values()) {
            if (!action.appliesToStack()) continue;
            LockProviderRegistry.resolveStack(player, stack, action).ifPresent(d -> decisions.put(action,
                    new StackRequirementsCP.View(d.allowed(), d.requirements(), d.matchedRuleIds(), d.unsupportedFacts())));
        }
        return new StackRequirementsCP(menu, slot, request, container.getStateId(), StackRequirementsCP.fingerprint(stack), decisions);
    }
}
