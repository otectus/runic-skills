package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Player → server: equip or unequip a {@link Power}. Server validates skill threshold,
 * tier slot capacity, mod-id requirement, and disabled-config status before mutating
 * {@link SkillCapability}, then resyncs the client.
 * <p>
 * Mirrors {@link TogglePerkSP}: same rate-limit, same authoritative-server gating, same
 * resync-on-reject pattern (the client sees its requested change rejected via the next
 * SyncSkillCapabilityCP rather than a per-action error packet).
 */
public class PowerEquipSP {

    /** True = equip, false = unequip. */
    private final boolean equip;
    private final String powerName;

    public PowerEquipSP(Power power, boolean equip) {
        this.powerName = power.getName();
        this.equip = equip;
    }

    public PowerEquipSP(FriendlyByteBuf buffer) {
        this.powerName = buffer.readUtf();
        this.equip = buffer.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.powerName);
        buffer.writeBoolean(this.equip);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!PacketRateLimiter.allow(player, "equip_power", 2)) return;

            SkillCapability cap = SkillCapability.get(player);
            if (cap == null) return;

            Power power = RegistryPowers.getPower(this.powerName);
            if (power == null) {
                SyncSkillCapabilityCP.send(player);
                return;
            }

            // Unequip is always allowed — clean up stuck slots even if the Power is disabled.
            if (!this.equip) {
                cap.unequipPower(power);
                SyncSkillCapabilityCP.send(player);
                return;
            }

            // Reject equipping a disabled Power.
            if (RegistryPowers.isDisabled(power)) {
                SyncSkillCapabilityCP.send(player);
                return;
            }

            // Required-mod check — the Power was registered, but the mod could have been
            // removed mid-save in theory. Fail safe.
            if (power.requiredModId != null
                    && !net.minecraftforge.fml.ModList.get().isLoaded(power.requiredModId)) {
                SyncSkillCapabilityCP.send(player);
                return;
            }

            // Skill threshold (overridden by JSON tunable if set).
            int requiredLvl = com.otectus.runicskills.registry.powers.PowerOverridesManager
                    .requiredSkillLevelOr(power, power.requiredSkillLevel);
            if (requiredLvl > 0
                    && cap.getSkillLevel(power.getGoverningSkill()) < requiredLvl) {
                SyncSkillCapabilityCP.send(player);
                return;
            }

            // Capacity / duplicate enforced inside SkillCapability.equipPower; if it
            // returns false the client just gets resynced.
            cap.equipPower(power);
            SyncSkillCapabilityCP.send(player);
        });
        context.setPacketHandled(true);
    }

    public static void send(Power power, boolean equip) {
        ServerNetworking.sendToServer(new PowerEquipSP(power, equip));
    }
}
