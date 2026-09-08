package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.integration.common.IntegrationAvailability.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import io.netty.handler.codec.DecoderException;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/** Bounded server evidence for the same availability predicate used by gameplay. */
public final class IntegrationStatusCP {
    private final Map<IntegrationModule, Evidence> evidence;
    public IntegrationStatusCP() { evidence = IntegrationRuntime.localEvidence(); }
    public IntegrationStatusCP(FriendlyByteBuf buf) {
        Map<IntegrationModule, Evidence> next = new EnumMap<>(IntegrationModule.class);
        for (IntegrationModule module : IntegrationModule.values()) {
            if (!buf.readBoolean()) continue;
            String version = buf.readUtf(128), sha256 = buf.readUtf(64);
            int count = buf.readVarInt();
            if (count < 0 || count > Capability.values().length) throw new DecoderException("Integration capability count");
            Map<Capability, String> entries = new EnumMap<>(Capability.class);
            for (int i = 0; i < count; i++) {
                Capability capability = buf.readEnum(Capability.class);
                if (entries.put(capability, buf.readUtf(512)) != null) throw new DecoderException("Duplicate capability");
            }
            next.put(module, new Evidence(version, sha256, entries));
        }
        evidence = Map.copyOf(next);
    }
    public void toBytes(FriendlyByteBuf buf) {
        for (IntegrationModule module : IntegrationModule.values()) {
            Evidence item = evidence.get(module);
            buf.writeBoolean(item != null);
            if (item == null) continue;
            buf.writeUtf(item.version(), 128); buf.writeUtf(item.sha256(), 64);
            buf.writeVarInt(item.capabilities().size());
            item.capabilities().forEach((capability, reason) -> { buf.writeEnum(capability); buf.writeUtf(reason, 512); });
        }
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> IntegrationRuntime.receive(evidence));
        context.setPacketHandled(true);
    }
}
