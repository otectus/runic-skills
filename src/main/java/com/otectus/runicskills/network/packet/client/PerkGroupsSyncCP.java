package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.registry.perks.PerkGroup;
import com.otectus.runicskills.registry.perks.PerkGroupManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Pushes the server's data-driven perk groups to every client. Sent on player login and
 * after {@code /skillsreload}. The GUI uses the client-side copy purely for display hints;
 * the authoritative cap check runs server-side in {@code TogglePerkSP}.
 */
public class PerkGroupsSyncCP {

    private final List<PerkGroup> groups;

    public PerkGroupsSyncCP() {
        this.groups = new ArrayList<>(PerkGroupManager.all());
    }

    public PerkGroupsSyncCP(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<PerkGroup> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buffer.readResourceLocation();
            int maxActive = buffer.readVarInt();
            int perkCount = buffer.readVarInt();
            Set<String> perks = new LinkedHashSet<>(perkCount);
            for (int j = 0; j < perkCount; j++) {
                perks.add(buffer.readUtf(Short.MAX_VALUE));
            }
            boolean hasMessage = buffer.readBoolean();
            String message = hasMessage ? buffer.readUtf(Short.MAX_VALUE) : null;
            list.add(new PerkGroup(id, maxActive, Set.copyOf(perks), message));
        }
        this.groups = list;
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.groups.size());
        for (PerkGroup group : this.groups) {
            buffer.writeResourceLocation(group.id());
            buffer.writeVarInt(group.maxActive());
            buffer.writeVarInt(group.perks().size());
            for (String perk : group.perks()) {
                buffer.writeUtf(perk, Short.MAX_VALUE);
            }
            boolean hasMessage = group.messageKey() != null;
            buffer.writeBoolean(hasMessage);
            if (hasMessage) buffer.writeUtf(group.messageKey(), Short.MAX_VALUE);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            Map<ResourceLocation, PerkGroup> next = new HashMap<>();
            for (PerkGroup g : this.groups) next.put(g.id(), g);
            PerkGroupManager.replaceAll(next);
        });
        context.setPacketHandled(true);
    }

    public static void sendToPlayer(ServerPlayer player) {
        ServerNetworking.sendToPlayer(new PerkGroupsSyncCP(), player);
    }

    public static void sendToAll() {
        // Iterate connected players via MinecraftServer; kept lightweight for callers.
        var server = com.otectus.runicskills.RunicSkills.server;
        if (server == null) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sendToPlayer(sp);
        }
    }
}
