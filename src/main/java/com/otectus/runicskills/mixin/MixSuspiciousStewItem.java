package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.otectus.runicskills.common.effects.EffectApplicationContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SuspiciousStewItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.function.Consumer;

/** Stew reads effects from NBT instead of FoodProperties. Scope each actual native application. */
@Mixin(SuspiciousStewItem.class)
public abstract class MixSuspiciousStewItem {
    @WrapOperation(method = "finishUsingItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/SuspiciousStewItem;listPotionEffects(Lnet/minecraft/world/item/ItemStack;Ljava/util/function/Consumer;)V"))
    private void runicskills$stewEffects(ItemStack stack, Consumer<MobEffectInstance> receiver, Operation<Void> original,
            ItemStack consumed, Level level, LivingEntity eater) {
        original.call(stack, (Consumer<MobEffectInstance>) effect -> EffectApplicationContext.apply(eater, effect,
                EffectApplicationContext.Origin.FOOD, () -> { receiver.accept(effect); return null; }));
    }
}
