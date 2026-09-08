package com.otectus.runicskills.mixin.simplyswords;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.integration.common.WeaponCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class MixPrimaryWeaponHit {
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), require = 1, expect = 1)
    private boolean runicskills$primary(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        try (var scope = WeaponCombat.primary((Player) (Object) this, target, source)) {
            return original.call(target, source, amount);
        }
    }
}
