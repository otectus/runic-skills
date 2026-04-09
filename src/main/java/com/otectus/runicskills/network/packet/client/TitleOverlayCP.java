package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.client.gui.OverlayTitleGui;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.registry.RegistryTitles;
import com.otectus.runicskills.registry.title.Title;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

public class TitleOverlayCP {
    private final String title;

    public TitleOverlayCP(Title title) {
        this.title = title.getName();
    }

    public TitleOverlayCP(FriendlyByteBuf buffer) {
        this.title = buffer.readUtf();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.title);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            Title title = RegistryTitles.getTitle(this.title);
            OverlayTitleGui.list.enqueue(title);
            OverlayTitleGui.showWarning();
        });
        context.setPacketHandled(true);
    }

    public static void send(Player player, Title title) {
        ServerNetworking.sendToPlayer(new TitleOverlayCP(title), (ServerPlayer) player);
    }
}


