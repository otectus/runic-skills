package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.client.gui.OverlayNoticeGui;
import com.otectus.runicskills.network.ServerNetworking;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client packet for transient over-GUI denial banners (see {@link OverlayNoticeGui}).
 *
 * <p>Carries a translation {@code key} plus zero or more argument strings. Each argument is
 * itself treated as a translation key on the client and wrapped in {@link Component#translatable}
 * — translation falls back to the literal string for plain values (e.g. a numeric level), so the
 * same encoding works for both nested-key args (a school/skill name) and literal args. This keeps
 * localization client-side without serializing whole {@link Component} trees.
 */
public class NoticeOverlayCP {
    private final String key;
    private final String[] args;

    public NoticeOverlayCP(String key, String... args) {
        this.key = key;
        this.args = args;
    }

    public NoticeOverlayCP(FriendlyByteBuf buffer) {
        this.key = buffer.readUtf();
        int n = buffer.readVarInt();
        this.args = new String[n];
        for (int i = 0; i < n; i++) this.args[i] = buffer.readUtf();
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.key);
        buffer.writeVarInt(this.args.length);
        for (String arg : this.args) buffer.writeUtf(arg);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            Object[] resolved = new Object[this.args.length];
            for (int i = 0; i < this.args.length; i++) resolved[i] = Component.translatable(this.args[i]);
            MutableComponent component = Component.translatable(this.key, resolved);
            OverlayNoticeGui.show(component);
        });
        context.setPacketHandled(true);
    }

    public static void send(Player player, String key, String... args) {
        if (player instanceof ServerPlayer serverPlayer) {
            ServerNetworking.sendToPlayer(new NoticeOverlayCP(key, args), serverPlayer);
        }
    }
}
