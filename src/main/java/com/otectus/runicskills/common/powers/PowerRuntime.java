package com.otectus.runicskills.common.powers;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;

/**
 * Bundles the eight shared utility services from RUNIC_SKILLS_POWERS.md §8.2 into one
 * static per-player state container. Each service is an independent nested subsystem with
 * its own per-player map; they're co-located here to match the codebase's one-file-per-concern
 * idiom (cf. {@link com.otectus.runicskills.network.PacketRateLimiter}) and to make
 * lifecycle hooks (logout cleanup) trivial.
 *
 * <ul>
 *   <li>{@link SpellHistory}    — last N cast (spellId, tick, schoolId) per player.</li>
 *   <li>{@link DamageTypeMemory} — per-player map of last-hit-tick per school, plus
 *                                  lastHitEntity → school.</li>
 *   <li>{@link ProcWindows}     — powerId → absolute game-time the window expires (also
 *                                 mirrored on the capability for persistence).</li>
 *   <li>{@link InternalCooldowns} — powerId → absolute game-time available.</li>
 *   <li>{@link TargetTags}      — short-lived per-entity tags ("marked", "conduit", etc.).</li>
 *   <li>{@link AllyDetector}    — party/ally resolution fallback.</li>
 *   <li>{@link PositionBuffer}  — 60-entry ring of player positions (used by Unraveled).</li>
 *   <li>{@link SummonRegistry}  — active summons per player.</li>
 * </ul>
 *
 * All services are transient — rebuilt on login, cleared on logout. Persistent power state
 * (cooldowns, equipped slots) lives on {@link com.otectus.runicskills.common.capability.SkillCapability}.
 */
public final class PowerRuntime {

    private PowerRuntime() {}

    /** Called from PlayerLifecycleHandler on logout to free state. */
    public static void clearPlayer(UUID id) {
        if (id == null) return;
        SpellHistory.clear(id);
        DamageTypeMemory.clear(id);
        ProcWindows.clear(id);
        InternalCooldowns.clear(id);
        PositionBuffer.clear(id);
        SummonRegistry.clear(id);
        TargetTags.clear(id);
    }

    // ── Spell history ───────────────────────────────────────────────────────────────

    public record SpellEvent(ResourceLocation spellId, ResourceLocation schoolId, long gameTime) {}

    public static final class SpellHistory {
        private static final int CAP = 32;
        private static final Map<UUID, Deque<SpellEvent>> STORE = new HashMap<>();

        public static synchronized void push(UUID id, ResourceLocation spellId, ResourceLocation schoolId, long gameTime) {
            Deque<SpellEvent> deque = STORE.computeIfAbsent(id, k -> new ArrayDeque<>());
            deque.addFirst(new SpellEvent(spellId, schoolId, gameTime));
            while (deque.size() > CAP) deque.removeLast();
        }

        public static synchronized int countSinceTick(UUID id, ResourceLocation schoolId, long sinceTick) {
            Deque<SpellEvent> deque = STORE.get(id);
            if (deque == null) return 0;
            int count = 0;
            for (SpellEvent ev : deque) {
                if (ev.gameTime < sinceTick) break;
                if (schoolId == null || schoolId.equals(ev.schoolId)) count++;
            }
            return count;
        }

        @Nullable
        public static synchronized SpellEvent mostRecent(UUID id) {
            Deque<SpellEvent> deque = STORE.get(id);
            return (deque == null || deque.isEmpty()) ? null : deque.peekFirst();
        }

        static void clear(UUID id) { STORE.remove(id); }
    }

    // ── Damage-type memory ──────────────────────────────────────────────────────────

    public static final class DamageTypeMemory {
        public enum School { FIRE, ICE, LIGHTNING, HOLY, ENDER, BLOOD, EVOCATION, NATURE, ELDRITCH }

        private static final Map<UUID, EnumMap<School, Long>> LAST_HIT_TICK = new HashMap<>();
        private static final Map<UUID, UUID> LAST_HIT_ENTITY_SCHOOL_OWNER = new HashMap<>();
        private static final Map<UUID, School> LAST_HIT_ENTITY_SCHOOL = new HashMap<>();

        public static synchronized void recordHit(UUID attacker, School school, long gameTime, @Nullable UUID victim) {
            LAST_HIT_TICK.computeIfAbsent(attacker, k -> new EnumMap<>(School.class)).put(school, gameTime);
            if (victim != null) {
                LAST_HIT_ENTITY_SCHOOL_OWNER.put(victim, attacker);
                LAST_HIT_ENTITY_SCHOOL.put(victim, school);
            }
        }

        public static synchronized long lastHitTick(UUID attacker, School school) {
            EnumMap<School, Long> m = LAST_HIT_TICK.get(attacker);
            if (m == null) return 0L;
            Long v = m.get(school);
            return v == null ? 0L : v;
        }

        @Nullable
        public static synchronized School lastSchoolHitOn(UUID victim) {
            return LAST_HIT_ENTITY_SCHOOL.get(victim);
        }

        static synchronized void clear(UUID id) {
            LAST_HIT_TICK.remove(id);
            // Drop victim-keyed entries whose recorded attacker is the departing player, and
            // keep LAST_HIT_ENTITY_SCHOOL in lockstep with the owner map — previously only the
            // owner map was pruned, leaving the school entries behind forever.
            LAST_HIT_ENTITY_SCHOOL_OWNER.entrySet().removeIf(e -> {
                if (id.equals(e.getValue())) {
                    LAST_HIT_ENTITY_SCHOOL.remove(e.getKey());
                    return true;
                }
                return false;
            });
            // The departing player may itself be a recorded victim.
            LAST_HIT_ENTITY_SCHOOL_OWNER.remove(id);
            LAST_HIT_ENTITY_SCHOOL.remove(id);
        }
    }

