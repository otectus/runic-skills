package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.util.CapabilityBounds;
import com.otectus.runicskills.common.util.PacketBounds;
import com.otectus.runicskills.event.PassiveLevelUpEvent;
import com.otectus.runicskills.integration.quests.RunicQuestBridge;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPassives;
import com.otectus.runicskills.registry.RegistryTitles;
import com.otectus.runicskills.registry.passive.Passive;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: change one passive by a signed number of levels, in a single request.
 *
 * <p>Replaces {@code PassiveLevelUpSP} and {@code PassiveLevelDownSP}, which carried one level
 * each. The skills screen advertises bulk levelling — Shift for 5, Ctrl for 10, Alt for the rest —
 * and implemented it by sending that many packets in a loop, while the rate limiter admits one
 * packet of a given type per player every two ticks. Every packet after the first was therefore
 * discarded in silence, so a Ctrl-click reliably bought exactly one level and told the player
 * nothing (RS10-007).
 *
 * <p>The server decides the outcome: it computes the furthest level the player is actually
 * entitled to, applies the change once, reconciles attributes, titles and quests once, and sends
 * one capability sync. The client's requested amount is a ceiling on the work, never an
 * instruction.
 *
 * <p><b>Event contract.</b> {@link PassiveLevelUpEvent} fires once per accepted request, carrying
 * the level before and the level after — not once per level crossed. Cancelling it cancels the
 * whole adjustment, so a batch is atomic; subscribers that compare {@code oldLevel} against
 * {@code newLevel}, which is how the down path already used this event, are unaffected.
 */
public class AdjustPassiveSP {

    /**
     * Widest adjustment the wire accepts. The screen's "Alt = all remaining" never exceeds a
     * passive's level count, and {@link CapabilityBounds#MAX_PASSIVE_LEVEL} is the ceiling any
     * configuration can declare — so this bounds the request to something a legitimate client
     * could ask for, and stops {@code Integer.MIN_VALUE} from reaching the arithmetic below where
     * negation would overflow.
     */
    private static final int MAX_ADJUSTMENT = CapabilityBounds.MAX_PASSIVE_LEVEL;

    private final String passiveId;
    private final int amount;

    public AdjustPassiveSP(Passive passive, int amount) {
        this.passiveId = passive.getName();
        this.amount = amount;
    }

    public AdjustPassiveSP(FriendlyByteBuf buffer) {
        this.passiveId = buffer.readUtf(PacketBounds.MAX_CONTENT_ID_CHARS);
        if (!PacketBounds.isContentIdValid(this.passiveId)) {
            throw new DecoderException("AdjustPassiveSP: malformed passive id");
        }
        this.amount = buffer.readVarInt() - MAX_ADJUSTMENT;
        if (this.amount < -MAX_ADJUSTMENT || this.amount > MAX_ADJUSTMENT) {
            throw new DecoderException("AdjustPassiveSP: amount out of range: " + this.amount);
        }
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.passiveId, PacketBounds.MAX_CONTENT_ID_CHARS);
        // Biased so the varint is never negative: writeVarInt encodes a negative int as five
        // bytes, and the decoder's range check is simpler against a known-positive value.
        buffer.writeVarInt(this.amount + MAX_ADJUSTMENT);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        // Admission control on the network thread, before enqueueWork, so a flooding client does
        // not make the server allocate a lambda and schedule a main-thread task per packet
        // (RS-150). One request now carries a whole bulk click, so the limiter no longer has to
        // choose between throttling floods and dropping legitimate work.
        ServerPlayer sender = context.getSender();
        if (sender == null || !PacketRateLimiter.allow(sender, "adjust_passive", 2)) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> apply(context.getSender()));
        context.setPacketHandled(true);
    }

    private void apply(ServerPlayer player) {
        if (player == null || this.amount == 0) return;

        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return;

        Passive passive = RegistryPassives.getPassive(this.passiveId);
        if (passive == null) return;

        int currentLevel = capability.getPassiveLevel(passive);
        int targetLevel = this.amount > 0
                ? highestAffordableLevel(capability, passive, currentLevel, this.amount)
                : Math.max(0, currentLevel - Math.min(this.amount == Integer.MIN_VALUE
                        ? MAX_ADJUSTMENT : -this.amount, MAX_ADJUSTMENT));

        if (targetLevel == currentLevel) return;

        // One cancellable event for the whole adjustment: a batch that applied some levels and
        // then stopped would leave the player somewhere neither they nor the subscriber asked for.
        if (MinecraftForge.EVENT_BUS.post(new PassiveLevelUpEvent(player, passive, currentLevel, targetLevel))) {
            // Tell the client what actually happened; it optimistically drew the new level.
            SyncSkillCapabilityCP.send(player);
            return;
        }

        if (targetLevel > currentLevel) {
            capability.addPassiveLevel(passive, targetLevel - currentLevel);
        } else {
            capability.subPassiveLevel(passive, currentLevel - targetLevel);
        }

        // Once each, not once per level. These walk every passive and every title.
        RunicQuestBridge.onPassiveLevelChanged(player, passive, currentLevel, targetLevel);
        RegistryAttributes.modifierAttributes(player);
        RegistryTitles.syncTitles(player);
        SyncSkillCapabilityCP.send(player);
    }

    /**
     * The highest level the player can reach right now, without exceeding {@code requested}.
     *
     * <p>Walks one level at a time because each level has its own skill-level requirement, and a
     * player may be able to afford the next one but not the one after. Bounded by the passive's own
     * level count, so the loop cannot run away on a malformed request.
     */
    private static int highestAffordableLevel(SkillCapability capability, Passive passive,
                                              int currentLevel, int requested) {
        int[] requirements = passive.levelsRequired;
        if (requirements == null) return currentLevel;

        if (RegistryPassives.isDisabled(passive)) return currentLevel;

        int skillLevel = capability.getSkillLevel(passive.getSkill());
        int level = currentLevel;
        int ceiling = Math.min(requirements.length, currentLevel + Math.min(requested, MAX_ADJUSTMENT));
        while (level < ceiling && skillLevel >= requirements[level]) {
            level++;
        }
        return level;
    }

    public static void send(Passive passive, int amount) {
        if (amount == 0) return;
        ServerNetworking.sendToServer(new AdjustPassiveSP(passive, amount));
    }
}
