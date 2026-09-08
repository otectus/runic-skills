package com.otectus.runicskills.mixin.simplyswords;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.integration.simplyswords.SwordsGems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo
@Mixin(targets="net.sweenus.simplyswords.power.powers.MomentumPower",remap=false)
public abstract class MixGemMomentum {
    @WrapOperation(method="usageTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",remap=true),remap=false)
    private void runicskills$gemMomentum(LivingEntity recipient,Vec3 velocity,Operation<Void> original) {
        var before=recipient.getDeltaMovement();original.call(recipient,velocity);
        var after=recipient.getDeltaMovement();SwordsGems.effect(recipient,Double.isFinite(after.lengthSqr()) && after.distanceToSqr(before)>1e-8);
    }
}
