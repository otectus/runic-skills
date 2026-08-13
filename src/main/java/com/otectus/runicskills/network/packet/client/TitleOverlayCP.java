package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.RunicSkills;
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
            // A title id this client does not know resolves to null. Enqueuing that null wedged
            // the overlay permanently: OverlayTitleGui#render dereferenced it and threw before
            // reaching the dequeue, so the same NPE recurred every frame and every legitimately
            // earned title afterwards queued up behind it. Titles are registered from each side's
            // own titles.json5, which is not part of the login handshake, so an unknown id is
            // ordinary server customisation rather than anything malicious (RS-022).
            if (title == null) {
                RunicSkills.getLOGGER().debug(
                        "Ignoring title overlay for unknown title '{}' — this client has no such title registered.",
                        this.title);
                return;
            }
            OverlayTitleGui.list.enqueue(title);
            OverlayTitleGui.showWarning();
        });
        context.setPacketHandled(true);
    }

    public static void send(Player player, Title title) {
        ServerNetworking.sendToPlayer(new TitleOverlayCP(title), (ServerPlayer) player);
    }
}


