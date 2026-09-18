package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.common.capability.SkillCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One bit about somebody else: whether the player with this network id holds Titan's Grip.
 *
 * <p><b>Why this exists at all.</b> The skill capability is synced to its owner only
 * ({@code SyncSkillCapabilityCP} goes out through {@code PacketDistributor.PLAYER}), which is the
 * right scope for everything else it carries — nobody needs a stranger's perk ranks or cooldowns.
 * Titan's Grip is the exception, because the <em>presentation</em> of one player's off-hand is
 * decided on every other player's client: Better Combat's mixin on {@code Player#getItemBySlot}
 * returns an empty off-hand for any two-handed wielder, and
 * {@code MixPlayerOffhandSlot} only undoes that for a player it can prove holds the perk. On a
 * remote client it could not prove it, so the shield the server had already sent was hidden again
 * on arrival — {@code ServerEntity} reads {@code LivingEntity#getItemBySlot} to build
 * {@code ClientboundSetEquipmentPacket}, the client applies it through
 * {@code Player#setItemSlot}, and the stack really is sitting in {@code inventory.offhand}; only
 * the predicate was missing.
 *
 * <p><b>One bit, and only about the perk.</b> The boolean is
 * {@code RegistryPerks.TITANS_GRIP.isEnabled(player)} — perk taken, active and at level — and
 * nothing about what the player is carrying. The carried items are already replicated to every
 * tracking client by vanilla equipment sync, so the client re-derives the weapon and shield halves
 * of the rule itself and this packet stays a fact about the build rather than a snapshot of a hand
 * that changes several times a second. {@code TitansGripSync} sends it on
 * {@link net.minecraftforge.event.entity.player.PlayerEvent.StartTracking} and on any change, and
 * on nothing else.
 *
 * <p><b>Client state.</b> Stored on the receiving client's own copy of the subject's
 * {@link SkillCapability} — every player entity has one on both sides — as a transient flag that is
 * never serialised and never sent back. That keeps the reader ({@code TwoHandedExemption}) in common
 * code with no client import and no parallel entity map to keep in step with entity lifetime: the
 * flag dies with the entity that holds it.
 *
 * <p>Presentation only. Blocking, the damage bonus and Spartan's penalties are all resolved on the
 * server, where the real capability answers; a forged or missing packet can change what a bystander
 * sees and nothing else.
 */
public record TitansGripSyncCP(int entityId, boolean held) {

    public TitansGripSyncCP(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeBoolean(held);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        // DistExecutor keeps the client-only body in a separate method reference, so the dedicated
        // server never resolves Minecraft.class when this record is loaded to register the channel.
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::apply));
        context.setPacketHandled(true);
    }

    /**
     * Applies the flag to the addressed entity, or drops the packet.
     *
     * <p>Three things can legitimately be absent by the time this runs on the client thread: the
     * level (world unload), the entity (it left the tracking range in the same tick the packet was
     * in flight, or its id was already recycled onto something else) and the capability (attachment
     * has not happened yet). None of them is an error, and none of them may throw out of the
     * network handler.
     */
    private void apply() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = client.level.getEntity(entityId);
        if (!(entity instanceof Player player)) return;
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return;
        capability.setRemoteTitansGrip(held);
    }
}
