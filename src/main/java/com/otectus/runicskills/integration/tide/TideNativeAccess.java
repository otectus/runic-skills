package com.otectus.runicskills.integration.tide;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/** Literal Tide-declared members, inspected in the pinned Forge binary. No native state writes. */
public final class TideNativeAccess {
    private static Method activeHook, rod, catchType, hookedIn, fishData, fishKey, mediumId, lavaAccess, voidAccess;
    private static Field medium;
    private TideNativeAccess() {}
    public static void probe() throws ReflectiveOperationException {
        var loader = TideNativeAccess.class.getClassLoader();
        Class<?> hook = Class.forName("com.li64.tide.registries.entities.misc.fishing.TideFishingHook", false, loader);
        Class<?> accessor = Class.forName("com.li64.tide.registries.entities.misc.fishing.HookAccessor", false, loader);
        Class<?> data = Class.forName("com.li64.tide.data.fishing.FishData", false, loader);
        Class<?> fluid = Class.forName("com.li64.tide.data.fishing.mediums.FishingMedium", false, loader);
        activeHook = accessor.getMethod("getHook", Player.class);
        rod = hook.getMethod("rod"); catchType = hook.getMethod("getCatchType");
        hookedIn = hook.getMethod("getHookedIn");
        lavaAccess = hook.getMethod("canFishInLava"); voidAccess = hook.getMethod("canFishInVoid");
        medium = hook.getDeclaredField("medium"); medium.setAccessible(true);
        mediumId = fluid.getMethod("id");
        // get(), unlike getExact(), resolves a variant through Tide's parent mapping.
        fishData = data.getMethod("get", ItemStack.class); fishKey = data.getMethod("fishKey");
    }
    public static Entity active(Player player) { return (Entity) invoke(activeHook, null, player); }
    public static ItemStack rod(Entity hook) { return (ItemStack) invoke(rod, hook); }
    public static String catchType(Entity hook) { return ((Enum<?>) invoke(catchType, hook)).name(); }
    public static boolean pulledEntity(Entity hook) { return invoke(hookedIn, hook) != null; }
    public static String medium(Entity hook) {
        try {
            Object value = medium.get(hook);
            return value == null ? "unknown" : ((ResourceLocation) invoke(mediumId, value)).toString();
        } catch (IllegalAccessException e) { throw new IllegalStateException("Tide medium read failed", e); }
    }
    public static String species(ItemStack stack) {
        var data = (Optional<?>) invoke(fishData, null, stack);
        return data.isEmpty() ? null : ((ResourceKey<?>) invoke(fishKey, data.get())).location().toString();
    }
    public static boolean legalMedium(Entity hook, String medium) {
        return switch (medium) {
            case "tide:water" -> true;
            case "tide:lava" -> (Boolean) invoke(lavaAccess, hook);
            case "tide:void" -> (Boolean) invoke(voidAccess, hook);
            default -> false;
        };
    }
    private static Object invoke(Method method, Object instance, Object... args) {
        try { return method.invoke(instance, args); }
        catch (ReflectiveOperationException | NullPointerException e) { throw new IllegalStateException("Tide read-only API unavailable", e); }
    }
}
