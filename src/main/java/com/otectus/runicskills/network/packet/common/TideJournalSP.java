package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.integration.tide.TideJournal;
import com.otectus.runicskills.network.*;
import com.otectus.runicskills.network.packet.client.TideJournalCP;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** A bounded journal selection intent. The server supplies knowledge, conditions and progression. */
public record TideJournalSP(int request, ResourceLocation fish, int page, int filter, boolean select) {
    public TideJournalSP(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), new ResourceLocation(buffer.readUtf(256)), buffer.readVarInt(), buffer.readUnsignedByte(), buffer.readBoolean());
        if (page < 0 || page > 512 || filter > 3) throw new io.netty.handler.codec.DecoderException("Invalid journal request");
    }
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(request); buffer.writeUtf(fish.toString(), 256); buffer.writeVarInt(page); buffer.writeByte(filter); buffer.writeBoolean(select);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); var player = context.getSender();
        if (player != null && PacketRateLimiter.allow(player, "tide_journal", 5)) context.enqueueWork(() ->
                ServerNetworking.sendToPlayer(new TideJournalCP(request, TideJournal.query(player, fish, page, filter, select)), player));
        context.setPacketHandled(true);
    }
}
