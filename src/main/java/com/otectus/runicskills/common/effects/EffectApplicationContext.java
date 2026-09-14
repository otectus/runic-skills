package com.otectus.runicskills.common.effects;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import java.util.function.Supplier;

/** Origin of one exact native application. Nested unrelated effects cannot inherit it. */
public final class EffectApplicationContext {
    public enum Origin { FOOD, POTION, SHARED }
    private record Application(LivingEntity target, MobEffectInstance effect, MobEffectInstance transformed, Origin origin) {}
    private static final ThreadLocal<Application> CURRENT = new ThreadLocal<>();
    private EffectApplicationContext() {}

    public static Origin origin(LivingEntity target, MobEffectInstance effect) {
        Application current = CURRENT.get();
        return current != null && current.target == target && current.effect == effect ? current.origin : null;
    }

    public static boolean sharing(LivingEntity target, MobEffectInstance effect) {
        Application current = CURRENT.get();
        return current != null && current.target == target && current.origin == Origin.SHARED
                && (current.effect == effect || current.transformed == effect);
    }

    public static MobEffectInstance transformed(LivingEntity target, MobEffectInstance before, MobEffectInstance after) {
        Application current = CURRENT.get();
        if (current != null && current.target == target && current.effect == before)
            CURRENT.set(new Application(target, before, after, current.origin));
        return after;
    }

    public static <T> T apply(LivingEntity target, MobEffectInstance effect, Origin origin, Supplier<T> action) {
        Application previous = CURRENT.get();
        CURRENT.set(new Application(target, effect, effect, origin));
        try { return action.get(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
