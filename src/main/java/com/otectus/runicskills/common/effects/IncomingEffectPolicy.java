package com.otectus.runicskills.common.effects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Shortening an effect that is arriving, without losing anything else it carries.
 *
 * <p>Pure and side-effect free: it is handed an instance and returns another. Nothing here touches
 * an entity, so the same reduction can be computed from a mixin, from a test, and from a later
 * integration without any of them needing a world.
 *
 * <p><b>Why the NBT round-trip.</b> A {@code MobEffectInstance} carries more than its duration:
 * ambient state, particle and icon visibility, Forge's curative-item list, the factor data a
 * darkness effect pulses with, and the hidden-effect chain that remembers the weaker effect a
 * stronger one displaced. Rebuilding one from a constructor means naming every one of those, and
 * the constructor that gets chosen decides which of them are silently dropped — which is exactly
 * how this mod once handed players effects that had quietly lost their ambient flag and their
 * curative items (RS10-008). {@code save} and {@code load} are vanilla's own definition of "the
 * whole of this instance", so a round-trip through them cannot drop a field that a future version
 * adds either.
 *
 * <p><b>Reductions are summed by the caller, not applied one after another.</b> Two perks that each
 * take a quarter off take half between them, not 43.75%. That is what keeps a stack of duration
 * perks legible, and it is why this method takes one total cut rather than being called twice.
 */
public final class IncomingEffectPolicy {

    private IncomingEffectPolicy() {
    }

    /**
     * A copy of {@code incoming} with its duration cut by {@code cut}, or the original instance
     * when nothing would change.
     *
     * <p>An infinite effect is returned untouched: a share of "forever" is meaningless, and
     * shortening one would be a different mechanic wearing this one's name. A cut of zero or less
     * likewise hands back the very instance vanilla was given, so "no perk" is byte-for-byte
     * vanilla by construction rather than by careful copying.
     *
     * @param cut fraction of the duration to remove, clamped to {@code [0, 1]}
     */
    public static MobEffectInstance shorten(MobEffectInstance incoming, double cut) {
        if (incoming == null || incoming.isInfiniteDuration()) return incoming;
        double bounded = Math.max(0.0, Math.min(1.0, cut));
        if (bounded <= 0) return incoming;

        int duration = incoming.getDuration();
        int shortened = Math.max(0, duration - (int) (duration * bounded));
        if (shortened >= duration) return incoming;

        return withDuration(incoming, shortened);
    }

    /**
     * The same instance with a different duration and everything else intact.
     *
     * <p>{@code load} returns {@code null} only for an effect whose id no longer resolves, which
     * cannot happen for an instance that is being applied right now; the original is returned in
     * that case anyway, because handing back {@code null} would cancel an effect rather than
     * shorten it.
     */
    private static MobEffectInstance withDuration(MobEffectInstance original, int duration) {
        CompoundTag tag = original.save(new CompoundTag());
        tag.putInt("Duration", duration);
        MobEffectInstance rebuilt = MobEffectInstance.load(tag);
        return rebuilt == null ? original : rebuilt;
    }
}
