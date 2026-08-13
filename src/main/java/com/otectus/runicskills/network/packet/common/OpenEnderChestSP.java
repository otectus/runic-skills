package com.otectus.runicskills.network.packet.common;

import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.registry.RegistryPerks;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraftforge.network.NetworkEvent;


public class OpenEnderChestSP {
    public OpenEnderChestSP() {
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        // Admission control runs on the network thread, BEFORE enqueueWork. Rate limiting used
        // to happen inside the scheduled task, so a flooding client still allocated a lambda and
        // queued a main-thread task for every packet — the limiter discarded the work only after
        // the server had already paid to schedule it (RS-150).
        ServerPlayer sender = context.getSender();
        if (sender == null || !PacketRateLimiter.allow(sender, "open_ender_chest", 5)) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player != null) {
                // C6: Validate WORMHOLE_STORAGE perk exists and is enabled
                if (RegistryPerks.WORMHOLE_STORAGE == null) return;
                if (!RegistryPerks.WORMHOLE_STORAGE.get().isEnabled(player)) return;
                // Vanilla checks these before opening any container; this path did not, so a
                // client could open its ender chest while dead, spectating or asleep — states in
                // which the player is not supposed to be able to move items at all (RS-151).
                if (!player.isAlive() || player.isSpectator() || player.isSleeping()) return;

                PlayerEnderChestContainer enderChest = player.getEnderChestInventory();
                SimpleMenuProvider enderChestContainer = new SimpleMenuProvider((id, pl, b) -> ChestMenu.threeRows(id, pl, enderChest), Component.translatable(RegistryPerks.WORMHOLE_STORAGE.get().getKey()));
                player.openMenu(enderChestContainer);
            }
        });
        context.setPacketHandled(true);
    }

    public OpenEnderChestSP(FriendlyByteBuf buffer) {
    }

    public void toBytes(FriendlyByteBuf buffer) {
    }

    public static void send() {
        ServerNetworking.sendToServer(new OpenEnderChestSP());
    }
}


