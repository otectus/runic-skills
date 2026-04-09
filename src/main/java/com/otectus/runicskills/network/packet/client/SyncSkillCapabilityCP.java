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
        context.enqueueWork(() -> SkillCapability.getLocal().deserializeNBT(this.nbt));

        context.setPacketHandled(true);
    }

    public static void send(Player player) {
        ServerNetworking.sendToPlayer(new SyncSkillCapabilityCP(SkillCapability.get(player).serializeNBT()), (ServerPlayer) player);
    }
}


