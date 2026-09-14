package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.registry.skill.SkillVisuals;
import com.otectus.runicskills.registry.skill.SkillVisualsManager;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Full replacement; an empty snapshot clears the preceding server's presentation rules. */
public final class SkillVisualsSyncCP {
    private final Map<ResourceLocation, SkillVisuals> visuals;
    public SkillVisualsSyncCP() { visuals = SkillVisualsManager.serverSnapshot(); }

    public SkillVisualsSyncCP(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > SkillVisualsManager.MAX_OVERRIDES)
            throw new DecoderException("SkillVisualsSyncCP: invalid override count " + count);
        Map<ResourceLocation, SkillVisuals> decoded = new HashMap<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation skill = readId(buffer);
            SkillVisuals next = new SkillVisuals(readOptional(buffer), readOptional(buffer), readOptional(buffer));
            if (decoded.putIfAbsent(skill, next) != null)
                throw new DecoderException("SkillVisualsSyncCP: duplicate skill " + skill);
        }
        visuals = Map.copyOf(decoded);
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(visuals.size());
        new TreeMap<>(visuals).forEach((skill, value) -> {
            buffer.writeUtf(skill.toString(), SkillVisualsManager.MAX_ID_LENGTH);
            writeOptional(buffer, value.overviewIcon());
            writeOptional(buffer, value.detailIcon());
            writeOptional(buffer, value.background());
        });
    }

    public void apply() { SkillVisualsManager.receive(visuals); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(this::apply);
        context.setPacketHandled(true);
    }
    public static void sendToPlayer(ServerPlayer player) { ServerNetworking.sendToPlayer(new SkillVisualsSyncCP(), player); }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        String value = buffer.readUtf(SkillVisualsManager.MAX_ID_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new DecoderException("SkillVisualsSyncCP: invalid resource id");
        return id;
    }
    private static ResourceLocation readOptional(FriendlyByteBuf buffer) { return buffer.readBoolean() ? readId(buffer) : null; }
    private static void writeOptional(FriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeBoolean(id != null);
        if (id != null) buffer.writeUtf(id.toString(), SkillVisualsManager.MAX_ID_LENGTH);
    }
}
