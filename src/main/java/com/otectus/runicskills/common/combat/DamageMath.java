package com.otectus.runicskills.common.combat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The last check between a computed damage figure and {@code LivingHurtEvent#setAmount}.
 *
 * <p>Nearly every damage adjustment in this mod is a config-driven multiplier: a percentage read
 * from a hand-editable JSON5 file, divided by 100, multiplied onto the event amount. The config is
 * validated on load, but a value that survives validation can still combine into something the
 * damage pipeline cannot use — a {@code NaN} propagating through a sum of percentages, an infinity
 * from a multiplier a pack author typed with too many zeroes, a negative from a reduction above
 * 100%. Vanilla then carries that value into the health calculation, and a {@code NaN} health is
 * not a crash at the point of damage: it is an entity that can never die, an armour bar that
 * renders as nothing, and a save that fails to load later. That is the failure this class exists
 * to stop happening in the first place.
 *
 * <p>The contract is deliberately conservative: an unusable figure means the handler's adjustment
 * is dropped and the amount the event already carried stands. No handler gets to make the hit
 * worse by miscalculating, and none of them has to write the check itself.
 *
 * <p>Pure Java with its own slf4j logger, so it can be unit-tested headlessly alongside
 * {@link DamageContext}.
 */
public final class DamageMath {

    private static final Logger LOGGER = LoggerFactory.getLogger("runicskills");

    /** How often an unusable figure may be reported, in milliseconds. */
    private static final long WARN_INTERVAL_MS = 5_000L;

    /** Wall-clock time of the last warning; shared, because one broken multiplier fires per hit. */
    private static final AtomicLong LAST_WARN_AT = new AtomicLong(Long.MIN_VALUE);

    private DamageMath() {
    }

    /**
     * The amount a handler should set, given what it computed.
     *
     * @param original the amount the event already carries, used when {@code computed} is unusable
     * @param computed the handler's result
     * @return {@code computed} as a float when it is finite and not negative — and stays finite as
     *         a float, which a very large double does not — otherwise {@code original}
     */
    public static float safeAmount(float original, double computed) {
        float result = (float) computed;
        if (Double.isFinite(computed) && computed >= 0.0D && Float.isFinite(result) && result >= 0.0F) {
            return result;
        }
        warnRateLimited(original, computed);
        return original;
    }

    private static void warnRateLimited(float original, double computed) {
        long now = System.currentTimeMillis();
        long last = LAST_WARN_AT.get();
        if (now - last < WARN_INTERVAL_MS) return;
        if (!LAST_WARN_AT.compareAndSet(last, now)) return;
        LOGGER.warn("Runic Skills computed an unusable damage amount ({}); keeping {} instead. "
                        + "This is almost always a config value: check the percentages for the "
                        + "perks and Powers involved. Context: {}. Further reports are muted for "
                        + "{} ms.",
                computed, original, DamageContext.describe(), WARN_INTERVAL_MS);
    }
}
