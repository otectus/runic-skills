package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class TogglePerkSP {
    private final String perk;
    private final int targetRank;

    public TogglePerkSP(Perk perk, int targetRank) {
        this.perk = perk.getName();
        this.targetRank = targetRank;
    }

    // Legacy constructor for boolean toggle compatibility
    public TogglePerkSP(Perk perk, boolean toggle) {
        this.perk = perk.getName();
        this.targetRank = toggle ? 1 : 0;
    }

    public TogglePerkSP(FriendlyByteBuf buffer) {
        this.perk = buffer.readUtf();
        this.targetRank = buffer.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.perk);
        buffer.writeVarInt(this.targetRank);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player != null) {
                if (!PacketRateLimiter.allow(player, "toggle_perk", 2)) return;

                SkillCapability capability = SkillCapability.get(player);
                if (capability == null) return;

                Perk perk = RegistryPerks.getPerk(this.perk);
                if (perk == null) return;

                // Disabling (rank 0) is always allowed
                if (this.targetRank <= 0) {
                    capability.setPerkRank(perk, 0);
                    SyncSkillCapabilityCP.send(player);
                    return;
                }

                // Validate target rank is within bounds
                if (this.targetRank > perk.getMaxRank()) {
                    SyncSkillCapabilityCP.send(player);
                    return;
                }

                // Validate skill level for the requested rank
                int requiredLevel = perk.getLevelForRank(this.targetRank);
                if (capability.getSkillLevel(perk.getSkill()) < requiredLevel) {
                    SyncSkillCapabilityCP.send(player);
                    return;
                }

                // School attunement limit check: only when enabling (going from 0 to 1+)
                int currentRank = capability.getPerkRank(perk);
                if (currentRank == 0 && RegistryPerks.isSchoolAttunementPerk(this.perk)) {
                    int max = com.otectus.runicskills.handler.HandlerCommonConfig.HANDLER.instance().ironsMaxSchoolSelections;
                    if (RegistryPerks.countEnabledSchoolPerks(capability) >= max) {
                        SyncSkillCapabilityCP.send(player);
                        return;
                    }
                }

                capability.setPerkRank(perk, this.targetRank);
                SyncSkillCapabilityCP.send(player);
            }
        });
        context.setPacketHandled(true);
    }

    // Send with specific rank
    public static void send(Perk perk, int targetRank) {
        ServerNetworking.sendToServer(new TogglePerkSP(perk, targetRank));
    }

    // Legacy send for boolean toggle
    public static void send(Perk perk, boolean toggle) {
        ServerNetworking.sendToServer(new TogglePerkSP(perk, toggle));
    }
}
