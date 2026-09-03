package com.otectus.runicskills.common.durability;

/**
 * The arithmetic behind the durability perks that promise a bigger pool rather than a repair.
 *
 * <p>Pure Java, like {@link com.otectus.runicskills.common.util.ProcRoll} and
 * {@code ExperienceMath}, so the three conversions below can be argued about in a headless unit
 * test instead of in a running server. Each of them was got wrong at least once by being written
 * inline at the call site.
 */
public final class DurabilityMath {

    private DurabilityMath() {
    }

    /**
     * The per-point avoidance probability that yields exactly {@code +percent} durability.
     *
     * <p>Precision Tools promises "tool durability increased by X%", but it is a per-<em>player</em>
     * perk and the only per-player place durability is spent is {@code ItemStack#hurt} — which
     * subtracts points, it does not enlarge the pool. Ignoring each point with probability {@code p}
     * gives an item an expected lifetime of {@code 1/(1-p)} times its nominal one, so the promise
     * "+X%" is solved by {@code 1/(1-p) = 1 + X/100}, that is {@code p = X/(100+X)}. 15% therefore
     * costs 13.04% avoidance, not 15%: avoiding X% of points would give {@code 1/(1-X/100)}, which
     * is 17.6% more durability, not 15%.
     *
     * <p>Non-finite and non-positive configurations answer {@code 0} rather than throwing, matching
     * {@code ProcRoll.chance01}: a hand-edited config file is an input, not a contract.
     */
    public static double bonusDurabilityToAvoidance(double percent) {
        if (!Double.isFinite(percent) || percent <= 0.0D) return 0.0D;
        return percent / (100.0D + percent);
    }

    /**
     * An item's maximum damage raised by {@code percent}, for the perks that really do enlarge the
     * pool because they act on the item itself rather than on whoever is holding it.
     *
     * <p>Zero stays zero — an unbreakable item must not become breakable with a maximum of one —
     * and the result never drops below {@code original}, so a negative percentage hand-edited into
     * the config cannot make gear worse than the game shipped it. The multiplication runs in
     * {@code long} and saturates, because the value ends up in an {@code int} damage comparison and
     * an overflowed maximum reads as "already broken".
     */
    public static int scaledMaxDamage(int original, int percent) {
        if (original <= 0 || percent <= 0) return Math.max(0, original);
        long bonus = (long) original * (long) percent / 100L;
        long scaled = (long) original + bonus;
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    /**
     * What a bonus stamp becomes when an item that already carries one is stamped again: the
     * larger of the two, never the sum.
     *
     * <p>The stamps are written at craft and repair time, so an item can pass through an anvil any
     * number of times. Adding would let a player farm a 10% bonus into an arbitrarily large one by
     * repairing the same sword repeatedly, which is a duplication bug wearing a perk's name; taking
     * the maximum makes re-repairing a no-op and still lets a player who has since raised the
     * config value (or the perk's rank) benefit from it on their existing gear.
     */
    public static int stampValue(int existing, int incoming) {
        return Math.max(existing, incoming);
    }
}
