package com.otectus.runicskills.common.powers;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
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
 *   <li>{@link TimedModifiers} — self-expiring transient attribute modifiers on any entity.</li>
 *   <li>{@link Counters}       — expiring per-key integers (chain hop index, hole extensions).</li>
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
        ProcThrottle.clear(id);
        TargetTags.clear(id);
    }

    /**
     * Drops every service's state. Called on server stop so a subsequent world in the same JVM
     * does not inherit the previous one's proc windows, cooldowns and position buffers (RS-132).
     */
    public static void clearAll() {
        SpellHistory.clearAll();
        DamageTypeMemory.clearAll();
        ProcWindows.clearAll();
        InternalCooldowns.clearAll();
        PositionBuffer.clearAll();
        ProcThrottle.clearAll();
        TargetTags.clearAll();
        TimedModifiers.clearAll();
        Counters.clearAll();
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

        // synchronized like every other accessor on this map. These five clear() methods were
        // the only ones that mutated STORE outside the lock, so a logout racing a gameplay
        // read could observe a torn HashMap (RS-131).
        static synchronized void clear(UUID id) { STORE.remove(id); }

        static synchronized void clearAll() { STORE.clear(); }
    }

    // ── Damage-type memory ──────────────────────────────────────────────────────────

    /**
     * Who last hit whom, with which school, and when.
     *
     * <p><b>Reserved, not abandoned.</b> Nothing calls {@link #recordHit} yet: the one Power that
     * wants it, The Grove Remembers, ships only its damage half because
     * {@code LivingHealEvent} carries no attacker pointer, so scoping the heal-reduction half to
     * "marked by this player" needs the attribution this class exists to provide. See the note at
     * that Power's dispatcher case. Kept deliberately so the deferred half has somewhere to land;
     * a sibling registry with no possible consumer was deleted rather than left here.
     */
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

        static synchronized void clearAll() {
            LAST_HIT_TICK.clear();
            LAST_HIT_ENTITY_SCHOOL_OWNER.clear();
            LAST_HIT_ENTITY_SCHOOL.clear();
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

        /**
         * Whether {@code powerName}'s window is still open, dropping it if it is not.
         *
         * <p>The expiry check used to be read-only, so an elapsed window stayed in the map until
         * the player logged out. Every proc of a windowed Power added a key that nothing ever
         * removed; over a long session that is per-player growth with no ceiling. Pruning on read
         * is what {@link TargetTags#has} already does, and it costs nothing here.
         */
        public static synchronized boolean active(UUID id, String powerName, long now) {
            Map<String, Long> m = STORE.get(id);
            if (m == null) return false;
            Long exp = m.get(powerName);
            if (exp == null) return false;
            if (exp <= now) {
                m.remove(powerName);
                if (m.isEmpty()) STORE.remove(id);
                return false;
            }
            return true;
        }

        public static synchronized void consume(UUID id, String powerName) {
            Map<String, Long> m = STORE.get(id);
            if (m != null) m.remove(powerName);
        }

        // synchronized like every other accessor on this map. These five clear() methods were
        // the only ones that mutated STORE outside the lock, so a logout racing a gameplay
        // read could observe a torn HashMap (RS-131).
        static synchronized void clear(UUID id) { STORE.remove(id); }

        static synchronized void clearAll() { STORE.clear(); }
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

        /**
         * Whether {@code powerName} is off cooldown, dropping the entry once it has elapsed.
         *
         * <p>Same reasoning as {@link ProcWindows#active}: reading expiry without removing it left
         * one dead key per Power per player for the rest of the session.
         */
        public static synchronized boolean isAvailable(UUID id, String powerName, long now) {
            Map<String, Long> m = STORE.get(id);
            if (m == null) return true;
            Long avail = m.get(powerName);
            if (avail == null) return true;
            if (avail <= now) {
                m.remove(powerName);
                if (m.isEmpty()) STORE.remove(id);
                return true;
            }
            return false;
        }

        /**
         * Brings forward the remaining cooldown of every Power in {@code powerNames} by
         * {@code fraction} of what is left.
         *
         * <p>Several Powers are specified as "reduces that ability's cooldown by N%", which needs a
         * partial adjustment rather than the all-or-nothing clear the rest of this class offers.
         * Only Powers still cooling are touched, and a fully elapsed cooldown is dropped rather
         * than left in the map.
         *
         * @return how many cooldowns were shortened
         */
        public static synchronized int reduceRemaining(UUID id, java.util.Collection<String> powerNames,
                                                       double fraction, long now) {
            Map<String, Long> m = STORE.get(id);
            if (m == null || powerNames.isEmpty() || fraction <= 0.0) return 0;
            int shortened = 0;
            for (String powerName : powerNames) {
                Long avail = m.get(powerName);
                if (avail == null || avail <= now) continue;
                long remaining = avail - now;
                long reduced = remaining - (long) Math.ceil(remaining * Math.min(1.0, fraction));
                if (reduced <= 0) {
                    m.remove(powerName);
                } else {
                    m.put(powerName, now + reduced);
                }
                shortened++;
            }
            return shortened;
        }

        // synchronized like every other accessor on this map. These five clear() methods were
        // the only ones that mutated STORE outside the lock, so a logout racing a gameplay
        // read could observe a torn HashMap (RS-131).
        static synchronized void clear(UUID id) { STORE.remove(id); }

        static synchronized void clearAll() { STORE.clear(); }
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
        static synchronized void clearAll() { STORE.clear(); }

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

        // synchronized like every other accessor on this map. These five clear() methods were
        // the only ones that mutated STORE outside the lock, so a logout racing a gameplay
        // read could observe a torn HashMap (RS-131).
        static synchronized void clear(UUID id) { STORE.remove(id); }

        static synchronized void clearAll() { STORE.clear(); }
    }

    // ── Summon registry ─────────────────────────────────────────────────────────────

    // ── Timed attribute modifiers ───────────────────────────────────────────────────

    /**
     * Transient attribute modifiers that expire on their own.
     *
     * <p>Scorched Earth's armour shred lands on whatever walked into a fire field — a mob nobody
     * is tracking, in a chunk that may unload before the debuff is due to end. A
     * {@link MobEffectInstance}-style countdown does not exist for attributes, so the expiry has
     * to live somewhere, and it cannot be a strong reference to the entity or every mob a fire
     * field ever touched would be pinned for the session. Weak references plus a periodic
     * {@link #sweep} is the cheapest correct shape.
     */
    public static final class TimedModifiers {

        private record Entry(WeakReference<LivingEntity> target, Attribute attribute,
                             UUID id, long expiresAt) {}

        private static final Map<String, Entry> STORE = new HashMap<>();

        private static String key(LivingEntity target, UUID id) {
            return id + "@" + target.getId();
        }

        /**
         * Applies (or refreshes) a transient modifier that will be removed at {@code expiresAt}.
         * Re-applying with the same UUID replaces the existing modifier rather than stacking,
         * because {@code addTransientModifier} throws on a duplicate id.
         */
        public static synchronized void apply(LivingEntity target, Attribute attribute, UUID id,
                                              String name, double amount,
                                              AttributeModifier.Operation operation, long expiresAt) {
            if (target == null || attribute == null || id == null) return;
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance == null) return;
            AttributeModifier existing = instance.getModifier(id);
            if (existing != null) instance.removeModifier(existing);
            instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
            STORE.put(key(target, id), new Entry(new WeakReference<>(target), attribute, id, expiresAt));
        }

        /** Removes a modifier ahead of its expiry. */
        public static synchronized void remove(LivingEntity target, Attribute attribute, UUID id) {
            if (target == null || attribute == null || id == null) return;
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance != null) {
                AttributeModifier existing = instance.getModifier(id);
                if (existing != null) instance.removeModifier(existing);
            }
            STORE.remove(key(target, id));
        }

        /** Drops every elapsed modifier, and every entry whose target has been collected. */
        public static synchronized void sweep(long now) {
            STORE.entrySet().removeIf(e -> {
                Entry entry = e.getValue();
                LivingEntity target = entry.target().get();
                if (target == null) return true;
                if (entry.expiresAt() > now) return false;
                AttributeInstance instance = target.getAttribute(entry.attribute());
                if (instance != null) {
                    AttributeModifier existing = instance.getModifier(entry.id());
                    if (existing != null) instance.removeModifier(existing);
                }
                return true;
            });
        }

        static synchronized void clearAll() {
            for (Entry entry : STORE.values()) {
                LivingEntity target = entry.target().get();
                if (target == null) continue;
                AttributeInstance instance = target.getAttribute(entry.attribute());
                if (instance == null) continue;
                AttributeModifier existing = instance.getModifier(entry.id());
                if (existing != null) instance.removeModifier(existing);
            }
            STORE.clear();
        }
    }

    // ── Expiring counters ───────────────────────────────────────────────────────────

    /**
     * Per-key integer counters that forget themselves.
     *
     * <p>Two Powers need to count events that belong to one transient thing rather than to a
     * player: which hop of a Chain Lightning bolt is landing, and how many times one black hole
     * has already been extended. Keying on the entity id and letting the entry expire is what
     * keeps that from becoming a map the server never empties.
     */
    public static final class Counters {

        private record Count(int value, long expiresAt) {}

        private static final Map<String, Count> STORE = new HashMap<>();

        /** Increments {@code key} (creating it at 1) and returns the new value. */
        public static synchronized int increment(String key, long now, long ttlTicks) {
            Count current = STORE.get(key);
            int next = (current == null || current.expiresAt() <= now) ? 1 : current.value() + 1;
            STORE.put(key, new Count(next, now + ttlTicks));
            return next;
        }

        /** The current value of {@code key}, or 0 when unset or elapsed. */
        public static synchronized int get(String key, long now) {
            Count current = STORE.get(key);
            if (current == null) return 0;
            if (current.expiresAt() <= now) { STORE.remove(key); return 0; }
            return current.value();
        }

        public static synchronized void reset(String key) { STORE.remove(key); }

        static synchronized void clearAll() { STORE.clear(); }
    }

}

