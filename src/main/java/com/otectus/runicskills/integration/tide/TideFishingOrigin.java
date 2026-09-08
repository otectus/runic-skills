package com.otectus.runicskills.integration.tide;

import net.minecraft.world.entity.projectile.FishingHook;

/** Origin is the native wrapper class, never the namespace of a copied drop. No Tide class loads. */
public final class TideFishingOrigin {
    private TideFishingOrigin() {}
    public static boolean isTide(FishingHook hook) {
        return hasNativeClass(hook, "com.li64.tide.registries.entities.misc.fishing.HookAccessor");
    }
    public static boolean isNativeRod(net.minecraft.world.item.Item item) {
        return hasNativeClass(item, "com.li64.tide.registries.items.TideFishingRodItem");
    }
    private static boolean hasNativeClass(Object instance, String name) {
        if (instance == null) return false;
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            if (name.equals(type.getName())) return true;
        }
        return false;
    }
}
