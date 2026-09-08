package com.otectus.runicskills.mixin.simplyswords;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.integration.simplyswords.SwordsGems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo
@Mixin(targets={"net.sweenus.simplyswords.power.powers.FrostWardPower","net.sweenus.simplyswords.power.powers.UnstablePower"},remap=false)
public abstract class MixPassiveGemEffect {
    @WrapOperation(method="inventoryTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z",remap=true),remap=false)
    private boolean runicskills$passiveGemEffect(LivingEntity recipient,MobEffectInstance effect,Operation<Boolean> original) {
        boolean success=original.call(recipient,effect);SwordsGems.effect(recipient,success);return success;
    }
}
