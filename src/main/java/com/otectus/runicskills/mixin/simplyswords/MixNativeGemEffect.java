package com.otectus.runicskills.mixin.simplyswords;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.otectus.runicskills.integration.simplyswords.SwordsGems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Exact native status-proc sites in 1.70.2. Rejected/unchanged effects do not count as success. */
@Pseudo @Mixin(targets={
        "net.sweenus.simplyswords.power.powers.EchoPower", "net.sweenus.simplyswords.power.powers.FloatPower",
        "net.sweenus.simplyswords.power.powers.FreezePower", "net.sweenus.simplyswords.power.powers.NullificationPower",
        "net.sweenus.simplyswords.power.powers.OnslaughtPower", "net.sweenus.simplyswords.power.powers.RadiancePower",
        "net.sweenus.simplyswords.power.powers.ShieldingPower", "net.sweenus.simplyswords.power.powers.SlowPower",
        "net.sweenus.simplyswords.power.powers.StoneskinPower", "net.sweenus.simplyswords.power.powers.SwiftnessPower",
        "net.sweenus.simplyswords.power.powers.TrailblazePower", "net.sweenus.simplyswords.power.powers.WeakenPower",
        "net.sweenus.simplyswords.power.powers.WildfirePower", "net.sweenus.simplyswords.power.powers.ZephyrPower"
},remap=false)
public abstract class MixNativeGemEffect {
    @WrapOperation(method="postHit",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",remap=true),remap=false)
    private boolean runicskills$nativeGemEffect(LivingEntity recipient,MobEffectInstance effect,Entity source,Operation<Boolean> original) {
        boolean applied=original.call(recipient,effect,source);
        SwordsGems.effect(recipient,applied); return applied;
    }
}
