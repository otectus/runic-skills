package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.client.integration.tconstruct.TinkerStationPanel;
import com.otectus.runicskills.common.util.PacketBounds;
import com.otectus.runicskills.network.ServerNetworking;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * What this player, specifically, would get from the station they are looking at.
 *
 * <p>§14.2 asks for a quote that is generated from the same calculation as the commit, and that is
 * what makes this worth a packet at all: the station's own cached result is shared by everyone with
 * the menu open, so it can never carry one player's perks. The preview here is a private copy the
 * server transformed for one player, exactly as it will transform the real copy when that player
 * takes it.
 *
 * <p>Sent only when the station's inputs or its native result actually change, and at most twice a
 * second. The revision travels with it so a stale panel can be told apart from a current one; it is
 * a display fact, not an authorisation — taking the result goes through the native menu and the
 * ordinary lock refusal, neither of which asks the client anything.
 */
public class StationQuoteCP {

    /** Longer than any operation name this mod produces; short enough to bound the field. */
    private static final int MAX_KIND_CHARS = 32;

    private final int menuId;
    private final long revision;
    private final String kind;
    private final String recipeId;
    private final ItemStack preview;

    public StationQuoteCP(int menuId, long revision, String kind, String recipeId,
                          ItemStack preview) {
        this.menuId = menuId;
        this.revision = revision;
        this.kind = kind == null ? "" : kind;
        this.recipeId = recipeId == null ? "" : recipeId;
        this.preview = preview == null ? ItemStack.EMPTY : preview;
    }

    public StationQuoteCP(FriendlyByteBuf buffer) {
        this.menuId = buffer.readVarInt();
        this.revision = buffer.readVarLong();
        this.kind = buffer.readUtf(MAX_KIND_CHARS);
        this.recipeId = buffer.readUtf(PacketBounds.MAX_CONTENT_ID_CHARS);
        if (!this.recipeId.isEmpty() && !PacketBounds.isContentIdValid(this.recipeId)) {
            throw new DecoderException("StationQuoteCP: malformed recipe id");
        }
        if (this.revision < 0L) {
            throw new DecoderException("StationQuoteCP: negative revision " + this.revision);
        }
        this.preview = buffer.readItem();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.menuId);
        buffer.writeVarLong(this.revision);
        buffer.writeUtf(this.kind, MAX_KIND_CHARS);
        buffer.writeUtf(this.recipeId, PacketBounds.MAX_CONTENT_ID_CHARS);
        buffer.writeItem(this.preview);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> TinkerStationPanel.acceptQuote(
                this.menuId, this.revision, this.kind, this.recipeId, this.preview));
        context.setPacketHandled(true);
    }

    /** Sends one player the quote the bridge computed for them. */
    public static void send(ServerPlayer player, int menuId, long revision, String kind,
                            String recipeId, ItemStack preview) {
        if (player == null) return;
        ServerNetworking.sendToPlayer(
                new StationQuoteCP(menuId, revision, kind, recipeId, preview), player);
    }
}
