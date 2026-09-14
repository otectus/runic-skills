package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.otectus.runicskills.common.effects.EffectApplicationContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.PotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PotionItem.class)
public abstract class MixPotionItem {
    @WrapOperation(method = "finishUsingItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean runicskills$potionEffect(LivingEntity target, MobEffectInstance effect, Operation<Boolean> original) {
        return EffectApplicationContext.apply(target, effect, EffectApplicationContext.Origin.POTION,
                () -> original.call(target, effect));
    }
}
