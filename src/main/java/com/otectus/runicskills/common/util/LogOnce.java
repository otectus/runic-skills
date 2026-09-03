package com.otectus.runicskills.common.util;

import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Tell me about this once, not once per craft."
 *
 * <p>The hardening added in 2.0.5 isolates third-party recipe, ingredient and enchantment
 * implementations behind {@code catch (RuntimeException)}. Isolation without a diagnostic hides
 * the broken mod, but a plain {@code LOGGER.warn} inside a crafting handler is worse than the
 * crash it replaced: one misbehaving ingredient writes a stack trace on every craft, every tick a
 * hopper moves an item, for the life of the server. This class keeps the diagnostic and drops the
 * repetition, keyed on whatever identifies the offender (a recipe id, an enchantment id).
 *
 * <p>The key set is bounded, because keys are derived from third-party ids and a pathological pack
 * could otherwise turn a logging aid into a memory leak. Once the bound is reached the class stops
 * remembering but does <em>not</em> stop logging — silently discarding diagnostics past an
 * arbitrary limit is the failure mode this whole class exists to avoid, and a server that has
 * produced 512 distinct warnings has a real problem worth hearing about.
 *
 * <p>Pure Java: it takes an slf4j {@link Logger}, so the deduplication can be unit-tested without
 * a Minecraft server. The static entry points log through the mod logger and share one key set;
 * {@link #with(Logger)} gives a caller (or a test) an independent one.
 */
public final class LogOnce {

    /** Distinct keys remembered before the class keeps logging without remembering. */
    static final int MAX_KEYS = 512;

    /** Shared instance behind the static entry points; created on first use. */
    private static volatile LogOnce defaultInstance;

    private final Logger logger;
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    private LogOnce(Logger logger) {
        this.logger = logger;
    }

    /** A deduplicator with its own key set, writing to {@code logger}. */
    public static LogOnce with(Logger logger) {
        return new LogOnce(logger);
    }

    private static LogOnce shared() {
        LogOnce existing = defaultInstance;
        if (existing != null) return existing;
        synchronized (LogOnce.class) {
            if (defaultInstance == null) {
                defaultInstance = new LogOnce(com.otectus.runicskills.RunicSkills.getLOGGER());
            }
            return defaultInstance;
        }
    }

    /**
     * Whether {@code key} should be logged now. First call for a key is true, later calls false —
     * until the key set is full, after which every call is true and nothing more is remembered.
     */
    boolean shouldLog(String key) {
        if (key == null) return true;
        if (seen.contains(key)) return false;
        if (seen.size() >= MAX_KEYS) return true;
        return seen.add(key);
    }

    public void warn(String key, String message, Object... args) {
        if (shouldLog(key)) logger.warn(message, args);
    }

    public void info(String key, String message, Object... args) {
        if (shouldLog(key)) logger.info(message, args);
    }

    public void debug(String key, String message, Object... args) {
        // isDebugEnabled first: the formatting is lazy, but the key set write is not, and a
        // DEBUG-only diagnostic must not consume one of the bounded slots on a production server.
        if (logger.isDebugEnabled() && shouldLog(key)) logger.debug(message, args);
    }

    public static void warnOnce(String key, String message, Object... args) {
        shared().warn(key, message, args);
    }

    public static void infoOnce(String key, String message, Object... args) {
        shared().info(key, message, args);
    }

    public static void debugOnce(String key, String message, Object... args) {
        shared().debug(key, message, args);
    }
}
