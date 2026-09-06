package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.common.workshop.WorkshopFocusService;
import com.otectus.runicskills.common.workshop.WorkshopFocusService.Outcome;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.NoticeOverlayCP;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "Focus this workshop", "also this casting table", or "let it go".
 *
 * <p>The one client-to-server packet the workshop layer has, and deliberately the smallest thing
 * that can express those three intents: a menu id, an action, a position and the token the client
 * was last told. §15.3 lists what a client may identify — a menu action, a target controller, an
 * association, a selection — and this carries exactly those. It carries no player identity, no
 * skill level, no bonus and no duration, because every one of those is the server's answer rather
 * than the client's request.
 *
 * <p><b>What the server checks, in order, and why that order.</b> The sender must be a real player
 * and must not be flooding; the open container must be the one the request names; the token must be
 * the current one; and only then is anything asked of the world. Distance is checked before the
 * chunk is, inside {@link WorkshopFocusService}, so a fabricated coordinate is refused without the
 * server ever asking whether that chunk exists — W05's "denied without loading or mutating the
 * target" is a property of that ordering. A replayed token is stale by definition, because every
 * accepted change issues a new one, so L07's completed action token does nothing the second time.
 */
public class WorkshopFocusSP {

    /** What the player is asking for. */
    public enum Action {

        /** Claim the controller at the named position. */
        FOCUS,

        /** Add the casting table or basin at the named position to an existing claim. */
        ASSOCIATE,

        /** Drop the caller's own claim. The position is ignored. */
        RELEASE
    }

    private final int containerId;
    private final Action action;
    private final BlockPos target;
    private final long token;

    public WorkshopFocusSP(int containerId, Action action, BlockPos target, long token) {
        this.containerId = containerId;
        this.action = action;
        this.target = target == null ? BlockPos.ZERO : target;
        this.token = token;
    }

    public WorkshopFocusSP(FriendlyByteBuf buffer) {
        this.containerId = buffer.readVarInt();
        int ordinal = buffer.readByte();
        if (ordinal < 0 || ordinal >= Action.values().length) {
            throw new DecoderException("WorkshopFocusSP: unknown action " + ordinal);
        }
        this.action = Action.values()[ordinal];
        // A fixed-width position and a varlong: the whole payload is bounded at a couple of dozen
        // bytes by its own shape, so there is no count here for an oversized packet to lie about.
        this.target = buffer.readBlockPos();
        this.token = buffer.readVarLong();
        if (this.token < 0L) {
            throw new DecoderException("WorkshopFocusSP: negative token " + this.token);
        }
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        buffer.writeByte(this.action.ordinal());
        buffer.writeBlockPos(this.target);
        buffer.writeVarLong(this.token);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        // Admission on the network thread, before a main-thread task is scheduled for it: the same
        // rule every other server-bound packet in this mod follows (RS-150).
        ServerPlayer sender = context.getSender();
        if (sender == null || !PacketRateLimiter.allow(sender, "tc_workshop_focus", 5)) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!TConstructCompatibilityStatus.current().supports(Capability.WORKSHOP)) return;
            // A request that names a menu the player does not have open is a stale screen or a
            // forged packet; either way it describes a state the server is not in.
            if (player.containerMenu == null || player.containerMenu.containerId != this.containerId) {
                return;
            }

            Outcome outcome = switch (this.action) {
                case FOCUS -> WorkshopFocusService.focus(player, this.target, this.token);
                case ASSOCIATE -> WorkshopFocusService.associate(player, this.target, this.token);
                case RELEASE -> WorkshopFocusService.release(player, this.token);
            };
            NoticeOverlayCP.send(player, outcome.messageKey());
            // Always, refused or not: a client that was told no needs the token it should have used.
            WorkshopFocusService.publishStatus(player);
        });
        context.setPacketHandled(true);
    }

    /** Sends one focus request. Client side. */
    public static void send(int containerId, Action action, BlockPos target, long token) {
        ServerNetworking.sendToServer(new WorkshopFocusSP(containerId, action, target, token));
    }
}
