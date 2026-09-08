package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.common.util.PacketBounds;
import io.netty.handler.codec.DecoderException;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
        this(power.getName(), equip);
    }

    private PowerEquipSP(String powerName, boolean equip) {
        if (!PacketBounds.isContentIdValid(powerName)) {
            throw new IllegalArgumentException("Malformed Power id");
        }
        this.powerName = powerName;
        this.equip = equip;
    }

    /** A missing addon cannot supply a Power instance, but its saved slot must remain removable. */
    public static PowerEquipSP unequipUnknown(String powerName) {
        return new PowerEquipSP(powerName, false);
    }

    public PowerEquipSP(FriendlyByteBuf buffer) {
        this.powerName = buffer.readUtf(PacketBounds.MAX_CONTENT_ID_CHARS);
        if (!PacketBounds.isContentIdValid(this.powerName)) {
            throw new DecoderException("PowerEquipSP: malformed Power id");
        }
        this.equip = buffer.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.powerName, PacketBounds.MAX_CONTENT_ID_CHARS);
        buffer.writeBoolean(this.equip);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        // Admission control runs on the network thread, BEFORE enqueueWork. Rate limiting used
        // to happen inside the scheduled task, so a flooding client still allocated a lambda and
        // queued a main-thread task for every packet — the limiter discarded the work only after
        // the server had already paid to schedule it (RS-150).
        ServerPlayer sender = context.getSender();
        if (sender == null || !PacketRateLimiter.allow(sender, "equip_power", 2)) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            SkillCapability cap = SkillCapability.get(player);
            if (cap == null) return;

            Power power = RegistryPowers.getPower(this.powerName);
            if (power == null) {
                // Unresolvable id. Unequipping one is handled below by raw id, because a Power
                // whose addon has been removed still occupies a slot and the player has to be able
                // to reclaim it (RS10-006).
                if (!this.equip && cap.unequipUnknownPower(this.powerName)) {
                    SyncSkillCapabilityCP.send(player);
                    return;
                }
                SyncSkillCapabilityCP.send(player);
                return;
            }

            // Unequip is always allowed — a slot must be reclaimable even when the Power in it has
            // since been disabled, lost its dependency, or fallen below its requirement.
            if (!this.equip) {
                cap.unequipPower(power);
                SyncSkillCapabilityCP.send(player);
                return;
            }

            // One evaluation for every rule: disabled state, dependency, governing skill, the Seal
            // secondary gate, the Crown total-skill gate, the same-school prerequisite chain, slot
            // capacity and the Power Point budget. PowerTier's javadoc has claimed since 1.1.0 that
            // these were "checked server-side"; four of them did not exist (RS10-006).
            PowerEligibility.Result verdict = PowerEligibility.evaluateEquip(player, power);
            if (!verdict.eligible()) {
                // Say why. Silently resyncing left the player watching a button do nothing.
                player.sendSystemMessage(Component.translatable("power.runicskills.equip_denied",
                        Component.translatable(power.getKey()), verdict.describe(power)));
                SyncSkillCapabilityCP.send(player);
                return;
            }

            cap.equipPower(power);
            SyncSkillCapabilityCP.send(player);
        });
        context.setPacketHandled(true);
    }

    public static void send(Power power, boolean equip) {
        ServerNetworking.sendToServer(new PowerEquipSP(power, equip));
    }
}
