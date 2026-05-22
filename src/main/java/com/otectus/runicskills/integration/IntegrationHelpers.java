package com.otectus.runicskills.integration;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Tiny utility for the namespaced-id matching pattern that every integration
 * previously re-implemented inline (see SpartanIntegration, IceAndFireIntegration,
 * MowziesMobsIntegration, FarmersDelightIntegration, CataclysmIntegration).
 *
 * <p>Introduced for R3 batch 4 (mod-gated Strength perks) — the new integration
 * handlers detect their target items / entities by ResourceLocation namespace
 * match rather than by importing the upstream mod's type. This avoids new build
 * deps and keeps the dev env minimal.
 *
 * <p>All methods are null-safe and stack-empty-safe.
 */
public final class IntegrationHelpers {

    private IntegrationHelpers() {}

    /** True iff {@code stack} is non-empty and its item's registry id namespace equals {@code modId}. */
    public static boolean itemFromMod(ItemStack stack, String modId) {
        if (stack == null || stack.isEmpty() || modId == null) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && modId.equals(id.getNamespace());
    }

    /** True iff {@code entity}'s type registry id namespace equals {@code modId}. */
    public static boolean entityFromMod(Entity entity, String modId) {
        if (entity == null || modId == null) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && modId.equals(id.getNamespace());
    }

    /** True iff {@code stack} is non-empty and its item id's path contains {@code substring}. */
    public static boolean itemPathContains(ItemStack stack, String substring) {
        if (stack == null || stack.isEmpty() || substring == null) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getPath().contains(substring);
    }
}
