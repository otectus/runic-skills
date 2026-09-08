package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.integration.common.GuardBudget;
import com.otectus.runicskills.client.gui.OverlayGuardGui;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Presentation only; UUID prevents a reused network entity id from inheriting old Guard. */
public record GuardStateCP(int entityId, UUID uuid, float points, int ticks) {
    public GuardStateCP {
        if (uuid == null || !Float.isFinite(points) || points < 0 || points > GuardBudget.MAX_POINTS
                || ticks < 0 || ticks > GuardBudget.MAX_TICKS || (points == 0) != (ticks == 0))
            throw new DecoderException("Invalid Guard state");
    }
    public GuardStateCP(FriendlyByteBuf buf) { this(buf.readVarInt(), buf.readUUID(), buf.readFloat(), buf.readVarInt()); }
    public void toBytes(FriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeUUID(uuid); buf.writeFloat(points); buf.writeVarInt(ticks); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> OverlayGuardGui.accept(this)));
        context.setPacketHandled(true);
    }
}
