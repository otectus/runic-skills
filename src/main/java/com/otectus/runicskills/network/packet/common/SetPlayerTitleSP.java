package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryTitles;
import com.otectus.runicskills.registry.title.Title;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class SetPlayerTitleSP {
    private final String title;

    public SetPlayerTitleSP(Title title) {
        this.title = title.getName();
    }

    public SetPlayerTitleSP(FriendlyByteBuf buffer) {
        this.title = buffer.readUtf();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.title);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            SkillCapability capability = SkillCapability.get(player);
            if (capability == null) return;

            Title title = RegistryTitles.getTitle(this.title);
            if (title == null) {
                RunicSkills.getLOGGER().warn("SetPlayerTitleSP: unknown title '{}' from {}", this.title, player.getName().getString());
                return;
            }

            // Verify the player has actually unlocked this title
            if (!capability.getLockTitle(title)) {
                RunicSkills.getLOGGER().warn("SetPlayerTitleSP: {} tried to set locked title '{}'", player.getName().getString(), this.title);
                return;
            }

            capability.setPlayerTitle(title);

            if (HandlerCommonConfig.HANDLER.instance().titlesUseCustomName) {
                player.setCustomName(Component.translatable(title.getKey()));
                player.refreshDisplayName();
                player.refreshTabListName();
            }

            SyncSkillCapabilityCP.send(player);
        });
        context.setPacketHandled(true);
    }

    public static void send(Title title) {
        ServerNetworking.sendToServer(new SetPlayerTitleSP(title));
    }
}


