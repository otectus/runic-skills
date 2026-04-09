package com.otectus.runicskills.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-player, per-packet-type rate limiter for server-bound packets.
 * Uses server tick count for timing — no wall-clock dependency.
 */
public class PacketRateLimiter {
    private static final Map<String, Long> lastPacketTick = new ConcurrentHashMap<>();

    /**
     * Check if this packet type is allowed for this player.
     * @param player the sender
     * @param packetType a unique key identifying the packet type
     * @param cooldownTicks minimum ticks between allowed packets of this type per player
     * @return true if allowed, false if rate-limited
     */
    public static boolean allow(ServerPlayer player, String packetType, int cooldownTicks) {
        String key = player.getUUID() + ":" + packetType;
        long currentTick = player.getServer().getTickCount();
        Long lastTick = lastPacketTick.get(key);
        if (lastTick != null && currentTick - lastTick < cooldownTicks) {
            return false;
        }
        lastPacketTick.put(key, currentTick);
        return true;
    }

    /**
     * Clean up entries for a disconnected player.
     */
    public static void clearPlayer(UUID playerUUID) {
        String prefix = playerUUID + ":";
        lastPacketTick.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