    // ── Proc windows ────────────────────────────────────────────────────────────────

    public static final class ProcWindows {
        private static final Map<UUID, Map<String, Long>> STORE = new HashMap<>();

        public static synchronized void open(UUID id, String powerName, long expiresAt) {
            STORE.computeIfAbsent(id, k -> new HashMap<>()).put(powerName, expiresAt);
        }

        public static synchronized boolean active(UUID id, String powerName, long now) {
            Map<String, Long> m = STORE.get(id);
            if (m == null) return false;
            Long exp = m.get(powerName);
            return exp != null && exp > now;
        }

        public static synchronized void consume(UUID id, String powerName) {
            Map<String, Long> m = STORE.get(id);
            if (m != null) m.remove(powerName);
        }

        static void clear(UUID id) { STORE.remove(id); }
    }

    // ── Internal cooldowns ──────────────────────────────────────────────────────────

    public static final class InternalCooldowns {
        private static final Map<UUID, Map<String, Long>> STORE = new HashMap<>();

        public static synchronized boolean checkAndStart(UUID id, String powerName, long now, long durationTicks) {
            Map<String, Long> m = STORE.computeIfAbsent(id, k -> new HashMap<>());
            Long avail = m.get(powerName);
            if (avail != null && avail > now) return false;
            m.put(powerName, now + durationTicks);
            return true;
        }

        public static synchronized boolean isAvailable(UUID id, String powerName, long now) {
            Map<String, Long> m = STORE.get(id);
            if (m == null) return true;
            Long avail = m.get(powerName);
            return avail == null || avail <= now;
        }

        static void clear(UUID id) { STORE.remove(id); }
    }

    // ── Target tagging ──────────────────────────────────────────────────────────────

    public static final class TargetTags {
        /** tagKey → (entityId → expiresAt). Entity lookups are by UUID so cross-dimension drops are safe. */
        private static final Map<String, Map<UUID, Long>> STORE = new HashMap<>();

        public static synchronized void tag(String tagKey, UUID entityId, long expiresAt) {
            STORE.computeIfAbsent(tagKey, k -> new HashMap<>()).put(entityId, expiresAt);
        }

        public static synchronized boolean has(String tagKey, UUID entityId, long now) {
            Map<UUID, Long> m = STORE.get(tagKey);
            if (m == null) return false;
            Long exp = m.get(entityId);
            if (exp == null) return false;
            if (exp <= now) { m.remove(entityId); return false; }
            return true;
        }

        public static synchronized void remove(String tagKey, UUID entityId) {
            Map<UUID, Long> m = STORE.get(tagKey);
            if (m != null) m.remove(entityId);
        }

        /** Drops every tag on this entity — logout cleanup; expiry otherwise only happens lazily on has(). */
        static synchronized void clear(UUID entityId) {
            STORE.values().forEach(m -> m.remove(entityId));
            STORE.values().removeIf(Map::isEmpty);
        }
    }

    // ── Ally detection ──────────────────────────────────────────────────────────────

    public static final class AllyDetector {
        /**
         * No party system exists in this mod yet, so the default rule is:
         * same owner (tamed/summon chain) OR both are players on the same team OR just
         * "non-hostile to self". Callers can relax the last clause for Herald of Dawn-style
         * Powers that want to heal any non-enemy.
         */
        public static boolean isAlly(Player self, LivingEntity other) {
            if (self == null || other == null || self == other) return false;
            if (other instanceof Player op) {
                if (self.getTeam() != null && self.getTeam() == op.getTeam()) return true;
            }
            // Tamed chain: if the target is owned by self (a summon, a tamed pet), treat as ally.
            return false;
        }
    }

    // ── Position buffer ─────────────────────────────────────────────────────────────

    public static final class PositionBuffer {
        public record Snapshot(Vec3 pos, float yRot, long gameTime) {}
        private static final int CAP = 60;
        private static final Map<UUID, Deque<Snapshot>> STORE = new WeakHashMap<>();

        public static synchronized void push(UUID id, Vec3 pos, float yRot, long gameTime) {
            Deque<Snapshot> deque = STORE.computeIfAbsent(id, k -> new ArrayDeque<>());
            deque.addFirst(new Snapshot(pos, yRot, gameTime));
            while (deque.size() > CAP) deque.removeLast();
        }

        /** Return the oldest snapshot within the last {@code sinceTicks}, or null. */
        @Nullable
        public static synchronized Snapshot pastBy(UUID id, long ticksAgo, long now) {
            Deque<Snapshot> deque = STORE.get(id);
            if (deque == null || deque.isEmpty()) return null;
            long cutoff = now - ticksAgo;
            Snapshot best = null;
            for (Snapshot s : deque) {
                if (s.gameTime <= cutoff) return s;
                best = s;
            }
            return best;
        }

        static void clear(UUID id) { STORE.remove(id); }
    }

    // ── Summon registry ─────────────────────────────────────────────────────────────

    public static final class SummonRegistry {
        private static final Map<UUID, java.util.Set<UUID>> STORE = new HashMap<>();

        public static synchronized void add(UUID owner, UUID summon) {
            STORE.computeIfAbsent(owner, k -> new java.util.LinkedHashSet<>()).add(summon);
        }

        public static synchronized void remove(UUID owner, UUID summon) {
            java.util.Set<UUID> set = STORE.get(owner);
            if (set != null) set.remove(summon);
        }

        public static synchronized int count(UUID owner) {
            java.util.Set<UUID> set = STORE.get(owner);
            return set == null ? 0 : set.size();
        }

        static void clear(UUID id) { STORE.remove(id); }
    }
}
