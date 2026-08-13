package com.otectus.runicskills.common.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which containers have already paid a player out, so a reward tied to "open a
 * container" cannot be farmed by reopening the same one.
 *
 * <p>{@code LOCKSMITH} granted vanilla experience on every {@code PlayerContainerEvent.Open} with
 * no cooldown and no per-container state. Because vanilla XP is the single authoritative currency
 * for skill level-ups, spamming right-click on a crafting table was a direct progression bypass
 * that cost nothing and consumed nothing — every other XP grant in the mod is funded by a consumed
 * resource (RS-001). This ledger is what makes the perk pay out for *finding* containers rather
 * than for clicking one repeatedly.
 *
 * <p>Two independent limits apply, because either alone is farmable:
 * <ul>
 *   <li>a per-container cooldown, so the same chest pays at most once per configured window; and</li>
 *   <li>a per-player floor between any two payouts, so a built wall of hundreds of distinct
 *       chests degrades to a slow trickle rather than restoring the original faucet.</li>
 * </ul>
 *
 * <p>Retention is bounded at {@value #MAX_TRACKED_PER_PLAYER} containers per player by an
 * access-ordered LRU, so a player touring a large base cannot grow this without limit. Eviction
 * only ever costs a player an extra payout on a container they have not visited in a long time.
 *
 * <p>State is transient: it is dropped on logout and on server shutdown. A player could relog to
 * clear their own cooldowns, but a relog costs far more time than the reward is worth, so this is
 * a rate limit rather than a hole. Persisting it would put an unbounded position map into player
 * NBT for a minor perk.
 *
 * <p>All times are {@code Level#getGameTime()} ticks — monotonic, persisted with the world, and
 * unaffected by server restarts, unlike {@code server.getTickCount()} (RS-041, RS-132).
 */
public final class ContainerRewardLedger {

    /** Per-player cap on remembered containers. 256 covers any realistic base without unbounded growth. */
    private static final int MAX_TRACKED_PER_PLAYER = 256;

    private static final Map<UUID, Map<Long, Long>> OPENED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_REWARD = new ConcurrentHashMap<>();

    private ContainerRewardLedger() {
    }

    /**
     * Claims a reward for {@code container}, returning {@code true} only if both the per-container
     * cooldown and the per-player floor have elapsed. A successful claim records the time; a
     * refused one changes nothing.
     *
     * @param player         the player opening the container
     * @param container      an identity for the container, stable across opens (see {@link #key})
     * @param gameTime       current {@code Level#getGameTime()}
     * @param cooldownTicks  ticks before the same container may pay out again
     * @param minGapTicks    ticks required between any two payouts for this player
     */
    public static boolean claim(UUID player, long container, long gameTime,
                                long cooldownTicks, long minGapTicks) {
        Long last = LAST_REWARD.get(player);
        // gameTime >= last for the same reason as the per-container check below: a world clock
        // that moved backwards must read as expired, not lock the player out until it catches up.
        if (last != null && gameTime >= last && gameTime - last < minGapTicks) return false;

        Map<Long, Long> seen = OPENED.computeIfAbsent(player, id -> Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
                        return size() > MAX_TRACKED_PER_PLAYER;
                    }
                }));

        synchronized (seen) {
            Long opened = seen.get(container);
            // A negative delta means the world clock moved backwards (a restored backup); treat
            // that as expired rather than locking the player out until the clock catches up.
            if (opened != null && gameTime >= opened && gameTime - opened < cooldownTicks) return false;
            seen.put(container, gameTime);
        }
        LAST_REWARD.put(player, gameTime);
        return true;
    }

    /** Packs a dimension and block position into one identity. */
    public static long key(String dimensionId, long packedPos) {
        return packedPos * 31L + dimensionId.hashCode();
    }

    /** Drops a player's history on logout so the maps do not retain entries for absent players. */
    public static void forget(UUID player) {
        OPENED.remove(player);
        LAST_REWARD.remove(player);
    }

    /**
     * Clears all state. Called on server stop so a subsequent world in the same JVM — a
     * singleplayer player returning to the main menu and loading a different save — does not
     * inherit the previous world's cooldowns (RS-132).
     */
    public static void clear() {
        OPENED.clear();
        LAST_REWARD.clear();
    }
}
