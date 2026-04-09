package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.client.gui.OverlaySkillGui;
import com.otectus.runicskills.network.ServerNetworking;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

public class SkillOverlayCP {
    private final String skill;

    public SkillOverlayCP(String skill) {
        this.skill = skill;
    }

    public SkillOverlayCP(FriendlyByteBuf buffer) {
        this.skill = buffer.readUtf();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.skill);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> OverlaySkillGui.showWarning(this.skill));
        context.setPacketHandled(true);
    }

    public static void send(Player player, String skill) {
        ServerNetworking.sendToPlayer(new SkillOverlayCP(skill), (ServerPlayer) player);
    }
}


