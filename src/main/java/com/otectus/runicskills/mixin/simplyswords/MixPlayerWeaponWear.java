package com.otectus.runicskills.mixin.simplyswords;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.otectus.runicskills.integration.simplyswords.SwordsWear;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class MixPlayerWeaponWear {
    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtEnemy(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/player/Player;)V"), require = 0, expect = 1)
    private void runicskills$directWeaponWear(ItemStack stack, LivingEntity target, Player actor, Operation<Void> original) {
        try (var ignored = SwordsWear.direct(stack, target, actor)) { original.call(stack, target, actor); }
    }
}
