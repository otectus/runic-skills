package com.otectus.runicskills.mixin.simplyswords;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.integration.simplyswords.SwordsGems;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo
@Mixin(targets="net.sweenus.simplyswords.power.powers.BerserkPower",remap=false)
public abstract class MixNativeGemHealing {
    @WrapOperation(method="postHit",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;heal(F)V",remap=true),remap=false)
    private void runicskills$gemHealing(LivingEntity recipient,float amount,Operation<Void> original) {
        float before=recipient.getHealth();original.call(recipient,amount);SwordsGems.effect(recipient,recipient.getHealth()>before);
    }
}
