package com.otectus.runicskills.integration.tconstruct;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The temporary things the twelve Artifice Powers remember, and nothing else.
 *
 * <p>Sibling of {@link TConstructPerkState} and governed by the same rule from §11.5: a personal
 * charge, streak or prepared strike is server memory, is bounded, and is cleared on death, logout,
 * dimension change and respec. Cooldowns are the one exception and they are not here — they are
 * debt, they survive all of that, and they live on the capability
 * ({@code SkillCapability.tcPowerCooldowns}).
 *
 * <p><b>Every collection is bounded by §11.5's own numbers</b>: at most one pending tool per Power
 * that has one, at most ten Foundry input records, at most four contributor records. A bound that
 * is stated in the specification and not enforced in the field that holds it is a bound that grows
 * the first time somebody automates the trigger.
 */
final class TConstructPowerState {

    /** §11.5: at most ten pending manual-input records for Heart of the Foundry. */
    static final int MAX_FOUNDRY_INPUTS = 10;

    /** §11.4: at most four contributors to one cooperative award. */
    static final int MAX_CONTRIBUTORS = 4;

    /** One player's transient Artifice memory. Server-thread only. */
    static final class Player {

        // -- First Heat -------------------------------------------------------------------------

        /** Accepted primary hits so far in the current sequence. */
        int firstHeatHits;

        /** The tick the current sequence started on; the window is measured from it. */
        long firstHeatWindowStart;

        /** The tick the prepared strike lapses unused; {@code 0} when nothing is prepared. */
        long firstHeatPreparedUntil;

        // -- Plumb Line -------------------------------------------------------------------------

        int plumbLineActions;
        long plumbLineWindowStart;
        long plumbLinePreparedUntil;

        // -- Quench -----------------------------------------------------------------------------

        /** The tick the fire reduction stops applying. */
        long quenchUntil;

        // -- Working Memory ---------------------------------------------------------------------

        /** The station the pending tool is sitting in; {@code null} when nothing is pending. */
        BlockPos workingMemoryStation;

        /** The registry id of the tool the part change was made to. */
        String workingMemoryItem;

        /** The tick the pending change stops counting. */
        long workingMemoryUntil;

        // -- Hammer and Tongs -------------------------------------------------------------------

        long hammerPreparedUntil;

        // -- Temper Reserve ---------------------------------------------------------------------

        /** The repaired tool the extra avoidance belongs to, by registry id. */
        String temperReserveItem;

        long temperReserveUntil;

        // -- Resonant Return --------------------------------------------------------------------

        /** The tick the prepared launch stops being available after a genuine return. */
        long resonantArmedUntil;

        // -- Workshop Aegis ---------------------------------------------------------------------

        long aegisUntil;

        // -- The Great Work ---------------------------------------------------------------------

        long greatWorkAssemblyAt;
        long greatWorkRepairAt;
        long greatWorkCastAt;

        /** The tick Inspired ends; {@code 0} when it is not running. */
        long greatWorkInspiredUntil;

        // -- Heart of the Foundry ---------------------------------------------------------------

        /** Melting slots this player has personally filled and that have not yet been counted. */
        final Set<String> foundryInputs = new LinkedHashSet<>();

        /** Completed operations attributed to those insertions. */
        int foundryOperations;

        long foundryUntil;

        // -- Many Hands -------------------------------------------------------------------------

        /** The workshop the owner's own contribution was made at. */
        BlockPos manyHandsWorkshop;

        /** The tick the owner contributed on. */
        long manyHandsOwnerAt;

        /** Allies who have contributed at that workshop inside the window, and when. */
        final Map<UUID, Long> manyHandsContributors = new ConcurrentHashMap<>();

        /** The tick this player's paid-repair bonus from a cooperative award ends. */
        long manyHandsBonusUntil;
    }

    private static final Map<UUID, Player> STATE = new ConcurrentHashMap<>();

    private TConstructPowerState() {
    }

    /** This player's memory, created empty on first use. */
    static Player of(UUID player) {
        return STATE.computeIfAbsent(player, key -> new Player());
    }

    /** This player's memory if they have any, or {@code null}. Reads that must not allocate. */
    static Player peek(UUID player) {
        return player == null ? null : STATE.get(player);
    }

    /** Forgets everything temporary about one player. Never touches their cooldown debt. */
    static void clear(UUID player) {
        if (player != null) STATE.remove(player);
    }

    /** Forgets everyone, on server stop, so a second world in this JVM starts clean. */
    static void clearAll() {
        STATE.clear();
    }

    /** A stable key for one melting slot: dimension, controller position, slot index. */
    static String meltingKey(String dimension, BlockPos controller, int slot) {
        return dimension + '@' + controller.asLong() + '#' + slot;
    }
}
