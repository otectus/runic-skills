package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.otectus.runicskills.integration.common.RunicGuard;
import com.otectus.runicskills.integration.common.WeaponCombat;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both vanilla implementations call this once after armor, magic resistance and absorption. */
@Mixin({LivingEntity.class, Player.class})
public abstract class MixGuardDamage {
    @Inject(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V", at = @At("HEAD"))
    private void runicskills$beforeDamage(DamageSource source, float damage, CallbackInfo ci,
            @Share("guardIncoming") LocalRef<RunicGuard.Incoming> incoming,
            @Share("healthBefore") LocalFloatRef health) {
        incoming.set(RunicGuard.incoming((LivingEntity) (Object) this));
        health.set(((LivingEntity) (Object) this).getHealth());
    }
    @WrapOperation(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F",
            remap = false), require = 1, expect = 1)
    private float runicskills$guard(LivingEntity entity, DamageSource source, float damage, Operation<Float> original,
            @Share("guardIncoming") LocalRef<RunicGuard.Incoming> incoming) {
        float result = original.call(entity, source, damage);
        result = WeaponCombat.primaryDamage(entity, source, result);
        result = com.otectus.runicskills.integration.tom.TomCombatBridge.damage(entity, source, result);
        return RunicGuard.afterCallbacks(entity, source, result, incoming.get());
    }
    @Inject(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V", at = @At("RETURN"))
    private void runicskills$landed(DamageSource source, float damage, CallbackInfo ci,
            @Share("healthBefore") LocalFloatRef health) {
        LivingEntity self = (LivingEntity) (Object) this;
        WeaponCombat.landed(self, source, health.get() - self.getHealth());
        com.otectus.runicskills.integration.tom.TomCombatBridge.landed(self, source, health.get() - self.getHealth());
    }
}
