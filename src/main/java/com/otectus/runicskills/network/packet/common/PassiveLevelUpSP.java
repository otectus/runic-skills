package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPassives;
import com.otectus.runicskills.registry.RegistryTitles;
import com.otectus.runicskills.registry.passive.Passive;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class PassiveLevelUpSP {
    private final String passive;

    public PassiveLevelUpSP(Passive passive) {
        this.passive = passive.getName();
    }

    public PassiveLevelUpSP(FriendlyByteBuf buffer) {
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
                SkillCapability capability = SkillCapability.get(player);
                if (capability == null) return;

                Passive passive = RegistryPassives.getPassive(this.passive);
                if (passive == null) return;

                // Reject level-up of disabled-via-config passives
                if (RegistryPassives.isDisabled(passive)) return;

                // H1: Validate passive isn't at max level
                int currentLevel = capability.getPassiveLevel(passive);
                if (currentLevel >= passive.levelsRequired.length) return;

                // H1: Validate skill level meets requirement for next passive tier
                int requiredSkillLevel = passive.levelsRequired[currentLevel];
                int actualSkillLevel = capability.getSkillLevel(passive.getSkill());
                if (actualSkillLevel < requiredSkillLevel) return;

                capability.addPassiveLevel(passive, 1);
                RegistryAttributes.modifierAttributes(player); // H8: Recalculate attributes after passive change
                RegistryTitles.syncTitles(player); // P5: Sync titles on passive level change
                SyncSkillCapabilityCP.send(player);
            }
        });
        context.setPacketHandled(true);
    }

    public static void send(Passive passive) {
        ServerNetworking.sendToServer(new PassiveLevelUpSP(passive));
    }
}


