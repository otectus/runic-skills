package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.snapshot.ConfigSchema;
import com.otectus.runicskills.config.snapshot.GameplayConfigSnapshot;
import com.otectus.runicskills.network.ServerNetworking;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Server to client: the whole gameplay configuration, generated rather than hand-listed.
 *
 * <p>Replaces {@code CommonConfigSyncCP} and {@code DynamicConfigSyncCP}, which between them
 * carried 128 of 1,133 fields across ~800 lines of read/write/apply boilerplate and had no
 * mechanism that would ever notice a new field had been left out. The payload here is produced by
 * {@link ConfigSchema} from the config class itself, so "the server's configuration" means all of
 * it and stays that way as fields are added (RS10-005).
 *
 * <p>Sent on join and after every {@code /skillsreload}. Idempotent — receiving it twice simply
 * republishes the same values.
 */
public class GameplayConfigCP {

    /**
     * Upper bound on the payload. The real encoding is a little over 4 KB (925 ints, 110 floats,
     * 53 booleans, 38 int arrays, 7 string lists); 4 MB leaves room for lock lists that
     * namespace discovery can grow into the thousands, while still refusing to allocate whatever
     * a hostile server names. The per-field bounds inside {@link ConfigSchema} do the fine-grained
     * work; this is the outer limit on the allocation the length prefix alone can force.
     */
    private static final int MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;

    private final byte[] payload;

    public GameplayConfigCP() {
        this.payload = GameplayConfigSnapshot.encodeForClients();
    }

    public GameplayConfigCP(FriendlyByteBuf buffer) {
        int length = buffer.readVarInt();
        if (length < 0 || length > MAX_PAYLOAD_BYTES) {
            throw new DecoderException("GameplayConfigCP: payload length out of range: " + length);
        }
        if (buffer.readableBytes() < length) {
            throw new DecoderException("GameplayConfigCP: payload declares " + length
                    + " bytes but only " + buffer.readableBytes() + " remain");
        }
        this.payload = new byte[length];
        buffer.readBytes(this.payload);
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.payload.length);
        buffer.writeBytes(this.payload);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientHandler.apply(this.payload));
        context.setPacketHandled(true);
    }

    public static void sendToPlayer(ServerPlayer player) {
        ServerNetworking.sendToPlayer(new GameplayConfigCP(), player);
        ServerNetworking.sendToPlayer(new IntegrationStatusCP(), player);
    }

    public static void sendToAllPlayers() {
        ServerNetworking.sendToAllClients(new GameplayConfigCP());
        ServerNetworking.sendToAllClients(new IntegrationStatusCP());
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void apply(byte[] payload) {
            try {
                GameplayConfigSnapshot.applyFromServer(payload);
            } catch (ConfigSchema.ConfigSchemaMismatchException e) {
                // Different builds. Say so and leave, rather than playing on with a configuration
                // neither side agrees about — that is precisely the silent divergence this packet
                // replaces.
                RunicSkills.getLOGGER().error("Refusing this server's configuration: {}", e.getMessage());
                disconnect(Component.translatable("runicskills.disconnect.config_schema_mismatch"));
            } catch (IOException e) {
                RunicSkills.getLOGGER().error("Refusing this server's configuration: malformed payload", e);
                disconnect(Component.translatable("runicskills.disconnect.config_malformed"));
            }
        }

        private static void disconnect(Component reason) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getConnection() != null) {
                minecraft.getConnection().getConnection().disconnect(reason);
            }
        }
    }
}
