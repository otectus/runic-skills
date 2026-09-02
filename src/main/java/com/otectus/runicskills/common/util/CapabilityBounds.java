package com.otectus.runicskills.common.util;

/**
 * Absolute bounds for everything stored in a player's Runic Skills capability.
 *
 * <p>Deserialization used to accept saved skill levels, passive levels, perk ranks, perk
 * cooldowns, Power cooldowns and Power windows with no range or entry-count validation at all
 * (RS10-017). A negative skill level breaks eligibility maths and overflows the global-level sum;
 * an {@code Integer.MAX_VALUE} rank bypasses every intended rank limit; a hostile or corrupt
 * compound with a million entries becomes permanent per-tick map work for as long as that player
 * is online.
 *
 * <p><b>These are corruption bounds, not balance caps.</b> The distinction matters. A pack owner
 * who lowers {@code skillMaxLevel} from 32 to 20, or shrinks a passive's {@code levels} array,
 * must not have their players' earned progress destroyed the next time those players log in —
 * that is why the runtime caps effective values at the point of use instead (see the level clamp
 * in {@code RegistryAttributes.modifierAttributes}, added for RS-043). So the numbers here are
 * deliberately generous: they are set to what the config schema itself permits, so only data that
 * could never have been produced by a legitimate configuration is touched.
 *
 * <p>Kept free of Minecraft imports so it can be exercised directly by unit tests, matching the
 * other {@code common/util} maths helpers.
 */
public final class CapabilityBounds {

    private CapabilityBounds() {}

    /**
     * Highest skill level any configuration can grant: {@code skillMaxLevel} is itself declared
     * {@code @Clamp(min = 2, max = 1000)}, so a stored level above this was never reachable.
     */
    public static final int MAX_SKILL_LEVEL = 1000;

    /** Lowest legal skill level. Skills start at 1, never 0, and never negative. */
    public static final int MIN_SKILL_LEVEL = 1;

    /**
     * Highest passive level any configuration can grant. A passive's real maximum is the length of
     * its configured {@code levels} array; this is the ceiling on that length that a sane config
     * file could ever declare.
     */
    public static final int MAX_PASSIVE_LEVEL = 1000;

    /**
     * Highest perk rank any configuration can grant. Ranked perks in the registry top out in the
     * single digits; 100 leaves generous room for packs and addons while still rejecting the
     * overflow values that motivated this bound.
     */
    public static final int MAX_PERK_RANK = 100;

    /**
     * Longest perk cooldown that can remain outstanding, in ticks: 24 hours of continuous play.
     * Cooldowns are stored as remaining duration, and no perk's is more than a few minutes.
     */
    public static final int MAX_COOLDOWN_TICKS = 20 * 60 * 60 * 24;

    /**
     * Highest absolute game time a Power cooldown or proc window may be scheduled at. Power state
     * is stored as an absolute world game-time deadline rather than a remaining duration, so the
     * bound has to cover a very long-lived world: this is roughly 1,500 in-game years, while still
     * being fourteen million times smaller than {@link Long#MAX_VALUE}.
     */
    public static final long MAX_GAME_TIME = 1_000_000_000_000L;

    /**
     * Maximum entries retained in any one of the cooldown/window maps. These are runtime state
     * keyed by content id; a legitimate save holds a handful.
     */
    public static final int MAX_TIMER_ENTRIES = 1024;

    /**
     * Maximum length of a stored content-id key. Matches the network-side id bound so a value that
     * survives a save cannot fail to survive a packet.
     */
    public static final int MAX_KEY_CHARS = 128;

    public static int clampSkillLevel(int stored) {
        if (stored < MIN_SKILL_LEVEL) return MIN_SKILL_LEVEL;
        return Math.min(stored, MAX_SKILL_LEVEL);
    }

    public static int clampPassiveLevel(int stored) {
        if (stored < 0) return 0;
        return Math.min(stored, MAX_PASSIVE_LEVEL);
    }

    public static int clampPerkRank(int stored) {
        if (stored < 0) return 0;
        return Math.min(stored, MAX_PERK_RANK);
    }

    public static int clampCooldownTicks(int stored) {
        if (stored < 0) return 0;
        return Math.min(stored, MAX_COOLDOWN_TICKS);
    }

    /**
     * Bounds an absolute game-time deadline. A negative deadline is in the past and therefore
     * already expired, so it collapses to {@code 0}; an implausibly distant one is corrupt and is
     * pulled back to the ceiling rather than dropped, so a stuck cooldown still expires.
     */
    public static long clampGameTime(long stored) {
        if (stored < 0L) return 0L;
        return Math.min(stored, MAX_GAME_TIME);
    }

    /** A key is storable if it is non-blank and within the shared id length bound. */
    public static boolean isStorableKey(String key) {
        return key != null && !key.isEmpty() && key.length() <= MAX_KEY_CHARS;
    }

    /**
     * The level a write of {@code requested} may actually store, given the maximum a server has
     * configured.
     *
     * <p>Separate from {@link #clampSkillLevel}, which bounds what a <em>save</em> may contain
     * against the absolute ceiling. This bounds what a <em>command or purchase</em> may write
     * against the pack's own, usually much lower, maximum — and it takes that maximum as an
     * argument rather than reading it, so the boundaries can be tested without a running Forge
     * (RS10-013).
     *
     * <p>A configured maximum below the minimum is treated as the minimum: a pack that sets
     * {@code skillMaxLevel} to zero has misconfigured itself, and refusing to write level 1 would
     * make every player's skills unreadable rather than merely unimprovable.
     */
    public static int clampSkillLevelTo(int requested, int configuredMax) {
        int ceiling = Math.min(configuredMax, MAX_SKILL_LEVEL);
        if (ceiling < MIN_SKILL_LEVEL) ceiling = MIN_SKILL_LEVEL;
        if (requested < MIN_SKILL_LEVEL) return MIN_SKILL_LEVEL;
        return Math.min(requested, ceiling);
    }

    /**
     * Saturating addition for summing per-skill levels into a global total. The global level is a
     * plain sum of stored {@code int}s and could overflow into a negative number, which then reads
     * as "below every requirement" everywhere it is compared (RS10-013).
     */
    public static int addSaturating(int a, int b) {
        long sum = (long) a + (long) b;
        if (sum > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (sum < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) sum;
    }
}
