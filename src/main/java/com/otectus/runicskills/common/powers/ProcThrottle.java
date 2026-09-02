package com.otectus.runicskills.common.powers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side admission control for proc presentation packets.
 *
 * <p>A proc packet is sent from inside combat handlers, so a Power that keys off "every hit" fires
 * as fast as the player can swing — and after the tracking change it is sent to everyone nearby,
 * not just the caster, which multiplies the same burst by the number of players in range. This
 * collapses repeats of the same {@code (caster, power, target)} inside a short window down to one
 * packet. It is deliberately on the server: dropping the packet costs nothing, while dropping it
 * client-side would still have paid for the bandwidth.
 *
 * <p>The window is short enough that a genuinely distinct proc still reads as one — the HUD's own
 * coalescing handles anything that slips through by incrementing a counter rather than adding a
 * card. Entries are pruned on read, so nothing accumulates for a player who stops fighting, and
 * {@link PowerRuntime#clearPlayer} drops the rest on logout.
 */
public final class ProcThrottle {

    /** Default coalescing window. The design allows 2–5 ticks; 3 is the middle of it. */
    public static final int DEFAULT_WINDOW_TICKS = 3;

    private static final Map<UUID, Map<String, Long>> STORE = new HashMap<>();

    private ProcThrottle() {
    }

    /**
     * Whether this proc should be presented, recording it if so.
     *
     * @param caster    who procced
     * @param powerName the Power's path-only id
     * @param targetId  entity id of what it acted on, or {@code -1} for a self/area proc — part of
     *                  the key so a cleave that procs on three mobs in one tick still shows three
     * @param now       current game time in ticks
     */
    public static synchronized boolean admit(UUID caster, String powerName, int targetId, long now) {
        return admit(caster, powerName, targetId, now, DEFAULT_WINDOW_TICKS);
    }

    public static synchronized boolean admit(UUID caster, String powerName, int targetId, long now,
                                             int windowTicks) {
        if (caster == null || powerName == null) return false;
        if (windowTicks <= 0) return true;
        Map<String, Long> m = STORE.computeIfAbsent(caster, k -> new HashMap<>());
        String key = powerName + '@' + targetId;
        Long until = m.get(key);
        if (until != null && until > now) return false;
        m.put(key, now + windowTicks);
        // Prune here rather than on a tick handler: the map is only ever this player's recent
        // procs, so a full pass is a handful of entries and it means no periodic work at all.
        m.values().removeIf(expiry -> expiry <= now);
        if (m.isEmpty()) STORE.remove(caster);
        return true;
    }

    static synchronized void clear(UUID id) {
        STORE.remove(id);
    }

    static synchronized void clearAll() {
        STORE.clear();
    }
}
