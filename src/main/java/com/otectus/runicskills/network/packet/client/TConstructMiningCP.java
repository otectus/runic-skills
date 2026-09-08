package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.integration.tconstruct.TConstructMiningState;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Change-only server snapshot for client mining prediction; never accepts client authority. */
public record TConstructMiningCP(float bonus, int slot) {
    public TConstructMiningCP {
        if (!Float.isFinite(bonus) || bonus < 0.0F || bonus > 1.0F || slot < 0 || slot > 8) {
            throw new DecoderException("TConstructMiningCP: field out of range");
        }
    }

    public TConstructMiningCP(FriendlyByteBuf buffer) {
        this(buffer.readFloat(), buffer.readUnsignedByte());
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeFloat(bonus);
        buffer.writeByte(slot);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> TConstructMiningState.accept(bonus, slot));
        context.setPacketHandled(true);
    }
}
