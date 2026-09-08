package com.otectus.runicskills.mixin.simplyswords;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.otectus.runicskills.integration.simplyswords.SwordsWear;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.function.Consumer;

@Mixin(SwordItem.class)
public abstract class MixSwordOrdinaryWear {
    @WrapOperation(method = "hurtEnemy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"), require = 0, expect = 1)
    private void runicskills$meleeWear(ItemStack stack, int amount, LivingEntity user, Consumer<LivingEntity> broken,
            Operation<Void> original, @Local(argsOnly = true, ordinal = 0) LivingEntity target) {
        try (var ignored = SwordsWear.spend(stack, user, target, false)) { original.call(stack, amount, user, broken); }
    }
    @WrapOperation(method = "mineBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"), require = 0, expect = 1)
    private void runicskills$miningWear(ItemStack stack, int amount, LivingEntity user, Consumer<LivingEntity> broken, Operation<Void> original) {
        try (var ignored = SwordsWear.spend(stack, user, null, true)) { original.call(stack, amount, user, broken); }
    }
}
