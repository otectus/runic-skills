package com.otectus.runicskills.common.effects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Shortening an effect that is arriving, without losing anything else it carries.
 *
 * <p>It is handed an instance and returns another, with bounded diagnostics for unsupported copies. Nothing here touches
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
 * whole of this instance", for the pinned vanilla/Forge instance. Unknown subclasses are left unchanged because their
 * additional state may not serialize.
 *
 * <p><b>Reductions are summed by the caller, not applied one after another.</b> Two perks that each
 * take a quarter off take half between them, not 43.75%. That is what keeps a stack of duration
 * perks legible, and it is why this method takes one total cut rather than being called twice.
 */
public final class IncomingEffectPolicy {
    private static final java.util.Set<String> WARNED_INSTANCE_CLASSES = new java.util.HashSet<>();

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
        if (incoming == null || incoming.isInfiniteDuration() || incoming.getEffect().isInstantenous()) return incoming;
        double bounded = Double.isFinite(cut) ? Math.max(0.0, Math.min(1.0, cut)) : 0;
        if (bounded <= 0) return incoming;

        int duration = incoming.getDuration();
        int shortened = Math.max(0, duration - (int) (duration * bounded));
        if (shortened >= duration) return incoming;

        return withDuration(incoming, shortened);
    }

    /** Percentage bonuses compose additively, then potion flat ticks are added once. */
    public static MobEffectInstance extend(MobEffectInstance incoming, double percent, double flatTicks, int amplifierBonus) {
        if (incoming == null || incoming.isInfiniteDuration() || incoming.getEffect().isInstantenous()) return incoming;
        double extra = Double.isFinite(percent) ? Math.max(0, percent) : 0;
        double flat = Double.isFinite(flatTicks) ? Math.max(0, flatTicks) : 0;
        int duration = (int) Math.min(Integer.MAX_VALUE,
                Math.floor(Math.max(0, incoming.getDuration()) * (1.0 + extra / 100.0)) + flat);
        int amplifier = (int) Math.min(255L, (long) incoming.getAmplifier() + Math.max(0, amplifierBonus));
        return withState(incoming, duration, amplifier);
    }

    public static MobEffectInstance withDuration(MobEffectInstance original, int duration) {
        return withState(original, duration, original.getAmplifier());
    }

    /** Vanilla/Forge's full serialized state; unknown instance subclasses remain untouched. */
    public static MobEffectInstance withState(MobEffectInstance original, int duration, int amplifier) {
        if (original.getDuration() == duration && original.getAmplifier() == amplifier) return original;
        if (original.getClass() != MobEffectInstance.class) {
            warnUnsupportedInstance(original.getClass());
            return original;
        }
        CompoundTag tag = original.save(new CompoundTag());
        tag.putInt("Duration", duration);
        tag.putByte("Amplifier", (byte) amplifier);
        MobEffectInstance rebuilt = MobEffectInstance.load(tag);
        return rebuilt == null ? original : rebuilt;
    }

    private static synchronized void warnUnsupportedInstance(Class<?> type) {
        // Bound both retained names and log output, even if another mod generates instance classes.
        if (WARNED_INSTANCE_CLASSES.size() < 16 && WARNED_INSTANCE_CLASSES.add(type.getName()))
            org.apache.logging.log4j.LogManager.getLogger(IncomingEffectPolicy.class).warn(
                    "Runic Skills left custom effect instance {} unchanged because its extra state has no verified copy adapter (at most 16 class diagnostics per process).", type.getName());
    }
}
