package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.client.integration.tconstruct.TinkerStationPanel;
import com.otectus.runicskills.common.workshop.WorkshopFocusService.Focus;
import com.otectus.runicskills.network.ServerNetworking;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Where one player's workshop focus stands, twice a second at most while a panel is open.
 *
 * <p>Everything a client is allowed to know and nothing it is allowed to decide: who holds the
 * workshop in front of them, how long it lasts, what the speed bonus actually is, and the token it
 * must quote to change any of that. The remaining time is a <em>count</em>, not a stream — §15.3 is
 * explicit that a countdown is derived locally from one server value rather than sent per tick, and
 * that is why this is sent at ten-tick intervals and the panel does the arithmetic.
 *
 * <p>The {@code menuBlock} fields are what let the client's Focus button name a position without
 * knowing anything about Tinker's Construct: the server reads the block behind the menu the player
 * has open and tells them where it is, and validates the position again when it comes back. A
 * client that sends a different one is refused by the ordinary distance and target checks, so the
 * round trip is a convenience rather than a trust.
 */
public class WorkshopStatusCP {

    /** Long enough for any player name; short enough that the field cannot be used as a payload. */
    private static final int MAX_NAME_CHARS = 64;

    /** The block behind the open menu is not something a focus applies to. */
    public static final byte MENU_NONE = 0;

    /** The block behind the open menu is a controller a focus may be claimed on. */
    public static final byte MENU_CONTROLLER = 1;

    /** The block behind the open menu is a casting table or basin. */
    public static final byte MENU_CASTING = 2;

    private final boolean focused;
    private final BlockPos controller;
    private final int associations;
    private final int remainingTicks;
    private final long token;
    private final int bonusPercent;
    private final boolean automationRewards;
    private final String owner;
    private final BlockPos menuBlock;
    private final byte menuKind;

    public WorkshopStatusCP(boolean focused, BlockPos controller, int associations,
                            int remainingTicks, long token, int bonusPercent,
                            boolean automationRewards, String owner, BlockPos menuBlock,
                            byte menuKind) {
        this.focused = focused;
        this.controller = controller == null ? BlockPos.ZERO : controller;
        this.associations = associations;
        this.remainingTicks = remainingTicks;
        this.token = token;
        this.bonusPercent = bonusPercent;
        this.automationRewards = automationRewards;
        this.owner = owner == null ? "" : owner;
        this.menuBlock = menuBlock == null ? BlockPos.ZERO : menuBlock;
        this.menuKind = menuKind;
    }

    public WorkshopStatusCP(FriendlyByteBuf buffer) {
        this.focused = buffer.readBoolean();
        this.controller = buffer.readBlockPos();
        this.associations = buffer.readVarInt();
        this.remainingTicks = buffer.readVarInt();
        this.token = buffer.readVarLong();
        this.bonusPercent = buffer.readVarInt();
        this.automationRewards = buffer.readBoolean();
        this.owner = buffer.readUtf(MAX_NAME_CHARS);
        this.menuBlock = buffer.readBlockPos();
        this.menuKind = buffer.readByte();
        if (this.associations < 0 || this.remainingTicks < 0 || this.bonusPercent < 0
                || this.token < 0L || this.menuKind < MENU_NONE || this.menuKind > MENU_CASTING) {
            throw new DecoderException("WorkshopStatusCP: field out of range");
        }
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.focused);
        buffer.writeBlockPos(this.controller);
        buffer.writeVarInt(this.associations);
        buffer.writeVarInt(this.remainingTicks);
        buffer.writeVarLong(this.token);
        buffer.writeVarInt(this.bonusPercent);
        buffer.writeBoolean(this.automationRewards);
        buffer.writeUtf(this.owner, MAX_NAME_CHARS);
        buffer.writeBlockPos(this.menuBlock);
        buffer.writeByte(this.menuKind);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> TinkerStationPanel.acceptStatus(
                this.focused, this.controller, this.associations, this.remainingTicks, this.token,
                this.bonusPercent, this.automationRewards, this.owner, this.menuBlock,
                this.menuKind));
        context.setPacketHandled(true);
    }

    /**
     * Sends {@code player} their own status.
     *
     * @param focus their claim, or {@code null} when they hold none
     */
    public static void send(ServerPlayer player, Focus focus, long remainingTicks,
                            int bonusPercent, boolean automationRewards, String owner,
                            BlockPos menuBlock, byte menuKind) {
        if (player == null) return;
        ServerNetworking.sendToPlayer(new WorkshopStatusCP(
                focus != null, focus == null ? BlockPos.ZERO : focus.controller(),
                focus == null ? 0 : focus.associations().size(),
                (int) Math.min(Integer.MAX_VALUE, Math.max(0L, remainingTicks)),
                focus == null ? 0L : focus.revision(),
                Math.max(0, bonusPercent), automationRewards, owner, menuBlock, menuKind), player);
    }
}
