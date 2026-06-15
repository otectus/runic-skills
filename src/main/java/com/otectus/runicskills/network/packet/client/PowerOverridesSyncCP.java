package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.registry.powers.PowerOverrides;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server → client: pushes {@link PowerOverrides} loaded from datapack JSON. Sent on player
 * login and after {@code /skillsreload}. Mirrors {@link PerkGroupsSyncCP}.
 * <p>
 * The client uses overrides for tooltip rendering (showing tuned values) and for prediction
 * in the equip UI; server-side gating still wins.
 */
public class PowerOverridesSyncCP {

    private final List<PowerOverrides> overrides;

    public PowerOverridesSyncCP() {
        this.overrides = new ArrayList<>(PowerOverridesManager.all());
    }

    private static final int MAX_OVERRIDES = 8192;
    private static final int MAX_VALUES_PER_OVERRIDE = 1024;

    public PowerOverridesSyncCP(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_OVERRIDES) {
            throw new DecoderException("PowerOverridesSyncCP: override count out of range: " + count);
        }
        List<PowerOverrides> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buffer.readResourceLocation();
            int reqLvl = buffer.readVarInt();
            int icd = buffer.readVarInt();
            int valCount = buffer.readVarInt();
            if (valCount < 0 || valCount > MAX_VALUES_PER_OVERRIDE) {
                throw new DecoderException("PowerOverridesSyncCP: value count out of range: " + valCount);
            }
            Map<String, Double> values = new LinkedHashMap<>(valCount);
            for (int j = 0; j < valCount; j++) {
                String k = buffer.readUtf(Short.MAX_VALUE);
                values.put(k, buffer.readDouble());
            }
            list.add(new PowerOverrides(id,
                    reqLvl == Integer.MIN_VALUE ? PowerOverrides.UNSET : reqLvl,
                    icd == Integer.MIN_VALUE ? PowerOverrides.UNSET : icd,
                    values));
        }
        this.overrides = list;
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.overrides.size());
        for (PowerOverrides ov : this.overrides) {
            buffer.writeResourceLocation(ov.id());
            buffer.writeVarInt(ov.requiredSkillLevel());
            buffer.writeVarInt(ov.icdTicks());
            buffer.writeVarInt(ov.values().size());
            for (Map.Entry<String, Double> e : ov.values().entrySet()) {
                buffer.writeUtf(e.getKey(), Short.MAX_VALUE);
                buffer.writeDouble(e.getValue());
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            Map<ResourceLocation, PowerOverrides> next = new HashMap<>();
            for (PowerOverrides ov : this.overrides) next.put(ov.id(), ov);
            PowerOverridesManager.replaceAll(next);
        });
        context.setPacketHandled(true);
    }

    public static void sendToPlayer(ServerPlayer player) {
        ServerNetworking.sendToPlayer(new PowerOverridesSyncCP(), player);
    }

    public static void sendToAll() {
        var server = com.otectus.runicskills.RunicSkills.server;
        if (server == null) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sendToPlayer(sp);
        }
    }
}
