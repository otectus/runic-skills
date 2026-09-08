package com.otectus.runicskills.mixin.tide;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.otectus.runicskills.integration.tide.TidePreparation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Tide-declared member; literal name/descriptor verified in the Forge 2.1.1 hotfix binary. */
@Pseudo
@Mixin(targets = "com.li64.tide.registries.items.TideFishingRodItem", remap = false)
public abstract class MixTideFishingRodItem {
    @ModifyReturnValue(method = "getChargeDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I",
            at = @At("RETURN"), remap = false, require = 0, expect = 1)
    private int runicskills$measuredCast(int original, ItemStack rod, LivingEntity user) {
        return TidePreparation.duration(original, rod, user);
    }
}
