package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.network.ServerNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: notifies the client that a Power proc'd on this player. The client
 * spawns a brief enchant-rune particle burst at the player's position and (later) shows
 * a HUD ping. Spec is RUNIC_SKILLS_POWERS.md §3.2 "Proc feedback".
 * <p>
 * Kept tiny: a single string id + a server game-time tick-stamp. The client looks up the
 * Power's school from {@link com.otectus.runicskills.registry.RegistryPowers} to pick
 * the particle color/sound. Sound and color tinting are deferred to follow-up.
 */
public class PowerProcCP {

    private final String powerName;
    private final long gameTime;

    public PowerProcCP(String powerName, long gameTime) {
        this.powerName = powerName;
        this.gameTime = gameTime;
    }

    public PowerProcCP(FriendlyByteBuf buffer) {
        this.powerName = buffer.readUtf(Short.MAX_VALUE);
        this.gameTime = buffer.readLong();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.powerName, Short.MAX_VALUE);
        buffer.writeLong(this.gameTime);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientHandler.spawn(this.powerName));
        context.setPacketHandled(true);
    }

    public static void sendToPlayer(ServerPlayer player, String powerName) {
        if (player == null || player.level() == null) return;
        ServerNetworking.sendToPlayer(new PowerProcCP(powerName, player.level().getGameTime()), player);
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void spawn(String powerName) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.level() == null) return;
            for (int i = 0; i < 8; i++) {
                double dx = (player.getRandom().nextDouble() - 0.5) * 1.2;
                double dy = player.getRandom().nextDouble() * 1.6;
                double dz = (player.getRandom().nextDouble() - 0.5) * 1.2;
                player.level().addParticle(ParticleTypes.ENCHANT,
                        player.getX() + dx, player.getY() + dy, player.getZ() + dz,
                        0, 0.05, 0);
            }
        }
    }
}
