package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.network.ServerNetworking;

import java.util.function.Supplier;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

public class SyncSkillCapabilityCP {
    private final CompoundTag nbt;

    public SyncSkillCapabilityCP(CompoundTag nbt) {
        this.nbt = nbt;
    }

    public SyncSkillCapabilityCP(FriendlyByteBuf buffer) {
        this.nbt = buffer.readNbt();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeNbt(this.nbt);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // getLocal() is @Nullable: it returns null before client setup or while the local
            // player is absent (login handshake, world unload, fast respawn). A sync arriving in
            // that window must be dropped, not NPE the netty handler thread. Mirrors PlayerMessagesCP.
            SkillCapability cap = SkillCapability.getLocal();
            if (cap != null) cap.deserializeNBT(this.nbt);
        });

        context.setPacketHandled(true);
    }

    public static void send(Player player) {
        // get(player) is @Nullable: capability attach can fail / not yet be present (e.g. during
        // EntityJoinLevelEvent on first join). Skip the sync rather than NPE — another lifecycle
        // event re-syncs once the capability exists. This guards ~30 call sites that funnel here.
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;
        ServerNetworking.sendToPlayer(new SyncSkillCapabilityCP(cap.serializeNBT()), (ServerPlayer) player);
    }
}


