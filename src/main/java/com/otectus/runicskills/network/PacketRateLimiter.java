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
        if (player.getServer() == null) return false;
        String key = player.getUUID() + ":" + packetType;
        long currentTick = player.getServer().getTickCount();
        Long lastTick = lastPacketTick.get(key);
        // getTickCount() restarts at 0 with the server, so a stale baseline from a previous run
        // in the same JVM (an integrated server returning to the main menu and loading another
        // world) made every delta negative and rate-limited every packet indefinitely. Treat a
        // backwards clock as expired (RS-132).
        if (lastTick != null && currentTick >= lastTick && currentTick - lastTick < cooldownTicks) {
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

    /**
     * Drops every baseline. Called on server stop so the next world in this JVM starts from a
     * clean tick baseline rather than inheriting the previous one (RS-132).
     */
    public static void clear() {
        lastPacketTick.clear();
    }
}
