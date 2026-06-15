package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.event.PassiveLevelUpEvent;
import com.otectus.runicskills.integration.quests.RunicQuestBridge;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPassives;
import com.otectus.runicskills.registry.passive.Passive;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;

public class PassiveLevelDownSP {
    private final String passive;

    public PassiveLevelDownSP(Passive passive) {
        this.passive = passive.getName();
    }

    public PassiveLevelDownSP(FriendlyByteBuf buffer) {
        this.passive = buffer.readUtf();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.passive);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player != null) {
                if (!PacketRateLimiter.allow(player, "passive_level_down", 2)) return;
                SkillCapability capability = SkillCapability.get(player);
                if (capability == null) return;

                Passive passive = RegistryPassives.getPassive(this.passive);
                if (passive == null) return;
                int currentLevel = capability.getPassiveLevel(passive);
                if (currentLevel <= 0) return;

                // Fire public Forge event (since 1.2.0). Same event class used for level-ups;
                // subscribers should compare oldLevel vs newLevel to distinguish.
                if (MinecraftForge.EVENT_BUS.post(new PassiveLevelUpEvent(player, passive, currentLevel, currentLevel - 1))) {
                    return;
                }

                capability.subPassiveLevel(passive, 1);
                RunicQuestBridge.onPassiveLevelChanged(player, passive, currentLevel, currentLevel - 1);
                RegistryAttributes.modifierAttributes(player); // H8: Recalculate attributes after passive change
                SyncSkillCapabilityCP.send(player);
            }
        });
        context.setPacketHandled(true);
    }

    public static void send(Passive passive) {
        ServerNetworking.sendToServer(new PassiveLevelDownSP(passive));
    }
}


