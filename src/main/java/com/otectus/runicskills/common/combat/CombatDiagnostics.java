package com.otectus.runicskills.common.combat;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The combat trace that had to be reconstructed by hand for the 2.0.2 Iron Golem report.
 *
 * <p>The reported crash was a maxed multi-skill build attacking one mob, and the only evidence
 * available was the crash report itself: which of eight overlapping perks and Powers had actually
 * fired, in what order, and how much each had added to the hit could not be recovered from it. The
 * lines this class writes are that missing evidence — attacker, target, damage source, the
 * {@link DamageContext} frame the hit is running under, and the amount before and after the
 * handler touched it.
 *
 * <p>Off by default and off cheaply: {@link #enabled()} is a field read and a logger flag, and no
 * caller may format anything before checking it. Turn it on with
 * {@code -Drunicskills.debug.combat=true} on the launch command line, or by putting this mod's
 * logger at DEBUG. No config field, no packet, no protocol change (spec §22).
 *
 * <p>Rate-limited per note, because a trace is only useful if a player can send it: a line per
 * event during a fight in a spawner room would bury the ten lines anyone needs.
 */
public final class CombatDiagnostics {

    private static final Logger LOGGER = RunicSkills.getLOGGER();

    /**
     * Read once. A system property cannot change during a run, and this is consulted from the
     * damage path.
     */
    private static final boolean PROPERTY_SET = Boolean.getBoolean("runicskills.debug.combat");

    /** How often a given note may be traced, in milliseconds. */
    private static final long INTERVAL_MS = 1_000L;

    /** Bounds the note map: notes are compile-time strings, so this is a generous ceiling. */
    private static final int MAX_NOTES = 256;

    private static final Map<String, Long> LAST_TRACE_AT = new ConcurrentHashMap<>();

    static {
        // DamageContext is pure Java and must stay that way, so it cannot call in here itself.
        // This class is loaded the first time a combat handler traces, which is well before any
        // damage has had a chance to nest four deep.
        DamageContext.setSuppressionListener(CombatDiagnostics::suppressed);
    }

    private CombatDiagnostics() {
    }

    /** Whether combat tracing is on. Every caller checks this before doing any work at all. */
    public static boolean enabled() {
        return PROPERTY_SET || LOGGER.isDebugEnabled();
    }

    /**
     * Traces one damage adjustment.
     *
     * @param note what changed the amount, in a few words — also the rate-limiting key
     */
    public static void trace(Entity attacker, Entity target, DamageSource source,
                             float before, float after, String note) {
        if (!enabled() || !allow(note)) return;
        LOGGER.debug("[combat] attacker={} target={} source={} {} before={} after={} note={}",
                attacker == null ? "none" : attacker.getUUID(),
                target == null ? "none" : target.getType() + "/" + target.getUUID(),
                source == null ? "none" : source.getMsgId(),
                DamageContext.describe(), before, after, note);
    }

    /**
     * Traces a hit that {@link DamageContext} refused for depth. Separate from
     * {@link #trace} because there is no amount and no event: the hit never happened.
     */
    public static void suppressed(DamageContext.Origin origin, UUID owner) {
        if (!enabled() || !allow("suppressed:" + origin)) return;
        LOGGER.debug("[combat] suppressed origin={} owner={} {}",
                origin, owner == null ? "none" : owner, DamageContext.describe());
    }

    private static boolean allow(String note) {
        long now = System.currentTimeMillis();
        Long last = LAST_TRACE_AT.get(note);
        if (last != null && now - last < INTERVAL_MS) return false;
        if (last == null && LAST_TRACE_AT.size() >= MAX_NOTES) return true;
        LAST_TRACE_AT.put(note, now);
        return true;
    }
}
